#!/system/bin/sh
# Move only the launcher process before exec; never unfreeze an application group.
lc_scope_path() {
  awk -F: '$1 == "0" && $2 == "" {print $3}' "/proc/$1/cgroup" 2>/dev/null
}

lc_scope_detach_self() {
  [ -r "/proc/$$/cgroup" ] || { echo '[LumaCurve启动] 无法读取进程控制组，阻止启动。' >&2; return 1; }
  _lc_scope_before=$(lc_scope_path "$$")
  case "$_lc_scope_before" in
    /) printf '[LumaCurve启动] launch_build=20260929-freezer01 cgroup=already_root\n'; return 0;;
    '')
      # Older systems without cgroup v2: refuse an inherited v1 freezer group.
      _lc_v1_freezer=$(awk -F: '$2 ~ /(^|,)freezer(,|$)/ {print $3}' "/proc/$$/cgroup" 2>/dev/null)
      case "$_lc_v1_freezer" in ''|/) return 0;; *) echo '[LumaCurve启动] 未能脱离 v1 freezer，阻止启动。' >&2; return 1;; esac
      ;;
    /*) :;;
    *) echo '[LumaCurve启动] 无法识别控制组，阻止启动。' >&2; return 1;;
  esac
  # Field 4 must be /: a bind/subtree mount is not the real hierarchy root.
  _lc_scope_mount=$(awk '$4 == "/" {for (i=7;i<NF;i++) if ($i == "-" && $(i+1) == "cgroup2") {print $5; exit}}' /proc/self/mountinfo)
  case "$_lc_scope_mount" in /*) :;; *) echo '[LumaCurve启动] 缺少 cgroup2 根挂载，阻止启动。' >&2; return 1;; esac
  case "$_lc_scope_mount" in *\\*) echo '[LumaCurve启动] 无法解析控制组挂载路径，阻止启动。' >&2; return 1;; esac
  [ -f "$_lc_scope_mount/cgroup.procs" ] || { echo '[LumaCurve启动] 控制组迁移接口不存在。' >&2; return 1; }
  if ! printf '%s\n' "$$" > "$_lc_scope_mount/cgroup.procs"; then
    echo '[LumaCurve启动] 脱离应用控制组失败，阻止启动。' >&2
    return 1
  fi
  [ "$(lc_scope_path "$$")" = / ] || { echo '[LumaCurve启动] 控制组迁移读回不符，阻止启动。' >&2; return 1; }
  printf '[LumaCurve启动] launch_build=20260929-freezer01 cgroup=detached from=%s\n' "$_lc_scope_before"
}
