# 4.0.0 Beta 01：折叠主光感与监听绑定复审

日期：2026-10-19。范围：在公开 4.0.0 基础上新增已验证 ABI 的折叠输入，原标准主光感与辅助路线保留；本地 Beta，不发布。未引入机型 / OS 版本白名单。

## 样本与原因

私有分析包显示基础曲线引擎已连接、照度有效，暗光锁定配置无法应用；不能将其概括为全部引擎失效。当前默认逻辑屏的 ABC.mLightSensor 使用 xiaomi.sensor.light_secondary 私有照度源，旧检查只接受 TYPE_LIGHT。

进一步核对 SensorService 的注册记录发现：ABC 实际注册了 OEM FOV 虚拟光感，ABC 字段仍保留配置的物理光感。仅放行私有类型还会跟错输入。固件 switchLightSensor、setLightSensorEnabled 与 ABC 的 SensorEvent 消费者相互印证；原生正常数据使用 values[0]，确认驾驶状态的特定数据包使用 values[2] × 原生倍率，不能统一重复缩放。

显示布局包含两个物理屏，并由系统为各设备状态决定默认逻辑屏映射。传感器清单还包含标准、辅助、唤醒、原始、工厂与流式通道。数量和名称不能充当选择器。本次只跟随默认逻辑屏的真实对象，不新增独立副屏或外接屏控制。

## 路线契约

| 项目 | 实现 |
| --- | --- |
| 功能及路线 | main_sensor：保留 oem_main；新增 oem_selected_secondary、oem_selected_fov；dark_lock 仍使用 abc_sensor_manager 组合输入适配器 |
| 负责模块 | BrightnessSensorAccess 集中选择、验证、单位及绑定快照；RuntimeFeatureChecks 与 BrightnessControl 共用；FeatureCompatibility 只读展示 |
| 标准旧路线 | 默认屏 ABC 原 TYPE_LIGHT 检查优先；不因额外 FOV 槽存在或异常改变既有标准分支；原辅助 TYPE_LIGHT / 原生私有类型 / 已验证移植语义不变 |
| 私有直出路线 | ABC 已选中、精确语义 xiaomi.sensor.light_secondary、私有类型范围、非唤醒、按变化上报、无权限、有限有效量程 / 精度；暗光控制要求其为直接 lux，不猜测未知缩放 |
| FOV 路线 | 配置主光感为非标准类型时，要求真实启用布尔、默认屏标识、Sensor 类型 FOV 对象、非静态 Sensor→Sensor 选择器返回同一类型和句柄、私有虚拟源及已验证元数据；不硬编码数字类型或句柄，当前已验证虚拟源 stringType 为空 |
| FOV 消费者 | 正常 values[0] 已为 lux；values.length>2、values[1]==1 且原生 getDrivingStatus 为 true 才用 values[2] × getFovAmplifyFactor；两个 getter 需完整非静态签名，倍率有限且在原界限内 |
| 拒绝 | 优先接口存在但残缺、签名错误、选择器抛错或指向其他对象，不回落物理槽；拒绝未知语义、原始 / 工厂 / 流式 / 唤醒 / 要权限源、错误倍率、重复主辅句柄和其他逻辑屏 |
| 绑定归属 | 当前默认逻辑屏 owner，实际 impl / ABC / dual / SensorManager，配置主光感与实际主辅句柄，倍率，面板配置及可读物理屏唯一标识；反射方法按实际对象类获取，不按类名跨加载器缓存 |
| 线程与控制 | 沿用显示 Handler；不创建新 Hook、线程或服务，不枚举 / 抢选其他光感；仍按用户、屏幕、HDR、距离、原生接受照度、手动接管和当前自动模式门槛进入 |
| 单位与功耗 | 原标准与辅助倍率不变；新 FOV 与已换算照度分开解析；沿用原 1 秒监听请求与现有健康检查，不增加轮询 / 唤醒锁，不宣称测得功耗改善 |
| 停止与恢复 | 绑定任一归属变化时注销旧监听、丢弃两路采样与确认时间，仅恢复本模块拥有的自动模式；全新监听代际拒绝旧队列数据，即使句柄相同也不跨代混用 |
| 部分注册失败 | 主辅任一注册失败注销本代监听；先失效代际，再执行清理，避免旧回调再写状态；仍沿用原故障恢复机制 |
| 状态与自检 | 读取状态不调用 bindSensors，不改变模式或监听；main_input_compatibility 提供实际 / 配置光感与选择方式，watch_binding_active / watch_main_profile / watch_physical_display_id 区分控制绑定；缺证据不猜选路线 |
| 配置保留 | 不修改 schema、默认值、迁移、保存协议或保持范围；能力失败只暂停本次功能，原保存值保留；包名及签名连续 |

新增类登记 source-map，路线 / ABI 登记 compatibility-manifest 与 CompatibilityCatalog，生成 compatibility-index；未改动 baseline 的旧契约。未合入独立 official_curve、API 102 或其他研究改动。

## 验证

- 生产 BrightnessControl 与 Android 服务替身：454 项，含原标准 / 辅助与移植行为、私有直出、FOV 正常 / 驾驶解析、未知元数据、配置槽和实际槽区分、屏幕与控制器切换、状态只读、所有权恢复及旧代际回调。
- 生产功能自检：321 项；生产兼容展示：1094 项；私有实际固件：21 项。固件只读确认选用源、显示拓扑、原始通道隔离与数据消费者，不代表 Android 执行。
- 维护契约：24 项；完整构建执行所有既有主机 / 私有固件回归、Android Java 编译、资源和 DEX 构建、原签名校验、APK ZIP 与来源文件核对。实际记录位于本地 build/fold-compat/，打包核对见 build/refactor-hook/verification.json 和 Beta 目录 build-info。
- 没有目标折叠机安装、折叠 / 展开长测、Android 渲染或功耗测量。不同的私有光感、非空语义的未验证虚拟 FOV、其他副屏独立链路不在此次已验证范围。

## 交付边界

4.0.0-beta01 / 40001 / beta-4.0.0-beta01。公开正式 4.0.0 文件保留；APK / 源码 / 完整包位于独立 Beta 目录。分析包按其实际运行版本 4.0.0 归入私有诊断目录；固件、反汇编、认证和签名材料不进入公开产物。仅用户再次明确要求后才发布或安装手机。
