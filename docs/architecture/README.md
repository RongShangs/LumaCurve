# 架构与兼容维护入口

本目录只记录长期工程设计，不追加发布状态。全局规则见 [GLOBAL_RULES.md](../../GLOBAL_RULES.md)，当前任务见 [工作状态](../work-status.md)。

| 问题 | 入口 |
| --- | --- |
| 系统与模块怎样分工 | [架构与模块边界](architecture.md) |
| 每个功能有哪些需求与路线 | [按功能目录](../features/README.md) |
| 新增兼容如何核验、回滚和交付 | [兼容开发规范](compatibility-policy.md) |
| 差异在哪里分叉、怎样收束 | [适配器契约](compatibility-convergence.md) |
| 全部路线概览、ABI变体及职责 | [生成索引](compatibility-index.md) |
| 记录新适配 | [适配模板](compatibility-change-template.md) |
| 审阅与验证证据 | [复审记录](../reviews/) |

维护源分工：`CompatibilityCatalog.java`声明稳定功能/路线；`compatibility-manifest.json`记录选择、组合、证据、清理、测试与契约；`source-map.json`登记源码职责；`compatibility-baseline.json`保护旧路线；`docs/requirements/feature-requirements.json`维护功能需求。它们共同生成索引和功能页面，不能再手写一套选路表。

```text
python tools/verify_compatibility.py --write-doc
python tools/verify_compatibility.py
python tests/refactor_compatibility_contract.py
python tools/build_refactor_hook_test.py --verify-only
```

无私有固件环境可显式使用`--skip-device-fixtures`，必须记录跳过范围。静态和主机通过不能写成真机通过。
