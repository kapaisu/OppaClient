use std::cell::RefCell;
use std::collections::HashMap;
use std::rc::Rc;

use adw::prelude::*;
use gtk::glib;

use crate::auth::{self, Account};
use crate::instance::{self, Instance, data_dir};
use crate::net::{self, Activity};
use crate::{browse, dialogs, friends, images, places, play, prefs, skin};

type Listener = Box<dyn Fn(&Rc<App>)>;
type Joiner = Rc<dyn Fn(String)>;

pub struct App {
    pub window: adw::ApplicationWindow,
    pub backdrop: gtk::Picture,
    pub dim: gtk::Box,
    pub toasts: adw::ToastOverlay,
    pub stack: adw::ViewStack,
    pub instances: RefCell<Vec<Instance>>,
    current: RefCell<Option<String>>,
    pub account: RefCell<Option<Account>>,
    instance_listeners: RefCell<Vec<Listener>>,
    account_listeners: RefCell<Vec<Listener>>,
    pub join: RefCell<Option<Joiner>>,
}

#[derive(Default, serde::Serialize, serde::Deserialize)]
pub struct Settings {
    #[serde(default)]
    pub last_instance: Option<String>,
    #[serde(default)]
    pub background: Option<String>,
    #[serde(default)]
    pub dim: Option<f64>,
    #[serde(default)]
    pub discord: Option<bool>,
    #[serde(default)]
    pub share_server: Option<bool>,
}

pub const DEFAULT_DIM: f64 = 0.80;

pub fn settings() -> Settings {
    std::fs::read(data_dir().join("settings.json"))
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or_default()
}

pub fn save_settings(s: &Settings) {
    let _ = std::fs::create_dir_all(data_dir());
    let _ = std::fs::write(data_dir().join("settings.json"), serde_json::to_vec_pretty(s).unwrap_or_default());
}

impl App {
    pub fn current(&self) -> Option<Instance> {
        let dir = self.current.borrow().clone()?;
        self.instances.borrow().iter().find(|i| i.dir == dir).cloned()
    }

    pub fn edit_current(self: &Rc<Self>, f: impl FnOnce(&mut Instance)) {
        let Some(dir) = self.current.borrow().clone() else { return };
        let mut list = self.instances.borrow_mut();
        if let Some(inst) = list.iter_mut().find(|i| i.dir == dir) {
            f(inst);
            if let Err(e) = inst.save() {
                drop(list);
                self.toast(&format!("Couldn't save: {e}"));
                return;
            }
        }
        drop(list);
        self.instance_changed();
    }

    pub fn edit_instance(self: &Rc<Self>, dir: &str, f: impl FnOnce(&mut Instance)) {
        let mut list = self.instances.borrow_mut();
        if let Some(inst) = list.iter_mut().find(|i| i.dir == dir) {
            f(inst);
            let _ = inst.save();
        }
        drop(list);
        if self.current.borrow().as_deref() == Some(dir) {
            self.instance_changed();
        }
    }

    pub fn select(self: &Rc<Self>, dir: Option<String>) {
        *self.current.borrow_mut() = dir.clone();
        let mut s = settings();
        s.last_instance = dir;
        save_settings(&s);
        self.instance_changed();
    }

    pub fn add_instance(self: &Rc<Self>, inst: Instance) {
        let dir = inst.dir.clone();
        self.instances.borrow_mut().insert(0, inst);
        self.select(Some(dir));
    }

    pub fn remove_current(self: &Rc<Self>) {
        let Some(inst) = self.current() else { return };
        if let Err(e) = instance::delete(&inst) {
            self.toast(&format!("Couldn't delete: {e}"));
            return;
        }
        self.instances.borrow_mut().retain(|i| i.dir != inst.dir);
        let next = self.instances.borrow().first().map(|i| i.dir.clone());
        self.select(next);
        self.toast(&format!("Moved “{}” to the Trash", inst.name));
    }

    pub fn on_instance(&self, f: impl Fn(&Rc<App>) + 'static) {
        self.instance_listeners.borrow_mut().push(Box::new(f));
    }

    pub fn on_account(&self, f: impl Fn(&Rc<App>) + 'static) {
        self.account_listeners.borrow_mut().push(Box::new(f));
    }

    pub fn instance_changed(self: &Rc<Self>) {
        for f in self.instance_listeners.borrow().iter() {
            f(self);
        }
    }

