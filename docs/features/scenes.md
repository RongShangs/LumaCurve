# 原生场景参与：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 夜间驾驶 · `scene_night_driving`

| 项目 | 要求 |
| --- | --- |
| 触发 | 系统事实成立、原生策略可用，且用户选择跟随或所设准入条件允许。 |
| 需求与行为 | 对已有的夜间驾驶场景选择跟随、阻止或自定义准入；进入、效果和退出按实际能力展开，不创建新命名场景。 |
| 退出 / 失败 | 按照对应原生退出事实及已保存的确认条件退让，换对象只恢复自己的字段。 |
| 原生默认条件 | 系统驾驶传感器识别为驾驶，且当前时间在系统日落之后或日出之前，设备允许夜间驾驶亮度策略。退出：驾驶识别结束或进入白天。识别标签不证明正在开车。 |
| 原生效果 | 抬高夜间亮度下限，延长确认；使用主光感时暂停辅助输入。 |
| 默认 | `native` · 跟随系统，允许对现有场景配置，不新增用户命名场景。 |
| 配置键 | `scene_options` |
| 依赖能力 | 由本项实际接口独立判断 |

### 运动延迟 · `scene_step`

| 项目 | 要求 |
| --- | --- |
| 触发 | 系统事实成立、原生策略可用，且用户选择跟随或所设准入条件允许。 |
| 需求与行为 | 对已有的运动延迟场景选择跟随、阻止或自定义准入；进入、效果和退出按实际能力展开，不创建新命名场景。 |
| 退出 / 失败 | 按照对应原生退出事实及已保存的确认条件退让，换对象只恢复自己的字段。 |
| 原生默认条件 | 设备启用步行检测且收到步行事件；每次事件续期，超过系统有效时间退出。仅增加降亮确认时间，不改变基础曲线。 |
| 原生效果 | 延后降亮，减少步行时光线波动的影响。 |
| 默认 | `native` · 跟随系统，允许对现有场景配置，不新增用户命名场景。 |
| 配置键 | `scene_options` |
| 依赖能力 | 由本项实际接口独立判断 |

### 触摸遮挡 · `scene_touch`

| 项目 | 要求 |
| --- | --- |
| 触发 | 系统事实成立、原生策略可用，且用户选择跟随或所设准入条件允许。 |
| 需求与行为 | 对已有的触摸遮挡场景选择跟随、阻止或自定义准入；进入、效果和退出按实际能力展开，不创建新命名场景。 |
| 退出 / 失败 | 按照对应原生退出事实及已保存的确认条件退让，换对象只恢复自己的字段。 |
| 原生默认条件 | 手指进入系统定义的光感保护区域，或离开后尚未超过触摸释放等待。保护区域由机型、旋转和折叠姿态决定。 |
| 原生效果 | 忽略手指遮挡造成的照度变化。 |
| 默认 | `native` · 跟随系统，允许对现有场景配置，不新增用户命名场景。 |
| 配置键 | `scene_options` |
| 依赖能力 | 由本项实际接口独立判断 |

### 普通距离遮挡 · `scene_proximity`

| 项目 | 要求 |
| --- | --- |
| 触发 | 系统事实成立、原生策略可用，且用户选择跟随或所设准入条件允许。 |
| 需求与行为 | 对已有的普通距离遮挡场景选择跟随、阻止或自定义准入；进入、效果和退出按实际能力展开，不创建新命名场景。 |
| 退出 / 失败 | 按照对应原生退出事实及已保存的确认条件退让，换对象只恢复自己的字段。 |
| 原生默认条件 | 普通距离采样开启，距离读数非负且小于本机距离阈值。离开阈值后解除；本机可能没有启用此策略。 |
| 原生效果 | 遮挡时暂停照度更新，避免误降亮。 |
| 默认 | `native` · 跟随系统，允许对现有场景配置，不新增用户命名场景。 |
| 配置键 | `scene_options` |
| 依赖能力 | 由本项实际接口独立判断 |

### 流式距离遮挡 · `scene_stream_proximity`

