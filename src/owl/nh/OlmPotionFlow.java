package owl.nh;

/** Confirmed sips and tick-aligned request spacing; failed clicks never advance preparation. */
final class OlmPotionFlow {
 enum Result { NONE, CONFIRMED, TIMED_OUT }
 private String pending,lastKind;private int before,requestedAt,nextAllowed=-1000;private boolean credit,lastCredit;
 void reset(int tick){pending=null;if(nextAllowed>tick+30)nextAllowed=tick;}
 boolean pending(){return pending!=null;}String kind(){return pending;}
 boolean ready(int tick){return pending==null&&tick>=nextAllowed;}
 int remaining(int tick){return Math.max(0,nextAllowed-tick);}
 void requested(String kind,int doses,int tick,boolean advancesPreparation){if(!ready(tick))throw new IllegalStateException("Potion not ready");pending=kind;before=doses;requestedAt=tick;credit=advancesPreparation;}
 Result observe(int doses,int tick,int cooldown){
  if(pending==null)return Result.NONE;
  if(doses==before-1){lastKind=pending;lastCredit=credit;pending=null;
   // The observed consumption tick is the only available server-state boundary.
   // Request phase and inventory acknowledgement can differ; subtracting a tick
   // can issue the next sip before the actual potion cooldown has expired.
   nextAllowed=tick+cooldown;return Result.CONFIRMED;}
  if(tick-requestedAt>=5){lastKind=pending;lastCredit=false;pending=null;nextAllowed=tick+1;return Result.TIMED_OUT;}
  return Result.NONE;
 }
 String confirmedKind(){return lastKind;}boolean preparationCredit(){return lastCredit;}int latency(int tick){return tick-requestedAt;}
 void cancel(){pending=null;}
}
