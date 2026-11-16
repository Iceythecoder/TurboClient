package turbo.toa;

import java.util.Arrays;

/**
 * Pure ToA decision policy.  It deliberately knows nothing about mouse input:
 * {@link ToaModule} must translate every decision through RaidInputBridge.
 */
final class ToaCombatPolicy {
 enum Phase { BEGIN, OBELISK, ELIDINIS, CORE, TUMEKEN, COMPLETE, UNKNOWN }
 enum Style { MAGIC, MELEE, RANGED, SPEC }
 enum Kind {
  MOVE_FLOOR_SLAM, MOVE_IMMEDIATE_HAZARD, PROTECTION_PRAYER, OFFENSIVE_PRAYER,
  MOVE_POSITION, DRINK, EQUIP, ACTIVATE_SPECIAL, SKIP_SPECIAL, BEGIN, ATTACK, WAIT, STOP
 }

 static final String[] PREPOT={"overload","aid","aid","aid","aid","revitalisation","enhance"};

 static final class Observation {
  int tick,hp=99,prayer=99;
  Phase phase=Phase.UNKNOWN;
  Style wornStyle;
  Style elidinisStyle=Style.MAGIC;
  boolean floorSlamWarning,floorSafeTileKnown;
  boolean immediateMovementHazard,immediateSafeTileKnown;
  String protectionPrayer;
  boolean protectionPrayerActive;
  String offensivePrayer;
  boolean offensivePrayerActive;
  boolean targetAvailable,beginAvailable,specialAvailable,specialActive,specialAttackPending;
  boolean siphonAvailable;
  boolean combatStatsBelow100;
  boolean atRequiredPosition=true;
  boolean wardenCampKnown,atWardenCamp=true,wardenMoveRequested;
 }

 static final class Decision {
  final Kind kind;final String value;final Style style;
  private Decision(Kind kind,String value,Style style){this.kind=kind;this.value=value;this.style=style;}
  static Decision of(Kind kind,String value){return new Decision(kind,value,null);}
  static Decision style(Kind kind,Style style){return new Decision(kind,null,style);}
  static Decision waitFor(String why){return of(Kind.WAIT,why);}
  public String toString(){return kind+(value==null?"":" "+value)+(style==null?"":" "+style);}
 }

 private int preparationStep,nextPotionTick=-1000,pendingRequestedTick=-1000,pendingBefore=-1;
 private String pendingPotion;
 private boolean obeliskSpecialAttempted;

 void reset(){preparationStep=0;nextPotionTick=-1000;pendingRequestedTick=-1000;pendingBefore=-1;pendingPotion=null;obeliskSpecialAttempted=false;}
 int preparationStep(){return preparationStep;}
 String pendingPotion(){return pendingPotion;}
 boolean preparationComplete(){return preparationStep>=PREPOT.length;}

 /** A potion request is creditable only after exactly one observed dose disappears. */
 void potionRequested(String kind,int doseCount,int tick){
  if(pendingPotion!=null)throw new IllegalStateException("A potion request is already awaiting confirmation");
  pendingPotion=kind;pendingBefore=doseCount;pendingRequestedTick=tick;
 }

 /** Returns true only when a real one-dose inventory transition is observed. */
 boolean observePotion(int currentDoseCount,int tick,int cooldownTicks){
  if(pendingPotion==null)return false;
  if(currentDoseCount==pendingBefore-1){
   if(preparationStep<PREPOT.length&&PREPOT[preparationStep].equals(pendingPotion))preparationStep++;
   pendingPotion=null;pendingBefore=-1;nextPotionTick=tick+Math.max(3,cooldownTicks);return true;
  }
  if(tick-pendingRequestedTick>=5){pendingPotion=null;pendingBefore=-1;nextPotionTick=tick+1;}
  return false;
 }

 void markObeliskSpecialAttempted(){obeliskSpecialAttempted=true;}
 boolean obeliskSpecialAttempted(){return obeliskSpecialAttempted;}

