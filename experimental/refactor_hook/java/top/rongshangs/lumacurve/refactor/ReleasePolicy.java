package top.rongshangs.lumacurve.refactor;

import java.net.URI;

/** Only release APKs from this repository can become app updates. */
public final class ReleasePolicy {
    private static int[] parts(String version){
        if(version==null||!version.matches("[vV]?[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}"))throw new IllegalArgumentException("Invalid release version");
        String[] values=version.replaceFirst("^[vV]","").split("\\.");int[] parts=new int[3];
        for(int i=0;i<3;i++)parts[i]=Integer.parseInt(values[i]);return parts;
    }
    public static int compare(String first,String second){int[] a=parts(first),b=parts(second);for(int i=0;i<3;i++)if(a[i]!=b[i])return Integer.compare(a[i],b[i]);return 0;}
    public static boolean shouldOffer(String version,String ignored,boolean manual){
        try{parts(version);}catch(IllegalArgumentException invalid){return false;}
        if(manual)return true;
        try{return compare(version,ignored)!=0;}catch(IllegalArgumentException absent){return true;}
    }
    public static boolean trustedApk(String version,String name,String url){
        try{parts(version);String normalized=version.replaceFirst("^[vV]","");
            if(!("HyperLux-"+normalized+".apk").equals(name)&&!("LumaCurve-"+normalized+".apk").equals(name))return false;
            URI uri=new URI(url);String path=uri.getPath();return "https".equals(uri.getScheme())&&"github.com".equals(uri.getHost())&&uri.getUserInfo()==null&&uri.getPort()==-1&&uri.getQuery()==null&&uri.getFragment()==null
                &&(path.equals("/RongShangs/LumaCurve/releases/download/v"+normalized+"/"+name)||path.equals("/RongShangs/LumaCurve/releases/download/"+normalized+"/"+name));
        }catch(Exception invalid){return false;}
    }
}
