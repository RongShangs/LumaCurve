public final class LumaFrameworkMappingFeedbackTest {
    static void require(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args) {
        LumaFrameworkMappingFeedback guard=new LumaFrameworkMappingFeedback();
        require(!guard.failed(0,.1f,.1f,.1f,false));
        require(!guard.failed(2999,.1f,.1f,.1f,false));
        require(guard.failed(3000,.1f,.1f,.1f,false));
        require(!guard.failed(3001,.1f,.1f,.1f,true));
        require(!guard.failed(6000,.1f,.1f,.1f,false));
        require(!guard.failed(9000,.11f,.2f,.11f,false));
        require(!guard.failed(12000,.1f,.1f,.1f,false));
        require(!guard.failed(15000,.1f,.1f,.15f,false));
        require(!guard.failed(17000,.1f,.1f,.1f,false));
        require(!guard.failed(16000,.1f,.1f,.1f,false));
        require(!guard.failed(18999,.1f,.1f,.1f,false));
        require(guard.failed(19000,.1f,.1f,.1f,false));
        guard.reset();require(!guard.failed(22000,.1f,.1f,.1f,false));
        require(!guard.failed(25000,Float.NaN,.1f,.1f,false));
        require(!guard.failed(28000,.1f,.1f,.1f,false));
        System.out.println("Mapping feedback: dwell boundary, valid mapping, ramp, unsettled feedback, clock and release PASS");
    }
}
