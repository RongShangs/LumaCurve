package top.rongshangs.lumacurve.refactor;
import android.content.AttributionSource;
import android.os.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Actual settings access, independent of root-manager branding and native-node access. */
final class RootSettings implements AutoCloseable {
 interface Access extends AutoCloseable{String id();String get(String table,String key)throws Exception;void put(String table,String key,String value)throws Exception;}
 Access access;final JSONArray attempts=new JSONArray();boolean readable,writeConfirmed;String lastError="",writeConfirmedKey="";
 static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{
  Method method=target.getClass().getMethod(name,types);if(!Modifier.isPublic(method.getDeclaringClass().getModifiers()))method.setAccessible(true);
  try{return method.invoke(target,args);}catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;throw error;}
 }
 RootSettings(){
  for(int route=0;route<3;route++){
   Access candidate=null;String id=route==0?"external_provider":route==1?"cmd_settings":"settings_cli";
   try{candidate=route==0?new Provider():new Shell(route==1);candidate.get("global","lumacurve_refactor_config_v1");access=candidate;readable=true;lastError="";attempt(id,true,"");break;}
   catch(Exception unavailable){lastError=unavailable.toString();attempt(id,false,lastError);if(candidate!=null)try{candidate.close();}catch(Exception ignored){}}
  }
 }
 void attempt(String id,boolean ok,String detail){try{attempts.put(new JSONObject().put("id",id).put("readable",ok).put("detail",detail.substring(0,Math.min(detail.length(),512))));}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
 boolean available(){return access!=null&&readable;}
 void require()throws IOException{if(access==null)throw new IOException("当前 Root 无法访问系统设置接口；请检查 Root 管理器的应用权限配置。"+lastError);}
 String read(String table,String key)throws Exception{require();try{String value=access.get(table,key);readable=true;return value;}catch(Exception error){readable=false;lastError=error.toString();throw error;}}
 String get(String key)throws Exception{return read("global",key);}
 String getSystem(String key)throws Exception{return read("system",key);}
 boolean write(String table,String key,String value)throws Exception{
  require();writeConfirmed=false;writeConfirmedKey="";try{access.put(table,key,value);boolean ok=Objects.equals(value,read(table,key));if(ok){writeConfirmed=true;writeConfirmedKey=key;lastError="";}else lastError="系统设置写入未确认";return ok;}
  catch(Exception error){lastError=error.toString();throw error;}
 }
 boolean put(String key,String value)throws Exception{return write("global",key,value);}
 boolean putSystem(String key,String value)throws Exception{return write("system",key,value);}
 JSONObject report()throws JSONException{return new JSONObject().put("route",access==null?"":access.id()).put("readable",available()).put("write_confirmed",writeConfirmed).put("write_confirmed_key",writeConfirmedKey).put("write_probe","explicit_operations_only").put("last_error",lastError).put("routes",new JSONArray(attempts.toString()));}
 public void close()throws Exception{if(access!=null)access.close();}
 static final class Provider implements Access{
  final Object manager,provider;final IBinder token=new Binder();final AttributionSource source;
  Provider()throws Exception{
   manager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
   Object holder=call(manager,"getContentProviderExternal",new Class<?>[]{String.class,int.class,IBinder.class,String.class},"settings",0,token,"LumaCurve-refactor-test");if(holder==null)throw new IOException("未取得系统设置接口");
   try{provider=holder.getClass().getField("provider").get(holder);if(provider==null)throw new IOException("系统设置接口为空");int uid=android.os.Process.myUid();AttributionSource.Builder builder=new AttributionSource.Builder(uid).setPackageName(uid==0||uid==1000?"android":uid==2000?"com.android.shell":"top.rongshangs.lumacurve");try{builder.getClass().getMethod("setPid",int.class).invoke(builder,android.os.Process.myPid());}catch(NoSuchMethodException optional){}source=builder.build();}
   catch(Exception failure){try{close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;}
  }
  public String id(){return "external_provider";}
  Bundle request(String method,String key,Bundle extras)throws Exception{extras.putInt("_user",0);return (Bundle)call(provider,"call",new Class<?>[]{AttributionSource.class,String.class,String.class,String.class,Bundle.class},source,"settings",method,key,extras);}
  public String get(String table,String key)throws Exception{Bundle result=request("GET_"+table,key,new Bundle());if(result==null){if(table.equals("global"))return null;throw new IOException("系统设置接口未返回结果");}return result.getString("value");}
  public void put(String table,String key,String value)throws Exception{Bundle extras=new Bundle();extras.putString("value",value);request("PUT_"+table,key,extras);}
  public void close()throws Exception{call(manager,"removeContentProviderExternalAsUser",new Class<?>[]{String.class,IBinder.class,int.class},"settings",token,0);}
 }
 static final class Shell implements Access{
  final boolean cmd;Shell(boolean cmd){this.cmd=cmd;}public String id(){return cmd?"cmd_settings":"settings_cli";}
  String request(String operation,String table,String key,String value)throws Exception{
   List<String> args=new ArrayList<>();args.add(cmd?"/system/bin/cmd":"/system/bin/settings");if(cmd)args.add("settings");args.add("--user");args.add("0");args.add(operation);args.add(table);args.add(key);if(value!=null)args.add(value);
   String result=RootCommand.run(5000,args.toArray(new String[0]));
   String lower=result.toLowerCase(Locale.ROOT);if(lower.startsWith("error")||lower.startsWith("exception")||lower.startsWith("java.")||lower.startsWith("permission denied")||lower.startsWith("permission denial")||lower.startsWith("security exception")||lower.startsWith("unknown")||lower.startsWith("invalid")||lower.startsWith("usage:")||lower.startsWith("settings provider"))throw new IOException("系统设置命令被拒绝："+result.substring(0,Math.min(result.length(),512)));
   return result;
  }
  public String get(String table,String key)throws Exception{String value=request("get",table,key,null);return "null".equals(value)?null:value;}
  public void put(String table,String key,String value)throws Exception{String result=request(value==null?"delete":"put",table,key,value);if(value!=null&&!result.isEmpty()||value==null&&!result.matches("Deleted [0-9]+ rows"))throw new IOException("系统设置命令未确认完成");}
  public void close(){}
 }
}
