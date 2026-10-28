package top.rongshangs.lumacurve.refactor;

/** Deterministic, output-free demonstration. Finger movement never changes the actual device. */
final class ControlCenterDemoState {
    static final long CYCLE_MS=11000;
    static final class Frame {
        final int phase;final double progress,finger;final boolean touching;
        Frame(int phase,double progress,double finger,boolean touching){this.phase=phase;this.progress=progress;this.finger=finger;this.touching=touching;}
    }
    static double ease(double value){double t=Math.max(0,Math.min(1,value));return t*t*(3-2*t);}
    static Frame at(long elapsed,boolean animate){
        if(!animate)return new Frame(1,.75,.75,false);
        long t=Math.floorMod(elapsed,CYCLE_MS);
        if(t<2000){double p=.12+.63*ease(t/2000.);return new Frame(0,p,p,true);}
        if(t<3300)return new Frame(1,.75,.75+.17*ease((t-2000)/1300.),true);
        if(t<4100)return new Frame(1,.75,.75,false);
        if(t<5700){double p=.75+.25*ease((t-4100)/1600.);return new Frame(2,p,p,true);}
        if(t<6700)return new Frame(2,1,1,false);
        if(t<9000){double p=1-.82*ease((t-6700)/2300.);return new Frame(3,p,p,true);}
        double p=.18+.77*ease((t-9000)/2000.);return new Frame(4,p,p,false);
    }
}
