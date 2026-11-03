# HyperLux 项目范围与任务边界

长期规则以 [GLOBAL_RULES.md](GLOBAL_RULES.md) 为准；产品与技术设计见 [PROJECT.md](PROJECT.md)。本文件只规定工作范围，不保存版本进展。

## 生产主线

- `experimental/refactor_hook/` 是当前APP、legacy API82 Hook和原生主屏工具主线。
- `tools/build_refactor_hook_test.py` 是主线构建/验证入口，根CMake和历史KSU产物不能代替当前APK构建。
- 支持按真实HyperOS接口扩展原机、官改和移植系统能力；不以某个型号或系统大版本直接放行。
- 仅配置已有系统场景，不开放用户新增命名场景；完整/分区节点控制、应用暂停、应用隔离和普通曲线是不同功能及控制权。

## 历史与独立研究

`module/`、`csrc/`、`include/`、`experimental/framework_output/`为历史工程；API102、official_curve、uevent和固件研究独立保留。没有当前用户授权和完整主线核验时不自动合入、发布或修改其未提交工作。

## 授权边界

本地检查和可回退修复可继续完成；实际功能改动按长期授权生成递增Beta。正式发布、服务器版本切换及真机安装/重启遵守当前用户明确授权，不沿用历史版本日志里的“已授权发布”。

只读诊断不自动包含主动调亮度、熄屏或重启测试。服务器认证、签名材料、用户诊断及固件均留私有区域；设备诊断按实际机型分类。

## 当前工作

只在 [docs/work-status.md](docs/work-status.md)维护当前目标、待验证事项、发布状态和暂停任务。任务完成后改该状态或新增复审记录，不在本文件追加交接段落。
