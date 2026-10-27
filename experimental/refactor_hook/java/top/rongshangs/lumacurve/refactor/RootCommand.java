package top.rongshangs.lumacurve.refactor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded command transport; reading diagnostics never requires /data/adb writes. */
final class RootCommand {
 static String run(long timeout,String...command)throws Exception{
  Process child=new ProcessBuilder(command).redirectErrorStream(true).start();
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();AtomicReference<IOException> failure=new AtomicReference<>();
  Thread reader=new Thread(()->{try(InputStream in=child.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>8*1024*1024)throw new IOException("命令输出过长");bytes.write(buffer,0,n);}}catch(IOException error){failure.set(error);child.destroyForcibly();}},"HyperLux-root-output");reader.setDaemon(true);reader.start();
  try{
   if(!child.waitFor(timeout,TimeUnit.MILLISECONDS)){child.destroyForcibly();throw new IOException("Root 命令超时："+command[0]);}
   reader.join(1000);if(reader.isAlive())throw new IOException("Root 命令输出未关闭："+command[0]);
   if(failure.get()!=null)throw failure.get();String result=new String(bytes.toByteArray(),StandardCharsets.UTF_8).trim();
   if(child.exitValue()!=0)throw new IOException("Root 命令失败："+command[0]+"（"+child.exitValue()+"）"+(result.isEmpty()?"":"\n"+result.substring(0,Math.min(result.length(),1024))));
   return result;
  }finally{child.destroy();try{child.getInputStream().close();}catch(IOException ignored){}reader.interrupt();}
 }
}
