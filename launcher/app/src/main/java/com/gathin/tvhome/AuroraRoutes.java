package com.gathin.tvhome;
final class AuroraRoutes{
 static String canonical(String route){
  if(route.equals("appearance"))return "config/appearance";
  if(route.startsWith("appearance/"))return "config/"+route;
  if(route.equals("custom-css"))return "themes/custom-css";
  if(route.equals("library"))return "assets";
  if(route.startsWith("library/"))return "assets/"+route.substring(8);
  if(route.startsWith("apps/icon/"))return "icons/"+route.substring(10);
  return route;
 }
 static boolean pending(String route){return route.equals("legacy")||route.equals("ai/chat")||route.equals("themes/upload")||route.equals("icons");}
}
