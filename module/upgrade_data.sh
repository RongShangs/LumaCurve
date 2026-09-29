#!/system/bin/sh

# Same policy as the engine: prefer the original valid panel, otherwise require
# exactly one readable, valid brightness/max_brightness pair. Never guess.
luma_backlight_valid() {
  _lc_bl_current=$(cat "$1/brightness" 2>/dev/null) || return 1
  _lc_bl_max=$(cat "$1/max_brightness" 2>/dev/null) || return 1
  case "$_lc_bl_current:$_lc_bl_max" in *[!0-9:]*|:*|*:) return 1 ;; esac
  [ "$_lc_bl_max" -gt 0 ] 2>/dev/null && [ "$_lc_bl_current" -le "$_lc_bl_max" ] 2>/dev/null
}
luma_backlight_path() {
  _lc_bl_root=${1:-/sys/class/backlight}
  if luma_backlight_valid "$_lc_bl_root/panel0-backlight"; then
    printf '%s/brightness\n' "$_lc_bl_root/panel0-backlight"; return 0
  fi
  _lc_bl_found= _lc_bl_count=0 _lc_bl_entries=0
  for _lc_bl_dir in "$_lc_bl_root"/*; do
    [ -d "$_lc_bl_dir" ] || continue
    _lc_bl_entries=$((_lc_bl_entries + 1))
    [ "$_lc_bl_entries" -le 254 ] || return 1
    if luma_backlight_valid "$_lc_bl_dir"; then
      _lc_bl_found=$_lc_bl_dir; _lc_bl_count=$((_lc_bl_count + 1))
      [ "$_lc_bl_count" -le 1 ] || return 1
    fi
  done
  [ "$_lc_bl_count" -eq 1 ] || return 1
  printf '%s/brightness\n' "$_lc_bl_found"
}
# Persistent-data upgrade transaction for LumaCurve 1.0.0.
# This file is sourced by both recovery installers and service.sh.

IOS_RUNTIME_DIR=${IOS_RUNTIME_DIR:-/data/local/tmp}
IOS_PERSIST_DIR=${IOS_PERSIST_DIR:-/data/adb/luma_curve}
IOS_UPGRADE_DIR=${IOS_UPGRADE_DIR:-$IOS_PERSIST_DIR/upgrade_snapshot}
IOS_UPGRADE_STATUS=${IOS_UPGRADE_STATUS:-$IOS_PERSIST_DIR/upgrade_status}
IOS_UPGRADE_LOG=${IOS_UPGRADE_LOG:-$IOS_PERSIST_DIR/upgrade.log}
IOS_UPGRADE_PAUSE_MARKER=${IOS_UPGRADE_PAUSE_MARKER:-$IOS_PERSIST_DIR/pause_owned}
IOS_LIVE_DIR=${IOS_LIVE_DIR:-$IOS_PERSIST_DIR/live}
IOS_LIVE_STATUS=${IOS_LIVE_STATUS:-$IOS_PERSIST_DIR/live_status}
IOS_LIVE_LOCK=${IOS_LIVE_LOCK:-$IOS_PERSIST_DIR/live.lock}
IOS_RUNTIME_CONFIG=${IOS_RUNTIME_CONFIG:-$IOS_RUNTIME_DIR/luma_curve.conf}
IOS_RUNTIME_PAUSE=${IOS_RUNTIME_PAUSE:-$IOS_RUNTIME_DIR/luma_curve.paused}
IOS_RUNTIME_PID=${IOS_RUNTIME_PID:-$IOS_RUNTIME_DIR/luma_curve.pid}
IOS_UPGRADE_LOG_MAX_BYTES=${IOS_UPGRADE_LOG_MAX_BYTES:-262144}
IOS_UPGRADE_LOG_KEEP_BYTES=${IOS_UPGRADE_LOG_KEEP_BYTES:-131072}

ios_upgrade_now() {
  date '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null || echo unknown
}

ios_upgrade_log() {
  mkdir -p "$IOS_PERSIST_DIR" 2>/dev/null || return 1
  chmod 0700 "$IOS_PERSIST_DIR" 2>/dev/null
  _ios_log_size=$(stat -c%s "$IOS_UPGRADE_LOG" 2>/dev/null || echo 0)
  if [ "$_ios_log_size" -gt "$IOS_UPGRADE_LOG_MAX_BYTES" ] 2>/dev/null; then
    _ios_log_tmp="${IOS_UPGRADE_LOG}.trim.$$"
    if tail -c "$IOS_UPGRADE_LOG_KEEP_BYTES" "$IOS_UPGRADE_LOG" > "$_ios_log_tmp" 2>/dev/null ||
       tail -n 1200 "$IOS_UPGRADE_LOG" > "$_ios_log_tmp" 2>/dev/null; then
      chmod 0600 "$_ios_log_tmp" 2>/dev/null
      mv -f "$_ios_log_tmp" "$IOS_UPGRADE_LOG" 2>/dev/null || rm -f "$_ios_log_tmp"
    else
      rm -f "$_ios_log_tmp" 2>/dev/null
    fi
  fi
  printf '[%s] %s\n' "$(ios_upgrade_now)" "$1" >> "$IOS_UPGRADE_LOG" 2>/dev/null
}

ios_upgrade_mode() {
  if [ -f "$IOS_UPGRADE_DIR/mode" ]; then
    sed -n '1p' "$IOS_UPGRADE_DIR/mode" 2>/dev/null
  else
    echo unknown
  fi
}

ios_upgrade_status_value() {
  _ios_status_key="$1"
  _ios_status_value=""
  while IFS= read -r _ios_status_line || [ -n "$_ios_status_line" ]; do
    case "$_ios_status_line" in
      "${_ios_status_key}="*) _ios_status_value=${_ios_status_line#*=} ;;
    esac
  done < "$IOS_UPGRADE_STATUS" 2>/dev/null
  printf '%s\n' "$_ios_status_value"
}

ios_upgrade_write_status() (
  umask 077
  _ios_phase="$1"
  _ios_detail="$2"
  _ios_tmp="${IOS_UPGRADE_STATUS}.tmp.$$"
  _ios_mode=$(ios_upgrade_mode)
  [ -n "$_ios_mode" ] || _ios_mode=unknown
  mkdir -p "$IOS_PERSIST_DIR" 2>/dev/null || exit 1
  chmod 0700 "$IOS_PERSIST_DIR" 2>/dev/null
  {
    echo "format_version=1"
    echo "phase=$_ios_phase"
    echo "mode=$_ios_mode"
    echo "detail=$_ios_detail"
    echo "updated_at=$(ios_upgrade_now)"
    echo "backup_dir=$IOS_UPGRADE_DIR"
  } > "$_ios_tmp" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
  chmod 0600 "$_ios_tmp" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
  mv -f "$_ios_tmp" "$IOS_UPGRADE_STATUS" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
)

ios_upgrade_fail() {
  ios_upgrade_log "操作失败： $1"
  ios_upgrade_write_status failed "$1"
  return 1
}

ios_upgrade_fail_precommit() {
  ios_upgrade_log "快照提交前失败： $1"
  ios_upgrade_write_status complete "precommit_failed_$1"
  return 1
}

ios_atomic_copy() (
  umask 077
  _ios_src="$1"
  _ios_dst="$2"
  _ios_mode=${3:-0600}
  _ios_parent=${_ios_dst%/*}
  [ "$_ios_parent" != "$_ios_dst" ] || _ios_parent=.
  _ios_tmp="${_ios_dst}.tmp.$$"
  [ -f "$_ios_src" ] || exit 1
  mkdir -p "$_ios_parent" 2>/dev/null || exit 1
  rm -f "$_ios_tmp" 2>/dev/null
  cp -f "$_ios_src" "$_ios_tmp" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
  chmod "$_ios_mode" "$_ios_tmp" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
  mv -f "$_ios_tmp" "$_ios_dst" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
)

ios_atomic_touch() (
  umask 077
  _ios_dst="$1"
  _ios_tmp="${_ios_dst}.tmp.$$"
  : > "$_ios_tmp" 2>/dev/null || exit 1
  chmod 0600 "$_ios_tmp" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
  mv -f "$_ios_tmp" "$_ios_dst" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
)

ios_live_lock_acquire() {
  mkdir -p "$IOS_PERSIST_DIR" 2>/dev/null || return 1
  chmod 0700 "$IOS_PERSIST_DIR" 2>/dev/null
  _ios_lock_attempt=0
  while ! mkdir "$IOS_LIVE_LOCK" 2>/dev/null; do
    _ios_lock_owner=$(cat "$IOS_LIVE_LOCK/pid" 2>/dev/null)
    _ios_lock_now=$(date +%s 2>/dev/null || echo 0)
    _ios_lock_mtime=$(stat -c%Y "$IOS_LIVE_LOCK" 2>/dev/null || echo 0)
    _ios_lock_stale=0
    if [ "$_ios_lock_now" -gt 0 ] 2>/dev/null &&
       [ "$_ios_lock_mtime" -gt 0 ] 2>/dev/null &&
       [ $((_ios_lock_now - _ios_lock_mtime)) -ge 30 ] 2>/dev/null; then
      _ios_lock_stale=1
    else
      case "$_ios_lock_owner" in
        ''|*[!0-9]*) ;;
        *) kill -0 "$_ios_lock_owner" 2>/dev/null || _ios_lock_stale=1 ;;
      esac
    fi
    if [ "$_ios_lock_stale" -eq 1 ]; then
      rm -rf "$IOS_LIVE_LOCK" 2>/dev/null
      continue
    fi
    _ios_lock_attempt=$((_ios_lock_attempt + 1))
    [ "$_ios_lock_attempt" -lt 6 ] || return 1
    sleep 1
  done
  printf '%s\n' "$$" > "$IOS_LIVE_LOCK/pid" 2>/dev/null || {
    rm -rf "$IOS_LIVE_LOCK" 2>/dev/null
    return 1
  }
  chmod 0700 "$IOS_LIVE_LOCK" 2>/dev/null
  chmod 0600 "$IOS_LIVE_LOCK/pid" 2>/dev/null
  return 0
}

ios_live_lock_release() {
  _ios_lock_owner=$(cat "$IOS_LIVE_LOCK/pid" 2>/dev/null)
  [ "$_ios_lock_owner" = "$$" ] || return 0
  rm -rf "$IOS_LIVE_LOCK" 2>/dev/null
}

ios_upgrade_data_names() {
  echo luma_curve.conf
  echo luma_curve_learn
  echo luma_curve_presets.json
  echo luma_curve_preference
  echo luma_curve_learn_ledger
  echo luma_curve.paused
}

ios_file_signature() {
  _ios_sig_file="$1"
  if [ -f "$_ios_sig_file" ]; then
    _ios_sig_size=$(stat -c%s "$_ios_sig_file" 2>/dev/null || echo -1)
    _ios_sig_mtime=$(stat -c%Y "$_ios_sig_file" 2>/dev/null || echo -1)
    printf '%s:%s\n' "$_ios_sig_size" "$_ios_sig_mtime"
  else
    echo missing
  fi
}

ios_runtime_signature() {
  for _ios_sig_name in $(ios_upgrade_data_names); do
    if [ "$_ios_sig_name" = luma_curve.paused ] && [ -f "$IOS_UPGRADE_PAUSE_MARKER" ]; then
      echo "$_ios_sig_name:installer-owned"
    else
      echo "$_ios_sig_name:$(ios_file_signature "$IOS_RUNTIME_DIR/$_ios_sig_name")"
    fi
  done
}

ios_live_write_status() (
  umask 077
  _ios_reason="$1"
  _ios_tmp="${IOS_LIVE_STATUS}.tmp.$$"
  mkdir -p "$IOS_PERSIST_DIR" 2>/dev/null || exit 1
  {
    echo "format_version=1"
    echo "phase=ready"
    echo "reason=$_ios_reason"
    echo "updated_at=$(ios_upgrade_now)"
    echo "live_dir=$IOS_LIVE_DIR"
  } > "$_ios_tmp" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
  chmod 0600 "$_ios_tmp" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
  mv -f "$_ios_tmp" "$IOS_LIVE_STATUS" 2>/dev/null || { rm -f "$_ios_tmp"; exit 1; }
)

ios_live_refresh() (
  umask 077
  _ios_reason=${1:-manual}
  _ios_phase=$(ios_upgrade_status_value phase)
  case "$_ios_phase" in
    ''|complete) ;;
    *) [ "$_ios_reason" = upgrade_complete ] || {
         ios_upgrade_log "当前阶段跳过快照：$_ios_phase"
         return 1
       } ;;
  esac
  ios_live_lock_acquire || {
    ios_upgrade_log "快照延后：其他刷新正在使用锁"
    return 1
  }
  trap 'ios_live_lock_release' 0
  trap 'exit 1' HUP INT TERM
  ios_live_restore_missing runtime_data_only || return 1

  _ios_stage="${IOS_LIVE_DIR}.new.$$"
  _ios_old="${IOS_LIVE_DIR}.old.$$"
  rm -rf "$_ios_stage" "$_ios_old" 2>/dev/null
  mkdir -p "$_ios_stage" 2>/dev/null || return 1
  : > "$_ios_stage/manifest" || { rm -rf "$_ios_stage"; return 1; }
  : > "$_ios_stage/source_signatures" || { rm -rf "$_ios_stage"; return 1; }
  ios_runtime_signature > "$_ios_stage/runtime_signature.candidate" || {
    rm -rf "$_ios_stage" 2>/dev/null
    return 1
  }
  _ios_count=0
  for _ios_name in $(ios_upgrade_data_names); do
    if [ "$_ios_name" = luma_curve.paused ] && [ -f "$IOS_UPGRADE_PAUSE_MARKER" ]; then
      continue
    fi
    _ios_src="$IOS_RUNTIME_DIR/$_ios_name"
    [ -f "$_ios_src" ] || continue
    _ios_before=$(ios_file_signature "$_ios_src")
    ios_atomic_copy "$_ios_src" "$_ios_stage/$_ios_name" 0600 || {
      rm -rf "$_ios_stage" 2>/dev/null
      ios_upgrade_log "快照复制失败： $_ios_name"
      return 1
    }
    echo "$_ios_name|$_ios_before" >> "$_ios_stage/source_signatures" || {
      rm -rf "$_ios_stage" 2>/dev/null
      return 1
    }
    echo "$_ios_name" >> "$_ios_stage/manifest" || {
      rm -rf "$_ios_stage" 2>/dev/null
      return 1
    }
    _ios_count=$((_ios_count + 1))
  done

  [ "$_ios_count" -eq 0 ] || sleep 1
  while IFS='|' read -r _ios_name _ios_before || [ -n "$_ios_name" ]; do
    [ -n "$_ios_name" ] || continue
    _ios_after=$(ios_file_signature "$IOS_RUNTIME_DIR/$_ios_name")
    if [ "$_ios_before" != "$_ios_after" ]; then
      rm -rf "$_ios_stage" 2>/dev/null
      ios_upgrade_log "快照延后： $_ios_name 在复制期间发生变化"
      return 1
    fi
  done < "$_ios_stage/source_signatures"

  if [ -f "$_ios_stage/luma_curve.conf" ] &&
     ! grep -q '^config_version=' "$_ios_stage/luma_curve.conf" 2>/dev/null; then
    rm -rf "$_ios_stage" 2>/dev/null
    ios_upgrade_log "快照被拒绝：配置不完整"
    return 1
  fi
  if [ -f "$_ios_stage/luma_curve_learn" ] &&
     { ! grep -q '^version=[0-9][0-9]*$' "$_ios_stage/luma_curve_learn" 2>/dev/null ||
       ! grep -q '^global_offset=' "$_ios_stage/luma_curve_learn" 2>/dev/null; }; then
    rm -rf "$_ios_stage" 2>/dev/null
    ios_upgrade_log "快照被拒绝：学习文件不完整"
    return 1
  fi
  _ios_verified_signature=$(ios_runtime_signature)
  _ios_candidate_signature=$(cat "$_ios_stage/runtime_signature.candidate" 2>/dev/null)
  if [ "$_ios_verified_signature" != "$_ios_candidate_signature" ]; then
    rm -rf "$_ios_stage" 2>/dev/null
    ios_upgrade_log "快照延后： 提交前运行版本发生变化"
    return 1
  fi
  mv -f "$_ios_stage/runtime_signature.candidate" "$_ios_stage/runtime_signature" 2>/dev/null || {
    rm -rf "$_ios_stage" 2>/dev/null
    return 1
  }

  if [ -d "$IOS_LIVE_DIR" ]; then
    mv "$IOS_LIVE_DIR" "$_ios_old" 2>/dev/null || { rm -rf "$_ios_stage"; return 1; }
  fi
  if ! mv "$_ios_stage" "$IOS_LIVE_DIR" 2>/dev/null; then
    [ -d "$_ios_old" ] && mv "$_ios_old" "$IOS_LIVE_DIR" 2>/dev/null
    return 1
  fi
  rm -rf "$_ios_old" 2>/dev/null
  chmod 0700 "$IOS_LIVE_DIR" 2>/dev/null
  ios_live_write_status "$_ios_reason" || return 1
  ios_upgrade_log "持久快照已刷新（原因=$_ios_reason 文件数=$_ios_count)"
  return 0
)

ios_live_refresh_if_changed() {
  _ios_current=$(ios_runtime_signature)
  _ios_saved=$(cat "$IOS_LIVE_DIR/runtime_signature" 2>/dev/null)
  [ -n "$_ios_saved" ] && [ "$_ios_current" = "$_ios_saved" ] && return 0
  ios_live_refresh "${1:-changed}"
}

ios_live_restore_missing() {
  _ios_restore_scope=${1:-runtime_data_only}
  [ -f "$IOS_LIVE_DIR/manifest" ] || return 0
  while IFS= read -r _ios_name || [ -n "$_ios_name" ]; do
    case "$_ios_name" in
      luma_curve.conf|luma_curve_learn|luma_curve_presets.json|luma_curve_preference|luma_curve_learn_ledger|luma_curve.paused) ;;
      *) ios_upgrade_log "恢复被拒绝：清单项无效"; return 1 ;;
    esac
    if [ "$_ios_name" = luma_curve.paused ] && [ "$_ios_restore_scope" != include_pause ]; then
      continue
    fi
    [ -f "$IOS_RUNTIME_DIR/$_ios_name" ] && continue
    [ -f "$IOS_LIVE_DIR/$_ios_name" ] || return 1
    ios_atomic_copy "$IOS_LIVE_DIR/$_ios_name" "$IOS_RUNTIME_DIR/$_ios_name" 0600 || return 1
    ios_upgrade_log "已从快照恢复缺失的运行文件： $_ios_name"
  done < "$IOS_LIVE_DIR/manifest"
  return 0
}

ios_upgrade_stop_service_loop() {
  _ios_service_pids=""
  for _ios_proc in /proc/[0-9]*; do
    [ -r "$_ios_proc/cmdline" ] || continue
    _ios_cmdline=$(tr '\000' ' ' < "$_ios_proc/cmdline" 2>/dev/null)
    case "$_ios_cmdline" in
      *"/luma_curve/service.sh"*)
        _ios_service_pid=${_ios_proc##*/}
        _ios_service_pids="$_ios_service_pids $_ios_service_pid"
        kill -TERM "$_ios_service_pid" 2>/dev/null
        ;;
    esac
  done
  [ -n "$_ios_service_pids" ] || return 0
  sleep 1
  for _ios_service_pid in $_ios_service_pids; do
    kill -0 "$_ios_service_pid" 2>/dev/null && kill -KILL "$_ios_service_pid" 2>/dev/null
  done
  return 0
}