 Decision decide(Observation o){
  // This ordering is contractual.  A slam can pre-empt an unpressed potion,
  // gear or prayer hover and remains first even at critically low resources.
  if(o.floorSlamWarning){
   return o.floorSafeTileKnown?Decision.of(Kind.MOVE_FLOOR_SLAM,"slam-safe"):
    Decision.of(Kind.STOP,"Floor slam detected but no calibrated safe tile is available");
  }
  if(o.immediateMovementHazard){
   return o.immediateSafeTileKnown?Decision.of(Kind.MOVE_IMMEDIATE_HAZARD,"hazard-safe"):
    Decision.of(Kind.STOP,"Immediate movement hazard detected but no calibrated safe tile is available");
  }
  // Protection remains the first combat priority, but an already-confirmed
  // overhead must not monopolise every decision cycle. The old behaviour
  // repeatedly selected the same active overhead and starved gear changes,
  // Augury/Rigour and the pre-core melee switch.
  if(o.protectionPrayer!=null&&!o.protectionPrayer.isEmpty()&&!o.protectionPrayerActive)
   return Decision.of(Kind.PROTECTION_PRAYER,o.protectionPrayer);

  // Guardian zero health starts the short core transition immediately.  Do
  // not spend this first transition slot on routine aid/restore maintenance:
  // the complete Rapier/Avernic preset must already be changing while the
  // core is falling.  Immediate hazards and a missing protection overhead
  // remain the only higher priorities.
  if(o.phase==Phase.CORE&&o.wornStyle!=Style.MELEE)
   return Decision.style(Kind.EQUIP,Style.MELEE);

  if(!preparationComplete()){
   if(pendingPotion!=null)return Decision.waitFor("Waiting for observed "+pendingPotion+" dose decrease");
   if(o.tick<nextPotionTick)return Decision.waitFor("Shared potion cooldown");
   return Decision.of(Kind.DRINK,PREPOT[preparationStep]);
  }
  if(o.hp<=80){
   if(pendingPotion!=null)return Decision.waitFor("Waiting for observed aid dose decrease");
   if(o.tick<nextPotionTick)return Decision.waitFor("Shared potion cooldown");
   return Decision.of(Kind.DRINK,"aid");
  }
  if(o.prayer<=40){
   if(pendingPotion!=null)return Decision.waitFor("Waiting for observed revitalisation dose decrease");
   if(o.tick<nextPotionTick)return Decision.waitFor("Shared potion cooldown");
   return Decision.of(Kind.DRINK,"revitalisation");
  }
  if(o.combatStatsBelow100){
   if(pendingPotion!=null)return Decision.waitFor("Waiting for observed revitalisation dose decrease");
   if(o.tick<nextPotionTick)return Decision.waitFor("Shared potion cooldown");
   return Decision.of(Kind.DRINK,"revitalisation");
  }

  // Make the style boost part of the policy instead of an incidental side
  // effect of EQUIP or ATTACK.  This gives Augury/Rigour/Piety a verified
  // action slot immediately after any required overhead switch.
  if(o.offensivePrayer!=null&&!o.offensivePrayer.isEmpty()&&!o.offensivePrayerActive)
   return Decision.of(Kind.OFFENSIVE_PRAYER,o.offensivePrayer);

  switch(o.phase){
   case BEGIN:
     // Arrive at Osmumten with the maul equipped, but never arm the special
     // before the encounter has actually begun.
     if(o.wornStyle!=Style.SPEC)return Decision.style(Kind.EQUIP,Style.SPEC);
     return o.beginAvailable?Decision.of(Kind.BEGIN,"Osmumten"):Decision.waitFor("Looking for Osmumten Begin");
   case OBELISK:
    if(!o.atRequiredPosition)return Decision.of(Kind.MOVE_POSITION,"obelisk-block");
    if(!obeliskSpecialAttempted){
     if(o.wornStyle!=Style.SPEC)return Decision.style(Kind.EQUIP,Style.SPEC);
     if(!o.specialAvailable)return Decision.of(Kind.SKIP_SPECIAL,"Elder maul special energy is below 50%");
     if(o.specialAttackPending)return Decision.waitFor("Waiting for the Elder maul special to consume energy");
     if(!o.specialActive)return Decision.of(Kind.ACTIVATE_SPECIAL,"Elder maul");
     return o.targetAvailable?Decision.of(Kind.ATTACK,"obelisk-special"):Decision.waitFor("Looking for the obelisk");
    }
    if(o.wornStyle!=Style.MELEE)return Decision.style(Kind.EQUIP,Style.MELEE);
    return o.targetAvailable?Decision.of(Kind.ATTACK,"obelisk-scythe"):Decision.waitFor("Waiting for the obelisk transition");
   case CORE:
    // MELEE resolves to the dedicated Rapier/Avernic preset during the core
    // window. Every other melee target resolves to the charged Scythe preset.
    if(o.wornStyle!=Style.MELEE)return Decision.style(Kind.EQUIP,Style.MELEE);
    return o.targetAvailable?Decision.of(Kind.ATTACK,"warden-core"):Decision.waitFor("Waiting for the core");
   case ELIDINIS:
    // The obelisk is a one-time stage. Guardian exposures repeat around core
    // windows and use the style selected by the observed core transition:
    // opening exposure MAGIC, then RANGED after the first core closes, and so on.
    if(!o.wardenCampKnown)return Decision.waitFor("Resolving the Warden firing tile");
    if(!o.atWardenCamp&&!o.wardenMoveRequested)return Decision.of(Kind.MOVE_POSITION,"warden-camp");
    if(o.wornStyle!=o.elidinisStyle)return Decision.style(Kind.EQUIP,o.elidinisStyle);
    if(!o.atWardenCamp)return Decision.waitFor(o.elidinisStyle+" setup ready; finishing movement to the Warden firing tile");
    return o.targetAvailable?Decision.of(Kind.ATTACK,"elidinis"):Decision.waitFor("Waiting for Elidinis Warden");
   case TUMEKEN:
    if(o.siphonAvailable){
     if(o.wornStyle!=Style.MELEE)return Decision.style(Kind.EQUIP,Style.MELEE);
     return Decision.of(Kind.ATTACK,"energy-siphon");
    }
    if(o.wornStyle!=Style.RANGED)return Decision.style(Kind.EQUIP,Style.RANGED);
    return o.targetAvailable?Decision.of(Kind.ATTACK,"tumeken"):Decision.waitFor("Waiting for Tumeken Warden");
   case COMPLETE:return Decision.of(Kind.STOP,"ToA encounter complete");
   default:return Decision.waitFor("No calibrated ToA phase target is visible");
  }
 }

 static boolean exactPrepotOrder(String... supplied){return Arrays.equals(PREPOT,supplied);}
}
