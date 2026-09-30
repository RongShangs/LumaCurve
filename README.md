# LumaCurve · 流光亮度

面向 HyperOS 4 的开源免费亮度引擎。原生 C 核心以**前置优先、后置参考**判断场景，沿**可编辑的 14 点曲线**平滑调节主屏；自动亮度下的手动选择会保持到明显场景变化或锁屏，同一照度区间的重复偏好可缓慢学习到附近锚点。曲线可关闭学习、手动编辑并保存预设。持续强光增强与极端温控在设备条件允许时生效。

版本 **1.0.0 / 10000**，ID `luma_curve`，作者酷安@戎Shangs。沿用 **GPL-3.0**，保留[来源与上游贡献](docs/来源与许可证.md)。

- 官网：[lc.rongshangs.top](https://lc.rongshangs.top)
- 仓库：[RongShangs/LumaCurve](https://github.com/RongShangs/LumaCurve)
- 博客：[rongshangs.top](https://rongshangs.top)
- [酷安@戎Shangs](https://www.coolapk.com/u/3261403)

## 曲线与学习

设置顶部提供引擎控制，其后是统一曲线编辑器。14 个锚点可在 0.1%–100% 完整范围修改，邻点按需联动并保持单调，也可保存预设；学习默认开启，可以关闭并保留曲线。

确认的手动调节只训练附近锚点：8 秒确认，每次最多改变该点高度的 0.25%，至少间隔 60 秒，累计限制 ±20% 并保持单调。用户手动选择在照度变化不足 3 倍或 8 lux 时继续保持；达到更大差异后仍需至少 3 秒、3 次确认才退出，锁屏时立即结束。不会因系统自行调光训练整条曲线。详见[学习与预设说明](docs/偏好学习与可编辑曲线实施.md)。

状态页每秒显示原始光感、控制输入、平滑照度、曲线和实际背光。温控区分不可读、辅助与可信温度；“变化确认”指防抖，允许调节不等于已达到目标。日志有时间戳，跟随最新内容但不打断向上阅读。关于页介绍模块，运行核心标记只放管理器模块描述中。

## 安装与构建

当前框架输出版本面向 HyperOS 4，安装需 ARM64、Root、可用光感，以及与安装包固件校验一致的显示框架接口。WebUI 控制需要 KernelSU 兼容桥。安装依次检查冲突模块、固件与主屏接口，备份现有数据，然后安装并恢复配置；发现 `ios_auto_brightness` 时通过音量键选择卸载冲突模块并继续，或取消安装。安装预检不能代替实机使用验收。更新保留本模块配置、学习和预设。

```powershell
cd D:/IOS/LumaCurve
python tools/build_framework_probe.py
python tools/build_framework_module.py --release
python tools/package_source.py
python tools/sync_website.py
```

输出 `dist/luma_curve-1.0.0.zip`、`dist/LumaCurve-1.0.0-source.zip`，并同步到静态官网目录。正式包使用 36 个明确列出的 C 输入及 HyperOS 4 显示框架代理；ARM64 PIE 保留 16KB 段对齐。构建不依赖原 ELF。

核心在 `csrc`，安装与 WebUI 在 `module`，工具在 `tools`，回归在 `tests`。运行数据在 `/data/local/tmp/luma_curve*`，持久备份在 `/data/adb/luma_curve`。状态发布与控制分离，浏览界面不会增加传感器事件。

## 官网与更新

`website` 是独立静态官网，没有框架、外部字体或构建步骤。`python tools/sync_website.py` 输出工作目录 `web`，包含对应模块、源码下载及 SHA-256；域名由维护者自行部署。

模块管理器通过官网的 `update.json` 检查版本；WebUI 打开时从官网的 `update.js` 检查，有新版本才弹窗。下载后的 ZIP 由用户在模块管理器中安装。本仓库目前按维护者要求保持私密；在公开前，用户可从官网取得与安装包对应的完整 GPL-3.0 源码，GitHub 的仓库链接需要访问权限。打赏完全自愿。

## 验证范围

本地验证包含旧基线、时域、双侧场景、局部学习与保护、语义负向测试、浏览器交互、安装回滚和独立源码构建。报告在 `docs/validation`；新行为与旧对照明确分开。耗电、SELinux、厂商手动事件、温度节点与 HBM 仍需实机验收，缺失可信温度不会解释成设备凉爽。

原生 C 核心计算开销很小，但正式包还常驻 Java 显示框架代理，不能将早期 C 版的“30 MB / 0.02%”作为整包数据。一组亮屏实测为：C 核心约 **9.4 MiB PSS**、所有模块进程合计约 **65–71 MiB PSS**，60 秒窗口 CPU 合计约 **1.26% 单核**。这些数据随设备和状态变化，不代表熄屏耗电。官网使用同一口径。
