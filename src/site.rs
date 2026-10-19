use std::sync::Mutex;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

use anyhow::bail;
use serde::Deserialize;
use serde_json::json;
use tokio::sync::{Notify, watch};

use crate::net;

const API: &str = "https://oppaclient.com/api";

fn api() -> String {
    #[cfg(debug_assertions)]
    if let Ok(over) = std::env::var("OPPA_API") {
        return over;
    }
    API.to_string()
}

#[derive(Deserialize, Clone)]
pub struct Latest {
    pub version: Option<String>,
    #[serde(default)]
    pub notes: String,
    #[serde(default)]
    pub notice: String,
}

pub async fn latest() -> anyhow::Result<Latest> {
    net::get_json(&format!("{}/latest.php", api())).await
}

#[derive(Clone, PartialEq)]
struct Presence {
    status: &'static str,
    server: String,
    game: String,
}

static PRESENCE: Mutex<Presence> = Mutex::new(Presence { status: "launcher", server: String::new(), game: String::new() });
static GENERATION: AtomicU64 = AtomicU64::new(0);

fn changed() -> &'static Notify {
    static N: std::sync::OnceLock<Notify> = std::sync::OnceLock::new();
    N.get_or_init(Notify::new)
}

type Session = (watch::Sender<Option<String>>, watch::Receiver<Option<String>>);

fn session() -> &'static Session {
    static S: std::sync::OnceLock<Session> = std::sync::OnceLock::new();
    S.get_or_init(|| watch::channel(None))
}

pub fn token() -> Option<String> {
    session().1.borrow().clone()
}

pub async fn token_changed() {
    let mut rx = session().1.clone();
    let _ = rx.changed().await;
}

pub async fn wait_token(limit: Duration) -> Option<String> {
    let mut rx = session().1.clone();
    let _ = tokio::time::timeout(limit, rx.wait_for(Option::is_some)).await;
    token()
}

pub fn set_status(status: &'static str, server: &str, game: &str) {
    let next = Presence { status, server: server.to_string(), game: game.to_string() };
    let mut p = PRESENCE.lock().unwrap();
    if *p != next {
        *p = next;
        changed().notify_one();
    }
}

pub fn from_log(line: &str, share_server: bool) {
    let Some(at) = line.find("oppa-presence ") else { return };
    let mut parts = line[at + "oppa-presence ".len()..].trim().splitn(3, ' ');
    let (Some(game), Some(kind)) = (parts.next(), parts.next()) else { return };
    let server = parts.next().unwrap_or("").trim();
    match kind {
        "menu" => set_status("menu", "", game),
        "singleplayer" => set_status("singleplayer", "", game),
        "server" if share_server && !server.is_empty() => set_status("server", server, game),
        _ => set_status("multiplayer", "", game),
    }
}

static SIGNED_IN: Mutex<String> = Mutex::new(String::new());

pub fn start(acc: Option<crate::auth::Account>) {
    let uuid = acc.as_ref().map(|a| a.uuid.clone()).unwrap_or_default();
    {
        let mut signed = SIGNED_IN.lock().unwrap();
        if !uuid.is_empty() && *signed == uuid && token().is_some() {
            return;
        }
        *signed = uuid;
    }
    let generation = GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    session().0.send_replace(None);
    let Some(acc) = acc else { return };
    net::rt().spawn(async move {
        let current = || GENERATION.load(Ordering::SeqCst) == generation;
        let mut wait = 30;
        let token = loop {
            if !current() {
                return;
            }
            match hello(&acc.name, &acc.uuid, &acc.mc_token).await {
                Ok(t) => break t,
                Err(_) => {
                    tokio::time::sleep(Duration::from_secs(wait)).await;
                    wait = (wait * 2).min(600);
                }
            }
        };
        if !current() {
            return;
        }
        session().0.send_replace(Some(token.clone()));
        loop {
            let p = PRESENCE.lock().unwrap().clone();
            let _ = post::<serde_json::Value>("ping.php", json!({ "token": token, "status": p.status, "server": p.server, "game": p.game })).await;
            tokio::select! {
                _ = tokio::time::sleep(Duration::from_secs(60)) => {}
                _ = changed().notified() => {}
            }
            if !current() {
                return;
            }
        }
    });
}

pub async fn sign_off() {
    if let Some(token) = token() {
        let _ = post::<serde_json::Value>("ping.php", json!({ "token": token, "status": "offline" })).await;
    }
}

async fn post<T: serde::de::DeserializeOwned>(file: &str, body: serde_json::Value) -> anyhow::Result<T> {
    let mut req = net::http().post(format!("{}/{file}", api())).json(&body).timeout(Duration::from_secs(15));
    if let Some(t) = token() {
        req = req.header("X-Oppa-Token", t);
    }
    let resp = req.send().await?;
    let status = resp.status();
    let value: serde_json::Value = resp.json().await.unwrap_or_default();
    if !status.is_success() {
        match value["error"].as_str() {
            Some(e) => bail!("{e}"),
            None => bail!("{file} returned {status}"),
        }
    }
    Ok(serde_json::from_value(value)?)
}

