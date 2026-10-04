#!/bin/sh
# Installed-system additive update only. Installer/Live deliberately unchanged.
set -eu
[ "$(id -u)" = 0 ] || { echo 'Run with sudo'; exit 1; }
. /etc/os-release
[ "$ID" = ubuntu ] && [ "$VERSION_ID" = 24.04 ]
source_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
python3 - "$source_dir" <<'PY'
import ast,sys,pathlib,gi,ctypes
gi.require_version('Gst','1.0')
from gi.repository import Gst
Gst.init(None)
assert Gst.ElementFactory.find('x264enc'), 'x264enc missing'
assert Gst.ElementFactory.find('appsrc'), 'appsrc missing'
ctypes.CDLL('libGL.so.1'); ctypes.CDLL('libXtst.so.6')
for name in ('uavf-encoded-display.py','uavf-encoded-capture.py','winavf-frame-bridge.py'):
    ast.parse(pathlib.Path(sys.argv[1],name).read_text())
PY
systemd-analyze verify "$source_dir/uavf-encoded.socket" "$source_dir/uavf-encoded@.service"
dest=/usr/local/lib/uavf-encoded
backup=/var/lib/uavf/encoded-backups/$(date +%Y%m%d-%H%M%S)
if [ -d "$dest" ]; then
    install -d "$backup"
    cp -a "$dest" "$backup/runtime"
    for unit in uavf-encoded.socket uavf-encoded@.service; do
        [ ! -f "/etc/systemd/system/$unit" ] || cp -a "/etc/systemd/system/$unit" "$backup/"
    done
fi
install -d "$dest"
for name in uavf-encoded-display.py uavf-encoded-capture.py winavf-frame-bridge.py; do
    install -m 0644 "$source_dir/$name" "$dest/$name"
done
for name in uavf-encoded.socket uavf-encoded@.service; do
    install -m 0644 "$source_dir/$name" "/etc/systemd/system/$name"
done
systemctl daemon-reload
systemctl enable --now uavf-encoded.socket
printf '%s\n' 'version=2' 'backend=GPU_I420_X264_UVF1' 'animation_tail=bounded_post_damage_burst' > "$dest/runtime-version"
echo 'ENCODED_INSTALLED_UPDATE=PASS (Live/installer untouched; original raw service retained)'
