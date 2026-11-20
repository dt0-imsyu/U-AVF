#!/system/bin/sh
# Isolated disposable Windows only. Never stop production U-AVF/Ubuntu.
# Detached on-device guard survives an ADB connection loss.
set -u
package=com.example.uavfwinpetest
owner_name=com.example.uavfwinpetest:vmowner
reserve_kib=52428800
stop_kib=54525952
expected_pid=${1:-}
mode=${2:-watch}
case "$expected_pid" in ''|*[!0-9]*) echo 'INVALID_OWNER_PID'; exit 2;; esac
case "$mode" in watch|check) ;; *) echo 'INVALID_MODE'; exit 2;; esac
tick=0
while true; do
    found=false
    for candidate in $(pidof "$owner_name"); do
        if [ "$candidate" = "$expected_pid" ]; then found=true; fi
    done
    if [ "$found" != true ]; then echo 'OWNER_ENDED; NO_ACTION'; exit 0; fi
    available=$(df -k /data | awk 'NR == 2 {print $4}')
    case "$available" in ''|*[!0-9]*)
        echo 'DF_FAILED; STOP_ISOLATED_WINDOWS_TO_PROTECT_RESERVE'
        if [ "$mode" = watch ]; then am force-stop "$package"; fi
        exit 3;;
    esac
    if [ "$mode" = check ]; then
        echo "CHECK available_kib=$available reserve_kib=$reserve_kib stop_kib=$stop_kib owner_pid=$expected_pid"
        [ "$available" -gt "$stop_kib" ]; exit $? 
    fi
    if [ "$available" -le "$stop_kib" ]; then
        echo "RESERVE_GUARD_STOP available_kib=$available reserve_kib=$reserve_kib"
        am force-stop "$package"
        exit 4
    fi
    if [ $((tick % 10)) -eq 0 ]; then echo "AVAILABLE_KIB=$available OWNER_PID=$expected_pid"; fi
    tick=$((tick + 1))
    sleep 1
done
