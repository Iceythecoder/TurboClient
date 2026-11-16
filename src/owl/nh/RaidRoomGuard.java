package owl.nh;

import java.util.*;

/** Prevents one raid runner from accepting another raid's room. */
final class RaidRoomGuard {
 private static final Set<Integer> OSMUMTEN=new HashSet<>(Arrays.asList(11689,11690,11692,11693));
 private static final Set<Integer> OBELISK=new HashSet<>(Arrays.asList(11698,11699,11750,11751,11752));
 private static final Set<Integer> WARDEN=new HashSet<>(Arrays.asList(11746,11747,11753,11754,11755,11759,11762,11764));
 private RaidRoomGuard(){}

 static boolean toa(Collection<Integer> ids,Collection<String> names){
  boolean osm=false,obelisk=false,warden=false;
  if(ids!=null)for(int id:ids){osm|=OSMUMTEN.contains(id);obelisk|=OBELISK.contains(id);warden|=WARDEN.contains(id);}
  if(names!=null)for(String raw:names){String name=raw==null?"":raw.replaceAll("<[^>]*>"," ").toLowerCase(Locale.ROOT);osm|=name.contains("osmumten");obelisk|=name.contains("obelisk");warden|=name.contains("warden");}
  return osm&&(obelisk||warden)||obelisk&&warden;
 }
}
