# Ubuntu GNOME V19 valid runtime — 2026-09-21

## Scope

This was one disposable Linux-only runtime.  It used the dedicated Android
intent `--ez ubuntu_gnome true`, not the product UI `Launch` action.  The
Windows baseline, Windows WIM/BCD, and Windows firmware source were not
modified.

## Why the first claimed V19 result was invalid

The ordinary UI `Launch` button intentionally calls the product Windows
profile.  Android's crosvm log for that attempt named the VM
`winavf-gop-ebs-r1` and opened the immutable Windows backing file.  Therefore
its `CONVERT_AUDIT ... Not Found` record, missing Ubuntu files, and no `LVH1`
record were not evidence about V19.

`CONVERT_AUDIT ... Not Found` is also a known pre-EBS EDK2 diagnostic for
non-RAM addresses, not a Linux boot failure.

## Valid V19 inputs

| Item | Value |
| --- | --- |
| raw image | `winavf-ubuntu-gnome-24.04.5-v19-vsock-systemd-candidate.img` |
| raw SHA-256 | `DDEB51940FA6A714C00062C9F3822A696130EDE31F82133457BD5DC54DD221AA` |
| firmware patch SHA-256 | `AA8970A3A2855307A03DBF0D4C9AC91186293F323B28198BBEF7D8232E74BC91` |
| Android VM name | `winavf-ubuntu-gnome-24045` |
| guest port | `4051` |
| serial SHA-256 | `5205AB02B951DC0BFE1251A33E2E5C8B4758C1650B1B486BE6AFF64E535AEC3C` |
| vsock-report SHA-256 | `0525B3379610D59CD52015C042D9EB7DC5E79EBFD415DF25EEC174BA9571348B` |

The crosvm command confirms that the VM used the V19 app-private Ubuntu image
and its separate VM name.  It did not open `win11-gop-ebs-r1.img`.

## Runtime evidence

The V19 serial log contains both required initramfs markers:

```text
Begin: Running /scripts/init-premount ... LV:PRE
Begin: Running /scripts/init-bottom ... LV:UNIT
```

Thus V19's hook and transient systemd-unit installation both executed.  The
guest then emitted repeated real live-root read failures:

```text
SQUASHFS error: xz decompression failed, data probably corrupt
SQUASHFS error: Failed to read block ...: -5
Kernel panic - not syncing: Attempted to kill init! exitcode=0x00000007
CPU: 0 ... PID: 1 Comm: systemd
```

Consequently PID 1 died before it could create the post-switch-root systemd
AF_VSOCK listener.  Android's 90-second public `connectVsock(4051)` probe
ended with `No such device (os error 19)`, which is expected once the guest VM
has already terminated.  It is not evidence that the V19 systemd unit was
rejected and it is not a valid result for `APP_OWNED_LINUX_VSOCK`.

## Correct status

```text
V19_INITRAMFS_HOOKS                 = PASS
V19_LIVE_ROOT_SQUASHFS              = FAIL
V19_SYSTEMD_LISTENER_STARTED        = NOT_REACHED
APP_OWNED_LINUX_VSOCK               = NOT_CONFIRMED
WINDOWS_BASELINE_AFTER_V19_CLEANUP  = PASS
```

The Android cleanup action removed only `ubuntu-gnome-payload`; the external
V19 raw and patch were removed.  The immutable Windows baseline was rehashed
after cleanup as:

`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.

## Narrow next step

Do not add more systemd diagnostics.  A transport probe must execute before
the live SquashFS mount, so the next candidate should repair and place an
AF_VSOCK listener entirely in the usable initramfs stage.  First audit the
actual Linux ELF load layout and archive placement: the retained V18 listener
artifact still shows a `PT_LOAD` file offset of `0x10000`, contrary to the old
"4 KiB" report.  Resolve that offline before one further disposable runtime.
