package top.rongshangs.lumacurve.refactor;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.List;

/** Immediate in-page filtering. All results navigate through existing settings destinations. */
final class SettingsSearch {
    final MainActivity a;final LinearLayout header;final EditText input;final TextView clear;LinearLayout results;boolean restoring,searching;int homePosition;
    SettingsSearch(MainActivity owner){
        a=owner;header=a.column();header.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(8));LinearLayout box=a.row(header);box.setPadding(a.dp(8),0,a.dp(4),0);box.setMinimumHeight(a.dp(52));box.setBackground(a.background(0xffeef2fa,28));
        View icon=new View(a){final Paint paint=new Paint(3);protected void onDraw(Canvas c){paint.setColor(a.MUTED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(1.8f));float x=getWidth()/2f,y=getHeight()/2f;c.drawCircle(x-a.dp(2),y-a.dp(2),a.dp(6),paint);c.drawLine(x+a.dp(3),y+a.dp(3),x+a.dp(8),y+a.dp(8),paint);}};icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);box.addView(icon,new LinearLayout.LayoutParams(a.dp(36),a.dp(48)));
        input=new EditText(a);input.setSingleLine(true);input.setTextSize(16);input.setTextColor(a.INK);input.setHintTextColor(a.MUTED);input.setHint(a.english?"Search settings":"搜索设置与功能");input.setContentDescription(input.getHint());input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(a.dp(2),a.dp(9),a.dp(2),a.dp(9));input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);box.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        clear=a.text("×",22,a.MUTED);clear.setGravity(Gravity.CENTER);clear.setContentDescription(a.english?"Clear search":"清空搜索");clear.setBackground(a.ripple(Color.TRANSPARENT,24));clear.setClickable(true);clear.setFocusable(true);clear.setOnClickListener(v->input.setText(""));box.addView(clear,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));clear.setVisibility(View.GONE);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter(!restoring);}public void afterTextChanged(Editable e){}});
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){blur();return true;}return false;});
    }
    void bind(){results=a.column();a.pages[1].addView(results);results.setVisibility(View.GONE);}
    String query(){return input.getText().toString();}
    void restore(String query){restoring=true;input.setText(query==null?"":query);input.setSelection(input.length());restoring=false;}
    void filter(boolean scrollToTop){
        if(results==null)return;String query=query();boolean next=!SettingsSearchIndex.normalize(query).isEmpty(),leaving=searching&&!next,modeChanged=searching!=next;if(next&&!searching&&!restoring&&a.settingsHomeScroll!=null)homePosition=a.settingsHomeScroll.getScrollY();searching=next;clear.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);a.settingsActions.setVisibility(searching?View.GONE:View.VISIBLE);a.settingsHome.setVisibility(searching?View.GONE:View.VISIBLE);results.setVisibility(searching?View.VISIBLE:View.GONE);results.removeAllViews();
        if(searching){List<SettingsSearchIndex.Entry> matches=SettingsSearchIndex.find(query);if(matches.isEmpty()){LinearLayout empty=a.card(results);empty.addView(a.text(a.english?"No matching settings":"没有找到相关功能",16,a.INK));empty.addView(a.text(a.english?"Try brightness, memory, low light or temperature.":"试试亮度、记忆、暗光或温控等关键词",13,a.MUTED));}
            for(SettingsSearchIndex.Entry entry:matches){LinearLayout card=a.card(results);card.setPadding(a.dp(16),a.dp(12),a.dp(14),a.dp(12));card.setBackground(a.ripple(Color.WHITE,18));card.setClipToOutline(true);card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->open(entry));LinearLayout line=a.row(card);LinearLayout copy=a.column();line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));copy.addView(a.text(entry.title(a.english),16,a.INK));copy.addView(a.text(entry.description(a.english),12,a.MUTED));copy.addView(a.text(entry.section(a.english),11,a.BLUE));line.addView(a.new NavigationArrow(false),new LinearLayout.LayoutParams(a.dp(28),a.dp(40)));}
        }
        if(scrollToTop&&a.settingsHomeScroll!=null){++a.settingsScrollRestore;a.settingsHomeRestoring=false;a.settingsHomeY=leaving?homePosition:0;if(leaving)a.restoreSettingsHome();else a.settingsHomeScroll.scrollTo(0,0);if(a.floatingNavigation!=null)a.floatingNavigation.show(false);}
        if(modeChanged)revealMode(scrollToTop);
    }
    void finishAnimation(){for(View view:new View[]{results,a.settingsActions,a.settingsHome})if(view!=null){view.animate().setListener(null).withEndAction(null);view.animate().cancel();view.setAlpha(1);view.setTranslationY(0);}}
    void revealMode(boolean animate){finishAnimation();if(!animate||!a.visible||!android.animation.ValueAnimator.areAnimatorsEnabled())return;for(View view:searching?new View[]{results}:new View[]{a.settingsActions,a.settingsHome}){view.setAlpha(.8f);view.setTranslationY(a.dp(4));view.animate().alpha(1).translationY(0).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}}
    void open(SettingsSearchIndex.Entry entry){blur();if(entry.target.equals("feature_checks")){a.select(2);a.showFeatureChecks();}else if(entry.target.equals("logs"))a.select(2);else if(entry.target.equals("quick_setup"))a.quickSetup.open();else a.openAdvice(new StatusAdvice.Action(entry.title(a.english),entry.group,entry.target));}
    void blur(){input.clearFocus();InputMethodManager ime=(InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE);if(ime!=null)ime.hideSoftInputFromWindow(input.getWindowToken(),0);}
}
