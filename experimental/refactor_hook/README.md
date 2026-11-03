# HyperLux APP与框架源码

这是当前生产主线，目录名experimental/refactor_hook是历史保留。它包含APP、legacy API82 Hook及受管主屏原生程序；API102与旧KSU/C研究不自动合入。

## 使用与开发入口

- 安装APK后按LSPosed模块说明启用系统框架；控制中心映射还需实际SystemUI注入。新增Hook通常需要重启相应进程或设备，未提供热重载承诺。
- 默认沿用本机基线；额外控制由用户明确选择。节点数值不能当作物理nit，无法换算时采用明确单位的实际读数。
- 完整源码包根目录的GLOBAL_RULES.md是长期规则，PROJECT.md是需求与设计入口，docs/features/是逐项需求/路线/契约/实现/测试。
- 当前状态只看docs/work-status.md；本版变化看docs/releases/对应版本，不在源码说明不断追加旧版本进展。

## 本地检查

在完整仓库根目录运行：

```text
python tools/verify_compatibility.py --write-doc
python tools/verify_compatibility.py
python tests/refactor_compatibility_contract.py
python tools/build_refactor_hook_test.py --verify-only
```

普通构建生成独立APK、源码包和完整包，不覆盖旧交付。源码内的诊断工具不代表本轮已经做了真机安装、强光测试或功耗测量。许可与来源见仓库LICENSE及docs/来源与许可证.md。
