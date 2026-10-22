package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.json.*;

/** Explicit fold-layout route; ordinary primary/LED discovery remains unchanged. */
final class ActivePanelNode {
    static final String PROFILE="fold_default_display";
    interface DisplaySource {JSONObject read()throws Exception;}
    static JSONObject address(Object info)throws Exception {
        if(info==null)throw new IOException("default_display_pending");
        if(info.getClass().getField("type").getInt(info)!=1)throw new IOException("default_display_not_internal");
        String unique=(String)info.getClass().getField("uniqueId").get(info);
        Object address=info.getClass().getField("address").get(info);
        if(address==null)throw new IOException("default_display_address_pending");
        Class<?> shape=address.getClass();String name=shape.getName();
        boolean stable=name.equals("android.view.DisplayAddress$StablePhysical");
        if(!stable&&!name.equals("android.view.DisplayAddress$Physical"))throw new IOException("default_display_not_physical: "+name);
        Method idMethod=shape.getMethod("getPhysicalDisplayId"),portMethod=shape.getMethod("getPort");
        if(idMethod.getReturnType()!=long.class||portMethod.getReturnType()!=int.class||Modifier.isStatic(idMethod.getModifiers())||Modifier.isStatic(portMethod.getModifiers()))throw new IOException("physical_display_address_abi");
        long id=(Long)idMethod.invoke(address);int port=(Integer)portMethod.invoke(address);
        if(id<=0||port<0||port>255||!unique.equals("local:"+id)||!stable&&(id&255)!=port)throw new IOException("physical_display_identity_mismatch");
        return new JSONObject().put("unique_id",unique).put("physical_id",id).put("port",port)
            .put("display_state",info.getClass().getField("state").getInt(info))
            .put("address_profile",stable?"stable_physical":"physical").put("address_class",name);
    }
    static JSONObject display()throws Exception {
        Class<?> cls=Class.forName("android.hardware.display.DisplayManagerGlobal");
        Object global=cls.getMethod("getInstance").invoke(null);
        return address(cls.getMethod("getDisplayInfo",int.class).invoke(global,0)).put("port_source","native_display_address");
    }
    static File layout(){
        for(String base:new String[]{"/odm","/vendor","/product"}){
            File f=new File(base+"/etc/displayconfig/display_layout_configuration.xml");if(f.isFile())return f;
        }
        return null;
    }
    static Set<Long> defaults(File file)throws Exception {
        if(file.length()>131072)throw new IOException("fold_layout_too_large");
        byte[] bytes=Files.readAllBytes(file.toPath());if(bytes.length>131072)throw new IOException("fold_layout_too_large");
        String xml=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        // Reject DTDs before DOM parsing, without relying on parser-specific feature flags.
        if(java.util.regex.Pattern.compile("<!\\s*(DOCTYPE|ENTITY)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xml).find()||xml.indexOf(0)>=0)throw new IOException("fold_layout_dtd_rejected");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setExpandEntityReferences(false);
        Document doc=f.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(xml)));Set<Long> ids=new LinkedHashSet<>();
        NodeList layouts=doc.getElementsByTagName("layout");if(layouts.getLength()>64)throw new IOException("fold_layout_count");
        for(int i=0;i<layouts.getLength();i++){
            NodeList screens=((Element)layouts.item(i)).getElementsByTagName("display");int count=0;
            for(int j=0;j<screens.getLength();j++){
                Element screen=(Element)screens.item(j);if(!"true".equals(screen.getAttribute("defaultDisplay")))continue;
                if(++count!=1||!"true".equals(screen.getAttribute("enabled")))throw new IOException("invalid_fold_default");
                NodeList addresses=screen.getElementsByTagName("address");if(addresses.getLength()!=1)throw new IOException("invalid_fold_address");
                String value=addresses.item(0).getTextContent().trim();if(!value.matches("[0-9]{1,19}"))throw new IOException("invalid_fold_address");
                long id=Long.parseLong(value);if(id<=0)throw new IOException("invalid_fold_address");ids.add(id);
            }
        }
        return ids;
    }
    static JSONObject discover()throws Exception {return discover(new File("/sys"),layout(),ActivePanelNode::display);}
    static String small(File f)throws Exception {if(f.length()>4096)throw new IOException("attribute_too_large");return new String(Files.readAllBytes(f.toPath()),StandardCharsets.US_ASCII).trim();}
    static JSONObject discover(File sys,File layout,DisplaySource source)throws Exception {
        JSONObject legacy=PanelNodeDiscovery.discover(sys);
        if(layout==null)return legacy;
        Set<Long> ids;
        try{ids=defaults(layout);}catch(Exception bad){return new JSONObject().put("supported",false).put("discovery",PROFILE).put("reason","fold_layout_invalid").put("detail",bad.toString());}
        if(ids.size()<2)return legacy; // No evidence that another panel can be the logical default.
        JSONObject out=new JSONObject().put("supported",false).put("discovery",PROFILE).put("reason","fold_binding_pending");
        try{
            JSONObject current=source.read();long physical=current.getLong("physical_id");int port=current.getInt("port");
            if(!ids.contains(physical)||port<0||port>255||!current.getString("unique_id").equals("local:"+physical))throw new IOException("fold_display_not_in_layout");
            // Composer ports can be unrelated to kernel panel ordinals. In that
            // separate route the logical default must itself be on, and exactly
            // one paired internal DSI may be lit. Never subtract a device offset.
            boolean exclusive=port>7;
            if(exclusive&&current.optInt("display_state",-1)!=2)throw new IOException("fold_default_not_on");
            // Stable physical IDs and ports are independent. Enumerate paired
            // kernel ports rather than decoding an inactive display's stable ID.
            File[] backlights=new File(sys,"class/backlight").listFiles();
            if(backlights==null||backlights.length>64)throw new IOException("fold_backlight_scan_pending");
            Set<Integer> kernelPorts=new TreeSet<>();
            for(File backlight:backlights)if(backlight.getName().matches("panel[0-7]-backlight"))kernelPorts.add(backlight.getName().charAt(5)-'0');
            if(kernelPorts.size()!=ids.size()||!exclusive&&!kernelPorts.contains(port))throw new IOException("fold_port_pair_ambiguous");
            File parent=null;JSONObject chosen=null;JSONArray rows=new JSONArray();
            for(int index:kernelPorts){
                File folder=new File(sys,"class/backlight/panel"+index+"-backlight"),node=new File(folder,"brightness"),max=new File(folder,"max_brightness");
                if(!node.isFile()||!max.isFile()||Files.isSymbolicLink(node.toPath())||Files.isSymbolicLink(max.toPath()))throw new IOException("fold_node_pair_missing");
                File real=node.toPath().toRealPath().toFile(),ceiling=max.toPath().toRealPath().toFile();
                if(!real.getPath().startsWith(new File(sys,"devices").getCanonicalPath()+File.separator)||!ceiling.getParentFile().equals(real.getParentFile()))throw new IOException("fold_node_identity");
                File controller=real.getParentFile().getParentFile();if(parent!=null&&!parent.equals(controller))throw new IOException("fold_nodes_not_paired");parent=controller;
                int maximum=PanelNodeDiscovery.number(max),actual=PanelNodeDiscovery.number(node);
                if(maximum<=10||actual>maximum)throw new IOException("fold_node_range");
                JSONObject row=new JSONObject().put("port",index).put("path",node.getAbsolutePath()).put("canonical_path",real.getAbsolutePath()).put("maximum",maximum).put("actual",actual);rows.put(row);
                if(index==port)chosen=row;
            }
            File[] drm=new File(sys,"class/drm").listFiles();if(drm==null||drm.length>64)throw new IOException("fold_drm_pending");
            int active=0,activePort=-1;
            for(File connector:drm){
                String name=connector.getName();if(!name.matches("card[0-9]+-DSI-[0-9]+"))continue;
                boolean on=small(new File(connector,"status")).equals("connected")&&small(new File(connector,"enabled")).equals("enabled")&&small(new File(connector,"dpms")).equals("On");
                if(on){active++;activePort=Integer.parseInt(name.substring(name.lastIndexOf('-')+1))-1;}
            }
            if(active!=1||!kernelPorts.contains(activePort)||!exclusive&&activePort!=port)throw new IOException("fold_screen_not_exclusive_or_on");
            if(exclusive)for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getInt("port")==activePort)chosen=rows.getJSONObject(i);
            if(chosen==null||chosen.getInt("actual")<=0)throw new IOException("fold_selected_node_missing_or_off");
            for(String key:new String[]{"path","canonical_path","maximum","actual"})out.put(key,chosen.get(key));
            // Re-read after sysfs scanning: never publish a mixture of two fold positions.
            JSONObject after=source.read();if(!after.getString("unique_id").equals(current.getString("unique_id"))||after.getLong("physical_id")!=physical||after.getInt("port")!=port||exclusive&&after.optInt("display_state",-1)!=2)throw new IOException("fold_display_changed_during_scan");
            return out.put("supported",true).put("class","backlight").put("name","panel"+activePort+"-backlight").put("minimum",10).put("fold_physical_ids",new JSONArray(ids))
                .put("address_profile",current.optString("address_profile")).put("port_source",current.optString("port_source"))
                .put("node_binding",exclusive?"exclusive_internal_dsi":"native_port_pair").put("physical_port",port).put("fold_port",activePort).put("physical_display_id",current.getString("unique_id")).put("layout_source",layout.getAbsolutePath()).put("candidates",rows).put("reason","active_fold_default");
        }catch(Exception pending){return out.put("detail",pending.toString());}
    }
}
