package owl.nh;
import java.util.*;
/** Video-measured cadence is weapon-scoped; input requests are never shot evidence. */
final class OlmWeaponCadence {
 static Map<String,Integer> defaults(){Map<String,Integer> m=new LinkedHashMap<>();m.put("holy cards",2);m.put("eye of ayak",2);m.put("twisted bow",4);return m;}
 static Map<String,Integer> withDefaults(Map<String,Integer> configured){Map<String,Integer> merged=new LinkedHashMap<>();if(configured!=null)merged.putAll(configured);defaults().forEach(merged::putIfAbsent);return merged;}
 static Integer speed(Map<String,Integer> timings,String name){if(name==null||timings==null)return null;String normalized=name.replaceAll("<[^>]*>","").trim().toLowerCase(Locale.ROOT);Integer exact=timings.get(normalized);if(exact!=null)return exact;for(Map.Entry<String,Integer> timing:timings.entrySet())if(normalized.contains(timing.getKey().toLowerCase(Locale.ROOT)))return timing.getValue();return null;}
 static boolean attributable(int px,int py,int sx,int sy,int age,boolean sameFloor,boolean targetMatches,boolean sharedOrigin){return px==sx&&py==sy&&age>=-2&&age<=3&&sameFloor&&targetMatches&&!sharedOrigin;}
}
