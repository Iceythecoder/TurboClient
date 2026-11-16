package turbo.toa;

import com.google.gson.JsonParser;

/** Pure priority/phase checks; sends no game input. */
public final class ToaCombatPolicyTests {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  check(ToaEntryFlow.positiveOption(java.util.Arrays.asList("No", "Begin the raid"))==2,"entry confirmation must select Begin");
  check(ToaEntryFlow.positiveOption(java.util.Arrays.asList("Leave", "Yes, I am ready"))==2,"entry confirmation must reject Leave");
  ToaEntryFlow entry=new ToaEntryFlow();entry.reset();check(entry.ready(),"entry must start ready");entry.sentBegin(10);
  check(entry.waiting()&&!entry.retryDue(17,true),"entry retried before its acknowledgement window");
  entry.sentDialogue(100,17);check(!entry.dialogueDue(114)&&entry.dialogueDue(115),"entry dialogue must be paced by client cycles");
  entry.confirmActive();check(entry.active(),"only observed combat evidence may confirm entry");
  ToaCombatPolicy p=new ToaCombatPolicy();p.reset();
  check(ToaCombatPolicy.exactPrepotOrder("overload","enhance","aid","aid","aid","aid","revitalisation"),"pre-pot order changed");
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

  prep.hp=80;prep.prayer=5;prep.tick+=4;check("aid".equals(p.decide(prep).value),"HP 80 must guard before prayer 40");prep.hp=81;check("revitalisation".equals(p.decide(prep).value),"prayer 40 guard missing");

  prep.hp=99;prep.prayer=99;prep.phase=ToaCombatPolicy.Phase.CORE;prep.wornStyle=ToaCombatPolicy.Style.SPEC;prep.targetAvailable=true;
  ToaCombatPolicy.Decision core=p.decide(prep);check(core.kind==ToaCombatPolicy.Kind.EQUIP&&core.style==ToaCombatPolicy.Style.MELEE,"core must force Rapier melee, never Elder maul");
  prep.phase=ToaCombatPolicy.Phase.TUMEKEN;prep.siphonAvailable=true;check(p.decide(prep).style==ToaCombatPolicy.Style.MELEE,"energy siphons must use melee");
  String profileJson="{\"combatImplemented\":true,\"eatAtHp\":80,\"restorePrayerAt\":40,\"potionCooldownTicks\":3,"
   +"\"observedGear\":{\"MAGIC\":[1],\"MELEE\":[2],\"RANGED\":[3],\"SPEC\":[4]},"
   +"\"weapons\":{\"rapierId\":2,\"elderMaulId\":4},"
   +"\"supplies\":{\"overload\":[11],\"enhance\":[12],\"aid\":[13],\"revitalisation\":[14]},"
   +"\"roomPosition\":[3200,3200,0]}";
  ToaProfile profile=ToaProfile.parse(new JsonParser().parse(profileJson).getAsJsonObject());
  check(profile.tumeken.contains(11762)&&profile.tumeken.contains(11764)&&!profile.tumeken.contains(11747),"charging Warden ID must not skip P1/P2");
  System.out.println("ToA policy checks passed: slam priority, confirmed pre-pot, staged entry/spec, 80/40 guards, core and siphon melee rules.");
 }
}
