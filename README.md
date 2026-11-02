# HyperLux

**HyperOS 亮度个性化工具 · 4.4.1 正式版 / 44100**

基于 Root 与 LSPosed，在系统亮度链路内调整基础曲线、手动偏好记忆、现有场景与输出范围，保留系统过渡和保护。GPL-3.0，官方版本完全开源免费。

[官网与下载](https://lc.rongshangs.top) · [GitHub Release](https://github.com/RongShangs/LumaCurve/releases/latest) · [LSPosed](https://github.com/Xposed-Modules-Repo/top.rongshangs.lumacurve) · [作者博客](https://rongshangs.top)

## 当前版本

**4.4.1 / 44100**，保留4.4.0大版本内容，追加之后Beta01—04的改进：

1. 新增应用亮度隔离：应用内临时调亮或调暗，退出后恢复系统调节，控制中心主动调整仍按原有设置处理。
2. 扩大基础曲线可调整范围，按设备实际亮度上下限编辑；节点统一按 0—100% 显示和输入。
3. 修复分区亮度条普通区间调亮受限、绿色记忆曲线提前到顶，以及松手后滑块位置回落的问题。
4. 修复进入场景设置时误弹作用域提醒，保留已有设置和兼容路线。
5. 修复更新弹窗底部按钮遮挡，改善短屏、横屏和大字体下的显示。
6. 完善控制中心映射的开机恢复，减少重启后需打开应用或重新开关的情况。
7. 统一曲线百分比标尺，以本机普通手动亮度上限作为100%，改善不同设备的显示一致性。
8. 减少无关操作时的作用域提醒，完善启动恢复和兼容状态说明。
9. 修复记忆存档异常时当前手动记忆可能显示不完整的问题，保留已有设置和兼容路线。

[完整累计更新日志](docs/releases/4.4.1.md) · [本版复审](docs/reviews/review-4.4.1.md) · [默认行为约定](docs/guides/default-behavior.md)

当前产品使用legacy Xposed API 82。安装或更新模块后仍需重启手机；作用域变更可通过设置中的“重启系统界面”加载UI钩子。控制中心节点控制默认关闭，功能以本机实际接口自检为准；主机/静态核对不等于全部设备真机验证。

后续维护遵循 [统一兼容规范与完整索引](docs/architecture/README.md)，全项目架构审阅见 [审阅记录](docs/reviews/architecture-review-20261018.md)。

## 安装

面向 HyperOS 亮度框架，最低 Android 14，需要 Root 和 LSPosed。安装 HyperLux-4.4.1.apk，在 LSPosed 启用模块、作用域选择“系统框架 / Android 系统”；控制中心节点控制另需勾选“系统界面”，重启后打开 APP 授予 Root。后续修改设置不需要重启；更新模块后需重启加载新 Hook。其他亮度模块请先停用，避免同时控制。

完整 ZIP 是 APK、对应源码与恢复工具合集，不是 KSU 刷入模块。恢复可用设置内“恢复系统默认”，或在 LSPosed 停用并重启。

## 功能

- 六题可选快速设置，确认后统一应用，不选项目保留原设置。
- 基础曲线编辑、预设及手动偏好记忆；实际绿色曲线可保存为蓝色基础。
- 主辅光感、当前场景、生效曲线、输出保护与实际亮度状态。
- 暗光稳定、暗光锁定与可选显示层温控调整。
- 户外增强、独立 nit 上限和按真实节点范围设置的可选节点增强。
- 现有场景的进入、效果、退出配置；多窗口可见应用自动亮度名单。
- 主屏手动亮度磁贴、控制中心完整/分区节点控制、配置备份、隐藏桌面图标与分析包导出。
- 可选深色模式独立基础曲线；主题切换可清除、共用或独立保持手动记忆。

详细行为及当前默认值见 [使用与机制](docs/guides/usage.md)。设备实际接口决定可用功能，不承诺所有机型相同亮度。

## 界面预览

<table><tr><th>状态</th><th>场景控制</th><th>户外高亮</th></tr><tr><td><img src="docs/screenshots/3.0.0/status.jpg" alt="状态页示例" width="260"></td><td><img src="docs/screenshots/3.0.0/scene-controls.jpg" alt="场景控制示例" width="260"></td><td><img src="docs/screenshots/3.0.0/outdoor-brightness.jpg" alt="户外高亮示例" width="260"></td></tr></table>

截图为 3.0.0 的界面示例，当前功能以设置和发布说明为准。

## 开发入口

- [详细项目文档](PROJECT.md)：需求、技术路线、亮度机制、实现、协议与验证边界。
- [当前交接](AGENT_HANDOFF.md)：已发布状态、回执及未完成验证。
- [任务边界](PROJECT_SCOPE.md)：产品主线、历史路线和独立研究。
- [开发与构建](docs/guides/development.md)：工具链、验证、打包与签名。
- [文档导航](docs/README.md)：现行说明、发布记录、复审和归档。

当前源码在 experimental/refactor_hook/，当前构建入口是 tools/build_refactor_hook_test.py。module/、csrc/ 和根 CMakeLists.txt 属于历史 KSU/C 路线。

## 反馈、许可与感谢

异常请从日志页导出分析包，附机型、系统/模块版本及时间；设备资料建议私下提供。交流群 **314981836**，暗号 **1691**。

遵循 [GPL-3.0](LICENSE)，来源记录保留在 [来源与许可证](docs/来源与许可证.md)。作者：[戎Shang](https://rongshangs.top) · [酷安@戎Shangs](https://www.coolapk.com/u/3261403)。打赏自愿，不影响功能。

感谢名单的唯一数据源是 [website/thanks.json](website/thanks.json)，官网与 APP 共用；日常更新无需重新发布 APK，具体规则见 [发布约定](docs/release-policy.md)。

本项目与小米、红米、HyperOS 官方无关联。
