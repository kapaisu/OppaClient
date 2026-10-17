use gtk::cairo;

#[derive(Clone, Copy)]
struct V3 {
    x: f64,
    y: f64,
    z: f64,
}

impl V3 {
    fn new(x: f64, y: f64, z: f64) -> Self {
        V3 { x, y, z }
    }

    fn sub(self, o: V3) -> V3 {
        V3::new(self.x - o.x, self.y - o.y, self.z - o.z)
    }

    fn add(self, o: V3) -> V3 {
        V3::new(self.x + o.x, self.y + o.y, self.z + o.z)
    }

    fn rot_x(self, a: f64) -> V3 {
        let (s, c) = a.sin_cos();
        V3::new(self.x, self.y * c - self.z * s, self.y * s + self.z * c)
    }

    fn rot_y(self, a: f64) -> V3 {
        let (s, c) = a.sin_cos();
        V3::new(self.x * c + self.z * s, self.y, -self.x * s + self.z * c)
    }

    fn rot_z(self, a: f64) -> V3 {
        let (s, c) = a.sin_cos();
        V3::new(self.x * c - self.y * s, self.x * s + self.y * c, self.z)
    }
}

#[derive(Clone, Copy)]
enum Axis {
    X,
    Y,
    Z,
}

struct Turn {
    axis: Axis,
    angle: f64,
    pivot: V3,
}

impl Turn {
    fn apply(&self, p: V3) -> V3 {
        let r = p.sub(self.pivot);
        let r = match self.axis {
            Axis::X => r.rot_x(self.angle),
            Axis::Y => r.rot_y(self.angle),
            Axis::Z => r.rot_z(self.angle),
        };
        r.add(self.pivot)
    }

    fn apply_dir(&self, d: V3) -> V3 {
        match self.axis {
            Axis::X => d.rot_x(self.angle),
            Axis::Y => d.rot_y(self.angle),
            Axis::Z => d.rot_z(self.angle),
        }
    }
}

struct Cube {
    min: V3,
    max: V3,
    u: f64,
    v: f64,
    w: f64,
    h: f64,
    d: f64,
    mirror: bool,
    overlay: bool,
    cape: bool,
}

struct Face {
    depth: f64,
    origin: (f64, f64),
    e1: (f64, f64),
    e2: (f64, f64),
    rect: (f64, f64, f64, f64),
    overlay: bool,
    cape: bool,
}

pub struct Skin {
    opaque: cairo::ImageSurface,
    full: cairo::ImageSurface,
    legacy: bool,
}

pub struct Model {
    pub skin: Option<Skin>,
    pub cape: Option<cairo::ImageSurface>,
    pub slim: bool,
    pub yaw: f64,
    pub pitch: f64,
}

fn surface(rgba: &image::RgbaImage, force_opaque: bool) -> Option<cairo::ImageSurface> {
    let (w, h) = (rgba.width() as i32, rgba.height() as i32);
    let mut surf = cairo::ImageSurface::create(cairo::Format::ARgb32, w, h).ok()?;
    let stride = surf.stride() as usize;
    {
        let mut data = surf.data().ok()?;
        for (x, y, p) in rgba.enumerate_pixels() {
            let a = if force_opaque { 255 } else { p[3] as u32 };
            let px = (a << 24) | ((p[0] as u32 * a / 255) << 16) | ((p[1] as u32 * a / 255) << 8) | (p[2] as u32 * a / 255);
            let i = y as usize * stride + x as usize * 4;
            data[i..i + 4].copy_from_slice(&px.to_ne_bytes());
        }
    }
    Some(surf)
}

impl Skin {
    pub fn from_png(png: &[u8]) -> Option<Skin> {
        let rgba = image::load_from_memory(png).ok()?.to_rgba8();
        if rgba.width() != 64 || (rgba.height() != 64 && rgba.height() != 32) {
            return None;
        }
        Some(Skin { opaque: surface(&rgba, true)?, full: surface(&rgba, false)?, legacy: rgba.height() == 32 })
    }
}

pub fn cape_from_png(png: &[u8]) -> Option<cairo::ImageSurface> {
    let rgba = image::load_from_memory(png).ok()?.to_rgba8();
    surface(&rgba, false)
}

impl Default for Model {
    fn default() -> Self {
        Model { skin: None, cape: None, slim: false, yaw: 0.45, pitch: 0.12 }
    }
}

fn cube(min: (f64, f64, f64), max: (f64, f64, f64), uv: (f64, f64), inflate: f64, mirror: bool, overlay: bool) -> Cube {
    let (w, h, d) = (max.0 - min.0, max.1 - min.1, max.2 - min.2);
    Cube {
        min: V3::new(min.0 - inflate, min.1 - inflate, min.2 - inflate),
        max: V3::new(max.0 + inflate, max.1 + inflate, max.2 + inflate),
        u: uv.0,
        v: uv.1,
        w,
        h,
        d,
        mirror,
        overlay,
        cape: false,
    }
}

