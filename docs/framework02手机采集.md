# 给手机 root Agent：第二轮只读采集

解压 LumaCurve-framework-probe02.zip，在解压目录执行相对路径：

```sh
su
sh ./probe_framework02_android.sh
```

保留当前模块运行，不必重启。通常约 1～2 分钟；框架复制和系统 dump 的速度依设备而定。
它只复制四个系统 JAR、读取接口与显示状态，不改模式、亮度、配置或 SELinux。
需要为复制与压缩预留约 300MB 可用空间。

将输出的整个 `luma-framework02-时间戳-PID` 文件夹压缩回传。
需要包含 firmware 下四个 JAR 和 firmware-sha256.txt，以便离线核对本机实现。
这些 JAR 不会发布到 GitHub 或放入模块。

第一轮已证实基础亮度读取可用、小米亮度实现存在。现在需要核对实际字节码，
不能根据方法名猜测临时模式参数。旧版 control 暂时不用；本包入口只接受只读采集。
目前尚未实现稳定框架接管，也没有更新模块核心。
