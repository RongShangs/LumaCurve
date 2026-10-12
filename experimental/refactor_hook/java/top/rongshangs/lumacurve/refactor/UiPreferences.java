package top.rongshangs.lumacurve.refactor;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Activity.getPreferences is keyed by the launching component, including activity aliases.
 * Keep the original activity store and merge old alias-only preferences once.
 */
final class UiPreferences {
    static final String NAME="refactor.MainActivity",MIGRATED="stable_interface_store_v1";
    static synchronized SharedPreferences open(Context context){
        SharedPreferences result=context.getSharedPreferences(NAME,Context.MODE_PRIVATE);
        if(result.getBoolean(MIGRATED,false))return result;
        Map<String,?> original=result.getAll();Set<String> present=new HashSet<>(original.keySet());
        boolean acknowledged=result.getBoolean(SettingsNotice.ACK_KEY,false);
        SharedPreferences.Editor editor=result.edit();
        for(String name:new String[]{"Launcher",context.getPackageName()+".Launcher",context.getPackageName()+".refactor.MainActivity"}){
            SharedPreferences old=context.getSharedPreferences(name,Context.MODE_PRIVATE);
            acknowledged|=old.getBoolean(SettingsNotice.ACK_KEY,false);
            for(Map.Entry<String,?> item:old.getAll().entrySet()){
                String key=item.getKey();if(key.equals(MIGRATED)||!present.add(key))continue;Object value=item.getValue();
                if(value instanceof String)editor.putString(key,(String)value);
                else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)editor.putInt(key,(Integer)value);
                else if(value instanceof Long)editor.putLong(key,(Long)value);
                else if(value instanceof Float)editor.putFloat(key,(Float)value);
                else if(value instanceof Set){Set<String> copy=new HashSet<>();for(Object element:(Set<?>)value)if(element instanceof String)copy.add((String)element);editor.putStringSet(key,copy);}
            }
        }
        editor.putBoolean(SettingsNotice.ACK_KEY,acknowledged).putBoolean(MIGRATED,true).apply();return result;
    }
}
