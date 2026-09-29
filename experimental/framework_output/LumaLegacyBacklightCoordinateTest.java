public final class LumaLegacyBacklightCoordinateTest {
    static void check(boolean yes){if(!yes)throw new AssertionError();}
    public static void main(String[] args) {
        float low=LumaLegacyBacklightCoordinate.toFramework(1049,16383);
        float mid=LumaLegacyBacklightCoordinate.toFramework(6202,16383);
        check(low>.058f&&low<.06f);
        check(mid>.347f&&mid<.349f);
        check(LumaLegacyBacklightCoordinate.toFramework(15563,16383)>.37486267f);
        check(LumaLegacyBacklightCoordinate.plausibleAnchor(1049,.0586904f));
        check(!LumaLegacyBacklightCoordinate.plausibleAnchor(1049,.2f));
        check(LumaLegacyBacklightCoordinate.plausibleAnchor(5000,.3f));
        check(!LumaLegacyBacklightCoordinate.plausibleAnchor(16000,.3f));
        check(!LumaLegacyBacklightCoordinate.plausibleAnchor(0,.0586904f));
        check(!LumaLegacyBacklightCoordinate.plausibleAnchor(1049,Float.NaN));
        boolean invalid=false;try{LumaLegacyBacklightCoordinate.toFramework(6202,4095);}catch(IllegalArgumentException expected){invalid=true;}check(invalid);
        System.out.println("legacy raw-to-framework mapping: 10 cases PASS; normal range only");
    }
}
