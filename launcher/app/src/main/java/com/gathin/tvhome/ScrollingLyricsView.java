package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.view.View;
import java.util.Arrays;

/** Seven fixed-height lyric rows; the current row stays centered as its neighbours scroll. */
final class ScrollingLyricsView extends View {
 private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG),mask=new Paint(Paint.ANTI_ALIAS_FLAG);
 private String[] lines=new String[7];
 private String song="";
 private int index=Integer.MIN_VALUE,base=0xFF79E8FF;
 private float progress,offset;
 private ValueAnimator scroll,highlight;
 ScrollingLyricsView(Context c){super(c);Arrays.fill(lines,"");setLayerType(View.LAYER_TYPE_SOFTWARE,null);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 void update(String[] next,int at,float amount,String track,int color){
  if(next==null||next.length!=7)return;
  boolean sameSong=TextUtils.equals(song,track),newRow=at!=index||!sameSong;
  if(newRow){
   if(scroll!=null)scroll.cancel();
   if(sameSong&&at==index+1){
    scroll=ValueAnimator.ofFloat(1,0);scroll.setDuration(400);
    scroll.setInterpolator(new android.view.animation.DecelerateInterpolator());
    scroll.addUpdateListener(a->{offset=(float)a.getAnimatedValue();invalidate();});scroll.start();
   }else offset=0;
  }
  lines=next.clone();song=track;index=at;base=color;
  amount=Float.isNaN(amount)?0:Math.max(0,Math.min(1,amount));
  if(highlight!=null)highlight.cancel();
  if(newRow||Math.abs(amount-progress)>.3f)progress=amount;
  else{
   highlight=ValueAnimator.ofFloat(progress,amount);highlight.setDuration(180);
   highlight.setInterpolator(new android.view.animation.LinearInterpolator());
   highlight.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});highlight.start();
  }
  invalidate();
 }
 void clear(){Arrays.fill(lines,"");index=Integer.MIN_VALUE;stop();invalidate();}
 void stop(){if(scroll!=null)scroll.cancel();if(highlight!=null)highlight.cancel();offset=0;}
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){
  mask.setShader(new LinearGradient(0,0,0,h,new int[]{0x00FFFFFF,0x99FFFFFF,0xFFFFFFFF,0xFFFFFFFF,0x99FFFFFF,0x00FFFFFF},new float[]{0,.1f,.26f,.74f,.9f,1},Shader.TileMode.CLAMP));
  mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
 }
 @Override protected void onDraw(Canvas c){
  int layer=c.saveLayer(0,0,getWidth(),getHeight(),null);
  float dp=getResources().getDisplayMetrics().density,step=getHeight()/7f;
  for(int i=0;i<7;i++){
   String line=lines[i];if(TextUtils.isEmpty(line))continue;
   text.setTypeface(Typeface.create("sans-serif",i==3?Typeface.BOLD:Typeface.NORMAL));
   text.setTextSize((i==3?17:14)*dp);
   float width=text.measureText(line),max=getWidth()-16*dp;
   if(width>max){text.setTextSize(text.getTextSize()*max/width);width=max;}
   float x=(getWidth()-width)/2,y=getHeight()/2f+(i-3+offset)*step-(text.ascent()+text.descent())/2;
   text.setColor(i==3?base:0xFFD4E2EF);text.setAlpha(i==3?255:190);
   text.setShadowLayer(2*dp,0,dp,0xCC000000);c.drawText(line,x,y,text);
   if(i==3&&progress>0){
    float chars=line.length()*progress;int at=Math.min(line.length(),(int)chars);
    float completed=text.measureText(line,0,at);
    if(at<line.length())completed+=text.measureText(line,at,at+1)*(chars-at);
    int save=c.save();c.clipRect(x,y+text.ascent()-dp,x+completed,y+text.descent()+dp);
    text.setColor(getContext().getSharedPreferences(Config.SETTINGS_NAME,0).getInt("lyrics_highlight",0xFFFFD166));text.setAlpha(255);c.drawText(line,x,y,text);c.restoreToCount(save);
   }
  }
  c.drawRect(0,0,getWidth(),getHeight(),mask);c.restoreToCount(layer);
 }
 @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
