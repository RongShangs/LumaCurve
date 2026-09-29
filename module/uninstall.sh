#!/system/bin/sh
[ ! -r "${0%/*}/upgrade_data.sh" ] || . "${0%/*}/upgrade_data.sh"
# LumaCurve 1.0.0 uninstall

PID_FILE=/data/local/tmp/luma_curve.pid
CONF_FILE=/data/local/tmp/luma_curve.conf
PAUSE_FILE=/data/local/tmp/luma_curve.paused

touch "$PAUSE_FILE" 2>/dev/null

service_pids=""
for proc in /proc/[0-9]*; do
  [ -r "$proc/cmdline" ] || continue
  cmdline=$(tr '\000' ' ' < "$proc/cmdline" 2>/dev/null)
  case "$cmdline" in
    *"/luma_curve/service.sh"*)
      service_pid=${proc##*/}
      service_pids="$service_pids $service_pid"
      kill -TERM "$service_pid" 2>/dev/null
      ;;
  esac
done
if [ -n "$service_pids" ]; then
  sleep 1
  for service_pid in $service_pids; do
    kill -0 "$service_pid" 2>/dev/null && kill -KILL "$service_pid" 2>/dev/null
  done
fi

pids=$(pidof luma_curve_daemon 2>/dev/null)
if [ -n "$pids" ]; then
  for pid in $pids; do kill -TERM "$pid" 2>/dev/null; done
  sleep 1
  pids=$(pidof luma_curve_daemon 2>/dev/null)
  if [ -n "$pids" ]; then
    for pid in $pids; do kill -KILL "$pid" 2>/dev/null; done
    sleep 1
  fi
fi
rm -f "$PID_FILE"

if [ ! -r "${0%/*}/framework-broker.jar" ]; then
hbm_node=$(sed -n 's/^hbm_node_path=//p' "$CONF_FILE" 2>/dev/null | tail -n 1)
hbm_off=$(sed -n 's/^hbm_off_value=//p' "$CONF_FILE" 2>/dev/null | tail -n 1)
[ -n "$hbm_off" ] || hbm_off=0
if [ -n "$hbm_node" ] && [ -e "$hbm_node" ]; then
  printf '%s' "$hbm_off" > "$hbm_node" 2>/dev/null
fi
_lc_release_path=$(luma_backlight_path)
_lc_hbm_root=${_lc_release_path%/brightness}
[ -n "$_lc_hbm_root" ] || _lc_hbm_root=/sys/class/backlight/.luma-unresolved
for node in \
  "$_lc_hbm_root/hbm" \
  "$_lc_hbm_root/hbm_mode" \
  /sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/card0-DSI-1/hbm; do
  [ "$node" = "$hbm_node" ] && continue
  [ -e "$node" ] && printf '%s' "$hbm_off" > "$node" 2>/dev/null
done
[ -z "$_lc_release_path" ] || chmod 0644 "$_lc_release_path" 2>/dev/null
fi

rm -f /data/local/tmp/luma_curve.log
rm -f /data/local/tmp/luma_curve.log.*
rm -f /data/local/tmp/luma_curve_learn
rm -f /data/local/tmp/luma_curve_learn_ledger
rm -f /data/local/tmp/luma_curve_preference
rm -f /data/local/tmp/luma_curve_preference.tmp-daemon
rm -f /data/local/tmp/luma_curve_presets.json
rm -f /data/local/tmp/luma_curve_presets.json.tmp-ui-*
rm -f /data/local/tmp/luma_curve_learn.bak
rm -f /data/local/tmp/luma_curve_learn.bak.*
rm -f /data/local/tmp/luma_curve_learn.tmp*
rm -f /data/local/tmp/luma_curve.conf
rm -f /data/local/tmp/luma_curve.conf.bak
rm -f /data/local/tmp/luma_curve.conf.bak.*
rm -f /data/local/tmp/luma_curve.conf.tmp*
rm -f /data/local/tmp/luma_curve_state
rm -f /data/local/tmp/luma_curve_state.tmp
rm -f /data/local/tmp/luma_curve_ui_watch
rm -f /data/local/tmp/luma_curve_cmd
rm -f /data/local/tmp/luma_curve.paused
rm -rf /data/adb/luma_curve
rm -f /data/local/tmp/luma_curve_system_auto_backup
rm -f /data/local/tmp/luma_curve_system_auto_adj_backup
rm -f /data/adb/modules/luma_curve/companion_choice
