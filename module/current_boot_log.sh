#!/system/bin/sh
# Print only the active log lines from the requested boot.
[ "$#" -eq 2 ] || exit 1
log_file=$1
boot_id=$2
[ -r "$log_file" ] && [ -n "$boot_id" ] || exit 1
boot_marker="LUMA_BOOT_ID=$boot_id"
if grep -Fq "$boot_marker" "$log_file"; then
  awk -v marker="$boot_marker" 'index($0, marker) { showing=1 } showing { print }' "$log_file"
elif [ "$(cat "${log_file}.boot_id" 2>/dev/null)" = "$boot_id" ]; then
  # Size rotation can remove the marker; its retained tail is still from this boot.
  cat "$log_file"
fi
