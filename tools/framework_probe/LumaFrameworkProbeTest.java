import java.util.*;
public final class LumaFrameworkProbeTest {
    static final class Fake implements LumaFrameworkProbe.Bridge {
        String mode="1",initialMode="1"; float value=.12f,initial=.12f,min=.001f,max=.37486267f;
        int writes,samples,modeWrites; String error=""; List<Float> targets=new ArrayList<>();
        public String mode() { return error.equals("mode_readback")&&modeWrites==1?"1":mode; }
        public float brightness() { return value; }
        public float minimum() { return min; }
        public float maximum() { return max; }
        public void mode(String v) { mode=v;modeWrites++; }
        public void brightness(float v) throws Exception {
            writes++; targets.add(v);
            if(error.equals("write")&&writes==2)throw new Exception("injected write failure");
            value=v;
        }
        public void sample(String phase,float target) throws Exception {
            if(!mode.equals("0"))throw new AssertionError("system automatic mode during test");
            samples++;if(error.equals("sample")&&samples==5)throw new Exception("injected sample failure");
        }
        public void delay(long ignored) {}
    }
    static void check(boolean test) { if(!test)throw new AssertionError(); }
    public static void main(String[] args)throws Exception {
        int count=0;
        for(String name:new String[]{"auto","manual","near_min","near_max","invalid_mode","nan","invalid_range","mode_readback","write","sample"}) {
            Fake f=new Fake();
            if(name.equals("manual"))f.initialMode=f.mode="0";
            if(name.equals("near_min"))f.initial=f.value=.005f;
            if(name.equals("near_max"))f.initial=f.value=f.max-.005f;
            if(name.equals("invalid_mode"))f.mode="null";
            if(name.equals("nan"))f.value=Float.NaN;
            if(name.equals("invalid_range"))f.max=0;
            f.error=name;
            boolean failed=false;try{LumaFrameworkProbe.control(f);}catch(Exception e){failed=true;}
            boolean good=Arrays.asList("auto","manual","near_min","near_max").contains(name);
            check(good!=failed);
            if(Arrays.asList("invalid_mode","nan","invalid_range").contains(name))check(f.writes==0&&f.modeWrites==0);
            else {check(f.mode.equals(f.initialMode));check(f.value==f.initial);}
            if(good) {
                check(f.samples==120);
                for(float v:f.targets)check(v>=f.min&&v<=f.max&&Math.abs(v-f.initial)<.020001f);
            }
            count++;
        }
        System.out.println("Framework control host fixture: "+count+" cases PASS; not Android device validation");
    }
}
