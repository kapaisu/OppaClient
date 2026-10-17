use std::path::PathBuf;

use anyhow::Context;
use serde_json::Value;

use crate::instance::data_dir;
use crate::net::{self, Fetch, Job};

const INDEX: &str = "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

fn platform() -> &'static str {
    if cfg!(target_arch = "aarch64") { "windows-arm64" } else { "windows-x64" }
}

pub fn exe(component: &str) -> PathBuf {
    data_dir().join("runtime").join(component).join("bin").join("javaw.exe")
}

pub async fn ensure(component: &str, job: &Job) -> anyhow::Result<PathBuf> {
    let root = data_dir().join("runtime").join(component);
    let marker = root.join(".oppa-version");
    let index: Value = match net::get_json(INDEX).await {
        Ok(v) => v,
        Err(e) if exe(component).exists() => {
            eprintln!("java runtime index unavailable ({e:#}), using the installed copy");
            return Ok(exe(component));
        }
        Err(e) => return Err(e),
    };
    let entry = index[platform()][component]
        .get(0)
        .or_else(|| index[platform()]["java-runtime-delta"].get(0))
        .context("Mojang doesn't offer a Java runtime for this version")?;
    let version = entry["version"]["name"].as_str().unwrap_or_default().to_string();
    if exe(component).exists() && std::fs::read_to_string(&marker).ok().as_deref() == Some(version.as_str()) {
        return Ok(exe(component));
    }

    job.relabel(format!("Downloading Java {version}"));
    let manifest_url = entry["manifest"]["url"].as_str().context("runtime manifest missing")?;
    let manifest: Value = net::get_json(manifest_url).await?;
    let mut fetches = Vec::new();
    for (path, info) in manifest["files"].as_object().context("runtime manifest has no files")? {
        let dest = root.join(path);
        match info["type"].as_str() {
            Some("directory") => std::fs::create_dir_all(&dest)?,
            Some("file") => {
                let raw = &info["downloads"]["raw"];
                fetches.push(Fetch {
                    url: raw["url"].as_str().unwrap_or_default().to_string(),
                    dest,
                    sha1: raw["sha1"].as_str().map(str::to_string),
                    size: raw["size"].as_u64().unwrap_or(0),
                });
            }
            _ => {}
        }
    }
    net::fetch_all(fetches, job).await?;
    std::fs::write(&marker, &version)?;
    Ok(exe(component))
}
