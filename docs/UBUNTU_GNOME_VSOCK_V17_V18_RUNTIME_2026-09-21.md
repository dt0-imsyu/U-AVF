# Ubuntu GNOME public-vsock probe — V17/V18 runtime

## Scope

Two disposable Linux-only runs tested an initramfs listener on AF_VSOCK port
4051 through WinAVF's public Android `VirtualMachine.connectVsock()` API.
Windows media, BCD, EDK2 source, and the Android baseline were not modified.

## V17

V17 corrected initramfs placement: the listener hook and binary were inserted
in the Zstd main CPIO archive, and the final
`scripts/init-premount/ORDER` explicitly executed the hook. Static extraction
confirmed the final order and executable file modes.

* raw candidate SHA-256:
  `4B6BAD59B4DE46B2DB8A95E599E7F983F8F1AA155E0C0F04454D6562A3A0177E`
* firmware overlay SHA-256:
  `C81F51AE766B2539E3CE4C40074D5D7D107C52B16D5BF7A693946BB995D7DA28`
* raw serial SHA-256:
  `49052ACAF22B8D9534947B82374F45197B6F427064126B91DE8D6DA5061FF81B`

The serial log proved `LV:HOOK`, then showed:

```text
/scripts/init-premount/99-winavf-vsock: line 6:
/usr/local/sbin/winavf-vsock-hello: not found
```

Therefore V17 proves initramfs hook execution but not listener execution.
Android received `ENODEV`, and later live-root SquashFS failures caused an
init panic. This does not identify a vsock transport failure.

## V18

V18 rebuilt the same libc-free ARM64 listener with a compact 4 KiB ELF load
offset (`PT_LOAD offset=0x1000`) rather than the bare-metal linker's 64 KiB
default. Static checks passed:

```text
ELF_AARCH64=PASS
ELF_STATIC_EXEC=PASS
ELF_COMPACT_LOAD_OFFSET_4K=PASS
```

* listener SHA-256:
  `10B60EEFBBCAA3569510D38DC190AAFE12E3AB1C00E45E97609A05B89AE3EC9B`
* patched initrd SHA-256:
  `288CF24C5F5EB58BCEB43A5E8A079E1717D6F7CD51F9AC96BB0F35E35A86AC71`
* raw candidate SHA-256:
  `28F1CA554CE32FEBC210B822BA69830FCBAC56FFA3F7AF883508B99D00527A94`
* firmware overlay SHA-256:
  `266FC334B3647E979F815CC87101CCC80609BB0DDAFE2A6F40A14C44E45AE59F`
* raw serial SHA-256:
  `33CC9D8D00B5292CDE2D1C4B08E51ABF0958632DBD35ED9396F10AD2593324C8`
* Android vsock report SHA-256:
  `E01CB34978D1D93FEA197E00C8C410A9148E082279B7E3413A0A85A2B02598C9`

V18 repeated `LV:HOOK` and the exact same `not found` result. Android's
public connection advanced to `ECONNRESET` rather than establishing the
application listener. Crucially, Linux continued through systemd to
`graphical.target` and printed both `ubuntu ttyS0` and `ubuntu hvc0` login
prompts. Thus the current product VM has a proven post-EBS Linux
kernel-to-graphical-userland path.

## Classification

```text
LINUX_KERNEL_POST_EBS        = PASS
UBUNTU_GNOME_USERLAND        = PASS
INITRAMFS_VSOCK_HOOK         = PASS
INITRAMFS_STATIC_LISTENER    = FAIL_TO_EXECUTE
APP_OWNED_LINUX_VSOCK        = NOT_CONFIRMED
```

The next Linux-only investigation is narrow: determine why the guest kernel
returns the shell-visible `not found` error for a present, executable, static
ARM64 ELF. Do not restart Windows/KD/ACPI work. A next listener must first be
validated as executable by the exact Ubuntu initramfs environment; only then
run another app-owned vsock runtime.

## Cleanup

After each run WinAVF's `ubuntu_gnome_cleanup` action removed only the
disposable app-private Ubuntu payload. The staged V17/V18 Linux raw and patch
files were removed. The Android-resident immutable Windows baseline was
rechecked as:

`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.
