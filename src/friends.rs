use std::cell::{Cell, RefCell};
use std::collections::{HashMap, HashSet};
use std::rc::Rc;

use adw::prelude::*;
use gtk::glib;

use crate::chat::{self, Event};
use crate::images;
use crate::instance::now;
use crate::net;
use crate::site::{self, Friend, Social};
use crate::window::App;

struct Page {
    list: gtk::Box,
    status: gtk::Label,
    entry: gtk::Entry,
    add: gtk::Button,
    seen_invites: RefCell<HashSet<u64>>,
    first: Cell<bool>,
    busy: Cell<bool>,
    again: Cell<bool>,
    talk: Talk,
}

#[derive(Default)]
struct Talk {
    online: Cell<bool>,
    buttons: RefCell<HashMap<String, gtk::Button>>,
    unread: RefCell<HashMap<String, i64>>,
    threads: RefCell<HashMap<String, Vec<chat::Message>>>,
    view: RefCell<Option<View>>,
}

struct View {
    uuid: String,
    name: String,
    list: gtk::Box,
    scroll: gtk::ScrolledWindow,
}

pub fn page(app: &Rc<App>) -> gtk::Widget {
    let question = gtk::Label::builder().label("Friends").xalign(0.0).css_classes(["question"]).build();
    let entry = gtk::Entry::builder().placeholder_text("Add a friend by Minecraft name").hexpand(true).max_length(16).build();
    let add = gtk::Button::builder().label("Add").css_classes(["pill", "suggested-action", "add-friend"]).build();
    let adder = gtk::Box::builder().spacing(8).margin_top(24).build();
    adder.append(&entry);
    adder.append(&add);
    let status = gtk::Label::builder().xalign(0.0).wrap(true).css_classes(["dim-label"]).margin_top(10).visible(false).build();
    let list = gtk::Box::builder().orientation(gtk::Orientation::Vertical).margin_top(18).build();

    let column = gtk::Box::builder().orientation(gtk::Orientation::Vertical).margin_top(36).margin_bottom(36).build();
    column.append(&question);
    column.append(&adder);
    column.append(&status);
    column.append(&list);
    let clamp = adw::Clamp::builder().maximum_size(720).tightening_threshold(520).margin_start(28).margin_end(28).child(&column).build();
    let scroller = gtk::ScrolledWindow::builder().hscrollbar_policy(gtk::PolicyType::Never).child(&clamp).build();

    let p = Rc::new(Page {
        list,
        status,
        entry: entry.clone(),
        add: add.clone(),
        seen_invites: Default::default(),
        first: Cell::new(true),
        busy: Cell::new(false),
        again: Cell::new(false),
        talk: Talk::default(),
    });

    let events = chat::events();
    glib::spawn_future_local(glib::clone!(
        #[weak]
        app,
        #[weak]
        p,
        async move {
            while let Ok(event) = events.recv().await {
                heard(&app, &p, event);
            }
        }
    ));

    let submit = glib::clone!(
        #[weak]
        app,
        #[weak]
        p,
        move || add_friend(&app, &p)
    );
    let s = submit.clone();
    add.connect_clicked(move |_| s());
    entry.connect_activate(move |_| submit());

    app.stack.connect_visible_child_name_notify(glib::clone!(
        #[weak]
        app,
        #[weak]
        p,
        move |stack| {
            if stack.visible_child_name().as_deref() == Some("friends") {
                refresh(&app, &p);
            }
        }
    ));
    app.on_account(glib::clone!(
        #[weak]
        p,
        move |app| {
            if site::token().is_none() {
                p.seen_invites.borrow_mut().clear();
                p.first.set(true);
                refresh(app, &p);
            }
        }
    ));
    glib::spawn_future_local(glib::clone!(
        #[weak]
        app,
        #[strong]
        p,
        async move {
            let mut token = site::token_watch();
            while token.changed().await.is_ok() {
                refresh(&app, &p);
            }
        }
    ));
    glib::timeout_add_seconds_local(
        30,
        glib::clone!(
            #[weak]
            app,
            #[weak]
            p,
            #[upgrade_or]
            glib::ControlFlow::Break,
            move || {
                if app.window.is_visible() {
                    refresh(&app, &p);
                }
                glib::ControlFlow::Continue
            }
        ),
    );

    scroller.upcast()
}

fn say(p: &Page, text: &str) {
    p.status.set_label(text);
    p.status.set_visible(!text.is_empty());
}

