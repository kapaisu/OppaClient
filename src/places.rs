use std::cell::RefCell;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::time::SystemTime;

use adw::prelude::*;
use gtk::{gdk, gio, glib};

use crate::images;
use crate::instance::Place;
use crate::modrinth::{self, Project};
use crate::net;
use crate::play::relative_day;
use crate::window::App;

thread_local! {
    static KNOWN: RefCell<HashMap<(PathBuf, SystemTime), Option<Project>>> = RefCell::new(HashMap::new());
}

struct Where {
    nav: adw::NavigationView,
    counts: Vec<(Place, gtk::Label)>,
    path: gtk::Button,
}

pub fn page(app: &Rc<App>) -> gtk::Widget {
    let nav = adw::NavigationView::new();

    let question = gtk::Label::builder().label("Where to?").xalign(0.0).css_classes(["question"]).build();
    let grid = gtk::Grid::builder().column_homogeneous(true).column_spacing(8).row_spacing(4).margin_top(28).build();
    let mut counts = Vec::new();
    for (i, place) in Place::ALL.into_iter().enumerate() {
        let num = gtk::Label::builder().label((i + 1).to_string()).css_classes(["place-key", "numeric"]).valign(gtk::Align::Start).build();
        let title = gtk::Label::builder().label(place.title()).xalign(0.0).css_classes(["place-title"]).build();
        let count = gtk::Label::builder().xalign(0.0).css_classes(["dim-label", "numeric"]).build();
        let text = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(2).build();
        text.append(&title);
        text.append(&count);
        let inner = gtk::Box::builder().spacing(14).build();
        inner.append(&num);
        inner.append(&text);
        let button = gtk::Button::builder().child(&inner).css_classes(["flat", "place"]).build();
        button.connect_clicked(glib::clone!(
            #[weak]
            app,
            #[weak]
            nav,
            move |_| open(&app, &nav, place)
        ));
        grid.attach(&button, (i % 2) as i32, (i / 2) as i32, 1, 1);
        counts.push((place, count));
    }
    let path = gtk::Button::builder().css_classes(["flat", "path"]).halign(gtk::Align::Start).margin_top(24).tooltip_text("Open in file manager").build();

    let column = gtk::Box::builder().orientation(gtk::Orientation::Vertical).margin_top(36).margin_bottom(36).build();
    column.append(&question);
    column.append(&grid);
    column.append(&path);
    let clamp = adw::Clamp::builder().maximum_size(720).tightening_threshold(520).margin_start(28).margin_end(28).child(&column).build();
    let scroller = gtk::ScrolledWindow::builder().hscrollbar_policy(gtk::PolicyType::Never).child(&clamp).build();
    nav.add(&adw::NavigationPage::builder().tag("where").title("Where to?").child(&scroller).build());

    let w = Rc::new(Where { nav: nav.clone(), counts, path: path.clone() });

    path.connect_clicked(glib::clone!(
        #[weak]
        app,
        move |_| {
            if let Some(inst) = app.current() {
                let _ = std::fs::create_dir_all(inst.game_dir());
                launch(&app, &inst.game_dir());
            }
        }
    ));

    app.on_instance(glib::clone!(
        #[strong]
        w,
        move |app| {
            w.nav.pop_to_tag("where");
            update_counts(app, &w);
        }
    ));
    nav.connect_popped(glib::clone!(
        #[weak]
        app,
        #[weak]
        w,
        move |_, _| update_counts(&app, &w)
    ));
    app.stack.connect_visible_child_name_notify(glib::clone!(
        #[weak]
        app,
        #[weak]
        w,
        move |stack| {
            if stack.visible_child_name().as_deref() == Some("files") {
                update_counts(&app, &w);
            }
        }
    ));

    let keys = gtk::EventControllerKey::new();
    keys.connect_key_pressed(glib::clone!(
        #[weak]
        app,
        #[weak]
        nav,
        #[upgrade_or]
        glib::Propagation::Proceed,
        move |_, key, _, mods| {
            if !mods.is_empty() || app.stack.visible_child_name().as_deref() != Some("files") {
                return glib::Propagation::Proceed;
            }
            if nav.visible_page().and_then(|p| p.tag()).as_deref() != Some("where") {
                return glib::Propagation::Proceed;
            }
            let digit = key.to_unicode().and_then(|c| c.to_digit(10)).unwrap_or(0) as usize;
            match Place::ALL.get(digit.wrapping_sub(1)) {
                Some(place) => {
                    open(&app, &nav, *place);
                    glib::Propagation::Stop
                }
                None => glib::Propagation::Proceed,
            }
        }
    ));
    app.window.add_controller(keys);

    nav.upcast()
}

