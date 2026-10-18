use std::cell::{Cell, RefCell};
use std::rc::Rc;

use adw::prelude::*;
use gtk::{gio, glib};
use tokio::io::AsyncBufReadExt;

use crate::auth::{self, Account};
use crate::instance::{Instance, Loader, Session, now};
use crate::net::{self, Job};
use crate::window::App;
use crate::{dialogs, game};

enum Status {
    Renewed(Account),
    Started,
    Exited { code: Option<i32>, start: i64, secs: u64, killed: bool },
    Failed(String),
}

struct Running {
    dir: String,
    kill: async_channel::Sender<()>,
    started: Option<i64>,
    hold: Option<gio::ApplicationHoldGuard>,
}

struct Ui {
    name: gtk::Label,
    hero: gtk::Stack,
    loader: gtk::Label,
    play: gtk::Button,
    facts: gtk::Box,
    strip: gtk::DrawingArea,
    log: gtk::TextView,
    log_reveal: gtk::Revealer,
    body: gtk::Stack,
    running: RefCell<Option<Running>>,
    sessions: RefCell<Vec<Session>>,
    hero_gen: Cell<u32>,
    shown: RefCell<Option<(String, String, Loader)>>,
}

pub fn page(app: &Rc<App>) -> gtk::Widget {
    let name = gtk::Label::builder().xalign(0.0).css_classes(["instance-label"]).ellipsize(gtk::pango::EllipsizeMode::End).build();
    let hero = gtk::Stack::builder()
        .transition_type(gtk::StackTransitionType::SlideUp)
        .transition_duration(260)
        .hhomogeneous(false)
        .interpolate_size(true)
        .halign(gtk::Align::Start)
        .build();
    let loader = gtk::Label::builder().xalign(0.0).css_classes(["loader-line"]).build();
    let play = gtk::Button::builder().label("Play").css_classes(["pill", "suggested-action", "play"]).build();

    let more = gtk::MenuButton::builder()
        .icon_name("view-more-symbolic")
        .css_classes(["flat", "circular"])
        .valign(gtk::Align::Center)
        .tooltip_text("Instance")
        .build();
    let menu = gio::Menu::new();
    menu.append(Some("Settings"), Some("play.settings"));
    menu.append(Some("Change Version…"), Some("play.version"));
    menu.append(Some("Open Game Folder"), Some("play.folder"));
    menu.append(Some("Show Log"), Some("play.log"));
    more.set_menu_model(Some(&menu));

    let actions_row = gtk::Box::builder().spacing(8).margin_top(28).build();
    actions_row.append(&play);
    actions_row.append(&more);

    let left = gtk::Box::builder().orientation(gtk::Orientation::Vertical).valign(gtk::Align::End).hexpand(true).build();
    left.append(&name);
    left.append(&hero);
    left.append(&loader);
    left.append(&actions_row);

    let strip = gtk::DrawingArea::builder()
        .content_width(30 * 7 - 3)
        .content_height(30)
        .halign(gtk::Align::End)
        .has_tooltip(true)
        .margin_bottom(14)
        .build();
    let facts = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(2).halign(gtk::Align::End).build();
    let right = gtk::Box::builder().orientation(gtk::Orientation::Vertical).valign(gtk::Align::End).margin_bottom(6).build();
    right.append(&strip);
    right.append(&facts);

    let bottom = gtk::Box::builder().spacing(24).build();
    bottom.append(&left);
    bottom.append(&right);

    let log = gtk::TextView::builder()
        .editable(false)
        .cursor_visible(false)
        .monospace(true)
        .wrap_mode(gtk::WrapMode::WordChar)
        .css_classes(["log"])
        .build();
    log.buffer().create_tag(Some("err"), &[("weight", &600i32)]);
    let log_scroll = gtk::ScrolledWindow::builder().child(&log).vexpand(true).build();
    let hide_log = gtk::Button::builder().label("Hide").css_classes(["flat", "small"]).halign(gtk::Align::End).build();
    let log_box = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(6).margin_bottom(28).build();
    log_box.append(&hide_log);
    log_box.append(&log_scroll);
    let log_reveal = gtk::Revealer::builder()
        .transition_type(gtk::RevealerTransitionType::Crossfade)
        .transition_duration(300)
        .vexpand(true)
        .child(&log_box)
        .build();

    let filled = gtk::Box::builder().orientation(gtk::Orientation::Vertical).build();
    filled.append(&log_reveal);
    filled.append(&bottom);

    let empty = gtk::Box::builder().orientation(gtk::Orientation::Vertical).valign(gtk::Align::End).spacing(24).build();
    empty.append(&gtk::Label::builder().label("Nothing to play yet").xalign(0.0).wrap(true).css_classes(["question"]).build());
    let create = gtk::Button::builder().label("New Instance…").halign(gtk::Align::Start).css_classes(["pill", "suggested-action"]).build();
    empty.append(&create);

    let body = gtk::Stack::builder()
        .transition_type(gtk::StackTransitionType::Crossfade)
        .margin_top(12)
        .margin_bottom(36)
        .margin_start(44)
        .margin_end(44)
        .build();
    body.add_named(&filled, Some("instance"));
    body.add_named(&empty, Some("empty"));
    body.set_vexpand(true);

    let logo = gtk::Picture::builder()
        .paintable(&gtk::gdk::Texture::from_bytes(&glib::Bytes::from_static(include_bytes!("../data/logo-white.png"))).expect("logo"))
        .content_fit(gtk::ContentFit::Contain)
        .can_shrink(true)
        .opacity(0.92)
        .build();
    let logo = adw::Clamp::builder()
        .maximum_size(150)
        .tightening_threshold(150)
        .child(&logo)
        .halign(gtk::Align::Start)
        .valign(gtk::Align::Start)
        .margin_top(20)
        .margin_start(42)
        .build();
    let page = gtk::Box::builder().orientation(gtk::Orientation::Vertical).build();
    page.append(&logo);
    page.append(&body);

    let ui = Rc::new(Ui {
        name,
        hero,
        loader,
        play,
        facts,
        strip,
        log,
        log_reveal,
        body: body.clone(),
        running: RefCell::new(None),
        sessions: RefCell::new(Vec::new()),
        hero_gen: Cell::new(0),
        shown: RefCell::new(None),
    });

    setup_strip(&ui);

    create.connect_clicked(glib::clone!(
        #[weak]
        app,
        move |_| dialogs::new_instance(&app)
    ));
    hide_log.connect_clicked(glib::clone!(
        #[weak]
        ui,
        move |_| ui.log_reveal.set_reveal_child(false)
    ));
    ui.play.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        ui,
        move |_| play_or_stop(&app, &ui)
    ));

    let group = gio::SimpleActionGroup::new();
    let settings = gio::SimpleAction::new("settings", None);
    settings.connect_activate(glib::clone!(
        #[weak]
        app,
        move |_, _| dialogs::instance_settings(&app)
    ));
    let change = gio::SimpleAction::new("version", None);
    change.connect_activate(glib::clone!(
        #[weak]
        app,
        move |_, _| dialogs::change_version(&app)
    ));
    let folder = gio::SimpleAction::new("folder", None);
    folder.connect_activate(glib::clone!(
        #[weak]
        app,
        move |_, _| {
            if let Some(inst) = app.current() {
                let _ = std::fs::create_dir_all(inst.game_dir());
                gtk::FileLauncher::new(Some(&gio::File::for_path(inst.game_dir()))).launch(
                    Some(&app.window),
                    gio::Cancellable::NONE,
                    |_| {},
                );
            }
        }
    ));
    let show_log = gio::SimpleAction::new("log", None);
    show_log.connect_activate(glib::clone!(
        #[weak]
        app,
        #[weak]
        ui,
        move |_, _| {
            if ui.log.buffer().char_count() == 0 {
                if let Some(inst) = app.current() {
                    let latest = inst.game_dir().join("logs/latest.log");
                    let text = std::fs::read_to_string(latest).unwrap_or_else(|_| "No log yet.".into());
                    ui.log.buffer().set_text(&text);
                }
            }
            ui.log_reveal.set_reveal_child(true);
            scroll_to_end(&ui.log);
        }
    ));
    group.add_action(&settings);
    group.add_action(&change);
    group.add_action(&folder);
    group.add_action(&show_log);
    body.insert_action_group("play", Some(&group));

    let keys = gtk::ShortcutController::new();
    keys.set_scope(gtk::ShortcutScope::Managed);
    keys.add_shortcut(gtk::Shortcut::new(
        gtk::ShortcutTrigger::parse_string("<Ctrl>Return"),
        Some(gtk::CallbackAction::new(glib::clone!(
            #[weak]
            app,
            #[weak]
            ui,
            #[upgrade_or]
            glib::Propagation::Proceed,
            move |_, _| {
                play_or_stop(&app, &ui);
                glib::Propagation::Stop
            }
        ))),
    ));
    app.window.add_controller(keys);

    app.window.connect_close_request(glib::clone!(
        #[weak]
        ui,
        #[upgrade_or]
        glib::Propagation::Proceed,
        move |window| {
            let mut running = ui.running.borrow_mut();
            let Some(r) = running.as_mut().filter(|r| r.started.is_some()) else {
                return glib::Propagation::Proceed;
            };
            r.hold = window.application().map(|a| a.hold());
            window.set_visible(false);
            glib::Propagation::Stop
        }
    ));

    app.on_instance(glib::clone!(
        #[strong]
        ui,
        move |app| refresh(app, &ui)
    ));

    let weak_app = Rc::downgrade(app);
    let joiner = ui.clone();
    *app.join.borrow_mut() = Some(Rc::new(move |server: String| {
        let Some(app) = weak_app.upgrade() else { return };
        if joiner.running.borrow().is_some() {
            app.toast("Minecraft is already running. Accept the invite in game.");
            return;
        }
        app.stack.set_visible_child_name("play");
        launch(&app, &joiner, Some(server));
    }));

    glib::timeout_add_seconds_local(
        30,
        glib::clone!(
            #[weak]
            app,
            #[weak]
            ui,
            #[upgrade_or]
            glib::ControlFlow::Break,
            move || {
                if ui.running.borrow().as_ref().is_some_and(|r| r.started.is_some()) {
                    refresh(&app, &ui);
                }
                glib::ControlFlow::Continue
            }
        ),
    );

    page.upcast()
}

