package top.rongshangs.lumacurve.refactor;

import java.util.*;
import org.json.*;

/** Independent message speeds with full-height jitter and predictive headway. */
final class AboutMarquee {
    static final String[] TESTERS={"勿忘","灭","春暖花开","无缺","DickTsai","NOTTRUE"};
    interface Measure {float width(String text);}
    static final class Line {final String text;final int lane;final float width,speed,y;float x;Line(String text,int lane,float width,float x,float speed,float y){this.text=text;this.lane=lane;this.width=width;this.x=x;this.speed=speed;this.y=y;}}
    final List<Line> lines=new ArrayList<>();final List<String> cycle=new ArrayList<>();final Random random;List<String> messages=Collections.emptyList();int cursor,lanes;float width,density=1;Measure measure;
    AboutMarquee(){random=new Random();}
    AboutMarquee(long seed){random=new Random(seed);}
    static float visibleHeight(float top,float height,float viewport){return Math.max(0,Math.min(Math.max(0,viewport),top+Math.max(0,height))-Math.max(0,top));}
    static List<String> messages(JSONObject data)throws JSONException {List<String> values=new ArrayList<>();JSONArray entries=data.getJSONArray("entries");for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);values.add(e.getString("name")+"："+ThanksFeed.message(e.getString("message")));}values.addAll(Arrays.asList(TESTERS));return Collections.unmodifiableList(values);}
    void configure(List<String> next,float width,int lanes,float density,Measure measure){width=Math.max(1,width);lanes=Math.max(1,Math.min(10,lanes));density=Math.max(.5f,density);if(this.width==width&&this.lanes==lanes&&this.density==density&&messages.equals(next))return;this.messages=new ArrayList<>(next);this.width=width;this.lanes=lanes;this.density=density;this.measure=measure;cursor=0;cycle.clear();cycle.addAll(next);Collections.shuffle(cycle,random);lines.clear();if(!messages.isEmpty())for(int lane=0;lane<this.lanes;lane++)spawn(lane,this.width*(random.nextFloat()*.82f-.08f),speed());}
    float speed(){return (16+random.nextFloat()*12)*density;}
    void spawn(int lane,float x,float speed){if(cursor>=cycle.size()){cursor=0;Collections.shuffle(cycle,random);}String text=cycle.get(cursor++);float y=(lane+.35f+random.nextFloat()*.3f)/lanes;lines.add(new Line(text,lane,Math.max(1,measure.width(text)),x,speed,y));}
    boolean safe(int lane,float x,float speed,float gap){for(Line line:lines)if(line.lane==lane){float distance=x-line.x-line.width;if(distance<gap)return false;float seconds=Math.max(0,(line.x+line.width)/line.speed);if(speed>line.speed&&distance-(speed-line.speed)*seconds<gap)return false;}return true;}
    void advance(float seconds){if(messages.isEmpty()||measure==null)return;float dt=Float.isFinite(seconds)?Math.max(0,Math.min(.1f,seconds)):0;if(dt==0)return;for(Iterator<Line> it=lines.iterator();it.hasNext();){Line line=it.next();line.x-=line.speed*dt;if(line.x+line.width<0)it.remove();}for(int lane=0;lane<lanes;lane++){int count=0;for(Line line:lines)if(line.lane==lane){count++;}if(count==0){float x=width+(20+random.nextFloat()*40)*density,nextSpeed=speed(),gap=(28+random.nextFloat()*24)*density;if(safe(lane,x,nextSpeed,gap))spawn(lane,x,nextSpeed);}}}
}
