package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Shared website data, parsed as text only. Keep a validated cache for offline use. */
final class ThanksFeed {
    static final String URL="https://lc.rongshangs.top/thanks.json";
    static JSONObject parse(String raw)throws Exception{
        if(raw==null||raw.length()>65536)throw new IOException("Thanks response too large");
        JSONObject data=new JSONObject(raw);
        if(data.getInt("schema")!=1)throw new IOException("Unsupported thanks format");
        JSONArray entries=data.getJSONArray("entries");
        if(entries.length()>200)throw new IOException("Too many thanks entries");
        for(int i=0;i<entries.length();i++){
            JSONObject entry=entries.getJSONObject(i);String name=entry.getString("name"),message=entry.getString("message");
            if(name.trim().isEmpty()||name.length()>80||message.length()>200||name.contains("\n")||message.contains("\n")||name.contains("\r")||message.contains("\r"))throw new IOException("Invalid thanks entry");
        }
        return data;
    }
    static JSONObject check()throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new java.net.URL(URL).openConnection();
        connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent","HyperLux/"+AppBuild.VERSION);
        try{
            if(connection.getResponseCode()!=200)throw new IOException("Thanks HTTP "+connection.getResponseCode());
            if(!"https".equals(connection.getURL().getProtocol()))throw new IOException("Invalid thanks redirect");
            return parse(read(connection.getInputStream()));
        }finally{connection.disconnect();}
    }
    static String read(InputStream stream)throws IOException{
        try(InputStream input=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1){if(out.size()+count>65536)throw new IOException("Thanks response too large");out.write(buffer,0,count);}
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
}
