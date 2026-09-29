#!/system/bin/sh
# Read-only prerequisite for user-mode handoff and preference learning.
set -u
[ "$(id -u)" = 0 ] || { echo '请先执行 su。' >&2; exit 1; }
[ "$#" = 0 ] || { echo '用法：sh ./probe_framework05_android.sh' >&2; exit 1; }
HERE=${0%/*}; HERE=$(cd "$HERE" && pwd) || exit 1
for FILE in LumaFrameworkProbe.jar process_scope.sh probe_settings_runtime.sh; do
  [ -r "$HERE/$FILE" ] || { echo '请完整解压采集包。' >&2; exit 1; }
done
OUT="./luma-framework05-$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$OUT" || exit 1
OUT=$(cd "$OUT" && pwd) || exit 1
RUN=$(mktemp -d /data/local/tmp/luma-framework05.XXXXXX) || exit 1
chmod 0700 "$RUN"
cleanup() { rm -f "$RUN/probe.jar"; rmdir "$RUN" 2>/dev/null || :; }
trap cleanup EXIT
cp "$HERE/LumaFrameworkProbe.jar" "$RUN/probe.jar" && chmod 0444 "$RUN/probe.jar" || exit 1
APP=/system/bin/app_process; [ -x "$APP" ] || APP=/system/bin/app_process64
{
 date; id; getenforce
 for KEY in ro.product.model ro.build.version.release ro.build.version.sdk ro.build.version.incremental; do printf '%s=' "$KEY"; getprop "$KEY"; done
 for FILE in /system/framework/framework.jar /system/framework/services.jar; do sha256sum "$FILE"; done
} > "$OUT/environment.txt"
timeout 25 sh "$HERE/probe_settings_runtime.sh" "$RUN/probe.jar" "$APP" > "$OUT/settings-bridge.txt" 2>&1
RESULT=$?
cat "$OUT/settings-bridge.txt"
# Copy only the main panel's declared display calibration, not global user settings.
CONFIG=/product/etc/displayconfig/display_id_4630946949513469331.xml
if [ -r "$CONFIG" ]; then
 cp "$CONFIG" "$OUT/main-display-config.xml" || RESULT=2
 sha256sum "$CONFIG" > "$OUT/display-config-sha256.txt"
fi
timeout 10 dumpsys display > "$OUT/display.txt" 2>&1
echo "输出目录：$OUT"
echo '仅读取模式、偏好、亮度单位和主屏标定；未暂停模块或写入亮度。'
exit "$RESULT"