impl Model {
    fn parts(&self, t: f64) -> Vec<(Vec<Turn>, Vec<Cube>)> {
        let legacy = self.skin.as_ref().is_some_and(|s| s.legacy);
        let aw = if self.slim { 3.0 } else { 4.0 };
        let sway = (t * 1.3).sin() * 0.035;
        let swing = (t * 0.9).sin() * 0.05;
        let mut parts = Vec::new();

        parts.push((vec![], vec![
            cube((-4.0, 24.0, -4.0), (4.0, 32.0, 4.0), (0.0, 0.0), 0.0, false, false),
            cube((-4.0, 24.0, -4.0), (4.0, 32.0, 4.0), (32.0, 0.0), 0.5, false, true),
        ]));

        let mut body = vec![cube((-4.0, 12.0, -2.0), (4.0, 24.0, 2.0), (16.0, 16.0), 0.0, false, false)];
        if !legacy {
            body.push(cube((-4.0, 12.0, -2.0), (4.0, 24.0, 2.0), (16.0, 32.0), 0.25, false, true));
        }
        parts.push((vec![], body));

        let right_shoulder = V3::new(-4.0 - aw / 2.0, 22.0, 0.0);
        let mut right_arm = vec![cube((-4.0 - aw, 12.0, -2.0), (-4.0, 24.0, 2.0), (40.0, 16.0), 0.0, false, false)];
        if !legacy {
            right_arm.push(cube((-4.0 - aw, 12.0, -2.0), (-4.0, 24.0, 2.0), (40.0, 32.0), 0.25, false, true));
        }
        parts.push((
            vec![
                Turn { axis: Axis::X, angle: swing, pivot: right_shoulder },
                Turn { axis: Axis::Z, angle: -0.06 - sway, pivot: right_shoulder },
            ],
            right_arm,
        ));

        let left_shoulder = V3::new(4.0 + aw / 2.0, 22.0, 0.0);
        let left_arm = if legacy {
            vec![cube((4.0, 12.0, -2.0), (4.0 + aw, 24.0, 2.0), (40.0, 16.0), 0.0, true, false)]
        } else {
            vec![
                cube((4.0, 12.0, -2.0), (4.0 + aw, 24.0, 2.0), (32.0, 48.0), 0.0, false, false),
                cube((4.0, 12.0, -2.0), (4.0 + aw, 24.0, 2.0), (48.0, 48.0), 0.25, false, true),
            ]
        };
        parts.push((
            vec![
                Turn { axis: Axis::X, angle: -swing, pivot: left_shoulder },
                Turn { axis: Axis::Z, angle: 0.06 + sway, pivot: left_shoulder },
            ],
            left_arm,
        ));

        let mut right_leg = vec![cube((-4.0, 0.0, -2.0), (0.0, 12.0, 2.0), (0.0, 16.0), 0.0, false, false)];
        if !legacy {
            right_leg.push(cube((-4.0, 0.0, -2.0), (0.0, 12.0, 2.0), (0.0, 32.0), 0.25, false, true));
        }
        parts.push((vec![], right_leg));

        let left_leg = if legacy {
            vec![cube((0.0, 0.0, -2.0), (4.0, 12.0, 2.0), (0.0, 16.0), 0.0, true, false)]
        } else {
            vec![
                cube((0.0, 0.0, -2.0), (4.0, 12.0, 2.0), (16.0, 48.0), 0.0, false, false),
                cube((0.0, 0.0, -2.0), (4.0, 12.0, 2.0), (0.0, 48.0), 0.25, false, true),
            ]
        };
        parts.push((vec![], left_leg));

        if self.cape.is_some() {
            let hinge = V3::new(0.0, 24.0, -2.0);
            let mut cape = cube((-5.0, 8.0, -3.0), (5.0, 24.0, -2.0), (0.0, 0.0), 0.0, false, false);
            cape.cape = true;
            parts.push((
                vec![
                    Turn { axis: Axis::Y, angle: std::f64::consts::PI, pivot: V3::new(0.0, 16.0, -2.5) },
                    Turn { axis: Axis::X, angle: 0.16 + (t * 0.8).sin() * 0.03, pivot: hinge },
                ],
                vec![cape],
            ));
        }
        parts
    }

    fn view(&self, p: V3) -> V3 {
        p.sub(V3::new(0.0, 16.0, 0.0)).rot_y(self.yaw).rot_x(self.pitch)
    }

