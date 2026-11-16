package owl.nh;

import java.util.*;
import java.util.function.*;
import net.runelite.api.*;

/** Style boosts use the visible Prayer tab and never take a defensive action's slot. */
final class OlmOffensivePrayers {
 private final Client client;private final BooleanSupplier ui;private final Consumer<Prayer> output;
 private java.util.function.Predicate<Prayer> verifiedOutput;void verifiedOutput(java.util.function.Predicate<Prayer> next){verifiedOutput=next;}
 private Prayer pending;private int requestedAt,uiWaitSince=-1;
 private final EnumMap<Prayer,Integer> retryAfter=new EnumMap<>(Prayer.class);
 String status="Offensive prayer idle";
 OlmOffensivePrayers(Client client,BooleanSupplier ui,Consumer<Prayer> output){this.client=client;this.ui=ui;this.output=output;}
 static Map<String,String> defaults(){Map<String,String> map=new LinkedHashMap<>();map.put("MAGIC","AUGURY");map.put("MELEE","PIETY");map.put("RANGED","RIGOUR");return map;}
 static Prayer selected(Map<String,String> map,CombatCore.Style style){String name=map.get((style==CombatCore.Style.SPEC?CombatCore.Style.MELEE:style).name());return name==null||name.equals("NONE")?null:Prayer.valueOf(name);}
 static void validate(Map<String,String> map){if(map==null||map.size()>3)throw new IllegalArgumentException("Invalid offensive prayers");for(Map.Entry<String,String> e:map.entrySet()){if(!Arrays.asList("MAGIC","MELEE","RANGED").contains(e.getKey())||e.getValue()==null)throw new IllegalArgumentException("Invalid offensive style");if(e.getValue().equals("NONE"))continue;Prayer prayer=Prayer.valueOf(e.getValue());List<Prayer> allowed=e.getKey().equals("MAGIC")?Arrays.asList(Prayer.AUGURY,Prayer.MYSTIC_MIGHT,Prayer.MYSTIC_LORE,Prayer.MYSTIC_WILL):e.getKey().equals("RANGED")?Arrays.asList(Prayer.RIGOUR,Prayer.EAGLE_EYE,Prayer.HAWK_EYE,Prayer.SHARP_EYE):Arrays.asList(Prayer.PIETY,Prayer.CHIVALRY,Prayer.ULTIMATE_STRENGTH,Prayer.SUPERHUMAN_STRENGTH,Prayer.BURST_OF_STRENGTH);if(!allowed.contains(prayer))throw new IllegalArgumentException("Prayer is not an offensive boost for "+e.getKey());}}
 void reset(){pending=null;uiWaitSince=-1;retryAfter.clear();status="Offensive prayer idle";}
 boolean ready(Map<String,String> map,CombatCore.Style style,boolean defenseRequired){
  if(defenseRequired){status="Defense takes priority over offensive prayer";return false;}
  Prayer desired=selected(map,style);int cycle=client.getGameCycle();
  if(desired==null||client.getBoostedSkillLevel(Skill.PRAYER)<=0)return true;
  if(client.isPrayerActive(desired)){pending=null;uiWaitSince=-1;status="Offensive prayer active: "+desired;return true;}
  if(cycle<retryAfter.getOrDefault(desired,-1)){status="Offensive prayer unavailable/unconfirmed: "+desired+"; DPS continues";return true;}
  if(pending==desired){if(cycle-requestedAt<35){status="Offensive prayer confirmation pending; compatible DPS continues: "+desired;return true;}retryAfter.put(desired,cycle+60);pending=null;status="Offensive prayer not confirmed: "+desired+"; DPS continues";return true;}
  if(uiWaitSince<0)uiWaitSince=cycle;
  if(cycle-uiWaitSince>=35){retryAfter.put(desired,cycle+60);uiWaitSince=-1;status="Offensive prayer interface unavailable: "+desired+"; DPS continues";return true;}
  if(!ui.getAsBoolean()){status="Opening Prayer tab for "+desired;return false;}
  net.runelite.api.widgets.Widget widget=client.getWidget(desired.getWidgetInfo());if(widget==null||!widget.isVisible()){status="Waiting for visible offensive prayer: "+desired;return false;}
  if(verifiedOutput!=null){if(!verifiedOutput.test(desired)){status="Targeting the visible prayer control; waiting for its Activate menu.";return false;}}else output.accept(desired);pending=desired;requestedAt=cycle;uiWaitSince=-1;status="Requested offensive prayer: "+desired;return false;
 }
}
