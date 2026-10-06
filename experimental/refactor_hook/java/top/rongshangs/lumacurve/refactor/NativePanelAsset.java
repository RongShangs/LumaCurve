package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.util.zip.*;

/** Shared installer/build contract for the bounded ARM64 payload. */
final class NativePanelAsset {
    static final String PATH="assets/hyperlux-main-panel";
    static final int MAX_BYTES=4194304;

    static byte[] read(File source)throws IOException {
        try(ZipFile apk=new ZipFile(source)) {
            ZipEntry entry=apk.getEntry(PATH);
            if(entry==null||entry.isDirectory())throw new IOException("APK 缺少主屏守护文件："+PATH);
            if(entry.getSize()<64)throw new IOException("主屏守护文件长度无效："+entry.getSize());
            if(entry.getSize()>MAX_BYTES)throw tooLarge(entry.getSize());
            byte[] bytes;
            try(InputStream in=apk.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream((int)entry.getSize())) {
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){
                    if(count>MAX_BYTES-out.size())throw tooLarge((long)out.size()+count);
                    out.write(buffer,0,count);
                }
                bytes=out.toByteArray();
            }
            if(bytes.length!=entry.getSize())throw new IOException("主屏守护文件读取不完整");
            if(bytes[0]!=0x7f||bytes[1]!='E'||bytes[2]!='L'||bytes[3]!='F'||bytes[4]!=2||bytes[5]!=1||
               (bytes[18]&255)!=183||bytes[19]!=0)throw new IOException("主屏守护文件不是 ARM64 ELF 程序");
            return bytes;
        }
    }
    private static IOException tooLarge(long size){return new IOException("主屏守护文件超出大小上限："+size+" 字节，上限 "+MAX_BYTES+" 字节");}
}
