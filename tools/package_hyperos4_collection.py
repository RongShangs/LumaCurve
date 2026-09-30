"""Package the standalone collector; no device firmware is included."""
from pathlib import Path
import hashlib, zipfile
ROOT=Path(__file__).resolve().parents[1]
target=ROOT/'dist/LumaCurve-device-collection01.zip'
script=(ROOT/'tools/collect_hyperos4_android.sh').read_bytes().replace(b'\r\n',b'\n')
(ROOT/'dist/collect_hyperos4_android.sh').write_bytes(script)
instructions='''LumaCurve HyperOS 4 一次性只读采集

把 collect_hyperos4_android.sh 放进手机一个目录。
在该目录打开 Root 终端，保持主屏亮起，执行：
su
sh ./collect_hyperos4_android.sh

无需安装模块，无需重启，也无需其他文件。
通常 30～90 秒；系统繁忙或框架文件较大时可能更久。
结束后，只回传内部存储根目录的 LumaCurve-device-info-*.tar.gz。
内部存储根目录即 /sdcard，不是系统的 /。

内容：固件框架文件及哈希、显示配置、主屏与背光信息、光感、唤醒状态，
以及已有 LumaCurve 的配置/状态/最近日志（若存在）。
不调整亮度、不暂停模块、不改设置、不改节点权限。
采集只用于本地分析，不自动上传、不将设备自动加入兼容名单。
框架文件不随官网、GitHub 或模块安装包分发。
'''
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('collect_hyperos4_android.sh',script)
    archive.writestr('使用说明.txt',instructions)
    archive.writestr('LICENSE',(ROOT/'LICENSE').read_bytes())
with zipfile.ZipFile(target) as archive:assert archive.testzip() is None
print(target)
print('SHA256',hashlib.sha256(target.read_bytes()).hexdigest())
