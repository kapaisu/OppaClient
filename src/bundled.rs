use crate::instance::{Instance, Loader};
use crate::modrinth::{self, Kind};

const FABRIC_API: &str = "P7dR8mSH";
const FILE: &str = "oppaclient.jar";

fn jar_for(game: &str) -> Option<&'static [u8]> {
    match game {
        "1.21.1" => Some(include_bytes!("../data/mods/oppaclient-1.21.1.jar")),
        "1.21.4" => Some(include_bytes!("../data/mods/oppaclient-1.21.4.jar")),
        "1.21.10" => Some(include_bytes!("../data/mods/oppaclient-1.21.10.jar")),
        "1.21.11" => Some(include_bytes!("../data/mods/oppaclient-1.21.11.jar")),
        "26.1" | "26.1.1" | "26.1.2" => Some(include_bytes!("../data/mods/oppaclient-26.1.jar")),
        "26.2" => Some(include_bytes!("../data/mods/oppaclient-26.2.jar")),
        "26.3" => Some(include_bytes!("../data/mods/oppaclient-26.3.jar")),
        _ => None,
    }
}

pub fn place(inst: &Instance) -> anyhow::Result<bool> {
    if inst.loader == Loader::Vanilla {
        return Ok(false);
    }
    let Some(jar) = jar_for(&inst.game_version) else { return Ok(false) };
    let mods = inst.game_dir().join("mods");
    if mods.join(format!("{FILE}.disabled")).exists() {
        return Ok(false);
    }
    let dest = mods.join(FILE);
    if std::fs::read(&dest).is_ok_and(|b| b == jar) {
        return Ok(true);
    }
    std::fs::create_dir_all(&mods)?;
    let tmp = mods.join(format!(".{FILE}.part"));
    std::fs::write(&tmp, jar)?;
    std::fs::rename(tmp, dest)?;
    Ok(true)
}

const PERFORMANCE: [(&str, &str, &str); 5] = [
    ("AANobbMI", "sodium", "Sodium"),
    ("gvQqBUqZ", "lithium", "Lithium"),
    ("uXXizFIs", "ferritecore", "FerriteCore"),
    ("5ZwdcRci", "immediatelyfast", "ImmediatelyFast"),
    ("NNAgCjsB", "entityculling", "Entity Culling"),
];

pub async fn ensure(inst: &Instance) -> anyhow::Result<()> {
    let ours = place(inst)?;
    if inst.loader == Loader::Vanilla {
        return Ok(());
    }
    if ours && !present(inst, FABRIC_API, "fabric-api") {
        modrinth::install(FABRIC_API, "Fabric API", Kind::Mod, inst).await?;
    }
    if inst.performance {
        for (project, needle, title) in PERFORMANCE {
            if present(inst, project, needle) {
                continue;
            }
            if let Err(e) = modrinth::install(project, title, Kind::Mod, inst).await {
                eprintln!("skipping {title}: {e:#}");
            }
        }
    }
    Ok(())
}

fn present(inst: &Instance, project: &str, needle: &str) -> bool {
    if inst.installed().contains_key(project) {
        return true;
    }
    std::fs::read_dir(inst.game_dir().join("mods"))
        .map(|d| {
            d.flatten().any(|e| {
                let name = e.file_name().to_string_lossy().to_lowercase().replace(['-', '_', ' '], "");
                name.starts_with(needle)
            })
        })
        .unwrap_or(false)
}
