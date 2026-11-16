package owl.nh;

import java.awt.Canvas;
import java.lang.reflect.Proxy;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.widgets.Widget;

/** Synthetic API fixtures only; the prayer output is replaced by an in-memory collector. */
public final class ChambersTests {
 @SuppressWarnings("unchecked") static <T>T fake(Class<T> type,Map<String,Object> fields){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
  if(fields.containsKey(m.getName()))return fields.get(m.getName());
  Class<?> t=m.getReturnType();if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==double.class)return 0d;return null;
 });}
 static void check(boolean value,String message){CoreTests.check(value,"Chambers: "+message);}
 public static void main(String[] args){
  ChambersController.Config config=ChambersController.parse("12850","9001","7001=MAGIC\n7002=RANGED",5);
  check(ChambersController.raidValid(true,12850,Collections.singleton(9001),config),"region and NPC validate together");
  check(!ChambersController.raidValid(true,12850,Collections.singleton(9002),config),"region alone insufficient");
  check(!ChambersController.raidValid(true,12851,Collections.singleton(9001),config),"NPC alone insufficient");
  check(!ChambersController.raidValid(false,12850,Collections.singleton(9001),config),"dead player rejected");
  boolean rejected=false;try{ChambersController.parse("1","2","3=FIRE",5);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid style rejected");
  rejected=false;try{ChambersController.parse("1","2","3=MAGIC\n3=RANGED",5);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"ambiguous mapping rejected");
  ChambersController.Threat late=new ChambersController.Threat(7001,120,Prayer.PROTECT_FROM_MAGIC),early=new ChambersController.Threat(7002,110,Prayer.PROTECT_FROM_MISSILES);
  check(ChambersController.earliest(Arrays.asList(late,early),100)==early,"earliest impact wins");
  check(ChambersController.earliest(Arrays.asList(late,early),120)==null,"expired hits excluded");
  check(ChambersController.conflict(Arrays.asList(early,new ChambersController.Threat(7001,110,Prayer.PROTECT_FROM_MAGIC)),early),"simultaneous conflicting threats detected");
  Map<String,Object> actor=new HashMap<>();actor.put("getLocalLocation",new LocalPoint(128,128));actor.put("getWorldLocation",new WorldPoint(3201,3201,0));Player self=fake(Player.class,actor);
  Map<String,Object> npcFields=new HashMap<>();npcFields.put("getId",9001);npcFields.put("getWorldLocation",new WorldPoint(3202,3202,0));NPC npc=fake(NPC.class,npcFields);
  final boolean[] focus={true};Canvas canvas=new Canvas(){@Override public boolean isFocusOwner(){return focus[0];}};
  Map<String,Object> fields=new HashMap<>();fields.put("getGameState",GameState.LOGGED_IN);fields.put("getLocalPlayer",self);fields.put("getNpcs",Collections.singletonList(npc));fields.put("getBaseX",3200);fields.put("getBaseY",3200);fields.put("getGameCycle",100);fields.put("getBoostedSkillLevel",99);fields.put("getCanvas",canvas);fields.put("getWidget",fake(Widget.class,Collections.emptyMap()));
  Client client=fake(Client.class,fields);java.util.List<Prayer> output=new ArrayList<>();ChambersController controller=new ChambersController(client,output::add);
  Map<String,Object> projectile=new HashMap<>();projectile.put("getId",7001);projectile.put("getEndCycle",120);projectile.put("getInteracting",self);
  controller.configure(config);controller.start();controller.projectile(fake(Projectile.class,projectile));controller.pulse(true);check(output.isEmpty(),"observation never outputs");
  controller.arm();controller.projectile(fake(Projectile.class,projectile));controller.pulse(true);check(output.equals(Collections.singletonList(Prayer.PROTECT_FROM_MAGIC)),"mapped targeted projectile requests prayer");
  controller.pulse(true);check(output.size()==1,"pending prayer does not toggle repeatedly");
  fields.put("getGameCycle",105);controller.pulse(true);check(!controller.armed,"unconfirmed output stops");
  fields.put("getGameCycle",100);controller.arm();projectile.put("getId",7999);controller.projectile(fake(Projectile.class,projectile));controller.pulse(true);check(!controller.armed&&output.size()==1,"unknown projectile stops without input");
  projectile.put("getId",7001);projectile.put("getInteracting",fake(Player.class,actor));controller.arm();controller.projectile(fake(Projectile.class,projectile));controller.pulse(true);check(output.size()==1,"other player's projectile excluded");
  projectile.put("getInteracting",self);controller.projectile(fake(Projectile.class,projectile));controller.pulse(false);check(!controller.armed&&output.size()==1,"stale tick blocks output");
  controller.arm();fields.put("isPrayerActive",true);controller.projectile(fake(Projectile.class,projectile));controller.pulse(true);check(output.size()==1,"already-active prayer not toggled");
  focus[0]=false;controller.pulse(true);check(!controller.armed,"focus loss stops after focus was acquired");focus[0]=true;
  controller.arm();fields.put("getNpcs",Collections.emptyList());controller.pulse(true);check(!controller.armed,"leaving encounter disarms");
  controller.close();check(!controller.enabled&&!controller.armed&&controller.history.isEmpty(),"closing clears monitoring and in-memory events");
  // Live Olm tolerates unknown specials, keeps threats seen before arming, and waits for server acknowledgement.
  fields.put("getNpcs",Collections.singletonList(npc));fields.put("getGameCycle",200);fields.put("isPrayerActive",false);fields.put("getWidget",fake(Widget.class,Collections.singletonMap("isVisible",true)));
  final boolean[] uiReady={false};java.util.List<Prayer> visibleOutputs=new ArrayList<>();ChambersController tolerant=new ChambersController(client,visibleOutputs::add,()->uiReady[0]);
  tolerant.configure(config);tolerant.start();tolerant.tolerant=true;projectile.put("getId",7001);projectile.put("getEndCycle",260);tolerant.projectile(fake(Projectile.class,projectile));tolerant.arm();fields.put("getWidget",null);tolerant.pulse(true);check(tolerant.armed&&visibleOutputs.isEmpty(),"hidden tab defers prayer without losing the threat");
  fields.put("getGameCycle",201);fields.put("getWidget",fake(Widget.class,Collections.singletonMap("isVisible",true)));uiReady[0]=true;tolerant.pulse(true);check(visibleOutputs.equals(Collections.singletonList(Prayer.PROTECT_FROM_MAGIC)),"pre-arm projectile survives and sends when tab is ready");
  fields.put("getGameCycle",206);tolerant.pulse(true);check(tolerant.armed&&visibleOutputs.size()==1,"tolerant mode does not resend an unacknowledged toggle after five cycles");
  projectile.put("getId",7002);projectile.put("getEndCycle",240);tolerant.projectile(fake(Projectile.class,projectile));tolerant.pulse(true);check(visibleOutputs.size()==2&&visibleOutputs.get(1)==Prayer.PROTECT_FROM_MISSILES,"new earlier threat can supersede pending protection");tolerant.close();
  fields.put("getNpcs",Collections.singletonList(npc));boolean captureRejected=false;try{controller.teachCurrentLocation();}catch(IllegalStateException e){captureRejected=true;}check(captureRejected,"noninstance cannot teach Chambers");
  fields.put("isInInstancedRegion",true);fields.put("getInstanceTemplateChunks",new int[4][13][13]);
  controller.teachCurrentLocation();check(controller.taughtLocationMatches(),"manually confirmed template region matches");
  fields.put("isInInstancedRegion",false);check(!controller.taughtLocationMatches(),"normal world cannot match taught instance");
  fields.put("isInInstancedRegion",true);controller.forgetLocations();check(!controller.taughtLocationMatches(),"forget removes location confirmation");
  System.out.println("Chambers checks passed: raid guards, mapping, impact priority, target filtering, prayer output collector, confirmation, stale ticks and shutdown. No live inputs issued.");
 }
}
