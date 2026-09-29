#!/system/bin/sh
# LumaCurve 1.0.0 control helper
MODDIR=${0%/*}
DAEMON="$MODDIR/system/bin/luma_curve_daemon"
CONF_FILE=/data/local/tmp/luma_curve.conf
PID_FILE=/data/local/tmp/luma_curve.pid
LOG_FILE=/data/local/tmp/luma_curve.log
CMD_FILE=/data/local/tmp/luma_curve_cmd
PAUSE_FILE=/data/local/tmp/luma_curve.paused
UPGRADE_HELPER="$MODDIR/upgrade_data.sh"
LOG_RETENTION_DEFAULT_DAYS=3
LOG_RETENTION_MAX_DAYS=30
LOG_MAX_BACKUPS_DEFAULT=2
LOG_MAX_BACKUPS_LIMIT=10

if [ "$1" = current-log ]; then
  [ "$#" -eq 1 ] || exit 1
  current_boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null) || exit 1
  [ -n "$current_boot_id" ] || exit 1
  sh "$MODDIR/current_boot_log.sh" "$LOG_FILE" "$current_boot_id"
  exit $?
fi

[ ! -r "$MODDIR/core_status.sh" ] || . "$MODDIR/core_status.sh"
trap 'lc_core_description_refresh >/dev/null 2>&1 || :' 0

[ ! -r "$UPGRADE_HELPER" ] || . "$UPGRADE_HELPER"

refresh_persistent_state() {
  refresh_reason=${1:-ctl}
  command -v ios_live_refresh >/dev/null 2>&1 || {
    echo "Persistent-data helper unavailable"
    return 1
  }
  ios_live_refresh "$refresh_reason" || {
    echo "Persistent snapshot refresh failed; see /data/adb/luma_curve/upgrade.log"
    return 1
  }
}

daemon_pids() {
  pidof luma_curve_daemon 2>/dev/null
}

release_display_nodes_if_idle() {
  [ -z "$(daemon_pids)" ] || return 1
  [ ! -r "$MODDIR/framework-broker.jar" ] || return 0
  _lc_release_path=$(luma_backlight_path)
  [ -z "$_lc_release_path" ] || chmod 0644 "$_lc_release_path" 2>/dev/null
  hbm_node=$(sed -n 's/^hbm_node_path=//p' "$CONF_FILE" 2>/dev/null | tail -n 1)
  hbm_off=$(sed -n 's/^hbm_off_value=//p' "$CONF_FILE" 2>/dev/null | tail -n 1)
  [ -n "$hbm_off" ] || hbm_off=0
  if [ -n "$hbm_node" ] && [ -e "$hbm_node" ]; then
    printf '%s' "$hbm_off" > "$hbm_node" 2>/dev/null
  fi
  _lc_hbm_root=${_lc_release_path%/brightness}
  [ -n "$_lc_hbm_root" ] || _lc_hbm_root=/sys/class/backlight/.luma-unresolved
  for node in \
    "$_lc_hbm_root/hbm" \
    "$_lc_hbm_root/hbm_mode" \
    /sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/card0-DSI-1/hbm; do
    [ "$node" = "$hbm_node" ] && continue
    [ -e "$node" ] && printf '%s' "$hbm_off" > "$node" 2>/dev/null
  done
  return 0
}

daemon_count() {
  pids=$(daemon_pids)
  if [ -z "$pids" ]; then
    echo 0
  else
    echo "$pids" | wc -w
  fi
}

kill_all_daemons() {
  reason="${1:-unknown}"
  pids=$(daemon_pids)
  if [ -z "$pids" ]; then
    rm -f "$PID_FILE"
    release_display_nodes_if_idle
    return 0
  fi
  for pid in $pids; do
    kill -TERM "$pid" 2>/dev/null
  done
  sleep 1
  pids=$(daemon_pids)
  if [ -n "$pids" ]; then
    for pid in $pids; do
      kill -KILL "$pid" 2>/dev/null
    done
    sleep 1
  fi
  rm -f "$PID_FILE"
  pids=$(daemon_pids)
  [ -z "$pids" ] || { echo "daemon stop failed: $pids"; return 1; }
  release_display_nodes_if_idle
  return 0
}

background_daemon_now() {
  pid=$(daemon_pids)
  [ -n "$pid" ] || return 1
  for task in /proc/"$pid"/task/*; do
    [ -d "$task" ] || continue
    tid="${task%/}"; tid="${tid##*/}"
    [ -d /dev/cpuset/background ] && echo "$tid" > /dev/cpuset/background/tasks 2>/dev/null
    [ -d /dev/cpuctl/background ] && echo "$tid" > /dev/cpuctl/background/tasks 2>/dev/null
    [ -d /dev/stune/background ] && echo "$tid" > /dev/stune/background/tasks 2>/dev/null
  done
  command -v renice >/dev/null 2>&1 && renice -n 10 -p "$pid" >/dev/null 2>&1
  return 0
}

