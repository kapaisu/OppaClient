use std::cell::{Cell, RefCell};
use std::rc::Rc;

use adw::prelude::*;
use gtk::{gio, glib};

use crate::auth::Account;
use crate::model::{self, Model};
use crate::net;
use crate::wardrobe::{self, Profile, Variant};
use crate::window::App;

struct Page {
    stack: gtk::Stack,
    name: gtk::Label,
    model: RefCell<Model>,
    variant: adw::ToggleGroup,
    upload: gtk::Button,
    reset: gtk::Button,
    capes: gtk::FlowBox,
    saved: gtk::FlowBox,
    profile: RefCell<Option<Profile>>,
    skin_png: RefCell<Option<Vec<u8>>>,
    loaded_for: RefCell<Option<String>>,
    busy: Cell<bool>,
    quiet: Cell<bool>,
    spin: Cell<f64>,
    started: std::time::Instant,
}

pub fn page(app: &Rc<App>) -> gtk::Widget {
    let area = gtk::DrawingArea::builder().hexpand(true).vexpand(true).width_request(260).build();

    let name = gtk::Label::builder().xalign(0.0).css_classes(["question"]).ellipsize(gtk::pango::EllipsizeMode::End).build();
    let upload = gtk::Button::builder().label("Upload Skin…").css_classes(["pill", "suggested-action"]).build();
    let variant = adw::ToggleGroup::builder().css_classes(["flat"]).build();
    variant.add(adw::Toggle::builder().name("classic").label("Classic").build());
    variant.add(adw::Toggle::builder().name("slim").label("Slim").build());
    variant.set_active_name(Some("classic"));
    let reset = gtk::Button::builder().label("Reset").css_classes(["flat"]).tooltip_text("Back to a default skin").build();
    let actions = gtk::Box::builder().spacing(10).build();
    actions.append(&upload);
    actions.append(&variant);
    actions.append(&reset);

    let capes = gtk::FlowBox::builder()
        .selection_mode(gtk::SelectionMode::None)
        .homogeneous(true)
        .max_children_per_line(6)
        .min_children_per_line(3)
        .column_spacing(6)
        .row_spacing(6)
        .activate_on_single_click(true)
        .build();
    let saved = gtk::FlowBox::builder()
        .selection_mode(gtk::SelectionMode::None)
        .homogeneous(true)
        .max_children_per_line(6)
        .min_children_per_line(3)
        .column_spacing(6)
        .row_spacing(6)
        .activate_on_single_click(true)
        .build();

    let heading = |t: &str| gtk::Label::builder().label(t).xalign(0.0).css_classes(["section"]).build();
    let column = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(10).build();
    column.append(&name);
    column.append(&heading("Skin"));
    column.append(&actions);
    column.append(&heading("Cape"));
    column.append(&capes);
    column.append(&heading("Saved"));
    column.append(&saved);
    let scroller = gtk::ScrolledWindow::builder()
        .hscrollbar_policy(gtk::PolicyType::Never)
        .propagate_natural_height(true)
        .child(&column)
        .width_request(380)
        .valign(gtk::Align::Center)
        .build();

    let layout = gtk::Box::builder().spacing(24).margin_start(24).margin_end(44).margin_top(12).margin_bottom(24).build();
    layout.append(&area);
    layout.append(&scroller);

    let signed_out = gtk::Box::builder().orientation(gtk::Orientation::Vertical).valign(gtk::Align::End).spacing(24)
        .margin_start(44).margin_bottom(36).build();
    signed_out.append(&gtk::Label::builder().label("Sign in to change your skin").xalign(0.0).wrap(true).css_classes(["question"]).build());
    let sign_in = gtk::Button::builder().label("Sign In").halign(gtk::Align::Start).css_classes(["pill", "suggested-action"]).build();
    signed_out.append(&sign_in);

    let stack = gtk::Stack::builder().transition_type(gtk::StackTransitionType::Crossfade).build();
    stack.add_named(&layout, Some("skin"));
    stack.add_named(&signed_out, Some("signed-out"));

    let p = Rc::new(Page {
        stack: stack.clone(),
        name,
        model: RefCell::new(Model::default()),
        variant: variant.clone(),
        upload: upload.clone(),
        reset: reset.clone(),
        capes,
        saved,
        profile: RefCell::new(None),
        skin_png: RefCell::new(None),
        loaded_for: RefCell::new(None),
        busy: Cell::new(false),
        quiet: Cell::new(false),
        spin: Cell::new(0.0),
        started: std::time::Instant::now(),
    });

    let pd = p.clone();
    area.set_draw_func(move |_, cr, w, h| {
        let t = pd.started.elapsed().as_secs_f64();
        pd.model.borrow().draw(cr, w as f64, h as f64, t);
    });
    area.add_tick_callback(glib::clone!(
        #[weak]
        p,
        #[upgrade_or]
        glib::ControlFlow::Break,
        move |area, _| {
            let v = p.spin.get();
            let spinning = v.abs() > 0.0005;
            if spinning {
                p.model.borrow_mut().yaw += v;
                p.spin.set(v * 0.92);
            }
            let focused = area.root().and_downcast::<gtk::Window>().is_some_and(|w| w.is_active());
            if spinning || focused {
                area.queue_draw();
            }
            glib::ControlFlow::Continue
        }
    ));
    let drag = gtk::GestureDrag::new();
    let last = Rc::new(Cell::new((0.0, 0.0)));
    drag.connect_drag_begin(glib::clone!(
        #[strong]
        last,
        #[weak]
        p,
        move |_, _, _| {
            last.set((0.0, 0.0));
            p.spin.set(0.0);
        }
    ));
    drag.connect_drag_update(glib::clone!(
        #[strong]
        last,
        #[weak]
        p,
        move |_, dx, dy| {
            let (lx, ly) = last.get();
            let (ddx, ddy) = (dx - lx, dy - ly);
            last.set((dx, dy));
            let mut m = p.model.borrow_mut();
            m.yaw += ddx * 0.012;
            m.pitch = (m.pitch + ddy * 0.006).clamp(-0.6, 0.6);
            p.spin.set(ddx * 0.012);
        }
    ));
    area.add_controller(drag);

    sign_in.connect_clicked(glib::clone!(
        #[weak]
        app,
        move |_| crate::dialogs::sign_in(&app, None)
    ));

    upload.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        p,
        move |_| choose_file(&app, &p)
    ));
    reset.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        p,
        move |_| {
            run(&app, &p, |acc| async move { wardrobe::reset(&acc).await }, "Skin reset");
        }
    ));
    variant.connect_active_name_notify(glib::clone!(
        #[weak]
        app,
        #[weak]
        p,
        move |g| {
            let slim = g.active_name().as_deref() == Some("slim");
            p.model.borrow_mut().slim = slim;
            if p.quiet.get() {
                return;
            }
            let Some(png) = p.skin_png.borrow().clone() else { return };
            let v = if slim { Variant::Slim } else { Variant::Classic };
            run(&app, &p, move |acc| async move { wardrobe::upload(&acc, png, v).await }, "Arm width changed");
        }
    ));

    app.on_account(glib::clone!(
        #[strong]
        p,
        move |app| {
            let uuid = app.account.borrow().as_ref().map(|a| a.uuid.clone());
            if uuid.is_none() {
                *p.loaded_for.borrow_mut() = None;
                p.stack.set_visible_child_name("signed-out");
                return;
            }
            p.stack.set_visible_child_name("skin");
            if *p.loaded_for.borrow() != uuid {
                load(app, &p);
            }
        }
    ));
    stack.upcast()
}

