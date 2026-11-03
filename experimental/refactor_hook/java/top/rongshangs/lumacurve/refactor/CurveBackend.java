package top.rongshangs.lumacurve.refactor;

/** Native ABI boundaries expose capabilities; shared managers never select concrete backends. */
public abstract class CurveBackend {
    public float[] factoryLux,factoryNit;
    public final float min,max;
    public SavedCurve savedBase;
    protected CurveBackend(float min,float max){this.min=min;this.max=max;}
    public abstract Object get(String name)throws Exception;
    public float number(String name)throws Exception{return ((Number)get(name)).floatValue();}
    public int integer(String name)throws Exception{return ((Number)get(name)).intValue();}
    public abstract CurvePlan plan();
    public abstract void configure(float[] factors)throws Exception;
    public void configure(float[] factors,float floor)throws Exception{if(floor!=0)throw new IllegalArgumentException("此曲线路径不支持亮度下限");configure(factors);}
    public void configure(float[] factors,float floor,SavedCurve saved)throws Exception{SavedCurve old=savedBase;savedBase=saved;try{configure(factors,floor);}catch(Exception failure){savedBase=old;throw failure;}}
    protected CurvePlan proposed(float[] factors,float floor){return SavedCurve.plan(savedBase,factoryLux,factoryNit,min,max,factors,floor);}
    public float[] baselineLux(){return plan()==null?fullLux():plan().lux();}
    public float[] baselineNit(){return plan()==null?fullNit():plan().nits();}
    public abstract void clearMemory()throws Exception;
    public abstract float currentAt(float lux)throws Exception;
    public float memoryAt(float lux)throws Exception{return currentAt(lux);}
    public abstract String name();
    public String compatibilityRoute(){return name();}
    public Object target(){throw new IllegalStateException("此后端没有 Refactor 对象");}
    public void afterReset()throws Exception{}
    public float currentInputLux(float lux){return lux;}
    public boolean reconcile()throws Exception{return false;}
    public void putVerification(org.json.JSONObject status)throws Exception{}
    public void putCurveState(org.json.JSONObject status)throws Exception{}
    public boolean savedCurveSupported(){return false;}
    public String coordinate(){return "unknown";}
    public String legacyConfigBackend(){return "";}
    public boolean usesRuntimeUserIdentity(){return false;}
    public String interpolation(){return "unknown";}
    public boolean strictOrder(){return false;}
    public int savedPointLimit(){return 0;}
    public String currentPipelineRoute(){return "";}
    public boolean nativeMappingHooks(){return false;}
    public void installMappingHooks(Class<?> type)throws Exception{}
    public float mappingOutputNit(float brightness)throws Exception{return Float.NaN;}
    public boolean memoryContextActive()throws Exception{return true;}
    public boolean memoryRestoreIncludesModel(){return false;}
    public Object memoryLifecycleTarget(){return null;}
    public String memoryDescription()throws Exception{return "手动记忆能力尚未确认";}
    public org.json.JSONObject curveContract(org.json.JSONObject status)throws Exception{
        return CurveCompatibility.descriptor(coordinate(),interpolation(),memoryDescription(),strictOrder(),memoryContextActive()&&CurveCompatibility.normalPipeline(status,currentPipelineRoute()),savedCurveSupported(),savedPointLimit(),Math.max(0,status.optInt("memory_live_count")));
    }
    public boolean memoryHandoverSupported(){return true;}
    public boolean persistentMemorySupported(){return false;}
    /** Observable native learning capacity; independent of archive replay support. */
    public int manualPointCapacity(){return 0;}
    public org.json.JSONArray manualPoints()throws Exception{return new org.json.JSONArray();}
    public void restoreManualPoints(org.json.JSONArray points)throws Exception{throw new IllegalStateException("此设备的手动记忆恢复接口尚未兼容");}
    public void replaceManualPoints(org.json.JSONArray points)throws Exception{clearMemory();restoreManualPoints(points);}
    public float manualDisplayValue(float value)throws Exception{return value;}
    public float[] fullLux(){return factoryLux.clone();}
    public float[] fullNit(){return factoryNit.clone();}
    public float[] currentLux()throws Exception{throw new IllegalStateException("当前曲线读取接口尚未兼容");}
    public float[] currentNit()throws Exception{throw new IllegalStateException("当前曲线读取接口尚未兼容");}
}
