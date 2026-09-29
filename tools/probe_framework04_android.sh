#!/system/bin/sh
# Select the extended profile; all preflight and recovery remain in one wrapper.
HERE=${0%/*}
LUMA_FRAMEWORK_PROFILE=sustained; export LUMA_FRAMEWORK_PROFILE
exec sh "$HERE/probe_framework03_android.sh" "$@"