fn set_busy(p: &Page, busy: bool) {
    p.busy.set(busy);
    p.upload.set_sensitive(!busy);
    p.reset.set_sensitive(!busy);
    p.variant.set_sensitive(!busy);
    p.capes.set_sensitive(!busy);
    p.saved.set_sensitive(!busy);
}

fn load(app: &Rc<App>, p: &Rc<Page>) {
    let Some(acc) = app.account.borrow().clone() else { return };
    *p.loaded_for.borrow_mut() = Some(acc.uuid.clone());
    p.name.set_label(&acc.name);
    set_busy(p, true);
    let (app, p) = (app.clone(), p.clone());
    glib::spawn_future_local(async move {
        let res = net::bg(async move { wardrobe::profile(&acc).await }).await;
        set_busy(&p, false);
        match res {
            Ok((acc, profile)) => {
                keep_account(&app, acc);
                show(&app, &p, profile, None);
            }
            Err(e) => app.toast(&format!("{e}")),
        }
        refresh_saved(&app, &p);
    });
}

fn keep_account(app: &Rc<App>, acc: Account) {
    let same = app.account.borrow().as_ref().is_some_and(|a| a.uuid == acc.uuid);
    if same {
        *app.account.borrow_mut() = Some(acc);
    }
}

fn show(app: &Rc<App>, p: &Rc<Page>, profile: Profile, png: Option<Vec<u8>>) {
    p.name.set_label(&profile.name);
    p.quiet.set(true);
    p.variant.set_active_name(Some(if profile.variant() == Variant::Slim { "slim" } else { "classic" }));
    p.quiet.set(false);
    p.model.borrow_mut().slim = profile.variant() == Variant::Slim;

    let skin_url = profile.active_skin().map(|s| s.url.clone());
    let cape_url = profile.active_cape().map(|c| c.url.clone());
    let variant = profile.variant();
    fill_capes(app, p, &profile);
    *p.profile.borrow_mut() = Some(profile);

    let (app, p) = (app.clone(), p.clone());
    glib::spawn_future_local(async move {
        let skin = match png {
            Some(b) => Some(b),
            None => match skin_url.clone() {
                Some(url) => net::bg(async move { wardrobe::texture(&url).await }).await.ok(),
                None => None,
            },
        };
        let cape = match cape_url {
            Some(url) => net::bg(async move { wardrobe::texture(&url).await }).await.ok(),
            None => None,
        };
        if let Some(bytes) = &skin {
            p.model.borrow_mut().skin = model::Skin::from_png(bytes);
            let _ = wardrobe::remember(bytes, variant);
        }
        p.model.borrow_mut().cape = cape.as_deref().and_then(model::cape_from_png);
        *p.skin_png.borrow_mut() = skin;
        refresh_saved(&app, &p);

        let acc = app.account.borrow().clone();
        if let Some(mut acc) = acc.filter(|a| a.skin_url != skin_url) {
            acc.skin_url = skin_url;
            let _ = std::fs::remove_file(crate::images::skin_cache(&acc.uuid));
            let store = acc.clone();
            net::rt().spawn(async move { crate::auth::store(&store).await });
            app.set_account(Some(acc));
        }
    });
}

