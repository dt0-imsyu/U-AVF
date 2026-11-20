"""Static safety audit for the U-AVF Ubuntu autoinstall profile."""
from pathlib import Path
import sys

import yaml


ROOT = Path(__file__).resolve().parents[2]
GUEST = ROOT / "tools/linux-gnome/guest"
CONFIG = GUEST / "uavf-autoinstall.yaml"
PREFLIGHT = GUEST / "uavf-install-preflight.sh"
RUNTIME = GUEST / "uavf-install-runtime.sh"
SYSTEMD_HOOK = GUEST / "99-winavf-vsock-systemd"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("FAIL=" + message)


if len(sys.argv) > 2:
    raise SystemExit("Usage: audit-uavf-autoinstall.py [config.yaml]")
config_path = Path(sys.argv[1]).resolve() if len(sys.argv) == 2 else CONFIG
doc = yaml.safe_load(config_path.read_text(encoding="utf-8"))
require(isinstance(doc, dict) and set(doc) == {"autoinstall"}, "TOP_LEVEL_AUTOINSTALL")
cfg = doc["autoinstall"]
require(cfg.get("version") == 1, "AUTOINSTALL_VERSION")
require(set(cfg.get("interactive-sections", [])) == {"locale", "keyboard", "identity"},
        "EXPECTED_USER_SETUP_SECTIONS")
require(cfg.get('identity', {}).get('password') == '!', 'NO_SHARED_DEFAULT_PASSWORD')
require('USER_ACCOUNT_NOT_READY' in RUNTIME.read_text(encoding='utf-8'),
        'USABLE_ACCOUNT_REQUIRED_BEFORE_READY')
require(cfg.get("shutdown") == "poweroff", "POWER_OFF_AFTER_INSTALL")
require(cfg.get("early-commands") == ["/usr/local/sbin/uavf-install-preflight.sh"],
        "EARLY_FAIL_CLOSED_PREFLIGHT")
require(cfg.get("late-commands") == ["/usr/local/sbin/uavf-install-runtime.sh --installer-complete"],
        "RUNTIME_ONLY_AFTER_INSTALLER_SUCCESS")

storage = cfg.get("storage", {}).get("config", [])
require(cfg.get("storage", {}).get("version") == 2,
        "CURTIN_V2_EXPLICIT_PARTITION_OFFSETS")
require(type(cfg.get("storage", {}).get("version")) is int,
        "CURTIN_STORAGE_VERSION_IS_INTEGER")
require(storage and all(isinstance(action, dict) for action in storage), "STORAGE_ACTIONS")
ids = [action.get("id") for action in storage]
require(all(isinstance(action_id, str) and action_id for action_id in ids)
        and len(ids) == len(set(ids)), "UNIQUE_NONEMPTY_ACTION_IDS")
disks = [a for a in storage if a.get("type") == "disk"]
partitions = [a for a in storage if a.get("type") == "partition"]
formats = [a for a in storage if a.get("type") == "format"]
mounts = [a for a in storage if a.get("type") == "mount"]
require(len(disks) == 1 and disks[0].get("path") == "/dev/vda"
        and disks[0].get("preserve") is True and disks[0].get("ptable") == "gpt",
        "PRESERVE_EXISTING_GPT_DISK")
require(len(partitions) == 3, "REFERENCE_ALL_EXISTING_PARTITIONS")
by_number = {p.get("number"): p for p in partitions}
require(set(by_number) == {1, 2, 3}, "PRESERVE_ESP_ISO_AND_ROOT_PARTITIONS")
require(all(p.get("preserve") is True for p in partitions), "PRESERVE_PARTITION_GEOMETRY")
partition_indexes = [i for i, action in enumerate(storage) if action.get("type") == "partition"]
require(partition_indexes == list(range(partition_indexes[0], partition_indexes[-1] + 1)),
        "CURTIN_PARTITION_ACTIONS_CONTIGUOUS")
require(all(p.get("device") == disks[0].get("id") for p in partitions),
        "ALL_PARTITIONS_ON_PRESERVED_VDA")
require(by_number[1].get("size") == "126M", "ESP_GEOMETRY")
require(by_number[1].get("flag") == "boot"
        and by_number[1].get("grub_device") is True,
        "PRESERVED_ESP_SELECTED_AS_UEFI_BOOTLOADER_TARGET")
expected_offsets = {1: 1048576, 2: 134217728, 3: 4102029312}
require(all(type(by_number[n].get("offset")) is int
            and by_number[n].get("offset") == offset
            for n, offset in expected_offsets.items()),
        "ALL_EXISTING_PARTITION_OFFSETS_EXPLICIT_INTEGER_BYTES")
expected_sizes = {1: 132120576, 2: 3967463424, 3: 34359738368}
require(all(isinstance(by_number[n].get("size"), str)
            for n in expected_sizes), "PARTITION_SIZES_ARE_CURTIN_SIZE_STRINGS")
geometry = sorted((by_number[n]["offset"], expected_sizes[n], n)
                  for n in expected_sizes)
require(all(start % 512 == 0 and size % 512 == 0
            for start, size, _n in geometry), "PARTITION_GEOMETRY_SECTOR_ALIGNED")
require(all(geometry[i][0] + geometry[i][1] <= geometry[i + 1][0]
            for i in range(len(geometry) - 1)), "PARTITION_GEOMETRY_NON_OVERLAPPING")
require(geometry[-1][0] + geometry[-1][1] <= 38462816256,
        "PARTITION_GEOMETRY_WITHIN_VDA_CAPACITY")
