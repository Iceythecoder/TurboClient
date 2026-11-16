package owl.nh;
import java.util.*;

/** Issue remaining items while earlier items await container acknowledgement. No server-tick delay. */
final class OlmGearSwitch {
 /** Resolve Orion's server-specific card name from actual available definitions. */
 static void recogniseHolyCards(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names){
  for(int id:available){String name=names.apply(id);if(name==null||!name.replaceAll("<[^>]*>","").trim().equalsIgnoreCase("holy cards"))continue;
   for(int i=0;i<required.size();i++){int old=required.get(i);String oldName=names.apply(old);if(old==net.runelite.api.ItemID.MYSTIC_CARDS||oldName!=null&&oldName.toLowerCase(Locale.ROOT).contains("cards")){required.set(i,id);return;}}
   return;
  }
 }
 /** Recognised cosmetic upgrades only; never substitute an unrelated item or silently downgrade. */
 static Map<Integer,Integer> recogniseUpgrades(List<Integer> required,Set<Integer> worn,Set<Integer> inventory,Set<Integer> pending){
  Map<Integer,Integer> changed=new LinkedHashMap<>();
  for(int[] pair:new int[][]{{net.runelite.api.ItemID.ANCESTRAL_HAT,net.runelite.api.ItemID.TWISTED_ANCESTRAL_HAT},{net.runelite.api.ItemID.ANCESTRAL_ROBE_TOP,net.runelite.api.ItemID.TWISTED_ANCESTRAL_ROBE_TOP},{net.runelite.api.ItemID.ANCESTRAL_ROBE_BOTTOM,net.runelite.api.ItemID.TWISTED_ANCESTRAL_ROBE_BOTTOM}}){
   int old=pair[0],upgrade=pair[1];if(!required.contains(old)||worn.contains(old)||inventory.contains(old)||pending.contains(old)||!worn.contains(upgrade)&&!inventory.contains(upgrade))continue;
   int index=required.indexOf(old);if(required.contains(upgrade))required.remove(index);else required.set(index,upgrade);changed.put(old,upgrade);
  }return changed;
 }
 static void exclude(List<Integer> required,Set<Integer> excluded){required.removeIf(excluded::contains);}
 static int next(List<Integer> required,Set<Integer> worn,Set<Integer> pending){for(int id:required)if(!worn.contains(id)&&!pending.contains(id))return id;return -1;}
}

