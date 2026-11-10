# 控制中心开机恢复：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### Android开机恢复 · `mapping_startup`

| 项目 | 要求 |
| --- | --- |
| 触发 | 已保存启用映射，开机/解锁或真实SystemUI就绪事件触发受管恢复。 |
| 需求与行为 | 开机、解锁及系统界面就绪触发同一有界恢复，核验boot/进程/用户/revision/物理屏并等实际回执，不回放历史手势或亮度；折叠主屏或其本次标定暂未就绪为pending，损坏布局不降级；保存路径仅在当前已验证等范围配对节点间复用，不重写保存配置 |
| 退出 / 失败 | 关闭映射取消任务；Root拒绝即停止，接口未就绪仅有界重试，不回放旧手势或亮度。 |
| 默认 | `on_request` · 仅已保存启用映射才恢复 |
| 配置键 | `control_center_node_mode` |
| 依赖能力 | 由本项实际接口独立判断 |

### Root开机恢复 · `mapping_boot`

| 项目 | 要求 |
| --- | --- |
| 触发 | 已保存启用映射且受管Root启动入口实际执行，身份、目录和本次boot证据有效。 |
| 需求与行为 | 受管service.d入口与Android入口共用恢复契约；文件存在不等于本次已执行；停用仅清理自己的入口 |
| 退出 / 失败 | 仅更新/撤回内容哈希和受管标记吻合的启动文件；等待与连接次数有限，不建永久循环。 |
| 默认 | `on_request` · 仅真实权限允许时部署 |
| 配置键 | `control_center_node_mode` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

仅已保存开启的主用户映射，在开机解锁或覆盖安装后通过系统JobScheduler恢复；Root重新核验本机原配置、当前系统进程/启动代次、标定和节点。可读Settings仅作调度提示，读取失败不覆盖本地记录。 APP广播可能受后台策略延后；已保存开启且Root目录可读写时，补充受管service.d脚本入口，按真实目录权限选择，文件可执行与开机实际执行分别记录，不宣称所有Root实现均支持。 系统界面实际读到本次有效TABLES且缺少绑定时，以平台签名权限保护的明确广播触发同一Android任务；首次请求即时任务，配额/框架拒绝保留原延迟任务。成功要求当前存活SystemUI反馈同一引擎代次，不仅是PROFILE写入。 折叠屏暂未点亮或同屏标定滞后保持pending，沿用既有有界任务和就绪事件；损坏布局仍unavailable，不按待就绪降级。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `per_feature` |
| 统一输出 | 共用mappingRecoveryWanted + recover；入口、执行证据与代次分开；系统界面就绪事件只唤起同一个有界Job，Root和UI共用代次/确认契约。 |
| 坐标与身份 | 同boot/pid/process_start/revision/node身份；只恢复绑定不回放亮度 |

### 清理及回滚

默认关闭不启动任务；关闭映射取消任务，Root拒绝不重试。仅接口暂未就绪最多四次，3/10/30/60秒间隔，每次Root连接8秒上限；系统取消时终止本次连接，旧任务不覆盖新代次。恢复只绑定坐标和闲置监听，不回放旧手势/亮度，无APP常驻服务。 Root启动文件仅在内容哈希/受管标记吻合时更新或撤回，未知文件/链接保留并报错；脚本只等待boot完成最多240秒，最多四次8秒连接，无永久循环。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `android_boot_job` · 开机后一次性映射恢复 | 仅恢复已保存映射；启动/解锁或系统界面就绪事件触发有界任务，首次优先即时调度并保留原回退。准备后需当前系统界面确认同一代次才报成功；不回放手势，Root拒绝停止，无常驻APP服务。 |
| `root_service_directory` · Root开机脚本入口 | 按实际Root目录权限配置受管启动文件；开机独立恢复监听，不依赖打开APP，不写入旧亮度。 |

路线内部 ABI：`mapping_startup_trigger`、`mapping_recovery_confirmation`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`startup.state`、`startup.detail`、`startup.attempt`、`startup.pending`、`hyperlux_mapping_startup_v1`、`control-center/startup.json`、`startup.trigger`、`startup.dispatch`、`startup.system_ui_confirmed`、`bound_profile`、`recovery_signal`、`log_present`。

验证入口：

- [refactor_mapping_startup.py](../../tests/refactor_mapping_startup.py)
- [refactor_control_center_root.py](../../tests/refactor_control_center_root.py)
- [refactor_system_ui_setup.py](../../tests/refactor_system_ui_setup.py)
- [refactor_root_mapping_boot.py](../../tests/refactor_root_mapping_boot.py)
- [refactor_mapping_readiness.py](../../tests/refactor_mapping_readiness.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [ControlCenterRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterRoot.java) | `49074e9977d153a4` |
| [FeatureCompatibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FeatureCompatibility.java) | `562f210773c96c49` |
| [MappingRecoveryService.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MappingRecoveryService.java) | `bb5945884db22b08` |
| [MappingRecoverySignal.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MappingRecoverySignal.java) | `1db1ab21d1981f71` |
| [MappingRecoveryState.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MappingRecoveryState.java) | `846a31654e6cb880` |
| [MappingStartup.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MappingStartup.java) | `b10d03c77ba0b2c0` |
| [RootControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootControl.java) | `6cf40b35ec4446c5` |
| [RootMappingBoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootMappingBoot.java) | `c6502c10b0b44fd1` |
| [RootPanelBridge.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootPanelBridge.java) | `d756398bad23b1d4` |
