package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;
public class SpecifiedReleaseTest {
 @Test public void normalizesPublishedVersion(){assertEquals("0.1.3",SpecifiedRelease.normalize(" v0.1.3 "));assertEquals("0.1.4-beta.1",SpecifiedRelease.normalize("0.1.4-beta.1"));}
 @Test public void cannotEscapePublisherTag(){for(String value:new String[]{"../v0.1.3","0.1.3/evil","0.1.3?token=x","0.1.3#x","https://evil.test","v1",""})try{SpecifiedRelease.normalize(value);fail(value);}catch(IllegalArgumentException expected){}}
}
