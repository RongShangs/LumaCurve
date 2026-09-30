#!/system/bin/sh
# Standalone read-only collection. No brightness/mode/permission/module changes.
set -u
[ "$(id -u)" = 0 ] || { echo '请先执行 su，再运行本脚本。' >&2; exit 1; }
[ "$#" = 0 ] || { echo '用法：sh ./collect_hyperos4_android.sh' >&2; exit 1; }
for TOOL in timeout sha256sum tar cp; do
  command -v "$TOOL" >/dev/null 2>&1 || { echo "缺少采集工具：$TOOL" >&2; exit 1; }
done
STORAGE=/sdcard
[ -d "$STORAGE" ] && [ -w "$STORAGE" ] || { echo '内部存储 /sdcard 不可写。' >&2; exit 1; }
NAME="LumaCurve-device-info-$(date +%Y%m%d-%H%M%S)-$$"
OUT=${LUMA_COLLECT_BUNDLE_DIR:-$STORAGE/$NAME}
if [ -n "${LUMA_COLLECT_BUNDLE_DIR:-}" ]; then
  case "$OUT" in /data/local/tmp/luma_curve-export-*/device) :;; *) echo "无效的内部采集目录" >&2; exit 1;; esac
  case "$OUT" in *..*) exit 1;; esac
