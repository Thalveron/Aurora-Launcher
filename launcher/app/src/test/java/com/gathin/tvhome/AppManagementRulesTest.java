package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class AppManagementRulesTest {
    @Test public void criticalPackagesAreProtected(){
        for(String pkg:new String[]{"android","com.android.settings","com.android.systemui","com.android.providers.media","com.google.android.permissioncontroller","com.android.bluetooth","com.android.packageinstaller"})assertTrue(pkg,AppManagementRules.protectedPackage(pkg));
        assertFalse(AppManagementRules.protectedPackage("org.example.weather"));
    }
    @Test public void namesCannotImpersonateOtherRows(){
        assertEquals("Aurora 音乐",AppManagementRules.name("  Aurora 音乐  "));assertEquals("",AppManagementRules.name(" "));
        for(String name:new String[]{"abc\n系统设置","abc\u202eexe","abc\u2066def",new String(new char[61]).replace('\0','A')})try{AppManagementRules.name(name);fail("must reject");}catch(IllegalArgumentException expected){}
    }
    @Test public void remoteVolumeStillRejectsPowerAndArbitraryCodes(){
        assertEquals(24,AuroraRules.key("volume_up"));assertEquals(25,AuroraRules.key("volume_down"));
        for(String key:new String[]{"power","26","volume_up;reboot"})try{AuroraRules.key(key);fail("must reject");}catch(IllegalArgumentException expected){}
    }
}
