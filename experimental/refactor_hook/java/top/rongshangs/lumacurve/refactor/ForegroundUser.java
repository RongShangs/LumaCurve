package top.rongshangs.lumacurve.refactor;

import java.util.function.IntSupplier;

/** Unknown startup identity must be retryable, and must never count as a user match. */
public final class ForegroundUser {
    private volatile int serial=-1;
    private volatile String source="unresolved";
    public int serial(){return serial;}
    public String source(){return source;}
    public void refresh(IntSupplier reader){
        try{int value=reader.getAsInt();serial=value>=0?value:-1;source=value>=0?"activity_manager":"unavailable";}
        catch(RuntimeException unavailable){serial=-1;source="unavailable";}
    }
    public void switched(int value){serial=value>=0?value:-1;source=value>=0?"display_user_switch":"unavailable";}
    public static boolean matches(int bound,int current){return bound>=0&&current>=0&&bound==current;}
    public static boolean canApply(int foregroundUserId,int hookSerial){return foregroundUserId==0&&hookSerial==0;}
}
