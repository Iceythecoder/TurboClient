package turbo.toa;

import com.google.gson.*;
import java.util.*;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;

/** Validated, ToA-owned copy of setup data.  It never mutates Chambers data. */
final class ToaProfile {
 boolean combatImplemented;
 int eatAtHp=80,restorePrayerAt=40,potionCooldownTicks=3;
 int meleeWeaponId=-1,rapierId=-1,avernicDefenderId=-1,elderMaulId=-1,rangedWeaponId=-1,bootsId=-1,blessingId=-1;
 final List<Integer> coreGear=new ArrayList<>();
 final EnumMap<ToaCombatPolicy.Style,List<Integer>> gear=new EnumMap<>(ToaCombatPolicy.Style.class);
 final Map<String,List<Integer>> supplies=new LinkedHashMap<>();
 final Map<String,Prayer> offensive=new LinkedHashMap<>();
 final Set<Integer> osmumten=new LinkedHashSet<>(),obelisk=new LinkedHashSet<>(),chargingWardens=new LinkedHashSet<>(),elidinis=new LinkedHashSet<>(),core=new LinkedHashSet<>(),tumeken=new LinkedHashSet<>(),siphon=new LinkedHashSet<>(),zebak=new LinkedHashSet<>(),baba=new LinkedHashSet<>();
 final Set<Integer> floorAnimations=new LinkedHashSet<>(),floorGraphics=new LinkedHashSet<>(),floorObjects=new LinkedHashSet<>(),babaGraphics=new LinkedHashSet<>(),imprisonmentGraphics=new LinkedHashSet<>();
 final Map<Integer,String> protectionProjectiles=new LinkedHashMap<>();
 WorldPoint blockTile,wardenCampTile;
 int attackRange=10;

