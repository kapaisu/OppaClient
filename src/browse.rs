use std::cell::{Cell, RefCell};
use std::collections::HashSet;
use std::rc::Rc;

use adw::prelude::*;
use gtk::glib;

use crate::images;
use crate::instance::Loader;
use crate::modrinth::{self, Hit, Kind};
use crate::net;
use crate::window::App;

struct Browser {
    entry: gtk::SearchEntry,
    list: gtk::ListBox,
    status: gtk::Stack,
    message: gtk::Label,
    retry: gtk::Button,
    kind: Cell<Kind>,
    generation: Cell<u32>,
    offset: Cell<u32>,
    total: Cell<u64>,
    loading: Cell<bool>,
    debounce: RefCell<Option<glib::SourceId>>,
    installed: RefCell<HashSet<String>>,
    busy: RefCell<HashSet<String>>,
}

pub fn page(app: &Rc<App>) -> gtk::Widget {
    let entry = gtk::SearchEntry::builder().placeholder_text("Search Modrinth").hexpand(true).build();
    let kinds = adw::ToggleGroup::new();
    for (name, label) in [("mod", "Mods"), ("shader", "Shaders"), ("resourcepack", "Resource Packs")] {
        kinds.add(adw::Toggle::builder().name(name).label(label).build());
    }
    kinds.set_active_name(Some("mod"));
    kinds.add_css_class("flat");

    let top = gtk::Box::builder().spacing(12).build();
    top.append(&entry);
    top.append(&kinds);

    let list = gtk::ListBox::builder().selection_mode(gtk::SelectionMode::None).css_classes(["results"]).build();
    let clamp = adw::Clamp::builder()
        .maximum_size(860)
        .tightening_threshold(600)
        .child(&list)
        .margin_start(18)
        .margin_end(18)
        .margin_bottom(24)
        .build();
    let scroller = gtk::ScrolledWindow::builder().hscrollbar_policy(gtk::PolicyType::Never).child(&clamp).vexpand(true).build();

    let message = gtk::Label::builder().wrap(true).justify(gtk::Justification::Center).css_classes(["dim-label"]).build();
    let retry = gtk::Button::builder().label("Try Again").halign(gtk::Align::Center).css_classes(["pill"]).visible(false).build();
    let msg_box = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(14).valign(gtk::Align::Start).margin_top(120).build();
    msg_box.append(&message);
    msg_box.append(&retry);
    let spinner = adw::Spinner::builder().width_request(28).height_request(28).valign(gtk::Align::Start).margin_top(140).build();

    let status = gtk::Stack::builder().transition_type(gtk::StackTransitionType::Crossfade).transition_duration(150).build();
    status.add_named(&scroller, Some("results"));
    status.add_named(&msg_box, Some("message"));
    status.add_named(&spinner, Some("loading"));

    let b = Rc::new(Browser {
        entry: entry.clone(),
        list,
        status: status.clone(),
        message,
        retry: retry.clone(),
        kind: Cell::new(Kind::Mod),
        generation: Cell::new(0),
        offset: Cell::new(0),
        total: Cell::new(0),
        loading: Cell::new(false),
        debounce: RefCell::new(None),
        installed: RefCell::new(HashSet::new()),
        busy: RefCell::new(HashSet::new()),
    });

    let shell = gtk::Box::builder().orientation(gtk::Orientation::Vertical).build();
    shell.append(
        &adw::Clamp::builder()
            .maximum_size(860)
            .tightening_threshold(600)
            .margin_start(18)
            .margin_end(18)
            .margin_top(10)
            .margin_bottom(10)
            .child(&top)
            .build(),
    );
    shell.append(&status);

    entry.connect_search_changed(glib::clone!(
        #[weak]
        app,
        #[weak]
        b,
        move |_| {
            if let Some(id) = b.debounce.take() {
                id.remove();
            }
            let (app2, b2) = (app.clone(), b.clone());
            let id = glib::timeout_add_local_once(std::time::Duration::from_millis(280), move || {
                b2.debounce.take();
                search(&app2, &b2, false);
            });
            b.debounce.replace(Some(id));
        }
    ));
    entry.connect_activate(glib::clone!(
        #[weak]
        app,
        #[weak]
        b,
        move |_| {
            if let Some(id) = b.debounce.take() {
                id.remove();
            }
            search(&app, &b, false);
        }
    ));
    kinds.connect_active_name_notify(glib::clone!(
        #[weak]
        app,
        #[weak]
        b,
        move |g| {
            b.kind.set(match g.active_name().as_deref() {
                Some("shader") => Kind::Shader,
                Some("resourcepack") => Kind::ResourcePack,
                _ => Kind::Mod,
            });
            search(&app, &b, false);
        }
    ));
    retry.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        b,
        move |_| search(&app, &b, false)
    ));
    scroller.connect_edge_reached(glib::clone!(
        #[weak]
        app,
        #[weak]
        b,
        move |_, pos| {
            if pos == gtk::PositionType::Bottom && !b.loading.get() && (b.offset.get() as u64) < b.total.get() {
                search(&app, &b, true);
            }
        }
    ));

    let stale = Rc::new(Cell::new(true));
    app.on_instance(glib::clone!(
        #[strong]
        b,
        #[strong]
        stale,
        move |app| {
            stale.set(true);
            if app.stack.visible_child_name().as_deref() == Some("browse") {
                stale.set(false);
                search(app, &b, false);
            }
        }
    ));
    app.stack.connect_visible_child_name_notify(glib::clone!(
        #[weak]
        app,
        #[weak]
        b,
        #[strong]
        stale,
        move |stack| {
            if stack.visible_child_name().as_deref() == Some("browse") {
                if stale.replace(false) {
                    search(&app, &b, false);
                }
                b.entry.grab_focus();
            }
        }
    ));

    let keys = gtk::ShortcutController::new();
    keys.set_scope(gtk::ShortcutScope::Managed);
    keys.add_shortcut(gtk::Shortcut::new(
        gtk::ShortcutTrigger::parse_string("<Ctrl>f"),
        Some(gtk::CallbackAction::new(glib::clone!(
            #[weak]
            app,
            #[weak]
            b,
            #[upgrade_or]
            glib::Propagation::Proceed,
            move |_, _| {
                app.stack.set_visible_child_name("browse");
                b.entry.grab_focus();
                glib::Propagation::Stop
            }
        ))),
    ));
    app.window.add_controller(keys);

    shell.upcast()
}

