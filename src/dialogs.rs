use std::cell::RefCell;
use std::rc::Rc;

use adw::prelude::*;
use gtk::{gio, glib};

use crate::auth;
#[cfg(target_os = "linux")]
use webkit6::prelude::*;
use crate::game;
use crate::instance::{Instance, Loader};
use crate::net;
use crate::window::App;

pub fn sign_in(app: &Rc<App>, then: Option<Box<dyn Fn()>>) {
    let then = Rc::new(RefCell::new(then));

    let intro = gtk::Label::builder()
        .label("Use the Microsoft account that owns Minecraft.")
        .wrap(true)
        .xalign(0.0)
        .css_classes(["dim-label"])
        .build();
    let browser = gtk::Button::builder().label("Sign In with Microsoft").css_classes(["pill", "suggested-action"]).halign(gtk::Align::Start).build();
    let use_code = gtk::Button::builder().label("Use a Code Instead").css_classes(["flat"]).halign(gtk::Align::Start).build();
    let choose = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(14).build();
    choose.append(&intro);
    choose.append(&browser);
    choose.append(&use_code);

    let lead = gtk::Label::builder()
        .label("Open microsoft.com/link and enter this code")
        .wrap(true)
        .xalign(0.0)
        .css_classes(["dim-label"])
        .build();
    let code = gtk::Label::builder().label(" ").xalign(0.0).css_classes(["code"]).build();
    let go = gtk::Button::builder().label("Copy Code and Open Browser").css_classes(["pill", "suggested-action"]).halign(gtk::Align::Start).sensitive(false).build();
    let code_page = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(10).build();
    code_page.append(&lead);
    code_page.append(&code);
    code_page.append(&go);

    let pages = gtk::Stack::builder()
        .transition_type(gtk::StackTransitionType::SlideLeftRight)
        .vhomogeneous(false)
        .interpolate_size(true)
        .build();
    pages.add_named(&choose, Some("choose"));
    pages.add_named(&code_page, Some("code"));

    let spinner = adw::Spinner::new();
    let state = gtk::Label::builder().xalign(0.0).wrap(true).hexpand(true).css_classes(["dim-label"]).build();
    let status = gtk::Box::builder().spacing(10).margin_top(10).visible(false).build();
    status.append(&spinner);
    status.append(&state);

    let body = gtk::Box::builder()
        .orientation(gtk::Orientation::Vertical)
        .spacing(10)
        .margin_top(6)
        .margin_bottom(28)
        .margin_start(28)
        .margin_end(28)
        .build();
    body.append(&pages);
    body.append(&status);

    let toolbar = adw::ToolbarView::builder().content(&body).build();
    toolbar.add_top_bar(&adw::HeaderBar::new());
    let dialog = adw::Dialog::builder().title("Sign In").content_width(440).child(&toolbar).build();
    dialog.present(Some(&app.window));

    let working = {
        let (status, spinner, state) = (status.clone(), spinner.clone(), state.clone());
        move |text: &str| {
            status.set_visible(true);
            spinner.set_visible(true);
            state.set_label(text);
        }
    };
    let stopped = {
        let (status, spinner, state) = (status.clone(), spinner.clone(), state.clone());
        move |text: &str| {
            status.set_visible(true);
            spinner.set_visible(false);
            state.set_label(text);
        }
    };

    browser.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        dialog,
        #[strong]
        then,
        #[strong]
        working,
        #[strong]
        stopped,
        move |b| {
            b.set_sensitive(false);
            working("Finish signing in in the Microsoft window");
            let (tx, rx) = async_channel::bounded::<Result<String, String>>(1);
            login_window(&app.window, tx);
            let (app, dialog, then, stopped, b) = (app.clone(), dialog.clone(), then.clone(), stopped.clone(), b.clone());
            glib::spawn_future_local(async move {
                match rx.recv().await {
                    Ok(Ok(code)) => {
                        complete(app, dialog, then, stopped, async move { auth::finish_browser_login(&code).await }).await;
                    }
                    Ok(Err(msg)) => stopped(&msg),
                    Err(_) => stopped("The sign-in window was closed."),
                }
                b.set_sensitive(true);
            });
        }
    ));

    use_code.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        dialog,
        #[weak]
        pages,
        #[strong]
        then,
        move |_| {
            pages.set_visible_child_name("code");
            working("Asking Microsoft for a code");
            let (app, dialog, then) = (app.clone(), dialog.clone(), then.clone());
            let (code, go, working, stopped) = (code.clone(), go.clone(), working.clone(), stopped.clone());
            glib::spawn_future_local(async move {
                let dc = match net::bg(auth::start_device_login()).await {
                    Ok(dc) => dc,
                    Err(e) => return stopped(&e.to_string()),
                };
                code.set_label(&dc.user_code);
                working("Waiting for you to finish in the browser");
                go.set_sensitive(true);
                let (uri, user_code) = (dc.verification_uri.clone(), dc.user_code.clone());
                go.connect_clicked(glib::clone!(
                    #[weak]
                    dialog,
                    move |b| {
                        b.clipboard().set_text(&user_code);
                        gtk::UriLauncher::new(&uri).launch(
                            dialog.root().and_downcast_ref::<gtk::Window>(),
                            gio::Cancellable::NONE,
                            |_| {},
                        );
                    }
                ));
                complete(app, dialog, then, stopped, async move { auth::finish_device_login(&dc).await }).await;
            });
        }
    ));
}

