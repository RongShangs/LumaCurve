#!/system/bin/sh
# Sourced by modern Magisk/KernelSU installers. Own extraction and preflight.
SKIPUNZIP=1

lc_install_fail() {
  if command -v lc_rollback_legacy_flags >/dev/null 2>&1; then lc_rollback_legacy_flags; fi
  if command -v ios_upgrade_restore >/dev/null 2>&1; then
    ios_upgrade_restore >/dev/null 2>&1
    ios_upgrade_release_pause >/dev/null 2>&1
  fi
  abort "$1"
}

lc_install_module() {
  ui_print "=============================================="
  ui_print "  LumaCurve · 流光亮度 1.0.0"
  ui_print "  仿 iOS 曲线的系统亮度引擎 · 酷安@戎Shangs"
  ui_print "=============================================="
  COMPAT_BOOTSTRAP="$TMPDIR/luma_curve_compatibility.sh"
  unzip -p "$ZIPFILE" compatibility.sh > "$COMPAT_BOOTSTRAP" 2>/dev/null || abort "兼容性检测文件解压失败。"
  [ -s "$COMPAT_BOOTSTRAP" ] || abort "兼容性检测文件缺失。"
  . "$COMPAT_BOOTSTRAP"
  lc_compat_environment || abort "基础环境不兼容，已中止安装。"
  LC_INSTALL_BOOTSTRAP="$TMPDIR/luma_curve_install_choice.sh"
  UPGRADE_BOOTSTRAP="$TMPDIR/luma_curve_upgrade_data.sh"
  unzip -p "$ZIPFILE" install_choice.sh > "$LC_INSTALL_BOOTSTRAP" 2>/dev/null || abort "安装检查文件解压失败。"
  [ -s "$LC_INSTALL_BOOTSTRAP" ] || abort "安装检查文件缺失。"
  . "$LC_INSTALL_BOOTSTRAP"
  ui_print "[1/4] 检查冲突模块与安装环境"
  lc_check_legacy || abort "安装已取消，原版模块保留。"
  ui_print "[2/4] 备份流光亮度配置与数据"
  unzip -p "$ZIPFILE" upgrade_data.sh > "$UPGRADE_BOOTSTRAP" 2>/dev/null || abort "数据维护文件解压失败。"
  [ -s "$UPGRADE_BOOTSTRAP" ] || abort "数据维护文件缺失。"
  . "$UPGRADE_BOOTSTRAP"
  ios_upgrade_begin || lc_install_fail "数据备份失败，已中止安装。"
  ui_print "[3/4] 安装亮度引擎与轻量 WebUI"
  unzip -o "$ZIPFILE" -d "$MODPATH" >&2 || lc_install_fail "解压失败，已恢复数据。"
  [ -s "$MODPATH/system/bin/luma_curve_daemon" ] &&
    [ -s "$MODPATH/luma_curve.conf" ] && [ -s "$MODPATH/webroot/index.html" ] || lc_install_fail "安装包不完整。"
  set_perm_recursive "$MODPATH" 0 0 0755 0644 || lc_install_fail "设置文件权限失败。"
  for _lc_exec in system/bin/luma_curve_daemon service.sh post-fs-data.sh check_status.sh uninstall.sh luma_curvectl.sh; do
    set_perm "$MODPATH/$_lc_exec" 0 0 0755 || lc_install_fail "设置执行权限失败。"
  done
  [ ! -r "$MODPATH/framework-broker.jar" ] ||
    set_perm "$MODPATH/framework_daemon_launcher.sh" 0 0 0755 || lc_install_fail "设置框架启动器权限失败。"
  lc_compat_probe "$MODPATH/system/bin/luma_curve_daemon" "$MODPATH" || lc_install_fail "设备能力检测失败，已中止安装并恢复数据。"
  ios_upgrade_finish "$MODPATH/luma_curve.conf" || lc_install_fail "配置恢复失败，已中止安装。"
  ui_print "[4/4] 完成模块切换"
  lc_commit_legacy_removal || lc_install_fail "旧模块切换失败；如原版已停止，请重启恢复。"
  rm -f "$LC_INSTALL_BOOTSTRAP" "$UPGRADE_BOOTSTRAP" "$COMPAT_BOOTSTRAP"
  ui_print ""
  ui_print "安装完成。请重启设备，再从管理器打开 WebUI。"
  if [ -f /data/local/tmp/luma_curve.paused ]; then
    ui_print "检测到此前保留的暂停状态：重启后引擎不会自动启动。"
    ui_print "需要运行时，请在 WebUI 设置页点「恢复」，无需再次安装。"
  fi
  if [ -r "$MODPATH/framework-broker.jar" ]; then
    ui_print "当前是 HyperOS 4 本地体验构建，框架引擎会在重启后接管。"
    ui_print "此内核尚未推送 GitHub；管理器更新源不会提供它。"
  else
    ui_print "后续可在管理器检查更新并直接安装。"
  fi
  ui_print "官网：https://lc.rongshangs.top"
}

lc_install_module
