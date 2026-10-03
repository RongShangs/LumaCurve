package top.rongshangs.lumacurve.refactor;
/** Selection follows the active controller flag, not class presence or a model list. */
public final class BackendSelection {
    public static String choose(Boolean usesRefactor,boolean refactorReady,boolean mapperReady){
        if(Boolean.TRUE.equals(usesRefactor))return refactorReady?"refactor":"waiting";
        if(Boolean.FALSE.equals(usesRefactor))return mapperReady?"physical_mapping":"waiting";
        return refactorReady?"refactor":mapperReady?"physical_mapping":"waiting";
    }
}