async fn complete<F>(
    app: Rc<App>,
    dialog: adw::Dialog,
    then: Rc<RefCell<Option<Box<dyn Fn()>>>>,
    stopped: impl Fn(&str),
    login: F,
) where
    F: std::future::Future<Output = anyhow::Result<auth::Account>> + Send + 'static,
{
    let result = net::bg(async move {
        let acc = login.await?;
        auth::store(&acc).await?;
        anyhow::Ok(acc)
    })
    .await;
    match result {
        Ok(acc) => {
            let mut roster = auth::roster();
            roster.active = Some(acc.uuid.clone());
            let _ = auth::save_roster(&roster);
            app.toast(&format!("Signed in as {}", acc.name));
            app.set_account(Some(acc));
            dialog.close();
            let next = then.borrow_mut().take();
            if let Some(f) = next {
                f();
            }
        }
        Err(_) if dialog.parent().is_none() => {}
        Err(e) => stopped(&format!("{e:#}")),
    }
}

#[cfg(target_os = "linux")]
fn login_window(parent: &adw::ApplicationWindow, done: async_channel::Sender<Result<String, String>>) {
    let session = webkit6::NetworkSession::new_ephemeral();
    let view = webkit6::WebView::builder().network_session(&session).vexpand(true).build();
    let toolbar = adw::ToolbarView::builder().content(&view).build();
    toolbar.add_top_bar(&adw::HeaderBar::new());
    let window = adw::Window::builder()
        .title("Microsoft")
        .default_width(500)
        .default_height(680)
        .modal(true)
        .transient_for(parent)
        .content(&toolbar)
        .build();

    view.connect_uri_notify(glib::clone!(
        #[weak]
        window,
        move |view| {
            let Some(uri) = view.uri() else { return };
            match auth::redirect_result(&uri) {
                Ok(None) => {}
                result => {
                    view.stop_loading();
                    let _ = done.try_send(result.map(|c| c.unwrap_or_default()));
                    window.close();
                }
            }
        }
    ));
    view.load_uri(&auth::authorize_url());
    window.present();
}

#[cfg(windows)]
fn login_window(_parent: &adw::ApplicationWindow, done: async_channel::Sender<Result<String, String>>) {
    std::thread::spawn(move || {
        if let Err(e) = webview_login(&done) {
            let _ = done.send_blocking(Err(format!("Couldn't open the sign-in window: {e}")));
        }
    });
}

