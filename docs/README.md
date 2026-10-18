# 文档导航

## 当前使用

- [详细项目文档](../PROJECT.md)：完整需求、技术路线、实现、默认行为与验证。
- [架构与兼容维护规范](architecture/README.md)：职责分层、完整路线 / ABI 变体、自动校验和新增适配模板。
- [使用与亮度机制](guides/usage.md)。
- [开发、构建与交付](guides/development.md)。
- [项目任务边界](../PROJECT_SCOPE.md) 与 [当前交接](../AGENT_HANDOFF.md)。

## 版本与审阅

- [4.0.0 正式版日志](releases/4.0.0.md) 与 [完整架构审阅](reviews/architecture-review-20261018.md)：当前源码维护规范与欢迎教程修订；同版本本地复审已完成，本轮按授权发布。
- [本地 3.2.0 正式包日志](releases/3.2.0.md) 与 [复审](reviews/review-3.2.0.md)：仅打包，未发布。
- [当前 3.1.1 日志](releases/3.1.1.md)，含相较 3.0.0 的累计内容。
- [当前 3.1.1 复审](reviews/review-3.1.1.md)。
- [全部版本记录](releases/)；[专项与版本复审](reviews/)；[历史界面截图](screenshots/)。
- [发布约定](release-policy.md) 和 [来源与许可证](来源与许可证.md)。

## 历史资料

[archive/](archive/README.md) 保存旧 KSU/C 路线、整理前的长交接和 README。它们的版本、默认值和任务计划按当时保留，不能覆盖当前说明。validation/ 为早期 WebUI/KSU 校验资料，不代表当前 APP 已做实机验证。

设备包、固件、反汇编和私有回执不放在本目录。工作区的 diagnostics/index.json 管理原始分析包，build 保留必要的私有构建输入和兼容入口。
