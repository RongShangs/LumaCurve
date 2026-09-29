#!/system/bin/sh
# Read-only firmware collection. Never call the old control path.
set -u
[ "$(id -u)" = 0 ] || { echo '请先执行 su。' >&2; exit 1; }
[ "$#" = 0 ] || { echo '用法：sh ./probe_framework02_android.sh（仅只读采集）' >&2; exit 1; }
HERE=${0%/*}
OUT="./luma-framework02-$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$OUT/firmware" || exit 1
OUT=$(cd "$OUT" && pwd) || exit 1
for JAR in /system/framework/framework.jar /system/framework/services.jar /system_ext/framework/miui-framework.jar /system_ext/framework/miui-services.jar; do
  [ -r "$JAR" ] || { echo "缺少框架文件：$JAR" >&2; exit 1; }
  DEST="$OUT/firmware/${JAR##*/}"
  cp "$JAR" "$DEST" || exit 1
  BEFORE=$(sha256sum "$JAR") || exit 1
  AFTER=$(sha256sum "$DEST") || exit 1
  [ "${BEFORE%% *}" = "${AFTER%% *}" ] || { echo '框架复制校验失败。' >&2; exit 1; }
  printf '%s\n' "$BEFORE" >> "$OUT/firmware-sha256.txt"
done
# Keep paths absolute when the inspect script changes its output directory.
HERE=$(cd "$HERE" && pwd) || exit 1
(cd "$OUT" && sh "$HERE/probe_framework_android.sh" inspect) > "$OUT/collection.txt" 2>&1
RESULT=$?
cat "$OUT/collection.txt"
echo "采集目录：$OUT"
echo '请将整个目录压缩回传；框架文件仅用于核对这台手机的实际实现。'
exit "$RESULT"
