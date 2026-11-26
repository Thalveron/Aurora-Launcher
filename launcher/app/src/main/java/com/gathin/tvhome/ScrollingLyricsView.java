package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.view.View;
import java.util.Arrays;

/** Seven fixed-height lyric rows for the side panel, or a bottom strip for the fullscreen player. */
final class ScrollingLyricsView extends View {
 private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG),mask=new Paint(Paint.ANTI_ALIAS_FLAG);
 private String[] lines=new String[7];
 private String song="";
 private int index=Integer.MIN_VALUE,base=0xFF79E8FF;
 private float progress,offset;
 private ValueAnimator scroll,highlight;
 private final float fullscreenScale;
 /** side: the original centred seven-row panel. bottom-one / bottom-two: strips for the fullscreen player. */
 private String layout="side";
 /** 0 centre, 1 left, 2 right: where a line sits inside the view. */
 private int align=0;
 ScrollingLyricsView(Context c){this(c,0);}
 ScrollingLyricsView(Context c,float scale){super(c);fullscreenScale=scale;if(scale>0)lines=new String[8];Arrays.fill(lines,"");setLayerType(View.LAYER_TYPE_SOFTWARE,null);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 /** Fullscreen only: side (default), bottom-one, bottom-two, plus left/centre/right alignment. */
 void setStyle(String value,String alignment){
  if("side".equals(value)||"bottom-one".equals(value)||"bottom-two".equals(value))layout=value;
  align="left".equals(alignment)?1:"right".equals(alignment)?2:0;
  invalidate();
 }
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
  if(fullscreenScale>0){
   if(newRow)lines[0]=sameSong&&at==index+1?lines[1]:"";
   System.arraycopy(next,0,lines,1,7);
  }else lines=next.clone();
  song=track;index=at;base=color;
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
  if(fullscreenScale<=0||"side".equals(layout)){drawSide(c);return;}
  if(index<0)return;
  if("bottom-one".equals(layout)){drawBottomOne(c);return;}
  drawBottomTwo(c);
 }
 private void drawSide(Canvas c){
  int layer=c.saveLayer(0,0,getWidth(),getHeight(),null);
  int current=fullscreenScale>0?4:3;
  float dp=getResources().getDisplayMetrics().density,step=getHeight()/(float)lines.length;
  for(int i=0;i<lines.length;i++){
   String line=lines[i];if(TextUtils.isEmpty(line))continue;
   float size=fullscreenScale>0?(i==current?36:30)*fullscreenScale:(i==current?17:14)*dp;
   float centerY=(i+.5f+offset)*step;
   drawLine(c,line,size,centerY,i==current?base:0xFFD4E2EF,i==current?255:190,align,i==current);
  }
  c.drawRect(0,0,getWidth(),getHeight(),mask);c.restoreToCount(layer);
 }
 private void drawBottomOne(Canvas c){
  int current=fullscreenScale>0?4:3;
  float size=Math.min(getHeight()*.58f,44*fullscreenScale);
  drawLine(c,lines[current],size,getHeight()*.56f,base,255,align,true);
 }
 private void drawBottomTwo(Canvas c){
  int current=fullscreenScale>0?4:3;
  drawLine(c,lines[current],38*fullscreenScale,getHeight()*.30f,base,255,1,true);
  drawLine(c,lines[current+1],27*fullscreenScale,getHeight()*.76f,0xFFD4E2EF,175,2,false);
 }
 private float dp(){return getResources().getDisplayMetrics().density;}
 private void drawLine(Canvas c,String line,float size,float centerY,int color,int alpha,int alignMode,boolean highlight){
  if(TextUtils.isEmpty(line))return;
  text.setTypeface(Typeface.create("sans-serif",highlight?Typeface.BOLD:Typeface.NORMAL));
  text.setTextSize(Math.max(8f,size));
  float max=getWidth()-16*dp(),width=text.measureText(line);
  if(width>max&&width>0){text.setTextSize(text.getTextSize()*max/width);width=max;}
  float x=alignMode==1?8*dp():alignMode==2?getWidth()-width-8*dp():(getWidth()-width)/2f;
  float y=centerY-(text.ascent()+text.descent())/2f;
  text.setColor(color);text.setAlpha(alpha);text.setShadowLayer(2*dp(),0,dp(),0xCC000000);
  c.drawText(line,x,y,text);
  if(highlight&&progress>0){
   float chars=line.length()*progress;int at=Math.min(line.length(),(int)chars);
   float completed=text.measureText(line,0,at);
   if(at<line.length())completed+=text.measureText(line,at,at+1)*(chars-at);
   int save=c.save();c.clipRect(x,y+text.ascent()-dp(),x+completed,y+text.descent()+dp());
   text.setColor(getContext().getSharedPreferences(Config.SETTINGS_NAME,0).getInt("lyrics_highlight",0xFFFFD166));text.setAlpha(255);
   c.drawText(line,x,y,text);c.restoreToCount(save);
  }
 }
 @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
