use std::collections::{HashMap, HashSet};
use std::path::PathBuf;

use anyhow::{Context, bail};
use serde::Deserialize;
use serde_json::json;

use crate::instance::{Instance, InstalledFile, Loader};
use crate::net::{self, Fetch, Job, http};

const API: &str = "https://api.modrinth.com/v2";

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Kind {
    Mod,
    Shader,
    ResourcePack,
}

impl Kind {
    fn project_type(self) -> &'static str {
        match self {
            Kind::Mod => "mod",
            Kind::Shader => "shader",
            Kind::ResourcePack => "resourcepack",
        }
    }

    pub fn folder(self) -> &'static str {
        match self {
            Kind::Mod => "mods",
            Kind::Shader => "shaderpacks",
            Kind::ResourcePack => "resourcepacks",
        }
    }
}

#[derive(Clone, Debug, Deserialize)]
pub struct Hit {
    pub project_id: String,
    pub title: String,
    pub description: String,
    pub author: String,
    pub downloads: u64,
    #[serde(default)]
    pub icon_url: Option<String>,
}

#[derive(Deserialize)]
struct SearchResponse {
    hits: Vec<Hit>,
    total_hits: u64,
}

pub struct Page {
    pub hits: Vec<Hit>,
    pub total: u64,
}

pub async fn search(query: &str, kind: Kind, game_version: &str, loader: Loader, offset: u32) -> anyhow::Result<Page> {
    let mut facets = vec![
        vec![format!("project_type:{}", kind.project_type())],
        vec![format!("versions:{game_version}")],
    ];
    if kind == Kind::Mod {
        if let Some(l) = loader.modrinth() {
            facets.push(vec![format!("categories:{l}")]);
        }
    }
    let index = if query.trim().is_empty() { "downloads" } else { "relevance" };
    let resp = http()
        .get(format!("{API}/search"))
        .query(&[
            ("query", query.trim()),
            ("facets", &serde_json::to_string(&facets)?),
            ("index", index),
            ("limit", "30"),
            ("offset", &offset.to_string()),
        ])
        .send()
        .await?;
    if !resp.status().is_success() {
        bail!("Modrinth returned {}", resp.status());
    }
    let r: SearchResponse = resp.json().await?;
    Ok(Page { hits: r.hits, total: r.total_hits })
}

#[derive(Clone, Debug, Deserialize)]
pub struct Version {
    pub id: String,
    pub project_id: String,
    pub version_type: String,
    pub files: Vec<VersionFile>,
    #[serde(default)]
    pub dependencies: Vec<Dependency>,
}

#[derive(Clone, Debug, Deserialize)]
pub struct VersionFile {
    pub url: String,
    pub filename: String,
    pub primary: bool,
    pub size: u64,
    pub hashes: HashMap<String, String>,
}

#[derive(Clone, Debug, Deserialize)]
pub struct Dependency {
    #[serde(default)]
    pub project_id: Option<String>,
    #[serde(default)]
    pub version_id: Option<String>,
    pub dependency_type: String,
}

async fn best_version(project: &str, kind: Kind, inst: &Instance) -> anyhow::Result<Option<Version>> {
    let mut req = http()
        .get(format!("{API}/project/{project}/version"))
        .query(&[("game_versions", serde_json::to_string(&[&inst.game_version])?)]);
    if kind == Kind::Mod {
        if let Some(l) = inst.loader.modrinth() {
            let loaders: Vec<&str> = if l == "quilt" { vec!["quilt", "fabric"] } else { vec![l] };
            req = req.query(&[("loaders", serde_json::to_string(&loaders)?)]);
        }
    }
    let resp = req.send().await?;
    if !resp.status().is_success() {
        bail!("Modrinth returned {}", resp.status());
    }
    let versions: Vec<Version> = resp.json().await?;
    let release = versions.iter().find(|v| v.version_type == "release").cloned();
    Ok(release.or_else(|| versions.into_iter().next()))
}

async fn version_by_id(id: &str) -> anyhow::Result<Version> {
    net::get_json(&format!("{API}/version/{id}")).await
}

