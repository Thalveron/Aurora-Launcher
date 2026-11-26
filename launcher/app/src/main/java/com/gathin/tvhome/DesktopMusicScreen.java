package com.gathin.tvhome;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** Attached only while a desktop activity is in the foreground. */
final class DesktopMusicScreen {
    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long idle;
    private Dialog screen;
    private FrameLayout root, content, backgroundLayer;
    private View backgroundView;
    private ImageView portrait;
    private ScrollingLyricsView lyrics;
    private AudioSpectrumView spectrum;
    private boolean spectrumRunning;
    private VideoView video;
    private String pendingVideoPath;
    private TextView title, album, artist;
    private LinearLayout info;
    private MediaMetadata previous;
    private final java.util.function.Supplier<PersonalBackground> desktop;
    private boolean running;
    private float scale=1f;
    private int screenWidth,screenHeight;
    /** Settings snapshot the open dialog was built from, so console changes apply live. */
    private String renderedSettings="";
    private String backgroundId="";
    private boolean uiHideCover,uiHideLyrics;
    private String uiLayout="side",uiAlign="center";
    private JSONObject uiSpectrum;
    DesktopMusicScreen(Activity a,java.util.function.Supplier<PersonalBackground> desktop){activity=a;this.desktop=desktop;}
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("fullscreen_"+AuroraThemes.current(c),0);}
    static JSONObject settings(Context c)throws Exception{
        return new JSONObject().put("minutes",prefs(c).getInt("minutes",0))
                .put("background",prefs(c).getString("background",""))
                .put("hideCover",prefs(c).getBoolean("hide_cover",false))
                .put("hideLyrics",prefs(c).getBoolean("hide_lyrics",false))
                .put("lyricsAlign",prefs(c).getString("lyrics_align","center"))
                .put("lyricsLayout",prefs(c).getString("lyrics_layout","side"))
                .put("spectrum",spectrum(c));
    }
    static JSONObject spectrum(Context c)throws Exception{
        String raw=prefs(c).getString("spectrum","");
        return raw.isEmpty()?defaultSpectrum():new JSONObject(raw);
    }
    private static JSONObject defaultSpectrum()throws Exception{
        return new JSONObject().put("style","wave").put("color","#55ddffff").put("color2","#8266ffff")
                .put("opacity",50).put("size",100).put("angle",315).put("stops",new JSONArray());
    }
    /** Stable signature of the fullscreen options, so a console save applies once. */
    private static String signature(JSONObject value){
        JSONObject spec=value.optJSONObject("spectrum");
        return value.optInt("minutes")+"|"+value.optString("background","")+"|"+value.optBoolean("hideCover")+"|"
                +value.optBoolean("hideLyrics")+"|"+value.optString("lyricsAlign","")+"|"+value.optString("lyricsLayout","")+"|"
                +(spec==null?"-":spec.optString("style","")+"|"+spec.optString("color","")+"|"+spec.optString("color2","")+"|"
                +spec.optInt("opacity")+"|"+spec.optInt("size")+"|"+spec.optInt("angle")+"|"+spec.optJSONArray("stops"));
    }
    private static void validSpectrum(JSONObject value)throws Exception{
        AuroraSettings.only(value,"style","color","color2","opacity","size","angle","stops");
        AuroraSettings.choice(value.optString("style","wave"),"bars","wave","mirror","dots","ring");
        AuroraSettings.color(value.optString("color","#55ddffff"));
        AuroraSettings.color(value.optString("color2","#8266ffff"));
        AuroraSettings.range(value.optInt("opacity",50),0,100);
        AuroraSettings.range(value.optInt("size",100),30,200);
        AuroraSettings.range(value.optInt("angle",315),0,360);
        JSONArray stops=value.optJSONArray("stops");
        if(stops!=null){
            if(stops.length()>6)throw new IllegalArgumentException("渐变最多6个中间色标");
            for(int i=0;i<stops.length();i++){JSONObject stop=stops.getJSONObject(i);AuroraSettings.only(stop,"color","position");AuroraSettings.color(stop.getString("color"));AuroraSettings.range(stop.getDouble("position"),0,100);}
        }
    }
    static JSONObject save(Context c,JSONObject b)throws Exception{
        AuroraSettings.only(b,"minutes","background","hideCover","hideLyrics","lyricsAlign","lyricsLayout","spectrum");
        android.content.SharedPreferences.Editor edit=prefs(c).edit();
        if(b.has("minutes")){int m=b.getInt("minutes");if(m!=0&&m!=1&&m!=3&&m!=5&&m!=8)throw new IllegalArgumentException("时间无效");edit.putInt("minutes",m);}
        if(b.has("background")){String id=b.getString("background");if(!id.isEmpty())AuroraLibrary.file(c,id,"background");edit.putString("background",id);}
        if(b.has("hideCover")){if(!(b.get("hideCover") instanceof Boolean))throw new IllegalArgumentException("隐藏专辑图片必须是开关值");edit.putBoolean("hide_cover",b.getBoolean("hideCover"));}
        if(b.has("hideLyrics")){if(!(b.get("hideLyrics") instanceof Boolean))throw new IllegalArgumentException("隐藏歌词必须是开关值");edit.putBoolean("hide_lyrics",b.getBoolean("hideLyrics"));}
        if(b.has("lyricsAlign")){String value=b.getString("lyricsAlign");if(!java.util.Arrays.asList("center","left","right").contains(value))throw new IllegalArgumentException("歌词对齐方式无效");edit.putString("lyrics_align",value);}
        if(b.has("lyricsLayout")){String value=b.getString("lyricsLayout");if(!java.util.Arrays.asList("side","bottom-one","bottom-two").contains(value))throw new IllegalArgumentException("歌词样式无效");edit.putString("lyrics_layout",value);}
        if(b.has("spectrum")){JSONObject value=b.getJSONObject("spectrum");validSpectrum(value);edit.putString("spectrum",value.toString());}
        edit.apply();return settings(c);
    }
    void reset(){idle=SystemClock.elapsedRealtime();}
    void start(){running=true;reset();handler.removeCallbacks(tick);handler.post(tick);}
    void stop(){running=false;handler.removeCallbacks(tick);if(screen!=null)screen.dismiss();}
    private final Runnable tick=new Runnable(){public void run(){
        int minutes=prefs(activity).getInt("minutes",0);
        MediaController controller=minutes==0?null:AuroraData.controller(activity);
        PlaybackState state=controller==null?null:controller.getPlaybackState();
        if(state==null||state.getState()!=PlaybackState.STATE_PLAYING||AuroraSaver.active){
            reset();if(screen!=null)screen.dismiss();
        }else if(screen!=null){
            String signature="";
            try{signature=signature(settings(activity));}catch(Exception ignored){}
            if(!signature.isEmpty()&&!signature.equals(renderedSettings)){
                renderedSettings=signature;
                applySettings();
            }
            update(controller);
        }
        else if(!activity.hasWindowFocus())reset();
        else if(SystemClock.elapsedRealtime()-idle>=minutes*60000L)open(controller);
        handler.postDelayed(this,1000);
    }};
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(lyrics==null)return;
        lyrics.update(i.getStringArrayExtra("window"),i.getIntExtra("index",-1),i.getFloatExtra("progress",0),i.getStringExtra("song"),activity.getSharedPreferences(Config.SETTINGS_NAME,0).getInt("lyrics_color",0xff79e8ff));
    }};
    private void update(MediaController controller){
        MediaMetadata m=controller.getMetadata();if(m==previous)return;previous=m;
        Bitmap cover=null;
        if(m!=null){cover=m.getDescription().getIconBitmap();title.setText(m.getString(MediaMetadata.METADATA_KEY_TITLE));}
        else title.setText("");
        setDetail(album,m,MediaMetadata.METADATA_KEY_ALBUM);
        setDetail(artist,m,MediaMetadata.METADATA_KEY_ARTIST);
        portrait.setImageBitmap(cover);
        if(cover==null&&m!=null&&m.getDescription().getIconUri()!=null){
            android.net.Uri uri=m.getDescription().getIconUri();if("content".equals(uri.getScheme()))portrait.setImageURI(uri);
        }
        tintBackground();
    }
    private void tintBackground(){
        if(albumBackground()==null)return;
        android.graphics.drawable.Drawable art=portrait==null?null:portrait.getDrawable();
        if(art==null){albumBackground().setBackgroundColor(0xff172037);return;}
        Bitmap sample=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);
        Rect bounds=new Rect(art.getBounds());art.setBounds(0,0,16,16);art.draw(new Canvas(sample));art.setBounds(bounds);
        int red=0,green=0,blue=0,total=0;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int pixel=sample.getPixel(x,y),r=Color.red(pixel),g=Color.green(pixel),b=Color.blue(pixel);
            int weight=Color.alpha(pixel)*(32+Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b)))/255;
            red+=r*weight;green+=g*weight;blue+=b*weight;total+=weight;
        }
        sample.recycle();if(total==0){albumBackground().setBackgroundColor(0xff172037);return;}
        int r=red/total,g=green/total,b=blue/total;
        albumBackground().setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(32+r*3/4,32+g*3/4,32+b*3/4),Color.rgb(12+r/3,12+g/3,12+b/3)}));
    }
    /** The tinted surface: root when no custom background is selected. */
    private FrameLayout albumBackground(){return backgroundId.isEmpty()?root:null;}
    private TextView caption(LinearLayout parent,float pixels){
        TextView label=new TextView(activity);label.setGravity(Gravity.CENTER);label.setTextColor(Color.WHITE);
        label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,pixels);parent.addView(label,-1,-2);return label;
    }
    private void setDetail(TextView label,MediaMetadata metadata,String key){
        String value=metadata==null?null:metadata.getString(key);
        label.setText(value);label.setVisibility(android.text.TextUtils.isEmpty(value)?View.GONE:View.VISIBLE);
    }
    private void open(MediaController controller){
        JSONObject config;
        try{config=settings(activity);}catch(Exception unavailable){config=new JSONObject();}
        root=new FrameLayout(activity);root.setBackgroundColor(0xff101419);
        android.util.DisplayMetrics metrics=activity.getResources().getDisplayMetrics();
        screenWidth=metrics.widthPixels;screenHeight=metrics.heightPixels;
        scale=Math.min(screenWidth/1920f,screenHeight/1080f);
        backgroundLayer=new FrameLayout(activity);
        root.addView(backgroundLayer,new FrameLayout.LayoutParams(-1,-1));
        content=new FrameLayout(activity);content.setBackgroundColor(0x66000000);
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        info=new LinearLayout(activity);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.CENTER_HORIZONTAL);
        int coverSize=Math.round(398*scale);
        FrameLayout.LayoutParams infoBox=new FrameLayout.LayoutParams(coverSize,-2);
        infoBox.leftMargin=Math.round(screenWidth*.267f-coverSize/2f);
        infoBox.topMargin=Math.round(screenHeight*.24f);
        content.addView(info,infoBox);
        portrait=new ImageView(activity);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.graphics.drawable.GradientDrawable corners=new android.graphics.drawable.GradientDrawable();
        corners.setColor(Color.TRANSPARENT);corners.setCornerRadius(14*scale);
        portrait.setBackground(corners);portrait.setClipToOutline(true);info.addView(portrait,coverSize,coverSize);
        title=caption(info,23*scale);title.setPadding(0,Math.round(10*scale),0,0);
        album=caption(info,18*scale);artist=caption(info,18*scale);
        IntentFilter filter=new IntentFilter("com.gathin.tvhome.DESKTOP_LYRICS");String permission="com.gathin.tvhome.permission.DESKTOP_LYRICS";
        if(Build.VERSION.SDK_INT>=33)activity.registerReceiver(receiver,filter,permission,handler,Context.RECEIVER_EXPORTED);else activity.registerReceiver(receiver,filter,permission,handler);
        screen=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);screen.setContentView(root);
        Dialog opened=screen;
        PersonalBackground background=desktop.get();
        screen.setOnDismissListener(d->{
            activity.unregisterReceiver(receiver);
            if(lyrics!=null){lyrics.stop();lyrics=null;}
            if(spectrum!=null){spectrum.stop();spectrum=null;}spectrumRunning=false;
            stopBackgroundVideo();
            previous=null;screen=null;reset();if(background!=null)background.finishFullscreen(opened,running);
        });
        screen.setOnKeyListener((dialog,key,event)->{
            if(event.getAction()==KeyEvent.ACTION_UP)dialog.dismiss();
            return true;
        });
        screen.show();update(controller);
        renderedSettings=signature(config);
        applySettings();
        Runnable startBackground=()->{if(screen==opened&&running)startBackgroundVideo();};
        if(background==null)startBackground.run();
        else background.suspendForFullscreen(opened,startBackground);
    }
    /** Applies every option in place; nothing is torn down except the layer that really changed. */
    private void applySettings(){
        JSONObject config;
        try{config=settings(activity);}catch(Exception unavailable){return;}
        uiHideCover=config.optBoolean("hideCover",false);
        uiHideLyrics=config.optBoolean("hideLyrics",false);
        uiLayout=config.optString("lyricsLayout","side");
        uiAlign=config.optString("lyricsAlign","center");
        uiSpectrum=config.optJSONObject("spectrum");
        applyBackground(config.optString("background",""));
        if(info!=null)info.setVisibility(uiHideCover?View.GONE:View.VISIBLE);
        applyLyrics();
        applySpectrum();
    }
    private void applyBackground(String id){
        if(Objects.equals(id,backgroundId)&&(backgroundView!=null||id.isEmpty()))return;
        backgroundId=id;
        stopBackgroundVideo();
        if(backgroundView!=null){backgroundLayer.removeView(backgroundView);backgroundView=null;}
        if(id.isEmpty()){tintBackground();return;}
        try{
            File file=AuroraLibrary.file(activity,id,"background");
            if(id.matches(".*\\.(mp4|webm|mkv)")){
                VideoView view=new VideoView(activity);
                view.setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_NONE);
                view.setOnPreparedListener(mp->{if(view==video){mp.setVolume(0,0);mp.setLooping(true);view.start();}});
                video=view;backgroundView=view;
                backgroundLayer.addView(view,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
                pendingVideoPath=file.getAbsolutePath();
                startBackgroundVideo();
            }else{
                ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
                options.inSampleSize=1;while(options.outWidth/options.inSampleSize>3840||options.outHeight/options.inSampleSize>2160)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;image.setImageBitmap(BitmapFactory.decodeFile(file.getPath(),options));
                backgroundView=image;backgroundLayer.addView(image,new FrameLayout.LayoutParams(-1,-1));
            }
        }catch(Exception ignored){backgroundId="";tintBackground();}
    }
    private void startBackgroundVideo(){
        if(video==null||pendingVideoPath==null||screen==null)return;
        String path=pendingVideoPath;pendingVideoPath=null;
        video.setVideoPath(path);
    }
    private void stopBackgroundVideo(){
        if(video!=null){try{video.stopPlayback();}catch(Exception ignored){}video=null;}
        pendingVideoPath=null;
    }
    private void applyLyrics(){
        if(uiHideLyrics){if(lyrics!=null){content.removeView(lyrics);lyrics.stop();lyrics=null;}return;}
        if(lyrics==null){lyrics=new ScrollingLyricsView(activity,scale);content.addView(lyrics);}
        lyrics.setStyle(uiLayout,uiAlign);
        lyrics.setLayoutParams(lyricsParams());
    }
    private FrameLayout.LayoutParams lyricsParams(){
        if("bottom-one".equals(uiLayout)||"bottom-two".equals(uiLayout)){
            int height="bottom-two".equals(uiLayout)?Math.round(300*scale):Math.round(190*scale);
            FrameLayout.LayoutParams lines=new FrameLayout.LayoutParams(Math.round(screenWidth*.9f),height,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            lines.bottomMargin=Math.round(170*scale);
            return lines;
        }
        FrameLayout.LayoutParams lines=new FrameLayout.LayoutParams(Math.round(screenWidth*.49f),Math.round(824*scale));
        lines.leftMargin=Math.round(screenWidth*.47f);lines.topMargin=Math.round((screenHeight-lines.height)/2f);
        return lines;
    }
    private void applySpectrum(){
        if(spectrum==null){
            spectrum=new AudioSpectrumView(activity);
            content.addView(spectrum);
        }
        spectrum.setLayoutParams(spectrumParams());
        if(uiSpectrum!=null){
            spectrum.setSpectrum(uiSpectrum);
            if(!spectrumRunning){spectrum.start();spectrum.setPlaying(true);spectrumRunning=true;}
        }
    }
    private FrameLayout.LayoutParams spectrumParams(){
        if("side".equals(uiLayout)){
            int coverSize=Math.round(398*scale);
            FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(coverSize,Math.round(110*scale));
            box.leftMargin=Math.round(screenWidth*.267f-coverSize/2f);
            box.topMargin=Math.round(screenHeight*.74f);
            return box;
        }
        FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(Math.round(screenWidth*.55f),Math.round(110*scale),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        box.bottomMargin=Math.round(30*scale);
        return box;
    }
}
