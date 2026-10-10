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
    int INK=0xff24303c,MUTED=0xff616d79,BLUE=0xff3265df,BG=0xfffafafa;boolean darkTheme;
    int themed(int color){return ThemePalette.color(color,darkTheme);}
    MemoryOptions memoryOptions=new MemoryOptions(true,30);Switch persistSwitch;SeekBar retentionSlider,pointLimitSlider;TextView retentionLabel,pointLimitLabel;Bundle restoredUi;
    final ExecutorService worker=Executors.newSingleThreadExecutor(),network=Executors.newSingleThreadExecutor();final Handler ui=new Handler(Looper.getMainLooper());
    FrameLayout content;LinearLayout root,header,pages[]=new LinearLayout[4];
    TextView badge,note,stateTitle,luxReading,nitReading,flow,thermalState,userState,compatibility,logText,curveHint,memoryText,memoryStatus;
    LinearLayout nav[]=new LinearLayout[4]; NavIcon navIcons[]=new NavIcon[4];TextView navNames[]=new TextView[4];Button apply,stop;Switch thermalSwitch;SeekBar ceilingSlider;TextView ceilingText;
    float factors[]={1,1,1,1},factoryLux[],factoryNit[],minimum,maximum,thermalCeiling=43,memoryStrength=1;boolean thermalRelax,memoryEnabled=true,busy,visible,dirty,destroyed;
    Switch memorySwitch;SeekBar memorySlider;PipelineBoard pipeline;boolean english,rereadPending,legacyChecked;String permissionNotice="",loadedBaseline="";boolean permissionDialogVisible,permissionBlocked;ScrollView logScroller;
    JSONObject runtime,lastAnswer;CurveView stateGraph,settingsGraph,memoryGraph;int page;
    TextView memoryOverview,memorySystemStatus,memoryDisabledWarning;
    final Map<String,Switch> memorySwitches=new LinkedHashMap<>();final Map<String,SeekBar> memoryParameters=new LinkedHashMap<>();final Map<String,TextView> memoryLabels=new LinkedHashMap<>();
    long memoryWindow=1500,brightenDelay=1500,darkenDelay=5000;float memoryLuxRange=.3f,thermalCooling=1;int refreshSeconds=2;boolean responseOverride,smallBrightenOverride,updateChecked,updateBusy,firmwarePrompted,legacyDialogVisible;long smallBrightenDelay=5000;JSONArray pendingLegacy;String legacyNotice="";
    Switch responseSwitch,smallBrightenSwitch,lowLightSwitch;boolean lowLightStability;SeekBar smallBrightenSlider;TextView smallBrightenLabel;SeekBar memoryWindowSlider,memoryRangeSlider,coolingSlider,brightenSlider,darkenSlider,refreshSlider;
    TextView memoryWindowLabel,memoryRangeLabel,coolingLabel,brightenLabel,darkenLabel,refreshLabel,updateLabel;
    JSONObject pendingUpdate;AlertDialog updateDialog;
    final Set<AlertDialog> openDialogs=new HashSet<>();
    final Map<String,Runnable> detailOpeners=new HashMap<>();final ScrollView[] settingsScrollers=new ScrollView[10];
    LinearLayout thanksList;TextView thanksStatus;boolean thanksBusy,autoScroll=true;long thanksChecked;
    float lowLightLimit=50;long lowLightBrighten=3000,lowLightDarken=4000;
    SeekBar lowLimitSlider,lowBrightSlider,lowDarkSlider;TextView lowLimitLabel,lowBrightLabel,lowDarkLabel;
    LowLightThresholds lowThresholds=new LowLightThresholds();Switch lowThresholdSwitch;final SeekBar[] lowThresholdSliders=new SeekBar[4];final TextView[] lowThresholdLabels=new TextView[4];
    LowLightAssistGate assistGateOptions=new LowLightAssistGate();Switch assistGateSwitch;SeekBar assistGateWait,assistGateTolerance;TextView assistGateWaitLabel,assistGateToleranceLabel,assistGateStatus;
    OutdoorOptions outdoorOptions=new OutdoorOptions();Switch outdoorSwitch,hbmSwitch,rangeSwitch,oprSwitch;TextView outdoorStatus;final SeekBar[] outdoorSliders=new SeekBar[OutdoorOptions.KEYS.length];final TextView[] outdoorLabels=new TextView[OutdoorOptions.KEYS.length];
    AdvancedOptions advanced=new AdvancedOptions();
    SceneOptions sceneOptions=new SceneOptions();SceneSettings sceneSettings;
    final Switch[] advancedSwitches=new Switch[AdvancedOptions.GROUPS.length];final SeekBar[] advancedSliders=new SeekBar[AdvancedOptions.KEYS.length];final TextView[] advancedLabels=new TextView[AdvancedOptions.KEYS.length];
    TextView advancedStatus,unsavedHint;final TextView[] settingHints=new TextView[10];Switch logFollow;
    LinearLayout settingsHome,settingsTarget,bottomNav;ScrollView settingsHomeScroll;int settingsHomeY;long settingsScrollRestore;boolean settingsHomeRestoring;final LinearLayout[] settingGroups=new LinearLayout[10];final LinearLayout[] settingScreens=new LinearLayout[10];final Button[] settingApply=new Button[10];int settingsGroup=-1;float curveFloor;
    View displayedPage;long pageTransition;
    BrightnessControlOptions controls=new BrightnessControlOptions();ManualBrightnessPanel manualPanel;boolean openManualPanel;
    Switch darkLockSwitch,manualPanelSwitch;final SeekBar[] controlSliders=new SeekBar[4];final TextView[] controlLabels=new TextView[4];TextView controlStatus;
    java.lang.Process bridge;BufferedReader bridgeReader;BufferedWriter bridgeWriter;
    final Runnable tick=()->{if(visible){if(!busy&&!permissionBlocked&&(page==0||page==2||page==1&&(settingsGroup==5||settingsGroup==2)))run("inspect",null);ui.postDelayed(this.tick,page==0?refreshSeconds*1000:5000);}};
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    String tr(String value){return UiText.translate(value,english);}
    class LocalText extends TextView{
        LocalText(){super(MainActivity.this);}
        @Override public void setText(CharSequence value,BufferType type){super.setText(value==null?null:value instanceof android.text.Spanned?value:tr(value.toString()),type);}
    }
    class LocalButton extends Button{
        LocalButton(){super(MainActivity.this);setStateListAnimator(null);setElevation(0);setTranslationZ(0);}
        @Override public void setText(CharSequence value,BufferType type){super.setText(value==null?null:tr(value.toString()),type);}
    }
    TextView text(String value,int size,int color){TextView t=new LocalText();t.setText(value);t.setTextSize(size);t.setTextColor(themed(color));t.setPadding(0,dp(4),0,dp(4));return t;}
    TextView curveLegend(String value){
        String translated=tr(value);android.text.SpannableString styled=new android.text.SpannableString(translated);
        String[] tokens={"灰线","蓝线","浅绿线","浅绿色实点","空心点"};int[] colors={themed(0xffa5aab3),BLUE,themed(0xff78b997),themed(0xff78b997),themed(0xff78b997)};
        for(int i=0;i<tokens.length;i++){String token=tr(tokens[i]);int at=translated.indexOf(token);if(at>=0)styled.setSpan(new android.text.style.ForegroundColorSpan(colors[i]),at,at+token.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        TextView view=new TextView(this);view.setText(styled);view.setTextSize(12);view.setTextColor(MUTED);view.setIncludeFontPadding(false);view.setPadding(0,0,0,dp(4));return view;
    }
    RippleDrawable ripple(int color,float radius){return new RippleDrawable(ColorStateList.valueOf(darkTheme?0x3091b3ff:0x203265df),background(color,radius),background(Color.WHITE,radius));}
    ImageView image(String asset,int size,float radius){ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setBackground(background(0xffeef2ff,radius));v.setClipToOutline(true);
        try{if(asset==null)v.setImageDrawable(getDrawable(getResources().getIdentifier("icon","drawable",getPackageName())));else v.setImageBitmap(decodeAsset(asset,size));}catch(IOException ignored){}
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return v;}
    Bitmap decodeAsset(String asset,int size)throws IOException{
        int target=Math.max(1,dp(size));BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream input=getAssets().open(asset)){BitmapFactory.decodeStream(input,null,bounds);}
        BitmapFactory.Options options=new BitmapFactory.Options();int sample=1;
        while(bounds.outWidth/sample>target*2||bounds.outHeight/sample>target*2)sample<<=1;
        options.inSampleSize=sample;
        try(InputStream input=getAssets().open(asset)){return BitmapFactory.decodeStream(input,null,options);}
    }
    AlertDialog dialog(String title,View body){LinearLayout actions=column();actions.setOrientation(LinearLayout.HORIZONTAL);actions.setBaselineAligned(false);actions.setGravity(Gravity.END);actions.setTag("actions");
        ResponsiveDialog box=new ResponsiveDialog(this,text(title,20,INK),body,actions);boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;box.setPadding(dp(22),dp(landscape?12:20),dp(22),dp(landscape?12:18));box.setBackground(background(BG,24));AlertDialog d=new AlertDialog.Builder(this).setView(box).create();
        box.setClipToOutline(true);Window w=d.getWindow();ResponsiveDialog.configure(this,w,dp(landscape?520:440));if(w!=null)w.setDimAmount(.32f);
        box.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){openDialogs.add(d);}public void onViewDetachedFromWindow(View v){openDialogs.remove(d);}});
        // Animate the content itself: application styles are not valid WMS animation resources.
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){box.setAlpha(0);d.setOnShowListener(v->box.animate().alpha(1).setDuration(180).start());}return d;}
    void dialogButton(AlertDialog d,String label,Runnable task,boolean primary){View decor=d.getWindow().getDecorView();LinearLayout actions=(LinearLayout)decor.findViewWithTag("actions");
        Button b=compact(label,()->{d.dismiss();if(task!=null)task.run();},actions);b.setTextColor(primary?(darkTheme?BG:Color.WHITE):BLUE);b.setBackground(ripple(primary?BLUE:0xffeef2fa,12));}
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
    GradientDrawable background(int color,float radius){GradientDrawable b=new GradientDrawable();b.setColor(themed(color));b.setCornerRadius(dp(radius));return b;}
    LinearLayout card(LinearLayout parent){LinearLayout b=column();b.setPadding(dp(18),dp(17),dp(18),dp(17));b.setBackground(background(Color.WHITE,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);parent.addView(b,lp);return b;}
    LinearLayout row(LinearLayout parent){LinearLayout r=new LinearLayout(this);r.setBaselineAligned(false);r.setGravity(Gravity.CENTER_VERTICAL);parent.addView(r,new LinearLayout.LayoutParams(-1,-2));return r;}
    Button action(String name,Runnable task){Button b=new LocalButton();b.setText(name);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(BLUE);b.setBackground(ripple(0xffeef2fa,12));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);b.setPadding(dp(10),0,dp(10),0);b.setOnClickListener(v->task.run());return b;}
    Button button(String name,Runnable task,LinearLayout parent){Button b=action(name,task);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(8);lp.bottomMargin=dp(4);parent.addView(b,lp);return b;}
    Button compact(String name,Runnable task,LinearLayout parent){Button b=action(name,task);boolean dialog="actions".equals(parent.getTag());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(dialog?4:3),dp(dialog?14:6),dp(dialog?4:3),dp(dialog?0:4));parent.addView(b,lp);return b;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);settingsHomeY=saved==null?0:saved.getInt("settings_home_y",0);openManualPanel=getIntent().getBooleanExtra("manual_brightness_panel",false);restoredUi=saved;darkTheme=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;INK=themed(INK);MUTED=themed(MUTED);BLUE=themed(BLUE);BG=themed(BG);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(darkTheme?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        autoScroll=getPreferences(0).getBoolean("log_auto_scroll",true);refreshSeconds=getPreferences(0).getInt("refresh_seconds",2);english=!getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        try{factors=CurvePlan.factors(getPreferences(0).getString("draft","1,1,1,1"));}catch(Exception ignored){}
        curveFloor=getPreferences(0).getFloat("draft_floor",0);
        root=column();root.setBackgroundColor(BG);setContentView(root);
        header=row(root);header.setPadding(dp(18),0,dp(18),0);header.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(64)));
        ImageView icon=image(null,38,12);header.addView(icon);
        LinearLayout brand=column();LinearLayout.LayoutParams brandLp=new LinearLayout.LayoutParams(0,-2,1);brandLp.leftMargin=dp(12);header.addView(brand,brandLp);brand.addView(text("HyperLux",20,INK));
        badge=text("连接中",12,INK);badge.setPadding(dp(10),dp(5),dp(10),dp(5));badge.setBackground(background(0xffedf3ff,10));header.addView(badge);
        note=text("",12,MUTED);note.setVisibility(View.GONE);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<4;i++){LinearLayout b=column();pages[i]=b;if(i==0||i==2){b.setPadding(dp(i==0?18:16),dp(8),dp(i==0?18:16),dp(i==0?8:6));content.addView(b,new FrameLayout.LayoutParams(-1,-1));}else{ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);if(i==1){settingsHomeScroll=scroll;scroll.setFocusableInTouchMode(true);scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);}b.setPadding(dp(18),dp(10),dp(18),dp(22));scroll.addView(b);content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));scroll.setVisibility(View.GONE);}}
        makeStatus();makeSettings();makeLogs();makeAbout();
        LinearLayout bottom=row(root);bottomNav=bottom;bottom.setBaselineAligned(false);bottom.setPadding(dp(8),dp(8),dp(8),dp(8));String[] names={"状态","设置","日志","关于"};
        for(int i=0;i<4;i++){final int index=i;LinearLayout tab=column();tab.setBaselineAligned(false);tab.setGravity(Gravity.CENTER);tab.setBackground(ripple(Color.TRANSPARENT,14));tab.setClickable(true);tab.setOnClickListener(v->select(index));
            NavIcon iconView=new NavIcon(i);tab.addView(iconView,new LinearLayout.LayoutParams(dp(25),dp(25)));TextView name=text(names[i],12,MUTED);name.setGravity(Gravity.CENTER);name.setIncludeFontPadding(false);name.setPadding(0,dp(4),0,0);tab.addView(name,new LinearLayout.LayoutParams(-1,dp(22)));bottom.addView(tab,new LinearLayout.LayoutParams(0,dp(58),1));nav[i]=tab;navIcons[i]=iconView;navNames[i]=name;}
        select(saved==null?0:saved.getInt("page",0));apply.setEnabled(false);
    }
    void makeStatus(){
        stateTitle=text("",16,INK);luxReading=text("",20,BLUE);nitReading=text("",20,INK);flow=text("",12,MUTED);thermalState=text("",12,INK);userState=text("",12,MUTED);
        pipeline=new PipelineBoard();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.addView(pipeline,new android.widget.FrameLayout.LayoutParams(-1,-2));pages[0].addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    SeekBar parameter(LinearLayout parent,int maximum,int initial,java.util.function.IntConsumer changed){SeekBar s=new SeekBar(this);s.setMax(maximum);s.setProgress(initial);parent.addView(s,new LinearLayout.LayoutParams(-1,dp(38)));s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar v,int p,boolean user){if(user){changed.accept(p);dirty=true;drawCurve();}}public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}});return s;}
    LinearLayout settingsDetails(LinearLayout parent,String key){
        LinearLayout body=column();boolean expanded=getPreferences(0).getBoolean("detail_"+key,false);final String openText=key.startsWith("scene_")?"收起详细设置":"收起详细参数",closedText=key.startsWith("scene_")?"展开详细设置":"展开详细参数";
        Button toggle=action("",()->{});toggle.setText(tr(expanded?openText:closedText)+(expanded?"  ▴":"  ▾"));toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(8);lp.bottomMargin=dp(6);parent.addView(toggle,lp);parent.addView(body);body.setVisibility(expanded?View.VISIBLE:View.GONE);
        toggle.setOnClickListener(v->{boolean open=body.getVisibility()!=View.VISIBLE;body.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(tr(open?openText:closedText)+(open?"  ▴":"  ▾"));getPreferences(0).edit().putBoolean("detail_"+key,open).apply();});detailOpeners.put(key,()->{if(body.getVisibility()!=View.VISIBLE)toggle.performClick();});return body;
    }
    void quickPreset(String group,int mode){if(busy){show("另一项操作还在执行");return;}try{if(runtime==null)throw new IllegalStateException("连接后读取曲线");JSONObject next=SettingsPresets.merge(configuration(),SettingsPresets.patch(group,mode,runtime));loadOptions(next);dirty=true;drawCurve();show("预设已选中，保存并应用后生效");}catch(Exception error){show(error.getMessage());}}
    void quickPresetRow(LinearLayout parent,String group,String first,String second){LinearLayout r=row(parent);compact("系统默认",()->quickPreset(group,0),r);compact(first,()->quickPreset(group,1),r);compact(second,()->quickPreset(group,2),r);}
    void makeSettings(){
        LinearLayout b=card(pages[1]);b.setPadding(dp(16),dp(13),dp(16),dp(13));LinearLayout engineHeading=row(b);engineHeading.setBaselineAligned(false);TextView engineTitle=text("引擎控制",18,INK);engineTitle.setIncludeFontPadding(false);engineHeading.addView(engineTitle,new LinearLayout.LayoutParams(0,-2,1));unsavedHint=text("有未保存的设置",12,0xffbd3434);unsavedHint.setIncludeFontPadding(false);unsavedHint.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);engineHeading.addView(unsavedHint,new LinearLayout.LayoutParams(-2,-2));unsavedHint.setVisibility(View.GONE);LinearLayout r=row(b);apply=compact("保存并应用",this::submit,r);stop=compact("停用并恢复",()->run("stop",null),r);
        LinearLayout files=row(b);compact("导出配置",this::exportConfiguration,files);compact("导入配置",this::importConfiguration,files);
        settingsHome=column();pages[1].addView(settingsHome);
        String[] names={"亮度曲线","手动记忆","户外高亮","环境变化与确认","亮度过渡","场景判定与控制","运行状态与界面","暗光稳定与锁定","主屏手动亮度","温控与显示保护"};
        String[] descriptions={"基础亮度、暗处下限与曲线预设","偏好强度、持久保存与锁屏交接","强光增强、HBM 与手动阳光屏","主辅光感阈值与确认时间","分别设置变亮和变暗时长","场景的进入条件、生效效果与退出条件","兼容状态与界面刷新","减少暗光波动，或定时保持亮度","磁贴面板、直接写值与自动退让","显示温控、温度门槛与冷却恢复"};
        for(int i:new int[]{3,5,0,1,4,7,2,9,8,6}){
            if(i==3||i==0||i==7||i==8){TextView section=text(i==3?"光感输入与场景":i==0?"曲线目标与响应":i==7?"输出范围与保护":"运行与工具",13,BLUE);section.setPadding(dp(4),dp(12),dp(4),dp(8));settingsHome.addView(section);}
            final int group=i;LinearLayout entry=card(settingsHome);entry.setPadding(dp(16),dp(11),dp(12),dp(11));((LinearLayout.LayoutParams)entry.getLayoutParams()).bottomMargin=dp(8);entry.setBackground(ripple(Color.WHITE,18));entry.setClipToOutline(true);entry.setClickable(true);entry.setOnClickListener(v->showSettingsGroup(group));LinearLayout line=row(entry);line.setBaselineAligned(false);LinearLayout labels=column();line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView name=text(names[i],16,INK);name.setIncludeFontPadding(false);name.setPadding(0,0,0,dp(3));labels.addView(name);TextView description=text(descriptions[i],12,MUTED);description.setIncludeFontPadding(false);description.setPadding(0,0,0,0);labels.addView(description);line.addView(new NavigationArrow(false),new LinearLayout.LayoutParams(dp(28),dp(32)));
            // Each category owns a separate screen and scroll position, outside the settings home.
            LinearLayout screen=column();screen.setBackgroundColor(BG);settingScreens[i]=screen;content.addView(screen,new FrameLayout.LayoutParams(-1,-1));screen.setVisibility(View.GONE);
            LinearLayout bar=row(screen);bar.setPadding(dp(10),dp(6),dp(18),dp(6));bar.setBaselineAligned(false);NavigationArrow back=new NavigationArrow(true);back.setBackground(ripple(Color.TRANSPARENT,24));back.setClipToOutline(true);back.setClickable(true);back.setFocusable(true);back.setContentDescription(tr("返回设置"));back.setOnClickListener(v->showSettingsGroup(-1));bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView heading=text(names[i],18,INK);LinearLayout.LayoutParams title=new LinearLayout.LayoutParams(0,-2,1);title.leftMargin=dp(6);heading.setMaxLines(2);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.setAutoSizeTextTypeUniformWithConfiguration(12,18,1,android.util.TypedValue.COMPLEX_UNIT_SP);LinearLayout navTitle=column();bar.addView(navTitle,title);navTitle.addView(heading);settingHints[i]=text("有未保存的设置",10,0xffbd3434);settingHints[i].setPadding(0,0,0,0);navTitle.addView(settingHints[i]);settingHints[i].setVisibility(View.GONE);settingApply[i]=action("保存并应用",this::submit);settingApply[i].setTextSize(12);settingApply[i].setMinWidth(0);settingApply[i].setMinimumWidth(0);settingApply[i].setMaxLines(2);bar.addView(settingApply[i],new LinearLayout.LayoutParams(dp(104),dp(48)));settingApply[i].setEnabled(false);
            ScrollView scroll=new ScrollView(this);settingsScrollers[i]=scroll;scroll.setFillViewport(true);screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));settingGroups[i]=column();settingGroups[i].setPadding(dp(18),dp(10),dp(18),dp(12));scroll.addView(settingGroups[i]);
        }
        settingsTarget=settingGroups[0];makeCurveSettings();
        settingsTarget=settingGroups[1];makeMemorySettings();
        settingsTarget=settingGroups[2];makeOutdoorSettings();
        settingsTarget=settingGroups[3];makeMainResponseSettings();makeAdvancedSettings(0);makeAdvancedSettings(1);
        settingsTarget=settingGroups[4];makeAdvancedSettings(2);
        settingsTarget=settingGroups[5];sceneSettings=new SceneSettings(this,settingsTarget);makeAdvancedSettings(4,sceneSettings.effects(2));
        settingsTarget=settingGroups[2];makeAdvancedSettings(3,sceneSettings.addScene(settingsTarget,7));sceneSettings.refresh();
        settingsTarget=settingGroups[9];makeThermalSettings();
        settingsTarget=settingGroups[6];makeAdvancedStatus();makeRefreshSettings();
        settingsTarget=settingGroups[7];makeDarkLockSettings();makeLowLightSettings();
        settingsTarget=settingGroups[8];makeManualPanelSettings();
    }
    void showSettingsGroup(int group){rememberSettingsHome();settingsGroup=group;header.setVisibility(group<0?View.VISIBLE:View.GONE);if(bottomNav!=null)bottomNav.setVisibility(group<0?View.VISIBLE:View.GONE);transitionPage(group<0?content.getChildAt(1):settingScreens[group],group<0?-1:1);drawCurve();if(group<0)restoreSettingsHome();}
    void rememberSettingsHome(){if(settingsHomeScroll!=null&&displayedPage==settingsHomeScroll&&!settingsHomeRestoring)settingsHomeY=settingsHomeScroll.getScrollY();++settingsScrollRestore;settingsHomeRestoring=false;}
    void restoreSettingsHome(){if(settingsHomeScroll==null)return;final ScrollView scroll=settingsHomeScroll;final int y=settingsHomeY;final long request=++settingsScrollRestore;settingsHomeRestoring=true;scroll.requestFocus();
        scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(scroll.getViewTreeObserver().isAlive())scroll.getViewTreeObserver().removeOnPreDrawListener(this);if(settingsScrollRestore==request){settingsHomeRestoring=false;if(!destroyed&&page==1&&settingsGroup<0&&displayedPage==scroll)scroll.scrollTo(0,y);}return true;}});scroll.invalidate();
    }
    void openAdvice(StatusAdvice.Action action){
        if(action.group<0||action.group>=settingScreens.length)return;
        select(1);showSettingsGroup(action.group);
        Runnable expand=detailOpeners.get(action.target.startsWith("scene_")?action.target:action.group==2?"outdoor":action.target.equals("low_light")?"low":"");if(expand!=null)expand.run();
        View target=action.target.equals("range")?rangeSwitch:action.target.equals("opr")?oprSwitch:action.target.equals("thermal")?thermalSwitch:action.target.equals("dark_lock")?darkLockSwitch:action.target.equals("low_light")?lowLightSwitch:action.target.equals("curve")?settingsGraph:action.target.equals("memory")?memorySwitch:sceneSettings==null?null:sceneSettings.find(action.target);
        ScrollView scroll=settingsScrollers[action.group];if(target!=null&&scroll!=null){scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(scroll.getViewTreeObserver().isAlive())scroll.getViewTreeObserver().removeOnPreDrawListener(this);if(!destroyed&&displayedPage==settingScreens[action.group]&&target.isShown()){Rect rect=new Rect();target.getDrawingRect(rect);scroll.offsetDescendantRectToMyCoords(target,rect);scroll.smoothScrollTo(0,Math.max(0,rect.top-dp(16)));if(!action.target.startsWith("scene_"))target.requestFocus();}return true;}});scroll.invalidate();}
    }
    android.text.SpannableStringBuilder adviceText(StatusAdvice advice){
        android.text.SpannableStringBuilder result=new android.text.SpannableStringBuilder();if(advice.inlineActions)result.append(tr(advice.detail));
        for(StatusAdvice.Action action:advice.actions){String label=tr(action.text);int start;if(advice.inlineActions){start=result.toString().indexOf(label);if(start<0)continue;}else{if(result.length()>0)result.append("\n");start=result.length();result.append(label);}
            result.setSpan(new android.text.style.ClickableSpan(){@Override public void onClick(View widget){openAdvice(action);}@Override public void updateDrawState(android.text.TextPaint paint){paint.setColor(BLUE);paint.setUnderlineText(false);}},start,start+label.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }return result;
    }
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
        LinearLayout b=card(settingsTarget);b.addView(text("照度与亮度曲线",18,INK));
        LinearLayout presets=row(b);compact("柔和",()->preset(new float[]{.7f,.8f,.95f,1}),presets);compact("系统默认",()->preset(new float[]{1,1,1,1}),presets);compact("稍亮",()->preset(new float[]{1.2f,1.15f,1.05f,1}),presets);
        settingsGraph=new CurveView(true);b.addView(settingsGraph,new LinearLayout.LayoutParams(-1,dp(235)));
        b.addView(curveLegend("灰线：系统默认 · 蓝线：基础曲线"));curveHint=text("连接后显示节点范围",12,MUTED);b.addView(curveHint);
        b.addView(text("拖动蓝色节点，或点击输入数值；保存并应用后生效。",12,MUTED));
        b.addView(text("实际生效曲线会受到手动记忆影响，可在「手动记忆」页查看。",12,0xffbd3434));
        b.addView(text("左端可设置暗处亮度下限。若调不上去，先提高相邻节点；手动选择和温控仍优先。",12,MUTED));
        presets=row(b);compact("保存为预设",this::savePreset,presets);compact("我的预设",this::pickPreset,presets);
    }
    void makeOutdoorSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("户外高亮增强",18,INK));quickPresetRow(b,"outdoor","温和增强","均衡增强");
        button("强光优先",()->quickPreset("outdoor",3),b);
        b.addView(text("强光优先：提高目标并开放高亮范围；支持时放宽画面灰阶限亮，保留系统过渡和温控保护。保存后生效。",12,MUTED));
        outdoorSwitch=new Switch(this);outdoorSwitch.setText(tr("持续强光下提高亮度"));outdoorSwitch.setTextSize(14);b.addView(outdoorSwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        b.addView(text("持续强光下提高目标亮度。离开强光、锁屏、手动调节或过热时退出。默认关闭。",12,MUTED));
        rangeSwitch=new Switch(this);rangeSwitch.setText(tr("开放系统映射内的高亮范围"));rangeSwitch.setTextSize(14);b.addView(rangeSwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        b.addView(text("强光下放宽软件亮度上限，开放本机映射支持的高亮范围。",12,MUTED));
        oprSwitch=new Switch(this);oprSwitch.setText(tr("强光下放宽画面限亮"));oprSwitch.setTextSize(14);b.addView(oprSwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        b.addView(text("强光下减少画面内容造成的限亮，提高可用亮度上限；需先开放高亮范围。",12,MUTED));
        b.addView(text("两项仍受温控、电量和 HBM 时间预算限制。",11,MUTED));
        outdoorStatus=text("等待读取户外高亮条件",13,BLUE);b.addView(outdoorStatus);button("查看高亮条件",this::branches,b);
        b=settingsDetails(b,"outdoor");
        for(int i:new int[]{0,1,5})outdoorParameter(b,i);
        LinearLayout details=column();b.addView(details);
        for(int i:new int[]{2,3,4,6,7})outdoorParameter(details,i);
        hbmSwitch=new Switch(this);hbmSwitch.setText(tr("调整自动 HBM 触发与预算"));hbmSwitch.setTextSize(14);details.addView(hbmSwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        details.addView(text("仅支持带 HBM 计时的设备。预算最多为系统的 2 倍且不超时间窗口；仍受温控与省电限制。",12,MUTED));for(int i:new int[]{8,9})outdoorParameter(details,i);
        oprSwitch.setOnCheckedChangeListener((v,on)->{if(on!=outdoorOptions.flags[3]){outdoorOptions.flags[3]=on;dirty=true;drawOutdoor();}});
        outdoorSwitch.setOnCheckedChangeListener((v,on)->{if(on!=outdoorOptions.flags[0]){outdoorOptions.flags[0]=on;if(!on){outdoorOptions.flags[1]=false;outdoorOptions.flags[2]=false;outdoorOptions.flags[3]=false;}dirty=true;drawOutdoor();}});
        hbmSwitch.setOnCheckedChangeListener((v,on)->{if(on!=outdoorOptions.flags[1]){outdoorOptions.flags[1]=on;dirty=true;drawOutdoor();}});rangeSwitch.setOnCheckedChangeListener((v,on)->{if(on!=outdoorOptions.flags[2]){outdoorOptions.flags[2]=on;if(!on)outdoorOptions.flags[3]=false;dirty=true;drawOutdoor();}});
    }
    void outdoorParameter(LinearLayout parent,int key){outdoorLabels[key]=text("",13,INK);parent.addView(outdoorLabels[key]);outdoorSliders[key]=parameter(parent,(int)Math.round((OutdoorOptions.MAX[key]-OutdoorOptions.MIN[key])/OutdoorOptions.STEP[key]),(int)Math.round((OutdoorOptions.DEFAULT[key]-OutdoorOptions.MIN[key])/OutdoorOptions.STEP[key]),value->{outdoorOptions.values[key]=OutdoorOptions.MIN[key]+value*OutdoorOptions.STEP[key];if(outdoorOptions.values[1]<=outdoorOptions.values[0])outdoorOptions.values[1]=Math.ceil((outdoorOptions.values[0]+2000)/2000)*2000;});}
    void drawOutdoor(){if(outdoorSwitch==null)return;
        boolean supported=runtime!=null&&runtime.optBoolean("outdoor_supported"),hbm=runtime!=null&&runtime.optBoolean("outdoor_hbm_supported"),range=runtime!=null&&runtime.optBoolean("outdoor_range_supported");
        outdoorSwitch.setChecked(outdoorOptions.flags[0]);outdoorSwitch.setEnabled(supported||outdoorOptions.flags[0]);hbmSwitch.setChecked(outdoorOptions.flags[1]);hbmSwitch.setEnabled(outdoorOptions.flags[0]&&(hbm||outdoorOptions.flags[1]));rangeSwitch.setChecked(outdoorOptions.flags[2]);rangeSwitch.setEnabled(outdoorOptions.flags[0]&&(range||outdoorOptions.flags[2]));
        oprSwitch.setChecked(outdoorOptions.flags[3]);oprSwitch.setEnabled(outdoorOptions.flags[0]&&outdoorOptions.flags[2]&&(runtime!=null&&runtime.optBoolean("outdoor_opr_supported")||outdoorOptions.flags[3]));
        String[] labels={"进入照度","全强度照度","进入确认","退出确认","退出照度比例","增强强度","单次最长增强","增强冷却","HBM 触发照度倍率","HBM 时间预算倍率"};
        for(int i=0;i<outdoorSliders.length;i++)if(outdoorSliders[i]!=null){double value=outdoorOptions.values[i];boolean time=i==2||i==3||i==6||i==7;outdoorLabels[i].setText(tr(labels[i])+"："+format(time?value/1000:i==4||i==5?value*100:value)+(time?tr(" 秒"):i==0||i==1?" lux":i==4||i==5?"%":"×"));outdoorSliders[i].setProgress((int)Math.round((value-OutdoorOptions.MIN[i])/OutdoorOptions.STEP[i]));enableParameter(outdoorSliders[i],outdoorLabels[i],supported&&outdoorOptions.flags[0]&&(i<8||hbm&&outdoorOptions.flags[1]));}
        JSONObject state=runtime==null?null:runtime.optJSONObject("outdoor");outdoorStatus.setText(state==null?tr("等待读取户外高亮条件"):outdoorSummary(state));drawDraftState();
    }
    String outdoorSummary(JSONObject state){String message=tr(state.optString("text","未取得"));if(state.has("blocking_condition"))message+=" · "+tr(OutdoorController.description(state.optString("blocking_condition")));if(state.has("session_left_ms"))message+=" · "+tr("剩余 ")+format(state.optDouble("session_left_ms")/1000)+tr(" 秒");return message;}
    String outdoorDetails(JSONObject state){StringBuilder out=new StringBuilder(outdoorSummary(state));
        String[][] fields={{"normal_max","HBM 亮度分界"},{"native_range_max","系统原范围上限"},{"effective_range_max","当前范围上限"},{"dynamic_native_max","照度动态范围原上限"},{"dynamic_effective_max","照度动态范围现上限"},{"device_mapping_max","设备映射上限"},{"display_range_max","显示范围上限"},{"clamper_max","最终限亮器上限"},{"requested_brightness","户外请求目标"},{"actual_brightness","当前屏幕亮度"}};
        for(String[] field:fields)if(state.has(field[0]))out.append("\n").append(tr(field[1])).append(": ").append(format(state.optDouble(field[0])*100)).append("%");
        out.append("\n").append(tr(state.optBoolean("controller_managed")?"本机使用 HBM 控制器":"本机未启用 HBM 计时，不能据此推断没有其他限亮"));
        if(state.has("original_minimum_lux"))out.append("\n").append(tr("HBM 原触发照度")).append(": ").append(format(state.optDouble("original_minimum_lux"))).append(" lux");
        if(state.has("effective_minimumLux"))out.append("\n").append(tr("HBM 当前触发照度")).append(": ").append(format(state.optDouble("effective_minimumLux"))).append(" lux");
        if(state.has("hbm_remaining_estimate_ms"))out.append("\n").append(tr("HBM 剩余预算估值")).append(": ").append(format(state.optDouble("hbm_remaining_estimate_ms")/1000)).append(tr(" 秒"));
        out.append("\n").append(tr("画面限亮放宽次数")).append(": ").append(state.optLong("opr_adjustments"));
        JSONArray stages=state.optJSONArray("limit_trace");if(stages!=null){out.append("\n\n").append(tr("最近观察到的输出限制"));long now=SystemClock.uptimeMillis();int shown=0;for(int i=stages.length()-1;i>=0&&shown<6;i--){JSONObject stage=stages.optJSONObject(i);if(stage==null||!stage.optBoolean("limited")||now-stage.optLong("uptime_ms")>60000)continue;out.append("\n").append(limitStage(stage.optString("stage"))).append(": ").append(format(stage.optDouble("before")*100)).append("% → ").append(format(stage.optDouble("after")*100)).append("% · ").append(format((now-stage.optLong("uptime_ms"))/1000d)).append(tr(" 秒前"));shown++;}if(shown==0)out.append("\n").append(tr("最近一分钟未观察到降亮，不代表驱动没有限制"));}
        return out.toString();
    }
    String limitStage(String stage){switch(stage){case "outdoor_opr_native":case "adjustBrightnessByOpr":return tr("画面灰阶限亮");case "adjustBrightnessByThermal":return tr("显示温控");case "adjustBrightnessByBattery":return tr("电池限亮");case "adjustBrightnessByPowerSaveMode":return tr("省电限亮");case "adjustBrightnessToPeak":return tr("峰值范围");case "adjustBrightnessByBcbc":return tr("画面亮度修正");case "adjustSdrBrightness":return tr("SDR 输出");default:return stage;}}
    void makeMemorySettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("记住手动偏好",18,INK));
        LinearLayout strengths=row(b);compact("完整记忆",()->{memoryEnabled=true;memoryStrength=1;dirty=true;drawCurve();},strengths);compact("渐进记忆",()->{memoryEnabled=true;memoryStrength=.25f;dirty=true;drawCurve();},strengths);compact("不记忆",()->{memoryEnabled=false;dirty=true;drawCurve();},strengths);memorySwitch=new Switch(this);memorySwitch.setText(tr("记住我的手动调整"));memorySwitch.setTextSize(14);memorySwitch.setShowText(false);b.addView(memorySwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        memorySwitch.setOnCheckedChangeListener((v,on)->{if(on!=memoryEnabled){memoryEnabled=on;dirty=true;drawCurve();}});
        memoryDisabledWarning=text("不记忆将会导致在开启自动亮度时，您将无法手动调整屏幕亮度",12,0xffbd3434);memoryDisabledWarning.setVisibility(View.GONE);b.addView(memoryDisabledWarning);
        b.addView(text("手动调节立即生效。强度越低，曲线记得越慢；100% 沿用系统力度。",12,MUTED));
        b=settingsDetails(b,"memory-strength");
        memoryText=text("",13,INK);b.addView(memoryText);memorySlider=new SeekBar(this);memorySlider.setMax(99);b.addView(memorySlider,new LinearLayout.LayoutParams(-1,dp(40)));
        memorySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){if(from){memoryStrength=(p+1)/100f;dirty=true;drawCurve();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        memoryWindowLabel=text("",13,INK);b.addView(memoryWindowLabel);memoryWindowSlider=parameter(b,5,2,value->memoryWindow=500+value*500);
        b.addView(text("合并连续拖动，避免一次滑动被反复累计。",12,MUTED));memoryRangeLabel=text("",13,INK);b.addView(memoryRangeLabel);memoryRangeSlider=parameter(b,8,4,value->memoryLuxRange=.1f+value*.05f);
        b.addView(text("只用于合并同一次拖动；不影响亮度保持或清除记忆。暗处容差至少 5 lux。",12,MUTED));
        LinearLayout overview=card(settingsTarget);overview.addView(text("曲线与记忆",18,INK));
        overview.addView(text("基础曲线定起点，手动记忆改附近亮度，存档供下次恢复。暂时保持亮度不等于清除记忆。",12,MUTED));
        memoryOverview=text("连接后显示实际节点与系统能力",13,BLUE);overview.addView(memoryOverview);
        memoryGraph=new CurveView(false,true);overview.addView(memoryGraph,new LinearLayout.LayoutParams(-1,dp(230)));
        overview.addView(curveLegend("蓝线：已应用基础曲线 · 浅绿线：记忆后的实际曲线\n浅绿色实点：当前记忆 · 空心点：已保存，恢复后才生效"));
        overview.addView(text("曲线表示亮度目标，最终屏幕亮度仍受温控和场景影响。",12,MUTED));
        button("查看记忆节点与交接记录",this::memoryDetails,overview);
        b=card(settingsTarget);b.addView(text("保存与恢复",18,INK));
        memoryFlag(b,"memory_persist","跨重启保留手动记忆");persistSwitch=memorySwitches.get("memory_persist");
        b=settingsDetails(b,"memory-save");
        memoryParameter(b,"memory_retention_days","存档保留天数",1,90,1," 天");retentionLabel=memoryLabels.get("memory_retention_days");retentionSlider=memoryParameters.get("memory_retention_days");
        memoryParameter(b,"memory_max_points","最多保存手动点",0,7,1," 个");pointLimitLabel=memoryLabels.get("memory_max_points");pointLimitSlider=memoryParameters.get("memory_max_points");
        b.addView(text("0 为本机支持的最多数量，不增加编辑点。换曲线、用户或固件后不混用旧存档；关闭保存保留当前偏好。",12,MUTED));
        memoryStatus=text("等待读取手动锚点",12,MUTED);b.addView(memoryStatus);
        b=card(settingsTarget);b.addView(text("锁屏后的记忆",18,INK));memoryFlag(b,"memory_restore_unlock","亮屏后恢复已保存偏好");memoryFlag(b,"memory_restore_same_scene","仅在相近照度恢复");
        b=settingsDetails(b,"memory-wake");
        memoryParameter(b,"memory_restore_ratio","恢复照度容差",.1,2,.1,"%");memoryParameter(b,"memory_restore_min_lux","暗处最小容差",1,30,1," lux");memoryParameter(b,"memory_restore_settle","亮屏采样稳定等待",500,5000,500," 秒");
        b.addView(text("恢复时同时核对锁屏前照度与存档点，避免带回其他环境的偏好。",12,MUTED));
        memoryFlag(b,"memory_restore_replace","保留锁屏前的完整偏好");
        b.addView(text("亮屏后确认照度，再恢复一次；新手动调整优先。开启完整偏好可替换旧备用点，关闭则只补空缺。",12,MUTED));
        b=card(settingsTarget);b.addView(text("系统短期记忆",18,INK));memorySystemStatus=text("连接后读取系统记忆规则",12,BLUE);b.addView(memorySystemStatus);
        b=settingsDetails(b,"memory-native");
        memoryFlag(b,"memory_reset_override","自定义锁屏后重判规则");memoryParameter(b,"memory_reset_off_minutes","照度变化重判起始",1,30,1," 分钟");memoryParameter(b,"memory_reset_lux","重判照度差",1,200,1," lux");memoryParameter(b,"memory_reset_force_minutes","最长免重判熄屏时长",1,120,1," 分钟");
        b.addView(text("熄屏够久且照度变化够大，或超过最长时长时，允许系统重判偏好；不会删除存档。默认沿用系统。",12,MUTED));
        memoryFlag(b,"memory_timeout_override","自定义短期模型失效时间");memoryParameter(b,"memory_timeout_minutes","短期模型失效等待",1,120,1," 分钟");
        b.addView(text("到时后，系统在下一次有效照度下重判。只影响新计时，不立即清除曲线。",12,MUTED));
        button("清除手动记忆",()->messageDialog("清除手动记忆？","清除当前曲线与已保存的手动节点，保留基础曲线。此操作不会在下次重启时重复执行。","清除",()->run("reset-memory",null)),b);
    }
    void memoryFlag(LinearLayout parent,String key,String label){Switch v=new Switch(this);v.setText(tr(label));v.setTextSize(14);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(48)));memorySwitches.put(key,v);v.setOnCheckedChangeListener((button,on)->{try{JSONObject options=new JSONObject();memoryOptions.put(options);if(on!=options.optBoolean(key)){memoryOptions=memoryOptions.with(key,on);dirty=true;drawCurve();}}catch(Exception invalid){show(invalid.getMessage());}});}
    void memoryParameter(LinearLayout parent,String key,String label,double min,double max,double step,String unit){TextView name=text("",13,INK);parent.addView(name);memoryLabels.put(key,name);SeekBar bar=parameter(parent,(int)Math.round((max-min)/step),0,value->{double next=min+value*step;if(key.equals("memory_reset_off_minutes")&&next>memoryOptions.forceMinutes)memoryOptions=memoryOptions.with("memory_reset_force_minutes",(int)next);if(key.equals("memory_reset_force_minutes")&&next<memoryOptions.offMinutes)next=memoryOptions.offMinutes;memoryOptions=memoryOptions.with(key,next);});bar.setTag(new Object[]{label,min,step,unit});memoryParameters.put(key,bar);}
    void drawMemory(){if(memoryDisabledWarning!=null)memoryDisabledWarning.setVisibility(memoryEnabled?View.GONE:View.VISIBLE);if(memoryGraph==null)return;try{JSONObject o=new JSONObject();memoryOptions.put(o);boolean connected=runtime!=null,persist=connected&&memoryEnabled&&runtime.optBoolean("memory_persist_supported")&&memoryOptions.persist;
        for(Map.Entry<String,Switch> e:memorySwitches.entrySet()){String k=e.getKey();Switch v=e.getValue();v.setChecked(o.getBoolean(k));boolean supported=k.equals("memory_reset_override")?connected&&runtime.optBoolean("memory_reset_supported"):k.equals("memory_timeout_override")?connected&&runtime.optBoolean("memory_timeout_supported"):connected&&runtime.optBoolean("memory_persist_supported");v.setEnabled((supported&&memoryEnabled&&(k.equals("memory_persist")||k.startsWith("memory_reset_")||k.startsWith("memory_timeout_")||persist))||o.getBoolean(k)&&!supported);}
        for(Map.Entry<String,SeekBar> e:memoryParameters.entrySet()){String k=e.getKey();SeekBar bar=e.getValue();Object[] spec=(Object[])bar.getTag();double min=(Double)spec[1],step=(Double)spec[2],value=o.getDouble(k);if(k.equals("memory_max_points"))bar.setMax(connected?runtime.optInt("memory_point_capacity",0):7);bar.setProgress((int)Math.round((value-min)/step));String shown=k.equals("memory_max_points")&&value==0?tr("自动（按本机能力）"):format(k.equals("memory_restore_ratio")?value*100:k.equals("memory_restore_settle")?value/1000:value)+tr((String)spec[3]);memoryLabels.get(k).setText(tr((String)spec[0])+"："+shown);
            boolean enabled=persist;if(k.startsWith("memory_restore_"))enabled=persist&&memoryOptions.unlock&&(!k.equals("memory_restore_ratio")&&!k.equals("memory_restore_min_lux")||memoryOptions.sameScene);if(k.startsWith("memory_reset_"))enabled=connected&&memoryEnabled&&memoryOptions.resetOverride&&runtime.optBoolean("memory_reset_supported");if(k.equals("memory_timeout_minutes"))enabled=connected&&memoryEnabled&&memoryOptions.timeoutOverride&&runtime.optBoolean("memory_timeout_supported");enableParameter(bar,memoryLabels.get(k),enabled);
        }
        if(connected){int live=runtime.optInt("memory_live_count"),saved=runtime.optInt("memory_saved_points"),count=runtime.optJSONArray("current_anchors_lux")==null?0:runtime.optJSONArray("current_anchors_lux").length();boolean ref=runtime.optString("curve_backend").equals("refactor");memoryOverview.setText("基础编辑点：4 个 · 当前手动点："+live+" 个\n"+(ref?"当前曲线节点："+count+" / 7（包含基础与手动点）":"本机保留完整基础曲线，手动模型支持 1 个偏好点")+"\n已保存："+saved+" 个 · 系统同区间新调整可能替换旧点");
            memorySystemStatus.setText((runtime.optBoolean("memory_reset_supported")?"锁屏重判接口可用":"锁屏重判接口暂未兼容")+" · "+(runtime.optBoolean("memory_timeout_supported")?"短期模型计时接口可用":"短期模型计时接口暂未兼容")+"\n最近交接："+memoryEvent(runtime.optString("memory_last_system_event")));
        }memoryGraph.invalidate();
    }catch(Exception invalid){memoryOverview.setText("记忆状态待确认");}}
    String memoryEvent(String key){switch(key){case "display_policy":return tr("亮屏／熄屏交接");case "resetShortTermModel":case "environment_reset":return tr("系统重建记忆曲线");case "invalidate":return tr("短期模型标为失效");case "shouldUseGoodCurve":return tr("系统检查备用曲线");case "needResetShortTermModelPolicy":return tr("系统评估锁屏后的记忆");case "archive_restored":return tr("已恢复偏好存档");default:return tr(key);}}
    void memoryDetails(){if(runtime==null){show("等待读取手动锚点");return;}LinearLayout body=column();body.addView(text("当前节点",16,INK));memoryPointRows(body,runtime.optJSONArray("memory_live_points"));body.addView(text("存档节点",16,INK));memoryPointRows(body,runtime.optJSONArray("memory_saved_anchors"));body.addView(text(runtime.optString("memory_persist_status"),13,BLUE));JSONObject model=runtime.optJSONObject("memory_short_term_model");if(model!=null)body.addView(text("短期模型："+format(model.optDouble("lux"))+" lux · "+tr(model.optBoolean("valid")?"有效":"已失效，等待系统判断"),12,MUTED));body.addView(text("最近交接",16,INK));JSONArray events=runtime.optJSONArray("memory_lifecycle");if(events!=null)for(int i=events.length()-1;i>=0;i--){JSONObject e=events.optJSONObject(i);if(e!=null)body.addView(text(new java.text.SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date(e.optLong("time_ms")))+" · "+memoryEvent(e.optString("event"))+" · "+(e.has("manual_points_before")?e.optInt("manual_points_before")+" → ":"")+e.optInt("manual_points")+tr(" 个手动点"),12,MUTED));}ScrollView scroll=new ScrollView(this);scroll.addView(body);scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(350)));AlertDialog d=dialog("手动记忆详情",scroll);d.show();dialogButton(d,"知道了",null,true);}
    void memoryPointRows(LinearLayout body,JSONArray points){if(points==null||points.length()==0){body.addView(text("无手动节点",12,MUTED));return;}for(int i=0;i<points.length();i++){JSONObject p=points.optJSONObject(i);if(p!=null)body.addView(text(format(p.optDouble("lux"))+" lux · "+(p.has("display_nit")?curvePercent(p.optDouble("display_nit")/maximum*100)+"%":"—")+tr("（曲线坐标）"),13,INK));}}
    void makeThermalSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("温控",18,INK));LinearLayout thermalPresets=row(b);compact("系统默认",()->quickPreset("thermal",0),thermalPresets);compact("适度放宽",()->quickPreset("thermal",1),thermalPresets);thermalSwitch=new Switch(this);thermalSwitch.setText(tr("减少温控降亮"));thermalSwitch.setTextSize(14);thermalSwitch.setShowText(false);b.addView(thermalSwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        thermalSwitch.setOnCheckedChangeListener((v,on)->{if(on!=thermalRelax){thermalRelax=on;dirty=true;drawCurve();}});
        b.addView(text("默认关闭。开启后可减少显示层的温控限亮；严重过热或达到电池温度阈值时恢复系统策略。",12,MUTED));
        b=settingsDetails(b,"thermal");
        ceilingText=text("电池温度阈值：43℃（建议）",13,INK);b.addView(ceilingText);ceilingSlider=new SeekBar(this);ceilingSlider.setMax(12);ceilingSlider.setProgress(5);b.addView(ceilingSlider,new LinearLayout.LayoutParams(-1,dp(40)));
        ceilingSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){if(from){thermalCeiling=p+38;dirty=true;drawCurve();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        coolingLabel=text("",13,INK);b.addView(coolingLabel);coolingSlider=parameter(b,4,0,value->thermalCooling=1+value*.5f);
        b.addView(text("可设 38～50℃。达到阈值交回系统，冷却到设定幅度后再允许减少降亮。",12,MUTED));
    }
    void makeMainResponseSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("主光感确认",18,INK));quickPresetRow(b,"response","反应灵敏","减少波动");responseSwitch=new Switch(this);responseSwitch.setText(tr("自定义变化确认时间"));responseSwitch.setTextSize(14);b.addView(responseSwitch,new LinearLayout.LayoutParams(-1,dp(48)));responseSwitch.setOnCheckedChangeListener((v,on)->{if(on!=responseOverride){responseOverride=on;dirty=true;drawCurve();}});
        b.addView(text("默认沿用系统。光线持续越过变化阈值后再调节，等待越长越不易受短暂遮挡影响。",12,MUTED));
        b=settingsDetails(b,"response");
        brightenLabel=text("",13,INK);b.addView(brightenLabel);brightenSlider=parameter(b,19,2,value->brightenDelay=500+value*500);
        darkenLabel=text("",13,INK);b.addView(darkenLabel);darkenSlider=parameter(b,28,8,value->darkenDelay=1000+value*500);
        b.addView(text("变亮 0.5～10 秒，变暗 1～15 秒；实际等待受本机采样窗口限制。熄屏、HDR、闲置与驾驶沿用系统策略。",12,MUTED));
        smallBrightenSwitch=new Switch(this);smallBrightenSwitch.setText(tr("自定义微小变亮确认"));smallBrightenSwitch.setTextSize(14);b.addView(smallBrightenSwitch,new LinearLayout.LayoutParams(-1,dp(48)));smallBrightenSwitch.setOnCheckedChangeListener((v,on)->{if(on!=smallBrightenOverride){smallBrightenOverride=on;dirty=true;drawCurve();}});
        smallBrightenLabel=text("",13,INK);b.addView(smallBrightenLabel);smallBrightenSlider=parameter(b,29,9,value->smallBrightenDelay=500+value*500);
        b.addView(text("0.5～15 秒。仅影响系统判定的微小变亮；关闭后沿用系统时间。",12,MUTED));
    }
    void makeLowLightSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("暗光稳定",18,INK));
        quickPresetRow(b,"low","暗光少动","暗光更稳");
        lowLightSwitch=new Switch(this);lowLightSwitch.setText(tr("暗光稳定"));lowLightSwitch.setTextSize(14);b.addView(lowLightSwitch,new LinearLayout.LayoutParams(-1,dp(48)));lowLightSwitch.setOnCheckedChangeListener((v,on)->{if(on!=lowLightStability){lowLightStability=on;dirty=true;drawCurve();}});
        b.addView(text("减少暗处小波动引起的调节。默认关闭；手动、熄屏、HDR 等特殊场景沿用系统。",12,MUTED));
        b.addView(text("「暗光少动」提高亮暗门槛，并在本机支持时启用辅助闸门。选择后记得保存并应用。",12,MUTED));
        b=settingsDetails(b,"low");
        lowLimitLabel=text("",13,INK);b.addView(lowLimitLabel);lowLimitSlider=parameter(b,19,9,value->lowLightLimit=5+value*5);
        lowBrightLabel=text("",13,INK);b.addView(lowBrightLabel);lowBrightSlider=parameter(b,6,4,value->lowLightBrighten=1000+value*500);
        lowDarkLabel=text("",13,INK);b.addView(lowDarkLabel);lowDarkSlider=parameter(b,6,6,value->lowLightDarken=1000+value*500);
        b.addView(text("确认时间受采样窗口限制，保留系统更长等待。",12,MUTED));
        b.addView(text("实际生效时间见下方「参数生效状态」。",12,MUTED));
        lowThresholdSwitch=new Switch(this);lowThresholdSwitch.setText(tr("暗光小变化不调节"));lowThresholdSwitch.setTextSize(14);b.addView(lowThresholdSwitch,new LinearLayout.LayoutParams(-1,dp(48)));lowThresholdSwitch.setOnCheckedChangeListener((v,on)->{if(on!=lowThresholds.enabled){lowThresholds=new LowLightThresholds(on,lowThresholds.values);dirty=true;drawCurve();}});
        b.addView(text("比例、最小照度差和系统门槛取更严格者；持续变化才调节，不修改光感读数。",12,MUTED));
        double[] steps={.1,1,.1,1};for(int i=0;i<4;i++){final int key=i;lowThresholdLabels[i]=text("",13,INK);b.addView(lowThresholdLabels[i]);lowThresholdSliders[i]=parameter(b,(int)Math.round((LowLightThresholds.MAX[i]-LowLightThresholds.MIN[i])/steps[i]),(int)Math.round((LowLightThresholds.DEFAULT[i]-LowLightThresholds.MIN[i])/steps[i]),value->{double[] next=lowThresholds.values.clone();next[key]=LowLightThresholds.MIN[key]+value*steps[key];lowThresholds=new LowLightThresholds(lowThresholds.enabled,next);});}
        assistGateSwitch=new Switch(this);assistGateSwitch.setText(tr("辅助光感参考闸门"));assistGateSwitch.setTextSize(14);b.addView(assistGateSwitch,new LinearLayout.LayoutParams(-1,dp(48)));assistGateSwitch.setOnCheckedChangeListener((v,on)->{if(on!=assistGateOptions.enabled){assistGateOptions.enabled=on;dirty=true;drawCurve();}});
        b.addView(text("仅主光感变亮、辅助近期稳定时多等一会儿。辅助变化、遮挡、过期，或明显变亮、等待到时即放行。",12,MUTED));
        assistGateWaitLabel=text("",13,INK);b.addView(assistGateWaitLabel);assistGateWait=parameter(b,18,10,value->assistGateOptions.waitMs=1000+value*500);
        assistGateToleranceLabel=text("",13,INK);b.addView(assistGateToleranceLabel);assistGateTolerance=parameter(b,8,2,value->assistGateOptions.stableRatio=.1f+value*.05f);
        assistGateStatus=text("",12,BLUE);b.addView(assistGateStatus);
    }
    String assistGateReason(String reason){switch(reason){case "confirming_main_only":return tr("主光感单独变亮，观察中");case "assist_unreliable":return tr("辅助无可靠证据，已放行");case "assist_changed":return tr("辅助也有变化，已放行");case "large_main_rise":return tr("主光感明显变亮，已放行");case "wait_complete":case "released_cooldown":return tr("观察结束，已放行");case "no_main_rise":return tr("未触发主光感变亮");default:return tr("未启用或不适用");}}
    void makeDarkLockSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("暗光定时锁定",18,INK));
        LinearLayout presets=row(b);compact("不锁定",()->{controls.darkLock=false;dirty=true;drawCurve();},presets);compact("睡前保持",()->{controls.bedtime();dirty=true;drawCurve();},presets);
        darkLockSwitch=new Switch(this);darkLockSwitch.setText(tr("暗光持续后关闭自动亮度"));b.addView(darkLockSwitch,new LinearLayout.LayoutParams(-1,dp(48)));darkLockSwitch.setOnCheckedChangeListener((v,on)->{if(on!=controls.darkLock){controls.darkLock=on;dirty=true;drawCurve();}});
        b.addView(text("保持进入时的亮度；照度明显升高后恢复自动。锁屏或重新开启自动亮度即退出。你主动关闭的自动亮度不会被重新开启。",12,MUTED));
        b.addView(text("需要主辅光感共同确认暗处；任一侧持续变亮可退出。锁定期间低频监听光感，温控和应用指定亮度仍生效。",12,MUTED));
        controlStatus=text("等待读取暗光锁定状态",12,BLUE);b.addView(controlStatus);LinearLayout details=settingsDetails(b,"dark_lock");
        int[] maximums={29,29,99,29},initial={4,4,5,4};for(int i=0;i<4;i++){final int key=i;controlLabels[i]=text("",13,INK);details.addView(controlLabels[i]);controlSliders[i]=parameter(details,maximums[i],initial[i],n->{if(key==0){controls.enterLux=1+n;controls.exitLux=Math.max(controls.exitLux,controls.enterLux+5);}else if(key==1)controls.minutes=1+n;else if(key==2)controls.exitLux=Math.max(controls.enterLux+5,5+n*5);else controls.exitSeconds=1+n;});}
    }
    void makeManualPanelSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("主屏手动亮度",18,INK));manualPanelSwitch=new Switch(this);manualPanelSwitch.setText(tr("允许磁贴手动调节"));b.addView(manualPanelSwitch,new LinearLayout.LayoutParams(-1,dp(48)));manualPanelSwitch.setOnCheckedChangeListener((v,on)->{if(on!=controls.manualPanel){controls.manualPanel=on;dirty=true;drawCurve();}});
        b.addView(text("点击打开独立弹窗，直接写主屏背光节点；滑动关闭自动亮度，重新开启自动亮度即退让。",12,MUTED));
        b.addView(text("滑条上限读取主屏 max_brightness，可超过系统普通滑条。小型 C 守护保持目标；锁屏期间释放节点，解锁后恢复，开启自动亮度结束保持。",12,MUTED));
        b.addView(text("拖动按相对距离调节。锁屏暂停，解锁恢复；开启自动亮度结束保持。",12,MUTED));
        b.addView(text("100% 为驱动公布的节点上限，不是 nit；实际亮度仍受面板限制，高亮会增加发热与耗电。",12,MUTED));
        button("打开主屏亮度面板",this::showManualPanel,b);button("添加控制中心磁贴",this::addBrightnessTile,b);
    }
    void drawControls(){if(darkLockSwitch==null||manualPanelSwitch==null)return;boolean supported=runtime!=null&&runtime.optBoolean("dark_lock_supported");darkLockSwitch.setChecked(controls.darkLock);darkLockSwitch.setEnabled(supported||controls.darkLock);manualPanelSwitch.setChecked(controls.manualPanel);manualPanelSwitch.setEnabled(runtime!=null&&runtime.optBoolean("manual_panel_supported"));
        String[] labels={"进入照度："+format(controls.enterLux)+" lux","等待时间："+controls.minutes+" 分钟","退出照度："+format(controls.exitLux)+" lux","退出确认："+controls.exitSeconds+" 秒"};int[] p={Math.round(controls.enterLux)-1,controls.minutes-1,Math.round((controls.exitLux-5)/5),controls.exitSeconds-1};for(int i=0;i<4;i++){controlSliders[i].setProgress(p[i]);controlLabels[i].setText(labels[i]);enableParameter(controlSliders[i],controlLabels[i],supported&&controls.darkLock);}
        JSONObject c=runtime==null?null:runtime.optJSONObject("brightness_control");String reason=c==null?"":c.optString("reason");String state=reason.equals("locked")?"已保持当前亮度":reason.equals("countdown")?"暗光计时中":reason.equals("watching")?"等待持续暗光":reason.equals("waiting_sensor_data")?"等待光感读数":reason.equals("user_manual")?"用户手动模式，不自动恢复":reason.equals("manual_panel")?"主屏手动面板控制中":reason.equals("error")?"接口异常，已退出":"未锁定";controlStatus.setText(state+(c!=null&&reason.equals("countdown")?" · "+Math.round(c.optLong("countdown_left_ms")/1000.0)+" 秒":""));
    }
    void showManualPanel(){startActivity(new Intent(this,BrightnessPanelActivity.class));}
    void addBrightnessTile(){try{android.app.StatusBarManager manager=getSystemService(android.app.StatusBarManager.class);manager.requestAddTileService(new ComponentName(this,BrightnessTileService.class),tr("主屏亮度"),android.graphics.drawable.Icon.createWithResource(this,getResources().getIdentifier("brightness_tile","drawable",getPackageName())),getMainExecutor(),result->show(result==android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED||result==android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED?"磁贴已添加":"可在控制中心编辑磁贴时添加 HyperLux 主屏亮度"));}catch(Throwable unavailable){show("可在控制中心编辑磁贴时添加 HyperLux 主屏亮度");}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("manual_brightness_panel",false)){openManualPanel=true;if(runtime!=null){openManualPanel=false;showManualPanel();}else if(!busy)run("inspect",null);}}
    void makeRefreshSettings(){
        LinearLayout b=card(settingsTarget);b.addView(text("界面",18,INK));refreshLabel=text("",13,INK);b.addView(refreshLabel);refreshSlider=parameter(b,4,refreshSeconds-1,value->{refreshSeconds=value+1;getPreferences(0).edit().putInt("refresh_seconds",refreshSeconds).apply();});
        b.addView(text("只影响状态显示，不改变亮度调节频率。",12,MUTED));
    }
    void makeAdvancedSettings(int g){makeAdvancedSettings(g,null);}
    void makeAdvancedSettings(int g,LinearLayout existing){
        String[] titles={"变化阈值","辅助光感确认","过渡动画","手动模式阳光屏","触摸遮挡保护"};
        String[] explanations={
            "倍率越大越不易调节，最小照度差过滤小波动。仅在设定照度内生效，特殊场景沿用系统。",
            "调整辅助光感的确认时间；暗光稳定开启时取更长等待，最长 4 秒。",
            "倍率越大过渡越慢，亮暗分别设置。只影响正常自动亮度，不重启正在进行的过渡。",
            "调整手动模式阳光屏的进入、退出等待；不强制开启，也不改变自动 HBM 冷却。",
            "只调整手指移开后的等待。0 秒取消额外等待，仍保留遮挡保护。"};
        int[] starts={0,6,9,11,13},ends={6,9,11,13,14};
        final int group=g;LinearLayout b=existing==null?card(settingsTarget):existing;if(existing==null)b.addView(text(titles[g],18,INK));String[] first={"少些变化","快速确认","柔和过渡","快速响应","快速恢复"},second={"更灵敏","稳妥确认","快速过渡","避免误触发","遮挡少动"};quickPresetRow(b,AdvancedOptions.GROUPS[g],first[g],second[g]);
            Switch toggle=new Switch(this);toggle.setText(tr("自定义"));toggle.setTextSize(14);toggle.setShowText(false);b.addView(toggle,new LinearLayout.LayoutParams(-1,dp(48)));advancedSwitches[g]=toggle;
            b.addView(text(explanations[g],12,MUTED));LinearLayout details=existing==null?settingsDetails(b,AdvancedOptions.GROUPS[g]):b;
            for(int i=starts[g];i<ends[g];i++){final int key=i;advancedLabels[i]=text("",13,INK);details.addView(advancedLabels[i]);advancedSliders[i]=parameter(details,(int)Math.round((AdvancedOptions.MAX[i]-AdvancedOptions.MIN[i])/AdvancedOptions.STEP[i]),(int)Math.round((AdvancedOptions.DEFAULT[i]-AdvancedOptions.MIN[i])/AdvancedOptions.STEP[i]),value->advanced.values[key]=AdvancedOptions.MIN[key]+value*AdvancedOptions.STEP[key]);}
            toggle.setOnCheckedChangeListener((v,on)->{if(on!=advanced.enabled[group]){advanced.enabled[group]=on;dirty=true;drawAdvanced();}});
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
        LinearLayout b=card(pages[3]);b.setPadding(dp(22),dp(22),dp(22),dp(18));LinearLayout r=row(b);r.addView(image(null,68,20));LinearLayout title=column();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(18);r.addView(title,lp);title.addView(text("HyperLux",26,INK));title.addView(text(AppBuild.ARTIFACT_VERSION+" · "+tr(AppBuild.TEST?"曲线适配测试版":AppBuild.INTERNAL?"内部测试版":"正式版"),14,BLUE));
        TextView intro=text("适配 HyperOS 4 的自动亮度工具。\n\n沿用系统双侧感光与平滑过渡，支持可编辑曲线、手动记忆保存与暗光稳定。户外高亮、温控和变化确认可按需调整，配置支持导入导出。",15,INK);intro.setLineSpacing(dp(5),1);intro.setPadding(0,dp(20),0,dp(16));b.addView(intro);
        b.addView(text("HyperOS 4 · Root · LSPosed",12,MUTED));updateLabel=text("自动检查应用更新",12,BLUE);updateLabel.setPadding(dp(12),dp(10),dp(12),dp(10));updateLabel.setBackground(ripple(0xffeef3ff,12));updateLabel.setOnClickListener(v->checkUpdate(true));lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);lp.bottomMargin=dp(6);b.addView(updateLabel,lp);
        r=row(b);compact("官网",()->open("https://lc.rongshangs.top"),r);compact("GitHub",()->open(UpdateChecker.REPO),r);compact("开源协议",()->open("https://www.gnu.org/licenses/gpl-3.0.html"),r);
        LinearLayout group=column();group.setPadding(dp(14),dp(12),dp(14),dp(12));group.setBackground(ripple(0xffeef3ff,14));group.setClipToOutline(true);group.setClickable(true);group.setOnClickListener(v->copyGroupNumber());lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);b.addView(group,lp);
        group.addView(text("QQ 交流群",12,MUTED));r=row(group);r.setBaselineAligned(false);TextView number=text("314981836",22,BLUE);number.setPadding(0,0,0,0);r.addView(number,new LinearLayout.LayoutParams(0,-2,1));Button copy=action("复制群号",this::copyGroupNumber);copy.setTextSize(12);copy.setPadding(dp(8),0,dp(8),0);r.addView(copy,new LinearLayout.LayoutParams(dp(88),dp(34)));group.addView(text("暗号：1691",12,MUTED));
        b=card(pages[3]);b.addView(text("打赏",18,INK));b.addView(text("完全开源免费。欢迎捐赠 2.3 元支持开发。备注「昵称：想说的一句话」，总计不超过 30 个字符，将会尽快更新到感谢名单。",13,MUTED));r=row(b);compact("微信",()->donate("微信","donate-wechat.jpg"),r);compact("支付宝",()->donate("支付宝","donate-alipay.jpg"),r);
        b=card(pages[3]);b.addView(text("感谢名单",18,INK));thanksList=column();b.addView(thanksList);thanksStatus=text("名单来自官网，联网时自动更新",11,MUTED);b.addView(thanksStatus);
        b=card(pages[3]);b.addView(text("测试贡献",18,INK));b.addView(text("勿忘 灭",14,INK));
        b=card(pages[3]);b.addView(text("代码贡献",18,INK));b.addView(text("凌乱的风W",14,INK));b.addView(text("贡献手动接管恢复、系统进程重启与断连兜底、节点校验顺序修复，以及图片解码优化。",12,MUTED));
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
        if(updateBusy||updateDialog!=null)return;updateBusy=true;updateChecked=true;updateLabel.setText("正在检查更新…");
        network.execute(()->{try{JSONObject found=UpdateChecker.check();ui.post(()->{if(destroyed)return;updateBusy=false;if(found.optBoolean("newer")){String version=found.optString("version");if(!ReleasePolicy.shouldOffer(version,getPreferences(0).getString("ignored_update_version",""),manual)){pendingUpdate=null;updateLabel.setText(tr("已忽略版本 ")+version);return;}try{found.put("manual_check",manual);}catch(JSONException invalid){show(invalid.getMessage());return;}pendingUpdate=found;updateLabel.setText("发现新版本 "+version);offerUpdate();}else{pendingUpdate=null;updateLabel.setText("当前没有新应用版本");if(manual)show("当前没有新应用版本");}});}catch(Exception failure){ui.post(()->{if(destroyed)return;updateBusy=false;updateLabel.setText("暂时无法检查更新");if(manual)show("暂时无法检查更新");});}});
    }
    void offerUpdate(){
        if(pendingUpdate==null||!visible||permissionDialogVisible||legacyDialogVisible||pendingLegacy!=null||updateDialog!=null)return;
        JSONObject release=pendingUpdate;pendingUpdate=null;String version=release.optString("version");
        if(!ReleasePolicy.shouldOffer(version,getPreferences(0).getString("ignored_update_version",""),release.optBoolean("manual_check")))return;
        LinearLayout body=column();body.addView(text("版本 "+version,18,BLUE));String notes=release.optString("notes");TextView changes=text(notes.isEmpty()?"查看仓库了解更新内容":notes,13,MUTED);ScrollView scroll=new ScrollView(this);scroll.addView(changes);scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.min(dp(200),dp(70+Math.min(8,notes.length()/40)*16))));body.addView(scroll);
        AlertDialog d=dialog("有新版本可用",body);updateDialog=d;d.setOnDismissListener(v->{updateDialog=null;offerLegacyModules();});
        button("官网下载",()->{d.dismiss();open(UpdateChecker.WEBSITE);},body);button("GitHub 下载",()->{d.dismiss();open(release.optString("url"));},body);
        d.show();dialogButton(d,"稍后",null,false);dialogButton(d,"忽略此版本",()->{getPreferences(0).edit().putString("ignored_update_version",version).apply();updateLabel.setText(tr("已忽略版本 ")+version);},false);
    }
    void select(int value){rememberSettingsHome();int previous=page;page=Math.max(0,Math.min(3,value));settingsGroup=-1;if(bottomNav!=null)bottomNav.setVisibility(View.VISIBLE);header.setVisibility(page==3?View.GONE:View.VISIBLE);note.setVisibility(View.GONE);transitionPage(content.getChildAt(page),Integer.compare(page,previous));if(page==1)restoreSettingsHome();
        for(int i=0;i<4;i++){if(nav[i]!=null){navNames[i].setTextColor(i==page?BLUE:MUTED);navIcons[i].selected=i==page;navIcons[i].invalidate();nav[i].setSelected(i==page);}}
        // Refresh every visit; retain unapplied edits until a successful save.
        if(page==3&&previous!=3&&visible){checkUpdate(false);refreshThanks();}if(page==2)scrollLogs();if(visible){if(busy)rereadPending=true;else run("inspect",null);}
    }
    void show(String message){if(!destroyed){note.setText(message);note.setVisibility(message==null||message.isEmpty()?View.GONE:View.VISIBLE);if(message!=null&&!message.isEmpty()&&page!=2)Toast.makeText(this,tr(message),Toast.LENGTH_SHORT).show();}}
    void permissionPrompt(String key,String title,String message){
        permissionBlocked=true;if(!visible||destroyed||permissionDialogVisible||permissionNotice.equals(key))return;
        permissionNotice=key;permissionDialogVisible=true;LinearLayout body=column();TextView icon=text(key.equals("root")?"ROOT":key.equals("lsp")?"LSPosed":key.equals("os")?"HyperOS 4":"接口检查",14,BLUE);icon.setPadding(dp(12),dp(8),dp(12),dp(8));icon.setBackground(background(0xffedf3ff,12));body.addView(icon);body.addView(text(message,14,MUTED));
        AlertDialog d=dialog(title,body);d.setOnDismissListener(v->{permissionDialogVisible=false;ui.post(()->{offerLegacyModules();offerUpdate();});});d.show();dialogButton(d,"稍后",null,false);dialogButton(d,"重新检测",()->{permissionNotice="";permissionBlocked=false;ui.postDelayed(()->run("inspect",null),150);},true);
    }
    void preset(float[] next){
        if(factoryLux==null){show("连接后读取曲线");return;}
        try{float[] adjusted=CurveEditor.preset(next,factoryNit,minimum,maximum);
            factors=adjusted;curveFloor=0;dirty=true;drawCurve();show(Arrays.equals(next,adjusted)?"曲线已选中，点击保存并应用后生效":"预设已按本机可调范围调整，保存并应用后生效");
        }catch(Exception error){show(error.getMessage());}
    }
    void drawCurve(){
        drawAdvanced();drawOutdoor();drawControls();if(sceneSettings!=null)sceneSettings.refresh();

        drawMemory();
        thermalSwitch.setChecked(thermalRelax);ceilingSlider.setProgress(Math.round(thermalCeiling)-38);ceilingText.setText("电池温度阈值："+Math.round(thermalCeiling)+"℃"+(Math.round(thermalCeiling)==43?"（建议）":""));stateGraph.invalidate();settingsGraph.invalidate();
        memorySwitch.setChecked(memoryEnabled);memorySlider.setProgress(Math.round(memoryStrength*100)-1);memoryText.setText("记忆强度 "+Math.round(memoryStrength*100)+"%"+(memoryStrength==1?" · 完整记忆":" · 缓慢记忆"));pipeline.invalidate();
        memoryWindowSlider.setProgress((int)(memoryWindow-500)/500);memoryWindowLabel.setText("连续调节间隔："+format(memoryWindow/1000d)+" 秒");memoryRangeSlider.setProgress(Math.round((memoryLuxRange-.1f)/.05f));memoryRangeLabel.setText("拖动分组照度容差：±"+Math.round(memoryLuxRange*100)+"%");
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
        if(lowThresholdSwitch!=null){lowThresholdSwitch.setChecked(lowThresholds.enabled);boolean supported=runtime!=null&&runtime.optBoolean("low_light_threshold_supported");lowThresholdSwitch.setEnabled(low&&supported||lowThresholds.enabled&&!supported);String[] names={"暗光最小变亮比例","暗光最小变亮照度差","暗光最小变暗比例","暗光最小变暗照度差"};double[] steps={.1,1,.1,1};for(int i=0;i<4;i++){lowThresholdSliders[i].setProgress((int)Math.round((lowThresholds.values[i]-LowLightThresholds.MIN[i])/steps[i]));lowThresholdLabels[i].setText(names[i]+"："+format(lowThresholds.values[i]*(i%2==0?100:1))+(i%2==0?"%":" lux"));enableParameter(lowThresholdSliders[i],lowThresholdLabels[i],low&&supported&&lowThresholds.enabled);}}
        if(assistGateSwitch!=null){boolean supported=runtime!=null&&runtime.optBoolean("low_light_assist_gate_supported");assistGateSwitch.setChecked(assistGateOptions.enabled);assistGateSwitch.setEnabled(low&&supported||assistGateOptions.enabled&&!supported);assistGateWait.setProgress((int)(assistGateOptions.waitMs-1000)/500);assistGateWaitLabel.setText("单侧变亮最长观察："+format(assistGateOptions.waitMs/1000d)+" 秒");assistGateTolerance.setProgress(Math.round((assistGateOptions.stableRatio-.1f)/.05f));assistGateToleranceLabel.setText("辅助稳定容差：±"+Math.round(assistGateOptions.stableRatio*100)+"%（至少 2 lux）");enableParameter(assistGateWait,assistGateWaitLabel,low&&supported&&assistGateOptions.enabled);enableParameter(assistGateTolerance,assistGateToleranceLabel,low&&supported&&assistGateOptions.enabled);assistGateStatus.setText(runtime==null?"等待读取辅助闸门状态":"辅助闸门："+assistGateReason(runtime.optString("low_light_assist_gate_reason"))+" · 观察 "+runtime.optLong("low_light_assist_gate_holds")+" 次");}
        drawDraftState();
        try{if(factoryLux!=null)new CurvePlan(factoryLux,factoryNit,minimum,maximum,factors,curveFloor);curveHint.setText(factoryLux==null?"连接后显示节点范围":"拖动或点击节点 · 高照度端固定"+(dirty?" · 尚未保存":""));}catch(Exception error){curveHint.setText(error.getMessage());}

    }
    void enableParameter(SeekBar slider,TextView label,boolean enabled){slider.setEnabled(enabled);label.setTextColor(enabled?INK:themed(0xffa5aab3));}
    void drawDraftState(){if(unsavedHint!=null)unsavedHint.setVisibility(dirty?View.VISIBLE:View.GONE);for(int i=0;i<settingApply.length;i++){if(settingApply[i]!=null)settingApply[i].setEnabled(!busy&&runtime!=null&&factoryLux!=null);if(settingHints[i]!=null)settingHints[i].setVisibility(dirty?View.VISIBLE:View.GONE);}}
    JSONObject configuration()throws Exception{return configuration(true);}
    JSONObject configuration(boolean validateScenes)throws Exception{
            if(factoryLux==null)throw new IllegalStateException("连接后读取曲线");new CurvePlan(factoryLux,factoryNit,minimum,maximum,factors,curveFloor);ThermalPolicy.validate(thermalCeiling);
            JSONObject options=new JSONObject().put("factors",CurvePlan.encode(factors)).put("thermal_relax",thermalRelax).put("thermal_ceiling",thermalCeiling).put("memory_strength",memoryEnabled?memoryStrength:0).put("memory_window",memoryWindow).put("memory_lux_range",memoryLuxRange).put("thermal_cooling",thermalCooling).put("response_override",responseOverride).put("brighten_delay",brightenDelay).put("darken_delay",darkenDelay).put("small_brighten_override",smallBrightenOverride).put("small_brighten_delay",smallBrightenDelay);
            if(validateScenes&&sceneSettings!=null)sceneSettings.commit();sceneOptions.put(options);advanced.put(options);outdoorOptions.put(options);controls.put(options);BrightnessControlOptions.parse(options);OutdoorOptions.parse(options);
            options.put("low_light_stability",lowLightStability).put("low_light_limit",lowLightLimit).put("low_light_brighten",lowLightBrighten).put("low_light_darken",lowLightDarken);
            lowThresholds.put(options);
            assistGateOptions.put(options);
            memoryOptions.put(options);options.put("curve_floor_nit",curveFloor);return options;
    }
    void exportConfiguration(){if(busy){show("另一项操作还在执行");return;}try{JSONObject file=ConfigurationFile.export(configuration(),runtime,dirty,refreshSeconds,autoScroll);run("export-config",Base64.getEncoder().encodeToString(file.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception error){show(error.getMessage());}}
    void importConfiguration(){if(factoryLux==null){show("连接后读取曲线");return;}if(busy){show("另一项操作还在执行");return;}try{Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType("*/*");picker.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});startActivityForResult(picker,501);}catch(ActivityNotFoundException unavailable){show("未找到文件选择器");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=501||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();worker.execute(()->{try(InputStream input=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(input==null)throw new IOException("无法读取配置文件");byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>ConfigurationFile.LIMIT)throw new IOException("配置文件过大");out.write(buffer,0,count);}String content=new String(out.toByteArray(),StandardCharsets.UTF_8);ui.post(()->previewConfiguration(content));}catch(Exception error){ui.post(()->show(error.getMessage()));}});}
    void previewConfiguration(String text){if(destroyed)return;try{if(runtime==null)throw new IllegalStateException("连接后读取曲线");ConfigurationFile.Imported imported=ConfigurationFile.read(text,runtime,configuration());String message="导入后先检查设置，再点击保存并应用。旧版本缺少的新选项使用默认值。";if(dirty)message+="\n\n导入会替换当前尚未保存的设置。";if(!imported.notes.isEmpty())message+="\n\n"+String.join("\n",imported.notes);ScrollView scroll=new ScrollView(this);scroll.addView(MainActivity.this.text(message,14,MUTED));scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(240)));AlertDialog d=dialog("导入配置",scroll);d.show();dialogButton(d,"取消",null,false);dialogButton(d,"导入",()->{try{ConfigurationFile.Imported checked=ConfigurationFile.read(text,runtime,configuration());loadOptions(checked.options);if(checked.ui.has("refresh_seconds"))refreshSeconds=checked.ui.getInt("refresh_seconds");if(checked.ui.has("log_auto_scroll"))autoScroll=checked.ui.getBoolean("log_auto_scroll");getPreferences(0).edit().putInt("refresh_seconds",refreshSeconds).putBoolean("log_auto_scroll",autoScroll).apply();dirty=true;logFollow.setChecked(autoScroll);drawCurve();show("配置已导入，检查后点击保存并应用");}catch(Exception error){show(error.getMessage());}},true);}catch(Exception error){show(error.getMessage());}}
    void loadOptions(JSONObject config)throws Exception{SceneOptions nextScenes=SceneOptions.parse(config);controls=BrightnessControlOptions.parseStored(config);LowLightAssistGate nextAssistGate=new LowLightAssistGate();nextAssistGate.configure(config);LowLightThresholds nextLowThresholds=LowLightThresholds.parse(config);MemoryOptions nextMemory=MemoryOptions.parse(config);float[] next=CurvePlan.factors(config.getString("factors"));float floor=CurvePlan.floor(config.has("curve_floor_nit")?config.opt("curve_floor_nit"):null);new CurvePlan(factoryLux,factoryNit,minimum,maximum,next,floor);AdvancedOptions a=AdvancedOptions.parse(config);OutdoorOptions o=OutdoorOptions.parse(config);sceneOptions=nextScenes;memoryOptions=nextMemory;lowThresholds=nextLowThresholds;assistGateOptions=nextAssistGate;factors=next;curveFloor=floor;advanced=a;outdoorOptions=o;thermalRelax=config.optBoolean("thermal_relax");thermalCeiling=(float)config.optDouble("thermal_ceiling",43);float memory=(float)config.optDouble("memory_strength",1);memoryEnabled=memory>0;if(memoryEnabled)memoryStrength=memory;memoryWindow=config.optLong("memory_window",1500);memoryLuxRange=(float)config.optDouble("memory_lux_range",.3);thermalCooling=(float)config.optDouble("thermal_cooling",1);responseOverride=config.optBoolean("response_override");brightenDelay=config.optLong("brighten_delay",1500);darkenDelay=config.optLong("darken_delay",5000);smallBrightenOverride=config.optBoolean("small_brighten_override");smallBrightenDelay=config.optLong("small_brighten_delay",5000);lowLightStability=config.optBoolean("low_light_stability");lowLightLimit=(float)config.optDouble("low_light_limit",50);lowLightBrighten=config.optLong("low_light_brighten",3000);lowLightDarken=config.optLong("low_light_darken",4000);}
    void submit(){
        if(factoryLux==null)return;
        try{JSONObject options=configuration();
            run("apply",Base64.getEncoder().encodeToString(options.toString().getBytes(StandardCharsets.UTF_8)));
        }catch(Exception error){if(sceneSettings!=null)sceneSettings.revealError();show(error.getMessage());}
    }
    JSONObject presets(){try{return new JSONObject(getPreferences(0).getString("presets","{}"));}catch(Exception error){return new JSONObject();}}
    EditText input(String hint){EditText v=new EditText(this);v.setSingleLine(true);v.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);v.setTextSize(16);v.setHint(tr(hint));v.setTextColor(INK);v.setHintTextColor(MUTED);v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setBackground(background(0xffedf1f7,12));return v;}
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
    void branchRow(LinearLayout parent,String title,String state,String explanation){LinearLayout b=card(parent);b.setBackground(background(0xffeef3ff,14));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.addView(text(title,12,MUTED));b.addView(text(state,14,INK));if(!explanation.isEmpty())b.addView(text(explanation,12,MUTED));}
    void scenesDialog(){
        long elapsed=SystemClock.elapsedRealtime();final JSONObject snapshot=runtime;LinearLayout body=column();body.addView(text(SceneCatalog.summary(snapshot,elapsed),14,INK));body.addView(text("优先显示生效场景；详细条件在设置中查看。",12,MUTED));
        java.util.List<SceneCatalog.Row> rows=SceneCatalog.read(snapshot,elapsed);View firstActive=null;
        for(boolean active:new boolean[]{true,false}){boolean heading=false;for(SceneCatalog.Row r:rows){if(SceneCatalog.active(r.id,snapshot,elapsed)!=active)continue;if(!heading){body.addView(text(active?"正在影响亮度":"其他场景与策略",14,MUTED));heading=true;}LinearLayout b=card(body);b.setPadding(dp(12),dp(8),dp(12),dp(8));((LinearLayout.LayoutParams)b.getLayoutParams()).bottomMargin=dp(8);LinearLayout line=row(b);TextView title=text(r.title,14,INK);title.setIncludeFontPadding(false);line.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView state=text(SceneCatalog.briefState(r,snapshot,elapsed),11,active?INK:MUTED);state.setIncludeFontPadding(false);state.setGravity(Gravity.END);line.addView(state);b.addView(text(SceneCatalog.brief(r),12,MUTED));if(active&&firstActive==null)firstActive=b;}}
        ScrollView scroll=new ScrollView(this);scroll.addView(body);scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(Math.max(140,Math.min(430,getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density-210)))));AlertDialog d=dialog("光感输入与场景",scroll);d.show();dialogButton(d,"关闭",null,false);dialogButton(d,"场景设置",()->{select(1);showSettingsGroup(5);},true);
        positionScene(d,firstActive);
    }
    void positionScene(AlertDialog d,View target){if(target==null||d.getWindow()==null)return;View found=d.getWindow().getDecorView().findViewWithTag("dialog-scroll");if(!(found instanceof ScrollView))return;ScrollView viewport=(ScrollView)found;
        viewport.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(viewport.getViewTreeObserver().isAlive())viewport.getViewTreeObserver().removeOnPreDrawListener(this);if(d.isShowing()&&target.isShown())viewport.scrollTo(0,Math.max(0,target.getTop()-dp(28)));return true;}});viewport.invalidate();
    }
    void branches(){
        LinearLayout body=column();
        for(BranchReport.Section section:BranchReport.read(runtime,SystemClock.uptimeMillis(),SystemClock.elapsedRealtime())){
            TextView heading=text(section.title,16,INK);heading.setPadding(0,dp(12),0,dp(8));body.addView(heading);
            for(BranchReport.Row item:section.rows)branchRow(body,item.title,item.value,item.detail);
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(body);scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.max(dp(120),Math.min(dp(430),getResources().getDisplayMetrics().heightPixels-dp(240)))));
        AlertDialog d=dialog("亮度分支",scroll);d.show();dialogButton(d,"关闭",null,true);
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
                factors[point]=CurveEditor.clamp(point,value,factoryNit,minimum,maximum,factors,curveFloor);dirty=true;drawCurve();d.dismiss();}catch(Exception ex){error.setText("请输入有效数字");}},actions);confirm.setTextColor(darkTheme?BG:Color.WHITE);confirm.setBackground(ripple(BLUE,12));
        }catch(Exception error){show(error.getMessage());}
    }
    void editFloor(){try{float[] bounds=CurveEditor.floorBounds(factoryNit,minimum,maximum,factors);float current=new CurvePlan(factoryLux,factoryNit,minimum,maximum,factors,curveFloor).nits()[0];LinearLayout body=column();body.addView(text("暗处亮度下限",16,INK));body.addView(text("按本机曲线范围输入百分比，不是控制中心滑块的百分比。仅调整基础曲线，手动选择和温控仍优先。",12,MUTED));body.addView(text("可调范围："+curvePercent(bounds[0]/maximum*100)+"% ～ "+curvePercent(bounds[1]/maximum*100)+"%",13,BLUE));EditText number=input("输入百分比");number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);number.setText(String.format(Locale.ROOT,"%.4f",current/maximum*100));body.addView(number);TextView error=text("",12,0xffad5426);body.addView(error);AlertDialog d=dialog("编辑最低照度节点",body);d.show();dialogButton(d,"取消",null,false);LinearLayout actions=(LinearLayout)d.getWindow().getDecorView().findViewWithTag("actions");Button confirm=compact("确定",()->{try{float value=Float.parseFloat(number.getText().toString())/100*maximum;if(!Float.isFinite(value)||value<bounds[0]-.0001f||value>bounds[1]+.0001f){error.setText("请输入范围内的数值");return;}curveFloor=Math.max(bounds[0],Math.min(bounds[1],value));dirty=true;drawCurve();d.dismiss();}catch(Exception failure){error.setText("请输入有效数字");}},actions);confirm.setTextColor(darkTheme?BG:Color.WHITE);confirm.setBackground(ripple(BLUE,12));}catch(Exception error){show(error.getMessage());}}
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
    boolean draftStillMatches(String payload){
        try{return payload!=null&&ConfigurationFile.sameOptions(new JSONObject(new String(Base64.getDecoder().decode(payload),StandardCharsets.UTF_8)),configuration());}
        catch(Exception invalidDraft){return false;}
    }
    void run(String command,String payload){
        if(busy||destroyed)return;busy=true;apply.setEnabled(false);stop.setEnabled(false);drawDraftState();
        if(!command.equals("inspect"))show(command.equals("apply")?"正在应用曲线和温控选项…":command.equals("stop")?"正在恢复官方策略…":command.equals("reset-memory")?"正在清除手动记忆…":command.equals("export-config")?"正在导出配置…":"正在导出分析包…");
        worker.execute(()->{
            try{
                if(command.equals("inspect")){SystemVersion.Result platform=SystemVersion.read();if(platform.unsupported()){
                    JSONObject unsupported=new JSONObject().put("ok",true).put("connected",false);platform.put(unsupported);
                    ui.post(()->{if(destroyed)return;busy=false;render(unsupported);drawDraftState();});return;
                }}
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
                ui.post(()->{if(destroyed)return;busy=false;stop.setEnabled(true);if(command.equals("apply")){dirty=!draftStillMatches(payload);}
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
            if(answer.optString("hook_issue").equals("system_version_unknown")){
                runtime=null;factoryLux=factoryNit=null;badge.setText("版本未识别");stateTitle.setText("未能识别系统版本");compatibility.setText(tr("未能识别 HyperOS 版本；请核对系统设置中的版本号。"));apply.setEnabled(false);stop.setEnabled(false);thermalSwitch.setEnabled(false);memorySwitch.setEnabled(false);stateGraph.invalidate();pipeline.invalidate();permissionPrompt("os","未能识别系统版本","未能识别 HyperOS 版本；请核对系统设置中的版本号。");return;
            }
            if(answer.optString("system_compatibility").equals("unsupported")||answer.optString("hook_issue").equals("unsupported_system")){
                runtime=null;factoryLux=factoryNit=null;badge.setText("系统不支持");stateTitle.setText("当前仅支持 HyperOS 4");
                String message=tr("检测到系统版本：")+answer.optString("system_version",tr("未能识别"))+"\n\n"+tr("HyperLux 当前仅支持 HyperOS 4。HyperOS 3 等版本无法使用，授权 Root、启用 LSPosed 或重启不能解决此兼容问题。");
                compatibility.setText(message);apply.setEnabled(false);stop.setEnabled(false);thermalSwitch.setEnabled(false);memorySwitch.setEnabled(false);drawAdvanced();drawOutdoor();drawControls();stateGraph.invalidate();pipeline.invalidate();permissionPrompt("os","系统版本不支持",message);return;
            }
            if(!answer.optBoolean("connected")){runtime=null;drawAdvanced();drawOutdoor();drawControls();factoryLux=factoryNit=null;lowLightSwitch.setEnabled(false);badge.setText("未连接");stateTitle.setText("尚未连接系统曲线");luxReading.setText("— nit");nitReading.setText("— nit");compatibility.setText("✓ Root 授权已通过\n○ 系统框架连接未建立\n请核对 LSPosed 作用域和是否重启");apply.setEnabled(false);thermalSwitch.setEnabled(false);memorySwitch.setEnabled(false);stateGraph.invalidate();pipeline.invalidate();
                if(answer.optString("system_compatibility").equals("unknown"))compatibility.append("\n"+tr("未能识别 HyperOS 版本；请核对系统设置中的版本号。"));
                if(answer.optBoolean("lsp_loaded")){badge.setText("已加载");compatibility.setText("✓ Root 授权已通过\n✓ LSPosed 已加载\n○ 设备曲线尚未连接");if(!answer.optString("hook_issue").equals("curve_incompatible")){permissionBlocked=false;permissionNotice="";show(answer.optString("hook_message"));return;}permissionPrompt("abi","设备曲线尚未连接",answer.optString("hook_message","等待读取设备本地曲线；如持续未连接，请导出分析包。"));return;}
                permissionPrompt("lsp","LSPosed 尚未接入",answer.optString("hook_issue").equals("restart_required")?"Root 已获授权，但系统仍在运行旧版 Hook。请重启手机以加载新版本。":"Root 已授权。请在 LSPosed 启用 HyperLux，勾选系统框架，然后重启手机。\n从旧测试版迁移时，请先关闭旧版的 LSPosed 开关。");return;}
            runtime=answer.getJSONObject("runtime");if(!runtime.has("factory_lux")){factoryLux=factoryNit=null;badge.setText("不兼容");compatibility.setText(runtime.optString("message"));show("");apply.setEnabled(false);thermalSwitch.setEnabled(false);memorySwitch.setEnabled(false);permissionPrompt("abi","系统接口尚未兼容",runtime.optString("message"));return;}
            factoryLux=RootControl.numbers(runtime.getJSONArray("factory_lux"));factoryNit=RootControl.numbers(runtime.getJSONArray("factory_logical_nit"));minimum=(float)runtime.getDouble("min_logical_nit");maximum=(float)runtime.getDouble("max_logical_nit");
            String baseline=runtime.optString("baseline_id");
            if(!baseline.equals(loadedBaseline)){loadedBaseline=baseline;dirty=false;curveFloor=0;factors=new float[]{1,1,1,1};if(baseline.equals(getPreferences(0).getString("draft_baseline","")))try{factors=CurvePlan.factors(getPreferences(0).getString("draft","1,1,1,1"));curveFloor=getPreferences(0).getFloat("draft_floor",0);}catch(Exception ignored){}}
            JSONObject config=answer.optJSONObject("config");
            if(config!=null&&(!Build.FINGERPRINT.equals(config.optString("fingerprint"))||!runtime.optString("curve_backend").equals(config.optString("curve_backend",runtime.optString("curve_backend").equals("refactor")?"refactor":""))||(config.has("baseline_id")&&!baseline.equals(config.optString("baseline_id")))))config=null;
            if(restoredUi!=null){Bundle previousUi=restoredUi;restoredUi=null;if(previousUi.getBoolean("dirty")&&baseline.equals(previousUi.getString("baseline"))){try{loadOptions(new JSONObject(previousUi.getString("draft_options")));dirty=true;}catch(Exception invalid){show("未保存的设置无法恢复，请重新检查");}}int group=previousUi.getInt("settings_group",-1);if(page==1&&group>=0&&group<settingGroups.length)showSettingsGroup(group);}
            if(!dirty&&config!=null&&config.optBoolean("enabled"))loadOptions(config);
            if(!dirty)outdoorOptions=config!=null&&config.optBoolean("enabled")?OutdoorOptions.parse(config):new OutdoorOptions();
            if(!dirty)controls=config!=null&&config.optBoolean("enabled")?BrightnessControlOptions.parseStored(config):new BrightnessControlOptions();
            if(!dirty){advanced=config!=null&&config.optBoolean("enabled")?AdvancedOptions.parse(config):new AdvancedOptions();sceneOptions=config!=null&&config.optBoolean("enabled")?SceneOptions.parse(config):new SceneOptions();}
            if(!dirty){assistGateOptions.configure(config==null?new JSONObject():config);lowThresholds=LowLightThresholds.parse(config==null?new JSONObject():config);lowLightStability=config!=null&&config.optBoolean("enabled")&&config.optBoolean("low_light_stability");lowLightLimit=config==null?50:(float)config.optDouble("low_light_limit",50);lowLightBrighten=config==null?3000:config.optLong("low_light_brighten",3000);lowLightDarken=config==null?4000:config.optLong("low_light_darken",4000);}

            String phase=runtime.optString("phase");boolean active=phase.equals("active"),auto=runtime.optBoolean("auto_mode");badge.setText(runtime.optJSONObject("brightness_control")!=null&&"dark".equals(runtime.getJSONObject("brightness_control").optString("owner"))?"暗光锁定":!auto?"手动亮度":active?"运行中":phase.equals("error")?"接入异常":"官方基准");
            stateTitle.setText(active?(auto?"自动亮度已接入":"系统处于手动亮度"):phase.equals("error")?"已交回系统控制":"使用系统基础曲线");
            luxReading.setText(runtime.has("calculated_physical_nit")?format(runtime.optDouble("calculated_physical_nit"))+" nit":"等待计算");nitReading.setText(runtime.has("actual_nit")?format(runtime.optDouble("actual_nit"))+" nit":"等待输出");
            flow.setText(runtime.optBoolean("auto_mode")?"照度定位曲线，换算亮度后交由系统平滑调节。":"当前为手动亮度，曲线将在开启自动亮度后参与调节。");
            if(runtime.optJSONObject("brightness_control")!=null&&"dark".equals(runtime.optJSONObject("brightness_control").optString("owner"))){stateTitle.setText("暗光锁定，保持当前亮度");flow.setText("自动亮度暂停；主辅光感继续低频监听，持续变亮后恢复自动。");}
            String thermal=!runtime.optBoolean("thermal_supported")?"该固件接口未兼容":!runtime.optBoolean("thermal_relax")?"系统温控生效":runtime.optBoolean("thermal_permitted")?"减少温控降亮已启用":!runtime.has("battery_temperature")||runtime.optInt("thermal_severity",-1)<0?"等待温度读数，系统温控生效":"温度较高，系统温控生效";
            thermalState.setText("温控："+thermal+"\n电池 "+(runtime.has("battery_temperature")?format(runtime.optDouble("battery_temperature"))+"℃":"无读数")+"；系统热状态 "+severity(runtime.optInt("thermal_severity",-1)));
            double anchor=runtime.optDouble("user_anchor_lux",-1);userState.setText("手动偏好："+(anchor>=0?"系统锚点位于 "+format(anchor)+" lux":"当前没有手动锚点"));
            JSONArray flags=runtime.optJSONArray("manual_anchor_flags");int remembered=0;if(flags!=null)for(int i=0;i<flags.length();i++)if(flags.optBoolean(i))remembered++;
            memoryStatus.setText("当前手动节点："+(runtime.optString("curve_backend").equals("physical_mapping")?(anchor>=0?1:0):remembered)+" 个 · 已保存 "+runtime.optInt("memory_saved_points")+" 个\n"+runtime.optString("memory_persist_status","尚无已保存的手动记忆"));
            compatibility.setText("✓ Root 与系统框架已连接\n"+(runtime.optString("curve_backend").equals("physical_mapping")?"✓ 传统系统曲线 · 设备本地基准":"✓ Refactor 曲线 · 设备本地基准")+"\n"+(phase.equals("error")?"○ 接入异常，请查看详细读数":active?"✓ 自定义曲线已应用":"○ 当前使用系统基础曲线")+"\n"+(runtime.optBoolean("thermal_supported")?"✓ 温控接口兼容":"○ 温控接口暂未兼容"));
            JSONArray logs=runtime.optJSONArray("logs");StringBuilder lines=new StringBuilder();if(logs!=null)for(int i=0;i<logs.length();i++)lines.append(logs.getString(i)).append('\n');String next=lines.length()==0?"尚无运行记录":lines.toString();
            if(!tr(next).contentEquals(logText.getText())){int position=logScroller.getScrollY();logText.setText(next);if(autoScroll)scrollLogs();else logScroller.post(()->logScroller.scrollTo(0,position));}
            // After an OTA, an unsupported option that was enabled must still be switchable off.
            smallBrightenSwitch.setEnabled(runtime.optBoolean("small_response_supported")||smallBrightenOverride);smallBrightenSlider.setEnabled(runtime.optBoolean("small_response_supported"));
            lowLightSwitch.setEnabled(runtime.optBoolean("low_light_supported")||lowLightStability);for(SeekBar slider:new SeekBar[]{lowLimitSlider,lowBrightSlider,lowDarkSlider})slider.setEnabled(runtime.optBoolean("low_light_supported")&&lowLightStability);
            if(answer.optLong("state_age_ms")>15000)compatibility.append("\n"+tr("系统状态未及时刷新"));
            thermalSwitch.setEnabled(runtime.optBoolean("thermal_supported")||thermalRelax);responseSwitch.setEnabled(runtime.optBoolean("response_supported")||responseOverride);brightenSlider.setEnabled(runtime.optBoolean("response_supported"));darkenSlider.setEnabled(runtime.optBoolean("response_supported"));memorySwitch.setEnabled(true);apply.setEnabled(true);permissionNotice="";permissionBlocked=false;drawCurve();
            if(!firmwarePrompted&&config!=null&&config.optBoolean("enabled")&&!config.optString("fingerprint").equals(Build.FINGERPRINT)){firmwarePrompted=true;AlertDialog d=dialog("系统已更新",text("当前已恢复系统曲线。请核对兼容状态，再重新保存并应用。",14,MUTED));d.show();dialogButton(d,"知道了",null,true);}if(!busy&&!dirty)show("");
            if(manualPanel!=null)manualPanel.update(runtime.optJSONObject("brightness_control"));if(openManualPanel){openManualPanel=false;ui.post(this::showManualPanel);}
        }catch(Exception error){runtime=null;factoryLux=factoryNit=null;apply.setEnabled(false);stateGraph.invalidate();pipeline.invalidate();drawDraftState();show("状态格式不兼容："+error);}
    }
    static String severity(int level){String[] names={"正常","轻微","中等","严重","危急","紧急","关机"};return level>=0&&level<names.length?names[level]:"未知";}
    @Override public void onResume(){super.onResume();visible=true;permissionNotice="";permissionBlocked=false;run("inspect",null);ui.removeCallbacks(tick);ui.postDelayed(tick,refreshSeconds*1000);checkUpdate(false);if(page==3)refreshThanks();offerLegacyModules();offerUpdate();}
    @Override public void onPause(){visible=false;if(manualPanel!=null)manualPanel.close();finishPageTransition();ui.removeCallbacks(tick);getPreferences(0).edit().putString("draft",CurvePlan.encode(factors)).putString("draft_baseline",loadedBaseline).putFloat("draft_floor",curveFloor).apply();if(!busy)worker.execute(this::closeBridge);super.onPause();}
    @Override public void onSaveInstanceState(Bundle saved){rememberSettingsHome();saved.putInt("settings_home_y",settingsHomeY);saved.putInt("page",page);saved.putInt("settings_group",settingsGroup);saved.putBoolean("dirty",dirty);saved.putString("baseline",loadedBaseline);if(dirty&&factoryLux!=null)try{saved.putString("draft_options",configuration(false).toString());}catch(Exception ignored){}super.onSaveInstanceState(saved);}
    @Override public void onDestroy(){destroyed=true;for(AlertDialog d:new ArrayList<>(openDialogs))d.dismiss();if(manualPanel!=null)manualPanel.close();if(!busy)closeBridge();worker.shutdown();network.shutdownNow();super.onDestroy();}
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
        final LinearLayout protectionPanel=column();final TextView[] protectionStates=new TextView[3],protectionDetails=new TextView[3];
        final TextView curveGuide=new TextView(MainActivity.this);final LinearLayout curveInfo=column(),fusedPanel=column();
        final TextView fusedTitle=text("光感输入与场景",12,MUTED),fusedValue=text("等待读取",21,INK),fusedScene=text("场景：等待读取",10,BLUE);
        final LinearLayout[] readingPanels=new LinearLayout[3];final TextView[] readingTitles=new TextView[3],readingValues=new TextView[3];
        final boolean[] readingNumeric={true,true};
        long adviceAt=-1;JSONObject adviceRuntime;String adviceKey="";
        PipelineBoard(){
            super(MainActivity.this);setWillNotDraw(false);for(int i=0;i<6;i++)boxes[i]=new RectF();
            stateGraph=new CurveView(false);addView(stateGraph);curveInfo.setPadding(dp(10),dp(4),dp(10),dp(4));curveInfo.setBackground(background(0xffeef3ff,12));addView(curveInfo);
            curveGuide.setTextSize(11);curveGuide.setTextColor(MUTED);curveGuide.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());curveGuide.setIncludeFontPadding(false);curveGuide.setGravity(Gravity.CENTER_VERTICAL);curveGuide.setMaxLines(1);curveGuide.setEllipsize(android.text.TextUtils.TruncateAt.END);curveGuide.setAutoSizeTextTypeUniformWithConfiguration(9,11,1,android.util.TypedValue.COMPLEX_UNIT_SP);curveInfo.addView(curveGuide,new LinearLayout.LayoutParams(-1,-1));
            fusedPanel.setGravity(Gravity.CENTER);for(TextView line:new TextView[]{fusedTitle,fusedValue,fusedScene}){line.setPadding(0,0,0,0);line.setIncludeFontPadding(true);line.setGravity(Gravity.CENTER);line.setMaxLines(1);line.setEllipsize(android.text.TextUtils.TruncateAt.END);line.setAutoSizeTextTypeUniformWithConfiguration(line==fusedValue?12:9,line==fusedValue?21:line==fusedTitle?12:10,1,android.util.TypedValue.COMPLEX_UNIT_SP);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,line==fusedValue?1.8f:1.1f);lp.topMargin=line==fusedValue?dp(4):line==fusedScene?dp(2):0;fusedPanel.addView(line,lp);}fusedScene.setOnClickListener(v->scenesDialog());fusedScene.setFocusable(true);fusedScene.setContentDescription(tr("查看当前场景与全部触发条件"));addView(fusedPanel);
            for(int i=0;i<3;i++){LinearLayout panel=column();panel.setGravity(Gravity.CENTER);readingPanels[i]=panel;TextView title=text(i==0?"主光感":i==1?"辅助光感":"当前屏幕亮度",12,MUTED),value=text("—",i==2?27:23,INK);readingTitles[i]=title;readingValues[i]=value;value.setAutoSizeTextTypeUniformWithConfiguration(12,i==2?27:23,1,android.util.TypedValue.COMPLEX_UNIT_SP);for(TextView line:new TextView[]{title,value}){line.setPadding(0,0,0,0);line.setIncludeFontPadding(false);line.setGravity(Gravity.CENTER);line.setMaxLines(1);line.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=line==title?0:dp(8);panel.addView(line,lp);}addView(panel);}
            TextView protectionTitle=text("输出范围与保护",12,MUTED);protectionTitle.setGravity(Gravity.CENTER);protectionTitle.setPadding(0,0,0,0);protectionTitle.setIncludeFontPadding(false);protectionPanel.addView(protectionTitle,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout cells=row(protectionPanel);cells.setGravity(Gravity.CENTER);LinearLayout.LayoutParams cellsLp=new LinearLayout.LayoutParams(-1,0,1);cellsLp.topMargin=dp(8);cells.setLayoutParams(cellsLp);
            for(int i=0;i<3;i++){final int at=i;LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(4),dp(6),dp(4),dp(6));cell.setBackground(ripple(0xffeef3ff,10));cell.setClipToOutline(true);cell.setOnClickListener(v->{OutputProtection.Item item=OutputProtection.item(at,"","");openAdvice(new StatusAdvice.Action(item.title,item.group,item.target));});cell.setClickable(true);cell.setFocusable(true);LinearLayout.LayoutParams cellLp=new LinearLayout.LayoutParams(0,-1,1);cellLp.leftMargin=i==0?0:dp(8);cells.addView(cell,cellLp);TextView name=text(OutputProtection.item(i,"","").title,11,MUTED);name.setIncludeFontPadding(false);name.setGravity(Gravity.CENTER);name.setPadding(0,0,0,0);cell.addView(name,new LinearLayout.LayoutParams(-1,0,.85f));
                protectionStates[i]=text("等待读取",i==2?14:12,BLUE);protectionDetails[i]=text("点击查看设置",10,MUTED);for(TextView line:new TextView[]{protectionStates[i],protectionDetails[i]}){line.setIncludeFontPadding(false);line.setPadding(0,0,0,0);line.setGravity(Gravity.CENTER);line.setMaxLines(line==protectionStates[i]?1:2);line.setEllipsize(android.text.TextUtils.TruncateAt.END);line.setAutoSizeTextTypeUniformWithConfiguration(line==protectionStates[i]?9:8,line==protectionStates[i]?(i==2?14:12):10,1,android.util.TypedValue.COMPLEX_UNIT_SP);cell.addView(line,new LinearLayout.LayoutParams(-1,0,line==protectionStates[i]?1.05f:1.1f));}}
            addView(protectionPanel);
            setContentDescription(tr("主光感和辅助光感，经融合与场景判断产生有效照度"));refreshAdvice();
        }
        StatusPageLayout geometry(int height){return new StatusPageLayout(height,getResources().getDisplayMetrics().density,getResources().getConfiguration().fontScale);}
        void layoutBoxes(){float w=getWidth(),gap=dp(12),half=(w-gap)/2;StatusPageLayout layout=geometry(getHeight());boxes[0].set(0,layout.top[0],half,layout.bottom[0]);boxes[1].set(half+gap,layout.top[0],w,layout.bottom[0]);for(int i=2;i<6;i++)boxes[i].set(0,layout.top[i-1],w,layout.bottom[i-1]);}
        @Override protected void onMeasure(int ws,int hs){
            int w=MeasureSpec.getSize(ws);StatusPageLayout layout=geometry(MeasureSpec.getMode(hs)==MeasureSpec.UNSPECIFIED?0:MeasureSpec.getSize(hs));setMeasuredDimension(w,layout.height);
            int width=MeasureSpec.makeMeasureSpec(Math.max(0,w-dp(24)),MeasureSpec.EXACTLY),chartHeight=layout.bottom[2]-layout.top[2];
            stateGraph.measure(width,MeasureSpec.makeMeasureSpec(Math.max(0,chartHeight-dp(24)-dp(8)-layout.infoHeight),MeasureSpec.EXACTLY));
            curveInfo.measure(width,MeasureSpec.makeMeasureSpec(layout.infoHeight,MeasureSpec.EXACTLY));
            fusedPanel.measure(width,MeasureSpec.makeMeasureSpec(Math.max(0,layout.bottom[1]-layout.top[1]-dp(24)),MeasureSpec.EXACTLY));
            protectionPanel.measure(width,MeasureSpec.makeMeasureSpec(Math.max(0,layout.bottom[3]-layout.top[3]-dp(24)),MeasureSpec.EXACTLY));
            for(int i=0;i<3;i++){int left=i==1?(int)((w-dp(12))/2f+dp(12)):0,right=i==0?(int)((w-dp(12))/2f):w;readingPanels[i].measure(MeasureSpec.makeMeasureSpec(Math.max(0,right-left-dp(24)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,layout.bottom[0]-layout.top[0]-dp(24)),MeasureSpec.EXACTLY));}
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            layoutBoxes();RectF graph=boxes[3];int bottom=(int)graph.bottom-dp(12),infoTop=bottom-geometry(getHeight()).infoHeight;
            stateGraph.layout(dp(12),(int)graph.top+dp(12),getWidth()-dp(12),infoTop-dp(8));curveInfo.layout(dp(12),infoTop,getWidth()-dp(12),bottom);
            fusedPanel.layout(dp(12),(int)boxes[2].top+dp(12),getWidth()-dp(12),(int)boxes[2].bottom-dp(12));
            protectionPanel.layout(dp(12),(int)boxes[4].top+dp(12),getWidth()-dp(12),(int)boxes[4].bottom-dp(12));
            for(int i=0;i<3;i++){RectF box=boxes[i==2?5:i];readingPanels[i].layout((int)box.left+dp(12),(int)box.top+dp(12),(int)box.right-dp(12),(int)box.bottom-dp(12));}
        }
        void refreshAdvice(){
            long now=SystemClock.uptimeMillis();if(adviceRuntime==runtime&&adviceAt>=0&&now-adviceAt<1000)return;adviceRuntime=runtime;adviceAt=now;
            long elapsed=SystemClock.elapsedRealtime();OutputProtection.Item[] protection=OutputProtection.read(runtime,now,elapsed);StatusAdvice guide=StatusAdvice.guidance(runtime,elapsed);String scene=tr(SceneCatalog.summary(runtime,elapsed));JSONObject control=runtime==null?null:runtime.optJSONObject("brightness_control");boolean holding=control!=null&&"dark".equals(control.optString("owner"))&&control.optBoolean("listening");boolean manual=runtime!=null&&runtime.has("auto_mode")&&!runtime.optBoolean("auto_mode");
            String value=!StatusPresentation.fresh(runtime,elapsed)?tr(runtime==null?"等待读取":"状态待刷新"):holding?tr("暗光锁定保持"):manual?tr("自动采样暂停"):!"active".equals(runtime.optString("sensor_status"))?tr("等待有效照度"):reading(runtime.has("official_effective_lux")?"official_effective_lux":"last_lux"," lux");
            StringBuilder key=new StringBuilder(guide.detail).append('\n').append(scene).append('\n').append(value);for(OutputProtection.Item item:protection)key.append('\n').append(OutputProtection.primary(item,runtime,elapsed)).append(':').append(item.state).append(':').append(item.detail);for(StatusAdvice.Action link:guide.actions)key.append('\n').append(link.text).append(':').append(link.group).append(':').append(link.target);if(key.toString().equals(adviceKey))return;adviceKey=key.toString();
            for(int i=0;i<3;i++){protectionStates[i].setText(tr(OutputProtection.primary(protection[i],runtime,elapsed)));protectionStates[i].setTextColor(themed(OutputProtection.temperatureAlert(protection[i],runtime,elapsed)?0xffbd3434:BLUE));protectionDetails[i].setText(tr(OutputProtection.secondary(protection[i])));protectionStates[i].setContentDescription(tr(OutputProtection.primary(protection[i],runtime,elapsed))+" · "+tr(protection[i].state)+" · "+tr(protection[i].detail));}curveGuide.setText(adviceText(guide));curveGuide.setContentDescription(tr(guide.detail));fusedValue.setText(value);fusedScene.setText(scene);
        }
        void prepareLabel(String value,int size,int color,float width){paint.setColor(color);paint.setStyle(Paint.Style.FILL);float scale=Math.min(1,Math.max(.65f,getHeight()/(float)dp(440)));paint.setTextSize(dp(Math.max(9,size*scale)));while(paint.measureText(value)>width&&paint.getTextSize()>dp(9))paint.setTextSize(paint.getTextSize()-1);}
        void label(Canvas c,String value,float x,float y,int size,int color,float width){value=tr(value);prepareLabel(value,size,color,width);c.drawText(value,x-paint.measureText(value)/2,y,paint);}
        void labelStart(Canvas c,String value,float x,float y,int size,int color,float width){value=tr(value);prepareLabel(value,size,color,width);c.drawText(value,x,y,paint);}
        void labelEnd(Canvas c,String value,float x,float y,int size,int color,float width){value=tr(value);prepareLabel(value,size,color,width);c.drawText(value,x-paint.measureText(value),y,paint);}
        String reading(String key,String unit){double value=runtime==null?Double.NaN:runtime.optDouble(key);return Double.isFinite(value)&&value>=0?format(value)+unit:"—";}
        void connection(Canvas c,float x,float y,float bottom){paint.setStyle(Paint.Style.STROKE);paint.setColor(themed(0xff9db4e2));paint.setStrokeWidth(dp(1.4f));paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,x,bottom,paint);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);refreshAdvice();float w=getWidth();if(w<=0||getHeight()<=0)return;float middle=w/2;boolean animate=visible&&isShown()&&page==0&&runtime!=null&&runtime.optBoolean("auto_mode")&&android.animation.ValueAnimator.areAnimatorsEnabled();
            paint.setPathEffect(new DashPathEffect(new float[]{dp(5),dp(5)},animate?-(SystemClock.uptimeMillis()%1000)/1000f*dp(10):0));float merge=(boxes[0].bottom+boxes[2].top)/2;
            // Both paths start at their sensor and end at the merge point, so both flow inward.
            paint.setStyle(Paint.Style.STROKE);paint.setColor(themed(0xff9db4e2));paint.setStrokeWidth(dp(1.4f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            for(int i=0;i<2;i++){Path branch=new Path();branch.moveTo(boxes[i].centerX(),boxes[i].bottom);branch.lineTo(boxes[i].centerX(),merge);branch.lineTo(middle,merge);c.drawPath(branch,paint);}connection(c,middle,merge,boxes[2].top);
            for(int i=2;i<5;i++)connection(c,middle,boxes[i].bottom,boxes[i+1].top);paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<6;i++){paint.setColor(themed(i==1?0xffeef0f4:i==2?0xffedf3ff:Color.WHITE));c.drawRoundRect(boxes[i],dp(17),dp(17),paint);}
            JSONObject control=runtime==null?null:runtime.optJSONObject("brightness_control");boolean holding=control!=null&&"dark".equals(control.optString("owner"))&&control.optBoolean("listening");boolean manual=runtime!=null&&!runtime.optBoolean("auto_mode");String sensor=runtime==null?"unknown":runtime.optString("sensor_status","unknown");boolean paused=manual||sensor.equals("paused")||sensor.equals("warming");
            for(int i=0;i<2;i++){
                readingText(readingTitles[i],tr(holding?(i==0?"主光感监听":"辅助光感监听"):(i==0?"主光感":"辅助光感")));
                StatusPresentation.Reading value=StatusPresentation.sensor(runtime,i==1,SystemClock.elapsedRealtime());boolean numeric=Double.isFinite(value.lux);
                if(readingNumeric[i]!=numeric){readingNumeric[i]=numeric;readingValues[i].setAutoSizeTextTypeUniformWithConfiguration(numeric?12:9,numeric?23:14,1,android.util.TypedValue.COMPLEX_UNIT_SP);}readingText(readingValues[i],tr(numeric?format(value.lux)+" lux":value.text));
            }
            RectF fused=boxes[2];
            float thresholdY=(fused.bottom+boxes[3].top)/2+dp(3),thresholdGap=dp(6);if(!paused){labelEnd(c,runtime!=null&&runtime.has("darkening_lux_threshold")?"变暗 < "+format(runtime.optDouble("darkening_lux_threshold"))+" lux":"变暗 —",middle-thresholdGap,thresholdY,10,MUTED,w/2-dp(12));labelStart(c,runtime!=null&&runtime.has("brightening_lux_threshold")?"变亮 > "+format(runtime.optDouble("brightening_lux_threshold"))+" lux":"变亮 —",middle+thresholdGap,thresholdY,10,MUTED,w/2-dp(12));}
            readingText(readingValues[2],reading("actual_nit"," nit"));if(animate)postInvalidateDelayed(100);
        }
        void readingText(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    }

    final class CurveView extends View{
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean draft,memory;int point=-1;float startX,startY,startFactor;float[] dragBounds;boolean moved;
        CurveView(boolean draft){this(draft,false);}
        CurveView(boolean draft,boolean memory){super(MainActivity.this);this.draft=draft;this.memory=memory;setContentDescription(tr(draft?"拖动节点调整曲线，点击节点输入数值":memory?"蓝色基础曲线与浅绿色实际曲线":"当前亮度曲线"));setFocusable(draft);}
        float left(){return dp(draft||memory?38:32);}float top(){return dp(draft||memory?18:8);}float width(){return Math.max(0,getWidth()-left()-dp(draft||memory?12:8));}float height(){return getHeight()-top()-dp(draft||memory?28:22);}
        float x(float lux){return left()+(float)(Math.log1p(Math.max(0,Math.min(lux,factoryLux[3])))/Math.log1p(factoryLux[3]))*width();}
        float y(float nit){return top()+height()*(1-nit/maximum);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(12));paint.setColor(MUTED);
            if(factoryLux==null){canvas.drawText(tr("连接后读取曲线"),dp(10),dp(65),paint);return;}
            if(!draft&&!memory&&!StatusPresentation.fresh(runtime,SystemClock.elapsedRealtime())){canvas.drawText(tr("状态待刷新"),dp(10),dp(65),paint);return;}
            float left=left(),top=top(),width=width(),height=height();if(width<=0||height<=0)return;
            CurveComparison data;try{data=CurveComparison.read(runtime,factors,curveFloor,draft);}catch(Exception invalid){canvas.drawText(tr("曲线数据未就绪"),dp(10),dp(65),paint);return;}
            paint.setStrokeWidth(dp(1));paint.setTextSize(dp(10));for(int i=0;i<3;i++){float yy=top+height*i/2;paint.setColor(themed(0xffe7ebf2));canvas.drawLine(left,yy,left+width,yy,paint);paint.setColor(MUTED);canvas.drawText((100-i*50)+"%",0,yy+dp(4),paint);}
            // Editable drafts never replace the applied baseline or current memory in the other graphs.
            String kind=data.kind(runtime);int currentColor=memory||kind.equals("memory")?themed(0xff78b997):kind.equals("system")?themed(0xffa5aab3):BLUE;
            if(draft){drawLine(canvas,data.reference,themed(0xffa5aab3));drawLine(canvas,data.baseline,BLUE);}
            else if(memory){drawLine(canvas,data.baseline,BLUE);drawLine(canvas,data.current,currentColor);}
            else drawLine(canvas,data.current,currentColor);
            for(int i=0;i<4;i++){float xx=x(factoryLux[i]);paint.setTextSize(dp(10));paint.setColor(MUTED);String label=format(factoryLux[i]);canvas.drawText(label,Math.max(0,Math.min(getWidth()-paint.measureText(label),xx-paint.measureText(label)/2)),top+height+dp(20),paint);
                if(draft){float yy=y(data.baseline.at(factoryLux[i]));paint.setColor(themed(Color.WHITE));canvas.drawCircle(xx,yy,dp(8),paint);paint.setColor(i==3?MUTED:BLUE);canvas.drawCircle(xx,yy,dp(5),paint);}
            }
            if(memory&&runtime!=null){for(String key:new String[]{"memory_saved_anchors","memory_live_points"}){JSONArray points=runtime.optJSONArray(key);if(points==null)continue;paint.setColor(themed(0xff78b997));boolean saved=key.equals("memory_saved_anchors");for(int i=0;i<points.length();i++){JSONObject p=points.optJSONObject(i);if(p==null)continue;double lux=p.optDouble("lux",Double.NaN),nit=p.optDouble("display_nit",Double.NaN);if(!Double.isFinite(lux)||lux<0||!Double.isFinite(nit)||nit<0)continue;float xx=x((float)lux),yy=y((float)Math.min(maximum,nit));paint.setStyle(saved?Paint.Style.STROKE:Paint.Style.FILL);paint.setStrokeWidth(dp(2));canvas.drawCircle(xx,yy,dp(saved?7:4),paint);}}paint.setStyle(Paint.Style.FILL);}
            if(!draft&&data.current==null){paint.setTextSize(dp(12));paint.setColor(MUTED);canvas.drawText(tr("当前路径未取得可绘制曲线"),left,top+dp(25),paint);}
            if(!draft&&data.current!=null&&runtime.optBoolean("auto_mode")&&runtime.optString("sensor_status","active").equals("active")){double value=runtime.optDouble("last_lux",Double.NaN);if(Double.isFinite(value)&&value>=0){float lux=(float)value,xx=x(lux),yy=y(Math.max(0,Math.min(maximum,data.current.at(lux))));paint.setColor(themed(Color.WHITE));canvas.drawCircle(xx,yy,dp(6),paint);paint.setColor(currentColor);canvas.drawCircle(xx,yy,dp(4),paint);}}
        }
        void drawLine(Canvas canvas,CurveComparison.Line line,int color){
            if(line==null)return;Path path=new Path();float logarithm=(float)Math.log1p(factoryLux[3]);for(int i=0;i<=128;i++){float lux=(float)Math.expm1(logarithm*i/128),xx=left()+width()*i/128,yy=y(Math.max(0,Math.min(maximum,line.at(lux))));if(i==0)path.moveTo(xx,yy);else path.lineTo(xx,yy);}paint.setColor(color);paint.setStrokeWidth(dp(2));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event){
            if(memory){if(event.getActionMasked()==MotionEvent.ACTION_UP){performClick();memoryDetails();}return true;}
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
    }
}