pub async fn install(project: &str, title: &str, kind: Kind, inst: &Instance) -> anyhow::Result<Vec<String>> {
    let job = Job::begin(format!("Installing {title}"));
    let mut installed = inst.installed();
    let mut queue = vec![(project.to_string(), None::<String>)];
    let mut visited = HashSet::new();
    let mut extras = Vec::new();

    while let Some((pid, pinned)) = queue.pop() {
        if !visited.insert(pid.clone()) {
            continue;
        }
        let is_root = pid == project;
        if !is_root && installed.contains_key(&pid) {
            continue;
        }
        let version = match pinned {
            Some(vid) => Some(version_by_id(&vid).await?),
            None => best_version(&pid, kind, inst).await?,
        };
        let Some(version) = version else {
            if is_root {
                bail!("{title} has no file for {} {}", inst.loader.label(), inst.game_version);
            }
            continue;
        };
        let file = version
            .files
            .iter()
            .find(|f| f.primary)
            .or(version.files.first())
            .context("version has no files")?;

        let folder = inst.game_dir().join(kind.folder());
        if let Some(old) = installed.get(&pid) {
            let _ = tokio::fs::remove_file(folder.join(&old.file)).await;
        }
        if !is_root {
            job.relabel(format!("Installing {title} (dependency)"));
        }
        net::fetch(Fetch {
            url: file.url.clone(),
            dest: folder.join(&file.filename),
            sha1: file.hashes.get("sha1").cloned(),
            size: file.size,
        })
        .await?;
        installed.insert(
            pid.clone(),
            InstalledFile {
                folder: kind.folder().to_string(),
                file: file.filename.clone(),
                version_id: version.id.clone(),
            },
        );
        if !is_root {
            extras.push(pid.clone());
        }
        for dep in version.dependencies.iter().filter(|d| d.dependency_type == "required") {
            if let Some(dep_pid) = &dep.project_id {
                queue.push((dep_pid.clone(), dep.version_id.clone()));
            } else if let Some(vid) = &dep.version_id {
                let v = version_by_id(vid).await?;
                queue.push((v.project_id, Some(vid.clone())));
            }
        }
    }
    inst.save_installed(&installed)?;
    if extras.is_empty() {
        return Ok(extras);
    }
    let titles: Vec<Project> = http()
        .get(format!("{API}/projects"))
        .query(&[("ids", serde_json::to_string(&extras)?)])
        .send()
        .await?
        .json()
        .await
        .unwrap_or_default();
    Ok(titles.into_iter().map(|p| p.title).collect())
}

#[derive(Clone, Debug, Deserialize)]
pub struct Project {
    pub id: String,
    pub title: String,
    #[serde(default)]
    pub icon_url: Option<String>,
}

pub async fn identify(paths: Vec<PathBuf>) -> HashMap<PathBuf, Project> {
    let hashed: Vec<(PathBuf, String)> = tokio::task::spawn_blocking(move || {
        paths
            .into_iter()
            .filter(|p| p.is_file())
            .filter_map(|p| net::sha1_file(&p).ok().map(|h| (p, h)))
            .collect()
    })
    .await
    .unwrap_or_default();
    if hashed.is_empty() {
        return HashMap::new();
    }
    match identify_hashes(&hashed).await {
        Ok(m) => m,
        Err(e) => {
            eprintln!("modrinth lookup failed: {e:#}");
            HashMap::new()
        }
    }
}

async fn identify_hashes(hashed: &[(PathBuf, String)]) -> anyhow::Result<HashMap<PathBuf, Project>> {
    let hashes: Vec<&str> = hashed.iter().map(|(_, h)| h.as_str()).collect();
    let resp = http()
        .post(format!("{API}/version_files"))
        .json(&json!({ "hashes": hashes, "algorithm": "sha1" }))
        .send()
        .await?;
    let by_hash: HashMap<String, Version> = resp.json().await?;
    let ids: HashSet<&str> = by_hash.values().map(|v| v.project_id.as_str()).collect();
    if ids.is_empty() {
        return Ok(HashMap::new());
    }
    let projects: Vec<Project> = http()
        .get(format!("{API}/projects"))
        .query(&[("ids", serde_json::to_string(&ids)?)])
        .send()
        .await?
        .json()
        .await?;
    let projects: HashMap<&str, &Project> = projects.iter().map(|p| (p.id.as_str(), p)).collect();
    Ok(hashed
        .iter()
        .filter_map(|(path, h)| {
            let v = by_hash.get(h)?;
            let p = projects.get(v.project_id.as_str())?;
            Some((path.clone(), (*p).clone()))
        })
        .collect())
}
