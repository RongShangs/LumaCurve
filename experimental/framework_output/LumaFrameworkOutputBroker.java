import java.io.*;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Firmware-pinned root broker. Never changes persistent brightness Settings. */
public final class LumaFrameworkOutputBroker implements LumaFrameworkOutputSession.Bridge {
    final LumaFrameworkProbe.AndroidBridge android=new LumaFrameworkProbe.AndroidBridge();
    final File run;
    final PrintWriter trace;
    final PrintWriter events;
    final boolean production="1".equals(System.getenv("LUMA_FRAMEWORK_PRODUCTION"));
    final LumaFrameworkOutputSession session=new LumaFrameworkOutputSession(this);
    final LumaFrameworkOutputLease client=new LumaFrameworkOutputLease();
    final LumaFrameworkOutputFeedback feedback=new LumaFrameworkOutputFeedback();
    final LumaFrameworkSliderOverride sliderOverride=new LumaFrameworkSliderOverride();
    final LumaFrameworkFrameLiveness frameLiveness=new LumaFrameworkFrameLiveness();
    LumaFrameworkOutputSession.Snapshot snapshot;
    LumaFrameworkOutputRamp ramp;
    boolean acquired,hasGoal,outputHealthy=true;
    long settingsStamp,sequence,lastClientSequence=-1;
    long frameTicks,providerReads,displayReads,heartbeats;
    int mode,slider;
    float adjustment,initial,goal=-1,rawGoal=-1,limited=-1,request;
    static long now(){return System.nanoTime()/1000000;}
    LumaFrameworkOutputBroker(File folder)throws Exception {
        run=folder;trace=new PrintWriter(new File(production?"/dev/null":new File(folder,"framework.trace").getPath()),"UTF-8");
        events=production?new PrintWriter(System.out,true):new PrintWriter(new File(folder,"framework.events"),"UTF-8");
        refresh();initial=snapshot.adjustedBrightness;request=initial;
        if((!production&&!LumaFrameworkOutputSession.usable(snapshot,now()))||
           !LumaFrameworkOutputSession.finite(initial)||initial<0||initial>1)
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
        if(!production){trace.println("time_ms,mode,owned,algorithm_goal,limited_goal,request,base,adjusted,node,min,max");trace.flush();}
    }
    void refresh()throws Exception {
        long t=now();
        if(settingsStamp==0||t-settingsStamp>=500){
            String[] values=LumaFrameworkProbeSettings.readSettings(true);
            providerReads++;
            int nextMode=Integer.parseInt(values[0]);
            float nextAdjustment=Float.parseFloat(values[1]);
            if(production&&settingsStamp!=0&&(acquired||sliderOverride.pending())&&
               sliderOverride.observe(t,nextMode,adjustment,nextAdjustment,rawGoal)){
                event("USER_SLIDER,adjustment="+nextAdjustment+",system_handoff=2500ms");
                if(acquired)release();
            }
            mode=nextMode;adjustment=nextAdjustment;slider=Integer.parseInt(values[2]);settingsStamp=now();
        }
        Object display=LumaFrameworkProbe.invoke(android.global,"getDisplayInfo",new Class<?>[]{int.class},0);
        displayReads++;
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
    synchronized int nextTickDelayMs() {
        return LumaFrameworkOutputCadence.nextDelay(acquired,hasGoal,request,limited,snapshot.adjustedBrightness);
    }
    void release()throws Exception {
        boolean responsible=acquired||session.state()!=LumaFrameworkOutputSession.State.IDLE;
        acquired=false;hasGoal=false;goal=limited=-1;
        frameLiveness.release();
        feedback.reset();
        if(responsible)event("RELEASE_BEGIN,mode="+mode);
        session.release();
        if(responsible)event("RELEASE_CONFIRMED,mode="+mode);
        if(responsible)event("OUTPUT_COUNTS,binder_writes="+session.binderWrites()+",unchanged_requests="+session.unchangedRequests()+
            ",frame_ticks="+frameTicks+",provider_reads="+providerReads+",display_reads="+displayReads+",heartbeats="+heartbeats);
    }
    synchronized void tick() {
        try {
            frameTicks++;
            refresh();long t=now();
            if(acquired&&(!client.alive(t)||!LumaFrameworkOutputSession.usable(snapshot,t))){
                event("RELEASE_CAUSE,"+(!client.alive(t)?"client_lease":"control_conditions"));release();
            }
            session.tick(t);
            if(acquired&&frameLiveness.expired(session.state())){
                event("RELEASE_CAUSE,frame_lease");acquired=false;hasGoal=false;frameLiveness.release();goal=limited=-1;
            }
            if(acquired&&hasGoal){
                // Each new ownership session stays near its real initial feedback.
                limited=production?Math.max(snapshot.min,Math.min(snapshot.max,goal)):
                    Math.max(snapshot.min,Math.min(snapshot.max,Math.max(initial-.01f,Math.min(initial+.01f,goal))));
                request=ramp.next(limited,snapshot.min,snapshot.max,now());
                session.submit(++sequence,now(),LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,request,3000);
                frameLiveness.submit();
                if(production&&feedback.failed(now(),request,limited,snapshot.adjustedBrightness,snapshot.node)){
                    outputHealthy=false;event("OUTPUT_FAULT,physical_readback_mismatch");release();
                }
            }
            if(!production){trace.printf(Locale.ROOT,"%d,%d,%d,%.7f,%.7f,%.7f,%.7f,%.7f,%d,%.7f,%.7f%n",now(),mode,acquired?1:0,goal,limited,request,
                snapshot.baseBrightness,snapshot.adjustedBrightness,snapshot.node,snapshot.min,snapshot.max);trace.flush();}
        }catch(Exception error){
            System.err.println("BROKER_GUARD "+error);settingsStamp=0;
            try{release();}catch(Exception failure){System.err.println("RELEASE_PENDING "+failure);}
        }
    }
    synchronized String command(String line)throws Exception {
        if(line==null||line.length()>120)throw new IllegalArgumentException("invalid RPC");
        String[] words=line.trim().split(" +");
        // Heartbeat only extends the independent client lease. The frame loop checks
        // current mode, screen and feedback even when there is no new target.
        if(words.length==1&&words[0].equals("P")) {
            long t=now();if(!acquired||!LumaFrameworkOutputSession.usable(snapshot,t))throw new IllegalStateException("heartbeat without ownership");
            client.renew(t);
            heartbeats++;
            return response();
        }
        refresh();long t=now();
        switch(words[0]) {
        case "Q":if(words.length!=1)throw new IllegalArgumentException();break;
        case "A":
            if(words.length!=1||!LumaFrameworkOutputSession.usable(snapshot,t)||session.state()==LumaFrameworkOutputSession.State.RELEASE_PENDING)
                throw new IllegalStateException("control forbidden");
            if(production&&sliderOverride.waiting(t))throw new IllegalStateException("user slider owns brightness");
            if(!outputHealthy)throw new IllegalStateException("physical feedback fault; restart required");
            if(production&&!LumaLegacyBacklightCoordinate.plausibleAnchor(snapshot.node,snapshot.adjustedBrightness))
                throw new IllegalStateException("backlight coordinate calibration unavailable");
            if(!acquired){
                if(production&&sliderOverride.pending())event("USER_SLIDER_BIAS,factor="+sliderOverride.settle(t,snapshot.adjustedBrightness));
                initial=snapshot.adjustedBrightness;ramp.reset(initial,t);request=initial;
                goal=limited=-1;frameLiveness.acquire();event("ACQUIRE,anchor="+initial);
            }
            acquired=true;client.renew(t);ramp.stamp=t;break;
        case "T":
            if(words.length!=4||!acquired||!LumaFrameworkOutputSession.usable(snapshot,t))throw new IllegalStateException("goal without ownership");
            long seq=Long.parseLong(words[1]);int raw=Integer.parseInt(words[2]),max=Integer.parseInt(words[3]);
            if(seq<=lastClientSequence||max!=16383||raw<0||raw>max)throw new IllegalArgumentException("invalid sequence or raw goal");
            lastClientSequence=seq;
            rawGoal=production?LumaLegacyBacklightCoordinate.toFramework(raw,max):raw/(float)max;
            float desired=production?Math.max(0f,Math.min(1f,sliderOverride.apply(rawGoal))):rawGoal;
            if(!hasGoal||goal!=desired)event((production?"GOAL,framework_target=":"GOAL,legacy_fraction=")+desired);
            goal=desired;hasGoal=true;client.renew(t);break;
        case "P":if(words.length!=1)throw new IllegalArgumentException();client.renew(t);break;
        case "R":if(words.length!=1)throw new IllegalArgumentException();release();break;
        default:throw new IllegalArgumentException("unknown RPC");
        }
        if(acquired&&!LumaFrameworkOutputSession.usable(snapshot,now()))release();
        return response();
    }
    String response() {
        return String.format(Locale.ROOT,"OK %d %.7f %d %d %d %d %.7f %.7f %.7f %.7f %.7f %.7f %.7f %d\n",mode,adjustment,slider,
            snapshot.on?1:0,snapshot.windowOverride?1:0,snapshot.node,snapshot.min,snapshot.max,snapshot.baseBrightness,snapshot.adjustedBrightness,goal,limited,request,acquired?1:0);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2||!args[0].matches("[A-Za-z0-9._-]{1,90}")||!args[1].startsWith("/data/local/tmp/luma-framework-core.")||args[1].contains(".."))
            throw new IllegalArgumentException("isolated socket and runtime required");
        LumaFrameworkOutputBroker broker=new LumaFrameworkOutputBroker(new File(args[1]));
        Object server=Class.forName("android.net.LocalServerSocket").getConstructor(String.class).newInstance(args[0]);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{synchronized(broker){try{broker.release();}catch(Exception e){System.err.println(e);}}}));
        Thread frames=new Thread(()->{for(;;){broker.tick();try{Thread.sleep(broker.nextTickDelayMs());}catch(InterruptedException e){return;}}},"Luma-framework-frames");
        frames.setDaemon(true);frames.start();
        new File(broker.run,"broker-ready").createNewFile();
        System.out.println(broker.production?"BROKER_READY build=20260930-framework-lean01 output=normal_range_ramp":
            "BROKER_READY build=20260930-framework-core-test04 budget=acquisition+/-0.01");
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
