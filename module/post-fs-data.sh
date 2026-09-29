#!/system/bin/sh
# LumaCurve 1.0.0 early permissions

MODDIR=${0%/*}
chmod 755 "$MODDIR/system/bin/luma_curve_daemon" 2>/dev/null
chmod 755 "$MODDIR/customize.sh" 2>/dev/null
chmod 755 "$MODDIR/check_status.sh" 2>/dev/null
chmod 755 "$MODDIR/uninstall.sh" 2>/dev/null
chmod 0644 "$MODDIR/upgrade_data.sh" 2>/dev/null
