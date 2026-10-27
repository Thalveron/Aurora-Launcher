package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final PersonalBackground background;
    private android.graphics.Bitmap blurred;
    private long sampled;
    private final Paint imagePaint = new Paint(3);
    private final int[] location = new int[2];
    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    DockDecoration(int dockSize, float density, PersonalBackground background) {
        this.background=background;
        this.dockSize = dockSize;
        this.density = density;
        band.setColor(0x55303C50);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(0x66FFFFFF);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (parent.hasWindowFocus() && android.os.SystemClock.uptimeMillis()-sampled>900) {
            sampled=android.os.SystemClock.uptimeMillis();
            android.graphics.Bitmap next=background.glassSnapshot();
            if(next!=null){blur(next);if(blurred!=null)blurred.recycle();blurred=next;}
        }
        parent.getLocationOnScreen(location);
        boolean any=false;
        RectF dock=new RectF();
        for(int i=0;i<parent.getChildCount();i++){
            View child=parent.getChildAt(i);int pos=parent.getChildAdapterPosition(child);
            if(pos<0)continue;
            View card=child.findViewById(R.id.card);if(card==null||card.getVisibility()!=View.VISIBLE)continue;
            float l=child.getLeft()+child.getTranslationX()+card.getLeft()+card.getTranslationX();
            float t=child.getTop()+child.getTranslationY()+card.getTop()+card.getTranslationY();
            RectF bounds=new RectF(l,t,l+card.getWidth(),t+card.getHeight());
            if(pos<dockSize){if(!any){dock.set(bounds);any=true;}else dock.union(bounds);}
            else {
                float expandX=card.getWidth()*(card.getScaleX()-1)/2,expandY=card.getHeight()*(card.getScaleY()-1)/2;
                bounds.inset(-expandX,-expandY);
                drawGlass(c,parent,bounds,AppAdapter.cornerRadius(card.getWidth(),density)*card.getScaleX());
            }
        }
        if(any){dock.inset(-14*density,-14*density);drawGlass(c,parent,dock,26*density);}
        if(parent.hasWindowFocus())parent.postInvalidateDelayed(1000);
    }
    private void drawGlass(Canvas c,RecyclerView parent,RectF bounds,float radius){
        if(blurred!=null){
            int save=c.save();android.graphics.Path clip=new android.graphics.Path();clip.addRoundRect(bounds,radius,radius,android.graphics.Path.Direction.CW);c.clipPath(clip);
            android.util.DisplayMetrics dm=parent.getResources().getDisplayMetrics();
            c.drawBitmap(blurred,null,new RectF(-location[0],-location[1],dm.widthPixels-location[0],dm.heightPixels-location[1]),imagePaint);c.restoreToCount(save);
        }
        c.drawRoundRect(bounds,radius,radius,band);
        c.drawRoundRect(bounds,radius,radius,edge);
    }
    private static void blur(android.graphics.Bitmap bitmap) {
        int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h],temp=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);
        for(int pass=0;pass<3;pass++){
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int r=0,g=0,b=0,n=0;
                for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
                    int p=pixels[Math.max(0,Math.min(h-1,y+dy))*w+Math.max(0,Math.min(w-1,x+dx))];r+=(p>>16)&255;g+=(p>>8)&255;b+=p&255;n++;
                }
                temp[y*w+x]=0xff000000|(r/n)<<16|(g/n)<<8|b/n;
            }
            int[] swap=pixels;pixels=temp;temp=swap;
        }
        bitmap.setPixels(pixels,0,w,0,0,w,h);
    }

}