fn update_counts(app: &Rc<App>, w: &Where) {
    let Some(inst) = app.current() else {
        for (_, l) in &w.counts {
            l.set_label("");
        }
        w.path.set_visible(false);
        return;
    };
    let root = inst.game_dir();
    for (place, label) in &w.counts {
        let n = std::fs::read_dir(root.join(place.folder()))
            .map(|d| d.flatten().filter(|e| !e.file_name().to_string_lossy().starts_with('.')).count())
            .unwrap_or(0);
        label.set_label(&match (place, n) {
            (_, 0) => "Empty".to_string(),
            (Place::Saves, 1) => "1 world".to_string(),
            (Place::Saves, n) => format!("{n} worlds"),
            (_, 1) => "1 file".to_string(),
            (_, n) => format!("{n} files"),
        });
    }
    let home = dirs::home_dir().unwrap_or_default();
    let shown = match root.strip_prefix(&home) {
        Ok(rel) => format!("~/{}", rel.display()),
        Err(_) => root.display().to_string(),
    };
    w.path.set_label(&shown);
    w.path.set_visible(true);
}

fn launch(app: &App, path: &Path) {
    let file = gio::File::for_path(path);
    let launcher = gtk::FileLauncher::new(Some(&file));
    let app_toasts = app.toasts.clone();
    launcher.launch(Some(&app.window), gio::Cancellable::NONE, move |r| {
        if let Err(e) = r {
            if !e.matches(gtk::DialogError::Dismissed) {
                app_toasts.add_toast(adw::Toast::new(&glib::markup_escape_text(e.message())));
            }
        }
    });
}

struct Folder {
    place: Place,
    dir: PathBuf,
    list: gtk::ListBox,
    grid: gtk::FlowBox,
    content: gtk::Stack,
    monitor: RefCell<Option<gio::FileMonitor>>,
    pending: RefCell<Option<glib::SourceId>>,
    generation: std::cell::Cell<u32>,
}

#[derive(Clone)]
struct Entry {
    path: PathBuf,
    name: String,
    is_dir: bool,
    size: u64,
    modified: SystemTime,
}