fn say(b: &Browser, text: &str, retry: bool) {
    b.message.set_label(text);
    b.retry.set_visible(retry);
    b.status.set_visible_child_name("message");
}

fn search(app: &Rc<App>, b: &Rc<Browser>, more: bool) {
    let Some(inst) = app.current() else {
        say(b, "Create an instance first. Results depend on its version.", false);
        return;
    };
    let kind = b.kind.get();
    if kind != Kind::ResourcePack && inst.loader == Loader::Vanilla {
        let what = if kind == Kind::Mod { "Mods" } else { "Shaders" };
        say(b, &format!("{what} need Fabric or Quilt.\n“{}” is a vanilla instance.", inst.name), false);
        return;
    }

    let generation_id = if more { b.generation.get() } else { b.generation.get() + 1 };
    b.generation.set(generation_id);
    if !more {
        b.offset.set(0);
        let game_dir = inst.game_dir();
        *b.installed.borrow_mut() = inst
            .installed()
            .into_iter()
            .filter(|(_, f)| {
                let p = game_dir.join(&f.folder).join(&f.file);
                p.exists() || p.with_file_name(format!("{}.disabled", f.file)).exists()
            })
            .map(|(pid, _)| pid)
            .collect();
        if b.list.first_child().is_none() {
            b.status.set_visible_child_name("loading");
        }
    }
    b.loading.set(true);

    let query = b.entry.text().to_string();
    let offset = b.offset.get();
    let (app, b) = (app.clone(), b.clone());
    glib::spawn_future_local(async move {
        let q = query.clone();
        let (game, loader) = (inst.game_version.clone(), inst.loader);
        let res = net::bg(async move { modrinth::search(&q, kind, &game, loader, offset).await }).await;
        if b.generation.get() != generation_id {
            return;
        }
        b.loading.set(false);
        match res {
            Ok(page) => {
                if !more {
                    while let Some(row) = b.list.first_child() {
                        b.list.remove(&row);
                    }
                }
                b.total.set(page.total);
                b.offset.set(offset + page.hits.len() as u32);
                for hit in page.hits {
                    b.list.append(&row(&app, &b, hit, kind));
                }
                if b.list.first_child().is_none() {
                    let text = if query.trim().is_empty() {
                        format!("Nothing on Modrinth for {} yet.", inst.game_version)
                    } else {
                        format!("No results for “{}” on {}.", query.trim(), inst.game_version)
                    };
                    say(&b, &text, false);
                } else {
                    b.status.set_visible_child_name("results");
                }
            }
            Err(e) => {
                if more {
                    app.toast(&format!("Couldn't load more: {e}"));
                } else {
                    say(&b, &format!("Modrinth didn't answer.\n{e}"), true);
                }
            }
        }
    });
}

