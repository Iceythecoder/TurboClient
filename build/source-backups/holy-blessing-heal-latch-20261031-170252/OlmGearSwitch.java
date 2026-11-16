package owl.nh;
import java.util.*;

/** Issue remaining items while earlier items await container acknowledgement. No server-tick delay. */
final class OlmGearSwitch {
 static boolean twistedBow(String name){return name!=null&&name.replaceAll("<[^>]*>","").trim().toLowerCase(Locale.ROOT).contains("twisted bow");}
 /** Replace only the ranged weapon when a Twisted bow is actually available. */
 static boolean recogniseTwistedBow(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.IntPredicate isWeapon){
  int bow=-1;for(int id:available)if(twistedBow(names.apply(id))){bow=id;break;}if(bow<0)return false;
  boolean changed=false;for(int i=required.size()-1;i>=0;i--){int id=required.get(i);if(id!=bow&&isWeapon.test(id)){required.remove(i);changed=true;}}
  if(!required.contains(bow)){required.add(bow);changed=true;}return changed;
 }
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
   int old=pair[0],upgrade=pair[1];if(!required.contains(old)||pending.contains(old)||old!=net.runelite.api.ItemID.ANCESTRAL_HAT&&(worn.contains(old)||inventory.contains(old))||!worn.contains(upgrade)&&!inventory.contains(upgrade))continue;
   int index=required.indexOf(old);if(required.contains(upgrade))required.remove(index);else required.set(index,upgrade);changed.put(old,upgrade);
  }return changed;
 }
 static boolean boots(int id,String name,int slot,int currentBoots){if(id==currentBoots||slot==net.runelite.api.EquipmentInventorySlot.BOOTS.getSlotIdx()||id==13237)return true;String n=name==null?"":name.replaceAll("<[^>]*>","").toLowerCase(Locale.ROOT);return n.matches("(^|.*[^a-z])(boots|sandals|slippers|shoes|greaves|sabatons|footwraps)([^a-z].*|$)");}
 static void keepBoots(List<Integer> required,java.util.function.IntPredicate isBoots){required.removeIf(id->isBoots.test(id));}
 static void exclude(List<Integer> required,Set<Integer> excluded){required.removeIf(excluded::contains);}
 static int next(List<Integer> required,Set<Integer> worn,Set<Integer> pending){for(int id:required)if(!worn.contains(id)&&!pending.contains(id))return id;return -1;}
}



