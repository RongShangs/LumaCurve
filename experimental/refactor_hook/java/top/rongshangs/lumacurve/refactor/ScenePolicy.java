package top.rongshangs.lumacurve.refactor;

import java.util.*;
import org.json.*;

/** Display-thread state machine. A matching rule can deny an OEM scene, never force it on. */
final class ScenePolicy {
    static final class State {boolean active;int pending=-1;long since=-1;int update(int raw,long now,int delay){if(raw<0){active=false;pending=-1;since=-1;return -1;}if(raw==(active?1:0)){pending=-1;since=-1;return raw;}if(pending!=raw||since<0||now<since){pending=raw;since=now;}if(now-since>=delay){active=raw==1;pending=-1;since=-1;}return active?1:0;}}
    final Map<String,State> states=new HashMap<>();long next=-1,lastNow=-1;
    int match(String id,SceneOptions.Condition c,JSONObject facts,int minute,long now){State state=states.get(id);if(state==null){state=new State();states.put(id,state);}int raw=c.test(facts,minute),delay=raw==1?c.confirmEnter:c.confirmExit;int result=state.update(raw,now,delay);if(state.pending>=0){long at=state.since+(state.pending==1?c.confirmEnter:c.confirmExit);next=next<0?at:Math.min(next,at);}return result;}
    boolean[] evaluate(SceneOptions options,JSONObject facts,int minute,long now){if(lastNow>=0&&now<lastNow)reset();lastNow=now;boolean[] denied=new boolean[SceneOptions.IDS.length];next=-1;
        for(int i=0;i<denied.length;i++){SceneOptions.Entry e=options.entries[i];denied[i]=e.mode.equals("block");if(e.mode.equals("condition"))denied[i]=match("entry:"+i,e.condition,facts,minute,now)==0;}
        return denied;
    }
    void reset(){states.clear();next=-1;lastNow=-1;}
}
