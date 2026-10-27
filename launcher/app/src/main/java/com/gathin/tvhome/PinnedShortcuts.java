package com.gathin.tvhome;
import android.content.*;import android.content.pm.*;import android.graphics.drawable.Drawable;import org.json.*;import java.util.*;
final class PinnedShortcuts {
 static final String PREFIX="aurora.shortcut.";
 static JSONArray records(Context c){JSONArray a=new JSONArray();try(android.database.Cursor cursor=c.getContentResolver().query(android.net.Uri.parse("content://com.alibaba.dingtalk.focus.shortcuts/items"),null,null,null,null)){if(cursor!=null)while(cursor.moveToNext())a.put(new JSONObject().put("key",cursor.getString(0)).put("package",cursor.getString(1)).put("id",cursor.getString(2)).put("label",cursor.getString(3)));}catch(Exception ignored){}return a;}
 static boolean remove(Context c,String key){return c.getContentResolver().delete(android.net.Uri.parse("content://com.alibaba.dingtalk.focus.shortcuts/items/"+key),null,null)>0;}
 static void launch(Context c,String key)throws Exception{JSONArray a=records(c);for(int i=0;i<a.length();i++)if(key.equals(a.getJSONObject(i).getString("key"))){c.startActivity(new Intent().setComponent(new ComponentName("com.alibaba.dingtalk.focus","org.cleanyourtv.homebridge.ShortcutLaunchActivity")).putExtra("key",key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}throw new IllegalArgumentException("快捷方式不存在");}
 static Drawable tile(Context c,Drawable icon,String label){
  android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(540,303,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(b);android.graphics.Paint paint=new android.graphics.Paint(3);
  paint.setShader(new android.graphics.LinearGradient(0,0,540,303,new int[]{0xff176eaa,0xff153d7e,0xff172b5e},null,android.graphics.Shader.TileMode.CLAMP));canvas.drawRect(0,0,540,303,paint);paint.setShader(null);
  paint.setColor(android.graphics.Color.WHITE);paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));paint.setTextSize(label.toLowerCase(java.util.Locale.ROOT).startsWith("steam")?58:52);
  String text=android.text.TextUtils.ellipsize(label,new android.text.TextPaint(paint),270,android.text.TextUtils.TruncateAt.END).toString();
  float width=paint.measureText(text),size=180,gap=52,left=(540-size-gap-width)/2;
  if(label.toLowerCase(java.util.Locale.ROOT).startsWith("steam")){
   android.graphics.Bitmap source=android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888);icon.setBounds(0,0,256,256);icon.draw(new android.graphics.Canvas(source));
   int[] pixels=new int[256*145];source.getPixels(pixels,0,256,0,0,256,145);
   for(int i=0;i<pixels.length;i++){int color=pixels[i];int level=Math.min(android.graphics.Color.red(color),Math.min(android.graphics.Color.green(color),android.graphics.Color.blue(color)));int alpha=Math.max(0,Math.min(255,(level-150)*255/85));pixels[i]=(alpha<<24)|0xffffff;}
   android.graphics.Bitmap mark=android.graphics.Bitmap.createBitmap(pixels,256,145,android.graphics.Bitmap.Config.ARGB_8888);
   int minX=256,minY=145,maxX=-1,maxY=-1;
   for(int y=0;y<145;y++)for(int x=0;x<256;x++)if((pixels[y*256+x]>>>24)>40){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
   if(maxX>=minX){float height=Math.min(160,size*(maxY-minY+1)/(maxX-minX+1));canvas.drawBitmap(mark,new android.graphics.Rect(minX,minY,maxX+1,maxY+1),new android.graphics.RectF(left,151.5f-height/2,left+size,151.5f+height/2),new android.graphics.Paint(3));}
   mark.recycle();source.recycle();
  }else{icon.setBounds(Math.round(left),62,Math.round(left+size),242);icon.draw(canvas);}
  canvas.drawText(text,left+size+gap,151.5f-(paint.ascent()+paint.descent())/2,paint);
  return new android.graphics.drawable.BitmapDrawable(c.getResources(),b);
 }
 static java.util.List<AppAdapter.Entry> entries(Context c){List<AppAdapter.Entry> out=new ArrayList<>();JSONArray a=records(c);for(int i=0;i<a.length();i++)try{JSONObject v=a.getJSONObject(i);String pkg=v.getString("package");Drawable icon=c.getPackageManager().getApplicationIcon(pkg);try(java.io.InputStream stream=c.getContentResolver().openInputStream(android.net.Uri.parse("content://com.alibaba.dingtalk.focus.shortcuts/icon/"+v.getString("key")))){Drawable custom=Drawable.createFromStream(stream,null);if(custom!=null)icon=custom;}catch(Exception ignored){}String label=v.getString("label").replaceFirst("\\s*[（(][^()（）]*[）)]\\s*$","");out.add(new AppAdapter.Entry(4,label,new ComponentName(pkg,PREFIX+v.getString("key")),tile(c,icon,label),icon,null,0));}catch(Exception ignored){}return out;}
}