start_daemon_now() {
  [ -f "$PAUSE_FILE" ] && { echo "module is paused"; return 1; }
  [ -x "$DAEMON" ] || { echo "daemon binary missing"; return 1; }
  pids=$(daemon_pids)
  pid_count=$(daemon_count)
  if [ "$pid_count" -gt 1 ]; then
    kill_all_daemons "duplicate-before-start" || return 1
  elif [ "$pid_count" -eq 1 ]; then
    echo "$pids" > "$PID_FILE"
    background_daemon_now
    return 0
  fi
  [ -r "$MODDIR/daemon_launcher.sh" ] && [ -r "$MODDIR/process_scope.sh" ] || { echo 'daemon launcher missing'; return 1; }
  nohup sh "$MODDIR/daemon_launcher.sh" >> "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
  sleep 1
  if [ -r "$MODDIR/framework-broker.jar" ]; then
    startup_attempts=0
    while [ "$startup_attempts" -lt 5 ] && [ -z "$(daemon_pids)" ]; do
      startup_attempts=$((startup_attempts + 1)); sleep 1
    done
  fi
  pids=$(daemon_pids)
  [ "$(daemon_count)" -eq 1 ] || { echo "daemon start failed"; return 1; }
  echo "$pids" > "$PID_FILE"
  background_daemon_now
  return 0
}

conf_value() {
  cv_key="$1"
  cv_fallback="$2"
  cv_value=""
  while IFS= read -r cv_line || [ -n "$cv_line" ]; do
    case "$cv_line" in
      "${cv_key}="*) cv_value="${cv_line#*=}" ;;
    esac
  done < "$CONF_FILE" 2>/dev/null
  [ -n "$cv_value" ] && echo "$cv_value" || echo "$cv_fallback"
}

bounded_uint_config() {
  bu_value="$1"
  bu_fallback="$2"
  bu_min="$3"
  bu_max="$4"
  case "$bu_value" in
    ''|*[!0-9]*) echo "$bu_fallback"; return ;;
  esac
  while [ "${bu_value#0}" != "$bu_value" ]; do
    bu_value=${bu_value#0}
  done
  [ -n "$bu_value" ] || bu_value=0
  if [ "$bu_value" -ge "$bu_min" ] 2>/dev/null &&
     [ "$bu_value" -le "$bu_max" ] 2>/dev/null; then
    echo "$bu_value"
  else
    echo "$bu_fallback"
  fi
}

validated_log_retention_days() {
  vl_value="$1"
  case "$vl_value" in
    ''|*[!0-9]*) return 1 ;;
  esac
  while [ "${vl_value#0}" != "$vl_value" ]; do
    vl_value=${vl_value#0}
  done
  [ -n "$vl_value" ] || vl_value=0
  if [ "$vl_value" -ge 0 ] 2>/dev/null &&
     [ "$vl_value" -le "$LOG_RETENTION_MAX_DAYS" ] 2>/dev/null; then
    echo "$vl_value"
    return 0
  fi
  return 1
}

