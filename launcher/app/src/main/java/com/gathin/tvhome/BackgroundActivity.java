package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;

public final class BackgroundActivity extends Activity {
    private String desktopLocaleAtCreation;
    @Override protected void attachBaseContext(android.content.Context base){desktopLocaleAtCreation=DesktopLanguage.code(base);super.attachBaseContext(DesktopLanguage.wrap(base));}

    private final android.content.BroadcastReceiver uploadChanged = new android.content.BroadcastReceiver() {
        public void onReceive(android.content.Context c, android.content.Intent i) { refreshGallery(); status.setText("usb".equals(i.getStringExtra("kind")) ? DesktopLanguage.t(BackgroundActivity.this,"歌曲已保存到U盘，可在播放器重新扫描。") : DesktopLanguage.t(BackgroundActivity.this,"上传完成，媒体库已更新。")); }
    };
    private TextView address, status;
    private LinearLayout gallery;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(0xFF101419);
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(40 * getResources().getDisplayMetrics().density); rows.setPadding(pad, pad, pad, pad);
        scroll.addView(rows); setContentView(scroll);
        text(rows, DesktopLanguage.t(BackgroundActivity.this,"背景管理"), 30);
        text(rows, DesktopLanguage.t(BackgroundActivity.this,"电脑和电视连接同一局域网，打开下面地址上传背景、纯音乐或U盘歌曲。开机自动接收，退出此页也能上传。"), 18);
        address = text(rows, DesktopLanguage.t(BackgroundActivity.this,"正在准备上传地址…"), 19); address.setTextColor(0xFFB8E4D0);
        status = text(rows, DesktopLanguage.t(BackgroundActivity.this,"登录账号和密码请在网页安全设置管理。"), 17);
        button(rows, DesktopLanguage.t(BackgroundActivity.this,"刷新背景库"), this::refreshGallery);
        button(rows, DesktopLanguage.t(BackgroundActivity.this,"背景音乐设置与本地音乐库"), () -> startActivity(new android.content.Intent(this, AmbientMusicActivity.class)));
        button(rows, DesktopLanguage.t(BackgroundActivity.this,"恢复默认背景"), () -> { BackgroundStore.select(this, "default"); status.setText(DesktopLanguage.t(BackgroundActivity.this,"已恢复默认背景，按返回键查看。")); });
        button(rows, DesktopLanguage.t(BackgroundActivity.this,"检查上传服务 / 刷新地址"), this::startServer);
        text(rows, DesktopLanguage.t(BackgroundActivity.this,"U盘歌曲上传：电脑网页选择已插入的U盘，可批量上传。歌曲保存到 U盘 /Music/极简桌面上传，不覆盖已有文件。上传后在播放器重新扫描本地歌曲。"), 17);
        text(rows, DesktopLanguage.t(BackgroundActivity.this,"图片：JPG / PNG / WebP，20 MB 内。视频：H.264 / H.265 MP4，30 秒内、支持 4K / 8K 等分辨率，能否播放取决于电视，80 MB 内。视频只在桌面可见时静音循环。"), 16);
        text(rows, DesktopLanguage.t(BackgroundActivity.this,"背景库 · 选择预览可放大，选择使用可切换背景"), 22);
        gallery = new LinearLayout(this); gallery.setOrientation(LinearLayout.VERTICAL); rows.addView(gallery); refreshGallery();
        rows.getChildAt(4).requestFocus();
    }
    @Override protected void onResume() {
        super.onResume();
        android.content.IntentFilter filter=new android.content.IntentFilter("com.gathin.tvhome.UPLOAD_CHANGED");
        if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(uploadChanged,filter,android.content.Context.RECEIVER_NOT_EXPORTED);else registerReceiver(uploadChanged,filter);
        startServer();
    }
    private void startServer() {
        try {
            UploadService.ensureStarted(this);
            String url = BackgroundUploadServer.address(this);
            address.setText(url);
            java.io.File folder = getExternalFilesDir(null);
            if (folder != null) try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(folder, "background-upload-address.txt"))) {
                out.write(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception failure) { android.util.Log.w("TvHome","Background upload could not start",failure); address.setText(DesktopLanguage.t(BackgroundActivity.this,"请确认已连接网络，再刷新地址。")); }
    }
    @Override protected void onPause() { unregisterReceiver(uploadChanged); super.onPause(); }
    private void refreshGallery() {
        gallery.removeAllViews();
        java.util.List<java.io.File> files = BackgroundStore.items(this);
        if(files.isEmpty()) { text(gallery,DesktopLanguage.t(BackgroundActivity.this,"还没有背景，请从电脑上传。"),18); return; }
        String selected=getSharedPreferences("background",0).getString("selected", "");
        for(java.io.File file : files) {
            LinearLayout row=new LinearLayout(this); row.setPadding(0,12,0,12); gallery.addView(row);
            ImageView thumb=new ImageView(this); thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumb.setImageBitmap(android.graphics.BitmapFactory.decodeFile(BackgroundStore.thumbnail(this,file).getPath()));
            row.addView(thumb,new LinearLayout.LayoutParams(300,170));
            LinearLayout controls=new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL); row.addView(controls,new LinearLayout.LayoutParams(-1,-2));
            String kind=BackgroundStore.kind(file);
            String label=(kind.equals("video")?DesktopLanguage.t(BackgroundActivity.this,"视频"):DesktopLanguage.t(BackgroundActivity.this,"图片"))+" · "+new java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.CHINA).format(new java.util.Date(file.lastModified()));
            if(file.getName().equals(selected) && !BackgroundStore.mode(this).equals("default")) label+=DesktopLanguage.t(BackgroundActivity.this," · 正在使用");
            text(controls,label,18);
            LinearLayout actions=new LinearLayout(this);controls.addView(actions);
            action(actions,DesktopLanguage.t(BackgroundActivity.this,"预览"),()->preview(file));
            action(actions,DesktopLanguage.t(BackgroundActivity.this,"使用"),()->{ BackgroundStore.selectFile(this,file);status.setText(DesktopLanguage.t(BackgroundActivity.this,"已切换背景，按返回键查看。"));refreshGallery(); });
            action(actions,DesktopLanguage.t(BackgroundActivity.this,"删除"),()->new android.app.AlertDialog.Builder(this).setMessage(DesktopLanguage.t(BackgroundActivity.this,"删除这项背景？")).setNegativeButton(DesktopLanguage.t(BackgroundActivity.this,"取消"),null).setPositiveButton(DesktopLanguage.t(BackgroundActivity.this,"删除"),(d,w)->{BackgroundStore.delete(this,file);refreshGallery();status.setText(DesktopLanguage.t(BackgroundActivity.this,"已删除。"));}).show());
        }
    }
    private void action(LinearLayout row,String title,Runnable run) {
        Button b=new Button(this);b.setText(title);b.setOnClickListener(v->run.run());row.addView(b,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void preview(java.io.File file) {
        android.app.Dialog dialog=new android.app.Dialog(this);
        if(BackgroundStore.kind(file).equals("video")) {
            VideoView video=new VideoView(this);dialog.setContentView(video);video.setVideoPath(file.getPath());
            video.setOnPreparedListener(m->{m.setLooping(true);m.setVolume(0,0);video.start();});dialog.setOnDismissListener(d->video.stopPlayback());
        } else {
            ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
            android.graphics.BitmapFactory.decodeFile(file.getPath(),options);options.inSampleSize=1;
            while(options.outWidth/options.inSampleSize>1920 || options.outHeight/options.inSampleSize>1080)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;options.inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;
            image.setImageBitmap(android.graphics.BitmapFactory.decodeFile(file.getPath(),options));dialog.setContentView(image);
        }
        dialog.show(); dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.8),(int)(getResources().getDisplayMetrics().heightPixels*.8));
    }
    private void choose(String kind) {
        if (!BackgroundStore.file(this, kind).isFile()) { status.setText(DesktopLanguage.t(BackgroundActivity.this,"还没有上传") + ("video".equals(kind) ? DesktopLanguage.t(BackgroundActivity.this,"视频") : DesktopLanguage.t(BackgroundActivity.this,"图片")) + DesktopLanguage.t(BackgroundActivity.this,"，请先从电脑上传。")); return; }
        BackgroundStore.select(this, kind); status.setText(DesktopLanguage.t(BackgroundActivity.this,"已选择，按返回键查看。"));
    }
    private TextView text(LinearLayout parent, String content, int size) {
        TextView t = new TextView(this); t.setText(content); t.setTextColor(Color.WHITE); t.setTextSize(size);
        t.setPadding(0, 0, 0, 20); parent.addView(t); return t;
    }
    private void button(LinearLayout parent, String title, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = 12; parent.addView(b, p);
    }
}
