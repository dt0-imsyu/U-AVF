#!/bin/sh
# Additive, idempotent extension: no boot/storage/display modifications.
set -eu
[ "$(id -u)" = 0 ] || { echo 'Root service installation required'; exit 1; }
src=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
for name in uavf-control.py uavf-control.socket uavf-control@.service; do
  [ -s "$src/$name" ] || { echo "Missing $name"; exit 1; }
done
python3 -m py_compile "$src/uavf-control.py"
systemd-analyze verify "$src/uavf-control.socket" "$src/uavf-control@.service"
install -m 0755 "$src/uavf-control.py" /usr/local/sbin/uavf-control.py
install -m 0644 "$src/uavf-control.socket" /etc/systemd/system/uavf-control.socket
install -m 0644 "$src/uavf-control@.service" /etc/systemd/system/uavf-control@.service
systemctl daemon-reload
systemctl enable --now uavf-control.socket
systemctl is-active --quiet uavf-control.socket
echo 'UAVF_CONTROL_SOCKET=ACTIVE PORT=4056 VERSION=1'
