# 主屏原生守护工作目录回退复审（beta03）

## 输入与结论

2026-11-22 的 Redmi K90 Pro Max/myron/25102RKBEC、OS4.0.6.0.XPMCNXM 分析包显示 Root 授权、节点发现（`panel0-backlight`，上限16383，`open_ok`）与曲线均正常；失败点为 `主屏操作失败（启动主屏原生守护）：Root 工作目录的父目录不可信`。`state.json` 的 `root_access` 同时显示首选 `/data/adb/luma_curve_refactor_test` 不可用、备用 `/data/local/.hyperlux-root-0` 已选中且可用，`native-storage.json` 显示首选目录本身仍为 uid 0、mode 700。同机 2026-11-01（4.4.0）与 11-03（4.4.3）的分析包中首选目录通过校验；11-03 的失败停在更后面的 `root_private_lock`，最后一次成功守护日志为 raw11。因此判定为设备侧 `/data/adb` 属性变化触发既有父目录校验，不是曲线、节点或 Root 权限整体失效。

## 实现

- `RootWorkspace`：新增 `candidates()`、`directory()`、`isCandidate()`；`requireNative()` 依次核验已选目录、首选、备用，全部通过既有 `secure()`（父目录为 root 所有且组/其他不可写、目录非链接、属主 root、规范路径一致、可读写）才返回，二者都失败时抛出一条同时说明首选与备用的错误，不写回任何配置。`nativeEvidence()` 改为只读列出两个候选目录及其锁、记录、程序与权限，供分析包定位。
- `NativePanelRoot`：`bin(dir)`、`logFile(dir)`、`install(File)` 跟随选定目录；安装器仍在同一目录创建临时程序、先以候选执行 `--recover --workspace=<dir>`、成功后才原子替换，失败保留旧程序并删除临时文件。`start()` 使用安装返回的目录，STATUS 新增 `workspace`；同节点但目录不同的守护会被停止后重建，无 `workspace` 字段的旧守护继续兼容复用（其节点、上限与规范路径仍逐项核对）。`stop()` 对两个候选目录分别尝试恢复：原生 `--version` 握手返回 `workspace-v1` 时使用新参数，旧程序退回旧固定目录的 `--recover`，不向旧程序传入无法识别的参数。
- 原生 `main_panel.c`：去掉编译期固定 `DIR`/`RECORD`，改为启动时解析唯一的 `--workspace=` 并派生 `record_path`、`outdoor_health_path`；`private_directory(path)` 接受调用方路径但复核内容与原来一致；锁、权限记录、户外健康文件、`NATIVE_READY` 与 STATUS 全部使用同一路径。新增 `--version`（`NATIVE_VERSION workspace-v1`）；参数解析先去除非模式参数，原有 `--daemon`/`--outdoor`/`--control-center`/`--active-fold`/`--fold-ids` 计数与顺序校验不变。
- `OutdoorNodeRoot`：户外节点守护同样安装到选定目录并携带 `--workspace`，健康文件、日志与 `/proc/<pid>/cmdline` 校验都绑定该目录；启动、停止与状态报告都会扫描两个候选目录，发现旧目录残留守护时终止，避免两个工作目录各跑一个写者。
- `RootControl`/`DiagnosticCollector`：面板日志、`last-panel-error.txt`、`last-control-error.txt`、临时命令输出与旧模块暂停标记改用 `RootWorkspace.directory()`；导出仍读取当前目录，并在使用备用目录时把首选目录的既有面板记录放入 `retained/`，`build.txt` 记录本次工作目录。

## 安全边界

父目录检查、目录身份、`O_NOFOLLOW` 打开与 dev/inode 复核均未放宽，也没有接受组/其他可写或非 root 父目录。备用目录只是第二个候选，而不是绕过条件；全部失败时主屏直写保持关闭。旧守护只在节点身份完全一致时复用；未知节点仍拒绝。原生程序只从两个候选目录读取并执行，`--workspace` 只接受单一普通绝对路径，不接受引号、反斜杠、控制字符、尾斜杠或超长路径。

## 验证与限制

主机回归：原生私有存储/安装器 307 项（含首选不可用、备用可用、两目录失败、备用父目录被改写/属主错误/别名、非法工作目录名），Root 访问 192 项，面板打开 141 项，主屏事务 65 项，户外折叠 195 项，户外写重试/恢复 101 项，恢复边界 115 项，原生日志传输 26 项，原生面板 141 项，户外节点 10795 项。完整构建、签名与交付核验见本机构建回执。以上均为主机与静态核验，不等于 Android/SELinux 或该 K90 设备的真机结果；实际 `/data/adb` 属性、备用目录接管、写入与恢复仍需新包回访，不据此宣布问题已在设备上修复。
