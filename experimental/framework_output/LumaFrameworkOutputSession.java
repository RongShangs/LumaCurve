/** Typed experimental framework-output boundary. Default installed engine is unchanged.
 * Caller must tick with monotonic time and run an independent crash watchdog.
 */
public final class LumaFrameworkOutputSession {
    public enum Unit { FRAMEWORK_FLOAT, LEGACY_RAW_PERCENT, NITS, PERCEPTUAL_PERCENT }
    public enum Mode { AUTO, MANUAL, UNKNOWN }
    public enum State { IDLE, OWNED, RELEASE_PENDING }
    public static final class Snapshot {
        public final long sampledAtMs;
        public final Mode mode;
        public final boolean on,windowOverride;
        public final float baseBrightness,adjustedBrightness,min,max;
        public final int node;
        public Snapshot(long time,Mode mode,boolean on,boolean window,float base,float adjusted,float min,float max,int node) {
            this.sampledAtMs=time;this.mode=mode;this.on=on;this.windowOverride=window;
            this.baseBrightness=base;this.adjustedBrightness=adjusted;this.min=min;this.max=max;this.node=node;
        }
    }
    public interface Bridge {
        Snapshot read() throws Exception;
        void temporary(float value) throws Exception;
        boolean temporaryCleared() throws Exception;
    }
    public static final class Request {
        public final long sequence,sentAtMs;
        public final float algorithmGoal,limitedGoal;
        public final Snapshot feedbackBeforeRequest;
        public Request(long sequence,long time,float desired,float limited,Snapshot before) {
            this.sequence=sequence;this.sentAtMs=time;this.algorithmGoal=desired;this.limitedGoal=limited;this.feedbackBeforeRequest=before;
        }
    }
    final Bridge bridge;
    private State state=State.IDLE;
    private long deadline,lastSequence=-1,lastClock=-1;
    private boolean applied;
    private float appliedValue;
    private long binderWrites,unchangedRequests;
    public LumaFrameworkOutputSession(Bridge bridge){this.bridge=bridge;}
    public State state(){return state;}
    public long binderWrites(){return binderWrites;}
    public long unchangedRequests(){return unchangedRequests;}
    static boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);}
    void clock(long now) {
        if(now<0||now<lastClock)throw new IllegalArgumentException("monotonic clock required");lastClock=now;
    }
    static boolean usable(Snapshot s,long now) {
        return s!=null&&s.mode==Mode.AUTO&&s.on&&!s.windowOverride&&s.sampledAtMs<=now&&now-s.sampledAtMs<=2000&&
            finite(s.min)&&finite(s.max)&&s.min>=0&&s.max<=1&&s.min<s.max&&finite(s.adjustedBrightness);
    }
    public Request submit(long sequence,long now,Unit unit,float desired,long leaseMs)throws Exception {
        clock(now);
        if(unit!=Unit.FRAMEWORK_FLOAT||!finite(desired)||desired<0||desired>1||sequence<=lastSequence||leaseMs<100||leaseMs>3000)
            throw new IllegalArgumentException("invalid goal, unit, sequence or lease");
        if(state==State.RELEASE_PENDING)throw new IllegalStateException("release must be confirmed before reacquiring");
        Snapshot snapshot;
        try{snapshot=bridge.read();}catch(Exception failure){releaseAfterFailure(failure);throw failure;}
        if(!usable(snapshot,now)){release();throw new IllegalStateException("mode/power/override/feedback forbids control");}
        float limited=Math.max(snapshot.min,Math.min(snapshot.max,desired));
        boolean unchanged=state==State.OWNED&&applied&&Float.floatToIntBits(appliedValue)==Float.floatToIntBits(limited)&&
            Math.abs(snapshot.adjustedBrightness-appliedValue)<=.000001f;
        // A Binder failure may happen after mutation: claim responsibility before calling.
        state=State.OWNED;deadline=now+leaseMs;
        if(unchanged)unchangedRequests++;
        else {
            try{bridge.temporary(limited);}catch(Exception failure){releaseAfterFailure(failure);throw failure;}
            appliedValue=limited;applied=true;binderWrites++;
        }
        lastSequence=sequence;
        return new Request(sequence,now,desired,limited,snapshot);
    }
    void releaseAfterFailure(Exception original) {
        try{release();}catch(Exception releaseError){original.addSuppressed(releaseError);}
    }
    public void tick(long now)throws Exception {
        clock(now);
        if(state==State.RELEASE_PENDING||state==State.OWNED&&now>=deadline){release();return;}
        if(state==State.OWNED) {
            Snapshot s;
            try{s=bridge.read();}catch(Exception failure){releaseAfterFailure(failure);throw failure;}
            if(!usable(s,now))release();
        }
    }
    public void release()throws Exception {
        if(state==State.IDLE)return;
        applied=false;
        state=State.RELEASE_PENDING;
        bridge.temporary(Float.NaN);
        if(!bridge.temporaryCleared())throw new IllegalStateException("temporary release not confirmed");
        state=State.IDLE;
    }
}
