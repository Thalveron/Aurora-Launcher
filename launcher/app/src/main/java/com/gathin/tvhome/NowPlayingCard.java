package com.gathin.tvhome;

import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaSessionManager;
import android.os.*;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.List;

/** A remote-friendly, session-driven music card. It never binds to or starts Aurora automatically. */
final class NowPlayingCard {
    private static java.lang.ref.WeakReference<NowPlayingCard> latest=new java.lang.ref.WeakReference<>(null);
    static org.json.JSONObject diagnostics()throws Exception{
        NowPlayingCard c=latest.get();if(c==null||(c.activity.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)throw new UnsupportedOperationException("仅限开发验收版");
        return AuroraBgm.onMain(c.activity,()->{int[] xy=new int[2];c.row.getLocationOnScreen(xy);return c.spectrum.diagnostics().put("cardX",xy[0]).put("cardY",xy[1]).put("cardWidth",c.row.getWidth()).put("cardHeight",c.row.getHeight()).put("stageHeight",c.stage.getHeight()).put("cardVisible",c.row.getVisibility()==View.VISIBLE).put("cardHasFocus",c.row.hasFocus()).put("titleCenter",centerOnScreen(c.title)).put("artistCenter",centerOnScreen(c.artist));});
    }
    private static float centerOnScreen(View view){int[] xy=new int[2];view.getLocationOnScreen(xy);return xy[0]+view.getWidth()/2f;}
    private final Activity activity;
    private final LinearLayout slot, row;
    private final TextView title, artist, time;
    private final FrameLayout stage;
    private final ScrollingLyricsView lyrics;
    private final AudioSpectrumView spectrum;
    private boolean lyricsRegistered;
    private final ImageView artwork;
    private final ProgressBar progress;
    private final Button previous, play, next, mode, shuffle, cardSettings;
    private LinearLayout buttonRow;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessions;
    private final ComponentName listener;
    private final Runnable goToApps;
    private MediaControllerCompat controller;
    private MediaBrowserCompat browser;
    private boolean connecting;
    private long cachedAt, resumeRequestedAt;
    private boolean active, listening, registered, closed;
    private CharSequence lastTitle;
    private boolean idleLocal;
    void setIdleLocal(String name){
        idleLocal=name!=null;
        if(idleLocal){title.setText(DesktopLanguage.t(activity,"本地背景音乐"));artist.setText(name);play.setText(DesktopLanguage.t(activity,"暂停"));lyrics.setVisibility(View.INVISIBLE);spectrum.setPlaying(true);time.setText("");progress.setProgress(0);}
        else{place();scan();}
    }
    private Bitmap lastArtwork;
    private final Runnable tick=new Runnable(){public void run(){if(!active)return;scan();updateProgress();main.postDelayed(this,1000);}};
    private final MediaSessionManager.OnActiveSessionsChangedListener changed=ignored->scan();
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){scan();}};
    private final BroadcastReceiver lyricReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
        if(idleLocal)return;
        if(controller==null || !"com.craftworks.music.tvcustom".equals(controller.getPackageName()))return;
        boolean enabled=activity.getSharedPreferences(Config.SETTINGS_NAME,0).getBoolean("desktop_lyrics_visible",activity.getSharedPreferences(Config.SETTINGS_NAME,0).getBoolean("desktop_lyrics",true));
        lyrics.setVisibility(enabled?View.VISIBLE:View.INVISIBLE);
        lyrics.update(intent.getStringArrayExtra("window"),intent.getIntExtra("index",-1),intent.getFloatExtra("progress",0f),
                intent.getStringExtra("song"),activity.getSharedPreferences(Config.SETTINGS_NAME,0).getInt("lyrics_color",0xFF79E8FF));
    }};
    private final MediaControllerCompat.Callback callback=new MediaControllerCompat.Callback(){
        @Override public void onMetadataChanged(MediaMetadataCompat m){render();}
        @Override public void onPlaybackStateChanged(PlaybackStateCompat s){render();}
        @Override public void onSessionReady(){render();}
        @Override public void onRepeatModeChanged(int repeat){renderMode();}
        @Override public void onShuffleModeChanged(int shuffle){renderMode();}
        @Override public void onSessionDestroyed(){detach();scan();}
    };
    NowPlayingCard(Activity a,LinearLayout target,Runnable goToApps) {
        activity=a;slot=target;this.goToApps=goToApps;latest=new java.lang.ref.WeakReference<>(this);
        sessions=(MediaSessionManager)a.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener=new ComponentName(a,MusicNotificationListener.class);
        slot.setVisibility(View.GONE);
        row=new LinearLayout(a);row.setOrientation(LinearLayout.VERTICAL);row.setGravity(Gravity.CENTER);row.setPadding(dp(8),dp(14),dp(8),dp(14));
        stage=new FrameLayout(a);slot.addView(stage,new LinearLayout.LayoutParams(-1,dp(252)));
        stage.addView(row,new FrameLayout.LayoutParams(dp(252),-2,Gravity.LEFT|Gravity.CENTER_VERTICAL));
        lyrics=new ScrollingLyricsView(a);stage.addView(lyrics,new FrameLayout.LayoutParams(dp(500),-1));
        stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)place();});
        row.setMinimumHeight(dp(228));
        artwork=new ImageView(a);artwork.setVisibility(View.GONE);
        buttonRow=new LinearLayout(a);buttonRow.setGravity(Gravity.CENTER_VERTICAL);row.addView(buttonRow);
        // Balance the right-side settings button so title and artist share the card center.
        buttonRow.addView(new View(a),new LinearLayout.LayoutParams(dp(28),dp(32)));
        title=text(buttonRow,20,Color.WHITE);title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        cardSettings=button("⚙",28,()->a.startActivity(new Intent(a,LauncherSettingsActivity.class).putExtra("music_card_settings",true)));cardSettings.setContentDescription(DesktopLanguage.t(activity,"播放卡片设置"));cardSettings.getLayoutParams().height=dp(32);
        title.setGravity(Gravity.CENTER);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);
        artist=text(row,13,0xFFD2DBE6);artist.setGravity(Gravity.CENTER);artist.setSingleLine(true);artist.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout timeline=new LinearLayout(a);timeline.setGravity(Gravity.CENTER_VERTICAL);row.addView(timeline,new LinearLayout.LayoutParams(-1,dp(28)));
        progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgressTintList(ColorStateList.valueOf(0xFF79E8FF));progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
        timeline.addView(progress,new LinearLayout.LayoutParams(0,dp(2),1));
        time=new TextView(a);time.setTextColor(DesktopAppearance.text(a));time.setTextSize(10);time.setPadding(dp(6),0,0,0);timeline.addView(time);
        buttonRow=new LinearLayout(a);row.addView(buttonRow);
        shuffle=button(DesktopLanguage.t(activity,"顺序循环"),42,()->new android.app.AlertDialog.Builder(activity).setTitle(DesktopLanguage.t(activity,"播放模式")).setItems(new String[]{DesktopLanguage.t(activity,"单曲循环"),DesktopLanguage.t(activity,"随机播放"),DesktopLanguage.t(activity,"顺序循环")},(dialog,index)->setMode(new int[]{0,2,1}[index])).show());
        previous=button(DesktopLanguage.t(activity,"上一首"),42,()->controls().skipToPrevious());
        play=button(DesktopLanguage.t(activity,"播放"),48,()->{scan();if(controller==null){startFromDesktop();return;}MediaMetadataCompat m=controller.getMetadata();if(m==null||TextUtils.isEmpty(m.getText(MediaMetadataCompat.METADATA_KEY_TITLE))){requestDesktopPlay();return;}PlaybackStateCompat s=controller.getPlaybackState();if(s!=null&&isPlaying(s))controls().pause();else controls().play();});
        next=button(DesktopLanguage.t(activity,"下一首"),42,()->controls().skipToNext());
        mode=button(DesktopLanguage.t(activity,"播放列表"),42,()->new DesktopSongList(activity));
        cardSettings.setNextFocusDownId(play.getId());
        buttonRow.setGravity(Gravity.CENTER);LinearLayout.LayoutParams controlsSpace=(LinearLayout.LayoutParams)buttonRow.getLayoutParams();controlsSpace.topMargin=dp(8);buttonRow.setLayoutParams(controlsSpace);
        spectrum=new AudioSpectrumView(a);row.addView(spectrum,new LinearLayout.LayoutParams(-1,dp(68)));
        title.setContentDescription(DesktopLanguage.t(activity,"当前播放的歌曲"));
    }
    private void place(){
        boolean hadCardFocus=row.hasFocus()&&!showCard();row.setVisibility(showCard()?View.VISIBLE:View.INVISIBLE);if(hadCardFocus)slot.post(goToApps);
        int w=stage.getWidth();if(w<=0)return;
        int position=activity.getSharedPreferences(Config.SETTINGS_NAME,0).getInt("music_card_position",0);
        FrameLayout.LayoutParams card=(FrameLayout.LayoutParams)row.getLayoutParams();
        card.gravity=Gravity.CENTER_VERTICAL|(position==1?Gravity.CENTER_HORIZONTAL:position==2?Gravity.RIGHT:Gravity.LEFT);row.setLayoutParams(card);
        int width,left;
        if(position==1){left=(w+dp(252))/2+dp(20);width=w-left;}
        else if(position==2){width=Math.min(dp(520),(int)(w*.46f));left=(w-width)/2;}
        else {width=Math.min(dp(520),(int)(w*.46f));left=(w-width)/2;}
        if(!showCard()){width=Math.min(dp(650),w);left=(w-width)/2;}
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)lyrics.getLayoutParams();
        lp.width=Math.max(dp(80),width);lp.height=-1;lp.leftMargin=left;lp.gravity=Gravity.LEFT;lyrics.setLayoutParams(lp);
        lyrics.setVisibility(activity.getSharedPreferences(Config.SETTINGS_NAME,0).getBoolean("desktop_lyrics_visible",activity.getSharedPreferences(Config.SETTINGS_NAME,0).getBoolean("desktop_lyrics",true))?View.VISIBLE:View.INVISIBLE);
    }
    private int dp(float n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
    private TextView text(LinearLayout p,int sp,int color){TextView v=new TextView(activity);v.setTextSize(sp);v.setTextColor(DesktopAppearance.text(activity));p.addView(v);return v;}
    private Button button(String name,int width,Runnable action) {
        Button b=new MusicIconButton(activity);b.setId(View.generateViewId());b.setText(name);b.setTextSize(12);b.setAllCaps(false);b.setPadding(dp(4),0,dp(4),0);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextColor(Color.WHITE);b.setFocusable(true);b.setBackground(background(false));
        b.setOnFocusChangeListener((v,has)->{v.setBackground(background(has));((TextView)v).setTextColor(has?0xFF79E8FF:DesktopAppearance.text(activity));((TextView)v).setShadowLayer(has?dp(8):0,0,0,0xFF3DC9FF);v.setScaleX(has?1.04f:1);v.setScaleY(has?1.04f:1);});
        b.setOnClickListener(v->{if(controller!=null||v==play||v==cardSettings||v==mode)try{action.run();}catch(RuntimeException e){Toast.makeText(activity,DesktopLanguage.t(activity,"播放器暂不可用，请重新打开 Aurora。"),Toast.LENGTH_SHORT).show();scan();}});
        b.setOnKeyListener((v,key,e)->{
            if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){
                if(v==cardSettings&&play.isEnabled())play.requestFocus();else goToApps.run();
                return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_UP){if(v!=cardSettings)cardSettings.requestFocus();else {View inputs=activity.findViewById(R.id.btnInputs);if(inputs!=null&&inputs.getVisibility()==View.VISIBLE)inputs.requestFocus();else {View wifi=activity.findViewById(R.id.btnWifi);if(wifi!=null)wifi.requestFocus();}}return true;}
            return false;
        });
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(width),dp(46));buttonRow.addView(b,params);return b;
    }
    private GradientDrawable background(boolean focus){GradientDrawable d=new GradientDrawable();d.setColor(Color.TRANSPARENT);return d;}
    boolean focus(){return showCard()&&slot.getVisibility()==View.VISIBLE && play.isEnabled() && play.requestFocus();}
    void resume(){
        if(closed)return;active=true;
        place();
        if(showCard())spectrum.start();else spectrum.stop();
        if(!registered){IntentFilter f=new IntentFilter(MusicNotificationListener.CHANGED);if(Build.VERSION.SDK_INT>=33)activity.registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else activity.registerReceiver(receiver,f);registered=true;}
        try{if(!listening){sessions.addOnActiveSessionsChangedListener(changed,listener,main);listening=true;}}catch(SecurityException ignored){}
        if(!lyricsRegistered){IntentFilter lf=new IntentFilter("com.gathin.tvhome.DESKTOP_LYRICS");if(Build.VERSION.SDK_INT>=33)activity.registerReceiver(lyricReceiver,lf,"com.gathin.tvhome.permission.DESKTOP_LYRICS",main,Context.RECEIVER_EXPORTED);else activity.registerReceiver(lyricReceiver,lf,"com.gathin.tvhome.permission.DESKTOP_LYRICS",main);lyricsRegistered=true;}
        scan();main.removeCallbacks(tick);main.post(tick);
    }
    void pause(){disconnectBrowser();spectrum.stop();lyrics.stop();if(lyricsRegistered){activity.unregisterReceiver(lyricReceiver);lyricsRegistered=false;}active=false;main.removeCallbacks(tick);if(listening){sessions.removeOnActiveSessionsChangedListener(changed);listening=false;}if(registered){activity.unregisterReceiver(receiver);registered=false;}detach();}
    void close(){closed=true;pause();}
    private void scan(){
        if(idleLocal)return;
        if(!active||closed)return;
        try {
            List<android.media.session.MediaController> all=sessions.getActiveSessions(listener);
            all=new java.util.ArrayList<>(all);android.media.session.MediaController connected=AuroraPlayback.controller(activity);if(connected!=null)all.add(0,connected);
            all.sort((a,b)->Boolean.compare(!"com.craftworks.music.tvcustom".equals(a.getPackageName()),!"com.craftworks.music.tvcustom".equals(b.getPackageName())));
            for(android.media.session.MediaController c:all)if("com.craftworks.music".equals(c.getPackageName())||"com.craftworks.music.tvcustom".equals(c.getPackageName())){
                MediaSessionCompat.Token token=MediaSessionCompat.Token.fromToken(c.getSessionToken());
                if(controller!=null && controller.getSessionToken().equals(token)){render();return;}
                detach();controller=new MediaControllerCompat(activity,token);controller.registerCallback(callback,main);render();return;
            }
        }catch(RuntimeException failure){android.util.Log.w("TvHomeMusic","Media session access unavailable",failure);}
        detach();hide();
    }
    private void detach(){if(controller!=null){controller.unregisterCallback(callback);controller=null;}lastArtwork=null;lastTitle=null;lyrics.clear();spectrum.setPlaying(false);}
    private boolean showCard(){android.content.SharedPreferences p=activity.getSharedPreferences(Config.SETTINGS_NAME,0);return p.getBoolean("desktop_card_visible",p.getBoolean("desktop_lyrics",true));}
    private boolean showLyrics(){android.content.SharedPreferences p=activity.getSharedPreferences(Config.SETTINGS_NAME,0);return p.getBoolean("desktop_lyrics_visible",p.getBoolean("desktop_lyrics",true));}
    private boolean enabled(){return showCard()||showLyrics();}
    private void hide(){
        if(idleLocal)return;
        if(!enabled()){boolean focused=slot.hasFocus();slot.setVisibility(View.INVISIBLE);if(focused)slot.post(goToApps);return;}
        place();slot.setVisibility(View.VISIBLE);artwork.setImageDrawable(null);
        android.content.SharedPreferences prefs=activity.getSharedPreferences(Config.SETTINGS_NAME,0);
        title.setText(prefs.getString("music_last_title",DesktopLanguage.t(activity,"桌面音乐")));
        artist.setText(prefs.getString("music_last_artist",DesktopLanguage.t(activity,"点击播放，继续上次音乐")));
        long duration=prefs.getLong("music_last_duration",0),position=prefs.getLong("music_last_position",0);
        time.setText(stamp(position)+" / "+stamp(duration));progress.setProgress(duration>0?(int)(Math.min(position,duration)*1000/duration):0);
        play.setText(connecting?DesktopLanguage.t(activity,"连接中"):DesktopLanguage.t(activity,"播放"));enable(play,true);enable(cardSettings,true);
        for(Button b:new Button[]{previous,next,shuffle})enable(b,false);enable(mode,true);
    }
    private void disconnectBrowser(){
        connecting=false;
        if(browser!=null){browser.disconnect();browser=null;}
        main.removeCallbacks(connectTimeout);
    }
    private final Runnable connectTimeout=this::connectionTimedOut;
    private void connectionTimedOut(){
        if(!connecting)return;
        disconnectBrowser();scan();
        Toast.makeText(activity,DesktopLanguage.t(activity,"播放器暂时未连接，请稍后再试。"),Toast.LENGTH_SHORT).show();
    }
    private void requestDesktopPlay(){
        resumeRequestedAt=SystemClock.elapsedRealtime();
        activity.sendBroadcast(new Intent("com.craftworks.music.tvcustom.DESKTOP_PLAY").setPackage("com.craftworks.music.tvcustom"));
    }
    private void startFromDesktop(){
        if(connecting)return;
        connecting=true;hide();
        browser=new MediaBrowserCompat(activity,new ComponentName("com.craftworks.music.tvcustom","com.craftworks.music.player.ChoraMediaLibraryService"),
            new MediaBrowserCompat.ConnectionCallback(){
                @Override public void onConnected(){
                    if(browser==null||!active)return;
                    try{
                        detach();
                        controller=new MediaControllerCompat(activity,browser.getSessionToken());
                        controller.registerCallback(callback,main);
                        connecting=false;main.removeCallbacks(connectTimeout);
                        requestDesktopPlay();render();
                        main.postDelayed(()->{if(active)scan();},800);
                    }catch(Exception e){onConnectionFailed();}
                }
                @Override public void onConnectionFailed(){
                    disconnectBrowser();scan();
                    Toast.makeText(activity,DesktopLanguage.t(activity,"无法连接 Aurora，请检查播放器是否仍已安装。"),Toast.LENGTH_SHORT).show();
                }
                @Override public void onConnectionSuspended(){disconnectBrowser();scan();}
            },null);
        browser.connect();main.postDelayed(connectTimeout,12000);
    }
    private MediaControllerCompat.TransportControls controls(){return controller.getTransportControls();}
    private boolean isPlaying(PlaybackStateCompat s){return s.getState()==PlaybackStateCompat.STATE_PLAYING || s.getState()==PlaybackStateCompat.STATE_BUFFERING || s.getState()==PlaybackStateCompat.STATE_CONNECTING;}
    private void render(){
        if(idleLocal)return;
        if(controller==null||!active)return;
        if(!enabled()){hide();return;}
        MediaMetadataCompat m=controller.getMetadata();
        CharSequence song=m==null?null:m.getText(MediaMetadataCompat.METADATA_KEY_TITLE);
        if(TextUtils.isEmpty(song)&&m!=null)song=m.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
        if(TextUtils.isEmpty(song)){hide();return;}
        place();slot.setVisibility(View.VISIBLE);title.setText(song);enable(cardSettings,true);
        CharSequence who=m.getText(MediaMetadataCompat.METADATA_KEY_ARTIST);if(TextUtils.isEmpty(who))who=m.getText(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);artist.setText(TextUtils.isEmpty(who)?"Aurora":who);
        Bitmap cover=m.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON);if(cover==null)cover=m.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);if(cover==null)cover=m.getBitmap(MediaMetadataCompat.METADATA_KEY_ART);
        if(cover!=lastArtwork || !TextUtils.equals(song,lastTitle)){lastArtwork=cover;lastTitle=song;artwork.setImageBitmap(cover);artwork.setVisibility(cover==null?View.GONE:View.VISIBLE);}
        PlaybackStateCompat state=controller.getPlaybackState();
        long savedDuration=m.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        if(resumeRequestedAt>0&&SystemClock.elapsedRealtime()-resumeRequestedAt<5000&&state!=null&&savedDuration>0&&state.getPosition()>=savedDuration-500){
            resumeRequestedAt=0;controls().seekTo(0);controls().play();
        }
        long actions=state==null?0:state.getActions();
        play.setText(state!=null&&isPlaying(state)?DesktopLanguage.t(activity,"暂停"):DesktopLanguage.t(activity,"播放"));
        enable(previous,(actions&PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)!=0);enable(next,(actions&PlaybackStateCompat.ACTION_SKIP_TO_NEXT)!=0);
        enable(play,(actions&(PlaybackStateCompat.ACTION_PLAY|PlaybackStateCompat.ACTION_PAUSE|PlaybackStateCompat.ACTION_PLAY_PAUSE))!=0);
        spectrum.setPlaying(state!=null&&state.getState()==PlaybackStateCompat.STATE_PLAYING);renderMode();updateProgress();
        activity.getSharedPreferences(Config.SETTINGS_NAME,0).edit().putString("music_last_title",song.toString()).putString("music_last_artist",artist.getText().toString()).putLong("music_last_duration",m.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)).apply();
    }
    private void enable(Button b,boolean enabled){b.setEnabled(enabled);b.setAlpha(enabled?1:.4f);}
    private void renderMode(){if(controller==null)return;int shuffle=controller.getShuffleMode(),repeat=controller.getRepeatMode();
        int selected=shuffle==PlaybackStateCompat.SHUFFLE_MODE_ALL||shuffle==PlaybackStateCompat.SHUFFLE_MODE_GROUP?2:repeat==PlaybackStateCompat.REPEAT_MODE_ONE?0:1;
        this.shuffle.setText(selected==0?DesktopLanguage.t(activity,"单曲循环"):selected==2?DesktopLanguage.t(activity,"随机播放"):DesktopLanguage.t(activity,"顺序循环"));mode.setText(DesktopLanguage.t(activity,"播放列表"));
        PlaybackStateCompat s=controller.getPlaybackState();long actions=s==null?0:s.getActions();boolean supported=(actions&PlaybackStateCompat.ACTION_SET_REPEAT_MODE)!=0 && (actions&PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)!=0;
        enable(mode,true);enable(this.shuffle,supported);
    }
    private void setMode(int which){
        controls().setShuffleMode(which==2?PlaybackStateCompat.SHUFFLE_MODE_ALL:PlaybackStateCompat.SHUFFLE_MODE_NONE);
        controls().setRepeatMode(which==0?PlaybackStateCompat.REPEAT_MODE_ONE:PlaybackStateCompat.REPEAT_MODE_ALL);
        main.postDelayed(this::renderMode,400);
    }
    private void updateProgress(){
        if(idleLocal)return;
        if(controller==null || slot.getVisibility()!=View.VISIBLE)return;
        MediaMetadataCompat m=controller.getMetadata();PlaybackStateCompat s=controller.getPlaybackState();if(m==null||s==null)return;
        long duration=m.getLong(MediaMetadataCompat.METADATA_KEY_DURATION),position=s.getPosition();
        if(s.getState()==PlaybackStateCompat.STATE_PLAYING&&s.getLastPositionUpdateTime()>0)position+=(long)((SystemClock.elapsedRealtime()-s.getLastPositionUpdateTime())*s.getPlaybackSpeed());
        position=Math.max(0,duration>0?Math.min(position,duration):position);if(SystemClock.elapsedRealtime()-cachedAt>5000){cachedAt=SystemClock.elapsedRealtime();activity.getSharedPreferences(Config.SETTINGS_NAME,0).edit().putLong("music_last_position",position).apply();}progress.setProgress(duration>0?(int)(position*1000/duration):0);time.setText(stamp(position)+" / "+stamp(duration));
    }
    private String stamp(long ms){long seconds=Math.max(0,ms)/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
}
