import java.io.*;
import java.util.*;

/** Firmware03 experiment: temporary strategy only, no Settings or mode writes. */
public final class LumaFrameworkProbeTemporary {
    interface Bridge {
        float current() throws Exception;
        float minimum() throws Exception;
        float maximum() throws Exception;
        void request(float value) throws Exception;
        void sample(String phase,float target) throws Exception;
        void delay() throws Exception;
    }
    static void run(Bridge b) throws Exception {
        float start=b.current(),min=b.minimum(),max=b.maximum();
        if (!LumaFrameworkProbe.finite(start)||!LumaFrameworkProbe.finite(min)||!LumaFrameworkProbe.finite(max)||
            min<0||max>1||min>=max||start<min||start>max)
            throw new IllegalStateException("invalid initial brightness/range");
        float up=Math.min(max,start+.01f),down=Math.max(min,start-.01f);
        System.out.printf(Locale.ROOT,"SAVED temporary=NaN current=%.7f min=%.7f max=%.7f%n",start,min,max);
        try {
            b.request(start); hold(b,"base",start,20);
            ramp(b,"rise",start,up); hold(b,"upper",up,40);
            ramp(b,"fall",up,down); hold(b,"lower",down,40);
            ramp(b,"return",down,start); hold(b,"final",start,20);
        } finally { b.request(Float.NaN); System.out.println("RELEASE_REQUEST sent"); }
        System.out.println("result=request_sequence_completed (device stability requires trace analysis)");
    }
    static void hold(Bridge b,String phase,float target,int n) throws Exception {
        for(int i=0;i<n;i++){b.sample(phase,target);b.delay();}
    }
    static void ramp(Bridge b,String phase,float from,float to) throws Exception {
        for(int i=1;i<=30;i++) {
            float value=i==30?to:Math.max(Math.min(from,to),Math.min(Math.max(from,to),from+(to-from)*(i/30f)));
            b.request(value); b.sample(phase,value);b.delay();
        }
    }
    static final class Android implements Bridge {
        final LumaFrameworkProbe.AndroidBridge bridge=new LumaFrameworkProbe.AndroidBridge();
        Android() throws Exception {}
        Object info() throws Exception {return bridge.info();}
        public float current() throws Exception {return LumaFrameworkProbe.floatField(info(),"brightness");}
        public float minimum() throws Exception {return bridge.minimum();}
        public float maximum() throws Exception {return bridge.maximum();}
        void preflight() throws Exception {
            Object display=LumaFrameworkProbe.invoke(bridge.global,"getDisplayInfo",new Class<?>[]{int.class},0);
            if (!"local:4630946949513469331".equals(display.getClass().getField("uniqueId").get(display)))
                throw new IllegalStateException("unexpected physical main display");
            if (display.getClass().getField("state").getInt(display)!=2)
                throw new IllegalStateException("main display must be ON");
            if (info().getClass().getField("isBrightnessOverrideByWindow").getBoolean(info()))
                throw new IllegalStateException("window brightness override is active");
            System.out.println("MAIN_DISPLAY verified");
        }
        public void request(float value) throws Exception {
            if (!Float.isNaN(value)) {
                preflight();
                if(value<minimum()||value>maximum())throw new IllegalStateException("range changed during test");
            }
            LumaFrameworkProbe.invoke(bridge.global,"setTemporaryBrightness",new Class<?>[]{int.class,float.class},0,value);
        }
        public void sample(String phase,float target) throws Exception {
            Object i=info();
            System.out.printf(Locale.ROOT,"SAMPLE,%d,%s,%.7f,%.7f,%.7f,%s%n",System.nanoTime(),phase,target,
                LumaFrameworkProbe.floatField(i,"brightness"),LumaFrameworkProbe.floatField(i,"adjustedBrightness"),
                LumaFrameworkProbe.file("/sys/class/backlight/panel0-backlight/brightness"));
        }
        public void delay() throws Exception {Thread.sleep(100);}
    }
    public static void main(String[] args) {
        try {
            Android b=new Android();
            if(args.length!=1)throw new IllegalArgumentException("preflight | test | release");
            if("release".equals(args[0])) {b.request(Float.NaN);System.out.println("RELEASE_REQUEST sent");}
            else if("preflight".equals(args[0])) {b.preflight();System.out.println("PREFLIGHT ok brightness="+b.current()+" min="+b.minimum()+" max="+b.maximum());}
            else if("test".equals(args[0])) {b.preflight();run(b);}
            else throw new IllegalArgumentException("unknown operation");
        } catch(Throwable e){e.printStackTrace();System.exit(2);}
    }
}
