# 当前 APP 开发与构建

先读 [详细项目文档](../../PROJECT.md)、[任务边界](../../PROJECT_SCOPE.md) 与 [当前交接](../../AGENT_HANDOFF.md)。唯一当前产品路线是 experimental/refactor_hook/，不使用旧 highlevel C/KSU 的生产说明。

## 目录

| 路径 | 责任 |
| --- | --- |
| experimental/refactor_hook/java/ | APP、Hook、曲线、场景、记忆、输出与 Root 控制 |
| experimental/refactor_hook/native/、assets/ | 主屏原生控制与随包资产 |
| tests/refactor_*.py | 当前模型、真实生产代码和接口回归 |
| tools/build_refactor_hook_test.py | 编译、完整回归、签名与源码/恢复归档 |
| website/ | 官网源文件、感谢名单和发布元数据 |
| build/ | 私有签名、固件输入、测试输出与回执 |
| dist/ | 按版本保留的交付物；不混入源码提交 |
| output/ | 本地宣传生成物及提示词 |

## 工具链与运行

新增功能或兼容先读 [架构维护入口](../architecture/README.md) 与 [兼容开发规范](../architecture/compatibility-policy.md)。路线、负责人、证据、清理及测试登记在统一 manifest；索引由工具生成，源码目录与功能自检覆盖由检查守卫。不要在页面、Root 命令和引擎分别新增一套兼容判断。

维护环境使用 JDK 17、Android SDK 37 / Build Tools 37.0.0、NDK 28.2.13676358、Xposed API 82；路径在构建工具中明确指定。宿主原生模型测试使用 GCC。Python 依赖与固定下载文件会校验摘要。

在仓库根目录：

```powershell
python tools/verify_compatibility.py
python tests/refactor_compatibility_contract.py
python tools/build_refactor_hook_test.py --verify-only
```

上面的只验证入口编译并运行回归，不需要发行私钥、不替换已交付 APK / 源码 / 网站包。确需生成新的交付物时使用完整构建：

```powershell
python tools/build_refactor_hook_test.py --skip-device-fixtures
```

有完整私有固件夹具时省略参数；缺夹具的构建不代表固件检查通过。静态检查、宿主服务替身和 Android 真机验证分开记录。完整构建会更新 dist 与共享 build 元数据，整理文档时不要顺手重建已发布版本。

当前脚本是维护者发行验证流程，正式构建要求既有签名，并核对原证书；缺少私钥会主动拒绝，不会自动替换证书。Fork 自行发行需明确配置自己的签名和预期证书，不能覆盖维护者签名的 APK。签名私钥不随源码分发。

## 网站与交付

发布准备齐全且用户授权后，核对版本身份、APK、完整源码、恢复合集、签名及校验和，再运行：

```powershell
python tools/sync_website.py
python tools/package_website.py
```

生成的工作区 web/ 是镜像，website/ 才是源文件。同步与打包不会自行上传；GitHub、LSPosed 和官网发布另行执行并核对实际下载内容。感谢名单只维护 website/thanks.json，遵循 [发布约定](../release-policy.md)。

## 文档与私有输入

当前指南在 docs/guides，版本日志在 docs/releases，版本与专项审阅在 docs/reviews；旧阶段文档在 docs/archive。修改文档路径同时检查 Markdown 链接及打包工具的引用。

设备 ZIP 统一看工作区 diagnostics/index.json，按包内模块版本分类。已展开输入通过兼容入口保留旧 build 路径，避免破坏既有固件测试。凭据、固件、日志、反汇编和签名不得加入源码归档。
