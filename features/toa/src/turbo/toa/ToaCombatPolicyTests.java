package turbo.toa;

import com.google.gson.JsonParser;
import java.util.Arrays;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/** Pure priority/phase checks; sends no game input. */
public final class ToaCombatPolicyTests {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  check(ToaEntryFlow.positiveOption(java.util.Arrays.asList("No", "Begin the raid"))==2,"entry confirmation must select Begin");
  WorldPoint rightLane=ToaObeliskLane.rightStreamTile(new WorldArea(10,10,3,3,0),Arrays.asList(new WorldArea(1,8,3,3,0),new WorldArea(20,10,3,3,0)));
  check(rightLane.equals(new WorldPoint(13,11,0)),"obelisk movement must choose the demonstrated tile between its east edge and the right charging Warden");
  WorldPoint camp=ToaWardenCamp.choose(new WorldPoint(10,10,0),new WorldPoint(20,5,0),10);
  check(camp.equals(new WorldPoint(17,7,0))&&camp.distanceTo(new WorldPoint(10,10,0))<=10,"Warden camp must be stable, on the approach side and inside ranged attack distance");
  WorldPoint yellowFiringTile=ToaWardenCamp.choose(new WorldPoint(23,10,0),new WorldPoint(13,10,0),10);
  check(yellowFiringTile.equals(new WorldPoint(16,10,0)),"right guardian firing tile must be resolved from the confirmed right-stream block tile");
  check(!yellowFiringTile.equals(new WorldPoint(13,10,0)),"guardian firing tile must remain distinct from the obelisk blocking tile");
  check(ToaEntryFlow.positiveOption(java.util.Arrays.asList("Leave", "Yes, I am ready"))==2,"entry confirmation must reject Leave");
  ToaEntryFlow entry=new ToaEntryFlow();entry.reset();check(entry.ready(),"entry must start ready");entry.sentBegin(10);
  check(entry.waiting()&&!entry.retryDue(17,true),"entry retried before its acknowledgement window");
  entry.sentDialogue(100,17);check(!entry.dialogueDue(114)&&entry.dialogueDue(115),"entry dialogue must be paced by client cycles");
  entry.confirmActive();check(entry.active(),"only observed combat evidence may confirm entry");
  ToaEntryFlow menuEntry=new ToaEntryFlow();menuEntry.reset();check(menuEntry.menuRequestDue(100),"first Osmumten right-click must be available");menuEntry.menuRequested(100);check(menuEntry.awaitingMenu(101)&&!menuEntry.menuRequestDue(129),"right-click acknowledgement window must suppress duplicate clicks");check(menuEntry.menuRequestDue(130),"an ignored right-click may retry only after its bounded menu wait");menuEntry.menuObserved();check(menuEntry.menuRequestDue(131),"an observed menu must clear the right-click latch");
  ToaCombatPolicy p=new ToaCombatPolicy();p.reset();
  check(ToaCombatPolicy.exactPrepotOrder("overload","aid","aid","aid","aid","revitalisation","enhance"),"pre-pot order changed");
  ToaCombatPolicy.Observation danger=new ToaCombatPolicy.Observation();danger.tick=1;danger.hp=1;danger.prayer=0;danger.floorSlamWarning=true;danger.floorSafeTileKnown=true;danger.phase=ToaCombatPolicy.Phase.TUMEKEN;
  check(p.decide(danger).kind==ToaCombatPolicy.Kind.MOVE_FLOOR_SLAM,"floor slam must outrank critical HP, prayer, gear and DPS");
  danger.floorSafeTileKnown=false;check(p.decide(danger).kind==ToaCombatPolicy.Kind.STOP,"unknown slam solution must fail closed");

  ToaCombatPolicy.Observation prep=new ToaCombatPolicy.Observation();prep.tick=10;prep.phase=ToaCombatPolicy.Phase.BEGIN;
  for(int i=0;i<ToaCombatPolicy.PREPOT.length;i++){
   prep.tick+=4;ToaCombatPolicy.Decision d=p.decide(prep);check(d.kind==ToaCombatPolicy.Kind.DRINK&&d.value.equals(ToaCombatPolicy.PREPOT[i]),"wrong pre-pot step "+i);
   p.potionRequested(d.value,20-i,prep.tick);prep.tick++;check(p.decide(prep).kind==ToaCombatPolicy.Kind.WAIT,"unconfirmed click advanced pre-pot");
   check(p.observePotion(19-i,prep.tick,3),"one-dose decrease was not confirmed");
  }
  check(p.preparationComplete(),"pre-pot did not finish after seven confirmed sips");

