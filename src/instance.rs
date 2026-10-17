use std::collections::BTreeMap;
use std::path::PathBuf;

use gtk::gio::prelude::*;

use serde::{Deserialize, Serialize};

pub fn data_dir() -> PathBuf {
    dirs::data_dir().expect("XDG data dir").join("oppa")
}

pub fn cache_dir() -> PathBuf {
    dirs::cache_dir().expect("XDG cache dir").join("oppa")
}

pub fn now() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Loader {
    Vanilla,
    Fabric,
    Quilt,
}

impl Loader {
    pub fn label(self) -> &'static str {
        match self {
            Loader::Vanilla => "Vanilla",
            Loader::Fabric => "Fabric",
            Loader::Quilt => "Quilt",
        }
    }

    pub fn modrinth(self) -> Option<&'static str> {
        match self {
            Loader::Vanilla => None,
            Loader::Fabric => Some("fabric"),
            Loader::Quilt => Some("quilt"),
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Session {
    pub start: i64,
    pub secs: u64,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Instance {
    #[serde(skip)]
    pub dir: String,
    pub name: String,
    pub game_version: String,
    pub loader: Loader,
    #[serde(default)]
    pub loader_version: Option<String>,
    #[serde(default = "default_memory")]
    pub memory_mb: u32,
    #[serde(default)]
    pub java: Option<PathBuf>,
    #[serde(default)]
    pub jvm_args: String,
    #[serde(default = "yes")]
    pub performance: bool,
    #[serde(default)]
    pub played_secs: u64,
    #[serde(default)]
    pub sessions: Vec<Session>,
    pub created: i64,
}

fn yes() -> bool {
    true
}

fn default_memory() -> u32 {
    4096
}

impl Instance {
    pub fn new(name: &str, game_version: &str, loader: Loader, loader_version: Option<String>) -> Self {
        Instance {
            dir: String::new(),
            name: name.trim().to_string(),
            game_version: game_version.to_string(),
            loader,
            loader_version,
            memory_mb: default_memory(),
            java: None,
            jvm_args: String::new(),
            performance: true,
            played_secs: 0,
            sessions: Vec::new(),
            created: now(),
        }
    }

    pub fn root(&self) -> PathBuf {
        data_dir().join("instances").join(&self.dir)
    }

    pub fn game_dir(&self) -> PathBuf {
        self.root().join("minecraft")
    }

    pub fn last_played(&self) -> Option<i64> {
        self.sessions.last().map(|s| s.start + s.secs as i64)
    }

    pub fn record_session(&mut self, start: i64, secs: u64) {
        self.played_secs += secs;
        self.sessions.push(Session { start, secs });
        let cutoff = now() - 120 * 86400;
        self.sessions.retain(|s| s.start >= cutoff);
    }

    pub fn save(&mut self) -> anyhow::Result<()> {
        if self.dir.is_empty() {
            self.dir = free_dir_name(&self.name);
        }
        std::fs::create_dir_all(self.game_dir())?;
        let json = serde_json::to_vec_pretty(self)?;
        let path = self.root().join("instance.json");
        let tmp = path.with_extension("json.tmp");
        std::fs::write(&tmp, json)?;
        std::fs::rename(tmp, path)?;
        Ok(())
    }

    pub fn installed(&self) -> Installed {
        std::fs::read(self.root().join("installed.json"))
            .ok()
            .and_then(|b| serde_json::from_slice(&b).ok())
            .unwrap_or_default()
    }

    pub fn save_installed(&self, installed: &Installed) -> anyhow::Result<()> {
        std::fs::write(self.root().join("installed.json"), serde_json::to_vec_pretty(installed)?)?;
        Ok(())
    }
}

pub type Installed = BTreeMap<String, InstalledFile>;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct InstalledFile {
    pub folder: String,
    pub file: String,
    pub version_id: String,
}

fn free_dir_name(name: &str) -> String {
    let mut slug: String = name
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() { c.to_ascii_lowercase() } else { '-' })
        .collect();
    while slug.contains("--") {
        slug = slug.replace("--", "-");
    }
    let slug = slug.trim_matches('-');
    let base = if slug.is_empty() { "instance" } else { slug };
    let parent = data_dir().join("instances");
    let mut candidate = base.to_string();
    let mut n = 2;
    while parent.join(&candidate).exists() {
        candidate = format!("{base}-{n}");
        n += 1;
    }
    candidate
}

pub fn load_all() -> Vec<Instance> {
    let mut out = Vec::new();
    let Ok(entries) = std::fs::read_dir(data_dir().join("instances")) else { return out };
    for entry in entries.flatten() {
        let Ok(bytes) = std::fs::read(entry.path().join("instance.json")) else { continue };
        match serde_json::from_slice::<Instance>(&bytes) {
            Ok(mut inst) => {
                inst.dir = entry.file_name().to_string_lossy().into_owned();
                out.push(inst);
            }
            Err(e) => eprintln!("skipping {}: {e}", entry.path().display()),
        }
    }
    out.sort_by_key(|i| std::cmp::Reverse(i.last_played().unwrap_or(i.created)));
    out
}

pub fn delete(inst: &Instance) -> anyhow::Result<()> {
    let root = inst.root();
    gtk::gio::File::for_path(&root).trash(gtk::gio::Cancellable::NONE)?;
    Ok(())
}

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Place {
    Mods,
    ResourcePacks,
    Shaders,
    Schematics,
    Saves,
    Screenshots,
    Config,
    Logs,
}

impl Place {
    pub const ALL: [Place; 8] = [
        Place::Mods,
        Place::ResourcePacks,
        Place::Shaders,
        Place::Schematics,
        Place::Saves,
        Place::Screenshots,
        Place::Config,
        Place::Logs,
    ];

    pub fn folder(self) -> &'static str {
        match self {
            Place::Mods => "mods",
            Place::ResourcePacks => "resourcepacks",
            Place::Shaders => "shaderpacks",
            Place::Schematics => "schematics",
            Place::Saves => "saves",
            Place::Screenshots => "screenshots",
            Place::Config => "config",
            Place::Logs => "logs",
        }
    }

    pub fn title(self) -> &'static str {
        match self {
            Place::Mods => "Mods",
            Place::ResourcePacks => "Resource packs",
            Place::Shaders => "Shaders",
            Place::Schematics => "Schematics",
            Place::Saves => "Worlds",
            Place::Screenshots => "Screenshots",
            Place::Config => "Config",
            Place::Logs => "Logs",
        }
    }
}