#[cfg(windows)]
fn webview_login(done: &async_channel::Sender<Result<String, String>>) -> anyhow::Result<()> {
    use tao::dpi::LogicalSize;
    use tao::event::{Event, WindowEvent};
    use tao::event_loop::{ControlFlow, EventLoopBuilder};
    use tao::platform::run_return::EventLoopExtRunReturn;
    use tao::platform::windows::EventLoopBuilderExtWindows;
    use tao::window::WindowBuilder;

    let mut event_loop = EventLoopBuilder::<()>::with_user_event().with_any_thread(true).build();
    let window = WindowBuilder::new()
        .with_title("Microsoft")
        .with_inner_size(LogicalSize::new(500.0, 680.0))
        .build(&event_loop)?;
    let proxy = event_loop.create_proxy();
    let tx = done.clone();
    let _webview = wry::WebViewBuilder::new()
        .with_url(auth::authorize_url())
        .with_incognito(true)
        .with_navigation_handler(move |uri| match auth::redirect_result(&uri) {
            Ok(None) => true,
            result => {
                let _ = tx.send_blocking(result.map(|c| c.unwrap_or_default()));
                let _ = proxy.send_event(());
                false
            }
        })
        .build(&window)?;
    event_loop.run_return(|event, _, flow| {
        *flow = ControlFlow::Wait;
        if let Event::UserEvent(()) | Event::WindowEvent { event: WindowEvent::CloseRequested, .. } = event {
            *flow = ControlFlow::Exit;
        }
    });
    Ok(())
}

pub fn new_instance(app: &Rc<App>) {
    let name = adw::EntryRow::builder().title("Name").build();
    let versions = gtk::StringList::new(&[]);
    let version = adw::ComboRow::builder().title("Version").model(&versions).enable_search(true).sensitive(false).build();
    version.set_expression(Some(gtk::PropertyExpression::new(gtk::StringObject::static_type(), gtk::Expression::NONE, "string")));
    let snapshots = adw::SwitchRow::builder().title("Show snapshots").build();
    let loaders = gtk::StringList::new(&["Fabric", "Quilt", "Vanilla"]);
    let loader = adw::ComboRow::builder().title("Loader").model(&loaders).build();

    let group = adw::PreferencesGroup::new();
    group.add(&name);
    group.add(&version);
    group.add(&snapshots);
    group.add(&loader);
    let note = gtk::Label::builder().wrap(true).xalign(0.0).css_classes(["dim-label", "caption"]).margin_top(12).build();
    note.connect_label_notify(|l| l.set_visible(!l.label().is_empty()));
    note.set_visible(false);
    let body = gtk::Box::builder()
        .orientation(gtk::Orientation::Vertical)
        .margin_top(6)
        .margin_bottom(24)
        .margin_start(18)
        .margin_end(18)
        .build();
    body.append(&group);
    body.append(&note);

    let create = gtk::Button::builder().label("Create").css_classes(["suggested-action"]).sensitive(false).build();
    let cancel = gtk::Button::builder().label("Cancel").build();
    let header = adw::HeaderBar::builder().show_end_title_buttons(false).show_start_title_buttons(false).build();
    header.pack_start(&cancel);
    header.pack_end(&create);
    let toolbar = adw::ToolbarView::builder().content(&body).build();
    toolbar.add_top_bar(&header);
    let dialog = adw::Dialog::builder().title("New Instance").content_width(440).child(&toolbar).default_widget(&create).build();
    dialog.present(Some(&app.window));

    cancel.connect_clicked(glib::clone!(
        #[weak]
        dialog,
        move |_| {
            dialog.close();
        }
    ));

    let manifest: Rc<std::cell::RefCell<Option<game::Manifest>>> = Default::default();
    let fill_versions = glib::clone!(
        #[weak]
        versions,
        #[weak]
        version,
        #[weak]
        snapshots,
        #[strong]
        manifest,
        move || {
            let Some(m) = manifest.borrow().clone() else { return };
            let ids: Vec<&str> = m
                .versions
                .iter()
                .filter(|v| v.kind == "release" || (snapshots.is_active() && v.kind == "snapshot"))
                .map(|v| v.id.as_str())
                .collect();
            versions.splice(0, versions.n_items(), &ids);
            version.set_selected(0);
            version.set_sensitive(true);
        }
    );
    let f = fill_versions.clone();
    snapshots.connect_active_notify(move |_| f());

    glib::spawn_future_local(glib::clone!(
        #[weak]
        create,
        #[weak]
        note,
        async move {
            match net::bg(game::manifest()).await {
                Ok(m) => {
                    *manifest.borrow_mut() = Some(m);
                    fill_versions();
                    create.set_sensitive(true);
                }
                Err(e) => note.set_label(&format!("Couldn't reach Mojang: {e}")),
            }
        }
    ));

    create.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        dialog,
        #[weak]
        version,
        #[weak]
        loader,
        #[weak]
        name,
        #[weak]
        note,
        move |create| {
            let Some(game_version) = version.selected_item().and_downcast::<gtk::StringObject>().map(|s| s.string().to_string()) else {
                return;
            };
            let kind = match loader.selected() {
                0 => Loader::Fabric,
                1 => Loader::Quilt,
                _ => Loader::Vanilla,
            };
            let typed = name.text().trim().to_string();
            let title = if typed.is_empty() {
                match kind {
                    Loader::Vanilla => game_version.clone(),
                    k => format!("{game_version} {}", k.label()),
                }
            } else {
                typed
            };
            create.set_sensitive(false);
            let create = create.clone();
            glib::spawn_future_local(async move {
                let gv = game_version.clone();
                let loader_version = match net::bg(async move { game::latest_loader(kind, &gv).await }).await {
                    Ok(v) => v,
                    Err(e) => {
                        note.set_label(&format!("Couldn't reach {}: {e}", kind.label()));
                        create.set_sensitive(true);
                        return;
                    }
                };
                if kind != Loader::Vanilla && loader_version.is_none() {
                    note.set_label(&format!("{} doesn't support {game_version} yet.", kind.label()));
                    create.set_sensitive(true);
                    return;
                }
                let mut inst = Instance::new(&title, &game_version, kind, loader_version);
                if let Err(e) = inst.save() {
                    note.set_label(&format!("Couldn't create the folder: {e}"));
                    create.set_sensitive(true);
                    return;
                }
                if inst.loader != Loader::Vanilla {
                    let target = inst.clone();
                    if let Err(e) = net::bg(async move { crate::bundled::ensure(&target).await }).await {
                        eprintln!("couldn't add the Oppa mod: {e:#}");
                    }
                }
                app.add_instance(inst);
                dialog.close();
            });
        }
    ));
}