fn open(app: &Rc<App>, nav: &adw::NavigationView, place: Place) {
    let Some(inst) = app.current() else { return };
    let dir = inst.game_dir().join(place.folder());
    let _ = std::fs::create_dir_all(&dir);

    let back = gtk::Button::builder().icon_name("go-previous-symbolic").css_classes(["flat", "circular"]).tooltip_text("Back").build();
    let title = gtk::Label::builder().label(place.title()).xalign(0.0).hexpand(true).css_classes(["folder-title"]).build();
    let add = gtk::Button::builder().label("Add Files…").css_classes(["flat"]).build();
    let reveal = gtk::Button::builder().icon_name("folder-open-symbolic").css_classes(["flat", "circular"]).tooltip_text("Open in file manager").build();
    let bar = gtk::Box::builder().spacing(10).build();
    bar.append(&back);
    bar.append(&title);
    bar.append(&add);
    bar.append(&reveal);

    let list = gtk::ListBox::builder().selection_mode(gtk::SelectionMode::None).css_classes(["results"]).valign(gtk::Align::Start).build();
    let grid = gtk::FlowBox::builder()
        .selection_mode(gtk::SelectionMode::None)
        .homogeneous(true)
        .min_children_per_line(2)
        .max_children_per_line(5)
        .column_spacing(10)
        .row_spacing(10)
        .valign(gtk::Align::Start)
        .activate_on_single_click(true)
        .build();
    let empty = gtk::Label::builder()
        .label(if place == Place::Saves || place == Place::Screenshots || place == Place::Logs {
            "Nothing here yet."
        } else {
            "Nothing here yet. Drop files onto this window to add them."
        })
        .wrap(true)
        .css_classes(["dim-label"])
        .valign(gtk::Align::Start)
        .margin_top(80)
        .build();
    let content = gtk::Stack::builder().transition_type(gtk::StackTransitionType::Crossfade).transition_duration(150).build();
    content.add_named(&list, Some("list"));
    content.add_named(&grid, Some("grid"));
    content.add_named(&empty, Some("empty"));

    let column = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(18).margin_top(18).margin_bottom(30).build();
    column.append(&content);
    let clamp = adw::Clamp::builder().maximum_size(900).tightening_threshold(600).margin_start(18).margin_end(18).child(&column).build();
    let scroller = gtk::ScrolledWindow::builder().hscrollbar_policy(gtk::PolicyType::Never).child(&clamp).vexpand(true).build();

    let page_box = gtk::Box::builder().orientation(gtk::Orientation::Vertical).css_classes(["dropzone"]).build();
    let bar_clamp = adw::Clamp::builder().maximum_size(900).tightening_threshold(600).margin_start(18).margin_end(18).margin_top(12).child(&bar).build();
    page_box.append(&bar_clamp);
    page_box.append(&scroller);

    let page = adw::NavigationPage::builder().title(place.title()).tag(place.folder()).child(&page_box).build();

    let f = Rc::new(Folder {
        place,
        dir: dir.clone(),
        list,
        grid,
        content,
        monitor: RefCell::new(None),
        pending: RefCell::new(None),
        generation: Default::default(),
    });

    back.connect_clicked(glib::clone!(
        #[weak]
        nav,
        move |_| {
            nav.pop();
        }
    ));
    reveal.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[strong]
        dir,
        move |_| launch(&app, &dir)
    ));
    add.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        f,
        move |_| {
            let dialog = gtk::FileDialog::builder().title(format!("Add to {}", f.place.title())).modal(true).build();
            dialog.open_multiple(Some(&app.window), gio::Cancellable::NONE, glib::clone!(
                #[weak]
                app,
                #[weak]
                f,
                move |res| {
                    if let Ok(files) = res {
                        let paths: Vec<PathBuf> = files.iter::<gio::File>().flatten().filter_map(|f| f.path()).collect();
                        copy_in(&app, &f, paths);
                    }
                }
            ));
        }
    ));

    let drop = gtk::DropTarget::new(gdk::FileList::static_type(), gdk::DragAction::COPY);
    drop.connect_drop(glib::clone!(
        #[weak]
        app,
        #[weak]
        f,
        #[upgrade_or]
        false,
        move |_, value, _, _| {
            let Ok(list) = value.get::<gdk::FileList>() else { return false };
            let paths: Vec<PathBuf> = list.files().iter().filter_map(|f| f.path()).collect();
            copy_in(&app, &f, paths);
            true
        }
    ));
    page_box.add_controller(drop);

    if let Ok(m) = gio::File::for_path(&dir).monitor_directory(gio::FileMonitorFlags::WATCH_MOVES, gio::Cancellable::NONE) {
        m.connect_changed(glib::clone!(
            #[weak]
            app,
            #[weak]
            f,
            move |_, _, _, _| {
                if let Some(id) = f.pending.take() {
                    id.remove();
                }
                let (a, ff) = (app.clone(), f.clone());
                f.pending.replace(Some(glib::timeout_add_local_once(std::time::Duration::from_millis(250), move || {
                    ff.pending.take();
                    fill(&a, &ff);
                })));
            }
        ));
        *f.monitor.borrow_mut() = Some(m);
    }
    page.connect_hidden(glib::clone!(
        #[strong]
        f,
        move |page| {
            if page.parent().is_none() {
                if let Some(m) = f.monitor.take() {
                    m.cancel();
                }
                f.grid.remove_all();
                while let Some(c) = f.list.first_child() {
                    f.list.remove(&c);
                }
            }
        }
    ));

    fill(app, &f);
    nav.push(&page);
}