require(by_number[2].get("size") == "3967463424B", "ISO_GEOMETRY")
require(by_number[3].get("size") == "32G", "ROOT_GEOMETRY")
root_id = by_number[3].get("id")
format_by_id = {a.get("id"): a for a in formats}
require(len(formats) == 2, "ONLY_ESP_AND_ROOT_FILESYSTEMS")
root_formats = [a for a in formats if a.get("volume") == root_id]
require(len(root_formats) == 1 and root_formats[0].get("fstype") == "ext4"
        and root_formats[0].get("preserve") is False,
        "ONLY_ROOT_FILESYSTEM_IS_FORMATTED")
esp_formats = [a for a in formats if a.get("volume") == by_number[1].get("id")]
require(len(esp_formats) == 1 and esp_formats[0].get("preserve") is True,
        "ESP_FILESYSTEM_PRESERVED")
require(not any(a.get("volume") == by_number[2].get("id") for a in formats),
        "ISO_FILESYSTEM_NEVER_FORMATTED")
require(len(mounts) == 2 and {m.get("path") for m in mounts} == {"/", "/boot/efi"},
        "ROOT_AND_EXISTING_ESP_MOUNTS")

preflight = PREFLIGHT.read_text(encoding="utf-8")
for exact in (
    "132120576", "3967463424", "34359738368", "1099511627776",
    "GPT_TYPE_MISMATCH", "2048,258048", "262144,7748952", "8011776,$((root_bytes / 512))",
    "U-AVF Platform", "Ubuntu 24.04.5 ISO", "U-AVF Ubuntu Root",
):
    require(exact in preflight, "PREFLIGHT_MATCHES_GPT_" + exact.replace(" ", "_"))
require('python3 - /autoinstall.yaml "$root_bytes"' in preflight
        and 'PASS_AUTOINSTALL_ROOT_SIZE_SYNCED' in preflight,
        "EARLY_COMMAND_SYNCS_AUDITED_ROOT_SIZE")
require('node="/sys/class/block/$device"' in preflight
        and 'cat "$node/start"' in preflight and 'cat "$node/size"' in preflight,
        "PREFLIGHT_USES_KERNEL_SYSFS_GEOMETRY")
require('lsblk -nro START,SECTORS' not in preflight,
        "PREFLIGHT_NO_UNSUPPORTED_LSBLK_SECTORS_COLUMN")
require('lsblk -no PARTLABEL' in preflight and 'lsblk -nro PARTLABEL' not in preflight,
        "PREFLIGHT_PARTLABEL_PRESERVES_SPACES")
runtime = RUNTIME.read_text(encoding="utf-8")
require("--installer-complete" in runtime and "LATE_COMMAND_ONLY" in runtime,
        "RUNTIME_REJECTS_EARLY_OR_MANUAL_INVOCATION")
require("runtime-staged-v5" in runtime and "installed-runtime-v5" in runtime,
        "VERSIONED_RUNTIME_MARKERS")
require("AutomaticLoginEnable=true" not in runtime and "AutomaticLogin=uavf" not in runtime,
        "INSTALLER_AUTHENTICATION_POLICY_PRESERVED")
require("User=uavf" not in runtime and "AUDIO_SESSION_PY" in runtime,
        "AUDIO_DISCOVERS_SELECTED_USER")
require("winavf-X0tigervnc" in runtime and "'-display', display" in runtime
        and "dbus-run-session -- gnome-session" not in runtime,
        "SINGLE_GDM_SESSION_FOR_SNAP_AND_LOCKSCREEN")
systemd_hook = SYSTEMD_HOOK.read_text(encoding="utf-8")
require('LV:CLIPBOARD_LIVE_ROOT=READY PORT=4054' in systemd_hook
        and 'uavf-clipboard.py uavf-clipboard.socket uavf-clipboard@.service' in systemd_hook
        and 'RUNTIME_PAYLOAD_MISSING:$name' in preflight,
        'CLIPBOARD_LIVE_STAGE_AND_EARLY_PAYLOAD_GUARD')
for payload in (
    "winavf-audio-bridge.py", "winavf-pactl", "winavf-parec",
):
    require(payload in systemd_hook, "LIVE_ROOT_STAGES_AUDIO_PAYLOAD_" + payload)
require("/usr/local/lib/uavf-installer/winavf-audio-bridge.py" in runtime
        and "/usr/local/lib/uavf-installer/winavf-pactl" in runtime
        and "/usr/local/lib/uavf-installer/winavf-parec" in runtime,
        "LATE_COMMAND_HAS_DURABLE_AUDIO_PAYLOAD_FALLBACK")
require("AUDIO_PAYLOAD bridge=" in runtime,
        "AUDIO_STAGE_FAILURE_REPORTS_PER_FILE_VISIBILITY")
print("RESULT=PASS")
print("AUTOINSTALL_SCHEMA_STRUCTURE=PASS")
print("CURTIN_V2_OFFSETS_INTEGER_BYTES=PASS")
print("CURTIN_PARTITIONS_CONTIGUOUS_AND_NON_OVERLAPPING=PASS")
print("ONLY_VDA3_FILESYSTEM_FORMATTED=PASS")
print("VDA1_ESP_AND_VDA2_ISO_PRESERVED=PASS")
print("EXACT_GPT_PARTITION_GEOMETRY_PREFLIGHT=PASS")
print("INSTALLER_SUCCESS_GATED_RUNTIME_STAGE=PASS")
print("LIVE_ROOT_AUDIO_PAYLOAD_STAGING=PASS")
print("LATE_COMMAND_DURABLE_AUDIO_FALLBACK=PASS")
