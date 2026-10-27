package com.gathin.tvhome;
import android.content.Context;
import java.io.*;
import java.util.*;

final class UsbMusic {
    static List<File> volumes() {
        List<File> result = new ArrayList<>();
        File[] files = new File("/storage").listFiles();
        if (files != null) for (File f : files) if (!f.getName().equals("self") && !f.getName().equals("emulated") && f.isDirectory() && f.canRead()) result.add(f);
        result.sort(Comparator.comparing(File::getName)); return result;
    }
    static File folder(String id) throws IOException {
        for (File root : volumes()) if (root.getName().equals(id)) {
            // TCL also exposes the removable FAT volume here. Only use its mounted,
            // readable counterpart, never a caller-supplied filesystem path.
            List<File> candidates = new ArrayList<>(); candidates.add(root);
            File physical = new File("/mnt/usb", root.getName());
            if (physical.isDirectory() && physical.canRead()) candidates.add(physical);
            for (File candidate : candidates) {
                File folder = new File(candidate, "Music/极简桌面上传");
                if ((folder.isDirectory() || folder.mkdirs()) && folder.canWrite()) return folder;
            }
            throw new IOException("电视未获得 U 盘写入权限，无法创建歌曲文件夹");
        }
        throw new IOException("U 盘已拔出或未识别，请刷新后重试");
    }
    static String safeName(String raw) throws IOException {
        String name = raw.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", "_").trim();
        if (name.length() > 160 || !name.toLowerCase(Locale.ROOT).matches(".+\\.(mp3|flac|wav|m4a|aac|ogg|opus)")) throw new IOException("请选择 MP3 / FLAC / WAV / M4A / AAC / OGG / OPUS 歌曲，文件名限160字");
        return name;
    }
    static synchronized File reserve(File dir, String name) throws IOException {
        int dot = name.lastIndexOf('.');
        for (int n=0; n<10000; n++) {
            File target = new File(dir, n==0 ? name : name.substring(0,dot)+" ("+n+")"+name.substring(dot));
            if (!target.exists()) return target;
        }
        throw new IOException("同名文件过多，请修改文件名");
    }
}
