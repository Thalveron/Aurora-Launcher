package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.Button;

/** Vector controls stay sharp at TV resolutions and retain spoken labels. */
final class MusicIconButton extends Button {
    private String label = "";
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    MusicIconButton(Context context) { super(context); }
    @Override public void setText(CharSequence text, BufferType type) {
        if(label==null)label="";
        if(text!=null&&text.length()>0)label=text.toString();
        String[] zh={"播放","暂停","上一首","下一首","单曲循环","随机播放","顺序循环","播放列表","连接中"};for(String z:zh)if(label.equals(DesktopLanguage.t(getContext(),z)))label=z;
        setContentDescription(DesktopLanguage.t(getContext(),label.replace("•", "")));
        super.setText("", type);
        invalidate();
    }
    private void line(Canvas c,float x,float y,float xx,float yy){c.drawLine(x,y,xx,yy,ink);}
    private void triangle(Canvas c,float x,float y,float xx,float yy,float xxx,float yyy){
        Path p=new Path();p.moveTo(x,y);p.lineTo(xx,yy);p.lineTo(xxx,yyy);p.close();
        ink.setStyle(Paint.Style.FILL);c.drawPath(p,ink);ink.setStyle(Paint.Style.STROKE);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        String name=label==null?"":label;
        canvas.save();float size=26*getResources().getDisplayMetrics().density;
        canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/32,size/32);
        ink.setColor(isFocused()||name.startsWith("•")?0xFF79E8FF:DesktopAppearance.text(getContext()));
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(2.6f);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);
        if(name.contains("暂停")||name.equals("播放")){
            ink.setStrokeWidth(1);canvas.drawCircle(16,16,15,ink);ink.setStrokeWidth(2.6f);
        }
        if(name.contains("上一首")||name.contains("下一首")){
            if(name.contains("上一首")){line(canvas,7,7,7,25);triangle(canvas,25,7,10,16,25,25);}
            else{line(canvas,25,7,25,25);triangle(canvas,7,7,22,16,7,25);}
        }else if(name.contains("暂停")){line(canvas,11,8,11,24);line(canvas,21,8,21,24);}
        else if(name.equals("播放")){triangle(canvas,10,6,26,16,10,26);}
        else if(name.contains("随机")&&!name.contains("播放列表")){
            Path p=new Path();p.moveTo(4,8);p.cubicTo(15,8,15,24,27,24);canvas.drawPath(p,ink);
            p.reset();p.moveTo(4,24);p.cubicTo(15,24,15,8,27,8);canvas.drawPath(p,ink);
            line(canvas,24,4,28,8);line(canvas,28,8,24,12);line(canvas,24,20,28,24);line(canvas,28,24,24,28);
        }else if(name.contains("播放列表")){
            line(canvas,4,7,26,7);line(canvas,4,14,20,14);line(canvas,4,21,16,21);
            Path p=new Path();p.moveTo(27,15);p.lineTo(27,25);p.lineTo(20,25);canvas.drawPath(p,ink);
            line(canvas,23,22,20,25);line(canvas,20,25,23,28);
        }else if(name.contains("循环")){
            Path p=new Path();p.moveTo(6,13);p.lineTo(6,9);p.quadTo(6,6,10,6);p.lineTo(26,6);canvas.drawPath(p,ink);
            line(canvas,22,2,26,6);line(canvas,26,6,22,10);
            p.reset();p.moveTo(26,19);p.lineTo(26,23);p.quadTo(26,26,22,26);p.lineTo(6,26);canvas.drawPath(p,ink);
            line(canvas,10,22,6,26);line(canvas,6,26,10,30);
            if(name.contains("单曲")){ink.setStyle(Paint.Style.FILL);ink.setTextSize(13);ink.setTextAlign(Paint.Align.CENTER);canvas.drawText("1",16,21,ink);}
        }else if(name.contains("连接")){canvas.drawArc(5,5,27,27,20,270,false,ink);}
        else {ink.setStyle(Paint.Style.FILL);ink.setTextSize(25);ink.setTextAlign(Paint.Align.CENTER);canvas.drawText("⚙",16,25,ink);}
        canvas.restore();
    }
}
