use std::rc::Rc;

use adw::prelude::*;
use gtk::{gio, glib};

use crate::instance::data_dir;
use crate::net;
use crate::window::{self, App, DEFAULT_DIM};

const FILE: &str = "background.png";

pub fn open(app: &Rc<App>) {
    let s = window::settings();

    let thumb = gtk::Picture::builder()
        .content_fit(gtk::ContentFit::Cover)
        .can_shrink(true)
        .css_classes(["thumb"])
        .overflow(gtk::Overflow::Hidden)
        .build();
    thumb.set_paintable(app.backdrop.paintable().as_ref());
    let frame = adw::Clamp::builder()
        .maximum_size(76)
        .tightening_threshold(76)
        .child(&thumb)
        .valign(gtk::Align::Center)
        .margin_top(8)
        .margin_bottom(8)
        .build();

    let image = adw::ActionRow::builder()
        .title("Image")
        .subtitle(if s.background.is_some() { "Your image" } else { "Forest" })
        .build();
    image.add_prefix(&frame);
    let choose = gtk::Button::builder().label("Choose…").valign(gtk::Align::Center).css_classes(["flat"]).build();
    let reset = gtk::Button::builder().label("Reset").valign(gtk::Align::Center).css_classes(["flat"]).sensitive(s.background.is_some()).build();
    image.add_suffix(&choose);
    image.add_suffix(&reset);

    let dim = adw::SpinRow::builder()
        .title("Dim")
        .subtitle("Percent")
        .adjustment(&gtk::Adjustment::new(s.dim.unwrap_or(DEFAULT_DIM) * 100.0, 0.0, 90.0, 5.0, 10.0, 0.0))
        .build();

    let group = adw::PreferencesGroup::builder().title("Background").build();
    group.add(&image);
    group.add(&dim);
    let page = adw::PreferencesPage::new();
    page.add(&group);
    let dialog = adw::PreferencesDialog::builder().title("Preferences").build();
    dialog.add(&page);

    dim.connect_value_notify(glib::clone!(
        #[weak]
        app,
        move |row| {
            let mut s = window::settings();
            s.dim = Some(row.value() / 100.0);
            window::save_settings(&s);
            app.apply_background();
        }
    ));
    reset.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        image,
        #[weak]
        thumb,
        move |b| {
            let mut s = window::settings();
            s.background = None;
            window::save_settings(&s);
            let _ = std::fs::remove_file(data_dir().join(FILE));
            app.apply_background();
            thumb.set_paintable(app.backdrop.paintable().as_ref());
            image.set_subtitle("Forest");
            b.set_sensitive(false);
        }
    ));
    choose.connect_clicked(glib::clone!(
        #[weak]
        app,
        #[weak]
        image,
        #[weak]
        thumb,
        #[weak]
        reset,
        move |_| {
            let filter = gtk::FileFilter::new();
            filter.add_mime_type("image/*");
            filter.set_name(Some("Images"));
            let filters = gio::ListStore::new::<gtk::FileFilter>();
            filters.append(&filter);
            let picker = gtk::FileDialog::builder().title("Choose a Background").modal(true).filters(&filters).build();
            picker.open(app.window.root().and_downcast_ref::<gtk::Window>(), gio::Cancellable::NONE, glib::clone!(
                #[weak]
                app,
                #[weak]
                image,
                #[weak]
                thumb,
                #[weak]
                reset,
                move |res| {
                    let Ok(file) = res else { return };
                    let Some(path) = file.path() else { return };
                    glib::spawn_future_local(async move {
                        let res = net::bg(async move { tokio::task::spawn_blocking(move || import(&path)).await? }).await;
                        match res {
                            Ok(()) => {
                                let mut s = window::settings();
                                s.background = Some(FILE.into());
                                window::save_settings(&s);
                                app.apply_background();
                                thumb.set_paintable(app.backdrop.paintable().as_ref());
                                image.set_subtitle("Your image");
                                reset.set_sensitive(true);
                            }
                            Err(e) => app.toast(&format!("Couldn't use that image: {e}")),
                        }
                    });
                }
            ));
        }
    ));
    dialog.present(Some(&app.window));
}

fn import(path: &std::path::Path) -> anyhow::Result<()> {
    let img = image::open(path)?;
    let img = if img.width() > 3840 || img.height() > 2160 { img.resize(3840, 2160, image::imageops::FilterType::Lanczos3) } else { img };
    std::fs::create_dir_all(data_dir())?;
    let tmp = data_dir().join("background.png.part");
    img.to_rgb8().save_with_format(&tmp, image::ImageFormat::Png)?;
    std::fs::rename(tmp, data_dir().join(FILE))?;
    Ok(())
}
