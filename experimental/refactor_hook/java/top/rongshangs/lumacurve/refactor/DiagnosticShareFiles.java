package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.nio.file.*;

/** Narrow, shared file boundary for an explicitly exported diagnostic copy. */
final class DiagnosticShareFiles {
    static boolean name(String name){return name!=null&&name.matches("LumaCurve-analysis-[0-9]{8}-[0-9]{6}-[0-9a-f]{6}\\.zip");}
    static boolean directoryPath(String path){return path!=null&&path.matches("/storage/emulated/[0-9]+/Android/data/top\\.rongshangs\\.lumacurve/cache/diagnostic-shares");}
    static File directory(String path)throws IOException{
        File dir=new File(path).getCanonicalFile();
        if(!directoryPath(dir.getPath()))throw new IOException("分享目录不匹配");
        if(!dir.isDirectory())throw new IOException("分享目录不可用");return dir;
    }
    static File file(File directory,String name)throws IOException{
        if(!name(name))throw new IOException("不是本次诊断压缩包");File target=new File(directory,name).getCanonicalFile();if(!directory.getCanonicalFile().equals(target.getParentFile()))throw new IOException("分享文件不匹配");return target;
    }
    static void copy(File source,File dir)throws IOException{
        File target=file(dir,source.getName());Path partial=Files.createTempFile(dir.toPath(),"share-",".partial");
        try{Files.copy(source.toPath(),partial,StandardCopyOption.REPLACE_EXISTING);Files.move(partial,target.toPath());}finally{Files.deleteIfExists(partial);}
    }
}
