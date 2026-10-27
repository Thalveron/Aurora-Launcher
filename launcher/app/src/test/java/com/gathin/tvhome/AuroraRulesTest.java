package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.net.InetAddress;

public class AuroraRulesTest {
    @Test public void confirmationCannotBeReplayedOrTransferred() {
        AuroraRules.Confirmations tokens=new AuroraRules.Confirmations();String token=tokens.issue("192.168.1.10",100);
        assertFalse(tokens.consume(token,"192.168.1.11",101));
        assertTrue(tokens.consume(token,"192.168.1.10",102));
        assertFalse(tokens.consume(token,"192.168.1.10",103));
        assertFalse(tokens.consume(tokens.issue("client",100),"client",60101));
    }
    @Test public void foreignBrowserOriginsCannotControlTheTv() {
        assertTrue(AuroraRules.originAllowed("http://192.168.1.12:8899","192.168.1.12:8899"));
        assertFalse(AuroraRules.originAllowed("http://evil.example","192.168.1.12:8899"));
        assertFalse(AuroraRules.originAllowed("http://user@192.168.1.12:8899","192.168.1.12:8899"));
        assertFalse(AuroraRules.originAllowed("null","192.168.1.12:8899"));
        assertFalse(AuroraRules.originAllowed("http://192.168.1.12:8899/extra","192.168.1.12:8899"));
    }
    @Test public void traversalCannotEscapeManagedStorage()throws Exception {
        File root=new File(System.getProperty("java.io.tmpdir"),"aurora-tests");
        assertEquals(new File(root,"themes/foundation/theme.css").getCanonicalFile(),AuroraRules.inside(root,"themes/foundation/theme.css"));
        for(String attack:new String[]{"../private","/etc/passwd","themes/../../private","..\\private"}){
            try{AuroraRules.inside(root,attack);fail(attack);}catch(java.io.IOException expected){}
        }
    }
    @Test public void onlyLanAddressesAndNamedRemoteKeysAreAccepted()throws Exception {
        assertTrue(AuroraRules.lan(InetAddress.getByName("127.0.0.1")));
        assertTrue(AuroraRules.lan(InetAddress.getByName("192.168.1.12")));
        assertTrue(AuroraRules.lan(InetAddress.getByName("fd00::1")));
        assertFalse(AuroraRules.lan(InetAddress.getByName("8.8.8.8")));
        assertEquals(3,AuroraRules.key("home"));
        for(String value:new String[]{"26","power","home; reboot","KEYCODE_HOME"}){
            try{AuroraRules.key(value);fail(value);}catch(IllegalArgumentException expected){}
        }
    }
}
