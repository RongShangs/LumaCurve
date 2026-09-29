#!/system/bin/sh
# Verified-firmware temporary strategy experiment; no Settings/mode/sysfs writes.
set -u
[ "$(id -u)" = 0 ] || { echo '请先执行 su。' >&2; exit 1; }
[ "$#" = 0 ] || { echo '用法：sh ./probe_framework03_android.sh' >&2; exit 1; }
HERE=${0%/*}; HERE=$(cd "$HERE" && pwd) || exit 1
for FILE in LumaFrameworkProbe.jar process_scope.sh probe_temporary_runtime.sh probe_temporary_watchdog.sh; do
  [ -r "$HERE/$FILE" ] || { echo '请完整解压探测包。' >&2; exit 1; }
done
OUT="./luma-framework03-$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$OUT" || exit 1
OUT=$(cd "$OUT" && pwd) || exit 1
{
  date; id; getenforce
  for KEY in ro.product.model ro.build.version.release ro.build.version.sdk ro.build.version.incremental; do printf '%s=' "$KEY"; getprop "$KEY"; done
} > "$OUT/environment.txt"
for PAIR in 'framework.jar:1d2bf53f6c2684103dadbeef0d2665a7f033b7746a75f3e7404145dd999600fd' 'services.jar:ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20'; do
  NAME=${PAIR%%:*}; EXPECTED=${PAIR#*:}
  HASH=$(sha256sum "/system/framework/$NAME") || exit 1
  [ "${HASH%% *}" = "$EXPECTED" ] || { echo '固件与已分析版本不符，停止控制测试。' >&2; exit 1; }
done
APP=/system/bin/app_process; [ -x "$APP" ] || APP=/system/bin/app_process64
RUN=$(mktemp -d /data/local/tmp/luma-framework03.XXXXXX) || exit 1
chmod 0700 "$RUN"
cp "$HERE/LumaFrameworkProbe.jar" "$RUN/probe.jar" && chmod 0444 "$RUN/probe.jar" || exit 1
CTL=/data/adb/modules/luma_curve/luma_curvectl.sh
RESUME=0
[ -f /data/local/tmp/luma_curve.paused ] || RESUME=1
timeout 10 dumpsys display > "$OUT/before-display.txt" 2>&1
awk '/^Display Power Controller:$/ {n++;} n==1 {print;}' "$OUT/before-display.txt" > "$OUT/before-main.txt"
if ! grep -q 'mDisplayId=0' "$OUT/before-main.txt" ||
   ! grep -q 'mTemporaryScreenBrightness:NaN' "$OUT/before-main.txt" ||
   grep -q 'mUseAutoBrightness=false' "$OUT/before-main.txt" ||
   ! grep -q 'mUseAutoBrightness=true' "$OUT/before-main.txt"; then
  echo '主屏必须开启系统自动亮度且没有现存临时请求；未进行控制。' >&2; exit 1
fi
timeout 10 sh "$HERE/probe_temporary_runtime.sh" "$RUN/probe.jar" "$APP" preflight > "$OUT/preflight.txt" 2>&1 || { cat "$OUT/preflight.txt"; exit 1; }
echo '即将暂停模块，进行约 21 秒的小幅升降与保持测试。'
sh "$CTL" pause > "$OUT/pause.txt" 2>&1 || { echo '暂停失败，未进行控制。' >&2; exit 1; }
[ -z "$(pidof luma_curve_daemon 2>/dev/null)" ] || { echo '模块仍在运行，未进行控制。' >&2; exit 1; }
# The independent watchdog owns all final release/resume actions, including if
# this interactive shell disappears. Its files must survive until recovery.
nohup sh "$HERE/probe_temporary_watchdog.sh" "$RUN" "$OUT" "$RESUME" "$APP" > "$OUT/watchdog.txt" 2>&1 </dev/null &
GUARD=$!
for ATTEMPT in 1 2 3 4 5; do
  [ -f "$RUN/armed" ] && break
  [ -f "$RUN/guard-failed" ] && break
  sleep 1
done
if [ ! -f "$RUN/armed" ]; then
  echo '恢复进程未就绪，未进行控制；模块保持暂停。' >&2; exit 1
fi
sh "$HERE/probe_temporary_runtime.sh" "$RUN/probe.jar" "$APP" test > "$OUT/trace.txt" 2>&1 &
TEST=$!; echo "$TEST" > "$RUN/test.pid"
wait "$TEST"; RESULT=$?
touch "$RUN/test-finished"
wait "$GUARD" || RESULT=2
cat "$OUT/recovery.txt"
echo "输出目录：$OUT"
if [ -f "$RUN/restored" ]; then
  rm -f "$RUN/probe.jar" "$RUN/armed" "$RUN/test.pid" "$RUN/test-finished" "$RUN/restored"
  rmdir "$RUN" 2>/dev/null || :
else
  echo "恢复未确认。保留目录 $RUN；请回传输出，模块保持暂停。" >&2
  RESULT=2
fi
exit "$RESULT"
