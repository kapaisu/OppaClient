#!/bin/sh
set -eu

version=$(sed -n 's/^version = "\(.*\)"/\1/p' Cargo.toml | head -n1)
arch=$(dpkg --print-architecture)
root=dist/deb/oppa
rm -rf dist/deb
mkdir -p "$root/DEBIAN"

install -Dm755 target/release/oppa "$root/usr/bin/oppa"
install -Dm644 LICENSE "$root/usr/share/doc/oppa/copyright"
install -Dm644 data/dev.oppa.Launcher.metainfo.xml "$root/usr/share/metainfo/dev.oppa.Launcher.metainfo.xml"
install -Dm644 data/dev.oppa.Launcher.desktop "$root/usr/share/applications/dev.oppa.Launcher.desktop"
for size in 32 48 64 128 256 512; do
  install -Dm644 "data/icon-$size.png" "$root/usr/share/icons/hicolor/${size}x${size}/apps/dev.oppa.Launcher.png"
done

mkdir -p debian
printf 'Source: oppa\n\nPackage: oppa\nArchitecture: any\n' > debian/control
depends=$(dpkg-shlibdeps -O -e"$root/usr/bin/oppa" 2>/dev/null | sed -n 's/^shlibs:Depends=//p' \
  | sed 's/libgtk-4-1 ([^)]*)/libgtk-4-1 (>= 4.20)/; s/libadwaita-1-0 ([^)]*)/libadwaita-1-0 (>= 1.8)/')
rm -rf debian

cat > "$root/DEBIAN/control" <<CONTROL
Package: oppa
Version: $version
Architecture: $arch
Maintainer: kapaisu <250955472+kapaisu@users.noreply.github.com>
Installed-Size: $(du -sk "$root/usr" | cut -f1)
Depends: $depends
Recommends: gnome-keyring
Section: games
Priority: optional
Homepage: https://oppaclient.com
Description: Minecraft launcher
 Oppa is a Minecraft launcher with its own in-game mod.
CONTROL

dpkg-deb --root-owner-group -Zxz --build "$root" "dist/oppa_${version}_${arch}.deb"
