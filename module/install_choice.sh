#!/system/bin/sh
# Installer functions shared by KernelSU/Magisk customize and recovery entry.
LC_MODULES_DIR=${LC_MODULES_DIR:-/data/adb/modules}
LC_UPDATES_DIR=${LC_UPDATES_DIR:-/data/adb/modules_update}
LC_RUNTIME_DIR=${LC_RUNTIME_DIR:-/data/local/tmp}
LC_DATA_DIR=${LC_DATA_DIR:-/data/adb/luma_curve}
LC_LEGACY_DATA_DIR=${LC_LEGACY_DATA_DIR:-/data/adb/ios_auto_brightness}
LC_PROC_DIR=${LC_PROC_DIR:-/proc}
LC_GETEVENT=${LC_GETEVENT:-/system/bin/getevent}
LC_KEY_TIMEOUT=${LC_KEY_TIMEOUT:-60}
LC_REPLACE_OLD=0
LC_CREATED_FLAGS=""

lc_old_present() {
  [ -d "$LC_MODULES_DIR/ios_auto_brightness" ] || [ -d "$LC_UPDATES_DIR/ios_auto_brightness" ]
}

lc_volume_choice() {
  [ -x "$LC_GETEVENT" ] && command -v timeout >/dev/null 2>&1 || return 2
  _lc_deadline=$(($(date "+%s") + LC_KEY_TIMEOUT))
  while [ "$(date "+%s")" -lt "$_lc_deadline" ]; do
    _lc_key=$(timeout 1 "$LC_GETEVENT" -ql 2>/dev/null | awk '
      /EV_KEY/ && /DOWN/ && /KEY_VOLUMEUP/ { print "up"; exit }
      /EV_KEY/ && /DOWN/ && /KEY_VOLUMEDOWN/ { print "down"; exit }
      /0001[[:space:]]+0073[[:space:]]+00000001/ { print "up"; exit }
      /0001[[:space:]]+0072[[:space:]]+00000001/ { print "down"; exit }
    ')
    case "$_lc_key" in up) return 0 ;; down) return 1 ;; esac
    sleep 1
  done
  return 2
}

lc_check_legacy() {
  LC_REPLACE_OLD=0
  if ! lc_old_present; then
    ui_print "- 未检测到原版模块，可以继续安装。"
    return 0
  fi
  ui_print ""
  ui_print "检测到 iOS Auto Brightness 原版模块。"
  ui_print "两个亮度引擎不能同时接管屏幕，请选择："
  ui_print "  音量上键 (+)：卸载原版，继续安装流光亮度"
  ui_print "  音量下键 (-)：取消本次安装，保留原版"
  ui_print "旧配置将先备份；原版由管理器在重启时移除。"
  ui_print "原版卸载脚本也可能清理其伴生应用与旧运行数据。"
  ui_print "请在 ${LC_KEY_TIMEOUT} 秒内按键；超时会取消安装。"
  if lc_volume_choice; then
    LC_REPLACE_OLD=1
    ui_print "- 已选择继续。新模块准备完成后才会停用原版。"
    return 0
  else
    _lc_choice=$?
    if [ "$_lc_choice" -eq 1 ]; then
      ui_print "- 已取消安装，原版模块未作修改。"
    else
      ui_print "- 未读取到有效音量键，已取消安装，原版未作修改。"
      ui_print "  可在模块管理器中卸载原版后重新安装。"
    fi
    return 1
  fi
}

lc_backup_legacy_data() {
  _lc_stamp=$(date '+%Y%m%d-%H%M%S' 2>/dev/null) || return 1
  LC_LEGACY_BACKUP="$LC_DATA_DIR/legacy-backup/${_lc_stamp}.$$"
  (umask 077; mkdir -p "$LC_LEGACY_BACKUP") || return 1
  chmod 0700 "$LC_LEGACY_BACKUP" || return 1
  if [ -d "$LC_LEGACY_DATA_DIR" ]; then
    [ ! -L "$LC_LEGACY_DATA_DIR" ] || return 1
    cp -R "$LC_LEGACY_DATA_DIR" "$LC_LEGACY_BACKUP/persistent" || return 1
  fi
  for _lc_name in ios_brightness.conf ios_brightness_learn ios_brightness_learn_ledger; do
    if [ -f "$LC_RUNTIME_DIR/$_lc_name" ]; then
      cp "$LC_RUNTIME_DIR/$_lc_name" "$LC_LEGACY_BACKUP/$_lc_name" || return 1
      chmod 0600 "$LC_LEGACY_BACKUP/$_lc_name" || return 1
    fi
  done
  if [ -f "$LC_MODULES_DIR/ios_auto_brightness/module.prop" ]; then
    cp "$LC_MODULES_DIR/ios_auto_brightness/module.prop" "$LC_LEGACY_BACKUP/original-module.prop" || return 1
  fi
  return 0
}

