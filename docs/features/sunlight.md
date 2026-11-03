# 手动阳光屏：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 手动阳光模式条件 · `sunlight_override`

| 项目 | 要求 |
| --- | --- |
| 触发 | 开启现有阳光模式条件覆盖，原生手动阳光策略判断进入或退出。 |
| 需求与行为 | 配置现有手动阳光模式照度条件，放在户外设置；区别于自动户外增强 |
| 退出 / 失败 | 关闭条件覆盖后返回原生手动阳光屏参数，不影响自动户外增强选择。 |
| 默认 | `native` · 跟随系统 |
| 配置键 | `sunlight_override`、`sunlight_enter`、`sunlight_exit` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

完整 SunlightController 及已验证时间语义，仅手动模式阳光屏；不与自动 HBM 混用。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 已核验原生阳光模式字段与独立能力状态 |
| 坐标与身份 | 参数保持本机定义，不以机型补默认阈值 |

### 清理及回滚

返回原生期限；不写传感器数据。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `manual_sunlight` · 系统手动模式阳光屏 | 调整 SunlightController 的进入 / 退出等待，不代替自动 HBM。 |

运行证据：`sunlight_override_supported`。

验证入口：

- [refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)
- [refactor_scene_hooks.py](../../tests/refactor_scene_hooks.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AdvancedTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AdvancedTuning.java) | `99787f6a9676e36d` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `4639a1d9ec1322dc` |
