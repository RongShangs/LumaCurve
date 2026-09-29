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
        if(node<100||!LumaFrameworkOutputSession.finite(adjusted)||adjusted<=.005f)return false;
        float expected=adjusted*PANEL_CODES_PER_FLOAT;
        return Math.abs(node-expected)<=Math.max(80f,expected*.07f);
    }
}
