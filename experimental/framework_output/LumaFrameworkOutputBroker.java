import java.io.*;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Isolated root broker. Firmware-specific, finite experiment, never changes Settings. */
public final class LumaFrameworkOutputBroker implements LumaFrameworkOutputSession.Bridge {
    final LumaFrameworkProbe.AndroidBridge android=new LumaFrameworkProbe.AndroidBridge();
    final File run;
    final PrintWriter trace;
    final PrintWriter events;
    final LumaFrameworkOutputSession session=new LumaFrameworkOutputSession(this);
    final LumaFrameworkOutputLease client=new LumaFrameworkOutputLease();
    LumaFrameworkOutputSession.Snapshot snapshot;
    LumaFrameworkOutputRamp ramp;
    boolean acquired,hasGoal;
    long settingsStamp,sequence,lastClientSequence=-1;
    int mode,slider;
    float adjustment,initial,goal=-1,limited=-1,request;
    static long now(){return System.nanoTime()/1000000;}
    LumaFrameworkOutputBroker(File folder)throws Exception {
        run=folder;trace=new PrintWriter(new File(folder,"framework.trace"),"UTF-8");
        events=new PrintWriter(new File(folder,"framework.events"),"UTF-8");
        refresh();initial=snapshot.adjustedBrightness;request=initial;
        if(!LumaFrameworkOutputSession.usable(snapshot,now())||initial<snapshot.min||initial>snapshot.max)
            throw new IllegalStateException("initial mode, display, range or feedback invalid");
        Class<?> utils=Class.forName("com.android.internal.display.BrightnessUtils");
        LumaFrameworkOutputRamp.Coordinate coordinate=new LumaFrameworkOutputRamp.Coordinate(){
            public float encode(float value)throws Exception{return ((Number)LumaFrameworkProbe.invoke(utils,"convertLinearToGamma",new Class<?>[]{float.class},value)).floatValue();}
            public float decode(float value)throws Exception{return ((Number)LumaFrameworkProbe.invoke(utils,"convertGammaToLinear",new Class<?>[]{float.class},value)).floatValue();}
        };
        // Reject wrong firmware conversion signatures/units before any output.
        if(Math.abs(coordinate.decode(coordinate.encode(initial))-initial)>.00001f)throw new IllegalStateException("coordinate roundtrip failed");
        ramp=new LumaFrameworkOutputRamp(coordinate,initial,now());
        try {
            int selector=Class.forName("android.system.OsConstants").getField("_SC_CLK_TCK").getInt(null);
            Object ticks=LumaFrameworkProbe.invoke(Class.forName("android.system.Os"),"sysconf",new Class<?>[]{int.class},selector);
            event("CPU_CLK_TCK,value="+ticks);
        }catch(Exception unavailable){event("CPU_CLK_TCK,unavailable");}
        trace.println("time_ms,mode,owned,algorithm_goal,limited_goal,request,base,adjusted,node,min,max");trace.flush();
    }
    void refresh()throws Exception {
        long t=now();
        if(settingsStamp==0||t-settingsStamp>=500){
            String[] values=LumaFrameworkProbeSettings.readSettings(true);
            mode=Integer.parseInt(values[0]);adjustment=Float.parseFloat(values[1]);slider=Integer.parseInt(values[2]);settingsStamp=now();
        }
        Object display=LumaFrameworkProbe.invoke(android.global,"getDisplayInfo",new Class<?>[]{int.class},0);
        if(display==null||!"local:4630946949513469331".equals(display.getClass().getField("uniqueId").get(display)))throw new IllegalStateException("wrong main display");
        boolean on=display.getClass().getField("state").getInt(display)==2;
        Object info=android.info();
        int node=Integer.parseInt(LumaFrameworkProbe.file("/sys/class/backlight/panel0-backlight/brightness").trim());
        snapshot=new LumaFrameworkOutputSession.Snapshot(now(),mode==1?LumaFrameworkOutputSession.Mode.AUTO:LumaFrameworkOutputSession.Mode.MANUAL,on,
            info.getClass().getField("isBrightnessOverrideByWindow").getBoolean(info),LumaFrameworkProbe.floatField(info,"brightness"),
            LumaFrameworkProbe.floatField(info,"adjustedBrightness"),LumaFrameworkProbe.floatField(info,"brightnessMinimum"),LumaFrameworkProbe.floatField(info,"brightnessMaximum"),node);
    }
    public LumaFrameworkOutputSession.Snapshot read(){return snapshot;}
    public void temporary(float value)throws Exception {LumaFrameworkProbe.invoke(android.global,"setTemporaryBrightness",new Class<?>[]{int.class,float.class},0,value);}
    public boolean temporaryCleared()throws Exception {
        File dump=new File(run,"release-display.txt");
        Process process=new ProcessBuilder("/system/bin/dumpsys","display").redirectErrorStream(true).redirectOutput(dump).start();
        if(!process.waitFor(3,TimeUnit.SECONDS)){process.destroyForcibly();return false;}
        if(process.exitValue()!=0)return false;
        boolean main=false,id=false,clear=false;
        try(BufferedReader reader=new BufferedReader(new FileReader(dump))){
            String line;while((line=reader.readLine())!=null){
                if(line.equals("Display Power Controller:")){if(main)break;main=true;continue;}
                if(main){if(line.contains("mDisplayId=0"))id=true;if(line.contains("mTemporaryScreenBrightness:NaN"))clear=true;}
            }
        }
        return id&&clear;
    }
    void event(String text){events.println(now()+","+text);events.flush();}
    void release()throws Exception {
        boolean responsible=acquired||session.state()!=LumaFrameworkOutputSession.State.IDLE;
        acquired=false;hasGoal=false;goal=limited=-1;
        if(responsible)event("RELEASE_BEGIN,mode="+mode);
        session.release();
        if(responsible)event("RELEASE_CONFIRMED,mode="+mode);
    }
    synchronized void tick() {
        try {
            refresh();long t=now();
            if(acquired&&(!client.alive(t)||!LumaFrameworkOutputSession.usable(snapshot,t)))release();
            session.tick(t);
            if(acquired&&hasGoal){
                // Each new ownership session stays near its real initial feedback.
                limited=Math.max(snapshot.min,Math.min(snapshot.max,Math.max(initial-.01f,Math.min(initial+.01f,goal))));
                request=ramp.next(limited,snapshot.min,snapshot.max,now());
                session.submit(++sequence,now(),LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,request,1000);
            }
            trace.printf(Locale.ROOT,"%d,%d,%d,%.7f,%.7f,%.7f,%.7f,%.7f,%d,%.7f,%.7f%n",now(),mode,acquired?1:0,goal,limited,request,
                snapshot.baseBrightness,snapshot.adjustedBrightness,snapshot.node,snapshot.min,snapshot.max);trace.flush();
        }catch(Exception error){
            System.err.println("BROKER_GUARD "+error);settingsStamp=0;
            try{release();}catch(Exception failure){System.err.println("RELEASE_PENDING "+failure);}
        }
    }
    synchronized String command(String line)throws Exception {
        if(line==null||line.length()>120)throw new IllegalArgumentException("invalid RPC");
        String[] words=line.trim().split(" +");refresh();long t=now();
        switch(words[0]) {
        case "Q":if(words.length!=1)throw new IllegalArgumentException();break;
        case "A":
            if(words.length!=1||!LumaFrameworkOutputSession.usable(snapshot,t)||session.state()==LumaFrameworkOutputSession.State.RELEASE_PENDING)
                throw new IllegalStateException("control forbidden");
            if(!acquired){
                initial=snapshot.adjustedBrightness;ramp.reset(initial,t);request=initial;
                goal=limited=-1;event("ACQUIRE,anchor="+initial);
            }
            acquired=true;client.renew(t);ramp.stamp=t;break;
        case "T":
            if(words.length!=4||!acquired||!LumaFrameworkOutputSession.usable(snapshot,t))throw new IllegalStateException("goal without ownership");
            long seq=Long.parseLong(words[1]);int raw=Integer.parseInt(words[2]),max=Integer.parseInt(words[3]);
            if(seq<=lastClientSequence||max!=16383||raw<0||raw>max)throw new IllegalArgumentException("invalid sequence or raw goal");
            lastClientSequence=seq;
            float desired=raw/(float)max;
            if(!hasGoal||goal!=desired)event("GOAL,legacy_fraction="+desired);
            goal=desired;hasGoal=true;client.renew(t);break;
        case "P":if(words.length!=1)throw new IllegalArgumentException();client.renew(t);break;
        case "R":if(words.length!=1)throw new IllegalArgumentException();release();break;
        default:throw new IllegalArgumentException("unknown RPC");
        }
        if(acquired&&!LumaFrameworkOutputSession.usable(snapshot,now()))release();
        return String.format(Locale.ROOT,"OK %d %.7f %d %d %d %d %.7f %.7f %.7f %.7f %.7f %.7f %.7f %d\n",mode,adjustment,slider,
            snapshot.on?1:0,snapshot.windowOverride?1:0,snapshot.node,snapshot.min,snapshot.max,snapshot.baseBrightness,snapshot.adjustedBrightness,goal,limited,request,acquired?1:0);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2||!args[0].matches("[A-Za-z0-9._-]{1,90}")||!args[1].startsWith("/data/local/tmp/luma-framework-core.")||args[1].contains(".."))
            throw new IllegalArgumentException("isolated socket and runtime required");
        LumaFrameworkOutputBroker broker=new LumaFrameworkOutputBroker(new File(args[1]));
        Object server=Class.forName("android.net.LocalServerSocket").getConstructor(String.class).newInstance(args[0]);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{synchronized(broker){try{broker.release();}catch(Exception e){System.err.println(e);}}}));
        Thread frames=new Thread(()->{for(;;){broker.tick();try{Thread.sleep(100);}catch(InterruptedException e){return;}}},"Luma-framework-frames");
        frames.setDaemon(true);frames.start();
        new File(broker.run,"broker-ready").createNewFile();
        System.out.println("BROKER_READY build=20260929-framework-core-test02 budget=initial+/-0.01");
        for(;;){
            Object socket=LumaFrameworkProbe.invoke(server,"accept",new Class<?>[0]);
            try {
                Object peer=LumaFrameworkProbe.invoke(socket,"getPeerCredentials",new Class<?>[0]);
                if(((Integer)LumaFrameworkProbe.invoke(peer,"getUid",new Class<?>[0]))!=0)throw new SecurityException("root peer required");
                LumaFrameworkProbe.invoke(socket,"setSoTimeout",new Class<?>[]{int.class},500);
                InputStream input=(InputStream)LumaFrameworkProbe.invoke(socket,"getInputStream",new Class<?>[0]);
                StringBuilder text=new StringBuilder();int c;
                while((c=input.read())!=-1&&c!='\n'){if(text.length()>=120)throw new IOException("RPC too long");text.append((char)c);}
                OutputStream output=(OutputStream)LumaFrameworkProbe.invoke(socket,"getOutputStream",new Class<?>[0]);
                String response;
                try{response=broker.command(text.toString());}catch(Exception invalid){response="ERR\n";System.err.println("RPC_REJECT "+invalid);}
                output.write(response.getBytes("US-ASCII"));output.flush();
            }catch(Exception failure){System.err.println("RPC_ERROR "+failure);}
            finally{LumaFrameworkProbe.invoke(socket,"close",new Class<?>[0]);}
        }
    }
}
