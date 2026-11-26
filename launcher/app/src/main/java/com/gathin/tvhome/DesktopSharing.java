package com.gathin.tvhome;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.*;

final class DesktopSharing {
    static final String SHORTCUT="aurora-wifi-shortcut";

    
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("wifi_shortcut",0);}
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
    static JSONObject config(Context c)throws Exception{
        JSONArray stored=networks(c), publicRows=new JSONArray();
        JSONArray items=new JSONArray();
        for(int i=0;i<stored.length();i++){
            JSONObject row=stored.getJSONObject(i);publicRows.put(row.getString("name"));
            items.put(new JSONObject().put("name",row.getString("name")).put("visible",row.optBoolean("visible",true)));
        }
        return new JSONObject().put("enabled",enabled(c)).put("names",publicRows).put("items",items);
    }
    static JSONArray networks(Context c)throws Exception{return new JSONArray(prefs(c).getString("networks","[]"));}
    static synchronized JSONObject save(Context c,JSONObject b)throws Exception{
        JSONArray rows=networks(c);
        boolean wasEnabled=enabled(c);
        if(b.has("operation")){
            String op=b.getString("operation"),name=b.getString("name");
            int found=-1;
            for(int i=0;i<rows.length();i++)if(name.equals(rows.getJSONObject(i).getString("name"))){found=i;break;}
            if("add".equals(op)){
                if(name.trim().isEmpty()||name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32)throw new IllegalArgumentException("网络名称须为1至32字节");
                if(found>=0)throw new IllegalArgumentException("该网络已存在");
                rows.put(new JSONObject().put("name",name).put("password",b.getString("password")).put("visible",true));
            }else if("remove".equals(op)){if(found>=0)rows.remove(found);}
            else if("visibility".equals(op)){
                if(found<0)throw new IllegalArgumentException("该网络已移除，请刷新");
                rows.getJSONObject(found).put("visible",b.getBoolean("visible"));
            }else throw new IllegalArgumentException("未知操作");
        }else if(b.has("networks")){
            JSONArray requested=b.getJSONArray("networks"),replacement=new JSONArray();
            if(requested.length()>5)throw new IllegalArgumentException("最多保存五个网络");
            java.util.Map<String,String> saved=new java.util.HashMap<>();
            for(int i=0;i<rows.length();i++){JSONObject old=rows.getJSONObject(i);saved.put(old.getString("name"),old.getString("password"));}
            java.util.Set<String> seen=new java.util.HashSet<>();
            for(int i=0;i<requested.length();i++){
                JSONObject entry=requested.getJSONObject(i);String name=entry.getString("name");
                if(name.trim().isEmpty()||name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32||!seen.add(name))throw new IllegalArgumentException("请检查网络名称，名称不能重复或超过32字节");
                String password=entry.has("password")?entry.getString("password"):saved.get(entry.optString("original"));
                if(password==null)throw new IllegalArgumentException("原网络已变化，请刷新");
                replacement.put(new JSONObject().put("name",name).put("password",password));
            }
            rows=replacement;
        }else if(b.has("name")){
            String name=b.getString("name");if(name.trim().isEmpty()||name.length()>32)throw new IllegalArgumentException("无线网络名称无效");
            for(int i=rows.length()-1;i>=0;i--)if(name.equals(rows.getJSONObject(i).getString("name")))rows.remove(i);
            if(!b.optBoolean("remove"))rows.put(new JSONObject().put("name",name).put("password",b.getString("password")));
        }
        if(rows.length()>5)throw new IllegalArgumentException("最多保存五个网络");

        prefs(c).edit().putBoolean("enabled",b.optBoolean("enabled",enabled(c))).putString("networks",rows.toString()).apply();
        if(wasEnabled!=enabled(c))c.sendBroadcast(new Intent("com.gathin.tvhome.UPLOAD_CHANGED").setPackage(c.getPackageName()).putExtra("kind","settings-layout"));
        return config(c);
    }
    private static int dp(Activity a,int value){return Math.round(a.getResources().getDisplayMetrics().density*value);}
    private static LinearLayout panel(Activity a){
        // Sample the desktop window only, before the password dialog is attached.
        View desktop=a.getWindow().getDecorView();
        int width=desktop.getWidth(),height=desktop.getHeight();
        LinearLayout panel=new LinearLayout(a){
            final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
            final int[] here=new int[2],origin=new int[2];
            Bitmap backdrop;
            {
                if(width>0&&height>0){
                    Bitmap tiny=Bitmap.createBitmap(Math.max(1,width/30),Math.max(1,height/30),Bitmap.Config.ARGB_8888);
                    try{
                        PixelCopy.request(a.getWindow(),tiny,result->{
                            if(result==PixelCopy.SUCCESS){backdrop=tiny;invalidate();}
                            else tiny.recycle();
                        },new Handler(Looper.getMainLooper()));
                    }catch(IllegalArgumentException e){tiny.recycle();}
                }
            }
            @Override protected void onDraw(Canvas c){
                super.onDraw(c);
                if(backdrop!=null){
                    getLocationOnScreen(here);desktop.getLocationOnScreen(origin);
                    int x=origin[0]-here[0],y=origin[1]-here[1];
                    c.drawBitmap(backdrop,null,new Rect(x,y,x+width,y+height),paint);
                }
                c.drawColor(backdrop==null?0xeb213247:0xb0142235);
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(a,24),dp(a,18),dp(a,24),dp(a,16));panel.setWillNotDraw(false);
        android.graphics.drawable.GradientDrawable outline=new android.graphics.drawable.GradientDrawable();
        outline.setColor(0xff26374d);outline.setCornerRadius(dp(a,20));panel.setBackground(outline);panel.setClipToOutline(true);
        android.graphics.drawable.GradientDrawable rim=new android.graphics.drawable.GradientDrawable();
        rim.setColor(Color.TRANSPARENT);rim.setStroke(dp(a,1),0x82e0f4ff);rim.setCornerRadius(dp(a,20));panel.setForeground(rim);
        return panel;
    }
    private static TextView text(Activity a,String value,int size){
        TextView label=new TextView(a);label.setText(value);label.setTextSize(size);label.setTextColor(Color.WHITE);
        label.setPadding(dp(a,6),dp(a,3),dp(a,6),dp(a,3));return label;
    }
    private static Button button(Activity a,String label){
        Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(a,46));
        b.setPadding(dp(a,16),dp(a,5),dp(a,16),dp(a,5));b.setFocusable(true);
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        for(boolean focused:new boolean[]{true,false}){
            android.graphics.drawable.GradientDrawable fill=new android.graphics.drawable.GradientDrawable();
            fill.setCornerRadius(dp(a,9));fill.setColor(focused?0xdae3f5ff:0x483f526d);
            fill.setStroke(dp(a,focused?3:1),focused?0xffffffff:0x77bfdaeb);
            states.addState(focused?new int[]{android.R.attr.state_focused}:new int[0],fill);
        }
        b.setBackgroundTintList(null);b.setBackground(states);
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[0]},new int[]{0xff102e43,Color.WHITE}));b.setOnFocusChangeListener((v,focused)->v.animate().scaleX(focused?1.025f:1f).scaleY(focused?1.025f:1f).setDuration(120).start());return b;
    }
    private static void icon(Activity a,TextView label){
        android.graphics.drawable.Drawable wifi=a.getDrawable(R.drawable.ic_wifi).mutate();
        wifi.setTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[0]},new int[]{0xff102e43,0xffeef8ff}));wifi.setBounds(0,0,dp(a,22),dp(a,22));label.setCompoundDrawables(null,null,wifi,null);label.setCompoundDrawablePadding(dp(a,8));
    }
    private static Dialog dialog(Activity a,LinearLayout panel){
        Dialog d=new Dialog(a);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(a);scroll.addView(panel);d.setContentView(scroll);
        Window window=d.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        Button close=button(a,"关闭");close.setOnClickListener(v->d.dismiss());LinearLayout.LayoutParams closeSpace=new LinearLayout.LayoutParams(-2,-2);closeSpace.topMargin=dp(a,14);panel.addView(close,closeSpace);
        return d;
    }
    static void choose(Activity a){
        try{
            LinearLayout box=panel(a);TextView heading=text(a,"选择无线网络",24);
            heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(heading);
            LinearLayout list=new LinearLayout(a);list.setOrientation(LinearLayout.VERTICAL);box.addView(list,-1,-2);
            Dialog picker=dialog(a,box);
            Runnable refresh=()->{
                if(!enabled(a)){picker.dismiss();return;}
                View focused=list.findFocus();Object selected=focused==null?null:focused.getTag();list.removeAllViews();
                try{
                    JSONArray rows=networks(a);View target=null;
                    for(int i=0;i<rows.length();i++){
                        JSONObject row=rows.getJSONObject(i);if(!row.optBoolean("visible",true))continue;
                        String name=row.getString("name");Button network=button(a,name);network.setTag(name);icon(a,network);
                        network.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                        LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(-1,-2);item.setMargins(dp(a,6),dp(a,5),dp(a,6),dp(a,5));list.addView(network,item);
                        network.setOnClickListener(v->{picker.dismiss();show(a,name,row.optString("password"));});
                        if(target==null||name.equals(selected))target=network;
                    }
                    if(target!=null)target.requestFocus();else list.addView(text(a,"暂无可显示的网络",16));
                }catch(Exception e){picker.dismiss();}
            };
            SharedPreferences.OnSharedPreferenceChangeListener listener=(pref,key)->{if(picker.isShowing())refresh.run();};
            picker.setOnDismissListener(d->prefs(a).unregisterOnSharedPreferenceChangeListener(listener));
            picker.show();prefs(a).registerOnSharedPreferenceChangeListener(listener);
            picker.getWindow().setLayout(Math.min(dp(a,600),(int)(a.getResources().getDisplayMetrics().widthPixels*.78f)),WindowManager.LayoutParams.WRAP_CONTENT);
            refresh.run();
        }catch(Exception e){Toast.makeText(a,"读取无线网络失败",Toast.LENGTH_SHORT).show();}
    }
    private static String escapeWifi(String value){
        StringBuilder out=new StringBuilder();for(char ch:value.toCharArray()){if("\\;,:\"".indexOf(ch)>=0)out.append('\\');out.append(ch);}return out.toString();
    }
    private static void show(Activity a,String name,String password){
        LinearLayout box=panel(a);TextView heading=text(a,name,23);icon(a,heading);box.addView(heading);
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);box.addView(row,-1,-2);
        int size=Math.min(dp(a,260),(int)(a.getResources().getDisplayMetrics().heightPixels*.45f));
        ImageView qr=new ImageView(a);row.addView(qr,size,size);
        LinearLayout side=new LinearLayout(a);side.setPadding(dp(a,14),0,0,0);side.setOrientation(LinearLayout.VERTICAL);
        row.addView(side,new LinearLayout.LayoutParams(0,-2,1));
        TextView countdown=text(a,"",25);side.addView(countdown);
        TextView iosGuide=text(a,"iOS：打开扫码器或相机扫码",16);
        int appleSize=dp(a,22);
        Bitmap apple=Bitmap.createBitmap(appleSize,appleSize,Bitmap.Config.ARGB_8888);
        Canvas appleCanvas=new Canvas(apple);appleCanvas.scale(appleSize/24f,appleSize/24f);appleCanvas.translate(2,1);
        Paint applePaint=new Paint(Paint.ANTI_ALIAS_FLAG);applePaint.setColor(0xfffaf3ff);
        appleCanvas.drawPath(androidx.core.graphics.PathParser.createPathFromPathData("M9.43 5.89 Q9.01 5.89 7.61 5.49 Q6.23 5.10 5.26 5.10 Q2.98 5.10 1.48 7.00 Q0.00 8.93 0.00 11.94 Q0.00 15.21 1.95 18.59 Q3.91 22.00 5.92 22.00 Q6.55 22.00 7.66 21.55 Q8.69 21.13 9.56 21.13 Q10.41 21.13 11.54 21.52 Q12.70 21.95 13.42 21.95 Q15.13 21.95 16.82 19.39 Q17.38 18.54 17.77 17.71 Q18.17 16.88 18.43 16.08 Q17.25 15.71 16.35 14.42 Q15.42 13.10 15.42 11.46 Q15.42 9.96 16.30 8.74 Q16.77 8.06 17.80 7.16 Q17.46 6.73 17.11 6.42 Q16.77 6.10 16.43 5.84 Q15.19 4.99 13.60 4.99 Q12.65 4.99 11.30 5.44 Q9.96 5.89 9.43 5.89 ZM13.47 0.55 Q13.47 0.45 13.46 0.32 Q13.44 0.18 13.39 0.00 Q11.07 0.53 10.08 1.97 Q9.09 3.41 9.06 5.36 Q9.90 5.28 10.41 5.15 Q11.09 4.91 11.86 4.20 Q12.68 3.35 13.07 2.38 Q13.47 1.40 13.47 0.55 Z"),applePaint);
        android.graphics.drawable.BitmapDrawable appleBadge=new android.graphics.drawable.BitmapDrawable(a.getResources(),apple);
        appleBadge.setBounds(0,0,appleSize,appleSize);iosGuide.setCompoundDrawables(appleBadge,null,null,null);
        iosGuide.setCompoundDrawablePadding(dp(a,5));side.addView(iosGuide);
        TextView androidGuide=text(a,"安卓：系统设置 → Wi-Fi → 右上角扫码",16);
        int logoSize=dp(a,22);Bitmap logo=Bitmap.createBitmap(logoSize,logoSize,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(logo);canvas.scale(logoSize/32f,logoSize/32f);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xff8de8a2);paint.setStrokeWidth(1.8f);canvas.drawLine(7,5,11,12,paint);canvas.drawLine(25,5,21,12,paint);
        canvas.drawArc(2,9,30,36,180,180,true,paint);paint.setColor(0xff263448);canvas.drawCircle(10,18,1.5f,paint);canvas.drawCircle(22,18,1.5f,paint);
        android.graphics.drawable.BitmapDrawable badge=new android.graphics.drawable.BitmapDrawable(a.getResources(),logo);
        badge.setBounds(0,0,logoSize,logoSize);androidGuide.setCompoundDrawables(badge,null,null,null);androidGuide.setCompoundDrawablePadding(dp(a,5));side.addView(androidGuide);
        Button reveal=button(a,"查看密码");reveal.setTypeface(Typeface.DEFAULT,Typeface.BOLD);LinearLayout.LayoutParams revealSpace=new LinearLayout.LayoutParams(-2,-2);revealSpace.topMargin=dp(a,20);box.addView(reveal,revealSpace);
        TextView detail=text(a,password.isEmpty()?"此网络无需密码":password,18);detail.setGravity(Gravity.CENTER);detail.setTypeface(Typeface.DEFAULT,Typeface.BOLD);detail.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));detail.setVisibility(View.GONE);box.addView(detail);
        reveal.setOnClickListener(v->{boolean visible=detail.getVisibility()!=View.VISIBLE;detail.setVisibility(visible?View.VISIBLE:View.GONE);reveal.setText(visible?"隐藏密码":"查看密码");});
        Dialog d=dialog(a,box);Handler handler=new Handler(Looper.getMainLooper());
        class Clock implements Runnable{
            long end;int mask;
            public void run(){
                if(!enabled(a)){d.dismiss();return;}
                long now=SystemClock.elapsedRealtime();
                if(now>=end){
                    try{
                        String value="WIFI:T:"+(password.isEmpty()?"nopass":"WPA")+";S:"+escapeWifi(name)+";P:"+escapeWifi(password)+";;";
                        java.util.Map<com.google.zxing.EncodeHintType,Object> hints=new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
                        hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8");hints.put(com.google.zxing.EncodeHintType.QR_MASK_PATTERN,mask++%8);
                        BitMatrix bits=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,size,size,hints);
                        int[] pixels=new int[bits.getWidth()*bits.getHeight()];for(int y=0;y<bits.getHeight();y++)for(int x=0;x<bits.getWidth();x++)pixels[y*bits.getWidth()+x]=bits.get(x,y)?Color.BLACK:Color.WHITE;
                        qr.setImageBitmap(Bitmap.createBitmap(pixels,bits.getWidth(),bits.getHeight(),Bitmap.Config.ARGB_8888));
                        end=now+10000;
                    }catch(Exception e){qr.setImageDrawable(null);countdown.setText("暂时无法生成二维码");handler.postDelayed(this,1000);return;}
                }
                countdown.setText(((end-now+999)/1000)+" 秒");handler.postDelayed(this,Math.min(1000,end-now));
            }
        }
        Clock clock=new Clock();
        SharedPreferences.OnSharedPreferenceChangeListener listener=(pref,key)->{
            try{JSONArray rows=networks(a);
                for(int i=0;enabled(a)&&i<rows.length();i++){
                    JSONObject rowData=rows.getJSONObject(i);
                    if(name.equals(rowData.getString("name"))&&rowData.optBoolean("visible",true)&&password.equals(rowData.getString("password")))return;
                }
            }catch(Exception ignored){}
            d.dismiss();
        };
        d.setOnDismissListener(v->{handler.removeCallbacks(clock);prefs(a).unregisterOnSharedPreferenceChangeListener(listener);});
        d.show();d.getWindow().setLayout(Math.min(dp(a,780),(int)(a.getResources().getDisplayMetrics().widthPixels*.88f)),WindowManager.LayoutParams.WRAP_CONTENT);prefs(a).registerOnSharedPreferenceChangeListener(listener);listener.onSharedPreferenceChanged(prefs(a),"networks");if(d.isShowing())clock.run();
    }
}
