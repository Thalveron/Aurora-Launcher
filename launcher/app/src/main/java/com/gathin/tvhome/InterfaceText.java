package com.gathin.tvhome;

import java.util.*;
import java.util.regex.*;

/** Translate interface messages, retaining dynamic values such as versions and URLs. */
final class InterfaceText {
 private final Map<String,String> words;
 private final Pattern phrases;
 InterfaceText(Map<String,String> source){
  words=new HashMap<>(source);
  List<String> keys=new ArrayList<>();
  for(String key:words.keySet())if(key.length()>1&&key.matches("(?s).*[\\u4e00-\\u9fff].*"))keys.add(key);
  keys.sort((a,b)->Integer.compare(b.length(),a.length()));
  StringBuilder pattern=new StringBuilder();
  for(String key:keys){if(pattern.length()>0)pattern.append('|');pattern.append(Pattern.quote(key));}
  phrases=pattern.length()==0?null:Pattern.compile(pattern.toString());
 }
 String translate(String text){
  if(text==null)return "";
  if(words.containsKey(text))return words.get(text);
  if(phrases==null)return text;
  Matcher matcher=phrases.matcher(text);StringBuffer result=new StringBuffer();
  while(matcher.find())matcher.appendReplacement(result,Matcher.quoteReplacement(words.get(matcher.group())));
  matcher.appendTail(result);return result.toString();
 }
}
