package top.rongshangs.lumacurve.refactor;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    static final int INK=0xff24303c,MUTED=0xff778390,BLUE=0xff3265df,BG=0xfffafafa;
    final ExecutorService worker=Executors.newSingleThreadExecutor(),network=Executors.newSingleThreadExecutor();final Handler ui=new Handler(Looper.getMainLooper());
    FrameLayout content;LinearLayout root,header,pages[]=new LinearLayout[4];
    TextView badge,note,stateTitle,luxReading,nitReading,flow,thermalState,userState,compatibility,logText,curveHint,memoryText,memoryStatus;
    LinearLayout nav[]=new LinearLayout[4]; NavIcon navIcons[]=new NavIcon[4];TextView navNames[]=new TextView[4];Button apply,stop;Switch thermalSwitch;SeekBar ceilingSlider;TextView ceilingText;
    float factors[]={1,1,1,1},factoryLux[],factoryNit[],minimum,maximum,thermalCeiling=43,memoryStrength=1;boolean thermalRelax,memoryEnabled=true,busy,visible,dirty,destroyed;
    Switch memorySwitch;SeekBar memorySlider;PipelineBoard pipeline;boolean english,rereadPending,legacyChecked;String permissionNotice="",loadedBaseline="";boolean permissionDialogVisible,permissionBlocked;ScrollView logScroller;
    JSONObject runtime,lastAnswer;CurveView stateGraph,settingsGraph;int page;
    long memoryWindow=1500,brightenDelay=1500,darkenDelay=5000;float memoryLuxRange=.3f,thermalCooling=1;int refreshSeconds=2;boolean responseOverride,smallBrightenOverride,updateChecked,updateBusy,firmwarePrompted,legacyDialogVisible;long smallBrightenDelay=5000;JSONArray pendingLegacy;String legacyNotice="";
    Switch responseSwitch,smallBrightenSwitch,lowLightSwitch;boolean lowLightStability;TextView branchOverview;SeekBar smallBrightenSlider;TextView smallBrightenLabel;SeekBar memoryWindowSlider,memoryRangeSlider,coolingSlider,brightenSlider,darkenSlider,refreshSlider;
    TextView memoryWindowLabel,memoryRangeLabel,coolingLabel,brightenLabel,darkenLabel,refreshLabel,updateLabel;
    JSONObject pendingUpdate;AlertDialog updateDialog;
    LinearLayout thanksList;TextView thanksStatus;boolean thanksBusy,autoScroll=true;long thanksChecked;
    float lowLightLimit=50;long lowLightBrighten=3000,lowLightDarken=4000;
    SeekBar lowLimitSlider,lowBrightSlider,lowDarkSlider;TextView lowLimitLabel,lowBrightLabel,lowDarkLabel;
    OutdoorOptions outdoorOptions=new OutdoorOptions();Switch outdoorSwitch,hbmSwitch,rangeSwitch;TextView outdoorStatus;final SeekBar[] outdoorSliders=new SeekBar[OutdoorOptions.KEYS.length];final TextView[] outdoorLabels=new TextView[OutdoorOptions.KEYS.length];
    AdvancedOptions advanced=new AdvancedOptions();
    final Switch[] advancedSwitches=new Switch[AdvancedOptions.GROUPS.length];final SeekBar[] advancedSliders=new SeekBar[AdvancedOptions.KEYS.length];final TextView[] advancedLabels=new TextView[AdvancedOptions.KEYS.length];
    TextView advancedStatus,unsavedHint;final TextView[] settingHints=new TextView[6];Switch logFollow;
    LinearLayout settingsHome,settingsTarget,bottomNav;final LinearLayout[] settingGroups=new LinearLayout[6];final LinearLayout[] settingScreens=new LinearLayout[6];final Button[] settingApply=new Button[6];int settingsGroup=-1;float curveFloor;
    View displayedPage;long pageTransition;
    java.lang.Process bridge;BufferedReader bridgeReader;BufferedWriter bridgeWriter;
    final Runnable tick=()->{if(visible){if(!busy&&!permissionBlocked&&(page==0||page==2))run("inspect",null);ui.postDelayed(this.tick,page==0?refreshSeconds*1000:5000);}};
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    String tr(String value){return UiText.translate(value,english);}
    class LocalText extends TextView{
        LocalText(){super(MainActivity.this);}
        @Override public void setText(CharSequence value,BufferType type){super.setText(value==null?null:tr(value.toString()),type);}
    }
    class LocalButton extends Button{
        LocalButton(){super(MainActivity.this);setStateListAnimator(null);setElevation(0);setTranslationZ(0);}
        @Override public void setText(CharSequence value,BufferType type){super.setText(value==null?null:tr(value.toString()),type);}
    }
    TextView text(String value,int size,int color){TextView t=new LocalText();t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));return t;}
    RippleDrawable ripple(int color,float radius){return new RippleDrawable(ColorStateList.valueOf(0x203265df),background(color,radius),background(Color.WHITE,radius));}
    ImageView image(String asset,int size,float radius){ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setBackground(background(0xffeef2ff,radius));v.setClipToOutline(true);
        try{if(asset==null)v.setImageDrawable(getDrawable(getResources().getIdentifier("icon","drawable",getPackageName())));else try(InputStream input=getAssets().open(asset)){v.setImageBitmap(BitmapFactory.decodeStream(input));}}catch(IOException ignored){}
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return v;}
    AlertDialog dialog(String title,View body){LinearLayout box=column();box.setPadding(dp(22),dp(20),dp(22),dp(18));box.setBackground(background(BG,24));box.addView(text(title,20,INK));
        if(body!=null){LinearLayout.LayoutParams lp=body.getLayoutParams() instanceof LinearLayout.LayoutParams?(LinearLayout.LayoutParams)body.getLayoutParams():new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);box.addView(body,lp);}
        LinearLayout actions=row(box);actions.setGravity(Gravity.END);actions.setTag("actions");AlertDialog d=new AlertDialog.Builder(this).setView(box).create();
        box.setClipToOutline(true);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.getDecorView().setElevation(0);w.setGravity(Gravity.CENTER);w.setDimAmount(.32f);w.setWindowAnimations(0);WindowManager.LayoutParams params=w.getAttributes();params.width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(440));params.height=WindowManager.LayoutParams.WRAP_CONTENT;w.setAttributes(params);}
        // Animate the content itself: application styles are not valid WMS animation resources.
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){box.setAlpha(0);d.setOnShowListener(v->box.animate().alpha(1).setDuration(180).start());}return d;}
    void dialogButton(AlertDialog d,String label,Runnable task,boolean primary){View decor=d.getWindow().getDecorView();LinearLayout actions=(LinearLayout)decor.findViewWithTag("actions");
        Button b=action(label,()->{d.dismiss();if(task!=null)task.run();});b.setTextColor(primary?Color.WHITE:BLUE);b.setBackground(ripple(primary?BLUE:0xffeef2fa,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.setMargins(dp(4),dp(14),dp(4),0);actions.addView(b,lp);}
    void messageDialog(String title,String message,String yes,Runnable task){AlertDialog d=dialog(title,text(message,14,MUTED));d.show();dialogButton(d,"取消",null,false);dialogButton(d,yes,task,true);}
    void operationFailure(String command,Throwable error){
        String detail=error.getMessage();if(detail==null||detail.isEmpty())detail=error.toString();
        while(detail.startsWith("java.io.IOException: "))detail=detail.substring("java.io.IOException: ".length());
        if(!command.equals("apply")&&!command.equals("stop")&&!command.equals("reset-memory")){show(detail);return;}
        show("");ScrollView scroll=new ScrollView(this);scroll.addView(text(detail,14,MUTED));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(230)));
        AlertDialog d=dialog("设置未完成",scroll);d.show();dialogButton(d,"知道了",null,false);dialogButton(d,"导出分析包",()->run("export",null),true);
    }
    LinearLayout column(){LinearLayout b=new LinearLayout(this);b.setOrientation(1);return b;}
    GradientDrawable background(int color,float radius){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(radius));return b;}
    LinearLayout card(LinearLayout parent){LinearLayout b=column();b.setPadding(dp(18),dp(17),dp(18),dp(17));b.setBackground(background(Color.WHITE,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);parent.addView(b,lp);return b;}
    LinearLayout row(LinearLayout parent){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);parent.addView(r,new LinearLayout.LayoutParams(-1,-2));return r;}
    Button action(String name,Runnable task){Button b=new LocalButton();b.setText(name);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(BLUE);b.setBackground(ripple(0xffeef2fa,12));b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(10),0,dp(10),0);b.setOnClickListener(v->task.run());return b;}
    Button button(String name,Runnable task,LinearLayout parent){Button b=action(name,task);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.topMargin=dp(8);lp.bottomMargin=dp(4);parent.addView(b,lp);return b;}
    Button compact(String name,Runnable task,LinearLayout parent){Button b=action(name,task);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(3),dp(6),dp(3),dp(4));parent.addView(b,lp);return b;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);autoScroll=getPreferences(0).getBoolean("log_auto_scroll",true);refreshSeconds=getPreferences(0).getInt("refresh_seconds",2);english=!getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        try{factors=CurvePlan.factors(getPreferences(0).getString("draft","1,1,1,1"));}catch(Exception ignored){}
        curveFloor=getPreferences(0).getFloat("draft_floor",0);
        root=column();root.setBackgroundColor(BG);setContentView(root);
        header=row(root);header.setPadding(dp(18),0,dp(18),0);header.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(64)));
        ImageView icon=image(null,38,12);header.addView(icon);
        LinearLayout brand=column();LinearLayout.LayoutParams brandLp=new LinearLayout.LayoutParams(0,-2,1);brandLp.leftMargin=dp(12);header.addView(brand,brandLp);brand.addView(text("HyperLux",20,INK));
        badge=text("连接中",12,BLUE);badge.setPadding(dp(10),dp(5),dp(10),dp(5));badge.setBackground(background(0xffedf3ff,10));header.addView(badge);
        note=text("",12,MUTED);note.setVisibility(View.GONE);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<4;i++){LinearLayout b=column();pages[i]=b;if(i==0||i==2){b.setPadding(dp(16),dp(8),dp(16),dp(6));content.addView(b,new FrameLayout.LayoutParams(-1,-1));}else{ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);b.setPadding(dp(18),dp(10),dp(18),dp(22));scroll.addView(b);content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));scroll.setVisibility(View.GONE);}}
        makeStatus();makeSettings();makeLogs();makeAbout();
        LinearLayout bottom=row(root);bottomNav=bottom;bottom.setBaselineAligned(false);bottom.setPadding(dp(8),dp(8),dp(8),dp(8));String[] names={"状态","设置","日志","关于"};
        for(int i=0;i<4;i++){final int index=i;LinearLayout tab=column();tab.setBaselineAligned(false);tab.setGravity(Gravity.CENTER);tab.setBackground(ripple(Color.TRANSPARENT,14));tab.setClickable(true);tab.setOnClickListener(v->select(index));
            NavIcon iconView=new NavIcon(i);tab.addView(iconView,new LinearLayout.LayoutParams(dp(25),dp(25)));TextView name=text(names[i],12,MUTED);name.setGravity(Gravity.CENTER);name.setIncludeFontPadding(false);name.setPadding(0,dp(4),0,0);tab.addView(name,new LinearLayout.LayoutParams(-1,dp(22)));bottom.addView(tab,new LinearLayout.LayoutParams(0,dp(58),1));nav[i]=tab;navIcons[i]=iconView;navNames[i]=name;}
        select(saved==null?0:saved.getInt("page",0));apply.setEnabled(false);
    }
    void makeStatus(){
        stateTitle=text("",16,INK);luxReading=text("",20,BLUE);nitReading=text("",20,INK);flow=text("",12,MUTED);thermalState=text("",12,INK);userState=text("",12,MUTED);
        branchOverview=text("亮度分支：等待读取 · 点击查看",12,BLUE);branchOverview.setMaxLines(2);branchOverview.setEllipsize(android.text.TextUtils.TruncateAt.END);branchOverview.setGravity(Gravity.CENTER);branchOverview.setIncludeFontPadding(false);branchOverview.setPadding(dp(8),0,dp(8),0);branchOverview.setBackground(ripple(0xffeef3ff,10));branchOverview.setClipToOutline(true);branchOverview.setOnClickListener(v->branches());
        pipeline=new PipelineBoard();pages[0].addView(pipeline,new LinearLayout.LayoutParams(-1,0,1));
    }
    SeekBar parameter(LinearLayout parent,int maximum,int initial,java.util.function.IntConsumer changed){SeekBar s=new SeekBar(this);s.setMax(maximum);s.setProgress(initial);parent.addView(s,new LinearLayout.LayoutParams(-1,dp(38)));s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar v,int p,boolean user){if(user){changed.accept(p);dirty=true;drawCurve();}}public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}});return s;}
    void makeSettings(){
        LinearLayout b=card(pages[1]);b.setPadding(dp(16),dp(13),dp(16),dp(13));LinearLayout engineHeading=row(b);engineHeading.setBaselineAligned(false);TextView engineTitle=text("引擎控制",18,INK);engineTitle.setIncludeFontPadding(false);engineHeading.addView(engineTitle,new LinearLayout.LayoutParams(0,-2,1));unsavedHint=text("有未保存的设置",12,0xffbd3434);unsavedHint.setIncludeFontPadding(false);unsavedHint.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);engineHeading.addView(unsavedHint,new LinearLayout.LayoutParams(-2,-2));unsavedHint.setVisibility(View.GONE);LinearLayout r=row(b);apply=compact("保存并应用",this::submit,r);stop=compact("停用并恢复",()->run("stop",null),r);
        LinearLayout files=row(b);compact("导出配置",this::exportConfiguration,files);compact("导入配置",this::importConfiguration,files);
        settingsHome=column();pages[1].addView(settingsHome);
        String[] names={"亮度曲线与偏好","户外高亮","环境变化与确认","亮度过渡","场景与温控","运行状态与界面"};
        String[] descriptions={"暗处亮度下限、曲线预设与手动记忆","强光目标、可用范围与 HBM 预算","变化阈值、主辅光感确认与暗光稳定","分别调整变亮和变暗的过渡时长","触摸遮挡、手动阳光屏与温控策略","参数生效情况与界面刷新"};
        for(int i=0;i<settingGroups.length;i++){
            final int group=i;LinearLayout entry=card(settingsHome);entry.setPadding(dp(16),dp(11),dp(12),dp(11));((LinearLayout.LayoutParams)entry.getLayoutParams()).bottomMargin=dp(8);entry.setBackground(ripple(Color.WHITE,18));entry.setClipToOutline(true);entry.setClickable(true);entry.setOnClickListener(v->showSettingsGroup(group));LinearLayout line=row(entry);line.setBaselineAligned(false);LinearLayout labels=column();line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView name=text(names[i],16,INK);name.setIncludeFontPadding(false);name.setPadding(0,0,0,dp(3));labels.addView(name);TextView description=text(descriptions[i],12,MUTED);description.setIncludeFontPadding(false);description.setPadding(0,0,0,0);labels.addView(description);line.addView(new NavigationArrow(false),new LinearLayout.LayoutParams(dp(28),dp(32)));
            // Each category owns a separate screen and scroll position, outside the settings home.
            LinearLayout screen=column();screen.setBackgroundColor(BG);settingScreens[i]=screen;content.addView(screen,new FrameLayout.LayoutParams(-1,-1));screen.setVisibility(View.GONE);
            LinearLayout bar=row(screen);bar.setPadding(dp(10),dp(6),dp(18),dp(6));bar.setBaselineAligned(false);NavigationArrow back=new NavigationArrow(true);back.setBackground(ripple(Color.TRANSPARENT,24));back.setClipToOutline(true);back.setClickable(true);back.setFocusable(true);back.setContentDescription(tr("返回设置"));back.setOnClickListener(v->showSettingsGroup(-1));bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView heading=text(names[i],18,INK);LinearLayout.LayoutParams title=new LinearLayout.LayoutParams(0,-2,1);title.leftMargin=dp(6);bar.addView(heading,title);
            ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));settingGroups[i]=column();settingGroups[i].setPadding(dp(18),dp(10),dp(18),dp(12));scroll.addView(settingGroups[i]);
            LinearLayout footer=column();footer.setPadding(dp(18),dp(4),dp(18),dp(10));screen.addView(footer);settingHints[i]=text("有未保存的设置",12,0xffbd3434);footer.addView(settingHints[i]);settingHints[i].setVisibility(View.GONE);settingApply[i]=button("保存并应用",this::submit,footer);settingApply[i].setEnabled(false);
        }
        settingsTarget=settingGroups[0];makeCurveSettings();makeMemorySettings();
        settingsTarget=settingGroups[1];makeOutdoorSettings();
        settingsTarget=settingGroups[2];makeAdvancedSettings(0);makeMainResponseSettings();makeAdvancedSettings(1);makeLowLightSettings();
        settingsTarget=settingGroups[3];makeAdvancedSettings(2);
        settingsTarget=settingGroups[4];makeAdvancedSettings(4);makeAdvancedSettings(3);makeThermalSettings();
        settingsTarget=settingGroups[5];makeAdvancedStatus();makeRefreshSettings();
    }
    void showSettingsGroup(int group){settingsGroup=group;header.setVisibility(group<0?View.VISIBLE:View.GONE);if(bottomNav!=null)bottomNav.setVisibility(group<0?View.VISIBLE:View.GONE);transitionPage(group<0?content.getChildAt(1):settingScreens[group],group<0?-1:1);drawCurve();}
    void transitionPage(View target,int direction){
        if(displayedPage==target)return;
        final long generation=++pageTransition;View previous=displayedPage;displayedPage=target;
        boolean animate=visible&&previous!=null&&android.animation.ValueAnimator.areAnimatorsEnabled();
        for(int i=0;i<content.getChildCount();i++){View child=content.getChildAt(i);child.animate().cancel();child.animate().setListener(null).withEndAction(null);if(child!=target&&(!animate||child!=previous)){child.setVisibility(View.GONE);child.setAlpha(1);child.setTranslationX(0);}}
        target.setVisibility(View.VISIBLE);
        if(!animate){target.setAlpha(1);target.setTranslationX(0);return;}
        float shift=dp(12)*Integer.signum(direction);target.setAlpha(0);target.setTranslationX(shift);previous.animate().alpha(0).translationX(-shift).setDuration(120).withEndAction(()->{if(pageTransition==generation&&displayedPage!=previous){previous.setVisibility(View.GONE);previous.setAlpha(1);previous.setTranslationX(0);}}).start();
        target.animate().alpha(1).translationX(0).setInterpolator(new android.view.animation.DecelerateInterpolator()).setDuration(180).start();
    }
    void finishPageTransition(){++pageTransition;for(int i=0;i<content.getChildCount();i++){View child=content.getChildAt(i);child.animate().cancel();child.animate().setListener(null).withEndAction(null);child.setAlpha(1);child.setTranslationX(0);child.setVisibility(child==displayedPage?View.VISIBLE:View.GONE);}}
    @Override public void onBackPressed(){if(page==1&&settingsGroup>=0){showSettingsGroup(-1);return;}super.onBackPressed();}
    void makeCurveSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("照度与亮度曲线",18,INK));b.addView(text("拖动节点调整亮度，点击节点输入数值。",13,MUTED));
        settingsGraph=new CurveView(true);b.addView(settingsGraph,new LinearLayout.LayoutParams(-1,dp(235)));curveHint=text("连接后显示节点范围",12,MUTED);b.addView(curveHint);
        b.addView(text("默认基准取自本设备的系统曲线。编辑保留原曲线形状与完整节点；亮度须随照度不递减，高照度末端保持原始值。",12,MUTED));
        b.addView(text("最左侧节点可上移，设置暗处曲线的亮度下限。上限由下一个节点决定；需要更高时先提高下一个节点。系统手动选择和温控仍优先。",12,MUTED));
        LinearLayout presets=row(b);compact("柔和",()->preset(new float[]{.7f,.8f,.95f,1}),presets);compact("系统默认",()->preset(new float[]{1,1,1,1}),presets);compact("稍亮",()->preset(new float[]{1.2f,1.15f,1.05f,1}),presets);
        presets=row(b);compact("保存为预设",this::savePreset,presets);compact("我的预设",this::pickPreset,presets);
    }
    void makeOutdoorSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("户外高亮增强",18,INK));
        outdoorSwitch=new Switch(this);outdoorSwitch.setText(tr("持续强光下提高亮度"));outdoorSwitch.setTextSize(14);b.addView(outdoorSwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        b.addView(text("确认持续强光后，在系统曲线目标之上增加亮度，保留系统平滑过渡。离开强光、锁屏、手动调节或温度较高时退出。默认关闭。",12,MUTED));
        outdoorStatus=text("等待读取户外高亮条件",13,BLUE);b.addView(outdoorStatus);button("查看高亮条件",this::branches,b);
        for(int i:new int[]{0,1,5})outdoorParameter(b,i);
        LinearLayout details=column();b.addView(details);
        for(int i:new int[]{2,3,4,6,7})outdoorParameter(details,i);
        hbmSwitch=new Switch(this);hbmSwitch.setText(tr("调整自动 HBM 触发与预算"));hbmSwitch.setTextSize(14);details.addView(hbmSwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        details.addView(text("仅支持实际启用 HBM 计时的设备。预算最多为系统的 2 倍，且不超过原时间窗口；保留使用历史、省电与温控条件。0 预算不会被当作无限制。",12,MUTED));for(int i:new int[]{8,9})outdoorParameter(details,i);
        rangeSwitch=new Switch(this);rangeSwitch.setText(tr("开放系统映射内的高亮范围"));rangeSwitch.setTextSize(14);details.addView(rangeSwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        details.addView(text("实验选项：满足户外条件时放宽软件峰值范围，最高仍受本机亮度映射限制。HBM 时间不足时不开放，HDR、画面限亮及驱动保护继续生效；不保证额外提升。",12,MUTED));
        outdoorSwitch.setOnCheckedChangeListener((v,on)->{outdoorOptions.flags[0]=on;if(!on){outdoorOptions.flags[1]=false;outdoorOptions.flags[2]=false;}if(v.isPressed())dirty=true;drawOutdoor();});
        hbmSwitch.setOnCheckedChangeListener((v,on)->{outdoorOptions.flags[1]=on;if(v.isPressed())dirty=true;drawOutdoor();});rangeSwitch.setOnCheckedChangeListener((v,on)->{outdoorOptions.flags[2]=on;if(v.isPressed())dirty=true;drawOutdoor();});
    }
    void outdoorParameter(LinearLayout parent,int key){outdoorLabels[key]=text("",13,INK);parent.addView(outdoorLabels[key]);outdoorSliders[key]=parameter(parent,(int)Math.round((OutdoorOptions.MAX[key]-OutdoorOptions.MIN[key])/OutdoorOptions.STEP[key]),(int)Math.round((OutdoorOptions.DEFAULT[key]-OutdoorOptions.MIN[key])/OutdoorOptions.STEP[key]),value->{outdoorOptions.values[key]=OutdoorOptions.MIN[key]+value*OutdoorOptions.STEP[key];if(outdoorOptions.values[1]<=outdoorOptions.values[0])outdoorOptions.values[1]=Math.ceil((outdoorOptions.values[0]+2000)/2000)*2000;});}
    void drawOutdoor(){if(outdoorSwitch==null)return;
        boolean supported=runtime!=null&&runtime.optBoolean("outdoor_supported"),hbm=runtime!=null&&runtime.optBoolean("outdoor_hbm_supported"),range=runtime!=null&&runtime.optBoolean("outdoor_range_supported");
        outdoorSwitch.setChecked(outdoorOptions.flags[0]);outdoorSwitch.setEnabled(supported||outdoorOptions.flags[0]);hbmSwitch.setChecked(outdoorOptions.flags[1]);hbmSwitch.setEnabled(outdoorOptions.flags[0]&&(hbm||outdoorOptions.flags[1]));rangeSwitch.setChecked(outdoorOptions.flags[2]);rangeSwitch.setEnabled(outdoorOptions.flags[0]&&(range||outdoorOptions.flags[2]));
        String[] labels={"进入照度","全强度照度","进入确认","退出确认","退出照度比例","增强强度","单次最长增强","增强冷却","HBM 触发照度倍率","HBM 时间预算倍率"};
        for(int i=0;i<outdoorSliders.length;i++)if(outdoorSliders[i]!=null){double value=outdoorOptions.values[i];boolean time=i==2||i==3||i==6||i==7;outdoorLabels[i].setText(tr(labels[i])+"："+format(time?value/1000:i==4||i==5?value*100:value)+(time?tr(" 秒"):i==0||i==1?" lux":i==4||i==5?"%":"×"));outdoorSliders[i].setProgress((int)Math.round((value-OutdoorOptions.MIN[i])/OutdoorOptions.STEP[i]));enableParameter(outdoorSliders[i],outdoorLabels[i],supported&&outdoorOptions.flags[0]&&(i<8||hbm&&outdoorOptions.flags[1]));}
        JSONObject state=runtime==null?null:runtime.optJSONObject("outdoor");outdoorStatus.setText(state==null?tr("等待读取户外高亮条件"):outdoorSummary(state));drawDraftState();
    }
    String outdoorSummary(JSONObject state){String message=tr(state.optString("text","未取得"));if(state.has("blocking_condition"))message+=" · "+tr(OutdoorController.description(state.optString("blocking_condition")));if(state.has("session_left_ms"))message+=" · "+tr("剩余 ")+format(state.optDouble("session_left_ms")/1000)+tr(" 秒");return message;}
    String outdoorDetails(JSONObject state){StringBuilder out=new StringBuilder(outdoorSummary(state));
        String[][] fields={{"normal_max","HBM 亮度分界"},{"native_range_max","系统原范围上限"},{"effective_range_max","当前范围上限"},{"dynamic_native_max","照度动态范围原上限"},{"dynamic_effective_max","照度动态范围现上限"},{"device_mapping_max","设备映射上限"},{"display_range_max","显示范围上限"},{"clamper_max","最终限亮器上限"},{"requested_brightness","户外请求目标"},{"actual_brightness","当前屏幕亮度"}};
        for(String[] field:fields)if(state.has(field[0]))out.append("\n").append(tr(field[1])).append(": ").append(format(state.optDouble(field[0])*100)).append("%");
        out.append("\n").append(tr(state.optBoolean("controller_managed")?"本机使用 HBM 计时":"本机未启用 HBM 计时，不能据此推断没有其他限亮"));
        if(state.has("original_minimum_lux"))out.append("\n").append(tr("HBM 原触发照度")).append(": ").append(format(state.optDouble("original_minimum_lux"))).append(" lux");
        if(state.has("effective_minimumLux"))out.append("\n").append(tr("HBM 当前触发照度")).append(": ").append(format(state.optDouble("effective_minimumLux"))).append(" lux");
        if(state.has("hbm_remaining_estimate_ms"))out.append("\n").append(tr("HBM 剩余预算估值")).append(": ").append(format(state.optDouble("hbm_remaining_estimate_ms")/1000)).append(tr(" 秒"));
        JSONArray stages=state.optJSONArray("limit_trace");if(stages!=null){out.append("\n\n").append(tr("最近观察到的输出限制"));long now=SystemClock.uptimeMillis();int shown=0;for(int i=stages.length()-1;i>=0&&shown<6;i--){JSONObject stage=stages.optJSONObject(i);if(stage==null||!stage.optBoolean("limited")||now-stage.optLong("uptime_ms")>60000)continue;out.append("\n").append(limitStage(stage.optString("stage"))).append(": ").append(format(stage.optDouble("before")*100)).append("% → ").append(format(stage.optDouble("after")*100)).append("% · ").append(format((now-stage.optLong("uptime_ms"))/1000d)).append(tr(" 秒前"));shown++;}if(shown==0)out.append("\n").append(tr("最近一分钟未观察到降亮，不代表驱动没有限制"));}
        return out.toString();
    }
    String limitStage(String stage){switch(stage){case "adjustBrightnessByOpr":return tr("画面灰阶限亮");case "adjustBrightnessByThermal":return tr("显示温控");case "adjustBrightnessByBattery":return tr("电池限亮");case "adjustBrightnessByPowerSaveMode":return tr("省电限亮");case "adjustBrightnessToPeak":return tr("峰值范围");case "adjustBrightnessByBcbc":return tr("画面亮度修正");case "adjustSdrBrightness":return tr("SDR 输出");default:return stage;}}
    void makeMemorySettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("手动偏好记忆",18,INK));memorySwitch=new Switch(this);memorySwitch.setText(tr("记住我的手动调整"));memorySwitch.setTextSize(14);memorySwitch.setShowText(false);b.addView(memorySwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        memorySwitch.setOnCheckedChangeListener((v,on)->{memoryEnabled=on;if(v.isPressed()){dirty=true;drawCurve();}});
        b.addView(text("手动亮度立即生效，也会影响附近照度的曲线。强度越低，每次记住的调整越少。",12,MUTED));
        memoryText=text("记忆强度 100%",13,INK);b.addView(memoryText);memorySlider=new SeekBar(this);memorySlider.setMax(99);memorySlider.setProgress(99);b.addView(memorySlider,new LinearLayout.LayoutParams(-1,dp(40)));
        memorySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){if(from){memoryStrength=(p+1)/100f;dirty=true;drawCurve();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        memoryWindowLabel=text("",13,INK);b.addView(memoryWindowLabel);memoryWindowSlider=parameter(b,5,2,value->memoryWindow=500+value*500);
        b.addView(text("间隔以内的连续拖动视为一次调整，避免重复累积。",12,MUTED));memoryRangeLabel=text("",13,INK);b.addView(memoryRangeLabel);memoryRangeSlider=parameter(b,8,4,value->memoryLuxRange=.1f+value*.05f);
        b.addView(text("在相近照度范围内延续同一次记忆；暗处至少保留 5 lux 的容差。",12,MUTED));
        memoryStatus=text("等待读取手动锚点",12,MUTED);b.addView(memoryStatus);
        button("清除当前手动记忆",()->messageDialog("清除手动记忆？","保留基础曲线和最近滑块位置，仅清除当前曲线的手动锚点。","清除",()->run("reset-memory",null)),b);
        b.addView(text("关闭后不再记入新调整，已有记忆保留。记忆可能被系统重置，不保证跨重启保留。",12,MUTED));
    }
    void makeThermalSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("温控",18,INK));thermalSwitch=new Switch(this);thermalSwitch.setText(tr("减少温控降亮"));thermalSwitch.setTextSize(14);thermalSwitch.setShowText(false);b.addView(thermalSwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        thermalSwitch.setOnCheckedChangeListener((v,on)->{thermalRelax=on;if(v.isPressed()){dirty=true;drawCurve();}});
        b.addView(text("默认关闭。开启后可减少显示层的温控限亮；严重过热或达到电池温度阈值时恢复系统策略。",12,MUTED));
        ceilingText=text("电池温度阈值：43℃（建议）",13,INK);b.addView(ceilingText);ceilingSlider=new SeekBar(this);ceilingSlider.setMax(7);ceilingSlider.setProgress(5);b.addView(ceilingSlider,new LinearLayout.LayoutParams(-1,dp(40)));
        ceilingSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){if(from){thermalCeiling=p+38;dirty=true;drawCurve();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        coolingLabel=text("",13,INK);b.addView(coolingLabel);coolingSlider=parameter(b,4,0,value->thermalCooling=1+value*.5f);
        b.addView(text("可设 38～45℃。达到阈值交回系统，冷却到设定幅度后再允许减少降亮。",12,MUTED));
    }
    void makeMainResponseSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("主光感确认",18,INK));responseSwitch=new Switch(this);responseSwitch.setText(tr("自定义变化确认时间"));responseSwitch.setTextSize(14);b.addView(responseSwitch,new LinearLayout.LayoutParams(-1,dp(48)));responseSwitch.setOnCheckedChangeListener((v,on)->{responseOverride=on;if(v.isPressed()){dirty=true;drawCurve();}});
        b.addView(text("默认沿用系统。光线持续越过变化阈值后再调节，等待越长越不易受短暂遮挡影响。",12,MUTED));
        brightenLabel=text("",13,INK);b.addView(brightenLabel);brightenSlider=parameter(b,19,2,value->brightenDelay=500+value*500);
        darkenLabel=text("",13,INK);b.addView(darkenLabel);darkenSlider=parameter(b,28,8,value->darkenDelay=1000+value*500);
        b.addView(text("变亮 0.5～10 秒，变暗 1～15 秒；实际等待受本机采样窗口限制。熄屏、HDR、闲置与驾驶沿用系统策略。",12,MUTED));
        smallBrightenSwitch=new Switch(this);smallBrightenSwitch.setText(tr("自定义微小变亮确认"));smallBrightenSwitch.setTextSize(14);b.addView(smallBrightenSwitch,new LinearLayout.LayoutParams(-1,dp(48)));smallBrightenSwitch.setOnCheckedChangeListener((v,on)->{smallBrightenOverride=on;if(v.isPressed()){dirty=true;drawCurve();}});
        smallBrightenLabel=text("",13,INK);b.addView(smallBrightenLabel);smallBrightenSlider=parameter(b,29,9,value->smallBrightenDelay=500+value*500);
        b.addView(text("0.5～15 秒。仅影响系统判定的微小变亮；关闭后沿用系统时间。",12,MUTED));
    }
    void makeLowLightSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("暗光稳定",18,INK));
        lowLightSwitch=new Switch(this);lowLightSwitch.setText(tr("暗光稳定"));lowLightSwitch.setTextSize(14);b.addView(lowLightSwitch,new LinearLayout.LayoutParams(-1,dp(48)));lowLightSwitch.setOnCheckedChangeListener((v,on)->{lowLightStability=on;if(v.isPressed()){dirty=true;drawCurve();}});
        b.addView(text("默认关闭。在设定照度以内增加主、辅助光感的变化确认时间。手动调节、熄屏、HDR、闲置与驾驶模式继续沿用系统。",12,MUTED));
        lowLimitLabel=text("",13,INK);b.addView(lowLimitLabel);lowLimitSlider=parameter(b,19,9,value->lowLightLimit=5+value*5);
        lowBrightLabel=text("",13,INK);b.addView(lowBrightLabel);lowBrightSlider=parameter(b,6,4,value->lowLightBrighten=1000+value*500);
        lowDarkLabel=text("",13,INK);b.addView(lowDarkLabel);lowDarkSlider=parameter(b,6,6,value->lowLightDarken=1000+value*500);
        b.addView(text("适用照度 5～100 lux；变亮、变暗最短确认各为 1～4 秒。保留系统更长等待；辅助光感采样窗口仅 5 秒，因此确认时间最高 4 秒。",12,MUTED));
        b.addView(text("新增等待受采样窗口限制；主光感最近实际确认时间显示在下方，系统更长等待仍保留。",12,MUTED));
    }
    void makeRefreshSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("界面",18,INK));refreshLabel=text("",13,INK);b.addView(refreshLabel);refreshSlider=parameter(b,4,refreshSeconds-1,value->{refreshSeconds=value+1;getPreferences(0).edit().putInt("refresh_seconds",refreshSeconds).apply();});
        b.addView(text("只影响状态显示，不改变亮度调节频率。",12,MUTED));
    }
    void makeAdvancedSettings(int g){
        String[] titles={"变化阈值","辅助光感确认","过渡动画","手动模式阳光屏","触摸遮挡保护"};
        String[] explanations={
            "默认沿用系统。倍率越大，需要更明显的照度变化才调节；最低照度差可过滤暗处的小波动。只在选定照度以内生效，高亮度、HDR 与驾驶场景保留系统阈值。",
            "辅助光感独立确认，避免背面遮挡影响持续过久。三种等待各为 0.5～4 秒，受系统 5 秒历史窗口约束；暗光稳定开启时取更长等待。",
            "保留系统感知亮度过渡，只调整时长。倍率越大越慢；变亮、变暗分别设置。仅自动亮度正常场景生效，已有过渡不重启。",
            "这里只调整系统手动模式阳光屏的进入、退出确认，不是自动亮度 HBM 的冷却时间。仍需要系统开启阳光屏并满足照度与亮度条件，不强制开启增强。",
            "系统检测到手指仍在遮挡区域时始终保留保护。这里只调整手指移开后的等待，可设 0～5 秒；0 秒仅取消移开后的附加等待，不关闭遮挡检测。"};
        int[] starts={0,6,9,11,13},ends={6,9,11,13,14};
        final int group=g;LinearLayout b=card(settingsTarget);b.addView(text(titles[g],18,INK));
            Switch toggle=new Switch(this);toggle.setText(tr("自定义"));toggle.setTextSize(14);toggle.setShowText(false);b.addView(toggle,new LinearLayout.LayoutParams(-1,dp(44)));advancedSwitches[g]=toggle;
            b.addView(text(explanations[g],12,MUTED));LinearLayout details=column();b.addView(details);
            for(int i=starts[g];i<ends[g];i++){final int key=i;advancedLabels[i]=text("",13,INK);details.addView(advancedLabels[i]);advancedSliders[i]=parameter(details,(int)Math.round((AdvancedOptions.MAX[i]-AdvancedOptions.MIN[i])/AdvancedOptions.STEP[i]),(int)Math.round((AdvancedOptions.DEFAULT[i]-AdvancedOptions.MIN[i])/AdvancedOptions.STEP[i]),value->advanced.values[key]=AdvancedOptions.MIN[key]+value*AdvancedOptions.STEP[key]);}
            toggle.setOnCheckedChangeListener((v,on)->{advanced.enabled[group]=on;if(v.isPressed())dirty=true;drawAdvanced();});
    }
    void makeAdvancedStatus(){
        LinearLayout b=card(settingsTarget);b.addView(text("参数生效状态",18,INK));advancedStatus=text("等待读取高级参数兼容状态",12,MUTED);b.addView(advancedStatus);
    }
    void drawAdvanced(){
        drawDraftState();
        String[] labels={"变亮阈值倍率","变暗阈值倍率","微小变亮阈值倍率","最小变亮照度差","最小变暗照度差","自定义阈值照度上限","辅助变亮确认","辅助变暗确认","辅助微小变亮确认","变亮时长倍率","变暗时长倍率","阳光屏进入确认","阳光屏退出确认","触摸释放等待"};
        int[] groups={0,0,0,0,0,0,1,1,1,2,2,3,3,4};
        for(int g=0;g<AdvancedOptions.GROUPS.length;g++)if(advancedSwitches[g]!=null){boolean supported=runtime!=null&&runtime.optBoolean(AdvancedOptions.GROUPS[g]+"_supported");if(advancedSwitches[g].isChecked()!=advanced.enabled[g])advancedSwitches[g].setChecked(advanced.enabled[g]);advancedSwitches[g].setEnabled(supported||advanced.enabled[g]);advancedSwitches[g].setText(tr(supported?"自定义":"此系统接口暂未兼容"));}
        for(int i=0;i<AdvancedOptions.KEYS.length;i++)if(advancedSliders[i]!=null){advancedSliders[i].setProgress((int)Math.round((advanced.values[i]-AdvancedOptions.MIN[i])/AdvancedOptions.STEP[i]));boolean timing=i>=6&&i<=12&&i!=9&&i!=10;advancedLabels[i].setText(labels[i]+"："+format(timing?advanced.values[i]/1000:advanced.values[i])+(timing||i==13?" 秒":i==3||i==4||i==5?" lux":"×"));enableParameter(advancedSliders[i],advancedLabels[i],advanced.enabled[groups[i]]&&runtime!=null&&runtime.optBoolean(AdvancedOptions.GROUPS[groups[i]]+"_supported"));}
        if(advancedStatus!=null)advancedStatus.setText(runtime==null?"等待读取高级参数兼容状态":"实际改写次数：阈值 "+runtime.optLong("threshold_adjustments")+" · 辅助确认 "+runtime.optLong("assist_adjustments")+" · 动画 "+runtime.optLong("animation_adjustments")+" · 阳光屏 "+runtime.optLong("sunlight_adjustments")+" · 触摸保护 "+runtime.optLong("touch_adjustments")+"\n确认时间受采样窗口限制："+runtime.optLong("delay_window_clamps")+" 次");
        if(advancedStatus!=null&&runtime!=null){JSONObject scenes=runtime.optJSONObject("system_scene");if(scenes!=null&&scenes.has("main_history_ms"))advancedStatus.append("\n"+tr("主光感历史窗口")+": "+format(scenes.optDouble("main_history_ms")/1000)+tr(" 秒"));
            if(runtime.has("last_main_brighten_ms")||runtime.has("last_main_darken_ms"))advancedStatus.append("\n"+tr("最近主光感基础确认：亮 ")+(runtime.has("last_main_brighten_ms")?format(runtime.optDouble("last_main_brighten_ms")/1000):"—")+tr(" 秒")+tr(" · 暗 ")+(runtime.has("last_main_darken_ms")?format(runtime.optDouble("last_main_darken_ms")/1000):"—")+tr(" 秒"));}
    }
    void makeLogs(){
        LinearLayout b=card(pages[2]);b.setPadding(dp(16),dp(12),dp(16),dp(12));b.addView(text("运行与兼容",17,INK));compatibility=text("正在等待系统状态…",13,MUTED);compatibility.setMaxLines(4);compatibility.setEllipsize(android.text.TextUtils.TruncateAt.END);b.addView(compatibility);
        b=card(pages[2]);LinearLayout.LayoutParams panel=(LinearLayout.LayoutParams)b.getLayoutParams();panel.height=0;panel.weight=1;panel.bottomMargin=0;b.setLayoutParams(panel);b.setPadding(dp(14),dp(12),dp(14),dp(12));
        LinearLayout r=row(b);r.addView(text("最近运行记录",15,INK),new LinearLayout.LayoutParams(0,-2,1));Button refresh=action("刷新",()->run("inspect",null));refresh.setTextSize(12);Switch follow=new Switch(this);logFollow=follow;follow.setText(tr("自动滚动"));follow.setTextSize(11);follow.setShowText(false);follow.setChecked(autoScroll);follow.setPadding(dp(4),0,dp(8),0);r.addView(follow,new LinearLayout.LayoutParams(-2,dp(36)));
        follow.setOnCheckedChangeListener((v,on)->{autoScroll=on;getPreferences(0).edit().putBoolean("log_auto_scroll",on).apply();if(on)scrollLogs();});r.addView(refresh,new LinearLayout.LayoutParams(dp(52),dp(32)));
        TextView hint=text("当前系统进程 · 最近 160 条",11,MUTED);b.addView(hint);
        logText=text("尚无记录",12,INK);logText.setTypeface(Typeface.MONOSPACE);logText.setTextIsSelectable(true);logText.setPadding(dp(10),dp(8),dp(10),dp(8));
        logScroller=new ScrollView(this);logScroller.setBackground(background(0xfff2f4f8,12));logScroller.setClipToOutline(true);logScroller.addView(logText);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);lp.topMargin=dp(8);lp.bottomMargin=dp(8);b.addView(logScroller,lp);
        logText.addOnLayoutChangeListener((v,l,t,right,bottom,ol,ot,or,ob)->{if(autoScroll&&bottom-t!=ob-ot)scrollLogs();});
        logScroller.addOnLayoutChangeListener((v,l,t,right,bottom,ol,ot,or,ob)->{if(autoScroll&&bottom-t!=ob-ot)scrollLogs();});
        r=row(b);compact("亮度分支",this::branches,r);compact("详细读数",this::details,r);compact("导出分析包",()->run("export",null),r);
        b.addView(text("分析包保存到手机存储根目录",11,MUTED));note.setMaxLines(2);note.setEllipsize(android.text.TextUtils.TruncateAt.END);b.addView(note);
    }

    void makeAbout(){
        LinearLayout b=card(pages[3]);b.setPadding(dp(22),dp(22),dp(22),dp(18));LinearLayout r=row(b);r.addView(image(null,68,20));LinearLayout title=column();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(18);r.addView(title,lp);title.addView(text("HyperLux",26,INK));title.addView(text(AppBuild.ARTIFACT_VERSION+" · "+tr(AppBuild.TEST?"曲线适配测试版":"正式版"),14,BLUE));
        TextView intro=text("适配 HyperOS 4 的自动亮度工具。\n\n沿用系统双侧感光、场景判定与平滑过渡，提供可编辑曲线、暗处亮度下限与手动偏好记忆。户外高亮、温控和变化确认可按需调整，配置支持导入导出。",15,INK);intro.setLineSpacing(dp(5),1);intro.setPadding(0,dp(20),0,dp(16));b.addView(intro);
        b.addView(text("HyperOS 4 · Root · LSPosed",12,MUTED));updateLabel=text("自动检查应用更新",12,BLUE);updateLabel.setPadding(dp(12),dp(10),dp(12),dp(10));updateLabel.setBackground(background(0xffeef3ff,12));lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);lp.bottomMargin=dp(6);b.addView(updateLabel,lp);
        r=row(b);compact("官网",()->open("https://lc.rongshangs.top"),r);compact("GitHub",()->open(UpdateChecker.REPO),r);compact("开源协议",()->open("https://www.gnu.org/licenses/gpl-3.0.html"),r);
        LinearLayout group=column();group.setPadding(dp(14),dp(12),dp(14),dp(12));group.setBackground(ripple(0xffeef3ff,14));group.setClipToOutline(true);group.setClickable(true);group.setOnClickListener(v->copyGroupNumber());lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);b.addView(group,lp);
        group.addView(text("QQ 交流群",12,MUTED));r=row(group);r.setBaselineAligned(false);TextView number=text("314981836",22,BLUE);number.setPadding(0,0,0,0);r.addView(number,new LinearLayout.LayoutParams(0,-2,1));Button copy=action("复制群号",this::copyGroupNumber);copy.setTextSize(12);copy.setPadding(dp(8),0,dp(8),0);r.addView(copy,new LinearLayout.LayoutParams(dp(88),dp(34)));group.addView(text("暗号：1691",12,MUTED));
        b=card(pages[3]);b.addView(text("打赏",18,INK));b.addView(text("完全开源免费。欢迎捐赠 2.3 元支持开发。备注「昵称：想说的一句话」，总计不超过 30 个字符，将会尽快更新到感谢名单。",13,MUTED));r=row(b);compact("微信",()->donate("微信","donate-wechat.jpg"),r);compact("支付宝",()->donate("支付宝","donate-alipay.jpg"),r);
        b=card(pages[3]);b.addView(text("感谢名单",18,INK));thanksList=column();b.addView(thanksList);thanksStatus=text("名单来自官网，联网时自动更新",11,MUTED);b.addView(thanksStatus);
        try{JSONObject data;try{data=ThanksFeed.parse(getPreferences(0).getString("thanks_cache",null));}catch(Exception absent){data=ThanksFeed.parse(ThanksFeed.read(getAssets().open("thanks.json")));}renderThanks(data);}catch(Exception ignored){thanksStatus.setText("暂时无法读取名单");}
        b=card(pages[3]);r=row(b);r.addView(image("avatar.jpg",46,15));TextView author=text("戎Shang",19,INK);author.setPadding(dp(14),0,0,0);r.addView(author);r=row(b);compact("作者博客",()->open("https://rongshangs.top"),r);compact("酷安主页",()->open("https://www.coolapk.com/u/3261403"),r);
    }
    void renderThanks(JSONObject data)throws JSONException{
        thanksList.removeAllViews();JSONArray entries=data.getJSONArray("entries");
        for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);TextView line=new TextView(this);line.setText(entry.getString("name")+"："+entry.getString("message"));line.setTextSize(14);line.setTextColor(INK);line.setPadding(0,dp(6),0,dp(6));thanksList.addView(line);}
    }
    void refreshThanks(){
        if(thanksBusy||SystemClock.elapsedRealtime()-thanksChecked<60000&&thanksChecked!=0)return;thanksBusy=true;thanksChecked=SystemClock.elapsedRealtime();
        network.execute(()->{try{JSONObject data=ThanksFeed.check();ui.post(()->{if(destroyed)return;thanksBusy=false;try{renderThanks(data);getPreferences(0).edit().putString("thanks_cache",data.toString()).apply();thanksStatus.setText("已同步官网感谢名单");}catch(Exception ignored){thanksStatus.setText("名单来自官网，联网时自动更新");}});}catch(Exception failure){ui.post(()->{if(destroyed)return;thanksBusy=false;thanksStatus.setText("暂未连接官网，显示已保存的名单");});}});
    }
    void scrollLogs(){if(logScroller!=null&&autoScroll)logScroller.post(()->{if(autoScroll&&!destroyed)logScroller.scrollTo(0,logText.getHeight());});}
    void copyGroupNumber(){android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText(tr("QQ 交流群"),"314981836"));show("已复制群号");}
    void rememberLegacy(JSONObject answer){JSONArray modules=answer.optJSONArray("legacy_modules");if(modules==null||modules.length()==0){pendingLegacy=null;legacyNotice="";return;}if(!modules.toString().equals(legacyNotice))pendingLegacy=modules;}
    void offerLegacyModules(){
        if(pendingLegacy==null||!visible||permissionDialogVisible||legacyDialogVisible||updateDialog!=null)return;
        JSONArray modules=pendingLegacy;pendingLegacy=null;legacyNotice=modules.toString();legacyDialogVisible=true;LinearLayout body=column();body.addView(text("请在 Root 管理器中卸载这些旧模块并重启，避免多套引擎同时调节亮度。",14,INK));
        for(int i=0;i<modules.length();i++){JSONObject module=modules.optJSONObject(i);if(module==null)continue;String state=module.optString("state");body.addView(text(module.optString("name")+"\n"+module.optString("id")+(state.equals("disabled")?" · 已停用":state.equals("pending")?" · 待更新":" · 已安装"),12,MUTED));}
        AlertDialog d=dialog("请卸载旧版亮度模块",body);d.setOnDismissListener(v->{legacyDialogVisible=false;offerUpdate();});d.show();dialogButton(d,"知道了",null,true);
    }

    void checkUpdate(boolean manual){
        if(updateBusy)return;updateBusy=true;updateChecked=true;updateLabel.setText("正在检查更新…");
        network.execute(()->{try{JSONObject found=UpdateChecker.check();ui.post(()->{if(destroyed)return;updateBusy=false;if(found.optBoolean("newer")){pendingUpdate=found;updateLabel.setText("发现新版本 "+found.optString("version"));offerUpdate();}else{updateLabel.setText("当前没有新应用版本");if(manual)show("当前没有新应用版本");}});}catch(Exception failure){ui.post(()->{if(destroyed)return;updateBusy=false;updateLabel.setText("暂时无法检查更新");if(manual)show("暂时无法检查更新");});}});
    }
    void offerUpdate(){if(pendingUpdate==null||!visible||permissionDialogVisible||legacyDialogVisible||pendingLegacy!=null||updateDialog!=null)return;JSONObject release=pendingUpdate;pendingUpdate=null;LinearLayout body=column();body.addView(text("版本 "+release.optString("version"),18,BLUE));String notes=release.optString("notes");TextView changes=text(notes.isEmpty()?"查看仓库了解更新内容":notes,13,MUTED);ScrollView scroll=new ScrollView(this);scroll.addView(changes);scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.min(dp(200),dp(70+Math.min(8,notes.length()/40)*16))));body.addView(scroll);AlertDialog d=dialog("有新版本可用",body);updateDialog=d;d.setOnDismissListener(v->{updateDialog=null;offerLegacyModules();});d.show();dialogButton(d,"稍后",null,false);dialogButton(d,"下载更新",()->open(release.optString("url")),true);}
    void select(int value){int previous=page;page=Math.max(0,Math.min(3,value));settingsGroup=-1;if(bottomNav!=null)bottomNav.setVisibility(View.VISIBLE);header.setVisibility(page==3?View.GONE:View.VISIBLE);note.setVisibility(View.GONE);transitionPage(content.getChildAt(page),Integer.compare(page,previous));
        for(int i=0;i<4;i++){if(nav[i]!=null){navNames[i].setTextColor(i==page?BLUE:MUTED);navIcons[i].selected=i==page;navIcons[i].invalidate();nav[i].setSelected(i==page);}}
        // Refresh every visit; retain unapplied edits until a successful save.
        if(page==3&&previous!=3&&visible){checkUpdate(false);refreshThanks();}if(page==2)scrollLogs();if(visible){if(busy)rereadPending=true;else run("inspect",null);}
    }
    void show(String message){if(!destroyed){note.setText(message);note.setVisibility(message==null||message.isEmpty()?View.GONE:View.VISIBLE);if(message!=null&&!message.isEmpty()&&page!=2)Toast.makeText(this,tr(message),Toast.LENGTH_SHORT).show();}}
    void permissionPrompt(String key,String title,String message){
        permissionBlocked=true;if(!visible||destroyed||permissionDialogVisible||permissionNotice.equals(key))return;
        permissionNotice=key;permissionDialogVisible=true;LinearLayout body=column();TextView icon=text(key.equals("root")?"ROOT":key.equals("lsp")?"LSPosed":"接口检查",14,BLUE);icon.setPadding(dp(12),dp(8),dp(12),dp(8));icon.setBackground(background(0xffedf3ff,12));body.addView(icon);body.addView(text(message,14,MUTED));
        AlertDialog d=dialog(title,body);d.setOnDismissListener(v->{permissionDialogVisible=false;ui.post(()->{offerLegacyModules();offerUpdate();});});d.show();dialogButton(d,"稍后",null,false);dialogButton(d,"重新检测",()->{permissionNotice="";permissionBlocked=false;ui.postDelayed(()->run("inspect",null),150);},true);
    }
    void preset(float[] next){
        if(factoryLux==null){show("连接后读取曲线");return;}
        try{float[] adjusted=CurveEditor.preset(next,factoryNit,minimum,maximum);
            factors=adjusted;curveFloor=0;dirty=true;drawCurve();show(Arrays.equals(next,adjusted)?"曲线已选中，点击保存并应用后生效":"预设已按本机可调范围调整，保存并应用后生效");
        }catch(Exception error){show(error.getMessage());}
    }
    void drawCurve(){
        drawAdvanced();drawOutdoor();
        thermalSwitch.setChecked(thermalRelax);ceilingSlider.setProgress(Math.round(thermalCeiling)-38);ceilingText.setText("电池温度阈值："+Math.round(thermalCeiling)+"℃"+(Math.round(thermalCeiling)==43?"（建议）":""));stateGraph.invalidate();settingsGraph.invalidate();
        memorySwitch.setChecked(memoryEnabled);memorySlider.setProgress(Math.round(memoryStrength*100)-1);memoryText.setText("记忆强度 "+Math.round(memoryStrength*100)+"%"+(memoryStrength==1?" · 系统速度":" · 缓慢记忆"));pipeline.invalidate();
        memoryWindowSlider.setProgress((int)(memoryWindow-500)/500);memoryWindowLabel.setText("连续调节间隔："+format(memoryWindow/1000d)+" 秒");memoryRangeSlider.setProgress(Math.round((memoryLuxRange-.1f)/.05f));memoryRangeLabel.setText("记忆照度范围：±"+Math.round(memoryLuxRange*100)+"%");
        coolingSlider.setProgress(Math.round((thermalCooling-1)*2));coolingLabel.setText("冷却幅度："+format(thermalCooling)+"℃");responseSwitch.setChecked(responseOverride);brightenSlider.setProgress((int)(brightenDelay-500)/500);darkenSlider.setProgress((int)(darkenDelay-1000)/500);brightenLabel.setText("变亮确认："+format(brightenDelay/1000d)+" 秒");darkenLabel.setText("变暗确认："+format(darkenDelay/1000d)+" 秒");refreshSlider.setProgress(refreshSeconds-1);refreshLabel.setText("状态刷新间隔："+refreshSeconds+" 秒");
        smallBrightenSwitch.setChecked(smallBrightenOverride);smallBrightenSlider.setProgress((int)(smallBrightenDelay-500)/500);smallBrightenLabel.setText("微小变亮确认："+format(smallBrightenDelay/1000d)+" 秒");
        lowLightSwitch.setChecked(lowLightStability);
        lowLimitSlider.setProgress(Math.round((lowLightLimit-5)/5));lowLimitLabel.setText("暗光适用照度："+format(lowLightLimit)+" lux");
        lowBrightSlider.setProgress((int)(lowLightBrighten-1000)/500);lowBrightLabel.setText("暗光最短变亮确认："+format(lowLightBrighten/1000d)+" 秒");
        lowDarkSlider.setProgress((int)(lowLightDarken-1000)/500);lowDarkLabel.setText("暗光最短变暗确认："+format(lowLightDarken/1000d)+" 秒");
        enableParameter(memorySlider,memoryText,runtime!=null&&memoryEnabled);enableParameter(memoryWindowSlider,memoryWindowLabel,runtime!=null&&memoryEnabled);enableParameter(memoryRangeSlider,memoryRangeLabel,runtime!=null&&memoryEnabled);
        boolean thermal=runtime!=null&&runtime.optBoolean("thermal_supported")&&thermalRelax;enableParameter(ceilingSlider,ceilingText,thermal);enableParameter(coolingSlider,coolingLabel,thermal);
        boolean response=runtime!=null&&runtime.optBoolean("response_supported")&&responseOverride;enableParameter(brightenSlider,brightenLabel,response);enableParameter(darkenSlider,darkenLabel,response);
        enableParameter(smallBrightenSlider,smallBrightenLabel,runtime!=null&&runtime.optBoolean("small_response_supported")&&smallBrightenOverride);
        boolean low=runtime!=null&&runtime.optBoolean("low_light_supported")&&lowLightStability;enableParameter(lowLimitSlider,lowLimitLabel,low);enableParameter(lowBrightSlider,lowBrightLabel,low);enableParameter(lowDarkSlider,lowDarkLabel,low);
        drawDraftState();
        try{if(factoryLux!=null)new CurvePlan(factoryLux,factoryNit,minimum,maximum,factors,curveFloor);curveHint.setText("拖动或点击节点 · 高照度端固定"+(dirty?" · 尚未保存":""));}catch(Exception error){curveHint.setText(error.getMessage());}
    }
    void enableParameter(SeekBar slider,TextView label,boolean enabled){slider.setEnabled(enabled);label.setTextColor(enabled?INK:0xffa5aab3);}
    void drawDraftState(){if(unsavedHint!=null)unsavedHint.setVisibility(dirty?View.VISIBLE:View.GONE);for(int i=0;i<settingApply.length;i++){if(settingApply[i]!=null)settingApply[i].setEnabled(!busy&&runtime!=null&&factoryLux!=null);if(settingHints[i]!=null)settingHints[i].setVisibility(dirty?View.VISIBLE:View.GONE);}}
    JSONObject configuration()throws Exception{
            if(factoryLux==null)throw new IllegalStateException("连接后读取曲线");new CurvePlan(factoryLux,factoryNit,minimum,maximum,factors,curveFloor);ThermalPolicy.validate(thermalCeiling);
            JSONObject options=new JSONObject().put("factors",CurvePlan.encode(factors)).put("thermal_relax",thermalRelax).put("thermal_ceiling",thermalCeiling).put("memory_strength",memoryEnabled?memoryStrength:0).put("memory_window",memoryWindow).put("memory_lux_range",memoryLuxRange).put("thermal_cooling",thermalCooling).put("response_override",responseOverride).put("brighten_delay",brightenDelay).put("darken_delay",darkenDelay).put("small_brighten_override",smallBrightenOverride).put("small_brighten_delay",smallBrightenDelay);
            advanced.put(options);outdoorOptions.put(options);OutdoorOptions.parse(options);
            options.put("low_light_stability",lowLightStability).put("low_light_limit",lowLightLimit).put("low_light_brighten",lowLightBrighten).put("low_light_darken",lowLightDarken);
            options.put("curve_floor_nit",curveFloor);return options;
    }
    void exportConfiguration(){if(busy){show("另一项操作还在执行");return;}try{JSONObject file=ConfigurationFile.export(configuration(),runtime,dirty,refreshSeconds,autoScroll);run("export-config",Base64.getEncoder().encodeToString(file.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception error){show(error.getMessage());}}
    void importConfiguration(){if(factoryLux==null){show("连接后读取曲线");return;}if(busy){show("另一项操作还在执行");return;}try{Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType("*/*");picker.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});startActivityForResult(picker,501);}catch(ActivityNotFoundException unavailable){show("未找到文件选择器");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=501||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();worker.execute(()->{try(InputStream input=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(input==null)throw new IOException("无法读取配置文件");byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>ConfigurationFile.LIMIT)throw new IOException("配置文件过大");out.write(buffer,0,count);}String content=new String(out.toByteArray(),StandardCharsets.UTF_8);ui.post(()->previewConfiguration(content));}catch(Exception error){ui.post(()->show(error.getMessage()));}});}
    void previewConfiguration(String text){if(destroyed)return;try{if(runtime==null)throw new IllegalStateException("连接后读取曲线");ConfigurationFile.Imported imported=ConfigurationFile.read(text,runtime,configuration());String message="导入后先检查设置，再点击保存并应用。旧版本缺少的新选项使用默认值。";if(dirty)message+="\n\n导入会替换当前尚未保存的设置。";if(!imported.notes.isEmpty())message+="\n\n"+String.join("\n",imported.notes);ScrollView scroll=new ScrollView(this);scroll.addView(MainActivity.this.text(message,14,MUTED));scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(240)));AlertDialog d=dialog("导入配置",scroll);d.show();dialogButton(d,"取消",null,false);dialogButton(d,"导入",()->{try{ConfigurationFile.Imported checked=ConfigurationFile.read(text,runtime,configuration());loadOptions(checked.options);if(checked.ui.has("refresh_seconds"))refreshSeconds=checked.ui.getInt("refresh_seconds");if(checked.ui.has("log_auto_scroll"))autoScroll=checked.ui.getBoolean("log_auto_scroll");getPreferences(0).edit().putInt("refresh_seconds",refreshSeconds).putBoolean("log_auto_scroll",autoScroll).apply();dirty=true;logFollow.setChecked(autoScroll);drawCurve();show("配置已导入，检查后点击保存并应用");}catch(Exception error){show(error.getMessage());}},true);}catch(Exception error){show(error.getMessage());}}
    void loadOptions(JSONObject config)throws Exception{float[] next=CurvePlan.factors(config.getString("factors"));float floor=CurvePlan.floor(config.has("curve_floor_nit")?config.opt("curve_floor_nit"):null);new CurvePlan(factoryLux,factoryNit,minimum,maximum,next,floor);AdvancedOptions a=AdvancedOptions.parse(config);OutdoorOptions o=OutdoorOptions.parse(config);factors=next;curveFloor=floor;advanced=a;outdoorOptions=o;thermalRelax=config.optBoolean("thermal_relax");thermalCeiling=(float)config.optDouble("thermal_ceiling",43);float memory=(float)config.optDouble("memory_strength",1);memoryEnabled=memory>0;if(memoryEnabled)memoryStrength=memory;memoryWindow=config.optLong("memory_window",1500);memoryLuxRange=(float)config.optDouble("memory_lux_range",.3);thermalCooling=(float)config.optDouble("thermal_cooling",1);responseOverride=config.optBoolean("response_override");brightenDelay=config.optLong("brighten_delay",1500);darkenDelay=config.optLong("darken_delay",5000);smallBrightenOverride=config.optBoolean("small_brighten_override");smallBrightenDelay=config.optLong("small_brighten_delay",5000);lowLightStability=config.optBoolean("low_light_stability");lowLightLimit=(float)config.optDouble("low_light_limit",50);lowLightBrighten=config.optLong("low_light_brighten",3000);lowLightDarken=config.optLong("low_light_darken",4000);}
    void submit(){
        if(factoryLux==null)return;
        try{JSONObject options=configuration();
            run("apply",Base64.getEncoder().encodeToString(options.toString().getBytes(StandardCharsets.UTF_8)));
        }catch(Exception error){show(error.getMessage());}
    }
    JSONObject presets(){try{return new JSONObject(getPreferences(0).getString("presets","{}"));}catch(Exception error){return new JSONObject();}}
    EditText input(String hint){EditText v=new EditText(this);v.setSingleLine(true);v.setTextSize(16);v.setHint(tr(hint));v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setBackground(background(0xffedf1f7,12));return v;}
    void savePreset(){
        if(factoryLux==null){show("连接系统后再保存预设");return;}try{new CurvePlan(factoryLux,factoryNit,minimum,maximum,factors,curveFloor);}catch(Exception error){show(error.getMessage());return;}
        EditText input=input("预设名称，最多 24 字");input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});AlertDialog d=dialog("保存曲线预设",input);d.show();dialogButton(d,"取消",null,false);dialogButton(d,"保存",()->{
            String name=input.getText().toString().trim();if(name.isEmpty()){show("请填写预设名称");return;}try{JSONObject all=presets();all.put(name,new JSONObject().put("factors",CurvePlan.encode(factors)).put("curve_floor_nit",curveFloor).put("baseline_id",loadedBaseline).put("fingerprint",Build.FINGERPRINT).put("factory_lux",array(factoryLux)));
                getPreferences(0).edit().putString("presets",all.toString()).apply();show("预设已保存到本机："+name);}catch(Exception error){show(error.toString());}
        },true);
    }
    void pickPreset(){
        JSONObject all=presets();ArrayList<String> names=new ArrayList<>();all.keys().forEachRemaining(names::add);Collections.sort(names);if(names.isEmpty()){show("还没有自定义预设");return;}
        LinearLayout list=column();ScrollView scroll=new ScrollView(this);scroll.addView(list);scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.min(dp(280),Math.max(dp(60),names.size()*dp(54)))));AlertDialog chooser=dialog("我的曲线预设",scroll);
        for(String name:names)button(name,()->{chooser.dismiss();try{JSONObject value=all.getJSONObject(name);
            if(!Build.FINGERPRINT.equals(value.optString("fingerprint"))){show("该预设来自其他固件，请先核对曲线节点");return;}
            if(value.has("baseline_id")&&!loadedBaseline.equals(value.getString("baseline_id"))){show("该预设来自其他固件，请先核对曲线节点");return;}float[] next=CurvePlan.factors(value.getString("factors"));float floor=CurvePlan.floor(value.has("curve_floor_nit")?value.opt("curve_floor_nit"):null);float[] adjusted=CurveEditor.preset(next,factoryNit,minimum,maximum);new CurvePlan(factoryLux,factoryNit,minimum,maximum,adjusted,floor);AlertDialog d=dialog(name,text("载入后点击保存并应用。",14,MUTED));d.show();dialogButton(d,"删除",()->{all.remove(name);getPreferences(0).edit().putString("presets",all.toString()).apply();show("已删除预设");},false);dialogButton(d,"载入",()->{factors=adjusted;curveFloor=floor;dirty=true;drawCurve();show("曲线已选中，点击保存并应用后生效");},true);
        }catch(Exception error){show(error.toString());}},list);
        chooser.show();dialogButton(chooser,"关闭",null,false);
    }
    void details(){
        TextView t=new TextView(this);t.setText(runtime==null?tr("尚未连接系统曲线"):pretty(runtime));t.setTextSize(12);t.setTextColor(INK);t.setTypeface(Typeface.MONOSPACE);t.setTextIsSelectable(true);t.setPadding(dp(10),dp(8),dp(10),dp(8));ScrollView s=new ScrollView(this);s.addView(t);s.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.min(getResources().getDisplayMetrics().heightPixels-dp(245),dp(390))));
        AlertDialog d=dialog("详细读数",s);d.show();dialogButton(d,"关闭",null,false);
    }
    String observed(JSONObject value,String key){return value==null||!value.has(key)?"未取得":value.optBoolean(key)?"已启用":"未启用";}
    String change(JSONObject value,String key){return value==null||!value.has(key)?"未取得":value.optBoolean(key)?"已改写目标":"未改写目标";}
    String route(JSONObject frame){if(frame==null)return "未取得";String r=frame.optString("route");return r.equals("refactor")?"Refactor 曲线":r.equals("mapping")?"标准映射曲线":"未取得";}
    String strategy(){if(runtime==null||!runtime.has("output_strategy"))return "未取得";String s=runtime.optString("output_strategy");
        if(s.equals("AutomaticBrightnessStrategy"))return "自动亮度";if(s.equals("FallbackBrightnessStrategy"))return "系统回退";
        if(s.equals("ScreenOffBrightnessStrategy"))return "熄屏";if(s.equals("DozeBrightnessStrategy"))return "息屏显示";
        if(s.equals("TemporaryBrightnessStrategy"))return "临时亮度";if(s.equals("OverrideBrightnessStrategy"))return "应用指定亮度";return s;
    }
    void branchRow(LinearLayout parent,String title,String state,String explanation){LinearLayout b=card(parent);b.setBackground(background(0xffeef3ff,14));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.addView(text(title+" · "+state,14,INK));b.addView(text(explanation,12,MUTED));}
    void branches(){
        LinearLayout body=column();if(runtime!=null&&runtime.optJSONObject("outdoor")!=null)branchRow(body,"户外高亮",outdoorDetails(runtime.optJSONObject("outdoor")),"数值是系统亮度坐标的百分比，不是实际 nit。请求目标、可用范围与实际输出分别记录；画面、温控和驱动仍可能限亮。高亮超时由独立事件计时，不依赖光感不断刷新。 ");JSONObject frame=runtime==null?null:runtime.optJSONObject("last_pipeline");
        String reference=runtime==null?"":runtime.optString("sensor_reference_name");
        branchRow(body,"当前光感参考",reference.equals("main")?"主光感":reference.equals("assist")?"辅助光感":reference.equals("other")?"其他系统策略":"未取得","系统会根据双侧读数与场景切换参考，不意味着两个独立亮度引擎在抢控制权。");
        branchRow(body,"输出路径",strategy(),"自动、临时、应用指定与系统回退是不同输出策略；回退不代表用户关闭了自动亮度。");
        if(runtime!=null)branchRow(body,"接入后端",runtime.optString("curve_backend").equals("physical_mapping")?"传统系统曲线":"Refactor 曲线","根据系统正在使用的控制器读取设备本地曲线，保留传感器处理和亮度动画。");
        branchRow(body,"曲线路径",route(frame),"显示最近一次实际执行的映射路径，不把支持某接口当作正在使用它。");
        branchRow(body,"场景修正",change(frame,"scene_changed"),"比较同一次计算中，场景处理前后的亮度目标。");
        branchRow(body,"手动保持修正",change(frame,"override_changed"),"显示系统手动保持策略是否改写目标；手动曲线节点仍可另行影响曲线。");
        branchRow(body,"户外目标修正",change(frame,"outdoor_changed"),"户外增强在系统手动保持之后、最终范围限亮之前加入目标；不会写入基础曲线或手动记忆。");
        branchRow(body,"短期记忆模型",observed(frame,"short_term_memory"),"系统记忆模型的状态；模型存在不等于当前曲线一定使用了它。");
        branchRow(body,"夜间唤醒策略",observed(frame,"night_wake"),"显示策略开关，不代表本次一定改变了亮度。");
        branchRow(body,"HDR",observed(runtime,"hdr_active"),"视频等 HDR 内容可改变输出亮度，与照度曲线变化分开记录。");
        branchRow(body,"阳光增强",observed(runtime,"sunlight_active"),"系统高亮控制器的当前状态；仍由系统判断何时触发。");
        branchRow(body,"暗光稳定",observed(runtime,"low_light_stability"),"仅增加照度变化的确认时间，保留原有传感器数值与系统过渡动画。");
        if(runtime!=null){branchRow(body,"变化阈值与辅助确认",advancedStatus==null?"":advancedStatus.getText().toString(),"高级选项默认关闭；次数为本次系统进程累计，不代表当前每一帧都在调整。");JSONObject scenes=runtime.optJSONObject("system_scene");if(scenes!=null)branchRow(body,"系统场景与增强条件",sceneSummary(scenes),"只读显示。未出现的字段表示未取得，并非关闭；系统仍负责口袋、反射、驾驶、HDR 和高亮度保护。");}
        if(runtime!=null)body.addView(text("实际延长确认：主光感 "+runtime.optLong("low_light_main_adjustments")+" · 辅助光感 "+runtime.optLong("low_light_assist_adjustments"),12,MUTED));
        if(frame!=null){long age=Math.max(0,SystemClock.uptimeMillis()-frame.optLong("uptime_ms"));body.addView(text("最近曲线计算："+format(age/1000d)+" 秒前 · #"+frame.optLong("sequence"),12,MUTED));}
        body.addView(text("未取得表示该阶段尚未被观察到。曲线计算与后续输出分开记录，导出分析包可查看切换详情。",12,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.min(dp(430),getResources().getDisplayMetrics().heightPixels-dp(240))));AlertDialog d=dialog("亮度分支",scroll);d.show();dialogButton(d,"关闭",null,true);
    }
    static String pretty(JSONObject object){try{return object.toString(2);}catch(Exception ignored){return object.toString();}}
    void donate(String name,String file){try{ImageView v=image(file,240,12);v.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout box=column();box.setGravity(Gravity.CENTER_HORIZONTAL);box.addView(v);TextView reminder=text("记得备注[昵称：想说的话]噢",13,MUTED);reminder.setGravity(Gravity.CENTER);box.addView(reminder);AlertDialog d=dialog(tr(name)+" · "+tr("打赏"),box);d.show();dialogButton(d,"关闭",null,false);}catch(Exception error){show(error.toString());}}
    void editPoint(int point){
        if(factoryLux==null)return;if(point==3){show("最高照度节点保持设备原始值");return;}
        if(point==0){editFloor();return;}try{float[] range=CurveEditor.bounds(point,factoryNit,minimum,maximum,factors,curveFloor);LinearLayout body=column();body.addView(text(format(factoryLux[point])+" lux · 节点强度",13,MUTED));
            body.addView(text("可调范围："+format(range[0]*100)+"% ～ "+format(range[1]*100)+"%",13,BLUE));body.addView(text("100% 为系统基础值。范围由系统亮度上下限及相邻节点决定。",12,MUTED));
            EditText number=input("输入百分比");number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);number.setText(format(factors[point]*100));body.addView(number);
            TextView error=text("",12,0xffad5426);body.addView(error);AlertDialog d=dialog("编辑曲线节点",body);d.show();dialogButton(d,"取消",null,false);
            LinearLayout actions=(LinearLayout)d.getWindow().getDecorView().findViewWithTag("actions");Button confirm=compact("确定",()->{try{float value=Float.parseFloat(number.getText().toString())/100;
                if(!Float.isFinite(value)||value<range[0]-.00001f||value>range[1]+.00001f){error.setText("请输入范围内的数值");return;}
                factors[point]=CurveEditor.clamp(point,value,factoryNit,minimum,maximum,factors,curveFloor);dirty=true;drawCurve();d.dismiss();}catch(Exception ex){error.setText("请输入有效数字");}},actions);confirm.setTextColor(Color.WHITE);confirm.setBackground(ripple(BLUE,12));
        }catch(Exception error){show(error.getMessage());}
    }
    void editFloor(){try{float[] bounds=CurveEditor.floorBounds(factoryNit,minimum,maximum,factors);float current=new CurvePlan(factoryLux,factoryNit,minimum,maximum,factors,curveFloor).nits()[0];LinearLayout body=column();body.addView(text("暗处亮度下限",16,INK));body.addView(text("按本机曲线范围输入百分比，不是控制中心滑块的百分比。仅调整基础曲线，手动选择和温控仍优先。",12,MUTED));body.addView(text("可调范围："+curvePercent(bounds[0]/maximum*100)+"% ～ "+curvePercent(bounds[1]/maximum*100)+"%",13,BLUE));EditText number=input("输入百分比");number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);number.setText(String.format(Locale.ROOT,"%.4f",current/maximum*100));body.addView(number);TextView error=text("",12,0xffad5426);body.addView(error);AlertDialog d=dialog("编辑最低照度节点",body);d.show();dialogButton(d,"取消",null,false);LinearLayout actions=(LinearLayout)d.getWindow().getDecorView().findViewWithTag("actions");Button confirm=compact("确定",()->{try{float value=Float.parseFloat(number.getText().toString())/100*maximum;if(!Float.isFinite(value)||value<bounds[0]-.0001f||value>bounds[1]+.0001f){error.setText("请输入范围内的数值");return;}curveFloor=Math.max(bounds[0],Math.min(bounds[1],value));dirty=true;drawCurve();d.dismiss();}catch(Exception failure){error.setText("请输入有效数字");}},actions);confirm.setTextColor(Color.WHITE);confirm.setBackground(ripple(BLUE,12));}catch(Exception error){show(error.getMessage());}}
    void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException error){show("未找到浏览器，可复制网址打开");android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("网址",url));}}
    static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    synchronized void closeBridge(){
        try{if(bridgeWriter!=null)bridgeWriter.close();}catch(Exception ignored){}
        if(bridge!=null)bridge.destroy();bridge=null;bridgeReader=null;bridgeWriter=null;
    }
    synchronized void connectBridge()throws IOException{
        if(bridge!=null&&bridge.isAlive())return;closeBridge();
        String command="CLASSPATH="+quote(getApplicationInfo().sourceDir)+" /system/bin/app_process /system/bin top.rongshangs.lumacurve.refactor.RootControl serve";
        bridge=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();bridgeReader=new BufferedReader(new InputStreamReader(bridge.getInputStream(),StandardCharsets.UTF_8));bridgeWriter=new BufferedWriter(new OutputStreamWriter(bridge.getOutputStream(),StandardCharsets.UTF_8));
    }
    JSONObject request(String command,String payload)throws Exception{
        connectBridge();final java.lang.Process current=bridge;Thread timeout=new Thread(()->{try{Thread.sleep(command.equals("export")?300000:90000);current.destroyForcibly();}catch(InterruptedException ignored){}});timeout.setDaemon(true);timeout.start();
        try{
            bridgeWriter.write(new JSONObject().put("command",command).put("payload",payload==null?JSONObject.NULL:payload).toString());bridgeWriter.newLine();bridgeWriter.flush();
            String line;StringBuilder errors=new StringBuilder();while((line=bridgeReader.readLine())!=null){
                if(line.startsWith("LUMA_PROGRESS=")){final String message=line.substring(14);ui.post(()->show(message));}
                if(line.startsWith("LUMA_RESULT="))return new JSONObject(line.substring(12));
                if(errors.length()<4096)errors.append(line).append('\n');
            }
            throw new IOException("未取得 Root 响应，请确认授权。"+errors);
        }finally{timeout.interrupt();}
    }
    void run(String command,String payload){
        if(busy||destroyed)return;busy=true;apply.setEnabled(false);stop.setEnabled(false);drawDraftState();
        if(!command.equals("inspect"))show(command.equals("apply")?"正在应用曲线和温控选项…":command.equals("stop")?"正在恢复官方策略…":command.equals("reset-memory")?"正在清除手动记忆…":command.equals("export-config")?"正在导出配置…":"正在导出分析包…");
        worker.execute(()->{
            try{
                JSONObject result=request(command,payload);if(!result.optBoolean("ok"))throw new IOException(result.optString("message"));
                if(command.equals("inspect")&&!legacyChecked&&!getPreferences(0).getBoolean("legacy_imported",false)){
                    legacyChecked=true;
                    try{JSONObject old=request("legacy-preferences",null);if(old.optBoolean("ok")){
                        android.content.SharedPreferences.Editor prefs=getPreferences(0).edit();
                        for(String key:new String[]{"draft","presets"})if(old.has(key)&&!getPreferences(0).contains(key))prefs.putString(key,old.getString(key));
                        prefs.putBoolean("legacy_imported",true).apply();
                    }}catch(Exception ignored){}
                }
                JSONObject state=command.equals("inspect")?result:(command.equals("apply")||command.equals("stop")||command.equals("reset-memory"))?request("inspect",null):null;
                ui.post(()->{if(destroyed)return;busy=false;stop.setEnabled(true);if(command.equals("apply"))dirty=false;
                    if(state!=null)render(state);else apply.setEnabled(runtime!=null&&factoryLux!=null);drawDraftState();offerLegacyModules();offerUpdate();
                    if(rereadPending){rereadPending=false;ui.post(()->run("inspect",null));}
                    if(!command.equals("inspect")){String message=result.optString("message",result.has("path")?"已导出："+result.optString("path"):"操作完成");show(message);
                        if(result.has("path")){boolean configFile=command.equals("export-config");AlertDialog d=dialog(configFile?"配置已导出":"分析包已导出",text(result.optString("path")+"\n\n"+(configFile?"文件位于设备存储根目录。包含当前设置，可在后续版本导入。":"包含设备与显示信息，请按需分享。"),14,MUTED));d.show();dialogButton(d,"知道了",null,true);}}
                });
            }catch(Throwable error){closeBridge();ui.post(()->{if(destroyed)return;busy=false;stop.setEnabled(true);if(command.equals("inspect")){runtime=null;factoryLux=factoryNit=null;badge.setText("连接中断");stateTitle.setText("等待连接");thermalSwitch.setEnabled(false);memorySwitch.setEnabled(false);permissionPrompt("root","需要 Root 授权","请在 Root 管理器中允许 HyperLux 获得 Root 权限，再点击重新检测。\nRoot 可用后才会继续检查 LSPosed。");}apply.setEnabled(runtime!=null&&factoryLux!=null);drawDraftState();operationFailure(command,error);if(rereadPending){rereadPending=false;ui.post(()->run("inspect",null));}});}
            finally{if(!visible)closeBridge();}
        });
    }
    String curvePercent(double value){return String.format(Locale.ROOT,"%.4f",value).replaceAll("0+$","").replaceAll("\\.$","");}
    String format(double number){return String.format(Locale.ROOT,"%.1f",number);}
    void render(JSONObject answer){
        lastAnswer=answer;rememberLegacy(answer);
        try{
            if(!answer.optBoolean("connected")){runtime=null;drawAdvanced();drawOutdoor();factoryLux=factoryNit=null;branchOverview.setText("亮度分支：等待读取 · 点击查看");lowLightSwitch.setEnabled(false);badge.setText("未连接");stateTitle.setText("尚未连接系统曲线");luxReading.setText("— nit");nitReading.setText("— nit");compatibility.setText("✓ Root 授权已通过\n○ 系统框架连接未建立\n请核对 LSPosed 作用域和是否重启");apply.setEnabled(false);thermalSwitch.setEnabled(false);memorySwitch.setEnabled(false);stateGraph.invalidate();pipeline.invalidate();
                if(answer.optBoolean("lsp_loaded")){badge.setText("已加载");compatibility.setText("✓ Root 授权已通过\n✓ LSPosed 已加载\n○ 设备曲线尚未连接");if(!answer.optString("hook_issue").equals("curve_incompatible")){permissionBlocked=false;permissionNotice="";show(answer.optString("hook_message"));return;}permissionPrompt("abi","设备曲线尚未连接",answer.optString("hook_message","等待读取设备本地曲线；如持续未连接，请导出分析包。"));return;}
                permissionPrompt("lsp","LSPosed 尚未接入",answer.optString("hook_issue").equals("restart_required")?"Root 已获授权，但系统仍在运行旧版 Hook。请重启手机以加载新版本。":"Root 已授权。请在 LSPosed 启用 HyperLux，勾选系统框架，然后重启手机。\n从旧测试版迁移时，请先关闭旧版的 LSPosed 开关。");return;}
            runtime=answer.getJSONObject("runtime");if(!runtime.has("factory_lux")){factoryLux=factoryNit=null;badge.setText("不兼容");compatibility.setText(runtime.optString("message"));show("");apply.setEnabled(false);thermalSwitch.setEnabled(false);memorySwitch.setEnabled(false);permissionPrompt("abi","系统接口尚未兼容",runtime.optString("message"));return;}
            factoryLux=RootControl.numbers(runtime.getJSONArray("factory_lux"));factoryNit=RootControl.numbers(runtime.getJSONArray("factory_logical_nit"));minimum=(float)runtime.getDouble("min_logical_nit");maximum=(float)runtime.getDouble("max_logical_nit");
            String baseline=runtime.optString("baseline_id");
            if(!baseline.equals(loadedBaseline)){loadedBaseline=baseline;dirty=false;curveFloor=0;factors=new float[]{1,1,1,1};if(baseline.equals(getPreferences(0).getString("draft_baseline","")))try{factors=CurvePlan.factors(getPreferences(0).getString("draft","1,1,1,1"));curveFloor=getPreferences(0).getFloat("draft_floor",0);}catch(Exception ignored){}}
            JSONObject config=answer.optJSONObject("config");
            if(config!=null&&(!Build.FINGERPRINT.equals(config.optString("fingerprint"))||!runtime.optString("curve_backend").equals(config.optString("curve_backend",runtime.optString("curve_backend").equals("refactor")?"refactor":""))||(config.has("baseline_id")&&!baseline.equals(config.optString("baseline_id")))))config=null;
            if(!dirty&&config!=null&&config.optBoolean("enabled")){factors=CurvePlan.factors(config.getString("factors"));curveFloor=CurvePlan.floor(config.has("curve_floor_nit")?config.opt("curve_floor_nit"):null);thermalRelax=config.optBoolean("thermal_relax");thermalCeiling=(float)config.optDouble("thermal_ceiling",43);float memory=(float)config.optDouble("memory_strength",1);memoryEnabled=memory>0;if(memoryEnabled)memoryStrength=memory;memoryWindow=config.optLong("memory_window",1500);memoryLuxRange=(float)config.optDouble("memory_lux_range",.3);thermalCooling=(float)config.optDouble("thermal_cooling",1);responseOverride=config.optBoolean("response_override");brightenDelay=config.optLong("brighten_delay",1500);darkenDelay=config.optLong("darken_delay",5000);smallBrightenOverride=config.optBoolean("small_brighten_override");smallBrightenDelay=config.optLong("small_brighten_delay",5000);}
            if(!dirty)outdoorOptions=config!=null&&config.optBoolean("enabled")?OutdoorOptions.parse(config):new OutdoorOptions();
            if(!dirty)advanced=config!=null&&config.optBoolean("enabled")?AdvancedOptions.parse(config):new AdvancedOptions();
            if(!dirty){lowLightStability=config!=null&&config.optBoolean("enabled")&&config.optBoolean("low_light_stability");lowLightLimit=config==null?50:(float)config.optDouble("low_light_limit",50);lowLightBrighten=config==null?3000:config.optLong("low_light_brighten",3000);lowLightDarken=config==null?4000:config.optLong("low_light_darken",4000);}
            JSONObject currentFrame=runtime.optJSONObject("last_pipeline");branchOverview.setText("曲线："+route(currentFrame)+" · 输出："+strategy()+"\n户外："+(runtime.optJSONObject("outdoor")==null?"未取得":runtime.getJSONObject("outdoor").optString("text"))+" · 点击查看亮度分支");
            String phase=runtime.optString("phase");boolean active=phase.equals("active"),auto=runtime.optBoolean("auto_mode");badge.setText(!auto?"手动亮度":active?"运行中":phase.equals("error")?"接入异常":"官方基准");
            stateTitle.setText(active?(auto?"自动亮度已接入":"系统处于手动亮度"):phase.equals("error")?"已交回系统控制":"使用系统基础曲线");
            luxReading.setText(runtime.has("calculated_physical_nit")?format(runtime.optDouble("calculated_physical_nit"))+" nit":"等待计算");nitReading.setText(runtime.has("actual_nit")?format(runtime.optDouble("actual_nit"))+" nit":"等待输出");
            flow.setText(runtime.optBoolean("auto_mode")?"照度定位曲线，换算亮度后交由系统平滑调节。":"当前为手动亮度，曲线将在开启自动亮度后参与调节。");
            String thermal=!runtime.optBoolean("thermal_supported")?"该固件接口未兼容":!runtime.optBoolean("thermal_relax")?"系统温控生效":runtime.optBoolean("thermal_permitted")?"减少温控降亮已启用":!runtime.has("battery_temperature")||runtime.optInt("thermal_severity",-1)<0?"等待温度读数，系统温控生效":"温度较高，系统温控生效";
            thermalState.setText("温控："+thermal+"\n电池 "+(runtime.has("battery_temperature")?format(runtime.optDouble("battery_temperature"))+"℃":"无读数")+"；系统热状态 "+severity(runtime.optInt("thermal_severity",-1)));
            double anchor=runtime.optDouble("user_anchor_lux",-1);userState.setText("手动偏好："+(anchor>=0?"系统锚点位于 "+format(anchor)+" lux":"当前没有手动锚点"));
            JSONArray flags=runtime.optJSONArray("manual_anchor_flags");int remembered=0;if(flags!=null)for(int i=0;i<flags.length();i++)if(flags.optBoolean(i))remembered++;
            memoryStatus.setText("手动记忆节点："+(runtime.optString("curve_backend").equals("physical_mapping")?(anchor>=0?1:0):remembered)+" 个");
            compatibility.setText("✓ Root 与系统框架已连接\n"+(runtime.optString("curve_backend").equals("physical_mapping")?"✓ 传统系统曲线 · 设备本地基准":"✓ Refactor 曲线 · 设备本地基准")+"\n"+(phase.equals("error")?"○ 接入异常，请查看详细读数":active?"✓ 自定义曲线已应用":"○ 当前使用系统基础曲线")+"\n"+(runtime.optBoolean("thermal_supported")?"✓ 温控接口兼容":"○ 温控接口暂未兼容"));
            JSONArray logs=runtime.optJSONArray("logs");StringBuilder lines=new StringBuilder();if(logs!=null)for(int i=0;i<logs.length();i++)lines.append(logs.getString(i)).append('\n');String next=lines.length()==0?"尚无运行记录":lines.toString();
            if(!tr(next).contentEquals(logText.getText())){int position=logScroller.getScrollY();logText.setText(next);if(autoScroll)scrollLogs();else logScroller.post(()->logScroller.scrollTo(0,position));}
            // After an OTA, an unsupported option that was enabled must still be switchable off.
            smallBrightenSwitch.setEnabled(runtime.optBoolean("small_response_supported")||smallBrightenOverride);smallBrightenSlider.setEnabled(runtime.optBoolean("small_response_supported"));
            lowLightSwitch.setEnabled(runtime.optBoolean("low_light_supported")||lowLightStability);for(SeekBar slider:new SeekBar[]{lowLimitSlider,lowBrightSlider,lowDarkSlider})slider.setEnabled(runtime.optBoolean("low_light_supported")&&lowLightStability);
            if(answer.optLong("state_age_ms")>15000)compatibility.append("\n"+tr("系统状态未及时刷新"));
            thermalSwitch.setEnabled(runtime.optBoolean("thermal_supported")||thermalRelax);responseSwitch.setEnabled(runtime.optBoolean("response_supported")||responseOverride);brightenSlider.setEnabled(runtime.optBoolean("response_supported"));darkenSlider.setEnabled(runtime.optBoolean("response_supported"));memorySwitch.setEnabled(true);apply.setEnabled(true);permissionNotice="";permissionBlocked=false;drawCurve();
            if(!firmwarePrompted&&config!=null&&config.optBoolean("enabled")&&!config.optString("fingerprint").equals(Build.FINGERPRINT)){firmwarePrompted=true;AlertDialog d=dialog("系统已更新",text("当前已恢复系统曲线。请核对兼容状态，再重新保存并应用。",14,MUTED));d.show();dialogButton(d,"知道了",null,true);}if(!busy&&!dirty)show("");
        }catch(Exception error){runtime=null;factoryLux=factoryNit=null;apply.setEnabled(false);stateGraph.invalidate();pipeline.invalidate();drawDraftState();show("状态格式不兼容："+error);}
    }
    String sceneSummary(JSONObject scenes){
        StringBuilder out=new StringBuilder();
        String[][] flags={{"touch_protection_active","触摸遮挡保护"},{"proximity_near","距离遮挡"},{"night_wake","夜间唤醒"},{"night_driving","夜间驾驶"},{"reflective","反射判定"},{"step_mode","运动延迟"},{"assist_reset_pending","辅助重置等待"},{"manual_sunlight_active","手动阳光屏"},{"manual_sunlight_sensor","阳光屏传感器"},{"hbm_controller_enabled","HBM 控制器"},{"hbm_time_available","HBM 时间条件"},{"hbm_ambient_allowed","HBM 照度条件"},{"hbm_low_power_block","HBM 省电限制"},{"hdr_layer_present","HDR 图层"},{"dolby_enabled","杜比显示"}};
        for(String[] f:flags)if(scenes.has(f[0]))out.append(tr(f[1])).append(": ").append(tr(scenes.optBoolean(f[0])?"已触发":"未触发")).append('\n');
        String[][] numbers={{"main_history_ms","主光感历史窗口","ms"},{"step_extra_ms","运动附加等待","ms"},{"hbm_minimum_lux","HBM 最低照度","lux"},{"hbm_time_window_ms","HBM 时间窗口","ms"},{"hbm_time_max_ms","HBM 时间预算","ms"}};
        for(String[] f:numbers)if(scenes.has(f[0]))out.append(tr(f[1])).append(": ").append(format(scenes.optDouble(f[0]))).append(' ').append(f[2]).append('\n');
        return out.length()==0?tr("未取得"):out.toString().trim();
    }
    static String severity(int level){String[] names={"正常","轻微","中等","严重","危急","紧急","关机"};return level>=0&&level<names.length?names[level]:"未知";}
    @Override public void onResume(){super.onResume();visible=true;permissionNotice="";permissionBlocked=false;run("inspect",null);ui.removeCallbacks(tick);ui.postDelayed(tick,refreshSeconds*1000);checkUpdate(false);if(page==3)refreshThanks();offerLegacyModules();offerUpdate();}
    @Override public void onPause(){visible=false;finishPageTransition();ui.removeCallbacks(tick);getPreferences(0).edit().putString("draft",CurvePlan.encode(factors)).putString("draft_baseline",loadedBaseline).putFloat("draft_floor",curveFloor).apply();if(!busy)worker.execute(this::closeBridge);super.onPause();}
    @Override public void onSaveInstanceState(Bundle saved){saved.putInt("page",page);super.onSaveInstanceState(saved);}
    @Override public void onDestroy(){destroyed=true;if(!busy)closeBridge();worker.shutdown();network.shutdownNow();super.onDestroy();}
    static JSONArray array(float[] values)throws JSONException{JSONArray result=new JSONArray();for(float n:values)result.put(n);return result;}
    final class NavigationArrow extends View{
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean back;
        NavigationArrow(boolean back){super(MainActivity.this);this.back=back;if(!back)setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float size=dp(back?24:18);canvas.save();canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/24,size/24);paint.setColor(back?INK:BLUE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.1f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);Path path=new Path();if(back){path.moveTo(12,5);path.lineTo(5,12);path.lineTo(12,19);canvas.drawPath(path,paint);canvas.drawLine(5,12,21,12,paint);}else{path.moveTo(9,5);path.lineTo(16,12);path.lineTo(9,19);canvas.drawPath(path,paint);}canvas.restore();}
    }
    final class NavIcon extends View{
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int kind;boolean selected;
        NavIcon(int type){super(MainActivity.this);kind=type;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(selected?BLUE:MUTED);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path path=new Path();
            if(kind==0){path.moveTo(3,18);path.cubicTo(12,18,10,6,21,6);c.drawPath(path,p);c.drawLine(3,22,21,22,p);}
            else if(kind==1){for(int i=0;i<3;i++){float x=5+i*7,y=i==1?16:8;c.drawLine(x,3,x,y-3,p);c.drawLine(x,y+3,x,21,p);c.drawCircle(x,y,2.5f,p);}}
            else if(kind==2){c.drawRoundRect(5,2,19,22,2,2,p);c.drawLine(8,8,16,8,p);c.drawLine(8,12,16,12,p);c.drawLine(8,16,14,16,p);}
            else{c.drawCircle(12,12,9,p);c.drawLine(12,11,12,17,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,7,1.1f,p);}c.restore();}
    }
    final class PipelineBoard extends ViewGroup {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF[] boxes=new RectF[6];
        PipelineBoard(){super(MainActivity.this);setWillNotDraw(false);for(int i=0;i<6;i++)boxes[i]=new RectF();stateGraph=new CurveView(false);addView(stateGraph);addView(branchOverview);setContentDescription(tr("主光感和辅助光感，经融合与场景判断产生有效照度"));}
        void layoutBoxes(){float w=getWidth(),h=getHeight(),gap=dp(12),half=(w-gap)/2;boxes[0].set(0,0,half,h*.12f);boxes[1].set(half+gap,0,w,h*.12f);boxes[2].set(0,h*.16f,w,h*.30f);boxes[3].set(0,h*.36f,w,h*.70f);boxes[4].set(0,h*.74f,w,h*.845f);boxes[5].set(0,h*.885f,w,h);}
        int branchHeight(int h){return Math.min(dp(46),Math.max(dp(30),(int)(h*.085f)));}
        @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);int bh=branchHeight(h),chartCardHeight=(int)(h*.70f)-(int)(h*.36f);stateGraph.measure(MeasureSpec.makeMeasureSpec(Math.max(0,w-dp(20)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,chartCardHeight-dp(24)-bh),MeasureSpec.EXACTLY));branchOverview.measure(MeasureSpec.makeMeasureSpec(Math.max(0,w-dp(24)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(bh,MeasureSpec.EXACTLY));}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){layoutBoxes();RectF graph=boxes[3];int bottom=(int)graph.bottom-dp(12),top=bottom-branchHeight(getHeight());stateGraph.layout(dp(10),(int)graph.top+dp(8),getWidth()-dp(10),top-dp(4));branchOverview.layout(dp(12),top,getWidth()-dp(12),bottom);}
        void prepareLabel(String value,int size,int color,float width){paint.setColor(color);paint.setStyle(Paint.Style.FILL);float scale=Math.min(1,Math.max(.65f,getHeight()/(float)dp(440)));paint.setTextSize(dp(Math.max(9,size*scale)));while(paint.measureText(value)>width&&paint.getTextSize()>dp(9))paint.setTextSize(paint.getTextSize()-1);}
        void label(Canvas c,String value,float x,float y,int size,int color,float width){value=tr(value);prepareLabel(value,size,color,width);c.drawText(value,x-paint.measureText(value)/2,y,paint);}
        void labelStart(Canvas c,String value,float x,float y,int size,int color,float width){value=tr(value);prepareLabel(value,size,color,width);c.drawText(value,x,y,paint);}
        void labelEnd(Canvas c,String value,float x,float y,int size,int color,float width){value=tr(value);prepareLabel(value,size,color,width);c.drawText(value,x-paint.measureText(value),y,paint);}
        String reading(String key,String unit){double value=runtime==null?Double.NaN:runtime.optDouble(key);return Double.isFinite(value)&&value>=0?format(value)+unit:"—";}
        void connection(Canvas c,float x,float y,float bottom){paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff9db4e2);paint.setStrokeWidth(dp(1.4f));paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,x,bottom,paint);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth();if(w<=0||getHeight()<=0)return;float middle=w/2;boolean animate=visible&&isShown()&&page==0&&runtime!=null&&runtime.optBoolean("auto_mode")&&android.animation.ValueAnimator.areAnimatorsEnabled();
            paint.setPathEffect(new DashPathEffect(new float[]{dp(5),dp(5)},animate?-(SystemClock.uptimeMillis()%1000)/1000f*dp(10):0));float merge=(boxes[0].bottom+boxes[2].top)/2;
            // Both paths start at their sensor and end at the merge point, so both flow inward.
            paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff9db4e2);paint.setStrokeWidth(dp(1.4f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            for(int i=0;i<2;i++){Path branch=new Path();branch.moveTo(boxes[i].centerX(),boxes[i].bottom);branch.lineTo(boxes[i].centerX(),merge);branch.lineTo(middle,merge);c.drawPath(branch,paint);}connection(c,middle,merge,boxes[2].top);
            for(int i=2;i<5;i++)connection(c,middle,boxes[i].bottom,boxes[i+1].top);paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<6;i++){paint.setColor(i==1?0xffeef0f4:i==2?0xffedf3ff:Color.WHITE);c.drawRoundRect(boxes[i],dp(17),dp(17),paint);}
            boolean manual=runtime!=null&&!runtime.optBoolean("auto_mode");String sensor=runtime==null?"unknown":runtime.optString("sensor_status","unknown");boolean paused=manual||sensor.equals("paused")||sensor.equals("warming");
            for(int i=0;i<2;i++){RectF box=boxes[i];label(c,i==0?"主光感":"辅助光感",box.centerX(),box.top+box.height()*.35f,12,MUTED,box.width()-dp(14));label(c,manual||sensor.equals("paused")?"采样暂停":sensor.equals("warming")?"等待采样":reading(i==0?"main_fast_lux":"assist_fast_lux"," lux"),box.centerX(),box.top+box.height()*.77f,22,i==0?BLUE:INK,box.width()-dp(14));}
            RectF fused=boxes[2];label(c,manual?"手动亮度模式":"融合滤波与场景判定",middle,fused.top+fused.height()*.35f,13,MUTED,w-dp(20));label(c,manual?"开启系统自动亮度后读取":paused?"等待有效照度":reading(runtime!=null&&runtime.has("official_effective_lux")?"official_effective_lux":"last_lux"," lux"),middle,fused.top+fused.height()*.78f,manual?14:24,BLUE,w-dp(20));
            float thresholdY=(fused.bottom+boxes[3].top)/2+dp(3),thresholdGap=dp(6);if(!paused){labelEnd(c,runtime!=null&&runtime.has("darkening_lux_threshold")?"变暗 < "+format(runtime.optDouble("darkening_lux_threshold"))+" lux":"变暗 —",middle-thresholdGap,thresholdY,10,MUTED,w/2-dp(12));labelStart(c,runtime!=null&&runtime.has("brightening_lux_threshold")?"变亮 > "+format(runtime.optDouble("brightening_lux_threshold"))+" lux":"变亮 —",middle+thresholdGap,thresholdY,10,MUTED,w/2-dp(12));}
            RectF thermal=boxes[4];labelStart(c,"温控",dp(16),thermal.top+thermal.height()*.60f,14,INK,dp(50));paint.setColor(0xffe5eaf2);paint.setStrokeWidth(dp(1));c.drawLine(dp(72),thermal.top+dp(12),dp(72),thermal.bottom-dp(12),paint);
            String control=runtime==null?"暂无读数":runtime.optBoolean("thermal_relax")&&runtime.optBoolean("thermal_permitted")?"减少温控降亮":"系统温控生效";labelStart(c,control,dp(88),thermal.top+thermal.height()*.40f,13,INK,w-dp(100));labelStart(c,"电池 "+reading("battery_temperature","℃")+" · "+(runtime==null?"未知":severity(runtime.optInt("thermal_severity",-1))),dp(88),thermal.top+thermal.height()*.78f,11,MUTED,w-dp(100));
            RectF screen=boxes[5];label(c,"当前屏幕亮度",middle,screen.top+screen.height()*.32f,12,MUTED,w-dp(20));label(c,reading("actual_nit"," nit"),middle,screen.top+screen.height()*.78f,27,INK,w-dp(20));if(animate)postInvalidateDelayed(100);
        }
    }

    final class CurveView extends View{
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean draft;int point=-1;float startX,startY,startFactor;float[] dragBounds;boolean moved;
        CurveView(boolean draft){super(MainActivity.this);this.draft=draft;setContentDescription(tr(draft?"拖动节点调整曲线，点击节点输入数值":"当前亮度曲线"));setFocusable(draft);}
        float left(){return dp(draft?38:32);}float top(){return dp(draft?18:8);}float width(){return getWidth()-left()-dp(25);}float height(){return getHeight()-top()-dp(draft?43:22);}
        float x(float lux){return left()+(float)(Math.log1p(Math.max(0,Math.min(lux,factoryLux[3])))/Math.log1p(factoryLux[3]))*width();}
        float y(float nit){return top()+height()*(1-nit/maximum);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(12));paint.setColor(MUTED);
            if(factoryLux==null){canvas.drawText(tr("连接后读取曲线"),dp(10),dp(65),paint);return;}
            if(!draft&&(runtime==null||!(runtime.optJSONObject("last_pipeline")==null&&"refactor".equals(runtime.optString("curve_backend"))&&runtime.has("current_anchors_nit")||"refactor".equals(runtime.optJSONObject("last_pipeline")==null?"":runtime.optJSONObject("last_pipeline").optString("route"))||("physical_mapping".equals(runtime.optString("curve_backend"))&&runtime.optBoolean("physical_mapping_active")&&"mapping".equals(runtime.optJSONObject("last_pipeline")==null?"":runtime.optJSONObject("last_pipeline").optString("route")))))){canvas.drawText(tr("当前路径未取得可绘制曲线"),dp(10),dp(65),paint);return;}
            float left=left(),top=top(),width=width(),height=height();if(width<=0||height<=0)return;
            paint.setStrokeWidth(dp(1));paint.setTextSize(dp(10));for(int i=0;i<3;i++){float yy=top+height*i/2;paint.setColor(0xffe7ebf2);canvas.drawLine(left,yy,left+width,yy,paint);paint.setColor(MUTED);canvas.drawText((100-i*50)+"%",0,yy+dp(4),paint);}
            CurvePlan base=new CurvePlan(factoryLux,factoryNit,minimum,maximum,new float[]{1,1,1,1});float[] target=base.nits();
            if(draft){try{target=new CurvePlan(factoryLux,factoryNit,minimum,maximum,factors,curveFloor).nits();}catch(Exception ignored){return;}}
            else if(runtime!=null&&runtime.has("active_logical_nit"))try{target=RootControl.numbers(runtime.getJSONArray("active_logical_nit"));}catch(Exception ignored){}
            float[] nativeLux=null,nativeNit=null;if(!draft&&runtime!=null)try{nativeLux=RootControl.numbers(runtime.getJSONArray("current_anchors_lux"));nativeNit=RootControl.numbers(runtime.getJSONArray("current_anchors_nit"));if(nativeLux.length!=nativeNit.length||nativeLux.length<2){nativeLux=nativeNit=null;}}catch(Exception ignored){}
            if(nativeLux!=null&&Arrays.equals(nativeLux,factoryLux)&&Arrays.equals(nativeNit,target))nativeLux=nativeNit=null;
            float[] denseLux=null,denseBase=null,denseTarget=null;
            if(draft&&runtime!=null&&runtime.optString("curve_backend").equals("physical_mapping"))try{TraditionalCurve dense=new TraditionalCurve(RootControl.numbers(runtime.getJSONArray("factory_full_lux")),RootControl.numbers(runtime.getJSONArray("factory_full_nit")));denseLux=dense.lux();denseBase=dense.nit();denseTarget=dense.reshape(minimum,maximum,factors,curveFloor);}catch(Exception error){return;}
            for(int curve=0;curve<(draft?2:1);curve++){float[] values=draft?(curve==0?base.nits():target):(nativeLux==null?target:nativeNit);Path path=new Path();
                for(int i=0;i<=128;i++){float lux=(float)Math.expm1(Math.log1p(factoryLux[3])*i/128),xx=left+width*i/128,yy=y(draft&&denseLux!=null?interpolate(lux,denseLux,curve==0?denseBase:denseTarget):!draft&&nativeLux!=null?interpolate(lux,nativeLux,values):interpolate(lux,values));if(i==0)path.moveTo(xx,yy);else path.lineTo(xx,yy);}
                paint.setColor(draft&&curve==0?0xffbbc5d2:BLUE);paint.setStrokeWidth(dp(draft&&curve==0?2:3));paint.setStyle(Paint.Style.STROKE);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            }
            for(int i=0;i<4;i++){float xx=x(factoryLux[i]);paint.setTextSize(dp(10));paint.setColor(MUTED);String label=String.format(Locale.ROOT,"%.0f",factoryLux[i]);canvas.drawText(label,xx-paint.measureText(label)/2,top+height+dp(20),paint);
                if(draft){paint.setColor(Color.WHITE);canvas.drawCircle(xx,y(target[i]),dp(8),paint);paint.setColor(i==3?MUTED:BLUE);canvas.drawCircle(xx,y(target[i]),dp(5),paint);
                    if(i==point&&i<3){paint.setTextSize(dp(11));String ratio=format(i==0?target[0]/maximum*100:factors[i]*100)+"%";canvas.drawText(ratio,Math.max(left,Math.min(xx-paint.measureText(ratio)/2,left+width-paint.measureText(ratio))),Math.max(dp(11),y(target[i])-dp(13)),paint);}}
            }
            if(!draft&&runtime!=null&&runtime.optBoolean("auto_mode")&&runtime.optString("sensor_status","active").equals("active")&&runtime.has("last_lux")){float lux=(float)runtime.optDouble("last_lux"),xx=x(lux),yy=y(nativeLux==null?interpolate(lux,target):interpolate(lux,nativeLux,nativeNit));paint.setColor(Color.WHITE);canvas.drawCircle(xx,yy,dp(6),paint);paint.setColor(BLUE);canvas.drawCircle(xx,yy,dp(4),paint);}
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event){
            if(!draft||factoryLux==null||!isEnabled())return super.onTouchEvent(event);
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){point=-1;float nearest=dp(32);float[] values;try{values=new CurvePlan(factoryLux,factoryNit,minimum,maximum,factors,curveFloor).nits();}catch(Exception ignored){return false;}
                for(int i=0;i<4;i++){float distance=(float)Math.hypot(event.getX()-x(factoryLux[i]),event.getY()-y(values[i]));if(distance<nearest){nearest=distance;point=i;}}
                if(point<0)return false;startX=event.getX();startY=event.getY();startFactor=point==0?values[0]:factors[point];dragBounds=point==0?CurveEditor.floorBounds(factoryNit,minimum,maximum,factors):point<3?CurveEditor.bounds(point,factoryNit,minimum,maximum,factors,curveFloor):null;moved=false;getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&point>=0){if(Math.hypot(event.getX()-startX,event.getY()-startY)>ViewConfiguration.get(MainActivity.this).getScaledTouchSlop())moved=true;
                if(moved&&point<3){try{float requested=startFactor+(startY-event.getY())/dp(140)*(dragBounds[1]-dragBounds[0]);if(point==0)curveFloor=Math.max(dragBounds[0],Math.min(dragBounds[1],requested));else factors[point]=CurveEditor.clamp(point,requested,factoryNit,minimum,maximum,factors,curveFloor);dirty=true;drawCurve();if(point==0)curveHint.setText("暗处亮度下限："+curvePercent(curveFloor/maximum*100)+"%");else{float[] range=CurveEditor.bounds(point,factoryNit,minimum,maximum,factors,curveFloor);curveHint.setText(format(factoryLux[point])+" lux · "+format(factors[point]*100)+"% · 可调 "+format(range[0]*100)+"%～"+format(range[1]*100)+"%");}}catch(Exception error){show(error.getMessage());}}return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP&&point>=0){int selected=point;getParent().requestDisallowInterceptTouchEvent(false);if(!moved){performClick();editPoint(selected);}point=-1;invalidate();return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){point=-1;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;}return point>=0;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        float interpolate(float lux,float[] values){return interpolate(lux,factoryLux,values);}
        float interpolate(float lux,float[] x,float[] y){if(lux<=x[0])return y[0];for(int i=1;i<x.length;i++)if(lux<=x[i])return y[i-1]+(y[i]-y[i-1])*(lux-x[i-1])/(x[i]-x[i-1]);return y[y.length-1];}
    }
}
