# 触摸遮挡释放：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 触摸遮挡释放 · `touch_override`

| 项目 | 要求 |
| --- | --- |
| 触发 | 开启释放等待覆盖，原生触摸遮挡策略判断离开保护区。 |
| 需求与行为 | 仅调整原生触摸遮挡释放等待；离开作用域按字段所有权恢复 |
| 退出 / 失败 | 每次调用或异常后恢复仍持有的临时触摸字段，系统后续更新优先。 |
| 默认 | `native` · 跟随系统 |
| 配置键 | `touch_override`、`touch_release_seconds` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

核验触摸保护消费者及 debounce 字段；保留普通资格和遮挡保护。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 本次字段所有权与可逆覆盖 |
| 坐标与身份 | 触摸/遮挡原生语义，失败不强行采样 |

### 清理及回滚

每次调用结束或异常后由FieldOverride逆序恢复临时字段；系统在调用期间更新的字段不回写旧值。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `touch_release` · 系统触摸遮挡释放等待 | 调整 TouchCoverProtectionHelper 的移开等待，继续保留遮挡保护。 |

运行证据：`touch_override_supported`。

验证入口：

- [refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)
- [refactor_review_safety.py](../../tests/refactor_review_safety.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AdvancedTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AdvancedTuning.java) | `99787f6a9676e36d` |
| [FieldOverride.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FieldOverride.java) | `6b47448ffaf6ac29` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `818d117cff24ad7d` |