ios_upgrade_quiesce_daemon() {
  ios_upgrade_stop_service_loop
  _ios_pids=$(pidof luma_curve_daemon 2>/dev/null)
  if [ -n "$_ios_pids" ]; then
    ios_upgrade_log "快照前停止引擎： $_ios_pids"
    for _ios_pid in $_ios_pids; do kill -TERM "$_ios_pid" 2>/dev/null; done
    _ios_wait=0
    while [ "$_ios_wait" -lt 4 ] && [ -n "$(pidof luma_curve_daemon 2>/dev/null)" ]; do
      sleep 1
      _ios_wait=$((_ios_wait + 1))
    done
    _ios_pids=$(pidof luma_curve_daemon 2>/dev/null)
    if [ -n "$_ios_pids" ]; then
      for _ios_pid in $_ios_pids; do kill -KILL "$_ios_pid" 2>/dev/null; done
      sleep 1
    fi
  fi
  rm -f "$IOS_RUNTIME_PID" 2>/dev/null
  _lc_release_path=$(luma_backlight_path)
  [ -z "$_lc_release_path" ] || chmod 0644 "$_lc_release_path" 2>/dev/null
  sleep 1
  [ -z "$(pidof luma_curve_daemon 2>/dev/null)" ] || return 1
  return 0
}

