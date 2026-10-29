package com.gathin.tvhome;
import org.junit.*;import static org.junit.Assert.*;import java.io.*;import java.nio.charset.StandardCharsets;
public class OfflineTimezoneTest {
 private OfflineTimezone map;
 @Before public void load()throws Exception{try(InputStream in=getClass().getResourceAsStream("/world-zones.dat")){assertNotNull(in);map=OfflineTimezone.read(in);}}
 @Test public void losAngelesUsesItsOwnZone(){assertEquals("America/Los_Angeles",map.lookup(34.05223,-118.24368));}
 @Test public void sameCountryCanHaveDifferentZones(){assertEquals("America/New_York",map.lookup(40.7128,-74.006));assertEquals("America/Chicago",map.lookup(41.8781,-87.6298));}
 @Test public void citiesAroundTheWorld(){assertEquals("Asia/Shanghai",map.lookup(31.2304,121.4737));assertEquals("Europe/London",map.lookup(51.5074,-.1278));assertEquals("Europe/Berlin",map.lookup(52.52,13.405));assertEquals("Asia/Tokyo",map.lookup(35.6762,139.6503));assertEquals("Australia/Sydney",map.lookup(-33.8688,151.2093));assertEquals("Asia/Kathmandu",map.lookup(27.7172,85.324));}
 @Test public void polesAndDateLineAreBounded(){assertEquals("Etc/GMT",map.lookup(90,180));assertNotNull(map.lookup(-90,-180));assertNotNull(map.lookup(0,180));assertNotNull(map.lookup(0,-180));}
 @Test public void invalidCoordinatesAreRejected(){for(double[] point:new double[][]{{91,0},{0,181},{Double.NaN,0},{0,Double.POSITIVE_INFINITY}})try{map.lookup(point[0],point[1]);fail();}catch(IllegalArgumentException expected){}}
 @Test public void javaWalkMatches1000UpstreamSyntheticPoints()throws Exception{try(BufferedReader r=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/timezone-reference.tsv"),StandardCharsets.UTF_8))){String line;int count=0;while((line=r.readLine())!=null){String[] p=line.split("\t");assertEquals(line,p[2],map.lookup(Double.parseDouble(p[0]),Double.parseDouble(p[1])));count++;}assertEquals(1000,count);}}
 @Test public void brokenDatabaseIsRejected()throws Exception{try{OfflineTimezone.read(new ByteArrayInputStream("bad\nEtc/UTC\n".getBytes(StandardCharsets.UTF_8)));fail();}catch(IOException expected){}}
}
