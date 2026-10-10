"""Exercise production display scheduling with queued callbacks and OEM reentry.

Host tests verify call ordering; they do not reproduce a particular phone freeze.
"""
from pathlib import Path
import subprocess

R = Path(__file__).resolve().parents[1]
S = R / 'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O = R / 'build/display-scheduling-tests'


def method(signature):
    text = (S / 'HookRuntime.java').read_text(encoding='utf8')
    start = text.index(signature)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += int(text[end] == '{') - int(text[end] == '}')
        end += 1
    return text[start:end]


methods = '\n'.join(method(s) for s in [
    'void requestRecalculation()', 'void recalculate()', 'void sample(float',
    'void queuePublish()', 'void queueControlPublish()', 'long publishInterval()',
])
files = {
    'android/os/SystemClock.java': '''package android.os;public class SystemClock{
        public static long now=20000;public static long elapsedRealtime(){return now;}}''',
    'de/robv/android/xposed/XposedBridge.java': '''package de.robv.android.xposed;
        public class XposedBridge{public static void log(String message){}}''',
    'top/rongshangs/lumacurve/refactor/HookEntry.java': '''package top.rongshangs.lumacurve.refactor;
        public class HookEntry{static Object get(Object o,String field)throws Exception{
        return o.getClass().getField(field).get(o);}}''',
    'top/rongshangs/lumacurve/refactor/HookRuntime.java': '''package top.rongshangs.lumacurve.refactor;
import android.os.*;import java.util.*;import de.robv.android.xposed.XposedBridge;
public class HookRuntime{
 boolean closed,publishQueued,controlPublishQueued,recalculationQueued,recalculating,thread=true;
 long consumed,lastPublish,viewUntil;float lastLux=Float.NaN,lastNit=Float.NaN;
 int publications;final Kernel kernel=new Kernel();final Queue handler=new Queue();
 final Owner owner=new Owner(this);
 boolean onDisplayThread(){return !closed&&thread;}
 void publish(){publications++;lastPublish=SystemClock.now;}
 public static class Kernel{boolean active=true;Object plan(){return active?this:null;}}
 public static class Queue{
  final List<Runnable> jobs=new ArrayList<>();final List<Long> delays=new ArrayList<>();boolean accept=true;
  boolean post(Runnable r){return postDelayed(r,0);}
  boolean postDelayed(Runnable r,long delay){if(!accept)return false;jobs.add(r);delays.add(delay);return true;}
  void drain(){int iterations=0;while(!jobs.isEmpty()){if(iterations++>10)throw new AssertionError("runaway queue");delays.remove(0);jobs.remove(0).run();}}
 }
 public static class Owner{
  public final Dpc mDisplayPowerController;public final Impl mAutomaticBrightnessControllerImpl=new Impl();
  int autoCalls;final HookRuntime s;Owner(HookRuntime s){this.s=s;mDisplayPowerController=new Dpc(s);}
  public void updateAutoBrightness(){autoCalls++;s.requestRecalculation();s.sample(3,4);}
 }
 public static class Impl{public Object mAutomaticBrightnessController=new Object();}
 public static class Dpc{
  int calls;boolean fail;final HookRuntime s;Dpc(HookRuntime s){this.s=s;}
  public void updateBrightness(){calls++;if(fail)throw new IllegalStateException("OEM unavailable");s.requestRecalculation();}
 }
''' + methods + '\n}',
    'top/rongshangs/lumacurve/refactor/SchedulingTest.java': '''package top.rongshangs.lumacurve.refactor;
import android.os.SystemClock;
public class SchedulingTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 public static void main(String[] args){
  HookRuntime s=new HookRuntime();
  for(int i=0;i<100;i++)s.requestRecalculation();
  check(s.owner.mDisplayPowerController.calls==0&&s.owner.autoCalls==0);
  check(s.handler.jobs.size()==1&&s.recalculationQueued);
  s.handler.drain();check(s.owner.mDisplayPowerController.calls==1&&s.owner.autoCalls==1);
  check(!s.recalculationQueued&&!s.recalculating&&s.handler.jobs.isEmpty());
  check(s.publications==1&&s.lastLux==3&&s.lastNit==4);
  s.requestRecalculation();s.handler.drain();check(s.owner.autoCalls==2);
  s.owner.mDisplayPowerController.fail=true;s.requestRecalculation();s.handler.drain();
  check(!s.recalculating&&!s.recalculationQueued);s.owner.mDisplayPowerController.fail=false;
  s.requestRecalculation();s.handler.drain();check(s.owner.autoCalls==3);
  s.requestRecalculation();s.closed=true;s.handler.drain();check(s.owner.autoCalls==3);
  s.requestRecalculation();check(s.handler.jobs.isEmpty());
  s=new HookRuntime();s.handler.accept=false;s.requestRecalculation();check(!s.recalculationQueued);
  s.handler.accept=true;s.requestRecalculation();s.handler.drain();check(s.owner.autoCalls==1);
  s=new HookRuntime();s.sample(10,20);check(s.publications==0&&s.consumed==1);
  for(int i=0;i<100;i++)s.sample(i,i+1);
  check(s.publications==0&&s.handler.jobs.size()<=2);
  s.handler.drain();check(s.publications<=2&&s.lastLux==99&&s.lastNit==100);
  s=new HookRuntime();s.thread=false;s.sample(10,20);check(s.consumed==0&&s.handler.jobs.isEmpty());
  s.thread=true;s.sample(Float.NaN,20);s.sample(10,-1);check(s.consumed==0&&s.handler.jobs.isEmpty());
  s.sample(1,2);s.closed=true;s.handler.drain();check(s.publications==0);
  s=new HookRuntime();s.kernel.active=false;s.sample(1,2);check(s.publications==0&&s.consumed==0);
  s.handler.drain();check(s.publications==1);SystemClock.now+=10000;
  s.sample(2,3);check(s.publications==1);s.handler.drain();check(s.publications==2);
  System.out.println("Display scheduling: "+cases+" cases PASS; host call-order verification, affected phones not tested");
 }
}''',
}
paths = []
for name, text in files.items():
    path = O / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf8')
    paths.append(str(path))
classes = O / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '-encoding', 'UTF-8', '--release', '8', '-d', str(classes), *paths], check=True)
subprocess.run(['java', '-cp', str(classes), 'top.rongshangs.lumacurve.refactor.SchedulingTest'], check=True)
