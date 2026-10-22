"""Execute production dialog measure/layout with host view doubles, not Android rendering."""
from pathlib import Path
import subprocess,re
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-dialog-layout';O.mkdir(exist_ok=True)
source=(S/'ResponsiveDialog.java').read_text(encoding='utf-8')
methods=source[source.index('    @Override protected void onMeasure(int w,int h){\n        WindowMetrics'):source.index('    static void configure(')]
code=r'''import java.util.*;
class Insets{int top,bottom,left,right;Insets(int t,int b,int l,int r){top=t;bottom=b;left=l;right=r;}}
class WindowInsets{Insets safe;WindowInsets(Insets s){safe=s;}Insets getInsets(int flags){return safe;}static class Type{static int systemBars(){return 1;}static int displayCutout(){return 2;}static int ime(){return 4;}}}
class Bounds{int width,height;Bounds(int w,int h){width=w;height=h;}int height(){return height;}int width(){return width;}}
class WindowMetrics{Bounds bounds;WindowInsets insets;Bounds getBounds(){return bounds;}WindowInsets getWindowInsets(){return insets;}}
class WindowManager{WindowMetrics metrics;WindowMetrics getCurrentWindowMetrics(){return metrics;}}
class Activity{WindowManager manager=new WindowManager();WindowManager getSystemService(Class<WindowManager> cls){return manager;}}
class View{
 int measuredWidth,measuredHeight,l,t,r,b,naturalWidth=1,naturalHeight;LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
 static class MeasureSpec{static final int UNSPECIFIED=0,EXACTLY=1<<30,AT_MOST=2<<30;static int makeMeasureSpec(int s,int m){return s|m;}static int getMode(int s){return s&0xc0000000;}static int getSize(int s){return s&0x3fffffff;}}
 Object getLayoutParams(){return params;}int getMeasuredWidth(){return measuredWidth;}int getMeasuredHeight(){return measuredHeight;}
 void measure(int w,int h){onMeasure(w,h);}protected void onMeasure(int w,int h){int width=MeasureSpec.getMode(w)==MeasureSpec.UNSPECIFIED?naturalWidth:MeasureSpec.getSize(w);int nh=naturalHeight*Math.max(1,(naturalWidth+Math.max(1,width)-1)/Math.max(1,width));setMeasuredDimension(width,MeasureSpec.getMode(h)==MeasureSpec.EXACTLY?MeasureSpec.getSize(h):MeasureSpec.getMode(h)==MeasureSpec.AT_MOST?Math.min(nh,MeasureSpec.getSize(h)):nh);}
 void setMeasuredDimension(int w,int h){measuredWidth=w;measuredHeight=h;}void layout(int left,int top,int right,int bottom){l=left;t=top;r=right;b=bottom;onLayout(true,left,top,right,bottom);}protected void onLayout(boolean c,int l,int t,int r,int b){}
}
class LinearLayout extends View{
 int pt=20,pb=18,pl=22,pr=22;List<View> children=new ArrayList<>();
 static class LayoutParams{int width,height,topMargin,bottomMargin,leftMargin,rightMargin;LayoutParams(int w,int h){width=w;height=h;}}
 int getPaddingTop(){return pt;}int getPaddingBottom(){return pb;}int getPaddingLeft(){return pl;}int getPaddingRight(){return pr;}
 void measureChildWithMargins(View child,int w,int wu,int h,int hu){LayoutParams p=child.params;int width=Math.max(0,MeasureSpec.getSize(w)-pl-pr-p.leftMargin-p.rightMargin-wu);int wm=MeasureSpec.getMode(w)==MeasureSpec.UNSPECIFIED?MeasureSpec.UNSPECIFIED:MeasureSpec.EXACTLY;int height=MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED?0:Math.max(0,MeasureSpec.getSize(h)-pt-pb-p.topMargin-p.bottomMargin-hu);child.measure(MeasureSpec.makeMeasureSpec(width,wm),MeasureSpec.makeMeasureSpec(height,MeasureSpec.getMode(h)));}
}
class ScrollView extends View{}
class ResponsiveDialog extends LinearLayout{
 final Activity host=new Activity();final View title=new View();final LinearLayout actions=new LinearLayout();final ScrollView body=new ScrollView();int bodyLimit=Integer.MAX_VALUE;float heightFraction=1;WindowInsets rootInsets;
 ResponsiveDialog(){children.add(title);children.add(body);children.add(actions);body.params.topMargin=10;}
 int dp(float x){return Math.round(x);}WindowInsets getRootWindowInsets(){return rootInsets;}
''' + methods + r'''
}
public class DialogLayoutTest{
 static int cases;static void check(boolean ok,String message){if(!ok)throw new AssertionError(cases+": "+message);cases++;}
 static int spec(int value,int mode){return View.MeasureSpec.makeMeasureSpec(value,mode);}
 static ResponsiveDialog create(int width,int height,int top,int bottom,float font,boolean longNotes){
  ResponsiveDialog d=new ResponsiveDialog();WindowMetrics m=new WindowMetrics();m.bounds=new Bounds(width,height);m.insets=new WindowInsets(new Insets(top,bottom,0,0));d.host.manager.metrics=m;
  d.title.naturalHeight=Math.round(36*font);d.title.naturalWidth=width<350?350:1;d.actions.naturalHeight=Math.round(110*Math.max(1,font));d.body.naturalHeight=longNotes?10000:60;d.body.naturalWidth=1;d.heightFraction=.82f;return d;
 }
 static void measure(ResponsiveDialog d,int width,int height,int mode){d.measure(spec(width,View.MeasureSpec.EXACTLY),spec(height,mode));d.layout(0,0,d.measuredWidth,d.measuredHeight);}
 static void verify(ResponsiveDialog d,int available){
  check(d.measuredHeight<=available,"window safe bounds");check(d.actions.getMeasuredHeight()==d.actions.naturalHeight,"footer natural height reserved");
  check(d.actions.b<=d.measuredHeight-d.pb,"footer fully inside padding");check(d.title.t==d.pt,"title top padding");
  check(d.body.t>=d.title.b+10,"notes after title");check(d.actions.t>=d.body.b,"no notes/action overlap");
  check(d.body.measuredHeight<=d.bodyLimit,"notes alone bounded");check(d.actions.l>=d.pl&&d.actions.r<=d.measuredWidth-d.pr,"footer horizontal bounds");
 }
 public static void main(String[] args){
  for(int width:new int[]{280,360,440,720})for(int height:new int[]{360,480,720,900})for(float font:new float[]{1,1.3f,1.7f})for(boolean longNotes:new boolean[]{false,true})for(int mode:new int[]{View.MeasureSpec.AT_MOST,View.MeasureSpec.UNSPECIFIED}){
   ResponsiveDialog d=create(width,height,24,24,font,longNotes);int available=height-24-24-24;measure(d,width,height,mode);verify(d,available);
   if(!longNotes)check(d.body.measuredHeight==Math.min(60,d.bodyLimit),"short notes do not force 320dp blank space");
   int preferred=Math.round(available*.82f),fixed=d.pt+d.pb+d.title.measuredHeight+d.actions.measuredHeight+10;
   check(d.measuredHeight<=Math.min(available,Math.max(preferred,fixed+48)),"preferred height respected");
   d.actions.naturalHeight+=12;measure(d,width,height,mode);verify(d,available);
  }
  ResponsiveDialog d=create(400,900,24,24,1,true);measure(d,400,900,View.MeasureSpec.AT_MOST);int shorter=d.measuredHeight;d.heightFraction=1;measure(d,400,900,View.MeasureSpec.AT_MOST);check(shorter<d.measuredHeight,"update popup shorter than generic detail dialog");
  d.rootInsets=new WindowInsets(new Insets(24,260,0,0));measure(d,400,900,View.MeasureSpec.AT_MOST);verify(d,900-24-260-24);
  System.out.println("Responsive dialog layout: "+cases+" cases PASS; production measure/layout with host doubles, Android rendering not tested");
 }
}'''
p=O/'DialogLayoutTest.java';p.write_text(code,encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(O),str(p)],check=True)
subprocess.run(['java','-cp',str(O),'DialogLayoutTest'],check=True)
