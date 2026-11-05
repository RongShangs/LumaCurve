# 熄屏前降亮与息屏显示协调：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 熄屏前降亮协调 · `dim_aod`

| 项目 | 要求 |
| --- | --- |
| 触发 | 仅完整五参SDR、已验证自动DIM子阶段和双全屏AOD探测匹配时启用旧路线；四参入口语义待验证，不安装修正。 |
| 需求与行为 | 保留已验证DIM/AOD子阶段协调与下游保护；缺少完整AOD消费者证据的四参候选只保留研究，不改原生输出，不增加默认接管或新配置键。 |
| 退出 / 失败 | 回普通显示后恢复原生链路，不改屏幕休眠策略。 |
| 默认 | `observe` · 保留系统休眠与AOD |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

完整旧五参SDR、DIM子阶段与双全屏AOD探测、主屏/活动用户/显示线程均验证后才安装修正；四参候选语义待验证，保留代码但不安装、不更改原生结果，独立报告不支持。

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
| `automatic_dim_aod_cap_modern` · 自动降亮与息屏目标协调（新式SDR入口） | 四参SDR入口缺少已验证的全屏AOD消费者语义；候选路线保留但不安装修正，所有亮度结果沿用系统。 |

路线内部 ABI：`dim_aod_signature`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`dim_aod_compatibility`、`feature_self_check`。

验证入口：

- [refactor_dim_aod.py](../../tests/refactor_dim_aod.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [DimAodCompatibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DimAodCompatibility.java) | `73712f01d444d717` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `f1cdc6bfa6a1d706` |
