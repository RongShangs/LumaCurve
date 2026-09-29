#!/system/bin/sh
# Read-only: never writes cgroups, settings, permissions or brightness.
set -u
[ "$(id -u)" = 0 ] || { echo '请先执行 su，再运行采集脚本。' >&2; exit 1; }
LABEL=${1:-foreground}
case "$LABEL" in ''|*[!a-zA-Z0-9_-]*) echo '阶段名只允许字母、数字、下划线和短横线。' >&2; exit 1;; esac
OUT="./luma-freezer-probe/$(date +%Y%m%d-%H%M%S)-$LABEL-$$"
mkdir -p "$OUT" || exit 1
date > "$OUT/environment.txt"
id >> "$OUT/environment.txt"
getenforce >> "$OUT/environment.txt" 2>&1
cat /proc/uptime >> "$OUT/environment.txt"
cat /proc/mounts > "$OUT/mounts.txt"
cat /proc/self/mountinfo > "$OUT/mountinfo.txt"
ps -A -o PID,PPID,NAME,ARGS > "$OUT/processes.txt" 2>&1
if ! grep -q 'PID' "$OUT/processes.txt"; then
  echo '系统不支持完整 ps 参数，仅采集当前 shell 与 daemon。' >> "$OUT/environment.txt"
  ps -A > "$OUT/processes.txt" 2>&1
  PIDS="$$ $(pidof luma_curve_daemon 2>/dev/null || :)"
else
  PIDS="$$ $(pidof luma_curve_daemon 2>/dev/null || :) $(awk '/luma_curve|LumaCurve|brightnesslock|service\.sh/ {if ($1 ~ /^[0-9]+$/) print $1}' "$OUT/processes.txt")"
fi
for PROC_PID in $(printf '%s\n' $PIDS | sort -nu); do
  [ -d "/proc/$PROC_PID" ] || continue
  {
    printf '\nPID=%s\ncmdline=' "$PROC_PID"
    tr '\000' ' ' < "/proc/$PROC_PID/cmdline" 2>/dev/null; printf '\n'
    for PROC_FILE in status cgroup wchan schedstat; do
      printf '\n[%s]\n' "$PROC_FILE"
      cat "/proc/$PROC_PID/$PROC_FILE" 2>&1
    done
    printf '\n[threads]\n'
    for THREAD_DIR in /proc/"$PROC_PID"/task/*; do
      [ -d "$THREAD_DIR" ] || continue
      printf '\nTID=%s wchan=' "${THREAD_DIR##*/}"
      cat "$THREAD_DIR/wchan" 2>&1; printf '\n'
      cat "$THREAD_DIR/cgroup" 2>&1
    done
    printf '\n[fd]\n'
    ls -l "/proc/$PROC_PID/fd" 2>&1
    CG_PATH=$(awk -F: '$1 == "0" && $2 == "" {print $3}' "/proc/$PROC_PID/cgroup")
    case "$CG_PATH" in /*) :;; *) continue;; esac
    for CG_MOUNT in $(awk '$3 == "cgroup2" {print $2}' /proc/mounts); do
      CG_BASE=$(readlink -f "$CG_MOUNT")
      CG_DIR=$(readlink -f "$CG_MOUNT$CG_PATH")
      [ -n "$CG_BASE" ] && [ -d "$CG_BASE" ] && [ -d "$CG_DIR" ] || continue
      case "$CG_DIR" in "$CG_BASE"|"$CG_BASE"/*) :;; *) echo '无法关联此 cgroup2 挂载，保留 mountinfo 供分析。'; continue;; esac
      while :; do
        printf '\n[cgroup %s]\n' "$CG_DIR"
        for CG_FILE in cgroup.freeze cgroup.events cgroup.type; do
          [ -r "$CG_DIR/$CG_FILE" ] || continue
          printf '%s=' "$CG_FILE"; cat "$CG_DIR/$CG_FILE"
        done
        [ "$CG_DIR" = "$CG_BASE" ] && break
        CG_DIR=${CG_DIR%/*}
      done
    done
  } > "$OUT/pid-$PROC_PID.txt" 2>&1
done
cp /data/local/tmp/luma_curve_state "$OUT/state.txt" 2>/dev/null || :
echo "采集完成：$OUT"
echo '本脚本仅取证，不解冻、不重启、不改亮度。'
