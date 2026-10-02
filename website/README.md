# HyperLux 静态官网

将本目录的**全部内容**放到 `lc.rongshangs.top` 站点根目录，或直接部署工作目录 `D:/IOS/web/`。`index.html`、`assets/`、`downloads/` 必须同级；只上传网页会导致下载失败。

无需构建工具或服务端脚本；资源与下载使用相对路径，本地打开也能查看页面。首页展示正式版 **2.0.1** 和更新日志，介绍当前 Root + LSPosed APP 的曲线、手动锚点记忆、系统双参考场景控制、温控及确认时间，以及新版本的亮度分支、可选暗光稳定与有界分析追踪；不沿用 1.x 模块机制和占用数字。

页面底部依次是交流群、打赏、感谢名单、作者。点击复制仅复制群号 `314981836`，暗号 `1691` 单独展示。微信与支付宝点击展示收款码。

下载文件：

- `downloads/HyperLux-2.0.1.apk`：安装 APP，在 LSPosed 启用系统框架作用域。
- `downloads/LumaCurve-2.0.1-source.zip`：对应 GPL-3.0 源码。
- `downloads/LumaCurve-2.0.1.zip`：APK、源码和恢复工具合集，**不是 KSU 刷入模块**。
- `downloads/SHA256SUMS.txt`：上述三个文件的校验值。

重建及同步：

```powershell
python tools/build_refactor_hook_test.py --skip-device-fixtures
python tools/sync_website.py
python tools/package_website.py
```

暗光稳定默认关闭，需在设置中开启并保存；不承诺消除所有持续输入波动或 HDR 引起的亮度变化。

同步脚本从 APP 清单读取版本和版本代码，从 `docs/releases/2.0.1.md` 读取更新说明，复制最新包、收款码和许可，更新 `update.json` / `update.js`，并复制完整站点到 `D:/IOS/web/`。`dist/LumaCurve-website-2.0.1.zip` 可直接解压到站点根目录。网站版本信息使用 `distribution=apk`；APP 自动更新查询原 GitHub 仓库的正式 APK Release，不再使用旧 KSU 模块更新字段。

部署后检查 `/downloads/HyperLux-2.0.1.apk`、对应源码、完整包、校验文件和 `/update.json` 返回 200，并确认 APK 校验值一致。历史 1.x 文件可能仍保留用于旧链接，首页只引导安装当前 APP。
