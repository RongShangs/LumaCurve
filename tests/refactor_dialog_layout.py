"""Execute production dialog measure/layout with host view doubles, not Android rendering."""
from pathlib import Path
import subprocess,re
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-dialog-layout';O.mkdir(exist_ok=True)
source=(S/'ResponsiveDialog.java').read_text(encoding='utf-8')
methods=source[source.index('    @Override protected void onMeasure(int w,int h){\n        WindowMetrics'):source.index('    static void configure(')]
main=(S/'MainActivity.java').read_text(encoding='utf-8');start=main.index('    void updateButton(');update_button=main[start:main.index('    void select(',start)]
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
 int pt=20,pb=18,pl=22,pr=22;String tag;List<View> children=new ArrayList<>();
 static class LayoutParams{static final int WRAP_CONTENT=-2;int width,height,topMargin,bottomMargin,leftMargin,rightMargin;LayoutParams(int w,int h){width=w;height=h;}}
 int getPaddingTop(){return pt;}int getPaddingBottom(){return pb;}int getPaddingLeft(){return pl;}int getPaddingRight(){return pr;}
 void measureChildWithMargins(View child,int w,int wu,int h,int hu){LayoutParams p=child.params;int width=Math.max(0,MeasureSpec.getSize(w)-pl-pr-p.leftMargin-p.rightMargin-wu);int wm=MeasureSpec.getMode(w)==MeasureSpec.UNSPECIFIED?MeasureSpec.UNSPECIFIED:MeasureSpec.EXACTLY;int height=MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED?0:Math.max(0,MeasureSpec.getSize(h)-pt-pb-p.topMargin-p.bottomMargin-hu);child.measure(MeasureSpec.makeMeasureSpec(width,wm),MeasureSpec.makeMeasureSpec(height,MeasureSpec.getMode(h)));}
 @Override protected void onMeasure(int w,int h){if(children.isEmpty()){super.onMeasure(w,h);return;}int width=MeasureSpec.getSize(w),height=pt+pb;for(View child:children){child.measure(MeasureSpec.makeMeasureSpec(Math.max(1,width-pl-pr-child.params.leftMargin-child.params.rightMargin),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));height+=child.measuredHeight+child.params.topMargin+child.params.bottomMargin;}setMeasuredDimension(width,height);}
 @Override protected void onLayout(boolean c,int l,int t,int r,int b){int y=pt;for(View child:children){y+=child.params.topMargin;child.layout(pl+child.params.leftMargin,y,pl+child.params.leftMargin+child.measuredWidth,y+child.measuredHeight);y+=child.measuredHeight+child.params.bottomMargin;}}
}
class AlertDialog{void dismiss(){}}
class Button extends View{
 String label;float font=1;boolean single=true;int maxLines=1,padTop,padBottom,padLeft,padRight,textHeight;
 Object getLayoutParams(){return params;}void setLayoutParams(LinearLayout.LayoutParams p){params=p;}void setSingleLine(boolean v){single=v;}void setMaxLines(int n){maxLines=n;}void setEllipsize(Object v){}void setPadding(int l,int t,int r,int b){padLeft=l;padTop=t;padRight=r;padBottom=b;}
 @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);double textWidth=0;for(char c:label.toCharArray())textWidth+=(c>127?14:8)*font;int lines=single?1:Math.max(1,(int)Math.ceil(textWidth/Math.max(1,width-padLeft-padRight)));textHeight=(int)Math.ceil(19*font)*Math.min(lines,maxLines)+padTop+padBottom;int wanted=Math.max(48,textHeight);int height=MeasureSpec.getMode(h)==MeasureSpec.EXACTLY?MeasureSpec.getSize(h):wanted;setMeasuredDimension(width,height);}
}
class ActionRow extends LinearLayout{
 ActionRow(){pt=pb=pl=pr=0;}
 @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w),height=0,each=width/Math.max(1,children.size());for(View child:children){LayoutParams p=child.params;child.measure(MeasureSpec.makeMeasureSpec(Math.max(1,each-p.leftMargin-p.rightMargin),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(p.height<0?0:p.height,p.height<0?MeasureSpec.UNSPECIFIED:MeasureSpec.EXACTLY));height=Math.max(height,child.measuredHeight+p.topMargin+p.bottomMargin);}setMeasuredDimension(width,height);}
 @Override protected void onLayout(boolean c,int l,int t,int r,int b){int x=0,each=measuredWidth/Math.max(1,children.size());for(View child:children){LayoutParams p=child.params;int y=p.topMargin;child.layout(x+p.leftMargin,y,x+p.leftMargin+child.measuredWidth,y+child.measuredHeight);x+=each;}}
}
class UpdateHost{
 int dp(int n){return n;}Button compact(String label,Runnable task,LinearLayout parent){Button b=new Button();b.label=label;b.params=new LinearLayout.LayoutParams(0,48);boolean dialog="actions".equals(parent.tag);b.params.leftMargin=b.params.rightMargin=dialog?4:3;b.params.topMargin=dialog?14:6;b.params.bottomMargin=dialog?0:4;parent.children.add(b);return b;}
''' + update_button + r'''
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
  check(d.actions.b<=d.measuredHeight-d.pb,"footer fully inside padding: window="+d.measuredWidth+"x"+available+", title="+d.title.measuredHeight+", actions="+d.actions.measuredHeight+", bottom="+d.actions.b+", measured="+d.measuredHeight+", body="+d.body.measuredHeight);check(d.title.t==d.pt,"title top padding");
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
  // Execute production updateButton styling, then reserve two real nested action rows.
  // Text metrics are deterministic doubles; no claim of Android font/render verification.
  int wrapped=0;UpdateHost host=new UpdateHost();
  for(int[] bounds:new int[][]{{280,480},{320,480},{360,640},{440,720},{720,360},{900,440}})for(float font:new float[]{1,1.3f,1.7f,2})for(boolean english:new boolean[]{false,true}){
   int width=bounds[0],height=bounds[1];
   d=create(width,height,24,24,font,true);if(width>height){d.pt=d.pb=12;}d.actions.pt=d.actions.pb=d.actions.pl=d.actions.pr=0;
   String[] labels=english?new String[]{"Website download","GitHub download","Later","Ignore this version"}:new String[]{"官网下载","GitHub 下载","稍后","忽略此版本"};
   for(int row=0;row<2;row++){ActionRow r=new ActionRow();if(row==1)r.tag="actions";d.actions.children.add(r);for(int col=0;col<2;col++){host.updateButton(new AlertDialog(),labels[row*2+col],null,r);((Button)r.children.get(col)).font=font;}}
   d.actions.measure(spec(width-d.pl-d.pr,View.MeasureSpec.EXACTLY),spec(0,View.MeasureSpec.UNSPECIFIED));int expected=d.actions.measuredHeight;d.actions.naturalHeight=expected;
   measure(d,width,height,View.MeasureSpec.AT_MOST);verify(d,height-24-24-24);check(d.actions.measuredHeight==expected,"both natural action rows reserved before notes");
   for(View row:d.actions.children){check(row.b<=d.actions.measuredHeight,"action row inside footer");for(View child:((LinearLayout)row).children){Button button=(Button)child;check(button.measuredHeight>=button.textHeight,"all translated button text visible");check(button.measuredHeight>=48,"touch target retained");check(button.t>=0&&button.b<=row.measuredHeight,"button vertically inside its row");check(button.l>=0&&button.r<=row.measuredWidth,"button horizontally inside its row");if(button.measuredHeight>48)wrapped++;}}
  }
  check(wrapped>0,"matrix exercises multiline actions taller than old fixed 48dp");
  System.out.println("Responsive dialog layout: "+cases+" cases PASS; production measure/layout with host doubles, Android rendering not tested");
 }
}'''
p=O/'DialogLayoutTest.java';p.write_text(code,encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(O),str(p)],check=True)
subprocess.run(['java','-cp',str(O),'DialogLayoutTest'],check=True)
