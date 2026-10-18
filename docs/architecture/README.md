# 架构与兼容维护入口

本目录是后续功能与适配开发的长期规范，不按某个临时版本另起一套。当前产品为 4.0.0 / 40000；审阅修复与欢迎教程已合入本版，原未发布包私有备份，本轮按用户授权发布。

| 阅读目的 | 依据 |
| --- | --- |
| 需求、最高要求和发布边界 | [根项目文档](../../PROJECT.md) |
| 哪个模块负责什么、亮度怎样流转 | [架构与模块边界](architecture.md) |
| 新增 / 修改兼容必须如何做 | [兼容开发规范](compatibility-policy.md) |
| 现有哪些路线、由谁选择、怎样退出和验证 | [完整兼容索引](compatibility-index.md) |
| 新适配的设计记录 | [适配记录模板](compatibility-change-template.md) |
| 本轮发现、修复和验证边界 | [完整审阅记录](../reviews/architecture-review-20261018.md) |

维护信息分工明确：

1. `CompatibilityCatalog.java`：APP 功能目录、稳定路线 ID、名称及简要条件。纯声明，不做设备探测。
2. `compatibility-manifest.json`：每组路线的实现负责人、选择规则、组合关系、证据、清理与验证入口；另登记路线内部的 ABI 变体。
3. `source-map.json`：每份生产 Java 的职责归属。逻辑分层先于搬动历史目录，不因整理破坏构建和旧适配。
4. `compatibility-baseline.json`：本轮前全部功能 / 路线的保留基线。新增路线不得删除旧路线或重写基线来逃过检查。
5. `compatibility-index.md`：由上述代码和数据生成，不能手工维护第三套路线表。

日常执行：

```text
python tools/verify_compatibility.py --write-doc
python tools/verify_compatibility.py
python tests/refactor_compatibility_contract.py
python tools/build_refactor_hook_test.py --verify-only
```

前三项为可在 CI 执行的轻量维护检查；最后一项编译 APP 与运行完整回归，不替换 APK / 源码 / 网站交付物。完整固件静态验证需要私有输入；公开源码可使用 `--skip-device-fixtures`，结果必须注明缺少哪些固件核验，不能写成真机通过。
