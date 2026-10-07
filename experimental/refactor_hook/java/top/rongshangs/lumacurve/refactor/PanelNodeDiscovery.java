package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

/** Read-only discovery. Only display-labelled LED devices can become candidates. */
final class PanelNodeDiscovery {
    private static final Set<String> PRIMARY=new HashSet<>(Arrays.asList(
        "panel0-backlight","panel0","display0-backlight","display0","lcd0-backlight","lcd0",
        "lcd-backlight","lcd_backlight","lcd","display-backlight","display_backlight",
        "mtk-lcd-backlight","mtk-lcd","mtk-backlight","mtk-bl","mdss-bl","primary-backlight"));
    static boolean excluded(String name) {
        String s=name.toLowerCase(Locale.ROOT);
        for(String word:new String[]{"secondary","second","aux","cover","rear","subdisplay","sub-display","sub_backlight","sub-backlight","outer","keyboard","kbd","button","torch","flash","notification","indicator","rgb"})
            if(s.contains(word))return true;
        return s.matches(".*(?:panel|display|lcd)[_-]?[1-9].*");
    }
    static boolean primary(String name){return PRIMARY.contains(name.toLowerCase(Locale.ROOT));}
    static int number(File file)throws IOException {
        try(InputStream in=new FileInputStream(file)){
            byte[] b=new byte[33];int used=0,n;
            while(used<b.length&&(n=in.read(b,used,b.length-used))>0)used+=n;
            if(used==b.length)throw new IOException("value_too_long");
            String s=new String(b,0,used,StandardCharsets.US_ASCII).trim();
            if(!s.matches("[0-9]{1,5}"))throw new IOException("invalid_integer");
            int value=Integer.parseInt(s);if(value>65535)throw new IOException("range_exceeds_protocol");return value;
        }
    }
    static JSONObject discover(File sys)throws Exception {
        JSONArray candidates=new JSONArray();Map<String,JSONObject> eligible=new LinkedHashMap<>();
        String allowed=new File(sys,"devices").getCanonicalPath()+File.separator;
        boolean incomplete=false;
        for(String group:new String[]{"backlight","leds"}){
            File dir=new File(sys,"class/"+group);File[] entries=dir.listFiles();
            if(entries==null){if(dir.exists())incomplete=true;continue;}
            Arrays.sort(entries,Comparator.comparing(File::getName));
            if(entries.length>64)incomplete=true;
            for(int i=0;i<Math.min(entries.length,64);i++){
                File device=entries[i],node=new File(device,"brightness"),max=new File(device,"max_brightness");
                JSONObject row=new JSONObject().put("path",node.getAbsolutePath()).put("class",group).put("name",device.getName());candidates.put(row);
                if(!device.getName().matches("[A-Za-z0-9_.:-]{1,96}")||device.getName().equals(".")||device.getName().equals("..")){row.put("rejected","unsupported_node_name");continue;}
                if(excluded(device.getName())){row.put("rejected","not_primary_display");continue;}
                boolean explicit=primary(device.getName());
                if("leds".equals(group)&&!explicit){row.put("rejected","led_not_identified_as_display");continue;}
                try{
                    if(!node.isFile()||!max.isFile())throw new IOException("missing_brightness_pair");
                    if(Files.isSymbolicLink(node.toPath())||Files.isSymbolicLink(max.toPath()))throw new IOException("attribute_is_symlink");
                    File realFile=node.toPath().toRealPath().toFile(),maxFile=max.toPath().toRealPath().toFile();String real=realFile.getAbsolutePath();
                    if(!real.startsWith(allowed)||!maxFile.getParentFile().equals(realFile.getParentFile()))throw new IOException("not_a_sysfs_device_pair");
                    int ceiling=number(max),actual=number(node);
                    if(ceiling<=10||actual>ceiling)throw new IOException("invalid_brightness_range");
                    row.put("canonical_path",real).put("maximum",ceiling).put("actual",actual).put("explicit_primary",explicit);
                    JSONObject old=eligible.get(real);
                    if(old==null||explicit&&!old.optBoolean("explicit_primary"))eligible.put(real,row);
                }catch(Exception failure){row.put("rejected",String.valueOf(failure.getMessage()));}
            }
        }
        List<JSONObject> explicit=new ArrayList<>();for(JSONObject row:eligible.values())if(row.optBoolean("explicit_primary"))explicit.add(row);
        Collection<JSONObject> pool=explicit.isEmpty()?eligible.values():explicit;
        JSONObject out=new JSONObject().put("supported",false).put("discovery","primary_node_v1").put("candidates",candidates).put("scan_complete",!incomplete);
        if(incomplete)return out.put("reason","scan_incomplete");
        if(pool.size()!=1)return out.put("reason",pool.isEmpty()?"no_primary_node":"ambiguous_primary_nodes");
        JSONObject chosen=pool.iterator().next();
        for(String key:new String[]{"path","canonical_path","maximum","actual","class","name"})out.put(key,chosen.get(key));
        return out.put("supported",true).put("minimum",10).put("reason",explicit.isEmpty()?"unique_backlight":"identified_primary");
    }
}
