#!/system/bin/sh
# Two-minute full-C-engine / framework-backend test in a private runtime.
set -u
[ "$#" = 0 ] && [ "$(id -u)" = 0 ] || { echo '用法：su 后 sh ./test_framework_core_android.sh'; exit 2; }
HERE=${0%/*}; HERE=$(cd "$HERE" && pwd) || exit 2
for FILE in LumaFrameworkProbe.jar luma_framework_core process_scope.sh framework_core_runtime.sh framework_core_watchdog.sh probe_temporary_runtime.sh luma_curve.conf; do
  [ -r "$HERE/$FILE" ] || { echo "缺少 $FILE，请完整解压。"; exit 2; }
done
. "$HERE/process_scope.sh"
lc_scope_detach_self || exit 2
for PAIR in 'framework.jar:1d2bf53f6c2684103dadbeef0d2665a7f033b7746a75f3e7404145dd999600fd' 'services.jar:ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20'; do
  NAME=${PAIR%%:*}; EXPECTED=${PAIR#*:}
  HASH=$(sha256sum "/system/framework/$NAME") || exit 2
  [ "${HASH%% *}" = "$EXPECTED" ] || { echo '固件不匹配，未启动测试。'; exit 2; }
done
APP=/system/bin/app_process; [ -x "$APP" ] || APP=/system/bin/app_process64
RUN=$(mktemp -d /data/local/tmp/luma-framework-core.XXXXXX) || exit 2
chmod 0700 "$RUN"
cp "$HERE/LumaFrameworkProbe.jar" "$RUN/probe.jar" && cp "$HERE/luma_framework_core" "$RUN/luma_framework_core" || exit 2
chmod 0700 "$RUN/luma_framework_core"
OUT="$HERE/luma-framework-core-test03-$(date +%Y%m%d-%H%M%S)-$$"
mkdir "$OUT" || exit 2
{ date; id; getprop ro.build.version.incremental; } > "$OUT/environment.txt"
timeout 10 dumpsys display > "$OUT/before-display.txt" 2>&1
awk '/^Display Power Controller:$/ {n++;} n==1 {print;}' "$OUT/before-display.txt" > "$OUT/before-main.txt"
if ! grep -q 'mDisplayId=0' "$OUT/before-main.txt" || ! grep -q 'mTemporaryScreenBrightness:NaN' "$OUT/before-main.txt" ||
   ! grep -q 'mUseAutoBrightness=true' "$OUT/before-main.txt" || grep -q 'mUseAutoBrightness=false' "$OUT/before-main.txt"; then
  echo '请开启系统自动亮度，并先清除其他临时亮度请求；未启动测试。'; exit 2
fi
timeout 10 sh "$HERE/probe_temporary_runtime.sh" "$RUN/probe.jar" "$APP" preflight > "$OUT/preflight.txt" 2>&1 || { cat "$OUT/preflight.txt"; exit 2; }
if [ -r /data/local/tmp/luma_curve.conf ]; then cp /data/local/tmp/luma_curve.conf "$RUN/luma_curve.conf";
else cp "$HERE/luma_curve.conf" "$RUN/luma_curve.conf"; fi
printf '\npreference_learning=0\nhigh_lux_hbm_enable=0\n' >> "$RUN/luma_curve.conf"
RESUME=0
if [ ! -f /data/local/tmp/luma_curve.paused ] && [ -n "$(pidof luma_curve_daemon 2>/dev/null)" ]; then RESUME=1; fi
printf 'original_resume=%s\nruntime=%s\n' "$RESUME" "$RUN" > "$OUT/session.txt"
# Arm recovery before pausing; terminal death cannot strand a temporary request.
nohup sh "$HERE/framework_core_watchdog.sh" "$RUN" "$OUT" "$RESUME" "$APP" > "$OUT/watchdog.txt" 2>&1 </dev/null &
GUARD=$!
for TRY in 1 2 3 4 5; do [ -f "$RUN/armed" ] && break; sleep 1; done
[ -f "$RUN/armed" ] || { echo '恢复进程未就绪，未启动控制。'; exit 2; }
finish() { touch "$RUN/test-finished"; }
trap finish EXIT HUP INT TERM
if ! sh /data/adb/modules/luma_curve/luma_curvectl.sh pause > "$OUT/pause.txt" 2>&1 ||
   [ -n "$(pidof luma_curve_daemon 2>/dev/null)" ]; then finish; wait "$GUARD"; exit 2; fi
SOCKET="luma.framework.core.$$"
nohup sh "$HERE/framework_core_runtime.sh" "$RUN" "$APP" broker "$SOCKET" > "$OUT/framework.log" 2>&1 </dev/null &
BROKER=$!; echo "$BROKER" > "$RUN/broker.pid"
for TRY in $(seq 1 15); do [ -f "$RUN/broker-ready" ] && break; kill -0 "$BROKER" 2>/dev/null || break; sleep 1; done
if [ ! -f "$RUN/broker-ready" ]; then echo '框架后端启动失败，执行恢复。'; finish; wait "$GUARD"; exit 2; fi
nohup sh "$HERE/framework_core_runtime.sh" "$RUN" "$APP" core "$SOCKET" > "$OUT/core.log" 2>&1 </dev/null &
CORE=$!; echo "$CORE" > "$RUN/core.pid"
echo '完整核心测试约 2 分钟，每次接管限制为当时框架亮度 ±0.01。不要安装或重启模块。'
RESULT=0
for TICK in $(seq 1 120); do
  if ! kill -0 "$CORE" 2>/dev/null || ! kill -0 "$BROKER" 2>/dev/null; then RESULT=2; break; fi
  touch "$RUN/luma_curve_ui_watch"
  if [ $((TICK % 10)) = 0 ]; then
    for PID in "$CORE" "$BROKER"; do
      { printf '\n--- tick=%s pid=%s ---\n' "$TICK" "$PID";
        cat /proc/uptime 2>/dev/null;
        cat "/proc/$PID/stat" 2>/dev/null;
        grep -E '^(Name|State|Pid|Threads|VmRSS|VmHWM):' "/proc/$PID/status" 2>/dev/null;
        cat "/proc/$PID/cgroup" 2>/dev/null;
      } >> "$OUT/process-samples.txt"
    done
  fi
  if [ -r "$RUN/luma_curve_state" ]; then
    { printf '\n--- tick=%s ---\n' "$TICK"; cat "$RUN/luma_curve_state"; } >> "$OUT/states.txt"
  fi
  sleep 1
done
finish
wait "$GUARD" || RESULT=2
[ ! -r "$RUN/framework.trace" ] || cp "$RUN/framework.trace" "$OUT/framework.trace"
[ ! -r "$RUN/framework.events" ] || cp "$RUN/framework.events" "$OUT/framework.events"
[ ! -r "$RUN/luma_curve_state" ] || cp "$RUN/luma_curve_state" "$OUT/final-state.txt"
cat "$OUT/recovery.txt"
echo "输出目录：$OUT"
[ -f "$RUN/restored" ] || RESULT=2
# Retain private evidence rather than removing a runtime while recovery is uncertain.
echo "独立运行目录保留在 $RUN；未替换正式模块，未改动原配置和学习文件。"
exit "$RESULT"
