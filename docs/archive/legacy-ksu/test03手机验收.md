# 给手机 root Agent：test03 无重启验收

先把 `LumaCurve-hot-test03.zip` 解压到 `/sdcard/Download/`。
包内有新模块 ZIP、热替换脚本、说明；不是正式发布。

## 准备

1. 备份当前运行配置和学习文件，保留旧日志。
2. 暂停引擎，在 WebUI 把极端曲线恢复为默认曲线并保存。
   实机现有 64.55%～100% 曲线会在修复后真正执行，先用温和曲线验收。
   若直接修改配置，先备份，设置 `curve_custom=0`，保留其余选项。
3. 使用原控制脚本恢复旧引擎；热替换脚本自己会暂停、探测和重启。
   若有意让引擎保持暂停，脚本会保留暂停状态，成功后需主动 resume。

## 热替换

```sh
su
sh /sdcard/Download/hot_test_android.sh /sdcard/Download/luma_curve-1.0.0.zip
```

核对实际运行文件：

```sh
/data/adb/modules/luma_curve/system/bin/luma_curve_daemon --build-info
```

应为 `20260929-test03`，启动日志及详细读数的 core_build 也应一致。
脚本只换核心，不替换 WebUI、配置或学习记录。启动失败会尝试恢复旧核心。
下次手机重启可能被管理器中未应用的旧安装覆盖，保留本包重新确认构建标识。

## 持续控制

保持自动亮度、亮屏，分别在稳定照度和改变环境照度时观察 1～2 分钟：

- `brightness_owner=daemon`，允许写入；目标变化后成功计数递增。
- `actuator_write_stage=readback_ok`，`actuator_write_errno=0`，last_write_result=0。
- target_br、current_br 和真实 sysfs 逐渐收敛。到达目标后不要求每秒写入。
- `/proc/PID/fd` 中能看到 panel0-backlight/brightness 的持有句柄，节点仍为 0444。
  同时记录 PID、FD 的 flags 和主屏读回值。

请区分“写命令成功”“读回一致”和“未被系统持续覆盖”；若读回冲突，提供
readback_mismatch/streak、external_write 及 errno 日志，不先更换写入通道。

## 生命周期

分别测试自动→手动→自动、熄屏→亮屏、暂停→恢复：

- 释放时节点变为 0644，主屏写句柄关闭，系统调节不受旧 FD 干扰。
- 自动恢复会经过已有唤醒观察窗，之后重新持有 FD 并设 0444。
- 不泄漏额外主屏 FD；亮度仍能收敛，暂停标志与服务状态一致。

不要用注入权限或故意高亮测试破坏设备状态；写失败降级已通过主机故障注入测试。
出现 `write_failed_passthrough` 时保持现场，返回错误阶段/errno，排查后重启引擎。

## 返回内容

发送：构建标识、安装探测原始输出、两段相隔 10 秒的详细读数、同时间真实主屏
brightness/max_brightness、daemon FD 清单，以及上述三种切换前后的 mode/FD/状态。
请明确 test03 是否在本机持续写入成功，不以“进程在运行”代替验收。
