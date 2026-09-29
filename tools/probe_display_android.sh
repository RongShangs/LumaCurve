#!/system/bin/sh
# Read-only display capture. No settings/chmod/brightness writes or daemon changes.
set -u
[ "$(id -u)" = 0 ] || { echo '请先执行 su，再运行采集脚本。' >&2; exit 1; }
PHASE=${1:-idle}
SECONDS_TO_CAPTURE=${2:-30}
PARENT=${3:-./luma-display-probe}
case "$PHASE" in ''|*[!a-zA-Z0-9_-]*) echo '阶段名只允许字母、数字、下划线和短横线。' >&2; exit 1;; esac
case "$SECONDS_TO_CAPTURE" in ''|*[!0-9]*) echo '采集秒数必须是整数。' >&2; exit 1;; esac
[ "$SECONDS_TO_CAPTURE" -ge 5 ] && [ "$SECONDS_TO_CAPTURE" -le 120 ] || {
  echo '采集时长必须为 5～120 秒。' >&2; exit 1;
}
OUT="$PARENT/$(date +%Y%m%d-%H%M%S)-$PHASE-$$"
mkdir -p "$OUT" || exit 1
NODE=/sys/class/backlight/panel0-backlight
STATE=/data/local/tmp/luma_curve_state
value() { if [ -r "$1" ]; then tr '\n\r' '  ' < "$1"; else printf NA; fi; }
snapshot() {
  label=$1
  {
    date; cat /proc/uptime; uname -a; id; getenforce 2>/dev/null
    for key in ro.product.model ro.product.device ro.build.version.release ro.build.version.sdk ro.build.version.incremental ro.build.fingerprint ro.hardware; do
      printf '%s=' "$key"; getprop "$key"
    done
    for key in screen_brightness_mode screen_brightness screen_auto_brightness_adj; do
      printf '%s=' "$key"; timeout 5 settings get system "$key" 2>&1
    done
    readlink -f "$NODE/brightness"; ls -lL "$NODE"/brightness "$NODE"/actual_brightness "$NODE"/max_brightness "$NODE"/bl_power 2>&1
  } > "$OUT/$label-metadata.txt"
  timeout 10 dumpsys display > "$OUT/$label-display.txt" 2>&1
  timeout 5 dumpsys power > "$OUT/$label-power.txt" 2>&1
  timeout 10 dumpsys SurfaceFlinger > "$OUT/$label-surfaceflinger.txt" 2>&1
  ps -A > "$OUT/$label-processes.txt" 2>&1
}
snapshot before
printf 'uptime_s,brightness,actual_brightness,bl_power\n' > "$OUT/backlight.csv"
read -r started unused < /proc/uptime
deadline=$(( ${started%%.*} + SECONDS_TO_CAPTURE ))
index=0
while :; do
  read -r stamp unused < /proc/uptime
  [ "${stamp%%.*}" -lt "$deadline" ] || break
  printf '%s,%s,%s,%s\n' "$stamp" "$(value "$NODE/brightness")" "$(value "$NODE/actual_brightness")" "$(value "$NODE/bl_power")" >> "$OUT/backlight.csv"
  if [ $((index % 10)) -eq 0 ]; then
    printf '\n--- uptime=%s ---\n' "$stamp" >> "$OUT/state.txt"
    cat "$STATE" >> "$OUT/state.txt" 2>/dev/null || :
  fi
  index=$((index + 1))
  sleep 0.1
done
snapshot after
echo "采集完成：$OUT"
echo '只读取设备状态；采样时间以 CSV 中的 uptime_s 为准。'
