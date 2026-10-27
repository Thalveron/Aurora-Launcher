package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.text.Layout;
import android.text.TextUtils;
import android.widget.TextView;

/** Paints completed lyrics over the base colour without changing text layout. */
final class KaraokeTextView extends TextView {
    private float fraction;
    private ValueAnimator animator;
    KaraokeTextView(Context context){super(context);}
    void updateLine(String line,float next){
        next=Float.isNaN(next)?0:Math.max(0,Math.min(1,next));
        boolean changed=!TextUtils.equals(getText(),line);
        if(changed)setText(line);
        stop();
        if(changed||Math.abs(next-fraction)>.25f){fraction=next;invalidate();return;}
        if(Math.abs(next-fraction)<.001f)return;
        animator=ValueAnimator.ofFloat(fraction,next);animator.setDuration(180);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(a->{fraction=(float)a.getAnimatedValue();invalidate();});animator.start();
    }
    void stop(){if(animator!=null){animator.cancel();animator=null;}}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);Layout layout=getLayout();if(layout==null||fraction<=0||getText().length()==0)return;
        float chars=getText().length()*fraction;Path completed=new Path();
        for(int i=0;i<layout.getLineCount();i++){
            int start=layout.getLineStart(i),end=layout.getLineEnd(i);if(chars<=start)break;
            float left=layout.getLineLeft(i),right=layout.getLineRight(i);
            if(chars<end){int at=Math.max(start,Math.min(end,(int)chars));float a=layout.getPrimaryHorizontal(at);float b=at<end?layout.getPrimaryHorizontal(at+1):a;right=a+(b-a)*(chars-at);}
            completed.addRect(left,layout.getLineTop(i),Math.max(left,right),layout.getLineBottom(i),Path.Direction.CW);
        }
        int saved=canvas.save();canvas.translate(getCompoundPaddingLeft()-getScrollX(),getExtendedPaddingTop()-getScrollY());canvas.clipPath(completed);
        int original=getPaint().getColor();getPaint().setColor(0xFFFFD166);layout.draw(canvas);getPaint().setColor(original);canvas.restoreToCount(saved);
    }
}