| 项目 | 要求 |
| --- | --- |
| 触发 | 系统事实成立、原生策略可用，且用户选择跟随或所设准入条件允许。 |
| 需求与行为 | 对已有的流式距离遮挡场景选择跟随、阻止或自定义准入；要求瞬时遮挡标记与跳过亮度变化入口完整，新版场景接口只保留 updateAmbientLux(int,float,boolean) 而缺少这些入口时本项保持不可用，不伪造准入或传感器行为，进入、效果和退出按实际能力展开。 |
| 退出 / 失败 | 按照对应原生退出事实及已保存的确认条件退让，换对象只恢复自己的字段。 |
| 原生默认条件 | 系统游戏策略允许流式距离检测，发生待确认的变暗事件后读取流式传感器。系统按原始通道与两级阈值判定遮挡；无遮挡才继续降亮。位置以本机传感器为准。新版场景接口只保留 updateAmbientLux 而缺少瞬时遮挡与跳过亮度变化入口时，本项保持不可用。 |
| 原生效果 | 变暗前确认是否被遮挡，确认期间暂缓降亮。 |
| 默认 | `native` · 跟随系统，允许对现有场景配置，不新增用户命名场景。 |
| 配置键 | `scene_options` |
| 依赖能力 | 由本项实际接口独立判断 |

### 反射与 AON 确认 · `scene_reflection`

| 项目 | 要求 |
| --- | --- |
| 触发 | 系统事实成立、原生策略可用，且用户选择跟随或所设准入条件允许。 |
| 需求与行为 | 对已有的反射与 AON 确认场景选择跟随、阻止或自定义准入；旧版按 isReflectiveScene 反射判定，新版整体缺少旧标记时映射到已核验的 AON 照度窗口（updateAmbientLux / checkAonFlareStatus），阻止只让窗口不可命中并在调用后恢复；两者都缺失时不开放，不创建新命名场景或额外传感器采样。 |
| 退出 / 失败 | 按照对应原生退出事实及已保存的确认条件退让，换对象只恢复自己的字段。 |
| 原生默认条件 | 主显示发生变暗候选，照度落在本机 AON 区间；旧版由反射判定、新版由 AON 窗口在 updateAmbientLux 内抑制降亮，均为系统原生确认，不额外采样传感器。 |
| 原生效果 | 等待反射确认，避免短时遮挡造成降亮。 |
| 默认 | `native` · 跟随系统，允许对现有场景配置，不新增用户命名场景。 |
| 配置键 | `scene_options` |
| 依赖能力 | 由本项实际接口独立判断 |

### 夜间唤醒 · `scene_night_wake`

| 项目 | 要求 |
| --- | --- |
| 触发 | 系统事实成立、原生策略可用，且用户选择跟随或所设准入条件允许。 |
| 需求与行为 | 对已有的夜间唤醒场景选择跟随、阻止或自定义准入；进入、效果和退出按实际能力展开，不创建新命名场景。 |
| 退出 / 失败 | 按照对应原生退出事实及已保存的确认条件退让，换对象只恢复自己的字段。 |
| 原生默认条件 | 上游夜间唤醒策略传入两个状态位；亮屏时使用夜间唤醒目标。解除由上游策略通知，具体识别时间段未在该亮度消费者中定义。 |
| 原生效果 | 唤醒时临时使用系统夜间亮度目标。 |
| 默认 | `native` · 跟随系统，允许对现有场景配置，不新增用户命名场景。 |
| 配置键 | `scene_options` |
| 依赖能力 | 由本项实际接口独立判断 |

### 手动模式阳光屏 · `scene_manual_sunlight`

| 项目 | 要求 |
| --- | --- |
| 触发 | 系统事实成立、原生策略可用，且用户选择跟随或所设准入条件允许。 |
| 需求与行为 | 对已有的手动模式阳光屏场景选择跟随、阻止或自定义准入；进入、效果和退出按实际能力展开，不创建新命名场景。 |
| 退出 / 失败 | 按照对应原生退出事实及已保存的确认条件退让，换对象只恢复自己的字段。 |
| 原生默认条件 | 系统自动亮度关闭、阳光屏开关开启、屏幕亮起、未被用户临时停用且未挂起；照度持续超过系统门槛后进入。与自动亮度 HBM 是两条不同链路。 |
| 原生效果 | 手动亮度下临时增亮，提高阳光可读性。 |
| 默认 | `native` · 跟随系统，允许对现有场景配置，不新增用户命名场景。 |
| 配置键 | `scene_options` |
| 依赖能力 | 由本项实际接口独立判断 |

### 游戏防误降亮 · `scene_game`

