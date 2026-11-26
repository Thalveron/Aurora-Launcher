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
import org.json.JSONArray;
import org.json.JSONObject;

/** Attached only while a desktop activity is in the foreground. */
final class DesktopMusicScreen {
    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long idle;
    private Dialog screen;
    private FrameLayout root;
    private FrameLayout content;
    private LinearLayout info;
    private float scale=1f;
    private View backgroundView;
    private String backgroundId="";
    private String renderedOther="";
    private FrameLayout albumBackground;
    private ImageView portrait;
    private ScrollingLyricsView lyrics;
    private AudioSpectrumView spectrum;
    private boolean spectrumRunning,spectrumHintShown;
    private VideoView video;
    private TextView title, album, artist;
    private MediaMetadata previous;
    private final java.util.function.Supplier<PersonalBackground> desktop;
    private boolean running;
    /** Settings snapshot the open dialog was built from, so console changes can rebuild it live. */
    private String renderedSettings="";
    DesktopMusicScreen(Activity a,java.util.function.Supplier<PersonalBackground> desktop){activity=a;this.desktop=desktop;}
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("fullscreen_"+AuroraThemes.current(c),0);}
    static JSONObject settings(Context c)throws Exception{
        return new JSONObject().put("minutes",prefs(c).getInt("minutes",0))
                .put("background",prefs(c).getString("background",""))
                .put("hideCover",prefs(c).getBoolean("hide_cover",false))
                .put("lyricsAlign",prefs(c).getString("lyrics_align","center"))
                .put("lyricsLayout",prefs(c).getString("lyrics_layout","side"))
                .put("hideLyrics",prefs(c).getBoolean("hide_lyrics",false))
                .put("spectrumEnabled",prefs(c).getBoolean("spectrum_enabled",true))
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
    /** Stable signature of the fullscreen options, so a console save rebuilds the open dialog once. */
    private static String otherSignature(JSONObject value){
        try{return signature(new JSONObject(value.toString()).put("background",""));}catch(Exception e){return signature(value);}
    }
    private static String signature(JSONObject value){
        JSONObject spec=value.optJSONObject("spectrum");
        return value.optInt("minutes")+"|"+value.optString("background","")+"|"+value.optBoolean("hideCover")+"|"
                +value.optBoolean("hideLyrics")+"|"+value.optBoolean("spectrumEnabled",true)+"|"+value.optString("lyricsAlign","")+"|"+value.optString("lyricsLayout","")+"|"
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
        AuroraSettings.only(b,"minutes","background","hideCover","hideLyrics","lyricsAlign","lyricsLayout","spectrum","spectrumEnabled");
        android.content.SharedPreferences.Editor edit=prefs(c).edit();
        if(b.has("minutes")){int m=b.getInt("minutes");if(m!=0&&m!=1&&m!=3&&m!=5&&m!=8)throw new IllegalArgumentException("时间无效");edit.putInt("minutes",m);}
        if(b.has("background")){String id=b.getString("background");if(!id.isEmpty())AuroraLibrary.file(c,id,"background");edit.putString("background",id);}
        if(b.has("hideCover")){if(!(b.get("hideCover") instanceof Boolean))throw new IllegalArgumentException("隐藏专辑图片必须是开关值");edit.putBoolean("hide_cover",b.getBoolean("hideCover"));}
        if(b.has("spectrumEnabled")){if(!(b.get("spectrumEnabled") instanceof Boolean))throw new IllegalArgumentException("频谱开关必须是布尔值");edit.putBoolean("spectrum_enabled",b.getBoolean("spectrumEnabled"));}
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
            JSONObject next=null;String signature="";
            try{next=settings(activity);signature=signature(next);}catch(Exception ignored){}
            if(next!=null&&!signature.isEmpty()&&!signature.equals(renderedSettings)){
                String other=otherSignature(next);
                if(!other.equals(renderedOther)){applyOptions(next);renderedOther=other;}
                String background=next.optString("background","");
                if(!background.equals(backgroundId))replaceBackground(background);
                renderedSettings=signature;
            }
            update(controller);
            if(spectrum!=null&&spectrum.failed())spectrumUnavailableHint();
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
            android.net.Uri uri=m.getDescription().getIconUri();if("content".equals(uri.getScheme())){portrait.setImageURI(uri);portrait.postDelayed(this::tintBackground,300);portrait.postDelayed(this::tintBackground,1200);}
        }
        tintBackground();
    }
    private android.graphics.drawable.GradientDrawable fallbackGradient(){
        return new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xff2b3f63,0xff16202f});
    }
    /** Small state dump for debugging the fullscreen layout on the TV. */
    private void writeDebug(String stage){
        try{
            java.io.File dir=activity.getExternalFilesDir(null);if(dir==null)return;
            StringBuilder text=new StringBuilder();
            text.append("stage=").append(stage).append('\n');
            text.append("backgroundId=").append(backgroundId).append('\n');
            text.append("background=").append(backgroundView==null?"null":backgroundView.getClass().getSimpleName()+" vis="+backgroundView.getVisibility()+" "+backgroundView.getWidth()+"x"+backgroundView.getHeight()).append('\n');
            text.append("info=").append(info==null?"null":String.valueOf(info.getVisibility())).append('\n');
            text.append("lyrics=").append(lyrics==null?"null":lyrics.getVisibility()+" "+lyrics.getLeft()+","+lyrics.getTop()+" "+lyrics.getWidth()+"x"+lyrics.getHeight()).append('\n');
            android.util.DisplayMetrics m=activity.getResources().getDisplayMetrics();
            text.append("screen=").append(m.widthPixels).append('x').append(m.heightPixels).append('\n');
            text.append("align=").append(prefs(activity).getString("lyrics_align","center")).append(" layout=").append(prefs(activity).getString("lyrics_layout","side")).append('\n');
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"fullscreen-debug.txt"))){out.write(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}
        }catch(Exception ignored){}
    }
    private void tintBackground(){
        if(albumBackground==null)return;
        android.graphics.drawable.Drawable art=portrait.getDrawable();
        if(art==null){albumBackground.setBackground(fallbackGradient());return;}
        Bitmap sample=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);
        Rect bounds=new Rect(art.getBounds());art.setBounds(0,0,16,16);art.draw(new Canvas(sample));art.setBounds(bounds);
        int red=0,green=0,blue=0,total=0;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int pixel=sample.getPixel(x,y),r=Color.red(pixel),g=Color.green(pixel),b=Color.blue(pixel);
            int weight=Color.alpha(pixel)*(32+Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b)))/255;
            red+=r*weight;green+=g*weight;blue+=b*weight;total+=weight;
        }
        sample.recycle();if(total==0){albumBackground.setBackground(fallbackGradient());return;}
        int r=red/total,g=green/total,b=blue/total;
        albumBackground.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(32+r*3/4,32+g*3/4,32+b*3/4),Color.rgb(12+r/3,12+g/3,12+b/3)}));
    }
    private TextView caption(LinearLayout parent,float pixels){
        TextView label=new TextView(activity);label.setGravity(Gravity.CENTER);label.setTextColor(Color.WHITE);
        label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,pixels);parent.addView(label,-1,-2);return label;
    }
    private void setDetail(TextView label,MediaMetadata metadata,String key){
        String value=metadata==null?null:metadata.getString(key);
        label.setText(value);label.setVisibility(android.text.TextUtils.isEmpty(value)?View.GONE:View.VISIBLE);
    }
    /** Local ADB can grant the capture permission the spectrum needs, without a manual dialog. */
    private void ensureAudioCapture(){
        try{
            if(activity.checkSelfPermission("android.permission.RECORD_AUDIO")==android.content.pm.PackageManager.PERMISSION_GRANTED)return;
            if(TvAdb.status().optBoolean("connected"))TvAdb.shell(activity,"pm grant "+activity.getPackageName()+" android.permission.RECORD_AUDIO");
        }catch(Exception ignored){}
    }
    private void spectrumUnavailableHint(){
        if(spectrumHintShown||content==null)return;
        spectrumHintShown=true;
        TextView hint=new TextView(activity);
        hint.setText("频谱不可用：需要音频捕获权限");
        hint.setTextColor(0x99FFFFFF);hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,12);
        FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        box.bottomMargin=Math.round(8*scale);
        content.addView(hint,box);
    }
    /** Applies visibility, lyrics and spectrum options to the open dialog without rebuilding it. */
    private void applyOptions(JSONObject config){
        if(info!=null)info.setVisibility(config.optBoolean("hideCover",false)&&backgroundView!=null?View.GONE:View.VISIBLE);
        tintBackground();
        boolean hideLyrics=config.optBoolean("hideLyrics",false);
        String layout=config.optString("lyricsLayout","side");
        String align=config.optString("lyricsAlign","center");
        if(hideLyrics){
            if(lyrics!=null){content.removeView(lyrics);lyrics.stop();lyrics=null;}
        }else{
            if(lyrics==null){lyrics=new ScrollingLyricsView(activity,scale);content.addView(lyrics);}
            lyrics.setStyle(layout,align);
            lyrics.setLayoutParams(lyricsParams(layout));
            lyrics.bringToFront();
        }
        boolean spectrumEnabled=config.optBoolean("spectrumEnabled",true);
        if(!spectrumEnabled){if(spectrum!=null){content.removeView(spectrum);spectrum.stop();spectrum=null;spectrumRunning=false;}}
        else{
            if(spectrum==null){ensureAudioCapture();spectrum=new AudioSpectrumView(activity);content.addView(spectrum);spectrumRunning=false;}
            JSONObject spec=config.optJSONObject("spectrum");
            if(spec!=null)spectrum.setSpectrum(spec);
            spectrum.setLayoutParams(spectrumParams(layout));
            if(!spectrumRunning){spectrum.start();spectrum.setPlaying(true);spectrumRunning=true;}
        }
    }
    private FrameLayout.LayoutParams lyricsParams(String layout){
        android.util.DisplayMetrics metrics=activity.getResources().getDisplayMetrics();
        int width=metrics.widthPixels,height=metrics.heightPixels;
        if("bottom-one".equals(layout)||"bottom-two".equals(layout)){
            int box="bottom-two".equals(layout)?Math.round(300*scale):Math.round(190*scale);
            FrameLayout.LayoutParams lines=new FrameLayout.LayoutParams(Math.round(width*.9f),box,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            lines.bottomMargin=Math.round(170*scale);
            return lines;
        }
        FrameLayout.LayoutParams lines=new FrameLayout.LayoutParams(width,Math.round(824*scale));
        lines.topMargin=Math.round((height-lines.height)/2f);
        return lines;
    }
    private FrameLayout.LayoutParams spectrumParams(String layout){
        android.util.DisplayMetrics metrics=activity.getResources().getDisplayMetrics();
        int width=metrics.widthPixels,height=metrics.heightPixels;
        if("side".equals(layout)){
            int coverSize=Math.round(398*scale);
            FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(coverSize,Math.round(110*scale));
            box.leftMargin=Math.round(width*.267f-coverSize/2f);
            box.topMargin=Math.round(height*.74f);
            return box;
        }
        FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(Math.round(width*.55f),Math.round(110*scale),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        box.bottomMargin=Math.round(30*scale);
        return box;
    }
    /** Swaps only the fullscreen background layer, so a running video keeps the dialog alive. */
    private void replaceBackground(String id){
        if(video!=null){try{video.stopPlayback();}catch(Exception ignored){}video=null;}
        if(backgroundView!=null){root.removeView(backgroundView);backgroundView=null;}
        backgroundId=id;albumBackground=id.isEmpty()?root:null;
        if(id.isEmpty()){tintBackground();return;}
        try{
            File file=AuroraLibrary.file(activity,id,"background");
            if(id.matches(".*\\.(mp4|webm|mkv)")){
                VideoView view=new VideoView(activity);
                view.setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_NONE);
                view.setOnPreparedListener(mp->{if(view==video){mp.setVolume(0,0);mp.setLooping(true);view.start();}});
                view.setOnErrorListener((mp,what,extra)->{albumBackground=root;tintBackground();return true;});
                video=view;backgroundView=view;root.addView(view,0,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
                view.setVideoPath(file.getAbsolutePath());
            }else{
                ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
                options.inSampleSize=1;while(options.outWidth/options.inSampleSize>3840||options.outHeight/options.inSampleSize>2160)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;Bitmap decoded=BitmapFactory.decodeFile(file.getPath(),options);if(decoded==null)throw new java.io.IOException("background decode failed");
                image.setImageBitmap(decoded);backgroundView=image;root.addView(image,0,new FrameLayout.LayoutParams(-1,-1));
            }
        }catch(Exception ignored){backgroundId="";albumBackground=root;tintBackground();}
    }
    private void open(MediaController controller){
        JSONObject config;
        try{config=settings(activity);}catch(Exception unavailable){config=new JSONObject();}
        boolean hideCover=config.optBoolean("hideCover",false);
        boolean hideLyrics=config.optBoolean("hideLyrics",false);
        String lyricsLayout=config.optString("lyricsLayout","side");
        String lyricsAlign=config.optString("lyricsAlign","center");
        JSONObject spectrumSettings=config.optJSONObject("spectrum");
        boolean spectrumEnabled=config.optBoolean("spectrumEnabled",true);
        renderedSettings=signature(config);renderedOther=otherSignature(config);
        root=new FrameLayout(activity);root.setBackgroundColor(0xff101419);
        String id=prefs(activity).getString("background","");
        backgroundId=id;
        albumBackground=id.isEmpty()?root:null;
        String videoPath=null;
        if(!id.isEmpty())try{
            File file=AuroraLibrary.file(activity,id,"background");
            if(id.matches(".*\\.(mp4|webm|mkv)")){
                video=new VideoView(activity);backgroundView=video;root.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));video.setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_NONE);
                VideoView view=video;view.setOnPreparedListener(mp->{if(view==video){mp.setVolume(0,0);mp.setLooping(true);view.start();}});videoPath=file.getAbsolutePath();
            }else{
                ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
                options.inSampleSize=1;while(options.outWidth/options.inSampleSize>3840||options.outHeight/options.inSampleSize>2160)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;Bitmap decoded=BitmapFactory.decodeFile(file.getPath(),options);if(decoded==null)throw new java.io.IOException("background decode failed");image.setImageBitmap(decoded);backgroundView=image;root.addView(image,new FrameLayout.LayoutParams(-1,-1));
            }
        }catch(Exception ignored){albumBackground=root;tintBackground();}
        android.util.DisplayMetrics metrics=activity.getResources().getDisplayMetrics();
        scale=Math.min(metrics.widthPixels/1920f,metrics.heightPixels/1080f);
        content=new FrameLayout(activity);content.setBackgroundColor(0x66000000);
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        info=new LinearLayout(activity);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.CENTER_HORIZONTAL);
        int coverSize=Math.round(398*scale);
        FrameLayout.LayoutParams infoBox=new FrameLayout.LayoutParams(coverSize,-2);
        infoBox.leftMargin=Math.round(metrics.widthPixels*.267f-coverSize/2f);
        infoBox.topMargin=Math.round(metrics.heightPixels*(hideCover ? .30f : .20f));
        content.addView(info,infoBox);
        portrait=new ImageView(activity);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.graphics.drawable.GradientDrawable corners=new android.graphics.drawable.GradientDrawable();
        corners.setColor(Color.TRANSPARENT);corners.setCornerRadius(14*scale);
        portrait.setBackground(corners);portrait.setClipToOutline(true);info.addView(portrait,coverSize,coverSize);
        if(hideCover&&backgroundView!=null)info.setVisibility(View.GONE);
        title=caption(info,23*scale);title.setPadding(0,Math.round(10*scale),0,0);
        album=caption(info,18*scale);artist=caption(info,18*scale);
        if(!hideLyrics){
            lyrics=new ScrollingLyricsView(activity,scale);
            lyrics.setStyle(lyricsLayout,lyricsAlign);
            FrameLayout.LayoutParams lines;
            if("bottom-one".equals(lyricsLayout)||"bottom-two".equals(lyricsLayout)){
                int height="bottom-two".equals(lyricsLayout)?Math.round(300*scale):Math.round(190*scale);
                lines=new FrameLayout.LayoutParams(Math.round(metrics.widthPixels*.9f),height,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
                lines.bottomMargin=Math.round(170*scale);
            }else{
                lines=new FrameLayout.LayoutParams(metrics.widthPixels,Math.round(824*scale));
                lines.topMargin=Math.round((metrics.heightPixels-lines.height)/2f);
            }
            content.addView(lyrics,lines);
        }
        if(spectrumEnabled)ensureAudioCapture();
        if(spectrumEnabled)spectrum=new AudioSpectrumView(activity);
        FrameLayout.LayoutParams spectrumBox;
        if("side".equals(lyricsLayout)){
            spectrumBox=new FrameLayout.LayoutParams(coverSize,Math.round(110*scale));
            spectrumBox.leftMargin=infoBox.leftMargin;spectrumBox.topMargin=Math.round(metrics.heightPixels*.74f);
        }else{
            spectrumBox=new FrameLayout.LayoutParams(Math.round(metrics.widthPixels*.55f),Math.round(110*scale),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            spectrumBox.bottomMargin=Math.round(30*scale);
        }
        if(spectrum!=null)content.addView(spectrum,spectrumBox);
        IntentFilter filter=new IntentFilter("com.gathin.tvhome.DESKTOP_LYRICS");String permission="com.gathin.tvhome.permission.DESKTOP_LYRICS";
        if(Build.VERSION.SDK_INT>=33)activity.registerReceiver(receiver,filter,permission,handler,Context.RECEIVER_EXPORTED);else activity.registerReceiver(receiver,filter,permission,handler);
        screen=new Dialog(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen);screen.setContentView(root);
        Dialog opened=screen;
        PersonalBackground background=desktop.get();
        screen.setOnDismissListener(d->{
            activity.unregisterReceiver(receiver);if(lyrics!=null)lyrics.stop();
            if(spectrum!=null){spectrum.stop();spectrum=null;}
            if(video!=null){video.stopPlayback();video=null;}previous=null;screen=null;reset();spectrumHintShown=false;if(background!=null)background.finishFullscreen(opened,running);
        });
        screen.setOnKeyListener((dialog,key,event)->{
            if(event.getAction()==KeyEvent.ACTION_UP)dialog.dismiss();
            return true;
        });
        screen.show();update(controller);
        if(spectrum!=null&&spectrumSettings!=null){spectrum.setSpectrum(spectrumSettings);spectrum.start();spectrum.setPlaying(true);spectrumRunning=true;}
        String path=videoPath;
        Runnable startBackground=()->{if(screen==opened&&running&&video!=null&&path!=null)video.setVideoPath(path);};
        if(background==null)startBackground.run();
        else background.suspendForFullscreen(opened,startBackground);
        writeDebug("open");
    }
}
