# 完整核心联调：候选 04（稳定阶段轮询）

本候选沿用 03 的小幅限幅和稳定目标去重，只改变稳定阶段的检查频率并增加真实内存 PSS 采样；用于比较接管响应、实际背光及资源占用。**它不是正式模块安装包。**

完整解压 `LumaCurve-framework-core-test04.zip`，手机 root 终端在解压目录运行：

```sh
su
sh ./test_framework_core_android.sh
```

约 2～3 分钟，不需要安装或重启。系统自动亮度开启且屏幕保持开启；先静置约 30 秒，切手动约 10 秒并小幅滑动，再回自动静置。不要叠加灭屏/AOD、窗口临时亮度或高照度测试，本轮没有验收这些场景。

测试配置放在私有副本，正式核心、配置和学习文件不被覆盖。输出仍在每次接管的框架浮点值 ±0.01 内，不能用于完整曲线体验。结束后确认临时请求清除，并恢复原模块的运行/暂停状态。

请回传整个 `luma-framework-core-test04-时间戳-PID` 输出目录。重点检查 `framework.events` 的周期/提交计数、`process-samples.txt` 的 CPU/RSS/PSS、`framework.trace` 的节点单调性、手动释放确认及 `recovery.txt` 的 `temporary=verified_clear`。若响应明显迟滞，也请描述实际体感和发生阶段。
