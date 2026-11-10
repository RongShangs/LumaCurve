# Root 存储与原生工具：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### Root操作目录 · `root_storage`

| 项目 | 要求 |
| --- | --- |
| 触发 | Root桥需要读写配置事务文件或生成诊断，在实际身份下校验受管目录。 |
| 需求与行为 | 私有受管目录优先，拒绝时可用受管临时目录；验证所有权、真实路径和权限，不把目录可写当成节点可写 原生锁只规范可信Root自有目录和同inode文件的私有权限，不删除或强抢锁；导出可只读记录元数据。 |
| 退出 / 失败 | 候选目录校验失败只拒绝对应工具；未知文件和链接保留，不移动原生节点。 |
| 默认 | `on_request` · 按实际权限选择 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 保存配置读取 · `stored_config`

| 项目 | 要求 |
| --- | --- |
| 触发 | APP读取设置、恢复配置或导出当前已保存内容。 |
| 需求与行为 | 区分草稿、持久化原文、运行裁剪副本和实际状态；坏数据保留证据，不回写默认 |
| 退出 / 失败 | 读取失败或坏数据保留原文和草稿，不能生成默认配置覆盖原记录。 |
| 默认 | `observe` · 只读保存结果 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 户外节点设备绑定 · `outdoor_node_device`

| 项目 | 要求 |
| --- | --- |
| 触发 | 节点增强已请求，原生框架支持且当前内部主屏及节点唯一绑定。 |
| 需求与行为 | 普通单主屏与折叠当前端口分别校验，复用同一租约；换屏释放旧目标，保留偏好 |
| 退出 / 失败 | 物理屏变化、熄屏或租约失效时结束旧屏协作，不跨屏复用目标。 |
| 默认 | `off` · 显式节点增强时使用 |
| 配置键 | `outdoor_node_boost`、`outdoor_node_target` |
| 依赖能力 | `node_output`、`main_panel_node` |

### 原生资产加载 · `native_payload`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户请求节点控制或对应工具需要读取并验证APK中的原生资产。 |
| 需求与行为 | 校验最终APK内程序、协议和执行身份；查看页面不启动写入器；资产失败只停对应工具 候选程序先完成原生恢复再原子替换，失败保留原文件；目录/身份/占用失败明确分开，权限残留不以关闭校验绕过。 |
| 退出 / 失败 | 资产、协议或执行校验失败停止对应原生工具，曲线和其他可用能力不回滚。 |
| 默认 | `on_request` · 需要节点控制时加载 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### Root权限身份 · `root_identity`

| 项目 | 要求 |
| --- | --- |
| 触发 | Root桥启动或用户主动进行权限诊断。 |
| 需求与行为 | 检查实际UID与限制，不能按KSU、Magisk或越狱模式名称假定完整权限；与LSPosed注入分开 |
| 退出 / 失败 | 无权限或读取失败标记对应操作未确认，不以此否定已核验LSPosed功能。 |
| 默认 | `observe` · 报告真实权限 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 系统设置桥 · `root_settings`

| 项目 | 要求 |
| --- | --- |
| 触发 | 需要读取/提交系统设置；Provider读取确实不可用时才评估同权限命令入口。 |
| 需求与行为 | 读取可回退同权限系统命令，写失败不跨路线重复写；继续经过系统鉴权，保留确认及失败回退 |
| 退出 / 失败 | 写失败不跨入口重放，提交未确认则按revision回退并保留用户草稿。 |
| 默认 | `on_request` · Provider优先 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### Root进程信息 · `root_process_identity`

| 项目 | 要求 |
| --- | --- |
| 触发 | 自检或导出需要实际进程身份与限制证据。 |
| 需求与行为 | 区分实际身份、目录、设置桥、节点及注入能力，读取失败明确未确认，不推断所有功能都失败 |
| 退出 / 失败 | 每次按真实进程代次读取；不能使用旧PID或旧身份报告当前状态。 |
| 默认 | `observe` · 只读真实进程 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

逐项检查私有存储、原始配置、当前 APK 资产和组合接口；不试写亮度，不擅自启动服务。 Root设置三入口互斥，原外部Provider优先；只读探测拒绝时尝试权限等价的cmd settings/settings CLI，不绕过系统权限。写入失败不重放，UID0不等于全权限。私有目录严格身份检查后才回退，原生节点不换目录。 原生程序使用同一Root私有锁检查，不按Root工具名称放行；候选载荷先完成原生恢复才原子替换。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `per_feature` |
| 统一输出 | 各操作实际权限、同一设置桥、受管目录与独立诊断结果 |
| 坐标与身份 | Root能力与LSPosed能力独立；失败不回写配置 |