fn refresh(app: &Rc<App>, p: &Rc<Page>) {
    if p.busy.get() {
        p.again.set(true);
        return;
    }
    if site::token().is_none() {
        clear(&p.list);
        say(p, if app.account.borrow().is_some() { "Connecting to Oppa…" } else { "Sign in to see your friends." });
        return;
    }
    p.busy.set(true);
    let (app, p) = (app.clone(), p.clone());
    glib::spawn_future_local(async move {
        let result = net::bg(site::social()).await;
        p.busy.set(false);
        if p.again.replace(false) {
            refresh(&app, &p);
            return;
        }
        match result {
            Ok(social) => {
                if p.status.label().starts_with("Connecting") || p.status.label().starts_with("Sign in") {
                    say(&p, "");
                }
                notify_invites(&app, &p, &social);
                if app.stack.visible_child_name().as_deref() == Some("friends") || p.list.first_child().is_none() {
                    render(&app, &p, &social);
                }
            }
            Err(e) => say(&p, &format!("Couldn't load friends: {e}")),
        }
    });
}

fn notify_invites(app: &Rc<App>, p: &Page, social: &Social) {
    let first = p.first.replace(false);
    let mut seen = p.seen_invites.borrow_mut();
    for inv in &social.invites {
        if !seen.insert(inv.id) || first {
            continue;
        }
        let toast = adw::Toast::builder()
            .title(glib::markup_escape_text(&format!("{} invited you to {}", inv.name, place(&inv.server))))
            .button_label("Join")
            .timeout(15)
            .build();
        let (app2, server, game) = (app.clone(), inv.server.clone(), inv.game.clone());
        toast.connect_button_clicked(move |_| join(&app2, &server, &game));
        app.toasts.add_toast(toast);
    }
}

fn clear(list: &gtk::Box) {
    while let Some(c) = list.first_child() {
        list.remove(&c);
    }
}

fn section(list: &gtk::Box, title: &str) {
    list.append(&gtk::Label::builder().label(title).xalign(0.0).css_classes(["section"]).margin_top(18).margin_bottom(6).build());
}

fn render(app: &Rc<App>, p: &Rc<Page>, social: &Social) {
    clear(&p.list);
    p.talk.buttons.borrow_mut().clear();
    if !social.invites.is_empty() {
        section(&p.list, "Invites");
        for inv in &social.invites {
            let join_button = gtk::Button::builder().label("Join").css_classes(["pill", "friend-action"]).valign(gtk::Align::Center).build();
            let (a, server, game) = (app.clone(), inv.server.clone(), inv.game.clone());
            join_button.connect_clicked(move |_| join(&a, &server, &game));
            p.list.append(&person(&inv.name, None, &format!("Invited you to {}", place(&inv.server)), &[join_button.upcast()]));
        }
    }
    if !social.incoming.is_empty() {
        section(&p.list, "Requests");
        for r in &social.incoming {
            let accept = gtk::Button::builder().label("Accept").css_classes(["pill", "suggested-action", "friend-action"]).valign(gtk::Align::Center).build();
            let decline = gtk::Button::builder().label("Decline").css_classes(["pill", "flat", "friend-action"]).valign(gtk::Align::Center).build();
            act(app, p, &accept, "accept", &r.uuid, &r.name);
            act(app, p, &decline, "decline", &r.uuid, &r.name);
            p.list.append(&person(&r.name, Some(&r.uuid), "Wants to be friends", &[decline.upcast(), accept.upcast()]));
        }
    }
    let mut friends: Vec<&Friend> = social.friends.iter().collect();
    friends.sort_by_key(|f| (rank(&f.status), f.name.to_lowercase()));
    section(&p.list, if friends.is_empty() { "No friends yet" } else { "Friends" });
    if friends.is_empty() {
        p.list.append(
            &gtk::Label::builder()
                .label("Add someone who plays with Oppa. They'll get a request in the launcher and in game.")
                .xalign(0.0)
                .wrap(true)
                .css_classes(["dim-label"])
                .build(),
        );
    }
    for f in friends {
        let mut buttons: Vec<gtk::Widget> = Vec::new();
        if f.status == "server" && !f.server.is_empty() {
            let b = gtk::Button::builder().label("Join").css_classes(["pill", "friend-action"]).valign(gtk::Align::Center).build();
            let (a, server, game) = (app.clone(), f.server.clone(), f.game.clone());
            b.connect_clicked(move |_| join(&a, &server, &game));
            buttons.push(b.upcast());
        }
        let talk = gtk::Button::builder().css_classes(["pill", "flat", "friend-action"]).valign(gtk::Align::Center).build();
        let key = chat::plain(&f.uuid);
        label_talk(&talk, p.talk.unread.borrow().get(&key).copied().unwrap_or(0));
        let (a, pp, uuid, name) = (app.clone(), p.clone(), f.uuid.clone(), f.name.clone());
        talk.connect_clicked(move |_| open_chat(&a, &pp, &uuid, &name));
        p.talk.buttons.borrow_mut().insert(key, talk.clone());
        buttons.push(talk.upcast());
                let more = gtk::MenuButton::builder().icon_name("view-more-symbolic").css_classes(["flat", "circular"]).valign(gtk::Align::Center).build();
        let remove = gtk::Button::builder().label(format!("Remove {}", f.name)).css_classes(["flat", "model-row"]).build();
        let pop = gtk::Popover::builder().css_classes(["menu"]).child(&remove).build();
        more.set_popover(Some(&pop));
        act(app, p, &remove, "remove", &f.uuid, &f.name);
        buttons.push(more.upcast());
        p.list.append(&person(&f.name, Some(&f.uuid), &describe(f), &buttons));
    }
    if !social.outgoing.is_empty() {
        section(&p.list, "Sent");
        for r in &social.outgoing {
            let cancel = gtk::Button::builder().label("Cancel").css_classes(["pill", "flat", "friend-action"]).valign(gtk::Align::Center).build();
            act(app, p, &cancel, "cancel", &r.uuid, &r.name);
            p.list.append(&person(&r.name, Some(&r.uuid), "Waiting for them to accept", &[cancel.upcast()]));
        }
    }
}

