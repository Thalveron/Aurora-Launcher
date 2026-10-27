package com.gathin.tvhome;
import android.content.*;import android.media.tv.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;
/** Native input tile target, shared with the authenticated console. Does not alter the top input menu. */
final class InputShortcut {
 private static final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
 private static final java.util.concurrent.atomic.AtomicBoolean opening=new java.util.concurrent.atomic.AtomicBoolean();
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("input_shortcut",0);}
 static JSONObject get(Context c)throws Exception{return new JSONObject().put("id",prefs(c).getString("id","EN_TCL_HDMI1")).put("remoteHint",prefs(c).getBoolean("remoteHint",true));}
 private static void validate(Context c,String id){
  if(id.matches("EN_TCL_(HDMI[1-4]|AV[12])"))return;
  TvInputManager tm=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
  if(tm!=null)for(TvInputInfo v:tm.getTvInputList())if(v.isPassthroughInput()&&v.getId().equals(id))return;
  throw new IllegalArgumentException("请选择有效的输入源");
 }
 static JSONObject save(Context c,JSONObject body)throws Exception{
  AuroraSettings.only(body,"id","remoteHint");SharedPreferences.Editor e=prefs(c).edit();
  if(body.has("id")){String id=body.getString("id");validate(c,id);e.putString("id",id);}
  if(body.has("remoteHint")){if(!(body.get("remoteHint") instanceof Boolean))throw new IllegalArgumentException("remoteHint 必须为布尔值");e.putBoolean("remoteHint",body.getBoolean("remoteHint"));}
  if(!e.commit())throw new IllegalStateException("保存输入源快捷方式失败");return get(c);
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
  ImageView image=new ImageView(c);image.setImageResource(R.drawable.input_remote_hint);image.setScaleType(ImageView.ScaleType.FIT_CENTER);panel.addView(image,new LinearLayout.LayoutParams(dp(c,65),dp(c,230)));
  TextView text=new TextView(c);text.setText("已切换 "+id.replace("EN_TCL_","")+"\n请使用目标设备遥控器");text.setTextColor(0xfff3f8ff);text.setTextSize(22);text.setPadding(pad,0,0,0);panel.addView(text);
  Toast toast=new Toast(c);toast.setView(panel);toast.setDuration(Toast.LENGTH_LONG);toast.setGravity(Gravity.END|Gravity.CENTER_VERTICAL,dp(c,38),0);toast.show();
 }
 private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
}