    pub fn set_account(self: &Rc<Self>, acc: Option<Account>) {
        let same = self.account.borrow().as_ref().map(|a| (&a.uuid, &a.mc_token)) == acc.as_ref().map(|a| (&a.uuid, &a.mc_token));
        if !same {
            crate::site::start(acc.clone());
        }
        *self.account.borrow_mut() = acc;
        for f in self.account_listeners.borrow().iter() {
            f(self);
        }
    }

    pub fn apply_background(&self) {
        let s = settings();
        let custom = s.background.as_ref().map(|f| data_dir().join(f)).filter(|p| p.exists());
        let texture = custom
            .and_then(|p| gtk::gdk::Texture::from_filename(p).ok())
            .or_else(|| gtk::gdk::Texture::from_bytes(&glib::Bytes::from_static(include_bytes!("../data/background.jpg"))).ok());
        self.backdrop.set_paintable(texture.as_ref());
        self.dim.set_opacity(s.dim.unwrap_or(DEFAULT_DIM));
    }

    pub fn toast(&self, msg: &str) {
        self.toasts.add_toast(adw::Toast::builder().title(glib::markup_escape_text(msg)).timeout(4).build());
    }
}

pub fn build(application: &adw::Application) {
    let window = adw::ApplicationWindow::builder()
        .application(application)
        .title("Oppa")
        .default_width(1000)
        .default_height(680)
        .width_request(560)
        .height_request(460)
        .build();

    let stack = adw::ViewStack::builder().enable_transitions(true).build();
    let backdrop = gtk::Picture::builder().content_fit(gtk::ContentFit::Cover).can_shrink(true).build();
    let dim = gtk::Box::builder().css_classes(["dim-layer"]).build();
    let app = Rc::new(App {
        window: window.clone(),
        backdrop: backdrop.clone(),
        dim: dim.clone(),
        toasts: adw::ToastOverlay::new(),
        stack: stack.clone(),
        instances: RefCell::new(instance::load_all()),
        current: RefCell::new(None),
        account: RefCell::new(None),
        instance_listeners: Default::default(),
        account_listeners: Default::default(),
        join: Default::default(),
    });

    stack.add_titled(&play::page(&app), Some("play"), "Play");
    stack.add_titled(&browse::page(&app), Some("browse"), "Mods");
    stack.add_titled(&places::page(&app), Some("files"), "Files");
    stack.add_titled(&skin::page(&app), Some("skin"), "Skin");
    stack.add_titled(&friends::page(&app), Some("friends"), "Friends");

    let switcher = adw::InlineViewSwitcher::builder()
        .stack(&stack)
        .display_mode(adw::InlineViewSwitcherDisplayMode::Labels)
        .homogeneous(true)
        .css_classes(["flat"])
        .build();

    let header = adw::HeaderBar::builder().title_widget(&switcher).build();
    header.pack_start(&instance_menu(&app));
    header.pack_end(&main_menu(&app));
    header.pack_end(&account_menu(&app));

    let (bar, reveal) = activity_bar();
    let toolbar = adw::ToolbarView::builder().content(&stack).reveal_bottom_bars(false).build();
    toolbar.add_top_bar(&header);
    toolbar.add_top_bar(&crate::update::banner(&app));
    toolbar.add_bottom_bar(&bar);
    reveal(toolbar.clone());

    app.apply_background();
    let layers = gtk::Overlay::builder().child(&backdrop).build();
    layers.add_overlay(&dim);
    layers.add_overlay(&toolbar);
    layers.set_measure_overlay(&toolbar, true);
    app.toasts.set_child(Some(&layers));
    let owner = app.clone();
    window.connect_close_request(move |_| {
        let _ = &owner;
        glib::Propagation::Proceed
    });
    window.set_content(Some(&app.toasts));
    shortcuts(&app);

    let last = settings().last_instance;
    let start = {
        let list = app.instances.borrow();
        last.filter(|d| list.iter().any(|i| &i.dir == d)).or_else(|| list.first().map(|i| i.dir.clone()))
    };
    app.select(start);
    restore_account(&app);
    crate::discord::enable(settings().discord.unwrap_or(true));
    crate::discord::set(crate::discord::Status::Launcher);
    window.present();
}

