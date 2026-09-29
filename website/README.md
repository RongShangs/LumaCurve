# 静态官网

无需构建或服务器脚本，将本目录内容放入站点根目录即可。域名为 `lc.rongshangs.top`。`index.html` 可直接本地打开，资源和下载均为相对路径，没有外部字体或框架。

本目录已经包含 `downloads`：模块安装包、对应 GPL 源码包及 SHA-256 校验文件，可以直接整体上传。仓库中也保留这些下载文件。

重新构建版本时，先打包模块与源码，再执行 `python tools/sync_website.py` 更新本目录的资源与下载，同时复制一份完整网站到 `D:/IOS/web`。源码 ZIP 排除 `website/downloads`，避免将自己递归打包；生产源码和官网代码仍完整包含。

下载文件必须和页面版本一致。更新版本时同步修改页面版本及两条下载链接。曲线演示使用与 WebUI 相同的数学模型，只展示基础曲线，页面没有设备访问权限。

部署直接使用本 `website` 目录的内容，也可以使用 `D:/IOS/web` 或执行 `python tools/package_website.py` 生成完整部署 ZIP。放入 `lc.rongshangs.top` 实际站点根目录后，确保 `index.html`、`assets`、`downloads` 同级。

部署后直接访问 `/downloads/luma_curve-1.0.0.zip`、`/downloads/LumaCurve-1.0.0-source.zip` 和 `/downloads/SHA256SUMS.txt`，都应返回 200。页面能打开但这三个路径返回 404 时，应检查 `downloads` 是否上传到该站点的正确根目录，以及 Web 服务器的静态文件路径配置。