fn refresh(app: &Rc<App>, ui: &Rc<Ui>) {
    let Some(inst) = app.current() else {
        ui.body.set_visible_child_name("empty");
        return;
    };
    ui.body.set_visible_child_name("instance");
    ui.name.set_label(&inst.name);

    let key = (inst.dir.clone(), inst.game_version.clone(), inst.loader);
    if ui.shown.borrow().as_ref() != Some(&key) {
        let first = ui.shown.borrow().is_none();
        let n = ui.hero_gen.get() + 1;
        ui.hero_gen.set(n);
        let label = gtk::Label::builder().label(&inst.game_version).xalign(0.0).css_classes(["hero"]).build();
        let page = format!("v{n}");
        ui.hero.add_named(&label, Some(&page));
        if first {
            ui.hero.set_transition_type(gtk::StackTransitionType::None);
        }
        ui.hero.set_visible_child_name(&page);
        ui.hero.set_transition_type(gtk::StackTransitionType::SlideUp);
        let hero = ui.hero.clone();
        glib::timeout_add_local_once(std::time::Duration::from_millis(400), move || {
            let mut child = hero.first_child();
            while let Some(c) = child {
                child = c.next_sibling();
                if hero.visible_child().as_ref() != Some(&c) {
                    hero.remove(&c);
                }
            }
        });
        *ui.shown.borrow_mut() = Some(key);
    }

    ui.loader.set_label(&match (&inst.loader, &inst.loader_version) {
        (Loader::Vanilla, _) => "Vanilla".to_string(),
        (l, Some(v)) => format!("{} {}", l.label(), v),
        (l, None) => l.label().to_string(),
    });

    let running_here = ui.running.borrow().as_ref().map(|r| (r.dir == inst.dir, r.started));
    match running_here {
        Some((true, Some(_))) => {
            ui.play.set_label("Stop");
            ui.play.set_sensitive(true);
        }
        Some((true, None)) => {
            ui.play.set_label("Starting");
            ui.play.set_sensitive(false);
        }
        Some((false, _)) => {
            ui.play.set_label("Play");
            ui.play.set_sensitive(false);
            ui.play.set_tooltip_text(Some("Another instance is running"));
        }
        None => {
            ui.play.set_label("Play");
            ui.play.set_sensitive(true);
            ui.play.set_tooltip_text(None);
        }
    }

    while let Some(c) = ui.facts.first_child() {
        ui.facts.remove(&c);
    }
    let mut lines = Vec::new();
    if let Some((true, Some(started))) = running_here {
        lines.push(format!("Running for {}", duration((now() - started).max(0) as u64)));
    }
    if inst.loader != Loader::Vanilla {
        let mods = count_mods(&inst);
        lines.push(match mods {
            0 => "No mods".into(),
            1 => "1 mod".into(),
            n => format!("{n} mods"),
        });
    }
    if inst.played_secs > 0 {
        lines.push(format!("{} played", duration(inst.played_secs)));
    }
    lines.push(match inst.last_played() {
        Some(t) => format!("Last played {}", relative_day(t)),
        None => "Never played".into(),
    });
    for l in lines {
        ui.facts.append(&gtk::Label::builder().label(&l).xalign(1.0).css_classes(["fact"]).build());
    }

    *ui.sessions.borrow_mut() = inst.sessions.clone();
    ui.strip.set_visible(!inst.sessions.is_empty());
    ui.strip.queue_draw();
}

