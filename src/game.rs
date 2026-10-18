use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};

use anyhow::{Context, bail};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::instance::{Instance, Loader, cache_dir, data_dir};
use crate::net::{self, Fetch, Job};

const MANIFEST: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Manifest {
    pub versions: Vec<ManifestEntry>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ManifestEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    pub sha1: String,
}

pub async fn manifest() -> anyhow::Result<Manifest> {
    let cached = cache_dir().join("version_manifest_v2.json");
    match net::get_bytes(MANIFEST).await {
        Ok(bytes) => {
            let m = serde_json::from_slice(&bytes)?;
            tokio::fs::create_dir_all(cache_dir()).await?;
            tokio::fs::write(&cached, &bytes).await?;
            Ok(m)
        }
        Err(e) => match tokio::fs::read(&cached).await {
            Ok(bytes) => Ok(serde_json::from_slice(&bytes)?),
            Err(_) => Err(e),
        },
    }
}

#[derive(Deserialize)]
struct LoaderEntry {
    loader: LoaderVersion,
}

#[derive(Deserialize)]
struct LoaderVersion {
    version: String,
    #[serde(default = "yes")]
    stable: bool,
}

fn yes() -> bool {
    true
}

fn loader_meta(loader: Loader) -> &'static str {
    match loader {
        Loader::Fabric => "https://meta.fabricmc.net/v2",
        Loader::Quilt => "https://meta.quiltmc.org/v3",
        Loader::Vanilla => unreachable!(),
    }
}