log_retention_days() {
  lr_value=$(conf_value log_retention_days "$LOG_RETENTION_DEFAULT_DAYS")
  bounded_uint_config "$lr_value" "$LOG_RETENTION_DEFAULT_DAYS" 0 "$LOG_RETENTION_MAX_DAYS"
}

log_max_backups() {
  lb_value=$(conf_value log_max_backups "$LOG_MAX_BACKUPS_DEFAULT")
  bounded_uint_config "$lb_value" "$LOG_MAX_BACKUPS_DEFAULT" 0 "$LOG_MAX_BACKUPS_LIMIT"
}

set_config_value_atomic() {
  (
    umask 077
    sc_key="$1"
    sc_value="$2"
    [ -f "$CONF_FILE" ] || { echo "Config not found: $CONF_FILE"; return 1; }

    sc_tmp="${CONF_FILE}.tmp.$$"
    sc_seen=0
    sc_write_ok=1
    rm -f "$sc_tmp"
    {
      while IFS= read -r sc_line || [ -n "$sc_line" ]; do
        case "$sc_line" in
          "${sc_key}="*)
            if [ "$sc_seen" -eq 0 ]; then
              printf '%s=%s\n' "$sc_key" "$sc_value" || sc_write_ok=0
              sc_seen=1
            fi
            ;;
          *) printf '%s\n' "$sc_line" || sc_write_ok=0 ;;
        esac
      done < "$CONF_FILE"
      if [ "$sc_seen" -eq 0 ]; then
        printf '%s=%s\n' "$sc_key" "$sc_value" || sc_write_ok=0
      fi
      [ "$sc_write_ok" -eq 1 ]
    } > "$sc_tmp" || { rm -f "$sc_tmp"; echo "Failed to prepare config update"; return 1; }

    chmod 0600 "$sc_tmp" 2>/dev/null || { rm -f "$sc_tmp"; echo "Failed to secure config update"; return 1; }
    mv -f "$sc_tmp" "$CONF_FILE" 2>/dev/null || { rm -f "$sc_tmp"; echo "Failed to replace config"; return 1; }
    chmod 0600 "$CONF_FILE" 2>/dev/null || { echo "Failed to secure config"; return 1; }
    return 0
  )
}

prune_log_backups() {
  pl_max_backups=$(log_max_backups)
  pl_retention_days=$(log_retention_days)
  pl_now_epoch=$(date +%s 2>/dev/null || echo 0)
  pl_retention_seconds=$((pl_retention_days * 86400))
  pl_removed=0
  pl_failed=0

  for pl_file in "${LOG_FILE}."[0-9]*; do
    [ -f "$pl_file" ] || continue
    pl_suffix=${pl_file#"$LOG_FILE".}
    case "$pl_suffix" in
      ''|*[!0-9]*) continue ;;
    esac

    pl_remove=0
    if [ "$pl_suffix" -gt "$pl_max_backups" ] 2>/dev/null; then
      pl_remove=1
    elif [ "$pl_retention_days" -gt 0 ] 2>/dev/null &&
         [ "$pl_now_epoch" -gt 0 ] 2>/dev/null; then
      pl_mtime=$(stat -c%Y "$pl_file" 2>/dev/null || echo 0)
      if [ "$pl_mtime" -gt 0 ] 2>/dev/null &&
         [ "$pl_now_epoch" -ge "$pl_mtime" ] 2>/dev/null &&
         [ $((pl_now_epoch - pl_mtime)) -ge "$pl_retention_seconds" ] 2>/dev/null; then
        pl_remove=1
      fi
    fi

    if [ "$pl_remove" -eq 1 ]; then
      if rm -f "$pl_file" 2>/dev/null; then
        pl_removed=$((pl_removed + 1))
      else
        pl_failed=$((pl_failed + 1))
      fi
    fi
  done

  PRUNED_LOG_COUNT=$pl_removed
  PRUNE_LOG_FAILURE_COUNT=$pl_failed
  [ "$pl_failed" -eq 0 ]
}

