#!/system/bin/sh
# Place this script beside luma-refactor-helper.jar. All file paths are relative.
[ "$(id -u)" = 0 ] || { echo '请先执行 su'; exit 1; }
DIR=${0%/*}
[ "$DIR" != "$0" ] || DIR=.
[ -r "$DIR/luma-refactor-helper.jar" ] || { echo '未找到同目录的恢复辅助文件'; exit 1; }
CLASSPATH="$DIR/luma-refactor-helper.jar" /system/bin/app_process /system/bin top.rongshangs.lumacurve.refactor.RootControl stop