fn count_mods(inst: &Instance) -> usize {
    std::fs::read_dir(inst.game_dir().join("mods"))
        .map(|d| d.flatten().filter(|e| e.file_name().to_string_lossy().ends_with(".jar")).count())
        .unwrap_or(0)
}

pub fn duration(secs: u64) -> String {
    let mins = secs / 60;
    match mins {
        0 => "Under a minute".into(),
        1..=59 => format!("{mins} min"),
        _ if mins < 600 && mins % 60 != 0 => format!("{} h {} min", mins / 60, mins % 60),
        _ => format!("{} h", mins / 60),
    }
}

fn local_midnight(t: i64) -> i64 {
    let Ok(dt) = glib::DateTime::from_unix_local(t) else { return t - t % 86400 };
    glib::DateTime::from_local(dt.year(), dt.month(), dt.day_of_month(), 0, 0, 0.0)
        .map(|d| d.to_unix())
        .unwrap_or(t)
}

fn days_ago(t: i64) -> i64 {
    let today = local_midnight(now());
    let day = local_midnight(t);
    ((today - day) as f64 / 86400.0).round() as i64
}

pub fn relative_day(t: i64) -> String {
    match days_ago(t) {
        i64::MIN..=0 => "today".into(),
        1 => "yesterday".into(),
        n @ 2..=6 => format!("{n} days ago"),
        _ => glib::DateTime::from_unix_local(t)
            .and_then(|d| d.format("%e %B"))
            .map(|s| s.trim().to_string())
            .unwrap_or_default(),
    }
}

