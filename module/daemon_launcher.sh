#!/system/bin/sh
# This child alone is detached; the invoking manager/terminal stays in its group.
set -u
[ "$(id -u)" = 0 ] || { echo '[LumaCurve启动] 需要 root。' >&2; exit 1; }
MODDIR=${0%/*}
DAEMON="$MODDIR/system/bin/luma_curve_daemon"
[ -x "$DAEMON" ] && [ -r "$MODDIR/process_scope.sh" ] || exit 1
. "$MODDIR/process_scope.sh"
lc_scope_detach_self || exit 1
exec "$DAEMON"
