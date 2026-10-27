package top.rongshangs.lumacurve.refactor.hooking;

import java.util.*;

/** One atomic installation unit. Publish capability only after every required hook exists.
 * An optional unit owns its rollback independently from other installed features.
 */
public final class HookTransaction implements AutoCloseable {
    private final List<XC_MethodHook.Unhook> handles=new ArrayList<>();
    private boolean finished;
    public void add(XC_MethodHook.Unhook handle){
        if(finished)throw new IllegalStateException("Hook transaction already finished");
        handles.add(Objects.requireNonNull(handle));
    }
    public void addAll(Collection<XC_MethodHook.Unhook> added){for(XC_MethodHook.Unhook handle:added)add(handle);}
    public void commit(){finished=true;handles.clear();}
    public void commitTo(Collection<XC_MethodHook.Unhook> owner){owner.addAll(handles);commit();}
    @Override public void close(){
        if(finished)return;finished=true;
        rollbackAll(handles);handles.clear();
    }
    /** Continue in reverse order even if one backend refuses removal. Failed handles remain retryable. */
    public static void rollbackAll(Collection<XC_MethodHook.Unhook> owned){
        List<XC_MethodHook.Unhook> reverse=new ArrayList<>(owned);
        for(int i=reverse.size()-1;i>=0;i--)try{reverse.get(i).unhook();}
        catch(Throwable failure){try{XposedBridge.log("HyperLux hook rollback failed: "+failure);}catch(Throwable loggingFailure){/* Cleanup must continue even if logging fails. */}}
    }

}
