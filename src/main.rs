mod auth;
mod browse;
mod bundled;
mod dialogs;
mod game;
mod images;
mod instance;
mod model;
mod modrinth;
mod net;
mod places;
mod play;
mod prefs;
mod skin;
mod wardrobe;
mod window;

use adw::prelude::*;

fn main() -> gtk::glib::ExitCode {
    let app = adw::Application::builder().application_id("dev.oppa.Launcher").build();
    app.connect_startup(|_| {
        gtk::Window::set_default_icon_name("dev.oppa.Launcher");
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