 static ToaProfile parse(JsonObject root){
  if(root==null)throw new IllegalArgumentException("Load ToA instance data first");
  ToaProfile p=new ToaProfile();p.combatImplemented=bool(root,"combatImplemented",false);
  JsonObject resources=object(root,"resources");
  p.eatAtHp=integer(resources,"eatAtHp",integer(root,"eatAtHp",80));
  p.restorePrayerAt=integer(resources,"restorePrayerAt",integer(root,"restorePrayerAt",40));
  p.potionCooldownTicks=integer(resources,"potionCooldownTicks",integer(root,"potionCooldownTicks",3));
  JsonObject supplyRoot=object(resources,"supplies");if(supplyRoot.size()==0)supplyRoot=object(root,"supplies");
  for(String kind:Arrays.asList("overload","enhance","aid","revitalisation"))p.supplies.put(kind,ids(supplyRoot,kind));
  JsonObject gear=object(root,"observedGear");for(ToaCombatPolicy.Style style:ToaCombatPolicy.Style.values())p.gear.put(style,ids(gear,style.name()));p.coreGear.addAll(ids(gear,"CORE"));for(List<Integer> setup:Arrays.asList(p.gear.get(ToaCombatPolicy.Style.MELEE),p.gear.get(ToaCombatPolicy.Style.SPEC),p.coreGear))upgradeTorva(setup);
  JsonObject weapons=object(root,"weapons");p.meleeWeaponId=integer(weapons,"meleeWeaponId",integer(root,"meleeWeaponId",-1));p.rapierId=integer(weapons,"rapierId",integer(root,"rapierId",-1));p.avernicDefenderId=integer(weapons,"avernicDefenderId",integer(root,"avernicDefenderId",-1));p.elderMaulId=integer(weapons,"elderMaulId",integer(root,"elderMaulId",-1));p.rangedWeaponId=integer(weapons,"rangedWeaponId",integer(weapons,"twistedBowId",integer(root,"twistedBowId",-1)));p.bootsId=integer(weapons,"bootsId",-1);p.blessingId=integer(weapons,"blessingId",-1);
  JsonObject prayers=object(resources,"offensivePrayers");if(prayers.size()==0)prayers=object(root,"offensivePrayers");
  prayer(prayers,"MAGIC",p.offensive);prayer(prayers,"MELEE",p.offensive);prayer(prayers,"RANGED",p.offensive);
  if(!p.offensive.containsKey("MAGIC"))p.offensive.put("MAGIC",Prayer.AUGURY);
  if(!p.offensive.containsKey("MELEE"))p.offensive.put("MELEE",Prayer.PIETY);
  if(!p.offensive.containsKey("RANGED"))p.offensive.put("RANGED",Prayer.RIGOUR);
  JsonObject mechanics=object(root,"mechanics"),npc=object(mechanics,"npcIds"),signals=object(mechanics,"signals");
  p.osmumten.addAll(ids(npc,"osmumten"));p.obelisk.addAll(ids(npc,"obelisk"));p.chargingWardens.addAll(ids(npc,"chargingWardens"));p.elidinis.addAll(ids(npc,"elidinis"));p.core.addAll(ids(npc,"core"));p.tumeken.addAll(ids(npc,"tumeken"));p.siphon.addAll(ids(npc,"siphon"));p.zebak.addAll(ids(npc,"zebak"));p.baba.addAll(ids(npc,"baba"));
  // Stable RuneLite/Orion candidate identities.  They identify roles only;
  // animations/projectiles remain profile-calibrated before actions are sent.
  if(p.osmumten.isEmpty())Collections.addAll(p.osmumten,11689,11690,11692,11693);
  if(p.obelisk.isEmpty())Collections.addAll(p.obelisk,11698,11699,11750,11751,11752);
  if(p.chargingWardens.isEmpty())Collections.addAll(p.chargingWardens,11746,11747);
  if(p.elidinis.isEmpty())Collections.addAll(p.elidinis,11753,11754,11755,11759);
  if(p.core.isEmpty())Collections.addAll(p.core,11770,11771);
  // Only final-arena forms belong here. Charging/phase-two Warden IDs would
  // incorrectly skip the obelisk and Elidinis phases when all statues are visible.
  if(p.tumeken.isEmpty())Collections.addAll(p.tumeken,11762,11764);
  if(p.siphon.isEmpty())p.siphon.add(11772);if(p.zebak.isEmpty())p.zebak.add(11774);if(p.baba.isEmpty())p.baba.add(11775);
  p.floorAnimations.addAll(ids(signals,"floorSlamAnimations"));p.floorGraphics.addAll(ids(signals,"floorSlamGraphics"));p.floorObjects.addAll(ids(signals,"floorSlamObjects"));p.babaGraphics.addAll(ids(signals,"babaRockGraphics"));p.imprisonmentGraphics.addAll(ids(signals,"imprisonmentGraphics"));
  JsonObject projectiles=object(signals,"protectionProjectiles");for(Map.Entry<String,JsonElement> e:projectiles.entrySet())p.protectionProjectiles.put(Integer.parseInt(e.getKey()),e.getValue().getAsString().toUpperCase(Locale.ROOT));
  for(int id:new int[]{2204,2206,2208,2224,2241})if(!p.protectionProjectiles.containsKey(id))p.protectionProjectiles.put(id,ToaSignals.protection(id));
  p.blockTile=point(root,"blockTile");if(p.blockTile==null)p.blockTile=point(root,"roomPosition");p.wardenCampTile=point(root,"wardenCampTile");p.attackRange=integer(mechanics,"attackRange",10);
  p.validate();return p;
 }

