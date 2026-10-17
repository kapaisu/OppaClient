use std::cell::RefCell;
use std::collections::HashMap;
use std::path::PathBuf;

use gtk::prelude::*;
use gtk::{gdk, glib};

use crate::net;

struct Rgba {
    w: u32,
    h: u32,
    px: Vec<u8>,
}

fn texture(img: Rgba) -> gdk::Texture {
    let bytes = glib::Bytes::from_owned(img.px);
    gdk::MemoryTexture::new(img.w as i32, img.h as i32, gdk::MemoryFormat::R8g8b8a8, &bytes, img.w as usize * 4)
        .upcast()
}

fn decode(bytes: &[u8], max: u32) -> anyhow::Result<Rgba> {
    let img = image::load_from_memory(bytes)?;
    let img = if img.width() > max || img.height() > max { img.thumbnail(max, max) } else { img };
    let rgba = img.to_rgba8();
    Ok(Rgba { w: rgba.width(), h: rgba.height(), px: rgba.into_raw() })
}

thread_local! {
    static ICONS: RefCell<HashMap<String, gdk::Texture>> = RefCell::new(HashMap::new());
}

pub fn icon(url: &str, pic: &gtk::Image) {
    if let Some(t) = ICONS.with(|c| c.borrow().get(url).cloned()) {
        pic.set_paintable(Some(&t));
        return;
    }
    let url = url.to_string();
    let weak = pic.downgrade();
    glib::spawn_future_local(async move {
        let u = url.clone();
        let res = net::bg(async move {
            let bytes = net::get_bytes(&u).await?;
            tokio::task::spawn_blocking(move || decode(&bytes, 128)).await?
        })
        .await;
        let Ok(img) = res else { return };
        let t = texture(img);
        ICONS.with(|c| c.borrow_mut().insert(url, t.clone()));
        if let Some(pic) = weak.upgrade() {
            pic.set_paintable(Some(&t));
        }
    });
}

pub fn thumbnail(path: PathBuf, pic: &gtk::Picture) {
    let weak = pic.downgrade();
    glib::spawn_future_local(async move {
        let res = net::bg(async move {
            tokio::task::spawn_blocking(move || decode(&std::fs::read(path)?, 400)).await?
        })
        .await;
        if let (Ok(img), Some(pic)) = (res, weak.upgrade()) {
            pic.set_paintable(Some(&texture(img)));
        }
    });
}

pub fn face_from_skin(png: &[u8]) -> Option<[u8; 256]> {
    let skin = image::load_from_memory(png).ok()?.to_rgba8();
    if skin.width() < 64 || skin.height() < 32 {
        return None;
    }
    let mut out = [0u8; 256];
    for y in 0..8 {
        for x in 0..8 {
            let base = skin.get_pixel(8 + x, 8 + y).0;
            let hat = skin.get_pixel(40 + x, 8 + y).0;
            let a = hat[3] as u32;
            let i = ((y * 8 + x) * 4) as usize;
            for c in 0..3 {
                out[i + c] = ((hat[c] as u32 * a + base[c] as u32 * (255 - a)) / 255) as u8;
            }
            out[i + 3] = 255;
        }
    }
    Some(out)
}

pub fn face_widget(size: i32) -> (gtk::DrawingArea, std::rc::Rc<RefCell<Option<[u8; 256]>>>) {
    let area = gtk::DrawingArea::builder().content_width(size).content_height(size).valign(gtk::Align::Center).build();
    let face: std::rc::Rc<RefCell<Option<[u8; 256]>>> = Default::default();
    let f = face.clone();
    area.set_draw_func(move |area, cr, w, h| {
        let px = w.min(h) as f64 / 8.0;
        let r = (w.min(h) as f64 * 0.18).max(2.0);
        rounded(cr, w as f64, h as f64, r);
        cr.clip();
        match &*f.borrow() {
            Some(face) => {
                for y in 0..8 {
                    for x in 0..8 {
                        let i = (y * 8 + x) * 4;
                        cr.set_source_rgb(face[i] as f64 / 255.0, face[i + 1] as f64 / 255.0, face[i + 2] as f64 / 255.0);
                        cr.rectangle(x as f64 * px, y as f64 * px, px + 0.5, px + 0.5);
                        let _ = cr.fill();
                    }
                }
            }
            None => {
                let c = area.color();
                cr.set_source_rgba(c.red() as f64, c.green() as f64, c.blue() as f64, 0.12);
                let _ = cr.paint();
            }
        }
    });
    (area, face)
}

fn rounded(cr: &gtk::cairo::Context, w: f64, h: f64, r: f64) {
    use std::f64::consts::PI;
    cr.new_sub_path();
    cr.arc(w - r, r, r, -PI / 2.0, 0.0);
    cr.arc(w - r, h - r, r, 0.0, PI / 2.0);
    cr.arc(r, h - r, r, PI / 2.0, PI);
    cr.arc(r, r, r, PI, 1.5 * PI);
    cr.close_path();
}

pub fn skin_cache(uuid: &str) -> PathBuf {
    crate::instance::cache_dir().join("skins").join(format!("{uuid}.png"))
}

pub async fn load_face(uuid: String, url: Option<String>) -> Option<[u8; 256]> {
    let path = skin_cache(&uuid);
    let cached = std::fs::read(&path).ok();
    if let Some(url) = url {
        let fresh = net::bg(async move {
            let b = net::get_bytes(&url).await.ok()?;
            let _ = std::fs::create_dir_all(path.parent()?);
            let _ = std::fs::write(&path, &b);
            Some(b)
        })
        .await;
        if let Some(b) = fresh {
            return face_from_skin(&b);
        }
    }
    face_from_skin(&cached?)
}
