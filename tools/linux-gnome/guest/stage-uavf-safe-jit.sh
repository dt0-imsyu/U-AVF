#!/bin/sh
# Additive UAVF safe-JIT stage; no desktop/display service restarts.
set -eu
src=$1
target=$2
[ -r "$target/etc/os-release" ] || exit 1
. "$target/etc/os-release"
[ "$ID" = ubuntu ] && [ "$VERSION_ID" = 24.04 ] || exit 1
[ -s "$src/uavf-safe-jit.py" ] || exit 1
shim="$target/usr/local/bin/vkmark"
if [ -e "$shim" ] && ! grep -q 'UAVF safe-JIT' "$shim"; then
    echo 'SAFE_JIT_STAGE=EXISTING_VKMARK_SHIM_PRESERVED'; exit 1
fi
mkdir -p "$target/usr/local/bin" "$target/etc/xdg/autostart" "$target/var/lib/uavf"
cp "$src/uavf-safe-jit.py" "$target/usr/local/bin/uavf-safe-jit"
chmod 0755 "$target/usr/local/bin/uavf-safe-jit"
printf '%s\n' '#!/bin/sh' '# UAVF safe-JIT compatibility launcher' \
    'exec /usr/local/bin/uavf-safe-jit vkmark "$@"' > "$shim"
chmod 0755 "$shim"
printf '%s\n' '[Desktop Entry]' 'Type=Application' 'Name=U-AVF compatibility policy' \
    'Exec=/usr/local/bin/uavf-safe-jit --setup-desktop' 'NoDisplay=true' \
    'X-GNOME-Autostart-enabled=true' > "$target/etc/xdg/autostart/uavf-safe-jit.desktop"
printf '%s\n' 'version=1' 'policy=llvm20-nosve-v1' 'scope=snap-store,vkmark' \
    > "$target/var/lib/uavf/safe-jit-policy"
echo 'SAFE_JIT_STAGED=PASS'
