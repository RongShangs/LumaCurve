# HyperLux 静态官网

将本目录的全部内容放到 `lc.rongshangs.top` 站点根目录。`index.html`、`assets/`、`downloads/`、`thanks.json` 与 `update.json` 同级；只上传网页会导致下载失败。

当前版本 **4.4.5 / 44500**，构建 **release-4.4.5**。兼容 HyperOS，需 Root 与 LSPosed。覆盖安装后重启一次加载新版 Hook，已有设置保留。

扩展小米12S Pro部分亮度接口兼容，完善场景、记忆与动画检测；修复动画控制权和户外退出确认。按实际能力逐项适配，保留已有路线与设置。

## 下载与安装

- `downloads/HyperLux-4.4.5.apk`：APP 安装包。
- `downloads/LumaCurve-4.4.5-source.zip`：对应 GPL-3.0 源码。
- `downloads/LumaCurve-4.4.5.zip`：APK、源码与恢复工具合集，不是 KSU 模块。
- `downloads/SHA256SUMS.txt`：上述三个文件的校验值。

暗光锁定、夜间范围、普通暗光稳定和户外增强默认关闭，保留已有显式设置。“暗处保持”全天按照度判断；“日落后锁定”仅在系统提供的日落至日出区间启用，仍需满足照度与持续时间，缺时间时等待。本版已完成主机回归与既有固件静态检查，未完成本版长期实机验证。

## 重建与同步

在仓库根目录运行：

```powershell
python tools/build_refactor_hook_test.py
python tools/sync_website.py
python tools/package_website.py
```

没有私有固件资料时，构建命令可加 `--skip-device-fixtures`；此时不代表固件静态核对通过。同步脚本读取 APP 清单和 `docs/releases/4.4.5.md`，复制对应 APK、源码、恢复合集及资源，生成更新元数据，再同步完整站点到 `D:/IOS/web/`。`dist/LumaCurve-website-4.4.5.zip` 解压后即站点根目录。

网站版本字段使用 `distribution=apk`。APP 查询 GitHub 最新正式 APK Release，不使用旧 KSU 模块元数据。上传后检查首页、`/update.json`、上述下载链接和校验文件，确认版本及 APK SHA-256 一致。本地同步和打包不代表服务器已更新。

## 感谢名单

网站根目录 `thanks.json` 为官网与 APP 共用数据源，新增或修改 `entries` 中的 `name` 与 `message` 即可；不需要重新发布 APK。纯文本，每人一项，`schema: 1`，最多 200 项，昵称最多 80 字符、留言最多 200 字符，不使用换行。

线上网页和 APP 读取 JSON，失败时保留缓存或随包名单。本地 `file://` 预览使用 `thanks.js`，由同步工具从 JSON 生成；部署时两者一起上传。同步前合并线上、仓库及部署副本差异，避免覆盖新增项。日常新增不重打APK；每次打包发布时必须同步最新随包离线名单，并核验APK、源码与网站JSON一致。

页面保留 QQ 群、打赏、感谢名单与作者。复制按钮只复制群号 314981836，暗号 1691 单独展示。
