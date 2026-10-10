"""Check status geometry and actual measure/layout methods across viewport sizes."""
from pathlib import Path
import os,subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-status-layout-tests';O.mkdir(parents=True,exist_ok=True)
main=(S/'MainActivity.java').read_text(encoding='utf-8');start=main.index('        StatusPageLayout geometry(');methods=main[start:main.index('        void refreshAdvice(){',start)]
test=O/'StatusLayoutTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor;
class RectF{float top,bottom,left,right;void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}
class MeasureSpec{static final int UNSPECIFIED=0,EXACTLY=1<<30;static int makeMeasureSpec(int size,int mode){return size|mode;}static int getMode(int s){return s&0xc0000000;}static int getSize(int s){return s&0x3fffffff;}}
class Resource{float density,fontScale;Resource(float d,float f){density=d;fontScale=f;}Resource getDisplayMetrics(){return this;}Resource getConfiguration(){return this;}}
class View{int width,height,l,t,r,b;void onMeasure(int w,int h){}void onLayout(boolean c,int l,int t,int r,int b){}void measure(int w,int h){width=MeasureSpec.getSize(w);height=MeasureSpec.getSize(h);}void layout(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;}}
class Board extends View{
 final Resource res;final RectF[] boxes=new RectF[6];final View stateGraph=new View(),protectionPanel=new View(),curveInfo=new View(),fusedPanel=new View();final View[] readingPanels={new View(),new View(),new View()};Board(float d,float f){res=new Resource(d,f);for(int i=0;i<6;i++)boxes[i]=new RectF();}
 Resource getResources(){return res;}int dp(float n){return Math.round(n*res.density);}int getWidth(){return width;}int getHeight(){return height;}void setMeasuredDimension(int w,int h){width=w;height=h;}
''' + methods + r'''}
public class StatusLayoutTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void child(View v){check(v.r-v.l==v.width&&v.b-v.t==v.height);check(v.width>0&&v.height>0);}
 public static void main(String[] args){
  for(float density:new float[]{1,2.75f,4})for(float font:new float[]{1,1.3f,2})for(int[] viewport:new int[][]{{240,220},{360,560},{412,597},{412,740},{800,340},{1280,1000}})for(int mode:new int[]{MeasureSpec.UNSPECIFIED,MeasureSpec.EXACTLY}){
   Board b=new Board(density,font);int w=b.dp(viewport[0]),h=b.dp(viewport[1]);b.onMeasure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,mode));b.onLayout(true,0,0,b.width,b.height);
   StatusPageLayout layout=b.geometry(b.height);check(b.height>=StatusPageLayout.minimumHeight(density,font));check(mode==MeasureSpec.UNSPECIFIED||b.height==Math.max(h,StatusPageLayout.minimumHeight(density,font)));
   check(layout.top[0]==0&&layout.bottom[4]==b.height);for(int i=1;i<5;i++)check(layout.top[i]-layout.bottom[i-1]==(i==2?layout.thresholdGap:layout.gap));
   check(layout.bottom[1]-layout.top[1]==b.dp(88+56*(font-1)));check(layout.bottom[0]-layout.top[0]==layout.bottom[4]-layout.top[4]);
   check(layout.bottom[2]-layout.top[2]>=b.dp(120+24*(font-1))+layout.infoHeight);check(layout.bottom[3]-layout.top[3]==b.dp(108+64*(font-1)));
   check(b.boxes[0].top==b.boxes[1].top&&b.boxes[0].bottom==b.boxes[1].bottom);check(b.boxes[1].left-b.boxes[0].right==b.dp(12));
   child(b.stateGraph);child(b.protectionPanel);child(b.curveInfo);
   child(b.fusedPanel);check(b.fusedPanel.t==b.boxes[2].top+b.dp(12));check(b.boxes[2].bottom-b.fusedPanel.b==b.dp(12));
   check(b.stateGraph.t==b.boxes[3].top+b.dp(12));check(b.curveInfo.t-b.stateGraph.b==b.dp(8));check(b.boxes[3].bottom-b.curveInfo.b==b.dp(12));
   check(b.protectionPanel.t==b.boxes[4].top+b.dp(12)&&b.boxes[4].bottom-b.protectionPanel.b==b.dp(12));
   check(b.stateGraph.l==b.curveInfo.l&&b.curveInfo.l==b.protectionPanel.l);check(b.stateGraph.r==b.curveInfo.r&&b.curveInfo.r==b.protectionPanel.r);
   check(b.stateGraph.height>=b.dp(88)-2);check(b.protectionPanel.height>=b.dp(84)-2);check(b.curveInfo.height==layout.infoHeight);
   check(layout.bottom[0]-layout.top[0]==b.dp(80+56*(font-1)));
   for(int i=0;i<3;i++){child(b.readingPanels[i]);RectF box=b.boxes[i==2?5:i];check(b.readingPanels[i].t==(int)box.top+b.dp(12));check(b.readingPanels[i].l==(int)box.left+b.dp(12));check(b.readingPanels[i].b==(int)box.bottom-b.dp(12));}
  }
  check(StatusPageLayout.minimumHeight(1,1)==588);check(StatusPageLayout.minimumHeight(1,2)==898);
  check(StatusPageLayout.minimumHeight(1,Float.NaN)==588);check(StatusPageLayout.minimumHeight(1,3)==898);
  StatusPageLayout small=new StatusPageLayout(584,1,1),tall=new StatusPageLayout(740,1,1);
  check(small.bottom[3]-small.top[3]==tall.bottom[3]-tall.top[3]);check(small.infoHeight==tall.infoHeight);check(tall.bottom[2]-tall.top[2]-(small.bottom[2]-small.top[2])==152);
  System.out.println("Status page layout: "+cases+" cases PASS; actual geometry/measure/layout with doubles, Android rendering not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(classes),str(S/'StatusPageLayout.java'),str(test)],check=True)
subprocess.run(['java','-cp',str(classes),'top.rongshangs.lumacurve.refactor.StatusLayoutTest'],check=True)
