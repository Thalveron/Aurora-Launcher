package com.gathin.tvhome;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaScannerConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;

/** Uploads only into a mounted volume's managed music folder. */
final class ConsoleUsb {
    static JSONArray volumes() throws Exception {
        JSONArray result=new JSONArray();
        for(File root:UsbMusic.volumes()) result.put(new JSONObject().put("id",root.getName())
                .put("label","U 盘 "+root.getName()).put("total",root.getTotalSpace()).put("free",root.getUsableSpace()));
        return result;
    }
    static JSONObject upload(Context context, InputStream input, long length, String volume, String name) throws Exception {
        if(length<=0||length>1024L*1024*1024)throw new IllegalArgumentException("歌曲文件大小需在 1 B 至 1 GB 之间");
        String safe=UsbMusic.safeName(name);File folder=UsbMusic.folder(volume);
        if(folder.getUsableSpace()<length+30L*1024*1024)throw new IOException("U 盘剩余空间不足");
        File temp=File.createTempFile("aurora-upload-",".tmp",folder);
        try {
            try(FileOutputStream out=new FileOutputStream(temp)) {
                byte[] buffer=new byte[32768];long left=length;
                while(left>0){int count=input.read(buffer,0,(int)Math.min(left,buffer.length));if(count<0)throw new EOFException("上传中断，未保存歌曲");out.write(buffer,0,count);left-=count;}
                out.getFD().sync();
            }
            MediaExtractor extractor=new MediaExtractor();boolean audio=false;
            try{extractor.setDataSource(temp.getPath());for(int i=0;i<extractor.getTrackCount();i++){String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/"))audio=true;}}finally{extractor.release();}
            if(!audio)throw new IllegalArgumentException("文件不是电视可读取的音频，未保存");
            File saved;
            synchronized(UsbMusic.class){saved=UsbMusic.reserve(folder,safe);if(!temp.renameTo(saved))throw new IOException("U 盘保存失败，可能已拔出或只读");}
            if(saved.length()!=length)throw new IOException("保存后文件大小不一致，请检查 U 盘");
            MediaScannerConnection.scanFile(context,new String[]{saved.getPath()},null,null);
            return new JSONObject().put("volume",volume).put("name",saved.getName()).put("bytes",saved.length()).put("verified",true);
        } finally {if(temp.exists())temp.delete();}
    }
}