 void validate(){
  if(!combatImplemented)throw new IllegalArgumentException("This ToA profile has not enabled prototype combat. Use Load instance data first.");
  if(eatAtHp!=80||restorePrayerAt!=40)throw new IllegalArgumentException("The prototype requires the Chambers guards: 80 HP and 40 Prayer");
  if(potionCooldownTicks<3||potionCooldownTicks>10)throw new IllegalArgumentException("Potion cooldown must be 3-10 server ticks");
  for(String key:Arrays.asList("overload","enhance","aid","revitalisation")){List<Integer> v=supplies.get(key);if(v==null||v.isEmpty()||v.size()>16)throw new IllegalArgumentException("Missing copied Chambers potion IDs: "+key);}
  for(ToaCombatPolicy.Style style:Arrays.asList(ToaCombatPolicy.Style.MAGIC,ToaCombatPolicy.Style.MELEE,ToaCombatPolicy.Style.RANGED,ToaCombatPolicy.Style.SPEC)){List<Integer> v=gear.get(style);if(v==null||v.isEmpty()||v.size()>14)throw new IllegalArgumentException("Missing ToA "+style+" gear preset");}
  if(meleeWeaponId<=0||!style(ToaCombatPolicy.Style.MELEE).contains(meleeWeaponId))throw new IllegalArgumentException("ToA MELEE needs a positively charged Scythe of Vitur");
  if(rapierId<=0||avernicDefenderId<=0||!coreGear.contains(rapierId)||!coreGear.contains(avernicDefenderId))throw new IllegalArgumentException("ToA CORE needs the observed Ghrazi rapier and Avernic defender");
  if(elderMaulId<=0||!style(ToaCombatPolicy.Style.SPEC).contains(elderMaulId))throw new IllegalArgumentException("ToA SPEC needs the observed Elder maul");
  if(rangedWeaponId<=0||!style(ToaCombatPolicy.Style.RANGED).contains(rangedWeaponId))throw new IllegalArgumentException("ToA RANGED needs the observed Holy Cards");
  if(bootsId<=0||gear.values().stream().anyMatch(items->!items.contains(bootsId)))throw new IllegalArgumentException("Every ToA setup needs the observed Avernic Treads");
  if(blessingId<=0||gear.values().stream().anyMatch(items->!items.contains(blessingId)))throw new IllegalArgumentException("Every ToA setup needs the observed blessing instead of Dragon arrows");
  if(!coreGear.contains(bootsId)||!coreGear.contains(blessingId))throw new IllegalArgumentException("The ToA CORE setup needs the shared Avernic Treads and blessing");
  if(blockTile==null)throw new IllegalArgumentException("Load instance data while standing on the obelisk blocking tile");
  if(attackRange<1||attackRange>10)throw new IllegalArgumentException("ToA attack range must be 1-10 tiles");
  for(Set<Integer> signals:Arrays.asList(floorAnimations,floorGraphics,floorObjects,babaGraphics,imprisonmentGraphics))if(signals.size()>128)throw new IllegalArgumentException("Too many ToA signal IDs");
  if(protectionProjectiles.size()>128||protectionProjectiles.values().stream().anyMatch(v->!Arrays.asList("MAGIC","RANGED","MISSILES","MELEE").contains(v)))throw new IllegalArgumentException("Invalid ToA protection-projectile mapping");
 }

 boolean hasCalibratedFloorSignal(){return !floorAnimations.isEmpty()||!floorGraphics.isEmpty()||!floorObjects.isEmpty();}
 List<Integer> supply(String kind){List<Integer> v=supplies.get(kind);return v==null?Collections.emptyList():v;}
 List<Integer> style(ToaCombatPolicy.Style style){List<Integer> v=gear.get(style);return v==null?Collections.emptyList():v;}
 private static void upgradeTorva(List<Integer> gear){if(gear==null)return;int old=gear.indexOf(24271);if(old>=0){if(gear.contains(26382))gear.remove(old);else gear.set(old,26382);}}

 private static void prayer(JsonObject source,String key,Map<String,Prayer> out){if(source.has(key))try{out.put(key,Prayer.valueOf(source.get(key).getAsString()));}catch(Exception e){throw new IllegalArgumentException("Invalid "+key+" offensive prayer");}}
 private static boolean bool(JsonObject o,String key,boolean fallback){return o!=null&&o.has(key)?o.get(key).getAsBoolean():fallback;}
 private static int integer(JsonObject o,String key,int fallback){return o!=null&&o.has(key)?o.get(key).getAsInt():fallback;}
 private static JsonObject object(JsonObject o,String key){return o!=null&&o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):new JsonObject();}
 private static List<Integer> ids(JsonObject o,String key){List<Integer> result=new ArrayList<>();if(o!=null&&o.has(key)&&o.get(key).isJsonArray())for(JsonElement e:o.getAsJsonArray(key)){int id=e.getAsInt();if(id>0&&!result.contains(id))result.add(id);}return result;}
 private static WorldPoint point(JsonObject o,String key){if(o==null||!o.has(key)||!o.get(key).isJsonArray())return null;JsonArray a=o.getAsJsonArray(key);if(a.size()!=3)return null;return new WorldPoint(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
}
