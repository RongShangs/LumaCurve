package top.rongshangs.lumacurve.refactor;

import android.os.Handler;
import java.lang.reflect.*;
import org.json.*;

/** Optional shared system twilight service; never requests location or guesses clock hours. */
final class DarkLockSchedule {
    static final String PROFILE="system_twilight";
    static final class Window {
        final long sunrise,sunset,next;final boolean known,night;
        Window(long rise,long set,long now){
            sunrise=rise;sunset=set;
            // The native state brackets the current daytime or nighttime interval.
            // A past/future snapshot is pending, not a reason to infer another day's times.
            known=rise>0&&set>0&&rise!=set&&now>=Math.min(rise,set)&&now<Math.max(rise,set);
            night=known&&set<rise;next=known?Math.max(rise,set):-1;
        }
    }
    static final class Binding {
        final Object service;final Class<?> listener,state;final Method read,register,unregister,rise,set;
        Binding(ClassLoader loader)throws Exception {
            Class<?> local=Class.forName("com.android.server.LocalServices",false,loader);
            Class<?> manager=Class.forName("com.android.server.twilight.TwilightManager",false,loader);
            listener=Class.forName("com.android.server.twilight.TwilightListener",false,loader);
            state=Class.forName("com.android.server.twilight.TwilightState",false,loader);
            if(!listener.isInterface())throw new IllegalStateException("Invalid twilight listener");
            Method get=local.getDeclaredMethod("getService",Class.class);
            if(!Modifier.isStatic(get.getModifiers()))throw new IllegalStateException("Invalid local service lookup");
            get.setAccessible(true);service=get.invoke(null,manager);
            if(service==null)throw new IllegalStateException("System twilight service pending");
            if(!manager.isInstance(service))throw new IllegalStateException("Invalid twilight service");
            read=method(manager,"getLastTwilightState",state);
            register=method(manager,"registerListener",void.class,listener,Handler.class);
            unregister=method(manager,"unregisterListener",void.class,listener);
            method(listener,"onTwilightStateChanged",void.class,state);
            rise=method(state,"sunriseTimeMillis",long.class);set=method(state,"sunsetTimeMillis",long.class);
        }
        static Method method(Class<?> cls,String name,Class<?> result,Class<?>... args)throws Exception {
            Method m=cls.getMethod(name,args);
            if(Modifier.isStatic(m.getModifiers())||m.getReturnType()!=result)throw new IllegalStateException("Invalid twilight ABI: "+name);
            m.setAccessible(true);return m;
        }
        Window read(long now)throws Exception {
            Object value=read.invoke(service);
            if(value==null)return new Window(0,0,now);
            if(!state.isInstance(value))throw new IllegalStateException("Invalid twilight state");
            return new Window((Long)rise.invoke(value),(Long)set.invoke(value),now);
        }
    }
    final ClassLoader loader;final Handler handler;final Runnable changed;
    Binding binding;Object listener;boolean active;int attempts;long generation;
    Window window=new Window(0,0,0);String error="";
    final Runnable boundary,retry;
    DarkLockSchedule(ClassLoader loader,Handler handler,Runnable changed){
        this.loader=loader;this.handler=handler;this.changed=changed;
        boundary=()->{refresh();changed.run();};retry=()->{if(active){attach();changed.run();}};
    }
    static boolean supported(ClassLoader loader)throws Exception {new Binding(loader);return true;}
    void enabled(boolean value){
        if(!value){if(active)stop();return;}
        if(!active){active=true;attempts=0;attach();}
        else if(binding!=null)refresh();
    }
    void attach(){
        if(!active||binding!=null)return;attempts++;
        Binding next=null;Object proxy=null;long token=++generation;
        try{
            next=new Binding(loader);
            proxy=Proxy.newProxyInstance(next.listener.getClassLoader(),new Class<?>[]{next.listener},(p,m,args)->{
                if(m.getDeclaringClass()==Object.class){
                    if(m.getName().equals("hashCode"))return System.identityHashCode(p);
                    if(m.getName().equals("equals"))return p==args[0];
                    return "HyperLuxTwilightListener";
                }
                // Re-read on our display handler; queued callbacks from a prior session have no ownership.
                if(m.getName().equals("onTwilightStateChanged"))handler.post(()->{
                    if(active&&generation==token){refresh();changed.run();}
                });
                return null;
            });
            next.register.invoke(next.service,proxy,handler);binding=next;listener=proxy;error="";refresh();
        }catch(Throwable unavailable){
            if(next!=null&&proxy!=null)try{next.unregister.invoke(next.service,proxy);}catch(Throwable cleanup){unavailable.addSuppressed(cleanup);}
            binding=null;listener=null;generation++;window=new Window(0,0,0);error=unavailable.toString();
            if(active&&attempts<3)handler.postDelayed(retry,60000);
        }
    }
    void refresh(){
        handler.removeCallbacks(boundary);
        if(!active||binding==null)return;
        try{
            long now=System.currentTimeMillis();window=binding.read(now);error="";
            if(window.known)handler.postDelayed(boundary,Math.max(1,window.next-now));
        }catch(Throwable unavailable){window=new Window(0,0,0);error=unavailable.toString();}
    }
    boolean allowed(){return active&&binding!=null&&window.known&&window.night;}
    void stop(){
        active=false;generation++;handler.removeCallbacks(boundary);handler.removeCallbacks(retry);
        Binding old=binding;Object oldListener=listener;binding=null;listener=null;window=new Window(0,0,0);
        if(old!=null&&oldListener!=null)try{old.unregister.invoke(old.service,oldListener);}catch(Throwable failure){error=failure.toString();}
    }
    JSONObject status()throws JSONException {
        return new JSONObject().put("profile",binding==null?"":PROFILE).put("active",active)
            .put("known",window.known).put("night",window.night).put("sunrise_ms",window.sunrise)
            .put("sunset_ms",window.sunset).put("next_ms",window.next).put("error",error);
    }
}
