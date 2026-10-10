package top.rongshangs.lumacurve.refactor;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
/** Compact bright-style node panel with one coalesced command queue. */
final class RawBrightnessPanel {
 final Activity host;final Runnable dismiss;final FrameLayout root;final LinearLayout card;
 final Handler ui=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();
 final RootPanelBridge bridge;final boolean dark,en,landscape;final int ink,muted,blue,bg,surface;
 TextView state,current,target,range,fault;TextView inputAction,maxAction;String lastFault="";NodeSlider slider;
 AlertDialog activeDialog;
 volatile boolean closed;boolean sending,dragging,claimed;Integer pending;String session=UUID.randomUUID().toString();int generation,maximum=10;long gesture;
 final Runnable poll,flush;
 RawBrightnessPanel(Activity host,Runnable dismiss){
  this.host=host;this.dismiss=dismiss;bridge=new RootPanelBridge(host);
  dark=(host.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;en=!Locale.getDefault().getLanguage().equals("zh");
  landscape=host.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
  ink=dark?0xffeef1f8:0xff253047;muted=dark?0xffa7b2c6:0xff657289;blue=dark?0xff9dbbff:0xff3265df;bg=dark?0xf21b202b:0xf7f9fbff;surface=dark?0xff282f3d:0xffedf1f8;
  root=new FrameLayout(host);root.setOnClickListener(v->dismiss.run());card=new LinearLayout(host);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(landscape?12:16),dp(16),dp(landscape?12:16));card.setBackground(shape(bg,24));card.setClipToOutline(true);card.setOnClickListener(v->{});
  ScrollView scroll=new ScrollView(host){@Override protected void onMeasure(int w,int h){super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.max(1,MeasureSpec.getSize(h)-dp(24)),MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(card);
  FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(Math.max(1,Math.min(host.getResources().getDisplayMetrics().widthPixels-dp(32),dp(landscape?640:400))),-2,Gravity.CENTER);root.addView(scroll,box);
  root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());root.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
  root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int width=Math.max(1,Math.min(r-l-root.getPaddingLeft()-root.getPaddingRight()-dp(32),dp(landscape?640:400)));if(box.width!=width){box.width=width;scroll.setLayoutParams(box);}});
  LinearLayout heading=new LinearLayout(host);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setBaselineAligned(false);card.addView(heading);
  LinearLayout titles=new LinearLayout(host);titles.setOrientation(1);heading.addView(titles,new LinearLayout.LayoutParams(0,-2,1));TextView title=text("HyperLux",19,ink);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);titles.addView(title);titles.addView(text("主屏亮度",12,muted));
  TextView close=text("×",26,muted);close.setGravity(Gravity.CENTER);close.setContentDescription(tr("关闭"));close.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x30ffffff:0x203265df),shape(surface,14),shape(Color.WHITE,14)));heading.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));close.setOnClickListener(v->dismiss.run());
  state=text("正在读取主屏节点…",12,blue);state.setPadding(0,dp(10),0,dp(10));card.addView(state);
  LinearLayout body=new LinearLayout(host);body.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);body.setBaselineAligned(false);card.addView(body,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout row=new LinearLayout(host);row.setBaselineAligned(false);int height=dp((landscape?160:192)*Math.max(1,Math.min(2,host.getResources().getConfiguration().fontScale)));body.addView(row,landscape?new LinearLayout.LayoutParams(0,height,1):new LinearLayout.LayoutParams(-1,height));
  LinearLayout info=new LinearLayout(host);info.setOrientation(1);info.setGravity(Gravity.CENTER_VERTICAL);info.setPadding(dp(12),dp(12),dp(12),dp(12));info.setBackground(shape(surface,18));row.addView(info,new LinearLayout.LayoutParams(0,-1,1));
  target=metric(info,"目标节点",blue,22);current=metric(info,"节点读回",ink,18);range=metric(info,"节点上限",muted,15);
  slider=new NodeSlider();LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(72),-1);sp.leftMargin=dp(12);row.addView(slider,sp);
  LinearLayout actions=new LinearLayout(host);actions.setOrientation(landscape?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);actions.setBaselineAligned(false);LinearLayout.LayoutParams ap=landscape?new LinearLayout.LayoutParams(dp(152),height):new LinearLayout.LayoutParams(-1,-2);if(landscape)ap.leftMargin=dp(12);else ap.topMargin=dp(12);body.addView(actions,ap);
  inputAction=action("输入数值",this::number,actions,false);maxAction=action("节点最大值",()->begin(maximum),actions,true);action("恢复自动",this::restore,actions,true);inputAction.setEnabled(false);maxAction.setEnabled(false);
  fault=text("",12,dark?0xffff9696:0xffb33535);fault.setVisibility(View.GONE);fault.setOnClickListener(v->showFault());card.addView(fault);
  poll=new Runnable(){public void run(){if(closed)return;if(!sending&&!dragging)submit(new JSONObjectRequest("status"),false);ui.postDelayed(this,1000);}};flush=()->flush();
 }
 int dp(float v){return Math.round(v*host.getResources().getDisplayMetrics().density);}
 String tr(String s){return UiText.translate(s,en);}
 GradientDrawable shape(int color,int radius){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(radius));return b;}
 TextView text(String s,int size,int color){TextView t=new TextView(host);t.setText(tr(s));t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);t.setPadding(0,dp(3),0,dp(3));return t;}
 TextView metric(LinearLayout parent,String label,int color,int size){LinearLayout line=new LinearLayout(host);line.setGravity(Gravity.CENTER_VERTICAL);line.setBaselineAligned(false);parent.addView(line,new LinearLayout.LayoutParams(-1,0,1));TextView name=text(label,11,muted);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView value=text("—",size,color);value.setTypeface(Typeface.DEFAULT,Typeface.BOLD);value.setGravity(Gravity.END);value.setSingleLine();value.setAutoSizeTextTypeUniformWithConfiguration(12,size,1,android.util.TypedValue.COMPLEX_UNIT_SP);line.addView(value,new LinearLayout.LayoutParams(0,-2,1.1f));return value;}
 TextView action(String label,Runnable click,LinearLayout parent,boolean gap){TextView b=text(label,12,blue);b.setGravity(Gravity.CENTER);b.setPadding(dp(6),dp(10),dp(6),dp(10));b.setMinHeight(dp(48));b.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x30ffffff:0x303265df),shape(surface,14),shape(Color.WHITE,14)));b.setOnClickListener(v->click.run());b.setFocusable(true);boolean vertical=parent.getOrientation()==LinearLayout.VERTICAL;LinearLayout.LayoutParams p=vertical?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1);if(gap){if(vertical)p.topMargin=dp(8);else p.leftMargin=dp(8);}parent.addView(b,p);return b;}
 static final class JSONObjectRequest extends JSONObject {JSONObjectRequest(String action){try{put("action",action);}catch(Exception impossible){throw new IllegalStateException(impossible);}}}
 void start(){submit(new JSONObjectRequest("status"),false);ui.postDelayed(poll,1000);}
 void show(JSONObject answer){
  if(closed)return;JSONObject node=answer.optJSONObject("node");JSONObject runtime=answer.optJSONObject("runtime");
  if(node==null||!node.optBoolean("supported")){
   String reason=node==null?"":node.optString("reason");
   String message="ambiguous_primary_nodes".equals(reason)?(en?"Multiple display nodes; primary screen is uncertain":"发现多个显示节点，暂不能确定主屏"):
       "scan_incomplete".equals(reason)?(en?"Node scan incomplete; export diagnostics":"节点扫描不完整，请导出分析包"):
       "guard_node_mismatch".equals(reason)?(en?"Writer changed; restore automatic brightness first":"守护节点已变化，请先恢复自动亮度"):
       "node_not_writable".equals(reason)?(en?"Display node is read-only; export diagnostics":"已找到显示节点，但未取得写入权限"):
       (en?"No identifiable primary brightness node":"未找到可识别的主屏亮度节点");
   if(node!=null&&node.has("write_errno"))message+=" (errno "+node.optInt("write_errno")+")";
   state.setText(message);current.setText(node!=null&&node.has("actual")?String.valueOf(node.optInt("actual")):"—");target.setText("—");range.setText(node!=null&&node.has("maximum")?String.valueOf(node.optInt("maximum")):"—");
   slider.ready=false;inputAction.setEnabled(false);maxAction.setEnabled(false);inputAction.setAlpha(.4f);maxAction.setAlpha(.4f);slider.invalidate();return;
  }
  maximum=node.optInt("maximum",10);int actual=node.optInt("actual",-1);JSONObject guard=node.optJSONObject("guard");
  boolean auto=runtime==null||runtime.optBoolean("auto_mode");JSONObject owner=runtime==null?null:runtime.optJSONObject("brightness_control");
  BrightnessTileService.publish(runtime);
  if(claimed&&(auto||owner==null||!session.equals(owner.optString("session"))||!"raw_panel".equals(owner.optString("owner")))){generation++;claimed=false;pending=null;dragging=false;state.setText(tr("已退让；再次滑动才会关闭自动亮度"));}
  else state.setText(tr(auto?"自动亮度中；滑动切换为手动":guard!=null&&guard.optBoolean("paused")?"解锁后恢复":guard!=null&&guard.optBoolean("armed")&&guard.optInt("target",-1)>=10?"节点亮度保持中":"手动模式；滑动接管节点"));
  slider.ready=runtime!=null&&"active".equals(runtime.optString("phase"))&&owner!=null&&owner.optBoolean("manual_panel_enabled")&&owner.optBoolean("manual_panel_supported");
  int goal=guard==null?-1:guard.optInt("target",-1);
  current.setText(actual<0?"—":String.valueOf(actual));target.setText(goal<10?"—":String.valueOf(goal));range.setText(String.valueOf(maximum));
  inputAction.setEnabled(slider.ready);maxAction.setEnabled(slider.ready);inputAction.setAlpha(slider.ready?1:.4f);maxAction.setAlpha(slider.ready?1:.4f);
  if(!dragging&&pending==null)slider.value=Math.max(10,Math.min(maximum,goal>=10?goal:actual));slider.invalidate();
  if(!slider.ready)state.setText(tr(owner!=null&&!owner.optBoolean("manual_panel_supported")?"主屏输出接口尚未兼容":"先启用引擎；刚更新应用需重启"));
 }
 void begin(int value){if(closed||!slider.ready)return;clearFault();generation++;claimed=false;pending=null;session=UUID.randomUUID().toString();gesture=SystemClock.elapsedRealtime();submit(command("begin",value),true);}
 JSONObject command(String action,int n){JSONObject j=new JSONObjectRequest(action);try{j.put("session",session).put("value",n).put("gesture_elapsed",gesture);}catch(Exception impossible){}return j;}
 void flush(){if(closed||sending||!claimed||pending==null)return;int n=pending;pending=null;submit(command("set",n),false);}
 void submit(JSONObject command,boolean begin){
  if(closed)return;final int epoch=generation;sending=true;
  worker.execute(()->{JSONObject answer=null;Throwable error=null;try{answer=bridge.request(command);}catch(Throwable failure){error=failure;}final JSONObject result=answer;final Throwable failure=error;
   ui.post(()->{if(closed)return;sending=false;if(epoch!=generation){flush();return;}if(failure!=null){claimed=false;pending=null;lastFault=String.valueOf(failure.getMessage());fault.setText(lastFault);fault.setVisibility(View.VISIBLE);if(!"status".equals(command.optString("action")))showFault();return;}if(begin)claimed=true;show(result);flush();});});
 }
 void restore(){clearFault();generation++;claimed=false;pending=null;dragging=false;submit(command("restore",0),false);}
 void clearFault(){lastFault="";fault.setText("");fault.setVisibility(View.GONE);}
 void showFault(){
  if(closed||host.isFinishing()||lastFault.isEmpty())return;
  TextView detail=text(lastFault,13,muted);detail.setTextIsSelectable(true);LinearLayout actions=new LinearLayout(host);AlertDialog d=dialog("主屏操作未完成",detail,actions);
  action("知道了",d::dismiss,actions,false);d.show();
 }
 AlertDialog dialog(String title,View content,LinearLayout actions){if(activeDialog!=null)activeDialog.dismiss();ResponsiveDialog box=new ResponsiveDialog(host,text(title,18,ink),content,actions);box.setPadding(dp(22),dp(landscape?12:18),dp(22),dp(landscape?12:18));box.setBackground(shape(bg,22));box.setClipToOutline(true);AlertDialog d=new AlertDialog.Builder(host).setView(box).create();activeDialog=d;d.setOnDismissListener(v->{if(activeDialog==d)activeDialog=null;});ResponsiveDialog.configure(host,d.getWindow(),dp(landscape?520:440));if(android.animation.ValueAnimator.areAnimatorsEnabled()){box.setAlpha(0);d.setOnShowListener(v->box.animate().alpha(1).setDuration(180).start());}return d;}
 void number(){
  EditText input=new EditText(host);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(String.valueOf(slider.value));input.setTextColor(ink);input.setSingleLine(true);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);input.setPadding(dp(12),dp(10),dp(12),dp(10));input.setBackground(shape(surface,12));input.setSelectAllOnFocus(true);
  LinearLayout content=new LinearLayout(host);content.setOrientation(1);content.addView(text("10～"+maximum,13,muted));content.addView(input);
  LinearLayout actions=new LinearLayout(host);actions.setBaselineAligned(false);LinearLayout.LayoutParams footer=new LinearLayout.LayoutParams(-1,-2);footer.topMargin=dp(10);actions.setLayoutParams(footer);AlertDialog d=dialog("输入主屏节点值",content,actions);
  action("取消",d::dismiss,actions,false);action("应用",()->{try{int n=Integer.parseInt(input.getText().toString());if(n<10||n>maximum)throw new IllegalArgumentException();d.dismiss();begin(n);}catch(Exception failure){input.setError(tr("请输入范围内的整数"));}},actions,true);d.show();
 }
 void close(){if(closed)return;closed=true;if(activeDialog!=null)activeDialog.dismiss();pending=null;ui.removeCallbacks(poll);ui.removeCallbacks(flush);worker.execute(bridge::close);worker.shutdown();}
 final class NodeSlider extends View {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RelativeNodeGesture relative=new RelativeNodeGesture();int value=10,pointer=-1;boolean ready,started;
  NodeSlider(){super(host);setContentDescription(tr("主屏节点亮度"));setFocusable(true);}
  void select(float y){int n=relative.move(y);if(n==value)return;value=n;target.setText(String.valueOf(n));invalidate();if(!started){started=true;begin(n);}else{pending=n;ui.removeCallbacks(flush);ui.postDelayed(flush,80);}}
  @Override protected void onDraw(Canvas c){paint.setAlpha(255);paint.setColor(surface);c.drawRoundRect(0,0,getWidth(),getHeight(),dp(18),dp(18),paint);float fraction=(value-10)/(float)Math.max(1,maximum-10),y=getHeight()*(1-fraction);c.save();Path clip=new Path();clip.addRoundRect(new RectF(0,0,getWidth(),getHeight()),dp(18),dp(18),Path.Direction.CW);c.clipPath(clip);paint.setColor(blue);paint.setAlpha(ready?255:90);c.drawRect(0,y,getWidth(),getHeight(),paint);c.restore();paint.setAlpha(255);int onFill=dark?0xff142440:Color.WHITE;paint.setColor(ready&&fraction>.9f?onFill:muted);paint.setTextSize(dp(11));paint.setTextAlign(Paint.Align.CENTER);c.drawText(""+Math.round(fraction*100)+"%",getWidth()/2f,dp(25),paint);paint.setColor(ready&&fraction>.2f?onFill:muted);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.7f));float x=getWidth()/2f,cy=getHeight()-dp(27);c.drawCircle(x,cy,dp(5),paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(x+(float)Math.cos(a)*dp(8),cy+(float)Math.sin(a)*dp(8),x+(float)Math.cos(a)*dp(11),cy+(float)Math.sin(a)*dp(11),paint);}paint.setStyle(Paint.Style.FILL);}
  @Override public boolean onTouchEvent(android.view.MotionEvent e){if(!ready||closed)return true;int index=e.findPointerIndex(pointer);switch(e.getActionMasked()){
   case MotionEvent.ACTION_DOWN:pointer=e.getPointerId(0);started=false;dragging=true;relative.begin(value,10,maximum,e.getY(),getHeight(),ViewConfiguration.get(host).getScaledTouchSlop());getParent().requestDisallowInterceptTouchEvent(true);return true;
   case MotionEvent.ACTION_MOVE:if(index>=0)select(e.getY(index));return true;
   case MotionEvent.ACTION_POINTER_UP:if(e.getPointerId(e.getActionIndex())==pointer){dragging=false;pointer=-1;flush();}return true;
   case MotionEvent.ACTION_UP:if(index>=0)select(e.getY(index));dragging=false;pointer=-1;getParent().requestDisallowInterceptTouchEvent(false);flush();performClick();return true;
   case MotionEvent.ACTION_CANCEL:dragging=false;pointer=-1;pending=null;ui.removeCallbacks(flush);getParent().requestDisallowInterceptTouchEvent(false);return true;
  }return true;}
  @Override public boolean performClick(){return super.performClick();}
 }
}