    pub fn draw(&self, cr: &cairo::Context, w: f64, h: f64, t: f64) {
        let Some(skin) = &self.skin else { return };
        let scale = (h * 0.8 / 34.0).min(w * 0.8 / 20.0);
        let (cx, cy) = (w / 2.0, h / 2.0 + scale);
        let screen = |p: V3| (cx + p.x * scale, cy - p.y * scale);

        let mut faces = Vec::with_capacity(96);
        for (turns, cubes) in self.parts(t) {
            let place = |p: V3| self.view(turns.iter().fold(p, |p, t| t.apply(p)));
            let dir = |d: V3| turns.iter().fold(d, |d, t| t.apply_dir(d)).rot_y(self.yaw).rot_x(self.pitch);
            for c in &cubes {
                let (x0, y0, z0, x1, y1, z1) = (c.min.x, c.min.y, c.min.z, c.max.x, c.max.y, c.max.z);
                let (u, v, w, hh, d) = (c.u, c.v, c.w, c.h, c.d);
                let side_a = (u, v + d, d, hh);
                let side_b = (u + d + w, v + d, d, hh);
                let (right_rect, left_rect) = if c.mirror { (side_b, side_a) } else { (side_a, side_b) };
                let list = [
                    (V3::new(0.0, 0.0, 1.0), V3::new(x0, y1, z1), V3::new(x1, y1, z1), V3::new(x0, y0, z1), (u + d, v + d, w, hh)),
                    (V3::new(0.0, 0.0, -1.0), V3::new(x1, y1, z0), V3::new(x0, y1, z0), V3::new(x1, y0, z0), (u + d + w + d, v + d, w, hh)),
                    (V3::new(-1.0, 0.0, 0.0), V3::new(x0, y1, z0), V3::new(x0, y1, z1), V3::new(x0, y0, z0), right_rect),
                    (V3::new(1.0, 0.0, 0.0), V3::new(x1, y1, z1), V3::new(x1, y1, z0), V3::new(x1, y0, z1), left_rect),
                    (V3::new(0.0, 1.0, 0.0), V3::new(x0, y1, z0), V3::new(x1, y1, z0), V3::new(x0, y1, z1), (u + d, v, w, d)),
                    (V3::new(0.0, -1.0, 0.0), V3::new(x0, y0, z1), V3::new(x1, y0, z1), V3::new(x0, y0, z0), (u + d + w, v, w, d)),
                ];
                for (normal, o, pu, pv, rect) in list {
                    if dir(normal).z <= 1e-4 {
                        continue;
                    }
                    let (o, pu, pv) = if c.mirror { (pu, o, pv.add(pu.sub(o))) } else { (o, pu, pv) };
                    let (so, su, sv) = (screen(place(o)), screen(place(pu)), screen(place(pv)));
                    let centre = place(V3::new((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0).add(normal));
                    faces.push(Face {
                        depth: centre.z,
                        origin: so,
                        e1: (su.0 - so.0, su.1 - so.1),
                        e2: (sv.0 - so.0, sv.1 - so.1),
                        rect,
                        overlay: c.overlay,
                        cape: c.cape,
                    });
                }
            }
        }
        faces.sort_by(|a, b| a.depth.total_cmp(&b.depth));

        for f in faces {
            let source = if f.cape {
                match &self.cape {
                    Some(c) => c,
                    None => continue,
                }
            } else if f.overlay {
                &skin.full
            } else {
                &skin.opaque
            };
            let (u0, v0, tw, th) = f.rect;
            if tw <= 0.0 || th <= 0.0 {
                continue;
            }
            cr.save().ok();
            cr.transform(cairo::Matrix::new(f.e1.0 / tw, f.e1.1 / tw, f.e2.0 / th, f.e2.1 / th, f.origin.0, f.origin.1));
            cr.rectangle(0.0, 0.0, tw, th);
            if cr.set_source_surface(source, -u0, -v0).is_ok() {
                cr.source().set_filter(cairo::Filter::Nearest);
                let _ = cr.fill();
            }
            cr.restore().ok();
        }
    }
}

pub fn draw_cape_front(cr: &cairo::Context, cape: &cairo::ImageSurface, w: f64, h: f64) {
    let scale = (w / 10.0).min(h / 16.0);
    let (ox, oy) = ((w - 10.0 * scale) / 2.0, (h - 16.0 * scale) / 2.0);
    cr.save().ok();
    cr.translate(ox, oy);
    cr.scale(scale, scale);
    cr.rectangle(0.0, 0.0, 10.0, 16.0);
    if cr.set_source_surface(cape, -1.0, -1.0).is_ok() {
        cr.source().set_filter(cairo::Filter::Nearest);
        let _ = cr.fill();
    }
    cr.restore().ok();
}
