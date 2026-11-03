package top.rongshangs.lumacurve.refactor;

/** Active native object -> one validated backend. Construction failure never selects another ABI. */
final class CurveBackendFactory {
    static CurveBackend create(Object owner,Object source)throws Exception{
        if(source.getClass().getName().equals(HookEntry.REFACTOR)){
            Class<?> util=Class.forName("com.android.server.display.RefactorBrightnessUtil",true,source.getClass().getClassLoader());
            if(HookEntry.field(util,"MIN_NIT").getType()!=float.class||HookEntry.field(util,"MAX_NIT").getType()!=float.class)throw new IllegalStateException("逻辑亮度边界类型已变化");
            float min=((Number)HookEntry.field(util,"MIN_NIT").get(null)).floatValue(),max=((Number)HookEntry.field(util,"MAX_NIT").get(null)).floatValue();
            return RefactorProfiles.create(source,min,max);
        }
        Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");
        if(source.getClass().getName().equals(SimpleCurveAdapter.TYPE))return new SimpleCurveAdapter(owner,source,abc,()->HookEntry.currentUser.serial());
        return new TraditionalAdapter(owner,source,abc,()->HookEntry.currentUser.serial());
    }
}
