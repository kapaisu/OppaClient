use std::io::{Read, Write};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Mutex, OnceLock};
use std::sync::mpsc::{Receiver, RecvTimeoutError, Sender, channel};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde_json::{Value, json};

const CLIENT_ID: &str = "1560907158831235082";
const LOGO: &str = "https://oppaclient.com/assets/img/icon-256.png";
const SITE: &str = "https://oppaclient.com";

#[derive(Clone, PartialEq)]
pub enum Status {
    Off,
    Launcher,
    Menu { game: String },
    Singleplayer { game: String },
    Multiplayer { game: String },
    Server { game: String, host: String },
}

fn sender() -> &'static Sender<Status> {
    static TX: OnceLock<Sender<Status>> = OnceLock::new();
    TX.get_or_init(|| {
        let (tx, rx) = channel();
        std::thread::Builder::new().name("discord".into()).spawn(move || run(rx)).expect("discord thread");
        tx
    })
}

static ENABLED: AtomicBool = AtomicBool::new(true);
static WANTED: Mutex<Status> = Mutex::new(Status::Off);

pub fn set(status: Status) {
    *WANTED.lock().unwrap() = status.clone();
    if ENABLED.load(Ordering::Relaxed) && !CLIENT_ID.is_empty() {
        let _ = sender().send(status);
    }
}

pub fn enable(on: bool) {
    ENABLED.store(on, Ordering::Relaxed);
    if !CLIENT_ID.is_empty() {
        let _ = sender().send(if on { WANTED.lock().unwrap().clone() } else { Status::Off });
    }
}

pub fn from_log(line: &str) -> Option<Status> {
    let rest = &line[line.find("oppa-presence ")? + "oppa-presence ".len()..];
    let mut parts = rest.trim().splitn(3, ' ');
    let game = parts.next()?.to_string();
    Some(match (parts.next()?, parts.next()) {
        ("menu", _) => Status::Menu { game },
        ("singleplayer", _) => Status::Singleplayer { game },
        ("server", Some(address)) => match public_host(address) {
            Some(host) => Status::Server { game, host },
            None => Status::Multiplayer { game },
        },
        _ => Status::Multiplayer { game },
    })
}

fn public_host(address: &str) -> Option<String> {
    let host = address.trim().trim_end_matches('.');
    let host = match host.rsplit_once(':') {
        Some((h, port)) if port.chars().all(|c| c.is_ascii_digit()) && !h.contains(':') => h,
        _ => host,
    };
    let host = host.to_ascii_lowercase();
    let private = host.parse::<std::net::IpAddr>().is_ok()
        || host.starts_with('[')
        || host == "localhost"
        || host.ends_with(".local")
        || !host.contains('.');
    (!private && host.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '-')).then_some(host)
}

fn now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

fn activity(status: &Status, since: u64) -> Value {
    let logo = json!({ "large_image": LOGO, "large_text": "Oppa" });
    let (details, state, assets) = match status {
        Status::Off => return Value::Null,
        Status::Launcher => ("In the launcher".to_string(), None, logo),
        Status::Menu { game } => ("In the menus".to_string(), Some(format!("Minecraft {game}")), logo),
        Status::Singleplayer { game } => ("Playing Singleplayer".to_string(), Some(format!("Minecraft {game}")), logo),
        Status::Multiplayer { game } => ("Playing Multiplayer".to_string(), Some(format!("Minecraft {game}")), logo),
        Status::Server { game, host } => (
            format!("Playing {host}"),
            Some(format!("Minecraft {game}")),
            json!({
                "large_image": format!("https://api.mcsrvstat.us/icon/{host}"),
                "large_text": host,
                "small_image": LOGO,
                "small_text": "Oppa",
            }),
        ),
    };
    let mut a = json!({
        "details": details,
        "timestamps": { "start": since },
        "assets": assets,
        "buttons": [{ "label": "Get Oppa", "url": SITE }],
    });
    if let Some(state) = state {
        a["state"] = json!(state);
    }
    a
}

