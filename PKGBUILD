pkgname=oppa
pkgver=0.1.0
pkgrel=2
pkgdesc='Minecraft launcher'
arch=(x86_64 aarch64)
license=(custom)
depends=(gtk4 libadwaita webkitgtk-6.0)
makedepends=(cargo jdk25-openjdk)
options=(!lto !debug)
optdepends=(
  'jre25-openjdk: Minecraft 26.x'
  'jre21-openjdk: Minecraft 1.20.5 to 1.21.x'
  'jre17-openjdk: Minecraft 1.17 to 1.20.4'
  'jre8-openjdk: Minecraft 1.16 and older'
  'gnome-keyring: keep sign-ins in the system keyring'
)

build() {
  cd "$startdir/mod"
  ./gradlew bundle --no-daemon --console=plain
  cd "$startdir"
  cargo build --release --locked
}

package() {
  cd "$startdir"
  install -Dm755 target/release/oppa "$pkgdir/usr/bin/oppa"
  install -Dm644 LICENSE "$pkgdir/usr/share/licenses/oppa/LICENSE"
  install -Dm644 data/dev.oppa.Launcher.desktop "$pkgdir/usr/share/applications/dev.oppa.Launcher.desktop"
  for size in 32 48 64 128 256 512; do
    install -Dm644 "data/icon-$size.png" "$pkgdir/usr/share/icons/hicolor/${size}x${size}/apps/dev.oppa.Launcher.png"
  done
}
