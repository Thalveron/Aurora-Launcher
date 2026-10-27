package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small native vector weather symbols; independent of the TV's emoji font. */
final class WeatherIcon extends Drawable {
    private final int code;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    WeatherIcon(int code){this.code=code;}
    static String description(int code) {
        if(code==0)return "晴";if(code==1)return "晴间多云";if(code==2)return "多云";if(code==3)return "阴";
        if(code==45||code==48)return "雾";
        if(code==51||code==53||code==55)return "毛毛雨";
        if(code==56||code==57||code==66||code==67)return "冻雨";
        if(code==61)return "小雨";if(code==63)return "中雨";if(code==65)return "大雨";
        if(code==71||code==73||code==75||code==77)return "雪";
        if(code==80||code==81||code==82)return "阵雨";
        if(code==85||code==86)return "阵雪";
        if(code==95)return "雷雨";if(code==97)return "强雷雨";if(code==96||code==99)return "雷雨伴冰雹";
        return "天气未知";
    }
    private void color(int color){paint.setColor(color);paint.setShader(null);paint.setStyle(Paint.Style.FILL);}
    private void sun(Canvas c,float x,float y,float r) {
        color(0xFFFFC34F);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(x+(float)Math.cos(a)*(r+2),y+(float)Math.sin(a)*(r+2),x+(float)Math.cos(a)*(r+4),y+(float)Math.sin(a)*(r+4),paint);}
        paint.setStyle(Paint.Style.FILL);paint.setShader(new LinearGradient(x,y-r,x,y+r,0xFFFFE49A,0xFFFFB52B,Shader.TileMode.CLAMP));c.drawCircle(x,y,r,paint);paint.setShader(null);
    }
    private void cloud(Canvas c,boolean dark) {
        paint.setShader(new LinearGradient(0,9,0,24,dark?0xFFD1D9E3:0xFFFFFFFF,dark?0xFF8F9FB4:0xFFCDD8E9,Shader.TileMode.CLAMP));paint.setStyle(Paint.Style.FILL);
        c.drawCircle(10,18,5,paint);c.drawCircle(16,14,7,paint);c.drawCircle(23,18,5,paint);c.drawRoundRect(7,17,27,23,3,3,paint);paint.setShader(null);
    }
    @Override public void draw(Canvas canvas) {
        int save=canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/32f,b.height()/32f);
        String desc=description(code);
        if(code==0)sun(canvas,16,16,6);
        else if(code==1||code==2){sun(canvas,12,11,4);cloud(canvas,false);}
        else if(code==3)cloud(canvas,true);
        else if(code==45||code==48){cloud(canvas,true);color(0xFFC5D1DF);paint.setStrokeWidth(1.5f);paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawLine(6,26,25,26,paint);canvas.drawLine(9,29,28,29,paint);}
        else if(!desc.equals("天气未知")) {
            cloud(canvas,true);
            if(code>=95){color(0xFFFFD24F);Path bolt=new Path();bolt.moveTo(17,20);bolt.lineTo(12,27);bolt.lineTo(17,27);bolt.lineTo(14,32);bolt.lineTo(23,23);bolt.lineTo(18,23);bolt.close();canvas.drawPath(bolt,paint);}
            else if((code>=71&&code<=77)||code==85||code==86){color(0xFFDEF2FF);paint.setStrokeWidth(1.3f);for(int x:new int[]{10,21}){canvas.drawLine(x-2,28,x+2,28,paint);canvas.drawLine(x,26,x,30,paint);canvas.drawLine(x-1.5f,26.5f,x+1.5f,29.5f,paint);}}
            else {color(0xFF72C3FF);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);for(int x:new int[]{10,17,24})canvas.drawLine(x,25,x-2,29,paint);}
        } else {color(0xFFD5DFEA);paint.setTextSize(23);paint.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("?",9,25,paint);}
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