fn label_talk(button: &gtk::Button, unread: i64) {
    button.set_label(&if unread > 0 { format!("Message | {unread}") } else { "Message".into() });
}

fn relabel(p: &Page, uuid: &str) {
    let n = p.talk.unread.borrow().get(uuid).copied().unwrap_or(0);
    if let Some(b) = p.talk.buttons.borrow().get(uuid) {
        label_talk(b, n);
    }
}

fn viewing(p: &Page) -> Option<String> {
    p.talk.view.borrow().as_ref().map(|v| v.uuid.clone())
}

fn heard(app: &Rc<App>, p: &Rc<Page>, event: Event) {
    match event {
        Event::Online(on) => p.talk.online.set(on),
        Event::Message(m) => {
            let mine = m.from == chat::me();
            let other = if mine { chat::plain(&m.to) } else { chat::plain(&m.from) };
            {
                let mut threads = p.talk.threads.borrow_mut();
                let thread = threads.entry(other.clone()).or_default();
                if thread.iter().any(|x| x.id == m.id) {
                    return;
                }
                thread.push(m.clone());
            }
            if viewing(p).as_deref() == Some(other.as_str()) {
                if !mine {
                    chat::read(&other);
                }
                show_thread(p);
            } else if !mine {
                *p.talk.unread.borrow_mut().entry(other.clone()).or_default() += 1;
                relabel(p, &other);
                let short: String = m.text.chars().take(60).collect();
                app.toast(&format!("{}: {short}", m.name));
            }
        }
        Event::History(with, messages) => {
            {
                let mut threads = p.talk.threads.borrow_mut();
                let thread = threads.entry(with.clone()).or_default();
                for m in messages {
                    if !thread.iter().any(|x| x.id == m.id) {
                        thread.push(m);
                    }
                }
                thread.sort_by_key(|m| m.id);
            }
            if viewing(p).as_deref() == Some(with.as_str()) {
                show_thread(p);
            }
        }
        Event::Unread(counts) => {
            let open = viewing(p);
            let mut unread = p.talk.unread.borrow_mut();
            unread.clear();
            for (k, n) in counts {
                let k = chat::plain(&k);
                if open.as_deref() != Some(k.as_str()) {
                    unread.insert(k, n);
                }
            }
            drop(unread);
            let keys: Vec<String> = p.talk.buttons.borrow().keys().cloned().collect();
            for k in keys {
                relabel(p, &k);
            }
        }
        Event::Refused(e) => app.toast(&format!("Message not sent: {e}")),
    }
}