ios_upgrade_acquire_pause() {
  if [ -f "$IOS_UPGRADE_PAUSE_MARKER" ]; then
    [ -f "$IOS_RUNTIME_PAUSE" ] || ios_atomic_touch "$IOS_RUNTIME_PAUSE" || return 1
    return 0
  fi
  if [ -f "$IOS_RUNTIME_PAUSE" ]; then
    return 0
  fi
  ios_atomic_touch "$IOS_RUNTIME_PAUSE" || return 1
  ios_atomic_touch "$IOS_UPGRADE_PAUSE_MARKER" || {
    rm -f "$IOS_RUNTIME_PAUSE" 2>/dev/null
    return 1
  }
}

ios_upgrade_release_pause() {
  [ -f "$IOS_UPGRADE_PAUSE_MARKER" ] || return 0
  rm -f "$IOS_RUNTIME_PAUSE" 2>/dev/null || return 1
  [ ! -e "$IOS_RUNTIME_PAUSE" ] || return 1
  if ! rm -f "$IOS_UPGRADE_PAUSE_MARKER" 2>/dev/null ||
     [ -e "$IOS_UPGRADE_PAUSE_MARKER" ]; then
    ios_atomic_touch "$IOS_RUNTIME_PAUSE" >/dev/null 2>&1
    return 1
  fi
  return 0
}

