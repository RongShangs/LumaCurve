package top.rongshangs.lumacurve.refactor;
/** Gesture grouping tolerance is not a restore/reset threshold. */
final class MemoryScene {
 static boolean near(float before,float now,float ratio,float minimum){return Float.isFinite(before)&&Float.isFinite(now)&&before>=0&&now>=0&&Math.abs(now-before)<=Math.max(minimum,before*ratio);}
 static boolean reset(float oldLux,float newLux,long offMs,MemoryOptions o){return offMs>=o.forceMinutes*60000L||offMs>=o.offMinutes*60000L&&Float.isFinite(oldLux)&&Float.isFinite(newLux)&&Math.abs(newLux-oldLux)>o.resetLux;}
}