lc_track_flag() {
  _lc_flag="$1"
  [ ! -L "$_lc_flag" ] || return 1
  [ -e "$_lc_flag" ] && return 0
  : > "$_lc_flag" || return 1
  LC_CREATED_FLAGS="${LC_CREATED_FLAGS}${_lc_flag}
"
}

lc_rollback_legacy_flags() {
  printf '%s' "$LC_CREATED_FLAGS" | while IFS= read -r _lc_flag; do
    [ -n "$_lc_flag" ] || continue
    rm -f "$_lc_flag" || exit 1
  done
  _lc_result=$?
  LC_CREATED_FLAGS=""
  return "$_lc_result"
}

lc_legacy_pid_matches() {
  _lc_pid="$1"
  case "$_lc_pid" in ''|*[!0-9]*) return 1 ;; esac
  [ -r "$LC_PROC_DIR/$_lc_pid/cmdline" ] || return 1
  _lc_cmd=$(tr '\000' ' ' < "$LC_PROC_DIR/$_lc_pid/cmdline" 2>/dev/null)
  case "$_lc_cmd" in
    *"/ios_auto_brightness/service.sh"*|*"/ios_brightness_daemon "*) return 0 ;;
  esac
  return 1
}

lc_stop_legacy_processes() {
  _lc_pids=""
  for _lc_proc in "$LC_PROC_DIR"/[0-9]*; do
    _lc_pid=${_lc_proc##*/}
    lc_legacy_pid_matches "$_lc_pid" || continue
    _lc_pids="$_lc_pids $_lc_pid"
    kill -TERM "$_lc_pid" 2>/dev/null || true
  done
  [ -n "$_lc_pids" ] || return 0
  sleep 1
  for _lc_pid in $_lc_pids; do
    lc_legacy_pid_matches "$_lc_pid" || continue
    kill -0 "$_lc_pid" 2>/dev/null || continue
    kill -KILL "$_lc_pid" 2>/dev/null || return 1
  done
  sleep 1
  for _lc_pid in $_lc_pids; do
    lc_legacy_pid_matches "$_lc_pid" && kill -0 "$_lc_pid" 2>/dev/null && return 1
  done
  return 0
}

lc_commit_legacy_removal() {
  [ "$LC_REPLACE_OLD" -eq 1 ] || return 0
  lc_old_present || return 0
  for _lc_old in "$LC_MODULES_DIR/ios_auto_brightness" "$LC_UPDATES_DIR/ios_auto_brightness"; do
    [ ! -L "$_lc_old" ] || return 1
  done
  lc_backup_legacy_data || { ui_print "- 旧配置备份失败，原版尚未停用。"; return 1; }
  for _lc_old in "$LC_MODULES_DIR/ios_auto_brightness" "$LC_UPDATES_DIR/ios_auto_brightness"; do
    [ -d "$_lc_old" ] || continue
    lc_track_flag "$_lc_old/disable" && lc_track_flag "$_lc_old/remove" || {
      lc_rollback_legacy_flags
      return 1
    }
  done
  lc_track_flag "$LC_RUNTIME_DIR/ios_brightness.paused" || { lc_rollback_legacy_flags; return 1; }
  lc_stop_legacy_processes || {
    lc_rollback_legacy_flags
    ui_print "- 原版进程无法停止，已撤销本次卸载标记。"
    return 1
  }
  ui_print "- 原版已停止并安排卸载，重启后由管理器移除。"
  ui_print "- 旧配置备份：$LC_LEGACY_BACKUP"
  return 0
}
