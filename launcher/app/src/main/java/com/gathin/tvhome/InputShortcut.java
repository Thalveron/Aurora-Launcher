package com.gathin.tvhome;
import android.content.*;import android.media.tv.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;
/** Native input tile target, shared with the authenticated console. Does not alter the top input menu. */
final class InputShortcut {
 private static final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
 private static final java.util.concurrent.atomic.AtomicBoolean opening=new java.util.concurrent.atomic.AtomicBoolean();
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("input_shortcut",0);}
 static boolean visible(Context c){return prefs(c).getBoolean("visible",false);}
 static JSONObject get(Context c)throws Exception{return new JSONObject().put("id",prefs(c).getString("id","EN_TCL_HDMI1")).put("visible",visible(c)).put("remoteHint",prefs(c).getBoolean("remoteHint",true)).put("customImage",image(c).isFile()).put("imageVersion",image(c).lastModified());}
 /** The bundled picture shows one specific remote; other TV and box combinations upload their own. */
 private static java.io.File image(Context c){return new java.io.File(c.getFilesDir(),"input-remote-hint.image");}
 private static android.graphics.BitmapFactory.Options bounds(java.io.File f){android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(f.getPath(),o);return o;}
 static JSONObject uploadImage(Context c,java.io.InputStream input,long length)throws Exception{
  if(length<=0||length>5L*1024*1024)throw new IllegalArgumentException("遥控器图片需在5 MB以内");
  java.io.File temp=new java.io.File(c.getFilesDir(),"input-remote-hint.part");boolean saved=false;
  try{
   try(java.io.FileOutputStream out=new java.io.FileOutputStream(temp)){byte[] buffer=new byte[8192];long left=length;while(left>0){int n=input.read(buffer,0,(int)Math.min(buffer.length,left));if(n<0)throw new java.io.EOFException("上传未完成");out.write(buffer,0,n);left-=n;}out.getFD().sync();}
   android.graphics.BitmapFactory.Options o=bounds(temp);
   if(o.outWidth<=0||o.outHeight<=0||o.outMimeType==null||!o.outMimeType.matches("image/(png|jpeg|webp)"))throw new IllegalArgumentException("请选择PNG、JPG或WebP图片");
   if(o.outWidth>8192||o.outHeight>8192)throw new IllegalArgumentException("遥控器图片边长需在8192像素以内");
   if(!temp.renameTo(image(c)))throw new java.io.IOException("保存遥控器图片失败");saved=true;return get(c);
  }finally{if(!saved)temp.delete();}
 }
 static JSONObject resetImage(Context c)throws Exception{java.io.File f=image(c);if(f.exists()&&!f.delete())throw new java.io.IOException("恢复默认遥控器图片失败");return get(c);}
 static String imageType(Context c){java.io.File f=image(c);String type=f.isFile()?bounds(f).outMimeType:null;return type==null?"image/png":type;}
 static byte[] imageBytes(Context c)throws Exception{java.io.File f=image(c);if(f.isFile())return java.nio.file.Files.readAllBytes(f.toPath());try(java.io.InputStream in=c.getResources().openRawResource(R.drawable.input_remote_hint)){return AuroraThemes.read(in,8*1024*1024);}}
 private static android.graphics.Bitmap customBitmap(Context c,int height){
  java.io.File f=image(c);if(!f.isFile())return null;
  try{android.graphics.BitmapFactory.Options o=bounds(f);if(o.outHeight<=0)return null;int sample=1;while(o.outHeight/(sample*2)>=height)sample*=2;o=new android.graphics.BitmapFactory.Options();o.inSampleSize=sample;return android.graphics.BitmapFactory.decodeFile(f.getPath(),o);}
  catch(Throwable unreadable){return null;}
 }
 private static void validate(Context c,String id){
  if(id.matches("EN_TCL_(HDMI[1-4]|AV[12])"))return;
  TvInputManager tm=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
  if(tm!=null)for(TvInputInfo v:tm.getTvInputList())if(v.isPassthroughInput()&&v.getId().equals(id))return;
  throw new IllegalArgumentException("请选择有效的输入源");
 }
 static JSONObject save(Context c,JSONObject body)throws Exception{
  AuroraSettings.only(body,"id","remoteHint","visible");SharedPreferences.Editor e=prefs(c).edit();
  if(body.has("id")){String id=body.getString("id");validate(c,id);e.putString("id",id);}
  if(body.has("visible")){if(!(body.get("visible") instanceof Boolean))throw new IllegalArgumentException("visible 必须为布尔值");e.putBoolean("visible",body.getBoolean("visible"));}
  if(body.has("remoteHint")){if(!(body.get("remoteHint") instanceof Boolean))throw new IllegalArgumentException("remoteHint 必须为布尔值");e.putBoolean("remoteHint",body.getBoolean("remoteHint"));}
  if(!e.commit())throw new IllegalStateException("保存输入源快捷方式失败");c.sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(c.getPackageName()).putExtra("kind","input-shortcut"));return get(c);
 }
 static void launch(Context context){
  if(!opening.compareAndSet(false,true))return;Context c=context.getApplicationContext();
  worker.execute(()->{Handler main=new Handler(Looper.getMainLooper());try{
   String id=prefs(c).getString("id","EN_TCL_HDMI1");validate(c,id);AuroraInputs.open(c,id);
   WebAudit.record(c,"电视遥控器","输入源快捷方式",id,200);
   if(prefs(c).getBoolean("remoteHint",true))main.postDelayed(()->hint(c,id),400);
  }catch(Exception e){WebAudit.record(c,"电视遥控器","输入源快捷方式失败",String.valueOf(e.getMessage()),500);main.post(()->Toast.makeText(c,"切换失败，请检查电视调试连接或在右上角选择输入源",Toast.LENGTH_LONG).show());}
  finally{opening.set(false);}});
 }
 private static void hint(Context c,String id){
  LinearLayout panel=new LinearLayout(c);panel.setOrientation(LinearLayout.HORIZONTAL);panel.setGravity(Gravity.CENTER_VERTICAL);int pad=dp(c,20);panel.setPadding(pad,pad,pad,pad);
  android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xed101d2c);bg.setCornerRadius(dp(c,22));panel.setBackground(bg);
  ImageView image=new ImageView(c);int width=dp(c,65),height=dp(c,230);android.graphics.Bitmap custom=customBitmap(c,height);
  if(custom!=null){image.setImageBitmap(custom);width=Math.max(dp(c,40),Math.min(dp(c,320),Math.round(height*(custom.getWidth()/(float)custom.getHeight()))));}else image.setImageResource(R.drawable.input_remote_hint);
  image.setScaleType(ImageView.ScaleType.FIT_CENTER);panel.addView(image,new LinearLayout.LayoutParams(width,height));
  TextView text=new TextView(c);text.setText("已切换 "+id.replace("EN_TCL_","")+"\n请使用目标设备遥控器");text.setTextColor(0xfff3f8ff);text.setTextSize(22);text.setPadding(pad,0,0,0);panel.addView(text);
  Toast toast=new Toast(c);toast.setView(panel);toast.setDuration(Toast.LENGTH_LONG);toast.setGravity(Gravity.END|Gravity.CENTER_VERTICAL,dp(c,38),0);toast.show();
 }
 private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
}
