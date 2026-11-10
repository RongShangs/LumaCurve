package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Read the device's dedicated game categories; mixed image lists are never video evidence. */
final class ApplicationCategories {
    // Exact package IDs supplement publishers that omit Android's video category.
    // Used only for installed picker entries; never grant brightness control by name.
    private static final Set<String> VIDEO_PACKAGES=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "tv.danmaku.bili","tv.danmaku.bilibilihd","com.bilibili.app.in","com.bilibili.app.blue",
        "com.tencent.qqlive","com.tencent.qqlivehd","com.qiyi.video","com.qiyi.video.pad",
        "com.qiyi.video.lite","com.youku.phone","com.youku.tv","com.hunantv.imgo.activity",
        "com.ss.android.ugc.aweme","com.ss.android.ugc.aweme.lite","com.smile.gifmaker",
        "com.kuaishou.nebula","com.ss.android.article.video","com.sohu.sohuvideo",
        "com.pplive.androidphone","com.cmcc.cmvideo","tv.acfundanmaku.video",
        "com.baidu.haokan","com.xunlei.kankan","com.kmxs.video",
        "com.mxtech.videoplayer.ad","com.mxtech.videoplayer.pro","org.videolan.vlc"
    )));
    static boolean video(String pkg,boolean declared){return declared||VIDEO_PACKAGES.contains(pkg);}
    static Set<String> games()throws Exception{
        for(String base:new String[]{"/product","/system/product","/vendor","/odm"}){
            File file=new File(base+"/etc/displayconfig/game_app_category_list_backup.xml");
            if(file.isFile())return games(file);
        }
        return Collections.emptySet();
    }
    static Set<String> games(File file)throws Exception{
        if(file.length()>262144)throw new IOException("应用分类文件过大");byte[] bytes=Files.readAllBytes(file.toPath());if(bytes.length>262144)throw new IOException("应用分类文件过大");
        String xml=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        if(java.util.regex.Pattern.compile("<!\\s*(DOCTYPE|ENTITY)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xml).find()||xml.indexOf(0)>=0)throw new IOException("应用分类文件格式无效");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setExpandEntityReferences(false);
        Document document=factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(xml)));
        if(!"display-config".equals(document.getDocumentElement().getTagName()))throw new IOException("应用分类根节点无效");
        NodeList lists=document.getElementsByTagName("game_app_category_list");if(lists.getLength()!=1)throw new IOException("游戏分类结构无效");Set<String> out=new LinkedHashSet<>();
        NodeList groups=lists.item(0).getChildNodes();
        for(int i=0;i<groups.getLength();i++){
            Node group=groups.item(i);if(group.getNodeType()!=Node.ELEMENT_NODE)continue;
            if(!group.getNodeName().equals("game_app_category_emotion")&&!group.getNodeName().equals("game_app_category_night"))throw new IOException("未知游戏分类结构");
            NodeList entries=group.getChildNodes();for(int j=0;j<entries.getLength();j++){
                Node entry=entries.item(j);if(entry.getNodeType()!=Node.ELEMENT_NODE)continue;
                if(!entry.getNodeName().equals("item")||out.size()>=4096)throw new IOException("游戏分类条目无效");String pkg=entry.getTextContent().trim();
                if(!BrightnessControlOptions.validPackage(pkg))throw new IOException("游戏分类包名无效");out.add(pkg);
            }
        }
        return out;
    }
}
