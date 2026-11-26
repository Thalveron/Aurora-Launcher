package com.gathin.tvhome;

import android.content.*;
import android.os.*;
import org.json.*;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.support.v4.media.MediaBrowserCompat;

final class WebSongLibrary {
    private static final String PLAYER="com.craftworks.music.tvcustom";
    static synchronized JSONObject list(Context c) throws Exception {
        String request=UUID.randomUUID().toString();
        JSONArray songs=new JSONArray();
        CountDownLatch done=new CountDownLatch(1);
        Handler main=new Handler(Looper.getMainLooper());
        String[] error={null};
        BroadcastReceiver pages=new BroadcastReceiver(){
            public void onReceive(Context ctx,Intent page){
                if(!request.equals(page.getStringExtra("request")))return;
                try {
                    if(page.getBooleanExtra("error",false))throw new Exception();
                    String[] titles=page.getStringArrayExtra("titles"),artists=page.getStringArrayExtra("artists"),ids=page.getStringArrayExtra("ids");
                    if(page.getIntExtra("offset",-1)!=songs.length()||titles==null||artists==null||ids==null||titles.length!=artists.length||ids.length!=titles.length)throw new Exception();
                    for(int i=0;i<titles.length;i++)songs.put(new JSONObject().put("title",titles[i]).put("artist",artists[i]).put("index",Integer.parseInt(ids[i])));
                    if(songs.length()==page.getIntExtra("total",-1))done.countDown();
                }catch(Exception e){error[0]="读取歌曲列表失败";done.countDown();}
            }
        };
        IntentFilter filter=new IntentFilter("com.gathin.tvhome.DESKTOP_LIBRARY");
        String permission="com.gathin.tvhome.permission.DESKTOP_LYRICS";
        if(Build.VERSION.SDK_INT>=33)c.registerReceiver(pages,filter,permission,main,Context.RECEIVER_EXPORTED);
        else c.registerReceiver(pages,filter,permission,main);
        MediaBrowserCompat[] browser={null};
        main.post(()->{
            browser[0]=new MediaBrowserCompat(c,new ComponentName(PLAYER,"com.craftworks.music.player.ChoraMediaLibraryService"),new MediaBrowserCompat.ConnectionCallback(){
                public void onConnected(){c.sendBroadcast(new Intent(PLAYER+".DESKTOP_LIBRARY_REQUEST").setPackage(PLAYER).putExtra("request",request));}
                public void onConnectionFailed(){error[0]="无法连接音乐应用";done.countDown();}
            },null);
            browser[0].connect();
        });
        try {
            if(!done.await(15,TimeUnit.SECONDS))throw new IllegalStateException("音乐库响应超时");
            if(error[0]!=null)throw new IllegalStateException(error[0]);
            return new JSONObject().put("request",request).put("songs",songs);
        }finally{c.unregisterReceiver(pages);main.post(()->{if(browser[0]!=null)browser[0].disconnect();});}
    }
    static JSONObject play(Context c,JSONObject body)throws Exception {
        c.sendBroadcast(new Intent(PLAYER+".DESKTOP_PLAY_INDEX").setPackage(PLAYER).putExtra("request",body.getString("request")).putExtra("index",body.getInt("index")));
        return new JSONObject().put("requested",true);
    }
}
