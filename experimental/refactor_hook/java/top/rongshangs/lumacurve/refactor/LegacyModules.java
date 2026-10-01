package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only inventory of this project's predecessors; never changes module files. */
public final class LegacyModules {
    static final String[] IDS={"luma_curve","luma_curve_official_test","ios_auto_brightness"};
    static final String[] NAMES={"LumaCurve 旧版模块","LumaCurve 官方曲线测试模块","iOS Auto Brightness"};
    public static List<String[]> scan(File installed,File pending){
        List<String[]> result=new ArrayList<>();
        for(int i=0;i<IDS.length;i++){
            File active=new File(installed,IDS[i]),update=new File(pending,IDS[i]);
            boolean hasActive=active.isDirectory()&&!new File(active,"remove").exists();
            boolean hasUpdate=update.isDirectory()&&!new File(update,"remove").exists();
            if(!hasActive&&!hasUpdate)continue;
            File module=hasUpdate?update:active;String name=NAMES[i];File prop=new File(module,"module.prop");
            if(prop.isFile()&&prop.length()<=16384)try(Reader r=new InputStreamReader(new FileInputStream(prop),StandardCharsets.UTF_8)){
                Properties p=new Properties();p.load(r);String candidate=p.getProperty("name",name).replaceAll("[\\p{Cntrl}]"," ").trim();if(!candidate.isEmpty()&&candidate.length()<=80)name=candidate;
            }catch(IOException|IllegalArgumentException ignored){}
            result.add(new String[]{IDS[i],name,hasUpdate?"pending":new File(active,"disable").exists()?"disabled":"installed"});
        }
        return result;
    }
}
