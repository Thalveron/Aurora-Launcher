package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.io.File;

public final class AmbientMusicActivity extends Activity {
    private String desktopLocaleAtCreation;
    @Override protected void attachBaseContext(android.content.Context base){desktopLocaleAtCreation=DesktopLanguage.code(base);super.attachBaseContext(DesktopLanguage.wrap(base));}

    private LinearLayout rows;
    private void text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(-1);v.setPadding(0,14,0,14);rows.addView(v);}
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(-1);b.setTextSize(18);
        b.setPadding(20,10,20,10);b.setFocusable(true);
        b.setOnFocusChangeListener((v,has)->style(b,has));style(b,false);
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,(int)(52*getResources().getDisplayMetrics().density));lp.bottomMargin=12;rows.addView(b,lp);return b;
    }
    private void style(Button button,boolean focus){
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(focus?0xFF234451:0xFF242C35);bg.setCornerRadius(18);
        bg.setStroke(focus?4:0,0xFF79E8FF);button.setBackground(bg);
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);render();}
    @Override public void onResume(){super.onResume();if(rows!=null)render();}
    @Override protected void onPause(){
        AmbientMusic.prefs(this).edit().putLong("play_request",android.os.SystemClock.elapsedRealtime()).apply();
        super.onPause();
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xFF101419);
        rows=new LinearLayout(this);rows.setOrientation(1);rows.setPadding(60,32,60,40);scroll.addView(rows);setContentView(scroll);
        android.content.SharedPreferences p=AmbientMusic.prefs(this);
        text(DesktopLanguage.t(AmbientMusicActivity.this,"桌面背景音乐"),28);
        button(DesktopLanguage.t(AmbientMusicActivity.this,"背景音乐：")+(p.getBoolean("enabled",false)?DesktopLanguage.t(AmbientMusicActivity.this,"开启"):DesktopLanguage.t(AmbientMusicActivity.this,"关闭")),()->{p.edit().putBoolean("enabled",!p.getBoolean("enabled",false)).apply();render();});
        button(DesktopLanguage.t(AmbientMusicActivity.this,"播放方式：")+(p.getBoolean("continuous",false)?DesktopLanguage.t(AmbientMusicActivity.this,"持续播放（仅桌面）"):DesktopLanguage.t(AmbientMusicActivity.this,"桌面闲置后播放")),()->new AlertDialog.Builder(this).setTitle(DesktopLanguage.t(AmbientMusicActivity.this,"播放方式")).setSingleChoiceItems(new String[]{DesktopLanguage.t(AmbientMusicActivity.this,"桌面闲置后播放"),DesktopLanguage.t(AmbientMusicActivity.this,"持续播放（仅桌面）")},p.getBoolean("continuous",false)?1:0,(d,i)->{p.edit().putBoolean("continuous",i==1).apply();d.dismiss();render();}).show());
        button(DesktopLanguage.t(AmbientMusicActivity.this,"闲置时间：")+p.getInt("minutes",5)+DesktopLanguage.t(AmbientMusicActivity.this,"分钟"),()->new AlertDialog.Builder(this).setTitle(DesktopLanguage.t(AmbientMusicActivity.this,"多久没有操作后播放？")).setItems(new String[]{DesktopLanguage.t(AmbientMusicActivity.this,"3分钟"),DesktopLanguage.t(AmbientMusicActivity.this,"5分钟"),DesktopLanguage.t(AmbientMusicActivity.this,"8分钟"),DesktopLanguage.t(AmbientMusicActivity.this,"10分钟")},(d,i)->{p.edit().putInt("minutes",new int[]{3,5,8,10}[i]).apply();render();}).show());
        android.media.AudioManager audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
        int maximum=audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        int current=audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        int selectedVolume=p.getInt("volume_index",Math.min(current,3));
        button(DesktopLanguage.t(AmbientMusicActivity.this,"背景音乐实际音量：")+selectedVolume+" / "+maximum,()->{
            LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(36,20,36,20);
            TextView value=new TextView(this);value.setText(DesktopLanguage.t(AmbientMusicActivity.this,"背景音乐：")+selectedVolume+" / "+maximum+DesktopLanguage.t(AmbientMusicActivity.this,"（电视当前：")+audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)+"）");value.setTextSize(22);box.addView(value);
            SeekBar seek=new SeekBar(this);seek.setMax(maximum);seek.setProgress(selectedVolume);seek.setKeyProgressIncrement(1);seek.setFocusableInTouchMode(true);box.addView(seek,new LinearLayout.LayoutParams(-1,80));
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int progress,boolean user){value.setText(DesktopLanguage.t(AmbientMusicActivity.this,"背景音乐：")+progress+" / "+maximum+DesktopLanguage.t(AmbientMusicActivity.this,"（电视实际档位）"));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
            new AlertDialog.Builder(this).setTitle(DesktopLanguage.t(AmbientMusicActivity.this,"电视实际音量 · 每次调整1格")).setView(box).setNegativeButton(DesktopLanguage.t(AmbientMusicActivity.this,"取消"),null).setPositiveButton(DesktopLanguage.t(AmbientMusicActivity.this,"保存"),(d,i)->{p.edit().putInt("volume_index",seek.getProgress()).apply();render();}).show();seek.requestFocus();
        });
        String[] modes={DesktopLanguage.t(AmbientMusicActivity.this,"完整信息"),DesktopLanguage.t(AmbientMusicActivity.this,"极简信息（时间、日期、今天天气）"),DesktopLanguage.t(AmbientMusicActivity.this,"全部隐藏")};
        button(DesktopLanguage.t(AmbientMusicActivity.this,"音乐氛围模式：")+modes[p.getInt("info_mode",0)],()->new AlertDialog.Builder(this).setTitle(DesktopLanguage.t(AmbientMusicActivity.this,"背景音乐播放时显示的信息")).setSingleChoiceItems(modes,p.getInt("info_mode",0),(d,i)->{p.edit().putInt("info_mode",i).apply();d.dismiss();render();}).show());
        button(DesktopLanguage.t(AmbientMusicActivity.this,"无缝交叉混音循环：")+(p.getBoolean("crossfade",true)?DesktopLanguage.t(AmbientMusicActivity.this,"开启"):DesktopLanguage.t(AmbientMusicActivity.this,"关闭")),()->{p.edit().putBoolean("crossfade",!p.getBoolean("crossfade",true)).apply();render();});
        android.content.SharedPreferences appearance=DesktopAppearance.prefs(this);
        button(DesktopLanguage.t(AmbientMusicActivity.this,"频谱透明度 / 不透明程度：")+appearance.getInt("opacity",50)+"%",()->DesktopAppearance.slider(this,DesktopLanguage.t(AmbientMusicActivity.this,"频谱不透明程度（0为全透明）"),"opacity",appearance.getInt("opacity",50),this::render));
        button(DesktopLanguage.t(AmbientMusicActivity.this,"频谱亮度：")+appearance.getInt("brightness",70)+"%",()->DesktopAppearance.slider(this,DesktopLanguage.t(AmbientMusicActivity.this,"频谱亮度"),"brightness",appearance.getInt("brightness",70),this::render));
        button(DesktopLanguage.t(AmbientMusicActivity.this,"频谱颜色 · HSV"),()->DesktopAppearance.picker(this,DesktopLanguage.t(AmbientMusicActivity.this,"频谱颜色"),"spectrum",0xFF69E7FF,this::render));
        button(DesktopLanguage.t(AmbientMusicActivity.this,"全局字体颜色 · HSV"),()->DesktopAppearance.picker(this,DesktopLanguage.t(AmbientMusicActivity.this,"全局字体颜色"),"text",-1,this::render));
        text(DesktopLanguage.t(AmbientMusicActivity.this,"全局字体颜色用于时钟、日期天气、播放卡片、右上角功能与应用名称。本地音频先解码缓存，曲尾和曲首交叉混音，持续输出声音，循环不重启播放器。第一次使用新音频需等待处理；按任意键仍停止。音频本身旋律变化无法完全消除。"),17);
        text(DesktopLanguage.t(AmbientMusicActivity.this,"仅播放电视本地内置或上传的音乐，不会启动音乐软件。播放时隐藏卡片、应用和右上角功能，频谱显示在屏幕底部中央；按任意键停止音乐、恢复桌面。采用电视实际音量档位，退出时先彻底停止背景音轨，再恢复进入前的电视音量，避免音乐残音被放大。开启后保持桌面常亮，关屏或离开桌面时停止。"),17);
        button(DesktopLanguage.t(AmbientMusicActivity.this,"从电脑上传纯音乐 / 管理上传文件"),()->startActivity(new Intent(this,BackgroundActivity.class)));
        text(DesktopLanguage.t(AmbientMusicActivity.this,"本地音乐库"),22);
        String selected=p.getString("selected","");
        button(DesktopLanguage.t(AmbientMusicActivity.this,"内置 · 静夜琴音")+(selected.isEmpty()?" ✓":""),()->{p.edit().remove("selected").apply();render();});
        for(File f:AmbientMusic.items(this)){
            button(AmbientMusic.name(this,f)+(f.getName().equals(selected)?" ✓":""),()->new AlertDialog.Builder(this).setTitle(AmbientMusic.name(this,f)).setItems(new String[]{DesktopLanguage.t(AmbientMusicActivity.this,"设为背景音乐"),DesktopLanguage.t(AmbientMusicActivity.this,"删除")},(d,i)->{if(i==0)p.edit().putString("selected",f.getName()).apply();else AmbientMusic.delete(this,f);render();}).show());
        }
        rows.getChildAt(1).requestFocus();
    }
}