case "$1" in
  core-status)
    lc_core_status
    ;;
  pause)
    touch "$PAUSE_FILE"
    kill_all_daemons "pause" || exit 1
    refresh_persistent_state ctl_pause || exit 1
    echo "Paused"
    ;;
  resume)
    rm -f "$PAUSE_FILE"
    refresh_persistent_state ctl_resume || exit 1
    start_daemon_now || exit 1
    echo "Resumed"
    ;;
  restart)
    kill_all_daemons "restart" || exit 1
    rm -f "$PID_FILE"
    rm -f "$PAUSE_FILE"
    refresh_persistent_state ctl_restart || exit 1
    start_daemon_now || exit 1
    echo "Restarted"
    ;;
  status)
    sh /data/adb/modules/luma_curve/check_status.sh
    ;;
  reset-learn)
    pids=$(daemon_pids)
    pid_count=$(daemon_count)
    if [ "$pid_count" -gt 1 ]; then
      echo "duplicate daemon detected ($pid_count), restart required"
      exit 1
    fi
    if [ "$pid_count" -eq 1 ]; then
      kill -USR1 "$pids" 2>/dev/null || { echo "Failed to request learn reset"; exit 1; }
    else
      echo "daemon not running"; exit 1
    fi
    sleep 3
    refresh_persistent_state ctl_reset_learn || exit 1
    echo "Learn reset requested"
    ;;
  config)
    cat "$CONF_FILE" 2>/dev/null || echo "Config not found"
    ;;
  set-log-retention)
    [ "$#" -eq 2 ] || { echo "Usage: luma_curvectl.sh set-log-retention DAYS"; exit 1; }
    retention_days=$(validated_log_retention_days "$2") || {
      echo "DAYS must be 0 or an integer from 1 to $LOG_RETENTION_MAX_DAYS"
      exit 1
    }
    set_config_value_atomic log_retention_days "$retention_days" || exit 1
    refresh_persistent_state ctl_config_change || exit 1
    if [ "$retention_days" -eq 0 ]; then
      echo "Log age pruning disabled; log_max_backups still applies"
    else
      echo "Log retention set to $retention_days day(s)"
    fi
    ;;
  prune-logs)
    if prune_log_backups; then
      echo "Pruned $PRUNED_LOG_COUNT expired/excess log backup(s)"
    else
      echo "Pruned $PRUNED_LOG_COUNT log backup(s); $PRUNE_LOG_FAILURE_COUNT removal(s) failed"
      exit 1
    fi
    ;;
  clear-log)
    : > "$LOG_FILE" || { echo "Failed to clear active log"; exit 1; }
    echo "Active log cleared"
    ;;
  reload-config)
    refresh_persistent_state ui_config_save || exit 1
    pids=$(daemon_pids); pid_count=$(echo "$pids" | wc -w)
    if [ "$pid_count" -eq 1 ]; then
      kill -USR2 "$pids" 2>/dev/null || { echo "Failed to send USR2"; exit 1; }
      echo "Config reload sent; persistent snapshot refreshed"
    elif [ "$pid_count" -gt 1 ]; then
      echo "duplicate daemon detected ($pid_count), restart required"; exit 1
    else
      echo "daemon not running"; exit 1
    fi
    ;;
  reload-learn)
    refresh_persistent_state ui_learn_import || exit 1
    pids=$(daemon_pids); pid_count=$(echo "$pids" | wc -w)
    if [ "$pid_count" -eq 1 ]; then
      kill -HUP "$pids" 2>/dev/null || { echo "Failed to send SIGHUP"; exit 1; }
      echo "Learn reload sent; persistent snapshot refreshed"
    elif [ "$pid_count" -gt 1 ]; then
      echo "duplicate daemon detected ($pid_count), restart required"; exit 1
    else
      echo "daemon not running"; exit 1
    fi
    ;;
  backup-state)
    refresh_persistent_state ctl_manual || exit 1
    echo "Persistent snapshot refreshed"
    ;;
  *)
    echo "Usage: luma_curvectl.sh {pause|resume|restart|status|reset-learn|config|set-log-retention DAYS|prune-logs|clear-log|reload-config|reload-learn|backup-state}"
    exit 1
    ;;
esac
