# Isolated WinPE codec / vsock proof

## Proven

Real 640x480 Windows Setup is captured using public GDI, not pre-EBS GOP or
a synthetic drawable. Diagnostic console is hidden. Current page requests
installation media/drivers; the disposable image intentionally lacks install
WIM/SWM payload, so this is not an installation PASS.

Official Cisco OpenH264 2.5.1 Windows ARM64 DLL loads in WinPE. SHA256:
`F5976BFF9E6DE84F6253C9BFF0F7C70CCDE446B3B20CF7B7206F7A12C35B460F`.
Public C ABI is used dynamically; no C++/MSVC runtime is introduced.
30 captured -> 30 encoded UVF1 packets -> 30 locally decoded frames.
First run mean capture/I420 conversion3.194ms; encode12.493ms; luma MAE0.413.
Second run3.126ms/12.848ms, same luma MAE. Test deliberately paced at10Hz;
neither these costs nor repeated static frames establish sustained desktop FPS.

Signed VirtioSocket driver is bound to unique PCI1053, problem0. Its WSP service
is alive. Public device IOCTLs report AF40 and matching guest CID2085. Winsock
bind/listen on4055 succeeds. Recording replay listener uses existing UVF1
kind4/8-byte clock handshake, kind2 Annex-B AUs, kind3 EOS, per-connection
generation. No alternative Windows video protocol is introduced.

## Not proven

No replay client connected, no Android MediaCodec presentation, no input, no
live encoded stream/reconnect yet. Direct Android shell `socket(AF_VSOCK)`
returns Permission denied before connect. This is a host permission boundary,
not a guest-driver failure. Do not retry direct sockets or weaken SELinux.
ADB exec-out merges the native stderr error into captured output and reports0;
`uvf-vsock-receive1/received.uvf` contains text, NOT video. Parser now rejects
such output explicitly and preserves a failure report.

## Next minimal owner-path proof

Use an isolated Android AVF VM owner with `VirtualMachine.connectVsock(4055)`,
not a foreign shell-created VM and not direct app AF_VSOCK. Existing app has
authenticated localhost raw KD pumps (`startProductKdBridge`) to reuse as a
reference, but its old `buildConfig` enables GPU/auto-balloon and uses an old
medium: DO NOT use it unchanged for this exact successful shell contract.

Preserve the oneCPU/4GiB/one Windows disk/noGPU/no-auto-balloon configuration,
exact wrapper and paired CAM EFI, guarded KD RAM fixes. Stage only disposable
Windows media; never touch installed Ubuntu. Prefer a separate diagnostic APK
owner/harness so the installed production APK remains intact.

Reuse `EncodedDisplayProbe` MediaCodec->Surface implementation. Its current
1920x1200 guard must accept explicitly negotiated native640x480 for this Windows
fixture without weakening production Linux defaults. Initial objective: receive
30 recorded real AUs, validate nonblack Surface pixels, then replace replay with
continuous GDI->OpenH264 capture and verify reconnect. Do not claim replay as
live-stream PASS. Common UIN1 can subsequently map to public Windows SendInput.

## Evidence and source

- `build-logs/windows-post-linux-reassessment-20261004/winpe-display-evidenceEncode1`
- `build-logs/windows-post-linux-reassessment-20261004/winpe-display-evidenceVsock1`
- `kd-winpe-encode-proof-20261007-runtime`
- `kd-winpe-vsock-replay-valid-20261007-runtime`
- Current pristine disk SHA256:
  `A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5`
- Device copy was guest-written: restore pristine copy before another run.

Official references: [Cisco OpenH264](https://github.com/cisco/openh264/releases/tag/2.5.1),
[VirtIO socket ABI](https://github.com/virtio-win/kvm-guest-drivers-windows/blob/master/viosock/inc/vio_sockets.h),
[WSP registration](https://github.com/virtio-win/kvm-guest-drivers-windows/blob/master/viosock/lib/install.c).
Keep publisher license notices before any future redistribution; no publication
or production packaging was performed here.

Both diagnostic VMs were cleaned up by the bounded observer. The first replay
attempt never launched a VM because wrapper accepts120/180/300, not240 seconds;
discard it as wrapper error. Valid run used300. No production Ubuntu mutation,
root/unlock/flash/SELinux change, commit or push.