  prep.hp=99;prep.prayer=99;prep.phase=ToaCombatPolicy.Phase.BEGIN;prep.beginAvailable=true;prep.wornStyle=ToaCombatPolicy.Style.MELEE;
  ToaCombatPolicy.Decision entryEquip=p.decide(prep);
  check(entryEquip.kind==ToaCombatPolicy.Kind.EQUIP&&entryEquip.style==ToaCombatPolicy.Style.SPEC,"entry must equip Elder maul before Begin");
  prep.wornStyle=ToaCombatPolicy.Style.SPEC;
  check(p.decide(prep).kind==ToaCombatPolicy.Kind.BEGIN,"entry must Begin without selecting special attack");

  prep.phase=ToaCombatPolicy.Phase.OBELISK;prep.atRequiredPosition=false;prep.targetAvailable=true;
  check(p.decide(prep).kind==ToaCombatPolicy.Kind.MOVE_POSITION,"obelisk tile must be reached before selecting special");
  prep.atRequiredPosition=true;prep.specialAvailable=true;prep.specialActive=false;
  check(p.decide(prep).kind==ToaCombatPolicy.Kind.ACTIVATE_SPECIAL,"special must arm only after encounter start and tile confirmation");
  prep.specialActive=true;prep.specialAttackPending=true;
  check(p.decide(prep).kind==ToaCombatPolicy.Kind.WAIT,"Rapier switch must wait for observed special-energy use");
  prep.specialAttackPending=false;
  p.markObeliskSpecialAttempted();prep.wornStyle=ToaCombatPolicy.Style.SPEC;
  ToaCombatPolicy.Decision rapierSwitch=p.decide(prep);
  check(rapierSwitch.kind==ToaCombatPolicy.Kind.EQUIP&&rapierSwitch.style==ToaCombatPolicy.Style.MELEE,"confirmed maul special must switch to Rapier/Avernic melee");
  prep.wornStyle=ToaCombatPolicy.Style.MELEE;
  check("obelisk-scythe".equals(p.decide(prep).value),"normal obelisk DPS must wait for the complete charged-Scythe preset");

  prep.phase=ToaCombatPolicy.Phase.ELIDINIS;prep.wardenCampKnown=true;prep.atWardenCamp=false;prep.wardenMoveRequested=false;
  ToaCombatPolicy.Decision campMove=p.decide(prep);check(campMove.kind==ToaCombatPolicy.Kind.MOVE_POSITION&&"warden-camp".equals(campMove.value),"post-obelisk transition must send the Warden camp destination first");
  prep.wardenMoveRequested=true;
  prep.elidinisStyle=ToaCombatPolicy.Style.MAGIC;
  ToaCombatPolicy.Decision openingMagic=p.decide(prep);check(openingMagic.kind==ToaCombatPolicy.Kind.EQUIP&&openingMagic.style==ToaCombatPolicy.Style.MAGIC,"opening Guardian exposure must switch full magic while travelling");
  prep.wornStyle=ToaCombatPolicy.Style.MAGIC;check(p.decide(prep).kind==ToaCombatPolicy.Kind.WAIT,"Guardian attack must wait for observed camp arrival");
  prep.atWardenCamp=true;prep.targetAvailable=true;check("elidinis".equals(p.decide(prep).value),"magic Guardian attack starts only after camp and setup confirmation");
  prep.offensivePrayer="AUGURY";prep.offensivePrayerActive=false;
  check(p.decide(prep).kind==ToaCombatPolicy.Kind.OFFENSIVE_PRAYER,"opening magic Guardian exposure must explicitly request Augury");
  prep.offensivePrayerActive=true;
  prep.elidinisStyle=ToaCombatPolicy.Style.RANGED;check(p.decide(prep).style==ToaCombatPolicy.Style.RANGED,"post-core Guardian exposure must advance to ranged");
  prep.wornStyle=ToaCombatPolicy.Style.RANGED;prep.offensivePrayer="RIGOUR";prep.offensivePrayerActive=false;
  check(p.decide(prep).kind==ToaCombatPolicy.Kind.OFFENSIVE_PRAYER,"post-core ranged Guardian exposure must explicitly request Rigour");
  prep.offensivePrayer=null;prep.offensivePrayerActive=false;