pub fn change_version(app: &Rc<App>) {
    let Some(inst) = app.current() else { return };
    let versions = gtk::StringList::new(&[]);
    let version = adw::ComboRow::builder().title("Version").model(&versions).enable_search(true).sensitive(false).build();
    version.set_expression(Some(gtk::PropertyExpression::new(gtk::StringObject::static_type(), gtk::Expression::NONE, "string")));
    let name = adw::EntryRow::builder().title("Name").build();
    let group = adw::PreferencesGroup::builder()
        .description("Makes a new instance on that version with your settings, servers, packs and mods. This one stays as it is.")
        .build();
    group.add(&version);
    group.add(&name);
    let note = gtk::Label::builder().wrap(true).xalign(0.0).css_classes(["dim-label", "caption"]).margin_top(12).visible(false).build();
    note.connect_label_notify(|l| l.set_visible(!l.label().is_empty()));
    let body = gtk::Box::builder().orientation(gtk::Orientation::Vertical).margin_top(6).margin_bottom(24).margin_start(18).margin_end(18).build();
    body.append(&group);
    body.append(&note);

    let go = gtk::Button::builder().label("Change").css_classes(["suggested-action"]).sensitive(false).build();
    let cancel = gtk::Button::builder().label("Cancel").build();
    let header = adw::HeaderBar::builder().show_end_title_buttons(false).show_start_title_buttons(false).build();
    header.pack_start(&cancel);
    header.pack_end(&go);
    let toolbar = adw::ToolbarView::builder().content(&body).build();
    toolbar.add_top_bar(&header);
    let dialog = adw::Dialog::builder().title("Change Version").content_width(440).child(&toolbar).default_widget(&go).build();
    dialog.present(Some(&app.window));
    cancel.connect_clicked(glib::clone!(
        #[weak]
        dialog,
        move |_| {
            dialog.close();
        }
    ));

    let order: Rc<RefCell<Vec<String>>> = Default::default();
    let current = inst.game_version.clone();
    let base = inst.name.clone();
    version.connect_selected_item_notify(glib::clone!(
        #[weak]
        name,
        move |row| {
            if let Some(v) = row.selected_item().and_downcast::<gtk::StringObject>() {
                let v = v.string();
                name.set_text(&if base.contains(current.as_str()) { base.replace(current.as_str(), &v) } else { format!("{base} {v}") });
            }
        }
    ));
    glib::spawn_future_local(glib::clone!(
        #[weak]
        versions,
        #[weak]
        version,
        #[weak]
        go,
        #[weak]
        note,
        #[strong]
        order,
        #[strong(rename_to = current)]
        inst.game_version,
        async move {
            match net::bg(game::manifest()).await {
                Ok(m) => {
                    let ids: Vec<String> = m.versions.iter().filter(|v| v.kind == "release").map(|v| v.id.clone()).collect();
                    let shown: Vec<&str> = ids.iter().map(String::as_str).filter(|v| *v != current).collect();
                    versions.splice(0, versions.n_items(), &shown);
                    *order.borrow_mut() = ids;
                    version.set_selected(0);
                    version.set_sensitive(true);
                    go.set_sensitive(true);
                }
                Err(e) => note.set_label(&format!("Couldn't reach Mojang: {e}")),
            }
        }
    ));

    go.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        dialog,
        #[weak]
        version,
        #[weak]
        name,
        #[weak]
        note,
        move |go| {
            let Some(target) = version.selected_item().and_downcast::<gtk::StringObject>().map(|s| s.string().to_string()) else { return };
            let ids = order.borrow();
            let pos = |v: &str| ids.iter().position(|i| i == v).unwrap_or(usize::MAX);
            let upgrade = pos(&target) < pos(&inst.game_version);
            drop(ids);
            let title = name.text().trim().to_string();
            let title = if title.is_empty() { target.clone() } else { title };
            go.set_sensitive(false);
            let (go, old) = (go.clone(), inst.clone());
            glib::spawn_future_local(async move {
                let kind = old.loader;
                let gv = target.clone();
                let loader_version = match net::bg(async move { game::latest_loader(kind, &gv).await }).await {
                    Ok(v) => v,
                    Err(e) => {
                        note.set_label(&format!("Couldn't reach {}: {e}", kind.label()));
                        go.set_sensitive(true);
                        return;
                    }
                };
                if kind != Loader::Vanilla && loader_version.is_none() {
                    note.set_label(&format!("{} doesn't support {target} yet.", kind.label()));
                    go.set_sensitive(true);
                    return;
                }
                let mut fresh = Instance::new(&title, &target, kind, loader_version);
                fresh.memory_mb = old.memory_mb;
                fresh.jvm_args = old.jvm_args.clone();
                fresh.performance = old.performance;
                if let Err(e) = fresh.save() {
                    note.set_label(&format!("Couldn't create the folder: {e}"));
                    go.set_sensitive(true);
                    return;
                }
                dialog.close();
                let made = fresh.clone();
                let result = net::bg(crate::migrate::carry(old, made, upgrade)).await;
                app.add_instance(fresh);
                match result {
                    Ok(r) if r.missing.is_empty() => app.toast(&format!("Ready on {target} | {} mods carried over", r.moved)),
                    Ok(r) => {
                        let list = r.missing.iter().map(|m| glib::markup_escape_text(m).to_string()).collect::<Vec<_>>().join("\n");
                        let d = adw::AlertDialog::new(
                            Some(&format!("Ready on {target}")),
                            Some(&format!("{} mods carried over. These aren't available for {target} yet:\n\n{list}", r.moved)),
                        );
                        d.set_body_use_markup(true);
                        d.add_response("ok", "OK");
                        d.present(Some(&app.window));
                    }
                    Err(e) => app.toast(&format!("Copied what it could: {e}")),
                }
                if !upgrade {
                    app.toast("Worlds stay in the old instance, since older versions can damage them");
                }
            });
        }
    ));
}

