import java.io.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Capability-based default-display discovery. No brightness or Settings writes. */
public final class LumaFrameworkDeviceProfile {
    final String uniqueId, name;
    final File backlight;
    final int maximum;
    final float codesPerFloat;
    final boolean calibrated;
    final Object global;
    static int integer(File file)throws Exception {
        try(BufferedReader reader=new BufferedReader(new FileReader(file))){return Integer.parseInt(reader.readLine().trim());}
    }
    static int validNode(File folder) {
        try {
            int max=integer(new File(folder,"max_brightness")),current=integer(new File(folder,"brightness"));
            return max>0&&max<=1000000&&current>=0&&current<=max?max:0;
        }catch(Exception error){return 0;}
    }
    static File selectBacklight(File root)throws Exception {
        File primary=new File(root,"panel0-backlight");
        if(validNode(primary)>0)return primary;
        File[] folders=root.listFiles();
        if(folders==null||folders.length>256)throw new IllegalStateException("backlight directory unavailable");
        File selected=null;
        for(File folder:folders)if(validNode(folder)>0){
            if(selected!=null)throw new IllegalStateException("ambiguous backlight nodes");
            selected=folder;
        }
        if(selected==null)throw new IllegalStateException("readable backlight missing");
        return selected;
    }
    static void normalizedConfig(File file)throws Exception {
        if(!file.isFile()||file.length()>2000000)throw new IllegalStateException("display configuration unavailable");
        byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());
        String xml=new String(bytes,"UTF-8");
        if(bytes.length>2000000||xml.toUpperCase(java.util.Locale.ROOT).contains("<!DOCTYPE")||
           xml.toUpperCase(java.util.Locale.ROOT).contains("<!ENTITY"))throw new IllegalStateException("external XML entities forbidden");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        Document document=factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        NodeList maps=document.getElementsByTagName("screenBrightnessMap");
        if(maps.getLength()!=1)throw new IllegalStateException("brightness mapping missing or ambiguous");
        NodeList points=((Element)maps.item(0)).getElementsByTagName("point");
        if(points.getLength()<2||points.getLength()>512)throw new IllegalStateException("invalid brightness mapping size");
        float previous=-1,previousNits=-1;
        for(int index=0;index<points.getLength();index++) {
            Element point=(Element)points.item(index);
            NodeList values=point.getElementsByTagName("value"),nits=point.getElementsByTagName("nits");
            if(values.getLength()!=1||nits.getLength()!=1)throw new IllegalStateException("invalid mapping point");
            float value=Float.parseFloat(values.item(0).getTextContent());
            float nit=Float.parseFloat(nits.item(0).getTextContent());
            if(!Float.isFinite(value)||!Float.isFinite(nit)||value<0||value>1||value<=previous||nit<0||nit<=previousNits)
                throw new IllegalStateException("unsupported/nonmonotonic brightness mapping");
            previous=value;previousNits=nit;
        }
        if(Math.abs(previous-1f)>.00001f)throw new IllegalStateException("mapping maximum is not normalized");
    }
    static File config(String id)throws Exception {
        if(id==null||!id.matches("local:[0-9]{1,20}"))throw new IllegalStateException("unsupported physical display identifier");
        for(String root:new String[]{"/product/etc/displayconfig","/vendor/etc/displayconfig","/system_ext/etc/displayconfig","/system/etc/displayconfig"}) {
            File file=new File(root,"display_id_"+id.substring(6)+".xml");
            if(file.isFile())return file;
        }
        throw new IllegalStateException("per-display brightness configuration missing");
    }
    static boolean internal(int type,String id){return type==1&&id!=null&&id.matches("local:[0-9]{1,20}");}
    static float scale(String id,int maximum) {
        return maximum;
    }
    static String sha(File file)throws Exception {
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(file)){byte[] bytes=new byte[16384];int n;while((n=input.read(bytes))>0)digest.update(bytes,0,n);}
        StringBuilder text=new StringBuilder();for(byte value:digest.digest())text.append(String.format("%02x",value&255));return text.toString();
    }
    static boolean measuredEvidence(String id,int max,String framework,String services) {
        return "local:4630946949513469331".equals(id)&&max==16383&&
          "1d2bf53f6c2684103dadbeef0d2665a7f033b7746a75f3e7404145dd999600fd".equals(framework)&&
          "ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20".equals(services);
    }
    static boolean plausible(int node,float adjusted,int maximum,float scale,boolean calibrated) {
        if(node<1||node>maximum||!Float.isFinite(adjusted)||adjusted<=0||adjusted>1)return false;
        if(calibrated)return LumaLegacyBacklightCoordinate.plausibleAnchor(node,adjusted);
        // A normalized config is a nominal mapping, not proof of a panel's transfer
        // function. Refuse obviously different coordinates instead of learning a
        // full-range conversion from a single sample.
        return Math.abs(node-adjusted*scale)<=Math.max(48f,adjusted*scale*.15f);
    }
    LumaFrameworkDeviceProfile()throws Exception {
        Class<?> properties=Class.forName("android.os.SystemProperties");
        String os=(String)LumaFrameworkProbe.invoke(properties,"get",new Class<?>[]{String.class},"ro.mi.os.version.name");
        if(!os.startsWith("OS4"))os=(String)LumaFrameworkProbe.invoke(properties,"get",new Class<?>[]{String.class},"ro.build.version.incremental");
        if(!os.startsWith("OS4"))throw new IllegalStateException("this capability adapter targets HyperOS 4");
        Class<?> cls=Class.forName("android.hardware.display.DisplayManagerGlobal");
        cls.getMethod("setTemporaryBrightness",int.class,float.class);
        cls.getMethod("getBrightnessInfo",int.class);
        global=LumaFrameworkProbe.invoke(cls,"getInstance",new Class<?>[0]);
        if(global==null)throw new IllegalStateException("display service unavailable");
        Object display=LumaFrameworkProbe.invoke(global,"getDisplayInfo",new Class<?>[]{int.class},0);
        if(display==null)throw new IllegalStateException("default display unavailable");
        uniqueId=(String)display.getClass().getField("uniqueId").get(display);
        if(!internal(display.getClass().getField("type").getInt(display),uniqueId))
            throw new IllegalStateException("default display is not a physical internal panel");
        backlight=selectBacklight(new File("/sys/class/backlight"));maximum=validNode(backlight);
        normalizedConfig(config(uniqueId));
        calibrated=measuredEvidence(uniqueId,maximum,sha(new File("/system/framework/framework.jar")),sha(new File("/system/framework/services.jar")));
        codesPerFloat=calibrated?17848f:scale(uniqueId,maximum);
        name=calibrated?"measured_legacy_panel":"normalized_config_candidate";
        Class<?> utils=Class.forName("com.android.internal.display.BrightnessUtils");
        for(float value:new float[]{.001f,.01f,.1f,.5f,1f}) {
            float gamma=((Number)LumaFrameworkProbe.invoke(utils,"convertLinearToGamma",new Class<?>[]{float.class},value)).floatValue();
            float linear=((Number)LumaFrameworkProbe.invoke(utils,"convertGammaToLinear",new Class<?>[]{float.class},gamma)).floatValue();
            if(!Float.isFinite(gamma)||!Float.isFinite(linear)||Math.abs(value-linear)>.00001f)
                throw new IllegalStateException("brightness coordinate roundtrip failed");
        }
    }
    void check(Object display)throws Exception {
        if(display==null||!uniqueId.equals(display.getClass().getField("uniqueId").get(display))||
           !internal(display.getClass().getField("type").getInt(display),uniqueId))
            throw new IllegalStateException("display or backlight changed; rediscovery required");
    }
    int node()throws Exception{return integer(new File(backlight,"brightness"));}
    boolean plausible(int node,float adjusted){return plausible(node,adjusted,maximum,codesPerFloat,calibrated);}
    float convert(int raw,int max) {
        if(max!=maximum||raw<0||raw>max)throw new IllegalArgumentException("raw goal does not match discovered backlight");
        return raw/codesPerFloat;
    }
    static boolean temporaryClear(BufferedReader reader)throws Exception {
        boolean main=false,id=false,clear=false;String line;
        while((line=reader.readLine())!=null){
            if(line.equals("Display Power Controller:")){if(main)break;main=true;continue;}
            if(main){if(line.contains("mDisplayId=0"))id=true;if(line.contains("mTemporaryScreenBrightness:NaN"))clear=true;}
        }
        return id&&clear;
    }
    static void verifyRelease()throws Exception {
        File dump=File.createTempFile("luma-preflight-release-",".txt",new File("/data/local/tmp"));
        try {
            Process process=new ProcessBuilder("/system/bin/dumpsys","display").redirectErrorStream(true).redirectOutput(dump).start();
            if(!process.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("display release inspection timed out");}
            if(process.exitValue()!=0)throw new IllegalStateException("display release inspection failed");
            try(BufferedReader reader=new BufferedReader(new FileReader(dump))){
                if(!temporaryClear(reader))throw new IllegalStateException("temporary brightness still owned; old backend has not released display");
            }
        } finally {dump.delete();}
    }
    public static void main(String[] args) {
        try {
            if(args.length!=1||!"preflight".equals(args[0]))throw new IllegalArgumentException("preflight only");
            LumaFrameworkDeviceProfile profile=new LumaFrameworkDeviceProfile();
            Object display=LumaFrameworkProbe.invoke(profile.global,"getDisplayInfo",new Class<?>[]{int.class},0);
            profile.check(display);
            if(display.getClass().getField("state").getInt(display)!=2)throw new IllegalStateException("main screen must be ON");
            Object info=LumaFrameworkProbe.invoke(profile.global,"getBrightnessInfo",new Class<?>[]{int.class},0);
            float min=LumaFrameworkProbe.floatField(info,"brightnessMinimum"),max=LumaFrameworkProbe.floatField(info,"brightnessMaximum");
            float adjusted=LumaFrameworkProbe.floatField(info,"adjustedBrightness");
            if(!Float.isFinite(min)||!Float.isFinite(max)||min<0||max>1||min>=max||
               info.getClass().getField("isBrightnessOverrideByWindow").getBoolean(info))throw new IllegalStateException("invalid range/window override");
            LumaFrameworkProbeSettings.readSettings(true);
            verifyRelease();
            int consecutive=0;
            for(int attempt=0;attempt<20;attempt++) {
                info=LumaFrameworkProbe.invoke(profile.global,"getBrightnessInfo",new Class<?>[]{int.class},0);
                display=LumaFrameworkProbe.invoke(profile.global,"getDisplayInfo",new Class<?>[]{int.class},0);profile.check(display);
                if(display.getClass().getField("state").getInt(display)!=2 || info.getClass().getField("isBrightnessOverrideByWindow").getBoolean(info))
                    throw new IllegalStateException("display conditions changed during preflight");
                adjusted=LumaFrameworkProbe.floatField(info,"adjustedBrightness");
                int node=profile.node();
                if(profile.plausible(node,adjusted))consecutive++;else consecutive=0;
                if(consecutive>=3)break;
                Thread.sleep(250);
            }
            if(consecutive<3)throw new IllegalStateException("coordinate did not stabilize; profile="+profile.name+" node="+profile.node()+" adjusted="+adjusted+" scale="+profile.codesPerFloat);
            System.out.println("MAIN_DISPLAY verified");
            System.out.println("PROFILE name="+profile.name+" id="+profile.uniqueId+" maximum="+profile.maximum+" codes_per_float="+profile.codesPerFloat);
            System.out.println("BACKLIGHT path="+new File(profile.backlight,"brightness"));
            System.out.println("PREFLIGHT ok brightness="+adjusted+" min="+min+" max="+max);
            System.out.println("actual_output=unverified; runtime feedback guard required");
        }catch(Throwable error){error.printStackTrace();System.exit(2);}
    }
}
