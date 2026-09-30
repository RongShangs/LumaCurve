# 静态官网

无需构建或服务器脚本，将本目录内容放入站点根目录即可。域名为 `lc.rongshangs.top`。`index.html` 可直接本地打开，资源和下载均为相对路径，没有外部字体或框架。

本目录包含 `downloads`：正式模块安装包、对应 GPL-3.0 源码包及 SHA-256 校验文件，也包含 `update.json` 与 `update.js`。部署时必须整体上传，版本检查和下载才能指向同一批文件。

重新构建版本时，先运行 `python tools/build_framework_probe.py`、`python tools/build_framework_module.py --release` 和 `python tools/package_source.py`，再运行 `python tools/sync_website.py`。它会刷新资源、下载与更新元数据，同时复制完整网站到 `D:/IOS/web`。源码 ZIP 排除 `website/downloads`，避免递归打包。

下载文件必须和页面版本一致。更新版本时同步修改页面版本及两条下载链接。曲线演示使用与 WebUI 相同的数学模型，只展示基础曲线，页面没有设备访问权限。

部署直接使用本 `website` 目录的内容，也可以使用 `D:/IOS/web` 或执行 `python tools/package_website.py` 生成完整部署 ZIP。放入 `lc.rongshangs.top` 实际站点根目录后，确保 `index.html`、`assets`、`downloads` 同级。

部署后直接访问 `/downloads/luma_curve-1.0.0.zip`、`/downloads/LumaCurve-1.0.0-source.zip`、`/downloads/SHA256SUMS.txt`、`/update.json` 和 `/update.js`，都应返回 200。页面能打开但下载或更新失败时，先检查这些文件是否同在站点根目录，且 ZIP 与元数据版本一致。
