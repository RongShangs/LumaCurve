"""Exercise the production client with Android's lazy LocalSocket descriptor contract.

This is an API-contract regression, not a claim of Android/SELinux integration coverage.
The negative control reintroduces the old ordering and must reproduce its failure.
"""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
OUT = ROOT / 'build/native-transport-tests'
JSON = ROOT / 'build/refactor-diagnostics/json-20240303.jar'
sources = {
    'android/net/LocalSocketAddress.java': '''package android.net;
public final class LocalSocketAddress {
 public enum Namespace { ABSTRACT }
 public final String name; public final Namespace namespace;
 public LocalSocketAddress(String name,Namespace namespace){this.name=name;this.namespace=namespace;}
}
''',
    'android/net/LocalSocket.java': r'''package android.net;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class LocalSocket implements Closeable {
 public static String response; public static boolean failConnect,failRead;
 public static int connections,timeouts,closes;public static LocalSocket last;
 boolean created,connected;InputStream input;public int timeout;public final ByteArrayOutputStream sent=new ByteArrayOutputStream();
 public LocalSocket(){last=this;}
 public static void reset(){response="{\"ok\":true,\"actual\":12000}\n";failConnect=failRead=false;connections=timeouts=closes=0;last=null;}
 public void connect(LocalSocketAddress address)throws IOException{
  created=true;if(!address.name.equals("hyperlux.main.panel.raw04")||address.namespace!=LocalSocketAddress.Namespace.ABSTRACT)throw new AssertionError("wrong endpoint");
  connections++;if(failConnect)throw new IOException("Connection refused");connected=true;
 }
 public void setSoTimeout(int value)throws IOException{
  // AOSP LocalSocket.setSoTimeout delegates to setOption without implCreateIfNeeded.
  if(!created)throw new IOException("socket not created");timeouts++;timeout=value;
 }
 public OutputStream getOutputStream()throws IOException{if(!connected)throw new IOException("socket not connected");return sent;}
 public InputStream getInputStream()throws IOException{
  if(!connected)throw new IOException("socket not connected");
  if(failRead)return new InputStream(){public int read()throws IOException{throw new IOException("read timed out");}};
  if(input==null)input=new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));return input;
 }
 public void close(){closes++;connected=false;}
}
''',
    'top/rongshangs/lumacurve/refactor/NativeTransportTest.java': r'''package top.rongshangs.lumacurve.refactor;
import android.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import org.json.*;
public final class NativeTransportTest {
 static int cases;static void check(boolean value){if(!value)throw new AssertionError("case "+cases);cases++;}
 interface Task{void run()throws Exception;}
 static void failed(Task action,String message)throws Exception{try{action.run();throw new AssertionError("unexpected success");}catch(IOException expected){check(expected.getMessage().contains(message));}check(LocalSocket.closes==1);}
 public static void main(String[] args)throws Exception{
  LocalSocket.reset();LocalSocket.response="{\"ok\":true,\"actual\":12000}\n";
  if(args.length>0){failed(()->NativePanelClient.call("STATUS"),"socket not created");check(LocalSocket.connections==0);System.out.println("Old client ordering reproduced: socket not created before connect PASS");return;}
  JSONObject result=NativePanelClient.call("STATUS");check(result.getBoolean("ok"));check(result.getInt("actual")==12000);
  check(LocalSocket.connections==1&&LocalSocket.timeouts==1&&LocalSocket.last.timeout==350);check(LocalSocket.closes==1);
  check(new String(LocalSocket.last.sent.toByteArray(),StandardCharsets.US_ASCII).equals("STATUS\n"));
  for(String command:new String[]{"ARM session123 1234","SET session123 12000","STOP session123"}){
   LocalSocket.reset();LocalSocket.response="{\"ok\":true}\n";NativePanelClient.require(command);
   check(new String(LocalSocket.last.sent.toByteArray(),StandardCharsets.US_ASCII).equals(command+"\n"));check(LocalSocket.closes==1);
  }
  LocalSocket.reset();LocalSocket.failConnect=true;failed(()->NativePanelClient.call("STATUS"),"Connection refused");check(LocalSocket.timeouts==0);
  LocalSocket.reset();LocalSocket.failRead=true;failed(()->NativePanelClient.call("STATUS"),"read timed out");
  LocalSocket.reset();LocalSocket.response="";failed(()->NativePanelClient.call("STATUS"),"主屏节点守护未响应");
  LocalSocket.reset();LocalSocket.response="{\"ok\":false,\"reason\":\"lease_invalid\"}\n";failed(()->NativePanelClient.require("ARM invalid 12"),"lease_invalid");
  LocalSocket.reset();LocalSocket.response="{\"ok\":false,\"reason\":\"写入失败\"}\n";check(NativePanelClient.call("STATUS").getString("reason").equals("写入失败"));check(LocalSocket.closes==1);
  LocalSocket.reset();StringBuilder large=new StringBuilder();for(int i=0;i<2200;i++)large.append('x');LocalSocket.response=large.toString();failed(()->NativePanelClient.call("STATUS"),"守护响应过长");
  LocalSocket.reset();LocalSocket.response="invalid json\n";try{NativePanelClient.call("STATUS");throw new AssertionError("invalid response accepted");}catch(JSONException expected){check(true);}check(LocalSocket.closes==1);
  System.out.println("Native socket production client: "+cases+" cases PASS; lazy-descriptor API model, Android not tested");
 }
}
''',
}
OUT.mkdir(parents=True, exist_ok=True)
paths = []
for name, source in sources.items():
    path = OUT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(source, encoding='utf-8')
    paths.append(path)
classes = OUT / 'classes'
classes.mkdir(exist_ok=True)
client = SRC / 'NativePanelClient.java'
subprocess.run(['javac', '-encoding', 'UTF-8', '--release', '8', '-cp', str(JSON), '-d', str(classes), str(client), *map(str, paths)], check=True)
entry = 'top.rongshangs.lumacurve.refactor.NativeTransportTest'
subprocess.run(['java', '-cp', str(classes) + os.pathsep + str(JSON), entry], check=True)

fixed = client.read_text(encoding='utf-8')
old = fixed.replace('socket.connect(new LocalSocketAddress("hyperlux.main.panel.raw04",LocalSocketAddress.Namespace.ABSTRACT));socket.setSoTimeout(350);', 'socket.setSoTimeout(350);socket.connect(new LocalSocketAddress("hyperlux.main.panel.raw04",LocalSocketAddress.Namespace.ABSTRACT));')
assert old != fixed, 'negative control did not restore the faulty ordering'
broken = OUT / 'negative/top/rongshangs/lumacurve/refactor/NativePanelClient.java'
broken.parent.mkdir(parents=True, exist_ok=True)
broken.write_text(old, encoding='utf-8')
negative = OUT / 'negative-classes'
negative.mkdir(exist_ok=True)
subprocess.run(['javac', '-encoding', 'UTF-8', '--release', '8', '-cp', str(JSON), '-d', str(negative), str(broken), *map(str, paths)], check=True)
subprocess.run(['java', '-cp', str(negative) + os.pathsep + str(JSON), entry, 'negative'], check=True)
