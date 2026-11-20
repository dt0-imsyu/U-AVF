#!/bin/sh
set -eu
base="$HOME/.local/state/uavf/vulkan-probe"
mkdir -p "$base"
curl -fsS http://10.159.93.215:39054/gfxstream-ring-probe.py -o "$base/probe.py"
curl -fsS http://10.159.93.215:39054/uavf-vulkan-ring-probe.service -o /tmp/uavf-vulkan-ring-probe.service
sudo install -m 0644 /tmp/uavf-vulkan-ring-probe.service /etc/systemd/system/uavf-vulkan-ring-probe.service
sudo systemctl daemon-reload
sudo systemctl enable uavf-vulkan-ring-probe.service
echo RING_PROBE_BOOT_DIAGNOSTIC_READY