  prep.protectionPrayer="MAGIC";prep.protectionPrayerActive=false;
  check(p.decide(prep).kind==ToaCombatPolicy.Kind.PROTECTION_PRAYER,"an unconfirmed Guardian overhead must retain first priority");
  prep.protectionPrayerActive=true;prep.wornStyle=ToaCombatPolicy.Style.MELEE;prep.elidinisStyle=ToaCombatPolicy.Style.MAGIC;
  ToaCombatPolicy.Decision confirmedOverhead=p.decide(prep);
  check(confirmedOverhead.kind==ToaCombatPolicy.Kind.EQUIP&&confirmedOverhead.style==ToaCombatPolicy.Style.MAGIC,
   "an active overhead must not starve the Guardian gear and Augury path");
  prep.protectionPrayer=null;prep.protectionPrayerActive=false;prep.wornStyle=ToaCombatPolicy.Style.MAGIC;

  prep.hp=80;prep.prayer=5;prep.tick+=4;check("aid".equals(p.decide(prep).value),"HP 80 must guard before prayer 40");prep.hp=81;check("revitalisation".equals(p.decide(prep).value),"prayer 40 guard missing");
  prep.prayer=99;prep.combatStatsBelow100=true;prep.tick+=4;check("revitalisation".equals(p.decide(prep).value),"94-stat revitalisation recovery missing");prep.combatStatsBelow100=false;

