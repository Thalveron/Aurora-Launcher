package com.gathin.tvhome;

import android.app.*;
import android.content.*;
import android.os.*;
import android.support.v4.media.MediaBrowserCompat;
import android.widget.*;
import java.util.*;

/** Fetches titles through the signed Chora bridge; no server credentials leave the player. */
final class DesktopSongList {
    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<String> labels=new ArrayList<>();
    private final ArrayList<Integer> indices=new ArrayList<>();
    private final ArrayAdapter<String> adapter;
    private final AlertDialog dialog;
    private MediaBrowserCompat browser;
    private String request;
    private int expected;
    private boolean closed;
    private final Runnable timeout=this::timedOut;
    private void timedOut(){if(!closed)dialog.setTitle(DesktopLanguage.t(activity,"歌曲加载超时 · 请刷新"));}
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(closed||!Objects.equals(request,i.getStringExtra("request")))return;
        if(i.getBooleanExtra("error",false)){handler.removeCallbacks(timeout);dialog.setTitle(DesktopLanguage.t(activity,"读取失败 · 请检查音乐服务器连接"));return;}
        String[] titles=i.getStringArrayExtra("titles"),artists=i.getStringArrayExtra("artists"),ids=i.getStringArrayExtra("ids");
        int offset=i.getIntExtra("offset",-1);expected=i.getIntExtra("total",0);
        if(titles==null||artists==null||ids==null||titles.length!=artists.length||titles.length!=ids.length||offset<0)return;
        if(offset==0){labels.clear();indices.clear();}
        if(offset!=labels.size())return;
        for(int n=0;n<titles.length;n++){try{indices.add(Integer.parseInt(ids[n]));labels.add(titles[n]+(artists[n].isEmpty()?"":"  ·  "+artists[n]));}catch(NumberFormatException ignored){}}
        adapter.notifyDataSetChanged();dialog.setTitle(DesktopLanguage.t(activity,"全部歌曲（")+labels.size()+" / "+expected+DesktopLanguage.t(activity,"首）"));
        if(labels.size()>=expected){handler.removeCallbacks(timeout);dialog.setTitle(DesktopLanguage.t(activity,"全部歌曲（")+expected+DesktopLanguage.t(activity,"首）"));if(expected==0)dialog.setTitle(DesktopLanguage.t(activity,"全部歌曲（0首）· 可刷新重试"));}
    }};
    DesktopSongList(Activity a){
        activity=a;adapter=new ArrayAdapter<>(a,android.R.layout.simple_list_item_1,labels);
        ListView list=new ListView(a);list.setAdapter(adapter);list.setFocusable(true);
        dialog=new AlertDialog.Builder(a).setTitle(DesktopLanguage.t(activity,"正在连接 Chora…")).setView(list).setNegativeButton(DesktopLanguage.t(activity,"关闭"),null).setNeutralButton(DesktopLanguage.t(activity,"刷新"),null).create();
        list.setOnItemClickListener((parent,v,position,id)->{if(position<indices.size()){activity.sendBroadcast(new Intent("com.craftworks.music.tvcustom.DESKTOP_PLAY_INDEX").setPackage("com.craftworks.music.tvcustom").putExtra("index",indices.get(position)).putExtra("request",request));dialog.dismiss();}});
        IntentFilter filter=new IntentFilter("com.gathin.tvhome.DESKTOP_LIBRARY");
        if(Build.VERSION.SDK_INT>=33)a.registerReceiver(receiver,filter,"com.gathin.tvhome.permission.DESKTOP_LYRICS",handler,Context.RECEIVER_EXPORTED);else a.registerReceiver(receiver,filter,"com.gathin.tvhome.permission.DESKTOP_LYRICS",handler);
        dialog.setOnDismissListener(d->{closed=true;handler.removeCallbacks(timeout);a.unregisterReceiver(receiver);if(browser!=null)browser.disconnect();});
        dialog.show();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->load());
        browser=new MediaBrowserCompat(a,new ComponentName("com.craftworks.music.tvcustom","com.craftworks.music.player.ChoraMediaLibraryService"),new MediaBrowserCompat.ConnectionCallback(){
            @Override public void onConnected(){if(!closed)load();}
            @Override public void onConnectionFailed(){if(!closed){handler.removeCallbacks(timeout);dialog.setTitle(DesktopLanguage.t(activity,"无法连接 Chora · 请先打开播放器"));}}
        },null);browser.connect();handler.postDelayed(timeout,15000);
    }
    private void load(){if(closed)return;if(browser==null||!browser.isConnected()){dialog.setTitle(DesktopLanguage.t(activity,"正在连接 Chora…"));return;}request=UUID.randomUUID().toString();labels.clear();indices.clear();adapter.notifyDataSetChanged();dialog.setTitle(DesktopLanguage.t(activity,"正在加载全部歌曲…"));handler.removeCallbacks(timeout);handler.postDelayed(timeout,20000);activity.sendBroadcast(new Intent("com.craftworks.music.tvcustom.DESKTOP_LIBRARY_REQUEST").setPackage("com.craftworks.music.tvcustom").putExtra("request",request));}
}