fn shortcuts(app: &Rc<App>) {
    let keys = gtk::ShortcutController::new();
    keys.set_scope(gtk::ShortcutScope::Global);
    for (i, name) in ["play", "browse", "files", "skin", "friends"].into_iter().enumerate() {
        let stack = app.stack.clone();
        keys.add_shortcut(gtk::Shortcut::new(
            gtk::ShortcutTrigger::parse_string(&format!("<Ctrl>{}", i + 1)),
            Some(gtk::CallbackAction::new(move |_, _| {
                stack.set_visible_child_name(name);
                glib::Propagation::Stop
            })),
        ));
    }
    let a = app.clone();
    keys.add_shortcut(gtk::Shortcut::new(
        gtk::ShortcutTrigger::parse_string("<Ctrl>comma"),
        Some(gtk::CallbackAction::new(move |_, _| {
            prefs::open(&a);
            glib::Propagation::Stop
        })),
    ));
    let w = app.window.clone();
    keys.add_shortcut(gtk::Shortcut::new(
        gtk::ShortcutTrigger::parse_string("<Ctrl>q|<Ctrl>w"),
        Some(gtk::CallbackAction::new(move |_, _| {
            w.close();
            glib::Propagation::Stop
        })),
    ));
    app.window.add_controller(keys);
}

fn main_menu(app: &Rc<App>) -> gtk::MenuButton {
    let menu = gtk::gio::Menu::new();
    menu.append(Some("Preferences"), Some("win.preferences"));
    menu.append(Some("About Oppa"), Some("win.about"));
    let prefs_action = gtk::gio::SimpleAction::new("preferences", None);
    prefs_action.connect_activate(glib::clone!(
        #[weak]
        app,
        move |_, _| prefs::open(&app)
    ));
    let about = gtk::gio::SimpleAction::new("about", None);
    about.connect_activate(glib::clone!(
        #[weak]
        app,
        move |_, _| {
            adw::AboutDialog::builder()
                .application_name("Oppa")
                .application_icon("dev.oppa.Launcher")
                .developer_name("kapaisu (q3c on Discord)")
                .version(env!("CARGO_PKG_VERSION"))
                .build()
                .present(Some(&app.window));
        }
    ));
    app.window.add_action(&prefs_action);
    app.window.add_action(&about);
    gtk::MenuButton::builder()
        .icon_name("open-menu-symbolic")
        .menu_model(&menu)
        .css_classes(["flat"])
        .tooltip_text("Main menu")
        .build()
}

fn instance_menu(app: &Rc<App>) -> gtk::MenuButton {
    let label = gtk::Label::builder().ellipsize(gtk::pango::EllipsizeMode::End).max_width_chars(22).build();
    let content = gtk::Box::new(gtk::Orientation::Horizontal, 6);
    content.append(&label);
    content.append(&gtk::Image::from_icon_name("pan-down-symbolic"));
    let button = gtk::MenuButton::builder().child(&content).css_classes(["flat"]).build();

    let popover = gtk::Popover::builder().css_classes(["menu", "instance-popover"]).build();
    button.set_popover(Some(&popover));

    app.on_instance(glib::clone!(
        #[weak]
        label,
        move |app| label.set_label(&app.current().map(|i| i.name).unwrap_or_else(|| "No instance".into()))
    ));

    popover.connect_show(glib::clone!(
        #[weak]
        app,
        move |popover| {
            let list = gtk::Box::new(gtk::Orientation::Vertical, 0);
            let current = app.current().map(|i| i.dir);
            for inst in app.instances.borrow().iter() {
                let row = gtk::Box::new(gtk::Orientation::Horizontal, 12);
                let name = gtk::Label::builder().label(&inst.name).xalign(0.0).hexpand(true).ellipsize(gtk::pango::EllipsizeMode::End).build();
                let ver = gtk::Label::builder().label(&inst.game_version).css_classes(["dim-label", "numeric"]).build();
                row.append(&name);
                row.append(&ver);
                let check = gtk::Image::from_icon_name("object-select-symbolic");
                check.set_opacity(if current.as_deref() == Some(inst.dir.as_str()) { 1.0 } else { 0.0 });
                row.append(&check);
                let item = gtk::Button::builder().child(&row).css_classes(["flat", "model-row"]).build();
                let dir = inst.dir.clone();
                item.connect_clicked(glib::clone!(
                    #[weak]
                    app,
                    #[weak]
                    popover,
                    move |_| {
                        popover.popdown();
                        app.select(Some(dir.clone()));
                    }
                ));
                list.append(&item);
            }
            if !app.instances.borrow().is_empty() {
                list.append(&gtk::Separator::new(gtk::Orientation::Horizontal));
            }
            let new = gtk::Button::builder().label("New Instance…").css_classes(["flat", "model-row"]).build();
            new.child().and_downcast::<gtk::Label>().map(|l| l.set_xalign(0.0));
            new.connect_clicked(glib::clone!(
                #[weak]
                app,
                #[weak]
                popover,
                move |_| {
                    popover.popdown();
                    dialogs::new_instance(&app);
                }
            ));
            list.append(&new);
            let scroller = gtk::ScrolledWindow::builder()
                .hscrollbar_policy(gtk::PolicyType::Never)
                .propagate_natural_height(true)
                .max_content_height(420)
                .width_request(260)
                .child(&list)
                .build();
            popover.set_child(Some(&scroller));
        }
    ));
    button
}

