#!/bin/sh
set -eu
# Remove only this experiment's unit; retain guest evidence and private ICD.
sudo systemctl disable --now uavf-vulkan-ring-probe.service
sudo rm -f /etc/systemd/system/uavf-vulkan-ring-probe.service
sudo systemctl daemon-reload
sudo systemctl reset-failed uavf-vulkan-ring-probe.service 2>/dev/null || true
echo RING_PROBE_TEMPORARY_UNIT_REMOVED
glxinfo -B
curl -fsS --data-binary @"$HOME/.local/state/uavf/vulkan-probe/aligned-ring-result.json" http://10.159.93.215:39054/report
