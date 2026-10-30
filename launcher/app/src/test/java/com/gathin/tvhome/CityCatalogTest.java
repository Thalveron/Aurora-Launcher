package com.gathin.tvhome;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CityCatalogTest {
    private List<CityCatalog.City> cities()throws Exception{return CityCatalog.read(getClass().getResourceAsStream("/china-cities.tsv"));}
    @Test public void cityWorksOfflineWithChineseAndPinyin()throws Exception {
        List<CityCatalog.City> all=cities();assertTrue(all.size()>10000);
        for(String query:new String[]{"上海","上海市","Shanghai","shang hai"}){
            CityCatalog.City city=CityCatalog.search(all,query).get(0);
            assertEquals("上海",city.region);assertEquals("Asia/Shanghai",city.timezone);
            assertEquals(31.22,city.lat,.1);assertEquals(121.46,city.lon,.1);
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
