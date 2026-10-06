"""Run the production payload reader against boundary fixtures and the final APK."""
from pathlib import Path
import subprocess,sys
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/native-asset-tests';O.mkdir(parents=True,exist_ok=True)
p=O/'NativeAssetTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.io.*;import java.util.*;import java.util.zip.*;import java.security.MessageDigest;
public class NativeAssetTest{
static int cases;static void check(boolean value){if(!value)throw new AssertionError("case "+cases);cases++;}
static File fixture(String name,byte[] bytes)throws Exception{
 File file=new File("build/native-asset-tests",name+".apk");
 try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){if(bytes!=null){out.putNextEntry(new ZipEntry(NativePanelAsset.PATH));out.write(bytes);out.closeEntry();}}return file;
}
static byte[] elf(int size){byte[] bytes=new byte[size];if(size>=64){bytes[0]=0x7f;bytes[1]='E';bytes[2]='L';bytes[3]='F';bytes[4]=2;bytes[5]=1;bytes[18]=(byte)183;}return bytes;}
static void reject(File file,String reason)throws Exception{try{NativePanelAsset.read(file);throw new AssertionError("accepted "+file);}catch(IOException expected){check(expected.getMessage().contains(reason));}}
public static void main(String[] args)throws Exception{
check(NativePanelAsset.MAX_BYTES==4194304);
byte[] oldSize=elf(2160320);check(oldSize.length>2*1024*1024);check(Arrays.equals(oldSize,NativePanelAsset.read(fixture("test03-size",oldSize))));
byte[] maximum=elf(NativePanelAsset.MAX_BYTES);check(Arrays.equals(maximum,NativePanelAsset.read(fixture("max",maximum))));
reject(fixture("missing",null),"缺少主屏守护文件");reject(fixture("empty",new byte[0]),"长度无效");
reject(fixture("short",elf(63)),"长度无效");reject(fixture("oversized",elf(NativePanelAsset.MAX_BYTES+1)),"超出大小上限");
byte[] wrong=elf(128);wrong[18]=62;reject(fixture("x86",wrong),"不是 ARM64 ELF");wrong=elf(128);wrong[0]=0;reject(fixture("not-elf",wrong),"不是 ARM64 ELF");
wrong=elf(128);wrong[5]=2;reject(fixture("wrong-endian",wrong),"不是 ARM64 ELF");
for(String path:args){File apk=new File(path);byte[] loaded=NativePanelAsset.read(apk),stored;
 try(ZipFile zip=new ZipFile(apk);InputStream in=zip.getInputStream(zip.getEntry(NativePanelAsset.PATH));ByteArrayOutputStream out=new ByteArrayOutputStream()){
  byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);stored=out.toByteArray();
 }
 check(loaded.length>=64&&loaded.length<=NativePanelAsset.MAX_BYTES);check(Arrays.equals(loaded,stored));
 check(Arrays.equals(MessageDigest.getInstance("SHA-256").digest(loaded),MessageDigest.getInstance("SHA-256").digest(stored)));
 System.out.println("Final APK native asset read: "+apk.getName()+", "+loaded.length+" bytes PASS");
}
System.out.println("Native asset production reader: "+cases+" cases PASS; final APK extraction verified, Android not tested");
}}
''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(classes),str(S/'NativePanelAsset.java'),str(p)],check=True)
subprocess.run(['java','-cp',str(classes),'top.rongshangs.lumacurve.refactor.NativeAssetTest',*sys.argv[1:]],check=True,cwd=R)
