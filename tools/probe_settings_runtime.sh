#!/system/bin/sh
set -u
[ "$#" = 2 ] || exit 1
HERE=${0%/*}
. "$HERE/process_scope.sh"
lc_scope_detach_self || exit 1
CLASSPATH="$1"; export CLASSPATH
exec "$2" /system/bin LumaFrameworkProbeSettings
