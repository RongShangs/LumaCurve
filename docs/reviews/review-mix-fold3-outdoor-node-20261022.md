# MIX Fold 3 节点高亮开关置灰分析

2026-10-22。用户提供 Beta 03 新分析包，反馈“强制达到节点上限”开关为灰色。本轮只定位并归档，不启用未经验证的节点增强路线，不改代码、保存设置或版本，不重打包 / 发布。

## 已确认的状态

样本为小米 MIX Fold 3、OS3.0.304.0.WMVCNXM，运行 beta-4.0.2-beta03。外屏 panel1-backlight 已被折叠发现路径正确选中，最大值 4095，节点可打开；本次显示端口 132、内核节点 1 的独立绑定已工作。传统过渡自检也已通过，不能把本次问题重新归为 Beta 01 的默认屏取址故障。

保存配置中户外增强、高亮范围、画面限亮放宽均开启，nit 上限为 0。它们已满足开关的配置前提，未开启 HBM 预算调整不是节点增强的必需前提。实际阻断的是独立能力 outdoor_node_supported 为 false；其框架自检 node_output 报 pending。

导出时自动亮度关闭，户外控制器说明为 manual_mode。它会使户外增强不触发，但不是节点开关持续置灰的独立原因。当前没有守护运行，也没有开启节点增强的保存记录。

## 两层未完成的适配

1. **框架输出来源识别。** OutdoorNodeBridge.interfaces 要求能够取得当前输出策略。HookRuntime.outputStrategy 当前只读取 DisplayPowerController.getLastSelectedStrategy()；该设备框架不存在此方法，返回 null 后能力检查失败。当前框架采用独立 AutomaticBrightnessStrategy2，显示日志中即使 reason=automatic 仍可能出现 strat=InvalidBrightnessStrategy。因此不能把 Invalid 当自动，也不能只看自动亮度总开关放行，以免覆盖窗口指定、临时、跟随或其他输出。
2. **折叠节点的户外协作写入。** Beta 01—03 新增的 ActivePanelNode 目前用于手动面板；OutdoorNodeRoot 仍走旧 PanelNodeDiscovery。原生 --outdoor 入口也没有接入 --active-fold 及切屏身份绑定。仅手动面板可用不能证明户外节点守护适配完成，单独取消 UI 禁用或替换一个节点发现函数会留下跨屏控制风险。

本次 services.jar 与此前已反汇编的 MIX Fold 3 样本逐字节一致，实际 DPC 字段和方法核对支持以上结论；不以机型或 OS 大版本进行推断。接口探测返回 false 被当前通用检查表述为 pending，这里应理解为现有路线不完整，反复等待不会自动补齐接口。

后续如实现，应在独立适配器补充原生自动输出归属证据，并让户外 Java 租约、Root 发现、原生协作写入共同绑定同一当前物理屏；保留特殊输出、手动接管、HDR、温控、电量、HBM 条件、熄屏和切屏退出。不能仅改布尔值或删掉旧路线。

## 数据与验证边界

原包已按机型归入私有 diagnostics/packages/小米MIXFold3_2308CPXD0C/，选取的状态、日志与核对结果在对应 unpacked 机型目录；旧 build/node-a48f59 路径以目录连接保留。ZIP CRC、SHA-256 及迁移文件哈希已核对。固件、日志和设备配置不进入公开源码与交付物。

结论是 **Beta 03 尚未适配这台机的强制节点高亮路径，置灰不是用户漏开前置选项**。本次没有真机节点增强效果、亮度上限或功耗验证，也没有将接口分析表述为修复完成。
