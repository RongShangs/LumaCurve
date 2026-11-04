# 熄屏前降亮与息屏显示协调：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 熄屏前降亮协调 · `dim_aod`

| 项目 | 要求 |
| --- | --- |
| 触发 | 当前处于已验证的自动DIM降亮阶段，曲线重算可能顶回系统降亮；旧路线额外核验全屏AOD探测，新路线以 mAppliedDimming 代替且不声称已验证全屏AOD。 |
| 需求与行为 | 仅在已验证自动DIM阶段协调，避免曲线反复顶回系统降亮；旧路线使用五参SDR入口与息屏子阶段，新版四参入口以 mAppliedDimming 代替被删第五参，只把原生结果中高于降亮输入的抬高夹回并保留原生降低保护；缺少全屏AOD探测属推断语义，高风险，需真机行为验证；不更改熄屏策略 |
| 退出 / 失败 | 回普通显示后恢复原生链路，不改屏幕休眠策略。 |
| 默认 | `observe` · 保留系统休眠与AOD |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

实际完整SDR入口、主屏DPC/电源请求/原因及降亮状态同时匹配；旧版要求 DIM 子阶段与全屏AOD策略，新版四参入口以 mAppliedDimming 代替被删的第五参、且全库不存在 adjustBrightnessWhenDimming 时才走新路线；不按机型或版本放行。两路线都只在当前自动DIM调用及活动用户和显示线程内参与，缺接口或异常独立跳过，不回退改写其他阶段。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `additive` |
| 统一输出 | 已核验自动DIM与全屏AOD目标协调条件 |
| 坐标与身份 | 自动DIM与真息屏/背屏/HDR保持不同语义 |

### 清理及回滚

只在当前同步调用中限制AOD子阶段抬高DIM输入，原生后续保护完整执行；外层调用无论正常或异常都释放嵌套上下文，安装失败撤销本组全部钩子。不改用户设置、不留节点或模式接管。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `automatic_dim_aod_cap` · 自动降亮与息屏目标协调 | 主屏仍亮且处于自动DIM降亮时，阻止全屏AOD目标重新抬高已降低的亮度；真正息屏、背屏、手动及HDR沿用系统。 |
| `automatic_dim_aod_cap_modern` · 自动降亮与息屏目标协调（新式SDR入口） | 新式四参SDR入口删除息屏子阶段后，用 mAppliedDimming 作为降亮状态；只把原生结果中高于降亮输入的抬高夹回，原生降低保护仍执行。缺少全屏AOD探测，语义为推断，高风险。 |

路线内部 ABI：`dim_aod_signature`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`dim_aod_compatibility`、`feature_self_check`。

验证入口：

- [refactor_dim_aod.py](../../tests/refactor_dim_aod.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [DimAodCompatibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DimAodCompatibility.java) | `44e0340bec79ede8` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `818d117cff24ad7d` |
