# 全项目兼容路线审阅 · 2026-11-02

最终本地交付：4.4.0 Beta 04 /44004；累计基线为公开4.4.0/44000。Beta03完成业务边界重构，Beta04继续全项目工程核验。两版均本地保留，未公开推送。

## 结论

现有兼容并非完全没有结构：实际接口适配、能力自检、配置副本和控制权已有分层。但本轮确认了曲线差异泄漏、记忆发布相互影响、启动条件重复及维护覆盖不足。已修关键分支和门禁；成熟的其他路线保持原优先顺序与保护，不为了统一而改写设备行为。

能确认的是本轮完整主机回归和已有固件样本静态检查通过、原路线登记未丢失、原生资产和保存语义未改。不能据此保证未取得固件/实机的全部设备效果；当前ADB无在线设备，没有新增真机或功耗结论。

## 全部兼容组

“路线”是实现分支，下面24组按实际功能组织。完整的每项功能条件、原生ABI内部变体、单位/身份、清理和测试见[自动生成索引](../architecture/compatibility-index.md)。该索引由同一manifest生成，避免页面、管理代码和文档各维护一套名单。

| 功能组 | 保留的路线标识 | 收束后的共同接口/数据 |
| --- | --- | --- |
| 曲线与手动记忆 (`curve`) | `refactor`, `refactor_coefficients`, `miui_physical`, `aosp_physical`, `aosp_physical_clamped` | CurveBackend 能力方法 + curve_contract v1 + Curves；普通手动100%显示标尺独立于全域与原生存档 |
| 主光感 (`main_input`) | `oem_main`, `oem_selected_secondary`, `oem_selected_fov` | 已验证的当前主屏Sensor与照度解码、可用性证据 |
| 辅助光感 (`assist_input`) | `native_auxiliary`, `ported_auxiliary` | 原生槽优先，唯一验证辅助源补位；双/单源运行状态 |
| 主辅确认与暗光策略 (`response`) | `native_driving`, `native_without_driving` | supported、应用/恢复操作及调整计数 |
| 原生亮度过渡 (`animation`) | `oem_animation`, `traditional_ramp` | 独立变亮/变暗倍率及原生过渡接口能力 |
| 手动阳光屏 (`sunlight`) | `manual_sunlight` | 已核验原生阳光模式字段与独立能力状态 |
| 触摸遮挡释放 (`touch`) | `touch_release` | 本次字段所有权与可逆覆盖 |
| 原生场景参与 (`scenes`) | `oem_night_driving`, `oem_step`, `oem_touch`, `oem_proximity`, `oem_stream_proximity`, `oem_reflection`, `oem_night_wake`, `oem_manual_sunlight`, `oem_game` | SceneCatalog状态、独立进入/效果/退出配置及应用计数 |
| 暗光锁定 (`dark_lock`) | `abc_sensor_manager` | DarkLockPolicy决策 + 已验证亮度控制能力/所有权 |
| 暂停自动亮度名单 (`applications`) | `visible_tasks`, `focus_fallback` | 可见名单应用/焦点回退的统一匹配结果与能力证据 |
| HBM 控制器与计时 (`hbm`) | `enabled_field`, `native_support_method`, `timed_budget`, `ambient_only` | 控制器身份/支持与预算数据；启用ABI和计时ABI独立组合 |
| 户外增强目标 (`outdoor`) | `override_target`, `legacy_custom_target` | 户外目标、进入条件与可逆参数应用 |
| 高亮范围 (`range`) | `hbm_range`, `dynamic_range` | 本机映射高亮范围支持与应用状态 |
| 灰阶放宽与物理 nit 限制 (`sdr`) | `privacy_aware`, `legacy_opr` | OutdoorSdrScope统一限定范围、优先级、保护及撤回 |
| 显示层温控 (`thermal`) | `display_thermal` | 温控策略结果与本次覆盖所有权 |
| 主屏输出隔离 (`raw_output`) | `raw_primary_output`, `automatic_strategy2_node` | RawPanelLease统一优先级、代次、屏幕身份及输出原因 |
| 真实主屏节点 (`node`) | `backlight_node`, `display_led_node`, `fold_default_display` | 已绑定当前物理屏的节点、实际max、读回及换算来源 |
| Root 存储与原生工具 (`root_tools`) | `root_private_storage`, `stored_json`, `primary_node_profile`, `apk_native_asset`, `fold_bound_outdoor`, `root_temporary_storage`, `root_actual_identity`, `external_provider`, `cmd_settings`, `settings_cli`, `root_proc_identity` | 各操作实际权限、同一设置桥、受管目录与独立诊断结果 |
| APP 本地工具 (`app_tools`) | `launcher_alias`, `app_preferences`, `app_files` | 界面偏好、窗口可用尺寸和分析包分享URI |
| 控制中心节点映射 (`control_center`) | `coordinate_v2`, `coordinate_v2_calibrated`, `raw_node_v3`, `partition_node_v4`, `partition_ddc_hbm`, `partition_hbm_normal` | 标定/分区坐标、同代profile、统一节点保持和作用域结果 |
| 日落后暗光锁定 (`dark_lock_schedule`) | `system_twilight` | 日落日出状态与是否允许锁定 |
| 熄屏前降亮与息屏显示协调 (`dim_aod`) | `automatic_dim_aod_cap` | 已核验自动DIM与全屏AOD目标协调条件 |
| 应用临时亮度隔离 (`app_brightness_isolation`) | `app_override_scoped_sync`, `native_window_no_sync` | 同屏同用户窗口来源限定的同步隔离/原生无同步能力 |
| 控制中心开机恢复 (`mapping_startup`) | `android_boot_job`, `root_service_directory` | 共用mappingRecoveryWanted + recover；入口、执行证据与代次分开 |

