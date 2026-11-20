#!/system/bin/sh
# Bounded diagnostic owner plus supported AVF FD delegation; Windows only.
set -u
work=/data/local/tmp/uavf-windows-uart-gas-20261004
token=$1
limit=${2:-180}
candidate_sha=${3:-A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5}
case "$limit" in 180|300) ;; *) exit 64;; esac
console="$work/owner-serial.bin"
: > "$console"
"$work/console-file-base64-tailer" "$console" &
tailer=$!
trap 'kill "$tailer" 2>/dev/null || true' EXIT HUP INT TERM
"$work/kd-base64-ingress-paced" --paced --log "$work/owner-decoded-host-tx.bin" |
  CLASSPATH="$work/owner-api-audit.jar" timeout -k 5 "$limit" app_process /system/bin com.example.winavf.AvfOwnerBridge "$token" "$limit" --candidate-sha "$candidate_sha"
rc=$?
kill "$tailer" 2>/dev/null || true
wait "$tailer" 2>/dev/null || true
exit "$rc"
