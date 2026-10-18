# 给手机 root Agent：HyperOS 4 框架接口探测

这是独立探测包，不是模块更新。已安装 freezer01 保留。先执行只读阶段，接口
验证后再做小幅控制试验；不注入 system_server，不调用未知厂商接口，不写 sysfs。

## 1. 只读探测（先做这个）

解压 LumaCurve-framework-probe01.zip，用当前目录的相对路径：

```sh
su
sh ./probe_framework_android.sh inspect
```

通常 10～60 秒，连同系统 dump 最多约 90 秒。输出 luma-framework-probe。
回传整个目录，尤其 framework.txt / environment.txt / before-display.txt。
不必现在再提供旧日志。若 root Agent 能执行，交给它执行并回传即可。

核对：

- 实际固件必须为本机 HyperOS 4；不得根据 AOSP 原型直接宣布 Hook 可用。
- DISPLAY0 的 uniqueId 与主屏对应，不能误用背屏。
- 获取的 BRIGHTNESS_INFO min/max、BRIGHTNESS_SETTING、SYSTEM_MODE 是否有效。
- DEX_CLASS 与 METHOD 中哪些是小米/MIUI 扩展，哪个候选类实际存在。
- 隐藏接口或 Settings 读取失败时保留异常；不要使用 Binder transaction 编号猜测。

脚本会把独立探测进程放入 cgroup2 根组，仅迁移探测进程自身，避免再次继承
应用 freezer；不改变调用终端/管理器或其他应用。没有改系统 freezer 策略。

## 2. 受控通道试验（只读接口确认后）

若 BRIGHTNESS_SETTING 为有限 float 且处于本机 min/max 内、SYSTEM_MODE 为 0/1，
再执行下面命令。若不满足，回传只读包，先适配，不用反复测试。

```sh
su
sh ./probe_framework_android.sh control
```

暂时关闭系统自动决策不等于最终架构要求用户长期关自动亮度，这是隔离控制权的实验。

过程：保存原 mode 和 float 请求，暂停 LumaCurve，切系统手动模式；依次测试原值、
原值+0.02、原值-0.02、原值（均限于 min/max），每段 3 秒；恢复原请求与模式，
再恢复模块原有暂停/运行状态。0.02 是系统归一化值，不是视觉亮度的 2%。

只操作 logical display 0，没有调节背屏或主动开启 HBM。不触动学习与配置。
控制过程中保持屏幕亮着，不拖动滑块、开关自动亮度、锁屏或切换 HDR 内容。
大约 15 秒加前后 dump。不要强制杀死探测进程；普通错误会执行恢复，强制终止
或设备断电不能保证 Java finally 执行，需手机 Agent 根据 SAVED 记录核对恢复。

应出现 `RESTORE mode=ok brightness_request=sent`。若未确认恢复，脚本会保持模块
暂停，明确报告，不自动重启以掩盖问题。该标记证明请求已发出及 mode 读回一致，
不是光学输出已经回到原值的证明；需结合 after-display 与用户观察。

输出 SAMPLE 列依次为：单调时间纳秒、阶段、请求目标、系统保存的亮度值、
BrightnessInfo.brightness、主屏 sysfs brightness。不要把后两者当光学测量值。

`result=control_calls_completed` 仅说明调用流程完成。需要判断各段节点是否能
稳定保持、是否被系统覆盖、系统限制是否生效；不能据此宣布完成接管或线性渐变。

## 保持与恢复

inspect 不暂停引擎，不改亮度或系统 mode；control 会短暂暂停并按上述记录恢复。
没有 services.jar 替换、SELinux 改动或 framework Hook。用户原本暂停时仍保持暂停。
探测 DEX 在 /data/local/tmp 临时目录执行，结束清理；输出在当前目录保留。
若中途人工终止，先查看 framework.txt 的 SAVED 与 RESTORE，然后手机 Agent
恢复原 mode/请求，并确认模块原来是否运行；不使用重装覆盖用户配置。

本地验证：控制逻辑 10 项（边界、模式读回、失败恢复等）、脚本暂停/恢复 7 项通过，DEX 已构建；
Android / HyperOS 实机隐藏接口签名和权限尚待本次探测。
