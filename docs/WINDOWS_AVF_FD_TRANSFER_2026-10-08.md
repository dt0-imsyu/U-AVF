# Actual AVF FD transfer failure — 2026-10-08

## Controlled outcome

Run `kd-winpe-duplex-proven-client-20261008-runtime`, CID2113, same
producer-accept-fix image SHA1E9D3B601D2003F5D8FD5A0D5AFEBFDF0E1B0C8359A2345BDB689D137998E4BE.
No CPU/PMU/config sweep, signed Windows RAM patch, or production changes.
viosock loads; shell AVF owner delegates its Binder; two actual AVF connections
return PFDs. Android fails before PFD.CREATOR/UVF reading with DeadObjectException.

Correlated logcat (19:23:33.341 and 19:23:34.346):

```
libbinder.IPCThreadState: Binder transaction failure. id: 57467299,
cmd: BR_FAILED_REPLY (29201), error: -1 (Operation not permitted)
JavaBinder: !!! FAILED BINDER TRANSACTION !!! (parcel size = 240)
```

Second transaction ID57467481 repeats the same refusal. Shell owner PID16929
remained alive, crosvm PID16953 active. Later Binder calls return normal Java
IllegalStateException after the two guest connections are consumed. Therefore
DeadObjectException does NOT demonstrate owner death or decoder corruption.
Android receive/decode/present remain zero; no actual tablet display PASS.

## Differential

Same provider, Binder code1, DelegatedAvfChannel, FD parcel serialization and APK:

- /dev/null descriptor transfer/fstat: PASS.
- Ordinary socketpair challenge shell -> APK -> shell: PASS.
- Actual AVF vsock PFD from shell-owned VM -> APK: Binder EPERM twice.

The socketpair test creates no VM and no AF_VSOCK socket. It is an IPC unit
test, not a display alternative. Reads are bounded5seconds; UID/token checks
are enforced. Actual AVF uses owner-authorized connectVsock, not shell socket().

Observed contexts: shell owner `u:r:shell:s0`; APK
`u:r:untrusted_app:s0:c42,c256,c512,c768`; shell virtualizationmanager/crosvm
contexts have no app categories. This is consistent with a cross-context FD
security restriction, but available all-buffer logcat contains no AVC naming
the exact denying rule. Do NOT claim a specific SELinux rule is proven.

## Decision

The shell-owner FD delegation route is blocked by an actual Android security
refusal, not missing guest frames or unknown Java marshalling. No retry/codec
flags/CPU flags will repair that refusal. Do not proxy socket bytes through
Binder as a policy workaround, retry raw shell AF_VSOCK, or weaken SELinux.

A VM created and connected by the actual APK remains the intended lawful
production ownership model, but its WinPE boot regression is unresolved and
the user explicitly closed the uninformative CPU/PMU/timing sweeps. Reopening
that work needs a new evidence-backed hypothesis, not another blind launch.

## Follow-up: supported ownership API audit

Read-only reflection on the shipped Samsung API (UID2000, no VM created)
enumerated ALL IVirtualizationService/IVirtualMachine methods. No live-VM
adoption/reassignment API was present. `debugListVms` returns debug information,
not a runnable handle. `claimVmInstance(byte[])` and `createAccessorBinder`
exist, but are not live-process ownership/SELinux relabel operations.
Manager `importFromDescriptor` and VM `toDescriptor` also exist.

[AOSP AVF API documentation](https://android.googlesource.com/platform/packages/modules/Virtualization/+/refs/heads/main/libs/framework-virtualization/README.md)
specifies descriptor transfer of a STOPPED VM's persisted instance/config/
storage files, equivalent to creating an imported instance. It does not adopt
the running shell-owned process. Consequently it would still require an
APK-owned boot, the unresolved regression; it is not a new live-display fix.
[AOSP virtmgr implementation](https://android.googlesource.com/platform/packages/modules/Virtualization/+/6f6bf89348dfa8a448be5e3c1bbe344e2779f5e2/android/virtmgr/src/aidl.rs)
forwards claimVmInstance to the global service and createAccessorBinder wraps
a guest Binder-RPC endpoint, not the UVF1 byte-stream decoder contract.
These upstream semantics guide the audit, not a claim that Samsung source is
identical. No claiming/importing/accessor operation was executed.

Expanded read-only audit jar SHA
AFF691E48D86635B2DDC2B006F56A25036BBA382AE7ED2DA8A6081AA23E20AEA
was built/deployed. Runtime APK and Windows image unchanged; VM[] remains.

USB disconnected again during observer cleanup; cooperative stop marker was
confirmed and subsequent authoritative VM list was empty. Android reports
pulled after reconnect. Preserve existing runtime events and guest extraction
`winpe-display-evidenceDuplexClient2113`; don't treat cleanup failure as a
Windows hang. No install operation attempted, Ubuntu untouched.
