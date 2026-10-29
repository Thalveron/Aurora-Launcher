package com.gathin.tvhome;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local Chinese aliases prevent a remote transliteration from resolving to the wrong city. */
final class CityCatalog {
    static final class City {
        final String name, aliases, region, timezone;
        final String[] searchKeys;
        final double lat, lon;
        final long population;
        City(String[] v) { name=v[1]; aliases=v[2]; region=v[3]; lat=Double.parseDouble(v[4]);
            lon=Double.parseDouble(v[5]); population=Long.parseLong(v[6]); timezone=CityTime.zone(v[7]); searchKeys=Arrays.stream(aliases.split("\\|")).map(CityCatalog::key).toArray(String[]::new); }
        String display(String query) {
            for(String alias:aliases.split("\\|"))if(key(alias).equals(key(query))&&han(alias))return alias;
            for(String alias:aliases.split("\\|"))if(han(alias))return alias;
            return name;
        }
    }
    static List<City> read(InputStream input)throws IOException {
        List<City> cities=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null){String[] values=line.split("\t",-1);
                if(values.length!=8)throw new IOException("城市数据格式损坏");
                cities.add(new City(values));
            }
        }return Collections.unmodifiableList(cities);
    }
    static String key(String value){return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+","").replaceAll("[市县区]$","");}
    private static boolean han(String value){for(char c:value.toCharArray())if(c>='\u4e00'&&c<='\u9fff')return true;return false;}
    private static int score(City city,String query){for(String alias:city.searchKeys)if(alias.equals(query))return 0;
        if(query.length()>=2)for(String alias:city.searchKeys)if(alias.startsWith(query))return 1;
        return 2;
    }
    static List<City> search(List<City> cities,String query){String key=key(query);List<City> matches=new ArrayList<>();
        for(City city:cities)if(score(city,key)<2)matches.add(city);
        matches.sort(Comparator.<City>comparingInt(c->score(c,key)).thenComparing(Comparator.comparingLong((City c)->c.population).reversed()));
        return matches.subList(0,Math.min(matches.size(),12));
    }
}
