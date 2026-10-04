#!/bin/sh
# Additive runtime extension only. No disk/boot/GDM/display changes.
set -eu
[ "$(id -u)" = 0 ] || { echo 'Run inside Ubuntu with sudo'; exit 1; }
src=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
python3 -c 'import gi; gi.require_version("Gtk", "3.0"); from gi.repository import Gtk'
[ -s /usr/local/sbin/winavf-frame-bridge.py ]
for file in uavf-clipboard.py uavf-clipboard.socket uavf-clipboard@.service; do
    [ -s "$src/$file" ]
done
# Refuse to overwrite a different pre-existing add-on silently.
for pair in 'uavf-clipboard.py:/usr/local/sbin/uavf-clipboard.py' \
    'uavf-clipboard.socket:/etc/systemd/system/uavf-clipboard.socket' \
    'uavf-clipboard@.service:/etc/systemd/system/uavf-clipboard@.service'; do
    name=${pair%%:*}; destination=${pair#*:}
    if [ -e "$destination" ]; then
        cmp -s "$src/$name" "$destination" || { echo "Existing different add-on: $destination"; exit 1; }
    fi
done
systemd-analyze verify "$src/uavf-clipboard.socket" "$src/uavf-clipboard@.service"
install -m 0755 "$src/uavf-clipboard.py" /usr/local/sbin/uavf-clipboard.py
install -m 0644 "$src/uavf-clipboard.socket" /etc/systemd/system/uavf-clipboard.socket
install -m 0644 "$src/uavf-clipboard@.service" /etc/systemd/system/uavf-clipboard@.service
systemctl daemon-reload
systemctl enable --now uavf-clipboard.socket
systemctl is-active --quiet uavf-clipboard.socket
echo 'UAVF_CLIPBOARD_SOCKET=ACTIVE PORT=4054'
