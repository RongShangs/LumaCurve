# HyperLux 静态官网

将本目录的**全部内容**放到 `lc.rongshangs.top` 站点根目录，或直接部署工作目录 `D:/IOS/web/`。`index.html`、`assets/`、`downloads/` 必须同级；只上传网页会导致下载失败。

无需构建工具或服务端脚本；资源与下载使用相对路径，本地打开也能查看页面。首页展示正式版 **2.0.2** 和更新日志，2.0.2 新增名单在线同步、日志自动滚动开关、完整分析包和可调暗光稳定，分支摘要归入曲线卡片。介绍当前 Root + LSPosed APP 的曲线、手动锚点记忆、系统双参考场景控制、温控及确认时间，以及新版本的亮度分支、可选暗光稳定与有界分析追踪；不沿用 1.x 模块机制和占用数字。

页面底部依次是交流群、打赏、感谢名单、作者。点击复制仅复制群号 `314981836`，暗号 `1691` 单独展示。微信与支付宝点击展示收款码。

下载文件：

- `downloads/HyperLux-2.0.2.apk`：安装 APP，在 LSPosed 启用系统框架作用域。
- `downloads/LumaCurve-2.0.2-source.zip`：对应 GPL-3.0 源码。
- `downloads/LumaCurve-2.0.2.zip`：APK、源码和恢复工具合集，**不是 KSU 刷入模块**。
- `downloads/SHA256SUMS.txt`：上述三个文件的校验值。

重建及同步：

```powershell
python tools/build_refactor_hook_test.py --skip-device-fixtures
python tools/sync_website.py
python tools/package_website.py
```

暗光稳定默认关闭，需在设置中开启并保存；不承诺消除所有持续输入波动或 HDR 引起的亮度变化。

同步脚本从 APP 清单读取版本和版本代码，从 `docs/releases/2.0.2.md` 读取更新说明，复制最新包、收款码和许可，更新 `update.json` / `update.js`，并复制完整站点到 `D:/IOS/web/`。`dist/LumaCurve-website-2.0.2.zip` 可直接解压到站点根目录。网站版本信息使用 `distribution=apk`；APP 自动更新查询原 GitHub 仓库的正式 APK Release，不再使用旧 KSU 模块更新字段。

部署后检查 `/downloads/HyperLux-2.0.2.apk`、对应源码、完整包、校验文件和 `/update.json` 返回 200，并确认 APK 校验值一致。历史 1.x 文件可能仍保留用于旧链接，首页只引导安装当前 APP。

## 只维护一份感谢名单

网站根目录的 `thanks.json` 是官网与 APP 的共同数据源。新增或修改 `entries` 中的 `name`（昵称）与 `message`（留言）即可，无需重新发布 APK。名单为纯文本，每人一项，不填留言可用 `[未填写]`。请保留 `schema: 1`；最多 200 项，昵称 80 字符、留言 200 字符以内，不使用换行。

线上网页每次打开读取 JSON，APP 进入关于页联网刷新并缓存，失败时保留旧名单。网站本地 `file://` 预览使用 `thanks.js`，这个文件由同步工具从 JSON 生成；本地改名单后运行 `python tools/sync_website.py` 更新预览。后续只在线上补名单时不必更新 JS 或 APK。

部署时请将 `thanks.json` 与 `thanks.js` 一并上传，并让 `/thanks.json` 可公开访问。二维码下方已加入备注提醒。