## 发现、修复与证据

1. 共用CurveBackend原来默认读取Refactor锚点。现由具体适配器读取；默认能力不冒充Refactor。曲线构造在CurveBackendFactory分叉并返回同一接口。
2. CurveComparison、SavedCurve、页面和MemoryPersistence存在重复后端判断。现由CurveCompatibility、后端能力方法收束；统一契约校验与旧快照读取在一个边界。原配置归属比较与诊断名称展示继续保留，它们不是设备路线选择。
3. 一个坏记忆存档会让同一try块里的实时点发布也跳过。现独立读取，错误分开呈现，档案不删除、不回写默认。
4. 两种启动入口重复判定。现共用mappingRecoveryWanted与同一个recover；Root包路径查询有限时，执行证据区分当前/历史。
5. 原防删除基线仅48项功能/81个组合，后来新增的39个组合未被同等保护。追加到58项/120个组合，原有条目保持；逐个删除均须命中明确基线错误。组合数量不是设备数量，也不是同时运行的控制器数量。
6. 登记的Root控制中心回归原先有单独测试，但未接入总构建。现接入，并增加登记测试的执行可达性检查；打包文件名不再被算作执行证明。
7. 全部24组新增必填收束登记：boundary、contract、coordinates、consumers。现有适配器、自检与共享业务责任可从同一处追踪。

## 工程规则

- 实际ABI差异在对应边界新增路径；新旧同时存在时固定优先顺序，残缺新接口不得通过旧路线绕过失败。
- 向共享管理提供明确能力、单位、所有权和状态。物理nit、逻辑nit、framework brightness与节点值不同；能换算才换算，不能伪造。
- 记忆的原生value由后端读写，统一展示用display_nit；旧存档不迁移，作用域/基准/用户/主题核验保持。
- Root权限、LSPosed注入、接口存在与当前启用分别报告。已保存配置和运行裁剪副本分开；可选失败不抹掉用户选项。
- 运行自检不试写、不安装Hook；查看页面不重选路线。临时覆盖按所有权撤回，Hook安装失败回滚。
- 新路线同时登记实现、内部ABI、证据、清理、收束和真实测试入口；检查失败不能靠删旧路线或重置配置解决。

规范：[兼容开发规范](../architecture/compatibility-policy.md)、[分叉与收束](../architecture/compatibility-convergence.md)、[源码职责](../architecture/source-map.json)。

## 验收与限制

维护负向159项，包括逐一删除全部120个功能-路线组合、收束登记缺失、向UI/管理器塞回原生分支及断开测试调用；生产曲线边界135项，含新旧数据一致、损坏拒绝降级与读取不改配置。77个登记测试脚本均连接到总构建实际执行入口。

完整构建继续执行原曲线/传感器/节点/权限/场景/HBM/事务/回滚/配置/界面测试与已存在的私有固件静态核验。Beta04相对Beta03生产Java仅AppBuild身份变化，业务重构没有在最后阶段追加修改。最终完整结果以私有build/beta-4.4.0-beta04/build.log、verification.json与local-audit.json为准。

保护450份旧APK/ZIP、原证书、raw10资产及公开4.4.0更新索引；源码和APK使用同一生产源哈希。没有发布或安装动作。

仍存在部分历史状态采集和原生执行内嵌在HookRuntime/SceneController，已在规范记录。它们不是新增机型/版本分支；后续出现ABI差异时应继续移入对应适配器，不在共用管理里复制。此次没有机械搬迁所有成熟Hook，避免组织整理改变原有行为。
