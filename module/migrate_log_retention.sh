#!/system/bin/sh
# One-time migration of the old seven-day default; later user choices are kept.
[ "$#" -eq 2 ] || exit 1
conf=$1
marker=$2
[ -f "$marker" ] && exit 0
[ -r "$conf" ] || exit 1
if grep -q '^log_retention_days=7$' "$conf"; then
  tmp="${conf}.retention.tmp.$$"
  trap 'rm -f "$tmp"' EXIT
  sed 's/^log_retention_days=7$/log_retention_days=3/' "$conf" > "$tmp" || exit 1
  chmod 0600 "$tmp" || exit 1
  mv -f "$tmp" "$conf" || exit 1
  echo migrated
fi
mkdir -p "${marker%/*}" || exit 1
: > "$marker" || exit 1
chmod 0600 "$marker" || exit 1