fn run(rx: Receiver<Status>) {
    let mut status = Status::Off;
    let mut since = now();
    let mut pipe: Option<Pipe> = None;
    let mut dirty = false;
    loop {
        match rx.recv_timeout(Duration::from_secs(15)) {
            Ok(first) => {
                for next in std::iter::once(first).chain(rx.try_iter()) {
                    if next != status {
                        status = next;
                        since = now();
                        dirty = true;
                    }
                }
            }
            Err(RecvTimeoutError::Timeout) => dirty = status != Status::Off,
            Err(RecvTimeoutError::Disconnected) => return,
        }
        if !dirty {
            continue;
        }
        if status == Status::Off {
            pipe = None;
            dirty = false;
            continue;
        }
        if pipe.is_none() {
            pipe = Pipe::connect();
        }
        if let Some(p) = pipe.as_mut() {
            let body = json!({
                "cmd": "SET_ACTIVITY",
                "args": { "pid": std::process::id(), "activity": activity(&status, since) },
                "nonce": since.to_string(),
            });
            if p.send(1, &body).and_then(|_| p.recv()).is_ok() {
                dirty = false;
            } else {
                pipe = None;
            }
        }
    }
}

#[cfg(unix)]
type Stream = std::os::unix::net::UnixStream;
#[cfg(windows)]
type Stream = std::fs::File;

struct Pipe {
    stream: Stream,
}

impl Pipe {
    #[cfg(unix)]
    fn candidates() -> Vec<std::path::PathBuf> {
        let mut bases: Vec<std::path::PathBuf> = ["XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"]
            .iter()
            .filter_map(|k| std::env::var_os(k).map(Into::into))
            .collect();
        if let Ok(meta) = std::fs::metadata("/proc/self") {
            use std::os::unix::fs::MetadataExt;
            bases.push(format!("/run/user/{}", meta.uid()).into());
        }
        bases.push("/tmp".into());
        let mut out = Vec::new();
        for base in bases {
            for sub in ["", "app/com.discordapp.Discord", "app/com.discordapp.DiscordCanary", "app/dev.vencord.Vesktop", "snap.discord"] {
                for i in 0..10 {
                    out.push(base.join(sub).join(format!("discord-ipc-{i}")));
                }
            }
        }
        out
    }

    #[cfg(unix)]
    fn open() -> Option<Stream> {
        Self::candidates().into_iter().find_map(|p| Stream::connect(p).ok())
    }

    #[cfg(windows)]
    fn open() -> Option<Stream> {
        (0..10).find_map(|i| {
            std::fs::OpenOptions::new().read(true).write(true).open(format!(r"\\?\pipe\discord-ipc-{i}")).ok()
        })
    }

    fn connect() -> Option<Pipe> {
        let stream = Self::open()?;
        #[cfg(unix)]
        let _ = stream.set_read_timeout(Some(Duration::from_secs(5)));
        let mut p = Pipe { stream };
        p.send(0, &json!({ "v": 1, "client_id": CLIENT_ID })).ok()?;
        let ready = p.recv().ok()?;
        (ready["evt"] == "READY").then_some(p)
    }

    fn send(&mut self, op: u32, body: &Value) -> std::io::Result<()> {
        let data = serde_json::to_vec(body)?;
        let mut frame = Vec::with_capacity(8 + data.len());
        frame.extend_from_slice(&op.to_le_bytes());
        frame.extend_from_slice(&(data.len() as u32).to_le_bytes());
        frame.extend_from_slice(&data);
        self.stream.write_all(&frame)?;
        self.stream.flush()
    }

    fn recv(&mut self) -> std::io::Result<Value> {
        let mut head = [0u8; 8];
        self.stream.read_exact(&mut head)?;
        let len = u32::from_le_bytes([head[4], head[5], head[6], head[7]]) as usize;
        let mut data = vec![0u8; len.min(1 << 20)];
        self.stream.read_exact(&mut data)?;
        let v: Value = serde_json::from_slice(&data)?;
        if v["evt"] == "ERROR" {
            return Err(std::io::Error::other(v["data"]["message"].as_str().unwrap_or("discord error").to_string()));
        }
        Ok(v)
    }
}
