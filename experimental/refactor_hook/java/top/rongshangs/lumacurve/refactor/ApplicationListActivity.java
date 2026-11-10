package top.rongshangs.lumacurve.refactor;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Secondary application picker. Results are drafts; classification and icon work stops on leaving. */
public final class ApplicationListActivity extends Activity {
    static final int REQUEST=503;String listId,title;org.json.JSONObject assigned=new org.json.JSONObject();
    final Handler ui=new Handler(Looper.getMainLooper());final Set<String> selected=new LinkedHashSet<>(),others=new LinkedHashSet<>(),moved=new LinkedHashSet<>();AlertDialog confirmation;
    final Map<String,Drawable> icons=new HashMap<>();final Set<String> iconPending=new HashSet<>();final List<Entry> all=new ArrayList<>();
    ExecutorService worker;boolean visible,loaded,dark;long generation;int ink,muted,blue,bg;EditText search;TextView count,hint;ListView list;Rows adapter;
    View page;boolean closing;long pageTransition;
    static final class Entry{final String pkg,label;final ApplicationInfo info;final boolean video,game;Entry(String p,String l,ApplicationInfo i,boolean v,boolean g){pkg=p;label=l;info=i;video=v;game=g;}}
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}String tr(String s){return UiText.translate(s,!getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh"));}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(ThemePalette.surface(color,dark));d.setCornerRadius(dp(radius));return d;}
    TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(tr(s));v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);return v;}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);v.setBaselineAligned(false);return v;}
    Button button(String s,Runnable action){Button v=new Button(this);v.setText(tr(s));v.setTextSize(13);v.setTextColor(blue);v.setMinWidth(0);v.setMinimumWidth(0);v.setStateListAnimator(null);v.setElevation(0);v.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x3091b3ff:0x203265df),shape(0xffeef2fa,14),shape(Color.WHITE,14)));v.setOnClickListener(x->action.run());return v;}
    WindowInsets applyInsets(View root,WindowInsets insets){
        Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()),ime=insets.getInsets(WindowInsets.Type.ime());
        root.setPadding(bars.left,bars.top,bars.right,0);
        if(list!=null)list.setPadding(dp(18),0,dp(18),dp(12)+Math.max(bars.bottom,ime.bottom));
        return WindowInsets.CONSUMED;
    }
    void configureTransitions(){
        // Animate content inside a stationary opaque window, like other pages.
        // Fading/translating both Activity surfaces exposes the task backdrop
        // and allows ROM window shadows/corner masks into this short transition.
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN,0,0,bg);
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,0,0,bg);
    }
    void transitionContent(boolean entering,Runnable complete){
        cancelContentTransition();
        if(page==null||!android.animation.ValueAnimator.areAnimatorsEnabled()){if(complete!=null)complete.run();return;}
        final long epoch=pageTransition;
        page.setAlpha(entering?0:1);page.setTranslationX(entering?dp(12):0);
        page.animate().alpha(entering?1:0).translationX(entering?0:dp(12)).setDuration(entering?180:120)
            .setInterpolator(new android.view.animation.DecelerateInterpolator()).withEndAction(()->{if(epoch==pageTransition&&complete!=null)complete.run();}).start();
    }
    void cancelContentTransition(){
        ++pageTransition;if(page==null)return;
        page.animate().cancel();page.animate().withEndAction(null);page.setAlpha(1);page.setTranslationX(0);
    }
    void dialogAction(LinearLayout actions,Button button){
        button.setAllCaps(false);button.setIncludeFontPadding(false);button.setGravity(Gravity.CENTER);button.setSingleLine(true);button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(10),0,dp(10),0);button.setMinHeight(dp(48));button.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(4),dp(14),dp(4),0);actions.addView(button,lp);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        ink=ThemePalette.color(0xff24303c,dark);muted=ThemePalette.color(0xff616d79,dark);blue=ThemePalette.color(0xff3265df,dark);bg=ThemePalette.color(0xfffafafa,dark);
        listId=getIntent().getStringExtra("list");title=getIntent().getStringExtra("title");if(listId==null||title==null){finish();return;}try{assigned=new org.json.JSONObject(getIntent().getStringExtra("assigned"));}catch(Exception ignored){}
        try{selected.addAll(ApplicationSelection.read(saved==null?getIntent().getStringArrayListExtra("selected"):saved.getStringArrayList("selected")));}catch(Exception invalid){finish();return;}
        if(saved!=null)try{moved.addAll(ApplicationSelection.read(saved.getStringArrayList("moved")));}catch(Exception invalid){finish();return;}for(String pkg:moved)assigned.remove(pkg);
        Set<String> initial=ApplicationSelection.read(getIntent().getStringArrayListExtra("selected"));java.util.Iterator<String> assignments=assigned.keys();while(assignments.hasNext()){String pkg=assignments.next();if(!initial.contains(pkg)&&BrightnessControlOptions.validPackage(pkg))others.add(pkg);}
        getWindow().setDecorFitsSystemWindows(false);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);getWindow().setNavigationBarContrastEnforced(false);getWindow().setStatusBarContrastEnforced(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root=column();root.setBackgroundColor(bg);root.setFocusableInTouchMode(true);setContentView(root);
        // PhoneWindow may have no DecorView until content is installed.
        if(getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        root.setOnApplyWindowInsetsListener((v,insets)->applyInsets(v,insets));
        page=root;configureTransitions();
        LinearLayout bar=row();bar.setPadding(dp(10),dp(6),dp(18),dp(6));root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        View back=new View(this){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);protected void onDraw(Canvas c){super.onDraw(c);MainActivity.drawNavigationArrow(c,p,getWidth(),getHeight(),dp(24),ink,true);}};
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x3091b3ff:0x203265df),shape(Color.TRANSPARENT,24),shape(Color.WHITE,24)));back.setClipToOutline(true);back.setContentDescription(tr("返回"));back.setClickable(true);back.setFocusable(true);back.setOnClickListener(v->done());bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text(this.title,18,ink);title.setPadding(0,dp(4),0,dp(4));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setAutoSizeTextTypeUniformWithConfiguration(12,18,1,android.util.TypedValue.COMPLEX_UNIT_SP);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(6);bar.addView(title,tp);Button done=button("完成",this::done);done.setTextSize(12);done.setIncludeFontPadding(false);done.setGravity(Gravity.CENTER);done.setPadding(dp(10),0,dp(10),0);bar.addView(done,new LinearLayout.LayoutParams(dp(76),dp(48)));
        LinearLayout controls=column();controls.setPadding(dp(18),dp(8),dp(18),dp(8));root.addView(controls,new LinearLayout.LayoutParams(-1,-2));LinearLayout searchBox=row();searchBox.setPadding(dp(8),0,dp(8),0);searchBox.setBackground(shape(0xffeef2fa,28));controls.addView(searchBox,new LinearLayout.LayoutParams(-1,-2));
        View glyph=new View(this){final Paint p=new Paint(3);protected void onDraw(Canvas c){p.setColor(muted);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.8f));float x=getWidth()/2f,y=getHeight()/2f;c.drawCircle(x-dp(2),y-dp(2),dp(6),p);c.drawLine(x+dp(3),y+dp(3),x+dp(8),y+dp(8),p);}};glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);searchBox.addView(glyph,new LinearLayout.LayoutParams(dp(36),dp(48)));
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(16);search.setTextColor(ink);search.setHintTextColor(muted);search.setHint(tr("正在读取应用…"));search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(2),dp(9),dp(2),dp(9));search.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);searchBox.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=text("×",22,muted);clear.setGravity(Gravity.CENTER);clear.setContentDescription(tr("清空搜索"));clear.setOnClickListener(v->search.setText(""));searchBox.addView(clear,new LinearLayout.LayoutParams(dp(40),dp(48)));
        list=new ApplicationRows();list.setDivider(null);list.setSelector(new ColorDrawable(Color.TRANSPARENT));list.setVerticalScrollBarEnabled(false);list.setScrollIndicators(0);list.setClipToPadding(false);list.setPadding(dp(18),0,dp(18),dp(12));list.setBackgroundColor(bg);
        LinearLayout scrolling=column();LinearLayout bulk=row();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(48));bp.topMargin=dp(10);scrolling.addView(bulk,bp);LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1);left.rightMargin=dp(4);bulk.addView(button("选中视频应用",()->bulk(true)),left);LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1);right.leftMargin=dp(4);bulk.addView(button("选中游戏应用",()->bulk(false)),right);
        hint=text("",12,muted);hint.setPadding(0,dp(10),0,dp(6));hint.setVisibility(View.GONE);scrolling.addView(hint);count=text("",13,muted);count.setPadding(dp(4),dp(8),0,dp(6));LinearLayout summary=row();scrolling.addView(summary);summary.addView(count,new LinearLayout.LayoutParams(0,-2,1));TextView raw=text("按包名添加",12,blue);raw.setPadding(dp(8),dp(12),dp(4),dp(12));raw.setOnClickListener(v->addPackage());summary.addView(raw);
        list.addHeaderView(scrolling,null,false);adapter=new Rows();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));root.requestApplyInsets();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void onTextChanged(CharSequence s,int a,int before,int count){if(adapter!=null)adapter.filter();}public void afterTextChanged(Editable e){}});
        search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){InputMethodManager ime=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(ime!=null)ime.hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();return true;}return false;});if(saved!=null)search.setText(saved.getString("query",""));updateCount();
        transitionContent(true,null);
    }
    void updateCount(){count.setText(selected.size()+tr(" 个已添加"));}
    void load(){final long epoch=generation;worker.execute(()->{
        List<Entry> entries=new ArrayList<>();String categoryWarning="";Set<String> gameNames=Collections.emptySet();try{gameNames=ApplicationCategories.games();}catch(Exception unavailable){categoryWarning="部分系统分类不可用，可手动选择。";}
        try{PackageManager pm=getPackageManager();Map<String,ApplicationInfo> apps=new LinkedHashMap<>();for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0))if(r.activityInfo!=null&&r.activityInfo.applicationInfo!=null)apps.put(r.activityInfo.packageName,r.activityInfo.applicationInfo);
            for(Map.Entry<String,ApplicationInfo> app:apps.entrySet()){if(Thread.currentThread().isInterrupted())return;String pkg=app.getKey();if(!BrightnessControlOptions.validPackage(pkg))continue;ApplicationInfo info=app.getValue();String label;try{label=pm.getApplicationLabel(info).toString();}catch(Exception absent){label=pkg;}entries.add(new Entry(pkg,label,info,ApplicationCategories.video(pkg,info.category==ApplicationInfo.CATEGORY_VIDEO),info.category==ApplicationInfo.CATEGORY_GAME||(info.flags&ApplicationInfo.FLAG_IS_GAME)!=0||gameNames.contains(pkg)));}
            final String notice=categoryWarning;ui.post(()->{if(!visible||generation!=epoch)return;Set<String> present=new HashSet<>();for(Entry e:entries)present.add(e.pkg);for(String pkg:selected)if(!present.contains(pkg))entries.add(new Entry(pkg,pkg,null,false,false));java.text.Collator order=java.text.Collator.getInstance(getResources().getConfiguration().getLocales().get(0));entries.sort((a,b)->{int chosen=Boolean.compare(selected.contains(b.pkg),selected.contains(a.pkg));return chosen!=0?chosen:order.compare(a.label,b.label);});all.clear();all.addAll(entries);loaded=true;search.setHint(tr("搜索应用名称或包名")+" · "+apps.size());hint.setText(tr(notice));hint.setVisibility(notice.isEmpty()?View.GONE:View.VISIBLE);adapter.filter();});
        }catch(Throwable error){ui.post(()->{if(visible&&generation==epoch){hint.setText(tr("应用列表读取失败，请返回后重试"));hint.setVisibility(View.VISIBLE);}});}
    });}
    String label(String pkg){for(Entry entry:all)if(entry.pkg.equals(pkg))return entry.label;return pkg;}
    void select(Collection<String> candidates,boolean report){
        if(confirmation!=null)return;
        ApplicationSelection.Change change=new ApplicationSelection.Change(selected,candidates,others);adapter.notifyDataSetChanged();
        if(change.added()==0){if(report)Toast.makeText(this,tr("没有新增选择，或已达到名单上限"),Toast.LENGTH_SHORT).show();return;}
        Runnable apply=()->{try{change.apply(selected,others,moved);for(String pkg:change.conflicts)assigned.remove(pkg);for(String pkg:selected){boolean exists=false;for(Entry entry:all)if(entry.pkg.equals(pkg))exists=true;if(!exists)all.add(0,new Entry(pkg,pkg,null,false,false));}updateCount();adapter.filter();if(report)Toast.makeText(this,tr("新增选中 ")+change.added()+tr(" 个应用"),Toast.LENGTH_SHORT).show();}catch(Exception stale){Toast.makeText(this,tr(stale.getMessage()),Toast.LENGTH_SHORT).show();}};
        if(change.conflicts.isEmpty()){apply.run();return;}
        StringBuilder message=new StringBuilder();int shown=0;for(String pkg:change.conflicts){if(shown++<4)message.append(label(pkg)).append(" · ").append(assigned.optString(pkg)).append("\n");}
        if(change.conflicts.size()>4)message.append(tr("共 ")).append(change.conflicts.size()).append(tr(" 个应用")).append("\n");
        message.append(tr("确认移入：")).append(title).append("\n\n").append(tr("确认后，新名单开启，原名单或分组中的开关自动关闭；取消则保持原样。返回后保存并应用生效。"));
        LinearLayout actions=row();actions.setGravity(Gravity.END);actions.setTag("actions");
        ResponsiveDialog body=new ResponsiveDialog(this,text("移动应用到当前名单？",20,ink),text(message.toString(),14,muted),actions);
        body.setPadding(dp(22),dp(20),dp(22),dp(18));body.setBackground(shape(bg,24));body.setClipToOutline(true);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(body).create();confirmation=dialog;
        Button cancel=button("取消",dialog::dismiss),confirm=button("确认移动",()->{dialog.dismiss();apply.run();});
        confirm.setTextColor(dark?bg:Color.WHITE);confirm.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff),shape(blue,12),shape(Color.WHITE,12)));
        dialogAction(actions,cancel);dialogAction(actions,confirm);
        ResponsiveDialog.configure(this,dialog.getWindow(),dp(440));if(dialog.getWindow()!=null)dialog.getWindow().setDimAmount(.32f);
        dialog.setOnDismissListener(ignored->confirmation=null);dialog.show();
    }
    void bulk(boolean video){List<String> candidates=new ArrayList<>();for(Entry e:all)if(video?e.video:e.game)candidates.add(e.pkg);if(candidates.isEmpty()){Toast.makeText(this,tr("未识别到此类应用，可手动选择"),Toast.LENGTH_SHORT).show();return;}select(candidates,true);}
    void addPackage(){EditText input=new EditText(this);input.setSingleLine(true);input.setHint("com.example.app");LinearLayout body=column();body.setPadding(dp(20),dp(8),dp(20),0);body.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));AlertDialog dialog=new AlertDialog.Builder(this).setTitle(tr("按包名添加")).setView(body).setNegativeButton(tr("取消"),null).setPositiveButton(tr("添加"),null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{String pkg=input.getText().toString().trim();if(!BrightnessControlOptions.validPackage(pkg)){Toast.makeText(this,tr("应用包名无效"),Toast.LENGTH_SHORT).show();return;}if(!ApplicationSelection.fits(selected,others,pkg)){Toast.makeText(this,tr("全部名单合计最多 64 个应用"),Toast.LENGTH_SHORT).show();return;}dialog.dismiss();select(Collections.singleton(pkg),false);}));dialog.show();}
    void done(){if(closing)return;closing=true;setResult(RESULT_OK,new Intent().putExtra("list",listId).putStringArrayListExtra("selected",new ArrayList<>(selected)).putStringArrayListExtra("moved",new ArrayList<>(moved)));transitionContent(false,this::finish);}
    @Override public void onBackPressed(){done();}
    @Override public void onResume(){super.onResume();if(search==null)return;visible=true;generation++;worker=Executors.newSingleThreadExecutor();if(!loaded)load();else adapter.notifyDataSetChanged();}
    @Override public void onPause(){cancelContentTransition();if(closing&&!isFinishing())finish();if(confirmation!=null)confirmation.dismiss();visible=false;generation++;if(worker!=null)worker.shutdownNow();iconPending.clear();super.onPause();}
    @Override public void onSaveInstanceState(Bundle out){out.putStringArrayList("selected",new ArrayList<>(selected));out.putStringArrayList("moved",new ArrayList<>(moved));if(search!=null)out.putString("query",search.getText().toString());super.onSaveInstanceState(out);}
    final class ApplicationRows extends ListView{
        final Paint card=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF bounds=new RectF();
        ApplicationRows(){super(ApplicationListActivity.this);}
        @Override protected void dispatchDraw(Canvas canvas){
            // A single background follows the whole data section; rows remain
            // transparent and recycled, including when the category header scrolls.
            int first=getFirstVisiblePosition(),header=getHeaderViewsCount(),end=header+(adapter==null?0:adapter.getCount());float top=Float.NaN,bottom=0,radius=dp(18);
            for(int i=0;i<getChildCount();i++){int position=first+i;if(position<header||position>=end)continue;View child=getChildAt(i);if(Float.isNaN(top))top=position==header?child.getTop():Math.min(-radius,child.getTop());bottom=position==end-1?child.getBottom():Math.max(getHeight()+radius,child.getBottom());}
            if(!Float.isNaN(top)){card.setColor(ThemePalette.surface(Color.WHITE,dark));bounds.set(getPaddingLeft(),top,getWidth()-getPaddingRight(),bottom);canvas.drawRoundRect(bounds,radius,radius,card);}
            super.dispatchDraw(canvas);
        }
    }
    final class Rows extends BaseAdapter{
        final List<Entry> shown=new ArrayList<>();void filter(){shown.clear();String q=search.getText().toString();for(Entry e:all)if(ApplicationSelection.matches(e.label,e.pkg,q))shown.add(e);notifyDataSetChanged();}
        public int getCount(){return shown.size();}public Object getItem(int n){return shown.get(n);}public long getItemId(int n){return n;}
        public View getView(int n,View recycled,ViewGroup parent){Holder h=recycled==null?new Holder():(Holder)recycled.getTag();Entry e=shown.get(n);h.pkg=e.pkg;h.label.setText(e.label+(!selected.contains(e.pkg)&&others.contains(e.pkg)?"\n"+tr("已在：")+assigned.optString(e.pkg):""));h.toggle.setOnCheckedChangeListener(null);h.toggle.setChecked(selected.contains(e.pkg));h.toggle.setContentDescription(e.label);h.toggle.setOnCheckedChangeListener((v,on)->{if(on){h.toggle.setOnCheckedChangeListener(null);h.toggle.setChecked(selected.contains(e.pkg));if(!ApplicationSelection.fits(selected,others,e.pkg)){Toast.makeText(ApplicationListActivity.this,tr("全部名单合计最多 64 个应用"),Toast.LENGTH_SHORT).show();adapter.notifyDataSetChanged();return;}select(Collections.singleton(e.pkg),false);}else{selected.remove(e.pkg);updateCount();adapter.notifyDataSetChanged();}});Drawable icon=icons.get(e.pkg);h.icon.setImageDrawable(icon==null?getPackageManager().getDefaultActivityIcon():icon);
            if(icon==null&&e.info!=null&&visible&&worker!=null&&!worker.isShutdown()&&iconPending.add(e.pkg)){final long epoch=generation;worker.execute(()->{Drawable loaded;try{loaded=e.info.loadIcon(getPackageManager());}catch(Exception absent){loaded=null;}final Drawable result=loaded;ui.post(()->{if(!visible||generation!=epoch)return;iconPending.remove(e.pkg);if(result!=null){icons.put(e.pkg,result);if(h.pkg.equals(e.pkg))h.icon.setImageDrawable(result);}});});}return h.root;
        }
    }
    final class Holder{String pkg="";final LinearLayout root=row();final ImageView icon=new ImageView(ApplicationListActivity.this);final TextView label=text("",17,ink);final Switch toggle=new Switch(ApplicationListActivity.this);
        Holder(){root.setTag(this);root.setPadding(dp(14),dp(12),dp(14),dp(12));root.setMinimumHeight(dp(80));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setBackground(shape(0xfff2f4f8,12));icon.setClipToOutline(true);root.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(14);lp.rightMargin=dp(8);root.addView(label,lp);toggle.setShowText(false);toggle.setMinimumHeight(dp(48));root.addView(toggle,new LinearLayout.LayoutParams(-2,dp(48)));root.setOnClickListener(v->toggle.performClick());}
    }
}
