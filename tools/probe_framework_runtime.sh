#!/system/bin/sh
# A separate shell PID is detached; do not source this into an app terminal.
set -u
HERE=${0%/*}
[ "$#" = 3 ] || exit 1
. "$HERE/process_scope.sh"
lc_scope_detach_self || exit 1
CLASSPATH="$1"; export CLASSPATH
exec "$2" /system/bin LumaFrameworkProbe "$3"
