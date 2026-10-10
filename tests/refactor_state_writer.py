"""Production telemetry writer: real blocked IO, bounded pending updates and cancellation."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/state-writer-tests';O.mkdir(exist_ok=True)
test=O/'StateWriterTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class StateWriterTest{
 static int cases;
 static void check(boolean value){if(!value)throw new AssertionError("case "+cases);cases++;}
 static Map<String,String> snapshot(String value){LinkedHashMap<String,String> result=new LinkedHashMap<>();result.put("chunk",value);result.put("core",value);return result;}
 static class Queue implements Executor{List<Runnable> tasks=new ArrayList<>();boolean reject;public void execute(Runnable task){if(reject)throw new RejectedExecutionException();tasks.add(task);}void drain(){while(!tasks.isEmpty())tasks.remove(0).run();}}
 public static void main(String[] args)throws Exception{
  Queue queue=new Queue();List<String> written=new ArrayList<>();List<Throwable> errors=new ArrayList<>();
  DeferredStateWriter writer=new DeferredStateWriter(queue,(k,v)->{written.add(k+"="+v);return true;},errors::add);
  Map<String,String> mutable=snapshot("first");writer.submit("state",mutable);mutable.put("core","mutated");
  check(written.isEmpty()&&queue.tasks.size()==1);queue.drain();check(written.equals(Arrays.asList("chunk=first","core=first")));
  written.clear();for(int i=0;i<10000;i++){writer.submit("state",snapshot("v"+i));writer.submit("ack",Collections.singletonMap("ack","v"+i));}
  check(queue.tasks.size()==1&&written.isEmpty());queue.drain();check(written.equals(Arrays.asList("chunk=v9999","core=v9999","ack=v9999")));
  // Replacing a long snapshot with a short one must not leave queued old chunks.
  written.clear();Map<String,String> many=snapshot("old");many.put("obsolete","old");writer.submit("state",many);writer.submit("state",snapshot("new"));queue.drain();check(!written.contains("obsolete=old")&&written.size()==2);
  writer.submit("state",snapshot("cancelled"));writer.close();queue.drain();check(written.size()==2);writer.submit("ack",Collections.singletonMap("ack","closed"));check(queue.tasks.isEmpty());
  boolean invalid=false;try{writer.submit("brightness",snapshot("bad"));}catch(IllegalArgumentException expected){invalid=true;}check(invalid);
  // A partial failed snapshot must not publish its core, but later snapshots can recover.
  written.clear();AtomicInteger attempts=new AtomicInteger();
  writer=new DeferredStateWriter(queue,(k,v)->{written.add(k+"="+v);return attempts.incrementAndGet()!=1;},errors::add);
  writer.submit("state",snapshot("fail"));queue.drain();check(written.equals(Arrays.asList("chunk=fail"))&&errors.size()==1);
  writer.submit("state",snapshot("recovered"));queue.drain();check(written.contains("core=recovered"));
  // Exceptions in the backend or error callback cannot wedge the drain flag.
  AtomicInteger fails=new AtomicInteger();writer=new DeferredStateWriter(queue,(k,v)->{if(fails.getAndIncrement()==0)throw new SecurityException();written.add(k+"="+v);return true;},error->{throw new IllegalStateException();});
  writer.submit("state",snapshot("throw"));queue.drain();writer.submit("state",snapshot("retry"));queue.drain();check(written.contains("core=retry"));
  queue.reject=true;writer.submit("state",snapshot("rejected"));check(queue.tasks.isEmpty());queue.reject=false;writer.submit("state",snapshot("accepted"));queue.drain();check(written.contains("core=accepted"));
  // Real IO remains blocked while callers submit freely, then only the latest pending state drains.
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
  List<String> asynchronous=Collections.synchronizedList(new ArrayList<>());AtomicReference<Thread> worker=new AtomicReference<>();
  writer=new DeferredStateWriter((k,v)->{worker.set(Thread.currentThread());if(v.equals("blocked")&&k.equals("chunk")){entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("backend release timed out");}asynchronous.add(k+"="+v);if(k.equals("core")&&v.equals("latest"))finished.countDown();return true;},error->{throw new AssertionError(error);});
  writer.submit("state",snapshot("blocked"));check(entered.await(5,TimeUnit.SECONDS));
  for(int i=0;i<10000;i++)writer.submit("state",snapshot("superseded"+i));writer.submit("state",snapshot("latest"));
  check(worker.get()!=Thread.currentThread()&&worker.get().getName().equals("HyperLux-state-io"));check(asynchronous.isEmpty());
  release.countDown();check(finished.await(5,TimeUnit.SECONDS));check(asynchronous.equals(Arrays.asList("chunk=blocked","core=blocked","chunk=latest","core=latest")));writer.close();
  // Closing during an in-flight call permits that call to finish but cancels the remaining core.
  ExecutorService dedicated=Executors.newSingleThreadExecutor();CountDownLatch begin=new CountDownLatch(1),end=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
  writer=new DeferredStateWriter(dedicated,(k,v)->{calls.incrementAndGet();begin.countDown();end.await(5,TimeUnit.SECONDS);return true;},errors::add);
  writer.submit("state",snapshot("closing"));check(begin.await(5,TimeUnit.SECONDS));writer.close();end.countDown();dedicated.shutdown();check(dedicated.awaitTermination(5,TimeUnit.SECONDS));check(calls.get()==1);
  System.out.println("Deferred state IO: "+cases+" cases PASS; real blocked writer and queue ordering; Android provider not tested");
 }
}
''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(classes),str(S/'DeferredStateWriter.java'),str(test)],check=True)
subprocess.run(['java','-cp',str(classes),'top.rongshangs.lumacurve.refactor.StateWriterTest'],check=True)