  prep.hp=99;prep.prayer=99;prep.phase=ToaCombatPolicy.Phase.CORE;prep.wornStyle=ToaCombatPolicy.Style.SPEC;prep.targetAvailable=true;prep.offensivePrayer="PIETY";prep.offensivePrayerActive=false;
  check(ToaModule.guardianCollapsed(0)&&!ToaModule.guardianCollapsed(-1)&&!ToaModule.guardianCollapsed(1),
   "core preparation must start only on an observed zero-health Guardian");
  ToaCombatPolicy.Decision core=p.decide(prep);check(core.kind==ToaCombatPolicy.Kind.EQUIP&&core.style==ToaCombatPolicy.Style.MELEE,"core must force Rapier melee, never Elder maul");
  prep.hp=50;prep.prayer=20;prep.combatStatsBelow100=true;
  core=p.decide(prep);check(core.kind==ToaCombatPolicy.Kind.EQUIP&&core.style==ToaCombatPolicy.Style.MELEE,"Guardian zero-health gear transition must precede routine aid and restore maintenance");
  prep.hp=99;prep.prayer=99;prep.combatStatsBelow100=false;
  check(ToaGearBatch.prioritize(java.util.Arrays.asList(10,11,12,13),12).equals(java.util.Arrays.asList(12,10,11,13)),"Holy Cards must be submitted in the first ranged batch");
  check(ToaGearBatch.prioritize(java.util.Arrays.asList(10,11,12,13),13,11).equals(java.util.Arrays.asList(13,11,10,12)),"Rapier and Avernic must lead the core batch");
  prep.offensivePrayer=null;prep.offensivePrayerActive=false;
  prep.phase=ToaCombatPolicy.Phase.TUMEKEN;prep.siphonAvailable=true;check(p.decide(prep).style==ToaCombatPolicy.Style.MELEE,"energy siphons must use melee");
  String profileJson="{\"combatImplemented\":true,\"eatAtHp\":80,\"restorePrayerAt\":40,\"potionCooldownTicks\":3,"
   +"\"observedGear\":{\"MAGIC\":[1,5,6],\"MELEE\":[2,5,6],\"CORE\":[7,8,5,6],\"RANGED\":[3,5,6],\"SPEC\":[4,5,6]},"
   +"\"weapons\":{\"meleeWeaponId\":2,\"rapierId\":7,\"avernicDefenderId\":8,\"elderMaulId\":4,\"rangedWeaponId\":3,\"bootsId\":5,\"blessingId\":6},"
   +"\"supplies\":{\"overload\":[11],\"enhance\":[12],\"aid\":[13],\"revitalisation\":[14]},"
   +"\"roomPosition\":[3200,3200,0]}";
  ToaProfile profile=ToaProfile.parse(new JsonParser().parse(profileJson).getAsJsonObject());
  check(profile.rangedWeaponId==3&&profile.style(ToaCombatPolicy.Style.RANGED).contains(3),"Holy Cards must be the ToA ranged weapon");
  check(profile.coreGear.contains(7)&&profile.coreGear.contains(8)&&!profile.coreGear.contains(2),"core must use Rapier/Avernic without the Scythe");
  check(profile.tumeken.contains(11762)&&profile.tumeken.contains(11764)&&!profile.tumeken.contains(11747),"charging Warden ID must not skip P1/P2");
  check("MELEE".equals(profile.protectionProjectiles.get(2204))&&"RANGED".equals(profile.protectionProjectiles.get(2206))&&"MAGIC".equals(profile.protectionProjectiles.get(2208))&&"MAGIC".equals(profile.protectionProjectiles.get(2224))&&"RANGED".equals(profile.protectionProjectiles.get(2241)),"verified Warden prayer projectiles missing");
  check(ToaSignals.spinningLight(2198)&&!ToaSignals.bombProjectile(2224)&&ToaSignals.bombProjectile(2225),"red magic skull must not be consumed as a movement hazard");
  ToaGearBatch batch=new ToaGearBatch();batch.reset();java.util.List<Integer> eight=java.util.Arrays.asList(1,2,3,4,5,6,7,8);java.util.Set<Integer> worn=new java.util.HashSet<>(),pending=new java.util.HashSet<>();batch.plan(ToaCombatPolicy.Style.RANGED,eight,worn,pending);for(int i=1;i<=4;i++){int id=ToaGearBatch.next(eight,worn,pending);check(id==i&&batch.allowed(10,(i-1)*120,600),"first gear tick lost request "+i);pending.add(id);batch.sent(10,(i-1)*120,600);}check(!batch.allowed(10,500,600),"gear batch exceeded four requests in one tick");for(int id:pending)worn.add(id);pending.clear();batch.plan(ToaCombatPolicy.Style.RANGED,eight,worn,pending);for(int i=5;i<=8;i++){int id=ToaGearBatch.next(eight,worn,pending);check(id==i&&batch.allowed(11,(i-5)*120,600),"second gear tick lost request "+i);pending.add(id);batch.sent(11,(i-5)*120,600);}
  ToaStateMachine machine=new ToaStateMachine();ToaStateMachine.Evidence evidence=new ToaStateMachine.Evidence();
  check(machine.update(evidence)==ToaStateMachine.State.PREPARING,"state machine must begin with verified preparation");
  evidence.preparationComplete=true;evidence.entryRequested=true;check(machine.update(evidence)==ToaStateMachine.State.ENTERING_RAID,"entry request state missing");
  evidence.encounterActive=true;evidence.obelisk=true;evidence.obeliskSpecial=true;check(machine.update(evidence)==ToaStateMachine.State.OBELISK_SPECIAL,"obelisk special state missing");
  evidence.obelisk=false;evidence.obeliskSpecial=false;evidence.warden=true;check(machine.update(evidence)==ToaStateMachine.State.WARDEN_ACTIVATION,"Warden activation state missing");
  check(machine.update(evidence)==ToaStateMachine.State.WARDEN_COMBAT,"stable Warden combat state missing");
  evidence.warden=false;evidence.core=true;check(machine.update(evidence)==ToaStateMachine.State.CORE_EXPOSED,"core exposure state missing");
  check(machine.update(evidence)==ToaStateMachine.State.CORE_DPS,"core DPS state missing");
  evidence.core=false;evidence.finalWarden=true;check(machine.update(evidence)==ToaStateMachine.State.FINAL_WARDEN,"final Warden state missing");
  System.out.println("ToA policy checks passed: slam priority, confirmed pre-pot, staged entry/spec, 80/40 guards, core and siphon melee rules.");
 }
}
