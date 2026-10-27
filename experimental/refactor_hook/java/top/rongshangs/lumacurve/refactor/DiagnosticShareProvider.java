package top.rongshangs.lumacurve.refactor;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

/** Read-only content grants for individual diagnostic ZIPs; no general storage access. */
public final class DiagnosticShareProvider extends ContentProvider {
    File resolve(Uri uri)throws FileNotFoundException{try{List<String> parts=uri.getPathSegments();if(parts.size()!=2||!parts.get(0).equals("zip"))throw new IOException("分享地址无效");File dir=DiagnosticSharing.directory(getContext());File file=DiagnosticShareFiles.file(dir,parts.get(1));if(!file.isFile())throw new IOException("分享文件不存在");return file;}catch(IOException error){throw new FileNotFoundException(error.getMessage());}}
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "application/zip";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("分享文件只读");return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){try{File file=resolve(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns,1);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?file.getName():OpenableColumns.SIZE.equals(columns[i])?file.length():null;cursor.addRow(row);return cursor;}catch(FileNotFoundException missing){return null;}}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("只读分享");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("只读分享");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("只读分享");}
}
