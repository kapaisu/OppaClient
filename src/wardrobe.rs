use std::path::PathBuf;

use anyhow::{Context, bail};
use serde::{Deserialize, Serialize};
use serde_json::json;
use sha1::{Digest, Sha1};

use crate::auth::{self, Account};
use crate::instance::{data_dir, now};
use crate::net::{self, http};

const PROFILE: &str = "https://api.minecraftservices.com/minecraft/profile";

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum Variant {
    #[default]
    Classic,
    Slim,
}

impl Variant {
    fn api(self) -> &'static str {
        match self {
            Variant::Classic => "classic",
            Variant::Slim => "slim",
        }
    }
}

#[derive(Clone, Debug, Deserialize)]
pub struct Profile {
    pub name: String,
    #[serde(default)]
    pub skins: Vec<RemoteSkin>,
    #[serde(default)]
    pub capes: Vec<Cape>,
}

#[derive(Clone, Debug, Deserialize)]
pub struct RemoteSkin {
    pub state: String,
    pub url: String,
    #[serde(default)]
    pub variant: Option<String>,
}

#[derive(Clone, Debug, Deserialize)]
pub struct Cape {
    pub id: String,
    pub state: String,
    pub url: String,
    #[serde(default)]
    pub alias: Option<String>,
}

impl Profile {
    pub fn active_skin(&self) -> Option<&RemoteSkin> {
        self.skins.iter().find(|s| s.state == "ACTIVE")
    }

    pub fn active_cape(&self) -> Option<&Cape> {
        self.capes.iter().find(|c| c.state == "ACTIVE")
    }

    pub fn variant(&self) -> Variant {
        match self.active_skin().and_then(|s| s.variant.as_deref()) {
            Some(v) if v.eq_ignore_ascii_case("slim") => Variant::Slim,
            _ => Variant::Classic,
        }
    }
}

impl Cape {
    pub fn name(&self) -> String {
        self.alias
            .clone()
            .unwrap_or_else(|| "Cape".into())
            .replace('_', " ")
            .split(' ')
            .map(|w| {
                let mut c = w.chars();
                c.next().map(|f| f.to_uppercase().chain(c.flat_map(char::to_lowercase)).collect()).unwrap_or_default()
            })
            .collect::<Vec<String>>()
            .join(" ")
    }
}

async fn parse(resp: reqwest::Response, what: &str) -> anyhow::Result<Profile> {
    let status = resp.status();
    if status.as_u16() == 429 {
        bail!("Minecraft is rate limiting skin changes. Wait a minute and try again.");
    }
    if !status.is_success() {
        let body: serde_json::Value = resp.json().await.unwrap_or_default();
        let msg = body["errorMessage"].as_str().or(body["error"].as_str()).unwrap_or("");
        if msg.is_empty() {
            bail!("Couldn't {what} ({status})");
        }
        bail!("Couldn't {what}: {msg}");
    }
    Ok(resp.json().await?)
}

async fn token(acc: &Account) -> anyhow::Result<Account> {
    auth::fresh(acc).await
}

pub async fn profile(acc: &Account) -> anyhow::Result<(Account, Profile)> {
    let acc = token(acc).await?;
    let resp = http().get(PROFILE).bearer_auth(&acc.mc_token).send().await?;
    let p = parse(resp, "read your profile").await?;
    Ok((acc, p))
}

pub async fn upload(acc: &Account, png: Vec<u8>, variant: Variant) -> anyhow::Result<(Account, Profile)> {
    check_skin(&png)?;
    let acc = token(acc).await?;
    let part = reqwest::multipart::Part::bytes(png).file_name("skin.png").mime_str("image/png")?;
    let form = reqwest::multipart::Form::new().text("variant", variant.api()).part("file", part);
    let resp = http().post(format!("{PROFILE}/skins")).bearer_auth(&acc.mc_token).multipart(form).send().await?;
    let p = parse(resp, "change your skin").await?;
    Ok((acc, p))
}

pub async fn reset(acc: &Account) -> anyhow::Result<(Account, Profile)> {
    let acc = token(acc).await?;
    let resp = http().delete(format!("{PROFILE}/skins/active")).bearer_auth(&acc.mc_token).send().await?;
    let p = parse(resp, "reset your skin").await?;
    Ok((acc, p))
}

pub async fn cape(acc: &Account, id: Option<&str>) -> anyhow::Result<(Account, Profile)> {
    let acc = token(acc).await?;
    let req = match id {
        Some(id) => http().put(format!("{PROFILE}/capes/active")).json(&json!({ "capeId": id })),
        None => http().delete(format!("{PROFILE}/capes/active")),
    };
    let resp = req.bearer_auth(&acc.mc_token).send().await?;
    let p = parse(resp, "change your cape").await?;
    Ok((acc, p))
}

pub fn check_skin(png: &[u8]) -> anyhow::Result<()> {
    let img = image::load_from_memory(png).context("That file isn't an image Minecraft can use.")?;
    match (img.width(), img.height()) {
        (64, 64) | (64, 32) => Ok(()),
        (w, h) => bail!("Skins are 64×64 pixels. This one is {w}×{h}."),
    }
}

pub async fn texture(url: &str) -> anyhow::Result<Vec<u8>> {
    let dir = crate::instance::cache_dir().join("textures");
    let name = hex::encode(Sha1::digest(url.as_bytes()));
    let path = dir.join(name);
    if let Ok(b) = tokio::fs::read(&path).await {
        return Ok(b);
    }
    let bytes = net::get_bytes(url).await?.to_vec();
    tokio::fs::create_dir_all(&dir).await?;
    tokio::fs::write(&path, &bytes).await?;
    Ok(bytes)
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Saved {
    pub file: String,
    pub variant: Variant,
    pub added: i64,
}

pub fn library_dir() -> PathBuf {
    data_dir().join("skins")
}

pub fn library() -> Vec<Saved> {
    let mut list: Vec<Saved> = std::fs::read(library_dir().join("library.json"))
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or_default();
    list.retain(|s| library_dir().join(&s.file).exists());
    list.sort_by_key(|s| std::cmp::Reverse(s.added));
    list
}

fn save_library(list: &[Saved]) -> anyhow::Result<()> {
    std::fs::create_dir_all(library_dir())?;
    std::fs::write(library_dir().join("library.json"), serde_json::to_vec_pretty(list)?)?;
    Ok(())
}

pub fn saved_path(s: &Saved) -> PathBuf {
    library_dir().join(&s.file)
}

pub fn remember(png: &[u8], variant: Variant) -> anyhow::Result<()> {
    let file = format!("{}.png", hex::encode(Sha1::digest(png)));
    std::fs::create_dir_all(library_dir())?;
    let path = library_dir().join(&file);
    if !path.exists() {
        std::fs::write(&path, png)?;
    }
    let mut list = library();
    list.retain(|s| s.file != file);
    list.push(Saved { file, variant, added: now() });
    save_library(&list)
}

pub fn forget(s: &Saved) -> anyhow::Result<()> {
    let mut list = library();
    list.retain(|x| x.file != s.file);
    save_library(&list)?;
    let _ = std::fs::remove_file(saved_path(s));
    Ok(())
}
