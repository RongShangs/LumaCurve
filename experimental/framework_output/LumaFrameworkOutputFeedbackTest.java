public final class LumaFrameworkOutputFeedbackTest {
    static void check(boolean v){if(!v)throw new AssertionError();}
    public static void main(String[] args){
        LumaFrameworkOutputFeedback f=new LumaFrameworkOutputFeedback();
        check(!f.failed(0,.2f,.2f,.2f,1000));
        check(!f.failed(1000,.2f,.2f,.2f,1000));
        check(!f.failed(3000,.2f,.2f,.2f,1000));
        check(!f.failed(3500,.2f,.2f,.2f,850));
        check(!f.failed(4000,.2f,.2f,.2f,800));
        check(f.failed(4500,.2f,.2f,.2f,790));
        f.reset();check(!f.failed(0,.2f,.2f,.2f,3569));
        check(!f.failed(5000,.2f,.2f,.2f,3569));
        check(!f.failed(5500,.2f,.2f,.1f,1000));
        check(!f.failed(9000,.2f,.2f,.2f,1000));
        System.out.println("physical node feedback guard: 10 cases PASS");
    }
}