pub async fn latest_loader(loader: Loader, game: &str) -> anyhow::Result<Option<String>> {
    if loader == Loader::Vanilla {
        return Ok(None);
    }
    let url = format!("{}/versions/loader/{game}", loader_meta(loader));
    let list: Vec<LoaderEntry> = net::get_json(&url).await?;
    let pick = list
        .iter()
        .find(|e| e.loader.stable && !e.loader.version.contains("beta"))
        .or(list.first());
    Ok(pick.map(|e| e.loader.version.clone()))
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionJson {
    pub id: String,
    #[serde(default)]
    pub inherits_from: Option<String>,
    #[serde(default)]
    pub main_class: Option<String>,
    #[serde(default)]
    pub arguments: Option<Arguments>,
    #[serde(default)]
    pub minecraft_arguments: Option<String>,
    #[serde(default)]
    pub libraries: Vec<Library>,
    #[serde(default)]
    pub asset_index: Option<AssetIndexRef>,
    #[serde(default)]
    pub assets: Option<String>,
    #[serde(default)]
    pub downloads: Option<HashMap<String, Artifact>>,
    #[serde(default)]
    pub java_version: Option<JavaVersion>,
    #[serde(default, rename = "type")]
    pub kind: Option<String>,
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<Value>,
    #[serde(default)]
    pub jvm: Vec<Value>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaVersion {
    pub major_version: u32,
    #[serde(default)]
    pub component: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AssetIndexRef {
    pub id: String,
    pub url: String,
    pub sha1: String,
    #[serde(default)]
    pub size: u64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Artifact {
    #[serde(default)]
    pub path: Option<String>,
    pub url: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: u64,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Library {
    pub name: String,
    #[serde(default)]
    pub downloads: Option<LibDownloads>,
    #[serde(default)]
    pub url: Option<String>,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
    #[serde(default)]
    pub rules: Option<Vec<Rule>>,
    #[serde(default)]
    pub natives: Option<HashMap<String, String>>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct LibDownloads {
    #[serde(default)]
    pub artifact: Option<Artifact>,
    #[serde(default)]
    pub classifiers: Option<HashMap<String, Artifact>>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Rule {
    pub action: String,
    #[serde(default)]
    pub os: Option<OsRule>,
    #[serde(default)]
    pub features: Option<HashMap<String, bool>>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct OsRule {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub arch: Option<String>,
}

#[cfg(windows)]
const OS: &str = "windows";
#[cfg(not(windows))]
const OS: &str = "linux";
#[cfg(windows)]
const NATIVE_EXT: &str = ".dll";
#[cfg(not(windows))]
const NATIVE_EXT: &str = ".so";
#[cfg(windows)]
const SEPARATOR: &str = ";";
#[cfg(not(windows))]
const SEPARATOR: &str = ":";

fn other_arch_natives(name: &str) -> bool {
    let Some(rest) = name.split(&format!(":natives-{OS}")).nth(1) else { return false };
    let wanted = if cfg!(target_arch = "aarch64") { ["-arm64", "-aarch_64"] } else { ["", ""] };
    !rest.is_empty() && rest != wanted[0] && rest != wanted[1]
}

fn rule_matches(rule: &Rule, features: &HashSet<&str>) -> bool {
    if let Some(os) = &rule.os {
        if let Some(name) = &os.name {
            if name != OS {
                return false;
            }
        }
        if let Some(arch) = &os.arch {
            let ours = std::env::consts::ARCH;
            let hit = match arch.as_str() {
                "x86" => ours == "x86",
                "arm64" | "aarch64" => ours == "aarch64",
                "x86_64" | "amd64" => ours == "x86_64",
                other => other == ours,
            };
            if !hit {
                return false;
            }
        }
    }
    if let Some(want) = &rule.features {
        for (k, v) in want {
            if features.contains(k.as_str()) != *v {
                return false;
            }
        }
    }
    true
}

fn allowed(rules: &Option<Vec<Rule>>, features: &HashSet<&str>) -> bool {
    let Some(rules) = rules else { return true };
    let mut ok = false;
    for r in rules {
        if rule_matches(r, features) {
            ok = r.action == "allow";
        }
    }
    ok
}

fn maven_path(coord: &str) -> Option<String> {
    let (coord, ext) = coord.split_once('@').unwrap_or((coord, "jar"));
    let parts: Vec<&str> = coord.split(':').collect();
    if parts.len() < 3 {
        return None;
    }
    let group = parts[0].replace('.', "/");
    let (artifact, version) = (parts[1], parts[2]);
    let file = match parts.get(3) {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.{ext}"),
        None => format!("{artifact}-{version}.{ext}"),
    };
    Some(format!("{group}/{artifact}/{version}/{file}"))
}

fn library_key(coord: &str) -> String {
    let parts: Vec<&str> = coord.split(':').collect();
    match parts.len() {
        0..=2 => coord.to_string(),
        3 => format!("{}:{}", parts[0], parts[1]),
        _ => format!("{}:{}:{}", parts[0], parts[1], parts[3]),
    }
}

async fn version_json(id: &str, url: Option<&str>, sha1: Option<&str>) -> anyhow::Result<VersionJson> {
    let path = data_dir().join("versions").join(id).join(format!("{id}.json"));
    if let Some(url) = url {
        net::fetch(Fetch {
            url: url.to_string(),
            dest: path.clone(),
            sha1: sha1.map(str::to_string),
            size: 0,
        })
        .await?;
    }
    let bytes = tokio::fs::read(&path)
        .await
        .with_context(|| format!("version {id} isn't downloaded and Mojang couldn't be reached"))?;
    Ok(serde_json::from_slice(&bytes)?)
}

async fn vanilla_json(id: &str) -> anyhow::Result<VersionJson> {
    let local = data_dir().join("versions").join(id).join(format!("{id}.json"));
    if local.exists() {
        if let Ok(v) = version_json(id, None, None).await {
            return Ok(v);
        }
    }
    let m = manifest().await?;
    let entry = m
        .versions
        .iter()
        .find(|v| v.id == id)
        .with_context(|| format!("Mojang doesn't list a version called {id}"))?;
    version_json(id, Some(&entry.url), Some(&entry.sha1)).await
}

async fn loader_json(inst: &Instance) -> anyhow::Result<VersionJson> {
    let loader_version = inst
        .loader_version
        .as_deref()
        .context("instance has no loader version")?;
    let id = format!("{}-{}-{}", inst.loader.label().to_lowercase(), loader_version, inst.game_version);
    let path = data_dir().join("versions").join(&id).join(format!("{id}.json"));
    if !path.exists() {
        let url = format!(
            "{}/versions/loader/{}/{}/profile/json",
            loader_meta(inst.loader),
            inst.game_version,
            loader_version
        );
        let bytes = net::get_bytes(&url).await?;
        tokio::fs::create_dir_all(path.parent().unwrap()).await?;
        tokio::fs::write(&path, &bytes).await?;
    }
    Ok(serde_json::from_slice(&tokio::fs::read(&path).await?)?)
}

async fn resolve(inst: &Instance) -> anyhow::Result<VersionJson> {
    let vanilla = vanilla_json(&inst.game_version).await?;
    if inst.loader == Loader::Vanilla {
        return Ok(vanilla);
    }
    let child = loader_json(inst).await?;
    let mut merged = vanilla.clone();
    merged.id = child.id;
    if child.main_class.is_some() {
        merged.main_class = child.main_class;
    }
    let mut libs = child.libraries;
    libs.extend(vanilla.libraries);
    merged.libraries = libs;
    if let Some(extra) = child.arguments {
        let args = merged.arguments.get_or_insert_with(Arguments::default);
        args.game.extend(extra.game);
        args.jvm.extend(extra.jvm);
    }
    if let Some(legacy) = child.minecraft_arguments {
        merged.minecraft_arguments = Some(legacy);
    }
    Ok(merged)
}

struct Resolved {
    classpath: Vec<PathBuf>,
    natives: Vec<PathBuf>,
    fetches: Vec<Fetch>,
}

fn collect_libraries(v: &VersionJson, features: &HashSet<&str>) -> Resolved {
    let libdir = data_dir().join("libraries");
    let mut seen = HashSet::new();
    let mut out = Resolved { classpath: vec![], natives: vec![], fetches: vec![] };

    for lib in &v.libraries {
        if !allowed(&lib.rules, features) || other_arch_natives(&lib.name) {
            continue;
        }
        if let Some(natives) = &lib.natives {
            if let Some(classifier) = natives.get(OS) {
                let classifier = classifier.replace("${arch}", "64");
                let art = lib
                    .downloads
                    .as_ref()
                    .and_then(|d| d.classifiers.as_ref())
                    .and_then(|c| c.get(&classifier));
                if let Some(art) = art {
                    let rel = art
                        .path
                        .clone()
                        .or_else(|| maven_path(&format!("{}:{classifier}", lib.name)))
                        .unwrap_or_default();
                    let dest = libdir.join(rel);
                    out.fetches.push(Fetch {
                        url: art.url.clone(),
                        dest: dest.clone(),
                        sha1: art.sha1.clone(),
                        size: art.size,
                    });
                    out.natives.push(dest);
                }
            }
            if lib.downloads.as_ref().and_then(|d| d.artifact.as_ref()).is_none() {
                continue;
            }
        }

        if !seen.insert(library_key(&lib.name)) {
            continue;
        }
        let Some(rel) = maven_path(&lib.name) else { continue };

        let fetch = match lib.downloads.as_ref().and_then(|d| d.artifact.as_ref()) {
            Some(art) => Fetch {
                url: art.url.clone(),
                dest: libdir.join(art.path.clone().unwrap_or(rel)),
                sha1: art.sha1.clone(),
                size: art.size,
            },
            None => {
                let base = lib.url.as_deref().unwrap_or("https://libraries.minecraft.net/");
                let base = base.trim_end_matches('/');
                Fetch {
                    url: format!("{base}/{rel}"),
                    dest: libdir.join(&rel),
                    sha1: lib.sha1.clone(),
                    size: lib.size.unwrap_or(0),
                }
            }
        };
        if fetch.url.is_empty() {
            continue;
        }
        if lib.name.contains(&format!(":natives-{OS}")) {
            out.natives.push(fetch.dest.clone());
        }
        out.classpath.push(fetch.dest.clone());
        out.fetches.push(fetch);
    }
    out
}

#[derive(Deserialize)]
struct AssetIndex {
    objects: HashMap<String, AssetObject>,
    #[serde(default, rename = "virtual")]
    is_virtual: bool,
    #[serde(default)]
    map_to_resources: bool,
}

#[derive(Deserialize)]
struct AssetObject {
    hash: String,
    size: u64,
}

async fn prepare_assets(v: &VersionJson, game_dir: &Path, job: &Job) -> anyhow::Result<()> {
    let Some(idx) = &v.asset_index else { return Ok(()) };
    let assets = data_dir().join("assets");
    let index_path = assets.join("indexes").join(format!("{}.json", idx.id));
    net::fetch(Fetch { url: idx.url.clone(), dest: index_path.clone(), sha1: Some(idx.sha1.clone()), size: 0 })
        .await?;
    let index: AssetIndex = serde_json::from_slice(&tokio::fs::read(&index_path).await?)?;

    let object = |hash: &str| assets.join("objects").join(&hash[..2]).join(hash);
    let fetches = index
        .objects
        .values()
        .map(|o| Fetch {
            url: format!("https://resources.download.minecraft.net/{}/{}", &o.hash[..2], o.hash),
            dest: object(&o.hash),
            sha1: Some(o.hash.clone()),
            size: o.size,
        })
        .collect();
    job.relabel("Downloading assets");
    net::fetch_all(fetches, job).await?;

    if index.is_virtual || index.map_to_resources {
        let root = if index.map_to_resources {
            game_dir.join("resources")
        } else {
            assets.join("virtual").join(&idx.id)
        };
        for (name, o) in &index.objects {
            let dest = root.join(name);
            if !dest.exists() {
                if let Some(p) = dest.parent() {
                    tokio::fs::create_dir_all(p).await?;
                }
                tokio::fs::copy(object(&o.hash), &dest).await?;
            }
        }
    }
    Ok(())
}

fn extract_natives(jars: &[PathBuf], into: &Path) -> anyhow::Result<()> {
    std::fs::create_dir_all(into)?;
    for jar in jars {
        let file = std::fs::File::open(jar).with_context(|| format!("opening {}", jar.display()))?;
        let mut zip = zip::ZipArchive::new(file)?;
        for i in 0..zip.len() {
            let mut entry = zip.by_index(i)?;
            let name = entry.name()?.to_string();
            if entry.is_dir() || name.starts_with("META-INF") || !name.ends_with(NATIVE_EXT) {
                continue;
            }
            let flat = Path::new(&name).file_name().unwrap();
            let dest = into.join(flat);
            if dest.exists() {
                continue;
            }
            let mut out = std::fs::File::create(&dest)?;
            std::io::copy(&mut entry, &mut out)?;
        }
    }
    Ok(())
}

#[derive(Clone, Debug)]
pub struct Jvm {
    pub major: u32,
    pub path: PathBuf,
    pub name: String,
}

#[cfg(windows)]
fn java_roots() -> Vec<PathBuf> {
    vec![data_dir().join("runtime")]
}

#[cfg(not(windows))]
fn java_roots() -> Vec<PathBuf> {
    vec![PathBuf::from("/usr/lib/jvm"), PathBuf::from("/app/jvm"), data_dir().join("runtime")]
}

pub fn installed_jvms() -> Vec<Jvm> {
    let exe = if cfg!(windows) { "bin/javaw.exe" } else { "bin/java" };
    let mut out = Vec::new();
    for root in java_roots() {
        let Ok(entries) = std::fs::read_dir(root) else { continue };
        for e in entries.flatten() {
            let dir = e.path();
            if dir.is_symlink() {
                continue;
            }
            let java = dir.join(exe);
            if !java.exists() {
                continue;
            }
            let name = e.file_name().to_string_lossy().into_owned();
            if let Some(major) = release_major(&dir).or_else(|| name_major(&name)) {
                out.push(Jvm { major, path: java, name });
            }
        }
    }
    out.sort_by_key(|j| j.major);
    out
}

fn release_major(dir: &Path) -> Option<u32> {
    let release = std::fs::read_to_string(dir.join("release")).ok()?;
    let ver = release.lines().find_map(|l| l.strip_prefix("JAVA_VERSION="))?.trim_matches('"').to_string();
    match ver.strip_prefix("1.") {
        Some(rest) => rest.split('.').next(),
        None => ver.split(['.', '-', '+']).next(),
    }
    .and_then(|m| m.parse().ok())
}

fn name_major(name: &str) -> Option<u32> {
    name.rsplit('-').next()?.parse().ok()
}

#[cfg(not(windows))]
fn pick_jvm(want: u32) -> anyhow::Result<PathBuf> {
    let jvms = installed_jvms();
    if let Some(j) = jvms.iter().find(|j| j.major == want) {
        return Ok(j.path.clone());
    }
    if want >= 16 {
        if let Some(j) = jvms.iter().find(|j| j.major > want) {
            return Ok(j.path.clone());
        }
    }
    if Path::new("/.flatpak-info").exists() {
        bail!("This version needs Java {want}, which this build doesn't include.");
    }
    let want = if want == 16 { 17 } else { want };
    if Path::new("/usr/bin/apt-get").exists() {
        bail!("This version needs Java {want}.\nsudo apt install openjdk-{want}-jre")
    }
    bail!("This version needs Java {want}.\nsudo pacman -S jre{want}-openjdk")
}

async fn system_java(v: &VersionJson, want: u32, job: &Job) -> anyhow::Result<PathBuf> {
    if let Some(j) = installed_jvms().into_iter().find(|j| j.major == want) {
        return Ok(j.path);
    }
    let component = v.java_version.as_ref().and_then(|j| j.component.clone()).unwrap_or_else(|| "jre-legacy".into());
    match crate::runtime::ensure(&component, job).await {
        Ok(path) => Ok(path),
        #[cfg(not(windows))]
        Err(e) => pick_jvm(want).map_err(|fallback| anyhow::anyhow!("{fallback}\nCouldn't download Java {want} either: {e}")),
        #[cfg(windows)]
        Err(e) => Err(e),
    }
}

pub struct Account<'a> {
    pub name: &'a str,
    pub uuid: &'a str,
    pub xuid: &'a str,
    pub token: &'a str,
}

pub async fn prepare(inst: &Instance, who: Account<'_>, job: &Job) -> anyhow::Result<tokio::process::Command> {
    job.relabel(format!("Resolving {}", inst.game_version));
    crate::bundled::ensure(inst).await?;
    let v = resolve(inst).await?;
    let game_dir = inst.game_dir();
    tokio::fs::create_dir_all(&game_dir).await?;

    let features: HashSet<&str> = HashSet::new();
    let mut libs = collect_libraries(&v, &features);

    let vanilla_id = &inst.game_version;
    let client_jar = data_dir().join("versions").join(vanilla_id).join(format!("{vanilla_id}.jar"));
    if let Some(client) = v.downloads.as_ref().and_then(|d| d.get("client")) {
        libs.fetches.push(Fetch {
            url: client.url.clone(),
            dest: client_jar.clone(),
            sha1: client.sha1.clone(),
            size: client.size,
        });
    }
    libs.classpath.push(client_jar);

    job.relabel("Downloading libraries");
    net::fetch_all(libs.fetches, job).await?;
    prepare_assets(&v, &game_dir, job).await?;

    let natives_dir = data_dir().join("natives").join(&v.id);
    let jars = libs.natives.clone();
    let nd = natives_dir.clone();
    tokio::task::spawn_blocking(move || extract_natives(&jars, &nd)).await??;

    let want = v.java_version.as_ref().map(|j| j.major_version).unwrap_or(8);
    let java = match &inst.java {
        Some(p) if p.exists() => p.clone(),
        _ => system_java(&v, want, job).await?,
    };

    let classpath = libs
        .classpath
        .iter()
        .map(|p| p.to_string_lossy().into_owned())
        .collect::<Vec<_>>()
        .join(SEPARATOR);
    let assets_root = data_dir().join("assets");
    let asset_id = v.asset_index.as_ref().map(|a| a.id.clone()).or(v.assets.clone()).unwrap_or_default();
    let vars: HashMap<&str, String> = HashMap::from([
        ("auth_player_name", who.name.to_string()),
        ("version_name", v.id.clone()),
        ("game_directory", game_dir.to_string_lossy().into_owned()),
        ("assets_root", assets_root.to_string_lossy().into_owned()),
        ("game_assets", assets_root.join("virtual").join(&asset_id).to_string_lossy().into_owned()),
        ("assets_index_name", asset_id.clone()),
        ("auth_uuid", who.uuid.replace('-', "")),
        ("auth_access_token", who.token.to_string()),
        ("auth_session", who.token.to_string()),
        ("auth_xuid", who.xuid.to_string()),
        ("clientid", String::new()),
        ("user_type", "msa".to_string()),
        ("user_properties", "{}".to_string()),
        ("version_type", v.kind.clone().unwrap_or_else(|| "release".into())),
        ("natives_directory", natives_dir.to_string_lossy().into_owned()),
        ("launcher_name", "oppa".to_string()),
        ("launcher_version", env!("CARGO_PKG_VERSION").to_string()),
        ("classpath", classpath),
        ("classpath_separator", SEPARATOR.to_string()),
        ("library_directory", data_dir().join("libraries").to_string_lossy().into_owned()),
    ]);
    let fill = |s: &str| {
        let mut s = s.to_string();
        while let Some(start) = s.find("${") {
            let Some(len) = s[start..].find('}') else { break };
            let key = &s[start + 2..start + len];
            let val = vars.get(key).cloned().unwrap_or_default();
            s.replace_range(start..start + len + 1, &val);
        }
        s
    };

    let xms = if inst.performance { inst.memory_mb } else { (inst.memory_mb / 4).max(512) };
    let mut jvm_args: Vec<String> = vec![
        format!("-Xmx{}M", inst.memory_mb),
        format!("-Xms{xms}M"),
        "-Dlog4j2.formatMsgNoLookups=true".into(),
    ];
    if inst.performance {
        jvm_args.extend(
            [
                "-XX:+UseG1GC",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:MaxGCPauseMillis=37",
                "-XX:+ParallelRefProcEnabled",
                "-XX:+DisableExplicitGC",
                "-XX:G1NewSizePercent=23",
                "-XX:G1MaxNewSizePercent=50",
                "-XX:G1HeapRegionSize=16M",
                "-XX:G1ReservePercent=20",
                "-XX:InitiatingHeapOccupancyPercent=20",
                "-XX:G1MixedGCCountTarget=3",
                "-XX:+PerfDisableSharedMem",
                "-XX:+UseStringDeduplication",
            ]
            .map(String::from),
        );
        if want >= 25 && inst.java.is_none() {
            jvm_args.push("-XX:+UseCompactObjectHeaders".into());
        }
    }
    jvm_args.extend(inst.jvm_args.split_whitespace().map(str::to_string));
    let mut game_args: Vec<String> = Vec::new();

    let empty = Arguments::default();
    let args = v.arguments.as_ref().unwrap_or(&empty);
    if !args.game.is_empty() {
        expand(&args.jvm, &features, &fill, &mut jvm_args);
        expand(&args.game, &features, &fill, &mut game_args);
    } else if let Some(legacy) = &v.minecraft_arguments {
        expand(&args.jvm, &features, &fill, &mut jvm_args);
        jvm_args.extend([
            fill("-Djava.library.path=${natives_directory}"),
            "-cp".into(),
            fill("${classpath}"),
        ]);
        game_args.extend(legacy.split_whitespace().map(fill));
    } else {
        bail!("version {} has no launch arguments", v.id);
    }

    let main_class = v.main_class.clone().context("version has no main class")?;
    let mut cmd = tokio::process::Command::new(java);
    cmd.args(&jvm_args).arg(main_class).args(&game_args).current_dir(&game_dir);
    #[cfg(windows)]
    cmd.creation_flags(0x0800_0000);
    Ok(cmd)
}

fn expand(list: &[Value], features: &HashSet<&str>, fill: &impl Fn(&str) -> String, out: &mut Vec<String>) {
    for item in list {
        match item {
            Value::String(s) => out.push(fill(s)),
            Value::Object(o) => {
                let rules: Option<Vec<Rule>> = o.get("rules").and_then(|r| serde_json::from_value(r.clone()).ok());
                if !allowed(&rules, features) {
                    continue;
                }
                match o.get("value") {
                    Some(Value::String(s)) => out.push(fill(s)),
                    Some(Value::Array(a)) => out.extend(a.iter().filter_map(Value::as_str).map(fill)),
                    _ => {}
                }
            }
            _ => {}
        }
    }
}

