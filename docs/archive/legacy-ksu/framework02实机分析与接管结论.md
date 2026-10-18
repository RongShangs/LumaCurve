# HyperOS 4 本机框架分析与下一步

输入 LumaCurve-probe02-collection-output.zip，采集时间 2026-09-29 21:18。
设备 pandora / 25098PN5AC，OS4.0.0.41.XBLCNXM，Android 17 / API 37。
四个系统 JAR 均与手机记录的 SHA-256 一致。原始文件及反汇编仅存于 build/device-reports，
不放入公开源码、模块或 Git。下面结论只适用于已核对的这份固件。

## 本机实际控制接口

`DisplayManagerGlobal.setTemporaryBrightnessMode(displayId, mode)` 经 Binder 到
DisplayManagerService.setTemporaryBrightnessModeInternal，再到 DPC.overrideBrightnessMode。
服务仅接受 0、1：0 对应 setUseAutoBrightness(false)，1 对应 true。
DPC 向自身 Handler 投递任务，最多等待 3500ms；恢复自动时还可能等待光感有效。
返回 true 是 CountDownLatch 完成，不表示物理亮度稳定，也没有自动回收的 Binder 租约。
因此暂不将它当作生产版长期接管入口；停止进程不保证自动恢复。

## 更小的输出通道验证

DisplayBrightnessStrategySelector.selectStrategy 的顺序（略去其他分支）是：
屏幕关闭/Doze → follower/boost → 窗口 override → 临时亮度 → 自动亮度 → fallback。
本机 TemporaryBrightnessStrategy.setTemporaryScreenBrightness 原样保存 float；
传入 NaN 后 isValidBrightnessValue 判无效，临时策略不再被选择。
该请求仍通过 DPC，不直接写 sysfs，也不要求使用失败的 SettingsProvider 入口。

先用临时策略覆盖小幅目标，测试主屏是否保持、能否均匀跟随输入，并核对清除恢复。
此次测试不调用 setTemporaryBrightnessMode，不写持久亮度设置。
自动算法可能仍在后台工作；临时请求还可能被系统事件清除，窗口 override 优先级也更高。
所以这是可行性实验，不是完成系统自动亮度替代的证明。

## 实际渐变路径

DPC.mScreenBrightnessRampAnimator 为 RampAnimator.DualRampAnimator，初始化和 animateScreenBrightness
确实调用它。单独发现 MiuiRampAnimator 类并不证明主屏使用那个整数渐变器。
本机 RampAnimator 有 mTargetHlgValue、convertGammaToLinear、可配置 ramp gamma、
基于单调时钟的逐帧推进，并与小米 DisplayPowerControllerStub 的动画、亮度/刷新率策略交互。
它已经包含感知域和厂商定制；不能再叠加旧 daemon 动画后宣称是线性均匀输出。
具体单位映射及各分支是否启用，还需受控采样与显示配置校准。

## 光感

DualSensorPolicy 包含主/辅助各自的 fast/slow ambient lux、各自阈值与过渡时间。
updateDualSensorPolicy 按主/辅助事件分支更新，存在光感选择与场景检测路径。
它不是简单把前后两值求平均。此轮已确认实现存在并读取相关方法；尚未逐条还原所有
厂商场景分支，也不能据此认定背光突然回落是某个光感分支导致。

## 单位与上限

21:18 的 BrightnessInfo：brightness=adjustedBrightness=0.09104382，minimum=0.000366256，
maximum=highBrightnessTransitionPoint=0.37486267，highBrightnessMode=0，窗口 override=false。
0.37486267 是这次正常模式读到的框架上限，不是永久常量。
框架 float、滑块/感知百分比、nit、硬件 0..16383 必须分开；禁止用硬件满量程
直接解释框架 100% 或视觉 100%。已有自定义曲线若沿用硬件百分比，需要版本化迁移，
不能在现有配置上静默重解释。HBM/热限制仍留给系统执行，并在 UI 展示实际限制。

## 当前交付范围

framework03 是独立实验包：目标相对起始框架值 ±0.01，受实时 min/max 限制；
逐段保持及输入域小幅升降，10Hz 采样。输入域等步长不等于光学或感知亮度均匀。
成功条件需结合 trace、临时策略读回、主屏节点和用户观察，不能以 API 返回成功代替。
本地验证不代表 Android 实机权限、恢复进程或稳定性已通过。
核心仍为 freezer01/test03，不推送 GitHub，不更新正式模块下载。
