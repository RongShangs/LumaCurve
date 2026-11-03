# 兼容开发流程

本文规定怎样落地和验证兼容变更。必须长期遵守的底线只维护于 [GLOBAL_RULES.md](../../GLOBAL_RULES.md)；单位及适配器边界见 [收束契约](compatibility-convergence.md)；每项功能的实际条件见 [功能目录](../features/README.md)。

## 1. 确定差异和归属

先核对实际对象、字段类型、方法签名与消费者，再判断差异属于新路线、已有路线的ABI变体，还是暂未就绪。型号、系统版本和ROM作者只记录为样本背景。

| 变更 | 维护位置 |
| --- | --- |
| 功能默认、触发、行为、退出及依赖 | `docs/requirements/feature-requirements.json` |
| 字段、签名、旧结构与数据换算 | 对应适配器；复杂布局可拆Access/Profile |
| 稳定功能/路线ID与简要条件 | `CompatibilityCatalog.java` |
| 选择顺序、组合、边界、契约、清理、证据和测试 | `compatibility-manifest.json` |
| 触发、持续与退出状态机 | 已有控制器；数值规则放Policy/Options |
| 页面和诊断解释 | 消费收束结果，不自行探测或选择ABI |
| 新生产文件职责 | `source-map.json` |

使用 [适配记录模板](compatibility-change-template.md)登记新旧差别。涉及线程、用户、主屏、控制权和原始载荷时写清归属；私有固件只提取必要接口事实进入公开记录。

## 2. 实现安装与清理

- 安装前校验必需反射成员；多Hook使用`HookTransaction`，句柄加入事务，成功后发布能力并`commit()`。子事务需随父组撤销时使用`commitTo(parentHandles)`。
- 回滚逆序执行；单个撤销或日志异常不能中断其余清理。未成功撤销的句柄保留再试能力，不先标记已完成。
- 临时字段优先`FieldOverride`，持续修改记录原对象和应用值；ThreadLocal按调用成对恢复，均覆盖异常路径。
- 安装及缓存以实际Class/加载器或实例为键，不能只按类名字符串合并不同对象。

扩展既有安装器时保留其ABI校验、优先顺序、能力发布时间及失败回归。不要仅为目录统一而重写成熟控制逻辑。

## 3. 接入状态与配置

分别提供接口支持、路线采用、用户启用和当前生效证据。`pending`为待就绪，`inactive`为原机未启用，`unsupported`为已确认接口不完整；缺少采用证据保持`unconfirmed`。自检完成时间固定，页面查看只读，主动重检另记来源。

按对应功能文档验证草稿、持久化原文、运行裁剪副本和实际确认结果。Root身份、目录、设置桥、节点与LSPosed注入各自检查，不从某一项通过推断其余通过。完整语义集中于[Root工具](../features/root_tools.md)、[配置工具](../features/interface.md)及各功能契约。

## 4. 最小回归矩阵

| 情况 | 核验重点 |
| --- | --- |
| 原ABI、新ABI、两者同时存在 | 旧路线保留，新路线独立，优先顺序确定，无双重控制 |
| 部分成员缺失、类型/静态性错误、原方法抛错 | 拒绝残缺结构；待就绪、未知和不支持分别报告 |
| 安装中途失败、可选观察失败 | 必需组无半套残留，可选项不拖停已提交核心 |
| 多实例、同名不同加载器、晚到回调 | 状态、缓存和控制权不串代 |
| 熄屏、解锁、换屏/用户、停用 | 正确清理，只恢复模块仍持有的值 |
| 覆盖升级、能力消失后恢复 | 设置、名单、曲线、保持范围和原存储含义保留 |

分别记录主机模型、私有固件静态核对、Android真机、长测与功耗证据。设备结果不能由接口匹配或主机回归推断。

## 5. 生成、审阅与交付

更新实现、需求和清单 → 生成文档 → 审阅差异 → 契约与对应新旧/负向回归 → 完整构建 → 保存验证回执。

```text
python tools/verify_compatibility.py --write-doc
python tools/verify_compatibility.py
python tests/refactor_compatibility_contract.py
python tools/build_refactor_hook_test.py --verify-only
```

生成检查用于发现结构、注册和源码追溯漂移，语义及原生保护顺序仍须人工审阅。本地Beta、不可覆盖交付、更新日志和正式发布按[发布约定](../release-policy.md)执行，不在此处再维护一套流程。
