use std::path::PathBuf;

use anyhow::Context;
use serde_json::Value;

use crate::instance::data_dir;
use crate::net::{self, Fetch, Job};

const INDEX: &str = "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

fn platform() -> &'static str {
    match (cfg!(windows), cfg!(target_arch = "aarch64")) {
        (true, true) => "windows-arm64",
        (true, false) => "windows-x64",
        (false, _) => "linux",
    }
}

pub fn exe(component: &str) -> PathBuf {
    let bin = data_dir().join("runtime").join(component).join("bin");
    if cfg!(windows) { bin.join("javaw.exe") } else { bin.join("java") }
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
    let mut executables = Vec::new();
    let mut links = Vec::new();
    for (path, info) in manifest["files"].as_object().context("runtime manifest has no files")? {
        let dest = root.join(path);
        match info["type"].as_str() {
            Some("directory") => std::fs::create_dir_all(&dest)?,
            Some("file") => {
                let raw = &info["downloads"]["raw"];
                if info["executable"].as_bool() == Some(true) {
                    executables.push(dest.clone());
                }
                fetches.push(Fetch {
                    url: raw["url"].as_str().unwrap_or_default().to_string(),
                    dest,
                    sha1: raw["sha1"].as_str().map(str::to_string),
                    size: raw["size"].as_u64().unwrap_or(0),
                });
            }
            Some("link") => {
                if let Some(target) = info["target"].as_str() {
                    links.push((dest, target.to_string()));
                }
            }
            _ => {}
        }
    }
    net::fetch_all(fetches, job).await?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        for path in executables {
            std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o755))?;
        }
        for (path, target) in links {
            if std::fs::symlink_metadata(&path).is_err() {
                if let Some(parent) = path.parent() {
                    std::fs::create_dir_all(parent)?;
                }
                std::os::unix::fs::symlink(target, &path)?;
            }
        }
    }
    #[cfg(not(unix))]
    let _ = (executables, links);
    std::fs::write(&marker, &version)?;
    Ok(exe(component))
}
