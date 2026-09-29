public final class LumaFrameworkFrameLivenessTest {
    static void yes(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] ignored){
        LumaFrameworkFrameLiveness l=new LumaFrameworkFrameLiveness();
        l.acquire();yes(!l.expired(LumaFrameworkOutputSession.State.IDLE));
        l.submit();yes(!l.expired(LumaFrameworkOutputSession.State.OWNED));
        yes(l.expired(LumaFrameworkOutputSession.State.IDLE));
        l.release();yes(!l.expired(LumaFrameworkOutputSession.State.IDLE));
        l.acquire();yes(!l.expired(LumaFrameworkOutputSession.State.IDLE));
        System.out.println("frame liveness: 5 cases PASS");
    }
}
