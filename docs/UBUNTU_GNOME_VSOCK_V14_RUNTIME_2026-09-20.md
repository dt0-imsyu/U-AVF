# Ubuntu GNOME vsock V14 runtime — 2026-09-20

## Scope

This was one disposable, app-owned Ubuntu runtime.  It did not modify Windows
WIM/BCD/firmware source or the immutable Windows payload.

The V14 raw changed only `CASPER\INITRD` through the Windows FAT32 driver.  Its
initramfs contained a static ARM64 AF_VSOCK listener and an init-premount hook
inserted before the source CPIO `TRAILER!!!` record.

| Item | Value |
| --- | --- |
| V14 raw SHA-256 | `20849C65EA25601CFB15BE6CEFBD2F101775788E4567E946A241C68EEBA12465` |
| V14 firmware-patch SHA-256 | `41E44D71172C03C79B5195B289745E71E16FAC5FE81355CA872F92E979056D18` |
| Firmware range | offset `7250927616`, length `2097152` |
| Forward / rollback simulation | PASS / PASS |
| Immutable Windows baseline after cleanup | `2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7` |

## Positive milestones

Serial evidence establishes:

```text
LINUX_KERNEL_POST_EBS        = PASS
LINUX_PF_VSOCK_REGISTERED    = PASS
UBUNTU_GNOME_DISPLAY_MANAGER = PASS
UBUNTU_GNOME_USER_SESSION    = PASS
```

The guest registered `PF_VSOCK` at 16.347944 seconds, then reached
`gdm.service` and `Session 1 of User ubuntu`.

## Vsock result

```text
LINUX_VSOCK_HELLO = NOT_OBSERVED
```

The Android public `connectVsock(4051)` path returned `Connection reset by
peer`, rather than V13's earlier `No such device`.  This establishes that the
host VM-side vsock endpoint exists after guest registration, but no listener
accepted the connection in this run.  No `LV:*` serial marker appeared, so the
remaining immediate question is whether the init-premount hook was invoked.

V15 therefore adds only a shell-level `LV:HOOK` marker before spawning the
same listener.  It does not modify Windows, firmware, guest kernel, or the
desktop filesystem.

## Cleanup

The app-private V14 disk/patch and the external V14 staging pair were removed
after evidence capture.  The cleanup report states
`WINDOWS_PAYLOAD_UNTOUCHED=true`.
