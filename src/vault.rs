#[cfg(target_os = "linux")]
mod imp {
    use std::collections::HashMap;
    use std::os::unix::fs::OpenOptionsExt;
    use std::path::PathBuf;

    use crate::instance::data_dir;

    fn file() -> PathBuf {
        data_dir().join("tokens.json")
    }

    fn attrs(uuid: &str) -> [(&'static str, String); 2] {
        [("application", "dev.oppa.Launcher".to_string()), ("uuid", uuid.to_string())]
    }

    async fn keyring() -> anyhow::Result<oo7::Keyring> {
        let k = oo7::Keyring::new().await?;
        k.unlock().await?;
        Ok(k)
    }

    fn read_file() -> HashMap<String, String> {
        std::fs::read(file()).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default()
    }

    fn write_file(all: &HashMap<String, String>) -> anyhow::Result<()> {
        std::fs::create_dir_all(data_dir())?;
        let mut f = std::fs::OpenOptions::new().write(true).create(true).truncate(true).mode(0o600).open(file())?;
        std::io::Write::write_all(&mut f, &serde_json::to_vec(all)?)?;
        Ok(())
    }

    pub async fn put(uuid: &str, label: &str, blob: &str) -> anyhow::Result<()> {
        let saved = async {
            keyring().await?.create_item(label, &attrs(uuid), blob, true).await?;
            anyhow::Ok(())
        }
        .await;
        if let Err(e) = saved {
            eprintln!("keyring unavailable ({e}), storing tokens in {}", file().display());
            let mut all = read_file();
            all.insert(uuid.to_string(), blob.to_string());
            write_file(&all)?;
        }
        Ok(())
    }

    pub async fn get(uuid: &str) -> anyhow::Result<Option<String>> {
        if let Ok(k) = keyring().await {
            if let Some(item) = k.search_items(&attrs(uuid)).await?.into_iter().next() {
                return Ok(Some(String::from_utf8(item.secret().await?.as_bytes().to_vec())?));
            }
        }
        Ok(read_file().remove(uuid))
    }

    pub async fn remove(uuid: &str) -> anyhow::Result<()> {
        if let Ok(k) = keyring().await {
            k.delete(&attrs(uuid)).await?;
        }
        let mut all = read_file();
        if all.remove(uuid).is_some() {
            write_file(&all)?;
        }
        Ok(())
    }
}

#[cfg(windows)]
mod imp {
    use std::collections::HashMap;
    use std::path::PathBuf;

    use anyhow::bail;
    use windows_sys::Win32::Foundation::LocalFree;
    use windows_sys::Win32::Security::Cryptography::{CRYPT_INTEGER_BLOB, CryptProtectData, CryptUnprotectData};

    use crate::instance::data_dir;

    fn file() -> PathBuf {
        data_dir().join("tokens.dat")
    }

    fn crypt(data: &[u8], seal: bool) -> anyhow::Result<Vec<u8>> {
        let input = CRYPT_INTEGER_BLOB { cbData: data.len() as u32, pbData: data.as_ptr() as *mut u8 };
        let mut out = CRYPT_INTEGER_BLOB { cbData: 0, pbData: std::ptr::null_mut() };
        let ok = unsafe {
            if seal {
                CryptProtectData(&input, std::ptr::null(), std::ptr::null(), std::ptr::null(), std::ptr::null(), 0, &mut out)
            } else {
                CryptUnprotectData(&input, std::ptr::null_mut(), std::ptr::null(), std::ptr::null(), std::ptr::null(), 0, &mut out)
            }
        };
        if ok == 0 {
            bail!("Windows couldn't {} the saved sign-in", if seal { "encrypt" } else { "decrypt" });
        }
        let bytes = unsafe { std::slice::from_raw_parts(out.pbData, out.cbData as usize).to_vec() };
        unsafe { LocalFree(out.pbData as _) };
        Ok(bytes)
    }

    fn read() -> HashMap<String, String> {
        std::fs::read(file())
            .ok()
            .and_then(|b| crypt(&b, false).ok())
            .and_then(|b| serde_json::from_slice(&b).ok())
            .unwrap_or_default()
    }

    fn write(all: &HashMap<String, String>) -> anyhow::Result<()> {
        std::fs::create_dir_all(data_dir())?;
        let sealed = crypt(&serde_json::to_vec(all)?, true)?;
        let tmp = file().with_extension("dat.part");
        std::fs::write(&tmp, sealed)?;
        std::fs::rename(tmp, file())?;
        Ok(())
    }

    pub async fn put(uuid: &str, _label: &str, blob: &str) -> anyhow::Result<()> {
        let mut all = read();
        all.insert(uuid.to_string(), blob.to_string());
        write(&all)
    }

    pub async fn get(uuid: &str) -> anyhow::Result<Option<String>> {
        Ok(read().remove(uuid))
    }

    pub async fn remove(uuid: &str) -> anyhow::Result<()> {
        let mut all = read();
        if all.remove(uuid).is_some() {
            write(&all)?;
        }
        Ok(())
    }
}

pub use imp::{get, put, remove};
