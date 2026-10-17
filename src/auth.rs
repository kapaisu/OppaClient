use std::collections::HashMap;
use std::path::PathBuf;

use anyhow::{Context, bail};
use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::instance::{data_dir, now};
use crate::net::http;

const CLIENT_ID: &str = "00000000402b5328";
const SCOPE: &str = "service::user.auth.xboxlive.com::MBI_SSL";

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Account {
    pub uuid: String,
    pub name: String,
    pub xuid: String,
    pub mc_token: String,
    pub mc_expires: i64,
    pub ms_refresh: String,
    #[serde(default)]
    pub skin_url: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct DeviceCode {
    pub user_code: String,
    pub device_code: String,
    pub verification_uri: String,
    pub interval: u64,
    pub expires_in: u64,
}

pub async fn start_device_login() -> anyhow::Result<DeviceCode> {
    let resp = http()
        .post("https://login.live.com/oauth20_connect.srf")
        .form(&[("client_id", CLIENT_ID), ("scope", SCOPE), ("response_type", "device_code")])
        .send()
        .await?;
    if !resp.status().is_success() {
        bail!("Microsoft refused the sign-in request ({})", resp.status());
    }
    Ok(resp.json().await?)
}

const REDIRECT: &str = "https://login.live.com/oauth20_desktop.srf";

pub fn authorize_url() -> String {
    reqwest::Url::parse_with_params(
        "https://login.live.com/oauth20_authorize.srf",
        &[
            ("client_id", CLIENT_ID),
            ("response_type", "code"),
            ("scope", SCOPE),
            ("redirect_uri", REDIRECT),
            ("prompt", "select_account"),
        ],
    )
    .expect("static url")
    .to_string()
}

pub fn redirect_result(uri: &str) -> Result<Option<String>, String> {
    if !uri.starts_with(REDIRECT) {
        return Ok(None);
    }
    let Ok(url) = reqwest::Url::parse(uri) else { return Ok(None) };
    let mut code = None;
    let mut error = None;
    for (k, v) in url.query_pairs() {
        match k.as_ref() {
            "code" => code = Some(v.into_owned()),
            "error_description" => error = Some(v.into_owned()),
            "error" if error.is_none() => error = Some(v.into_owned()),
            _ => {}
        }
    }
    match (code, error) {
        (Some(c), _) => Ok(Some(c)),
        (None, Some(e)) => Err(e),
        (None, None) => Err("Microsoft didn't return a sign-in code.".into()),
    }
}

pub async fn finish_browser_login(code: &str) -> anyhow::Result<Account> {
    let resp = http()
        .post("https://login.live.com/oauth20_token.srf")
        .form(&[
            ("client_id", CLIENT_ID),
            ("grant_type", "authorization_code"),
            ("code", code),
            ("redirect_uri", REDIRECT),
            ("scope", SCOPE),
        ])
        .send()
        .await?;
    if !resp.status().is_success() {
        let err: MsError = resp.json().await.unwrap_or(MsError { error: "unknown".into() });
        bail!("Microsoft refused the sign-in ({})", err.error);
    }
    minecraft_login(resp.json().await?).await
}

#[derive(Deserialize)]
struct MsToken {
    access_token: String,
    refresh_token: String,
}

#[derive(Deserialize)]
struct MsError {
    error: String,
}

pub async fn finish_device_login(code: &DeviceCode) -> anyhow::Result<Account> {
    let deadline = now() + code.expires_in as i64;
    let mut interval = code.interval.max(1);
    loop {
        tokio::time::sleep(std::time::Duration::from_secs(interval)).await;
        if now() > deadline {
            bail!("The code expired. Start again.");
        }
        let resp = http()
            .post("https://login.live.com/oauth20_token.srf")
            .form(&[
                ("client_id", CLIENT_ID),
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("device_code", code.device_code.as_str()),
            ])
            .send()
            .await?;
        if resp.status().is_success() {
            let tok: MsToken = resp.json().await?;
            return minecraft_login(tok).await;
        }
        let err: MsError = resp.json().await.unwrap_or(MsError { error: "unknown".into() });
        match err.error.as_str() {
            "authorization_pending" => {}
            "slow_down" => interval += 5,
            "authorization_declined" => bail!("Sign-in was declined."),
            "expired_token" => bail!("The code expired. Start again."),
            other => bail!("Microsoft said: {other}"),
        }
    }
}

async fn refresh_ms(refresh: &str) -> anyhow::Result<MsToken> {
    let resp = http()
        .post("https://login.live.com/oauth20_token.srf")
        .form(&[
            ("client_id", CLIENT_ID),
            ("grant_type", "refresh_token"),
            ("refresh_token", refresh),
            ("scope", SCOPE),
        ])
        .send()
        .await?;
    if !resp.status().is_success() {
        let err: MsError = resp.json().await.unwrap_or(MsError { error: "unknown".into() });
        if err.error == "invalid_grant" {
            bail!(Expired);
        }
        bail!("Microsoft refused the refresh ({})", err.error);
    }
    Ok(resp.json().await?)
}

#[derive(Debug)]
pub struct Expired;

impl std::fmt::Display for Expired {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        f.write_str("This account's session ended. Sign in again.")
    }
}

impl std::error::Error for Expired {}

#[derive(Deserialize)]
struct XblResponse {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    claims: XblClaims,
}

#[derive(Deserialize)]
struct XblClaims {
    xui: Vec<HashMap<String, String>>,
}

