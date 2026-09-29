#!/system/bin/sh
# LumaCurve 1.0.0 service

MODDIR=${0%/*}
DAEMON="$MODDIR/system/bin/luma_curve_daemon"
PID_FILE=/data/local/tmp/luma_curve.pid
LOG_FILE=/data/local/tmp/luma_curve.log
CONF_FILE=/data/local/tmp/luma_curve.conf
PAUSE_FILE=/data/local/tmp/luma_curve.paused
STATE_FILE=/data/local/tmp/luma_curve_state
UPGRADE_HELPER="$MODDIR/upgrade_data.sh"
IOS_CONF_VERSION=11
LOG_RETENTION_DEFAULT_DAYS=7
LOG_RETENTION_MAX_DAYS=30
LOG_MAX_BYTES_DEFAULT=1048576
LOG_MAX_BACKUPS_DEFAULT=2
LOG_MAX_BACKUPS_LIMIT=10

if [ -r "$MODDIR/core_status.sh" ]; then
  . "$MODDIR/core_status.sh"
else
  lc_core_description_refresh() { return 1; }
  lc_core_watch_wait() { sleep 60; }
fi
lc_core_description_refresh || :

log_msg() {
  printf '[%s] [LumaCurve服务] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$1" >> "$LOG_FILE"
}

wait_boot_complete() {
  waited=0
  while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
    waited=$((waited + 2))
    [ "$waited" -ge 90 ] && return
  done
  sleep 3
}

check_conflicts() {
  for m in ios_auto_brightness autobrightness LiveDisplay lux-brightness adaptive-brightness brightness-controller dimmer cf.lumen twilight; do
    if [ -d "/data/adb/modules/$m" ] &&
       [ ! -f "/data/adb/modules/$m/disable" ] &&
       [ ! -f "/data/adb/modules/$m/remove" ]; then
      log_msg "检测到冲突模块： $m"
      touch "$MODDIR/disable"
      kill_all_daemons "active-conflict"
      log_msg "存在其他亮度模块，本模块已停用"
      return 1
    fi
  done
  return 0
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

count_daemon_pids() {
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
  log_msg "停止所有引擎：原因=$reason 进程=$pids"
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
    pids=$(daemon_pids)
  fi
  rm -f "$PID_FILE"
  if [ -n "$pids" ]; then
    log_msg "引擎停止失败： $pids"
    return 1
  fi
  release_display_nodes_if_idle
  return 0
}

reconcile_singleton() {
  pids=$(daemon_pids)
  pid_count=$(count_daemon_pids)

  if [ "$pid_count" -eq 0 ]; then
    rm -f "$PID_FILE"
    return 1
  fi

  if [ "$pid_count" -eq 1 ]; then
    pidfile_pid=$(cat "$PID_FILE" 2>/dev/null)
    if [ "$pidfile_pid" != "$pids" ]; then
      echo "$pids" > "$PID_FILE"
    fi
    return 0
  fi

  log_msg "检测到重复引擎： $pids"
  kill_all_daemons "duplicate"
  return 1
}

is_running() {
  reconcile_singleton
}

background_daemon() {
  pid=$(cat "$PID_FILE" 2>/dev/null)
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null || return
  for task in /proc/"$pid"/task/*; do
    [ -d "$task" ] || continue
    tid="${task%/}"; tid="${tid##*/}"
    [ -d /dev/cpuset/background ] && echo "$tid" >/dev/cpuset/background/tasks 2>/dev/null
    [ -d /dev/cpuctl/background ] && echo "$tid" >/dev/cpuctl/background/tasks 2>/dev/null
    [ -d /dev/stune/background ] && echo "$tid" >/dev/stune/background/tasks 2>/dev/null
  done
  command -v renice >/dev/null 2>&1 && renice -n 10 -p "$pid" >/dev/null 2>&1
}

