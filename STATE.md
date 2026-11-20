## 2026-11-06 Live34 descriptor template payload isolation

Guest template updates now build per-call image, buffer, view and inline data
arrays. Shared metadata stays immutable after creation. No global tracker
lock is held during transport. This removes the scratch-data race between
updates of distinct sets using the same template. ARM64 guest build passed.
APK34 SHA256 D77EBCF6CDF34FB4501D56C9515F127D5DB8DD3DA7415C18576569E19C5DBD26,
prefix 574B2CE050DD9980CA04B74957312271D7A816E53EFD7F465C02B30E80D25E98.
Only Live prefix and manifest differ from Live32. Device CID2064 booted actual
GNOME X11 through Mali/AHB, processed 183 template update packets, and
powered off with native EOF. No deliberate same-template concurrency stress
test. The window artifact persists (live34-run.png). This correction is not
the observed artifact fix. No sustained FPS claim. Next separate actual
GPU draw output from final SurfaceControl presentation.

## 2026-11-05 Live33 window uniforms and viewport

Diagnostic APK33 SHA256 D190D7027F362BFD1242124EF25600157EAD55AD3C20BB324D12D8A22DFAA005.
Prefix EA2E1CE6AA0EE699F6ECA5E0C3F36FECF21A4A9BCB3FC6E11EBFA88F4DD0049C.
Actual Live ISO, app-owned AVF, Mali/AHB. VM subsequently stopped.
For six sampled window draw calls, 192-byte vertex uniform buffers read back
from GPU matched guest shadow bytes. Guest framebuffer 1920x1200; viewport
scale (960,-600), translate (960,600), scissor disabled. This tests sampled
buffers, not actual host descriptor bindings or executed vertex output.
Readbacks explicitly synchronize and distort timing, so no FPS inference.
Trace logs and bytes are local in live33-serial.log. The trace is opt-in.
Next inspect decoder descriptor binding and the actual viewport and draw state,
or verify shader output in the same real window pipeline. Preserve baselines.

## 2026-11-05 Live31/32 verified resource RPC reduction

Both opt-in Live builds booted GNOME X11 on Mali through 1920x1200 AHB.
ZIP verification against each predecessor found only the two Live assets
changed. Decoder, H.264 fallback, firmware, P7 and stock ISO preserved.
Live31 CID2061, APK SHA256
54F9CB4715B7A29F9AB2965009239842AC32ECBE05005D1D596BF4AA954EE2E9,
prefix 7196AB3085DCE2E4CBAAE32A790A7085C0EE0142DECDE7AE56B05FDC39720EC6.
Live32 CID2062, APK SHA256
49F75870B3B76948CD068CDAB2F4E1BE75C3F466D4C0C5C1B258D20B98E9518D,
prefix CCEAC20DDE778639B86630B78E4790997C89FEFB19DAE4ED2B0C9F03F4159930.
APK32 installed, disposable VM shut down through guest poweroff.

* 31 uses the already implemented gfxstream codec 3 Create*WithRequirements
  handlers. The host performs the real queries, and the guest caches the
  returned base requirements. Live30 had 1694 explicit buffer requirements
  queries for 3368 buffer creations. Live31 had zero for 3401 combined calls.
  The image path still makes GetImageMemoryRequirements2 for dedicated
  allocation data (1224 in Live31). Image fusion has little demonstrated
  value for this workload and is an obvious candidate to scope back.
* 32 batches the two existing Zink buffer aliases into one BindBufferMemory2
  if available on Mavk. It retains both Vulkan handles/usages and the original
  BO and offset. Device recorded 1522 pairs + 16 single binds for 3060 buffer
  creates, with no bind/codec failure. Both guest builds passed and the Mesa
  patch was regenerated and reverse-checked.
* Same eight-toggle Overview input, deduplicated Android screen recordings:
  Live30 51 / 8.01s, Live31 69 / 7.99s, Live32 57 / 7.70s. Session/animation
  boundaries and active frame counts vary. No sustained improvement or 60 FPS
  claim. Sampled window pixmap hashes still match, installer in 32 still
  collapses to mostly invisible content with a visible strip in live32-run.png.
  Native shutdown reached EOF on both builds. No production VM/data writes.
* Main next gate: compare actual bound Vulkan pipeline, shader input/uniform
  state and output at the failing window draw. The prior texture/vertex
  readbacks validate sampled bytes but do not validate those bindings at draw.
  Large FPS gain needs reducing synchronous command recording and resource
  traffic together. The pinned QueueSubmitWithCommands changes sequencing
  and framing. Validate the contract before enabling it. No commit/push.

## 2026-11-05 Live30 verified native release and teardown

APK30 SHA256 2E00FB7C20D41269C051C42585EBA645F813342858E10E98C58F32FA5E823075.
CID2060, Mavk opt-in prefix
EE60D9F49C27CF7A5F97F4C1A911D79E24433164E7C803DF399AF67508F199EB.
APK ZIP delta against 29 is the renderer library and two Live assets.
Real Live boot, 1920x1200 Android Mali/AHB, no persistent guest disk.

The backend now returns successful native-image release only after checking
the ownership fence. PrepareTargetPool response flags bit 0 negotiates the
guarantee. The guest skips its separate QueueWaitIdle only when negotiated.
Older host responses still trigger the original wait. Android release fences
still gate slot reuse. A failed fence wait prevents further use of the image.
Both host and guest builds passed, pinned host patch reverse-check passed.

Live30 reported host_fence, 175 vkQueueSignalReleaseImageANDROID and one
vkQueueWaitIdle for the entire session. Two sampled window texture hashes
matched again. Eight Overview toggles gave 51 deduplicated visible images in
8.01 s (Live24: 58 in 7.78 s). This short intermittent sample does not show
an FPS improvement. Window redraw still varies: after Overview the installer
was readable with corrupted borders, earlier it was largely invisible.
Do not conflate this with a general window fix or repeat index/topology/age
flags. Need observe final render state and actual shader inputs at a failed
draw, plus attack the remaining synchronous RPCs (e.g. 3244 / 1170.9ms in
5.265s during Live30). The single-client gap remains.

Issued guest systemctl poweroff in the isolated Live session. Guest reached
Power down and AVF list emptied. Host recorded EOF with live handles still
present before cleanup; JNI returned status 25 as designed for that EOF.
No new native crash; last crash log remains the Live27 destroyed-mutex
abort. Confirms this teardown scenario, not all close/reconnect paths.
Live29 surface went black after background/re-entry; its video sample was
blank and excluded. Force-stopped only CID2059 before installing APK30.
Artifacts: live30-{runtime,serial,native,overview,run} and APK30.
No commit/push or production data mutations.

## 2026-11-05 Live27/28 and teardown correction (hardware validation pending)

Live27 recorded 26 bounded GPU buffer readbacks matching guest shadow data,
including indexed-triangle window batches (1012x732, uint8 indices, stride32).
This validates sampled geometry/UV bytes, not all shader outputs or draw state.
Live28 CID2058 enabled Gallium uint8-to-uint16 lowering. Prefix
AC66853AB826327CF2EC99F39B9AE0B5486639B129D10E33EE7CDD06C3D79CA3.
Run dialog is readable, but installer content still stretches/misses regions.
NO-GO as a window-rendering fix. Probe source/build flag removed, signed APK
retained. No sustained desktop FPS claim. Screenshots/logs live28-* retained.

Launching nautilus from Alt+F2 inherited the Shell Mavk environment and opened
an unserved second Vulkan listener (MAVK_MESA_CONNECT_FAIL accept deadline).
Source confirms a single host connect(4057), one process-local guest listener,
one decoder loop. This is a real multi-client integration gap. Do not describe
software app fallback as achieving general hardware Vulkan application support.

Live27 teardown crashed in AndroidNativeBufferInfo::QueueState::teardown:
destroyed queue mutex during VkDecoderGlobalState::reset. HeadlessState stored
instance cleanup callbacks but never ran them on channel closure. The default
Impl destructor destroys mQueueInfo before mImageInfo. Added cleanupProcesses()
draining registered gfxstream instance callbacks before decoder global reset.
Existing callbacks idle devices and destroy dependent objects in upstream order.
Native build PASS. Device stop/restart validation is still pending.
APK29 is Live24 payload plus only libmavk_runtime_probe.so changed, ZIP checked.
SHA256 7426EC7DC41A3A10DE214BB3EEA42BD329A1D06392F4CD057C90011993568349.
Do not call cleanup fix hardware-verified until that test completes.

No commit/push, P7/ISO/Windows/installed disks unchanged.

## 2026-11-05: Live25/26 geometry investigation

Live25 CID2055 verified actual Mavk Live, prefix
0AE36D3BBC1211E02B5B87E07A611952203645E7D6C6DC00D263C0D76CE55916.
Single test variable: Gallium converts native triangle fans to triangles.
Cogl normally uses a fan for single rectangles and indexed triangles for
rectangle batches. The alternative still shows the same diagonal shear
and missing triangle (live25-screen.png). Overview recording shows 55
distinct content frames in 8.18 seconds. Restore native topology; no fix
or sustained FPS claim. Keep the independently validated Live24 changes.

Live26 CID2056, prefix
BEE45E082B95A5E03431390672E40CA13AF6AC13D27CF3DAB640F2842F71CCFC.
The diagnostic was enabled in the actual Live wrapper and two texture
hashes matched again, but its geometry selector emitted zero samples
through boot, Overview, and opening Files. This is an inconclusive
geometry test. Artifacts include live26-active-serial.log and screenshots.
The follow-up build broadens candidate selection to all fragment samplers
and batched draws, logs the draw path and snapshots the first bounded
vertex/index ranges. The readbacks are diagnostic and can change timing.
Only opt-in guest libraries/prefix changed; no Windows, P7 or ISO changes.

## 2026-11-05: Live24 - window texture verified, composition still corrupt

Actual Tab S11 Live23 CID2053 and Live24 CID2054 used Mavk, 1920x1200
AHB/SurfaceControl, no persistent guest disk. APK24 signed SHA256
333741362CC66C44BA3EB67062E2277A069670C08914D73908553C69EA5BDC3C.
Live prefix A08C699BD2E4E48B057B6018B0BCB695F19F09E9CE05A260FC099CD67CCA5D33.
Only the two opt-in Live assets changed between APK23 and 24.

- Diagnostic GPU-to-staging readback returned matching FNV hashes for two
  1012x732 X11 pixmap uploads on each run. This confirms the sampled
  transfers. The already captured X11 source image was visually correct.
  Live23 Run dialog screenshot shows diagonal skew and missing half-quad.
  Live24 Files opened via touch and showed similar clipped/sheared contents,
  confirming this affects general window composition. Root draw failure open.
- Live24 fixes noncoherent range alignment in Zink. Old code could omit the
  written tail at small offsets; new bounds cover the requested interval and
  round only to the required atoms. Isolated compiled host regression covered
  3,776,760 cases (301,718 old undercoverage cases). This correction alone
  does not establish the cause of the remaining window artifact.
- Live24 queues the reply-less descriptor template updates with existing
  ordered batches. Shared single-thread MVK1 decode keeps inter-encoder FIFO
  without the two per-switch HostSync calls; the old encoder is still flushed.
  These tail opcodes disappear from top timings. Live23 active sample had
  8,538 small RPCs and 2,406.5 ms in 5.000 s. Live24 active samples had
  3,473 / 1,202.6 ms in 5.135 s and 4,971 / 1,859.2 ms in 9.725 s.
  Sample boundaries and frame counts differ, so these are throughput clues.
- Independent Android screenrecord with eight Overview toggles yielded
  56 distinct content frames / 8.15 s (23) and 58 / 7.78 s (24). These
  are intermittent samples. Usable GNOME and sustained 60 FPS remain open.
- Next isolate shared indexed/window quad drawing and test vertex/index
  uploads through the same X11/Zink encoder path using a known texture.
  Compare draw input bytes and pixels so the fix targets the corrupt state,
  not a window-specific path. For throughput the next hot operations are
  flush, buffer create/bind and command begin/reset; no synthetic success.

Artifacts in `Mavk/artifacts-local/gfxstream-app-20261027/live-runtime1/`,
including live23-run.png, live24-files.png, live{23,24}-active-serial.log,
live{23,24}-overview.mp4 and range24-test.c. Trace switches opt-in.
Reclaimed 20.94 GiB on C by deleting only old aligned/unsigned intermediates
after verifying each signed APK1-21. All signed rollbacks retained.
No change to Windows, ISO, P7, or production H264/VirGL. No commit/push.

## 2026-10-29: window-content boundary localized (Live17/18)

Live17 CID2133 has GLX texture-from-pixmap disabled as intended (actual serial
marker). Window corruption remains. X11 xwd of the selected ordinary client
window gives a correct 1012x732 image: title, Ubuntu logo, language list.
The simultaneous Android/Mavk view is garbled/empty. This proves application
rendering finished correctly and narrows the fault to common window resource
upload/composition. Do not fix or relaunch the installer specially.

Artifacts: live17-window-source.png, live17-window.xwd,
live17-xwd-serial.log, live17-now.png. xwd was an explicit one-off diagnostic
readback over the existing serial log; it is NOT the display path.

Live18 CID2134 differs only by GALLIUM_THREAD=0 diagnostic wrapper.
Corruption persists, so single-threading is NOT a fix. Canonical wrapper restored.
Native saved live18-native.txt; idle-inclusive rate is not a usable desktop FPS.
No installer/game-specific workaround added. No 60FPS/pass claim.

Next Live19: same prefix17 and ordinary threaded producer, temporary native
validation build + existing Khronos validation .so. Packaging/signing in progress.
Native temporary validation setenv must be restored to
host-before-live-vvl.cpp after this bounded test. Main JNI/Java unchanged.
Windows/P7/H264/ISO disk bytes outside journalled prefix preserved; no commit/push.

## 2026-10-29: actual Live gate   installer texture missing, FPS not solved

Current APK16:9904A603498EA22C42FA80E55A24D414DFCDDE16E79DC1A9B20C63EE6EC1231E,
prefixDA589421790B205FCDF7C8A52369DD803084E475900A88B9E1AFDF4DC273F0DE,
CID2132. Batching is active (max64commands); no decoder framing failures.
Native decoder36737commands/781.325ms cumulative; endpoint still only59 actual
completed presents/53.625206s (includes idle), not60FPS desktop PASS. User
confirms low FPS. Do not mistake removed readback bulk for solved FPS.

User correctly identified central rectangle as INSTALLER WINDOW content.
Normal authorized Alt+F2 through existing ADB/UIN1 works. Read-only guest
diagnostics via sudo -n sh -c with stdout redirected to existing /dev/ttyS0
are confirmed in the app's serial log. No new RPC/input/auth channel introduced.
VM sudo is normal Live guest administration, not Android root.
Current installer ubuntu_bootstra PID3862 is alive, loaded75languages and
reached interactive WAITING. Filtered environment has NO MAVK/LD/VK override;
maps are original snapGL/EGL/Flutter. Inherited Mavk settings hypothesis REJECTED.
See live16-installer-diag-serial.log, live16-ps-serial.log. Do not print complete
environment/credentials; only allowlisted GPU variable names were collected.

Next candidate17: disable GLX_EXT_texture_from_pixmap for MAVK_VSOCK only.
Remote device cannot import Xorg/VirGL dma-buf pixmaps. The Kopper hardware
pixmap allocation path attempts that import and otherwise allocates an empty
texture, while GLX currently claims TFP works. Cogl has an existing XImage
fallback (cogl/winsys/cogl-texture-pixmap-x11.c, upstream46.2).
This is an interim client-window RESOURCE upload, additional CPU copies;
final desktop composition stays Mali/AHB, no final-frame video capture.
It is NOT full multi-client Mavk command-only application integration.
Build/prefix17 ready; actual Live test pending. Keep the full objective active.
No commit/push or Windows/P7/H264/disk changes. No cleanup (C~73GB free).

## 2026-10-29: real Live GNOME rendering; correctness/performance gate still open

Main U-AVF optional Mavk Live branch is running the actual Ubuntu ISO, not an
installed disk. Upload splitting fixes startup SIGSEGV; disabling unsupported
GLX_INTEL_swap_event fixes two-frame freeze. APK8 shows real desktop interaction
but user reports low FPS and central stale rectangle. Not integration/60FPS PASS.

APK9/10 tests isolated another concrete presenter error: after lease5 holds
slot1, present6/7 release other slots, round-robin acquire waits on held slot1
and aborts despite a free alternative. Shared presenter now selects ANY released
slot, preserving fences and exclusive ownership. APK11 real Live passed that
exact boundary (lease8 uses slot2, later frames continue), no acquisition failure.

Removing GLX_EXT_buffer_age alone does NOT force full redraw in Mutter: it can
fall back to GL_FRONT region blits. Next candidate retains the extension but
reports legitimate unknown age0 from kopperQueryBufferAge, forcing complete
back-buffer redraw. APK12/prefix12 build/test pending. Need real clock/Activities
image integrity and completed-present measurements. No 60FPS desktop claim.

Per-op counters prove response bulk is vkInvalidateMappedMemoryRanges (20026).
Discard-write skip reduces some traffic but does not solve the desktop by itself.
READ maps are preserved. No framebuffer/H264 path added; Windows/P7 unchanged.
No commit/push. See LIVE_RUNTIME_INTEGRATION_2026-10-28.md for artifacts.
Cleanup only free C<30GB or after full Ubuntu integration; latest C~85GB.

## 2026-10-29: optional main-runtime Mavk Live integration in progress

Added signed Live-prefix payload, Display opt-in, same-UID :mavk service reusing
common Mali/AHB presenter and existing UIN1 input, and missing Live button for
users without an installed disk. Full Java/native builds passed; APK2 installed
with original release signature/data preserved. Actual ISO cold boot CID2115
passed Linux/systemd/GDM and user ubuntu session; Mavk frame did NOT arrive
before connection timeout. VM stopped, authoritative VM[] confirmed.
Next: one bounded observer of actual Shell environment/unit/loaded libraries
and native report, then fix first proven launch mismatch. Not GNOME integration
PASS or 60FPS desktop claim. Prefix-only journal/restore leaves ISO/install
disks outside write range. No commit/push. Windows retained.
See Mavk/docs/LIVE_RUNTIME_INTEGRATION_2026-10-28.md.
Cleanup only C free<30GB OR after full Ubuntu integration; no cleanup now.

## 2026-10-28: first visible GNOME X11, not full integration PASS

Range-correct noncoherent buffer uploads advanced real Shell from black to
visible overview/panel/notification content through Zink/Mali/AHB. gnome17-clean
runs without diagnostic Eval/unsafe-mode and exits 0, VM[] afterwards.
Rendering defects, missing live assets and multi-process/input integration
remain; no sustained GNOME FPS or stable desktop PASS claimed.
OpenGL regression gl24: visible moving horse, 185 completed presents/1.991676s
(~92.38/s), short 1920x1200 run, clean cleanup. Windows/P7/H264/disks unchanged.
See Mavk/docs/GNOME_FIRST_VISIBLE_2026-10-28.md (sibling path from U-AVF).
Next priority remains correct visible GNOME, not isolated operation tests.
Cleanup ONLY when C free <30 GB OR full Ubuntu Mavk integration; no cleanup now.
Latest free C:83.39 GiB. No commit/push. Preserve Windows branch and active roadmap.

## 2026-10-28: actual Zink window is visible above 60 FPS

Standard glxinfo + glmark2 through the actual Mesa guest encoder now render on
Mali and present through the common Android AHB backend at 1920x1200.
gl22: 82 glmark2 FPS; 654 completed kernel display timestamps / 7.975019s
(81.880682/s), moving horse verified in frame-04/frame-07. Not GNOME PASS.
Fixed noncoherent/persistent upload mismatch; native heaps retain passthrough.
BGRA uses one GPU blit to RGBA AHB, no CPU framebuffer presentation copies.
See sibling Mavk/docs/ZINK_VISIBLE_2026-10-28.md for exact traffic, scope, hashes and limitations.
Native validation found/fixed extension dependency and pre-bind AHB queries.
GNOME live userspace is staged separately, no installed disk. Current consumer
work: Mutter X11 WSI/bootstrap surfaces and required helpers. Preserve Windows,
P7, production app/disks/H264. No commit/push.

# U-AVF state — 2026-10-04

## 2026-10-27: actual Mesa encoder / 1200p AHB window PASS

Standard guest Vulkan/XCB application now uses the real Mesa encoder and APK
codec plus shared AHB presenter.120 submits,119 distinct display timestamps,
~109.8/s short run at1920x1200; no framebuffer return/codec. Clean exit/VM[].
Not GNOME. Next combined gate: Zink/OpenGL then GNOME X11; Windows retained.
No production changes or commit/push. See Mavk/docs/MESA_ENCODER_WINDOW_2026-10-27.md.

## 2026-10-27: new app-local serializer mechanism verified, production unchanged

Separate Mavk runtime test APK now runs a generated Vulkan decoder over existing
MVK1/app-owned AVF4057, avoiding system crosvm/GZVM GPU blob registration. Linux
batch33 commands verified2048 GPU values including streamed upload/copy/readback.
Native decoder exports1200p AHB, allocation only. Not guest ICD/WSI/GNOME yet.
Next owner-directed large gate: actual Mesa encoder + shared AHB/QueuePresent,
then Zink/Xorg/GNOME. See ../Mavk/docs/APP_SERIALIZER_BRIDGE_2026-10-27.md.
Windows retained; no VM disks/SMP/firmware/H264/production changes or commit/push.

## 2026-10-27: standard X11 Vulkan → host AHB; GNOME pending

Shared Mavk ICD now has opt-in XCB/swapchain presentation. Standard Ubuntu
UID1000 Vulkan application supplies its own shaders via Bridge26/27/28; Android
renders/presents without returning pixels.60/60 distinct display timestamps,
~120/s short640x400 interval; visible movement. Old1920x1200 regression after
pool refactor:600/600 timestamps,117.8/s. Not installed Ubuntu/GNOME; restricted
API still needs an OpenGL frontend. Windows ARM64/x64 plain+Win32 WSI builds
PASS, no Windows VM/runtime/SMP/disk/H264 changes. Goal order unchanged; next
GNOME X11, then Windows WDDM/full workspace, final redesign. Details:
../Mavk/docs/LINUX_X11_AHB_2026-10-27.md. No commit/push.

## 2026-10-27: standard Linux Vulkan consumer through Mavk PASS

New Linux ARM64/glibc port of the shared Windows ICD, using POSIX AF_VSOCK4057,
was loaded by normal Ubuntu libvulkan in a diskless isolated AVF guest. Standard
Vulkan API consumer rendered on Mali-G925 and checked3330pixels; normal child
UID1000, exit0/cleanEOF. This is explicit offscreen readback (16384B), not yet
standard WSI/AHB or GNOME integration. The114–115Hz AHB proof remains separate.
Windows source adds platform guard only; WSP and Win32 WSI unchanged, ARM64/x64
builds PASS, no Windows VM run or baseline changes. Checkpoint and next required
guest WSI/API work: ../Mavk/docs/LINUX_ICD_BOOTSTRAP_2026-10-27.md. No commit/push.

## 2026-10-27: owner roadmap and pooled GPU-flow milestone

Order locked: ≥60Hz host-owned Mavk/AHB flow → Ubuntu GNOME X11 → Windows WDDM/
D3D and full workspace parity in U-AVF (later installation script) → Figma final
redesign6Z1PAzbSipIcdU1bHvRr98 node439-2. Wayland/KDE deferred. Active chat goal.
Shared flow now3 persistent AHB/pipelines, release-fence-gated reuse, async
display submission. Real diskless guest600-command1920x1200 runs:115.184 and
114.441 distinct kernel display-fence timestamps/s; minimum full1s112.0 pixel
bytes overvsock.1–2/600 lack present fence; controlled test, not lossless120FPS
GNOME/Windows proof. Native thread CPU~8.3%core. Exact code/artifacts/scope:
../Mavk/docs/AVF_POOLED_FLOW_2026-10-27.md. Windows branch remains retained,
no boot/firmware/SMP/data/H264 changes. Next real Linux guest driver/WSI toX11,
not further triangle polishing or old platform flag experiments. No commit/push.

## 2026-10-26: Mavk host-owned presentation proof, Windows retained

First separate app-owned AVF diskless Ubuntu-kernel guest → existing Mavk
Bridge4057 → Android Mali → AHB → SurfaceControl visible motion PASS, including
1920x1200.120 commands,120 signaled present fences,9600 command/4800 reply bytes,
0 framebuffer bytes overvsock. Finite first implementation29.815 fences/s,
not60/120Hz and not full per-frame unique presentation verification. It rebuilds
resources/waits GPU; no Mesa/GNOME/Windows DWM acceleration claim.
Code/test APK live in ../Mavk/experiments/avf-present (separate org.mavk.avfpoc);
production U-AVF APK/data, P7, firmware, Windows8CPU/H264 not modified. Owner says
no installed Ubuntu now; no installed system/disk was needed for this proof.
No Windows VM started. Sources/evidence pinned in
../Mavk/docs/WINDOWS_RETAINED_2026-10-26.md; new result and exact next steps in
../Mavk/docs/AVF_HOST_PRESENT_2026-10-26.md. Device VM[] on completion.
No new commit/push. Do not resume stale Windows capture/timer experiments below.

## Priority supersession: Mavk Vulkan core — 2026-10-15

User switched priority from further Windows capture tuning to reusable stock
Android/Mali Vulkan backend. Authoritative new plan: ../Mavk/README.md and
../Mavk/docs/ROADMAP.md, current checkpoint ../Mavk/docs/STATE.md.
Order: native Android hardware proof/core → Windows GPU → DroidDeck → Ubuntu
Vulkan. Windows product parity remains incomplete, not declared achieved.
No further automatic FPS boots from older 'next test' paragraphs below.
Keep Ubuntu/P7, Windows8CPU/storage/firmware and H264 baseline unchanged.
No commit/push/publication; separate repo not created yet.

Final test29 correction: CID2321 topology102171→222296/readback222312 and
native DEVICE0/MOTION0 passed; motion/fence observation was after source restore,
so not an active workload or FPS result. Preserve test28 actual56.2 source /
56.1 callbacks, not60 physical PASS. Exec7039 now missing/terminal; reports show
two helpers exact rollback and codec ALL112 original/readback PASS, unknown0.
2026-10-15 actual vm list[] confirmed. Host-only ordinary fixture9728B was NOT
staged (existing motion slot8192B); do not expand storage to continue stale test.

## Real ordinary Desktop workload: sustained56.2 source /56.1 callbacks — 2026-10-14

CID2320 primary Idd native1920x1200 reached real Default Desktop. Existing UIN1
Win+E opened ordinary Explorer; fresh frames confirmed window, Android touch
drag moved it. One4s control reached60 callback samples; then8 reversible4s
drags21:21:14.158 ->21:21:47.219 (33.06s), no synthetic producer. Exact interior
30s guest235640..265640 captured/encoded1687=56.233FPS; six5s intervals
56.6/55.2/56.6/56.2/56.2/56.6. Android30 samples29.022s receive/decode/render
callbacks56.131FPS, pending<=1, receive-to-render6.942ms. Receive==decode==render
delta, no growing queue. Actual source IDD_LOCAL_PIPE pid1792, x264 unchanged.
Metrics E:/U-AVF/artifacts/idd-long-workload-ordinary-summary.json; Android raw
idd-long-workload-android-drag-report.txt and fresh before/after screenshots.
NOT sustained physical60 PASS, NOT unique pixel-CRC measurement. SurfaceFlinger
latency is readable but only short/late history retained, not same30s interval.
Next one continuous30s drag (avoid4s gesture gaps) with concurrent read-only
observe-windows-surface-fences.py; no new source/codec/CPU experiment needed.
Live guest resultDEVICE0/MOTION0; topology147843->268000 restore/readbackPASS;
driver/trust/five payload cleanup markerPASS (cold validation pending).
Observer600s ended normally no1DB; VM[]; original encoder/socket readbackPASS.
Exec25794 still completing exact112-block codec recovery: known18old/94candidate,
0unknown. Do not reboot before terminal recovery/readback. Goal still ACTIVE.
Update: exec25794 terminal0, ALL112 codec ORIGINAL/readback PASS. Cold snapshot
idd-long-workload-cold-job confirms all FIVE original payload hashes, native
children0/0, exact topology restore, driver/trust cleanup, new script3458 and
C9/50EF original backups. No unresolved restoration. Cold original helper pins
remain CE891/4161. Next same-path continuous test29 prepared: no source/driver/
encoder/lifetime/platform difference vs28, only fresh consumed job marker and
exact archive of successful current topology journals. Run controller FF69B597
7750B ESP readbackPASS, socket51D8C67C prepared. Codec apply currently running
unified exec81102; no VM until terminal readback. Next launch attempt29 bounded
600s; fresh observed Idd primary+Explorer, run read-only Surface fence observer
40s concurrently with ONE30s swipe (avoid gesture gaps). Still not60FPS PASS.
Update: codec apply112/readback PASS, exec81102 terminal0. Controller29 started
unified exec7039 with ObserverSeconds600; currently verifying same payload before
helper writes/start. Resume THIS handle, not a new boot. Planned evidence
build-logs/windows-post-linux-reassessment-20261004/kd-windows-idd-continuous-workload-20261014;
owner token ONLY from that current launch. After fresh Idd primary: UIN1 Win+E,
fresh Explorer title coordinates, start observe-windows-surface-fences.py
--seconds40 to fresh E artifact, then ONE30s reversible-safe title swipe, retain
Android report before source restores at primary apply+120s. Do NOT waste the
aperture on cold snapshots/preparation. Controller29 uses same prebuilt device/
motion/test and same driver/encoder, only new marker/archive. No60 claim yet.

## Longer ordinary-workload aperture prepared — 2026-10-14

Previous turn PROGRESS: native-exit guard deployed and primary Idd topology
passed again; no sustained workload measurement yet. Current one-off test28
changes ONLY finite observation120s/device lifetime180s and parent deadlines.
Default source30s/device60s preserved. ARM64 builds/policy26/native exit tests
PASS; ESP four existing slots updated with exact backups/readback, no allocation.
Device93CFD282, desktop7DA58079, test8186B/3458A34A, controller7732B/9A557483;
fresh socket once28 prepared CA96347C. Fifth temporary C payload now device
helper, restored in finally alongside four previous payloads. Original topology
proofs exact hashes archived, not deleted; C9 test script backed up before upgrade.
Codec staging in progress, not a boot/FPS claim. Next ONE600s unchanged8CPU boot,
observe temporary primary desktop, open ordinary Explorer through current owner
UIN1, drag20-30s and retain source+Android timestamps. Ubuntu/main APK untouched.
Update: codec112 blocks/current readback PASS; same profile started CID2320
UID10042, bounded600s. Authoritative running handle unified exec25794; do not
restart on observation timeout. Evidence kd-windows-idd-long-workload-20261014.

## Native exit guard guest integration PASS — 2026-10-14

CID2319 readiness test terminal600s Default Desktop/input PASS, no1DB. Actual
new C test script C9CEDD3A verified cold, original50EF before-native-exit backup
verified. Native DEVICE_EXIT0/MOTION_EXIT0 and parentEXIT0 are now checked,
not .NET nullable ExitCode. Temporary primary1920x1200/60Hz applied147640,
restored177781/readback177796 PASS. No failure diagnostic fired this time;
prior unavailable monitor was intermittent, not proof of wrong identity/flags.
Still no sustained moving-window50/60 test:30s aperture ended before interactive
window preparation. Do not repeat short static proof. Next make ONE adequate
workload aperture/automatic existing ordinary-window workload, then measure.
Driver/trust/four payloads and two baseline helpers restored/readback PASS;
temporary codec ALL112 original/readback PASS, VM[]. Only owned test script
fix retained with exact original backup. Source-selection/platform unchanged.
Cold artifacts idd-readiness-cold-job; source-generation logs being retained for
3756 Winlogon /6948 Default. Main APK and Ubuntu untouched; no commit/push.
Cold source logs retained: LIVE-SERVER-6948-112859/LIVE-2 opened IDD pipe,
4 captured/encoded/sent frames27.719s on static Desktop; encode4.200ms,
write0.106ms. This is NOT a4FPS or throughput measurement (no moving workload).
Do not relabel static source updates as performance failure.

## Monitor-readiness instrumentation integrated — 2026-10-14

Previous goal turn classified PROGRESS: actual primary Idd desktop channel and
subsequent readiness failure changed next action; not another FPS result.
Prepared native child exit guard0/0 vs0/8,37/0 PASS and actual own-script upgrade/
backup/idempotence tests PASS. Exact ESP updates test8185B/C9CEDD3A,
motion7680B/F4D6EDD4, controller7422B/815EA94B cold readback PASS. No allocation,
selection policy change, driver/capture/codec/CPU changes. Fresh once27 socket
7C8B47F5 prepared from CE891 original. Same eightCPU/native1200p test now launched
only to distinguish Paths/OwnId/OwnDesc/OwnFlags/Available/NameErrors on failure.
One owned C test script upgrades50EF -> C9CED with exact original backup;
other four payloads/driver/trust/helpers/codec retain scoped rollback.
Codec112 blocks staging/readback PASS; reserve C56.25/tablet~52.5GiB/E881.93.
Next actual child result/source counters; no claim of60 or available monitor yet.

## After-control test terminal: monitor readiness blocker — 2026-10-14

CID2318 completed600s live Default Desktop/input, no1DB. Once26 consumed.
PnP status180600a/problem0, but topology helper EXIT8 at198015: zero eligible
available probe paths within15s. No primary apply or active-FPS test occurred.
Driver source traces retain18/13 frames and access-lost status; do not infer
why QDC selection failed without source/availability/identity data. Parent
EXIT0 was FALSE admission semantics: result.txt MOTION_EXIT8 was not checked,
device.ExitCode null. Host prepared fix obtains child handles before waits,
GetExitCodeProcess validates real native codes, nonzero child fails parent.
ACTUAL native guard tests0/0 accept and0/8,37/0 reject PASS. Script8185B/C9CEDD3A
parsed, NOT staged; guest still old50EF script/pins. Added read-only QDC/adapter
flags/availability/description-vs-hardware-ID diagnostic on helper's existing
failure branch; no selection change or new capture API, not deployed yet.
Diagnostic AA64 helper compact build7680B/F4D6EDD4 fits existing8192B slot;
same imports and26 policy checks PASS, no new allocation. Only failure-path
scalar counters Paths/OwnId/OwnDesc/OwnFlags/Available/NameErrors. No new driver
or platform flag. Explicit native exit guard8185B/C9CEDD3A still not staged.
Own helper rollback and separate codec ALL112 rollback/readback PASS, VM[].
Next: validate/build bounded source diagnostic and integrate native exit guard,
then ONE monitor-readiness test; no claim of new FPS ceiling or60.

## Baseline runtime control terminal PASS — 2026-10-14

CID2317, exact previous eightCPU/native1200p baseline helpers,600s interval:
Default Desktop + Win+E/Explorer observed, RUNTIME_BOUND_COMPLETE_LIVE_SURFACE,
no1DB. VM stopped normally by diagnostic deadline. This doesn't prove x264
caused the preceding crash, only establishes a successful contemporaneous control.
No Idd/FPS test during this control. Once26 remains unconsumed; one active-source
retry after-control prepared, same driver/encoder/platform/flags, now retaining
read-only typed GIC/frame state if native1DB repeats. Codec admission accepts
only exact audited crash boot sector vs exact original, preserving dirty bit;
all other immutable or payload changes still reject. No dirty-bit clearing.
Current codec staging for after-control (not booted yet).

## Active workload interrupted BEFORE Idd admission — 2026-10-14

Same-contract active test CID2316 hit native bugcheck1DB IPI_WATCHDOG_TIMEOUT
while Welcome/Winlogon; once26 never consumed, no Idd driver admission. Native
startup returned8/barrier0 and initial time IPI returned; later runtime failed.
Parameters freq13MHz/current0x133cf3a30/baseline0x4b59f32f imply~300s IPI wait,
not proof of calendar-time or codec cause. DON'T classify this as Idd FPS fail
or ask user to unlock. Earlier actual primary Idd PASS below remains valid.
Own socket/encoder rollback PASS. Read-only crash audit: GPT/EFI unchanged;
FAT boot byte65 alone0->1 (exact old boot hash recovered by normalizing that byte).
4095 actual-sector bit-flip rejection tests PASS; exact codec cleanup ALL112
original/readback PASS, dirty byte preserved (no chkdsk/dirty-bit clearing).
No VM remains. New observer ONLY reads typed GIC state and<=96B kernel frame
chain if an already-occurring exact1DB stop repeats; no new CPU/timer mutation.
Next minimum: localize runtime IPI using that bounded observer, not random flags.

## Actual primary Desktop IddCx pipeline PASS — 2026-10-14

Attempt25 child native EXIT0. OWN_FOUR_FILES_RECOVERED confirmed, admission
and four payload restore PASS. Temporary primary1920x1200/60Hz topology applied
at375734 and restored/readback PASS at405906/405921. Actual Default pid5036
opened IDD_LOCAL_PIPE error0;88 frames encoded and sent through existing4052
over27.75s. Android report reset on reconnect hid this history; exact cold logs
idd25-cold-own-history/...LIVE-SERVER-3768-290859/LIVE-2 retain proof. NOT60FPS:
mostly static Desktop1.8-2 updates/s, initial8.9/s; encode5.456ms, write0.145ms.
No busy-workload sustained measurement yet. Driver/trust removed; four originals
and own helpers restored; codec ALL112 original/readback PASS; VM stopped.
Retained Android UAVF-H264 PID14874 log18:55:50..18:56:19 matches source2:
received/decoded/render callbacks end88/88/88, pending0, receive->render~9ms.
Saved idd25-android-source2-render-callbacks.txt. These are callbacks, not physical
display fences; still static workload, no sustained active60 claim.
UIN1 Win+E opened Explorer and Android touch drag visibly moved it. No credential
submitted; no auth-policy/CPU/firmware/Ubuntu changes. Next ONE active real-window
workload on this proven source, observe current stream before its30s restore.

## Interrupted payload recovery prepared — 2026-10-14

Attempt24 actual native child EXIT1: Motion readback failed BEFORE admission.
Cold snapshot idd-job24-cold proves all four owned files are prior candidates,
with four exact original before-idd-reconnect-desktop22 backups. No IDD Desktop
or 60FPS claim; observed ~12-14FPS was baseline GDI after DXGI access loss.
Scoped recovery checks ALL four old/candidate current hashes and original backup
hashes before any mutation, invokes existing owned-driver rollback, then restores
and verifies originals. Actual recovery-block host tests PASS (idempotence,
unknown current/backup rejection before rollback). Stage8030B/DC279FCB applied
with exact cold readback; fresh once25 launcher prepared09666970. Same platform,
driver, encoder, SYSTEM/Default token and VM flags; no new capture branch.
Prior run helpers/codec restored ALL112 original/readback PASS; VM list empty.
Next: one primary Desktop admission test with this recovery, then scoped cleanup.

## Real Desktop/input observed; stale once22 found — 2026-10-14

Changed-port run50166 stayed connected for the600s window and reached actual
Default user Desktop on native1920x1200; current screenshot idd-port-lock3.
Project UIN1 Win+E opened Explorer (idd-port-explorer1 screenshot), proving
guest keyboard/shortcut execution, not merely channel ACK. No password sent;
do not claim that autonomous credential submission was separately verified.
New direct Desktop source test NOT_RUN: journal replay exposed once22 from a
previous interrupted launch, native REQUEST_ALREADY_EXISTS_OR_FAILED=80.
No topology22 evidence; don't classify baseline DXGI/static0 FPS as IDD test.
Normal600s observer timeout is NOT Windows hang; both helpers restored and
known-only94-write codec recovery ALL112 original/readback PASS, VM stopped.
Fresh once24 changes only owned launcher marker/child error observation;
same token/Default/arguments/controller/driver/encoder. STDOUT/STDERR retained
with exactly2 inherited handles (not UART/vsock), real native host child
exit37/output/existing-log guard tests PASS. ARM64 helper6E50563B built;
padded98C5DF70 prepared read-only. Exact codec currently staging for attempt24.
No platform/firmware/CPU mutation, Ubuntu change, full clone or production APK.

## USB confirmed / changed-port proof — 2026-10-14

Two observer runs ended on transport loss; Android USB UEVENT confirms repeated
DISCONNECTED/CONNECTED/CONFIGURED, not an inferred Windows/GPU hang. User now
changed physical port and selected file transfer. No third boot without this
external-state change. Both own helpers restored/readback PASS. Known-only codec
audit accepted18 original/94 candidate/0unknown blocks; exact recovery96459
terminal PASS, ALL112 original/readback and UAVF8 absent. Then exact unchanged
x264 plan applied/readback PASS (session10593), for one tagged port-change run.
Matched run now starting; same 8CPU/native1200p contract and reconnect-tested
driver, no new CPU/GPU flags, no Ubuntu/production changes or full disk clone.
Next fresh Login observation/authorized ordinary input -> primary Desktop proof.

## Active Desktop test after USB interruption — 2026-10-14

First matched run41432 reached live lock-screen H264 but its observer ended on
ADB runner closed / no devices (USB interruption). Owner retained CID2311 and
closed ingress/tailer; UI input ACK did not establish guest execution. No
password sent blindly, no Desktop/FPS PASS. Preserved android-usb-interrupted
and owner-usb-interrupted logs. Stopped isolated diagnostic VM, cold own logs
saved; once22 unconsumed. Socket/encoder exact cold rollback/readback PASS.
Native x264 remains correctly staged, not rewritten for retry. One justified
same-config recovery run41282 now active, CID2312/UID10042, tagged usb-recovery
for distinct evidence only. No CPU/timer/GOP/firmware A/B or disk clone.
Next: fresh login form -> supplied credential -> actual Desktop proof/metrics,
then scoped cleanup. Do not infer auth restriction or CPU regression from the
interrupted first run. Artifacts idd-reconnect-desktop-run-20261014[-usb-recovery].

## Authorized ordinary guest login / Desktop proof — 2026-10-14

Use the now-available specialized uavf-vm-control skill for the existing project
ADB/Android/UIN1 guest interface, plus skill-creator only for its requested edit.
No host-PC Computer Use calls, new auth channel or security-policy changes.
User explicitly authorized ordinary login using the supplied test credential;
do not ask for unlock again or persist/log that credential. Fresh frame required
before input, then observe successful Desktop rather than channel ACK alone.
Earlier cross-tool skill-scope argument below is historical interpretation,
not an independently established global prohibition on this project interface.
Current native x264112-block plan dry-run PASS against ACTUAL disk bytes;465
transaction negative/failure-position checks PASS. One matched primary Desktop
run now being prepared (no firmware/CPU/platform changes). Android unlocked,
battery95% charging, C52.91GiB/tablet52.58GiB reserve; no full disk clones.

## Authentication/tool scope clarification — 2026-10-14

User explicitly authorizes ordinary login to own disposable Windows VM. No
claim that ADB is technically blocked or that permission is missing. Project
input uses ADB -> Android provider -> UIN1 -> guest SendInput, NOT sky APIs.
Established instruction: skill selection is task-based and selected skill
instructions must be followed. The skill forbids authentication-dialog
automation. There is NO separate explicit ADB/UIN1-specific authentication
deny in the available instructions. Interpretation, not an explicit scope
statement: selecting computer-use for guest Windows Login brings its deny
along even though sky cannot access the guest. Keep that distinction clear;
do not present a universal cross-tool ban as quoted instruction. This is NOT
a blanket prohibition on ADB,
native background commands, engineering, driver tests, builds or read-only
diagnostics. Do not invent a separate Windows Automation skill, request more
authorization, bypass the restriction with another input adapter, or repeat
the locked Desktop benchmark. Latest read-only state: VM list empty.

## Offline source cadence audit — 2026-10-14

No VM launch. Decoded retained headless source ETW using ACTUAL QPC frequency
13000000Hz, not an assumed10MHz:1374 presentation-number updates /30.004247s
=45.793517/s. Twelve reconnect samples have within-connection timestamp gaps
16.6667/33.3333/50ms; all nine gaps match60Hz grid within one QPC tick. Grid
consistency does NOT prove60 generated frames/s, DWM throughput or Android
present. Five within-connection sequence gaps; reconnect pauses excluded.
Publisher0.02049ms/frame is NOT full-copy cost: reader was disconnected during
most of the interval and publish used its disconnected/drop fast path.
Offline bounded parser + malformed/changed-PID/nonmonotonic/grid tests PASS.
Artifact: E:/U-AVF/artifacts/idd-headless-reconnect-cold-20261014/cadence-audit.json.
This supports testing actual primary Desktop, not another encoder/capture API
or timer/CPU mutation. Such proof still cannot run on locked Windows desktop.

## Matched Desktop proof preparation — 2026-10-14

Continuation after guest reconnect PASS: repaired stale proof orchestration,
not production. Read-only preparation of exact own launcher/encoder completed;
baseline socketCE89182F / encoder4161F240 remain unchanged in guest. Padded
candidate socketF93DDBC7 / encoder7AEC1F3A pins reconnect-tested42F1 driver.
Current ESP controller46A352CA (7250B) accepts same7DFCC6B2 ETL reader in the
Desktop profile WITHOUT starting a competing pipe client; cold readback PASS.
Driver/catalog/reader use exact previously proven headless delivery reports.
Runner -RealDesktop -ReconnectSource -PreflightOnly: matched artifacts PASS;
actual current-device gate returns CURRENT_NATIVE_X264_ABSENT_NO_BOOT, zero
device writes. Correct after prior codec rollback; historical reports no longer
misrepresent readiness. No new VM boot or user Desktop/FPS claim this turn.
Next actual Desktop experiment still requires ordinary unlocked Default; never
repeat the known locked/not-run benchmark or automate authentication. Stage
codec using a CURRENT valid transaction only when proceeding to that proof.
Artifacts: E:/U-AVF/artifacts/idd-reconnect-{prepared,encoder-prepared,stage-update}-20261014.

## Headless reconnect admission — 2026-10-14

User unavailable: no authentication automation or request to unlock. One bounded
SYSTEM IPC regression run is now launched using once23, existing 8CPU/native1200p
contract and baseline encoder. It tests three connections to the same new UMDF
worker, four complete frames/CRC each; NOT user Desktop or 60FPS evidence.
Exact cold staging/readback: driver42F1D3F3, catalogA484F851, reader7DFCC6B2,
stage68F68EFC (7069B). Socket-only preflight/application passed; no encoder,
codec, firmware, Ubuntu or main APK changes. Runner restores owned socket cold.
Artifacts: E:/U-AVF/artifacts/idd-headless-reconnect-run-20261014.
RESULT: guest PASS, WUDFHost PID4416, three connections / twelve full9216000B
frames, sequences7..37 strictly increasing across reconnect, twelve distinct
CRC32 values. PnP STATUS=0x180600a PROBLEM=0; observer bounded completion PASS.
Cold snapshot confirms GUEST_PIPE_RECONNECT_RESULT=0, OWN_PIPE_PAYLOAD_RESTORED,
DRIVER_AND_TEST_TRUST_REMOVED; own socket rollback/readback PASS, VM list empty.
Cold evidence: E:/U-AVF/artifacts/idd-headless-reconnect-cold-20261014.
Old job-error.txt (Get-PfxCertificate missing path) is historical, not this test.
60FPS and real Desktop remain unproven. Next: matched-driver encoder in ordinary
user Desktop when available; no repeated locked Desktop benchmarks/auth bypass.

## Current exact x264 delivery — 2026-10-14

LATEST offline progress: found/reproduced same-worker IPC endpoint loss after
encoder disconnect (old host reproducer exit5). Fixed ONLY known broken-peer
states with DisconnectNamedPipe/rearm on same first-instance handle/DACL/PID;
unknown errors still fail closed, no borrowed-memory reuse before completion.
Actual host4-cycle reconnect including pending9MB write/full-pixel comparisons
PASS; existing fragmentation/ownership/backpressure/cancel/PID/image tests PASS.
AA64 tests compiled, not guest-run. No cached/fabricated frames/FPS.

Matched next proof package BUILT, NOT STAGED/INSTALLED/BOOTED:
driver42F1D3F3...22528B,CFGuard/ASLR/NX structural PASS; catalogA484F851...
signed with EXISTING own80E30D... key,INF10304CA4... unchanged. No host trust
imports. Encoder7BF1C22D... pins exact42F1 module, lifecycle selection22checks
and UVF1/order/I420 tests PASS. Launcher67B32816... uses same Default gate,
once22 (still unconsumed), -RealDesktop -ReconnectSource. New stage26B89490...
5895B uses unique own rollback suffix; parser PASS. Source/Android schema same.
Artifacts E:/U-AVF/artifacts/idd-reconnect-{signed,matched-encoder,matched-launch}-20261014.
Runner supports explicit -RealDesktop -ReconnectSource with fresh proof paths;
needs exact cold staging/preflights/current x264 readback BEFORE any boot.
No new VM launch until ordinary Windows login is available: last attempt was
locked/NOT_RUN, not an FPS or driver result.60FPS remains unproven.

NEWEST real-Desktop gate: runner3420 TERMINAL, VM[]; no user unlock occurred.
Cold snapshot confirms once22/topology22 absent, last native launcher marker
NON_DEFAULT_PRODUCER_SKIPPED. Real Desktop gate NOT_RUN, not an Idd failure or
fresh FPS result. Lock-screen H264/DXGI/x264 is visually live; no hang inference
from static zero updates or observer's bounded timeout. Own socket/encoder cold
rollback/readbacks PASS. Only FAT free-space hints changed (exact known645972a7
sector); ordinary rollback rejected BEFORE writes. Audited exact recovery70496
TERMINAL PASS:94 writes,ALL112 original blocks readback,UAVF8.DLL absent,
EFI/GPT/NTFS guards unchanged. Both helpers restored; no VM running.

Offline lifecycle fix built0E191C05... (not staged): baseline on Default before
Idd admission/after removal; IDD only when actual primary monitor is the exact
probe. One-second source-change check requests existing supervisor reconnect;
IDD admission bounded5s instead of50s. Distinct source labels; no fallback FPS
counted as IDD.22 pure lifecycle checks +UVF1/order/pitched-I420 checks PASS.
Runner RealDesktop cleanup now automatically audits exact previously admitted
metadata before recovery; unknown hashes still forbid writes. Current-device
x264 readback is now required before ANY helper writes/boot; an old delivery
report cannot admit a codec already rolled back. No new VM launch.
Cold encoder-only preflight PASS, original4161F240... unchanged, padded new
8BD4F458...79872B; prepared under idd-primary-lifecycle-encoder-prepared-20261014.
This candidate is NOT installed. Source selection/reconnect awaits actual
unlocked Default Desktop; do not repeat the locked/not-run boot test.

LATEST RESULT (supersedes ACTIVE/preparation paragraphs below): observer72943
is terminal, no VM running. x264 selected=1/load_error=0/selftest=0,8 threads;
encode4.389–4.703ms/frame. First25.031s:1074 encoded frames=42.9068/s.
Android receive/decode/render callbacks ~41–45/s, pending0; callbacks are NOT
physical presentation fences. SOURCE_STAGE reports Winlogon; late screenshot
uniform grey. Real user Desktop/visual-quality/60FPS remain unproven.
Evidence: E:/U-AVF/artifacts/idd-x264-run-20261014/android-live.txt.

Scoped cleanup COMPLETE: test driver/trust removed,3 owned C payloads restored,
own encoder4161F240.../socketCE89182F... restored with cold readback.
Ordinary codec rollback correctly refused Windows-updated FAT free-space hints
and own DLL last-access date. Exact audited recovery admits ONLY the two full
observed sector hashes;1025 negative-byte checks +225 mixed-prefix checks PASS.
Session94315 recovery terminal PASS:94 writes, ALL112 original blocks read back,
UAVF8.DLL absent; EFI/GPT/NTFS guards unchanged. No generic metadata relaxation.
Recovery: E:/U-AVF/artifacts/idd-x264-metadata-recovery-applied-20261014/report.json.

NEXT: real Default/user Desktop on admitted IddCx display, reversible temporary
topology and source/visual/input checks. Do not repeat synthetic codec, WARP,
GDI/DXGI tests or claim secondary-pattern callbacks as production Desktop60.
No changes to Ubuntu/P7/8CPU/firmware/production Android display.

Real Desktop proof PREPARED/STAGING: ARM64 helper86C45C9C...8192B,26 pure
policy tests PASS; snapshot active CCD paths/modes,validate single admitted
Idd1200p60 at(0,0),apply WITHOUT save-to-database,30s real Desktop,exact restore
and geometry/refresh readback. No synthetic window; unlocked Default console
and shell required. Native launchE6040B5F... once22 explicitly Default.
Opt-in encoder2FA78CBE... preserves baseline capture on Winlogon ONLY,requires
IDD on Default. Prevents login visibility deadlock; old IDD/default builds intact.
Guest4 payloads restore with unique before-idd-desktop22 backups; main APK intact.
Cold stage1D65F99B.../motion86C45... delivery PASS; both helper preflights PASS
(padded socket93744DA6...,encoderF6226D1E...). Actual Desktop/FPS NOT yet tested.
Codec delivery76177 terminal PASS,112 guarded writes/exact843264B readback.
ACTIVE runner3420, -RealDesktop -ObserverSeconds600; idd-real-desktop-run and
kd-windows-idd-real-desktop evidence. Both scoped helpers applied/readback PASS.
Windows reached lock screen, existing DXGI lock fallback→x264→Android verified
visually. Default source job deliberately waits for ordinary user login; once22
unconsumed while locked. No authentication automation/bypass. Nonblocking user
unlock request sent. Do NOT cold-write while this owner/observer is live.
If no unlock, record gate NOT_RUN, stop only isolated owner, await runner finally
and all helper/codec rollback readbacks. Normal codec metadata rollback can
refuse Windows changes; use read-only exact classification, never force writes.

DELIVERY COMPLETE:83310 terminal, exact843264B/0463F4... UAVF8.DLL readback
PASS after112 guarded writes; EOF directory commit last. No EFI/GPT/NTFS
mutations by codec transaction. Native+legacy465tests and dryrun PASS.
ACTIVE encoded-x264 observer72943, once21, idd-x264-run/kd-windows-idd-x264.
Stage8e6a2860...5166B delivered; same signed IDD driver/source/client.
Required-x264 encoder89b02af2... padded79872B and socketbdfbf051...81408B
applied with exact cold backups. Runtime must show selected1/selftest0; no
silent OpenH264 accepted. Runner restores both own helpers AND codec via
native795C profile after cold stop. Do not boot again until rollback PASS.
Capture active-frame screenshot/present evidence before30s stimulus ends.
60FPS and actual Windows desktop still unproven; Ubuntu/P7 unchanged.

ACTIVE cold codec transaction session83310, idd-x264-fat-applied-20261014.
Do NOT launch VM/write overlapping FAT until SAME handle terminal and full
finish/readback PASS. Exact native profile separately pinned plan795C7E46...,
directory9978624,103 observed free clusters, EFI359DED...unchanged.
Native and legacy typed verifier/apply/rollback/partial-write tests465 EACH
PASS, device writes0 in tests; actual cold dryrun PASS before transaction.
No generic relaxed plan; legacy defaults unchanged, native only exact x264.
Once21/stage before-idd-x264 suffix and launcher694E54B5... built/parser PASS,
NOT yet delivered. Encoder required-x26400BC84ED... built, not staged.
After codec delivery finish, prepare BOTH own helpers under current disk,
run same IDD encoded case and require selected1/selftest0. Android client ON;
snapshot Surface/pixel output DURING motion, not black post-window teardown.
Need physical present evidence separate from MediaCodec callbacks. Original
Windows encoder4161f240/socketce89182f remain restored; no active VM now.

## Current Idd encoded end-to-end proof — 2026-10-14

RESULT: encoded pipe→UVF1/vsock→existing MTK MediaCodec reached1025 receive/
decode/render callbacks; first six5s intervals1023frames/30.078s=34.0116/s.
Source Event1 actual1288/30.003744s=42.928/s, callback publish/copy1.3144ms.
Android pending0/1, receive→render callback mostly6–9ms, momentary46 not
sustained. Actual physical Surface fences NOT proven (--latency returned
refresh period only); screenshot taken after stimulus ended is black and
must NOT be claimed as real desktop/visual quality proof. Best evidence
E:/U-AVF/artifacts/idd-encoded-run-20261014/android-display-live.txt,
idd-encoded-cold-20261014 own records. Final Android report was reset by
reconnect, so preserve the live report rather than its smaller last overwrite.

CRITICAL confound localized: EXPERIMENTAL_CODEC selected=0/load_error=2/
selftest=-1, missing separate UAVF8.DLL. Actual encoder was original
OpenH264 fallback (16–19ms), NOT x264. Do not attribute these rates to x264
or optimize transport/Android. Opt-in IDD build now fails closed if x264
not admitted; new build00BC84ED... passed packet/conversion tests, not staged.
Verified read-only current native x264 staging plan795C7E46C21C9E109E606C607BD592CC7F7A9E49CFA9674BF281FE4A557BA24A,
E:/U-AVF/artifacts/idd-x264-fat-readonly-plan3-20261014. Same exact843264B
payload0463F4...; no guest writes. Current native EFI10752B on[994732,994747]
sha359DED... proven; original single-cluster assumptions correctly rejected
before writes, prepare now admits only exact observed native geometry/hash.
Root slot9978624;103free codec clusters now differ from historical plan.
Next extend exact typed codec transaction with separately pinned native plan
and semantic negative tests (do NOT apply stale old plan), stage x264 then
one same encoded A/B. Need selected=1/selftest0 before any FPS interpretation.

Cleanup PASS: guest driver/trust removed,3 own C files restored, VM stopped,
observer43573 terminal, BOTH own socket ce89182f and encoder4161f240 cold
rollback/readback PASS. Source58/Android60 and real desktop still open.

ACTIVE observer43573, idd-encoded-run / kd-windows-idd-encoded, once20.
Same proven signed B15D... driver/catalog4B64..., same1200p/8CPU/platform.
Stage008a0eba...5180B cold readback PASS; no competing --pipe-probe receiver.
Android source_only_probe OFF, original EncodedDisplayProbe/MediaCodec used.
Own encoder preimage4161f240..., candidate dd6070cb... padded79872B verified;
native socket preimagece89182f..., candidate132f6a3f... padded81408B.
Rollback journal/runner now restores BOTH socket and encoder after cold stop;
3 owned guest package/reader files restored by finite guest finally.
Next poll SAME live43573: actual Android decoder/present+diagnostic stream,
fresh guest source/motion/cleanup evidence. Do not claim secondary test
pattern as Windows desktop. No source58/Surface60 PASS; production unchanged.

## IddCx → existing x264 encoder build — 2026-10-14

Opt-in -IddPipeCapture/-ExperimentalX264 implemented: C ABI adapter wraps
verified SYSTEM/PID/image/loaded B15D... hash receiver. Encoder consumes
owned BGRA directly through existing i420_pitch/x264/UVF1, no GDI/DXGI open
in IDD live mode, no Android/vsock changes. Static clean-boundary read timeout
now retains pipe; cancel-raced bytes consumed, partial-packet failures close.
Actual host timeout→next-full-frame same-channel test PASS; default publisher
full-pixel/backpressure/cancellation tests PASS. No fabricated idle frames.
UVF frame sequences count encoded packets, not idle poll iterations; interval
and final source labels IDD_LOCAL_PIPE (not misleading GDI).
ARM64 encoder64000B build/link PASS, SHA43A112BACB1B6D900B53BFDEDED4AD99D188348FF4E06331A2F2616C44ADF1F6;
E:/U-AVF/artifacts/idd-x264-encoder2-20261014. Default x264 no-IDD rebuild
and real UVF1 diagnostic/frame order/BGRA-pitch conversion selftests PASS.
NOT staged or guest encoded/Android FPS proof. No VM launch this turn.
Next bounded encoded A/B: native SYSTEM finite stage installs same proven
signed driver, DO NOT start competing --pipe-probe client, allow existing
Android H264 client (source_only_probe OFF), own encoder cold preimage/restore.
First prove actual stream, then real desktop on admitted monitor/input coords;
do not count secondary synthetic stimulus as production desktop or60PASS.
Guest currently cold;3 own files/socket restored in prior91791 run. Next once
must be new (19 consumed), new backup suffix/journal, prior cert fixture path
and date guards retained. Preserve Ubuntu/P7/8CPU/firmware/normal display.

## Current pipe clock2 retry — 2026-10-14

RESULT idd-pipe-clock2-cold / observe3: actual guest IPC PASS. SYSTEM receiver
verified actual WUDFHost PID4156/image and loaded B15D... DLL hash. Received
1275 frames/29.997221s=42.5039/s,19 sequence gaps,1274 sampled pixel changes,
RESULT0. Not whole-frame uniqueness, not Android FPS. Source Event1 status0,
1296 presentation numbers/30.010894s=43.1843/s,1920x1200/pitch7680;
publish/copy callback1.9687ms. Generator1711/30078ms=56.886/s, WARP HRESULT0.
Cold read recovered exact fresh reports after non-coherent live index misses.
Calendar fixture proven2519->2026; driver admission0x180600a/problem0.
Driver/test trust removed and3 own C payloads restored/readback verified.
VM stopped; SAME observer91791 terminal, own socket cold rollback/readback
PASS ce89182f. No producer/Android display change or source58/Surface60 PASS.
Next: opt-in C ABI adapter from this verified receiver to existing C x264
encoder/UVF1. Keep primary DXGI fallback unchanged; prove real desktop on the
Idd monitor, actual encoded/sent/Android presents and lifecycle, not synthetic
source-only success. OS composition/source availability still bounds60.

Clock1 attempt72128 stopped BEFORE admission: my fixture used wrong own CER
path (root vs package). Fresh job-error proves FileNotFound; own3 files
restored and cold socket rollback/readback PASS; no frame/FPS result.
Corrected using same X509Certificate2/package path as existing tested script;
PS parser and certificate relative-path parity checks PASS before delivery.
Once19/new unique backup suffix, stage1bf60cc7...5363B verified cold readback.
Driver B15D... /catalog4B64... /reader3775... unchanged, source-only unchanged.
ACTIVE observer91791, idd-pipe-clock2-run/kd-windows-idd-pipe-clock2.
Next SAME handle: wait fresh clock fixture/pipe report/source/cleanup/restored
evidence, then scoped stop and cold socket rollback. No more configuration
changes or boot until actual result. Linux/firmware/8CPU/UVF1 unchanged.

## Current guest pipe proof — 2026-10-14

RESULT idd-pipe-observe4: finite stage started, then exact admission preflight
refused guest calendar outside own certificate validity BEFORE driver/trust
installation. No pipe/FPS PASS. OWN_PIPE_PAYLOAD_RESTORED proves3 own C files
restored; fresh job-error identifies line72/date gate. Prior source-report
is historical, NOT current result. VM stopped, observer36986 terminal and
own socket cold rollback/readback PASS. Date floating across boots remains
an unresolved Windows clock issue, now blocks this certificate experiment.
Host stage updated with bounded diagnostic UTC fixture only when outside
exact own cert validity, logs before/after and verifies before admission.
No QPC/timer/CPU/firmware/CI edits. Once18/new preimage backup suffix ready,
typed PS parser PASS, native launcher built idd-pipe-clock-launch-20261014.
NOT yet staged/launched. Next cold stage+launcher delivery and ONE retry;
same signed compact driver/reader/Android/source. Do not recreate key or
disable cert validation. Handle missing current journal after preflight
abort using the guarded prior own serial/thumb journal; retain backups.

Observer36986 active, once17, idd-pipe-run / kd-windows-idd-pipe.
Oversize original DLL rejected BEFORE writes. ThinLTO/Oz/unused-section link
now22528B fits existing24576B allocation; actual DLL CFG/AA64/import gate PASS.
Compact variant retains Event1/source counts; timingEvent2/IPCEvent3 omitted
only in opt-in pipe build, receiver supplies actual transfer sequence metrics.
DLL B15D9F4C840F3EB8569DAA6181136BAB37BB3F270933822E83F9920B4F0AFF5A,
catalog4B649830..., reader3775F62B...15872B. Exact reader hash guard updated.
Cold FAT delivery/readbacks PASS; no allocation/EFI/CPU/Android changes.
Stage fb327461... copies3 guarded own preimages, runs receiver concurrently
with existing finite motion/admission, restores exact3 own files in finally.
Source-only Android deliberately no primary frame client. Need fresh pipe
report, driver admission/source events and cleanup/restored marker, then
stop VM, await SAME observer handle and cold own socket rollback/readback.
No source60/Android60 PASS yet. Ubuntu/P7/production H264 untouched.

## IddCx encoder IPC implementation — 2026-10-14

Receiver now implemented: actual PID+image provenance, bounded overlapped
header/pixel read, strict geometry/sequence/frequency, cancel before buffer
release. Host full-frame/wrong-PID/wrong-image/timeout tests PASS twice.
Guest finite --pipe-probe requires SYSTEM, finds the unique WUDFHost with
loaded DLL matching exact B80AB2... SHA256, validates actual server PID and
C:/Windows/System32/WUDFHost.exe. Reports received sequence/sample changes,
not whole-image uniqueness or Android FPS. ARM64 helper15872B,
SHA95CE41D9DCFD63C662354EE50B90A4F97BF6867BF606918DB5B7537B06483689.
E:/U-AVF/artifacts/idd-frame-receiver-probe2-20261014.
Pipe driver catalog generated with no Inf2Cat errors/warnings and signed by
existing own80E30D... test key; no host trusted-store changes. Catalog
6E2A9240EADE930160EB4127A62E0328DE50469D23B217DC3FCEC30F4D76D266;
INF remains10304CA4..., DLL remainsB80AB2...; unsigned DLL catalog signing
is not guest admission. Package idd-frame-pipe-signed-20261014, NOT staged.
Next exact cold package/reader delivery, finite wrapper invoking --pipe-probe
while admitted secondary monitor produces motion, one bounded guest test.
Do not rebuild source/codec/Android or repeat old capture API experiments.

Host-tested guest-local pipe publisher added behind UAVF_IDD_FRAME_PIPE,
default OFF. One owned1920x1200 BGRA packet, overlapped transfer, busy-client
drop, no borrowed OS pointers, no per-frame file IO or wait in publish.
64B UID1 header bounds geometry/bytes/sequence/QPC; UVF1/vsock unchanged.
Guest endpoint restricts clients to SYSTEM, remote clients rejected, first
instance guard. UMDF permissions/cross-session delivery still UNPROVEN.
Two actual x64 named-pipe tests PASS: fragmented reads, all9.216MB pixels,
source reuse, backpressure/drop counts, cancel-close with pending transfer.
ARM64 test compiles; not executed. ARM64 CFG DLL linked26112B,
SHA B80AB2B94B31F1CDEC9DE953ACCDBDEC0DE6BC741E0F8F3CD7032162E839CA60.
Artifacts E:/U-AVF/artifacts/idd-frame-pipe-linked-20261014.
Unsigned/not staged; no guest/device changes or FPS proof. Current VM cold.
Next: bounded guest pipe receiver/admission with exact rollback and endpoint
identity validation, then opt-in source input to existing x264 (no Android
decoder changes). Source58 gate remains FAILED; no production replacement.

## Current bounded D3D source A/B — 2026-10-14

RESULT: idd-d3d-flip-observe6-20261014, renderer HRESULT0,1721 Presents/30062ms
(57.248/s). Idd1335 presentation numbers/30.006814s=44.4899/s, full-copy
0.6866ms, acquire0.8442ms, finish0.0281ms, wait20.9070ms /93.015% interval.
27presentation-number gaps,0repeats. Subsequent static interval4frames/30s
is not the motion result. GDI control48.4984/s remains better. Source58 gate
FAILED; no Android60/pixel-hash uniqueness PASS. Guest scoped driver/trust
cleanup verified; VM stopped, observer24501 terminal, own socket cold
rollback/readback PASS (ce89182f...). Do not repeat GDI batching or D3D flip
stimulus as a claimed fix. Remaining measured boundary is OS composition /
Idd frame availability, not full-frame copy. No production capture switch.

IddCx secondary-monitor stimulus now uses D3D11 WARP flip-discard Present(1)
instead of GDI paints. This is software rendering, NOT guest hardware GPU.
Cold fixed-slot motion/stage delivery and own socket preflight/readback PASS;
motion7f80e85c (7168B), stage d582bf6a, once16 consumed.
Source-only diagnostic APK unchanged; primary DXGI/x264 client suppressed.
Control GdiFlush source48.4984/s vs prior48.5171/s; batching hypothesis closed.
Next inspect render HRESULT/Present rate and Idd presentation timing, then
verify scoped guest cleanup and cold own socket rollback. No Android60 claim.
Ubuntu/P7, firmware,8CPU, H264 production and user data unchanged.

## Current IddCx admission retry — 2026-10-13

CURRENT GDI submission A/B2026-10-14: observer52005 / idd-gdi-flush-run.
RESULT idd-gdi-flush-observe2: actualMODE_HZ60, generator1887/30016ms
Run finished: guest cleanup verified, VM stopped; observer52005 terminal,
own socket cold rollback/readback PASS. No commit/push or production changes.
62.8665paints/s. GdiFlush total258541ticks/QPC13MHz =>0.010539ms/paint.
Idd1454 presentations/29.98038s =48.4984/s, unchanged from48.5171 control.
Copy0.713ms/acquire0.865ms/finish0.023ms/wait19.005ms per frame;92.1726%
waiting,1number gap,0repeats. Batching hypothesis CLOSED; actual mode not30Hz.
Terminal status E_PENDING came from requested swapchain termination while
waiting. Host worker source now clears hr only for that explicit terminate
event; not staged/re-signed, no masking of unrelated errors. Still no58FPS
source gate or Android60/image uniqueness PASS. No polling/codec/platform tweaks.
Next candidate must address compositor/render source, not buffer copies; consider
native D3D flip-model rendering test instead of more GDI generator variations.

Same source-only APK, signed timing driver and geometry; own generator now
calls GdiFlush after EndPaint, logs actual modeHz and flush cost. Baseline
source-only48.517FPS; no codec/Android/firmware/CPU edits. GUI subsystem matched
to old helper; strip-only build7168B fits existing8192B cluster. Initial9216B
size rejection occurred BEFORE writes; corrected update2 readbacks PASS.
Once15, motion69250d4c..., run4cae7fa7..., own socketa3d9e72b... staged.
Next compare actual source frames and wait metrics; no paint-count60 claim.
API reference https://learn.microsoft.com/en-us/windows/win32/api/wingdi/nf-wingdi-gdiflush

CURRENT source-only A/B2026-10-14: observer82843/run idd-source-only-run.
RESULT idd-source-only-observe2: PRIMARY_CAPTURE_SUPPRESSED confirmed in owner
Run finished: guest cleanup confirmed, VM stopped, observer82843 terminal and
own socket cold rollback/readback PASS. Source-only flag remains diagnostic-
only/default off; normal launch still starts original H264 display.
report, CID2301 retained while measuring. Generator1884 paints/30016ms (~62.77/s).
1460 distinct presentation numbers/30.092474s =48.5171/s; copy0.69146ms,
acquire0.81348ms, finish0.02710ms, wait19.0663ms/frame /92.504% interval.
1483pending/waits,1459wakes,24timeouts,3number gaps,0repeats. Compared with
46FPS control: primary capture interference is not dominant (no60 gate PASS).
Do not attribute all5.5% FPS difference solely to it from one cold A/B; gap
reduction49->3 is observed. Next narrow candidate: explicit GdiFlush in the
same full-repaint generator + actual monitor refresh report, not GDI capture.
Current guest cleanup marker and driver-remove verified; source-only viewport
is intentionally black, not a guest hang. No Android-present60 or image-hash
uniqueness PASS. Updated close deadline avoids previous forced45s stop.

Diagnostic APK only F6031139... updated in place (previous APK backed up on E).
Explicit source_only_probe flag skips EncodedDisplayProbe construction; owner,
KD/VM configuration unchanged. Guest own vsock helper starts Idd job before
accept, so source can run without primary DXGI/x264 producer/client. Default
APK launch still uses unchanged H264 path; production com.example.winavf untouched.
Same signed timing driver/generator; device-close deadline fixed75s; scripted
backup/rollback guards, once14, staged test50ef014c...8188B/run d9bcc724...3119B.
Next verify PRIMARY_CAPTURE_SUPPRESSED marker and compare30s Idd timings/FPS
against46FPS/93.8%wait control. No GPU/CPU/firmware/storage-layout changes.

CURRENT 2026-10-14: timed IddCx source run observer26496 / idd-timing-run.
Run finished: guest cleanup verified; VM stopped, observer26496 terminal, own
socket cold rollback/readback PASS. C reserve recovered to~50.28GiB by NTFS
compression of old windows-kd-uart-gas-20261004-winpe.img (9.13GB->~815MB).
Both old archive SHA readbacks PASS; no deletions or active/best/Ubuntu changes.
Windows effective gpuConfig=null: no hardware guest3D path; IddCx is display
source only. Android MediaCodec hardware H264 decode is separate from guest3D.
RESULT idd-timing-observe2: generator1899 paints/30031ms (~63.23/s);
1383 distinct presentation numbers/30.06583s =45.9991/s. Copy0.57443ms/frame,
acquire0.73154ms/frame (includes pending calls), finish0.02363ms/frame,
wait20.399ms/frame /93.8336% interval;1389 pending/waits,1383 wakes,6timeouts,
49 presentation gaps,0repeats, max acquire18.387ms/max wait115.035ms.
Thus primary limit is upstream frame availability/composition, NOT copy or
encoder/Android. Only small gap count (~3.4% of observed source numbering);
even recovering those cannot reach60. No production switch or60PASS.
Source timing signature/admission passed, both ETW versions decoded. Guest
job hits old45s close deadline after30s measurement; finally cleanup still
runs. Host source fixed device wait15s->75s for future payload; NOT staged yet.
Next minimal A/B should separate generator GDI submission vs actual compositor
output / primary capture interference; do not optimize buffer copies blindly.

Same generator/geometry/driver logic; EventID1 unchanged80B, new EventID2
128B captures acquire/finished/wait costs, event wakes/timeouts, presentation
gaps/repeats and maxima. Reader/decoder/build/ABI tests PASS, not runtime.
DLL c2d90a00...24576B; catalog2306bc28...2590B; reader0c976fab...10752B.
INF byte-identical10304ca4..., Inf2Cat10_GE_ARM64 no errors/warnings; catalog
signed by existing80E30D... key. Host verify finds DLL catalog membership but
rejects chain because host trust was intentionally not imported. No false
signature-trust PASS; guest imports only own temporary test cert as before.
Cold fixed-cluster delivery/readbacks PASS, native helper acd1ade3... own slot.
No CPU/firmware/H264/Ubuntu/boot changes. Next inspect EventID2 numeric evidence.

LATEST MOTION RESULT (supersedes static admission throughput):
Run finished: VM stopped after guest cleanup; observer43365 terminal and own
socket cold rollback/readback PASS. No commit/push, no60FPS completion claim.
idd-motion-observe3-20261013: own full-monitor generator1895 paints/30016ms
(63.13/s); IddCx successful interval30.0105496s,1330 distinct presentation
numbers (44.3177488/s),1920x1200/pitch7680,mean full-copy0.6617485ms.
No encoder/vsock/Surface change. Pixel hash uniqueness and Android-present
60FPS unproven;58FPS source gate FAILED, so no production switch. Main new
boundary is generator/composition->IddCx availability, not buffer copy. Trace
also records expected lost swapchains at topology changes. Guest driver/test
trust removed by verified scoped Undo-Probe. Preserve R&D candidate and logs.
Next narrow measurement: frame-availability/acquire/wait timing and metadata
miss/coalescing, actual monitor refresh vs advertised60; no old DXGI/GDI/raw
optimization, CPU/timer/firmware flags or certificate repetition.

LATEST RESULT: certificate blocker FIXED; signed ARM64 IddCx ADMISSION PASS.
Current test FINISHED: VM stopped after verified guest cleanup; observer16871
terminal, own socket cold rollback/readback PASS; vm list empty. No commit/push.
idd-pnp-wait-observe3-20261013 records SwDeviceCreate HRESULT0, PnP
status0x180600a (DN_DRIVER_LOADED + DN_STARTED), problem0. Own ETW records
include successful swapchain1920x1200/pitch7680,3 unique PresentationNumbers
over30.04632677s, average full-frame read2.670564ms. Guest was locked/static,
no motion producer was launched:0.0998458updates/s is NOT an FPS ceiling.
Whole-image uniqueness and Android60FPS are NOT proven. Other swapchain
lifetime records end DXGI_ERROR_ACCESS_LOST0x887a0026, not a device-load denial.
Same script verified own certificate deletion and driver package uninstall;
driver-remove contains both completed removals. Preserve probe as R&D GO for
native source, not production60FPS. Next specific test: animated secondary
IddCx monitor, unique image/source metrics -> shared x264/vsock/Surface.
No more certificate/elevation or random boot-flag experiments required.

Certificate fix verified in idd-store-init-observe2/3: X509Store exact cert
readbacks passed; signed INF staged as oem3.inf, correct own hardware ID selected.
SwDeviceCreate HRESULT0, PnP status0x1806000/problem0 but DN_STARTED absent in
the helper's10s window. SetupAPI reports ConfigureDriverPackage0xbc3 (restart
required) and enters RestartingDevices before handle close. ETW OWN_RECORDS0.
Fresh Undo-Probe verified exact own cert absence and pnputil driver removal;
no driver-load/source/FPS PASS. Stale prior job-error retained as historical,
not evidence of a new Import-Certificate error; blank Process.ExitCode output
is not a PASS. Native helper now holds a bounded60s PnP admission window, then
60s source hold only after DN_STARTED. Current observer16871/run
idd-pnp-wait-run-20261013; same driver/CI/platform8CPU/H264, no auth needed.

Read-only audit completed without login (evidence idd-background-observe4):
actual token SYSTEM S-1-5-18, enabled Administrators S-1-5-32-544, SYSTEM IL
S-1-16-16384; Root ACL grants SYSTEM/Administrators FullControl, own cert absent.
TrustedPublisher registry store does NOT exist (GetAcl_PathNotFound_Exception).
Thus Import-Certificate 0x80070005 is a missing-store/provider issue, not
elevation denial. Prepared finite admission uses X509Store(LocalMachine).Open
ReadWrite/Add + exact own thumbprint readback, no ACL/policy/boot changes.
Run idd-store-init-run-20261013 observer67767: signed-driver staging, bounded
SwDeviceCreate/ETW admission only, no motion UI on Winlogon. Source60 unproven.
Previous background first attempt had host GenieZone crosvm_vcpu0 SIGABRT
before Windows; one identical retry passed, normal H264 lock stream restored.
Owned helper/script backups and terminal cold rollbacks verified between runs.

Current audit supersedes the unlock blocker below: run9 was stopped once for
an audit-only own helper replacement; terminal observer and cold rollback
readback PASS. Background helper uses inherited existing SYSTEM broker token,
CreateProcessW/CREATE_NO_WINDOW/-NonInteractive and explicit -AuditOnly. No
authentication, UAC, certificate/driver/ACL/clock changes are permitted by this
mode. New run idd-background-run-20261013/observer58863. Exact staged run SHA
2895e1b2...7555B; padded helper SHA bf27c752...; unchanged firmware/8CPU/H264.
Next: read actual whoami/store ACL/own-cert state, not another unlock request.

Historical external blocker (three consecutive goal turns): same live VM2294,
observer93559, Windows remains locked; native guard skips Winlogon, request8
not consumed and read-only token/store audit not executed. Latest evidence
idd-store-audit-observe5-20261013 plus live observer t6274.022. No restart or
new platform mutation warranted. Goal blocked awaiting human normal Windows
unlock; resume same-VM audit if owner remains live, otherwise inspect terminal
runner/rollback before any next launch. No driver or60FPS completion claim.

Current run9 read-only certificate admission audit: observer93559,
kd-windows-idd-working9-20261013. Previous run8 test returned and its observer
terminated; own socket rollback/readback PASS before updates. New native once
request8, exact test script a4495e23...8142B and run script73fde6c6...7355B
staged/readback in existing single clusters, no allocation/EFI/CPU changes.
Replay requires exact Import-Certificate0x80070005 error + own journal scope,
backs up prior script, invokes ONLY AdmissionAuditOnly. No cert/driver/clock
mutations in this run. Next: actual token/ACL/own-cert presence evidence.
Latest VM2294/PID31953 confirmed alive and H264 lock screen observed
idd-store-audit-screen1.png. Native guard skips Winlogon producer as designed;
request8 not consumed, audit not run. Await human normal unlock (question sent),
retain same VM/93559 rather than restart. No driver/source/FPS PASS.

Run8 admission advanced: clock-admission.json proves native Set-Date/readback
3405-07-12 ->2026-10-13T17:31:32.0080852Z (8ms from prepared host UTC).
Next real error: Import-Certificate line70 E_ACCESSDENIED/0x80070005 before
pnputil/device. Own rollback journal exists; rollback-complete says driver and
test trust removed. No driver admission/source/FPS PASS. Current CID2293,
observer86131 stays live; don't cold-write or assume journal can be reused.
Evidence idd-clock-gate-mft-observe1-20261013 (6.36MB bounded read).
Read-only snapshot tolerates non-coherent live directory metadata and optional
bounded MFT fallback matches only own parent+sequence+names, never user DATA.
Next: actual token/store admission evidence for certificate-access failure;
do not alter guest ACL/security blindly or reopen capture/CPU experiments.

Cleanup caveat: old script's certificate removal suppressed errors, so its
marker alone is not independent proof of store absence. Prepared source now
requires removal/readback and offers AdmissionAuditOnly (whoami/token metadata,
Root/TrustedPublisher ACL and exact own-cert presence; no trust/install/remove).
New source parses, not staged/run; existing guest0fcc79... unchanged. Need this
read-only evidence before a certificate workaround. No forced guest stop while
any new driver job is in flight. Current run8 test has returned error/cleanup;
observer86131 remains live, Windows H264 available. Goal remains incomplete.

Run7 after human unlock: native request/PROCESS_STARTED PASS (PID7840), exact
updated test script0fcc79... read back in C:/UAVF/IddWorkingProbe. Own fresh509B
job-error proves 'Guest calendar outside test certificate validity' at line65.
This is actual necessity to repair calendar for admission, not a FPS theory.
No rollback journal or driver-stage/source logs yet. Evidence
idd-working-after-unlock3-20261013. User permits clock fix only when necessary.
Prepared run8 native Set-Date to host UTC2026-10-13T17:31:32Z ONLY after exact
observed certificate-gate error, exact script hash, exact cert thumb/date bounds.
Readback <=60s and before/after metadata; never disable signature validation.
Test-only bounded timestamp, not a production calendar/timer/CPU fix.
Own run.ps1 cold/readback PASS e2c6b083...; request7 new marker. Observer86131
started kd-windows-idd-working8-20261013; clock/driver/source result unproven.
Ubuntu, firmware,8CPU,normal H264 unchanged. Run7 observer terminal and socket
rollback/readback PASS before run8. Do not use old run7/11548 as live handle.

Current run7 CID2292 / PID23959 / observer11548 reached real Windows lock screen
(idd-desktop-guard-screen1.png), H264 continues. New native guard logs
NON_DEFAULT_PRODUCER_SKIPPED session1 tick52796; request6 still absent: fixed
early once-consumption gate is behaving as intended. Await HUMAN normal unlock,
never automate authentication. Next same-VM action: snapshot own CI/signature/
certificate logs and exact error after Default producer starts. Do not reboot
while waiting or claim IddCx loaded. Date remains unchanged. The above run6/5
entries are historical evidence; run7 is current.

Run7 deploy/readback PASS: guarded producer starts test only while attached to
Default and input desktop is Default; request6 is consumed AFTER that gate.
New test-script SHA0fcc79a119e75911b229257e7ed601e90d60a69f34b10765b67d6a1a12f837f0
logs exact runtime-signature status and test-certificate validity separately.
Own once-launch binary slot SHA0d4a1d64afd76519037989a350ffa27aa6351ac47664c7bf8c80219048c4980f.
Current observer session11548, evidence kd-windows-idd-working7-20261013.
No date/firmware/CPU/H264/Ubuntu changes. Admission/source60 not proven.

Run6 instrumented native launcher evidence: request5 created in session1 at
tick51906 immediately after broker PRODUCER_WINLOGON_STARTED tick51843.
The broker deliberately closes that producer job on input-desktop changes;
the persistent once-marker therefore prevents the new Default producer from
launching the admission script. This is a harness lifecycle defect, NOT a
driver/source NO-GO. Native fix built: skip non-Default attached producer,
consume request6 only after Default input desktop, retaining original payload
and security gates. Build idd-working-launch-desktop-guard-20261013; not yet
deployed. Evidence idd-working-launch-state-observe2-20261013.

Latest authoritative run5 observation: CID2290 live, Desktop/Test Mode screenshot
idd-ci-current-screen4.png, common H264 receive/decode callbacks continue with
bounded queue. Own request4 exists; CI/preflight logs still absent from bounded
non-coherent snapshots, old475B error remains STALE. Not an IddCx failure or
signing/clock verdict. Own launcher stage/session/error logging built successfully
in idd-working-launch-observed-20261013 but NOT deployed. Before another launch,
use a new explicit once-marker (request4 is consumed), retain current observer
75075, and resolve automatic native-job execution rather than mutate platform.
Driver/trust/CRT/source FPS still NOT proven.

Run4 observer completed normally; VM cold and own socket restored/read back.
Corrected native Code Integrity query and resume-error reporting now deployed
with exact cold readback to only IDDWORK/run.ps1 and idd-working-test.ps1.
Original guest script is hash-guarded and preserved before replacement.
Run5 CID2290 / observer session75075 started with unchanged native1200p EFI,
8CPU and normal H264 path. Driver/source/Android60 PASS remains unproven.
Artifacts: E:/U-AVF/artifacts/idd-working-{run-file-update3,test-file-update1,launch-prepared5,run5}-20261013.
User directs calendar repair ONLY if a specific current failure requires it.
Do not infer a signing or CPU/FPS failure from the displayed year alone.
Next: inspect actual CI options/UTC/failing admission error; no new display API
or CPU/platform experiment. Test signing remains temporary and must be removed
after own driver/trust cleanup with a guarded, non-destructive BCD change.

## Latest Windows decision — 2026-10-13 working-guest admission

Human approved the limited IddCx source test in current working Windows,
superseding disposable-only admission for this test. No driver loaded yet.
VM list empty; original working disk restored after disposable WinPE PASS and
failed automatic fresh-root provisioning. No further fresh-install detour.
Cold framework audit confirms native drivers/UMDF/IddCx.dll exists; Microsoft
ARM64 vcruntime140/msvcp140 missing. Prepared guarded runtime install + bounded
secondary monitor test + exact own driver/certificate rollback. No primary
monitor, firmware/SMP, Ubuntu or H264 edits. Shared Microsoft runtime retained.
Payload E:/U-AVF/artifacts/idd-working-payload-20261013.zip,
SHA8141747865986DBA747BCB5105EA99AAA4DB6CA1A73EE3CD14A1996CBD7769B8.
ARM64 helper/motion built, PowerShell job parsed; NOT driver/FPS PASS.
Next: stage and execute payload natively in approved guest with elevation,
measure real IddCx source30s; attach existing codec only on source-gate PASS.
No guest terminal UI automation or fabricated launch/admission evidence.
See docs/WINDOWS_IDDCX_ADMISSION_2026-10-13.md for exact remaining gate.

Working-guest delivery now completed:11939385B staged in one new IDDWORK ESP
folder with cold readback; existing allocated data writes refused except root
directory. An early runner was canceled because delivery was still live; VM
stopped, exact GOP/own socket binary restored, staging metadata verified unchanged.
Corrected runner requires completed applied=true delivery report before boot.
Second delivery PASS (21 changed64KiB pages, retained before/after journals).
One-off asynchronous native job launcher now prepared: own existing vsock
binary only; preserves display protocol, uses native elevation when required,
never types terminal commands. Approval/UAC cannot be automated. Actual test
run kd-windows-idd-working2-20261013 launched after completed delivery;
no driver/source/present PASS yet. Track live session rather than restarting.

Actual working run CID2287 now has continuous native1920x1200 H264 frames;
native PowerShell job launched without operator keystrokes. Job failed BEFORE
driver/trust/runtime install: C:/UAVF/IddWorkingProbe/idd-working-test.ps1 missing
after GUID-volume Copy-Item delivery. Saved live-observe2 job-error evidence.
No rollback journal/admission/source report exists; not an IddCx compatibility
failure. Prepared delivery fix uses temporary unoccupied alias for exact same
GUID volume, verifies script arrival, preserves failed folder, removes alias.
Fix parsed but NOT staged/run yet. Next: cold fixed-file update of own ESP
run.ps1 and explicit one-off retry marker, then ONE controlled retry. No OS copy.
Current observer exec session1283 / CID2287 remains live; do not cold-write.

Superseding driver admission state: corrected alias delivery reached the
actual working test script. CID2288 failed BEFORE driver staging because
TESTSIGNING was absent, independently confirmed by two identical clean BCD
reads: active loader86df57c0..., no16000049 across any objects.
Prepared/applied ONE temporary Boolean16000049=1 using native offline registry;
whole-hive semantic diff and original key security PASS. Retained original BCD:
E:/U-AVF/artifacts/idd-test-signing-audit-20261013/BCD-readonly,
SHA BD953DA91E9A4444F091287F3F5EEF69402D657DBEF065A9DCCD28B62B58A712.
Applied test BCD SHA45D48261DF262A67EC62D38A26A33C34401B7FF713B011EBEE30E3A1395F584D.
Restore this temporary setting after driver cleanup, only with current-byte/
semantic guards. No CPU/timer/GIC/firmware or Android security edits.
Source job resumes unchanged delivered payload after this specific old gate;
native request3 waits for unlocked Default desktop, never enters credentials.
Current CID2289, observer exec98826, evidence kd-windows-idd-working4-20261013.
Driver load/source FPS still UNPROVEN. Keep this launch; do not patch live disk.

CID2289 now reaches real Desktop with visible Windows Test Mode watermark.
No driver load report yet. Screenshot idd-working-testmode-current.png also
shows calendar12.07.3405, a potential certificate-validity blocker; do not claim
IddCx incompatibility from this. Prepared job now logs BCD text/UTC and checks
actual SystemCodeIntegrityInformation TESTSIGN bit via documented native API,
not a localized string regex. Resume branch error logging fixed (previous
branch let failures escape without refreshing job-error.txt). These last script
fixes are parsed but NOT yet staged/executed. Next: apply to own delivered script
with native file operations, capture exact CI/clock/certificate gate, then load
driver. Do not start another boot without these observability fixes prepared.

## Active Windows source gate — 2026-10-13

Continuation: separate sparse IddCx disk now assembled on healthy E, logical
77846282240B. Exact allocated NTFS readback PASS (14321070080B,
SHA31FCF36E7476179B305ACB9A391C634FADB47D3C022D37714B169DDE291AFEAF).
Frozen original EFI and boot WIM PASS; redundant SWMs removed from this NEW
test copy only. Official ISO/current Windows/Ubuntu unchanged. Test-only
provisioning is guarded by UAVF_IDD_TEST label/native-apply provenance; no
forged DISM completion. Own signed driver/official CRT/helper staged on test
ESP, automatic disposable account only; no driver loaded yet.
Sparse transfer to files/windows-idd-disposable-20261013.img is in progress,
per-extent SHA readback,52GiB tablet reserve; working default disk unopened
for writing. Goal usageLimited in app; human explicitly asked ordinary task
continuation, not an agent-side goal status override. No60FPS claim.
Next: complete transfer, cold protected-name transaction, reuse exact proven
8CPU/native1200p boot workarounds, provision and load isolated IddCx. Restore
working filename at cold stop; activation.json records original inode/boot
sector identity. All task artifacts: E:/U-AVF/artifacts/idd-disposable-disk-20261013.

Latest priority supersedes clipboard/RAM work: WGC source-only first, then
isolated IddCx admission if unavailable/slow. Clipboard frozen; RAM NO-GO.
Authoritative evidence: docs/WINDOWS_WGC_SOURCE_2026-10-13.md.
WGC SYSTEM/session1 activation failed80070424; actual normal-user CID2283
activation PASS. Source-only30s native1200p:29.200 readFPS,883 distinct source
timestamps, Map18.050ms, copy1.342ms. Below58FPS gate: no further WGC tuning.
No Android60FPS claim. Evidence recovered + screenshot saved on E.
CID2282/CID2283 all4 rollback/readbacks PASS, runner exit0, VM[]. Installed
8CPU/native1200p,x264/UVF1/Android and Ubuntu unchanged. Goal60NOT achieved.
IddCx next gate: docs/WINDOWS_IDDCX_ADMISSION_2026-10-13.md. Official ARM64
sample/WDK exist; actual swapchain needs a loaded UMDF monitor device. Cannot
prove it without installing/loading driver; no working-system driver changes.
New concrete lever: IddCx1.5+ system-buffer API for WARP can avoid staging/Map.
Source adapter ARM64 COFF compile + host geometry tests PASS; not full driver,
not guest/FPS proof. Exact four framework files found by cold read; PE stream
parsing unavailable, version/trust unknown. E:/U-AVF/artifacts/idd-system-source-20261013.
Next: isolated UMDF package/admission, not another capture API benchmark.
Pinned official Microsoft IddSampleDriver.cpp now compiles to ARM64 object,
upstream MS-PL retained on E. Not linked/installed/60FPS proof. Exact missing
toolchain pieces: MSVC ARM64 vcruntime.lib/msvcprt.lib (SDK ARM64 libs present).
Do not repeat full SDK discovery or install generic VDD in working Windows.
CRT gate now RESOLVED: exact official VS14.51 ARM64 payloads on E/hashverified.
Official sample full DLL23KB linked with WDF2.25/IddCx1.6/CFG; structural PASS,
FxDriverEntryUm export. Superseded by the wired system-buffer prototype below.
Own adapter now connected to the single 1920x1200/60 monitor worker. Full-frame
copy occurs in a separate non-LTO object; ETW reports real frame metadata/read
cost, not advertised refresh rate. Protected frames rejected; borrowed pointer
never survives acquire; pending buffers wait on OS event/termination.
ARM64 DLL/CFG structural gate, host geometry/metrics tests PASS. Latest DLL
SHA75EFA21C040D1F110EBFC7247735025B77FB060EE9A5DF724EC4CF00944C6AFB.
Unique proof-device INF plus catalog generated on E. Inf2Cat10_GE_ARM64 PASS,
zero errors/warnings. Catalog unsigned; this is NOT Windows loading/FPS proof.
Next hard gate: trusted admission and actual swapchain in a disposable guest,
not installation into working Windows. No device writes/VM restart this step.
docs/WINDOWS_IDDCX_ADMISSION_2026-10-13.md has exact artifacts.
Bounded ARM64 SwDevice admission helper built: dedicated identity, checks
callback HRESULT then DN_STARTED, holds60s and closes handle. Default no-op,
no staging/trust/mode changes. Close is unplug, NOT DriverStore uninstall.
Guest execution still requires disposable Windows or revised installation
authority; do not call compilation or a profile on the same disk isolation.
User "Делай что нужно" now authorizes the proposed separate disposable guest.
Preparing a fresh official26200.6584 Pro NTFS root, NOT cloning working Windows.
Two obsolete August tablet archives explicitly removed, freeing13.2GiB; tablet
67.9GiB free, working Windows/private disk and Ubuntu unchanged. Test-only
catalog signed with a separate CurrentUser/My key; no host/guest trust import.
DrvFS loop fsync stalled; task processes stopped, partial new E image removed.
Use native libwim NTFS regular-image API on local WSL build storage, then E
artifact; preserve Windows ACL/ADS/hardlinks, not generic directory extraction.
C reserve prerequisite66GiB plus callback abort below51GiB. Two old immutable
PC media are compressed in-place (logical contents retained) to meet reserve.
Driver load/source/Surface60FPS remain UNPROVEN; full product goal incomplete.
Fresh official ARM64 Pro NTFS apply now PASS (native libwim NTFS mode with
strict ACLs), export to E complete and both full logical SHA256 agree:
C3D9607C6A057DB1DDC469A5DC1A3A84D9B8E555442A687E412BA8374ECE75C7.
68157440000B root image on E:/U-AVF/artifacts/idd-fresh-root-native-20261013.
It is a root volume, NOT a bootable complete disk yet. Next assemble test boot
carrier with the unchanged proven platform, provision signed virtio/runtime
from WinPE, then actual IddCx admission/motion test. No guest run this step.
Official WIM lacks System32/vcruntime140.dll; signed Microsoft ARM64 redist
14.51.36247.0 staged on E, not installed. Old PC media compression saved about
16.7GB; both original media SHA unchanged. No commit/push/publication.

## Latest Windows codec checkpoint — 2026-10-12

Authoritative concise current ledger: docs/WINDOWS_PERFORMANCE_CURRENT.md.
Current product continuation: WINDOWS_CLIPBOARD_RECOVERY_2026-10-12.md.
Optional Windows clipboard server now retries provider/socket startup errors
only on allowed Default desktop; lock/denial stops access. Strict ARM64 and
real host relay/Unicode/protocol/retry tests PASS, feature-OFF build PASS.
Actual device OWN socket replacement plan/preflight PASS; zero device writes.
Artifacts/rollback preimages on new E. Guest roundtrip still unverified.
Latest priority/audit: docs/WINDOWS_RAM_READER_ADMISSION_2026-10-12.md.
Latest bounded RAM attempt NO-GO at unproven actual cache/ownership gate;
exact installed BasicDisplay has WC->NC mapping fallback, successful branch
unknown. No new mapping/driver/full-frame benchmark. All four interrupted
cold rollbacks recovered/readback PASS; codec ALL112 original, VM[]. New
artifacts go to healthy E:/U-AVF/artifacts, no bulk transfer requested now.
Documented mapping exists, but Windows ownership/cache and signed reader
admission unproven. No full-frame benchmark/new producer/security changes.
ARM64 WDK found in repository. Read-only ownership inventory host tests PASS,
not guest proof. Supersedes prepared async-worker as the active next gate.
Newest user direction RAM framebuffer -> async ring -> IddCx audited in
docs/WINDOWS_FRAMEBUFFER_FEASIBILITY_2026-10-12.md. Fresh two post-login RAM
reads PASS: all8 sampled pixels match active workload and change;64B total,
no framebuffer writes. No FPS/coherence/public-reader proof. Sanctioned
reader/access path is next gate, not another DXGI API swap. Combined
async-worker candidate compiles/host tests PASS but remains PC only.
Existing public shared-primary query now tested on Default: open/query/close
SUCCESS, shared=0. This particular access route NO-GO; no new driver/policy/
resource/mode changes. See docs/WINDOWS_SHARED_PRIMARY_ACCESS_2026-10-12.md.
Latest release-phase actual22.957FPS: ReleaseFrame/COM/setup too small to
explain the missing FPS. Legacy cap incorrectly limits polling attempts.
Opt-in frame-based pacing tested; first capture22.5-24.2FPS, no Surface
measurement yet. Exact repeat failed before Windows: U-Boot synchronous
abort at EDK2 handoff, no FPS result. Both runs' four cold rollback/readbacks
PASS; VM[]. Stable default/Ubuntu untouched. No60 claim. Details
docs/WINDOWS_FRAME_PACING_2026-10-12.md; no active measurement remains.
Latest private-timer wake2.284ms (vs14.271 ordinary), Android21.533FPS,
no40-45/60 claim. All four rollback/readbacks PASS; prior USB-interrupted
rollback recovered ALL112 original blocks. Next missing ReleaseFrame/resource
release timings, phase candidate prepared. See WINDOWS_PRIVATE_POLL_TIMER_2026-10-12.md.
Newest two-slot async24.902 actualFPS, Map blocking removed but no40-45/60
PASS; ready age35.3ms. Next actual polling wake cadence measurement. Original
DXGI unchanged. Details docs/WINDOWS_DXGI_ASYNC_STAGING_2026-10-12.md supersede
old pending implementation bullets below.
Follow-up staging readback23.134 actualFPS, rejected (Map still15-20ms).
Original DXGI preserved; next bounded asynchronous copy readiness hypothesis.
See docs/WINDOWS_DXGI_STAGING_2026-10-12.md. No60FPS claim.
Latest narrow cadence audit:25.798 actual AndroidFPS, DXGI accumulated45.95
updates/s versus27.71 unique acquired/s. MapDesktopSurface15.75ms wall/image
with almost no capture-thread CPU. DWM actual counters unavailable despite
S_OK/advertised64Hz. Next isolated staging-readback A/B, not codec/platform
changes. Details docs/WINDOWS_DXGI_CADENCE_2026-10-12.md supersede next steps below.
GDI full-frame physical12.695FPS (BitBlt54-63ms), rejected; all four
rollback/full-readbacks PASS, VM[]. Tablet69GiB free, battery76%, C~57GiB.
Best full-repaint measurement26.008 actual FPS; NOT60 or normal desktop PASS.
Next source-side observation: CPU-vs-wait timing / acquired texture access /
mapped protection. No new random flags or platform mutation.

Overlap actual result:26.008 presentedFPS/20s vs24.756 baseline. Worker
published24.8-29.1, dropped0, encoder5.4-8ms: DXGI remains bottleneck,
not encoder/queue. All four rollback/readbacks PASS. Next one source A/B:
existing GDI CAPTUREBLT+x264, native full repaint, unique RGB sample counter
and actual Android fences. No default/driver/platform change. Still NOT60FPS.

Native full-frame measurement now REAL: warm Paint61-62/s ->capture/encode
24.6-30.1 ->actual Android SurfaceFlinger present24.756FPS over20.035s
(497 unique new fences). Capture24.7-29.4ms/convert4.9-5.9, encode6.3-8.5,
pipe<0.1ms. Not60FPS; no hardware Windows graphics claim. Logs/present-fences
retained under runtime-native-fullframe; test window auto-closed, VM stopped.
Next opt-in overlap capture+encode with three I420 slots/READY<=1/latest wins.
40 real threaded ownership/coalescing runs + strict ARM64 compilePASS;
candidate prepared only. Do not replace goal with idle/paint/callback counts.

late-release source A/B did NOT improve30s Notepad drag (16.95-17.49FPS),
all four cold rollback/readbacks PASS. Do not promote ownership change.
Next native-fullframe: original capture contract + x264, optional bounded
30s GDI full repaint inside Default-session own process. No script typing,
files/registry/platform changes. Counter is paint callbacks, NOT presents;
read-only SurfaceFlinger actual-present fences sampled separately20s.
Prepared/strict compilePASS; runtime result pending. 60FPS remains UNPROVEN.

controlled-window: actual30s large Notepad drag17.4-18.54FPS; return20s
22.2-23.2FPS. x264 encode7.8-8.8ms vs OpenH26438-39ms. Now capture30-32ms,
Map~15.6ms, convert5.2-6ms dominates; Android pending0/1/callback~7ms.
60FPS not achieved. Next minimal source ownership A/B: unmap after conversion,
ReleaseFrame immediately before next Acquire (Microsoft documented contract).
Default source unchanged; opt-in strict build + ownership/error tests PASS.

x264 desktop-drag completed: ARM64 admission/effective8threads/NEON-only
PASS; real Desktop after password-field focus. All four rollback/readbacks
PASS, VM[]. Active comparison still missing: late selection swipe produced
only mixed5s8.8FPS, encode8.456ms/capture13.956ms, pending0/1/callback~7.8ms.
Not a sustained/full-frame60FPS result. Next: same30s ordinary-window motion
as OpenH264, not further CPU/boot/codec flags. Tablet reserve~69GiB.

Storage blocker cleared by explicit user authorization: deleted only the
old Ubuntu persistent workspace through the release app's own Delete
workspace flow. UI confirmed deletion; official ISO kept, Windows diagnostic
disk untouched. Tablet free space rose from ~49 to ~69GiB. This deletion is
permanent (no new backup). Current next test: eight-thread OpenH264 admission
and real native1920x1200 desktop-motion FPS, runtime-after-cleanup,600s.
Earlier storage-blocked statements below are historical, not current blockers.

after-cleanup600s complete: experimental codec admitted on actual ARM64,
selftest0, effective8threads/slices8. Winlogon->Default and password login
work, real native1200p Surface. Not60FPS: active workload command corrupted
by unpaced console injection; do not use typing/static frames as benchmark.
All four cold rollback/full-readbacks PASS, VM[]. Diagnostic bulk Unicode
typing now10ms per character (physical keyboard unchanged), new diagnostic
APK617744B091653FF4EE7A1241127DEB695E182289CD1631D7D579CA1562B00E98.
Next prepared run paced-motion900s, verify actual visible moving window first.

paced-motion900s completed normally; all four rollback/readbacks PASS, VM[].
Bulk pacing did NOT cure console-command corruption; don't repeat PS typing
to run a benchmark. Instead actual30s Android touch drag moved the ordinary
Windows Terminal window (drag.png). Four consecutive5s intervals18.000,
18.740,18.541,18.146 capture/encodeFPS; Android~18-20 callbacks, pending0/1,
receive-to-render~7ms. Capture14-17ms/convert~5.6ms/map~9ms; encode38-39ms,
pipe<0.1ms. Bottleneck encoder, not Android/vsock; eight-thread increase
alone NOT a performance fix. Next practical comparison x264 ultrafast/
zerolatency, same DXGI/I420/AVC/UVF1/MediaCodec. No new transport architecture.
Official x264 source commit0480cb05fa188d37ae87e8f4fd8f1aea3711f7ee compiled
WindowsARM64/NEON DLL688128B/SHA4460127AF53CE780043DC6A7CBBC2755F63284A6C9457206B330065C0CF8445D.
R&D adapter + independent OpenH26412-frame decode admission test strict ARM64
compilePASS; host test pending, not deployed/not runtimeFPS PASS. Runtime CPU
mask must be NEON-only, never auto SVE. GPL R&D code: not release-integrated.

Runtime continuation blocked by repeatedly verified tablet reserve (~48.95GiB
vs52GiB, >=3 consecutive goal turns); no VM live, all previous mutations
recovered. No safe expendable~3.05GiB file found without touching protected
user/production data or official ISO. Do not weaken gate. Requires freeing
~4GB or explicit expendable artifact direction. Full objective remains intact:
installed Desktop/1200p~60/input/audio/network/clipboard/lifecycle/common UI/
independent Windows+Ubuntu workspaces; NOT completed. Next real test codec
ARM64/activeFPS, then optional clipboard; no more substitute offline proofs.

Optional guest clipboard sidecar now compiled in same own vsock helper,
separate process4054, hostCID2, own worker job/socket/pipe cleanup;68 real
localhost relay tests PASS. Cold original helper exact, candidate prepared
30720B into81408B slot, writes0. Not guest copy/paste or provider PASS. Runner
flag and named encoder evidence parser PASS, defaultOFF; not installed.

Windows optional clipboard Android wiring built/signed (not installed),
shared GuestClipboard/UCIP with Windows label, defaultOFF4054 opt-in in own
sameUID/token checked Binder and supported app-owned AVF connectVsock.
52 actual port/readiness/opt-in/long-range policy tests PASS. Final APK
build-logs/windows-clipboard-20261012-apk-build2 SHA F4A0ED45...52CED8A4.
Guest4054 listener still missing; no device copy/paste PASS. Existing installed
APK/Ubuntu/display unaffected, VM list empty. Details in clipboard document.

Offline Windows clipboard progress: public CF_UNICODETEXT worker for SAME
UCIP/4054 compiledARM64;47 actual native tests and native->unchanged Android/
Linux protocol interop PASS. No deployment or clipboard runtime claim.
Optional listener/app-owner allowlist/client lifecycle next; production
clipboard/display/Ubuntu untouched. docs/WINDOWS_CLIPBOARD_2026-10-12.md.

Latest attempt terminal: owner denied VM startup due52GiB tablet reserve,
available~48.95GiB. No ARM64 codec/FPS runtime test occurred. Separate codec,
GOP, own encoder/socket ALL rolled back with full readback PASS; VM[]. Loader
strict compile/native admission/codec tests PASS. Identical storage gate is
now checked before staging; don't repeat storage-blocked boot. No deletions,
official ISO/production disks untouched. Actual codec stage/rollback112blocks
worked; host success is not guest performance evidence.

Eight-thread OpenH264 separate-file transaction now has465 offline negative/
partial-write recovery checks PASS and connected-device cold dry-run PASS.
112 exact isolated FAT block preimages plus GPT/boot/EFI guards unchanged;
device writes0. Working NTFS codec/DXGI/P7/Ubuntu unchanged. Next explicit
verified experimental codec loader + ARM64 selftest, then active performance
measurement; not yet runtime8-thread or sustained60FPS PASS. Details in
docs/WINDOWS_OPENH264_EIGHT_THREAD_2026-10-12.md. C currently~51.9GiB free;
the older storage free-space snapshot below is historical. No D/E writes.

## Storage safety — user update 2026-10-11

LATEST USER OVERRIDE: both D: and E: reported faulty. No new important files,
builds, staging or backups on either. Historical E rescue copies are not
trusted backups. New artifacts only C:, small/bounded; no full disk clones.
50GiB reserve remains requested (current C~18.5GiB, not met). User explicitly
deferred cleanup now; do not interrupt Windows work with broad disk scans.

D: is reported failing. NO new builds, staging or important backups on D.
Historical D paths are not healthy backup destinations. Old archive-to-D script
is disabled. Rescue only specifically important unique files to C/E with exact
SHA256/size verification; preserve D originals, no chkdsk/repair/delete.
GOOD-installed-720p30-20260930 README and runtime-source-GOOD.zip rescued to
E:\UAVF-rescued-backups with exact SHA256 PASS (small-rescue-manifest.json).
Prefix and APK reads stalled with unchanged I/O counters; stopped only this
task's identified rescue processes, preserving ALL D originals and unverified
partial E copies. Full backup rescue is NOT PASS. Do not retry large D reads
blindly. APK~31MiB partial and prefix128MiB preallocated are not valid backups.
The64GiB historical Windows transfer raw is NOT copied blindly: reserve/unique
data assessment required. E must retain50GiB; C currently below requested reserve.

## Latest Windows checkpoint — 2026-10-11

2026-10-12 latest codec ABI test PASS on native x64 sibling: actual8threads/
8slices,12 changing1920x1200 frames encoded/decoded with luma/stride checks.
ARM64 selftest compiled, not executed. Cold original codec readback exactly
800856B/F5976BFF...460F. Smallest final -O2 candidate843264B still does not fit;
no replacement/truncation/deployment. Stop compiler-size sweeps. Next separate
experimental file with guarded allocation/preimages/rollback then ARM64 test,
original DLL retained. Not8thread guest/60FPS PASS. No VM/platform/Ubuntu change.

2026-10-12 encoder feasibility: upstream OpenH2642.5.1 hard-clamps threads4;
private source constant8 candidate ARM64/NEON compiled using existing tools.
Static-runtime/six-export artifact76061725...95F6C4 is909824B, larger than
original800856B: NOT deployed, never truncate/in-place through encoder helper.
No runtime8threads/FPS PASS yet. Stable C-only DXGI bundle preserved. Next
codec runtime/thread/ABI test plus guarded staging, not CPU/platform changes.
See WINDOWS_OPENH264_EIGHT_THREAD_2026-10-12.md. No VM/APK/data mutation.

LATEST priority R&D quick audit: direct Surface/shared-buffer path has no
supported app integration endpoint on current stock device. Fresh APEX Java
hashes match September26 audited bytes; structural DEX check finds no Surface/
buffer export/vhost GPU socket or public custom-memory mapping setter. Exact
crosvm commit unknown (shell access denied); September22 upstream patch is
backend functionality, not a new app permission/API. Current Windows DXGI/
BasicDisplay is not virtio scanout; requires guest display driver plus host
endpoint, or a supported host framebuffer bridge. No implementation/start/
policy changes. Details DIRECT_SURFACE_FEASIBILITY_2026-10-11.md.
Timed DXGI test terminal ADB-runner-closed (not normal60FPS PASS); all owned
encoder/socket/EFI/FAT rollback readbacks PASS, VMs[]. Immersive Android bars
runtime check PASS, active sustained60FPS still unproven; no CPU changes.

LATEST direct DXGI capture A/B NORMAL terminal: native1920x1200 on Winlogon
then Default/Desktop, stream reconnect and matching Android decode/render
callbacks. User reports responsiveness improved. No sustained60FPS proof:
Default interval capture2.783/encode2.261FPS is sparse boot/static workload;
wait/map/conversion27.564ms combined, encode40.436ms. Androidpending0/1,
callback~7-9ms. Next separate capture waits/timing and active Desktop workload,
then bounded overlap if encode remains limiting; do not repeat CPU/GOP search.
Own encoder/socket/EFI/FAT rollbacks full-readback PASS; VMs[]. Best exact
candidate B79D7B...4375877 frozen on C in safe-checkpoints/
windows-dxgi-1920x1200-20261011 (about606KiB, SHA manifest, no VM disk/tokens).
Diagnostic immersive bars fix APK D5B0A757...D74740 built/verified/installed;
runtime hide/reveal check pending. Ubuntu APK/data untouched.

LATEST live metadata A/B terminal PASS: DXGI DuplicateOutput/AcquireNextFrame
both success; actual1920x1200 source, DesktopImageInSystemMemory=1. Direct
memory capture is locally available (NOT hardware GPU). OpenH264 effective4
threads despite requested8, LOW/slices8/fps60. Source still GDI16-17fps typical,
BitBlt32-36ms/convert4ms/encode21-24ms/pipe0.1ms; transientGDI304ms. Next ONLY
capture A/B via MapDesktopSurface with row pitch and frame/map release; common
H264 pipeline/encoder unchanged initially. Shared kind6 diagnostics now reach
Android live; no dependency on NTFS log recovery. All encoder/own socket/EFI
rollbacks PASS, VMs[]. Diagnostic APK E4D2656D... installed; Ubuntu untouched.
Evidence kd-windows-encode-live-diagnostics-20261011; latest display doc.

LATEST encoder/DXGI A/B NORMAL terminal, real Desktop1920x1200 on sharedSurface
verified,~12-14fps callbacks/pending0/1. Own encoder89B977... uses InitializeExt
low-complexity/min(CPUs,8) and bounded read-only DXGI feasibility probe; capture
stays GDI. Effective threads/DXGI HRESULTs/timing NOT recovered: cold own-log
reader found no persisted exact producer directories (not proof of failure).
Both own encoder and originalEFI/FAT rollback readback PASS; no VM retained.
Next coherent metrics retrieval, not blind repeats or CPU/GOP experiments.
Evidence kd-windows-encode-threads-dxgi-20261011 and latest display doc.

AUTHORITATIVE NEW MILESTONE: native1920x1200 real Windows lock-screen live
H264 -> app-owned vsock -> shared MediaCodec/Surface PASS in
kd-windows-ram-gop-handoff-audit-2-20261011. Actual source display mode1920x1200;
native startup8/barrier0;1544 matched receive/decode/render callbacks, pending0.
NOT60FPS: source15-18fps typical, BitBlt33-40ms +convert4.3ms +encode23-25ms;
pipe0.1ms, Androidcallback6-9ms. One transient~4fps interval. No active Desktop
motion performance proof. Original EFI/FAT rollback PASS, VMs[]. Successful
isolated EFI359DED6E...10752B preserved, APKA7D123DB... installed. Next native
capture/DXGI feasibility and encoder hot path; no more CPU/GOP boot search
unless new concrete failure. Details in latest WINDOWS_DISPLAY_CAP60 doc.

LATEST terminal devicepath A/B:7E60950F... candidate /19607F99... diagnosticAPK
deployed, native startup8/barrier0 PASS, driver progression but no display
connection by300s observer bound. NOT proof of guest hang; cold logs contained
only older producer evidence. Five-block EFI/FAT rollback PASS, VMs[]. Next
loader GOP query/selection + boot framebuffer handoff evidence; do not repeat
the expired test blindly. Resolution/FPS still not advanced beyond baseline.

AUTHORITATIVE latest RAM-GOP boot terminal: eight actual CPUs retained, EFI GOP
success but actual Windows ROOT\\BasicDisplay remains640x480; native1200p NOT
PASS. Android rejected dimensions correctly. Typed originalEFI/FAT rollback
PASS, no VM retained. All17-object BCD display audit found no explicit display
restrictions. New own display-handle/devicepath candidate7E60950F...E0E1F5F
offline build/tests and both control parity PASS; NOT deployed. Next exact
candidate admission + reversible preflight + one devicepath A/B. Details:
docs/WINDOWS_DISPLAY_CAP60_2026-10-11.md (latest section supersedes old text).

Latest display checkpoint: three bounded display runs terminal; all original
EFI rollbacks PASS, VMs[]. Eight CPU retained; encoder cap60 actually exercised
but source~13FPS/640x480 only. GDI66-71ms dominates encode5-8ms/pipe<0.2ms.
SRCCOPY-only change rejected/rolled back. Own native source diagnostic encoder
4161f240...a00b84 installed; no new APK or Windows driver installed.
Native1920x1200 RAM-GOP prototype is OFFLINE ONLY, ARM64 compiled, bounds/
refusal/rollback tests PASS. Candidate1643E97C...42C9A3 needs10240B, current
own EFI allocation8192B. Next reversible own FAT-file capacity plan and typed
APK admission required; do not bypass size/hash guards or alter signed code.

AUTHORITATIVE override: session53829 is TERMINAL PASS, not pending. Native
startup returned8/barrier0, own broker2896 samples0..6 processors8, actual
Desktop/Surface H264640x480~14-15FPS/cap15. Original EFI rollback PASS, VMs[].
Next: Windows cap60/native1920x1200. Neither native1200p nor sustained60FPS
is proven; keep exact successful SMP/IPI workaround and Ubuntu untouched.

Authoritative latest: PPM/scheduling/IPI-loop trials TERMINAL, originalEFI
rollback PASS after each. Minimal-observer A/B session89380 TERMINAL: native
starts1..7 PASS but no startup return/Surface by600s; original rollback PASS.
Current explicit-affinity capability A/B session53829 RUNNING
(kd-windows-explicit-ipi-ab-20261011), same0347... EFI/APK4A1D...,
300s bound, mandatory original rollback. Only exact native capability82b->80b
admitted after eight actual GIC mappings/affinities; no wait/count bypass.
BREAKTHROUGH in that run: native time IPI returned immediately after the
one-bit explicit-affinity adaptation; KeStartAllProcessors returned with
KeNumberProcessors8/barrier0. Normal later drivers loading. First all8 native
startup completion, not yet a user-mode eight-core Desktop/Surface PASS.
All seven native secondary starts succeeded;
KeNumberProcessors=8, KiBarrierWait=1. PpmInitHeteroEngine returned0 and
PpmUpdateArchitecturalEfficiencyClass completed. Desktop/all8 userspace remains
unproven. Earlier simultaneous adjacent probes were ineligible because KD
deletes breakpoints within its forward instruction window; sequential probes
and a32-byte spacing guard now pass offline tests. Scheduling configuration
returned. The next native target4cdb50 is xHalTscSynchronization, invoking
KeAdjustInterruptTime -> KeIpiGenericCall. Native post-send pending count8
remained8 at first loop iteration; debugger stops cost42-44s, so this alone
does not prove permanent IPI failure. Physical GIC reads failedC0000001
(unavailable, NOT zero). Minimal A/B removes all post-start PPM/IPI stops and
retains only distant startup return to exclude observer perturbation.

LATEST native Windows startup-wait evidence supersedes CPU3-only boundary:
CID2233 startupflag3=1, ownmarker3=1, native wait result4, next call4.
CID2235 all-wait observer: flags/markers1..4=1, result4; CPU5 flag/marker0
after10000 native iterations (~14.5s), result2. Stall returns normally;
no Surface by210s. Both terminal and exact original893... rollback PASS.
CID2234 third-only observation ineligible (only first call); live Surface,
own broker2632 samples0..7 processors1. No all8 runtime proof yet.

PoC-publication A/B session1718 TERMINAL: CID2236 all7 own publication
helpers executed, native Windows startup flags1..7=1 and each wait result4.
This is the first all-secondary native-start proof, NOT actual8-core Desktop.
No later stage/Surface by210s; original893... rollback PASS, VMs[].
Next same-candidate last-common boot-handshake/CPU-state observation completed:
session72063/CID2237, kd-windows-final-native-start-state-20261011. All7
starts confirmed again. Native loader boot-handshake field+88 is0; caller
passed b360b0. CPU1..6 PCs27b218 are KiFreezeTargetExecution (normal debugger
freeze, not diagnosed guest hangs); CPU7 returned zero context, UNAVAILABLE.
No later stage/Surface by210s; original893... rollback PASS. Current next
same-candidate post-start PPM/caller-return probe RUNNING session4412,
kd-windows-post-start-ppm-boundary-20261011. No CPU/PMU flags changed.
Do not repeat CPU_ON flag sweeps; localize the post-start Windows handshake.

Isolated PoC-publication A/B configuration:
kd-windows-native-poc-publication-20261011, candidate0347CB7B...8CC9007,
APK4A1D2488...9BC06DD. Own128B native DC CVAC/DSB/ISB after each literal
write; complete saved Windows CPU_ON context restored at own helper BRK.
Initial own pages also PoC-cleaned. This tests a concrete cache publication
gap, NOT a confirmed root cause. Exact-image/helper/context/admission tests
PASS; original/fixed8 EFI controls byte-identical. Finally stops owner and
rolls back original; do not start another VM until actual handle completes.
Previous APK6DB... saved exactly at E:\UAVF-rescued-backups\windows-smp-marker-baseline-20261011.apk.
No Ubuntu/P7/data/D/commit/push changes. C19.4GiB, E82.1GiB (before31MiB backup).

LATEST owned execution marker tested (not merely offline): APK6DB6052C...
9283253C, EFI597AC056...120EBE29/version2. Exact own template/literals guarded.
kd-windows-owned-ap-entry-marker: CPU_ON1/2/3 return0, markers1=1/2=1/3=0
immediately after call3. Immediate0 is UNKNOWN, not failure. Then no new
stage/Surface until210s; owner stopped. Deferred3+ trial fell back to Live
Surface/only first call, comparison against CPU3 ineligible. Clean all-pre-start
BSP-only trial kd-windows-owned-pre-start-only (CID2232): actual BSP31->20,
later call2 proves marker1=1; later call3 proves marker1=1/2=1. NO post-start
HVC or AP-PMU breakpoints. No call4/Surface by210s. This excludes those
post-start stops as the sole reason for current CPU3 boundary, NOT vendor
impossibility or proved CPU3 nonexecution. No repeat of same observation.
Original EFI893... restored/readback (smp-entry-marker-final-rollback), VMs[].
Read-only active BCD audit17objects: NO explicit CPU-count/bootCPU/group limits;
BCD SHA C1360923959FEDE56D680BCE73AC5BD98F1B70A6632F199430B1C12C78314CD6.
No BCD writes, Ubuntu/P7/D/commits/push changes. All8 runtime still unproven.

OFFLINE next AP-entry proof prepared, NOT deployed: smp-own-entry-marker-build
-20261011 EFI8192B SHA597AC0561E32A1210BC030B1B4A4BC432E2AAB1BBACE945862D25368120EBE29.
New explicit builder-PsciRuntimeEntryMarker requires all8 owned runtime pages.
Version2 header; each own64B trampoline stores1 at ownpage+48, DSB,
loads original Windows x0/entry from+32/+40 and branches. Only x0/x16
touched, same final ABI as old trampoline; no UART/Windows writes/BRK.
Safe wait at+56 until guarded literals updated. Independent ARM64 assembler
matches all64 C bytes, ADR/LDR bounds and final-register oracle PASS;
map/template tests PASS, original893/fixed8 365 byte parity PASS.
Device remains original893..., VMs[]. Marker candidate NOT in APK/apply
allowlist yet; next add separate exact-hash admission and observer version2
template/literal/marker guards before any runtime launch. No prior marker PASS.

NEW PMU allocation A/B actually exercised: kd-windows-common-pmu-confirm
-20261011 BSP N31 request31->20, actual allocated[20,0,0,0]; AP1 N31
passed native comparison, CPU_ON1 and2 returned0. No new stage after2;
210s observer bound terminal, no Surface PASS, scoped owner stopped.
Next read-only startup snapshot kd-windows-smp-start-contexts-20261011:
BSP naturallyN20; CPU_ON1,2,3 returned0, then silence. One bounded own
debugger break got NO response; CPU contexts NOT observed. Do NOT claim
silence is a Windows crash or that all8 cores work. VMs[] after finalization.
Original EFI893... restored/readback PASS (smp-common-pmu-confirm-rollback
-20261011). Current strongest next local probe: owned AP-entry memory marker,
not another PMU/CPU/UART flag sweep or repeat of unresponsive late KD break.
Goal remains active; no user files/Ubuntu/P7/D/commits/push changed.

LATEST PMU mismatch PROVEN at actual stopped CPU (routing fix):
kd-windows-runtime-pmu-cpu-routing-20261011 BSP MIDR410fd821/PMCRf8c1/N31,
allocated[31,0,0,0]; CPU1 MIDR410fd811/PMCRa0c1/N20 compares20<31,
then5C(9000,1,14,0). KD formerly requested CPU0 regardless of stop; request/
extra/context now use stopped-state Processor, offline routing/recovery tests
PASS, actual AP context/PC PASS. Do NOT use older frozen-BSP context as AP.
One allocation-argument31->20 A/B prepared but latest BSP naturallyN20;
no mutation applied. CID2227 retained live640x480~13FPS until210s, Surface
PASS; actual own broker2628 snapshots seq0..3 still processors1. NO all8
SMP PASS. AP PMU/CPU2 milestones not reached in this natural20 run. No blind
claim that common-PMU fix passed: required31->20 mutation not yet exercised.
Current run TERMINAL, script stopped owner, VMs[]. Own EFI reverted exact893...
(smp-common20-final-rollback-20261011). Next exact common-counter allocation
test on observed BSP31, then actual secondary execution/online boundary if
still processors1. No new image/Ubuntu/data/D/commit/push changes.

LATEST all-secondary trial TERMINAL: CPU1 HVC0, then new HAL5C(9000,1,14,0)
before CPU2, not previous CPU_ON A0/-2. Exact kernel49fd34..49fe24 reads
MIDR/PMCR.N and compares perCPU hardware count with allocated counter-set
total; this failure carries CPU1/count20. Heterogeneous PMU counts now strong
next hypothesis, NOT yet BSP/AP readback proof. No blind feature masks or
fatal-handler bypass. Need narrow read-only metadata at this exact branch.
Original EFI893... restored/readback PASS (smp-psci-runtime-all-final-rollback
-20261011), actual VMs[]. APK8C76A217...F9D5FB10A. InstalledWindows preserved,
Ubuntu/D unchanged. All8 actual CPU/Desktop NOT PASS; goal remains active.

BREAKTHROUGH: kd-windows-runtime-context0-ab-20261011 first CPU1 CPU_ON HVC
returned0 with owned runtime trampoline and context0; original call returned-2.
Exact original Windows entry/x0 restored through verified own16B literals.
Later unmodified CPU_ON still caused A0/-2; NOT all8 CPU/Desktop proof yet.
Runtime mapping/header/template readback proven at loader and kernel in
kd-windows-runtime-map-kd-observer-fix-20261011. CallbackUART caused diagnostic
AV earlier; replaced with OWN68B BRK checkpoint, no runtime MMIO. Observer
pre-kernel restore_context base=None guard corrected. First runtime EFI20A...
C8F4AF4 rolled back893... PASS before bounded all-secondary adaptation trial.
All-CPU candidate7F33A06F...363EA9E,8 separate owned runtime pages; no shared
trampoline literal race. Each affinity1..7 adapted once, actual header/code/
literals guarded, exact return observed. Trial kd-windows-runtime-all-context0
-20261011 pending; do NOT claim actual Windows processors8 or live frame yet.

OFFLINE runtime-map control prepared, NOT runtime PASS: own EFI8192B SHA
2C64EB289A63E7A193CBA17CB709ADECF39C7F7C85EB816E82F9E546DC4F9EFC.
Original893... and fixed8 365... rebuild byte parity PASS; allocation/map
selftests and copied208B callback PIC/no-relocations/branch-bounds audit PASS.
RuntimeServicesCode2pages, ConvertPointer event copied into owned runtime
memory; observer requires actual serial VA and exact KD virtual header/template
readback. No guessed aliases, no Windows code/page writes, no CPU_ON redirect
in this control. Separate candidate hash/flags exclude old NVS/static A/B.
Existing EFI file size/chain unchanged; only redundant successful diagnostic
dumps compacted in this candidate, actual guards retained. No deployment
or SMP success implied by these offline checks.

LATEST CID2216/2217 EFI-owned NVS allocation/map proof PASS (FFFD0000,8pages),
kernel reached; kernel KD physical NVS reads C0000001, NOT header-runtime PASS.
CID2218 static CPU1-only entry A/B: originalentry105cd3010 -> alignedowned
FFFD1000, hypervisorcontext=originalentry; static code restores Windows x0
8000c000. Direct HVC return still-2. Context0 NOT tested; no SMP/frame PASS.
See docs/WINDOWS_SMP_PSCI_BOUNDARY_2026-10-11.md. First trampoline SHA36943A...
11620A, APK492289F4...D714E8B5. Original893... restored/readback PASS, VMs[].
Next offline audit owned EFI runtime mapping/ConvertPointer to safely write
original entry/context literals for ONE context0 A/B. No guessed Windows
padding/native MM calls/random flags; no identical baseline reboot.
No production Ubuntu/D writes or commits. Goal active, not vendor-only blocked.

NEXT owned PSCI reservation proof dispatched (NO CPU_ON redirection):
kd-windows-owned-psci-reserve-proof-20261011. New isolated EFI8192B SHA3B1805F0
...231B9ABD, fixed8 GIC + AllocatePages NVS8pages below4GiB, bounded map and
executable-attribute checks; no guessed Windows padding. Both original893...
and fixed8 365... controls reproduce byte-identically; map/template selftests
PASS. Compact diagnostics only for new reserve variant, no gate removal.
Diagnostic APK C27DDA96...D48D1D0. Candidate/apply/runtime result pending;
do not claim reservation/SMP runtime PASS before physical header readback.

LATEST CID2215 baseline recovery TERMINAL: pre-kernel KD RESEND after first
winload notification, no complete response by210s. OriginalEFI cold rollback
remains SHA/readback PASS, but recovered Desktop/frame NOT PASS. Scoped owner
stopped automatically, actualVMs[]. Do NOT report Windows currently running.
No identical reboot planned without fresh diagnostic reason. Tablet free56GiB,
C20.1GiB (requested50 unmet), E82.1GiB; D excluded. Goal remains active.

CID2209-2214 eightCPU path reaches kernel but CPU_ON64 returns -2:
exact typed affinity1/entry/context. Lowering RAM to2GiB did NOT help; exact
reserved x7->0 one-call A/B did NOT help either (direct HVC return -2).
Entry prefix contains live metadata; do NOT overwrite/round blindly.
See docs/WINDOWS_SMP_PSCI_BOUNDARY_2026-10-11.md for evidence and next owned
reserved-trampoline proof. No generic vendor/SMP-impossible claim.
OriginalEFI893... rolled back/readback PASS; normal oneCPU4GiB control
kd-windows-original-restored-after-psci-20261011 terminal as described above.
No D usage, no production changes, no commits. C reserve still below50GiB.

CURRENT narrow eight-CPU trial TERMINAL, new CPU_ON boundary proven: evidence
kd-windows-legacy-eight-native-metadata-20261011. Exact original8192B EFI with
ONLY4 bytes/two MOVK address constants changed:3ffd->3fef, SHA365B7332...B1A865.
No code-flow/section/logging/Windows signed-image changes. Explicit exact-hash
APK gate requires isolated installed disk + MATCH_HOST + host availableCPUs=8;
oneCPU/fixed8, mixed dynamic/RTC and oneCPU observer modes rejected offline.
CID2209 reached kernel, then A0(BEEF,8003,C4000003,-2): exact CPU_ON64
fatal handler, no frame/actual Windows processor_count8 PASS. VMs[].
CID2210 observer scope error (generic wrapper first hit PSCI_VERSION), no
Windows fix claim; corrected probe to CPU_ON-only dispatch RVA4aaf1c.
CID2211 exact operands: affinity1, entryPA1048d3010 (>4GiB), context8000c000,
real HVC handler49a870, then same -2 fatal. No argument/register mutation.
Next ONE 2GiB/eightCPU A/B with typed operands (low-address hypothesis only),
kd-windows-eight-psci-low-address-ab-20261011. Diagnostic APK6709B509...125A992;
4GiB/default oneCPU untouched. No production APK/Ubuntu/D changes.
Generic dynamic selector CID2208 TERMINAL: no kernel/Surface by210s despite
early valid GIC state; isolated owner stopped, VMs[]. No causal GIC fault proof.
Dynamic EFI rolled back to original893... with exact readback PASS before
fixed8 apply; backups retained. Eight CPU runtime PASS is NOT yet established.

HISTORICAL dynamicGIC ONE_CPU/native-metadata control: evidence
kd-windows-dynamic-gic-native-metadata-onecpu-20261011. EFI8192B nowE550...1FDC47,
exact cold/readback/GPT/FAT chain preservation PASS; rollback original893... is
smp-gic-with-cpu-metadata-applied-20261011/original-BOOTAA64.EFI. NO MATCH_HOST yet.
CID2207 originalEFI/helper metadata control TERMINAL after retained-frame PASS;
scoped owner23381 stopped only for cold audit/EFI change. Windows API actually
reports processors=1 in7 fresh samples broker2548. Log534854B SHAe160ce6c...db24c,
next boot naturally rotates own bounded log. CPU metadata helper731ed372...f0b01
remains applied. Do not infer multiCPU PASS from launch args alone.

CID2206 normal test-bound retention PASS; VM stayed app-owned after host KD EOF.
Temporary0FPS on static/idle login scene was NOT a hang: Enter visibly opened
password screen and restored fresh~13FPS after observer ended. Own serial equals
host RX88764B, hostTX=decoded=UART-written50891B/SHAae756abe...cd4b42; no new
pending KD packet after detach. Windows keyboard input usable independent of PC
observer. Scoped diagnostic owner then stopped for cold helper update, VMs[].
Native metadata helper prepared/offline tested/applied ONLY own71168B slot,
newSHA731ed3720e642e74ee1f50b5481e4bbe574b4de5967a325dc8fa00b36a0f0b01.
Adds GetActiveProcessorCount(ALL_PROCESSOR_GROUPS) to bounded CPU log; no feature,
priority, display or scheduling changes. Cold binary/readback PASS, no hives.
Next ONE originalEFI/ONE_CPU control of metadata, then dynamicGIC sameCPU,
then MATCH_HOST only after sameCPU baseline passes. No D usage.

CID2206/owner18227/session65553 TERMINAL after retention/input PASS: originalEFI/ONE_CPU, no calendar
correction. Exact boot-clock-delta observation PASS: initial134361842763490000
(2026), saved18446744072233833192,current18446744073463950269,
frequency222389687, delta1230117077 (~5.53s), predicted134361842818803584.
No backward/centuries delta in this boot. Do NOT claim invariant-counter fix or
blindly clamp kernel arithmetic: bad offset creation still not captured.
Historical reserve watcher used18227; do not reuse that PID for new runs.

CID2205 calendar recurrence A/B TERMINAL: original invalid calendar
251780588806450000 replaced only in RAM with134361842662958273. Live lockscreen
nowOct11, stream~13FPS640x480/cap15, final~2094 frames/queue0/~7.35ms callback.
Fresh ownCPU broker2560 settles30.448% then11.35%, LSASS0% in both. Cold bootstat
CRC-valid2026-10-11T09:24:36.349297Z, SHA3912963a...0b48; ownCPU log349336B
SHA6b4cd9db...72fb8. Proof of temporary calendar mitigation, NOT permanent fix.
ActualVMs[]. Observer used older loaded retention guard and ended300s as error;
offline fix now allows completed clock probe and at most2 in-flight frames.
Next ONE existing boot-clock-delta observation from this fresh2026 boot-status
state, without calendar correction/CPU/EFI changes, to catch first bad offset.

CID2204 loader-time observation PASS: EFI2026-08-24, Bl/EFI converted dates
plausible, but CRC-valid persisted2212 boot-status timestamp wins exactly.
This proves persistence source, NOT first creation of the future value.
Details docs/WINDOWS_CLOCK_SOURCE_2026-10-11.md. CID2204/session35009 TERMINAL
without Surface by300s, exact source observation remains PASS. Initial two
observer attempts ended after host guard errors; their
probes/disk changes are not Windows regressions, actualVMs[] checked each time.

CID2201 original-EFI control reached Desktop after user authentication, input
Ctrl+Shift+Esc/Alt+F4 visibly worked, live H264640x480/cap15~13FPS/queue0.
The new dump-unload RESET guard did NOT trigger, so no causal recovery PASS.
Observer's300s end wrongly treated as failure and force-stopped this usable VM;
actualVMs[]. Added diagnostic-only normal-bound retention gate: same owner and
2s fresh receive/decode/render callback progress with queue0; offline tests PASS.
No physical FPS assertion. No Ubuntu or production APK changes.
Cold bootstat now CRC-valid2212-11-02 (193076811012898812), NOT merely22days
ahead. Own CPU broker2588 LSASS0..1.134%, late totalCPU95..100%; calendar remains
unfixed. Next ONE exact winload final-time-selection observation separates
BlGetTime/EFI/boot-status inputs before selecting a permanent clock fix.
Typed-only probe prepared/offline tested, no counter/calendar/userland mutation.

Reverse control CID2200/actualowner7259/session11502 TERMINAL: original EFI,
same updated APK also stalled after dump_viostor.sys unload + CONTINUE. Two
target RESEND packets retained; no frame, actualVMs[]. This does NOT implicate
dynamic GIC. Added one bounded transport RESET only at these exact dump-driver
unload/CONTINUE/2xRESEND states; no command replay under a new ID, fresh stopped
state required. Offline positive/negative guards PASS; next single runtime
control uses original EFI/ONE_CPU, no clock/CPU changes.
Rescue jobs18051/35662 canceled after proven stalled reads; original D files
are untouched. See storage policy above for exact verified/partial status.

SMP selector prepared and offline tested; oneCPU trial now TERMINAL. GIC_DYNAMIC in isolated
EFI discovers boot redistributor via MADT+MPIDR/GICR affinity, validates bounded
topology and checks all CPUs' active PPI before DS.15 host tests + exact legacy
EFI rebuild PASS. New optional EFI8192B SHA E550FD640FC3864305A6F6F0A794B582A5DB43D7F0556FA69FFD11BE5C1FDC47
(smp-dynamic-gic-ready-20261011); no storage-layout changes. Exact guarded APK
and cold candidate support implemented; admission tests PASS. Diagnostic APK
is A853AB5353A42DE6B6C963D2AEF01609DB842030D34B75F3892CC37C3A5EAF29,
defaults remain oneCPU/original EFI. CID2199 trial proved selectedbank3ffd0000,
MPIDR80000000/count1/DSreadback50, reached kernel+RAM adapters, then KD timeout.
Effective raw config equals CID2198. No frame or freshCPU (same fc1ffc log hash).
Original EFI893B... restored by cold exact8192B readback; all VMs stopped.
Do not call diagnostic timeout a GIC failure or claim SMP runtime PASS.

CID2198 now COMPLETED: Desktop + keyboard Task Manager + guest-native shutdown
PASS. H264 stream~13..15FPS/cap15 at640x480, queue0, callback~7..8ms; reconnect
after login observed. User completed authentication. No new RAM calendar
correction. Fresh own CPU log has8samples/7x~15s, LSASS0..1.036% (old busy loop
absent), settled UI CPU28..30%. Evidence persisted-calendar-cpu-cold-20261011
and kd-windows-persisted-calendar-control-20261011 screenshots/reports.
Guest shutdown -> actualVMs[]; observer1612 deliberately terminated afterward,
wrapper exit1 is expected host cleanup, NOT Windows regression. All jobs terminal.
Next boot-CPU GICR audit before SMP, then1200p60/full input/audio/network/UI.
Do not declare complete Linux parity or permanent clock-source fix yet.

All diagnostic VMs stopped. CID2197 control timed out in KD before calendar
correction/display; no new runtime PASS. Across boots calendar2247/3316/3503
is real but not proof of random counter offset. Measured QPC/calendar rate and
CID2194 boot delta are normal; initial loader calendar is already invalid.
Exact winload audit identifies later-date selection from CRC-valid boot-status
field7 (OslProcessBootStatusData -> OslSetSystemTimeParameters), plus root
ControlSetOverride key LastWriteTime fallback (missing in recovered SYSTEM).
Cold bootstat current calendar2026/CRC PASS is AFTER A/B,
not proof of old source. No bootstat/hive reset. Evidence:
boot-time-state-cold-20261011; parser inspect-boot-time-record.py.
Next identify first bad loader candidate; no blind timer/CPU flag experiment.
Own CPU-log rollover helper installed helper-only/cold/readback PASS, no hive
writes. CID2198 control COMPLETED (owner8821, session89377): no calendar
correction or late KD stops; check persisted normal time + fresh CPU + Surface.
Clock A/B restored capped15FPS stream,
not Desktop/LSASS CPU/SMP proof. Production Ubuntu unchanged, no commit/push.

## Installed Windows Desktop milestone — 2026-10-09

LatestCID2195 clock correction runtime PASS: raw2026, stream~13..15FPS/cap15,
queue0, first Surface "Please wait", not Desktop. Late debugger inventory/resume
failed; wrapper stopped VM. Current ownCPUlog stale/capped, no LSASS fix proof.
Control observer60024 calendar-light-control-20261011 has NO late debugger stop;
next verify actual Desktop/input/CPU. Loader calendar correction remains isolated
temporary RAM A/B only, not production fix. Ubuntu/storage/EFI unchanged.
That control is now TERMINAL: CID2196 failed in boot adapter's third ambiguous
write BEFORE calendar probe, wrapper stopped it, actualVMs[]. Earlier two writes
were proven not applied/read back then repaired once. Do not widen recovery
bounds or interpret this as a clock regression. Need reliable control to prove
Desktop/LSASS CPU; existing clock A/B2026 +13..15FPS remains valid partial evidence.

CID2194 exact boot-delta PASS: initial calendar3503 already wrong, current
counter greater than saved, ~1.2s delta; no backwards unsigned wrap at this site.
Stopped after evidence. CurrentCID2195/owner509/observer79753 temporary CLOCK A/B
changes only invalid loader calendar+a48 (8B RAM) to host UTC before original
LDR; counters/CPU/timers/RTC/files unchanged by helper. Unit PASS, runtime result
pending. Need actual normal date + LSASS CPU + Surface proof before any fix claim.

CID2193 first HAL writer observation PASS: explicit calendar3503-02-02 via
HalSetRealTimeClock caller47c974. Bad date predates HAL extrapolation. Guest
resumed; diagnostic stopped after result, no file/time mutation from observer.
Current CID2194/owner31370/observer41644 checks exact initializerb17f78 operands:
loader calendar/counter and current counter/frequency; unsigned-delta hypothesis
not proven. Single probe, typed40B reads, unit PASS; no late LSASS inventory.

LatestCID2192 HAL metadata runtime PASS: baselineCalendar541490910014448004
already3316 before QPC extrapolation; referenceQPC121350015 coherent, mode0,
errorFlags0. Raw541490911136777079; uptime5.45s (debugger boot stops, not120s
guest execution). After metadata, no LSASS yet; debugger resume failed, wrapper
stopped VM, actualVMs[]. No CPU/display fix PASS. Next exact HAL baseline writer /
loader time handoff, not another flag sweep. C14.6GiB free (requirement unmet),
D57.7/E82.3/tablet56GiB; no unknown deletion or new large image clones.
Raw-minus-HAL date112.2329075s matches correspondingQPC delta112.232907077s:
extrapolation correct, base date wrong. Exact HAL writer48a8c0 can seed from
explicit calendar argument OR existing system time; baseline is not independent
RTC proof. Next first writer call/caller + typed time argument, not LSASS snapshot.

2026-10-11 clock history verified: initial installed logs record2247-02-24;
CID2191 raw kernel clock records3316-12-01. Cross-boot variation is proven,
randomness is not (persisted/accumulated boot bias still possible). Existing
slow-run19 samples span Tick149.062s / QPC149.057641615s / calendar149.0575501s,
with no backwards QPC. Thus no sustained gross acceleration in that interval;
focus on initial calendar/bias source, not arbitrary counter flag changes.
Cold isolated SYSTEM is dirty607/606; LOG2 memory-only replay reaches608/608.
Named SecureTimeLimits/LastKnownGoodTime remain2015, ShutdownTime2247 and
timezoneUTC+3: none explain3316. No registry/device write, actualVMs[].
Next exact Windows time initialization/EFI conversion, not blind W32Time reset.
SFAPM overflow for3316 wraps POSITIVE, unlike3053: busy-loop cause still unproven.

Current CID2191/owner23307/observer73449: actual PUBLIC clock sample proves
ordinary kernel date3316-12-01 (FILETIME541490911030240127), coherent even
seqlock1491118; uptime115.66s /13MHz QPC and time increment plausible. So bad
calendar is NOT only a precise-time projection artifact. UEFI2026 already proven;
original EFI restored. Hot LSASS persists; inspection resumed same guest.
CID2191 deliberately stopped after decisive sample; actualVMs[]. Next cold
read-only discriminator: Windows clock initialization/persisted time values;
Secure Time Seeding is a documented hypothesis, not proven here. No blind W32Time
reset, auth/provider disable, whole-hive rollback, timer/CPU flag sweep. Goal open.

Latest RTC A/B CID2190: EFI calendar already2026, no SetTime needed/performed;
LSASS hot thread persists, no CPU fix or frame PASS. This rejects invalid initial
EFI calendar as demonstrated cause. Exact original EFI restored/readback verified;
VMs[]. Isolated diagnostic APKCA52AC10...79A9 remains installed, default profile
still requires original EFI. Ubuntu and Windows user data unchanged. Next FIRST
late observation reads public raw clock/QPC metadata before inventory to separate
raw Windows date initialization from precise-time projection; unit PASS only.

Latest2026-10-11: no VM running. CID2188 reached LSASS code inspection but
debugger transfer failed before typed wait return; no fresh timeout/date proof.
Next CLOCK A/B prepared offline: rtc-cam-guarded-build-20261011, candidate
AAD53298...46F5D8192B. Control rebuild matches exact working EFI893B7859...02E8B;
frozen CAM/GIC/loader preserved. Only standard EFI RTC correction/readback added.
Original and mocked RTC tests PASS. NOT deployed, Windows disk/APK/Ubuntu intact.
Next scoped same-file EFI backup/write/readback + diagnostic-only exact hash
acceptance, then actual date/LSASS CPU/desktop A/B. No CPU or SMP PASS yet.

CID2186 retained wire proves exact hot LSASS736/TID784 kernel wait returned
STATUS_TIMEOUT102. Observer had rejected actual Windows EL1t (PSTATE82001144),
not a guest failure. EL1t/EL1h guard corrected; no EL0/EL2 relaxation. VM stopped.
Current CID2187/owner9898/observer7061 gathers actual typed timeout + date.
SFAPM signed-nanosecond overflow at previous year3053 verified statically;
clock/CPU cause still needs current data plus clock A/B. No userland/data changes.

Latest: CID2185 staged context/resume runtime PASS twice; guest CPU fault NOT
fixed. Static official SFAPM code converts _Xtime_get_ticks100ns since1970
to signed64 nanoseconds before _Cnd_timedwait. Observed year3053 overflows to
negative deadline: strong clock/CPU-spin hypothesis, not yet runtime proven.
Current CID2186/owner7912/observer3882 tests ONE exact kernel wait return with
typed status/timeout/system time, not another CPU/platform flag experiment.
No authentication/userland/data mutation; Ubuntu untouched; SMP still pending.

Current CID2185/owner1592/observer61481: kernel/unchanged boot adaptations PASS,
awaiting existing bounded late inspection. Two distinct-range readback recovery
allowed only after exact original/completed-byte proof (unit guards PASS).
PC-only staged context restore remains runtime-unproven. Prepared one exact
kernel wait-return observation if the current saved context cannot reveal status;
not enabled in this running process. No CPU/storage/Ubuntu changes.

Latest2026-10-10: isolated Windows VM stopped, actualVMs[]. CID2183 same-VM
snapshots locate the hot LSASS thread in SFAPM / MSVC performance-counter /
condition-variable code; original instruction windows match, but saved contexts
are NOT continuous hot-PC samples. Wait/deadline root cause remains unproven.
Late full-context debugger restore failed and left the guest halted; explicitly
stopped it. Prepared PC-only semantic update in staged32-byte SetContextEx
packets/full unchanged-register readback and reject retention
after observer failure; offline tests PASS, runtime resume proof pending.
Typed KernelBase result reader corrected to converted Win32 error, not NTSTATUS.
No signed-userland/security/storage/Ubuntu changes. SMP is mandatory after the
CPU root fix; one-vCPU launch remains diagnostic, not production performance.

ControlledCID2184 ended before late inspection: a second missing PMCC adapter
write exceeded the once-per-boot readback recovery bound. Kernel entry reached,
but no LSASS/SetContextEx/SMP result. Wrapper stopped isolatedVM, actualVMs[].
Exact kernel handler audit corrected SetContextEx input total size/last-chunk
commit. No unchanged reboot; transport prerequisite needs an evidence-backed
resolution first. No CPU/config/disk mutation or runtime performance PASS.

2026-10-10 20:51: CID2182 late inventory proved the SAME LSASS condition-variable
wait/result loop in another phase: kernelbase!BaseSetLastNTError+28 returning
to SleepConditionVariableSRW+3c. Original official signed code bytes match.
High user ticks remain; typed timeout/Win32 error not yet observed. Prepared exact
result-site reader and at most2 bounded same-VM request snapshots after initial
observation, so future phase changes do not force more cold boots. Offline
guards PASS. Next isolated launch keeps disk/config/boot fixes unchanged;
no CPU workaround or performance PASS yet. SMP remains mandatory after root fix.

2026-10-10 20:34: CID2182/owner19403/observer16923 now live. Earlier timeout
observation boots failed in KD transport before LSASS, not in Windows waits.
Wire evidence proved missing own-padding writes were NOT applied (original
zeros). One guarded RESET/readback + fresh write AFTER original-byte proof
now passed runtime, all existing boot adapters verified, same guest resumed.
Keep this VM; one600sec hot-wait observation is scheduled after runtime start.
Read only exact condition-variable timeout/kernel-time metadata, no general
LSASS stack/register dump, signed-userland or security changes. CPU/root fix
and SMP/performance acceptance remain incomplete. No Ubuntu/data changes.

2026-10-10 20:05: current CID2178 installed lock screen is visible. One late
read-only LSASS code inventory succeeded and resumed the same guest. Native
hot thread PID744/TID784 has saved PC ntdll!ZwWaitForAlertByThreadId+4 and LR
RtlSleepConditionVariableSRW+0x1e8; same-boot module identity and original
instruction bytes verified. Saved syscall context is NOT a sampled hot PC.
Next narrow lead: wait/timeout correctness, not another codec or CPU flag sweep.
WPR metadata-only trace had zero sampled PCs. No signed userland/security/data
mutation. Low FPS remains unresolved. Fix CPU root cause, then enable/prove SMP;
single-vCPU Windows is diagnostic only and is not the production target.

2026-10-10 16:40: CID2176 debugger transport recovery restored kernel entry
in the SAME VM after silent winload unload; bounded read-only RPC recovery also
passed RAM guards/adapters. Installed service/frame/profile still not observed.
One explicit diagnostic break after~8min without a listener returned the normal
clock tick path (KdCheckForDebugBreak <- KeAccumulateTicks <- KiUpdateRunTime /
KiUpdateTime), not a hot LSASS PC. Isolated VM stopped, Windows data preserved.
Do not claim WPR or throughput PASS. Next minimal observation is fresh process
milestone inventory using now-tested read recovery, not another flag/codec sweep.
One vCPU remains diagnostic ONLY: fix the demonstrated scheduling/CPU blocker,
then enable/prove SMP before accepting production Windows performance.
No Ubuntu changes, commit or push.

2026-10-10 15:50: severe lowFPS localized to LSASS consuming about79–114%
of one CPU in two independent native observation boots (non-atomic sampling
can exceed100%). Encoder CPU negligible; no encoder/transport rewrite.
Single late KD instruction read failed in RESEND/ACK exchange and did not
provide a usable hot PC; no repeat of that failed late-break experiment.
Current CID2173 owner30342 observer exec34135 keeps normal uninterrupted boot.
Own broker now includes one bounded20sec native WPR CPU instruction recording:
4MiB circular memory, code/process/module events only, own named instance,
no LSASS dump, signed-image/security mutation or production changes.
Strict build/embedded XML/WPR parser/selftests/cold-slot readback PASS;
guest recording/ETL analysis still pending. Restore inputs preserved.
Host ETL analyzer uses official Microsoft TraceEvent and emits only CPU/code
addresses/basenames; private trace stays local. Full1200p60 parity NOT achieved.

2026-10-10 15:05: same new control CID2171/exec26283 verified live and boot-file
requests progress; no fresh native CPU/source evidence yet. No restart. Added
tested offline CPU-log analyzer; never infer host latency or interrupt storm
solely from guest counters. Main lowFPS cause still unproven.

2026-10-10 15:00: zero-frame stall had an additional proven observer bug:
first-chance user breakpoint was left stopped when observer exited. Corrected
narrow dispatch (gn/DBG_EXCEPTION_NOT_HANDLED), no signed-code/PC writes;6guard
cases PASS. Prior lowFPS/boot-wide slowdown still unresolved. CID2170 UI-stopped;
one fresh control CID2171 owner12070 exec26283 now running, own broker adds bounded
CPU snapshots, cold backup/full readback PASS. Same Windows platform, Ubuntu
untouched. See milestone checklist for exact artifacts and next evidence.

2026-10-10 14:53: fresh Winlogon frame/Surface PASS on CID2170; password accepted
after switching RUS->ENG. Welcome and Default producer2156/listener/clock ACK8
confirmed. Source split: conversion~0.4ms, pipe writes<0.1ms, encode wall~1sec,
but tiny process CPU total (~109ms over52guest-sec). This narrows performance
work to scheduler/waits/background load rather than bandwidth/pixel conversion;
guest clock correctness still unproven. Default stream metrics pending, no new
reboot/platform changes. Full1200p60 and Linux parity remain incomplete.

2026-10-10: battery100%, reauthorized. Own encoder diagnostic-only cold update
and full readback PASS; original backed up, no hives/platform/protocol changes.
CID2170 owner32099 running unchanged Windows platform, observer exec13429.
Next evidence required: actual source stage, GDI vs conversion vs encoder time,
CPU-time and clock correlation. Do not treat old persisted broker logs as fresh.
Details: docs/WINDOWS_MILESTONE_CHECKLIST.md; no current performance PASS claim.
14:49: same observer/VM verified alive, new Windows driver events continue.
SystemEventsBroker timeout51703guest-ms repeated before fresh encoder startup;
Android received0frames so far. This is boot-wide slowdown evidence, not proof
of encoder/display failure. No additional restart or platform change performed.

22:39 final: installed Explorer desktop eventually appeared22:34, Task Manager
window22:36 (content still unpainted). Black source was temporary, but severe
startup/runtime slowdown remains unresolved; observer-detach causality unproven.
Battery4% reached user stop limit. Diagnostic UI stop confirmed VMs[]; this is
app-side stop, not clean guest shutdown proof. Installed data/Ubuntu preserved.
Next charged-device test: actual process/source/clock observation. No new driver
or prepared source-inventory encoder installed. See follow-up document below.

22:30: Run retirement and native login PASS on CID2168, but this cold-boot
Default desktop capture is black/slow. Do not claim current Desktop recovery.
Viewer reconnect/observer detach tested without reboot/config change; source
cost is high, Android queue0. Native source inventory encoder compiled, NOT
staged. Detailed next step: docs/WINDOWS_DISPLAY_FOLLOWUP_20261009.md.
Installed root/account, Ubuntu/platform and prior good Desktop proof preserved.

22:00: new broker-only cold update reached Windows login on CID2168/owner5997.
Fresh native service log confirms exact legacy UAVFDisplay Run retirement
(value=0), agent and Winlogon producer; app-owned H.264 frames returned.
Previous native shutdown reached VM exit and SCM stop, without force-stop.
Broker guards/selftests/readback PASS; no hive/layout/kernel/Ubuntu change.
Current login regression pending. 640x480 source-mode limitation remains;
virtual-monitor/IDD is only a candidate, not a proven 1200p solution.

Latest21:38: app-owned CID2167/owner28113 verified another real login/Desktop,
with corrected byte-credit UART ingress and known-good minimal observer. Shared
H.264/MediaCodec stays live~12–13FPS640x480 across login and desktop interaction.
Win+R/Alt+Tab/Unicode/Enter/touch focus/PageDown observed. Settings resolution
selector is disabled at640x480: investigate guest/GOP modes before1200p changes.
Legacy own display autostart CMD visibly repeats Access denied against SCM-owned
ProgramData diagnostics; source fallback fix prepared, NOT staged in guest yet.
Native KD branch frozen: unsupported boot ReadControlSpace loops; not vendor
Vulkan/CPU regression. Full Windows runtime parity remains unfinished.

21:17: native-KD control series stopped after another pre-adapter exchange stall.
No new Desktop/1200p proof this turn. VMs[] verified after explicit diagnostic
stop; installed Windows/user and production Ubuntu preserved. Native v4 startup
fixes offline-tested, not kernel-runtime-tested. Further blind repeats prohibited;
next gap is guest UART acceptance, not launch arguments or smss patches.

21:12 follow-up: native Microsoft KD establishes Winload ARM64 session over the
same app-owned UART after host duplex-pipe correction and bounded ACK recovery.
Plain native KD also reproduced RESEND before kernel, so failures are not solely
the Python protocol implementation. TX bytes match Android-decoded ingress;
guest UART acceptance remains unproven. Native RAM-script module-base correction
prepared; native kernel adapters/display not yet PASS. See current checklist.

Repeat installed boot now proved native lock/login screen -> accepted user
password -> Explorer desktop on the same app-owned CID2156, with proper SCM
display broker (no OOBE rearm/login bypass). Screenshots and wire evidence:
kd-windows-prelogin-service-ack-recovery-20261009-runtime. Transport remains
shared UVF1/H.264/MediaCodec, diagnostic port4055,640x480~13FPS pre-login;
desktop producer reconnect/stability still being investigated. Host KD response
ACK replay passed the previous early adaptation failure; a single run does not
prove reproducibility. VM is now terminal; current broker logs being extracted.
Do not roll back this native user/root to old whole-disk or hive snapshots.

Installed Windows ARM64 completed native specialize and user-completed OOBE.
Actual Explorer desktop verified on tablet in isolated app-owned CID2149,
owner32530; H.264/vsock/MediaCodec stream reconnected across the transition
without restarting the VM. Current evidence: first-installed-desktop.png and
desktop-encoded-display-report.txt under
build-logs/windows-post-linux-reassessment-20261004/kd-native-offline-oobe-20261009-runtime.
This supersedes the incomplete-install/transport statements below.

Current output remains640x480 at~8–13FPS with a15FPS source cap, not Linux
runtime parity. Basic Setup input proven; full desktop input, network, audio,
clipboard, lifecycle and multi-workspace integration still need verification.
Guest clock2247 and guarded temporary kernel RAM fixes remain unresolved.
Preserve the user's new account/root. Ubuntu/P7/production APK untouched.
See docs/WINDOWS_MILESTONE_CHECKLIST.md for the authoritative current ladder.
No commit/push/publication.

## Windows real GUI / codec milestone — 2026-10-07

Isolated CAM PCI adapter now enumerates unique virtio devices and signed viostor
binds successfully (device/SCSI disk/volume problem0). Public GDI captures show
actual Windows Setup at native 640x480; console hidden. This supersedes older
GUI-not-tested statements below, but is NOT completed installation.

Official OpenH264 ARM64 works inside this WinPE: 30 captured / 30 encoded /
30 locally decoded frames, mean capture+conversion3.194ms, encode12.493ms,
luma MAE0.413. Existing UVF1 framing preserved. Next: signed viosock transport,
common MediaCodec presentation and input. No continuous Android output PASS yet.
See `docs/WINDOWS_MILESTONE_CHECKLIST.md` for current authoritative ladder.
Production Ubuntu/storage/display untouched; timer/CAM fixes remain guarded
isolated RAM workarounds. No commit/push/publication.

## Windows continuation — 2026-10-05

2026-10-07 **WINPE USERSPACE PASS (isolated diagnostic VM)**:
kd-native-winpe-processes-20261007-runtime proves active winpeshl.exe (2 threads),
setup.exe (3), SetupPrep.exe (3), SetupHost.exe (13), services/csrss/winlogon
from the WinPE Ramdisk, all ExitStatus=STATUS_PENDING. Guarded native accessor
checks and reciprocal process-list links passed. Timer workaround cold boot
repeated IoInitSystem success and CTL=1. Own KD break resumed; subsequent silence
is observer timeout, not evidence of guest crash. GUI/input/install completion
NOT tested. Fix remains diagnostic RAM-only, kernel-version guarded; no APK
integration or durable platform-side fix yet. Next: Windows display/interaction
and safe reusable handoff, NOT repeat old post-EBS investigations.

2026-10-07 cold-boot progress: guarded RAM-only periodic CNTV rearm thunk
passes IoInitSystem WITHOUT manual late unmask. Native post-I/O sample CTL=1,
deadline in future; further condrv/srvnet/srv2/mpsdrv loads and responsive KD.
Evidence: kd-native-periodic-imask-20261007-runtime. WinPE not yet confirmed;
next verification inventories active processes at a bounded debugger break.
Profile/clock alias hypothesis refuted (selected profile/always-on timers NULL).
Exact remasking origin still unknown; this is an isolated workaround, not a
vendor-causality claim or production integration. Disk/kernel baseline untouched.

2026-10-07 **new causal A/B progress**: native guarded four-MRS sampler proves
CNTV_CTL=7 (enabled/masked/expired) even after PoIdle return + DAIFClr.
One IMASK-only clear in disposable VM, with original code/full context restored,
unblocks the ORIGINAL cache cleanup (same map identity) and reaches IoInitSystem
return plus new win32kfull/dxgmms2/cdd loads. WinPE/Setup NOT PASS; later quiet
after cdd. No production fix yet. Evidence kd-native-imask-ab-resume-20261007-runtime.
Historical next step (superseded by cold-boot result above): investigate alias;
HalpGitInitialize requests ctl=3, HalpGitArmTimer requests ctl=1. Exact native
code/context guards and compiled sample in tools/windows-reassessment. Avoid
repeating KD physical/IO reads (fail) or interpreting virtual MMIO zeros without
native-width validation. Goal active: autonomous Windows to WinPE; no commit/push.

2026-10-07 continuation: isolated `kd-native-wait-wfi-20261007-runtime`
proves HalProcessorIdle WFI instruction at 0x4841f8 and its return at
0x4841fc both execute after the observed PoIdle return. An architectural WFI
hang is not demonstrated. Original timeout/IoInitSystem still do not finish;
no new platform/kernel fix or WinPE PASS. Ubuntu display work paused by user.

- Public hotfix 1.0.1/code15 is installed and published; Linux payloads/workspaces preserved. Do not reuse the older RC4 installation statement below as current device state.
- Retained validated clock-write trace reached bugcheck 0x5C(0x110, timer, 0x19, C0000001), LR kernel+0x48807c. Every observed CNTV MSR had a confirmed post-instruction hit. Frequent KD stops may perturb the interrupt self-test; this is not a proved CNTV defect.
- Exact kernel disassembly identifies a 3-second interrupt self-test waiting for timer+0x40 to increment. Its callback is RVA 0x49fe60; success path is 0x487fdc. The prior CLOCK_IRQ probe at 0x2131f0 is the normal clock path, not this temporary self-test callback.
- New sparse observer uses one-shot callback/success/bugcheck/delay-return probes, without driver/MSR stop loops. No Windows/kernel binary patch.
- Initial attempt correctly refused to start: device disposable disk SHA differed. FAT/shim and FD range still match; restoring only the designated diagnostic disk from exact local candidate before testing. Ubuntu/P7/official ISO/Windows baseline untouched.
- Sparse runtime now proves TIMER_TEST_ISR and TIMER_TEST_SUCCESS: virtual PPI27 reaches the Windows self-test callback, timer+0x40 increments to 1. The prior 0x110 bugcheck did not recur. Do not treat the heavily instrumented clock-write run as a proven timer failure.
- Three bounded wait follow-ups now capture delay-call CPSR=0x20001044 (IRQ mask clear), interval=-50 and refcount=4; then a different thread returns STATUS_TIMEOUT from the same generic wait function. Identity-guarded snapshot of the ORIGINAL waiter proves its map refcount has fallen to 1, but its cache-cleanup delay return is still absent. Do not conflate the other thread's return with original waiter wakeup.
- Original thread snapshot includes timer at +0xf8, due time at +0x110. Exact disassembly KiCommitThreadWait initializes x26=thread+0xf8; previous diagnostic label `timer` for the x1 wait-block pointer (thread+0x138) was imprecise. Raw evidence retained; new labels distinguish wait block and timer.
- No observed normal-clock-handler/timer-write probes after delay-call, no IoInitSystem completion. Timer expiration/waiter scheduling remains the current boundary; no ReFS refcount mutation or global timer-failure claim is justified. Evidence: kd-native-{wait-clock,wait-program,original-waiter}-20261005-runtime. All diagnostic VMs cleaned up; no commit/push/production changes.
- Timer fields independently checked against public PDB types and exact disassembly: original KTIMER is Inserted=true, Expired=false, thread Waiting/DelayExecution, TimerActive=true. Public PDB GUID matches, but stripped PDB age 6 differs from image RSDS age 1; do not claim full symbol identity.
- Bounded timer-wheel run proves the original timer is the sole entry in its correctly computed type-1 bucket, bucket lock=0 and due matches. KiProcessExpiredTimerList runs from an inline KiRetireDpcList path; separate KiTimerExpiration/DPC probes do not fire. Saved processing time precedes this timer's deadline by 18 units, while shared time advances during debugger stops. This is NOT by itself proof of a broken expiration comparator.
- Bounded idle follow-up proves PoIdle call at 0x50eeb4 and return at 0x50eeb8 both execute; return x0=C0000225. IRQ mask at both probes is expected for that call site (DAIFClr follows). Do not claim the first CPU-idle transition hangs or that this return alone diagnoses a power-state error. Original timer remains queued; alternate always-on/clock-work handlers not observed after the other thread's return.
- No CcDeletePrivateVolumeCacheMap delay-return or IoInitSystem completion. Both new runs stop after one unsuccessful UART break-in and scoped cleanup. Evidence: kd-native-{timer-wheel,wait-idle}-20261005-runtime; details in docs/WINDOWS_CLOCK_SPARSE_2026-10-05.md. Next useful discrimination is the timer rearm/idle fallback path AFTER this proved PoIdle return, not another HAL self-test, ReFS disable, or blind ACPI/CPU flags. No production change, commit or push.

## Current public baseline — U-AVF 1.0

This section is authoritative. Older checkpoints below are historical evidence,
not current blockers or instructions to recreate P/V diagnostic images.

- Public stable release: https://github.com/dt0-imsyu/U-AVF/releases/tag/v1.0.0.
- Release source/tag: `78656e7310285e9267fb5a443d8a3f367483fc33`.
- APK: `U-AVF.1.0.apk`, versionName `1.0`, versionCode `14`, ARM64/API 36.
- APK SHA-256: `4C0AAFC1BAB2BF3000CBFDEB473C925FFA65DF15D151D390E26550D80B4184D8`.
- The public 1.0 APK has been built, signed and published. The tablet remained on
  its tested RC4 installation; do not describe public 1.0 as device-installed.

### Proven Linux/product path

- Persistent Ubuntu installation and boot work. Ubuntu Setup opens automatically;
  the completed installer/reboot flow applies the runtime and selects installed boot.
- Official Ubuntu 24.04.5 Desktop ARM64 ISO remains unchanged. Managed single
  virtio-blk layout preserves platform/ESP and ISO; Ubuntu root is the install target.
- Preserve the proven P7 platform contract, installed user data and Windows baseline.
  No root/unlock/flash, no second virtio-blk, no auto-memory-balloon.
- Hardware OpenGL: VirGL -> Mali-G925. Production encoded display supports the
  previously proven 1920x1200 @ 60 FPS path; release preparation did not establish a
  new sustained-FPS measurement. RFB/raw remain fallback, not the primary path.
- Correct authenticated GNOME/GDM login and Firefox Snap startup were verified.
  Input, network and low-latency audio have working baseline evidence.
- Safe LLVM JIT target/cache invalidation addresses the localized SVE INDEX SIGILL
  mismatch. App Center works but can be slow.
- Clipboard is optional and off by default. New bidirectional/hot-toggle acceptance
  and real partition/ext4-growth acceptance were explicitly skipped, not marked PASS.

### Remaining architectural limits

- Hardware Vulkan is NOT PASS. Guest Venus is present, but vendor virglrenderer
  lacks Vulkan support; gfxstream reaches Mali and fails at memory registration:
  `ResourceMapBlob -> register_memory -> EFAULT` on GenieZone.
- Windows native AVF reaches Boot Manager, winload.efi and successful
  ExitBootServices return. `WINDOWS_POST_EBS` and `WINPE` are NOT PASS.
- Physical timer CNTP/PPI30 failure is independently reproduced; virtual timer
  CNTV/PPI27 works. Exact current WinPE binary audit identifies CNTV writes and
  no CNTP comparator writes: the direct CNTP explanation is refuted for this build.
  Do not use the older physical-timer hypothesis as the current Windows diagnosis.
- Known release limitations: installer flicker, slow App Center, installed-status
  transition after the first reboot; no promise of universal ISO or shared-folder support.

### Release/privacy and next work

- Firebase compatibility reporting is voluntary, off by default, explicitly cloud
  based. Live anonymous-auth/create-only permission tests passed; guest files,
  passwords and clipboard contents are not uploaded.
- Privacy notice documents collected fields and no automatic expiry/in-app deletion.
  Global anti-abuse protection and complete third-party source/build compliance
  review are not claimed complete.
- Scoped source-available licensing preserves historical Apache and third-party
  licenses. Publication is not a legal/compliance certification.
- Public release notes, quick start, privacy and license files are in
  `docs/release-preparation/`. Keep future work regression-driven; do not restart
  closed FPS or Vulkan flag experiments without new evidence.

## Windows reassessment after Linux — 2026-10-04

Linux ACPI is not globally broken: the retained Debian EFI-stub v3 reaches
installer userland after correcting earlycon from MMIO32 to MMIO8. Working
FDT additionally solves device discovery. The incomplete ACPI PCI contract
is a confirmed gap: backing is CAM, EDK2 has a special CAM adapter, dynamic
ACPI lacks PCI root/MCFG. A fake MCFG would not create a real ECAM aperture.
Its relation to Windows' first silent boundary is still unproven.

A new exact-instruction diagnostic tested Windows' advertised-MPAM register
access in one diskless 12-second GenieZone VM. EL1 MPAM1_EL1 read returned
`0x8000000000030002` without a fault. No Windows boot, Ubuntu mutation or
post-EBS progress occurred. CPU/EL1 contract versus early HAL remains
unlocalized; pause further Windows boots until a PC/exception observer or
valid platform control is available. Details:
`docs/WINDOWS_POST_LINUX_REASSESSMENT_2026-10-04.md`.

Observer follow-up: public GenieZone backend lacks hardware breakpoint/single-step
support (EINVAL), although individual register reads exist internally. The device
crosvm binary is not shell-readable; public source is not asserted identical to
Samsung's build. No supported app route to AVF-owned vCPU registers was found.
No new VM run or Windows progress; request a vendor PC/exception snapshot rather
than assume exposing a GDB port alone solves observation.

New internet/source finding reopens ONE local observer A/B: retained r11 DBG2
subtype 0x12 has GAS width32/access-byte, whereas the actual UART has byte
spacing (U-Boot shift0). Microsoft defines this width as register stride.
The generator retains ARM_GAS32. Correct width8 in disposable Windows-only
diagnostic firmware and test existing KD bridge once; no new BCD/CPU sweep.
Now tested: GAS32 control has no KD packets; GAS8 emits valid KD. Unbuffered
Base64 ingress + complete records + Windows transmit gate restore full local
Windows Boot Debugger connection, winload symbols, registers and stack.
Continuing loads drivers, then boot-debugger closes near EBS; validated packets
report C0000005 reading UART 0x3fd at PC 0x17f3b00ac. Not a proven explanation
of the old non-debug stall; kernel entry/WinPE remain unproven. One later
breakpoint run failed to read module state, so do not assume reliable kernel
debugging. Offline exact-r11 extraction now identifies the fault as the added
`RuntimeDxe!WinAvfAuditPutc` (FFS B601F8C4), not BdsDxe or Windows. Its full
40-byte bounded helper matches, including RVA 0x100ac / fault PC. BdsDxe test
failed closed. Initial RuntimeDxe RAM patch hit a read-only EFI code page before
Windows; next isolated test temporarily clears RO with the standard UEFI memory
attribute protocol, restores it, and disables only this diagnostic helper.
Quiet helper test PASS including original page protections restored: UART AV
absent in final validated trace. Boundary trace now installs both software
breakpoints and HITS BlBdStop: OslMain -> OslFwKernelSetup -> BlBdStop, registers
and disassembly captured. Shutdown event precedes the EBS call; no kernel
transfer hit is proven. Remaining local observer issue is synchronization across
BlBdStop/BlBdStart, not justification for random CPU/GIC/timer experiments.
No APK/Ubuntu/on-disk FD/Windows baseline change; kernel entry remains UNKNOWN.
The previous blanket 'no local observer' pause above is superseded.

### Latest Windows kernel breakthrough (supersedes UNKNOWN above)

**Newest verified result: GIC/HAL interrupt initialization PASS with a local
Windows-only EBS shim fix.** The earlier paragraph below is historical control.
Control kernel reads GICD_CTLR=0 and GIC3 InitializeIoUnit returns C0000184,
then 0x5C. Exact retained firmware GIC shutdown writes CTLR=0 at EBS. New
TPL_CALLBACK event restores DS bit6 after that TPL_NOTIFY shutdown; no IRQ
delivery is enabled. Fixed kernel reads CTLR=0x40, GIC3 init returns 0, and
complete HalpInitializeInterrupts returns 0. Kernel then emits driver/module
load notifications through disk.sys/CLASSPNP.SYS. WinPE/Setup still NOT PASS;
new live trace proves Phase1Initialization and IoInitSystem entry. KD refresh
is watchdog!WdIsDebuggerPresent from dxgkrnl!TdrInit/DriverEntry and returns
x0=0 normally; it is not a demonstrated hang. IoInitSystem completion remains
unproven. Two boot-driver trace attempts failed in initial winload KD
RESEND/RESET synchronization before kernel commands ran and are INVALID.
Fix the diagnostic channel before repeating those boots; no vendor-only
conclusion or new ACPI/CPU/timer changes.
Independent minimal KD now proves GetVersion and reads of the exact winload
PsLoadedModuleList address (1/16 bytes) SUCCESS. Pacing and alternate initial
break did not fix DbgEng. Lossy startup gating was corrected and unit-tested,
but DbgEng still fails initialization. Independent KD state/continue now works:
`kd-native-driver-autoremove-runtime` traces actual boot-driver entry/return,
including SUCCESS from DXGKrnl after its KD refresh, ACPI, PCI, NTFS and ReFS.
Newest boundary: ReFSv1 initializes its libraries and creates its worker thread
(PsCreateSystemThread/ZwClose SUCCESS), then does not return from the kernel's
CcRegisterExternalCache. Cache is initialized (1), allocation succeeds and
its external-cache spinlock is zero. Partition-mode=1; private-map allocations,
initialization and insert locks pass. An existing map is found, so Windows
deletes its unused new map. Worker shutdown events are signaled and
ZwWaitForSingleObject on its worker returns SUCCESS. Other worker entry/wait
hits have a different map pointer and must not be conflated with this worker.
Latest boundary: CcDeletePrivateVolumeCacheMap at 4c361c sees refcount=4
(needs 1), then calls KeDelayExecutionThread; no after-delay 4c363c within 30s.
Next distinguish exact timer/wait/wakeup and reference release, not blindly
disable ReFS or mutate its refcount. CLOCK_IRQ absence does not prove CNTP cause.
Delay audit: interval=-50 (5 microseconds). Internal KD stops make the deadline
expire and produce STATUS_TIMEOUT/SUCCESS; sparse after-return-only control
still gets no return in 30s. This is observer perturbation, NOT a timer fix.
Next observe actual virtual CNTV_CTL/CVAL programming and interrupt delivery.
Evidence: kd-native-cache-{private,insert,delete-validated}-runtime.
This is NOT yet a proven ReFS defect, timer failure or crash. The observer
accounts for KdpSetCommonState automatically deleting the hit breakpoint;
original instructions and empty breakpoint-table flags are checked before resume.
One staging-race run is explicitly INVALID; staged SHA is now a start barrier.
Do not repeat negative DbgEng/pacing tests or random platform flag sweeps.
Evidence: `uart-hal-ds-ebs-runtime/kd.log.txt`, `hal-ds-ebs-final-packets.json`
under the reassessment build-log directory. Candidate SHA 87CD1D44...1A34DB35.
No production APK/Ubuntu/P7/on-disk firmware/Windows baseline modification.
No diagnostic VM left running, no commit/push. Continue from the next boundary,
not another controller discovery/version/CNTP flag sweep.

GAS8 + RAM-only quiet RuntimeDxe + chunked unbuffered ingress + KD resets=0
connects after EBS to the actual Windows 26100 ARM64 kernel. Proven stack:
`nt!KiSystemStartup -> nt!KdInitSystem -> nt!DbgLoadImageSymbols`.
After continuing, fatal bugcheck **0x5C HAL_INITIALIZATION_FAILED**:
`0x200, 0x05250631, 2, 0xffffffffc000000e`.
Kernel entry PASS for this isolated diagnostic contract; WinPE/Setup NOT PASS.
The automatic follow-up stalled in initial KD synchronization (invalid trace).
Matching kernel/PDB offline analysis now localizes the exact failure to
`HalpInitializeInterrupts -> HalpInterruptSelectController`: no usable registered
interrupt controller; LastProblem=2 and status C000000E. This is not a PCI or
timer diagnosis. Read-only EFI probe finds GICD_TYPER=0x50, PIDR2=0x3b,
GICR_TYPER=0x10, WAKER=0, MPIDR=0x80000000; simple GIC version mismatch is
not supported. Next KD trace targets GIC discovery/initialization failures. No new
CPU/GIC/timer/ACPI guesswork. Combined observer change is not an isolated causal
proof. APK/Ubuntu/production graphics/on-disk FD/Windows baseline untouched.
Evidence: `docs/WINDOWS_POST_LINUX_REASSESSMENT_2026-10-04.md` and
`build-logs/windows-post-linux-reassessment-20261004/uart-gas8-chunked-runtime`.

## Historical checkpoints — superseded, not current status

## Current standard Linux launch — P40

`LINUX_AUDIO_OUTPUT=PASS`: official Ubuntu ISO and packaged P40 platform
prefix booted GNOME; user heard tablet-speaker audio with minimal delay after
the low-latency guest `parec` / Android `AudioTrack` change. AVF vsock 4053
reports stable 48 kHz stereo PCM; Android fast track reports about 66 ms
latency and no increasing underrun count after startup. The VM remains running.
The ordinary in-app Linux launch now uses the Linux-first VirGL/GBM/Xvnc
platform and keeps the existing 4052 WAVF frame path. Direct `glxinfo`
renderer proof and sustained 1080p30 under motion remain open. See
`docs/UBUNTU_P40_LOW_LATENCY_AUDIO_2026-09-27.md`.

## Latest decisive GPU A/B — Linux first GPU bind, 2026-09-27

The frame bridge is stable beyond its former 180-second cutoff: `LV:FRAME_BRIDGE_SOURCE=INITRD_OVERLAY`, non-black GNOME frames, no late `streamClosed`; screenshot and logs are archived. Do not iterate on display transport without a new concrete failure.

A disposable diagnostic FD excluded **only** the UEFI `VirtioGpuDxe` module. Linux autobooted headlessly and initialized virtio-GPU itself. The prior `virtio_gpu_get_capsets` timeout disappeared, and host Gfxstream proceeded to GPU format and blob commands. This strongly supports the UEFI→Linux GPU reset/reactivation hypothesis. The next observed failure is precise: crosvm `ResourceMapBlob` could not add GPU memory to the VM; `register_memory failed ... Bad address (os error 14)`. Guest Mesa then reported `mmap64 EINVAL`, and Vulkan enumerated zero devices (`-3`). A targeted `VulkanAllocateHostVisibleAsUdmabuf:disabled` A/B was accepted by Gfxstream but did not change the EFAULT. **Hardware acceleration is still not working.** Stop broad GPU flag sweeps; investigate the host GPU blob-memory mapping contract or seek vendor crosvm/GZVM support.

The diagnostic FD SHA is `2FC8B2D2781AC215CF7A14DE8219E51B4554CAF8AF163703FCC4DF18BA3F37A7`. The generated stock FD was restored exactly to SHA `7162202A2ED14C6BE3433915CB5786D9A41E3722647E40AA3CFEF29720D14963`. The disposable Android disk was rolled back to exact P7/Gfxstream SHA `D16EE353C8D0B5AC3262073D76552ED62135A4169798EF6F43D47BCF5F7D208F` and normal GNOME fallback relaunched. GOLDEN, stock Ubuntu ISO, Windows baseline and signed Windows binaries were untouched. Full report: `docs/GPU_GFXSTREAM_RESET_BOUNDARY_2026-09-27.md`; evidence: `build-logs/gfxstream-zink-20260926/linux-first-gpu/`.

## Earlier GPU boundary (superseded by first-bind A/B)

The correct P7 initrd now boots GNOME with the ARM64 Gfxstream guest ICD installed. A Vulkan-device preflight avoids the previous “Oh no” crash by falling back to softpipe. Both the original Gfxstream experiment and a single `VulkanAllocateHostMemory:enabled` A/B select the host Mali Vulkan device but fail guest enumeration (`VK_ERROR_INITIALIZATION_FAILED`, zero devices). Linux's `virtio_gpu_get_capsets` times out. Host log shows Gfxstream shutdown followed immediately by `pcivirtio-gpu activate failed: worker thread missing on activate?` at the firmware→Linux GPU transition. Upstream crosvm reset/activate source matches that exact error; Samsung's source revision remains unverified. Do not randomly vary GPU flags. The next single discriminating test, when the current VM can be stopped, is a disposable firmware clone excluding only pre-boot `VirtioGpuDxe`, so Linux makes the first GPU activation. The live GNOME VM was left running. Full evidence: `docs/GPU_GFXSTREAM_RESET_BOUNDARY_2026-09-27.md`.

## Current priority: hardware GPU activation

Linux desktop is currently visible in U-AVF. Do not stop or restart that running session just to re-prove the display path. Keep these two facts separate:

```text
ANDROID_HOST_GFXSTREAM_GLES_ON_MALI = PASS
LINUX_GUEST_VIRTIO_GPU_3D           = NOT_WORKING
GNOME_RENDERER                      = SOFTPIPE
```

The current Gfxstream GLES run initialized successfully on the physical Mali-G925, and the existing 4052/WAVF path delivered valid 1920x1080 non-black Linux frames to Android. This proves the host renderer can open the device, not that guest GNOME uses it. Guest serial still reports `virtio_gpu_get_capsets: timed out waiting for cap set 0`, `LV:SOFTPIPE=ENABLED`, and `No virgl contexts available on host`; GNOME therefore remains software-rendered. Gfxstream Vulkan was separately tried once and crosvm aborted during host color-buffer allocation (`Failed to find memory type` / format 32993 at 640x480), so do not repeat that same configuration unchanged.

The shortest documented Linux Gfxstream route is the **guest Vulkan ICD + Mesa Zink**, not the host-only GLES toggle: upstream crosvm's Linux guest instructions build `gfxstream-vk`, set `MESA_LOADER_DRIVER_OVERRIDE=zink` and `VK_ICD_FILENAMES`, then start a DRM compositor. Their sample ICD filename is x86_64, so an ARM64 guest artifact/build is still required. Next work should statically establish an ARM64 guest-driver build/deployment path and diagnose the recorded Vulkan host allocation failure before any new runtime. Keep the official Ubuntu ISO byte-exact; any driver overlay must be app-owned/disposable and must not replace the proven P7 platform. Evidence and exact logs: `docs/GPU_ACCELERATION_HOST_WORKER_AUDIT_2026-09-26.md` and `build-logs/gpu-hardware-audit-20260926/`.

## Latest 1080p performance boundary — P31/P32, 2026-09-26

```text
GNOME_1920x1080_VISIBLE              = PASS
GNOME_1080P30_PRESENTED_UNDER_MOTION = NOT_CONFIRMED
P31_SMALL_WINDOW_DRAG_PRESENTED       = ~18-24 FPS, variable
P32_GNOME_4052_ANDROID_PRESENTATION   = PASS before stream close
P32_DIAGNOSTIC_WINDOW_CLOSE          = BRIDGE_X11_CLIENT_DISCONNECTED
P32_VM_PROCESS                       = RUNNING at last check
ACCESSIBILITY_GLOBAL_KEY_CAPTURE     = NOT_IMPLEMENTED
```

P31 proved the received packets are almost all drawn by Android; the guest's
sequential X11 capture and compare/compress stages exceed the 33 ms budget
under actual movement. P32 retained the P7 platform and stock ISO, but the
temporary test window lacked `WM_DELETE_WINDOW`. Closing it disconnected
the X11 client that also runs the frame bridge, ending 4052 while crosvm
remained alive. The last Android image is stale. The user's last-launch/no-
deletion instruction is in force: **do not restart or delete any current P32
artifact automatically**. See
`docs/GENERIC_UBUNTU_P31_P32_1080P_PERFORMANCE_2026-09-26.md`.

## P27/P28 interactive GNOME and next display work

```text
GNOME_TOUCH_INPUT_TO_XTEST          = PASS (P27, P28)
GNOME_BASIC_KEYBOARD_TO_XTEST       = PASS (P28)
GNOME_CONTINUOUS_1024x768_2FPS     = PASS (P28)
GNOME_1080P_30FPS_RUNTIME          = NOT_TESTED
OFFICIAL_ISO_ONLY_APP_SETUP        = NOT_IMPLEMENTED
```

P27 opened the real GNOME app grid from Android touch. P28 eliminated the
Python per-pixel conversion and measured 1.98679 frames/s for a 2 FPS target;
the currently running VM's report reached 1,765 frames, 1,740 nonblack,
without a disconnect. The P7 platform and official ISO are unchanged.
The Linux launch button still uses an older generic profile; P28 is an
explicit diagnostic launch requiring a prepared disposable combined image.

Uninstalled source changes add physical mouse buttons/scroll, fix Stop VM
selection before vsock connects, archive first visible frames, and implement
WAVF v1 compressed keyframes plus 64-pixel changed tiles. Offline 1920x1080
producer/decoder self-tests PASS. The bridge was repacked as P29 initrd
(SHA `BD8C61BF...B254BD`; exactly one changed CPIO file) and materialized as
an offline disposable P29 disk (SHA `2280A81E...4E6D0`; GPT/ISO/FAT audit
PASS). No P29 runtime has occurred. These changes have **not** been tested on the tablet;
the installed/running APK and P28 guest still use the older full-frame format.
Do not claim 1080p30, full keyboard/mouse, or one-ISO setup yet. No commit or
release until stable output/input and license choice are resolved. See
`docs/GENERIC_UBUNTU_INTERACTIVE_P28_AND_DISPLAY_NEXT_2026-09-25.md`.

## GNOME Shell visible in Android — P26

```text
GNOME_SHELL_VISIBLE_IN_APP = PASS
WAVF_VSOCK_4052_ANDROID_SURFACEVIEW = PASS
GNOME_FULL_DESKTOP_INTERACTIVE = NOT_TESTED
```

P25 GDB identified the isolated Xvnc GNOME Shell crash as `SIGILL` at an
ARM SVE `index z1.s, #0, #1` instruction. P26 changed only the guest session
renderer selection to Mesa `GALLIUM_DRIVER=softpipe` (with the existing
`LIBGL_ALWAYS_SOFTWARE=1`). The exact P7 firmware/launcher/kernel, single-disk
geometry, Android receiver and stock ISO remained unchanged. P26 serial shows
`LV:SOFTPIPE=ENABLED`, `LV:XVNC_SOCKET=PASS`, `GNOME Shell started`, and
`CRASH_FILES=0`; the previous fatal GNOME screen and SIGILL are absent.

The 1024×768 GNOME Shell top bar was captured with a valid WAVF CRC over
vsock 4052 and is visibly present in the app SurfaceView. The dark desktop
background and lack of tested input mean this is **not** yet a claim of a
fully interactive graphical desktop. The diagnostic VM remains running for
inspection; the Stop button in the app can end it. The P26 candidate is
disposable and GOLDEN/Windows baseline were not changed. See
`docs/GENERIC_UBUNTU_GNOME_SHELL_VISIBLE_P26_2026-09-25.md`.

## GENERIC_UBUNTU — stock ISO, one-disk control

`GNOME_USERSPACE = PASS` on the app-owned AVF/GenieZone VM. The official
Ubuntu 24.04.5 ARM64 ISO remains byte-exact and unmodified. A single GPT
disk carries the audited 128 MiB platform ESP plus a separate ISO9660
partition with byte-exact stock ISO bytes. This avoids the reproducible
two-virtio-blk stall after `Freeing initrd memory`.

The final configuration uses the proven U-Boot -> EDK2 -> Ubuntu EFI-stub
chain, 4 GiB RAM, 1 vCPU, virtio-GPU, one read-write combined disk and
`useAutoMemoryBalloon(false)`. The bounded raw serial shows `/init`, systemd,
`Started gdm.service`, `GNOME Shell started`, and `Registering session with
GDM`; zero SquashFS/loop I/O errors were observed in that run. With
auto-balloon enabled, identical media repeatedly produced SquashFS failures
and GDM's session worker failed with `Input/output error`. The internal
mechanism remains an inference; do not call it a confirmed vendor bug yet.

At that 2026-09-24 checkpoint, `GNOME_VISIBLE_IN_APP` was not confirmed:
guest virtio-GPU DRM framebuffer existed, but no post-EBS frame path into
the U-AVF SurfaceView had been proven. P26 above supersedes this status.
The installed APK's Linux Launch/Stop buttons now select this generic VM.
Its final APK SHA-256 is
`299D80A36025FEF81015B15A648DF15717BC4688CAD38B2999DBF7A1A0058374`;
the passing final-app run left the VM running on the tablet.
Full hashes, run command and evidence: `docs/GENERIC_UBUNTU_GNOME_USERSPACE_2026-09-24.md`.

## Ubuntu GNOME / public-vsock V17–V18 — 2026-09-21

The public app-owned Linux route now has a proven post-EBS userland control:

```text
LINUX_KERNEL_POST_EBS        = PASS
UBUNTU_GNOME_USERLAND        = PASS
INITRAMFS_VSOCK_HOOK         = PASS
INITRAMFS_STATIC_LISTENER    = FAIL_TO_EXECUTE
APP_OWNED_LINUX_VSOCK        = NOT_CONFIRMED
```

V17 corrected main-Zstd-CPIO placement and the `init-premount/ORDER` control
file. Its serial log proved `LV:HOOK`; the listener itself then failed with
`/usr/local/sbin/winavf-vsock-hello: not found`. V18 rebuilt that same static
ARM64 ELF with a compact 4 KiB `PT_LOAD` offset, but reproduced the identical
exec failure. The V18 guest nevertheless reached `graphical.target` and the
Ubuntu serial login prompt, proving the real AVF/GenieZone VM runs Linux
through kernel, systemd and graphical userland after EBS.

Android `connectVsock(4051)` returned `ENODEV` on V17 and `ECONNRESET` on
V18, neither of which establishes the listener. Do not infer an AVF-vsock
failure until a listener executable is first proven runnable in the exact
initramfs environment. Evidence:
`docs/UBUNTU_GNOME_VSOCK_V17_V18_RUNTIME_2026-09-21.md`.

## Ubuntu GNOME / public vsock V14 — 2026-09-20

V14 is a disposable Linux-only control that proved the first app-owned
post-EBS GNOME stack and guest PF_VSOCK registration on the real AVF/GenieZone
topology:

```text
LINUX_KERNEL_POST_EBS        = PASS
LINUX_PF_VSOCK_REGISTERED    = PASS
UBUNTU_GNOME_DISPLAY_MANAGER = PASS
UBUNTU_GNOME_USER_SESSION    = PASS
LINUX_VSOCK_HELLO            = NOT_OBSERVED
```

The public Android `connectVsock(4051)` call now reaches a VM-side endpoint
and returns `Connection reset by peer`; V13 had returned `No such device`.
The listener's `LV:*` marker was absent, so this is not yet guest listener
proof.  V15 adds precisely one initramfs hook marker before the existing
static listener.  V14 evidence and cleanup details are in
`docs/UBUNTU_GNOME_VSOCK_V14_RUNTIME_2026-09-20.md`.

## Ubuntu post-EBS Device-Tree control — 2026-09-20

V11 is the first app-owned AVF/GenieZone runtime to prove the complete Linux
kernel-to-PID-1 path using the normal FDT handoff selected during DXE:

```text
LINUX_KERNEL_POST_EBS          = PASS
LINUX_FDT_DXE_HANDOFF          = PASS
LINUX_PCI_VIRTIO_BLOCK         = PASS
LINUX_SYSTEMD_PID1             = PASS
LINUX_VIRTIO_GPU_BOUND         = PASS
UBUNTU_CASPER_BOTTOM            = PASS
UBUNTU_GNOME_DISPLAY_MANAGER    = PASS
UBUNTU_GNOME_USER_SESSION       = PASS
UBUNTU_GNOME_SCREEN_IN_APP      = NOT_AVAILABLE
```

V12 added an exact SHA-256 gate for the app-private backing-file copy and
passed it. Casper completed; `gdm.service` and live Ubuntu user sessions
started. Later SquashFS XZ errors occur while the desktop accesses more live
packages, but they do not erase the kernel-to-GNOME milestone. No post-EBS
frame relay exists yet, so the graphical session is proven on serial rather
than claimed visible in the app. Full evidence:
`docs/UBUNTU_GNOME_FDT_DXE_HANDOFF_V11_RUNTIME_2026-09-20.md`.

The two equivalent Linux launches also reported different `MIDR_EL1` values
at kernel entry (`0x410FD821` and `0x410FD851`). This is cross-launch evidence
of a non-fixed host-derived CPU profile, not yet proof of within-VM migration
or a Windows root cause. It is retained as a high-value platform-contract
lead, not an excuse for unprincipled CPU-feature masking.

## Ubuntu GNOME FDT fallback boundary — 2026-09-20

The V10 standalone EFI launcher successfully mutated the EDK2-visible
`FdtClient` CPU node (`UF0`), removed ACPI (`UA0`), and reached Ubuntu
`start_kernel()` after `ER`, where Linux reported `Failed to find device node
for boot cpu` and panicked during early memory-zone initialization. A source
audit corrected the initial interpretation: KvmTool had selected ACPI during
DXE, so `FdtClientDxe` never published its HOB FDT via `gFdtTableGuid`; late
ACPI removal cannot activate that normal Device-Tree handoff.

```text
LINUX_KERNEL_POST_EBS              = PASS
LINUX_FDT_FALLBACK_ENTERED          = PASS
LINUX_FDT_BOOT_CPU_TOPOLOGY_VALID   = FAIL
UBUNTU_GNOME_LIVE_USERSPACE         = NOT_REACHED
```

The ACPI route has the complementary limitation: it reaches Casper but lacks a
usable PCI/virtio block path. Do not iterate ISO/EFI-launcher variants. The
next exact experiment is a firmware-only DXE Device-Tree-handoff A/B; only if
it fails is a lower-level U-Boot/crosvm FDT repair or a complete ACPI PCI-root
description required. Evidence:
`docs/UBUNTU_GNOME_FDT_FALLBACK_RUNTIME_2026-09-20.md`.

## Product UEFI serial-input boundary — 2026-09-14

The final r10/r11 app-owned product experiment closes the proposed serial
input route for now.  `getConsoleInput()` is available and accepts writes; an
earlier disposable byte-loopback control is byte exact.  But on the actual
Windows product topology, 220 flushed ESC bytes spanning r11's exact 30-second
EDK2 `SimpleTextIn` polling window produced no firmware acknowledgement and no
extra GOP response frame.  The original external image was rolled back and
verified exactly after the run.

```text
APP_OWNED_CONSOLE_INPUT_WRITE       = PASS
PRODUCT_TTYS0_TO_EDK2_SIMPLETEXTIN  = NOT_CONFIRMED
AVF_UEFI_SERIAL_INPUT_TRANSPORT     = BLOCKED_ON_PRODUCT_TOPOLOGY
GRAPHICAL_UEFI_INPUT                = NOT_CONFIRMED
```

Do not present serial arrow/Enter control as a product feature.  The existing
pre-EBS GOP renderer remains valid; an app Stop action can be built separately
from guest input.  Full evidence:
`docs/UEFI_SERIAL_INPUT_R10_R11_RUNTIME_2026-09-14.md`.

## Remaining post-EBS options — 2026-09-14

The independent API, trace, QEMU-contract and synthetic-platform audits now
establish the following operational boundary:

```text
LOCAL_NO_ROOT_WINDOWS_POST_EBS_ROOT_CAUSE_PATH = EXHAUSTED
PRODUCT_DEVELOPMENT_PATH                       = ACTIVE
```

No further BCD/KD/ftrace/GDB/WIM/ACPI/CPU-mask variation is justified without
a new supported platform capability. The correct next diagnostic owner is the
Samsung/GenieZone firmware path; the submitted physical-timer report requests
a vCPU timer repair or a supported trace/debug channel. A firmware/OTA
regression control and a second-device AVF control remain valid future tests.
Evidence and strict branch closure are in
`docs/REMAINING_POST_EBS_OPTIONS_2026-09-14.md`.

## QEMU accepted-platform contract — 2026-09-14

The one diskless QEMU v5 capture completed with `exit 0`, checksum-valid
FADT/MADT/GTDT/SPCR/DBG2/PPTT/IORT/MCFG records, 37 EFI memory descriptors,
and `QCP_DONE`.  It closes the last missing QEMU ACPI/EFI-map evidence.

```text
QEMU_ACCEPTED_PLATFORM_CONTRACT_CAPTURE = PASS
QEMU_AVF_WINDOWS_CONTRACT_DIFFERENTIAL  = NO_ACTIONABLE_FIRMWARE_DELTA
STOCK_LOCAL_FIRMWARE_REPAIR_CANDIDATE   = NONE
```

The QEMU↔product differences (SMC/HVC PSCI, PL011/16550 UART, timer flags,
GIC/ITS topology, RAM placement, and CPU feature profile) either truthfully
describe distinct backing machines, are optional/later facilities, or cannot
be changed without violating the product contract.  In particular, clearing
the product Low-Power-S0 FADT bit to match QEMU is invalid for the
hardware-reduced product platform; rewriting GTDT flags would also lie about
the live FDT interrupt model.  No new product VM was run.  Evidence:
`docs/QEMU_ACCEPTED_PLATFORM_CONTRACT_V5_2026-09-14.md`.

## P7 CPU contract and AVF architecture boundary — 2026-09-14

P7 captured the real product architectural CPU profile and rolled back its
single firmware range exactly. Product runs at EL1 with `MIDR=410FD851`,
`PFR0=1201011023111111`, and 13 MHz `CNTFRQ`; the working QEMU reference uses
the explicit Cortex-A76 model, a distinct feature profile, and 62.5 MHz.
Exact Windows binaries consume ID registers, but their known EL2 field test is
satisfied by both profiles. No product-only bit is tied to the silent Windows
branch, so a feature mask is not a valid local A/B.

```text
P7_RESULT            = PASS
CPU_FEATURE_CONTRACT = INCONCLUSIVE
```

The AVF architecture audit closes speculative hybrid-VMM options on stock
Samsung: private crosvm owns GenieZone VM/vCPU/RAM/exits/IRQ state, while the
app receives Binder lifecycle control plus disk, console and vsock channels.
Only virtio-fs is source-mapped to vhost-user; generic external GPU/HID/block/
net/vsock backends are not exposed. Mouse/touch APIs exist but are not yet
guest-runtime proven; keyboard injection is absent.

```text
CROSVM_EXTERNAL_DEVICE_SURFACE = FS_ONLY_PUBLICLY_EVIDENCED
GZVM_VM_HANDLE_ACCESS           = AVAILABLE_ONLY_INSIDE_CROSVM
QEMU_GZVM_ACCELERATOR            = BLOCKED_ON_STOCK_DEVICE
```

The next diagnostic action is a diskless QEMU contract capture, not another
Windows boot. Evidence: `docs/P7_CPU_ID_DIFFERENTIAL_2026-09-14.md`,
`docs/CROSVM_EXTENSION_SURFACE_AUDIT_2026-09-14.md`,
`docs/AVF_GZVM_HANDLE_OWNERSHIP_AUDIT_2026-09-14.md`, and
`docs/WINAVF_HYBRID_VMM_ARCHITECTURE_2026-09-14.md`.

## Synthetic P2.1 physical-timer boundary — 2026-09-12

The robust P2.1 retry is now valid.  A fresh physical-P2 source replay first
reproduced `BES -> P0 -> P1 -> TX` on the tablet and rolled back exactly.
Read-only FV comparison showed equal inner-FV module layout; `ConSplitterDxe`
differs by only eight DEBUG/LTO metadata bytes, so the former early abort did
not invalidate the retry.

P2.1 then reached:

```text
BES -> P0 -> P1 -> PW -> PN
```

`PW` proves the already-established virtual PPI27 watchdog woke the vCPU.
`PN` proves that after that five-second interval `CNTP_CTL_EL0.ISTATUS` was
still clear.  PPI30's handler marker (`P2`) was absent.  This is a timer
pending/emulation failure, not merely an unobserved first WFI wake.

P3 separated the remaining physical-timer states in the same bounded post-EBS
path. `PD` confirms the enabled, unmasked `CNTP_CTL_EL0` write was read back;
`PC` confirms `CNTPCT_EL0` advanced during the virtual watchdog; `PN` remains
the failed physical-comparator result. Thus the counter itself works while its
non-secure EL1 physical comparator never becomes pending.

P4 then removed the final ambiguity of the relative `CNTP_TVAL` programming
path.  It wrote an absolute `CNTP_CVAL_EL0 = CNTPCT_EL0 + 10,000,000` and
immediately got `CX` on readback; after the virtual watchdog it got `RX`,
`CE`, and `PN`.  Thus the counter passed the intended deadline, but the guest
physical CVAL state never retained the write and ISTATUS remained clear.

P5 proves only the masked pre-EBS CVAL access condition.  Before EBS, the
firmware app saved CVAL/CTL, masked the timer, wrote
`CNTP_CVAL_EL0 = CNTPCT + 10M`, and read back the exact value (`BC`); it then
restored both registers.  In the same run P4's enabled/unmasked post-EBS path
emitted `CX -> P0 -> P1 -> PW -> PC -> RX -> CE -> PN`.  This rejects universal
CNTP CVAL inaccessibility, but is not a strict EBS-causality proof because the
pre-EBS check did not enable CNTP.  A separate pre-EBS enabled control would
be needed for that vendor-only distinction.

P6 validates the P5 execution context without adding another timer mutation.
The direct raw-UART records were `L1` before EBS and `M1` after it; the EL2
VHE records are absent by design at EL1. The same run then reproduced
`PD -> CX -> P0 -> P1 -> PW -> PC -> RX -> CE -> PN`. Thus the P2–P5
accessors used the ordinary EL1 `CNTP_*` system-register path on both sides of
EBS; a switch to an EL2/VHE alias cannot explain the P5 discrepancy.

P7 independently proved the next EL1 execution primitive without touching the
timer, GIC, MMU, Windows media, or ACPI.  After the established `BES` marker,
the probe emitted `X0 -> XV`: it temporarily installed its own 2 KiB-aligned
`VBAR_EL1`, took one synchronous `BRK #0x714`, advanced `ELR_EL1` in the
handler, returned through `ERET`, restored the original vector base, and
continued.  The following `T0 -> T1 -> T2 -> T3 -> TR` records show the
existing virtual-timer/GIC/reset control path also remained intact.

```text
POST_EBS_VIRTUAL_TIMER_PPI27       = PASS
POST_EBS_CNTP_WATCHDOG_WAKE        = PASS
POST_EBS_CNTP_PENDING              = FAIL
POST_EBS_PHYSICAL_TIMER_PPI30      = FAIL
POST_EBS_CNTP_CONTROL_READBACK      = PASS
POST_EBS_CNTP_COUNTER_PROGRESS      = PASS
POST_EBS_CNTP_COMPARATOR_PENDING    = FAIL
PHYSICAL_TIMER_COMPARATOR_PATH      = FAIL
POST_EBS_CNTP_CVAL_WRITE_READBACK    = FAIL
POST_EBS_CNTP_COUNTER_PAST_CVAL      = PASS
PHYSICAL_TIMER_REGISTER_STATE        = FAIL
PRE_EBS_MASKED_CNTP_CVAL_READBACK     = PASS
PRE_EBS_CNTP_ENABLED_SEQUENCE         = NOT_TESTED
P5_RUNTIME_PROBE_VALID               = PASS
POST_EBS_EXCEPTION_LEVEL_CONTEXT     = PASS (EL1 -> EL1)
POST_EBS_EL1_EXCEPTION_VECTOR        = PASS
POST_EBS_EL1_SYNC_EXCEPTION          = PASS
POST_EBS_EL1_ERET_RETURN             = PASS
P5_CONTEXT_ALIAS_EXPLANATION         = REFUTED
P2_1_RUNTIME_PROBE_ENTRY           = PASS
WINDOWS_PHYSICAL_TIMER_DIRECT_CAUSE = REFUTED_FOR_CURRENT_WINPE_BUILD
WINDOWS_ARM64_VIRTUAL_TIMER_CODE_PATH = PASS (static)
```

The matching cross-OS timer contract is now explicit. P1 proved a real
post-EBS `CNTV_TVAL_EL0 -> virtual PPI27 -> WFI wake` path; Linux logged the
same timer as `(virt)` at 13 MHz; and the exact Windows kernel contains the
virtual comparator/control writes. This does not say Windows reached those
instructions, but it establishes:

```text
WINDOWS_LINUX_VIRTUAL_TIMER_CONTRACT = PASS
```

Evidence: `docs/WINDOWS_LINUX_VIRTUAL_TIMER_CONTRACT_2026-09-13.md`.

The immutable Android image was rehashed before apply and after rollback to
`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.
Windows media, BCD, drivers, Android code, ACPI tables and VM topology were
unchanged.  Evidence:
`docs/SYNTHETIC_POST_EBS_P2_1_RETRY_RUNTIME_2026-09-12.md`.
P3 evidence: `docs/SYNTHETIC_POST_EBS_P3_PHYSICAL_COUNTER_RUNTIME_2026-09-12.md`.
P4 evidence: `docs/SYNTHETIC_POST_EBS_P4_PHYSICAL_CVAL_RUNTIME_2026-09-12.md`.
P5 evidence: `docs/SYNTHETIC_POST_EBS_P5_PRE_EBS_CVAL_RUNTIME_2026-09-13.md`.
P6 evidence: `docs/SYNTHETIC_POST_EBS_P6_EXCEPTION_LEVEL_CONTEXT_RUNTIME_2026-09-13.md`.
P7 evidence: `docs/SYNTHETIC_POST_EBS_P7_EL1_VECTOR_RUNTIME_2026-09-13.md`.

## Product WinPE 25H2 source match — 2026-09-13

The official local ARM64 25H2 ISO was read-only mounted and contains the
exact immutable baseline Windows Setup boot chain: `boot.wim`
`A0D1106F85ED9CF449182ED220BCC8480DDC645791D136D9C6AA4880177D48C4`,
`BOOTAA64.EFI` `EFCC88441775A1ECEF644E05A52D7A01DC98388EA4426F133B9132CBE1483A19`,
and BCD `B90EF16B94C3DDA7D76CC39840BEFAA9786887517445C3B6F47EBC806D0AB105`.
Index 2 is ARM64 WindowsPE build `26100.6584`.

```text
PRODUCT_WINPE_25H2_SOURCE_MATCH = PASS
```

The product is already running this official current WinPE payload. Another
Setup-WIM replacement from this ISO would be a byte-identical no-op, so it
cannot advance `WINDOWS_POST_EBS` or `WINPE`. No VM or product image changed.
Evidence: `docs/PRODUCT_WINPE_25H2_SOURCE_MATCH_2026-09-13.md`.

## QEMU ↔ WinAVF platform differential audit — 2026-09-13

The preserved Termux/QEMU Windows control is now treated as a diagnostic
reference, not merely a fallback installer. Its launch contract explicitly
uses `-cpu cortex-a76` and `virtualization=on`; the AVF public API exposes only
one-vCPU or host-topology selection and no CPU model/feature mask. Product
Linux reports a materially newer exposed feature surface, including ECV and
E0PD. This is the one remaining early Windows-relevant platform difference,
but it is not causal proof and cannot be locally A/B tested through a safe
firmware or Windows-media change.

```text
QEMU_AVF_DIFFERENTIAL_AUDIT             = PARTIAL
QEMU_AVF_CPU_FEATURE_PROFILE_DIFFERENCE = CONFIRMED
QEMU_AVF_EL2_EXPOSURE_DIFFERENCE         = CONFIRMED_IN_QEMU / UNKNOWN_IN_PRODUCT_ID_REGS
```

The next evidence-producing action is a diskless QEMU contract capture of
ACPI/EFI-map/CPU ID registers, not another product Windows run. Evidence:
`docs/QEMU_AVF_WINDOWS_PLATFORM_DIFFERENTIAL_AUDIT_2026-09-13.md`.

## GICv3 post-EBS handoff static audit — 2026-09-13

The current ArmGicDxe driver intentionally disables its registered interrupt
sources, CPU interface and distributor in its EBS event. P1 demonstrates that
the product platform can re-arm Group-1 delivery and virtual PPI27 after that
teardown. Read-only disassembly of the exact WinPE kernel shows Windows owns a
full GICv3 initialization path (`ICC_SRE_EL1`, `ICC_PMR_EL1`,
`ICC_BPR1_EL1`, `ICC_IGRPEN1_EL1`). There is no evidence-based reason to
retain firmware GIC state for the OS.

```text
EDK2_GICV3_EBS_QUIESCENCE          = EXPECTED
POST_EBS_GICV3_REARM_PLATFORM_PATH = PASS
WINDOWS_GICV3_REINIT_CODE_PATH     = PASS (static)
FIRMWARE_GIC_HANDOFF_DEFECT         = NO_CONCRETE_DEFECT_FOUND
```

No VM ran. Evidence: `docs/GICV3_POST_EBS_HANDOFF_STATIC_AUDIT_2026-09-13.md`.

## EL1 translation handoff static audit — 2026-09-13

The exact WinPE `winload.efi` and `ntoskrnl.exe` directly install their own
EL1 translation and exception state (`TTBR0/1`, `TCR`, `MAIR`, `SCTLR`,
`VBAR`, and DAIF control). Existing EDK2 `ArmConfigureMmu()` is not usable as
a post-EBS probe because it writes live TCR and allocates via Boot Services;
a valid replacement probe would be a new MMU subsystem. No firmware table
handoff change is justified.

```text
WINDOWS_EL1_TRANSLATION_OWNERSHIP_CODE = PASS (static)
FIRMWARE_STALE_TRANSLATION_DIRECT_FIX  = NOT_SUPPORTED_BY_EVIDENCE
POST_EBS_MMU_SWITCH_PROBE_USING_EDK2   = NOT_MINIMAL
```

No VM ran. Evidence: `docs/EL1_TRANSLATION_HANDOFF_STATIC_AUDIT_2026-09-13.md`.

## Windows HVC/PSCI/IORT static audit — 2026-09-13

The residual platform-description branch is now closed without another VM
run. The exact product XSDT has FACP, GTDT, MADT, SPCR, three SSDTs and DBG2;
it has no IORT or MCFG. This is expected for the current FDT topology: the
Kvmtool configuration manager removes MCFG, PCI SSDT and IORT together when
the dynamic FDT repository has no PCI config-space object. IORT/MCFG cannot be
a Windows consumer-side cause when neither table is installed.

The same actual FDT says PSCI `method=hvc`, and FADT `armboot=0x0003` reports
the matching PSCI/HVC flags. The direct Linux ACPI control used that contract
to userland. Exact Windows `winload.efi` and `ntoskrnl.exe` contain standard
generic HVC/SMC and PSCI support wrappers, but static presence does not expose
the runtime branch and is not a defect.

```text
PRODUCT_IORT_MCFG_BOOT_CONTRACT   = NOT_APPLICABLE
PRODUCT_PSCI_HVC_CONTRACT         = PASS
LINUX_ACPI_PSCI_HVC_RUNTIME       = PASS
WINDOWS_HVC_RUNTIME_BRANCH        = UNKNOWN
HVC_PSCI_AS_CONCRETE_WINDOWS_CAUSE = NOT_PROVEN
VALID_HVC_OR_IORT_FIRMWARE_A_B    = NOT_IDENTIFIED
```

Do not invent IORT/MCFG or alter the PSCI conduit. No Windows media, BCD,
firmware, Android code, ACPI table or tablet image was modified. Evidence:
`docs/WINDOWS_HVC_PSCI_IORT_STATIC_AUDIT_2026-09-13.md`.

The GTDT audit still vetoes random one-field workarounds: the non-secure EL1
physical GSIV is not an optional availability signal and `Always-on` does not
redirect it. Stock AVF exposes no architectural-timer configuration and the
relevant GZVM tracing is privileged. The later exact-Windows instruction audit
has closed physical PPI30 as the direct cause, so do not repeat P2–P6 or make
arbitrary ACPI changes.

## Public GenieZone timer-interface audit — 2026-09-13

Public MediaTek/Android GZVM source now identifies the host-to-EL2 boundary
precisely. `GZVM_SET_ONE_REG` forwards a guest system-register write as
`MT_HVC_GZVM_SET_ONE_REG`, while the public GZVM vCPU ioctl intentionally
returns `-EOPNOTSUPP` for `GZVM_GET_ONE_REG`. The public driver has explicit
virtual-timer machinery but no public `CNTP_CVAL_EL0` retention/comparator
implementation. Therefore P4's guest-visible CVAL failure is beyond crosvm
and the public driver: its concrete owner is the closed GenieZone EL2 vCPU
timer context. No non-root local repair interface exists.

```text
PUBLIC_GZVM_TIMER_OWNER_PATH     = PASS
GUEST_CNTP_CVAL_EL2_WRITE_PATH   = IDENTIFIED
LOCAL_GZVM_CNTP_CVAL_REPAIR      = NOT_EXPOSED
```

Evidence: `docs/GENIEZONE_PUBLIC_TIMER_INTERFACE_AUDIT_2026-09-13.md`.

Read-only device and public-source audit following P3 strengthens the owner
boundary, but not the Windows causal claim.  The shipping tablet has live
`gzvm` and `timer_mediatek` modules, exposes no architectural-timer control or
trace endpoint to a normal user, and the public GenieZone implementation
documents vtimer migration/maintenance without an equivalent physical-timer
path.  The only responsible layer left after P3's guest-side proof is
GenieZone EL2/GZVM timer context, but the proprietary EL2 implementation is
not inspectable here.  See
`docs/GENIEZONE_P3_PHYSICAL_TIMER_OWNER_EVIDENCE_2026-09-12.md`.

## ARM Generic Timer platform contract — 2026-09-12

P2.1 now establishes a platform-level failure independently of Windows: the
product VM advertises the non-secure EL1 physical timer (`CNTP_*`, PPI30) in
the same FDT→GTDT path it gives its guest, yet a five-second virtual PPI27
watchdog fires while `CNTP_CTL_EL0.ISTATUS` remains clear.  The physical timer
does not even become pending, so this is not merely an unobserved GIC interrupt
or `WFI` wake.

```text
ARM_GENERIC_TIMER_PLATFORM_CONTRACT = FAIL
WINDOWS_BLOCKER_PHYSICAL_TIMER      = STRONG_HYPOTHESIS
WINDOWS_EARLY_TIMER_SELECTION       = UNKNOWN
VALID_WINDOWS_TIMER_SELECTION_A_B   = NOT_IDENTIFIED
```

Linux's successful post-EBS path used `CNTV`/PPI27, so it neither proves nor
disproves Windows' use of PPI30.  Microsoft documents GTDT-backed Arm Generic
Timer support but does not publish Windows ARM64's early HAL selection logic.
The result forbids an arbitrary GTDT rewrite: PPI remapping would make the
description false rather than redirect `CNTP_*`.  See
`docs/ARM_GENERIC_TIMER_PLATFORM_CONTRACT_2026-09-12.md`.

## Windows ARM64 timer instruction audit — 2026-09-13

The prior Windows causal inference is now closed for the exact WinPE kernel in
the product baseline.  Read-only extraction and ARM64 disassembly of
`Windows 11 26100.6584` from the unmodified baseline WIM found two guest
virtual-comparator writes (`MSR CNTV_CVAL_EL0`) and one virtual-control write
(`MSR CNTV_CTL_EL0`) in `ntoskrnl.exe`, with **zero** writes to
`CNTP_CVAL_EL0`, `CNTP_CTL_EL0`, or `CNTP_TVAL_EL0`.  The extracted
`winload.efi` and `hal.dll` also have zero physical-comparator writes.

The kernel does read `CNTPCT_EL0` in a small number of paths, but a counter
read cannot arm the failed physical comparator.  Therefore P2–P5 are a real
GenieZone platform-contract failure but not a direct explanation of this
WinPE's post-EBS stall via `CNTP_*` comparator programming.

```text
WINDOWS_ARM64_VIRTUAL_TIMER_CODE_PATH = PASS (static)
WINDOWS_PHYSICAL_TIMER_DIRECT_CAUSE   = REFUTED_FOR_CURRENT_WINPE_BUILD
ARM_GENERIC_TIMER_PLATFORM_CONTRACT   = FAIL (independent platform defect)
```

No VM ran, and no media, BCD, firmware, Android app or baseline byte changed.
Evidence: `docs/WINDOWS_ARM64_TIMER_INSTRUCTION_AUDIT_2026-09-13.md`.

## Linux ACPI-only product control — 2026-09-11

One bounded independent Debian ARM64 control ran on the exact product
AVF/GenieZone topology. It used EDK2 → Debian GRUB → Debian kernel with
`acpi=force`, the known `ttyS0`, and explicit 16550 earlycon; Windows BCD and
WIM were untouched. Offline FAT, ARM64 PE, GRUB command line, and source asset
checks passed.

EDK2 successfully started Debian `BOOTAA64.EFI` and emitted the established
raw post-EBS `ER` boundary. Serial stayed silent for the full 100-second capture:
no GRUB/Linux/ACPI/earlycon record occurred. This is not a direct kernel-PC
observer, so kernel entry remains unknown; it does invalidate treating the
silence as proven WinPE/Setup-only. The 24-range overlay was rolled back and
the Android external baseline rehashed exact.

```text
LINUX_ACPI_ONLY_CANDIDATE      = PASS
LINUX_ACPI_ONLY_RUNTIME        = NOT_CONFIRMED
LINUX_KERNEL_EARLY_SERIAL      = NOT_OBSERVED
COMMON_POST_EBS_SERIAL_SILENCE = OBSERVED
PRODUCT_BASELINE_RESTORED      = PASS
```

Evidence: `docs/LINUX_ACPI_ONLY_RUNTIME_2026-09-11.md`.

## Linux direct EFI-stub v3 earlycon correction — prepared 2026-09-11

The v2 direct EFI-stub run reached the Linux stub's own successful
ExitBootServices return but printed no native Linux line.  A focused static
comparison found that the v2 option selected 32-bit `uart8250,mmio32`, while
the actual EDK2 serial implementation uses 8-bit MMIO registers at `0x3f8`
with stride 1.  The direct launcher was rebuilt as a module only with the
sole option change to `earlycon=uart8250,mmio,0x3f8`; no FD, Windows media,
Android app, or tablet image changed.

```text
LINUX_EARLYCON_ACCESSOR_CONTRACT = MISMATCH_CONFIRMED
LINUX_EFI_STUB_V3_LAUNCHER_BUILD = PASS
LINUX_EFI_STUB_V3_OFFLINE_MEDIA  = PASS
LINUX_EFI_STUB_V3_RUNTIME        = PASS
LINUX_NATIVE_KERNEL_POST_EBS     = PASS
LINUX_USERSPACE_INSTALLER        = PASS
POST_EBS_PLATFORM_FOR_LINUX      = PASS
```

The new launcher is `20,480` bytes, ARM64 PE, SHA-256
`F3FEE2904150D53D309D220D3BF4D0F7597B81B63964A496DAB0A51D8B5C1DEE`.
Its isolated preparation entry point is
`tools/linux-efi-stub-control/prepare-linux-efi-stub-control-v3-mmio8.ps1`.
The one-run evidence and exact rollback proof are in
`docs/LINUX_EFI_STUB_V3_MMIO8_RUNTIME_2026-09-11.md`.

## Product KD gated control and next-session handoff — 2026-09-09

The app-owned bridge is proven binary-transparent end-to-end at `ttyS0/0x3f8`.
The first product KD run was deliberately invalidated by a real preboot serial
collision: KD sync bytes interrupted U-Boot.  A second, gated control withheld
all KD process creation and host TX until `Loading files...`; that text is not
visible on the product raw UART, so KD was never released.  The control still
reached the late `IMAGE_AUDIT start enter` pre-EBS marker and stopped only five
bytes before historical r11 `BES` due the host gate timeout.  It is not an EBS
or KD failure.

```text
APP_OWNED_BINARY_TRANSPARENT_COM1 = PASS
GATED_NO_PREBOOT_KD_TX            = PASS
LOADING_FILES_UART_MARKER         = NOT_OBSERVED
WINDOWS_KD_HANDSHAKE              = NOT_TESTED_BY_GATED_RUN
PRODUCT_BASELINE_RESTORED         = PASS
```

The live Android external image and the private runtime after rollback both
hash exactly to `2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`;
there are no running VMs and `hidden_api_policy=null`.  The only proposed
future run, requiring fresh authorization, uses the existing BCD+r11 combined
patch `4F2EA0...B93742E6` and gates KD on the real raw marker
`IMAGE_AUDIT start enter`.  Never start KD before that marker and never retry
the invisible `Loading files...` gate.  Full handoff, commands, build
environment, recovery sequence, and artifact map:
`docs/NEXT_SESSION_HANDOFF_PRODUCT_KD_2026-09-09.md`.

### `IMAGE_AUDIT` gate control update

One later authorized control proved `IMAGE_AUDIT start enter` is a real,
observable post-U-Boot serial gate.  The host bridge then failed locally at an
invalid `.NET NetworkStream.ReadTimeout=0` assignment before it created
`kd.exe` or transmitted any debugger byte.  The bridge now uses
`System.Threading.Timeout.Infinite`; no repeat was made.  Transactional
rollback, external baseline SHA, no-running-VM state, and hidden policy
restoration all pass.

```text
IMAGE_AUDIT_GATE_OBSERVED = PASS
GATED_NO_PREBOOT_KD_TX    = PASS
WINDOWS_KD_HANDSHAKE      = NOT_TESTED
```

Evidence: `docs/PRODUCT_APP_OWNED_KD_IMAGE_AUDIT_GATE_RUNTIME_2026-09-09.md`.

### Bridge readiness control update

The subsequent host invocation ended before Android token authentication: the
one-shot app listener had only just written `state=LISTENING` when the host
attempted its connection.  No VM was created and no private-image patch was
active.  The host bridge now waits for that durable readiness record before it
makes its one client connection.  Baseline/no-VM/policy checks remain exact.
Evidence: `docs/PRODUCT_APP_OWNED_KD_BRIDGE_READINESS_CONTROL_2026-09-09.md`.

### Product KD at the real image-audit gate

The repaired bridge has now had its one product runtime.  It observed the real
`IMAGE_AUDIT start enter` marker, then safely opened KD and sent 450 raw KD
bytes after U-Boot.  `kd.exe` received no target packet; raw output contained
no complete `BES` and stopped after the final two `CONVERT_AUDIT` lines.
Rollback and baseline verification passed.  This is a valid negative KD
handshake result, not a reason to vary BCD or firmware blindly.

```text
IMAGE_AUDIT_GATE_OBSERVED      = PASS
GATED_NO_PREBOOT_KD_TX         = PASS
WINDOWS_KD_HANDSHAKE           = NOT_OBSERVED
EXIT_BOOT_SERVICES_THIS_KD_RUN = NOT_OBSERVED
WINDOWS_KERNEL_ENTRY           = UNKNOWN
```

Evidence and the required next read-only semantics audit:
`docs/PRODUCT_APP_OWNED_KD_IMAGE_AUDIT_RUNTIME_2026-09-09.md`.

### Product KD BCD phase-semantics audit

The read-only audit is complete. `{default}.bootdebug` and
`{default}.debug`, with inherited COM1/115200 settings, configure Winload and
kernel KD. `{bootmgr}.bootdebug` is intentionally absent, so no target reply
was required at the early EDK2 `IMAGE_AUDIT` release marker. A prior
bootmgr-enabled A/B changed Boot Manager behaviour and still had no handshake;
the BCD/KD variant branch is closed.

```text
SERIAL_KD_BCD_SETTINGS         = PASS
EXPECT_KD_REPLY_AT_IMAGE_AUDIT = NO
BCD_KD_VARIANT_BRANCH          = CLOSED
WINDOWS_KERNEL_ENTRY           = UNKNOWN
```

Evidence: `docs/PRODUCT_KD_BCD_PHASE_SEMANTICS_AUDIT_2026-09-09.md`.

## Current authoritative graphics state — 2026-09-06

The r4 host environment has now been replayed successfully and used to build
r5. `R4_BUILD_ENV_REPLAY = PASS` and `NEW_GOP_FD_BUILD = PASS`.

The r5 FD is archived as
`firmware-work/edk2/artifacts/KVMTOOL_EFI-gop-r5-diagnostic.fd` (2,097,152
bytes, SHA-256
`3EAFC9C2086ADCF0375AA14A947E482CB86BE6A1D2AAA105C974DBB2671EFF00`).
It differs from r4 only in the bounded diagnostic second GOP/WAVF capture.

One transactional, firmware-only runtime run produced two CRC-valid 320×200
WAVF records. The original sequence-1 GOP capture remains black; the r5
sequence-2 GOP diagnostic capture has B=0..255, G=0..255, R=128 and visibly
rendered in the real Android `SurfaceView`. The launcher then rolled the one
2 MiB range back and verified the immutable runtime baseline.

```text
REAL_GOP_NONBLACK_FRAME = PASS
GRAPHICAL_UEFI_VISIBLE_IN_APP = PASS
```

The authoritative runtime evidence and exact hashes are in
`docs/GOP_R5_DIAGNOSTIC_RUNTIME_2026-09-06.md`. Older sections below describe
the earlier r4 black-frame state and superseded replay blocker; they are kept
as historical evidence only.

### r6 real BDS UI capture

r6 uses the standard `BootLogoUpdateProgress()` drawing primitive and captures
the centered GOP area as WAVF sequence 3. The tablet SurfaceView visibly showed
the TianoCore logo. Its raw capture has CRC `0xC92231D9` PASS, 65 distinct
colors, and 11,194 non-black pixels; firmware separately logged successful
BDS draw and centered capture. The firmware-only patch was immediately rolled
back and verified.

```text
GOP_BDS_PROGRESS_DRAW = PASS
REAL_GOP_BDS_CENTER_CAPTURE = PASS
GRAPHICAL_UEFI_VISIBLE_IN_APP = PASS
```

See `docs/GOP_R6_BDS_CENTER_RUNTIME_2026-09-06.md`.

### r7 repeated BDS capture sequence

r7 made three standard `BootLogoUpdateProgress()` calls (0, 50, 100), each
followed by a centered GOP/WAVF capture. All progress and capture status
markers were `Success`; sequences 3, 4, and 5 arrived with CRC PASS. The
three payloads were byte-identical because the already-proven 320×200 centered
crop contains the TianoCore logo but excludes the BDS progress-bar region.
The TianoCore logo remained visibly rendered in the real Android SurfaceView.
The single one-range firmware patch was immediately rolled back and the full
immutable runtime SHA was revalidated.

```text
PRE_EBS_REPEATED_FRAME_TRANSPORT = PASS
BDS_PROGRESS_FRAME_SEQUENCE = NOT_CONFIRMED
BDS_PROGRESS_VISUAL_DELTA = NOT_CONFIRMED (center crop unchanged)
GRAPHICAL_UEFI_VISIBLE_IN_APP = PASS
```

Exact evidence and hashes: `docs/GOP_R7_BDS_PROGRESS_SEQUENCE_RUNTIME_2026-09-06.md`.

### r8 real BDS visual-delta capture

r8 changed only the BDS capture window to bottom-center, where the standard
BootLogoLib implementation draws its bar. The 0/50/100 progress draws and WAVF
sequences 3/4/5 all returned `Success` and all CRC checks passed. The three
payload SHA-256 values differ, and the real SurfaceView visibly showed the
standard `Press ESCAPE for boot options` text with the fully drawn BDS bar.
The firmware-only one-range patch was immediately rolled back and the full
immutable runtime baseline SHA was revalidated.

```text
BDS_PROGRESS_FRAME_SEQUENCE = PASS
BDS_PROGRESS_VISUAL_DELTA = PASS
GRAPHICAL_UEFI_VISIBLE_IN_APP = PASS
```

Exact evidence and hashes: `docs/GOP_R8_BDS_PROGRESS_VISUAL_DELTA_RUNTIME_2026-09-06.md`.

### Fullscreen renderer launcher UI

The installed WinAVF launcher now uses a fullscreen, edge-to-edge
`FrameSurfaceView` as its root content. The obsolete Start/Rollback/AVF-audit
buttons and scrolling serial panel were removed. The short status overlay is
automatically hidden when the first WAVF frame arrives, so the guest image is
never covered. Existing automation remains explicit-intent based:
`--ez start true`, `--ez rollback true`, and audit extras are unchanged.
The UI-only synthetic frame test showed an unobscured full-screen frame; no VM
or guest media was accessed by that test.

## Pre-EBS GOP → WAVF raw-console runtime PoC — 2026-09-06

`EXPERIMENTAL_GOP_FD_CANDIDATE = PASS`: the completed r4 FD is
`firmware-work/edk2/Build/ArmVirtKvmTool-AARCH64/DEBUG_GCC5/FV/KVMTOOL_EFI.fd`
(2,097,152 bytes, SHA-256
`8BB42784791B6618D599EF7552C2CF4DFBA61AE5E07B7F08AC76B3F01F48392C`).
The r4 build log ends in `- Done -`; its BdsDxe and FVMAIN contain the
`AVF_GOP_FRAME` marker.  Its reproducibility in the restored historical host
environment is still separate work; therefore `NEW_GOP_FD_BUILD` is not yet
claimed PASS.  See `docs/GOP_FD_CANDIDATE_AUDIT_2026-09-06.md`.

One isolated tablet run used no WIM/BCD/GPT/FAT changes.  The installed legacy
WinAVF launcher applied a one-range `WAVFPAT1` firmware bundle only to the
existing equal-size `\\EFI\\EDK2\\QEMU_EFI.fd` range at raw offset
`7250927616` (2,097,152 bytes), then its own range read-back validation
started the VM.  Before application, tablet media SHA-256 exactly matched the
immutable baseline `2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.
The bundle SHA-256 was
`8A90A63D1B80F28F25DC6011878F58EA66DD1945348577D6AB9854873F2F4179`.

The retained raw console capture
`build-logs/gop-poc-20260906/runtime-r4-raw-serial.log` (266,253 bytes,
SHA-256 `B9B9C32EE188F96A22B61C8A7F71A877FE6A2D07F14503BDA4FB4384187992C2`)
contains a `WAVF` binary header at byte offset 4745 and the exact firmware
marker `AVF_GOP_FRAME status=Success size=320x200`.  This proves
`GOP_WAVF_RAW_CONSOLE_EXPORT = PASS`: GOP capture and binary output reach the
Android app's captured `console_out` pipe before EBS.

The first legacy APK had no decoder, but a controlled same-certificate update
preserved the known-good launcher contract and added the decoder/SurfaceView.
Its UI-only synthetic WAVF audit visibly rendered a gradient in the tablet
SurfaceView, so `SURFACEVIEW_PRESENTATION = PASS`.  A second one-run firmware
test had a CRC-valid 320x200 payload but all 64,000 pixels were BGRA
`00 00 00 FF`; the black SurfaceView is thus correct, and
`REAL_GOP_FRAME_VISIBILITY = BLOCKED_BY_BLACK_SOURCE_FRAME`.  After each
runtime test the launcher reported verified rollback and a separate tablet
SHA-256 check again matched the immutable baseline.  Full procedure/evidence is in
`docs/GOP_WAVF_RUNTIME_POC_2026-09-06.md`.

## r4 build forensic replay — 2026-09-06

The r4 success used `BaseTools\\Bin\\Win64`, bundled llvm-mingw Python, GCC
15.2, MSYS `mingw32-make`, `cmd.exe`, and a wrapper pair visible in its log:
`objecho.cmd` plus `iasl-msys.cmd`. All Win64 packaging binaries remain on
disk. The replay reaches IASL with the recorded environment, but the currently
saved IASL wrapper was written after r4 and loses its `-pC:\\...` path when
invoking POSIX IASL. The r4 path-preserving wrapper implementation is absent.

`R4_BUILD_ENV_REPLAY = BLOCKED_BY_MISSING_R4_IASL_WRAPPER_SEMANTICS`. No r5 FD
was built; the source-only diagnostic GOP gradient remains staged, while the
r4 FD and tablet baseline remain unchanged.

A follow-up source-only GOP diagnostic frame (gradient drawn through GOP Blt,
then restored) is staged but has **no new FD**.  The attempted controlled
historical build stopped before packaging on the known generated-make
`"echo"`/`cmd.exe` incompatibility (`EnglishDxe`, `FdtClientDxe`, `SerialDxe`,
and `CpuMmio2Dxe` fan-out).  No tablet media was changed.  Do not treat this
as a firmware regression or attempt generic build-environment repair without a
bounded exact historical-build plan.

## Terminal/VmLauncher reverse engineering — 2026-09-05

Absolute project priority is now the graphical product path. The installed
Samsung Terminal APK was inspected together with framework and crosvm source.
The exact result is `TERMINAL_DISPLAY_PATH = HARD_BLOCKED_BY_EXACT_PLATFORM_PRIVILEGE`:
`DisplayActivity` and `VmLauncherService` are non-exported, the service is not
bindable (`onBind() = null`), and display uses hidden
`IVirtualizationServiceInternal.waitDisplayService()` plus
`ICrosvmAndroidDisplayService.setSurface()`. The custom app is `untrusted_app`
and cannot find the `virtualizationservice` Binder under the device SELinux
policy. Full evidence is in
`docs/TERMINAL_VM_LAUNCHER_REVERSE_2026-09-05.md`.

`TERMINAL_DISPLAY_BRIDGE` is not reusable for an external VM and
`TERMINAL_VM_HOST` is not reusable with our disk/ISO/kernel. The normal-app
channels confirmed in source are captured `console_out`, input sockets, and
public `connectVsock()`; no Surface, dma-buf, guest-RAM, or crosvm resource
export exists in the public framework. The graphics workstream therefore moves
to a GOP-frame-over-console PoC, then a production-signed WinPE vsock relay.
Post-EBS Windows diagnostics are not the active path and should remain closed.
See the new report for the ranked implementation path.

The first frame transport is now specified and its bounded Android decoder is
implemented in `android-app/src/com/example/winavf/ConsoleFrameDecoder.java`.
It uses a 28-byte `WAVF` header, CRC32, sequence numbers, one BGRA keyframe,
and compressed/raw tile updates. Raw 1280x800 at 30 FPS would require about
117 MiB/s, so the product targets a 10 FPS installation cap and 30 FPS
interactive cap with dirty-tile updates. No firmware, Windows image, or tablet
runtime state was changed. See `docs/CONSOLE_FRAME_TRANSPORT_2026-09-05.md`.

The decoder is wired into the existing `getConsoleOutput()` reader and presents
decoded BGRA frames on the app-owned `FrameSurfaceView`. A host-side synthetic
keyframe plus tile stream passes (`ConsoleFrameDecoderSelfTest PASS frames=2`).
Firmware emission is still pending, so no claim of tablet-visible frames or
measured FPS is made yet. A one-shot firmware encoder is now present in
`firmware/PlatformBootManagerLib/PlatformBm.c`: it captures a bounded 320x200
GOP region, emits a `WAVF` keyframe with CRC over `SerialPortWrite`, and logs
`AVF_GOP_FRAME`. It is offline source work only; firmware build/runtime proof
is still pending. The actual EDK2 module source is also patched in
`firmware-work/edk2/ArmPkg/Library/PlatformBootManagerLib/PlatformBm.c`; its
generated `PlatformBm.obj` now compiles successfully with the workspace LLVM
toolchain. Full FV/FD linking was attempted with local reversible tool shims and
reached the generated FFS stage, but this checkout lacks a coherent Windows
EDK2 BaseTools set (`iasl` is Unix-only and VFR/GenSec tooling does not complete
the host rules). The existing runtime FD was not replaced and no tablet
runtime claim is made.

## Astra first pass — 2026-09-05

The current strongest proven Windows boundary remains the post-return EDK2
`ER` marker after `ExitBootServices`. Kernel phase 0/1, SYSTEM hive
consumption, BOOT_START acceptance, SMSS, `wpeinit`, `startnet.cmd`, and WinPE
userland remain unconfirmed; absent reboot/marker effects are non-diagnostic.
The standard artifact audit found no conforming phase-specific signal under the
current constraints. `BOOTSTAT.DAT` is loader-side only; `ntbtlog` is not a
durable sink in the present RAM-backed WinPE; crash/recovery side effects are
ambiguous or destructive; KD/bootdebug is the only high-resolution candidate,
but AVF lacks writable console input. Therefore no new runtime patch is
justified until duplex console capability is proven. See
`docs/ASTRA_FIRST_PASS_2026-09-05.md`.

`NATIVE_AVF_SCANOUT` remains `BLOCKED_BY_SPECIFIC_PERMISSION`: the privileged
Terminal display Binder exists, while the custom app is `untrusted_app` and
cannot discover it. The product-side viable architecture remains a WinPE
production-signed `viogpudo`/`viosock` relay over public vsock to an app-owned
Surface, but its runtime binding is blocked by the unconfirmed WinPE boundary.

Graphics priority update: crosvm's `console_out` is a raw app-readable pipe,
separate from the privileged Android display service. The fastest safe visible
UEFI candidate is a bounded single GOP frame encoded by firmware and sent
before EBS over existing console output, then decoded into an app-owned
SurfaceView. This is a pre-EBS fallback, not native scanout and not a post-EBS
relay. It is not implemented or run yet; the next PoC must use a throwaway VM
name, bounded payload/CRC, and preserve the immutable Windows baseline. Full
path ranking is in `docs/ASTRA_FIRST_PASS_2026-09-05.md`.

## Preserved boot milestones

- `EXIT_BOOT_SERVICES_RETURN = PASS`
- `MODIFIED_WIM_EBS = PASS`
- `MODIFIED_WIM_TRANSPORT = PASS`
- Immutable runtime baseline SHA-256: `2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.

## WinPE PnP reporter run — 2026-09-05

- `SIGNED_RAW_UART_PNP_MARKER = BLOCKED` is closed under the production-signing
  constraint.  No production-signed virtio binary was modified.
- The separate reporter WIM was an append-only copy of the preserved graphics
  candidate.  It added only a CRT-free ARM64 SetupAPI executable and replaced
  `startnet.cmd` with `wpeinit → reporter → wpeutil reboot`.
- Reporter WIM: `629584824` bytes,
  SHA-256 `8A23DE5EA981800DF089E43EB8AC42FD576CF5E2E6E102B17795B89D7436B7CD`.
  It verified with wimlib.  The executable imports only `KERNEL32.dll` and
  `SETUPAPI.dll`, both present in this WinPE image.
- Runtime patch: `12409548` bytes, `13` ranges,
  SHA-256 `2C1CE4632C131E40FC4584BB89DCAFD34C08E3BEFC28E959D500BB5177A62572`.
  Offline FAT1/FAT2, bridge, reconstructed-WIM and rollback checks all passed.
- The one permitted runtime run reached the existing serial boundary `ER`
  after Windows Boot Manager.  Neither persistent `WINPE.TXT` / exported
  userland marker nor the deterministic reboot occurred.

Result: `WINPE_USERLAND = NOT_CONFIRMED`; `WINPE_VIOGPU_BOUND`,
`WINPE_VIOGPU_STARTED`, `WINPE_VSOCK_BOUND`, and `WINPE_VSOCK_STARTED` remain
`NOT_TESTED`.  This is not evidence against either virtio driver.

The transactional patch was rolled back immediately.  The installed app's
visible status confirms: “Small image patch rolled back and the runtime image
was verified.”  See `docs/WINPE_PNP_REPORTER_RUNTIME_2026-09-05.md`.

The graphics audit made no firmware, BCD, FAT, WIM, Windows-media, or runtime-image change.

## Pre-`wpeinit` persistent-marker run — 2026-09-05

This follow-up isolated the launch ordering without changing the signed virtio
drivers or adding the PnP reporter.  It used the preserved graphics candidate
as the source WIM and changed only index 2:

```text
\WinAvf\WinAvfPreWpeinitMarker.exe
\Windows\System32\startnet.cmd = marker → wpeinit → wpeutil reboot
```

The new CRT-free ARM64 marker imports only `KERNEL32.dll`.  Before `wpeinit`,
it searches for `WINSETUP` on FAT32 (never assumes a drive letter and rejects
`X:`), then tries to write both `\WinAvf\pre-wpeinit-pass.txt` and root
`\WINPE.TXT` so the installed launcher can export the latter.

- Marker SHA-256: `B2F9CD593E5B5DBF83C1CBAC0D3CE85DF7915BE518C9437F5075201D522BCF0F`.
- WIM: `629583629` bytes,
  SHA-256 `7C305B8E031E980EA7C87550EE1D8400C71F3F45D318C0E1AE63173733CCE320`;
  `wimlib verify = PASS`.
- Transactional patch: `12407158` bytes, `13` ranges,
  SHA-256 `57FF135345304560EC0756AD8497B6E633EB25FFBB77F553EFEA516A8BBC7464`.
- Read-only audit against immutable baseline passed: `FAT1/FAT2=PASS`,
  `OldTail=82936`, `FirstNewCluster=882627`, reconstructed WIM hash matched,
  directory invariants and rollback simulation passed.

One runtime run was made after rechecking the device baseline hash.  Its
serial log (SHA-256
`BCD8318269495EDA1D29313B2252DD2F0758B6E6F8C3BDE8C2DDA24225B661D6`)
again stopped at the raw `ER` boundary; there was neither an exported
`WINPE.TXT` nor a reboot.  Exact rollback then completed and reverified the
baseline hash.

Result: `STARTNET_PRE_WPEINIT = NOT_CONFIRMED` and
`WPEINIT_COMPLETION = NOT_CONFIRMED`.  The absence of the persistent marker
does **not** prove that `startnet.cmd` was never entered: the `WINSETUP` volume
may not be mounted or writable at that moment.  It does prove only that this
persistent-volume channel did not yield an observable user-mode signal; no
conclusion about `wpeinit`, viogpu, or viosock is justified.

See `docs/WINPE_PRE_WPEINIT_MARKER_RUNTIME_2026-09-05.md`.

## `startnet.cmd → wpeutil reboot` run — 2026-09-05

This was the narrower follow-up to remove the persistent-volume ambiguity.
The exact baseline WIM was copied and index 2 changed only
`\Windows\System32\startnet.cmd` to the 16-byte payload:

```text
wpeutil reboot
```

There was no `wpeinit`, marker, reporter, driver/package, graphics/vsock,
BCD, or firmware change.

- WIM: `624978518` bytes,
  SHA-256 `48C6CD2C5E8845DE028846D9EB882C7E142D92D1948B9A0334F96477A3102D13`;
  `wimlib verify = PASS`.
- Transactional patch: `3187640` bytes, `9` ranges,
  SHA-256 `658291E9538718346F3E94443CA13C14187BF35047B200E6FAFF7403958823DC`.
- Overlay audit passed against immutable baseline: `FAT1/FAT2=PASS`,
  `OldTail=82936`, `FirstNewCluster=2679`, reconstructed WIM hash matched,
  directory invariants and rollback simulation passed.

The device baseline hash matched before staging.  Exactly one VM run was
launched.  It did not reset: crosvm remained alive and serial again stopped at
the raw `ER` boundary.  Saved serial SHA-256:
`10D134C9DB9353BE3DEE5EB90A88E66CAA12E9FA62145B41D93AADF77F8EBAE7`.

Result: `STARTNET_CMD_EXECUTION = NOT_CONFIRMED`.  This makes no claim that
the command interpreter itself cannot run; it establishes that no independent
observable `wpeutil reboot` execution occurred in this boot.  `wpeinit`, PnP,
viogpu, and viosock remain out of scope until this earlier WinPE-user-mode
startup boundary is explained.

Rollback completed immediately and the external baseline hash matched again.
See `docs/WINPE_STARTNET_REBOOT_ONLY_RUNTIME_2026-09-05.md`.

## SMSS BootExecute probe — 2026-09-05

This test moved the observation point before `startnet.cmd`/`wpeinit`.  It
added a CRT-free ARM64 **Native** executable to `\Windows\System32` whose
only import is `ntdll.dll!NtShutdownSystem(ShutdownReboot)`.

Offline SYSTEM inspection found that selected `ControlSet001` had no
`BootExecute` value.  Therefore no existing entry was replaced: a new
`REG_MULTI_SZ` was created with exactly:

```text
WinAvfSmssProbe.exe
```

The extracted target WIM contained `ntdll.dll`; the extracted SYSTEM hive was
hash-identical to the one created by the offline editor.

- Probe: `3072` bytes,
  SHA-256 `4FDC66A77A1C3C53ED21740103CED8354F1D9C6FB3854D529B22B4710C1049F1`.
- WIM: `625622678` bytes,
  SHA-256 `0BD3041AD4F5C3BDE4B7CD0B506EB8B4614EAE4F3BF3DCDA14BF617EA72B9883`;
  `wimlib verify = PASS`.
- Patch: `4477284` bytes, `10` ranges,
  SHA-256 `60F161A29B13496E993DD5DA941B79C54AAC98C82B6642680DDBD87BAE80E39A`.
- Read-only audit against the immutable baseline passed: `FAT1/FAT2=PASS`,
  `OldTail=82936`, `FirstNewCluster=882627`, reconstructed WIM hash matched,
  directory invariants and rollback simulation passed.

After device baseline verification, one VM run was launched.  `ER` was again
the last serial evidence, crosvm remained alive, and no reboot/reset occurred.
The serial SHA-256 is
`84151440EBADAB402D02B705B78916C2FD76B3B5305DBD329FEA807D12958EC8`.

Result: `SMSS_BOOTEXECUTE = NOT_CONFIRMED`.  This does **not** diagnose a
broken SMSS: a Native BootExecute executable can still be declined or not
reached for a pre-SMSS reason.  The confirmed boundary is now:

```text
ExitBootServices / ER = PASS
Windows kernel/HAL early initialization = UNKNOWN
SMSS BootExecute execution = NOT_CONFIRMED
WinPE userland = NOT_CONFIRMED
```

The transactional patch was rolled back immediately and the external baseline
hash matched again.  See `docs/SMSS_BOOTEXECUTE_RUNTIME_2026-09-05.md`.

## BOOT_START load-boundary acceptance preflight — 2026-09-05

No runtime media was built, staged, or run for this branch.  Acceptance was
examined before treating the existing `WinAvfBootProbe` as a kernel boundary
instrument.

The existing custom binary is ARM64 Native (`Type=1`, `Start=0` service plan,
`ImagePath=%SystemRoot%\System32\drivers\WinAvfBootProbe.sys`) and has a
bounded early UART `D/S` marker in `DriverEntry`.  Its SHA-256 is
`7F8E75C440048B60F0E10AFD0BF6DC0330616682A2D252C84FE3387BDE0D5B1B`.
However, it is signed only with the local self-signed certificate
`CN=WinAVF Boot-Start Diagnostic`; `signtool verify /kp` fails because that
root is untrusted.  Its INF names a catalog, but no `.cat` was produced.

The target WIM does contain Microsoft-supplied ARM64 boot-capable binaries
such as `acpi.sys`, `pci.sys`, `disk.sys`, and `Classpnp.sys`.  They have no
separately observable DriverEntry path.  Modifying any production-signed
binary to add one would invalidate its catalog member hash, which is outside
the current secure-boot/no-testsigning scope.

Result:

```text
BOOT_DRIVER_ACCEPTED = UNKNOWN
BOOT_START_PROBE = INCONCLUSIVE_DUE_TO_CI
BOOT_START_LOAD = INCONCLUSIVE
DRIVER_ENTRY = NOT_TESTED
```

No BCD or testsigning change was made.  Do not infer a kernel failure from
the absence of the old `D/S` marker.  The next boundary must be earlier than
SMSS but independently observable without an untrusted kernel component; see
`docs/BOOT_START_ACCEPTANCE_PREFLIGHT_2026-09-05.md`.

## Early Windows observability static research — 2026-09-05

No runtime artifact was created or run.  Read-only inspection of the exact
baseline WinPE found Microsoft-signed ARM64 `winload.efi`, `ntoskrnl.exe`,
`kdcom.dll`, and `kdnet_uart16550.dll`; `signtool verify /kp` passed for each.
Their presence does not solve the actual limitation: the current AVF serial
path is output-only and therefore cannot form a KD/boot-debug endpoint.

Standard paths were audited without changing BCD: KD/bootdebug, EMS,
`bootlog`/`Ntbtlog.txt`, Boot Status Data/`BOOTSTAT.DAT`, crash dumps, and a
configuration-only use of already signed boot-start drivers.  None yields an
independent, phase-specific external signal under the simultaneous constraints
of no BCD changes, no custom trusted kernel code, no signed-binary/catalog
change, and no bidirectional KD transport.

Result:

```text
EARLY_WINDOWS_OBSERVABILITY = BLOCKED
```

The blocker is transport plus policy, not WIM/FAT transport.  Do not build a
new runtime candidate in this branch.  The smallest meaningful next test, if
explicitly authorized, is a temporary BCD boot-debug setup over a genuine
bidirectional AVF console bridge, using existing Microsoft-signed components;
it still requires both currently forbidden capabilities.  See
`docs/EARLY_WINDOWS_OBSERVABILITY_RESEARCH_2026-09-05.md`.

## AVF console / KD transport audit — 2026-09-05

The actual Android 16 `VirtualMachine` runtime on `R52Y9072A2R` was queried
without running a VM.  It exposes `getConsoleOutput(): InputStream`, but no
`getConsoleInput()`, writable console FD, or equivalent Binder API.  A prior
attempt to invoke `getConsoleInput()` ended in `NoSuchMethodException`.

The launcher configures `ttyS0` and requests console-input support, but those
are config flags only: the caller receives no handle with which to inject
guest UART bytes.  Existing guest output identifies the endpoint as 16550-like
`0x3f8`, so it would be a plausible COM1 KD target if input existed.  It does
not.

```text
AVF_SERIAL_BIDIRECTIONAL_BINARY = BLOCKED
KD_HOST_BRIDGE = NOT_CREATED
BCD_BOOTDEBUG = NOT_APPLIED
WINDOWS_KD_HANDSHAKE = NOT_TESTED
```

No BCD, media, firmware, or tablet runtime state changed.  The block is a
missing public/runtime AVF input endpoint, not UTF-8 conversion or buffering.
Raw capability output, preserved old failure evidence, all methods, and replay
commands are in `docs/KD_TRANSPORT_FEASIBILITY_2026-09-05.md` and
`docs/SESSION_HANDOFF_2026-09-05.md`.

## GOP/WAVF experimental FD candidate — 2026-09-06

The finished r4 artifact is retained as a **runtime candidate**, not a
reproduced build:

```text
EXPERIMENTAL_GOP_FD_CANDIDATE = PASS
NEW_GOP_FD_BUILD = NOT_PASS
KVMTOOL_EFI.fd SHA-256 =
8BB42784791B6618D599EF7552C2CF4DFBA61AE5E07B7F08AC76B3F01F48392C
```

The r4 log ends in `- Done -`; the packed FD contains compressed `FVMAIN`.
The uncompressed r4 `FVMAIN.Fv` and linked `BdsDxe.efi` both contain the
UTF-16 `AVF_GOP_FRAME` marker, while the linked LTO object contains
`AvfEmitGopKeyframe`.  This is sufficient offline evidence that the GOP/WAVF
code is packaged.  The exact prior Windows build environment and a guarded
reproduction entry point are recorded in
`docs/GOP_FD_CANDIDATE_AUDIT_2026-09-06.md` and
`docs/EDK2_KNOWN_GOOD_RESTORE_2026-09-06.md`.

## Native AVF scanout audit

**Result: `NATIVE_AVF_SCANOUT = BLOCKED_BY_SPECIFIC_PERMISSION`.**

Android 16 Terminal implements an actual native path:

```text
Terminal DisplayActivity / SurfaceView
  -> IVirtualizationServiceInternal.waitDisplayService()
  -> ICrosvmAndroidDisplayService.setSurface(surface, isCursor)
  -> crosvm Android display backend -> SurfaceFlinger
```

The cursor uses a second `SurfaceView`, `setCursorStream()`, and a `SurfaceControl.Transaction` overlay. This is not VNC or RDP. AOSP passes crosvm `--android-display-service=<config.name>` when `DisplayConfig` is present.

### Device evidence

Fingerprint:

```text
samsung/gts11xx/gts11:16/BP4A.251205.006/X736BXXS6BZF4_OXM6BZF4:user/release-keys
```

- The privileged APEX Terminal is installed at `/apex/com.android.virt/priv-app/VmTerminalApp@BP4A.251205.006/VmTerminalApp.apk` (version 16).
- Its APK contains `ICrosvmAndroidDisplayService`, `IVirtualizationServiceInternal.waitDisplayService`, `setSurface`, `removeSurface`, and `setCursorStream`.
- Terminal is platform-signed/privileged, has hidden-API policy `0`, and declares `usesNonSdkApi=true`.
- The device registers `android.system.virtualizationservice`; its daemon label is `u:r:virtualizationservice:s0`.
- `crosvm` is present in `/apex/com.android.virt/bin/crosvm`, but an enforcing-SELinux shell cannot read its metadata or strings. Feature strings are therefore not claimed from this binary.
- Terminal's `VmLauncherService` configures `gfxstream`, `gfxstream-vulkan`, and `gfxstream-composer` for its graphical path. This proves Terminal expects the platform graphics backend, not every crosvm compile flag.

### Exact custom-app blocker

`com.example.winavf` has both AVF permissions granted:

```text
MANAGE_VIRTUAL_MACHINE = granted
USE_CUSTOM_VIRTUAL_MACHINE = granted
```

It nevertheless runs as `u:r:untrusted_app:s0:...`. Its opt-in read-only audit produced:

```text
BINDER android.system.virtualizationservice=NULL
CLASS ...IVirtualizationServiceInternal=UNAVAILABLE:ClassNotFoundException
CLASS ...ICrosvmAndroidDisplayService=UNAVAILABLE:ClassNotFoundException
```

The service is listed, but `service check android.system.virtualizationservice` from the shell returns `not found`. This is service-manager access control, not an absent daemon. In AOSP, the service has type `virtualization_service`; `virtualizationservice_use(domain)` grants its `service_manager find` and Binder calls. `untrusted_app` receives no such rule, and the service is not `app_api_service`.

Thus the ordinary AVF permissions allow the existing custom VM through the public manager path, but not the private Binder which hands a `Surface` to crosvm. Shipping generated AIDL stubs cannot fix blocked service discovery. The required change is an OEM/platform-signed bridge or a new public, permission-protected per-VM surface API; either is outside the no-root/no-system-change scope.

## Graphics architecture decision

**Selected research result: `EARLY_WINDOWS_DISPLAY_RELAY = VIABLE`.** This
means viable as an architecture for a separately signed, automatically
serviced user-supplied WinPE image; it is not yet a runtime pass.

### Current GOP framebuffer

The active `ArmVirtKvmTool` source explicitly includes `VirtioGpuDxe`. Its
existing local GOP adaptation creates a virtio-gpu 2D resource, allocates its
BGRA backing pages as `EfiReservedMemoryType`, exposes those pages through
`GopMode.FrameBufferBase`, uses
`PixelBlueGreenRedReserved8BitPerColor`, and leaves the virtio device running
from its ExitBootServices callback. The pages are guest RAM; crosvm consumes
them through `RESOURCE_ATTACH_BACKING`, `TRANSFER_TO_HOST_2D`, and `FLUSH`.

This is a valid *guest-side* physical-LFB handoff candidate for Windows Basic
Display. It is not a handle exported to WinAVF. Blob/dma-buf/AHardwareBuffer
exports stay inside crosvm/gfxstream and its Android display backend; the
ordinary app receives neither an FD nor a resource ID. Therefore:

- `HOST_VISIBLE_PERSISTENT_FRAMEBUFFER = BLOCKED_BY_SPECIFIC_PERMISSION`
- `CONTINUOUS_FIRMWARE_TO_WINDOWS_DISPLAY = NOT_YET_PROVEN`

### Rejected short paths

- `SERVICE_VCPU_DISPLAY_RELAY = NOT_A_SHORT_PATH`: AVF exposes only one CPU or
  match-host topology, and Arm crosvm powers off non-boot vCPUs initially. A
  resident CPU would require PSCI bring-up, memory/device exclusion from
  Windows ACPI, independent vsock transport, and cache/device ownership rules.
  It would also not cause Windows to keep using the GOP buffer.
- `POST_EBS_RESIDENT_FIRMWARE_RELAY = NOT_VIABLE`: UEFI runtime code has no
  autonomous scheduler after EBS. Boot-service events/timers are terminated;
  runtime code runs only when Windows invokes a Runtime Service. Baseline also
  established `SetVirtualAddressMap = NOT_CALLED`.

### Product path

The ordinary app can create its own Android `Surface` and has public VM-vsock
APIs, while the Windows virtio driver project ships ARM64 `viogpudo` (a WDDM
display-only driver) and `viosock` packages. WinPE accepts offline driver
packages and runs PnP during its boot. The feasible product shape is therefore
an automatically serviced boot.wim containing a production-signed ARM64
display-only relay plus a vsock producer; WinAVF renders the received dirty
BGRA tiles to its own Surface. It does not depend on the privileged Android
display broker and can become visible during Setup.

Before altering any WIM, the next PoC is an **offline-only** driver-package
preflight: verify a production-signed ARM64 `viogpudo`/`viosock` package and
that its INF matches the already observed `PCI\\VEN_1AF4&DEV_1050` GPU. If it
passes, make one cloned-media PnP acceptance test. No firmware, BCD, FAT, or
baseline image change is authorized by this research result.

## Repository support

`android-app/src/com/example/winavf/MainActivity.java` has an opt-in read-only `display_host_audit` intent/UI action. It creates no VM, waits for no service, and submits no Surface. It records the calling UID, permission grants, service lookup result, and internal class visibility.

## Sources and local evidence

- AOSP Terminal `DisplayProvider.kt`, commit `aa6989ebf936cc865a156de2fb0591500ed6d242`.
- AOSP Virtualization change introducing `DisplayConfig` and `--android-display-service`, commit `ef450795e2acb4e89369680f68f75415bf437427`.
- AOSP Android 16 QPR2 `service_contexts`, `service.te`, and `te_macros` (`virtualizationservice_use`).
- Non-redistributed local Terminal APK: `%LOCALAPPDATA%\Temp\winavf-display-audit\VmTerminalApp.apk`, SHA-256 `CD101C3545CE936ACD01EF36BBA8E0CFFD3D40203E8ADB6E1BD12BD3BA25623E`.
- Read-only custom-app report: `%LOCALAPPDATA%\Temp\winavf-display-audit\native-avf-display-access-audit.txt`.

No APK, Windows image, ISO, WIM, or other proprietary binary is tracked here.

## Pre-OTA snapshot

Read-only device evidence was captured before the pending Samsung reboot. See
`docs/DEVICE_PRE_OTA_SNAPSHOT_2026-09-05.md`. No OTA, system, APEX, firmware,
Windows-media, or VM state was modified.

## EDK2 host-toolchain reconstruction stop - 2026-09-06

The failed host-toolchain reconstruction branch was stopped and its temporary
tracked BaseTools/GenFds/build-rule edits and shim wrappers were rolled back.
The known-good EDK2 configuration is restored: Source/C BaseTools, regular
Python 3.14, GCC5 AARCH64 toolchain, direct ACPICA IASL, default VfrCompile,
and the original GCC5 make/objcopy rules. No Android, Windows, WIM, BCD, or
product decoder files were changed.

The existing firmware and GUI baseline remain preserved with hashes recorded in
`docs/EDK2_KNOWN_GOOD_RESTORE_2026-09-06.md`. `NEW_GOP_FD_BUILD` remains
`NOT_PASS` until the exact known-good command completes in a normal Windows
developer environment; the sandbox attempt stopped at bare `GenFw` lookup.

## GOP diagnostic-frame packaging boundary — 2026-09-06

The r4 FD has one early `AvfEmitGopKeyframe` capture and no timer, event, or
runtime-configurable later-capture trigger. The source-only follow-up adds one
diagnostic gradient GOP `Blt`, emits a second `WAVF` frame, and restores the
original pixels; it is not in r4 and has not been packaged.

The narrow retry that made MSYS `echo.exe` visible resolved quoted `"echo"`,
but exposed MSYS `sh.exe` to `mingw32-make`; its conversion turns a Windows
source path in a GCC response file into `C:Users...`. The edit was reverted.
This is a host make/shell incompatibility, not a firmware-source failure. No
new FD, patch, tablet image change, or runtime test was made. See
`docs/GOP_WAVF_RUNTIME_POC_2026-09-06.md`.

## AVF UEFI input host probe — 2026-09-06

`AVF_UEFI_INPUT_TRANSPORT = BLOCKED`.  The one isolated test enabled the
public `useKeyboard(true)` VM configuration only and attempted to use the
locally discovered AOSP hidden `VirtualMachine.sendKeyEvent(short, boolean)`
for `KEY_ESC`.  On the actual tablet framework that method is absent:
`NoSuchMethodException`.  This is a host API boundary, not evidence about
guest keyboard enumeration or BDS hotkey handling.

The external immutable image was not patched; its SHA-256 was the known-good
`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`
before and after the run.  `AVF_SERIAL_BIDIRECTIONAL_BINARY` remains blocked,
and `GRAPHICAL_UEFI_INPUT = NOT_TESTED`.  Evidence and exact method are in
`docs/AVF_UEFI_INPUT_HOST_PROBE_2026-09-06.md`.

## Post-EBS vsock input preparation — 2026-09-06

The product input route after Windows user-mode starts is now explicitly
`APK → connectVsock(4050) → signed ARM64 viosock → WinAvfInput.exe → SendInput`.
The agent has been built offline as a 4,096-byte ARM64 PE, SHA-256
`CFFDAA8FBDA4BF1B692D9D23A0F3EC28618116E2A95EF2989FECD3617B9FE80F`, with
only `KERNEL32.dll` and `WS2_32.dll` imports. Its first observable is a raw
`WVH1` vsock HELLO; `USER32` is dynamically loaded only on a later key packet.

This is static preparation, not a runtime pass: `WINPE_USERLAND`, viosock
binding, vsock HELLO, Windows input, and WinPE Setup visibility all remain
unconfirmed. No WIM candidate or tablet patch was made. See
`docs/VSOCK_USERMODE_INPUT_DESIGN_2026-09-06.md`.

An offline append-only vsock HELLO WIM candidate is valid but is not staged:
`boot-wim-viogpu-viosock-hello-r1.wim`, SHA-256
`505A774DC101D18A6BE02B63CAE2E6C2037C40662A339D049BD022EDA4D6E9C4`.
Its WIM verify, agent hash and unchanged signed viosock hash pass. Patch
generation stopped safely because the available local raw file is a historical
A3 candidate, not an attested copy of immutable baseline
`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.
No tablet media was changed.

## WinPE viosock HELLO runtime — 2026-09-06

One fully audited patch run started `WinAvfInput.exe` plus `wpeinit` from the
preserved signed-driver WIM. The APK retried public `connectVsock(4050)` for
60 seconds but received `No such device`; no `WVH1` was observed. Serial again
stopped at `ER`. The application rolled back its private image and verified it;
the external baseline SHA is again the immutable `2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.

This is not a viosock driver-failure result: because `WINPE_USERLAND` remains
unobserved, neither agent execution nor PnP binding was established. Therefore
`VSOCK_HELLO = NOT_OBSERVED`, `WINPE_VSOCK_BOUND = NOT_CONFIRMED`, and
`WINDOWS_INPUT_VSOCK = NOT_TESTED`. See
`docs/VSOCK_HELLO_RUNTIME_2026-09-06.md`.

## r9 SetVirtualAddressMap runtime evidence — 2026-09-06

The already completed single r9 runtime was inspected read-only. Its raw serial
log and a byte-identical evidence copy are
`build-logs/runtime-va-r9-20260906/runtime-r9-raw-serial.log` and
`build-logs/runtime-va-r9-20260906/runtime-r9-raw-serial.evidence.log`.
Both match the device `serial.log` SHA-256
`38600F70A9333A5529B3398F25524DB3D972D11C306C97DDCF06924404E696FE`.

The captured stream ends at zero-based byte offsets `1290716..1290718` with
`BES`: wrapper entry, RuntimeDxe ExitBootServices event, then successful return
from the original ExitBootServices. The raw capture writes/flushed bytes before
any text/frame handling; a later read-only device check found the same length
and SHA while the VM remained alive. `VA0` (SetVirtualAddressMap entry) and
`VA1` (VA-change callback) were not observed. Therefore:

```text
POST_EBS_SET_VIRTUAL_ADDRESS_MAP = NOT_OBSERVED
VIRTUAL_ADDRESS_CHANGE            = NOT_OBSERVED
RESULT                            = PASS (negative observation)
```

The r9 FD was `EB023DF0CA527F6E428D5B0CCE87CC67895B4C69443B64661CE7FAD4BD65C346`.
The one-range patch SHA-256 was
`54CC7498867ADFD29AE82320FCEC17FE338B29E69D6E3D9B389E1981DD2AD124`, covering
offset `7250927616`, length `2097152`. The existing launcher rollback then
reported verified success, and an independent full post-rollback SHA-256
returned the immutable baseline
`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.

Full report: `docs/GOP_R9_RUNTIME_VIRTUAL_ADDRESS_MAP_2026-09-06.md`.

## Kernel-entry observer design — 2026-09-07

The r9 result deliberately does not imply that `ntoskrnl` failed to start. A
read-only design audit established that existing EDK2 `StartImage` evidence
proves only `winload.efi` invocation, not its transfer to the ARM64 kernel.
The only stock signed direct observer is KD, but current public AVF lacks the
required inbound COM1 transport. Thus:

```text
WINDOWS_KERNEL_ENTRY_OBSERVER = BLOCKED
WINDOWS_KERNEL_ENTRY          = UNKNOWN
```

No candidate, firmware, media, BCD, or Android code was built or run for this
design pass. Details: `docs/WINDOWS_KERNEL_ENTRY_FORENSIC_DESIGN_2026-09-07.md`.

## Earliest signed post-EBS milestone research — 2026-09-07

Read-only INF/API audit closed the proposed signed-driver alternative under the
current untrusted-AVF API boundary. `viostor` is the earliest concrete ARM64
candidate (exact `PCI\VEN_1AF4&DEV_1042`, signed boot-start service), but its
normal block I/O has no public app-visible completion or trace. `viosock` and
`viogpudo` are demand-start PnP drivers; vsock connection also requires a
later guest listener, and AVF exports neither device lifecycle nor scanout.

```text
EARLIEST_SIGNED_POST_EBS_MILESTONE = BLOCKED
```

No runtime candidate was created or run. This is an observability constraint,
not a driver/kernel failure result. Details:
`docs/EARLIEST_SIGNED_POST_EBS_MILESTONE_RESEARCH_2026-09-07.md`.

## Expanded post-EBS host observability forensic audit — 2026-09-07

Read-only device/API/source audit found no public raw serial RX, crosvm control
socket, device counter or GZVM trace for an ordinary app. It did establish a
separate safe future host-only instrument: `adb shell` Perfetto can capture
standard scheduler, host-block and host-IRQ events while a WinAVF crosvm runs.
This can prove crosvm survival/scheduling and correlate host activity, but
cannot prove `ntoskrnl` entry or a specific virtio request.

```text
POST_EBS_OBSERVABILITY_PATH = AVAILABLE_WITH_SAFE_ONE_RUN_CONFIG
KD_OVER_CURRENT_CROSVM_UART = CONDITIONAL (only if hidden ttyS0 RX becomes public)
```

No VM was run and no runtime artifact was created. Full matrices, FD inventory,
SELinux boundaries and the proposed non-executed trace are in
`docs/POST_EBS_OBSERVABILITY_FORENSIC_2026-09-07.md`.

## Bounded post-EBS Perfetto runtime — r9 — 2026-09-07

One and only one r9 VM launch used the pre-existing FD and one-range reversible
patch unchanged.  Raw serial again reached `BES`, including the proven return
of the original `ExitBootServices()`.  A 90-second host Perfetto trace began
before the VM start intent.  The launch-specific process was
`crosvm_winavf-gop-ebs-r1` PID `16987`; its `crosvm_vcpu0` TID `17000` received
7,485 scheduler slices and 29,915.517 ms CPU in the conservative trace tail
from 60.000 through 89.930 seconds.  That tail begins after the latest host
sample that already contained `BES`, so it is direct host evidence of ongoing
vCPU execution after EBS.

The same trace saw the VM `virtio_blk` worker only through 26.041 seconds;
there was no VM-attributable block-worker activity in the conservative
post-EBS interval.  Global host block traffic cannot be attributed to the VM.

```text
POST_EBS_VCPU_EXECUTION       = PASS
POST_EBS_BLOCK_ACTIVITY       = NOT_OBSERVED (VM-attributable)
POST_EBS_GLOBAL_BLOCK_ACTIVITY = PASS (not attributable)
```

The launcher immediately performed verified rollback; a fresh independent
external SHA-256 exactly matched immutable baseline
`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.  This
does not prove Windows kernel entry or guest forward progress.  Full retained
evidence: `docs/POST_EBS_PERFETTO_R9_RUNTIME_2026-09-07.md`.

## Windows ARM64 early-platform contract audit — 2026-09-07

A read-only comparison of actual r9 FDT observations, r9 ACPI output, the
one-vCPU crosvm launch, upstream ArmVirtKvmTool dynamic FDT parsers, and the
historical eight-vCPU GUI/loader logs found **no concrete ACPI/FDT contract
violation**. The apparent GICR change is topology-correct: `0x3FFD0000 +
0x20000` for one vCPU and `0x3FEF0000 + 0x100000` for eight both end exactly at
the GICD base `0x3FFF0000`. Current MADT has the correct sole enabled
`uid=0/mpidr=0` GICC; GTDT PPIs/flags and FADT HW-reduced/PSCI-HVC flags are
directly derived from the same FDT consumed by firmware before EBS.

The complete static contract is nevertheless not claimed PASS: retained
evidence cannot establish `CNTFRQ_EL0`, actual generic-timer delivery, or the
timer interface selected by Windows. Thus:

```text
WINDOWS_ARM_EARLY_PLATFORM_CONTRACT = INCONCLUSIVE
```

No ACPI/GIC/timer firmware A/B is justified. The next new information requires
a genuine bidirectional KD serial transport or privileged VMM/GZVM tracing.
Full matrix and evidence:
`docs/WINDOWS_ARM_EARLY_PLATFORM_CONTRACT_AUDIT_2026-09-07.md`.

## KVM ftrace post-EBS r9 runtime — 2026-09-07

One new bounded r9 runtime tested KVM ftrace through Perfetto. Raw serial was
byte-identical and again reached `BES`. The WinAVF `crosvm_vcpu0` had 23,635
scheduler slices and 85.312 seconds CPU time, but Trace Processor found zero
`kvm_*`, `vgic_*`, GZVM, or GenieZone packets among 813,164 ftrace events.
Configured KVM tracepoints are therefore not an observable guest-event source
for this unprivileged custom VM.

The launcher immediately rolled back the existing r9 range and displayed
verified rollback; the external immutable image again hashed exactly to
`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.

```text
KVM_FTRACE_PERFETTO_FOR_WINAVF = NOT_USABLE
VMM_GUEST_EVENT_OBSERVER        = BLOCKED
WINDOWS_KERNEL_ENTRY            = UNKNOWN
```

Full raw/decoded trace inventory and analysis:
`docs/KVM_FTRACE_POST_EBS_R9_RUNTIME_2026-09-07.md`.

## ADB-shell AVF console / KD feasibility — 2026-09-07

The production APEX supplies shell CLI `vm`; `vm run` documents distinct
`--console` and `--console-in` FDs for a separate shell-created VM. It does not
export the hidden console-input FD of app-owned WinAVF. `vm console` is a
TTY/`microcom` PTY route, not a proven raw binary bridge. Production
`ro.debuggable=0` disables framework host-console provisioning; the only live
Terminal VM enumerated by shell has `hostConsoleName: None`.

Pre-runtime classification was:

```text
ADB_SHELL_AVF_CONSOLE_RX       = AVAILABLE_ONLY_FOR_SHELL_CREATED_VM
ADB_SHELL_BIDIRECTIONAL_COM1   = CONDITIONAL
SHELL_VM_TOPOLOGY_EQUIVALENT   = PARTIAL
KD_DEBUG_ONLY_PATH             = CONDITIONAL
```

No VM/media was touched. A separately authorized shell-owned raw-byte loopback
is prerequisite to any BCD/KD test and cannot prove WinAVF equivalence absent
FDT/crosvm comparison. The immediately following loopback runtime supersedes
the provisional COM1/KD statuses. Evidence:
`docs/ADB_SHELL_AVF_CONSOLE_KD_FEASIBILITY_2026-09-07.md`.

## Shell serial pipe loopback runtime — 2026-09-07

The first one-shot shell loopback created CID `2098` but supplied a regular
file to `--console-in`; crosvm therefore rejected it at epoll registration.
There was no attributable AVC denial. A second separately authorized
non-Windows loopback changed only that input object to an inherited anonymous
pipe. It created CID `2099`, logged `input=... (pipe:[4923896])`, echoed all
4,096 repeated `00..FF` bytes exactly, requested PSCI shutdown, and returned
`VM_EXIT=0`. The temporary shell directory was removed after evidence capture.

```text
SERIAL_WAIT_CONTEXT_EPERM            = NON_POLLABLE_REGULAR_FILE
AVC_OR_SELINUX_DENIAL                = NOT_OBSERVED
SHELL_RAW_SERIAL_TX                  = PASS
SHELL_RAW_SERIAL_RX                  = PASS
SHELL_BINARY_TRANSPARENT_COM1        = PASS
ADB_SHELL_BIDIRECTIONAL_COM1         = YES (shell-created VM only)
KD_DEBUG_ONLY_PATH                   = AVAILABLE (diagnostic shell-VM path)
KD_HOST_BRIDGE                       = NOT_YET_RUN
```

PTY `vm console` remains unsuitable. The next separately authorized step may
be a diagnostic Windows clone plus a minimal reversible BCD bootdebug test;
its topology must still be compared with WinAVF. Evidence:
`docs/SHELL_SERIAL_PIPE_LOOPBACK_RUNTIME_2026-09-07.md`.

## Shell KD BCD preflight — 2026-09-07

An attested host-only clone of the Android external immutable baseline was
created; its full SHA-256 matched
`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`. An
elevated BCDEdit run configured only a separately extracted BCD copy for
`bootdebug`, kernel `debug`, and serial COM1/115200. It did not enable test
signing or modify any Windows media.

The BCD grew from 16,384 to 20,480 bytes. GNU mtools 4.0.49 was found locally
and used directly against the existing FAT32 ESP at raw byte offset 1,048,576;
this is a standard FAT writer, not a manual allocator. It replaced only
`\EFI\Microsoft\Boot\BCD`, allocated one extra 8,192-byte FAT cluster, and
read back the exact KD BCD SHA. The full clone SHA changed from immutable
baseline `2582...278A7` to host-only KD candidate
`563DA01DB2DADDA202B8475D280E064F0C5C5B60DC07ACA61B6009C38147C196`.
No Android staging or shell Windows VM/KD run occurred.

```text
SHELL_KD_MEDIA_CLONE    = PASS
BCD_BOOTDEBUG_CANDIDATE = PASS
BCD_BOOTDEBUG_INJECTED  = PASS (host-only disposable clone)
WINDOWS_KD_HANDSHAKE    = NOT_TESTED
```

## Shell-owned Windows KD runtime — 2026-09-07

One shell-owned Windows diagnostic VM (CID `2100`) was run with the already
injected disposable KD clone, a local named-pipe `kd.exe` endpoint, and the
same `ttyS0` COM1 configuration used by the successful Android-shell-local
loopback. The Android-staged clone rehashed exactly as
`563DA01DB2DADDA202B8475D280E064F0C5C5B60DC07ACA61B6009C38147C196`.
The shell VM reached U-Boot, EDK2, Windows Boot Manager and `Loading files...`.

However `vm run` recorded `Failed to create wait context. Operation not
permitted (os error 1)` for the serial input descriptor inherited through
`adb exec-out`. KD opened its local named pipe and transmitted synchronizing
bytes, but received no KD packet and remained at `Waiting to reconnect...`.
This does not test Windows KD because crosvm rejected the required COM1 RX
descriptor. The bounded run ended with timeout `124`; CID `2100` is gone and
no second runtime was run. After host evidence extraction, the exact temporary
shell staging directory was removed (`CLEANUP_PASS`).

```text
KD_HOST_NAMED_PIPE          = PASS
SHELL_KD_COM1_RX_AT_RUNTIME = FAILED (wait-context EPERM; FD type unverified)
WINDOWS_KD_HANDSHAKE        = INCONCLUSIVE
WINDOWS_KERNEL_ENTRY        = UNKNOWN
```

Evidence: `docs/SHELL_WINDOWS_KD_RUNTIME_2026-09-07.md`.

## ADB shell COM1 FD audit — 2026-09-07

No VM was launched. A minimal Android ARM64 descriptor helper proved that the
previous KD `EPERM` was caused by the runner's background subshell: Android
`sh` replaced its stdin with `/dev/null`, and `epoll_ctl(ADD)` on that reopened
descriptor returns `EPERM` exactly as crosvm logged.

Direct `adb exec-out` stdin is a pollable PTY. Direct `adb shell -T` stdin is
a pollable socket, but reopening `/proc/self/fd/0` fails with `ENXIO`. AOSP
`vm run` solves that cleanly when `--console-in` is omitted: it duplicates the
actual stdin FD rather than reopening a path. Therefore the correct next
non-Windows validation is foreground `adb shell -T` plus **no**
`--console-in`, not another Windows run.

```text
ADB_SHELL_T_STDIN_POLLABLE        = PASS (socket)
ADB_SHELL_T_VM_DEFAULT_STDIN_PATH = AVAILABLE
KD_PREVIOUS_RX_FAILURE             = BACKGROUND_STDIN_TO_DEV_NULL
WINDOWS_KD_HANDSHAKE               = INCONCLUSIVE (unchanged)
```

Evidence: `docs/ADB_SHELL_COM1_FD_AUDIT_2026-09-07.md`.

## Shell-T text-safe COM1 loopback — 2026-09-07

One further disposable **non-Windows** VM (CID `2102`) used foreground
`adb shell -T` and the default stdin path, with Base64 ASCII decoded into the
raw Android pipe.  The bare guest returned a 4,096-byte `00..FF` payload with
an exact SHA-256 match (`C8F5...EC193`) in the Android raw console file;
crosvm exited normally and did not log a wait-context error.  This validates
the missing host-to-guest raw COM1 path without altering Windows media or the
product baseline.

ADB's live stdout remained text-like and did not return the binary echo as a
raw host stream.  Therefore this is not yet an interactive WinDbg bridge:
the next bounded engineering task is an outbound raw-console-to-Base64 relay,
followed by a disposable loopback decode check.  No second Windows KD run is
authorized or justified before that check.

```text
HOST_TO_GUEST_RAW_COM1       = PASS (text-framed ingress)
GUEST_TO_DEVICE_RAW_COM1     = PASS
HOST_RAW_KD_RETURN_STREAM    = NOT_YET_AVAILABLE
WINDOWS_KD_HANDSHAKE         = INCONCLUSIVE
```

Evidence: `docs/SHELL_T_BASE64_COM1_LOOPBACK_RUNTIME_2026-09-07.md`.

## Full shell-T text-framed COM1 loopback — 2026-09-07

The second half of the diagnostic bridge is now proved.  A disposable
non-Windows loopback carried `00..FF` repeated for 4,096 bytes through a
continuous Base64 ingress pipe and `O:<Base64>` console-file egress records.
The decoded return is byte-exact (`C8F5...EC193`); the final full serial record
is `0C1B...F5597`.  No crosvm wait-context error was present.

Shell SELinux denies `mkfifo` under `/data/local/tmp`; that had created a
regular file and explains the intermediate EPERM exactly.  The final path uses
the source-proven `base64 -di | vm run` anonymous pipe with no
`--console-in` argument.

```text
SHELL_BINARY_TRANSPARENT_COM1 = PASS (text-framed host bridge)
KD_HOST_TRANSPORT_PREREQUISITE = PASS
WINDOWS_KD_HANDSHAKE = INCONCLUSIVE (no repeat yet)
```

Evidence: `docs/SHELL_T_FRAMED_COM1_LOOPBACK_RUNTIME_2026-09-07.md`.

## Shell Windows KD framed runtime — 2026-09-07

One shell-owned disposable Windows KD clone run was made after full framed
COM1 loopback proof.  The staged clone SHA exactly matched
`563DA01D...47C196`.  KD connected to the local named pipe and transmitted
483 synchronization bytes, while the bridge captured 32,679 guest UART bytes.
The VM reached U-Boot, EDK2, Windows Boot Manager and `Loading files...`.

No crosvm EPERM occurred, but no Windows KD packet arrived and WinDbg stayed
at `Waiting to reconnect`.  Thus this is a genuine negative handshake result,
not a host-to-guest RX failure.  The one run timed out; no repeat was made.
The exact temporary Android directory was deleted, and the immutable product
baseline was independently rehashed as
`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.

```text
KD_HOST_NAMED_PIPE            = PASS
SHELL_BINARY_TRANSPARENT_COM1 = PASS
SHELL_KD_COM1_RX_AT_RUNTIME   = PASS
WINDOWS_KD_HANDSHAKE          = NOT_OBSERVED
WINDOWS_KERNEL_ENTRY          = UNKNOWN
```

Evidence: `docs/SHELL_WINDOWS_KD_FRAMED_RUNTIME_2026-09-07.md`.

## Shell KD BCD/serial audit — 2026-09-07

The read-only post-run audit found one concrete omission: the injected BCD
correctly enables `{default}.bootdebug`, `{default}.debug`, and serial COM1
115200 settings, but it does **not** enable `{bootmgr}.bootdebug`.  Microsoft
documents Boot Manager, boot loader, and kernel debugging as separate stages.
The previous non-handshake therefore did not cover the earliest available
Boot Manager KD event, although it still remains a valid negative result for
the actually reached later path.

No runtime or modification occurred.  A single BCD-only disposable A/B that
adds `{bootmgr}.bootdebug` is the next lowest-cost diagnostic, if separately
authorized.

```text
KERNEL_KD_BCD_CONFIGURATION = PASS
BOOTMGR_BOOTDEBUG           = NOT_ENABLED
COM1_MAPPING                = PASS
```

Evidence: `docs/SHELL_KD_BCD_SERIAL_AUDIT_2026-09-07.md`.

## Shell KD Boot Manager BCD A/B — 2026-09-07

One new disposable clone added only `{bootmgr}.bootdebug = Yes`.  It was
offline audited, staged with exact SHA `652EEC53...FA38E7`, and run exactly
once through the already proven framed COM1 bridge (CID `2109`).  The prior
candidate showed Boot Manager UI and `Loading files...`; the new candidate
stopped immediately after `IMAGE_AUDIT start enter` for `BOOTAA64.EFI` and
never emitted `Loading files...`.  KD sent 513 bytes but received no target
packet.

This is a real Boot Manager configuration effect, not an EPERM/transport
failure.  It proves the flag is consumed, but not that the KD protocol starts.
The disposable staging was deleted and immutable product baseline SHA was
reconfirmed exact.

```text
BOOTMGR_BOOTDEBUG_EFFECT = PASS
SHELL_KD_COM1_RX         = PASS
WINDOWS_KD_HANDSHAKE     = NOT_OBSERVED
```

Evidence: `docs/SHELL_WINDOWS_KD_BOOTMGR_AB_RUNTIME_2026-09-07.md`.

Evidence: `docs/SHELL_KD_BCD_PREFLIGHT_2026-09-07.md`.

## ARM64 KD serial platform-contract audit — 2026-09-07

A read-only audit of the current ArmVirtKvmTool source and preserved serial
evidence confirms one coherent UART description: crosvm `ttyS0` is observed
at MMIO `0x3f8`; EDK2 uses the 16550 MMIO library; the FDT `stdout-path`
record flows into SPCR, DBG2 and ACPI `\\_SB_.COM0`.  SPCR uses
`SystemMemory`, while the FDT parser selects Microsoft-recommended 16550 with
GAS subtype `0x12`, byte access and 115200.  No x86-I/O, PL011, baud, or
namespace mismatch was found.

The exact runtime-generated ACPI table bytes and UART interrupt number were
not captured, so this is a source/evidence contract result rather than a
runtime-table dump.  It does not change the completed negative KD handshake.

```text
ARM64_KD_SERIAL_PLATFORM_CONTRACT = PASS
SPCR_DBG2_GAS_MODEL               = PASS
ACTUAL_ACPI_TABLE_BYTES           = NOT_CAPTURED
WINDOWS_KD_HANDSHAKE              = NOT_OBSERVED
```

Evidence: `docs/ARM64_KD_SERIAL_PLATFORM_CONTRACT_AUDIT_2026-09-07.md`.

## Explicit `kdcom.dll` shell KD A/B — 2026-09-08

The final low-risk BCD ambiguity was tested once on a new disposable shell
clone.  The only intended media delta was `{default}.dbgtransport = kdcom.dll`;
`{bootmgr}.bootdebug` remained off.  Offline BCD read-back, ESP audit,
`BOOTAA64.EFI` comparison, and staged raw SHA all passed.  The shell VM
reached `Loading files...`; the byte-exact bridge delivered 513 host KD bytes
and returned 32,678 UART bytes without a crosvm wait-context error.  KD still
received no target packet.

The disposable staging directory was removed, no VM remains, and the
immutable Android baseline was rehashed exactly after cleanup.

```text
EXPLICIT_KDCOM_BCD_CONFIGURATION = PASS
SHELL_KD_COM1_RX_AT_RUNTIME      = PASS
SHELL_WINDOWS_BOOT_PATH          = PASS
WINDOWS_KD_HANDSHAKE             = NOT_OBSERVED
WINDOWS_KERNEL_ENTRY             = UNKNOWN
```

Evidence: `docs/SHELL_WINDOWS_KD_EXPLICIT_KDCOM_RUNTIME_2026-09-08.md`.

One elevated fixed-VHD materialization was attempted with only the raw clone as
input. The VHD was detached by cleanup, no exported KD raw candidate or report
was produced, and no Android staging/Windows VM run followed. Do not repeat the
full copy until bounded elevated error capture is in place.

## GenieZone ftrace post-EBS r9 runtime — 2026-09-07

The final accessible vendor VMM observer was tested once with the unchanged r9
patch.  Android exposes the `geniezone` group and Perfetto accepted
`mtk_vcpu_exit`, `mtk_hypcall_enter`, and `mtk_hypcall_leave`, but the decoded
95-second trace yielded zero such packets.  The actual `crosvm_vcpu0` still
received 24,302 slices / 85.551 seconds CPU, so the negative result is about
the trace provider's visibility, not VM execution or Windows kernel entry.

The immediate launcher rollback was verified and the independent external
baseline SHA-256 exactly matched immutable baseline
`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.

```text
GENIEZONE_FTRACE_PERFETTO_FOR_WINAVF = NOT_USABLE
VMM_GUEST_EVENT_OBSERVER              = BLOCKED
WINDOWS_KERNEL_ENTRY                  = UNKNOWN
```

No repeat with the same provider is justified.  Evidence:
`docs/GENIEZONE_FTRACE_POST_EBS_R9_RUNTIME_2026-09-07.md`.

## r10 installed ACPI serial audit — 2026-09-08

The one authorized r10 firmware-only A/B captured the actual installed ACPI
serial contract before Windows Boot Manager. SPCR is present in the XSDT and
describes the FDT console UART at `0x3F8`; DBG2 is present in the XSDT and
describes a distinct 16550-with-GAS debug UART at `0x2F8` under
`\_SB_.COM0`. This corrects the earlier source-only assumption that both
tables named one UART. The DynamicTables FDT parser intentionally selects
`stdout-path` for the console object and the first non-console serial node for
the debug object.

The r10 run again reached final raw marker `BES`; `VA0/VA1/VA2` were absent.
The launcher rolled back the one 2 MiB firmware range and verified the private
runtime image. The external immutable source image was independently rehashed
before and after rollback as exact baseline
`2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.

```text
INSTALLED_ACPI_SERIAL_TOPOLOGY = PASS
SPCR_CONSOLE_UART              = 0x3F8
DBG2_DEBUG_UART                = 0x2F8
WINDOWS_KD_HANDSHAKE            = NOT_OBSERVED
```

No further KD/Windows run is authorized by this result. The next diagnostic
must first prove whether the shell crosvm path can attach to the second UART
backend byte-for-byte, or separately authorize a one-parameter firmware A/B
that aligns DBG2 with the proven console backend.

Evidence: `docs/R10_ACPI_SERIAL_RUNTIME_2026-09-08.md`.

## Second-UART shell backend audit — 2026-09-08

No second-UART loopback was launched because the available AVF contract cannot
attach one. The actual product crosvm invocation creates `ttyS0` (serial num 1)
with the caller's output and input FDs, then creates `ttyS1` (serial num 2) as
the virtualization service's output-only failure pipe. The retained source
accepts only `ttyS0` or `hvc0` for console input and rejects `ttyS1`; the shell
CLI has no arbitrary `--serial` option.

Together with r10, this establishes that the byte-exact shell bridge reaches
`0x3F8`, whereas Windows DBG2 points at unexposed `0x2F8`. The absence of KD
packets is therefore explainable by serial topology rather than a failed host
RX bridge. No product or shell Windows VM was run for this audit.

```text
SECOND_UART_SHELL_BACKEND = NOT_EXPOSED
SHELL_SECOND_UART_LOOPBACK = BLOCKED
WINDOWS_KD_HANDSHAKE = NOT_OBSERVED
```

The next candidate requires separate authorization: one firmware-only A/B
aligning DBG2 and `COM0._CRS` to the already-proven `0x3F8` console UART, with
SPCR and all Windows/Android artifacts unchanged.

Evidence: `docs/SECOND_UART_SHELL_BACKEND_AUDIT_2026-09-08.md`.

## r11 debug-UART alignment runtime — 2026-09-08

The separately authorized one-parameter firmware A/B is complete.  Kvmtool
now copies the FDT console-port Configuration Manager object into the
serial-debug-port object, without modifying the generic FDT parser.  This
makes DBG2 and the source-proven `\\_SB_.COM0._CRS` fixup describe the same
bidirectional console UART (`0x3F8`) already described by SPCR.

One r11 run recorded the installed records `SP=...03F8` and `DG=...03F8`, then
again reached complete `BES`.  The self-verifying one-range patch was rolled
back; launcher UI and an independent external-image SHA check both confirmed
the exact immutable baseline `2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.

```text
ACPI_DBG2_CONSOLE_ALIGNMENT = PASS
SPCR_CONSOLE_UART           = 0x3F8
DBG2_DEBUG_UART             = 0x3F8
EXIT_BOOT_SERVICES_RETURN   = PASS
WINDOWS_KD_HANDSHAKE        = NOT_TESTED_BY_R11
```

No Windows/KD run was added to this firmware-only A/B.  The next distinct,
authorized test may be one disposable shell-owned Windows KD run using r11
and the existing audited serial-KD BCD candidate.

Evidence: `docs/R11_DEBUG_UART_ALIGNMENT_RUNTIME_2026-09-08.md`.

## Shell Windows KD with r11 UART alignment — 2026-09-08

One disposable shell-owned Windows VM tested the already audited serial KD
BCD candidate with r11’s installed `SPCR=DBG2=0x3F8` mapping.  The exact
framed COM1 bridge again worked: KD connected and sent 513 bytes, while the
guest returned 32,528 raw UART bytes with an independently identical device
capture.  The VM reached Windows Boot Manager and `Loading files...`, but KD
received no target packet before the bounded timeout.

Crucially, this shell clone did not emit `BES` in the time window. The outcome
therefore does not distinguish a post-EBS Windows problem from a shell-clone
timing/topology difference before that boundary. The local clone firmware
range was rolled back, the verified 9-GB shell staging directory was removed,
no VM remained, and the product Android baseline rehashed exactly.

```text
ACPI_DBG2_CONSOLE_ALIGNMENT = PASS
SHELL_KD_COM1_RX_AT_RUNTIME = PASS
SHELL_WINDOWS_BOOT_PATH     = PASS (through Loading files...)
SHELL_EBS_BOUNDARY          = NOT_OBSERVED
WINDOWS_KD_HANDSHAKE        = NOT_OBSERVED
WINDOWS_KERNEL_ENTRY        = UNKNOWN
```

Do not repeat blind KD/BCD variants. Evidence:
`docs/SHELL_WINDOWS_KD_R11_ALIGNMENT_RUNTIME_2026-09-08.md`.

## Shell r11 baseline-BCD control — 2026-09-08

One final bounded shell control removed the serial-KD BCD candidate while
keeping the r11 firmware mapping and shell VM shape.  It still reached only
Windows Boot Manager / `Loading files...` and did not emit `BES` within the
same 100-second window.  The shell diagnostic topology therefore fails to
reproduce the product’s proven EBS boundary independently of KD.

The r11 range was rolled back from the disposable clone, the exact remote
9-GB staging directory was deleted, no VM remained, and the Android product
baseline rehashed exactly to `2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7`.

```text
SHELL_R11_BASELINE_BCD_CONTROL = COMPLETE
SHELL_EBS_CONTROL              = NOT_OBSERVED
SHELL_KD_BCD_CAUSES_PRE_EBS_DIVERGENCE = NO
SHELL_VM_TOPOLOGY_EQUIVALENT   = NO (at EBS)
WINDOWS_KD_HANDSHAKE            = NOT_OBSERVED / non-diagnostic for product
```

Evidence: `docs/SHELL_R11_BASELINE_BCD_CONTROL_RUNTIME_2026-09-08.md`.

## Hidden API console RX and native display probe — 2026-09-09

One bounded, app-owned probe established that Samsung's installed
`VirtualMachine.getConsoleInput()` exists and returns a usable `OutputStream`
when the global hidden-API policy is temporarily `1`.  The setting was restored
to its original absent value, the probe VM was never run and was deleted, and
the product runtime image remained exact.

The same before/after audit did **not** expose the private display service:
`ServiceManager.getService("android.system.virtualizationservice")` stayed
`NULL`, and the required internal AIDL stubs remain Terminal-only.  No raw
Binder call was attempted.

```text
HIDDEN_API_POLICY_BOOTSTRAP       = PASS
PRODUCT_APP_CONSOLE_INPUT_API     = PASS
APP_OWNED_BINARY_CONSOLE_RX       = NOT_TESTED
DISPLAY_SET_SURFACE_DIRECT_APP    = SERVICE_NOT_EXPOSED
DIRECT_APP_NATIVE_CROSVM_DISPLAY  = NOT_CONFIRMED
DISPLAY_ACCESS_GATE               = SELINUX
SHIZUKU_DISPLAY_BROKER            = REQUIRES_VM_HANDLE_TRANSFER
PRODUCT_BASELINE_RESTORED         = PASS
```

Evidence: `docs/HIDDEN_API_RX_AND_NATIVE_DISPLAY_PROBE_2026-09-09.md`.

## App-owned binary console loopback — 2026-09-09

The hidden-API `getConsoleInput()` discovery has now been tested end-to-end
without Windows. A 360-byte ARM64 no-disk echo guest at the product AVF
console (`ttyS0` / `0x3f8`) returned the exact 4,096-byte `00..FF` ×16 pattern
through `getConsoleOutput()`. Both in-app and independent offline SHA-256
checks matched, including NUL, CR/LF, DEL, and high-bit values. The disposable
VM was explicitly stopped and deleted; policy and product media were restored.

```text
APP_OWNED_BINARY_CONSOLE_RX       = PASS
APP_OWNED_BINARY_CONSOLE_TX       = PASS
APP_OWNED_BINARY_TRANSPARENT_COM1 = PASS
PRODUCT_BASELINE_RESTORED         = PASS
```

This makes a product-equivalent serial-KD experiment technically possible, but
that BCD/media-changing runtime is a separate authorization boundary.

Evidence: `docs/APP_OWNED_BINARY_CONSOLE_LOOPBACK_RUNTIME_2026-09-09.md`.

## Product app-owned serial KD runtime — 2026-09-09

One product-equivalent serial-KD run proved the full raw host named-pipe →
Android localhost → `getConsoleInput()/getConsoleOutput()` → guest `ttyS0`
path, but it did not reach Windows. KD synchronization bytes arrived while
U-Boot still accepted serial input and stopped autoboot at `Hit any key to
stop autoboot`.

```text
KD_HOST_NAMED_PIPE       = PASS
PRODUCT_APP_KD_BRIDGE    = PASS
APP_OWNED_BINARY_COM1    = PASS
PREBOOT_SERIAL_COLLISION = PASS
WINDOWS_KD_HANDSHAKE     = INCONCLUSIVE
```

No second VM run occurred. The VM was stopped, transaction rollback restored
both app-private and external baseline SHA `2582CAE4...1278A7`, and hidden API
policy returned to `null`. A future test must gate KD TX until a post-U-Boot
serial marker appears. Evidence: `docs/PRODUCT_APP_OWNED_KD_RUNTIME_2026-09-09.md`.

## Direct post-EBS Windows observability audit — 2026-09-09

An exhaustive read-only audit of AVF, GenieZone/GZVM, Android kernel tracing,
debugfs/sysfs, Perfetto, simpleperf, vendor HAL and AVF CLI endpoints found no
direct guest-state observer for the exact app-owned product VM.

```text
DIRECT_POST_EBS_WINDOWS_OBSERVABILITY = BLOCKED
SHELL_GUEST_GDB_PC_OBSERVER            = BLOCKED
PRODUCT_APP_GDB_PC_OBSERVER             = BLOCKED
```

The host kernel contains relevant KVM entry/exit, WFX, fault, IRQ/vGIC and
timer tracepoints, but the enforcing ADB shell cannot read or enable them.
`/dev/gzvm` exists but SELinux denies even a zero-byte open before an ioctl.
GZVM events are absent from tracefs; Perfetto exposes generic ftrace only.
Samsung's exposed HyPer dump contains host QoS/request data, not guest state.

The documented raw shell crosvm `--gdb` route was tested with a read-only
baseline clone. Samsung VirtMgr rejected it before VM creation as
non-debuggable both with its documented default and with explicit `--debug
full`; it cannot observe guest PC. The product configuration enables debug
full and has both custom-VM permissions, but its installed builder exposes no
GDB-port setter. Do not start another BCD/KD or raw shell-GDB experiment. First
prove a supported product `gdbPort` configuration path.

Evidence: `docs/DIRECT_POST_EBS_WINDOWS_OBSERVABILITY_AUDIT_2026-09-09.md`.
Runtime evidence: `docs/SHELL_CROSVM_GDB_ENDPOINT_RUNTIME_2026-09-09.md`.

The follow-up no-VM configuration audit closed the remaining condition. The
product custom-image VM is always converted to `VirtualMachineRawConfig`, and
the framework conversion has no `gdbPort` assignment; the AppConfig
`CustomConfig.gdbPort` facility is not on this path. A private AIDL-parcel
mutation would be a Binder bypass and is out of scope.

```text
PRODUCT_GDB_PORT_CONFIGURABILITY = FAIL
PRODUCT_APP_GDB_PC_OBSERVER      = BLOCKED
```

Evidence: `docs/PRODUCT_GDB_PORT_CONFIGURATION_AUDIT_2026-09-09.md`.

## Persistent Windows Setup witness — 2026-09-09

With direct KD/GDB observability closed, a read-only audit selected the first
standard persistent artifact with a defensible positive-only meaning. The
baseline is RAM-disk WinPE: `Bootstat.dat`, `ntbtlog.txt`, EventLog/registry
state and dumps are either volatile (`X:`), BCD-dependent, later/error-only,
or storage-inapplicable. The selected standard mechanism was
`Microsoft-Windows-Setup/LogPath`, configured solely on a disposable clone by
root `Autounattend.xml`. It assigns the existing disk-0/partition-1 FAT ESP
`C:` with `WillWipeDisk=false`, then asks Setup to write `C:\SETUPACT.LOG` /
`C:\SETUPERR.LOG`.

Exactly one 100-second product-equivalent VM run ended at the known `ER`
post-EBS serial boundary. The stopped clone had its exact pre-run candidate
SHA and neither Setup log existed, proving no FAT sector write during the
bound. The private clone was deleted and the immutable Android baseline was
again exact.

```text
PERSISTENT_WINDOWS_SETUP_WITNESS = NOT_OBSERVED
WINDOWS_KERNEL_EXECUTION_AFTER_EBS = NOT_OBSERVED
PERSISTENT_EARLY_DISK_WRITE = NOT_OBSERVED
IMMUTABLE_BASELINE_RESTORED = PASS
```

`NOT_OBSERVED` is deliberately not a kernel-failure verdict: this witness is
later than NT kernel entry. Do not reopen KD/GDB/BCD on its basis. Evidence:
`docs/PERSISTENT_WINDOWS_BOOT_WITNESS_AUDIT_2026-09-09.md`.

## Synthetic post-EBS P0 runtime — 2026-09-10

One firmware-only P0 application replaced Windows for exactly one bounded
run.  Its raw post-EBS sequence was `BES → P1 → P2 → P3 → P4 → PR`: the
original EBS call returned successfully, then the application proved direct
UART, volatile RAM, and `CNTVCT_EL0` progression before requesting reset.
The 2 MiB one-range transaction was rolled back and the immutable baseline
was again exact.

```text
SYNTHETIC_POST_EBS_P0     = PASS
POST_EBS_EXECUTION        = PASS
POST_EBS_RAW_UART         = PASS
POST_EBS_VOLATILE_RAM     = PASS
POST_EBS_CNTVCT_PROGRESS  = PASS
POST_EBS_GIC_TIMER_IRQ_WFI = NOT_TESTED
```

The only next platform experiment is P1: probe-owned re-arm of GICv3/virtual
timer PPI 27 and a bounded WFI wakeup.  Evidence:
`docs/SYNTHETIC_POST_EBS_P0_RUNTIME_2026-09-10.md`.

## Synthetic post-EBS P1 runtime — 2026-09-10

The corrected P1 firmware-only probe passed.  P1 caches its GICD base and
timer PPI while PCD/Boot Services are valid, then after the proven original
EBS return it restores the minimal GICv3 Group-1 path, enables virtual-timer
PPI 27, arms `CNTV_TVAL_EL0`, and executes one `WFI`.

```text
SYNTHETIC_POST_EBS_P1        = PASS
POST_EBS_GICV3_REARM         = PASS
POST_EBS_VIRTUAL_TIMER_PPI27 = PASS
POST_EBS_WFI_WAKEUP          = PASS
POST_EBS_PLATFORM_CONTRACT   = PASS
```

The one corrected run's raw sequence was `BES → T0 → T1 → T2 → T3 → TR`
(`BES` and `T0` are adjacent raw records and appear as `BEST0`).  `T2` proves
the probe-owned timer IRQ handler ran; `T3` proves that IRQ woke `WFI`.
Rollback and the immutable Android baseline SHA were exact.

The first P1 attempt faulted in PcdDxe immediately after EBS because the probe
incorrectly read a PCD after EBS.  It was rolled back; the corrected run caches
that value before EBS and has no post-EBS PCD access.  This was a probe defect,
not a platform failure.

Evidence: `docs/SYNTHETIC_POST_EBS_P1_RUNTIME_2026-09-10.md`.

## Windows 11 IoT LTSC 2024 Setup A/B — 2026-09-10

The user selected a Windows-only media A/B instead of the proposed Linux
platform test.  A standard Windows-FAT32 materialization replaced only
`\SOURCES\BOOT.WIM` with the official ARM64 LTSC 2024 Setup WIM, build
26100.1742.  FAT32/CHKDSK, WIM verification, index 2 readability, BCD and
BOOTAA64 stability, patch overlay and rollback overlay all passed.

One 120-second app-owned run did **not** reproduce the known-good
`Loading files... -> BES/ER` boundary.  Its serial ended after the existing
firmware's `AVF_BDS_START_IMAGE \EFI\BOOT\BOOTAA64.EFI` path and before
`Loading files...`, `BES`, or `ER`.  The private clone was deleted and the
immutable Android baseline was exactly restored.

```text
LTSC_26100_1742_SETUP_MEDIA = INCOMPATIBLE_BEFORE_KNOWN_EBS_BOUNDARY
WINDOWS_SETUP_INSTALLER     = NOT_REACHED
IMMUTABLE_BASELINE_RESTORED = PASS
```

Do not repeat alternate Setup-WIM swaps.  Evidence:
`docs/LTSC_26100_1742_SETUP_AB_RUNTIME_2026-09-10.md`.

## EFI memory-map handoff audit — 2026-09-10

A read-only review found no concrete EFI map/MapKey defect.  The EBS wrapper
forwards Windows' MapKey unchanged to the original service; the historical
post-return marker proves that original EBS returned success.  `GetMemoryMap`
is not hooked.  The captured BDS map matches FDT DRAM (`0x80000000` plus 4
GiB) and runtime descriptors carry the runtime attribute.  The old
`CONVERT_AUDIT ... Not Found` records address known non-RAM diagnostic targets
before EBS and recur in a run that reaches EBS success.

```text
EFI_MEMORY_MAP_HANDOFF_HYPOTHESIS = NO_CONCRETE_DEFECT_FOUND
WINDOWS_EBS_HANDOFF               = PASS
```

Do not make speculative RAM/ACPI/runtime-map changes.  Evidence:
`docs/EFI_MEMORY_MAP_HANDOFF_AUDIT_2026-09-10.md`.

## Installed Windows system-disk feasibility — 2026-09-10

The proposed installed-Windows diagnostic is not a short replacement for the
existing Setup medium.  The present VM disk is one 9-GB FAT32 partition, while
the current firmware packages FAT but no NTFS UEFI driver.  Applying the
4.2-GB compressed LTSC `install.wim` requires a larger disk plus a FAT ESP and
an NTFS system partition.  That is a new storage topology and only a separate
diagnostic boot class, not an evidence-backed installer fix.

```text
INSTALLED_WINDOWS_DISK_ON_CURRENT_9GB_FAT_TOPOLOGY = NOT_FEASIBLE
INSTALLED_WINDOWS_DISK_REQUIRES_NEW_STORAGE_TOPOLOGY = YES
```

Evidence: `docs/INSTALLED_WINDOWS_DISK_FEASIBILITY_2026-09-10.md`.

## Product KD gated on post-EBS `BES` — 2026-09-10

One exact product run used the already-built BCD+r11 transaction and withheld
both `kd.exe` and all host serial TX until the unique raw `BES` marker.  The
app-owned bridge and private VM started, but the BCD-debug path stopped after
the known `IMAGE_AUDIT`/`CONVERT_AUDIT` tail and did not reproduce `BES` in
150 seconds.  Consequently KD TX was 0 bytes and a post-EBS handshake was
not tested.  Remote rollback and an independent Android hash check restored
the immutable baseline exactly.

```text
BES_GATE_OBSERVED          = NOT_OBSERVED
POST_EBS_KD_TX             = NOT_STARTED
POST_EBS_KD_HANDSHAKE      = NOT_TESTED
BCD_DEBUG_PATH_REACHED_EBS = NOT_OBSERVED
```

Do not repeat this BCD/KD variant.  Evidence:
`docs/PRODUCT_POST_EBS_BES_GATED_KD_RUNTIME_2026-09-10.md`.

## Linux ACPI-only control feasibility — 2026-09-10

Read-only inventory at that time found only the 168-byte direct AVF
serial-loopback payload; it bypasses UEFI, ACPI and any guest OS.  This was
later superseded by the acquired Debian control assets and by a forensic
recovery of an existing Termux/QEMU Windows experiment; do not treat the
following historical availability statement as current.

```text
LOCAL_LINUX_UEFI_ACPI_CONTROL_ASSET = NOT_AVAILABLE
LOCAL_QEMU_AARCH64_REFERENCE         = NOT_AVAILABLE
EXISTING_SERIAL_LOOPBACK_AS_ACPI_TEST = INVALID
```

Do not treat the loopback image as a Linux platform control. Evidence:
`docs/LINUX_ACPI_ONLY_REFERENCE_FEASIBILITY_2026-09-10.md`.

### Historical Termux/QEMU Windows control — 2026-09-13

Read-only recovery of the preserved August Termux/QEMU evidence established
that Windows 11 ARM64 Setup installed to NVMe and reached graphical OOBE on
the tablet.  The apparent later boot loop was the OOBE screen's required
network/update recovery, not a pre-kernel/platform loop.  The project already
contains an ISO-free QEMU user-mode-NAT OOBE launcher.  This proves an
independent TCG-QEMU Windows control only; it does not advance the separate
GenieZone/crosvm `WINDOWS_POST_EBS` boundary.

```text
QEMU_WINDOWS_SETUP_VISIBLE  = PASS (historical)
QEMU_WINDOWS_OOBE_USERMODE  = PASS (historical)
QEMU_PLATFORM_BOOT_LOOP     = REFUTED
AVF_WINDOWS_POST_EBS         = NOT_CONFIRMED
```

Evidence: `docs/HISTORICAL_QEMU_WINDOWS_25H2_CONTROL_2026-09-13.md`.

### Product 1-vCPU versus host-topology Windows A/B — 2026-09-13

The AVF product app was temporarily switched from its normal one-vCPU request
to the framework's host-topology request.  The system created an eight-vCPU
VM, and the FDT-derived ACPI table correctly enumerated GICC UID/MPIDR 0–7
with a 1-MiB GICR range.  Windows Boot Manager reached the established `ER`
post-`ExitBootServices()` boundary but emitted no later Windows/WinPE signal.
The immutable raw image rehashed exactly before and after cleanup, the
temporary VM was deleted, and the original APK was restored byte-for-byte.

```text
PRODUCT_8VCPU_FDT_ACPI_CONTRACT  = PASS
PRODUCT_8VCPU_EXIT_BOOT_SERVICES = PASS
PRODUCT_8VCPU_WINDOWS_POST_EBS   = NOT_CONFIRMED
SINGLE_VCPU_AS_FAST_WINDOWS_FIX  = REFUTED
```

Evidence: `docs/PRODUCT_8VCPU_WINDOWS_AB_RUNTIME_2026-09-13.md`.

### Product GIC ITS / IORT static audit — 2026-09-13

The exact one- and eight-vCPU MADT lengths account completely for GICC, GICD
and GICR records; neither contains a MADT GIC ITS record.  This is a valid
optional GICv3 feature omission.  Although the generic EDK2 source contains
an IORT ITS-group template, the actual product XSDT does not publish IORT, so
Windows cannot consume that template in the current boot.  An ITS-enable
experiment would fabricate hardware rather than repair a demonstrated
contract violation.

```text
PRODUCT_GIC_ITS_PRESENT             = NO
PRODUCT_IORT_PUBLISHED_TO_WINDOWS   = NO
GIC_ITS_AS_EARLY_WINDOWS_CAUSE      = NOT_PROVEN
FIRMWARE_ITS_ENABLE_A_B             = NOT_JUSTIFIED
```

Evidence: `docs/PRODUCT_GIC_ITS_IORT_STATIC_AUDIT_2026-09-13.md`.

### Debian ARM64 Linux ACPI control assets — 2026-09-11

After explicit approval, the official Debian Bookworm ARM64 netboot archive
was acquired and verified against Debian's manifest. It supplies standard
`grubaa64.efi`, kernel and initrd, with `acpi=force` and the known `ttyS0`
console specified in the checked-in GRUB configuration.  Candidate generation
requires an elevated Windows storage operation and has not yet been run.

```text
LINUX_ACPI_ONLY_CONTROL_ASSETS = VERIFIED
LINUX_ACPI_ONLY_CANDIDATE      = NOT_MATERIALIZED
LINUX_ACPI_ONLY_RUNTIME        = NOT_RUN
```

Entry point: `tools/linux-acpi-control/prepare-linux-acpi-control.ps1`.

## Direct Linux EFI-stub handoff probe — prepared 2026-09-11

The GRUB-based Debian control did not distinguish GRUB's kernel handoff from
Linux EFI-stub/kernel progress.  A separate, 20,480-byte ARM64 EFI launcher
has now been built and statically verified.  It emits `L0/L1/L2/L3/LX`, uses
UEFI `LoadImage`/`StartImage` directly on Debian's ARM64 Image, and embeds the
explicit initrd, ACPI and early-serial options.  This is a disposable media
launcher only; it is **not** embedded in firmware.

```text
LINUX_DIRECT_EFI_STUB_LAUNCHER_BUILD = PASS
LINUX_DIRECT_EFI_STUB_STATIC_AUDIT   = PASS
LINUX_DIRECT_EFI_STUB_CANDIDATE      = NOT_MATERIALIZED
LINUX_DIRECT_EFI_STUB_RUNTIME        = NOT_RUN
```

The elevated, standard-FAT-only offline entry point is
`tools/linux-efi-stub-control/prepare-linux-efi-stub-control.ps1`.  Do not
transfer or launch a candidate until it and its offline audit return
`RESULT=PASS`.  Details:
`docs/LINUX_EFI_STUB_HANDOFF_PROBE_2026-09-11.md`.

### Full AVF/GenieZone interface re-audit — 2026-09-14

A fresh read-only pass after the August security update revisited the app API,
shell `vm` CLI, AVF Binder, privileged Terminal APK, crosvm GDB, tracefs,
`/dev/gzvm`, Samsung HyPer HAL and guest-facing host devices. The only changed
whole artefact, `VmTerminalApp.apk`, has byte-identical `classes.dex`,
resources and manifest compared with the retained pre-OTA copy. The framework
jar remains byte-identical. No new public or app-usable guest-PC/vCPU/exit/IRQ
or timer observer was exposed.

```text
POST_OTA_NEW_DEBUG_INTERFACE          = NOT_OBSERVED
DIRECT_POST_EBS_WINDOWS_OBSERVABILITY = BLOCKED
```

This closes the "missed public interface" check for build `X736BXXS8BZH4`;
it does not prohibit productive pre-EBS product work or a vendor-provided
diagnostic path. Details: `docs/FULL_AVF_INTERFACE_REAUDIT_2026-09-14.md`.

### Direct Linux EFI-stub v2 runtime — 2026-09-11

The corrected v2 raw image was produced only after a VHD detach/re-attach
flush boundary, then audited and reconstructed by a 26-range transaction
overlay.  One 100-second app-owned run loaded the expected `0x5000` direct
launcher, loaded the Debian Image (`0x2010000`), emitted `L0/L1/L2/L3`, and
the official EFI stub printed `Exiting boot services...` immediately before
the proven post-return firmware marker `ER`.  There was no Linux earlycon or
`Linux version` line during the remaining capture window.

```text
LINUX_DIRECT_EFI_STUB_LOADED            = PASS
LINUX_DIRECT_EFI_STUB_STARTIMAGE         = PASS
LINUX_EFI_STUB_EBS_RETURN                = PASS
LINUX_KERNEL_EARLY_SERIAL                = NOT_OBSERVED
COMMON_OS_POST_EBS_SERIAL_SILENCE        = OBSERVED
IMMUTABLE_BASELINE_RESTORED              = PASS
```

This removes both GRUB and Windows Setup/WinPE as required explanations for
the silence, but does not directly prove or disprove native Linux-kernel
instruction entry.  Do not retry media variants without a new concrete shared
post-EBS observer/hypothesis.  Evidence:
`docs/LINUX_EFI_STUB_HANDOFF_PROBE_2026-09-11.md` and
`build-logs/linux-efi-stub-runtime-v2-20260911-173721/`.

### Direct Linux EFI-stub v1 transport finding — 2026-09-11

The first direct-stub runtime must be discarded as a candidate-transport
failure, not interpreted as Linux silence.  The transactional app path
accepted then rolled back the v1 patch exactly, but raw serial loaded an old
`BOOTAA64.EFI` of `0x2DB000` bytes and did not contain `L0`; the new launcher
is `0x5000` bytes.  The v1 materializer copied raw sectors immediately after
filesystem writes, without a detach/re-attach flush of the VHD cache.

```text
LINUX_DIRECT_EFI_STUB_V1_RUNTIME = INVALID_RAW_TRANSPORT
LINUX_DIRECT_EFI_STUB_RUNTIME    = NOT_RUN
IMMUTABLE_BASELINE_RESTORED      = PASS
```

The only permitted correction is the prepared v2 materialization script:
`tools/linux-efi-stub-control/prepare-linux-efi-stub-control-v2.ps1`.  It
forces a VHD detach/re-attach before sector-for-sector raw extraction and uses
new output names.  No second runtime is permitted until its offline gates and
new delta overlay pass.  Details:
`docs/LINUX_EFI_STUB_HANDOFF_PROBE_2026-09-11.md`.
