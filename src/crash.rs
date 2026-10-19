use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::time::{Duration, UNIX_EPOCH};

use adw::prelude::*;
use gtk::{gio, glib};

use crate::instance::Instance;
use crate::net;
use crate::window::App;

pub struct Report {
    pub path: PathBuf,
    pub text: String,
    pub summary: String,
}

fn newer(path: &Path, since: i64) -> bool {
    std::fs::metadata(path)
        .and_then(|m| m.modified())
        .map(|t| t.duration_since(UNIX_EPOCH).unwrap_or(Duration::ZERO).as_secs() as i64 >= since - 5)
        .unwrap_or(false)
}

fn newest(dir: &Path, since: i64, keep: impl Fn(&str) -> bool) -> Option<PathBuf> {
    std::fs::read_dir(dir)
        .ok()?
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.file_name().is_some_and(|n| keep(&n.to_string_lossy())) && newer(p, since))
        .max_by_key(|p| std::fs::metadata(p).and_then(|m| m.modified()).ok())
}

fn tail(text: &str, lines: usize) -> String {
    let all: Vec<&str> = text.lines().collect();
    all[all.len().saturating_sub(lines)..].join("\n")
}

fn noise(line: &str) -> bool {
    let l = line.to_lowercase();
    ["narrator", "flite", "text2speech", "gl error", "invalid path", "classnotfoundexception", "error loading class"].iter().any(|n| l.contains(n))
}

fn summarize(text: &str) -> String {
    let description = text.lines().find_map(|l| l.strip_prefix("Description: ")).map(str::trim);
    let is_error = |l: &&str| {
        (l.contains("Exception") || l.contains("Error:") || l.starts_with("Caused by")) && !l.starts_with("at ") && !noise(l)
    };
    let error = if description.is_some() {
        text.lines().map(str::trim).find(is_error)
    } else {
        text.lines().rev().map(str::trim).find(is_error)
    };
    let mut out = match (description, error) {
        (Some(d), Some(e)) if !e.contains(d) => format!("{d} | {e}"),
        (Some(d), _) => d.to_string(),
        (None, Some(e)) => e.to_string(),
        (None, None) => "Minecraft closed with an error".to_string(),
    };
    if out.len() > 280 {
        let mut cut = 280;
        while !out.is_char_boundary(cut) {
            cut -= 1;
        }
        out.truncate(cut);
        out.push('…');
    }
    out
}

pub fn find(inst: &Instance, since: i64) -> Option<Report> {
    let game = inst.game_dir();
    let path = newest(&game.join("crash-reports"), since, |n| n.ends_with(".txt"))
        .or_else(|| newest(&game, since, |n| n.starts_with("hs_err_pid") && n.ends_with(".log")))
        .or_else(|| Some(game.join("logs/latest.log")).filter(|p| p.is_file()))?;
    let raw = std::fs::read(&path).ok()?;
    let mut text = String::from_utf8_lossy(&raw).into_owned();
    if path.ends_with("latest.log") {
        text = tail(&text, 400);
    }
    let text = text
        .lines()
        .map(|l| match l.find("oppa-token ") {
            Some(at) => format!("{}oppa-token [hidden]", &l[..at]),
            None => l.to_string(),
        })
        .collect::<Vec<_>>()
        .join("\n");
    let summary = summarize(&text);
    Some(Report { path, text, summary })
}

fn mods(inst: &Instance) -> Vec<String> {
    let mut out: Vec<String> = std::fs::read_dir(inst.game_dir().join("mods"))
        .map(|d| d.flatten().map(|e| e.file_name().to_string_lossy().into_owned()).filter(|n| n.contains(".jar")).collect())
        .unwrap_or_default();
    out.sort_by_key(|n| n.to_lowercase());
    out
}