fn copy_in(app: &Rc<App>, f: &Rc<Folder>, paths: Vec<PathBuf>) {
    let dir = f.dir.clone();
    let app = app.clone();
    glib::spawn_future_local(async move {
        let n = paths.len();
        let res = net::bg(async move {
            for p in paths {
                let Some(name) = p.file_name() else { continue };
                if p.is_dir() {
                    copy_dir(&p, &dir.join(name))?;
                } else {
                    tokio::fs::copy(&p, dir.join(name)).await?;
                }
            }
            anyhow::Ok(())
        })
        .await;
        match res {
            Ok(()) if n == 1 => app.toast("Added 1 file"),
            Ok(()) => app.toast(&format!("Added {n} files")),
            Err(e) => app.toast(&format!("Couldn't copy: {e}")),
        }
    });
}

fn copy_dir(from: &Path, to: &Path) -> std::io::Result<()> {
    std::fs::create_dir_all(to)?;
    for e in std::fs::read_dir(from)?.flatten() {
        let target = to.join(e.file_name());
        if e.file_type()?.is_dir() {
            copy_dir(&e.path(), &target)?;
        } else {
            std::fs::copy(e.path(), target)?;
        }
    }
    Ok(())
}

fn read(dir: &Path, place: Place) -> Vec<Entry> {
    let mut out: Vec<Entry> = std::fs::read_dir(dir)
        .map(|d| {
            d.flatten()
                .filter(|e| !e.file_name().to_string_lossy().starts_with('.'))
                .filter_map(|e| {
                    let meta = e.metadata().ok()?;
                    Some(Entry {
                        path: e.path(),
                        name: e.file_name().to_string_lossy().into_owned(),
                        is_dir: meta.is_dir(),
                        size: meta.len(),
                        modified: meta.modified().unwrap_or(SystemTime::UNIX_EPOCH),
                    })
                })
                .collect()
        })
        .unwrap_or_default();
    match place {
        Place::Saves | Place::Screenshots | Place::Logs => out.sort_by(|a, b| b.modified.cmp(&a.modified)),
        _ => out.sort_by_key(|e| (!e.is_dir, e.name.to_lowercase())),
    }
    out
}

fn fill(app: &Rc<App>, f: &Rc<Folder>) {
    let generation_id = f.generation.get() + 1;
    f.generation.set(generation_id);
    let (dir, place) = (f.dir.clone(), f.place);
    let (app, f) = (app.clone(), f.clone());
    glib::spawn_future_local(async move {
        let entries = net::bg(async move { tokio::task::spawn_blocking(move || read(&dir, place)).await.unwrap_or_default() }).await;
        if f.generation.get() != generation_id {
            return;
        }
        while let Some(c) = f.list.first_child() {
            f.list.remove(&c);
        }
        f.grid.remove_all();
        if entries.is_empty() {
            f.content.set_visible_child_name("empty");
            return;
        }
        if place == Place::Screenshots {
            for e in &entries {
                f.grid.append(&shot(&app, e));
            }
            f.content.set_visible_child_name("grid");
            return;
        }
        let mut rows = Vec::new();
        for e in &entries {
            let (row, icon, title, detail) = file_row(&app, place, e);
            f.list.append(&row);
            rows.push((e.clone(), icon, title, detail));
        }
        f.content.set_visible_child_name("list");

        if matches!(place, Place::Mods | Place::ResourcePacks | Place::Shaders) {
            identify(rows).await;
        }
    });
}

