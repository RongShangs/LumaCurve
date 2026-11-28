package top.rongshangs.lumacurve.refactor;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;
import org.json.JSONArray;

/** Apply the same write level to the LSPosed sink and the recent app snapshot. */
final class RuntimeLog {
    static final String KEY="hyperlux_log_level_v1";
    static final String FULL="full",ERRORS="errors";
    static final int LIMIT=160;
    private static final String TIME_FORMAT="MM-dd HH:mm:ss";
    private static final String ERROR_MARK="[ERROR] ";
    private final ArrayDeque<String> records=new ArrayDeque<>();
    private final Consumer<String> sink;
    private volatile String level=FULL;

    RuntimeLog(Consumer<String> sink){this.sink=sink;}
    static String normalize(String value){return ERRORS.equals(value)?ERRORS:FULL;}
    String level(){return level;}
    boolean full(){return FULL.equals(level);}
    boolean select(String value){
        String next=normalize(value);
        if(next.equals(level))return false;
        level=next;
        return true;
    }
    void record(String message,boolean error){
        if(!error&&!full())return;
        String record=line(message,error);
        synchronized(records){
            if(records.size()>=LIMIT)records.removeFirst();
            records.addLast(record);
        }
        try{sink.accept("LumaCurve "+message);}catch(RuntimeException unavailable){/* Retain the recent snapshot if LSPosed logging fails. */}
    }
    JSONArray snapshot(){
        synchronized(records){return new JSONArray(records);}
    }

    static String line(String message,boolean error){
        String time=new SimpleDateFormat(TIME_FORMAT,Locale.ROOT).format(new Date());
        return "["+time+"] "+(error?ERROR_MARK:"")+message;
    }

    static String visibleLines(JSONArray logs,boolean errorsOnly){
        StringBuilder text=new StringBuilder();
        if(logs!=null)for(int i=0;i<logs.length();i++){
            String line=logs.optString(i,"");
            if(line.isEmpty())continue;
            if(!errorsOnly||line.startsWith("[")&&line.startsWith("] "+ERROR_MARK,TIME_FORMAT.length()+1)){
                text.append(line).append('\n');
            }
        }
        return text.toString();
    }
}
