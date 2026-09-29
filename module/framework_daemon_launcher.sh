#!/system/bin/sh
# Local HyperOS 4 build: one supervised C engine and one root framework broker.
set -u
MODDIR=${0%/*}
DAEMON="$MODDIR/system/bin/luma_curve_daemon"
JAR="$MODDIR/framework-broker.jar"
LOG=/data/local/tmp/luma_curve.log
[ "$(id -u)" = 0 ] && [ -x "$DAEMON" ] && [ -r "$JAR" ] || exit 2
for PAIR in \
  'framework.jar:1d2bf53f6c2684103dadbeef0d2665a7f033b7746a75f3e7404145dd999600fd' \
  'services.jar:ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20'; do
  NAME=${PAIR%%:*}; EXPECTED=${PAIR#*:}
  ACTUAL=$(sha256sum "/system/framework/$NAME" 2>/dev/null) || exit 2
  [ "${ACTUAL%% *}" = "$EXPECTED" ] || {
    echo "[LumaCurve服务] 固件已变化：$NAME；暂停框架接管，由系统控制亮度。" >> "$LOG"
    exit 2
  }
done
APP=/system/bin/app_process; [ -x "$APP" ] || APP=/system/bin/app_process64
RUN=$(mktemp -d /data/local/tmp/luma-framework-core.prod.XXXXXX) || exit 2
chmod 0700 "$RUN" || exit 2
SOCKET="luma.framework.prod.$$"
BROKER= CORE=
cleanup() {
  trap - EXIT
  [ -z "$CORE" ] || kill -TERM "$CORE" 2>/dev/null || :
  if [ -n "$BROKER" ]; then
    kill -TERM "$BROKER" 2>/dev/null || :
    for _CLEAN_TRY in 1 2 3; do
      kill -0 "$BROKER" 2>/dev/null || break
      sleep 1
    done
    kill -KILL "$BROKER" 2>/dev/null || :
    wait "$BROKER" 2>/dev/null || :
  fi
  CLASSPATH="$JAR" timeout 10 "$APP" /system/bin LumaFrameworkProbeTemporary release >> "$LOG" 2>&1 || :
  rm -f "$RUN/broker-ready" "$RUN/release-display.txt"
  rmdir "$RUN" 2>/dev/null || :
}
trap cleanup EXIT
trap 'exit 143' HUP INT TERM
export LUMA_FRAMEWORK_PRODUCTION=1
export CLASSPATH="$JAR"
"$APP" /system/bin LumaFrameworkOutputBroker "$SOCKET" "$RUN" >> "$LOG" 2>&1 &
BROKER=$!
READY=0
TRY=0
while [ "$TRY" -lt 20 ]; do
  [ -f "$RUN/broker-ready" ] && { READY=1; break; }
  kill -0 "$BROKER" 2>/dev/null || break
  TRY=$((TRY + 1)); sleep 1
done
[ "$READY" = 1 ] || { echo '[LumaCurve服务] 框架亮度后端启动失败，保留系统调节。' >> "$LOG"; exit 2; }
export LUMA_FRAMEWORK_SOCKET="$SOCKET" LUMA_FRAMEWORK_RUN="$RUN"
"$DAEMON" >> "$LOG" 2>&1 &
CORE=$!
# A dead broker leaves the native loop unable to control brightness. Stop the
# native child so service.sh can restart both processes as one unit.
while kill -0 "$CORE" 2>/dev/null; do
  if ! kill -0 "$BROKER" 2>/dev/null; then
    echo '[LumaCurve服务] 框架亮度后端退出，重启整组引擎。' >> "$LOG"
    kill -TERM "$CORE" 2>/dev/null || :
    wait "$CORE" 2>/dev/null || :
    CORE=
    exit 2
  fi
  sleep 1
done
wait "$CORE" 2>/dev/null
RESULT=$?
CORE=
exit "$RESULT"
