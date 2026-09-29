#!/system/bin/sh
# Process identity only: a live core is not a promise of healthy sensor data.
lc_core_status() {
  _lc_pids=$(pidof luma_curve_daemon 2>/dev/null)
  set -- $_lc_pids
  if [ "$#" -eq 0 ]; then
    if [ -f "$PAUSE_FILE" ]; then printf '%s\n' paused; else printf '%s\n' stopped; fi
    return
  fi
  [ "$#" -eq 1 ] || { printf '%s\n' multiple; return; }
  case "$1" in ''|*[!0-9]*|0) printf '%s\n' unknown; return;; esac
  kill -0 "$1" 2>/dev/null || { printf '%s\n' stopped; return; }
  _lc_exe=$(readlink "/proc/$1/exe" 2>/dev/null)
  [ "$_lc_exe" = "$DAEMON" ] || { printf '%s\n' unknown; return; }
  printf '%s\n' running
}

lc_core_description_refresh() {
  _lc_prop="$MODDIR/module.prop"
  [ -r "$_lc_prop" ] || return 1
  _lc_signature=$(cksum "$_lc_prop" 2>/dev/null) || return 1
  _lc_current=$(sed -n 's/^description=//p' "$_lc_prop")
  [ -n "$_lc_current" ] || return 1
  case "$_lc_current" in
    '[LumaCurve核心'*'] '*) _lc_base=${_lc_current#*] };;
    *) _lc_base=$_lc_current;;
  esac
  case "$(lc_core_status)" in
    running) _lc_marker='[LumaCurve核心✔]';;
    paused) _lc_marker='[LumaCurve核心Ⅱ]';;
    stopped) _lc_marker='[LumaCurve核心✘]';;
    multiple) _lc_marker='[LumaCurve核心！]';;
    *) _lc_marker='[LumaCurve核心？]';;
  esac
  _lc_description="$_lc_marker $_lc_base"
  [ "$_lc_current" != "$_lc_description" ] || return 0
  _lc_temp="$_lc_prop.luma-status.$$"
  (
    umask 022
    while IFS= read -r _lc_line || [ -n "$_lc_line" ]; do
      case "$_lc_line" in
        description=*) printf 'description=%s\n' "$_lc_description";;
        *) printf '%s\n' "$_lc_line";;
      esac
    done < "$_lc_prop" > "$_lc_temp"
  ) || { rm -f "$_lc_temp"; return 1; }
  # Do not overwrite metadata changed by a concurrent module upgrade.
  [ "$_lc_signature" = "$(cksum "$_lc_prop" 2>/dev/null)" ] || { rm -f "$_lc_temp"; return 1; }
  chmod 0644 "$_lc_temp" && mv -f "$_lc_temp" "$_lc_prop" || { rm -f "$_lc_temp"; return 1; }
}

lc_core_watch_wait() {
  _lc_wait_steps=0
  while [ "$_lc_wait_steps" -lt 6 ]; do
    lc_core_description_refresh || :
    sleep 10
    _lc_wait_steps=$((_lc_wait_steps + 1))
  done
}
