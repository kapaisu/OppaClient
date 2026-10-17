#![cfg_attr(windows, windows_subsystem = "windows")]

mod auth;
mod browse;
mod bundled;
mod dialogs;
mod discord;
mod game;
mod images;
mod instance;
mod model;
mod modrinth;
mod net;
mod places;
mod play;
mod prefs;
mod runtime;
mod site;
mod skin;
mod update;
mod vault;
mod wardrobe;
mod window;

use adw::prelude::*;

#[cfg(windows)]
fn text_setup() {
    unsafe extern "C" {
        fn _putenv_s(name: *const std::ffi::c_char, value: *const std::ffi::c_char) -> i32;
    }
    unsafe { _putenv_s(c"PANGOCAIRO_BACKEND".as_ptr(), c"fc".as_ptr()) };
}

fn main() -> gtk::glib::ExitCode {
    #[cfg(windows)]
    text_setup();
    let app = adw::Application::builder().application_id("dev.oppa.Launcher").build();
    app.connect_startup(|_| {
        gtk::Window::set_default_icon_name("dev.oppa.Launcher");
        #[cfg(windows)]
        if let Some(settings) = gtk::Settings::default() {
            settings.set_gtk_font_name(Some("Adwaita Sans 11"));
        }
        adw::StyleManager::default().set_color_scheme(adw::ColorScheme::ForceDark);
        let css = gtk::CssProvider::new();
        css.load_from_string(include_str!("style.css"));
        gtk::style_context_add_provider_for_display(
            &gtk::gdk::Display::default().expect("display"),
            &css,
            gtk::STYLE_PROVIDER_PRIORITY_APPLICATION,
        );
    });
    app.connect_activate(|app| match app.active_window() {
        Some(w) => w.present(),
        None => window::build(app),
    });
    app.run()
}
