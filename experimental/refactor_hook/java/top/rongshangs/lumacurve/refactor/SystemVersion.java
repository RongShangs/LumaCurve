package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import org.json.*;

/** Read-only platform preflight. Does not infer HyperOS from Android API or device name. */
final class SystemVersion {
    static final String NAME="ro.mi.os.version.name",INCREMENTAL="ro.mi.os.version.incremental";
    private static volatile Result cached;
    interface Reader {String get(String key)throws Exception;}
    static final class Result {
        final int major;final String version,status;
        Result(int major,String version){this.major=major;this.version=version;status=major<0?"unknown":major==4?"supported":"unsupported";}
        boolean unsupported(){return status.equals("unsupported");}
        void put(JSONObject j)throws JSONException{j.put("system_version",version).put("system_major",major).put("system_compatibility",status);}
    }
    static int major(String value){
        if(value==null||value.length()>128)return -1;
        Matcher m=Pattern.compile("^(?:OS|HyperOS\\s*)?([1-9][0-9]?)(?:\\.[0-9].*)?$",Pattern.CASE_INSENSITIVE).matcher(value.trim());
        return m.matches()?Integer.parseInt(m.group(1)):-1;
    }
    static Result inspect(Reader reader){
        String name="",incremental="";try{name=reader.get(NAME);}catch(Exception ignored){}try{incremental=reader.get(INCREMENTAL);}catch(Exception ignored){}
        int a=major(name),b=major(incremental);if(a>=0&&b>=0&&a!=b)return new Result(-1,"版本属性不一致");
        int version=a>=0?a:b;String label=a>=0?name:b>=0?incremental:"未能识别";return new Result(version,label.trim());
    }
    static Result read(){Result previous=cached;if(previous!=null)return previous;Result result=inspect(SystemVersion::property);if(result.major>=0)cached=result;return result;}
    static String property(String key)throws Exception{
        try{Object raw=Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,key);if(raw instanceof String&&!((String)raw).isEmpty())return (String)raw;}catch(Exception ignored){}
        Process p=new ProcessBuilder("/system/bin/getprop",key).redirectErrorStream(true).start();
        try{if(!p.waitFor(800,TimeUnit.MILLISECONDS))throw new IOException("版本读取超时");if(p.exitValue()!=0)throw new IOException("版本读取失败");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[256];int n;try(InputStream in=p.getInputStream()){while((n=in.read(block))!=-1){if(bytes.size()+n>1024)throw new IOException("版本输出异常");bytes.write(block,0,n);}}
            return new String(bytes.toByteArray(),StandardCharsets.UTF_8).trim();
        }finally{p.destroyForcibly();}
    }
}
