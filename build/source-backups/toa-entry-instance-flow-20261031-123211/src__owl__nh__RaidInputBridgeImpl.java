package owl.nh;

import com.google.gson.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.unethicalite.api.game.Combat;
import net.unethicalite.api.items.Inventory;
import net.unethicalite.api.widgets.Tab;

/** The only ToA/ToB path to the shared visible combat controls. */
final class RaidInputBridgeImpl implements RaidInputBridge {
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private final CombatRuntime runtime;

 RaidInputBridgeImpl(CombatRuntime runtime){this.runtime=Objects.requireNonNull(runtime);}

 @Override public int tick(){return runtime.client.getTickCount();}
 @Override public long tickElapsedMs(){return runtime.ticks.elapsedMs();}
 @Override public long observedTickPeriodMs(){return runtime.ticks.observedPeriodMs();}

 @Override public String baselineProfile(){
  JsonObject result=new JsonObject();result.addProperty("schema","turbo-raid-baseline-v1");
  JsonObject rawGear=parseObject(runtime.raidGearSnapshot()),gear=new JsonObject();
  for(String style:new String[]{"MAGIC","MELEE","RANGED","SPEC"})gear.add(style,positiveIds(rawGear.get(style)));
  result.add("observedGear",deepCopy(gear));
  JsonObject source=chambersProfile();JsonObject resources=new JsonObject();
  resources.addProperty("eatAtHp",boundedInt(source,"eatAtHp",80,1,120));
  resources.addProperty("restorePrayerAt",40);
  resources.addProperty("potionCooldownTicks",boundedInt(source,"potionCooldownTicks",3,3,10));
  JsonArray order=new JsonArray();for(String value:new String[]{"overload","enhance","aid","aid","aid","aid","revitalisation"})order.add(value);resources.add("prePotOrder",order);
  JsonObject supplies=new JsonObject(),sourceSupplies=object(source,"supplies");
  for(String name:new String[]{"overload","enhance","aid","revitalisation"})supplies.add(name,positiveIds(sourceSupplies.get(name)));
  resources.add("supplies",supplies);
  JsonObject defaults=new JsonObject();defaults.addProperty("MAGIC","AUGURY");defaults.addProperty("MELEE","PIETY");defaults.addProperty("RANGED","RIGOUR");
  JsonObject prayers=object(source,"offensivePrayers"),safePrayers=new JsonObject();
  for(String style:new String[]{"MAGIC","MELEE","RANGED"}){String fallback=defaults.get(style).getAsString(),value=prayers.has(style)&&prayers.get(style).isJsonPrimitive()?prayers.get(style).getAsString():fallback;try{Prayer.valueOf(value);safePrayers.addProperty(style,value);}catch(RuntimeException invalid){safePrayers.addProperty(style,fallback);}}
  resources.add("offensivePrayers",safePrayers);result.add("resources",resources);return GSON.toJson(result);
 }

 private JsonObject chambersProfile(){
  JsonObject active=parseObject(runtime.olm.profileText());
  if(hasSupplyFamilies(active))return active;
  java.nio.file.Path saved=CombatRuntime.projectRoot().toPath().resolve("profiles/olm-profile.json");
  try{if(java.nio.file.Files.isRegularFile(saved)){JsonObject candidate=parseObject(java.nio.file.Files.readString(saved));if(hasSupplyFamilies(candidate))return candidate;}}catch(Exception ignored){}
  return active;
 }
 private static boolean hasSupplyFamilies(JsonObject root){JsonObject supplies=object(root,"supplies");for(String name:new String[]{"overload","enhance","aid","revitalisation"})if(positiveIds(supplies.get(name)).size()==0)return false;return true;}
 private static JsonObject parseObject(String json){try{JsonElement parsed=new JsonParser().parse(json==null?"{}":json);return parsed.isJsonObject()?parsed.getAsJsonObject():new JsonObject();}catch(RuntimeException invalid){return new JsonObject();}}
 private static JsonObject object(JsonObject root,String key){return root!=null&&root.has(key)&&root.get(key).isJsonObject()?root.getAsJsonObject(key):new JsonObject();}
 private static JsonObject deepCopy(JsonObject source){return new JsonParser().parse(source.toString()).getAsJsonObject();}
 private static int boundedInt(JsonObject root,String key,int fallback,int min,int max){try{int value=root.get(key).getAsInt();return value>=min&&value<=max?value:fallback;}catch(RuntimeException invalid){return fallback;}}
 private static JsonArray positiveIds(JsonElement value){JsonArray result=new JsonArray();if(value!=null&&value.isJsonArray())for(JsonElement id:value.getAsJsonArray())try{int next=id.getAsInt();if(next>0&&result.size()<16)result.add(next);}catch(RuntimeException ignored){}return result;}

 @Override public boolean inventoryAction(int itemId,String... actions){
  Item item=Inventory.getFirst(itemId);if(item==null)return false;
  if(!runtime.combatUi.ready(Tab.INVENTORY,60))return false;
  if(!VisibleCombatUi.inventoryVisible(runtime.client,item))return false;
  return runtime.combatUi.inventoryAction(runtime.client,item,actions);
 }

 @Override public boolean activatePrayer(Prayer prayer,int priority){
  if(prayer==null)return false;if(runtime.client.isPrayerActive(prayer))return true;
  if(!runtime.combatUi.ready(Tab.PRAYER,priority))return false;
  return runtime.combatUi.activatePrayer(runtime.client,prayer);
 }

 @Override public boolean activateSpecial(){
  if(Combat.isSpecEnabled())return true;
  if(!runtime.combatUi.ready(Tab.COMBAT,70))return false;
  Widget widget=runtime.client.getWidget(WidgetInfo.COMBAT_SPECIAL_ATTACK_CLICKBOX);
  if(!VisibleCombatUi.effectivelyVisible(widget))widget=runtime.client.getWidget(WidgetInfo.COMBAT_SPECIAL_ATTACK);
  return VisibleCombatUi.effectivelyVisible(widget)&&widget.getBounds()!=null&&runtime.combatUi.activateSpecial(runtime.client,widget);
 }

 @Override public boolean move(WorldPoint tile){return tile!=null&&runtime.combatUi.clickGround(runtime.client,tile);}

 @Override public boolean npcAction(NPC npc,String action){
  if(npc==null||action==null||!npc.hasAction(action)||!runtime.combatUi.claim())return false;
  npc.interact(action);return true;
 }

 @Override public boolean npcMenuAction(NPC npc,String option){
  if(npc==null||option==null)return false;
  if(runtime.client.isMenuOpen()){
   if(runtime.combatUi.chooseNpcMenu(runtime.client,npc,option))return true;
   runtime.combatUi.escape(runtime.client);return false;
  }
  return runtime.combatUi.rightClickNpc(runtime.client,npc);
 }

 @Override public boolean objectAction(TileObject object,String action){
  if(object==null||action==null||!object.hasAction(action)||!runtime.combatUi.claim())return false;
  object.interact(action);return true;
 }

 @Override public void cancelPendingControl(){runtime.combatUi.cancelControl();}
 @Override public String status(){return runtime.combatUi.status;}
}
