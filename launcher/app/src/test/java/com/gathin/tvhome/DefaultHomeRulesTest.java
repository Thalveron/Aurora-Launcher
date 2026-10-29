package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class DefaultHomeRulesTest {
 private final String owner="com.gathin.tvhome";private final Set<String> homes=new HashSet<>(Arrays.asList(owner,"com.vendor.launcher","com.vendor.combined","com.android.systemui","com.android.settings")),settings=new HashSet<>(Arrays.asList("com.vendor.combined","com.android.settings"));
 @Test public void independentOriginalHomeIsEligible(){assertTrue(DefaultHomeRules.mayDisable("com.vendor.launcher",owner,homes,settings));}
 @Test public void neverDisableAuroraOrSystemUi(){assertFalse(DefaultHomeRules.mayDisable(owner,owner,homes,settings));assertFalse(DefaultHomeRules.mayDisable("com.android.systemui",owner,homes,settings));}
 @Test public void combinedSettingsAndHomeStayProtected(){assertFalse(DefaultHomeRules.mayDisable("com.vendor.combined",owner,homes,settings));assertFalse(DefaultHomeRules.mayDisable("com.android.settings",owner,homes,settings));}
 @Test public void packageMustActuallyBeAHome(){assertFalse(DefaultHomeRules.mayDisable("com.vendor.music",owner,homes,settings));}
 @Test public void auroraHomeBridgeAndMaintenanceStayProtected(){Set<String> entries=new HashSet<>(Arrays.asList("com.alibaba.dingtalk.focus",owner+".updater","com.vendor.SystemUI"));for(String pkg:entries)assertFalse(DefaultHomeRules.mayDisable(pkg,owner,entries,settings));}
 @Test public void invalidPackageCannotReachShell(){for(String pkg:new String[]{"android","",null,"com.vendor.launcher;reboot","com.vendor.launcher\nreboot","../com.vendor.launcher"})assertFalse(DefaultHomeRules.mayDisable(pkg,owner,homes,settings));}
}
