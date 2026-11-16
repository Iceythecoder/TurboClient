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
  MOVE_FLOOR_SLAM, MOVE_IMMEDIATE_HAZARD, PROTECTION_PRAYER,
  MOVE_POSITION, DRINK, EQUIP, ACTIVATE_SPECIAL, SKIP_SPECIAL, BEGIN, ATTACK, WAIT, STOP
 }

 static final String[] PREPOT={"overload","enhance","aid","aid","aid","aid","revitalisation"};

 static final class Observation {
  int tick,hp=99,prayer=99;
  Phase phase=Phase.UNKNOWN;
  Style wornStyle;
  Style elidinisStyle=Style.MAGIC;
  boolean floorSlamWarning,floorSafeTileKnown;
  boolean immediateMovementHazard,immediateSafeTileKnown;
  String protectionPrayer;
  boolean targetAvailable,beginAvailable,specialAvailable,specialActive;
  boolean siphonAvailable;
  boolean atRequiredPosition=true;
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
  if(o.protectionPrayer!=null&&!o.protectionPrayer.isEmpty())return Decision.of(Kind.PROTECTION_PRAYER,o.protectionPrayer);

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

  switch(o.phase){
   case BEGIN:
    return o.beginAvailable?Decision.of(Kind.BEGIN,"Osmumten"):Decision.waitFor("Looking for Osmumten Begin");
   case OBELISK:
    if(!o.atRequiredPosition)return Decision.of(Kind.MOVE_POSITION,"obelisk-block");
    if(!obeliskSpecialAttempted){
     if(o.wornStyle!=Style.SPEC)return Decision.style(Kind.EQUIP,Style.SPEC);
     if(!o.specialAvailable)return Decision.of(Kind.SKIP_SPECIAL,"Elder maul special energy is below 50%");
     if(!o.specialActive)return Decision.of(Kind.ACTIVATE_SPECIAL,"Elder maul");
     return o.targetAvailable?Decision.of(Kind.ATTACK,"obelisk-special"):Decision.waitFor("Looking for the obelisk");
    }
    if(o.wornStyle!=Style.MELEE)return Decision.style(Kind.EQUIP,Style.MELEE);
    return o.targetAvailable?Decision.of(Kind.ATTACK,"obelisk-rapier"):Decision.waitFor("Waiting for the obelisk transition");
   case CORE:
    // The core is always Rapier melee.  Elder maul is intentionally excluded.
    if(o.wornStyle!=Style.MELEE)return Decision.style(Kind.EQUIP,Style.MELEE);
    return o.targetAvailable?Decision.of(Kind.ATTACK,"warden-core"):Decision.waitFor("Waiting for the core");
   case ELIDINIS:
    Style vulnerable=o.elidinisStyle==Style.RANGED?Style.RANGED:Style.MAGIC;
    if(o.wornStyle!=vulnerable)return Decision.style(Kind.EQUIP,vulnerable);
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