fn account_menu(app: &Rc<App>) -> gtk::MenuButton {
    let (face, pixels) = images::face_widget(22);
    let who = gtk::Stack::builder().transition_type(gtk::StackTransitionType::Crossfade).hhomogeneous(false).interpolate_size(true).build();
    who.add_named(&face, Some("face"));
    who.add_named(&gtk::Label::new(Some("Sign In")), Some("none"));
    let button = gtk::MenuButton::builder().child(&who).css_classes(["flat"]).tooltip_text("Accounts").build();
    let popover = gtk::Popover::builder().css_classes(["menu"]).build();
    button.set_popover(Some(&popover));

    let faces: Rc<RefCell<HashMap<String, [u8; 256]>>> = Default::default();

    app.on_account(glib::clone!(
        #[weak]
        face,
        #[weak]
        who,
        #[strong]
        faces,
        move |app| {
            let acc = app.account.borrow().clone();
            *pixels.borrow_mut() = None;
            face.queue_draw();
            who.set_visible_child_name(if acc.is_some() { "face" } else { "none" });
            let Some(acc) = acc else { return };
            let pixels = pixels.clone();
            let faces = faces.clone();
            glib::spawn_future_local(async move {
                if let Some(f) = images::load_face(acc.uuid.clone(), acc.skin_url.clone()).await {
                    faces.borrow_mut().insert(acc.uuid.clone(), f);
                    *pixels.borrow_mut() = Some(f);
                    face.queue_draw();
                }
            });
        }
    ));

    popover.connect_show(glib::clone!(
        #[weak]
        app,
        #[strong]
        faces,
        move |popover| {
            let list = gtk::Box::new(gtk::Orientation::Vertical, 0);
            let roster = auth::roster();
            let active = app.account.borrow().as_ref().map(|a| a.uuid.clone());
            for entry in &roster.accounts {
                let row = gtk::Box::new(gtk::Orientation::Horizontal, 10);
                let (f, px) = images::face_widget(20);
                *px.borrow_mut() = faces.borrow().get(&entry.uuid).copied();
                if px.borrow().is_none() {
                    let uuid = entry.uuid.clone();
                    let px = px.clone();
                    glib::spawn_future_local(glib::clone!(
                        #[weak]
                        f,
                        async move {
                            *px.borrow_mut() = images::load_face(uuid, None).await;
                            f.queue_draw();
                        }
                    ));
                }
                row.append(&f);
                row.append(&gtk::Label::builder().label(&entry.name).xalign(0.0).hexpand(true).build());
                let check = gtk::Image::from_icon_name("object-select-symbolic");
                check.set_opacity(if active.as_deref() == Some(&entry.uuid) { 1.0 } else { 0.0 });
                row.append(&check);
                let item = gtk::Button::builder().child(&row).css_classes(["flat", "model-row"]).build();
                let uuid = entry.uuid.clone();
                item.connect_clicked(glib::clone!(
                    #[weak]
                    app,
                    #[weak]
                    popover,
                    move |_| {
                        popover.popdown();
                        switch_account(&app, uuid.clone());
                    }
                ));
                list.append(&item);
            }
            if !roster.accounts.is_empty() {
                list.append(&gtk::Separator::new(gtk::Orientation::Horizontal));
            }
            let add = gtk::Button::builder().label("Add Microsoft Account…").css_classes(["flat", "model-row"]).build();
            add.connect_clicked(glib::clone!(
                #[weak]
                app,
                #[weak]
                popover,
                move |_| {
                    popover.popdown();
                    dialogs::sign_in(&app, None::<Box<dyn Fn()>>);
                }
            ));
            list.append(&add);
            if let Some(uuid) = active {
                let out = gtk::Button::builder().label("Sign Out").css_classes(["flat", "model-row"]).build();
                out.connect_clicked(glib::clone!(
                    #[weak]
                    app,
                    #[weak]
                    popover,
                    move |_| {
                        popover.popdown();
                        sign_out(&app, uuid.clone());
                    }
                ));
                list.append(&out);
            }
            for b in list.observe_children().into_iter().flatten() {
                if let Some(l) = b.downcast_ref::<gtk::Button>().and_then(|b| b.child()).and_downcast::<gtk::Label>() {
                    l.set_xalign(0.0);
                }
            }
            list.set_width_request(240);
            popover.set_child(Some(&list));
        }
    ));
    button
}

