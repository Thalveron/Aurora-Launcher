package com.gathin.tvhome;
import android.content.Context;import org.json.*;import java.util.*;
final class AuroraPresets{
 static JSONObject get(Context c)throws Exception{return AuroraThemeProfiles.read(c,AuroraThemes.current(c),"presets");}
 static JSONObject save(Context c,JSONObject body)throws Exception{JSONArray presets=body.getJSONArray("presets");if(presets.length()>20)throw new IllegalArgumentException("最多20个预设");Set<String> names=new HashSet<>();String[] keys={"cardOpacity","glassBlur","backgroundDim","cardRadius","iconSize","spacing","fontScale"};double[] min={.05,0,0,0,24,0,.6},max={.6,40,.8,80,160,80,1.8};for(int i=0;i<presets.length();i++){JSONObject p=presets.getJSONObject(i);AuroraSettings.only(p,"name","values");String name=p.getString("name");if(name.trim().isEmpty()||name.length()>50||!names.add(name))throw new IllegalArgumentException("预设名称为空、过长或重复");JSONObject values=p.getJSONObject("values");AuroraSettings.only(values,keys);for(int k=0;k<keys.length;k++)if(values.has(keys[k]))AuroraSettings.range(values.getDouble(keys[k]),min[k],max[k]);}AuroraThemeProfiles.write(c,AuroraThemes.current(c),"presets",presets);return get(c);}
}
