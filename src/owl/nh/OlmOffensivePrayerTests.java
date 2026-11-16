package owl.nh;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;

public final class OlmOffensivePrayerTests {
 static void check(boolean b,String message){CoreTests.check(b,"Olm offensive prayers: "+message);}
 public static void main(String[] args){
  Map<String,String> config=OlmOffensivePrayers.defaults();OlmOffensivePrayers.validate(config);
  check(OlmOffensivePrayers.selected(config,CombatCore.Style.MAGIC)==Prayer.AUGURY,"mage hand Augury");check(OlmOffensivePrayers.selected(config,CombatCore.Style.RANGED)==Prayer.RIGOUR,"head Rigour");check(OlmOffensivePrayers.selected(config,CombatCore.Style.SPEC)==Prayer.PIETY,"melee special Piety");
  Map<String,Object> fields=new HashMap<>();fields.put("getGameCycle",100);fields.put("getBoostedSkillLevel",99);fields.put("getWidget",ChambersTests.fake(Widget.class,Collections.singletonMap("isVisible",true)));Client client=ChambersTests.fake(Client.class,fields);
  List<Prayer> output=new ArrayList<>();boolean[] visible={true};OlmOffensivePrayers p=new OlmOffensivePrayers(client,()->visible[0],output::add);
  check(!p.ready(config,CombatCore.Style.MAGIC,true)&&output.isEmpty(),"defensive requirement owns input before offensive boosts");
  visible[0]=false;check(!p.ready(config,CombatCore.Style.MAGIC,false)&&output.isEmpty(),"closed tab must open first");visible[0]=true;fields.put("getGameCycle",101);check(!p.ready(config,CombatCore.Style.MAGIC,false)&&output.equals(Collections.singletonList(Prayer.AUGURY)),"request visible boost");
  fields.put("getGameCycle",102);check(p.ready(config,CombatCore.Style.MAGIC,false)&&output.size()==1,"pending boost permits compatible DPS without repeated toggles");fields.put("isPrayerActive",true);check(p.ready(config,CombatCore.Style.MAGIC,false)&&output.size()==1,"observed active boost permits attack without toggling off");
  fields.put("isPrayerActive",false);fields.put("getGameCycle",103);p.ready(config,CombatCore.Style.RANGED,false);fields.put("getGameCycle",138);check(p.ready(config,CombatCore.Style.RANGED,false)&&p.status.contains("not confirmed"),"unavailable or locked boost cannot permanently stall DPS");fields.put("getGameCycle",139);check(p.ready(config,CombatCore.Style.RANGED,false)&&output.size()==2,"unavailable prayer cooldown prevents repeated requests");
  p.reset();fields.put("getGameCycle",200);fields.put("getWidget",null);p.ready(config,CombatCore.Style.MELEE,false);fields.put("getGameCycle",235);check(p.ready(config,CombatCore.Style.MELEE,false),"missing widget eventually releases DPS");
  config.put("MAGIC","PROTECT_FROM_MAGIC");boolean rejected=false;try{OlmOffensivePrayers.validate(config);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"offensive config cannot include an overhead that would replace protection");
  System.out.println("Olm offensive prayer checks passed: style mapping, visible tab, defense preemption, acknowledgement, unavailable recovery. Synthetic only.");
 }
}
