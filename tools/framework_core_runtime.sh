#!/system/bin/sh
set -eu
HERE=${0%/*}
. "$HERE/process_scope.sh"
lc_scope_detach_self || exit 2
RUN=$1; APP=$2; ACTION=$3; SOCKET=$4
case "$RUN" in /data/local/tmp/luma-framework-core.*) :;; *) exit 2;; esac
case "$ACTION" in
 broker) export CLASSPATH="$RUN/probe.jar"; exec "$APP" /system/bin LumaFrameworkOutputBroker "$SOCKET" "$RUN";;
 core) export LUMA_FRAMEWORK_SOCKET="$SOCKET" LUMA_FRAMEWORK_RUN="$RUN"; exec "$RUN/luma_framework_core";;
 *) exit 2;;
esac
