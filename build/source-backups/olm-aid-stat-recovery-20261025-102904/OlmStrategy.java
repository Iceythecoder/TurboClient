package owl.nh;

import java.util.*;

/** Rule-based baseline. Uncertain mechanics yield recoverable waits, not observer shutdown. */
final class OlmStrategy {
 enum Phase { UNKNOWN, REGULAR, FINAL_HANDS, HEAD }
 enum Kind { WAIT, DRINK, ENTER, EQUIP, ATTACK, SPECIAL }
 static final class View {
  boolean inRaid,alive,focused,entryAvailable,entered,magicAlive,meleeAlive,headAlive;
  int tick,hp=-1,hpPercent,prayerPercent,prayerPoints=-1,magicHp=-1,meleeHp=-1;boolean potionPending,prayerRequired,meleeHealing,overloadExpired,enhanceExpired;
  int eatAtHp=80,restorePrayerAt=40,finalHandPercent=10,finishWindowTicks=50;
  final Set<String> unavailableSupplies=new HashSet<>(); boolean canDrink(String kind){return !unavailableSupplies.contains(kind);}
  Phase phase=Phase.UNKNOWN;String special="";
 }
 static final class Choice {
  final Kind kind;final String value,reason;final boolean preparationCredit;
  Choice(Kind kind,String value,String reason){this(kind,value,reason,false);}
  Choice(Kind kind,String value,String reason,boolean preparationCredit){this.kind=kind;this.value=value;this.reason=reason;this.preparationCredit=preparationCredit;}
 }
 private int preparation,regularTarget,firstHandDown=-1;private boolean meleeParked,magicParked,finishing;private Phase lastPhase=Phase.REGULAR;
 private static final String[] PREP={"overload","enhance","aid","aid","aid","aid","revitalisation"};
 void reset(){preparation=0;regularTarget=0;firstHandDown=-1;meleeParked=magicParked=finishing=false;lastPhase=Phase.REGULAR;}
 Phase lastKnownPhase(){return lastPhase;}
 int preparationStep(){return preparation;}
 boolean withholdMelee(){return meleeParked&&!finishing;}
 void drinkConfirmed(){if(preparation<PREP.length)preparation++;}
 void overloadAlreadyActive(){if(preparation==0)preparation=1;}
 void enhanceAlreadyActive(){if(preparation==1)preparation=2;}
 Choice choose(View s){
  if(!s.inRaid||!s.alive||!s.focused)return waitFor("Waiting for raid, living player and game focus");
  if(s.prayerRequired)return waitFor("Protection prayer has priority over supplies and DPS");
  int hp=s.hp>=0?s.hp:s.hpPercent;
  if(!s.potionPending&&s.entered&&s.canDrink("aid")&&hp<=s.eatAtHp)return new Choice(Kind.DRINK,"aid","Heal at the configured absolute HP threshold",preparation<PREP.length&&PREP[preparation].equals("aid"));
  if(!s.potionPending&&preparation==0&&hp<=50&&s.canDrink("aid"))return new Choice(Kind.DRINK,"aid","Heal before overload's HP cost");
  if(preparation<PREP.length&&s.potionPending)return waitFor("Preparation dose pending; movement and protection continue");
  if(preparation<PREP.length&&(!s.entered||s.canDrink(PREP[preparation])))return new Choice(Kind.DRINK,PREP[preparation],"Preparation "+(preparation+1)+"/7",true);
  if(s.entered&&!s.potionPending){
   if((s.prayerPoints>=0?s.prayerPoints:s.prayerPercent)<=s.restorePrayerAt&&s.canDrink("revitalisation"))return new Choice(Kind.DRINK,"revitalisation","Restore prayer at the configured absolute point threshold");
   if(s.overloadExpired&&!finishing&&s.canDrink("overload")&&(hp>=85||s.canDrink("aid"))){if(hp<85)return new Choice(Kind.DRINK,"aid","Heal before renewing overload");return new Choice(Kind.DRINK,"overload","Renew observed expired overload");}
   if(s.enhanceExpired&&!finishing&&s.canDrink("enhance"))return new Choice(Kind.DRINK,"enhance","Renew observed expired prayer enhance");
  }

  if(!s.entered){if(hp<Math.max(80,s.eatAtHp))return new Choice(Kind.DRINK,"aid","Restore at least 80 HP before entering");return s.entryAvailable?new Choice(Kind.ENTER,"entry","Enter configured Olm room"):waitFor("Waiting for configured entry object");}
  if(s.phase!=Phase.UNKNOWN){if(s.phase!=lastPhase)regularTarget=0;lastPhase=s.phase;if(lastPhase!=Phase.FINAL_HANDS)finishing=false;}
  String uncertainty=s.phase==Phase.UNKNOWN?" (phase uncertain; retaining last strategy)":"";
  if(lastPhase==Phase.HEAD)return s.headAlive?new Choice(Kind.ATTACK,"head","Head phase"+uncertainty):waitFor("Waiting for head / completion confirmation");
  if(s.meleeHealing&&s.meleeAlive){boolean mageLow=s.magicHp>=0?s.magicHp<=s.finalHandPercent:magicParked;if(s.magicAlive&&!mageLow)return new Choice(Kind.ATTACK,"magic","Melee healing: magic gear and mage hand until its stopping threshold");return new Choice(Kind.EQUIP,"magic","Melee healing: mage dead or parked; prepare magic and wait without attacking");}
  if(lastPhase==Phase.FINAL_HANDS){
   if(s.magicAlive&&s.meleeAlive){
    if(firstHandDown>=0){meleeParked=false;magicParked=false;}
    if(s.meleeHp>=0)meleeParked=s.meleeHp<=s.finalHandPercent;
    if(s.magicHp>=0)magicParked=s.magicHp<=s.finalHandPercent;
    if(firstHandDown>=0||s.meleeHp>s.finalHandPercent||s.magicHp>s.finalHandPercent){finishing=false;firstHandDown=-1;}
    if(s.meleeHealing){if(!magicParked)return new Choice(Kind.ATTACK,"magic","Melee healing: observe/reduce mage to its parking threshold");return waitFor("Melee healing active: wait for the effect to fade");}
    if(!meleeParked)return new Choice(Kind.ATTACK,"melee","Reduce melee hand to about ten percent; acquire its bar if unknown"+uncertainty);
    if(!magicParked)return new Choice(Kind.ATTACK,"magic","Melee parked: reduce mage to about ten percent; acquire its bar if unknown"+uncertainty);
    finishing=true;return new Choice(Kind.ATTACK,"melee","Both hands parked: finish melee first, then mage immediately");
   }
   if(s.meleeAlive||s.magicAlive){finishing=true;if(firstHandDown<0)firstHandDown=s.tick;String window="; approximate finish window: "+Math.max(0,s.finishWindowTicks-(s.tick-firstHandDown))+" ticks remaining";if(s.meleeAlive)return s.meleeHealing?waitFor("Wait for melee healing to fade"):new Choice(Kind.ATTACK,"melee","Finish remaining melee immediately"+window);return new Choice(Kind.ATTACK,"magic","Finish remaining mage immediately"+window);}
   return waitFor("Waiting for verified head phase");
  }
  if(s.magicAlive){regularTarget=0;return new Choice(Kind.ATTACK,"magic","Regular phase: mage first"+uncertainty);}
  if(s.meleeAlive){if(s.meleeHealing)return waitFor("Wait for melee healing to fade");if(regularTarget==0){regularTarget=1;return new Choice(Kind.SPECIAL,"melee","Attempt one configured melee special");}return new Choice(Kind.ATTACK,"melee","Regular phase: finish melee"+uncertainty);}
  return waitFor("Waiting for hands to return; observer continues");
 }
 private static Choice waitFor(String reason){return new Choice(Kind.WAIT,"",reason);}
}