ios_upgrade_begin() {
  umask 077
  _ios_previous_phase=$(ios_upgrade_status_value phase)
  case "$_ios_previous_phase" in
    ''|complete) ;;
    *)
      ios_upgrade_log "新升级前恢复上次事务，阶段=$_ios_previous_phase"
      ios_upgrade_restore || return 1
      ios_upgrade_release_pause
      ios_upgrade_write_status complete recovered_before_new_begin || return 1
      ;;
  esac

  _ios_stage="${IOS_UPGRADE_DIR}.new.$$"
  _ios_old="${IOS_UPGRADE_DIR}.old.$$"
  rm -rf "$_ios_stage" "$_ios_old" 2>/dev/null
  mkdir -p "$_ios_stage" 2>/dev/null || return 1
  : > "$_ios_stage/manifest" || { rm -rf "$_ios_stage"; return 1; }

  if ! ios_live_restore_missing include_pause; then
    rm -rf "$_ios_stage" 2>/dev/null
    ios_upgrade_fail_precommit live_restore_before_upgrade_failed
    return 1
  fi

  _ios_mode=fresh
  for _ios_name in $(ios_upgrade_data_names); do
    if [ -f "$IOS_RUNTIME_DIR/$_ios_name" ]; then
      if [ "$_ios_name" != "luma_curve.paused" ] || [ ! -f "$IOS_UPGRADE_PAUSE_MARKER" ]; then
        _ios_mode=upgrade
      fi
    fi
  done
  echo "$_ios_mode" > "$_ios_stage/mode" || { rm -rf "$_ios_stage"; return 1; }

  if ! ios_upgrade_acquire_pause; then
    rm -rf "$_ios_stage" 2>/dev/null
    ios_upgrade_fail_precommit pause_acquire_failed
    return 1
  fi
  if ! ios_upgrade_quiesce_daemon; then
    rm -rf "$_ios_stage" 2>/dev/null
    ios_upgrade_release_pause
    ios_upgrade_fail_precommit daemon_stop_failed
    return 1
  fi

  for _ios_name in $(ios_upgrade_data_names); do
    _ios_src="$IOS_RUNTIME_DIR/$_ios_name"
    if [ -f "$_ios_src" ]; then
      if [ "$_ios_name" = "luma_curve.paused" ] && [ -f "$IOS_UPGRADE_PAUSE_MARKER" ]; then
        continue
      fi
      if ! ios_atomic_copy "$_ios_src" "$_ios_stage/$_ios_name" 0600; then
        rm -rf "$_ios_stage" 2>/dev/null
        ios_upgrade_release_pause
        ios_upgrade_fail_precommit "backup_failed_$_ios_name"
        return 1
      fi
      echo "$_ios_name" >> "$_ios_stage/manifest" || {
        rm -rf "$_ios_stage" 2>/dev/null
        ios_upgrade_release_pause
        ios_upgrade_fail_precommit manifest_write_failed
        return 1
      }
    fi
  done
  [ -f "$IOS_UPGRADE_PAUSE_MARKER" ] && echo temporary_pause=1 > "$_ios_stage/temporary_pause"

  if [ -d "$IOS_UPGRADE_DIR" ]; then
    mv "$IOS_UPGRADE_DIR" "$_ios_old" 2>/dev/null || {
      rm -rf "$_ios_stage" 2>/dev/null
      ios_upgrade_release_pause
      ios_upgrade_fail_precommit previous_backup_move_failed
      return 1
    }
  fi
  if ! mv "$_ios_stage" "$IOS_UPGRADE_DIR" 2>/dev/null; then
    [ -d "$_ios_old" ] && mv "$_ios_old" "$IOS_UPGRADE_DIR" 2>/dev/null
    ios_upgrade_release_pause
    ios_upgrade_fail_precommit backup_commit_failed
    return 1
  fi
  rm -rf "$_ios_old" 2>/dev/null
  if ! ios_upgrade_write_status backup_ready snapshot_complete; then
    ios_upgrade_release_pause
    ios_upgrade_fail_precommit backup_status_write_failed
    return 1
  fi
  ios_upgrade_log "持久数据快照已就绪（模式=$_ios_mode)"
  return 0
}

