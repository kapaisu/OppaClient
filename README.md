# Oppa

A Minecraft launcher for Arch Linux, with its own in-game mod.

## Launcher

- Microsoft sign-in that stays signed in
- Vanilla, Fabric and Quilt instances
- Mods, shaders and resource packs from Modrinth
- Files tab for mods, shaders, resource packs, schematics, worlds and screenshots
- Skin and cape changer with a 3D preview
- Custom background

## Mod

Fabric 1.21.1, 1.21.4, 1.21.10 and 1.21.11. The launcher installs it into matching instances.

- Draggable, resizable HUD
- Block overlay, zoom, free look and a custom crosshair
- Right Shift opens the menu

## Build

Needs `rust`, `jdk25-openjdk`, `gtk4`, `libadwaita` and `webkitgtk-6.0`.

```
makepkg -si
```

Made by kapaisu (q3c on Discord).
