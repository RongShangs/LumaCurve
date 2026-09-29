# 完整核心联调：候选 03（占用验证）

本候选沿用 02 的小幅限幅与手动/自动恢复实验，增加稳定目标提交去重。用于比较 Java broker 的调用数量和进程占用，不用于日常安装。

手机上完整解压 `LumaCurve-framework-core-test03.zip`，在解压目录用 root 终端执行：

```sh
su
sh ./test_framework_core_android.sh
```

约 2～3 分钟，无需重启或安装模块。先让自动亮度稳定约 30 秒，切手动约 10 秒并小幅改动滑块，然后回自动，静置至结束。不要同时做灭屏、AOD 或高照度实验；这些场景本轮没有验收。

与 02 一样，每次接管只允许实际框架亮度附近 ±0.01；未开放完整曲线。脚本使用配置副本，关闭实验学习/HBM 节点写入，不覆盖原配置或安装核心；结束清除临时亮度并恢复原模块运行状态。请回传整个 `luma-framework-core-test03-时间戳-PID` 输出目录，包括 `framework.events` 和 `process-samples.txt`。

验收重点：两段自动接管期实际背光不发生无故回落；手动模式后 `RELEASE_CONFIRMED`；末尾 `temporary=verified_clear`；稳定段 `OUTPUT_COUNTS` 中跳过重复提交，同时 CPU/RSS 实测。显示曲线仍为旧名义比例，完整亮度坐标迁移未完成。
