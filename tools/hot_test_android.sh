#!/system/bin/sh
# Apply only the local test core; configuration, module activation and WebUI stay in place.
set -eu
[ "$(id -u)" = 0 ] || { echo '请在 root 终端运行。' >&2; exit 1; }
[ "$#" = 1 ] || { echo '用法：sh hot_test_android.sh /sdcard/Download/luma_curve-1.0.0.zip' >&2; exit 1; }
ZIP=$1
MOD=/data/adb/modules/luma_curve
CTL="$MOD/luma_curvectl.sh"
BIN="$MOD/system/bin/luma_curve_daemon"
PAUSE=/data/local/tmp/luma_curve.paused
[ -f "$ZIP" ] && [ -x "$BIN" ] && [ -f "$CTL" ] || { echo '安装包或已安装模块不存在。' >&2; exit 1; }
unzip -p "$ZIP" module.prop | tr -d '\r' | grep -qx 'id=luma_curve' || { echo '安装包不是 LumaCurve。' >&2; exit 1; }
NEW=$(mktemp "$MOD/system/bin/.luma-core-test.XXXXXX")
BACKUP="$MOD/system/bin/luma_curve_daemon.hot-backup"
trap 'rm -f "$NEW"' EXIT
unzip -p "$ZIP" system/bin/luma_curve_daemon > "$NEW"
chmod 0755 "$NEW"
echo "待测试核心：$("$NEW" --build-info)"
"$NEW" --check-install || { echo '核心能力检查失败，当前核心保持不变。' >&2; exit 1; }
WAS_PAUSED=0
[ ! -f "$PAUSE" ] || WAS_PAUSED=1
sh "$CTL" pause
# The pause marker prevents the existing service watchdog from restarting during replacement.
if ! cp -p "$BIN" "$BACKUP" || ! mv -f "$NEW" "$BIN"; then
  [ "$WAS_PAUSED" = 1 ] || sh "$CTL" resume
  echo '替换失败，当前核心保留。' >&2; exit 1
fi
if [ "$WAS_PAUSED" = 0 ] && ! sh "$CTL" restart; then
  sh "$CTL" pause || :
  cp -p "$BACKUP" "$NEW" && mv -f "$NEW" "$BIN"
  sh "$CTL" restart || :
  echo '新核心启动失败，已尝试恢复旧核心。' >&2; exit 1
fi
echo "当前文件核心：$("$BIN" --build-info)"
echo '热测试完成，无需重启手机。仅替换运行目录的核心，尚未发布版本。'
echo '请从 WebUI 详细读数核对 core_build 和 actuator_write_stage。'
echo '若管理器另有待应用模块，重启手机时可能覆盖热测试核心。'
