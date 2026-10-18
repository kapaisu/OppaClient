# Oppa

A Minecraft launcher for Linux and Windows, with its own in-game mod.

## Launcher

- Microsoft sign-in that stays signed in
- Vanilla, Fabric and Quilt instances
- Mods, shaders and resource packs from Modrinth
- Files tab for mods, shaders, resource packs, schematics, worlds and screenshots, with search
- Skin and cape changer with a 3D preview
- Friends: see where they're playing and join them
- Change an instance's version and bring your mods, settings and servers along
- Crash reports you can send to the Oppa team
- Custom background

## Mod

Fabric 1.21.1, 1.21.4, 1.21.10, 1.21.11, 26.1 to 26.1.2, 26.2 and 26.3. The launcher installs it into matching instances.

- Draggable, resizable HUD with keystrokes, CPS, reach, combo and more
- Block overlay, zoom, free look and a custom crosshair
- Capes from your own images, hats, wings and auras that other Oppa players see
- Friends, invites and one-click joining
- Share a screenshot as a link with one key
- Right Shift opens the menu

## Install

Arch Linux: add this to `/etc/pacman.conf`

```
[oppa]
SigLevel = Optional TrustAll
Server = https://github.com/kapaisu/OppaClient/releases/download/repo
```

then

```
sudo pacman -Sy oppa
```

Flatpak: add the Oppa source once

```
flatpak remote-add --user --if-not-exists oppa https://kapaisu.github.io/OppaClient/oppa.flatpakrepo
```

then search for Oppa in GNOME Software or KDE Discover, or run

```
flatpak install --user oppa dev.oppa.Launcher
```

Ubuntu 26.04 or newer: download `oppa_<version>_amd64.deb` from the latest release, then

```
sudo apt install ./oppa_*_amd64.deb
```

Windows: download `Oppa-Setup.exe` from the latest release and run it.

## Build

Needs `rust`, `jdk25-openjdk`, `gtk4`, `libadwaita` and `webkitgtk-6.0`.

```
makepkg -si
```

Made by kapaisu (q3c on Discord). Source available, see [LICENSE](LICENSE).
