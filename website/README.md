# 静态官网

无需构建或服务器脚本，将本目录内容放入站点根目录即可。域名为 `lc.rongshangs.top`。`index.html` 可直接本地打开，资源和下载均为相对路径，没有外部字体或框架。

发布前使用 `python tools/sync_website.py` 同步项目图像、协议、曲线模型、已验证的安装包和源码包，输出到工作目录 `D:/IOS/web`。源码库不提交下载 ZIP；最终网站目录包含下载文件及其 SHA-256 校验值。

下载文件必须和页面版本一致。更新版本时同步修改页面版本及两条下载链接。曲线演示使用与 WebUI 相同的数学模型，只展示基础曲线，页面没有设备访问权限。

部署使用组装后的 `web` 目录，或执行 `python tools/package_website.py` 生成完整部署 ZIP。将 ZIP 内容解压到 `lc.rongshangs.top` 实际站点根目录，确保 `index.html`、`assets`、`downloads` 同级；不要额外套一层目录。

`website` 是仓库中的官网源码，下载文件由同步脚本从 `dist` 复制，不随 Git 提交。只上传官网源码会缺少安装包。部署后直接访问 `/downloads/luma_curve-1.0.0.zip`、`/downloads/LumaCurve-1.0.0-source.zip` 和 `/downloads/SHA256SUMS.txt`，都应返回 200。页面能打开但这三个路径返回 404 时，应检查 `downloads` 是否上传到该站点的正确根目录，以及 Web 服务器的静态文件路径配置。
