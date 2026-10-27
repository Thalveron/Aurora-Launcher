package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConsoleAccessRulesTest {
 @Test public void optionalPathAndCredentials(){assertEquals("",ConsoleAccessRules.path(""));assertEquals("my-tv",ConsoleAccessRules.path("my-tv"));assertEquals("用户_1",ConsoleAccessRules.username("用户_1"));ConsoleAccessRules.password("test:pass","test:pass");assertEquals(8899,ConsoleAccessRules.port(8899));}
 @Test public void rejectsUnsafeValues(){for(String p:new String[]{"../api","a/b","console","API","a%20b","//"})reject(()->ConsoleAccessRules.path(p));for(String u:new String[]{"","a:b","a\\n"})reject(()->ConsoleAccessRules.username(u));reject(()->ConsoleAccessRules.password("short","short"));reject(()->ConsoleAccessRules.password("longpassword","different"));for(int p:new int[]{0,80,5555,65536})reject(()->ConsoleAccessRules.port(p));}
 private static void reject(Runnable r){try{r.run();fail("expected rejection");}catch(IllegalArgumentException expected){}}
}
