use std::collections::BTreeSet;
use std::path::Path;

use crate::instance::Instance;
use crate::modrinth::{self, Kind};
use crate::net::Job;

const FILES: [&str; 3] = ["options.txt", "servers.dat", "servers.dat_old"];
const FOLDERS: [&str; 4] = ["config", "resourcepacks", "shaderpacks", "schematics"];
const OURS: [&str; 2] = ["oppaclient.jar", "fabric-api"];

pub struct Report {
    pub moved: usize,
    pub missing: Vec<String>,
}

fn copy_dir(from: &Path, to: &Path) -> std::io::Result<()> {
    std::fs::create_dir_all(to)?;
    for e in std::fs::read_dir(from)?.flatten() {
        let target = to.join(e.file_name());
        if e.file_type()?.is_dir() {
            copy_dir(&e.path(), &target)?;
        } else {
            std::fs::copy(e.path(), target)?;
        }
    }
    Ok(())
}

fn copy_files(src: &Path, dst: &Path, worlds: bool) -> std::io::Result<()> {
    for f in FILES {
        if src.join(f).is_file() {
            std::fs::copy(src.join(f), dst.join(f))?;
        }
    }
    for d in FOLDERS.iter().copied().chain(worlds.then_some("saves")) {
        if src.join(d).is_dir() {
            copy_dir(&src.join(d), &dst.join(d))?;
        }
    }
    Ok(())
}

fn ours(name: &str) -> bool {
    let n = name.to_lowercase();
    OURS.iter().any(|o| n.starts_with(o))
}

pub async fn carry(from: Instance, to: Instance, worlds: bool) -> anyhow::Result<Report> {
    let job = Job::begin(format!("Copying {} to {}", from.name, to.game_version));
    let (src, dst) = (from.game_dir(), to.game_dir());
    tokio::task::spawn_blocking(move || copy_files(&src, &dst, worlds)).await??;

    let mods_dir = from.game_dir().join("mods");
    let jars: Vec<(std::path::PathBuf, bool)> = std::fs::read_dir(&mods_dir)
        .map(|d| {
            d.flatten()
                .filter_map(|e| {
                    let name = e.file_name().to_string_lossy().into_owned();
                    let disabled = name.ends_with(".jar.disabled");
                    (name.ends_with(".jar") || disabled).then(|| (e.path(), disabled))
                })
                .filter(|(p, _)| !ours(&p.file_name().unwrap_or_default().to_string_lossy()))
                .collect()
        })
        .unwrap_or_default();

    let installed = from.installed();
    let mut known: Vec<(String, String, bool)> = Vec::new();
    let mut unknown = Vec::new();
    for (path, disabled) in &jars {
        let file = path.file_name().unwrap_or_default().to_string_lossy().into_owned();
        let plain = file.strip_suffix(".disabled").unwrap_or(&file).to_string();
        match installed.iter().find(|(_, f)| f.folder == "mods" && (f.file == plain || f.file == file)) {
            Some((id, _)) => known.push((id.clone(), plain, *disabled)),
            None => unknown.push((path.clone(), plain, *disabled)),
        }
    }
    let found = modrinth::identify(unknown.iter().map(|(p, ..)| p.clone()).collect()).await;
    let mut missing = Vec::new();
    for (path, plain, disabled) in unknown {
        match found.get(&path) {
            Some(project) => known.push((project.id.clone(), project.title.clone(), disabled)),
            None => missing.push(plain),
        }
    }

    let mut done = BTreeSet::new();
    let total = known.len() as u64;
    let mut moved = 0;
    for (i, (project, title, disabled)) in known.into_iter().enumerate() {
        job.progress(i as u64, total);
        if !done.insert(project.clone()) {
            continue;
        }
        match modrinth::install(&project, &title, Kind::Mod, &to).await {
            Ok(_) => {
                moved += 1;
                if let Some(f) = to.installed().get(&project).filter(|_| disabled) {
                    let path = to.game_dir().join("mods").join(&f.file);
                    let _ = std::fs::rename(&path, format!("{}.disabled", path.display()));
                }
            }
            Err(_) => missing.push(title),
        }
    }
    drop(job);
    let _ = crate::bundled::ensure(&to).await;
    missing.sort_by_key(|m| m.to_lowercase());
    Ok(Report { moved, missing })
}