### 清理及回滚

短命 Root 桥和有界 IPC；诊断不写回用户配置，失败只禁本项。 节点写入错误保留自动停用锁存；只有用户主动保存并获引擎确认后，才经过原有完整校验重试启动，不重置用户设置。 Root自检只读；启动初始化操作目录。已确认核心与可选节点工具分别报告，节点失败不回滚已确认曲线。导出失败转APP Downloads并明确标记非实时不完整包。 自检显示选中目录当前失败原因和各备用目录只读结果。 只规范可信Root自有目录与同inode锁权限；已持锁退让，错误身份/链接拒绝，不删除锁。候选恢复失败保留旧程序，仅清理本次临时文件。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `root_private_storage` · Root 私有数据目录 | 只检查配置 / 分析目录的读写能力，不安装或启动输出程序。 |
| `stored_json` · 原始配置 JSON | 读取原始配置；自检与兼容裁剪不会将默认值写回用户设置。 |
| `primary_node_profile` · 实际主屏节点与框架输出组合 | 按实际节点上限、曲线映射和输出能力核验，不按机型名单放行。 |
| `apk_native_asset` · APK 内原生程序校验 | 核对当前 APK 的主屏程序资产完整性，不启动守护、不试写亮度。 |
| `fold_bound_outdoor` · 折叠主屏节点协作绑定 | 当前物理主屏、原生布局和背光配对完整；用独立物理租约核验切屏，不跨屏继承目标。 |
| `root_temporary_storage` · Root 独立操作目录 | 原目录受限时使用受验证的私有目录；不迁移原生输出程序或用户设置。 |
| `root_actual_identity` · 实际进程权限信息 | 记录实际UID、Capabilities及SELinux上下文，不以Root管理器名称判定权限。 |
| `external_provider` · 原生设置接口 | 优先使用原有设置服务；写入仍需系统授权与读回确认。 |
| `cmd_settings` · 系统设置命令服务 | 原入口不可读时核验cmd设置服务；不重放失败的写入。 |
| `settings_cli` · 系统设置命令入口 | 前两条不可读时核验系统settings入口；仍遵守系统权限。 |
| `root_proc_identity` · 系统进程有效身份 | 核验当前系统进程、启动标识和构建；权限不足时不跳过校验。 |

路线内部 ABI：`root_attribution`、`outdoor_root_panel_binding`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`feature_self_check`、`root_feature_self_check`、`outdoor_node_device_profile`、`outdoor_node_guard.write_attempts`、`outdoor_node_guard.write_errno`、`outdoor_node_guard.write_stage`、`root_access`、`settings_route`、`settings_attempts`、`workspace_route`、`workspace_attempts`、`native-storage.json`、`NATIVE_START_FAIL.private_stage`、`NATIVE_START_FAIL.uid/euid`。

验证入口：

- [refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)
- [refactor_native_asset.py](../../tests/refactor_native_asset.py)
- [refactor_outdoor_node_settings.py](../../tests/refactor_outdoor_node_settings.py)
- [refactor_outdoor_fold.py](../../tests/refactor_outdoor_fold.py)
- [refactor_outdoor_write_retry.py](../../tests/refactor_outdoor_write_retry.py)
- [refactor_root_access.py](../../tests/refactor_root_access.py)
- [refactor_native_storage.py](../../tests/refactor_native_storage.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AppRootDiagnostics.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppRootDiagnostics.java) | `4739b5d2f6cc3a9e` |
| [NativePanelAsset.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NativePanelAsset.java) | `57da7aa4e8f0cfed` |
| [OutdoorNodeRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorNodeRoot.java) | `9def729b0d7dc0bf` |
| [RootAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootAccess.java) | `eb2e765208fa31b1` |
| [RootCommand.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootCommand.java) | `22091c80df72806c` |
| [RootControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootControl.java) | `6cf40b35ec4446c5` |
| [RootFeatureChecks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootFeatureChecks.java) | `f801660ebbb8a387` |
| [RootLaunch.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootLaunch.java) | `a681d9b5f44fe950` |
| [RootSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootSettings.java) | `3b21095ef09b28e0` |
| [RootWorkspace.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootWorkspace.java) | `a7620b43655d8bb4` |
