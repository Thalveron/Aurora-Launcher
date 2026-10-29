package com.gathin.tvhome;
import java.util.Set;
final class DefaultHomeRules {
 static boolean mayDisable(String pkg,String owner,Set<String> homes,Set<String> settings){return pkg!=null&&pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")&&!pkg.equals(owner)&&!pkg.startsWith(owner+".")&&!pkg.equals("com.alibaba.dingtalk.focus")&&!pkg.toLowerCase(java.util.Locale.ROOT).contains("systemui")&&!pkg.toLowerCase(java.util.Locale.ROOT).contains("settings")&&homes.contains(pkg)&&!settings.contains(pkg);}
}
