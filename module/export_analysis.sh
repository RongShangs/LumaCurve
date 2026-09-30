#!/system/bin/sh
# Fixed-root, token-scoped export jobs. Never change brightness, pause, or settings.
set -u
[ "$(id -u)" = 0 ] || { echo '导出需要模块管理器的 Root 权限' >&2; exit 1; }
MOD=${0%/*}
ACTION=${1:-}; MODE=${2:-}; TOKEN=${3:-}
case "$MODE" in log|analysis) :;; *) exit 1;; esac
case "$TOKEN" in ''|*[!a-z0-9-]*) exit 1;; esac
[ "${#TOKEN}" -le 80 ] || exit 1
WORK="/data/local/tmp/luma_curve-export-$TOKEN"
STATUS="$WORK/status"
state() { printf '%s\n%s\n%s\n' "$1" "$2" "${3:-}" > "$STATUS.tmp" && mv "$STATUS.tmp" "$STATUS"; }
case "$ACTION" in
 start)
  command -v nohup >/dev/null 2>&1 || { echo '缺少后台导出工具：nohup' >&2; exit 1; }
  umask 077
  mkdir "$WORK" || { echo '导出任务创建失败' >&2; exit 1; }
  state running '准备导出…' || exit 1
  nohup sh "$0" worker "$MODE" "$TOKEN" >/dev/null 2>&1 </dev/null &
  printf '%s\n' "$!" > "$WORK/pid"
  echo started; exit 0;;
 status)
  [ -r "$STATUS" ] || { echo '任务状态不可读' >&2; exit 1; }
  if [ "$(head -n 1 "$STATUS")" = running ] && [ -r "$WORK/pid" ]; then
    JOB_PID=$(cat "$WORK/pid")
    case "$JOB_PID" in ''|*[!0-9]*) state error '导出任务异常，请重新导出';;
      *) kill -0 "$JOB_PID" 2>/dev/null || state error '导出任务已停止，请重新导出';;
    esac
  fi
  if [ "$(head -n 1 "$STATUS")" = running ] && [ -s "$WORK/collector-progress" ]; then
    printf 'running\n'; cat "$WORK/collector-progress"; printf '\n'
  else cat "$STATUS"; fi
  exit 0;;
 worker) [ -d "$WORK" ] && [ -r "$STATUS" ] || exit 1;;
 *) exit 1;;
esac
DONE=0
trap '[ "$DONE" = 1 ] || state error "导出未完成，请检查可用空间与模块权限"' 0
trap 'exit 1' HUP INT TERM
for TOOL in timeout tar sha256sum; do command -v "$TOOL" >/dev/null 2>&1 || { state error "缺少导出工具：$TOOL"; DONE=1; exit 1; }; done
[ -d /sdcard ] && [ -w /sdcard ] || { state error '手机存储根目录 /sdcard 不可写'; DONE=1; exit 1; }
state running '正在读取本次开机日志与设备状态'
sh "$MOD/luma_curvectl.sh" current-log > "$WORK/current-boot.log" || exit 1
if [ -r /data/local/tmp/luma_curve_state ]; then cat /data/local/tmp/luma_curve_state > "$WORK/device-state.txt" || exit 1
else printf '设备状态当前不可读，核心可能未启动。\n' > "$WORK/device-state.txt"; fi
printf '导出时间：%s\n' "$(date '+%Y-%m-%d %H:%M:%S%z')" > "$WORK/README.txt"
NAME="LumaCurve-$MODE-$(date +%Y%m%d-%H%M%S)-$TOKEN"
if [ "$MODE" = log ]; then
  DEST="/sdcard/$NAME.log"
  state running '正在将日志与设备状态写入手机根目录'
  (set -C; { cat "$WORK/README.txt"; printf '\n=== 本次开机日志 ===\n'; cat "$WORK/current-boot.log"; printf '\n=== 设备状态 ===\n'; cat "$WORK/device-state.txt"; } > "$DEST") || exit 1
else
  state running '正在收集系统显示与光感资料，通常需要 30～90 秒'
  LUMA_COLLECT_BUNDLE_DIR="$WORK/device" LUMA_COLLECT_PROGRESS="$WORK/collector-progress" timeout 240 sh "$MOD/collect_hyperos4_android.sh" > "$WORK/collection-output.txt" 2>&1 || { state error "设备采集失败，诊断保留在 $WORK"; DONE=1; exit 1; }
  rm -f "$WORK/collector-progress"
  state running '正在压缩日志、设备状态与系统资料'
  DEST="/sdcard/$NAME.tar.gz"
  [ ! -e "$DEST" ] || exit 1
  timeout 180 tar -czf "$DEST" -C "$WORK" README.txt current-boot.log device-state.txt collection-output.txt device || { rm -f "$DEST"; exit 1; }
  state running '正在校验分析包完整性'
  timeout 60 tar -tzf "$DEST" >/dev/null 2>&1 || { rm -f "$DEST"; exit 1; }
fi
chmod 0644 "$DEST" || exit 1
sha256sum "$DEST" > "$DEST.sha256" || exit 1
chmod 0644 "$DEST.sha256" || exit 1
state done '导出完成，已保存到手机存储根目录' "$DEST" || exit 1
DONE=1
# Only our validated token-scoped staging data; completed status remains readable.
rm -f "$WORK/current-boot.log" "$WORK/device-state.txt" "$WORK/README.txt" "$WORK/collection-output.txt" "$WORK/pid"
[ ! -d "$WORK/device" ] || rm -rf "$WORK/device"