async fn minecraft_login(ms: MsToken) -> anyhow::Result<Account> {
    let mut xbl = None;
    for ticket in [format!("d={}", ms.access_token), format!("t={}", ms.access_token), ms.access_token.clone()] {
        let resp = http()
            .post("https://user.auth.xboxlive.com/user/authenticate")
            .header("x-xbl-contract-version", "1")
            .json(&json!({
                "Properties": {
                    "AuthMethod": "RPS",
                    "SiteName": "user.auth.xboxlive.com",
                    "RpsTicket": ticket,
                },
                "RelyingParty": "http://auth.xboxlive.com",
                "TokenType": "JWT",
            }))
            .send()
            .await?;
        if resp.status().is_success() {
            xbl = Some(resp.json::<XblResponse>().await?);
            break;
        }
    }
    let xbl = xbl.context("Xbox Live rejected the Microsoft token")?;

    let resp = http()
        .post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .json(&json!({
            "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl.token] },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT",
        }))
        .send()
        .await?;
    if resp.status().as_u16() == 401 {
        let body: serde_json::Value = resp.json().await.unwrap_or_default();
        match body["XErr"].as_u64() {
            Some(2148916233) => bail!("This Microsoft account has no Xbox profile. Create one at xbox.com first."),
            Some(2148916235) => bail!("Xbox Live isn't available in this account's country."),
            Some(2148916236 | 2148916237) => bail!("This account needs adult verification on xbox.com."),
            Some(2148916238) => bail!("This is a child account. An adult has to add it to a Microsoft family."),
            _ => bail!("Xbox Live refused the sign-in."),
        }
    }
    if !resp.status().is_success() {
        bail!("Xbox Live refused the sign-in ({})", resp.status());
    }
    let xsts: XblResponse = resp.json().await?;
    let uhs = xsts.claims.xui.first().and_then(|c| c.get("uhs")).cloned().context("no user hash")?;
    let xuid = xbl.claims.xui.first().and_then(|c| c.get("xid")).cloned().unwrap_or_default();

    #[derive(Deserialize)]
    struct McLogin {
        access_token: String,
        expires_in: i64,
    }
    let resp = http()
        .post("https://api.minecraftservices.com/authentication/login_with_xbox")
        .json(&json!({ "identityToken": format!("XBL3.0 x={uhs};{}", xsts.token) }))
        .send()
        .await?;
    if !resp.status().is_success() {
        bail!("Minecraft services refused the login ({})", resp.status());
    }
    let mc: McLogin = resp.json().await?;

    let profile = profile(&mc.access_token).await?;
    Ok(Account {
        uuid: profile.id,
        name: profile.name,
        xuid,
        mc_token: mc.access_token,
        mc_expires: now() + mc.expires_in,
        ms_refresh: ms.refresh_token,
        skin_url: profile.skins.into_iter().find(|s| s.state == "ACTIVE").map(|s| s.url),
    })
}

#[derive(Deserialize)]
struct Profile {
    id: String,
    name: String,
    #[serde(default)]
    skins: Vec<Skin>,
}

#[derive(Deserialize)]
struct Skin {
    state: String,
    url: String,
}

async fn profile(token: &str) -> anyhow::Result<Profile> {
    let resp = http()
        .get("https://api.minecraftservices.com/minecraft/profile")
        .bearer_auth(token)
        .send()
        .await?;
    if resp.status().as_u16() == 404 {
        bail!("This account doesn't own Minecraft: Java Edition.");
    }
    if !resp.status().is_success() {
        bail!("Couldn't read the Minecraft profile ({})", resp.status());
    }
    Ok(resp.json().await?)
}

pub async fn fresh(acc: &Account) -> anyhow::Result<Account> {
    if acc.mc_expires - now() > 15 * 60 {
        return Ok(acc.clone());
    }
    let ms = refresh_ms(&acc.ms_refresh).await?;
    let renewed = minecraft_login(ms).await?;
    store(&renewed).await?;
    Ok(renewed)
}

pub async fn roll(acc: &Account) -> anyhow::Result<Account> {
    let ms = refresh_ms(&acc.ms_refresh).await?;
    let renewed = minecraft_login(ms).await?;
    store(&renewed).await?;
    Ok(renewed)
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct Roster {
    pub active: Option<String>,
    pub accounts: Vec<RosterEntry>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct RosterEntry {
    pub uuid: String,
    pub name: String,
}

fn roster_path() -> PathBuf {
    data_dir().join("accounts.json")
}

pub fn roster() -> Roster {
    std::fs::read(roster_path())
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or_default()
}

pub fn save_roster(r: &Roster) -> anyhow::Result<()> {
    std::fs::create_dir_all(data_dir())?;
    std::fs::write(roster_path(), serde_json::to_vec_pretty(r)?)?;
    Ok(())
}

pub async fn store(acc: &Account) -> anyhow::Result<()> {
    let blob = serde_json::to_string(acc)?;
    crate::vault::put(&acc.uuid, &format!("Minecraft ({})", acc.name), &blob).await?;

    let mut r = roster();
    match r.accounts.iter_mut().find(|e| e.uuid == acc.uuid) {
        Some(e) => e.name = acc.name.clone(),
        None => r.accounts.push(RosterEntry { uuid: acc.uuid.clone(), name: acc.name.clone() }),
    }
    if r.active.is_none() {
        r.active = Some(acc.uuid.clone());
    }
    save_roster(&r)
}

pub async fn load(uuid: &str) -> anyhow::Result<Account> {
    let blob = crate::vault::get(uuid).await?.context("no stored tokens for this account")?;
    Ok(serde_json::from_str(&blob)?)
}

pub async fn forget(uuid: &str) -> anyhow::Result<()> {
    crate::vault::remove(uuid).await?;
    let mut r = roster();
    r.accounts.retain(|e| e.uuid != uuid);
    if r.active.as_deref() == Some(uuid) {
        r.active = r.accounts.first().map(|e| e.uuid.clone());
    }
    save_roster(&r)
}
