package top.rongshangs.lumacurve.refactor;

/** Preserve the OEM evidence timestamp and special-mode additions; replace only the base delay. */
public final class DelayPolicy {
    public static void validateSmall(long delay){if(delay<500||delay>15000)throw new IllegalArgumentException("微小变亮确认时间应在 0.5～15 秒");}
    public static void validate(long brighten,long darken){
        if(brighten<500||brighten>10000||darken<1000||darken>15000)
            throw new IllegalArgumentException("确认时间超出可调范围");
    }
    public static long deadline(long original,long now,long base,long chosen){
        if(now<0||original<0||base<0||base>60000||chosen<500||chosen>15000)
            throw new IllegalArgumentException("系统确认时间无效");
        long delta=chosen-base;
        long adjusted=Math.addExact(original,delta);
        return Math.max(now,adjusted);
    }
}
