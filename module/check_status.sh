#!/system/bin/sh
# LumaCurve 1.0.0 status helper

MODULE_ID=luma_curve
MODULE_VERSION=1.0.0
MODDIR=/data/adb/modules/$MODULE_ID
PID_FILE=/data/local/tmp/luma_curve.pid
LOG_FILE=/data/local/tmp/luma_curve.log
CONF_FILE=/data/local/tmp/luma_curve.conf
STATE_FILE=/data/local/tmp/luma_curve_state
LEARN_FILE=/data/local/tmp/luma_curve_learn
PERSIST_DIR=/data/adb/luma_curve
UPGRADE_STATUS=$PERSIST_DIR/upgrade_status
UPGRADE_LOG=$PERSIST_DIR/upgrade.log
UPGRADE_BACKUP=$PERSIST_DIR/upgrade_snapshot
LIVE_STATUS=$PERSIST_DIR/live_status
LIVE_BACKUP=$PERSIST_DIR/live
[ ! -r "$MODDIR/upgrade_data.sh" ] || . "$MODDIR/upgrade_data.sh"
BR_PATH=$(luma_backlight_path)
MAX_BR_PATH=
[ -z "$BR_PATH" ] || MAX_BR_PATH=${BR_PATH%/brightness}/max_brightness
LOG_RETENTION_DEFAULT_DAYS=3
LOG_RETENTION_MAX_DAYS=30

read_pid() {
  cat "$PID_FILE" 2>/dev/null
}

daemon_cmdline() {
  pid="$1"
  [ -n "$pid" ] || return 1
  [ -r "/proc/$pid/cmdline" ] || return 1
  tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null
}

is_daemon_running() {
  pid=$(read_pid)
  [ -n "$pid" ] || return 1
  [ -d "/proc/$pid" ] || return 1
  daemon_cmdline "$pid" | grep -q "luma_curve_daemon" || return 1
  kill -0 "$pid" 2>/dev/null
}

state_age() {
  updated=$(grep '^updated_unix=' "$STATE_FILE" 2>/dev/null | tail -n 1 | cut -d= -f2)
  now=$(date +%s 2>/dev/null || echo 0)
  if [ -n "$updated" ] && [ "$now" -gt 0 ] 2>/dev/null; then
    echo $((now - updated))
  else
    echo -1
  fi
}

print_prop() {
  key="$1"
  value=$(grep -m 1 "^${key}=" "$MODDIR/module.prop" 2>/dev/null | cut -d= -f2-)
  [ -n "$value" ] && echo "   $key=$value"
}

print_log_retention_status() {
  if ! grep -q '^log_retention_days=' "$CONF_FILE" 2>/dev/null; then
    echo "log_retention_days(raw)=<missing>"
    echo "log_retention_days(effective)=$LOG_RETENTION_DEFAULT_DAYS (default: key missing)"
    return
  fi

  retention_raw=$(sed -n 's/^log_retention_days=//p' "$CONF_FILE" 2>/dev/null | tail -n 1)
  if [ -z "$retention_raw" ]; then
    echo "log_retention_days(raw)=<empty>"
    echo "log_retention_days(effective)=$LOG_RETENTION_DEFAULT_DAYS (fallback: empty value)"
    return
  fi

  echo "log_retention_days(raw)=$retention_raw"
  case "$retention_raw" in
    *[!0-9]*)
      echo "log_retention_days(effective)=$LOG_RETENTION_DEFAULT_DAYS (fallback: invalid value)"
      return
      ;;
  esac

  retention_effective="$retention_raw"
  while [ "${retention_effective#0}" != "$retention_effective" ]; do
    retention_effective=${retention_effective#0}
  done
  [ -n "$retention_effective" ] || retention_effective=0
  if [ "$retention_effective" -ge 0 ] 2>/dev/null &&
     [ "$retention_effective" -le "$LOG_RETENTION_MAX_DAYS" ] 2>/dev/null; then
    if [ "$retention_effective" -eq 0 ]; then
      echo "log_retention_days(effective)=0 (age pruning disabled; backup count limit remains active)"
    else
      echo "log_retention_days(effective)=$retention_effective (configured)"
    fi
  else
    echo "log_retention_days(effective)=$LOG_RETENTION_DEFAULT_DAYS (fallback: outside 0-$LOG_RETENTION_MAX_DAYS)"
  fi
}

