/** Device-pinned normal-range bridge. Inputs are the existing physical backlight codes. */
public final class LumaLegacyBacklightCoordinate {
    private LumaLegacyBacklightCoordinate(){}
    // Median settled node / adjustedBrightness from three HyperOS 4 sessions.
    // This is an engineering estimate, not an optical calibration or HBM mapping.
    public static final float PANEL_CODES_PER_FLOAT=17848f;
    public static float toFramework(int raw,int maximum) {
        if(maximum!=16383||raw<0||raw>maximum)throw new IllegalArgumentException("unsupported backlight code");
        return raw/PANEL_CODES_PER_FLOAT;
    }
    public static boolean plausibleAnchor(int node,float adjusted) {
        if(node<1||node>16383||!LumaFrameworkOutputSession.finite(adjusted)||adjusted<=0)return false;
        float ratio=node/adjusted;
        // The panel is nonlinear near minimum brightness. Check only a broad
        // sanity envelope there; the display ID, mode and live feedback are
        // verified independently before and during ownership.
        if(adjusted<.05f)return ratio>=5000f&&ratio<=80000f;
        // Only the low range was measured closely. A wide high-range sanity
        // envelope avoids rejecting a valid nonlinear panel response.
        if(adjusted>.10f)return ratio>=8000f&&ratio<=30000f;
        float expected=adjusted*PANEL_CODES_PER_FLOAT;
        return Math.abs(node-expected)<=Math.max(80f,expected*.07f);
    }
}