ios_upgrade_refresh_backup() {
  [ -f "$IOS_UPGRADE_DIR/manifest" ] || return 0
  if ! ios_upgrade_quiesce_daemon; then
    ios_upgrade_fail daemon_restop_failed
    return 1
  fi
  while IFS= read -r _ios_name || [ -n "$_ios_name" ]; do
    case "$_ios_name" in
      luma_curve.conf|luma_curve_learn|luma_curve_presets.json|luma_curve_preference|luma_curve_learn_ledger|luma_curve.paused) ;;
      *) ios_upgrade_fail invalid_manifest_entry; return 1 ;;
    esac
    [ -f "$IOS_RUNTIME_DIR/$_ios_name" ] || continue
    ios_atomic_copy "$IOS_RUNTIME_DIR/$_ios_name" "$IOS_UPGRADE_DIR/$_ios_name" 0600 || {
      ios_upgrade_fail "backup_refresh_failed_$_ios_name"
      return 1
    }
  done < "$IOS_UPGRADE_DIR/manifest"
  ios_upgrade_write_status backup_ready snapshot_refreshed || return 1
  return 0
}

ios_upgrade_restore() {
  [ -f "$IOS_UPGRADE_DIR/manifest" ] || return 0
  ios_upgrade_write_status restoring persistent_data || return 1
  while IFS= read -r _ios_name || [ -n "$_ios_name" ]; do
    case "$_ios_name" in
      luma_curve.conf|luma_curve_learn|luma_curve_presets.json|luma_curve_preference|luma_curve_learn_ledger|luma_curve.paused) ;;
      *) ios_upgrade_fail invalid_manifest_entry; return 1 ;;
    esac
    [ -f "$IOS_UPGRADE_DIR/$_ios_name" ] || {
      ios_upgrade_fail "backup_missing_$_ios_name"
      return 1
    }
    ios_atomic_copy "$IOS_UPGRADE_DIR/$_ios_name" "$IOS_RUNTIME_DIR/$_ios_name" 0600 || {
      ios_upgrade_fail "restore_failed_$_ios_name"
      return 1
    }
  done < "$IOS_UPGRADE_DIR/manifest"
  return 0
}

