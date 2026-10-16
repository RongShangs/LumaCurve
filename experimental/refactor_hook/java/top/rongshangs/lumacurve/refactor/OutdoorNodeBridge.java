package top.rongshangs.lumacurve.refactor;

import android.os.SystemClock;
import android.system.Os;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** No I/O in OEM output hooks. Fresh, short leases are emitted off the display thread. */
final class OutdoorNodeBridge {
    static final String LEASE="/data/system/hyperlux-outdoor-node-lease";
    static final Executor IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"HyperLux-outdoor-lease");t.setDaemon(true);return t;});
    interface Sink {void write(String value)throws Exception;}
    final HookRuntime s;final Runnable check=this::evaluate;final Executor executor;final Sink sink;volatile boolean failed;String acceptedRevision="";
    volatile float frame=Float.NaN;boolean queued,requested,closed;String pending;boolean writing;
    OutdoorNodeBridge(HookRuntime state){this(state,IO,OutdoorNodeBridge::writeLease);}
    OutdoorNodeBridge(HookRuntime state,Executor executor,Sink sink){s=state;this.executor=executor;this.sink=sink;}
    static boolean interfaces(HookRuntime s,boolean range)throws Exception{
        if(!OutdoorNodeProfile.matches(s.kernel.name(),s.kernel.max,65535,RawPanelOutputHooks.supports(s.owner.getClass()),range))return false;
        Object dpc=HookEntry.get(s.owner,"mDisplayPowerController");if(dpc==null)return false;
        Object power=dpc.getClass().getMethod("getDisplayPowerState").invoke(dpc);if(power==null)return false;
        for(String name:new String[]{"getScreenBrightness","getSdrScreenBrightness"})if(power.getClass().getMethod(name).getReturnType()!=float.class)throw new NoSuchMethodException(name+" return type");
        Object clamp=HookEntry.get(dpc,"mBrightnessClamperController");if(clamp==null)return false;
        if(clamp.getClass().getMethod("getMaxBrightness").getReturnType()!=float.class)throw new NoSuchMethodException("getMaxBrightness return type");
        if(!(HookEntry.get(s.owner,"mBatteryThermalLimitBrightness") instanceof Number))throw new NoSuchFieldException("mBatteryThermalLimitBrightness type");
        OutdoorSdrScope.resolve(s.owner.getClass());return s.hdrActive()!=null&&s.outputStrategy(dpc)!=null;
    }
    void frame(float value){frame=value;changed();}
    void changed(){if(closed||s.closed||!s.outdoor.options.nodeBoost&&!requested||queued)return;queued=true;if(!s.handler.post(check))queued=false;}
    boolean allowed()throws Exception{
        if(failed||closed||s.closed||!s.outdoor.options.nodeBoost||!s.phase.equals("active")||!s.appliesToUser()||!s.onDisplayThread()||!s.screenOn()||s.power==null||!s.power.isInteractive()||s.power.isPowerSaveMode())return false;
        if(!OutdoorNodeProfile.matches(s.kernel.name(),s.kernel.max,65535,RawPanelOutputHooks.supports(s.owner.getClass()),s.outdoor.rangeSupported())||s.outdoor.access==null||s.outdoor.access.max()!=1f)return false;
        OutdoorSdrScope scope=OutdoorSdrScope.resolve(s.owner.getClass());if(scope.privacy!=null&&!scope.allows(s.owner))return false;
        if(!s.outdoor.policy.active||s.outdoor.block()!=null||s.outdoor.lux()<s.outdoor.options.values[1]||s.outdoor.options.values[OutdoorOptions.MAX_NIT]!=0)return false;
        if(!s.brightnessControl.owner.equals("none")||!s.brightnessControl.auto()||!s.brightnessControl.unlocked()||!Boolean.FALSE.equals(s.hdrActive()))return false;
        if(s.thermalSeverity!=0||!Float.isFinite(s.batteryTemperature)||s.batteryTemperature>Math.min(43,s.thermalCeiling)||s.batteryLevel<30)return false;
        Object dpc=HookEntry.get(s.owner,"mDisplayPowerController"),power=dpc.getClass().getMethod("getDisplayPowerState").invoke(dpc);
        float actual=((Number)power.getClass().getMethod("getScreenBrightness").invoke(power)).floatValue();
        float sdr=((Number)power.getClass().getMethod("getSdrScreenBrightness").invoke(power)).floatValue();
        Object clamp=HookEntry.get(dpc,"mBrightnessClamperController");float cap=((Number)clamp.getClass().getMethod("getMaxBrightness").invoke(clamp)).floatValue();
        float battery=((Number)HookEntry.get(s.owner,"mBatteryThermalLimitBrightness")).floatValue();
        return frame==1f&&actual==1f&&sdr==1f&&cap==1f&&battery==1f&&"AutomaticBrightnessStrategy".equals(s.outputStrategy(dpc));
    }
    void evaluate(){if(!acceptedRevision.equals(s.revision)){acceptedRevision=s.revision;failed=false;}queued=false;s.handler.removeCallbacks(check);boolean active=false;try{active=allowed();}catch(Throwable unknown){}
        if(active){requested=true;send(true,s.outdoor.options.nodeTarget);s.handler.postDelayed(check,500);}
        else release();
    }
    void release(){s.handler.removeCallbacks(check);queued=false;if(requested){requested=false;send(false,0);}}
    void close(){release();closed=true;}
    synchronized void send(boolean active,int target){
        pending="v2 "+android.os.Process.myPid()+" "+(SystemClock.uptimeMillis()+1500)+" "+target+" "+(active?1:0)+"\n";
        if(writing)return;writing=true;try{executor.execute(this::drain);}catch(RuntimeException rejected){writing=false;}
    }
    void drain(){while(true){String value;synchronized(this){value=pending;pending=null;if(value==null){writing=false;return;}}
        try{sink.write(value);}catch(Throwable unavailable){if(!failed)de.robv.android.xposed.XposedBridge.log("HyperLux outdoor lease unavailable: "+unavailable);failed=true;}
    }}
    static void writeLease(String value)throws Exception{
        File temp=new File(LEASE+".new");try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(value.getBytes(StandardCharsets.US_ASCII));}Os.chmod(temp.toString(),0600);Os.rename(temp.toString(),LEASE);}finally{temp.delete();}
    }
}