fn show_thread(p: &Page) {
    let view = p.talk.view.borrow();
    let Some(v) = view.as_ref() else { return };
    while let Some(c) = v.list.first_child() {
        v.list.remove(&c);
    }
    let threads = p.talk.threads.borrow();
    let thread = threads.get(&v.uuid).map(Vec::as_slice).unwrap_or_default();
    if thread.is_empty() {
        let hint = if p.talk.online.get() { format!("Say hi to {}.", v.name) } else { "Connecting to chat…".into() };
        v.list.append(&gtk::Label::builder().label(hint).xalign(0.0).css_classes(["dim-label"]).build());
    }
    let me = chat::me();
    let mut last = String::new();
    for m in thread {
        if m.from != last {
            let who = if m.from == me { "You".to_string() } else { v.name.clone() };
            v.list.append(&gtk::Label::builder().label(who).xalign(0.0).margin_top(10).css_classes(["dim-label", "caption"]).build());
            last = m.from.clone();
        }
        let text = gtk::Label::builder().label(&m.text).xalign(0.0).wrap(true).wrap_mode(gtk::pango::WrapMode::WordChar).selectable(true).build();
        if m.from == me {
            text.add_css_class("dim-label");
        }
        v.list.append(&text);
    }
    let scroll = v.scroll.clone();
    glib::idle_add_local_once(move || {
        let adj = scroll.vadjustment();
        adj.set_value(adj.upper());
    });
}

fn open_chat(app: &Rc<App>, p: &Rc<Page>, uuid: &str, name: &str) {
    let key = chat::plain(uuid);
    let list = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(4).margin_start(20).margin_end(20).margin_top(8).margin_bottom(12).build();
    let scroll = gtk::ScrolledWindow::builder().hscrollbar_policy(gtk::PolicyType::Never).vexpand(true).child(&list).build();
    let entry = gtk::Entry::builder().placeholder_text(format!("Message {name}")).hexpand(true).max_length(500).build();
    let send = gtk::Button::builder().label("Send").css_classes(["pill", "suggested-action"]).build();
    let bar = gtk::Box::builder().spacing(8).margin_start(16).margin_end(16).margin_top(8).margin_bottom(16).build();
    bar.append(&entry);
    bar.append(&send);
    let body = gtk::Box::builder().orientation(gtk::Orientation::Vertical).build();
    body.append(&scroll);
    body.append(&bar);
    let view = adw::ToolbarView::new();
    view.add_top_bar(&adw::HeaderBar::new());
    view.set_content(Some(&body));
    let dialog = adw::Dialog::builder().title(name).content_width(460).content_height(560).child(&view).build();

    *p.talk.view.borrow_mut() = Some(View { uuid: key.clone(), name: name.to_string(), list, scroll });
    p.talk.unread.borrow_mut().remove(&key);
    relabel(p, &key);
    show_thread(p);
    chat::open(&key);

    let submit = glib::clone!(
        #[weak]
        entry,
        #[weak]
        app,
        #[strong]
        p,
        #[strong]
        key,
        move || {
            let text = entry.text().to_string();
            if text.trim().is_empty() {
                return;
            }
            if !p.talk.online.get() {
                app.toast("Chat is offline. Try again in a moment.");
                return;
            }
            chat::send(&key, &text);
            entry.set_text("");
        }
    );
    let s = submit.clone();
    send.connect_clicked(move |_| s());
    entry.connect_activate(move |_| submit());
    dialog.connect_closed(glib::clone!(
        #[strong]
        p,
        move |_| {
            p.talk.view.borrow_mut().take();
        }
    ));
    dialog.present(Some(&app.window));
    entry.grab_focus();
}

fn rank(status: &str) -> u8 {
    match status {
        "server" => 0,
        "singleplayer" | "multiplayer" | "menu" => 1,
        "launcher" => 2,
        _ => 3,
    }
}

fn place(server: &str) -> &str {
    if server.starts_with("oppa:") { "their world" } else { server }
}

fn describe(f: &Friend) -> String {
    let game = if f.game.is_empty() { String::new() } else { format!(" | {}", f.game) };
    match f.status.as_str() {
        "server" if f.server.strip_prefix("oppa:").is_some_and(|h| h == f.uuid.replace('-', "")) => format!("Hosting a world{game}"),
        "server" if f.server.starts_with("oppa:") => format!("In a friend's world{game}"),
        "server" => format!("On {}{game}", f.server),
        "singleplayer" => format!("Singleplayer{game}"),
        "multiplayer" => format!("Multiplayer{game}"),
        "menu" => format!("In game{game}"),
        "launcher" => "In the launcher".into(),
        _ if f.seen > 0 => format!("Last online {}", ago(now() - f.seen)),
        _ => "Offline".into(),
    }
}

