package com.gathin.tvhome;
import android.content.Context;import android.graphics.*;import android.view.View;
/** Drawn over the existing desktop/saver: no theme reload, focus change or screenshot copy. */
final class StandbyCountdownView extends View {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private long deadline;
 StandbyCountdownView(Context c){super(c);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 void deadline(long value){deadline=value;invalidate();}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);long now=android.os.SystemClock.uptimeMillis();int seconds=StandbyTiming.seconds(deadline,now);
  float fade=Math.max(0,Math.min(1,(2000f-(deadline-now))/2000f));
  canvas.drawColor(Color.argb((int)(100+155*fade),0,5,18));
  int layer=canvas.saveLayerAlpha(0,0,getWidth(),getHeight(),(int)(255*(1-fade)));
  float h=getHeight(),x=getWidth()/2f,y=h*.38f,r=h*.155f,stroke=h*.012f;
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0x405C7DAA);
  canvas.drawCircle(x,y,r,paint);
  int color=seconds<=10?0xFFFFA351:0xFFBCEDFF;paint.setColor(color);
  canvas.drawArc(new RectF(x-r,y-r,x+r,y+r),-90,360f*Math.max(0,Math.min(60,seconds))/60f,false,paint);
  paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(h*.095f);
  Paint.FontMetrics fm=paint.getFontMetrics();canvas.drawText(StandbyTiming.label(seconds),x,y-(fm.ascent+fm.descent)/2,paint);
  paint.setTextSize(h*.043f);canvas.drawText(DesktopLanguage.t(getContext(),"即将进入待机"),x,h*.66f,paint);
  paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(h*.028f);paint.setColor(0xFFD8E6F4);
  canvas.drawText(DesktopLanguage.t(getContext(),"按任意键可取消"),x,h*.72f,paint);canvas.restoreToCount(layer);
  if(deadline>now){if(deadline-now<=2000)postInvalidateOnAnimation();else postInvalidateDelayed(Math.max(1,(deadline-now)%1000+1));}
 }
}
