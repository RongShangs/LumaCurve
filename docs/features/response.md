# 主辅确认与暗光策略：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 环境变化确认 · `response`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户开启响应预设/覆盖，系统在当前输入及场景中确认候选变亮或变暗。 |
| 需求与行为 | 分别调整变亮、变暗确认时间，保留原生场景和过渡动画 |
| 退出 / 失败 | 停用或对象变化时恢复仍属于本次修改的确认期限，不重置系统光感缓冲。 |
| 默认 | `native` · 跟随系统 |
| 配置键 | `response_override`、`brighten_delay`、`darken_delay` |
| 依赖能力 | 由本项实际接口独立判断 |

### 微小变亮确认 · `small_response`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户单独开启微小变亮覆盖，原生识别到受支持的小幅变亮候选。 |
| 需求与行为 | 小幅变亮单独确认，不替代普通变化确认 |
| 退出 / 失败 | 离开小幅变亮条件时回普通确认路径；停用恢复本次期限修改。 |
| 默认 | `native` · 跟随系统 |
| 配置键 | `small_brighten_override`、`small_brighten_delay` |
| 依赖能力 | 由本项实际接口独立判断 |

### 暗光稳定 · `low_light`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户启用暗光稳定，当前照度处于所设暗光范围。 |
| 需求与行为 | 暗处减少小幅波动，提供暗处保持等预设；单光感也可独立工作 |
| 退出 / 失败 | 离开暗光条件后回普通策略；停用恢复本次阈值和确认参数。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `low_light_stability`、`low_light_limit`、`low_light_brighten`、`low_light_darken` |
| 依赖能力 | 由本项实际接口独立判断 |

### 暗光阈值 · `low_threshold`

| 项目 | 要求 |
| --- | --- |
| 触发 | 启用暗光稳定和暗光阈值覆盖，实际变化处于该稳定作用域。 |
| 需求与行为 | 在暗光稳定条件内按比例和绝对门槛决定确认，不改变其他区间 |
| 退出 / 失败 | 离开暗光稳定区间后不影响其他区间；停用撤回本次门槛覆盖。 |
| 默认 | `native` · 跟随系统 |
| 配置键 | `low_light_threshold`、`low_light_brighten_ratio`、`low_light_brighten_floor`、`low_light_darken_ratio`、`low_light_darken_floor` |
| 依赖能力 | `low_light` |

### 光感变化门槛 · `threshold_override`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户开启高级阈值覆盖，系统请求普通光感变化门槛。 |
| 需求与行为 | 只通过已验证原生阈值接口调整，不捏造输入或场景 |
| 退出 / 失败 | 停用恢复已核验且仍属于本次修改的原生阈值，输入和缓冲不重置。 |
| 默认 | `native` · 跟随系统 |
| 配置键 | `threshold_override`、`threshold_brighten`、`threshold_darken`、`threshold_small`、`threshold_min_brighten`、`threshold_min_darken`、`threshold_limit` |
| 依赖能力 | 由本项实际接口独立判断 |

### 辅助光感暗光闸门 · `assist_gate`

| 项目 | 要求 |
| --- | --- |
| 触发 | 开启暗光稳定闸门，主辅输入和新鲜度证据齐全，实际变化需要辅助确认。 |
| 需求与行为 | 用新鲜辅助输入确认暗光变化；单源设备仅暂停此项，保留原设置 |
| 退出 / 失败 | 辅助样本不新鲜或源被替换时释放本次确认，独立保留主光感处理。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `low_light_assist_gate`、`low_light_assist_wait`、`low_light_assist_tolerance` |
| 依赖能力 | `low_light`、`assist_sensor` |

### 辅助确认窗口 · `assist_override`

| 项目 | 要求 |
| --- | --- |
| 触发 | 开启辅助确认覆盖，原生辅助输入在当前场景有效。 |
| 需求与行为 | 调整原生辅助确认时间，不增加监听；缺辅助仅暂停对应运行副本 |
| 退出 / 失败 | 停用或辅助对象变化时撤回本次确认窗口，不注销OEM拥有的监听。 |
| 默认 | `native` · 跟随系统 |
| 配置键 | `assist_override`、`assist_brighten`、`assist_darken`、`assist_small` |
| 依赖能力 | `assist_sensor` |

## 分叉与收束

### 选择边界

完整驾驶接口优先；没有任何驾驶残片且完整旧场景 ABI 才允许无驾驶路径；残缺 / 类型错误 / 调用失败不降级。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | supported、应用/恢复操作及调整计数 |
| 坐标与身份 | 时间毫秒、照度lux；保留主辅输入身份 |

### 清理及回滚

只改变已验证期限 / 阈值；停用释放闸门、恢复本次缓存及动画参数，不重置 OEM 环形缓冲。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `native_driving` · 原生确认与夜间驾驶保护 | 使用已验证的主辅光感确认和阈值；保留原生夜间驾驶判断及附加等待。 |
| `native_without_driving` · 传统确认与场景保护 | 完整旧场景结构没有夜间驾驶接口；使用原生确认窗口，保留步行、遮挡、HDR 等判断，不额外采样。 |

运行证据：`response_scene_profile`。

验证入口：

- [refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)
- [refactor_os3_compatibility.py](../../tests/refactor_os3_compatibility.py)
- [refactor_advanced_firmware.py](../../tests/refactor_advanced_firmware.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AdvancedTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AdvancedTuning.java) | `99787f6a9676e36d` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `4e3f51d5e9508d0c` |
| [LowLightAssistEvidence.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LowLightAssistEvidence.java) | `89a41dbcd6733592` |
| [LowLightTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LowLightTuning.java) | `66826507c957e3ce` |
| [ResponseTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ResponseTuning.java) | `f7dd9b35ce71c151` |