pub fn show(app: &Rc<App>, inst: &Instance, since: i64) {
    let Some(report) = find(inst, since) else {
        app.toast("Minecraft closed with an error");
        return;
    };
    let body = format!(
        "<tt>{}</tt>\n\nSend the report to the Oppa team and we'll help you fix it on Discord.",
        glib::markup_escape_text(&report.summary)
    );
    let dialog = adw::AlertDialog::new(Some("Minecraft crashed"), Some(&body));
    dialog.set_body_use_markup(true);
    dialog.add_response("close", "Close");
    dialog.add_response("open", "Open Report");
    dialog.add_response("send", "Send to Support");
    dialog.set_response_appearance("send", adw::ResponseAppearance::Suggested);
    dialog.set_default_response(Some("send"));
    dialog.set_close_response("close");
    let report = Rc::new(report);
    let (a, r) = (app.clone(), report.clone());
    dialog.connect_response(Some("open"), move |_, _| {
        gtk::FileLauncher::new(Some(&gio::File::for_path(&r.path))).launch(Some(&a.window), gio::Cancellable::NONE, |_| {});
    });
    let (a, r, i) = (app.clone(), report.clone(), inst.clone());
    dialog.connect_response(Some("send"), move |_, _| ask(&a, &i, r.clone()));
    dialog.present(Some(&app.window));
}

fn valid_discord(name: &str) -> bool {
    let (user, tag) = match name.split_once('#') {
        Some((u, t)) => (u, Some(t)),
        None => (name, None),
    };
    (2..=32).contains(&user.len())
        && user.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '.')
        && tag.is_none_or(|t| t.len() == 4 && t.chars().all(|c| c.is_ascii_digit()))
}

fn ask(app: &Rc<App>, inst: &Instance, report: Rc<Report>) {
    let discord = adw::EntryRow::builder().title("Discord username").activates_default(true).build();
    let note = adw::EntryRow::builder().title("What were you doing? (optional)").activates_default(true).build();
    let list = gtk::ListBox::builder().selection_mode(gtk::SelectionMode::None).css_classes(["boxed-list"]).build();
    list.append(&discord);
    list.append(&note);
    let dialog = adw::AlertDialog::new(
        Some("Send to Support"),
        Some("We'll message you on Discord about this crash. This sends your game log, which can include your computer's username and the servers you joined."),
    );
    dialog.set_extra_child(Some(&list));
    dialog.add_response("cancel", "Cancel");
    dialog.add_response("send", "Send");
    dialog.set_response_appearance("send", adw::ResponseAppearance::Suggested);
    dialog.set_default_response(Some("send"));
    dialog.set_close_response("cancel");
    dialog.set_response_enabled("send", false);
    discord.connect_changed(glib::clone!(
        #[weak]
        dialog,
        move |row| dialog.set_response_enabled("send", valid_discord(row.text().trim()))
    ));
    let (owner, inst) = (app.clone(), inst.clone());
    dialog.connect_response(Some("send"), move |_, _| {
        let app = owner.clone();
        let payload = serde_json::json!({
            "discord": discord.text().trim(),
            "note": note.text().trim(),
            "report": report.text,
            "summary": report.summary,
            "game": inst.game_version,
            "loader": match &inst.loader_version {
                Some(v) => format!("{} {v}", inst.loader.label()),
                None => inst.loader.label().to_string(),
            },
            "mods": mods(&inst),
            "os": if Path::new("/.flatpak-info").exists() { "flatpak" } else { std::env::consts::OS },
            "launcher": env!("CARGO_PKG_VERSION"),
            "name": app.account.borrow().as_ref().map(|a| a.name.clone()).unwrap_or_default(),
        });
        glib::spawn_future_local(async move {
            match net::bg(crate::site::send_crash(payload)).await {
                Ok(()) => app.toast("Sent. We'll reach out on Discord."),
                Err(e) => app.toast(&format!("Couldn't send: {e}")),
            }
        });
    });
    dialog.present(Some(&app.window));
}