fn switch_account(app: &Rc<App>, uuid: String) {
    let mut roster = auth::roster();
    roster.active = Some(uuid.clone());
    let _ = auth::save_roster(&roster);
    let app = app.clone();
    glib::spawn_future_local(async move {
        match net::bg(async move { auth::load(&uuid).await }).await {
            Ok(acc) => app.set_account(Some(acc)),
            Err(e) => app.toast(&format!("Couldn't read the stored login: {e}")),
        }
    });
}

fn sign_out(app: &Rc<App>, uuid: String) {
    let app = app.clone();
    glib::spawn_future_local(async move {
        let u = uuid.clone();
        if let Err(e) = net::bg(async move { auth::forget(&u).await }).await {
            app.toast(&format!("Couldn't remove the login: {e}"));
        }
        match auth::roster().active {
            Some(next) => switch_account(&app, next),
            None => app.set_account(None),
        }
    });
}

fn restore_account(app: &Rc<App>) {
    let Some(uuid) = auth::roster().active else {
        app.set_account(None);
        return;
    };
    let app = app.clone();
    glib::spawn_future_local(async move {
        let stored = match net::bg(async move { auth::load(&uuid).await }).await {
            Ok(acc) => acc,
            Err(e) => {
                app.toast(&format!("Couldn't read the stored login: {e}"));
                app.set_account(None);
                return;
            }
        };
        app.set_account(Some(stored.clone()));
        match net::bg(async move { auth::roll(&stored).await }).await {
            Ok(renewed) => {
                if app.account.borrow().as_ref().map(|a| &a.uuid) == Some(&renewed.uuid) {
                    app.set_account(Some(renewed));
                }
            }
            Err(e) if e.is::<auth::Expired>() => app.toast(&e.to_string()),
            Err(e) => eprintln!("token refresh skipped: {e:#}"),
        }
    });
}

fn activity_bar() -> (gtk::Box, impl FnOnce(adw::ToolbarView)) {
    let bar = gtk::Box::builder().orientation(gtk::Orientation::Vertical).css_classes(["activity"]).build();
    let row = gtk::Box::new(gtk::Orientation::Horizontal, 12);
    let label = gtk::Label::builder().xalign(0.0).hexpand(true).ellipsize(gtk::pango::EllipsizeMode::End).build();
    let more = gtk::Label::builder().css_classes(["dim-label"]).build();
    let count = gtk::Label::builder().css_classes(["dim-label", "numeric"]).build();
    row.append(&label);
    row.append(&more);
    row.append(&count);
    let progress = gtk::ProgressBar::new();
    bar.append(&row);
    bar.append(&progress);

    let start = move |toolbar: adw::ToolbarView| {
        let feed = net::activity_feed();
        glib::spawn_future_local(async move {
            let mut jobs: Vec<(u64, String, u64, u64)> = Vec::new();
            let hide: Rc<RefCell<Option<glib::SourceId>>> = Default::default();
            while let Ok(msg) = feed.recv().await {
                match msg {
                    Activity::Begin { id, label } => jobs.push((id, label, 0, 0)),
                    Activity::Progress { id, done, total } => {
                        if let Some(j) = jobs.iter_mut().find(|j| j.0 == id) {
                            j.2 = done;
                            j.3 = total;
                        }
                    }
                    Activity::Relabel { id, label } => {
                        if let Some(j) = jobs.iter_mut().find(|j| j.0 == id) {
                            j.1 = label;
                            j.2 = 0;
                            j.3 = 0;
                        }
                    }
                    Activity::End { id } => jobs.retain(|j| j.0 != id),
                }
                match jobs.last() {
                    Some((_, text, done, total)) => {
                        if let Some(h) = hide.take() {
                            h.remove();
                        }
                        label.set_label(text);
                        more.set_label(&if jobs.len() > 1 { format!("{} more", jobs.len() - 1) } else { String::new() });
                        if *total > 0 {
                            count.set_label(&format!("{done} / {total}"));
                            progress.set_fraction(*done as f64 / *total as f64);
                        } else {
                            count.set_label("");
                            progress.pulse();
                        }
                        toolbar.set_reveal_bottom_bars(true);
                    }
                    None if hide.borrow().is_none() => {
                        progress.set_fraction(1.0);
                        let t = toolbar.clone();
                        let h = hide.clone();
                        hide.replace(Some(glib::timeout_add_local_once(std::time::Duration::from_millis(700), move || {
                            h.take();
                            t.set_reveal_bottom_bars(false);
                        })));
                    }
                    None => {}
                }
            }
        });
    };
    (bar, start)
}
