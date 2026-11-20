# GOOD-preserving keyboard and clipboard extension

No Git commit/publication. Preserve the installed disk, P7 platform, official ISO,
GPU, firmware/kernel and the accepted 720p approximately 30 FPS display baseline.

## Recoverable GOOD

`D:\UAVF-backups\GOOD-installed-720p30-20260930` contains the known GOOD signed
APK, platform prefix, source/runtime archive and rollback instructions.
It is **not** a full backup of Ubuntu user data. Do not uninstall the application.

## Keyboard

Candidate `C5C884965E471FEEA2D8746969BBD26496D57F1C6E3A12A94B00395EDBE6031D`
was installed; device base.apk SHA matched. User confirms shortcuts work.
Stateful key-down/up forwarding now remembers the keysym held on key-down,
reconciles left/right Ctrl/Alt/Shift/Super, forwards function/navigation keys,
and releases held keys on focus loss or runtime panel opening.
Ordinary combinations are interpreted by Xorg, not individual app shortcuts.
Standalone state tests pass. Samsung-reserved shortcuts are not guaranteed to
reach an ordinary Activity; no accessibility service/system-policy override was added.

## Clipboard implementation and actual acceptance state

Installed APK SHA:
`0F21EB6DA517A733EAFEA2E57016AF7BCD022E06B209BE3C93CF7EEB7EC23DF7`.

Optional foreground Android ClipboardManager integration is OFF by default;
it was explicitly enabled for this test in Workspace settings → Input & gestures.
Only explicit text is read (not coerced file/URI clips). Limit: 256 KiB UTF-8.
Updates are debounced 200 ms, latest-value bounded, with echo suppression.

Guest Gtk3 `Gtk.Clipboard` agent subscribes to X11 owner-change. UCIP v1 uses
independent vsock 4054, never the WAVF decoder/input parser. GTK callbacks are
on its main thread; stale asynchronous reads are guarded by a generation counter.
Java/Python tests cover UTF-8/multiline, bounds, malformed headers and truncated data.

Runtime proof: `UAVF_CLIPBOARD=READY PORT=4054 UID=1000 DISPLAY=:0`;
Android logcat: `Clipboard 4054 connected (text only)` at 22:56 Moscow.
Connection alone was not counted as paste acceptance. User subsequently confirms
"работает" after the request to test Android→Ubuntu and Ubuntu→Android. Record
functional acceptance from the user separately from the offline UTF-8/multiline
and malformed-packet coverage. Next-login autostart still needs runtime acceptance.

Current guest add-on:

- `/home/uavf/.local/lib/uavf-clipboard/uavf-clipboard.py`
- SHA `0A1901B37E9DA7208A6D46AF429259CB9B0DE2A8F87328A1D7FC6980895EDA76`
- `/home/uavf/.config/autostart/uavf-clipboard.desktop`
- Installation marker `UAVF_CLIPBOARD_USER_AUTOSTART=INSTALLED` observed.

The current listener was started from the real user terminal and moved to a
background job. GNOME autostart is prepared for the next login; that login has
not been tested yet. No root service, GDM configuration, install marker or disk
partition was modified. The optional root socket-activation files are merely
prepared alternatives, **not installed on this device**.

Delivery lesson: long keyboard-injected commands were truncated/corrupted.
Do not deliver binary/script assets through virtual typing. Correct delivery used
temporary local HTTP bound to PC loopback + `adb reverse tcp:39054 tcp:39054`,
guest wget via the Android AVF tap address `10.159.93.215`, followed by exact SHA.
Both the server and reverse tunnel were closed after delivery. Tap address must
be read again if this diagnostic procedure is reused; do not hardcode it in product.

The per-user installer is idempotent and refuses to silently overwrite different
existing add-on files. A future APK-managed provisioning mechanism is still
required; merely packaging the agent does not install it. Do not claim clean-user
clipboard setup is finished.

## Display research: preliminary evidence only, no FPS A/B yet

1. Existing scraper/RFB: highest priority for one bounded A/B. Its actual wrapper
   adds no PollingCycle/MaxProcessorUsage override. Upstream exact TigerVNC 1.13.1
   source defaults to 30 ms polling and a 35% CPU duty budget. These are plausible
   artificial limits, **not yet a measured sole bottleneck**. Query installed
   binary help/process arguments, then try only polling/duty settings reversibly.
   Preserve current 720p fallback and do not touch GPU or bridge protocol.
2. Direct compositor capture: do not repeat failed XDamage/root/overlay trials.
   A distinct Mutter/PipeWire screencast source would require new feasibility and
   session-permission proof; no measured performance claim exists here.
3. Hardware video: device nodes actually exist, including `/dev/video0` and
   `/dev/video1` owned `media:system`, mode 0660. App `run-as` cannot even stat
   these nodes (Permission denied). Host shell cannot read their sysfs names.
   Shipped crosvm binary cannot be executed/stat'ed by shell at
   `/apex/com.android.virt/bin/crosvm` (Permission denied). Therefore its exact
   compiled media/video-encoder/v4l2 flags remain **UNVERIFIED**, not absent.
   `vm run --help` is accessible but is not crosvm feature evidence.
   Android Codec2 component-store services exist; this does not expose an encoder
   to the guest or prove virtio-media/V4L2 proxy availability.

Do not claim that `/dev/video*` are absent or that hardware encode is impossible.
Current blocker is lack of demonstrated app-accessible guest encoder provisioning.
No permission/SELinux weakening or new VM configuration was attempted.

Source: [TigerVNC 1.13.1 scraper](https://github.com/TigerVNC/tigervnc/blob/v1.13.1/unix/x0vncserver/x0vncserver.cxx)
also explicitly removes clipboard parameters because that scraper lacks clipboard
support. This is why UCIP is a separate channel.

## Next action

Clipboard functional acceptance is complete; retain one next-login autostart check.
Then query the real scraper limits and measure one reversible existing-RFB A/B
on sustained GNOME workload. No additional synthetic/XDamage profile series.
