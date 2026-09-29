public final class LumaFrameworkOutputSessionTest {
    static class Fake implements LumaFrameworkOutputSession.Bridge {
        LumaFrameworkOutputSession.Mode mode=LumaFrameworkOutputSession.Mode.AUTO;
        boolean on=true,override,failSet,failRelease,failRead,clearVisible=true;long stamp=1000;float last,adjusted=.2f;int writes,releases;
        public LumaFrameworkOutputSession.Snapshot read()throws Exception{
            if(failRead)throw new Exception("read");
            return new LumaFrameworkOutputSession.Snapshot(stamp,mode,on,override,.1f,adjusted,.00036f,.374f,1753);
        }
        public void temporary(float v)throws Exception {
            if(Float.isNaN(v)){releases++;if(failRelease)throw new Exception("release");}
            else{writes++;if(failSet)throw new Exception("set");last=adjusted=v;}
        }
        public boolean temporaryCleared(){return clearVisible;}
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        for(int c=0;c<12;c++){
            Fake f=new Fake();LumaFrameworkOutputSession s=new LumaFrameworkOutputSession(f);
            if(c==2)f.mode=LumaFrameworkOutputSession.Mode.UNKNOWN;
            if(c==3)f.mode=LumaFrameworkOutputSession.Mode.MANUAL;
            if(c==4)f.on=false;if(c==5)f.override=true;if(c==6)f.stamp=0;
            if(c==7)f.failSet=true;
            LumaFrameworkOutputSession.Unit unit=c==8?LumaFrameworkOutputSession.Unit.LEGACY_RAW_PERCENT:LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT;
            boolean error=false;
            try {
                long now=c==6?4000:1000;
                LumaFrameworkOutputSession.Request r=s.submit(1,now,unit,c==1?.9f:.15f,1000);
                check(r.feedbackBeforeRequest.node==1753&&r.feedbackBeforeRequest.baseBrightness==.1f&&r.feedbackBeforeRequest.adjustedBrightness==.2f);
                check(r.limitedGoal==(c==1?.374f:.15f));
            }catch(Exception e){error=true;}
            check(error==(c>=2&&c<=8));
            if(c==0||c==1){s.tick(2000);check(f.releases==1&&s.state()==LumaFrameworkOutputSession.State.IDLE);}
            if(c==7)check(f.releases==1);
            if(c==8)check(f.writes==0);
            if(c==9){f.mode=LumaFrameworkOutputSession.Mode.MANUAL;s.tick(1100);check(f.releases==1);}
            if(c==10){
                f.failRelease=true;try{s.release();}catch(Exception expected){}
                check(s.state()==LumaFrameworkOutputSession.State.RELEASE_PENDING);
                f.failRelease=false;s.tick(2000);check(s.state()==LumaFrameworkOutputSession.State.IDLE&&f.releases==2);
            }
            if(c==11){
                f.clearVisible=false;try{s.release();}catch(Exception expected){}
                check(s.state()==LumaFrameworkOutputSession.State.RELEASE_PENDING);
                boolean rejected=false;try{s.submit(2,1100,unit,.16f,1000);}catch(Exception expected){rejected=true;}
                check(rejected&&f.writes==1);
                f.clearVisible=true;s.tick(2000);check(s.state()==LumaFrameworkOutputSession.State.IDLE);
            }
        }
        {
            Fake f=new Fake();LumaFrameworkOutputSession s=new LumaFrameworkOutputSession(f);
            s.submit(1,1000,LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,.15f,1000);
            f.stamp=1900;s.submit(2,1900,LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,.15f,1000);
            check(f.writes==1&&s.binderWrites()==1&&s.unchangedRequests()==1);
            s.tick(2000);check(f.releases==0);s.tick(2900);check(f.releases==1);
            f.stamp=3000;s.submit(3,3000,LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,.15f,1000);
            check(f.writes==2);
        }
        {
            Fake f=new Fake();LumaFrameworkOutputSession s=new LumaFrameworkOutputSession(f);
            s.submit(1,1000,LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,.15f,1000);
            f.override=true;
            boolean rejected=false;try{s.submit(2,1100,LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,.15f,1000);}catch(Exception expected){rejected=true;}
            check(rejected&&f.writes==1&&f.releases==1);
        }
        {
            Fake f=new Fake();LumaFrameworkOutputSession s=new LumaFrameworkOutputSession(f);
            s.submit(1,1000,LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,.15f,1000);
            s.submit(2,1100,LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,.16f,1000);
            check(f.writes==2&&s.unchangedRequests()==0);
        }
        {
            Fake f=new Fake();LumaFrameworkOutputSession s=new LumaFrameworkOutputSession(f);
            s.submit(1,1000,LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,.15f,1000);
            f.adjusted=.12f;
            s.submit(2,1100,LumaFrameworkOutputSession.Unit.FRAMEWORK_FLOAT,.15f,1000);
            check(f.writes==2&&f.adjusted==.15f);
        }
        System.out.println("Typed framework output session: 16 cases PASS; isolated experimental backend");
    }
}
