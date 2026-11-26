package com.gathin.tvhome;
import android.view.*;
import android.graphics.Bitmap;
import android.os.*;
import java.util.concurrent.*;

final class WebPreview {
    static volatile Window window;
    static byte[] capture()throws Exception{
        Window source=window;if(source==null)throw new java.io.IOException("电视当前不在桌面，返回桌面后可预览");
        Bitmap bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);CountDownLatch done=new CountDownLatch(1);int[] result={-1};
        final java.util.concurrent.atomic.AtomicBoolean timedOut=new java.util.concurrent.atomic.AtomicBoolean(),released=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable release=()->{if(released.compareAndSet(false,true))bitmap.recycle();};
        new Handler(Looper.getMainLooper()).post(()->{try{PixelCopy.request(source,bitmap,status->{result[0]=status;if(timedOut.get())release.run();done.countDown();},new Handler(Looper.getMainLooper()));}catch(RuntimeException e){done.countDown();}});
        if(!done.await(4,TimeUnit.SECONDS)){
            // The copy may still complete on the main thread; its callback frees the buffer then.
            timedOut.set(true);if(done.getCount()==0)release.run();
            throw new java.io.IOException("预览超时，请重试");
        }
        try{if(result[0]!=PixelCopy.SUCCESS)throw new java.io.IOException("桌面尚未准备好，请稍后刷新");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,75,out);return out.toByteArray();}finally{release.run();}
    }
}
