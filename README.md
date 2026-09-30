# LumaCurve · 流光亮度

让屏幕随环境平稳调节。以前置光感为主，结合双侧场景判断与时域滤波，减少短暂遮挡与照度抖动；支持可编辑曲线、缓慢局部学习、命名预设、持续阳光增强及高温限亮。

版本 **1.0.0 / 10000**，ID `luma_curve`，作者酷安@戎Shangs。沿用 **GPL-3.0**，保留[来源与上游贡献](docs/来源与许可证.md)。

- 官网：[lc.rongshangs.top](https://lc.rongshangs.top)
- 仓库：[RongShangs/LumaCurve](https://github.com/RongShangs/LumaCurve)
- 博客：[rongshangs.top](https://rongshangs.top)
- [酷安@戎Shangs](https://www.coolapk.com/u/3261403)

## 曲线与学习

设置顶部提供引擎控制，其后是统一曲线编辑器。14 个锚点可在 0.1%–100% 完整范围修改，邻点按需联动并保持单调，也可保存预设；学习默认开启，可以关闭并保留曲线。

确认的手动调节只训练附近锚点：8 秒确认，每次最多改变该点高度的 0.5%，至少间隔 60 秒，累计限制 ±20% 并保持单调。不会因系统自行调光训练整条曲线。缺少用户事件接口时可手动编辑。详见[学习与预设说明](docs/偏好学习与可编辑曲线实施.md)。

状态页每秒显示原始光感、控制输入、平滑照度、曲线和实际背光。温控区分不可读、辅助与可信温度；“变化确认”指防抖，允许调节不等于已达到目标。日志有时间戳，跟随最新内容但不打断向上阅读。关于页介绍模块，运行核心标记只放管理器模块描述中。

## 安装与构建

当前框架输出版本面向 HyperOS 4，安装需 Android 8.0+、ARM64、Root、可用光感与显示框架接口；其他系统尚未验证。曲线可在设置中自由调整。WebUI 控制需要兼容 KernelSU 桥。安装检测基本能力；发现冲突模块 `ios_auto_brightness` 时通过音量键选择继续或取消。旧配置备份而不直接导入；更新保留本模块配置、学习和预设。

```powershell
cd D:/IOS/LumaCurve
python tools/build.py --ndk D:/App/SDK/ndk/28.2.13676358 --package
python tools/package_source.py
```

输出 `dist/luma_curve-1.0.0.zip` 与 `dist/LumaCurve-1.0.0-source.zip`。生产为 36 个明确列出的 C 输入，构建不需要原 ELF、模拟器或反编译工具。支持 Python 和标准 NDK CMake 构建，ARM64 PIE 保留 16KB 段对齐。

核心在 `csrc`，安装与 WebUI 在 `module`，工具在 `tools`，回归在 `tests`。运行数据在 `/data/local/tmp/luma_curve*`，持久备份在 `/data/adb/luma_curve`。状态发布与控制分离，浏览界面不会增加传感器事件。

## 官网与更新

`website` 是独立静态官网，没有框架、外部字体或构建步骤。`python tools/sync_website.py` 输出工作目录 `web`，包含对应模块、源码下载及 SHA-256；域名由维护者自行部署。

管理器支持 `updateJson`；关于页自动检查版本，提供新版本下载入口，安装由管理器完成。`main` 更新元数据只在 GitHub Releases 安装包就绪后启用；仅推送源码不等于发布 Release。打赏完全自愿。

## 验证范围

本地验证包含旧基线、时域、双侧场景、局部学习与保护、语义负向测试、浏览器交互、安装回滚和独立源码构建。报告在 `docs/validation`；新行为与旧对照明确分开。耗电、SELinux、厂商手动事件、温度节点与 HBM 仍需实机验收，缺失可信温度不会解释成设备凉爽。

作者设备实测资源占用：内存约 **30 MB**，平均 CPU **0.02%**。不同设备、库与使用负载可能不同。
