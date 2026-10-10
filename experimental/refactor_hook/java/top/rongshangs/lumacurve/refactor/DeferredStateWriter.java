package top.rongshangs.lumacurve.refactor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Telemetry only: never wait for SettingsProvider from the system display thread. */
final class DeferredStateWriter {
    interface Writer {boolean put(String key,String value)throws Exception;}
    private static final Executor IO=Executors.newSingleThreadExecutor(task->{
        Thread thread=new Thread(task,"HyperLux-state-io");thread.setDaemon(true);return thread;
    });
    private final Executor executor;
    private final Writer writer;
    private final Consumer<Throwable> errors;
    private final LinkedHashMap<String,LinkedHashMap<String,String>> pending=new LinkedHashMap<>();
    private boolean running,closed;
    DeferredStateWriter(Writer writer,Consumer<Throwable> errors){this(IO,writer,errors);}
    DeferredStateWriter(Executor executor,Writer writer,Consumer<Throwable> errors){this.executor=executor;this.writer=writer;this.errors=errors;}
    void submit(String group,Map<String,String> values){
        if(!group.equals("ack")&&!group.equals("state"))throw new IllegalArgumentException("Unknown state group");
        LinkedHashMap<String,String> copy=new LinkedHashMap<>(values);
        synchronized(this){
            if(closed)return;
            // At most one pending acknowledgement and one complete state snapshot.
            // Replace the entire snapshot: merging chunks would mix snapshot identities.
            pending.put(group,copy);if(running)return;running=true;
        }
        try{executor.execute(this::drain);}
        catch(RuntimeException rejected){synchronized(this){running=false;}report(rejected);}
    }
    private void drain(){
        while(true){
            LinkedHashMap<String,String> batch;
            synchronized(this){
                if(closed||pending.isEmpty()){pending.clear();running=false;return;}
                Iterator<LinkedHashMap<String,String>> iterator=pending.values().iterator();batch=iterator.next();iterator.remove();
            }
            try{
                // Preserve chunk order and publish the core manifest last.
                for(Map.Entry<String,String> entry:batch.entrySet()){
                    synchronized(this){if(closed){pending.clear();running=false;return;}}
                    if(!writer.put(entry.getKey(),entry.getValue()))throw new IOException("State write rejected: "+entry.getKey());
                }
            }catch(Throwable failure){report(failure);}
        }
    }
    private void report(Throwable failure){try{errors.accept(failure);}catch(Throwable ignored){}}
    void close(){synchronized(this){closed=true;pending.clear();}}
}