async fn identify(rows: Vec<(Entry, gtk::Image, gtk::Label, gtk::Label)>) {
    let unknown: Vec<PathBuf> = rows
        .iter()
        .filter(|(e, ..)| !e.is_dir && KNOWN.with(|k| !k.borrow().contains_key(&(e.path.clone(), e.modified))))
        .map(|(e, ..)| e.path.clone())
        .collect();
    if !unknown.is_empty() {
        let found = net::bg(modrinth::identify(unknown.clone())).await;
        KNOWN.with(|k| {
            let mut k = k.borrow_mut();
            for p in unknown {
                let modified = rows.iter().find(|(e, ..)| e.path == p).map(|(e, ..)| e.modified).unwrap();
                k.insert((p.clone(), modified), found.get(&p).cloned());
            }
        });
    }
    for (e, icon, title, detail) in rows {
        let Some(project) = KNOWN.with(|k| k.borrow().get(&(e.path.clone(), e.modified)).cloned().flatten()) else { continue };
        title.set_label(&project.title);
        if let Some(url) = project.icon_url.filter(|u| !u.is_empty()) {
            icon.set_pixel_size(36);
            icon.add_css_class("project-icon");
            icon.add_css_class("small");
            images::icon(&url, &icon);
        }
        detail.set_label(&format!("{}    {}", e.name, glib::format_size(e.size)));
    }
}

fn file_row(app: &Rc<App>, place: Place, e: &Entry) -> (gtk::ListBoxRow, gtk::Image, gtk::Label, gtk::Label) {
    let disabled = e.name.ends_with(".disabled");
    let shown = e.name.strip_suffix(".disabled").unwrap_or(&e.name).to_string();

    let gicon = if e.is_dir {
        gio::ThemedIcon::new("folder-symbolic").upcast::<gio::Icon>()
    } else {
        let (ctype, _) = gio::content_type_guess(Some(&shown), None);
        gio::content_type_get_symbolic_icon(&ctype)
    };
    let icon = gtk::Image::builder().gicon(&gicon).pixel_size(20).width_request(36).height_request(36).css_classes(["file-icon"]).build();

    let shown = if shown == "oppaclient.jar" { "Oppa".to_string() } else { shown };
    let title = gtk::Label::builder().label(&shown).xalign(0.0).ellipsize(gtk::pango::EllipsizeMode::Middle).css_classes(["project-title"]).build();
    let detail = match (place, e.is_dir) {
        (Place::Saves, true) => {
            let played = std::fs::metadata(e.path.join("level.dat"))
                .and_then(|m| m.modified())
                .unwrap_or(e.modified);
            let t = played.duration_since(SystemTime::UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0);
            format!("Played {}", relative_day(t))
        }
        (_, true) => "Folder".to_string(),
        _ => glib::format_size(e.size).to_string(),
    };
    let detail = gtk::Label::builder().label(&detail).xalign(0.0).ellipsize(gtk::pango::EllipsizeMode::Middle).css_classes(["dim-label", "caption"]).build();
    let text = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(2).hexpand(true).valign(gtk::Align::Center).build();
    text.append(&title);
    text.append(&detail);

    let line = gtk::Box::builder().spacing(14).margin_top(8).margin_bottom(8).margin_start(10).margin_end(10).build();
    line.append(&icon);
    line.append(&text);

    let trash = gtk::Button::builder().icon_name("user-trash-symbolic").css_classes(["flat", "circular", "trash"]).tooltip_text("Move to Trash").valign(gtk::Align::Center).build();
    line.append(&trash);

    if place == Place::Mods && !e.is_dir {
        let toggle = gtk::Switch::builder().active(!disabled).valign(gtk::Align::Center).tooltip_text("Load this mod").build();
        let path = e.path.clone();
        toggle.connect_state_set(glib::clone!(
            #[weak]
            app,
            #[upgrade_or]
            glib::Propagation::Proceed,
            move |sw, on| {
                let target = match path.to_string_lossy().strip_suffix(".disabled") {
                    Some(base) if on => PathBuf::from(base),
                    None if !on => PathBuf::from(format!("{}.disabled", path.display())),
                    _ => return glib::Propagation::Proceed,
                };
                if let Err(e) = std::fs::rename(&path, &target) {
                    app.toast(&format!("Couldn't rename: {e}"));
                    return glib::Propagation::Stop;
                }
                sw.set_state(on);
                glib::Propagation::Stop
            }
        ));
        line.append(&toggle);
    }

    let path = e.path.clone();
    let name = shown.clone();
    trash.connect_clicked(glib::clone!(
        #[weak]
        app,
        move |_| {
            let name = name.clone();
            gio::File::for_path(&path).trash_async(glib::Priority::DEFAULT, gio::Cancellable::NONE, glib::clone!(
                #[weak]
                app,
                move |r| match r {
                    Ok(()) => app.toast(&format!("Moved {name} to the Trash")),
                    Err(e) => app.toast(&format!("Couldn't move to Trash: {}", e.message())),
                }
            ));
        }
    ));

    let row = gtk::ListBoxRow::builder().child(&line).activatable(place != Place::Mods).build();
    let path = e.path.clone();
    row.connect_activate(glib::clone!(
        #[weak]
        app,
        move |_| launch(&app, &path)
    ));
    (row, icon, title, detail)
}

