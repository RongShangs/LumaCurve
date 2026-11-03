# 暂停自动亮度名单：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 暂停自动亮度名单 · `apps`

| 项目 | 要求 |
| --- | --- |
| 触发 | 当前默认屏上实际可见任务与用户名单匹配；缺多窗口支持时才按焦点判断。 |
| 需求与行为 | 主屏可见任务优先，含小窗分屏；仅没有多窗口接口才回退焦点；全部名单不可见才恢复；尊重用户主动开启自动亮度 |
| 退出 / 失败 | 所有匹配的可见应用消失才恢复本模块暂停的自动亮度；用户主动开启优先。 |
| 默认 | `off` · 名单为空 |
| 配置键 | `manual_brightness_apps` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

完整可见任务接口优先；原生焦点事件为必要前提，不支持多窗口时明确焦点回退；禁止把运行进程当可见任务。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 可见名单应用/焦点回退的统一匹配结果与能力证据 |
| 坐标与身份 | 同用户/同屏可见性；小窗不等同焦点 |

### 清理及回滚

按代次丢弃过期查询；停用 / 熄屏解除订阅，惰性线程空闲退出，名单设置保留。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `visible_tasks` · 可见任务路径（含小窗 / 分屏） | 可见任务接口完整；任一名单应用仍可见时保持暂停。 |
| `focus_fallback` · 前台焦点回退路径 | 没有完整的可见任务能力时按系统焦点判断，并提示小窗限制。 |

路线内部 ABI：`sensor_identity`、`sensor_freshness`、`foreground_user_field`、`task_visibility_field`、`manual_node_hold_policy`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`brightness_control.app_multiwindow_supported`。

验证入口：

- [refactor_brightness_control.py](../../tests/refactor_brightness_control.py)
- [refactor_foreground_firmware.py](../../tests/refactor_foreground_firmware.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [ApplicationBrightnessSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ApplicationBrightnessSettings.java) | `dd1fdadd688c106d` |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `8e91d605934d21fc` |
| [ForegroundApps.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ForegroundApps.java) | `06525008edad4f5c` |
