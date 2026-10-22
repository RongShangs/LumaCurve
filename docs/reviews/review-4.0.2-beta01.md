# 4.0.2 Beta 01 折叠主屏面板复审

2026-10-20。用户授权折叠面板修复后按 4.0.2 Beta 交付，仅本地打包，不发布、不安装手机。

## 诊断

样本实际运行 4.0.1 Beta 02。当前逻辑显示 0 是外屏物理端口 1；同一原生布局在不同折叠状态选择物理端口 0 / 1 作为默认屏。旧发现器固定识别 panel0，排除 panel1。诊断中 panel0 为 0、panel1 为 912，DSI-1 disabled / Off、DSI-2 enabled / On。原生守护正确拒绝向 0 节点输出，报 screen_not_writable。并非 Root 被拒，也不应通过允许写黑屏解决。

## 新路径与隔离

ActivePanelNode 集中读取本机原生 display_layout_configuration.xml：至少两个不同物理屏可为默认屏才采用折叠路线；实时 DisplayManagerGlobal 的默认内部屏、DisplayAddress.Physical 的 ID / port 与 uniqueId 一致，布局和端口匹配，成对背光在同一控制器下，真实 sysfs brightness / max_brightness 有效，且只有对应 DSI 连接且点亮。扫描后重读默认物理屏，变化即等待。XML 有界 UTF-8、预先拒绝 DTD / 实体声明；不依赖特定 XML 解析器私有 feature flags。

没有折叠切换证据时仍调用原 PanelNodeDiscovery，不将背屏、外接屏或仅有非零节点当作主屏。实际折叠证据存在但不足时等待，不回退固定内屏。适配基于实际接口和数据，未写死型号、系统版本、物理 ID、节点上限。当前实现验证的是成对 panelN / DSI 端口结构，其他结构仍需实际证据。

新增原生显式 --active-fold 入口，仅允许绑定端口的精确 backlight 节点；普通 panel_class_path 的副屏拒绝规则不变。折叠 v3 权限恢复记录带端口，v2 和旧记录仍独立严格校验。原生启动及每次写入 / 修正前核验唯一点亮 DSI，切屏或熄屏退出并恢复权限；0 节点继续拒绝，未取消屏幕保护。此路线的熄屏结束本次接管，不承诺跨折叠或锁屏延续手动目标。

Root / 面板 / 系统接管请求携带默认物理屏身份；框架输出隔离、续租和确认检查同一身份。物理屏切换后不再阻止新主屏的正常输出，只恢复模块取得的自动状态。面板丢弃旧排队目标及旧对话框，重新读取真实节点上限，再由新手势接管。状态和自检只读，显示采用路线及物理屏，未采用路线仍可展开。

仅 NativePanelRoot 的手动面板接入新选择器；户外节点输出和隐藏的控制中心映射不自动借用折叠路径，旧路线及用户设置保留。新增 Java / 原生文件和路线登记于 source-map / manifest / CompatibilityCatalog，旧 baseline 不修改。

## 验证与边界

实际分析包包含的布局、DSI 状态与节点，以及 framework.jar 的 getDisplayInfo / DisplayAddress.Physical API 完成静态核对。原始包、固件及展开内容留在私有诊断目录，不进入发布产物。

执行实际选择器与原生门控的主机文件夹具：内 / 外切换、错误物理 ID、双屏或无屏点亮、实际零值、范围异常、扫描中切屏、无布局旧路径、DTD 拒绝、v3 恢复及 v2 副屏拒绝。保留旧发现 / 权限 / Root 事务 / 显示线程 / 会话等全部回归，新增系统会话跨物理屏拒绝和自动恢复测试。完整结果见私有 build/beta-4.0.2-beta01/。

主机和固件核验不等于目标机实测；本轮没有安装到折叠机、长期折叠切换或功耗测量。发布的正式版和原有 Beta 的 APK / ZIP 保持原字节。
