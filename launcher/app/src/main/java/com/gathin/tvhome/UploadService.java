package com.gathin.tvhome;
import android.app.*;
import android.content.*;
import android.os.*;

public final class UploadService extends Service {
    private static volatile UploadService running;
    private volatile BackgroundUploadServer server;
    private volatile boolean closed;
    private final BroadcastReceiver lyrics = new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){AuroraData.lyrics(intent);}
    };

    static void ensureStarted(Context c) {
        if(running!=null&&!running.closed)return;
        try { c.startForegroundService(new Intent(c, UploadService.class)); }
        catch (RuntimeException e) { android.util.Log.w("TvHome", "Upload service unavailable", e); }
    }
    @Override public void onCreate() {
        super.onCreate();
        foreground();running=this;
        AuroraHttp.initialize(getApplicationContext());
        IdleStandby.start(this);
        NetworkTraffic.start(this);
        IntentFilter lyricFilter=new IntentFilter("com.gathin.tvhome.DESKTOP_LYRICS");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(lyrics,lyricFilter,"com.gathin.tvhome.permission.DESKTOP_LYRICS",null,Context.RECEIVER_EXPORTED);
        else registerReceiver(lyrics,lyricFilter,"com.gathin.tvhome.permission.DESKTOP_LYRICS",null);

        new Thread(() -> {
            BridgePreview.sync(this);
            try {
                BackgroundUploadServer created = new BackgroundUploadServer(this, kind -> {
                    sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(getPackageName()).putExtra("kind", kind));
                });
                server = created;
                if (closed) created.close();
            } catch (Exception e) { android.util.Log.w("TvHome", "Upload server unavailable", e); stopSelf(); }
        }, "upload-start").start();
    }
    static void reconfigure(Context c,int oldPort){
        UploadService service=running;if(service==null)return;
        new Handler(Looper.getMainLooper()).postDelayed(()->new Thread(()->{
            synchronized(service){
                if(service.closed)return;
                if(service.server!=null)service.server.close();
                try{service.server=service.newServer();}
                catch(Exception failure){
                    UploadSecurity.prefs(service).edit().putInt("port",oldPort).commit();
                    android.util.Log.w("TvHome","Console port rolled back",failure);
                    WebAudit.record(service,"127.0.0.1","后台端口回退","恢复端口 "+oldPort,503);
                    try{service.server=service.newServer();}catch(Exception unavailable){android.util.Log.e("TvHome","Console unavailable",unavailable);}
                }
            }
        },"console-rebind").start(),1000);
    }
    private BackgroundUploadServer newServer()throws java.io.IOException{
        return new BackgroundUploadServer(this,kind->sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(getPackageName()).putExtra("kind",kind)));
    }
    private void foreground(){
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("lan_upload", "局域网上传", NotificationManager.IMPORTANCE_LOW));
        startForeground(8899, new Notification.Builder(this, "lan_upload").setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Aurora Launcher 控制台").setContentText("局域网访问需要登录").setOngoing(true).build());
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) { foreground();return START_STICKY; }
    @Override public void onDestroy() { closed = true;if(running==this)running=null;unregisterReceiver(lyrics); if (server != null) server.close(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
