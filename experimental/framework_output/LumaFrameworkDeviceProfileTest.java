import java.io.*;
import java.nio.file.*;
public final class LumaFrameworkDeviceProfileTest {
    static void require(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        require(LumaFrameworkDeviceProfile.internal(1,"local:4630946639341352083"));
        require(!LumaFrameworkDeviceProfile.internal(2,"local:123"));
        require(!LumaFrameworkDeviceProfile.internal(1,"virtual:123"));
        require(LumaFrameworkDeviceProfile.scale("local:4630946949513469331",16383)==16383);
        require(!LumaFrameworkDeviceProfile.measuredEvidence("local:4630946949513469331",16383,"changed","changed"));
        require(LumaFrameworkDeviceProfile.scale("local:4630946639341352083",16383)==16383);
        require(LumaFrameworkDeviceProfile.scale("local:123",4095)==4095);
        require(LumaFrameworkDeviceProfile.plausible(1244,.07565393f,16383,16383,false));
        require(!LumaFrameworkDeviceProfile.plausible(8000,.07565393f,16383,16383,false));
        require(!LumaFrameworkDeviceProfile.plausible(1244,Float.NaN,16383,16383,false));
        File root=Files.createTempDirectory("luma-profile-").toFile();
        try {
            File node=new File(root,"other-panel");node.mkdir();
            Files.write(new File(node,"brightness").toPath(),"1244".getBytes("UTF-8"));
            Files.write(new File(node,"max_brightness").toPath(),"16383".getBytes("UTF-8"));
            require(LumaFrameworkDeviceProfile.selectBacklight(root).equals(node));
            File second=new File(root,"second-panel");second.mkdir();
            Files.write(new File(second,"brightness").toPath(),"12".getBytes("UTF-8"));
            Files.write(new File(second,"max_brightness").toPath(),"4095".getBytes("UTF-8"));
            boolean failed=false;try{LumaFrameworkDeviceProfile.selectBacklight(root);}catch(Exception error){failed=true;}
            require(failed);
            File xml=new File(root,"map.xml");
            String valid="<displayConfiguration><screenBrightnessMap><point><value>0.000366256</value><nits>1</nits></point><point><value>1</value><nits>3500</nits></point></screenBrightnessMap></displayConfiguration>";
            for(String text:new String[]{valid,valid.replace("<value>1</value>","<value>0</value>"),valid.replace("<value>1</value>","<value>0.5</value>"),"<!DOCTYPE root>"+valid}) {
                Files.write(xml.toPath(),text.getBytes("UTF-8"));failed=false;
                try{LumaFrameworkDeviceProfile.normalizedConfig(xml);}catch(Exception error){failed=true;}
                require(failed!=text.equals(valid));
            }
        }finally{
            Files.walk(root.toPath()).sorted(java.util.Comparator.reverseOrder()).forEach(path->{try{Files.delete(path);}catch(IOException error){throw new RuntimeException(error);}});
        }
        if(args.length==1)LumaFrameworkDeviceProfile.normalizedConfig(new File(args[0]));
        System.out.println("Device profile: discovery, arbitrary IDs/ranges, ambiguity, live mismatch and invalid maps PASS");
    }
}
