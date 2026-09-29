#!/system/bin/sh
# Finite independent supervisor, only experiment-owned PIDs may be signaled.
set -u
RUN=$1; OUT=$2; RESUME=$3; APP=$4
case "$RUN" in /data/local/tmp/luma-framework-core.*) :;; *) exit 2;; esac
HERE=${0%/*}
. "$HERE/process_scope.sh"
lc_scope_detach_self || { touch "$RUN/guard-failed"; exit 2; }
touch "$RUN/armed"
for TICK in $(seq 1 150); do
  [ -f "$RUN/test-finished" ] && break
  sleep 1
done
stop_owned() {
  _fc_pid=$(cat "$RUN/$1.pid" 2>/dev/null || :)
  case "$_fc_pid" in ''|*[!0-9]*) return;; esac
  _fc_cmd=$({ tr '\000' ' ' < "/proc/$_fc_pid/cmdline"; } 2>/dev/null || :)
  case "$_fc_cmd" in *"$RUN"*"luma_framework_core"*|*"LumaFrameworkOutputBroker "*"$RUN"*) :;; *) return;; esac
  kill -TERM "$_fc_pid" 2>/dev/null || :
  sleep 2
  _fc_cmd=$({ tr '\000' ' ' < "/proc/$_fc_pid/cmdline"; } 2>/dev/null || :)
  case "$_fc_cmd" in *"$RUN"*"luma_framework_core"*|*"LumaFrameworkOutputBroker "*"$RUN"*) kill -KILL "$_fc_pid" 2>/dev/null || :;; esac
}
echo 'WATCHDOG stopping isolated core and broker' >> "$OUT/recovery.txt"
stop_owned core
stop_owned broker
timeout 10 sh "$HERE/probe_temporary_runtime.sh" "$RUN/probe.jar" "$APP" release >> "$OUT/recovery.txt" 2>&1
RELEASE=$?
sleep 1
timeout 10 dumpsys display > "$OUT/released-display.txt" 2>&1
awk '/^Display Power Controller:$/ {n++;} n==1 {print;}' "$OUT/released-display.txt" > "$OUT/released-main.txt"
# Mode may deliberately be changed by the user: do not require or force AUTO.
if [ "$RELEASE" = 0 ] && grep -q '^RELEASE_REQUEST sent$' "$OUT/recovery.txt" &&
   grep -q 'mDisplayId=0' "$OUT/released-main.txt" &&
   grep -q 'mTemporaryScreenBrightness:NaN' "$OUT/released-main.txt"; then
  echo 'RESTORE temporary=verified_clear' >> "$OUT/recovery.txt"
  if [ "$RESUME" = 1 ]; then
    if ! sh /data/adb/modules/luma_curve/luma_curvectl.sh resume >> "$OUT/recovery.txt" 2>&1; then
      touch "$RUN/restore-failed"; exit 2
    fi
    echo 'RESTORE module=resume_command_completed' >> "$OUT/recovery.txt"
  else echo 'RESTORE module=originally_not_running' >> "$OUT/recovery.txt"; fi
  touch "$RUN/restored"
else
  echo 'RESTORE unconfirmed; module remains paused' >> "$OUT/recovery.txt"
  touch "$RUN/restore-failed"; exit 2
fi
