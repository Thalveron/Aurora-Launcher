package com.gathin.tvhome;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Configurable service adapters. Credentials remain on the native side, never in theme data. */
final class AuroraServices {
    private static volatile boolean weatherBusy,timeBusy;
    private static volatile long weatherTry,timeTry,offset;
    private static volatile Boolean rootClock;
    private static volatile boolean checkingRoot;
    static JSONObject defaults()throws Exception{return new JSONObject("{\"weather\":{\"provider\":\"openmeteo\",\"city\":\"上海\",\"lat\":31.2304,\"lon\":121.4737,\"key\":\"\",\"urls\":[\"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=4&timezone={timezone}\"],\"headers\":{},\"intervalMinutes\":60,\"mapping\":{\"temperature\":\"$.current.temperature_2m\",\"code\":\"$.current.weather_code\",\"dates\":\"$.daily.time\",\"min\":\"$.daily.temperature_2m_min\",\"max\":\"$.daily.temperature_2m_max\",\"codes\":\"$.daily.weather_code\"}},\"time\":{\"ntp\":[\"ntp.aliyun.com\",\"time.cloudflare.com\",\"pool.ntp.org\"],\"http\":[\"https://www.baidu.com\",\"https://www.cloudflare.com\"],\"intervalMinutes\":360,\"timezone\":\"Asia/Shanghai\",\"syncSystem\":false}}");}
    static synchronized JSONObject config(Context c)throws Exception{android.content.SharedPreferences p=c.getSharedPreferences("aurora_services",0);String saved=p.getString("config",null);JSONObject result=saved==null?defaults():new JSONObject(saved);if(!p.getBoolean("system_time_optin_v031",false)){result.getJSONObject("time").put("syncSystem",false);if(!p.edit().putString("config",result.toString()).putBoolean("system_time_optin_v031",true).commit())throw new IllegalStateException("无法保存校时默认开关");}if(CityTimezone.apply(c,result)){p.edit().putString("config",result.toString()).apply();weatherTry=0;}if(!canSetTime(c))result.getJSONObject("time").put("syncSystem",false);if(!result.getJSONObject("time").has("hour24"))result.getJSONObject("time").put("hour24",LocationSettings.prefs(c).getBoolean("hour24",true));return result;}
    static synchronized JSONObject save(Context c,JSONObject value)throws Exception{
        value=new JSONObject(value.toString());
        JSONObject built=defaults(),weather=value.getJSONObject("weather"),time=value.getJSONObject("time");
        if(!time.has("hour24"))time.put("hour24",LocationSettings.prefs(c).getBoolean("hour24",true));if(!(time.get("hour24") instanceof Boolean))throw new IllegalArgumentException("24 小时制必须为开关值");
        if(weather.getJSONArray("urls").length()==0){
            JSONObject d=built.getJSONObject("weather");
            for(String key:new String[]{"provider","urls","headers","mapping","key"})weather.put(key,d.get(key));
        }
        for(String key:new String[]{"ntp","http"})if(time.getJSONArray(key).length()==0)time.put(key,built.getJSONObject("time").get(key));

        AuroraSettings.only(value,"weather","time");JSONObject w=value.getJSONObject("weather"),t=value.getJSONObject("time");
        w.put("city",CityTime.name(w.getString("city")));
        AuroraSettings.only(w,"provider","city","lat","lon","key","urls","headers","intervalMinutes","mapping","timezone");AuroraSettings.choice(w.get("provider"),"openmeteo","qweather","custom");AuroraSettings.range(w.getDouble("lat"),-90,90);AuroraSettings.range(w.getDouble("lon"),-180,180);AuroraSettings.range(w.getInt("intervalMinutes"),5,1440);
        urls(w.getJSONArray("urls"));JSONObject h=w.getJSONObject("headers");for(Iterator<String> i=h.keys();i.hasNext();){String k=i.next();if(!k.matches("[A-Za-z0-9-]{1,80}")||h.getString(k).matches("(?s).*[\\r\\n].*"))throw new IllegalArgumentException("请求头无效");}
        JSONObject m=w.getJSONObject("mapping");AuroraSettings.only(m,"temperature","code","dates","min","max","codes");for(Iterator<String> i=m.keys();i.hasNext();)fieldPath(m.getString(i.next()));
        CityTimezone.apply(c,value);AuroraSettings.only(t,"ntp","http","intervalMinutes","timezone","syncSystem","hour24");java.time.ZoneId.of(t.getString("timezone"));AuroraSettings.range(t.getInt("intervalMinutes"),5,10080);urls(t.getJSONArray("http"));JSONArray ntp=t.getJSONArray("ntp");if(ntp.length()>8)throw new IllegalArgumentException("最多8个NTP地址");for(int i=0;i<ntp.length();i++)if(!ntp.getString(i).matches("[A-Za-z0-9.-]{1,253}"))throw new IllegalArgumentException("NTP只填写域名或IPv4地址");
        if(t.getBoolean("syncSystem")&&!canSetTime(c))throw new IllegalArgumentException("同步系统时间需要SET_TIME系统权限；WRITE_SECURE_SETTINGS不足以授权");
        if(value.toString().length()>32768)throw new IllegalArgumentException("服务配置过大");c.getSharedPreferences("aurora_services",0).edit().putString("config",value.toString()).commit();c.getSharedPreferences("desktop_location",0).edit().putString("city",w.getString("city")).putString("latitude",w.get("lat").toString()).putString("longitude",w.get("lon").toString()).putString("timezone",t.getString("timezone")).putBoolean("hour24",t.getBoolean("hour24")).commit();
        weatherTry=0;timeTry=0;weather(c);time(c);return status(c);
    }
    private static volatile java.util.List<CityCatalog.City> cityCatalog;
    static JSONArray cities(Context c,String name)throws Exception{
        name=CityTime.name(name);
        if(cityCatalog==null)synchronized(AuroraServices.class){if(cityCatalog==null)cityCatalog=CityCatalog.read(c.getAssets().open("geography/china-cities.tsv"));}
        JSONArray local=new JSONArray();
        for(CityCatalog.City city:CityCatalog.search(cityCatalog,name)){
            CityTimezone.remember(c,city.lat,city.lon,city.timezone);
            local.put(new JSONObject().put("name",city.display(name)).put("region",city.region).put("country","中国").put("countryCode","CN").put("timezone",city.timezone).put("lat",city.lat).put("lon",city.lon));
        }
        if(local.length()>0)return local;
        JSONObject raw=fetch("https://geocoding-api.open-meteo.com/v1/search?count=8&language=zh&format=json&name="+URLEncoder.encode(name,"UTF-8"),new JSONObject());
        JSONArray source=raw.optJSONArray("results"),out=new JSONArray();if(source==null)return out;
        for(int i=0;i<source.length();i++){JSONObject v=source.getJSONObject(i);String zone=v.optString("timezone");CityTimezone.remember(c,v.getDouble("latitude"),v.getDouble("longitude"),zone);out.put(new JSONObject().put("name",v.getString("name")).put("region",v.optString("admin1")).put("country",v.optString("country")).put("countryCode",v.optString("country_code")).put("timezone",zone).put("lat",v.getDouble("latitude")).put("lon",v.getDouble("longitude")));}
        return out;
    }
    static void urls(JSONArray a)throws Exception{if(a.length()<1||a.length()>8)throw new IllegalArgumentException("需1～8个服务地址");for(int i=0;i<a.length();i++){String s=a.getString(i).replace("{lat}","0").replace("{lon}","0").replace("{city}","x").replace("{key}","x");URL u=new URL(s);if(!u.getProtocol().equals("https")||u.getHost().isEmpty()||u.getUserInfo()!=null||s.length()>2048)throw new IllegalArgumentException("按当前安全设置仅允许HTTPS地址，不允许URL携带账号密码");}}
    static void fieldPath(String s){if(!s.matches("\\$?(\\.[A-Za-z_][A-Za-z0-9_]*|\\[\\d{1,4}\\]){1,16}"))throw new IllegalArgumentException("字段路径仅支持 $.字段.子字段 和 [序号]");}
    static Object at(Object object,String path)throws Exception{fieldPath(path);java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\.([A-Za-z_][A-Za-z0-9_]*)|\\[(\\d+)\\]").matcher(path);while(m.find())object=m.group(1)!=null?((JSONObject)object).get(m.group(1)):((JSONArray)object).get(Integer.parseInt(m.group(2)));return object;}
    static HttpURLConnection connection(String url,JSONObject headers)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setInstanceFollowRedirects(false);for(Iterator<String> i=headers.keys();i.hasNext();){String k=i.next();c.setRequestProperty(k,headers.getString(k));}return c;}
    static JSONObject fetch(String url,JSONObject headers)throws Exception{HttpURLConnection c=connection(url,headers);try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream()){return new JSONObject(new String(AuroraThemes.read(in,512*1024),"UTF-8"));}}finally{c.disconnect();}}
    static String expand(String s,JSONObject w)throws Exception{s=CityTime.weatherTemplate(s,w.optString("provider"));for(String k:new String[]{"lat","lon","city","key"})s=s.replace("{"+k+"}",URLEncoder.encode(String.valueOf(w.get(k)),"UTF-8"));return s.replace("{timezone}",URLEncoder.encode(w.optString("timezone","auto"),"UTF-8"));}
    static JSONObject normalize(JSONObject raw,JSONObject w)throws Exception{
        if(w.getString("provider").equals("qweather")){
            JSONObject now=raw.optJSONObject("now");JSONArray daily=raw.optJSONArray("daily");JSONObject out=new JSONObject(),d=new JSONObject();JSONArray dates=new JSONArray(),min=new JSONArray(),max=new JSONArray(),codes=new JSONArray();
            if(daily!=null)for(int i=0;i<Math.min(4,daily.length());i++){JSONObject day=daily.getJSONObject(i);dates.put(day.getString("fxDate"));min.put(day.getDouble("tempMin"));max.put(day.getDouble("tempMax"));codes.put(day.optInt("iconDay",999));}
            if(now==null&&daily!=null&&daily.length()>0)now=new JSONObject().put("temp",daily.getJSONObject(0).get("tempMax")).put("icon",daily.getJSONObject(0).optInt("iconDay",999));if(now==null)throw new IOException("和风天气缺少now/daily字段");
            return out.put("current",new JSONObject().put("temperature_2m",now.getDouble("temp")).put("weather_code",now.optInt("icon",999))).put("daily",d.put("time",dates).put("temperature_2m_min",min).put("temperature_2m_max",max).put("weather_code",codes));
        }
        JSONObject m=w.getJSONObject("mapping"),current=new JSONObject().put("temperature_2m",Double.parseDouble(at(raw,m.getString("temperature")).toString())).put("weather_code",at(raw,m.getString("code")));
        JSONObject d=new JSONObject();String[] fields={"dates","min","max","codes"},target={"time","temperature_2m_min","temperature_2m_max","weather_code"};int count=-1;
        for(int i=0;i<4;i++){JSONArray a=(JSONArray)at(raw,m.getString(fields[i]));if(count<0)count=a.length();if(count!=a.length()||count<1)throw new IOException("预报数组长度不一致");d.put(target[i],a);}return new JSONObject().put("current",current).put("daily",d);
    }
    static JSONObject weather(Context c)throws Exception{
        JSONObject w=config(c).getJSONObject("weather");android.content.SharedPreferences p=c.getSharedPreferences("aurora_service_state",0);long fetched=p.getLong("weatherAt",0);String signature=w.toString();
        if(!weatherBusy&&android.os.SystemClock.elapsedRealtime()>=weatherTry&&(System.currentTimeMillis()-fetched>w.getLong("intervalMinutes")*60000||!signature.equals(p.getString("weatherConfig",""))))refreshWeather(c,w);
        boolean sameLocation=false;try{JSONObject old=new JSONObject(p.getString("weatherConfig","{}"));sameLocation=old.optString("city").equals(w.getString("city"))&&old.optDouble("lat")==w.getDouble("lat")&&old.optDouble("lon")==w.getDouble("lon")&&old.optString("timezone").equals(w.getString("timezone"));}catch(Exception ignored){}
        JSONObject out=sameLocation?new JSONObject(p.getString("weatherData","{}")):new JSONObject();return out.put("available",out.has("current")).put("city",w.getString("city")).put("fetchedAt",fetched).put("stale",!signature.equals(p.getString("weatherConfig",""))||System.currentTimeMillis()-fetched>w.getLong("intervalMinutes")*60000).put("reason",p.getString("weatherError","正在获取天气")).put("source",w.getString("provider")).put("timezone",w.getString("timezone"));
    }
    private static synchronized void refreshWeather(Context c,JSONObject w){if(weatherBusy)return;weatherBusy=true;weatherTry=android.os.SystemClock.elapsedRealtime()+60000;new Thread(()->{String error="所有天气地址均失败";try{JSONArray urls=w.getJSONArray("urls");JSONObject aggregate=null;for(int i=0;i<urls.length();i++)try{JSONObject raw=fetch(expand(urls.getString(i),w),w.getJSONObject("headers"));if(w.optString("provider").equals("qweather")){if(aggregate==null)aggregate=new JSONObject();if(raw.has("now"))aggregate.put("now",raw.get("now"));if(raw.has("daily"))aggregate.put("daily",raw.get("daily"));raw=aggregate;}JSONObject normalized=normalize(raw,w);c.getSharedPreferences("aurora_service_state",0).edit().putString("weatherData",normalized.toString()).putLong("weatherAt",System.currentTimeMillis()).putString("weatherConfig",w.toString()).putString("weatherError","").apply();if(!w.optString("provider").equals("qweather")||normalized.getJSONObject("daily").getJSONArray("time").length()>0)return;}catch(Exception e){error=e instanceof java.net.SocketTimeoutException?"请求超时":e.getClass().getSimpleName()+"：服务响应或字段不匹配";}}catch(Exception e){error="服务配置不可用";}finally{weatherBusy=false;}c.getSharedPreferences("aurora_service_state",0).edit().putString("weatherError",error).apply();},"aurora-weather-adapter").start();}
    static long now(){return System.currentTimeMillis()+offset;}
    static String zone(Context c){try{return config(c).getJSONObject("time").getString("timezone");}catch(Exception e){return "Asia/Shanghai";}}
    static boolean canSetTime(Context c)throws Exception{
        if(c.checkSelfPermission("android.permission.SET_TIME")==android.content.pm.PackageManager.PERMISSION_GRANTED)return true;
        if(rootClock==null&&!checkingRoot&&TvAdb.status().optBoolean("connected")){checkingRoot=true;new Thread(()->{try{rootClock=TvAdb.shell(c,"id -u").trim().equals("0")&&TimeCapability.rootClockCapability(TvAdb.shell(c,"cat /proc/self/status"));}catch(Exception ignored){}finally{checkingRoot=false;}},"aurora-clock-permission").start();}
        return Boolean.TRUE.equals(rootClock)&&TvAdb.status().optBoolean("connected");
    }
    static void time(Context c)throws Exception{
        JSONObject t=config(c).getJSONObject("time");if(timeBusy||android.os.SystemClock.elapsedRealtime()<timeTry)return;sync(c,t);
    }
    private static synchronized void sync(Context c,JSONObject t){if(timeBusy)return;timeBusy=true;timeTry=android.os.SystemClock.elapsedRealtime()+60000;new Thread(()->{try{
        Long value=null;String source="";JSONArray ntp=t.getJSONArray("ntp");for(int i=0;i<ntp.length();i++)try{value=ntp(ntp.getString(i));source=ntp.getString(i);break;}catch(Exception ignored){}
        if(value==null){JSONArray http=t.getJSONArray("http");for(int i=0;i<http.length();i++){HttpURLConnection h=null;try{h=connection(http.getString(i),new JSONObject());h.setRequestMethod("HEAD");h.getResponseCode();long date=h.getDate();if(date>1577836800000L){value=date;source=new URL(http.getString(i)).getHost();break;}}catch(Exception ignored){}finally{if(h!=null)h.disconnect();}}}
        if(value==null)throw new IOException("所有校时地址均失败");offset=value-System.currentTimeMillis();if(Math.abs(offset)>10L*365*86400000)throw new IOException("校时偏差异常");
        if(t.getBoolean("syncSystem")&&canSetTime(c)){
            if(c.checkSelfPermission("android.permission.SET_TIME")==android.content.pm.PackageManager.PERMISSION_GRANTED)((android.app.AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setTime(value);
            else {String result=TvAdb.shell(c,"date -u -s @"+(value/1000));if(result.toLowerCase(Locale.ROOT).contains("invalid")||result.toLowerCase(Locale.ROOT).contains("denied"))throw new IOException("系统拒绝校时");}
            offset=0;
        }
        c.getSharedPreferences("aurora_service_state",0).edit().putLong("timeAt",System.currentTimeMillis()).putString("timeSource",source).putString("timeError","").apply();timeTry=android.os.SystemClock.elapsedRealtime()+t.getLong("intervalMinutes")*60000;
    }catch(Exception e){c.getSharedPreferences("aurora_service_state",0).edit().putString("timeError","校时失败，继续使用电视时钟").apply();}finally{timeBusy=false;}},"aurora-clock-sync").start();}
    private static long ntp(String host)throws Exception{try(DatagramSocket s=new DatagramSocket()){s.setSoTimeout(2000);byte[] b=new byte[48];b[0]=0x23;long start=android.os.SystemClock.elapsedRealtime();byte[] nonce=new byte[8];new java.security.SecureRandom().nextBytes(nonce);System.arraycopy(nonce,0,b,40,8);InetAddress addr=InetAddress.getByName(host);s.connect(addr,123);s.send(new DatagramPacket(b,b.length));DatagramPacket p=new DatagramPacket(new byte[48],48);s.receive(p);byte[] r=p.getData();if(p.getLength()<48||(r[0]&7)!=4||(r[0]&0xc0)==0xc0||(r[1]&255)<1||(r[1]&255)>15)throw new IOException("NTP响应无效");for(int i=0;i<8;i++)if(r[24+i]!=nonce[i])throw new IOException("NTP请求不匹配");long seconds=ByteBuffer.wrap(r,40,4).getInt()&0xffffffffL,fraction=ByteBuffer.wrap(r,44,4).getInt()&0xffffffffL;return (seconds-2208988800L)*1000+(fraction*1000>>>32)+(android.os.SystemClock.elapsedRealtime()-start)/2;}}
    static JSONObject status(Context c)throws Exception{android.content.SharedPreferences p=c.getSharedPreferences("aurora_service_state",0);return new JSONObject().put("config",config(c)).put("weather",new JSONObject().put("updating",weatherBusy).put("updatedAt",p.getLong("weatherAt",0)).put("error",p.getString("weatherError","")).put("success",p.getLong("weatherAt",0)>0&&p.getString("weatherError","").isEmpty())).put("time",new JSONObject().put("updating",timeBusy).put("updatedAt",p.getLong("timeAt",0)).put("error",p.getString("timeError","")).put("source",p.getString("timeSource","")).put("offsetMs",offset).put("canSyncSystem",canSetTime(c)).put("permissionReason","需要SET_TIME系统签名权限或Root专用后端；WRITE_SECURE_SETTINGS不授予系统校时能力"));}
    static JSONObject test(Context c)throws Exception{weatherTry=0;timeTry=0;refreshWeather(c,config(c).getJSONObject("weather"));time(c);return status(c);}
}