fi
report() {
  echo "$1"
  if [ -n "${LUMA_COLLECT_PROGRESS:-}" ]; then
    printf '%s\n' "$1" > "$LUMA_COLLECT_PROGRESS.tmp" && mv "$LUMA_COLLECT_PROGRESS.tmp" "$LUMA_COLLECT_PROGRESS"
  fi
}
mkdir "$OUT" || exit 1
mkdir -p "$OUT/firmware" "$OUT/display-config" "$OUT/module" || exit 1
echo '开始一次性只读采集。请保持屏幕亮起，通常 30～90 秒。'
report '[1/5] 系统版本与亮度设置'
{
  date; id; getenforce; cat /proc/uptime
  for KEY in ro.product.model ro.product.device ro.product.board ro.product.cpu.abi ro.build.fingerprint ro.build.version.sdk ro.build.version.release ro.build.version.incremental ro.mi.os.version.name ro.mi.os.version.incremental; do
    printf '%s=' "$KEY"; getprop "$KEY"
  done
  printf 'BOOTCLASSPATH=%s\nSYSTEMSERVERCLASSPATH=%s\n' "${BOOTCLASSPATH:-}" "${SYSTEMSERVERCLASSPATH:-}"
  for KEY in screen_brightness_mode screen_brightness screen_auto_brightness_adj; do
    printf '%s=' "$KEY"; timeout 5 settings get system "$KEY"
  done
} > "$OUT/environment.txt" 2>&1
report '[2/5] 提取系统与小米显示框架文件'
for BASE in /system/framework /system_ext/framework /product/framework /vendor/framework; do
  for JAR in "$BASE"/*.jar; do
    [ -r "$JAR" ] || continue
    case "${JAR##*/}" in framework.jar|*services*.jar|*miui*.jar|*xiaomi*.jar|*display*.jar|*brightness*.jar) ;; *) continue;; esac
    RELATIVE=${JAR#/}
    DEST="$OUT/firmware/$RELATIVE"
    mkdir -p "${DEST%/*}" || exit 1
    BEFORE=$(sha256sum "$JAR") || { printf 'hash_failed=%s\n' "$JAR" >> "$OUT/collection-errors.txt"; continue; }
    if ! cp "$JAR" "$DEST"; then
      printf 'copy_failed=%s\n' "$JAR" >> "$OUT/collection-errors.txt"; continue
    fi
    AFTER=$(sha256sum "$DEST") || { printf 'copy_hash_failed=%s\n' "$JAR" >> "$OUT/collection-errors.txt"; continue; }
    if [ "${BEFORE%% *}" = "${AFTER%% *}" ]; then
      printf '%s\n' "$BEFORE" >> "$OUT/firmware-sha256.txt"
    else
      printf 'copy_changed=%s\n' "$JAR" >> "$OUT/collection-errors.txt"
    fi
  done
done
for RESOURCE in /system/framework/framework-res.apk /system/framework/miui-framework-res.apk /system_ext/framework/miui-framework-res.apk; do
  [ -r "$RESOURCE" ] || continue
  DEST="$OUT/firmware/${RESOURCE#/}"
  mkdir -p "${DEST%/*}" || exit 1
  BEFORE=$(sha256sum "$RESOURCE") || continue
  cp "$RESOURCE" "$DEST" || { printf 'resource_copy_failed=%s\n' "$RESOURCE" >> "$OUT/collection-errors.txt"; continue; }
  AFTER=$(sha256sum "$DEST") || continue
  if [ "${BEFORE%% *}" = "${AFTER%% *}" ]; then
    printf '%s\n' "$BEFORE" >> "$OUT/firmware-sha256.txt"
  else
    printf 'resource_copy_changed=%s\n' "$RESOURCE" >> "$OUT/collection-errors.txt"
  fi
done
report '[3/5] 显示配置与背光节点'
for CONFIG in /system/etc/displayconfig /system_ext/etc/displayconfig /product/etc/displayconfig /vendor/etc/displayconfig /odm/etc/displayconfig; do
  [ -d "$CONFIG" ] || continue
  DEST="$OUT/display-config/${CONFIG#/}"
  mkdir -p "${DEST%/*}" || exit 1
  cp -RL "$CONFIG" "$DEST" 2>> "$OUT/collection-errors.txt" || printf 'config_copy_failed=%s\n' "$CONFIG" >> "$OUT/collection-errors.txt"
done
{
  for NODE in /sys/class/backlight/*; do
    [ -d "$NODE" ] || continue
    printf '\nBACKLIGHT %s\n' "$NODE"; readlink -f "$NODE"; ls -l "$NODE/brightness"
    for FIELD in brightness actual_brightness max_brightness bl_power type; do
      [ -r "$NODE/$FIELD" ] || continue
      printf '%s=' "$FIELD"; cat "$NODE/$FIELD"
    done
  done
  for NODE in /sys/class/drm/*; do
    [ -d "$NODE" ] || continue
    printf '\nDRM %s\n' "$NODE"
    for FIELD in status enabled dpms; do
      [ -r "$NODE/$FIELD" ] || continue
      printf '%s=' "$FIELD"; cat "$NODE/$FIELD"
    done
    ls "$NODE" 2>/dev/null
  done
} > "$OUT/backlight.txt" 2>&1
report '[4/5] 显示、光感和唤醒状态'
for SERVICE in display sensorservice power; do
  timeout 15 dumpsys "$SERVICE" > "$OUT/$SERVICE.txt" 2>&1
  printf 'dumpsys_%s_exit=%s\n' "$SERVICE" "$?" >> "$OUT/summary.txt"
done
timeout 5 cmd display help > "$OUT/display-command-help.txt" 2>&1
timeout 5 cmd overlay list > "$OUT/resource-overlays.txt" 2>&1
{
  for RESOURCE in config_screenBrightnessNits config_screenBrightnessBacklight config_screenBrightnessBacklightFloat config_autoBrightnessLevels config_autoBrightnessDisplayValuesNits; do
    printf '\nRESOURCE %s\n' "$RESOURCE"
    timeout 5 cmd overlay lookup android "android:array/$RESOURCE"
  done
} > "$OUT/brightness-resources.txt" 2>&1
sleep 2
timeout 15 dumpsys display > "$OUT/display-second.txt" 2>&1
printf 'dumpsys_display_second_exit=%s\n' "$?" >> "$OUT/summary.txt"
for FILE in /data/local/tmp/luma_curve_state /data/local/tmp/luma_curve.conf /data/adb/modules/luma_curve/module.prop /data/adb/modules/luma_curve/build-info.json /data/adb/modules/luma_curve/compatibility-report.txt; do
  [ -r "$FILE" ] || continue
  cp "$FILE" "$OUT/module/${FILE##*/}" 2>> "$OUT/collection-errors.txt"
done
[ ! -r /data/local/tmp/luma_curve.log ] || tail -n 250 /data/local/tmp/luma_curve.log > "$OUT/module/recent-log.txt"
{
  echo '采集方式：只读，未请求亮度写入、暂停模块或修改设置。'
  echo '用途：核对 HyperOS 4 框架实现、主屏标识、亮度范围、显示配置与光感。'
  echo '此结果不能证明实际写入、完整亮度范围或锁屏唤醒稳定性。'
  echo '框架文件供本地适配分析使用，不上传 GitHub、不随安装包或官网分发。'
} > "$OUT/说明.txt"
if [ -n "${LUMA_COLLECT_BUNDLE_DIR:-}" ]; then
  report '设备资料采集完成，正在整理分析包'; exit 0
fi
report '[5/5] 打包到内部存储根目录'
ARCHIVE="$STORAGE/$NAME.tar.gz"
if tar -czf "$ARCHIVE" -C "$STORAGE" "$NAME" && tar -tzf "$ARCHIVE" >/dev/null 2>&1; then
  case "$OUT" in /sdcard/LumaCurve-device-info-*) rm -rf "$OUT";; esac
  echo "采集完成，请只回传这个文件：$ARCHIVE"
  ls -lh "$ARCHIVE"
else
  echo "自动压缩失败，采集文件仍保留在：$OUT" >&2
  echo '请用文件管理器压缩该目录。' >&2
  exit 1
fi
