package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** APK-only updates from the existing project. A KSU ZIP is never offered as an app update. */
final class UpdateChecker {
    static final String VERSION=AppBuild.VERSION,WEBSITE="https://lc.rongshangs.top",REPO="https://github.com/RongShangs/LumaCurve",API="https://api.github.com/repos/RongShangs/LumaCurve/releases/latest";
    static JSONObject check()throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(API).openConnection();connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("User-Agent","HyperLux/"+VERSION);
        try{
            if(connection.getResponseCode()!=200)throw new IOException("GitHub HTTP "+connection.getResponseCode());
            ByteArrayOutputStream data=new ByteArrayOutputStream();try(InputStream input=connection.getInputStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(data.size()+count>1024*1024)throw new IOException("Release response too large");data.write(buffer,0,count);}}
            JSONObject release=new JSONObject(new String(data.toByteArray(),StandardCharsets.UTF_8));
            if(release.optBoolean("draft")||release.optBoolean("prerelease"))return new JSONObject().put("newer",false);
            String tag=release.getString("tag_name");if(!tag.matches("[vV]?\\d+\\.\\d+\\.\\d+")||ReleasePolicy.compare(tag,VERSION)<0||(ReleasePolicy.compare(tag,VERSION)==0&&!AppBuild.TEST&&!AppBuild.INTERNAL))return new JSONObject().put("newer",false);
            JSONArray assets=release.optJSONArray("assets");if(assets!=null)for(int i=0;i<assets.length();i++){
                JSONObject asset=assets.getJSONObject(i);String name=asset.optString("name"),url=asset.optString("browser_download_url");
                if(!ReleasePolicy.trustedApk(tag,name,url))continue;
                return new JSONObject().put("newer",true).put("version",tag.replaceFirst("^[vV]","")).put("url",url).put("notes",release.optString("body")).put("name",name);
            }
            return new JSONObject().put("newer",false);
        }finally{connection.disconnect();}
    }
}
