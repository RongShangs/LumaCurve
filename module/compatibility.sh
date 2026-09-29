#!/system/bin/sh
# Only probe capabilities; never launch the normal daemon during installation.
lc_compat_fail() { ui_print "  [不通过] $1"; return 1; }
lc_compat_environment() {
  ui_print "- 检查系统架构、Android 版本和执行工具"
  [ "$(id -u)" = 0 ] || { lc_compat_fail "需要 root 安装环境。"; return 1; }
  _lc_arch=${ARCH:-$(getprop ro.product.cpu.abi 2>/dev/null)}
  case "$_lc_arch" in arm64|arm64-v8a) ;; *) lc_compat_fail "引擎只支持 ARM64，当前：${_lc_arch:-未知}"; return 1 ;; esac
  _lc_api=${API:-$(getprop ro.build.version.sdk 2>/dev/null)}
  case "$_lc_api" in ''|*[!0-9]*) lc_compat_fail "无法确认 Android API 版本。"; return 1 ;; esac
  [ "$_lc_api" -ge 26 ] || { lc_compat_fail "需要 Android 8.0 / API 26 或更新系统。"; return 1; }
  for _lc_tool in timeout settings base64 tr grep; do
    command -v "$_lc_tool" >/dev/null 2>&1 || { lc_compat_fail "缺少运行工具：$_lc_tool"; return 1; }
  done
  ui_print "  [通过] ARM64 / API $_lc_api / 必要工具"
}
lc_compat_probe() {
  _lc_engine="$1"
  _lc_report="$2/compatibility-report.txt"
  if [ -r "$2/framework-broker.jar" ]; then
    ui_print "- 核对主屏框架、固件和只读接口（不修改亮度）"
    for _lc_pair in \
      'framework.jar:1d2bf53f6c2684103dadbeef0d2665a7f033b7746a75f3e7404145dd999600fd' \
      'services.jar:ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20'; do
      _lc_file=${_lc_pair%%:*}; _lc_expected=${_lc_pair#*:}
      _lc_hash=$(sha256sum "/system/framework/$_lc_file" 2>/dev/null) || { lc_compat_fail "固件文件缺失：$_lc_file"; return 1; }
      [ "${_lc_hash%% *}" = "$_lc_expected" ] || { lc_compat_fail "固件已变化：$_lc_file，当前包只适配已验证的 HyperOS 4。"; return 1; }
    done
    _lc_app=/system/bin/app_process; [ -x "$_lc_app" ] || _lc_app=/system/bin/app_process64
    CLASSPATH="$2/framework-broker.jar" timeout 12 "$_lc_app" /system/bin LumaFrameworkProbeTemporary preflight > "$_lc_report" 2>&1
    _lc_result=$?
    [ "$_lc_result" = 0 ] && grep -q '^MAIN_DISPLAY verified$' "$_lc_report" &&
      grep -q '^PREFLIGHT ok brightness=' "$_lc_report" || {
      while IFS= read -r _lc_line; do ui_print "  $_lc_line"; done < "$_lc_report"
      lc_compat_fail "主屏框架预检未通过；请保持主屏亮起，并停用其他亮度控制。"; return 1;
    }
    ui_print "  [通过] 主屏标识、框架亮度接口及固件均匹配"
    ui_print "  运行时仍会核对当前背光与框架反馈；不匹配就交还系统。"
    return 0
  fi
  ui_print "- 运行引擎兼容性探测（最多 12 秒，不接管亮度）"
  timeout 12 "$_lc_engine" --check-install > "$_lc_report" 2>&1
  _lc_result=$?
  [ "$_lc_result" -eq 0 ] && grep -qx 'probe_protocol=1' "$_lc_report" && grep -qx 'result=pass' "$_lc_report" && grep -qx 'actual_brightness_write=verified_current_value' "$_lc_report" || {
    while IFS= read -r _lc_line; do ui_print "  $_lc_line"; done < "$_lc_report"
    lc_compat_fail "引擎探测未通过或超时（退出码 $_lc_result），停止安装。"; return 1;
  }
  ui_print "  [通过] 新引擎可以在当前系统中加载执行"
  ui_print "  [通过] 主屏背光已写回当前值，读回一致"
  if grep -qx 'ndk_als=1' "$_lc_report"; then
    ui_print "  [通过] 检测到 NDK 支持的环境光传感器"
  else
    ui_print "  [通过] 检测到有效 sysfs 照度，使用备用光感路径"
  fi
  _lc_mode=$(timeout 5 settings get system screen_brightness_mode 2>/dev/null)
  _lc_settings_result=$?
  case "$_lc_settings_result:$_lc_mode" in 0:0|0:1) ui_print "  [通过] 系统亮度设置可读取" ;;
    *) ui_print "  [提醒] 当前亮度模式读取异常，重启后需检查 settings_read_error。" ;; esac
  [ -d /sys/class/thermal ] || ui_print "  [提醒] 温控节点未确认，重启后检查可信温度。"
  ui_print "  [待验证] 光感连续事件、持续背光控制和服务启动后的 SELinux 权限"
  ui_print "  检测通过表示基础能力满足，不能代替重启后的实机验收。"
}
