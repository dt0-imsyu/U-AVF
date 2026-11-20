#!/bin/sh
set -eu
sudo journalctl -b -1 --no-pager -u uavf-vulkan-ring-probe.service > /tmp/uavf-ring-journal.txt
ls -l /dev/dri >> /tmp/uavf-ring-journal.txt
id >> /tmp/uavf-ring-journal.txt
curl -fsS --data-binary @/tmp/uavf-ring-journal.txt http://10.159.93.215:39054/report