fn run<F, Fut>(app: &Rc<App>, p: &Rc<Page>, job: F, done: &'static str)
where
    F: FnOnce(Account) -> Fut + Send + 'static,
    Fut: std::future::Future<Output = anyhow::Result<(Account, Profile)>> + Send + 'static,
{
    if p.busy.get() {
        return;
    }
    let Some(acc) = app.account.borrow().clone() else { return };
    set_busy(p, true);
    let (app, p) = (app.clone(), p.clone());
    glib::spawn_future_local(async move {
        let res = net::bg(async move { job(acc).await }).await;
        set_busy(&p, false);
        match res {
            Ok((acc, profile)) => {
                keep_account(&app, acc);
                show(&app, &p, profile, None);
                app.toast(done);
            }
            Err(e) => app.toast(&format!("{e}")),
        }
    });
}

fn choose_file(app: &Rc<App>, p: &Rc<Page>) {
    let filter = gtk::FileFilter::new();
    filter.add_mime_type("image/png");
    filter.set_name(Some("PNG images"));
    let filters = gio::ListStore::new::<gtk::FileFilter>();
    filters.append(&filter);
    let dialog = gtk::FileDialog::builder().title("Choose a Skin").modal(true).filters(&filters).build();
    dialog.open(Some(&app.window), gio::Cancellable::NONE, glib::clone!(
        #[weak]
        app,
        #[weak]
        p,
        move |res| {
            let Ok(file) = res else { return };
            let Some(path) = file.path() else { return };
            let png = match std::fs::read(&path) {
                Ok(b) => b,
                Err(e) => return app.toast(&format!("Couldn't read the file: {e}")),
            };
            if let Err(e) = wardrobe::check_skin(&png) {
                return app.toast(&e.to_string());
            }
            apply_png(&app, &p, png, current_variant(&p));
        }
    ));
}

fn current_variant(p: &Page) -> Variant {
    if p.variant.active_name().as_deref() == Some("slim") { Variant::Slim } else { Variant::Classic }
}

fn apply_png(app: &Rc<App>, p: &Rc<Page>, png: Vec<u8>, variant: Variant) {
    let _ = wardrobe::remember(&png, variant);
    run(app, p, move |acc| async move { wardrobe::upload(&acc, png, variant).await }, "Skin changed");
}

fn fill_capes(app: &Rc<App>, p: &Rc<Page>, profile: &Profile) {
    p.capes.remove_all();
    let active = profile.active_cape().map(|c| c.id.clone());
    let none = tile(None, "None", active.is_none());
    none.connect_activate(glib::clone!(
        #[weak]
        app,
        #[weak]
        p,
        move |_| run(&app, &p, |acc| async move { wardrobe::cape(&acc, None).await }, "Cape hidden")
    ));
    p.capes.append(&none);
    for cape in &profile.capes {
        let chosen = active.as_deref() == Some(cape.id.as_str());
        let child = tile(Some(cape.url.clone()), &cape.name(), chosen);
        let id = cape.id.clone();
        child.connect_activate(glib::clone!(
            #[weak]
            app,
            #[weak]
            p,
            move |_| {
                let id = id.clone();
                run(&app, &p, move |acc| async move { wardrobe::cape(&acc, Some(&id)).await }, "Cape changed")
            }
        ));
        p.capes.append(&child);
    }
}

fn tile(cape_url: Option<String>, label: &str, chosen: bool) -> gtk::FlowBoxChild {
    let art = gtk::DrawingArea::builder().content_width(40).content_height(64).halign(gtk::Align::Center).build();
    let surface: Rc<RefCell<Option<gtk::cairo::ImageSurface>>> = Default::default();
    let s = surface.clone();
    art.set_draw_func(move |area, cr, w, h| match &*s.borrow() {
        Some(c) => model::draw_cape_front(cr, c, w as f64, h as f64),
        None => {
            let c = area.color();
            cr.set_source_rgba(c.red() as f64, c.green() as f64, c.blue() as f64, 0.25);
            cr.set_line_width(1.5);
            cr.rectangle(8.0, 6.0, w as f64 - 16.0, h as f64 - 12.0);
            let _ = cr.stroke();
        }
    });
    if let Some(url) = cape_url {
        let art = art.clone();
        glib::spawn_future_local(async move {
            if let Ok(png) = net::bg(async move { wardrobe::texture(&url).await }).await {
                *surface.borrow_mut() = model::cape_from_png(&png);
                art.queue_draw();
            }
        });
    }
    let name = gtk::Label::builder().label(label).ellipsize(gtk::pango::EllipsizeMode::End).max_width_chars(10).css_classes(["caption"]).build();
    let col = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(6).margin_top(8).margin_bottom(8).build();
    col.append(&art);
    col.append(&name);
    let child = gtk::FlowBoxChild::builder().child(&col).css_classes(["tile"]).build();
    if chosen {
        child.add_css_class("chosen");
    }
    child
}

fn refresh_saved(app: &Rc<App>, p: &Rc<Page>) {
    p.saved.remove_all();
    let current = p.skin_png.borrow().as_ref().map(|b| {
        use sha1::Digest;
        format!("{}.png", hex::encode(sha1::Sha1::digest(b)))
    });
    for entry in wardrobe::library() {
        let Ok(png) = std::fs::read(wardrobe::saved_path(&entry)) else { continue };
        let mut m = Model { yaw: 0.35, pitch: 0.1, slim: entry.variant == Variant::Slim, ..Model::default() };
        m.skin = model::Skin::from_png(&png);
        let m = Rc::new(m);
        let art = gtk::DrawingArea::builder().content_width(56).content_height(84).halign(gtk::Align::Center).build();
        let md = m.clone();
        art.set_draw_func(move |_, cr, w, h| md.draw(cr, w as f64, h as f64, 0.0));

        let trash = gtk::Button::builder()
            .icon_name("user-trash-symbolic")
            .css_classes(["flat", "circular", "trash"])
            .halign(gtk::Align::End)
            .valign(gtk::Align::Start)
            .tooltip_text("Forget this skin")
            .build();
        let overlay = gtk::Overlay::builder().child(&art).margin_top(6).margin_bottom(6).build();
        overlay.add_overlay(&trash);
        let child = gtk::FlowBoxChild::builder().child(&overlay).css_classes(["tile"]).build();
        if current.as_deref() == Some(entry.file.as_str()) {
            child.add_css_class("chosen");
        }
        let e = entry.clone();
        trash.connect_clicked(glib::clone!(
            #[weak]
            app,
            #[weak]
            p,
            move |_| {
                let _ = wardrobe::forget(&e);
                refresh_saved(&app, &p);
            }
        ));
        let variant = entry.variant;
        child.connect_activate(glib::clone!(
            #[weak]
            app,
            #[weak]
            p,
            move |_| apply_png(&app, &p, png.clone(), variant)
        ));
        p.saved.append(&child);
    }
}
