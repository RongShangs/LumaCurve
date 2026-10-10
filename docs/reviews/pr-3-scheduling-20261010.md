# PR #3 调度审阅

2026-10-10。审阅 [PR #3](https://github.com/RongShangs/LumaCurve/pull/3)，固定 head `e98f99a02ee8528f786ddbdf7c124a38adaebc71`、base `bf839360ba1c6d2b47ae2a7fe3f3c500e7ade1c8`，共 19 个文件、2 个提交。对照本地 3.0.0 r1/r2，未合并或移植该 PR；只读获取引用，测试在忽略的 build/pr3-review 内隔离运行，未操作手机。

## 有意义的部分

| 内容 | 对当前代码的价值 | 建议 |
| --- | --- | --- |
| 同时核对交互状态、屏幕状态、Doze/ScreenOff 输出策略 | 当前暗光和手动接管已有交互检查；额外识别明确的 AOD 输出策略有边界价值，现有低光/场景路径也可受益 | 独立移植策略判断，核对唤醒次序；避免在频繁调用的 screenOn getter 内再访问电源服务 |
| 屏幕状态变化后 queuePublish | 让状态更快反映监听释放、亮屏接入，改动小 | 可保留本地异步/合并发布机制后加入 |
| 采样/健康检查诊断字段 | 有助于区分模块计时器与系统光感事件，便于验证耗电 | 必须按真实监听与待执行任务报告，区分事件驱动和 on-change 上报类型 |
| 缺少历史 MSYS2 时显式跳过主机 C 测试并记账 | 改善贡献者构建便利性 | 可作为独立工具改进；本地完整验证不需要跳过，也不证明运行稳定性 |

已有的 30 秒 on-change / 10 秒连续型健康检查、按光感事件判断、息屏释放监听，并非这个 PR 新增的优化。本地 3.0.0 已进一步合并同批光感事件、排队息屏清理、排队 OEM 重算、隔离遥测写入和恢复记忆时序，直接覆盖旧文件会丢失这些改进。

## 不宜原样采用的问题

### [P2] 高频观察窗口可被普通显示变化反复续期

`onDisplayChanged(0)` → `displayChanged()` → `screenOn()` 每次都会调用 `sampling.wake(now)`；没有确认实际从息屏转亮屏，也没有检查输出策略是否发生改变。`wakeUntil` 总被设成当前时间加 2 秒。

隔离运行 PR 的实际控制器：每 500 ms 发送一次显示变化通知，持续 10 秒，即可让 `wake_transition` 一直存在，最后仍剩 2 秒。这证明窗口不是从一次真实唤醒起算的有界观察。是否在某台真机频繁触发这些通知尚未测量，不能把模型结果表述为实际耗电幅度。

应记录真实亮屏/策略转换边沿，去重并设置硬截止；当前正排查解锁卡死，不能未经验证就默认增加这一阶段的系统服务读取。

位置：[wake 定义](https://github.com/shisjsji/LumaCurve/blob/e98f99a02ee8528f786ddbdf7c124a38adaebc71/experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java#L21)、[显示变化入口](https://github.com/shisjsji/LumaCurve/blob/e98f99a02ee8528f786ddbdf7c124a38adaebc71/experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java#L49)、[亮屏续期](https://github.com/shisjsji/LumaCurve/blob/e98f99a02ee8528f786ddbdf7c124a38adaebc71/experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java#L53)。

### [P2] 健康检查状态不反映任务是否存在

`sampling_phase` 和 `sampling_health_ms` 只按屏幕/交互与 grace 时间生成。暗光锁定关闭、监听已注销、timer 已移除时，仍会显示 `stable_on / 30000`。该场景已用实际 PR 控制器在服务替身中复现。

`sampling_event_driven` 实际使用是否 on-change 判断，连续型传感器同样经 SensorEventListener 回调，并不等于模块用轮询读取传感器。建议分别记录任务启用状态、下次期限和 reporting mode，暂停时明确写 inactive/disabled。

位置：[status](https://github.com/shisjsji/LumaCurve/blob/e98f99a02ee8528f786ddbdf7c124a38adaebc71/experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java#L190)。

### 接入热路径需要重做验证

PR 将 `PowerManager.isInteractive()` 加到共享 `HookRuntime.screenOn()`，当前本地该方法还用于低光计算、户外与场景事实读取。多数控制器调用已经另查交互状态，直接照搬会增加重复调用。它不是已证实的死锁，但与减少 OEM 计算调用内部服务访问的方向不一致，应将复合生命周期判断限制在合适的调度入口，或使用同显示线程维护的状态。

PR 新增测试主要是 SamplingPolicy 六项数字断言；HookRuntime 在原控制器测试中用 `screenOn(){return on;}` 替身，未执行新增 Doze 策略判断。K90 同版本固件能找到目标 getter，但仍不能据此称 AOD 接入/退出时序已完成真机验证。

位置：[screenOn](https://github.com/shisjsji/LumaCurve/blob/e98f99a02ee8528f786ddbdf7c124a38adaebc71/experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java#L241)。

## 排除的内容

- 2.3.2 / 23107 的版本、发布文档、网站元数据和预编译 APK/ZIP，不适用于当前未发布的 3.0.0。
- README 中改为从贡献者 fork 下载 APK 的链接不能进入维护者安装说明。
- “只依赖系统已经注册的光感事件”的表述不精确：`listen()` 仍会额外调用两次 registerListener，参数还是 1000000 us。PR 没有减少注册次数或修改传感器请求速率，也没有功耗 A/B 证据。
- 不把该 PR 当成 K90PM/K80PM 卡死根因证明或已验证修复。

## 实际验证

- 原 PR 亮度控制测试：183 项通过，在隔离目录执行。
- 增补调度检查：26 项通过，复现窗口反复续期和无计时器却报告检查状态的情况。
- 未执行其发布脚本、未使用其预编译安装包、未提交远端评论或合并操作；本地 3.0.0 亮度控制保持原实现。

结论：有可吸收的边界判断和可观测性改进；250 ms 高频观察应修正并验证后再考虑，不能整包合并。