fn ago(secs: i64) -> String {
    if secs < 120 {
        "just now".into()
    } else if secs < 3600 {
        format!("{} min ago", secs / 60)
    } else if secs < 86_400 {
        format!("{} h ago", secs / 3600)
    } else if secs < 172_800 {
        "yesterday".into()
    } else {
        format!("{} days ago", secs / 86_400)
    }
}

fn person(name: &str, uuid: Option<&str>, detail: &str, buttons: &[gtk::Widget]) -> gtk::Box {
    let (face, pixels) = images::face_widget(36);
    face.add_css_class("friend-face");
    if let Some(uuid) = uuid {
        let uuid = uuid.to_string();
        glib::spawn_future_local(glib::clone!(
            #[weak]
            face,
            async move {
                *pixels.borrow_mut() = images::face_of(uuid).await;
                face.queue_draw();
            }
        ));
    }
    let title = gtk::Label::builder().label(name).xalign(0.0).css_classes(["project-title"]).build();
    let sub = gtk::Label::builder().label(detail).xalign(0.0).ellipsize(gtk::pango::EllipsizeMode::End).css_classes(["dim-label", "caption"]).build();
    let text = gtk::Box::builder().orientation(gtk::Orientation::Vertical).spacing(2).hexpand(true).valign(gtk::Align::Center).build();
    text.append(&title);
    text.append(&sub);
    let row = gtk::Box::builder().spacing(14).css_classes(["friend"]).build();
    row.append(&face);
    row.append(&text);
    for b in buttons {
        row.append(b);
    }
    row
}

fn act(app: &Rc<App>, p: &Rc<Page>, button: &gtk::Button, action: &'static str, uuid: &str, name: &str) {
    let uuid = uuid.to_string();
    let done = match action {
        "accept" => format!("You and {name} are friends"),
        "remove" => format!("Removed {name}"),
        _ => String::new(),
    };
    button.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        p,
        move |b| {
            b.set_sensitive(false);
            let (uuid, done) = (uuid.clone(), done.clone());
            let button = b.clone();
            glib::spawn_future_local(async move {
                match net::bg(site::friend(action, uuid)).await {
                    Ok(_) if !done.is_empty() => app.toast(&done),
                    Ok(_) => {}
                    Err(e) => {
                        button.set_sensitive(true);
                        app.toast(&format!("Couldn't do that: {e}"));
                    }
                }
                refresh(&app, &p);
            });
        }
    ));
}

fn add_friend(app: &Rc<App>, p: &Rc<Page>) {
    let name = p.entry.text().trim().to_string();
    if name.is_empty() {
        return;
    }
    if !name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        say(p, "That isn't a Minecraft name.");
        return;
    }
    if site::token().is_none() {
        say(p, "Sign in first.");
        return;
    }
    p.add.set_sensitive(false);
    let (app, p) = (app.clone(), p.clone());
    glib::spawn_future_local(async move {
        let result = net::bg(async move {
            let found = site::lookup(name).await?;
            if !found.oppa {
                return anyhow::Ok(format!("{} doesn't use Oppa yet.", found.name));
            }
            if found.me {
                return Ok("That's you.".into());
            }
            if found.friend {
                return Ok(format!("You're already friends with {}.", found.name));
            }
            Ok(match site::friend("add", found.uuid).await?.as_str() {
                "friends" => format!("You and {} are now friends.", found.name),
                _ => format!("Sent {} a friend request.", found.name),
            })
        })
        .await;
        p.add.set_sensitive(true);
        match result {
            Ok(msg) => {
                p.entry.set_text("");
                say(&p, &msg);
            }
            Err(e) => say(&p, &e.to_string()),
        }
        refresh(&app, &p);
    });
}

fn join(app: &Rc<App>, server: &str, game: &str) {
    let pick = {
        let list = app.instances.borrow();
        let current = app.current();
        current
            .filter(|i| game.is_empty() || i.game_version == game)
            .or_else(|| list.iter().find(|i| i.game_version == game).cloned())
            .or_else(|| app.current())
    };
    let Some(inst) = pick else {
        app.toast("Make an instance first");
        return;
    };
    if app.current().map(|i| i.dir) != Some(inst.dir.clone()) {
        app.select(Some(inst.dir.clone()));
    }
    let hook = app.join.borrow().clone();
    if let Some(join) = hook {
        join(server.to_string());
    }
}
