package com.gathin.tvhome;
import java.util.Locale;
/** Pure validation policy shared by initialization and later security changes. */
final class ConsoleAccessRules {
 static String username(String value){if(value==null||!value.matches("[\\p{L}\\p{N}_.@-]{1,40}"))throw new IllegalArgumentException("用户名为1～40位文字、数字或 _ . @ -");return value;}
 static void password(String value,String confirmation){if(value==null||value.length()<8||value.length()>64||value.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("密码需8～64位，不能包含控制字符");if(!value.equals(confirmation))throw new IllegalArgumentException("两次密码不一致");}
 static String path(String value){if(value==null)throw new IllegalArgumentException("路径无效");if(value.isEmpty())return value;if(!value.matches("[A-Za-z0-9_-]{3,40}")||java.util.Arrays.asList("console","core","themes","api","desktop").contains(value.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("路径可留空，或使用3～40位字母、数字、横线、下划线");return value;}
 static int port(int value){if(value<1024||value>65535||value==5555)throw new IllegalArgumentException("端口为1024～65535，不能使用ADB端口5555");return value;}
}