start_daemon() {
  [ -f "$PAUSE_FILE" ] && { log_msg "模块已暂停，跳过启动"; return; }
  reconcile_singleton && { log_msg "引擎已运行，单实例检查通过"; return; }
  [ -f "$DAEMON" ] && [ -x "$DAEMON" ] || { log_msg "引擎文件缺失： $DAEMON"; return; }
  rm -f "$PID_FILE"
  [ -r "$MODDIR/daemon_launcher.sh" ] && [ -r "$MODDIR/process_scope.sh" ] || { log_msg "核心启动辅助脚本缺失"; return 1; }
  nohup sh "$MODDIR/daemon_launcher.sh" >> "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
  sleep 1
  if [ -r "$MODDIR/framework-broker.jar" ]; then
    attempts=0
    while [ "$attempts" -lt 5 ] && [ -z "$(daemon_pids)" ]; do
      attempts=$((attempts + 1)); sleep 1
    done
  fi
  if reconcile_singleton; then
    background_daemon
    log_msg "引擎已启动，PID=$(cat "$PID_FILE" 2>/dev/null)"
  else
    log_msg "引擎启动失败"
    rm -f "$PID_FILE"
  fi
}

conf_value() {
  _key="$1" _fallback="$2" _val=""
  while IFS= read -r _line || [ -n "$_line" ]; do
    case "$_line" in
      "${_key}="*) _val="${_line#*=}" ;;
    esac
  done < "$CONF_FILE" 2>/dev/null
  [ -n "$_val" ] && echo "$_val" || echo "$_fallback"
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

log_retention_days() {
  value=$(conf_value log_retention_days "$LOG_RETENTION_DEFAULT_DAYS")
  bounded_uint_config "$value" "$LOG_RETENTION_DEFAULT_DAYS" 0 "$LOG_RETENTION_MAX_DAYS"
}

log_max_bytes() {
  value=$(conf_value log_max_bytes "$LOG_MAX_BYTES_DEFAULT")
  bounded_uint_config "$value" "$LOG_MAX_BYTES_DEFAULT" 65536 16777216
}

log_max_backups() {
  value=$(conf_value log_max_backups "$LOG_MAX_BACKUPS_DEFAULT")
  bounded_uint_config "$value" "$LOG_MAX_BACKUPS_DEFAULT" 0 "$LOG_MAX_BACKUPS_LIMIT"
}

rotate_log() {
  [ -f "$LOG_FILE" ] || return
  size=$(stat -c%s "$LOG_FILE" 2>/dev/null || echo 0)
  max_bytes=$(log_max_bytes)
  max_backups=$(log_max_backups)
  [ "$size" -le "$max_bytes" ] 2>/dev/null && return

  tmp="${LOG_FILE}.tmp"
  keep_bytes=$((max_bytes / 2))
  rm -f "$tmp"
  if ! tail -c "$keep_bytes" "$LOG_FILE" > "$tmp" 2>/dev/null; then
    if ! tail -n 2000 "$LOG_FILE" > "$tmp" 2>/dev/null; then
      rm -f "$tmp"
      log_msg "跳过日志轮转：无法保留最近日志"
      return 1
    fi
  fi

  if [ "$max_backups" -gt 0 ]; then
    i=$max_backups
    while [ "$i" -gt 1 ]; do
      old=$((i - 1))
      if [ -f "${LOG_FILE}.${old}" ]; then
        if ! mv -f "${LOG_FILE}.${old}" "${LOG_FILE}.${i}" 2>/dev/null; then
          rm -f "$tmp"
          log_msg "跳过日志轮转：无法移动归档 $old to $i"
          return 1
        fi
      elif ! rm -f "${LOG_FILE}.${i}" 2>/dev/null; then
        rm -f "$tmp"
        log_msg "跳过日志轮转：无法准备归档位置 $i"
        return 1
      fi
      i=$old
    done
    if ! cp -f "$LOG_FILE" "${LOG_FILE}.1" 2>/dev/null; then
      rm -f "$tmp"
      log_msg "跳过日志轮转：无法归档当前日志"
      return 1
    fi
  fi

  if ! : > "$LOG_FILE"; then
    rm -f "$tmp"
    log_msg "跳过日志轮转：无法清空当前日志"
    return 1
  fi
  if [ -f "$tmp" ] && ! cat "$tmp" >> "$LOG_FILE"; then
    log_msg "日志轮转异常：最近日志恢复失败，保留恢复副本于 $tmp"
    return 1
  fi
  rm -f "$tmp"
  log_msg "日志已轮转（保留 ${max_backups} 份归档）"
}

