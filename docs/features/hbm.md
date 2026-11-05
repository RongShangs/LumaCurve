# HBM 控制器与计时：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### HBM触发与预算 · `hbm`

| 项目 | 要求 |
| --- | --- |
| 触发 | 户外会话生效且用户启用HBM调整，真实原生数据、照度与预算结构可调。 |
| 需求与行为 | 控制器ABI与计时ABI分开；合法零计时仅调整照度，有限预算受原时间窗口限制；不制造HBM数据；数据身份接受 config.* 十字段或 DisplayDeviceConfig 内嵌七字段，逐项校验 |
| 退出 / 失败 | 停用时仅在字段仍指向本次克隆时恢复原对象，不覆盖系统新配置。 |
| 默认 | `native` · 不额外修改 |
| 配置键 | `outdoor_hbm_tuning`、`outdoor_hbm_lux_factor`、`outdoor_hbm_budget_factor` |
| 依赖能力 | `outdoor` |

## 分叉与收束

### 选择边界

控制器与计时为两个独立维度：启用字段优先；缺字段且完整旧结构才用原生能力方法。合法有限计时 / 三零计时互斥；不能制造预算。 HBM 数据身份优先 config.* 十字段；仅整体缺失才接受 DisplayDeviceConfig 内嵌七字段类，字段与构造必须逐项匹配。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `two_dimensions` |
| 统一输出 | 控制器身份/支持与预算数据；启用ABI和计时ABI独立组合 |
| 坐标与身份 | lux、毫秒；原生零计时不补造时长 |

### 清理及回滚

克隆 HBM 数据，不改共享配置；只在字段仍指向本次 clone 时恢复原对象，系统替换优先。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `enabled_field` · 控制器：原生启用字段 | 读取 mHbmControllerIsEnabled；存在此字段时始终优先，不回退绕过错误。 |
| `native_support_method` · 控制器：原生能力方法 | 没有启用字段且旧结构完整；调用 deviceSupportsHbm 判断实际支持，继续保留照度、时间与省电条件。 |
| `timed_budget` · HBM 照度＋时间预算 | 时间窗口、最大时长与最短时长均通过预算校验；复制本机数据后调整。 |
| `ambient_only` · HBM 仅照度门槛（原生零计时） | 三个时间字段全部为零且接口完整；只调整照度，保留三个零计时值。 |

路线内部 ABI：`outdoor_hbm_data_presence`、`hbm_data_identity`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`outdoor.hbm_controller_profile`、`outdoor.hbm_tuning_profile`。

验证入口：

- [refactor_outdoor.py](../../tests/refactor_outdoor.py)
- [refactor_os3_compatibility.py](../../tests/refactor_os3_compatibility.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [HbmAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HbmAccess.java) | `1b60fcde7b4ab2b7` |
| [OutdoorController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorController.java) | `7c041c22158401f2` |