const DAYS: usize = 30;
const BAR: f64 = 4.0;
const GAP: f64 = 3.0;

fn minutes_per_day(sessions: &[Session]) -> [u64; DAYS] {
    let mut per = [0u64; DAYS];
    for s in sessions {
        let ago = days_ago(s.start);
        if (0..DAYS as i64).contains(&ago) {
            per[DAYS - 1 - ago as usize] += s.secs / 60;
        }
    }
    per
}

fn setup_strip(ui: &Rc<Ui>) {
    let sessions = ui.clone();
    ui.strip.set_draw_func(move |area, cr, _w, h| {
        let per = minutes_per_day(&sessions.sessions.borrow());
        let max = per.iter().copied().max().unwrap_or(0).max(30) as f64;
        let c = area.color();
        let h = h as f64;
        for (i, m) in per.iter().enumerate() {
            let x = i as f64 * (BAR + GAP);
            let (bar_h, alpha) = if *m == 0 { (2.0, 0.18) } else { ((3.0 + (h - 3.0) * (*m as f64 / max)).min(h), 0.75) };
            let alpha = if i == DAYS - 1 && *m > 0 { 1.0 } else { alpha };
            cr.set_source_rgba(c.red() as f64, c.green() as f64, c.blue() as f64, alpha);
            cr.rectangle(x, h - bar_h, BAR, bar_h);
            let _ = cr.fill();
        }
    });
    let sessions = ui.clone();
    ui.strip.connect_query_tooltip(move |_, x, _, _, tip| {
        let i = (x as f64 / (BAR + GAP)) as usize;
        if i >= DAYS {
            return false;
        }
        let per = minutes_per_day(&sessions.sessions.borrow());
        let ago = (DAYS - 1 - i) as i64;
        let day = glib::DateTime::now_local()
            .and_then(|d| d.add_days(-ago as i32))
            .and_then(|d| d.format("%A %e %B"))
            .map(|s| s.to_string())
            .unwrap_or_default();
        let played = if per[i] == 0 { "Didn't play".into() } else { duration(per[i] * 60) };
        tip.set_text(Some(&format!("{day}\n{played}")));
        true
    });
}

