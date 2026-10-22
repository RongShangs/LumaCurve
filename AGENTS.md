# HyperLux 工作约定

先读根目录 PROJECT.md、PROJECT_SCOPE.md 与 docs/architecture/README.md；当前主线为 experimental/refactor_hook，旧 KSU/C 及 API 102 研究不自动合入。

最高要求：新增兼容保留旧路线；覆盖升级保留用户全部已保存设置。兼容判断基于实际接口 / 对象；不得用型号或系统大版本放行功能，不得因检测失败回写默认。

所有功能或适配修改遵守 docs/architecture/compatibility-policy.md。新增路线 / ABI 变体需在对应适配器集中实现，更新 CompatibilityCatalog.java、docs/architecture/compatibility-manifest.json，新增 Java 登记 source-map.json；不删除 compatibility-baseline.json 的旧路线。修改后生成兼容索引，不在 UI、Root 桥和运行协调器各添一套兼容判断。

检查入口：

```text
python tools/verify_compatibility.py --write-doc
python tools/verify_compatibility.py
python tests/refactor_compatibility_contract.py
python tools/build_refactor_hook_test.py --verify-only
```

按变更跑对应旧 / 新 / 负向测试，再跑完整检查；区分主机、私有固件静态核验、真机与功耗结果。私有输入不进公开产物。

默认保留已有 APK / ZIP，先完成本地代码与验证；只有用户要求才重新打包，发布必须得到用户授权并同时同步官网与 GitHub / LSPosed。不要提交服务器认证、签名材料、固件或用户分析包。既有未提交改动不得重置。

Beta 日志以其上一个已公开正式版为基线累计，每个 Beta 独立文件，文件内按各 Beta 分段；不只写前一 Beta 到本版的差异。具体规则见 docs/release-policy.md。
