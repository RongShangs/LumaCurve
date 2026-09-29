#!/system/bin/sh
# Read-only by default; control is a short, bounded framework test with restoration.
set -u
[ "$(id -u)" = 0 ] || { echo '请先执行 su。' >&2; exit 1; }
MODE=${1:-inspect}
case "$MODE" in inspect|control) :;; *) echo '用法：sh ./probe_framework_android.sh inspect|control' >&2; exit 1;; esac
HERE=${0%/*}
for INPUT in LumaFrameworkProbe.jar process_scope.sh probe_framework_runtime.sh; do
  [ -r "$HERE/$INPUT" ] || { echo '探测文件缺失，请完整解压探测包。' >&2; exit 1; }
done
OUT="./luma-framework-probe/$(date +%Y%m%d-%H%M%S)-$MODE-$$"
mkdir -p "$OUT" || exit 1
APP_PROCESS=/system/bin/app_process
[ -x "$APP_PROCESS" ] || APP_PROCESS=/system/bin/app_process64
[ -x "$APP_PROCESS" ] || { echo 'app_process 不可用。' >&2; exit 1; }
RUN=$(mktemp -d /data/local/tmp/luma-framework-probe.XXXXXX) || exit 1
chmod 0700 "$RUN"
CTL=/data/adb/modules/luma_curve/luma_curvectl.sh
PAUSE=/data/local/tmp/luma_curve.paused
RESUME=0
cleanup() {
  if [ "$RESUME" = 1 ]; then
    sh "$CTL" resume >> "$OUT/module-restore.txt" 2>&1 || echo '模块恢复失败，详见 module-restore.txt。' >&2
  fi
  rm -rf "$RUN"
}
trap cleanup EXIT
cp "$HERE/LumaFrameworkProbe.jar" "$RUN/probe.jar" && chmod 0444 "$RUN/probe.jar" || exit 1
{
  date; id; getenforce; cat /proc/uptime
  for KEY in ro.product.model ro.build.version.release ro.build.version.sdk ro.build.version.incremental; do printf '%s=' "$KEY"; getprop "$KEY"; done
  cmd display help 2>&1
  for JAR in /system/framework/services.jar /system/framework/framework.jar /system/framework/miui-services.jar; do
    [ -r "$JAR" ] || continue
    ls -l "$JAR"; sha256sum "$JAR" 2>&1
  done
  service list 2>&1
  ps -A -o PID,PPID,NAME,ARGS 2>&1
  for ROOT in /system/framework /system_ext/framework /product/framework /vendor/etc/displayconfig /product/etc/displayconfig /odm/etc/displayconfig; do
    [ -d "$ROOT" ] && ls -l "$ROOT"
  done
} > "$OUT/environment.txt"
timeout 10 dumpsys display > "$OUT/before-display.txt" 2>&1
cat /data/local/tmp/luma_curve_state > "$OUT/before-state.txt" 2>/dev/null || :
if [ "$MODE" = control ]; then
  [ -r "$CTL" ] || { echo '缺少模块控制脚本，无法隔离测试。' >&2; exit 1; }
  [ -f "$PAUSE" ] || RESUME=1
  if ! sh "$CTL" pause > "$OUT/module-pause.txt" 2>&1; then echo '暂停失败，未进行亮度测试。' >&2; exit 1; fi
  [ -z "$(pidof luma_curve_daemon 2>/dev/null)" ] || { echo '模块仍在运行，停止测试。' >&2; exit 1; }
fi
if [ "$MODE" = inspect ]; then
  timeout 60 sh "$HERE/probe_framework_runtime.sh" "$RUN/probe.jar" "$APP_PROCESS" "$MODE" > "$OUT/framework.txt" 2>&1
else
  sh "$HERE/probe_framework_runtime.sh" "$RUN/probe.jar" "$APP_PROCESS" "$MODE" > "$OUT/framework.txt" 2>&1
fi
RESULT=$?
if [ "$MODE" = control ] && ! grep -q '^RESTORE mode=ok brightness_request=sent$' "$OUT/framework.txt"; then
  # A failed preflight does not mutate settings. Once SAVED appears, require restore confirmation.
  if grep -q '^SAVED ' "$OUT/framework.txt"; then
    RESUME=0
    echo '未确认系统设置完整恢复，模块保持暂停；请手机 Agent 按 SAVED 记录恢复。' >&2
  fi
fi
timeout 10 dumpsys display > "$OUT/after-display.txt" 2>&1
echo "探测输出：$OUT"
[ "$RESULT" = 0 ] || echo '接口未通过，保留失败信息供适配；这不代表系统没有亮度接口。' >&2
exit "$RESULT"