fn scroll_to_end(view: &gtk::TextView) {
    let buf = view.buffer();
    let mark = buf.create_mark(None, &buf.end_iter(), false);
    view.scroll_mark_onscreen(&mark);
    buf.delete_mark(&mark);
}

fn append_log(view: &gtk::TextView, line: &str) {
    let buf = view.buffer();
    let adj = view.vadjustment();
    let at_bottom = adj.as_ref().is_none_or(|a| a.value() + a.page_size() >= a.upper() - 40.0);
    let mut end = buf.end_iter();
    let loud = line.contains("ERROR") || line.contains("FATAL") || line.contains("Exception") || line.starts_with("\tat ");
    if loud {
        buf.insert_with_tags_by_name(&mut end, line, &["err"]);
    } else {
        buf.insert(&mut end, line);
    }
    buf.insert(&mut buf.end_iter(), "\n");
    if buf.line_count() > 4000 {
        let mut start = buf.start_iter();
        let mut cut = buf.iter_at_line(1000).unwrap_or(buf.start_iter());
        buf.delete(&mut start, &mut cut);
    }
    if at_bottom {
        scroll_to_end(view);
    }
}

fn play_or_stop(app: &Rc<App>, ui: &Rc<Ui>) {
    launch(app, ui, None);
}

fn launch(app: &Rc<App>, ui: &Rc<Ui>, join: Option<String>) {
    if let Some(r) = ui.running.borrow().as_ref() {
        if r.started.is_some() {
            let _ = r.kill.try_send(());
        }
        return;
    }
    let Some(inst) = app.current() else { return };
    let acc = app.account.borrow().clone();
    let Some(acc) = acc else {
        let (a, u) = (app.clone(), ui.clone());
        dialogs::sign_in(app, Some(Box::new(move || launch(&a, &u, join.clone())) as Box<dyn Fn()>));
        return;
    };

    let (lines_tx, lines_rx) = async_channel::unbounded::<String>();
    let (kill_tx, kill_rx) = async_channel::bounded::<()>(1);
    let (status_tx, status_rx) = async_channel::unbounded::<Status>();

    *ui.running.borrow_mut() = Some(Running { dir: inst.dir.clone(), kill: kill_tx, started: None, hold: None });
    ui.log.buffer().set_text("");
    refresh(app, ui);

    net::rt().spawn(run(inst.clone(), acc, join, lines_tx, kill_rx, status_tx));

    let view = ui.log.clone();
    glib::spawn_future_local(async move {
        while let Ok(line) = lines_rx.recv().await {
            append_log(&view, &line);
        }
    });

    let (app, ui) = (app.clone(), ui.clone());
    glib::spawn_future_local(async move {
        while let Ok(st) = status_rx.recv().await {
            match st {
                Status::Renewed(acc) => {
                    if app.account.borrow().as_ref().map(|a| &a.uuid) == Some(&acc.uuid) {
                        *app.account.borrow_mut() = Some(acc);
                    }
                }
                Status::Started => {
                    if let Some(r) = ui.running.borrow_mut().as_mut() {
                        r.started = Some(now());
                    }
                    ui.log_reveal.set_reveal_child(true);
                    refresh(&app, &ui);
                }
                Status::Exited { code, start, secs, killed } => {
                    let finished = ui.running.borrow_mut().take();
                    let crashed = !killed && code.is_some_and(|c| c != 0);
                    app.edit_instance(&inst.dir, |i| i.record_session(start, secs));
                    if finished.is_some_and(|r| r.hold.is_some()) && !crashed {
                        app.window.close();
                        continue;
                    }
                    if crashed {
                        app.window.present();
                        crate::crash::show(&app, &inst, start);
                    } else {
                        ui.log_reveal.set_reveal_child(false);
                    }
                    refresh(&app, &ui);
                }
                Status::Failed(msg) => {
                    ui.running.borrow_mut().take();
                    refresh(&app, &ui);
                    failed(&app, &msg);
                }
            }
        }
    });
}

