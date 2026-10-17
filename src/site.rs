use std::time::Duration;

use anyhow::bail;
use serde::Deserialize;
use serde_json::json;

use crate::net;

const API: &str = "https://oppaclient.com/api";

#[derive(Deserialize, Clone)]
pub struct Latest {
    pub version: Option<String>,
    #[serde(default)]
    pub notes: String,
    #[serde(default)]
    pub notice: String,
}

pub async fn latest() -> anyhow::Result<Latest> {
    net::get_json(&format!("{API}/latest.php")).await
}

async fn post<T: serde::de::DeserializeOwned>(file: &str, body: serde_json::Value) -> anyhow::Result<T> {
    let resp = net::http().post(format!("{API}/{file}")).json(&body).timeout(Duration::from_secs(15)).send().await?;
    if !resp.status().is_success() {
        bail!("{file} returned {}", resp.status());
    }
    Ok(resp.json().await?)
}

fn system() -> &'static str {
    if std::path::Path::new("/.flatpak-info").exists() { "flatpak" } else { std::env::consts::OS }
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
        json!({ "name": name, "nonce": nonce, "os": system(), "version": env!("CARGO_PKG_VERSION") }),
    )
    .await?;
    Ok(token)
}

async fn ping(token: &str, left: bool) -> anyhow::Result<()> {
    post::<serde_json::Value>("ping.php", json!({ "token": token, "left": left })).await?;
    Ok(())
}

pub async fn presence(name: String, uuid: String, mc_token: String, mut done: tokio::sync::watch::Receiver<bool>) {
    let token = loop {
        match hello(&name, &uuid, &mc_token).await {
            Ok(t) => break t,
            Err(_) => {
                tokio::select! {
                    _ = tokio::time::sleep(Duration::from_secs(120)) => {}
                    _ = done.changed() => return,
                }
            }
        }
    };
    loop {
        tokio::select! {
            _ = tokio::time::sleep(Duration::from_secs(60)) => {
                let _ = ping(&token, false).await;
            }
            _ = done.changed() => {
                let _ = ping(&token, true).await;
                return;
            }
        }
    }
}
