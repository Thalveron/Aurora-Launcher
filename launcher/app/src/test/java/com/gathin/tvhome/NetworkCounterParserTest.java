package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class NetworkCounterParserTest {
 @Test public void parsesUidCountersAndTags(){List<NetworkCounterParser.Row> rows=NetworkCounterParser.qtaguid("idx iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes tx_packets\n2 wlan0 0x0 10123 0 10240 10 2048 3\n3 wlan0 0x100000000 10123 0 100 1 20 1");assertEquals(2,rows.size());assertEquals(10123,rows.get(0).uid);assertEquals(10240,rows.get(0).rx);assertEquals(2048,rows.get(0).tx);assertEquals(0,rows.get(0).tag);assertEquals(1,rows.get(1).tag);}
 @Test public void skipsMalformedAndNegativeCounters(){assertEquals(0,NetworkCounterParser.qtaguid("idx iface tag uid set rx rp tx tp\n2 wlan0 0x0 x 0 1 2 3 4\n3 wlan0 0x0 123 0 -1 2 3 4").size());}
 @Test public void parsesInterfacesWithoutConfusingHeaders(){List<NetworkCounterParser.Row> rows=NetworkCounterParser.interfaces("Inter-| Receive | Transmit\n eth0: 12345 12 0 0 0 0 0 0 6789 6 0 0 0 0 0 0\n lo: 1 1 0 0 0 0 0 0 1 1 0 0 0 0 0 0");assertEquals(2,rows.size());assertEquals("eth0",rows.get(0).iface);assertEquals(12345,rows.get(0).rx);assertEquals(6789,rows.get(0).tx);}
 @Test public void uidListsAreSortedAndDeduplicated(){assertArrayEquals(new int[]{1000,10001},NetworkCounterParser.uids("10001,1000,10001"));assertEquals(0,NetworkCounterParser.uids("").length);}
 @Test public void rejectsCommandsAndInvalidUidLists(){for(String value:new String[]{"1000;id","-1","0\nSTOP","1,abc"})try{NetworkCounterParser.uids(value);fail();}catch(IllegalArgumentException expected){}}
}
