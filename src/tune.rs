use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};

use crate::instance::{Instance, Loader};

pub struct Report {
    pub notes: Vec<String>,
    pub memory: Option<u32>,
}

struct Jar {
    path: PathBuf,
    id: String,
    name: String,
    version: String,
}

fn read_meta(path: &Path) -> Option<Jar> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(path).ok()?).ok()?;
    let mut text = String::new();
    zip.by_name("fabric.mod.json").ok()?.take(1 << 20).read_to_string(&mut text).ok()?;
    let meta: serde_json::Value = serde_json::from_str(text.trim_start_matches('\u{feff}')).ok()?;
    let id = meta["id"].as_str()?.to_string();
    Some(Jar {
        path: path.to_path_buf(),
        name: meta["name"].as_str().unwrap_or(&id).to_string(),
        version: meta["version"].as_str().unwrap_or("").to_string(),
        id,
    })
}

fn numbers(version: &str) -> Vec<u64> {
    version.split(|c: char| !c.is_ascii_digit()).filter(|s| !s.is_empty()).filter_map(|s| s.parse().ok()).collect()
}

fn modified(path: &Path) -> std::time::SystemTime {
    std::fs::metadata(path).and_then(|m| m.modified()).unwrap_or(std::time::UNIX_EPOCH)
}

fn disable(path: &Path) -> bool {
    std::fs::rename(path, format!("{}.disabled", path.display())).is_ok()
}

pub fn memory_mb() -> u64 {
    std::fs::read_to_string("/proc/meminfo")
        .ok()
        .and_then(|s| {
            s.lines()
                .find_map(|l| l.strip_prefix("MemTotal:"))
                .and_then(|v| v.trim().trim_end_matches("kB").trim().parse::<u64>().ok())
        })
        .map(|kb| kb / 1024)
        .unwrap_or(8192)
}

fn gb(mb: u32) -> String {
    let g = mb as f32 / 1024.0;
    if g.fract() < 0.05 { format!("{g:.0} GB") } else { format!("{g:.1} GB") }
}

pub fn check(inst: &Instance) -> Report {
    let mut notes = Vec::new();
    let mods_dir = inst.game_dir().join("mods");
    let mut jars: Vec<Jar> = Vec::new();
    if inst.loader != Loader::Vanilla {
        let paths: Vec<PathBuf> = std::fs::read_dir(&mods_dir)
            .map(|d| d.flatten().map(|e| e.path()).filter(|p| p.extension().is_some_and(|x| x == "jar")).collect())
            .unwrap_or_default();
        jars = paths.iter().filter_map(|p| read_meta(p)).collect();

        let mut by_id: HashMap<String, Vec<usize>> = HashMap::new();
        for (i, j) in jars.iter().enumerate() {
            by_id.entry(j.id.clone()).or_default().push(i);
        }
        let mut gone = vec![false; jars.len()];
        for copies in by_id.values().filter(|c| c.len() > 1) {
            let best = *copies
                .iter()
                .max_by(|&&a, &&b| numbers(&jars[a].version).cmp(&numbers(&jars[b].version)).then(modified(&jars[a].path).cmp(&modified(&jars[b].path))))
                .unwrap();
            let mut extra = 0;
            for &i in copies.iter().filter(|&&i| i != best) {
                if disable(&jars[i].path) {
                    gone[i] = true;
                    extra += 1;
                }
            }
            if extra > 0 {
                notes.push(format!("Turned off {extra} extra {} of {}", if extra == 1 { "copy" } else { "copies" }, jars[best].name));
            }
        }

        let has = |id: &str| jars.iter().enumerate().any(|(i, j)| !gone[i] && j.id == id);
        if has("sodium") {
            for (i, j) in jars.iter().enumerate() {
                let file = j.path.file_name().unwrap_or_default().to_string_lossy().to_lowercase();
                if !gone[i] && (j.id == "optifabric" || file.contains("optifine")) && disable(&j.path) {
                    gone[i] = true;
                    notes.push(format!("Turned off {} because it breaks Sodium", j.name));
                }
            }
        }
        let mut kept = Vec::new();
        for (i, j) in jars.into_iter().enumerate() {
            if !gone[i] {
                kept.push(j);
            }
        }
        jars = kept;
    }

    let mut memory = None;
    if inst.performance {
        let total = memory_mb() as u32;
        let ceiling = (total * 60 / 100 / 512 * 512).max(2048);
        let want = ((2560 + jars.len() as u32 * 20).div_ceil(512) * 512).clamp(3072, 10240).min(ceiling);
        if inst.memory_mb > total * 75 / 100 {
            memory = Some(want);
            notes.push(format!("Lowered memory to {} so your computer doesn't run out", gb(want)));
        } else if inst.memory_mb + 512 < want {
            memory = Some(want);
            notes.push(format!("Raised memory to {} for {} mods", gb(want), jars.len()));
        }
    }
    Report { notes, memory }
}

#[cfg(test)]
mod tests {
    use super::numbers;

    #[test]
    fn newer_versions_sort_higher() {
        assert!(numbers("0.6.13+mc1.21.11") > numbers("0.6.9+mc1.21.11"));
        assert!(numbers("1.10.0") > numbers("1.9.9"));
        assert!(numbers("2") > numbers("1.99"));
    }
}

