/** Detect a persistent physical-node mismatch after the framework request settles. */
public final class LumaFrameworkOutputFeedback {
    private long stableSince=-1;
    private int mismatches;
    public void reset(){stableSince=-1;mismatches=0;}
    public boolean failed(long now,float request,float goal,float adjusted,int node){
        if(!LumaFrameworkOutputSession.finite(request)||!LumaFrameworkOutputSession.finite(goal)||
           !LumaFrameworkOutputSession.finite(adjusted)||node<0||
           Math.abs(request-goal)>.000001f||Math.abs(request-adjusted)>.000001f){reset();return false;}
        if(stableSince<0){stableSince=now;return false;}
        if(now<stableSince){reset();return false;}
        if(now-stableSince<3000)return false;
        float expected=request*LumaLegacyBacklightCoordinate.PANEL_CODES_PER_FLOAT;
        if(Math.abs(node-expected)>Math.max(120f,expected*.10f))mismatches++;
        else mismatches=0;
        return mismatches>=3;
    }
}