pub fn instance_settings(app: &Rc<App>) {
    let Some(inst) = app.current() else { return };

    let name = adw::EntryRow::builder().title("Name").text(&inst.name).build();
    let total_mb = system_memory_mb();
    let memory = adw::SpinRow::builder()
        .title("Memory")
        .subtitle(format!("In MB. This PC has {} GB.", (total_mb as f64 / 1024.0).round()))
        .adjustment(&gtk::Adjustment::new(inst.memory_mb as f64, 1024.0, total_mb.max(2048) as f64, 512.0, 1024.0, 0.0))
        .build();

    let jvms = game::installed_jvms();
    let mut labels = vec!["Automatic".to_string()];
    labels.extend(jvms.iter().map(|j| format!("Java {} ({})", j.major, j.name)));
    let java = adw::ComboRow::builder()
        .title("Java")
        .model(&gtk::StringList::new(&labels.iter().map(String::as_str).collect::<Vec<_>>()))
        .build();
    if let Some(p) = &inst.java {
        if let Some(i) = jvms.iter().position(|j| &j.path == p) {
            java.set_selected(i as u32 + 1);
        }
    }
    let args = adw::EntryRow::builder().title("Java arguments").text(&inst.jvm_args).build();
    let performance = adw::SwitchRow::builder()
        .title("Performance")
        .subtitle("Sodium, Lithium, FerriteCore and other speed mods, plus tuned memory settings")
        .active(inst.performance)
        .build();

    let general = adw::PreferencesGroup::new();
    general.add(&name);
    let runtime = adw::PreferencesGroup::builder().title("Runtime").build();
    runtime.add(&memory);
    runtime.add(&java);
    runtime.add(&args);
    runtime.add(&performance);

    let about = adw::PreferencesGroup::new();
    let ver = adw::ActionRow::builder().title("Minecraft").subtitle(&inst.game_version).css_classes(["property"]).build();
    about.add(&ver);
    if let Some(lv) = &inst.loader_version {
        about.add(&adw::ActionRow::builder().title(inst.loader.label()).subtitle(lv).css_classes(["property"]).build());
    }

    let delete = adw::ButtonRow::builder().title("Delete Instance").css_classes(["destructive-action"]).build();
    let danger = adw::PreferencesGroup::new();
    danger.add(&delete);

    let page = adw::PreferencesPage::new();
    page.add(&general);
    page.add(&runtime);
    page.add(&about);
    page.add(&danger);
    let dialog = adw::PreferencesDialog::builder().title(&inst.name).build();
    dialog.add(&page);

    delete.connect_activated(glib::clone!(
        #[weak]
        app,
        #[weak]
        dialog,
        move |_| {
            let confirm = adw::AlertDialog::new(
                Some(&format!("Delete “{}”?", inst.name)),
                Some("Its worlds, mods and settings go to the Trash."),
            );
            confirm.add_responses(&[("cancel", "Cancel"), ("delete", "Delete")]);
            confirm.set_response_appearance("delete", adw::ResponseAppearance::Destructive);
            confirm.set_default_response(Some("cancel"));
            confirm.connect_response(Some("delete"), glib::clone!(
                #[weak]
                app,
                #[weak]
                dialog,
                move |_, _| {
                    dialog.set_can_close(true);
                    dialog.force_close();
                    app.remove_current();
                }
            ));
            confirm.present(Some(&dialog));
        }
    ));

    let dir = app.current().map(|i| i.dir).unwrap_or_default();
    dialog.connect_closed(glib::clone!(
        #[weak]
        app,
        move |_| {
            if app.current().map(|i| i.dir) != Some(dir.clone()) {
                return;
            }
            let picked = java.selected() as usize;
            app.edit_current(|i| {
                let n = name.text().trim().to_string();
                if !n.is_empty() {
                    i.name = n;
                }
                i.memory_mb = memory.value() as u32;
                i.java = picked.checked_sub(1).and_then(|k| jvms.get(k)).map(|j| j.path.clone());
                i.jvm_args = args.text().trim().to_string();
                i.performance = performance.is_active();
            });
        }
    ));
    dialog.present(Some(&app.window));
}

fn system_memory_mb() -> u64 {
    std::fs::read_to_string("/proc/meminfo")
        .ok()
        .and_then(|s| {
            s.lines()
                .find_map(|l| l.strip_prefix("MemTotal:"))
                .and_then(|v| v.trim().trim_end_matches("kB").trim().parse::<u64>().ok())
        })
        .map(|kb| kb / 1024)
        .unwrap_or(8192)
}
