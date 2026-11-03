# HyperLux 工作入口

开始前阅读根目录 [GLOBAL_RULES.md](GLOBAL_RULES.md)、[PROJECT.md](PROJECT.md)、[PROJECT_SCOPE.md](PROJECT_SCOPE.md)，再看 [当前状态](docs/work-status.md)及 [架构入口](docs/architecture/README.md)。这些稳定入口不追加版本日志。

最高要求是新增兼容保留旧路线、覆盖升级保留全部已保存设置。详细全局约束以GLOBAL_RULES.md为准。

每项需求编辑 `docs/requirements/feature-requirements.json`；选路、ABI和收束编辑适配器及兼容清单；新增生产文件登记source-map。按功能文档和索引只生成，不手改，不把长期要求写进版本日志后遗忘。

```text
python tools/verify_compatibility.py --write-doc
python tools/verify_compatibility.py
python tests/refactor_compatibility_contract.py
python tools/build_refactor_hook_test.py --verify-only
```

实际功能修改按长期授权递增本地Beta，保留全部旧交付；发布和设备操作按当前明确授权。既有独立研究和未提交改动不得重置。私有输入、认证和签名不进入公开源码或产物。
