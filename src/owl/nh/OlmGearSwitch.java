package owl.nh;
import java.util.*;

/** Issue remaining items while earlier items await container acknowledgement. No server-tick delay. */
final class OlmGearSwitch {
 static final int HOLY_BLESSING=20220;
 static String normalized(String name){return name==null?"":name.replaceAll("<[^>]*>"," ").replace('\u00a0',' ').trim().toLowerCase(Locale.ROOT);}
 static boolean blessing(int id,String name){String n=normalized(name);return id==HOLY_BLESSING||n.contains("blessing");}
 static boolean holyBlessing(int id,String name){return blessing(id,name);}
 static void removeHolyBlessing(List<Integer> required,java.util.function.IntFunction<String> names){required.removeIf(id->holyBlessing(id,names==null?null:names.apply(id)));}
 static boolean holyCards(String name){return normalized(name).contains("holy cards");}
 static boolean twistedBow(String name){return normalized(name).contains("twisted bow");}
 static boolean chargedScythe(String name){String n=normalized(name);return n.contains("scythe of vitur")&&!n.contains("uncharged")&&!n.matches(".*(?:\\(0\\)|0\\s*charges?).*");}
 static boolean avernicTreads(String name){return normalized(name).contains("avernic treads");}
 static boolean torvaHelm(String name){String n=normalized(name);return n.contains("torva")&&n.contains("helm");}
 static boolean archersRing(String name){return normalized(name).contains("archers ring");}
 static boolean archersRingI(String name){String n=normalized(name);return n.contains("archers ring")&&(n.contains("(i)")||n.matches(".*archers ring[ ]+i(?:$|[^a-z].*).*"));}
 static boolean seersRing(String name){return normalized(name).contains("seers ring");}
 static boolean seersRingI(String name){String n=normalized(name);return n.contains("seers ring")&&(n.contains("(i)")||n.matches(".*seers ring[ ]+i(?:$|[^a-z].*).*"));}
 static boolean barrowsGloves(String name){return normalized(name).contains("barrows gloves");}
 static boolean ferociousGloves(String name){return normalized(name).contains("ferocious gloves");}
 static boolean zaryteVambraces(String name){return normalized(name).contains("zaryte vambraces");}
 static boolean tormentedBracelet(String name){return normalized(name).contains("tormented bracelet");}
 static boolean conflictionGauntlets(String name){String n=normalized(name);return n.contains("confliction")&&(n.contains("gaunt")||n.contains("gaunlet")||n.contains("glove"));}
 static boolean styleGloves(String name){return barrowsGloves(name)||ferociousGloves(name)||zaryteVambraces(name)||tormentedBracelet(name)||conflictionGauntlets(name);}
 static boolean elderWarhammer(String name){return normalized(name).contains("elder warhammer");}
 static boolean torvaPlatelegs(String name){String n=normalized(name);return n.contains("torva")&&n.contains("platelegs");}
 static boolean meleeLegs(String name){String n=normalized(name);return torvaPlatelegs(name)||n.contains("vesta")&&n.contains("plateskirt");}
 static boolean amuletOfTorture(String name){return normalized(name).contains("amulet of torture");}
 static boolean meleeAmulet(String name){String n=normalized(name);return amuletOfTorture(name)||n.contains("blood fury");}
 static boolean faceguard(int id,String name){return id==24271||normalized(name).contains("faceguard");}
 static boolean dragonArrows(String name){String n=normalized(name);return n.contains("dragon arrow");}
 /** Replace only the ranged weapon when a Twisted bow is actually available. */
 static boolean recogniseTwistedBow(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.IntPredicate isWeapon){
  int bow=-1;for(int id:available)if(twistedBow(names.apply(id))){bow=id;break;}if(bow<0)return false;
  boolean changed=false;for(int i=required.size()-1;i>=0;i--){int id=required.get(i);if(id!=bow&&isWeapon.test(id)){required.remove(i);changed=true;}}
  if(!required.contains(bow)){required.add(bow);changed=true;}return changed;
 }
 /** Resolve Orion's server-specific card name from actual available definitions. */
 static void recogniseHolyCards(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names){
  recogniseNamed(required,available,names,OlmGearSwitch::holyCards,name->normalized(name).contains("cards")||twistedBow(name)||normalized(name).contains("eye of ayak"),null);
 }
 static boolean recogniseChargedScythe(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.IntPredicate isWeapon,java.util.function.IntPredicate isOffhand){return recogniseNamed(required,available,names,OlmGearSwitch::chargedScythe,name->chargedScythe(name)||normalized(name).contains("ghrazi rapier")||normalized(name).contains("dragon hunter lance")||normalized(name).matches(".*(?:^| )rapier.*"),id->isWeapon.test(id)||isOffhand.test(id));}
 static boolean recogniseElderWarhammer(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.IntPredicate isWeapon,java.util.function.IntPredicate isOffhand){return recogniseNamed(required,available,names,OlmGearSwitch::elderWarhammer,name->elderWarhammer(name)||chargedScythe(name)||normalized(name).contains("elder maul"),id->isWeapon.test(id)||isOffhand.test(id));}
 static boolean recogniseAvernicTreads(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.IntPredicate isBoots){return recogniseNamed(required,available,names,OlmGearSwitch::avernicTreads,OlmGearSwitch::avernicTreads,isBoots);}
 static boolean recogniseTorvaHelm(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names){return recogniseNamed(required,available,names,OlmGearSwitch::torvaHelm,name->torvaHelm(name)||normalized(name).contains("faceguard"),id->faceguard(id,names.apply(id)));}
 static boolean recogniseTorvaPlatelegs(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.IntPredicate isLegs){return recogniseNamed(required,available,names,OlmGearSwitch::torvaPlatelegs,OlmGearSwitch::meleeLegs,isLegs);}
 static boolean recogniseAmuletOfTorture(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.IntPredicate isAmulet){return recogniseNamed(required,available,names,OlmGearSwitch::amuletOfTorture,OlmGearSwitch::meleeAmulet,isAmulet);}
 static boolean recogniseBlessing(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.IntPredicate isAmmo){return recogniseNamed(required,available,names,name->normalized(name).contains("blessing"),name->normalized(name).contains("blessing")||dragonArrows(name),isAmmo);}
 static boolean recogniseArchersRingI(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.IntPredicate isRing){return recogniseNamed(required,available,names,OlmGearSwitch::archersRingI,OlmGearSwitch::archersRing,isRing);}
 static boolean recogniseSeersRingI(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names){return recogniseNamed(required,available,names,OlmGearSwitch::seersRingI,OlmGearSwitch::seersRing,null);}
 static boolean recogniseSeersRingI(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.IntPredicate isRing){return recogniseNamed(required,available,names,OlmGearSwitch::seersRingI,OlmGearSwitch::seersRing,isRing);}
 static boolean recogniseStyleGloves(CombatCore.Style style,List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names){
  java.util.function.Predicate<String> desired=style==CombatCore.Style.MAGIC?OlmGearSwitch::conflictionGauntlets:style==CombatCore.Style.RANGED?OlmGearSwitch::zaryteVambraces:style==CombatCore.Style.MELEE||style==CombatCore.Style.SPEC?OlmGearSwitch::ferociousGloves:name->false;
  return recogniseNamed(required,available,names,desired,OlmGearSwitch::styleGloves,null);
 }
 private static boolean recogniseNamed(List<Integer> required,Set<Integer> available,java.util.function.IntFunction<String> names,java.util.function.Predicate<String> desired,java.util.function.Predicate<String> replaced,java.util.function.IntPredicate slot){
  int selected=-1;for(int id:available)if(desired.test(names.apply(id))){selected=id;break;}if(selected<0)return false;boolean changed=false;
  for(int i=required.size()-1;i>=0;i--){int id=required.get(i);if(id!=selected&&(replaced.test(names.apply(id))||slot!=null&&slot.test(id))){required.remove(i);changed=true;}}
  if(!required.contains(selected)){required.add(selected);changed=true;}return changed;
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
 /** Keep a style switch strict for every preset item that is actually present, while
  * ignoring stale preset ids that cannot possibly be equipped in this session. */
 static void keepAvailable(List<Integer> required,Set<Integer> available){required.removeIf(id->!available.contains(id));}
 static void exclude(List<Integer> required,Set<Integer> excluded){required.removeIf(excluded::contains);}
 static int next(List<Integer> required,Set<Integer> worn,Set<Integer> pending){for(int id:required)if(!worn.contains(id)&&!pending.contains(id))return id;return -1;}
}



