#!/bin/sh
# Optional per-user add-on, not a boot/install/display migration.
set -eu
[ "$(id -u)" -ne 0 ] || { echo 'Run from the logged-in Ubuntu user terminal, not sudo'; exit 1; }
src=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
dest="$HOME/.local/lib/uavf-clipboard"
python3 -c 'import gi; gi.require_version("Gtk", "3.0"); from gi.repository import Gtk'
python3 -m py_compile "$src/uavf-clipboard.py"
mkdir -p "$dest" "$HOME/.config/autostart"
if [ "$src/uavf-clipboard.py" != "$dest/uavf-clipboard.py" ]; then
    if [ -e "$dest/uavf-clipboard.py" ]; then
        cmp -s "$src/uavf-clipboard.py" "$dest/uavf-clipboard.py" || {
            echo 'Different existing clipboard agent; do not overwrite silently'; exit 1;
        }
    else
        install -m 0644 "$src/uavf-clipboard.py" "$dest/uavf-clipboard.py"
    fi
fi
desktop="$HOME/.config/autostart/uavf-clipboard.desktop"
tmp="$desktop.tmp.$$"
printf '[Desktop Entry]\nType=Application\nName=U-AVF text clipboard\nExec=/usr/bin/python3 "%s/uavf-clipboard.py" --listen\nTerminal=false\nNoDisplay=true\nX-GNOME-Autostart-enabled=true\n' "$dest" > "$tmp"
if [ -e "$desktop" ] && ! cmp -s "$desktop" "$tmp"; then
    rm -f "$tmp"
    echo 'Different existing clipboard autostart; do not overwrite silently'; exit 1
fi
chmod 0644 "$tmp"
mv -f "$tmp" "$desktop"
echo 'UAVF_CLIPBOARD_USER_AUTOSTART=INSTALLED'
