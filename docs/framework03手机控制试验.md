# 给手机 root Agent：系统临时亮度通道测试

这是独立探测包，保留现有模块，无需安装模块或重启。
仅适用于已采集的本机 HyperOS 4；脚本核对 framework/services 哈希与主屏物理 ID。

解压 LumaCurve-framework-probe03.zip，在解压目录执行：

```sh
su
sh ./probe_framework03_android.sh
```

保持系统自动亮度开启，屏幕亮着。测试期间不调滑块、不切自动亮度、不锁屏，
留在普通页面，避免视频/HDR和指定窗口亮度的应用。核心保持现有安装版本即可。

过程：核对主屏没有现存临时请求 → 暂停模块 → 在原框架亮度附近 ±0.01
缓慢升降并保持 → 清除临时请求 → 读回确认 → 恢复模块原有运行/暂停状态。
控制约 21 秒，加上采样、哈希和 dump 通常约 30～60 秒。不会拉到满亮度。
±0.01 是框架输入值，不是视觉 1%；本次用于测稳定性，不是最终渐变效果。

没有 Settings 写入、模式修改、sysfs 写入、SELinux 修改、system_server 注入。
只有目标为 logical display 0 的 setTemporaryBrightness 调用。窗口 override 优先级仍更高。

应输出 `RESTORE temporary=verified_clear`。模块原本运行时还应有
`RESTORE module=resume_command_completed`，后者只证明恢复命令成功，需要核对模块运行。
独立 root 恢复进程有 45 秒控制期限，超时尝试终止该探测并清除请求。
如果恢复读回不符，会保持模块暂停并保留运行目录，明确报告；请回传结果处理。
设备断电、恢复进程自身失败等无法靠脚本保证恢复。

将整个 `luma-framework03-时间戳-PID` 文件夹压缩回传。
尤其 trace.txt（目标/框架/节点采样）、recovery.txt、released-main.txt、before-display.txt。
请同时描述是否出现“慢慢变亮后突然回落”；原核心 WebUI 在暂停期间不会反映这个实验轨迹。

SAMPLE 列：单调时间纳秒、阶段、请求值、BrightnessInfo.brightness、adjustedBrightness、
主屏 sysfs 读数。节点读数不是光学亮度，物理变化需结合用户观察。
当前尚未完成生产级框架接管，本包不发布到 GitHub。
