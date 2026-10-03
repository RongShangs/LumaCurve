package top.rongshangs.lumacurve.refactor;

import java.util.*;

/** Bounded records of one calculation, separate from later display output calls. */
public final class PipelineHistory {
    public static final int CAPACITY=24;
    public static final class Frame {
        final long sequence,uptime,unixMs;
        String route="unobserved";
        float lux=Float.NaN,main=Float.NaN,assist=Float.NaN,mapped=Float.NaN,
            curve=Float.NaN,sceneIn=Float.NaN,sceneOut=Float.NaN,
            overrideIn=Float.NaN,overrideOut=Float.NaN,outdoorIn=Float.NaN,outdoorOut=Float.NaN,finalTarget=Float.NaN;
        Boolean nightWake,shortTermMemory;
        int sensor=-1;
        String sensorName;
        boolean failed;
        Frame(long sequence,long uptime,long unixMs){this.sequence=sequence;this.uptime=uptime;this.unixMs=unixMs;}
        public static boolean changed(float a,float b){return Float.isFinite(a)&&Float.isFinite(b)&&Math.abs(a-b)>0.000001f;}
    }
    private long sequence;
    private final ArrayDeque<Frame> frames=new ArrayDeque<>();
    public Frame begin(long uptime,long unixMs){return new Frame(++sequence,uptime,unixMs);}
    public void finish(Frame frame){if(frame==null)return;if(frames.size()>=CAPACITY)frames.removeFirst();frames.addLast(frame);}
    public Frame last(){return frames.peekLast();}
    public List<Frame> snapshot(){return new ArrayList<>(frames);}
}
