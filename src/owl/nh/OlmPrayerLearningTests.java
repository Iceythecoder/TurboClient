package owl.nh;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;

/** Synthetic evidence and controller regressions, never connected to a game. */
public final class OlmPrayerLearningTests {
 static void check(boolean b,String message){CoreTests.check(b,"Olm prayer learning: "+message);}
 public static void main(String[] args){
  OlmPrayerLearning l=new OlmPrayerLearning();
  OlmPrayerLearning.Flight f=l.flight(1,90,100,120,Prayer.PROTECT_FROM_MAGIC,99,1,2,3);
  l.requested(f,101);l.acknowledged(4);l.observe(f,105,true);l.observe(f,119,true);l.finish(f,120,false);
  check(l.checkpoint().timely==1&&l.margin()==6,"measured acknowledgement adjusts safety margin and near-impact state is recorded");
  f=l.flight(1,120,125,140,Prayer.PROTECT_FROM_MAGIC,99,0,0,0);l.observe(f,130,true);l.observe(f,139,false);l.finish(f,140,false);
  check(l.checkpoint().late==1&&l.margin()==8,"prayer disabled before impact invalidates earlier activation and increases margin");
  f=l.flight(1,140,145,160,Prayer.PROTECT_FROM_MAGIC,99,0,0,0);l.observe(f,150,true);l.finish(f,170,false);
  check(l.checkpoint().unobserved==1,"missed observation window is never scored successful");
  f=l.flight(1,160,165,180,Prayer.PROTECT_FROM_MAGIC,99,0,0,0);l.observe(f,180,true);l.finish(f,180,false);
  check(l.checkpoint().unobserved==2,"first activation at estimated impact is not credited as timely");
  f=l.flight(99,180,185,200,null,99,0,0,0);l.finish(f,200,false);check(l.checkpoint().unknown==1,"unknown classification is retained without guessing a prayer");
  l.damage(20);check(l.status().contains("cause unassigned")&&l.status().contains("UNVERIFIED"),"damage correlation does not become proof of protection");
  OlmPrayerLearning restored=new OlmPrayerLearning();restored.restore(l.checkpoint());check(restored.margin()==l.margin()&&restored.checkpoint().late==1,"timing checkpoint restores independently");
  OlmPrayerLearning.Checkpoint bad=l.checkpoint();bad.marginCycles=100;boolean rejected=false;try{restored.restore(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid safety margin rejected");
  for(int i=0;i<150;i++){f=l.flight(1,0,1,3,Prayer.PROTECT_FROM_MAGIC,99,0,0,0);l.finish(f,10,false);}check(l.outcomes().size()==128,"outcomes stay bounded");
  restorationAndDeadline();
  System.out.println("Olm prayer learning checks passed: measured timing, disabled prayer, unknown impacts, bounded records, checkpoint validation, imminent threat preemption and immediate restoration. No live inputs.");
 }
 private static void restorationAndDeadline(){
  Map<String,Object> actor=new HashMap<>();actor.put("getLocalLocation",new net.runelite.api.coords.LocalPoint(128,128));actor.put("getWorldLocation",new net.runelite.api.coords.WorldPoint(3201,3201,0));Player self=ChambersTests.fake(Player.class,actor);
  Map<String,Object> fields=new HashMap<>();fields.put("getGameState",GameState.LOGGED_IN);fields.put("getLocalPlayer",self);fields.put("getNpcs",Collections.emptyList());fields.put("getBaseX",3200);fields.put("getBaseY",3200);fields.put("getBoostedSkillLevel",99);fields.put("getCanvas",new java.awt.Canvas(){@Override public boolean isFocusOwner(){return true;}});fields.put("getWidget",ChambersTests.fake(Widget.class,Collections.singletonMap("isVisible",true)));fields.put("getGameCycle",100);
  Client client=ChambersTests.fake(Client.class,fields);List<Prayer> output=new ArrayList<>();ChambersController c=new ChambersController(client,output::add);c.configure(ChambersController.parse("1","2","10=MAGIC\n11=RANGED",5));c.start();c.tolerant=true;c.trustedContext=true;c.arm();
  Map<String,Object> p=new HashMap<>();p.put("getId",10);p.put("getInteracting",self);p.put("getEndCycle",200);c.projectile(ChambersTests.fake(Projectile.class,p));c.pulse(true);
  check(!c.protectionRequired(),"submitted defense with ample flight time releases routine work instead of globally waiting for acknowledgement");
  fields.put("getGameCycle",198);check(c.protectionRequired(),"unconfirmed protection reserves the action path near estimated impact");
  fields.put("getGameCycle",101);fields.put("isPrayerActive",true);c.pulse(true);fields.put("getGameCycle",102);fields.put("isPrayerActive",false);c.pulse(true);
  check(output.size()==2,"confirmed prayer can be restored immediately after disabling, without old 35-cycle retry lock");
  p=new HashMap<>(p);p.put("getId",11);p.put("getEndCycle",130);fields.put("getGameCycle",103);c.projectile(ChambersTests.fake(Projectile.class,p));c.pulse(true);check(output.size()==3&&output.get(2)==Prayer.PROTECT_FROM_MISSILES,"earlier incompatible projectile preempts pending request on next client cycle");
  c.close();check(c.learning.checkpoint().tracked==0,"close clears retained evidence");
 }
}
