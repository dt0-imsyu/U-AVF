#!/bin/sh
# Fail closed before Subiquity is allowed to format anything. This profile is
# intentionally tied to the GPT that PersistentUbuntuDisk.create() produces.
set -eu

say() {
  printf 'UAVF_INSTALL_PREFLIGHT=%s\n' "$1" >/dev/console 2>/dev/null || true
}

fail() {
  say "FAIL $1"
  exit 1
}

[ -b /dev/vda ] || fail DISK_VDA_MISSING
disk_bytes=$(blockdev --getsize64 /dev/vda)
root_bytes=$(blockdev --getsize64 /dev/vda3)
case "$disk_bytes,$root_bytes" in *[!0-9,]*) fail INVALID_SIZE ;; esac
[ "$root_bytes" -ge 34359738368 ] && [ "$root_bytes" -le 1099511627776 ] \
    && [ $((root_bytes % 1048576)) -eq 0 ] || fail ROOT_SIZE_MISMATCH
[ $((disk_bytes % 512)) -eq 0 ] || fail DISK_SIZE_MISMATCH
tail_sectors=$((disk_bytes / 512 - 8011776 - root_bytes / 512))
[ "$tail_sectors" -ge 34 ] && [ "$tail_sectors" -le 2081 ] || fail DISK_SIZE_MISMATCH
[ "$(lsblk -dnro PTTYPE /dev/vda | tr '[:upper:]' '[:lower:]')" = 'gpt' ] || fail GPT_TYPE_MISMATCH
[ "$(blockdev --getsize64 /dev/vda1)" = '132120576' ] || fail ESP_SIZE_MISMATCH
[ "$(blockdev --getsize64 /dev/vda2)" = '3967463424' ] || fail ISO_SIZE_MISMATCH

geometry() {
  # util-linux versions shipped in Ubuntu 24.04 expose START but not a
  # SECTORS column. The kernel sysfs partition attributes are stable and both
  # values are expressed in 512-byte sectors, matching the GPT audit below.
  device=${1##*/}
  node="/sys/class/block/$device"
  [ -r "$node/start" ] && [ -r "$node/size" ] || return 1
  start=$(cat "$node/start")
  sectors=$(cat "$node/size")
  case "$start" in
    ''|*[!0-9]*) return 1 ;;
  esac
  case "$sectors" in
    ''|*[!0-9]*) return 1 ;;
  esac
  printf '%s,%s\n' "$start" "$sectors"
}

[ "$(geometry /dev/vda1)" = '2048,258048' ] || fail ESP_GEOMETRY_MISMATCH
[ "$(geometry /dev/vda2)" = '262144,7748952' ] || fail ISO_GEOMETRY_MISMATCH
[ "$(geometry /dev/vda3)" = "8011776,$((root_bytes / 512))" ] || fail ROOT_GEOMETRY_MISMATCH

label() {
  # In util-linux raw mode, lsblk escapes spaces in PARTLABEL values as
  # `\x20`. Use the normal unquoted column renderer so the exact GPT label
  # compares as written (the partition geometry is independently checked).
  lsblk -no PARTLABEL "$1" | sed 's/[[:space:]]*$//'
}

[ "$(label /dev/vda1)" = 'U-AVF Platform' ] || fail ESP_LABEL_MISMATCH
[ "$(label /dev/vda2)" = 'Ubuntu 24.04.5 ISO' ] || fail ISO_LABEL_MISMATCH
[ "$(label /dev/vda3)" = 'U-AVF Ubuntu Root' ] || fail ROOT_LABEL_MISMATCH
[ "$(blkid -s TYPE -o value /dev/vda1)" = 'vfat' ] || fail ESP_FILESYSTEM_MISMATCH
[ "$(blkid -s TYPE -o value /dev/vda2)" = 'iso9660' ] || fail ISO_FILESYSTEM_MISMATCH

# Verify mandatory install-relay sources BEFORE formatting or copying Ubuntu.
for name in uavf-clipboard.py uavf-clipboard.socket uavf-clipboard@.service \
    uavf-control.py uavf-control.socket uavf-control@.service \
    uavf-install-runtime.sh winavf-X0tigervnc stage-uavf-encoded-runtime.sh; do
  [ -s "/usr/local/sbin/$name" ] || fail "RUNTIME_PAYLOAD_MISSING:$name"
done
[ -s /usr/local/share/uavf-licenses/TIGERVNC_X0_COPYRIGHT ] || fail SCRAPER_LICENSE_MISSING
[ -s /run/winavf-frame-bridge.py ] || fail FRAME_BRIDGE_MISSING
say PASS_RUNTIME_PAYLOAD_READY
say PASS_EXACT_GPT_GEOMETRY_AND_PROTECTED_MEDIA
# Subiquity reloads /autoinstall.yaml after early-commands. Preserve the
# audited GPT and only replace the root partition's expected byte count.
python3 - /autoinstall.yaml "$root_bytes" <<'PY' || fail AUTOINSTALL_ROOT_SIZE_UPDATE
import os, re, sys
from pathlib import Path
path = Path(sys.argv[1])
size = int(sys.argv[2])
if not (32 * 1024**3 <= size <= 1024 * 1024**3 and size % 1048576 == 0):
    raise SystemExit('Invalid audited root size')
text = path.read_text()
pattern = r'(?m)(^      - id: uavf-root\n)(.*?)(?=^      - id:|\Z)'
matches = list(re.finditer(pattern, text, re.S))
if len(matches) != 1:
    raise SystemExit('Expected exactly one root partition')
match = matches[0]
block = match.group(2)
for field in ('type: partition', 'device: uavf-disk', 'number: 3', 'offset: 4102029312', 'preserve: true'):
    if '        ' + field + '\n' not in block:
        raise SystemExit('Unexpected root partition contract: ' + field)
block, count = re.subn(r'(?m)^        size: [^\n]+$', '        size: ' + str(size) + 'B', block)
if count != 1:
    raise SystemExit('Expected exactly one root size')
updated = text[:match.start()] + match.group(1) + block + text[match.end():]
temporary = path.with_name(path.name + '.uavf-tmp')
temporary.write_text(updated)
temporary.chmod(0o644)
os.replace(temporary, path)
print('UAVF_INSTALL_ROOT_BYTES=' + str(size))
PY
say PASS_AUTOINSTALL_ROOT_SIZE_SYNCED
exit 0
