#!/system/bin/sh
# Dedicated disposable Windows VM only. Logs retained; no app/storage actions.
set -u
work=/data/local/tmp/uavf-windows-uart-gas-20261004
mode=$1
case "$mode" in control|control-fixed|gas8|gas8-immediate|gas8-gated|gas8-observer|gas8-stoptrace|gas8-noraw|gas8-runtime-quiet|gas8-runtime-quiet-ro|gas8-boundary|gas8-reset0|gas8-chunked|gas8-kernel|gas8-paced|gas8-gic-audit) ;; *) exit 64;; esac
limit=${2:-180}
case "$limit" in 120|180|300) ;; *) exit 64;; esac
dir="$work/$mode"
mkdir -p "$dir"
console="$dir/raw-serial.bin"
: > "$console"
"$work/console-file-base64-tailer" "$console" &
tailer=$!
# FIFO creation is denied by stock Android shell SELinux. Use the existing
# anonymous-pipe bridge and retain both device and host time bounds.
trap 'kill "$tailer" 2>/dev/null || true' EXIT HUP INT TERM
case "$mode" in gas8-immediate|gas8-gated|gas8-observer|gas8-stoptrace|gas8-noraw|gas8-runtime-quiet|gas8-runtime-quiet-ro|gas8-boundary|gas8-reset0) ingress="$work/kd-base64-ingress" ;; *) ingress=base64 ;; esac
case "$mode" in gas8-chunked|gas8-kernel) ingress="$work/kd-base64-ingress-chunked" ;; esac
if [ "$ingress" = base64 ]; then set -- base64 -di; else set -- "$ingress"; fi
if [ "$mode" = gas8-paced ]; then set -- "$work/kd-base64-ingress-paced" --paced --log "$dir/decoded-host-tx.bin"; fi
"$@" | timeout -k 5 "$limit" /apex/com.android.virt/bin/vm run \
  --console "$console" --log "$dir/crosvm.log" "$work/vm-config.json" >"$dir/vm.log" 2>&1
rc=$?
sleep 1
kill "$tailer" 2>/dev/null || true
wait "$tailer" 2>/dev/null || true
printf 'VM_EXIT=%s\n' "$rc" > "$dir/vm.exit"
exit "$rc"