ios_config_find_value() {
  IOS_CONFIG_FOUND=0
  IOS_CONFIG_VALUE=""
  _ios_find_file="$1"
  _ios_find_key="$2"
  while IFS= read -r _ios_find_line || [ -n "$_ios_find_line" ]; do
    case "$_ios_find_line" in
      "${_ios_find_key}="*)
        IOS_CONFIG_FOUND=1
        IOS_CONFIG_VALUE=${_ios_find_line#*=}
        ;;
    esac
  done < "$_ios_find_file" 2>/dev/null
}

ios_config_version() {
  ios_config_find_value "$1" config_version
  [ "$IOS_CONFIG_FOUND" -eq 1 ] && printf '%s\n' "$IOS_CONFIG_VALUE"
}

ios_config_needs_migration() {
  _ios_default="$1"
  _ios_runtime="$2"
  [ -f "$_ios_runtime" ] || return 0
  _ios_target_version=$(ios_config_version "$_ios_default")
  _ios_current_version=$(ios_config_version "$_ios_runtime")
  [ -n "$_ios_target_version" ] || return 0
  [ "$_ios_current_version" = "$_ios_target_version" ] || return 0
  while IFS= read -r _ios_line || [ -n "$_ios_line" ]; do
    case "$_ios_line" in
      ''|'#'*) continue ;;
      *=*)
        _ios_key=${_ios_line%%=*}
        [ "$_ios_key" = config_version ] && continue
        ios_config_find_value "$_ios_runtime" "$_ios_key"
        [ "$IOS_CONFIG_FOUND" -eq 1 ] || return 0
        ;;
    esac
  done < "$_ios_default"
  return 1
}

