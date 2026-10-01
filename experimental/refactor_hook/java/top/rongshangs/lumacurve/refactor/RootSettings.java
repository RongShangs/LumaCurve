package top.rongshangs.lumacurve.refactor;

import android.content.AttributionSource;
import android.os.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.Objects;

/** External provider token protocol already used by the device's framework probes. */
final class RootSettings implements AutoCloseable {
    final Object manager,provider;
    final IBinder token=new Binder();
    final AttributionSource source;
    static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception {
        Method method=target.getClass().getMethod(name,types);
        if(!Modifier.isPublic(method.getDeclaringClass().getModifiers()))method.setAccessible(true);
        try{return method.invoke(target,args);}catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;throw error;}
    }
    RootSettings()throws Exception {
        manager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Object holder=call(manager,"getContentProviderExternal",new Class<?>[]{String.class,int.class,IBinder.class,String.class},"settings",0,token,"LumaCurve-refactor-test");
        if(holder==null)throw new IOException("未取得系统设置接口");
        try {
            provider=holder.getClass().getField("provider").get(holder);
            if(provider==null)throw new IOException("系统设置接口为空");
            AttributionSource.Builder builder=new AttributionSource.Builder(0).setPackageName("android");
            try{builder.getClass().getMethod("setPid",int.class).invoke(builder,android.os.Process.myPid());}catch(NoSuchMethodException optional){}
            source=builder.build();
        }catch(Exception failure){
            try{release();}catch(Exception cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    Bundle request(String method,String name,Bundle extras)throws Exception {
        extras.putInt("_user",0);
        return (Bundle)call(provider,"call",new Class<?>[]{AttributionSource.class,String.class,String.class,String.class,Bundle.class},source,"settings",method,name,extras);
    }
    String get(String name)throws Exception {Bundle response=request("GET_global",name,new Bundle());return response==null?null:response.getString("value");}
    boolean put(String name,String value)throws Exception {
        Bundle extras=new Bundle();extras.putString("value",value);
        request("PUT_global",name,extras);
        return Objects.equals(value,get(name));
    }
    private void release()throws Exception {call(manager,"removeContentProviderExternalAsUser",new Class<?>[]{String.class,IBinder.class,int.class},"settings",token,0);}
    public void close()throws Exception {release();}
}
