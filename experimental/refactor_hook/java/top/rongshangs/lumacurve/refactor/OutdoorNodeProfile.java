package top.rongshangs.lumacurve.refactor;

/** Capability classification, independent of model names and firmware version strings. */
final class OutdoorNodeProfile {
    static boolean matches(String backend,double maxNit,int maximum,boolean output,boolean range){
        return ("physical_mapping".equals(backend)||"refactor".equals(backend))&&Double.isFinite(maxNit)&&maxNit>0&&maximum>10&&maximum<=65535&&output&&range;
    }
}
