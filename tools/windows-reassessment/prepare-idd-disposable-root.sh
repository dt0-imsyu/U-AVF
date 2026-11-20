#!/bin/sh
# Fresh official image, not a clone. Only a new file-backed loop device is formatted.
set -eu
task_output=/home/denis/uavf-build/idd-fresh-root-20261013
task_payload=/mnt/c/Users/denis/MainProjects/win11ontab/build-logs/windows-post-linux-reassessment-20261004/install-payload-udf-20261008
test "$(id -u)" = 0
test ! -e "$task_output"
test -f "$task_payload/install.swm"
test -f "$task_payload/install2.swm"
command -v mkntfs >/dev/null
command -v wimlib-imagex >/dev/null
test "$(df -B1 --output=avail /mnt/c | tail -1)" -gt 70866960384
# Match the existing 64GiB-target partition's exact length, not its contents.
mkdir -p /home/denis/uavf-build
mkdir "$task_output"
truncate -s 68157440000 "$task_output/root.ntfs"
printf 'FORMATTING_ONLY_NEW_REGULAR_FILE_VOLUME=%s\n' "$task_output/root.ntfs"
# Do not loop-mount DrvFS files: observed fsync/writeback stall on this host.
mkntfs -Q -F -L UAVF_IDD_TEST -s 512 -c 4096 -p 17858560 -H 255 -S 63 "$task_output/root.ntfs"
python3 /mnt/c/Users/denis/MainProjects/win11ontab/Non-root-Windows-VM/tools/windows-reassessment/apply-idd-fresh-root.py
sync "$task_output/root.ntfs"
printf 'OFFICIAL_FRESH_ROOT_APPLIED; NOT_BOOT_OR_DRIVER_OR_FPS_PASS\n'
du -k "$task_output/root.ntfs"