fn system() -> &'static str {
    if std::path::Path::new("/.flatpak-info").exists() { "flatpak" } else { std::env::consts::OS }
}

pub async fn session_for(acc: &crate::auth::Account) -> anyhow::Result<String> {
    hello(&acc.name, &acc.uuid, &acc.mc_token).await
}

async fn hello(name: &str, uuid: &str, mc_token: &str) -> anyhow::Result<String> {
    #[derive(Deserialize)]
    struct Nonce {
        nonce: String,
    }
    #[derive(Deserialize)]
    struct Token {
        token: String,
    }
    let Nonce { nonce } = post("hello.php", json!({ "name": name })).await?;
    let joined = net::http()
        .post("https://sessionserver.mojang.com/session/minecraft/join")
        .json(&json!({ "accessToken": mc_token, "selectedProfile": uuid, "serverId": nonce }))
        .send()
        .await?;
    if !joined.status().is_success() {
        bail!("session join returned {}", joined.status());
    }
    let Token { token } = post(
        "join.php",
        json!({ "name": name, "nonce": nonce, "os": system(), "version": env!("CARGO_PKG_VERSION"), "launcher": true }),
    )
    .await?;
    Ok(token)
}

#[derive(Deserialize, Clone, Default)]
pub struct Friend {
    pub uuid: String,
    pub name: String,
    pub status: String,
    #[serde(default)]
    pub server: String,
    #[serde(default)]
    pub game: String,
    #[serde(default)]
    pub seen: i64,
}

#[derive(Deserialize, Clone)]
pub struct Person {
    pub uuid: String,
    pub name: String,
}

#[derive(Deserialize, Clone)]
pub struct Invite {
    pub id: u64,
    pub name: String,
    pub server: String,
    #[serde(default)]
    pub game: String,
}

#[derive(Deserialize, Clone, Default)]
pub struct Social {
    #[serde(default)]
    pub friends: Vec<Friend>,
    #[serde(default)]
    pub incoming: Vec<Person>,
    #[serde(default)]
    pub outgoing: Vec<Person>,
    #[serde(default)]
    pub invites: Vec<Invite>,
}

pub async fn social() -> anyhow::Result<Social> {
    post("me.php", json!({})).await
}

#[derive(Deserialize)]
pub struct Found {
    pub oppa: bool,
    #[serde(default)]
    pub uuid: String,
    pub name: String,
    #[serde(default)]
    pub me: bool,
    #[serde(default)]
    pub friend: bool,
}

pub async fn lookup(name: String) -> anyhow::Result<Found> {
    post("lookup.php", json!({ "name": name })).await
}

pub async fn friend(action: &'static str, uuid: String) -> anyhow::Result<String> {
    let v: serde_json::Value = post("friend.php", json!({ "action": action, "uuid": uuid })).await?;
    Ok(v["state"].as_str().unwrap_or_default().to_string())
}

const SHOT_LIMIT: usize = 7 * 1024 * 1024;

fn fit_shot(data: Vec<u8>) -> anyhow::Result<(Vec<u8>, &'static str)> {
    let png = data.starts_with(b"\x89PNG");
    if data.len() <= SHOT_LIMIT {
        return Ok((data, if png { "image/png" } else { "image/jpeg" }));
    }
    let mut img = image::load_from_memory(&data)?;
    if img.width() > 2560 {
        img = img.resize(2560, 2560, image::imageops::FilterType::Triangle);
    }
    let mut out = Vec::new();
    image::codecs::jpeg::JpegEncoder::new_with_quality(&mut out, 90).encode_image(&img.to_rgb8())?;
    Ok((out, "image/jpeg"))
}

pub async fn upload_shot(path: std::path::PathBuf) -> anyhow::Result<String> {
    let Some(token) = token() else { bail!("Oppa isn't signed in yet") };
    let data = tokio::fs::read(&path).await?;
    let (data, kind) = tokio::task::spawn_blocking(move || fit_shot(data)).await??;
    let resp = net::http()
        .post(format!("{}/shot.php", api()))
        .header("X-Oppa-Token", token)
        .header("Content-Type", kind)
        .body(data)
        .timeout(Duration::from_secs(120))
        .send()
        .await?;
    let status = resp.status();
    let v: serde_json::Value = resp.json().await.unwrap_or_default();
    match v["url"].as_str() {
        Some(url) if status.is_success() => Ok(url.to_string()),
        _ => bail!("{}", v["error"].as_str().unwrap_or("upload failed")),
    }
}

pub async fn send_crash(payload: serde_json::Value) -> anyhow::Result<()> {
    post::<serde_json::Value>("crash.php", payload).await?;
    Ok(())
}
