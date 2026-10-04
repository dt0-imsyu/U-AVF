#!/bin/sh
# Configure the SAME encoded backend in Live root or validated install target.
# No guest services are started here (safe before switch_root or in /target).
set -eu
source_dir=$1
target=$2
[ -r "$target/etc/os-release" ] || exit 1
. "$target/etc/os-release"
[ "$ID" = ubuntu ] && [ "$VERSION_ID" = 24.04 ] || exit 1
dest="$target/usr/local/lib/uavf-encoded"
for name in uavf-encoded-display.py uavf-encoded-capture.py uavf-encoded.socket uavf-encoded@.service libgstx264.so libx264.so.164 GST_X264_COPYRIGHT X264_COPYRIGHT; do
  [ -s "$source_dir/$name" ] || { echo "ENCODED_PAYLOAD_MISSING=$name"; exit 1; }
done
bridge="$source_dir/winavf-frame-bridge.py"
[ -s "$bridge" ] || bridge="$source_dir/winavf-frame-bridge"
[ -s "$bridge" ] || exit 1
mkdir -p "$dest/plugins" "$dest/lib" "$dest/licenses" \
  "$target/etc/systemd/system/sockets.target.wants"
copy_readonly() {
  cp "$1" "$2"
  chmod 0644 "$2"
}
for name in uavf-encoded-display.py uavf-encoded-capture.py; do
  copy_readonly "$source_dir/$name" "$dest/$name"
done
copy_readonly "$bridge" "$dest/winavf-frame-bridge.py"
copy_readonly "$source_dir/libgstx264.so" "$dest/plugins/libgstx264.so"
copy_readonly "$source_dir/libx264.so.164" "$dest/lib/libx264.so.164"
for name in GST_X264_COPYRIGHT X264_COPYRIGHT; do
  copy_readonly "$source_dir/$name" "$dest/licenses/$name"
done
for name in uavf-encoded.socket uavf-encoded@.service; do
  copy_readonly "$source_dir/$name" "$target/etc/systemd/system/$name"
done
ln -sfn ../uavf-encoded.socket "$target/etc/systemd/system/sockets.target.wants/uavf-encoded.socket"
printf '%s\n' 'version=3' 'backend=GPU_I420_X264_UVF1' 'modes=installed,live,installer' > "$dest/runtime-version"
echo ENCODED_RUNTIME_STAGED=PASS
if [ -s "$source_dir/stage-uavf-safe-jit.sh" ]; then
  sh "$source_dir/stage-uavf-safe-jit.sh" "$source_dir" "$target"
fi