fn shot(app: &Rc<App>, e: &Entry) -> gtk::FlowBoxChild {
    let pic = gtk::Picture::builder()
        .content_fit(gtk::ContentFit::Cover)
        .height_request(110)
        .width_request(180)
        .css_classes(["shot"])
        .overflow(gtk::Overflow::Hidden)
        .tooltip_text(&e.name)
        .build();
    images::thumbnail(e.path.clone(), &pic);
    let child = gtk::FlowBoxChild::builder().child(&pic).build();
    let path = e.path.clone();
    child.connect_activate(glib::clone!(
        #[weak]
        app,
        move |_| launch(&app, &path)
    ));

    let menu = gio::Menu::new();
    menu.append(Some("Open"), Some("shot.open"));
    menu.append(Some("Move to Trash"), Some("shot.trash"));
    let pop = gtk::PopoverMenu::from_model(Some(&menu));
    pop.set_parent(&child);
    pop.set_has_arrow(false);
    let group = gio::SimpleActionGroup::new();
    let open = gio::SimpleAction::new("open", None);
    let p = e.path.clone();
    open.connect_activate(glib::clone!(
        #[weak]
        app,
        move |_, _| launch(&app, &p)
    ));
    let trash = gio::SimpleAction::new("trash", None);
    let p = e.path.clone();
    trash.connect_activate(glib::clone!(
        #[weak]
        app,
        move |_, _| {
            if let Err(e) = gio::File::for_path(&p).trash(gio::Cancellable::NONE) {
                app.toast(&format!("Couldn't move to Trash: {}", e.message()));
            }
        }
    ));
    group.add_action(&open);
    group.add_action(&trash);
    child.insert_action_group("shot", Some(&group));
    let click = gtk::GestureClick::builder().button(3).build();
    click.connect_pressed(glib::clone!(
        #[weak]
        pop,
        move |_, _, x, y| {
            pop.set_pointing_to(Some(&gdk::Rectangle::new(x as i32, y as i32, 1, 1)));
            pop.popup();
        }
    ));
    child.add_controller(click);
    child
}
