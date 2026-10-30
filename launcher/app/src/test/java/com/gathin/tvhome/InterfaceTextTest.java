package com.gathin.tvhome;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class InterfaceTextTest {
 private InterfaceText translator(){Map<String,String> m=new HashMap<>();m.put("更新","Update");m.put("正在检查更新","Checking for updates");m.put("发现新版本 v","New version available: v");m.put("下载完成：","Download complete: ");return new InterfaceText(m);}
 @Test public void prefersCompleteMessages(){assertEquals("Checking for updates",translator().translate("正在检查更新"));}
 @Test public void preservesVersionAndNetworkAddress(){assertEquals("New version available: v1.4.5",translator().translate("发现新版本 v1.4.5"));assertEquals("Download complete: https://example.org/app.apk",translator().translate("下载完成：https://example.org/app.apk"));}
 @Test public void preservesUnknownAndAlreadyEnglishText(){assertEquals("Custom app name",translator().translate("Custom app name"));assertEquals("用户输入",translator().translate("用户输入"));}
}
