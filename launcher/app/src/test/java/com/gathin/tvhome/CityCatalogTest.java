package com.gathin.tvhome;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CityCatalogTest {
    private List<CityCatalog.City> cities()throws Exception{return CityCatalog.read(getClass().getResourceAsStream("/china-cities.tsv"));}
    @Test public void leiyangWorksOfflineWithChineseAndPinyin()throws Exception {
        List<CityCatalog.City> all=cities();assertTrue(all.size()>10000);
        for(String query:new String[]{"耒阳","耒阳市","Leiyang","lei yang"}){
            CityCatalog.City city=CityCatalog.search(all,query).get(0);
            assertEquals("湖南",city.region);assertEquals("Asia/Shanghai",city.timezone);
            assertEquals(26.40,city.lat,.1);assertEquals(112.85,city.lon,.1);
        }
    }
    @Test public void hengyangResolvesHunanBeforeAnyPartialMatch()throws Exception {
        CityCatalog.City city=CityCatalog.search(cities(),"衡阳").get(0);
        assertEquals("湖南",city.region);assertTrue(city.display("衡阳").contains("衡阳"));
    }
    @Test public void foreignCitiesContinueToOnlineSearch()throws Exception {
        assertTrue(CityCatalog.search(cities(),"Los Angeles").isEmpty());
        assertTrue(CityCatalog.search(cities(),"不存在的城市abcdef").isEmpty());
    }
}