ios_config_migrate() {
  umask 077
  _ios_default="$1"
  _ios_runtime="$2"
  _ios_tmp="${_ios_runtime}.migrate.tmp.$$"
  _ios_target_version=$(ios_config_version "$_ios_default")
  [ -n "$_ios_target_version" ] || { ios_upgrade_fail default_config_version_missing; return 1; }
  _ios_backup="${_ios_runtime}.bak.$(date +%Y%m%d%H%M%S 2>/dev/null || echo upgrade)"
  if [ -f "$_ios_runtime" ]; then
    ios_atomic_copy "$_ios_runtime" "$_ios_backup" 0600 || {
      ios_upgrade_fail config_backup_failed
      return 1
    }
  fi
  : > "$_ios_tmp" || { ios_upgrade_fail config_temp_create_failed; return 1; }
  while IFS= read -r _ios_line || [ -n "$_ios_line" ]; do
    case "$_ios_line" in
      ''|'#'*) printf '%s\n' "$_ios_line" >> "$_ios_tmp" ;;
      *=*)
        _ios_key=${_ios_line%%=*}
        if [ "$_ios_key" = config_version ]; then
          printf 'config_version=%s\n' "$_ios_target_version" >> "$_ios_tmp"
        else
          ios_config_find_value "$_ios_runtime" "$_ios_key"
          if [ "$IOS_CONFIG_FOUND" -eq 1 ]; then
            printf '%s=%s\n' "$_ios_key" "$IOS_CONFIG_VALUE" >> "$_ios_tmp"
          else
            printf '%s\n' "$_ios_line" >> "$_ios_tmp"
          fi
        fi
        ;;
      *) printf '%s\n' "$_ios_line" >> "$_ios_tmp" ;;
    esac
  done < "$_ios_default"
  chmod 0600 "$_ios_tmp" 2>/dev/null || { rm -f "$_ios_tmp"; ios_upgrade_fail config_temp_chmod_failed; return 1; }
  mv -f "$_ios_tmp" "$_ios_runtime" 2>/dev/null || { rm -f "$_ios_tmp"; ios_upgrade_fail config_commit_failed; return 1; }
  ios_upgrade_log "运行配置已迁移至版本 $_ios_target_version；原文件=$_ios_backup"
  return 0
}

