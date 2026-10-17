use std::path::Path;
use std::rc::Rc;

use adw::prelude::*;
use gtk::glib;

use crate::net::{self, Job};
use crate::site::{self, Latest};
use crate::window::App;

#[derive(Clone, Copy, PartialEq)]
enum Channel {
    Windows,
    Pacman,
    Flatpak,
    Other,
}

fn channel() -> Channel {
    if cfg!(windows) {
        return Channel::Windows;
    }
    if Path::new("/.flatpak-info").exists() {
        return Channel::Flatpak;
    }
    let exe = std::env::current_exe().unwrap_or_default();
    if exe.starts_with("/usr/bin") && Path::new("/usr/bin/pacman").exists() {
        return Channel::Pacman;
    }
    Channel::Other
}

fn parts(v: &str) -> Vec<u32> {
    v.split('.').map(|p| p.parse().unwrap_or(0)).collect()
}

fn newer(theirs: &str) -> bool {
    let p = parts(theirs);
    p.len() == 3 && theirs.bytes().all(|b| b.is_ascii_digit() || b == b'.') && p > parts(env!("CARGO_PKG_VERSION"))
}

pub fn banner(app: &Rc<App>) -> adw::Banner {
    let banner = adw::Banner::new("");
    let app = app.clone();
    let b = banner.clone();
    glib::spawn_future_local(async move {
        let Ok(latest) = net::bg(site::latest()).await else { return };
        match latest.version.clone().filter(|v| newer(v)) {
            Some(v) => offer(&app, &b, latest, v),
            None if !latest.notice.is_empty() => {
                b.set_title(&glib::markup_escape_text(&latest.notice));
                b.set_revealed(true);
            }
            None => {}
        }
    });
    banner
}

fn offer(app: &Rc<App>, banner: &adw::Banner, latest: Latest, version: String) {
    let ch = channel();
    banner.set_title(&glib::markup_escape_text(&format!("Oppa {version} is out")));
    banner.set_button_label(Some(match ch {
        Channel::Flatpak => "Open Software",
        Channel::Other => "Download",
        _ => "Update",
    }));
    banner.set_revealed(true);
    let app = app.clone();
    banner.connect_button_clicked(move |banner| match ch {
        Channel::Flatpak => open(&app, "appstream://dev.oppa.Launcher"),
        Channel::Other => open(&app, "https://oppaclient.com/download/"),
        _ => confirm(&app, banner, &latest, &version, ch),
    });
}

fn open(app: &Rc<App>, uri: &str) {
    gtk::UriLauncher::new(uri).launch(Some(&app.window), gtk::gio::Cancellable::NONE, |_| {});
}

fn confirm(app: &Rc<App>, banner: &adw::Banner, latest: &Latest, version: &str, ch: Channel) {
    let notes = if latest.notes.is_empty() { None } else { Some(latest.notes.as_str()) };
    let dialog = adw::AlertDialog::new(Some(&format!("Update to {version}")), notes);
    dialog.add_response("cancel", "Not Now");
    dialog.add_response("update", "Update");
    dialog.set_response_appearance("update", adw::ResponseAppearance::Suggested);
    dialog.set_default_response(Some("update"));
    let url = format!("https://github.com/kapaisu/OppaClient/releases/download/v{version}/Oppa-Setup.exe");
    let (app2, banner) = (app.clone(), banner.clone());
    dialog.connect_response(Some("update"), move |_, _| {
        banner.set_revealed(false);
        let (app, url) = (app2.clone(), url.clone());
        glib::spawn_future_local(async move {
            let result = match ch {
                Channel::Windows => net::bg(windows(url)).await,
                _ => net::bg(pacman()).await,
            };
            match result {
                Ok(()) if ch == Channel::Windows => app.window.close(),
                Ok(()) => restart_prompt(&app),
                Err(e) => failed(&app, ch, &format!("{e:#}")),
            }
        });
    });
    dialog.present(Some(&app.window));
}

async fn windows(url: String) -> anyhow::Result<()> {
    let job = Job::begin("Downloading update");
    let dest = std::env::temp_dir().join("Oppa-Setup.exe");
    let _ = tokio::fs::remove_file(&dest).await;
    net::fetch(net::Fetch { url, dest: dest.clone(), sha1: None, size: 0 }).await?;
    drop(job);
    std::process::Command::new(dest).args(["/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CLOSEAPPLICATIONS"]).spawn()?;
    Ok(())
}

async fn pacman() -> anyhow::Result<()> {
    let _job = Job::begin("Updating Oppa");
    let status = tokio::process::Command::new("pkexec").args(["pacman", "-Sy", "--noconfirm", "oppa"]).status().await?;
    if !status.success() {
        anyhow::bail!("pacman exited with {}", status.code().unwrap_or(-1));
    }
    Ok(())
}

fn restart_prompt(app: &Rc<App>) {
    let dialog = adw::AlertDialog::new(Some("Oppa is up to date"), Some("Restart Oppa to use the new version."));
    dialog.add_response("later", "Later");
    dialog.add_response("restart", "Restart");
    dialog.set_response_appearance("restart", adw::ResponseAppearance::Suggested);
    let app2 = app.clone();
    dialog.connect_response(Some("restart"), move |_, _| {
        if let Ok(exe) = std::env::current_exe()
            && std::process::Command::new(exe).spawn().is_ok()
        {
            app2.window.close();
        }
    });
    dialog.present(Some(&app.window));
}

fn failed(app: &Rc<App>, ch: Channel, msg: &str) {
    if ch == Channel::Pacman {
        let cmd = "sudo pacman -Sy oppa";
        let dialog = adw::AlertDialog::new(Some("Couldn't update"), Some(&format!("Run this in a terminal instead\n\n<tt>{cmd}</tt>")));
        dialog.set_body_use_markup(true);
        dialog.add_response("ok", "OK");
        dialog.add_response("copy", "Copy Command");
        dialog.connect_response(Some("copy"), move |d, _| d.clipboard().set_text(cmd));
        dialog.present(Some(&app.window));
    } else {
        app.toast(&format!("Update failed: {msg}"));
    }
}
