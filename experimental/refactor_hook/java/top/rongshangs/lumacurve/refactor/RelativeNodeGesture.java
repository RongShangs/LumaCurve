package top.rongshangs.lumacurve.refactor;
/** Relative displacement, continuous precision and clamp reversal; tapping never changes value. */
final class RelativeNodeGesture {
 double value;float last,start;boolean moving;int minimum,maximum;float height,slop;
 void begin(int value,int minimum,int maximum,float y,float height,float slop){this.value=value;this.minimum=minimum;this.maximum=maximum;this.start=this.last=y;this.height=Math.max(1,height);this.slop=Math.max(0,slop);moving=false;}
 int move(float y){if(!Float.isFinite(y))return (int)Math.round(value);if(!moving&&Math.abs(y-start)<=slop)return (int)Math.round(value);moving=true;value=Math.max(minimum,Math.min(maximum,value+(last-y)*(maximum-minimum)/height));last=y;return (int)Math.round(value);}
}