ios_upgrade_prepare_runtime() {
  _ios_default_conf="$1"
  [ -f "$_ios_default_conf" ] || { ios_upgrade_fail module_default_config_missing; return 1; }
  _ios_phase=$(ios_upgrade_status_value phase)
  _ios_force_migrate=0
  _ios_needs_final_live=0
  case "$_ios_phase" in
    ''|complete) ;;
    *)
      ios_upgrade_log "恢复中断的升级，阶段=$_ios_phase"
      ios_upgrade_restore || return 1
      [ "$(ios_upgrade_mode)" != upgrade ] || _ios_force_migrate=1
      _ios_needs_final_live=1
      ;;
  esac
  ios_live_restore_missing include_pause || {
    ios_upgrade_fail live_restore_missing_failed
    return 1
  }

  if [ ! -f "$IOS_RUNTIME_CONFIG" ]; then
    ios_atomic_copy "$_ios_default_conf" "$IOS_RUNTIME_CONFIG" 0600 || {
      ios_upgrade_fail default_config_install_failed
      return 1
    }
    ios_upgrade_log "默认运行配置已安装"
  fi

  if [ "$_ios_force_migrate" -eq 1 ] ||
     ios_config_needs_migration "$_ios_default_conf" "$IOS_RUNTIME_CONFIG"; then
    ios_upgrade_write_status migrating config_schema || return 1
    ios_config_migrate "$_ios_default_conf" "$IOS_RUNTIME_CONFIG" || return 1
    _ios_needs_final_live=1
  fi

  if [ "$_ios_needs_final_live" -eq 1 ]; then
    ios_live_refresh upgrade_complete || {
      ios_upgrade_fail live_snapshot_finalize_failed
      return 1
    }
  fi
  ios_upgrade_write_status complete ready || return 1
  ios_upgrade_release_pause || ios_upgrade_log "安装暂停清理延后，服务将重试"
  ios_upgrade_log "持久数据升级完成"
  return 0
}

ios_upgrade_finish() {
  _ios_default_conf="$1"
  ios_upgrade_write_status module_installed files_ready || return 1
  ios_upgrade_refresh_backup || return 1
  ios_upgrade_restore || return 1
  ios_upgrade_prepare_runtime "$_ios_default_conf"
}
