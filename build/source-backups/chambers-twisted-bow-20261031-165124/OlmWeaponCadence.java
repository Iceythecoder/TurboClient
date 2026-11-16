package owl.nh;
import java.util.*;
/** Video-measured cadence is weapon-scoped; input requests are never shot evidence. */
final class OlmWeaponCadence {
 static Map<String,Integer> defaults(){Map<String,Integer> m=new LinkedHashMap<>();m.put("holy cards",2);m.put("eye of ayak",2);return m;}
 static Integer speed(Map<String,Integer> timings,String name){return name==null||timings==null?null:timings.get(name.replaceAll("<[^>]*>","").trim().toLowerCase(Locale.ROOT));}
 static boolean attributable(int px,int py,int sx,int sy,int age,boolean sameFloor,boolean targetMatches,boolean sharedOrigin){return px==sx&&py==sy&&age>=-2&&age<=3&&sameFloor&&targetMatches&&!sharedOrigin;}
}