prune_log_backups() {
  max_backups=$(log_max_backups)
  retention_days=$(log_retention_days)
  now_epoch=$(date +%s 2>/dev/null || echo 0)
  retention_seconds=$((retention_days * 86400))
  removed=0

  for file in "${LOG_FILE}."[0-9]*; do
    [ -f "$file" ] || continue
    suffix=${file#"$LOG_FILE".}
    case "$suffix" in
      ''|*[!0-9]*) continue ;;
    esac

    if [ "$suffix" -gt "$max_backups" ] 2>/dev/null; then
      rm -f "$file" 2>/dev/null && removed=$((removed + 1))
      continue
    fi

    [ "$retention_days" -gt 0 ] || continue
    [ "$now_epoch" -gt 0 ] 2>/dev/null || continue
    mtime=$(stat -c%Y "$file" 2>/dev/null || echo 0)
    [ "$mtime" -gt 0 ] 2>/dev/null || continue
    [ "$now_epoch" -ge "$mtime" ] 2>/dev/null || continue
    age=$((now_epoch - mtime))
    if [ "$age" -ge "$retention_seconds" ] 2>/dev/null; then
      rm -f "$file" 2>/dev/null && removed=$((removed + 1))
    fi
  done

  [ "$removed" -eq 0 ] || log_msg "已清理 $removed 份过期或超限日志"
}

maintain_logs() {
  rotate_log
  prune_log_backups
}

ensure_runtime_log_config() {
  [ -f "$CONF_FILE" ] || return
  grep -q '^log_retention_days=' "$CONF_FILE" 2>/dev/null && return
  {
    printf '\n# Rotated log retention: 1-30 days; 0 disables age-based pruning.\n'
    printf 'log_retention_days=%s\n' "$LOG_RETENTION_DEFAULT_DAYS"
  } >> "$CONF_FILE"
  log_msg "已添加日志保留配置 log_retention_days=$LOG_RETENTION_DEFAULT_DAYS"
}

sync_runtime_config() {
  if [ ! -r "$UPGRADE_HELPER" ]; then
    log_msg "持久化辅助脚本缺失： $UPGRADE_HELPER"
    return 1
  fi
  . "$UPGRADE_HELPER"
  if ios_upgrade_prepare_runtime "$MODDIR/luma_curve.conf"; then
    cur_ver=$(ios_config_version "$CONF_FILE")
    log_msg "运行配置已就绪，版本=${cur_ver:-unknown}"
    return 0
  fi
  log_msg "持久数据恢复或迁移失败，阻止引擎启动（详见 $IOS_UPGRADE_STATUS)"
  return 1
}

mkdir -p "$(dirname "$LOG_FILE")"
touch "$LOG_FILE"
sync_runtime_config || exit 0
ensure_runtime_log_config
ios_live_refresh_if_changed service_start || log_msg "持久快照更新延后，稍后重试"
maintain_logs
trap 'lc_core_description_refresh || :; log_msg "服务正在停止"; exit 0' INT TERM
wait_boot_complete

existing=$(getprop persist.sys.display.als_name 2>/dev/null)
[ -n "$existing" ] && log_msg "传感器属性： $existing" || log_msg "传感器属性为空"

check_conflicts || exit 0
start_daemon
lc_core_description_refresh || :
background_daemon

count=0
while true; do
  count=$((count + 1))
  if [ "$count" -ge 10 ]; then
    count=0
    maintain_logs
  fi
  if [ -f "$IOS_UPGRADE_PAUSE_MARKER" ] &&
     [ "$(ios_upgrade_status_value phase)" = complete ]; then
    ios_upgrade_release_pause && log_msg "已解除安装器遗留暂停"
  fi
  ios_live_refresh_if_changed service_periodic || log_msg "持久快照刷新延后"
  if [ -f "$PAUSE_FILE" ]; then
    [ $((count % 6)) -eq 1 ] && log_msg "模块已暂停，跳过引擎重启"
    lc_core_watch_wait
    continue
  fi
  if ! reconcile_singleton; then
    log_msg "正在重启引擎"
    start_daemon
  fi
  lc_core_watch_wait
done