fn compact(n: u64) -> String {
    match n {
        0..=999 => n.to_string(),
        1_000..=999_999 => trim(format!("{:.1}", n as f64 / 1e3)) + "k",
        _ => trim(format!("{:.1}", n as f64 / 1e6)) + "M",
    }
}

fn trim(s: String) -> String {
    s.strip_suffix(".0").map(str::to_string).unwrap_or(s)
}

fn row(app: &Rc<App>, b: &Rc<Browser>, hit: Hit, kind: Kind) -> gtk::ListBoxRow {
    let icon = gtk::Image::builder()
        .pixel_size(48)
        .valign(gtk::Align::Start)
        .css_classes(["project-icon"])
        .overflow(gtk::Overflow::Hidden)
        .build();
    if let Some(url) = &hit.icon_url {
        if !url.is_empty() {
            images::icon(url, &icon);
        }
    }

    let title = gtk::Label::builder().label(&hit.title).xalign(0.0).ellipsize(gtk::pango::EllipsizeMode::End).css_classes(["project-title"]).build();
    let author = gtk::Label::builder().label(&hit.author).xalign(0.0).ellipsize(gtk::pango::EllipsizeMode::End).css_classes(["dim-label"]).build();
    let head = gtk::Box::builder().spacing(8).build();
    head.append(&title);
    head.append(&author);
    let desc = gtk::Label::builder()
        .label(hit.description.trim())
        .xalign(0.0)
        .wrap(true)
        .lines(2)
        .ellipsize(gtk::pango::EllipsizeMode::End)
        .css_classes(["project-desc"])
        .build();
    let downloads = gtk::Label::builder()
        .label(format!("{} downloads", compact(hit.downloads)))
        .xalign(0.0)
        .css_classes(["dim-label", "caption", "numeric"])
        .build();
    let text = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(3).hexpand(true).build();
    text.append(&head);
    text.append(&desc);
    text.append(&downloads);

    let installed = b.installed.borrow().contains(&hit.project_id);
    let button = gtk::Button::builder().valign(gtk::Align::Center).css_classes(["install"]).build();
    set_state(&button, if b.busy.borrow().contains(&hit.project_id) { State::Busy } else if installed { State::Installed } else { State::Ready });

    let pid = hit.project_id.clone();
    let name = hit.title.clone();
    button.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        b,
        move |button| {
            let Some(inst) = app.current() else { return };
            set_state(button, State::Busy);
            b.busy.borrow_mut().insert(pid.clone());
            let (pid, name, button) = (pid.clone(), name.clone(), button.clone());
            glib::spawn_future_local(async move {
                let (p, n) = (pid.clone(), name.clone());
                let target = inst.clone();
                let res = net::bg(async move { modrinth::install(&p, &n, kind, &target).await }).await;
                b.busy.borrow_mut().remove(&pid);
                match res {
                    Ok(extra) => {
                        b.installed.borrow_mut().insert(pid);
                        set_state(&button, State::Installed);
                        if !extra.is_empty() {
                            app.toast(&format!("Also installed {}", extra.join(", ")));
                        }
                        app.instance_changed();
                    }
                    Err(e) => {
                        set_state(&button, State::Ready);
                        app.toast(&format!("{name}: {e}"));
                    }
                }
            });
        }
    ));

    let line = gtk::Box::builder().spacing(16).margin_top(12).margin_bottom(12).margin_start(12).margin_end(12).build();
    line.append(&icon);
    line.append(&text);
    line.append(&button);
    gtk::ListBoxRow::builder().child(&line).activatable(false).build()
}

enum State {
    Ready,
    Busy,
    Installed,
}

fn set_state(button: &gtk::Button, state: State) {
    match state {
        State::Ready => {
            button.set_child(None::<&gtk::Widget>);
            button.set_label("Install");
            button.set_sensitive(true);
            button.remove_css_class("done");
        }
        State::Busy => {
            button.set_child(Some(&adw::Spinner::new()));
            button.set_sensitive(false);
        }
        State::Installed => {
            button.set_child(None::<&gtk::Widget>);
            button.set_label("Installed");
            button.set_sensitive(false);
            button.add_css_class("done");
        }
    }
}
