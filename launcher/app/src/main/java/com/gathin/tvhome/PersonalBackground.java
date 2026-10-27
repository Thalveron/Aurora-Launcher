package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
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
    private TextureView texture;
    private MediaPlayer player;
    private Surface surface;
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
    private void rotateSlide(){if(!active)return;java.util.List<File> files=playlist();if(files.size()<2)return;slide=files.get((++slideIndex)%files.size());clearViews();show();if(active&&texture!=null&&texture.isAvailable())startVideo(texture.getSurfaceTexture());scheduleSlides();}
    private void scheduleSlides(){handler.removeCallbacks(rotate);if(active&&playlist().size()>1)handler.postDelayed(rotate,(AuroraBackgrounds.active(activity)?AuroraBackgrounds.seconds(activity,ambient):activity.getSharedPreferences("background",0).getInt("slides_seconds",8))*1000L);}
    private String mode(){if(AuroraBackgrounds.active(activity)){File f=selected();return f.isFile()?BackgroundStore.kind(f):"default";}return slide!=null?BackgroundStore.kind(slide):BackgroundStore.mode(activity,ambient);}
    private File selected(){if(slide!=null)return slide;if(AuroraBackgrounds.active(activity)){java.util.List<File> files=playlist();return files.isEmpty()?new File(activity.getFilesDir(),"no-selected-background"):files.get(0);}return BackgroundStore.file(activity,mode(),ambient);}
    private String key(){return mode()+":"+selected().getName();}
    void setAmbient(boolean value){if(ambient==value)return;ambient=value;slide=null;slideIndex=0;java.util.List<File> files=playlist();if(!files.isEmpty())slide=files.get(0);scheduleSlides();if(!contentKey.equals(key())){clearViews();show();if(active&&texture!=null&&texture.isAvailable())startVideo(texture.getSurfaceTexture());}}
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
        if(texture!=null&&texture.isAvailable())startVideo(texture.getSurfaceTexture());
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
                    image.setImageBitmap(bitmap); poster = image; add(image);
                }
            }
            texture = new TextureView(activity); texture.setOpaque(false); texture.setAlpha(0f); media = texture; add(texture);
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { if (active) startVideo(s); }
                public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { crop(); }
                public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { releasePlayer(); return true; }
                public void onSurfaceTextureUpdated(SurfaceTexture s) {}
            });
        }
        if (media != null) { shade = new View(activity); shade.setBackgroundColor(externalDim?0x00000000:0x55000000); add(shade); }
    }
    Bitmap glassSnapshot() {
        if (!active) return null;
        if (texture != null && texture.isAvailable()) return texture.getBitmap(128,72);
        if (bitmap == null || bitmap.isRecycled()) return null;
        Bitmap result=Bitmap.createBitmap(128,72,Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c=new android.graphics.Canvas(result);
        float scale=Math.max(128f/bitmap.getWidth(),72f/bitmap.getHeight());
        float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
        c.drawBitmap(bitmap,null,new android.graphics.RectF((128-w)/2,(72-h)/2,(128+w)/2,(72+h)/2),new android.graphics.Paint(3));
        return result;
    }
    private void startVideo(SurfaceTexture target) {
        if (!active || player != null) return;
        try {
            texture.setVisibility(View.VISIBLE);
            texture.setAlpha(0f);
            if (poster != null) poster.setVisibility(View.VISIBLE);
            player = new MediaPlayer(); final MediaPlayer current = player;
            current.setAudioStreamType(android.media.AudioManager.STREAM_SYSTEM);
            surface = new Surface(target); current.setSurface(surface);
            current.setDataSource(selected().getPath());
            current.setLooping(true); current.setVolume(0f, 0f);
            current.setOnInfoListener((p, what, extra) -> {
                if (player == p && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    texture.setAlpha(1f);
                    ready = true;
                    if (poster != null) poster.setVisibility(View.INVISIBLE);
                }
                return false;
            });
            current.setOnPreparedListener(p -> {
                if (player != p) return;
                if (!active) { releasePlayer(); return; }
                videoWidth = p.getVideoWidth(); videoHeight = p.getVideoHeight(); crop(); p.start();
            });
            current.setOnVideoSizeChangedListener((p, w, h) -> { if(player!=p)return;videoWidth = w; videoHeight = h; crop(); });
            current.setOnErrorListener((p, what, extra) -> { if(player==p)videoFailed(); return true; });
            current.prepareAsync();
        } catch (Exception failure) { videoFailed(); }
    }
    private void videoFailed() {
        ready = true;
        releasePlayer();
        if (texture != null) texture.setVisibility(View.INVISIBLE);
        android.widget.Toast.makeText(activity, "电视无法播放这段视频，请在背景管理里换一个。", android.widget.Toast.LENGTH_LONG).show();
    }
    private void crop() {
        if (texture == null || texture.getWidth() == 0 || texture.getHeight() == 0 || videoWidth <= 0 || videoHeight <= 0) return;
        float w = texture.getWidth(), h = texture.getHeight();
        float scale = Math.max(w / videoWidth, h / videoHeight);
        Matrix transform = new Matrix();
        transform.setScale(videoWidth * scale / w, videoHeight * scale / h, w / 2, h / 2);
        texture.setTransform(transform);
    }
    private void releasePlayer() {
        MediaPlayer old=player;Surface oldSurface=surface;player=null;surface=null;
        if(old!=null||oldSurface!=null)releaser.execute(()->{
            try{if(old!=null)old.release();}catch(RuntimeException ignored){}
            finally{if(oldSurface!=null)oldSurface.release();}
        });
    }
    private void clearViews() {
        releasePlayer();
        if (media != null) root.removeView(media);
        if (shade != null) root.removeView(shade);
        if (poster != null) root.removeView(poster);
        media = null; shade = null; texture = null; poster = null;
        if (bitmap != null) { bitmap.recycle(); bitmap = null; }
    }
}
