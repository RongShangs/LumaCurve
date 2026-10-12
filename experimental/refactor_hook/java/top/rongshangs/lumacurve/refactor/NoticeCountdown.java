package top.rongshangs.lumacurve.refactor;

/** Uses elapsed time, rather than counting callbacks that may be delayed or duplicated. */
final class NoticeCountdown {
    final long started;
    NoticeCountdown(long elapsed){started=elapsed;}
    int remaining(long elapsed){long passed=Math.max(0,elapsed-started);return passed>=10000?0:(int)((10000-passed+999)/1000);}
}