echo "iOS Auto Brightness $MODULE_VERSION Status"
echo "----------------------------------------"

echo "[Module]"
if [ -d "$MODDIR" ]; then
  echo "Dir: $MODDIR"
  print_prop version
  print_prop versionCode
  print_prop author
  if [ -f "$MODDIR/disable" ]; then
    echo "Status: disabled"
  else
    echo "Status: enabled"
  fi
else
  echo "Module dir not found: $MODDIR"
fi
echo ""

echo "[Persistent Data Upgrade]"
if [ -f "$UPGRADE_STATUS" ]; then
  grep -E '^(phase|mode|detail|updated_at|backup_dir)=' "$UPGRADE_STATUS" 2>/dev/null
  [ -f "$UPGRADE_BACKUP/manifest" ] && {
    preserved=$(wc -l < "$UPGRADE_BACKUP/manifest" 2>/dev/null || echo 0)
    echo "preserved_files=$preserved"
  }
else
  echo "No upgrade transaction recorded"
fi
[ -f "$UPGRADE_LOG" ] && tail -5 "$UPGRADE_LOG"
[ -f "$LIVE_STATUS" ] && grep -E '^(phase|reason|updated_at|live_dir)=' "$LIVE_STATUS" 2>/dev/null
[ -f "$LIVE_BACKUP/manifest" ] && {
  live_preserved=$(wc -l < "$LIVE_BACKUP/manifest" 2>/dev/null || echo 0)
  echo "live_files=$live_preserved"
}
echo ""

echo "[Daemon]"
pid=$(read_pid)
if is_daemon_running; then
  echo "Running: PID=$pid"
  echo "Command: $(daemon_cmdline "$pid")"
else
  echo "Not running"
  [ -n "$pid" ] && echo "Stale PID: $pid"
fi
echo ""

echo "[State File]"
age=$(state_age)
if [ -f "$STATE_FILE" ]; then
  echo "Path: $STATE_FILE"
  echo "Age: ${age}s"
  if [ "$age" -lt 0 ] 2>/dev/null || [ "$age" -gt 20 ] 2>/dev/null || ! is_daemon_running || [ -f "$MODDIR/disable" ]; then
    echo "Display: stale"
  else
    echo "Display: live"
  fi
  grep -E "^(version|mode|screen|lux|smooth|target_br|current_br|br_delta|poll_ms|brightness_owner|reason_primary|reason_chain|transition_active|write_governor_reason|low_lux_bright_spike_guard|zero_lux_suspect|thermal_guard_source|heat_guard_active|sunlight_active|hbm_active|fast_dark|fast_dark_candidate|dark_settle_left_ms|ownership|settings_read_source)=" "$STATE_FILE" 2>/dev/null
else
  echo "State file not found"
fi
echo ""

echo "[Brightness]"
cur=$(cat "$BR_PATH" 2>/dev/null || echo "?")
max=$(cat "$MAX_BR_PATH" 2>/dev/null || echo "?")
echo "Current: $cur / $max"
echo "Mode: $(/system/bin/settings get system screen_brightness_mode 2>/dev/null)"
echo "Slider: $(/system/bin/settings get system screen_brightness 2>/dev/null)/255"
echo ""

echo "[Config]"
if [ -f "$CONF_FILE" ]; then
  grep -E "^(config_version|alpha_|hyst_|min_step|learn_strength|thermal_|charging_heat_guard|high_lux_|log_max_bytes|log_max_backups)=" "$CONF_FILE"
  print_log_retention_status
else
  echo "Config not found: $CONF_FILE"
fi
echo ""

echo "[Learning]"
[ -f "$LEARN_FILE" ] && head -20 "$LEARN_FILE" || echo "No learning data"
echo ""

echo "[Recent Log]"
[ -f "$LOG_FILE" ] && tail -12 "$LOG_FILE" || echo "No log"
