import java.util.*;
public final class LumaFrameworkProbeTemporaryTest {
    static final class Fake implements LumaFrameworkProbeTemporary.Bridge {
        float start=.09f,min=.00036f,max=.374f;int requests,samples,failRequest=-1,failSample=-1;boolean cleared;
        List<Float> values=new ArrayList<>();
        public float current(){return start;} public float minimum(){return min;} public float maximum(){return max;}
        public void request(float v)throws Exception{
            if(Float.isNaN(v)){cleared=true;return;}
            if(++requests==failRequest)throw new Exception("request failed");
            if(v<min||v>max)throw new AssertionError("out of range");values.add(v);
        }
        public void sample(String p,float v)throws Exception{if(++samples==failSample)throw new Exception("sample failed");}
        public void delay(){}
    }
    public static void main(String[] args)throws Exception{
        for(int c=0;c<8;c++){
            Fake f=new Fake();
            if(c==1)f.start=f.min;if(c==2)f.start=f.max;
            if(c==3)f.failRequest=1;if(c==4)f.failSample=1;
            if(c==5)f.start=Float.NaN;if(c==6)f.min=.5f;if(c==7)f.start=2;
            boolean error=false;
            try{LumaFrameworkProbeTemporary.run(f);}catch(Exception e){error=true;}
            if(error!=(c>=3))throw new AssertionError("case "+c);
            if(c<5&&!f.cleared)throw new AssertionError("not released "+c);
            if(c>=5&&(f.cleared||f.requests!=0))throw new AssertionError("invalid preflight wrote");
            if(c<3){
                if(f.samples!=210||f.requests!=91)throw new AssertionError("trace incomplete");
                for(int i=2;i<=30;i++)if(f.values.get(i)<f.values.get(i-1))throw new AssertionError("rise reversed");
                for(int i=32;i<=60;i++)if(f.values.get(i)>f.values.get(i-1))throw new AssertionError("fall reversed");
            }
        }
        System.out.println("Temporary framework controller: 8 cases PASS; device not verified");
    }
}