fn failed(app: &Rc<App>, msg: &str) {
    let (heading, body) = msg.split_once('\n').unwrap_or(("Couldn't start the game", msg));
    let dialog = adw::AlertDialog::new(Some(heading), Some(body));
    dialog.add_response("ok", "OK");
    if body.starts_with("sudo pacman") {
        dialog.set_body_use_markup(true);
        dialog.set_body(&format!("<tt>{}</tt>", glib::markup_escape_text(body)));
        dialog.add_response("copy", "Copy Command");
        let body = body.to_string();
        dialog.connect_response(Some("copy"), move |d, _| d.clipboard().set_text(&body));
    }
    dialog.present(Some(&app.window));
}

fn quick_play(game: &str, server: &str) -> Vec<String> {
    let minor = game.strip_prefix("1.").and_then(|r| r.split('.').next()).and_then(|m| m.parse::<u32>().ok());
    if minor.is_some_and(|m| m < 20) {
        let (host, port) = match server.rsplit_once(':') {
            Some((h, p)) if p.parse::<u16>().is_ok() => (h, p),
            _ => (server, "25565"),
        };
        return vec!["--server".into(), host.into(), "--port".into(), port.into()];
    }
    vec!["--quickPlayMultiplayer".into(), server.into()]
}

async fn run(
    inst: Instance,
    acc: Account,
    join: Option<String>,
    lines: async_channel::Sender<String>,
    kill: async_channel::Receiver<()>,
    status: async_channel::Sender<Status>,
) {
    let result = async {
        let job = Job::begin(format!("Preparing {}", inst.name));
        let acc2 = match auth::fresh(&acc).await {
            Ok(a) => a,
            Err(e) if e.is::<auth::Expired>() => return Err(e),
            Err(_) => acc.clone(),
        };
        if acc2.mc_token != acc.mc_token {
            let _ = status.send(Status::Renewed(acc2.clone())).await;
        }
        let mut cmd = game::prepare(
            &inst,
            game::Account { name: &acc2.name, uuid: &acc2.uuid, xuid: &acc2.xuid, token: &acc2.mc_token },
            &job,
        )
        .await?;
        drop(job);
        if let Some(token) = crate::site::wait_token(std::time::Duration::from_secs(4)).await {
            cmd.env("OPPA_TOKEN", token);
        }
        if let Some(server) = &join {
            cmd.args(quick_play(&inst.game_version, server));
        }
        cmd.stdout(std::process::Stdio::piped()).stderr(std::process::Stdio::piped());
        let child = cmd.spawn().map_err(|e| anyhow::anyhow!("Couldn't start Java: {e}"))?;
        anyhow::Ok((child, acc2))
    }
    .await;

    let (mut child, acc) = match result {
        Ok(c) => c,
        Err(e) => {
            let _ = status.send(Status::Failed(format!("{e:#}"))).await;
            return;
        }
    };
    let start = now();
    let _ = status.send(Status::Started).await;
    let _ = acc;
    let share = crate::window::settings().share_server.unwrap_or(true);
    crate::site::set_status("menu", "", &inst.game_version);

    crate::discord::set(crate::discord::Status::Menu { game: inst.game_version.clone() });
    if let Some(out) = child.stdout.take() {
        let tx = lines.clone();
        tokio::spawn(async move {
            let mut r = tokio::io::BufReader::new(out).lines();
            while let Ok(Some(l)) = r.next_line().await {
                if let Some(status) = crate::discord::from_log(&l) {
                    crate::discord::set(status);
                    crate::site::from_log(&l, share);
                }
                let _ = tx.send(l).await;
            }
        });
    }
    if let Some(err) = child.stderr.take() {
        let tx = lines.clone();
        tokio::spawn(async move {
            let mut r = tokio::io::BufReader::new(err).lines();
            while let Ok(Some(l)) = r.next_line().await {
                let _ = tx.send(l).await;
            }
        });
    }

    let mut killed = false;
    let exit = tokio::select! {
        s = child.wait() => s,
        _ = kill.recv() => {
            killed = true;
            let _ = child.start_kill();
            child.wait().await
        }
    };
    crate::site::set_status("launcher", "", "");
    crate::discord::set(crate::discord::Status::Launcher);
    let secs = (now() - start).max(0) as u64;
    let code = exit.ok().and_then(|s| s.code());
    let _ = status.send(Status::Exited { code, start, secs, killed }).await;
}
