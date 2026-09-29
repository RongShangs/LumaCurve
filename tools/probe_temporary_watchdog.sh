#!/system/bin/sh
# Independent recovery process. Only the recorded experiment PID may be killed.
set -u
[ "$#" = 4 ] || exit 1
RUN=$1; OUT=$2; RESUME=$3; APP=$4
case "$RUN" in /data/local/tmp/luma-framework03.*) :;; *) exit 1;; esac
HERE=${0%/*}
. "$HERE/process_scope.sh"
if ! lc_scope_detach_self; then touch "$RUN/guard-failed"; exit 1; fi
touch "$RUN/armed"
for TICK in $(seq 1 45); do
  [ -f "$RUN/test-finished" ] && break
  sleep 1
done
if [ ! -f "$RUN/test-finished" ]; then
  echo 'WATCHDOG experiment deadline reached' >> "$OUT/recovery.txt"
  PID=$(cat "$RUN/test.pid" 2>/dev/null || :)
  case "$PID" in ''|*[!0-9]*) :;; *)
    # Verify PID still names this probe before signaling; no broad killall.
    if tr '\000' ' ' < "/proc/$PID/cmdline" 2>/dev/null | grep -q 'LumaFrameworkProbeTemporary test'; then
      kill -TERM "$PID" 2>/dev/null || :
      sleep 1
      if tr '\000' ' ' < "/proc/$PID/cmdline" 2>/dev/null | grep -q 'LumaFrameworkProbeTemporary test'; then kill -KILL "$PID" 2>/dev/null || :; fi
    fi;;
  esac
fi
timeout 10 sh "$HERE/probe_temporary_runtime.sh" "$RUN/probe.jar" "$APP" release >> "$OUT/recovery.txt" 2>&1
RELEASE=$?
sleep 1
timeout 10 dumpsys display > "$OUT/released-display.txt" 2>&1
awk '/^Display Power Controller:$/ {n++;} n==1 {print;}' "$OUT/released-display.txt" > "$OUT/released-main.txt"
if [ "$RELEASE" = 0 ] && grep -q '^RELEASE_REQUEST sent$' "$OUT/recovery.txt" &&
   grep -q 'mDisplayId=0' "$OUT/released-main.txt" &&
   grep -q 'mUseAutoBrightness=true' "$OUT/released-main.txt" &&
   ! grep -q 'mUseAutoBrightness=false' "$OUT/released-main.txt" &&
   grep -q 'mTemporaryScreenBrightness:NaN' "$OUT/released-main.txt"; then
  echo 'RESTORE temporary=verified_clear' >> "$OUT/recovery.txt"
  if [ "$RESUME" = 1 ]; then
    if sh /data/adb/modules/luma_curve/luma_curvectl.sh resume >> "$OUT/recovery.txt" 2>&1; then
      echo 'RESTORE module=resume_command_completed' >> "$OUT/recovery.txt"
    else touch "$RUN/restore-failed"; exit 2; fi
  fi
  touch "$RUN/restored"
else
  echo 'RESTORE unconfirmed; module remains paused' >> "$OUT/recovery.txt"
  touch "$RUN/restore-failed"
  exit 2
fi
