package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaPlayer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.File;

/** Own media is displayed only while Home is visible; video decoding is released on pause. */
final class PersonalBackground {
    // Vendor decoders can block release during prepare; never stall Home/input dispatch.
    private static final java.util.concurrent.ExecutorService releaser=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"aurora-video-release");t.setDaemon(true);return t;});
    private final Activity activity;
    private final FrameLayout root;
    private View media, shade, poster;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable delayedRelease = () -> { if (!this.active) releasePlayer(); };
    private SurfaceView surfaceView;
    private boolean surfaceReady;
    private MediaPlayer player;

    private Bitmap bitmap;
    private boolean active;
    private boolean ready;
    boolean isReady() { return ready; }
    private boolean ambient;private boolean externalDim;
    void useExternalDim(){externalDim=true;if(shade!=null)shade.setBackgroundColor(0x00000000);}
    private String contentKey="";
    private File slide;private int slideIndex;
    private final Runnable rotate=this::rotateSlide;
    private java.util.List<File> playlist(){return AuroraBackgrounds.active(activity)?AuroraBackgrounds.files(activity,ambient):ambient?AmbientSlideshow.files(activity):java.util.Collections.emptyList();}
    private void rotateSlide(){if(!active)return;java.util.List<File> files=playlist();if(files.size()<2)return;slide=files.get((++slideIndex)%files.size());clearViews();show();if(active&&surfaceReady)startVideo();scheduleSlides();}
    private void scheduleSlides(){handler.removeCallbacks(rotate);if(active&&fullscreenOwner==null&&playlist().size()>1)handler.postDelayed(rotate,(AuroraBackgrounds.active(activity)?AuroraBackgrounds.seconds(activity,ambient):activity.getSharedPreferences("background",0).getInt("slides_seconds",8))*1000L);}
    private String mode(){if(AuroraBackgrounds.active(activity)){File f=selected();return f.isFile()?BackgroundStore.kind(f):"default";}return slide!=null?BackgroundStore.kind(slide):BackgroundStore.mode(activity,ambient);}
    private File selected(){if(slide!=null)return slide;if(AuroraBackgrounds.active(activity)){java.util.List<File> files=playlist();return files.isEmpty()?new File(activity.getFilesDir(),"no-selected-background"):files.get(0);}return BackgroundStore.file(activity,mode(),ambient);}
    private String key(){return mode()+":"+selected().getName();}
    void setAmbient(boolean value){if(ambient==value)return;ambient=value;slide=null;slideIndex=0;java.util.List<File> files=playlist();if(!files.isEmpty())slide=files.get(0);scheduleSlides();if(!contentKey.equals(key())){clearViews();show();if(active&&surfaceReady)startVideo();}}
    private long revision = -1;
    private int videoWidth, videoHeight;
    PersonalBackground(Activity a, FrameLayout root) {
        this.activity = a; this.root = root;
        revision = a.getSharedPreferences("background", 0).getLong("revision", 0);
        show();
    }
    void resume() {
        active = true;
        handler.removeCallbacks(delayedRelease);
        scheduleSlides();
        long next = activity.getSharedPreferences("background", 0).getLong("revision", 0);
        if (next != revision) { revision = next;slide=null;slideIndex=0;java.util.List<File> files=playlist();if(!files.isEmpty())slide=files.get(0);scheduleSlides();if(!contentKey.equals(key())){clearViews();show();} }
        if(surfaceReady)startVideo();
    }
    private Object fullscreenOwner;
    void suspendForFullscreen(Object owner,Runnable released){
        fullscreenOwner=owner;
        handler.removeCallbacks(rotate);
        releasePlayer();
        releaser.execute(()->handler.post(()->{if(fullscreenOwner==owner)released.run();}));
    }
    void finishFullscreen(Object owner,boolean foreground){
        if(fullscreenOwner!=owner)return;
        fullscreenOwner=null;
        if(foreground&&active)resume();
    }
    void pause() { active = false; handler.removeCallbacks(rotate); handler.postDelayed(delayedRelease, 400); }
    void close() { active = false; handler.removeCallbacks(rotate); handler.removeCallbacks(delayedRelease); clearViews(); }
    private void add(View view) {
        root.addView(view, root.indexOfChild(root.findViewById(R.id.sky)),
                new FrameLayout.LayoutParams(-1, -1));
    }
    private void show() {
        ready = true;
        String mode = mode();
        File file = selected();
        contentKey=key();
        if ("default".equals(mode) || !file.isFile()) return;
        if ("image".equals(mode)) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), o);
                int sample = 1;
                int displayW = activity.getResources().getDisplayMetrics().widthPixels;
                int displayH = activity.getResources().getDisplayMetrics().heightPixels;
                while (o.outWidth / sample > Math.min(4096, displayW) || o.outHeight / sample > Math.min(4096, displayH)) sample *= 2;
                o.inJustDecodeBounds = false; o.inSampleSize = sample; o.inPreferredConfig = Bitmap.Config.RGB_565;
                bitmap = BitmapFactory.decodeFile(file.getPath(), o);
                if (bitmap == null) return;
                ImageView image = new ImageView(activity); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(bitmap); media = image; add(image);
            } catch (RuntimeException | OutOfMemoryError failure) { return; }
        } else if ("video".equals(mode)) {
            ready = false;
            File preview = new File(file.getPath()+".jpg");
            if (preview.isFile()) {
                bitmap = BitmapFactory.decodeFile(preview.getPath());
                if (bitmap != null) {
                    ImageView image = new ImageView(activity);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setImageBitmap(bitmap); poster = image;
                }
            }
            // SurfaceView uses the TV's hardware video plane, so 4K clips no longer go through the GPU.
            surfaceView = new SurfaceView(activity);
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                public void surfaceCreated(SurfaceHolder holder) { surfaceReady = true; if (active) startVideo(); }
                public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { }
                public void surfaceDestroyed(SurfaceHolder holder) { surfaceReady = false; releasePlayer(); }
            });
            media = surfaceView; add(surfaceView);
            if (poster != null) { root.removeView(poster); add(poster); }
        }
        if (media != null) { shade = new View(activity); shade.setBackgroundColor(externalDim?0x00000000:0x55000000); add(shade); }
    }
    Bitmap glassSnapshot() {
        if (!active) return null;
        if (bitmap == null || bitmap.isRecycled()) return null;
        Bitmap result=Bitmap.createBitmap(128,72,Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c=new android.graphics.Canvas(result);
        float scale=Math.max(128f/bitmap.getWidth(),72f/bitmap.getHeight());
        float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
        c.drawBitmap(bitmap,null,new android.graphics.RectF((128-w)/2,(72-h)/2,(128+w)/2,(72+h)/2),new android.graphics.Paint(3));
        return result;
    }
    private void startVideo() {
        if (!active || fullscreenOwner!=null || player != null || !surfaceReady) return;
        try {
            if (poster != null) poster.setVisibility(View.VISIBLE);
            player = new MediaPlayer(); final MediaPlayer current = player;
            current.setAudioStreamType(android.media.AudioManager.STREAM_SYSTEM);
            current.setDisplay(surfaceView.getHolder());
            current.setDataSource(selected().getPath());
            current.setLooping(true); current.setVolume(0f, 0f);
            current.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            current.setOnInfoListener((p, what, extra) -> {
                if (player == p && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    ready = true;
                    if (poster != null) poster.setVisibility(View.INVISIBLE);
                }
                return false;
            });
            current.setOnPreparedListener(p -> {
                if (player != p) return;
                if (!active) { releasePlayer(); return; }
                p.start();
            });
            current.setOnErrorListener((p, what, extra) -> { if(player==p)videoFailed(); return true; });
            current.prepareAsync();
        } catch (Exception failure) { videoFailed(); }
    }
    private void videoFailed() {
        ready = true;
        releasePlayer();
        if (surfaceView != null) surfaceView.setVisibility(View.INVISIBLE);
        if (poster != null) poster.setVisibility(View.VISIBLE);
        android.widget.Toast.makeText(activity, "电视无法播放这段视频，请在背景管理里换一个。", android.widget.Toast.LENGTH_LONG).show();
    }
    private void releasePlayer() {
        MediaPlayer old=player;player=null;
        if(old!=null)releaser.execute(()->{try{old.release();}catch(RuntimeException ignored){} });
    }
    private void clearViews() {
        releasePlayer();
        if (media != null) root.removeView(media);
        if (shade != null) root.removeView(shade);
        if (poster != null) root.removeView(poster);
        media = null; shade = null; surfaceView = null; surfaceReady = false; poster = null;
        if (bitmap != null) { bitmap.recycle(); bitmap = null; }
    }
}