| 项目 | 要求 |
| --- | --- |
| 触发 | 系统事实成立、原生策略可用，且用户选择跟随或所设准入条件允许。 |
| 需求与行为 | 对已有的游戏防误降亮场景选择跟随、阻止或自定义准入；进入、效果和退出按实际能力展开，不创建新命名场景。 |
| 退出 / 失败 | 按照对应原生退出事实及已保存的确认条件退让，换对象只恢复自己的字段。 |
| 原生默认条件 | 应用在系统游戏名单内，且本机采用游戏触摸防误降亮分支；入场短期或最近触摸期间抑制降亮。设备的距离/触摸策略决定是否走此分支。 |
| 原生效果 | 游戏入场或触摸期间暂缓降亮。 |
| 默认 | `native` · 跟随系统，允许对现有场景配置，不新增用户命名场景。 |
| 配置键 | `scene_options` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

各场景独立实际消费者；普通 / 流式距离分别检测。新版 SceneDetector 缺少瞬时遮挡与跳过亮度变化入口时流式距离保持不可用（不伪造）；反射确认在旧 isReflectiveScene 缺失时映射到已核验的 AON 照度窗口。OEM 未启用普通距离时报告 inactive，不强开传感器；只能约束现有场景。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `per_feature` |
| 统一输出 | SceneCatalog状态、独立进入/效果/退出配置及应用计数 |
| 坐标与身份 | 照度lux、时间毫秒；未知与未生效分开 |

### 清理及回滚

调用结束或异常时由FieldOverride逆序恢复临时字段；停止时取消本次确认并恢复仍持有的步行/夜间设置，系统后来更新优先。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `oem_night_driving` · 系统夜间驾驶策略 | 匹配本机该场景的原生消费者，仅限制参与条件，不强制进入场景。 |
| `oem_step` · 系统步行延迟策略 | 使用 ABC 原生步行额外降亮确认接口。 |
| `oem_touch` · 系统触摸遮挡策略 | 使用 TouchCoverProtectionHelper 的实时遮挡判断。 |
| `oem_proximity` · 普通距离策略 | 匹配 dropAmbientLuxIfNeeded 和普通距离开关；系统未开启时不强制启动。流式距离是另一项独立策略，不混用设置。 |
| `oem_stream_proximity` · 流式距离策略 | 匹配 SceneDetector 的瞬时遮挡标记与跳过亮度变化接口；新版只有 updateAmbientLux(int,float,boolean) 而缺少这些入口时本项不可用，不伪造准入或传感器行为；与普通距离分别设置。 |
| `oem_reflection` · 系统反射 / AON 策略 | 旧版使用 isReflectiveScene 反射判定；新版整体缺少旧标记时映射到已核验的 AON 照度窗口（updateAmbientLux / checkAonFlareStatus），阻止时只让窗口不可命中；两者都缺失时不开放。 |
| `oem_night_wake` · 系统夜间唤醒策略 | 使用 ABC 的 updateNightWakeMode，不伪造夜间唤醒条件。 |
| `oem_manual_sunlight` · 系统手动阳光屏策略 | 使用 SunlightController 原生状态与条件。 |
| `oem_game` · 系统游戏防误降亮策略 | 匹配本机该场景的原生消费者，仅限制参与条件，不强制进入场景。 |

路线内部 ABI：`scene_reflection_mapping`、`scene_stream_proximity_absent`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`system_scene`、`scene_options`、`feature_self_check`。

验证入口：

- [refactor_scenes.py](../../tests/refactor_scenes.py)
- [refactor_scene_hooks.py](../../tests/refactor_scene_hooks.py)
- [refactor_scene_settings.py](../../tests/refactor_scene_settings.py)
- [refactor_scene_firmware.py](../../tests/refactor_scene_firmware.py)
- [refactor_review_safety.py](../../tests/refactor_review_safety.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [FieldOverride.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FieldOverride.java) | `6b47448ffaf6ac29` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `818d117cff24ad7d` |
| [SceneCatalog.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SceneCatalog.java) | `3f1dc5f8e950556e` |
| [SceneController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SceneController.java) | `2f40296a53139af9` |
| [SceneSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SceneSettings.java) | `cc5e5a66ffd6f854` |
| [SceneTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SceneTuning.java) | `6d7bb5b7a90a2e37` |
