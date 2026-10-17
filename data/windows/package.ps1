param([string]$Gtk = "C:\gtk", [string]$Out = "dist\windows\Oppa")

$ErrorActionPreference = "Stop"
Remove-Item -Recurse -Force $Out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$Out\bin", "$Out\lib", "$Out\share\glib-2.0", "$Out\share\icons", "$Out\etc", "$Out\fonts" | Out-Null

Copy-Item target\release\oppa.exe "$Out\bin\"
Copy-Item "$Gtk\bin\*.dll" "$Out\bin\"
Copy-Item -Recurse "$Gtk\lib\gdk-pixbuf-2.0" "$Out\lib\"
Copy-Item -Recurse "$Gtk\share\glib-2.0\schemas" "$Out\share\glib-2.0\"
Copy-Item -Recurse "$Gtk\share\icons\Adwaita", "$Gtk\share\icons\hicolor" "$Out\share\icons\"
Copy-Item -Recurse "$Gtk\etc\fonts" "$Out\etc\"
Copy-Item data\windows\fonts\* "$Out\fonts\"
Copy-Item LICENSE "$Out\"

$conf = "$Out\etc\fonts\fonts.conf"
(Get-Content $conf -Raw) -replace '<dir>WINDOWSFONTDIR</dir>', "<dir prefix=`"relative`">../../fonts</dir>`n`t<dir>WINDOWSFONTDIR</dir>" | Set-Content $conf -NoNewline

foreach ($s in 32, 48, 64, 128, 256, 512) {
    $dir = "$Out\share\icons\hicolor\${s}x${s}\apps"
    New-Item -ItemType Directory -Force $dir | Out-Null
    Copy-Item "data\icon-$s.png" "$dir\dev.oppa.Launcher.png"
}
