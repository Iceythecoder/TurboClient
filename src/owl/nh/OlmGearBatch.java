package owl.nh;
import java.util.*;

/** Inventory transaction: four item requests per actual game tick, then Prayer/verification. */
final class OlmGearBatch {
 enum Stage { INVENTORY, NEXT_TICK, PRAYER_AND_VERIFY, READY }
 private CombatCore.Style style;private List<Integer> required=Collections.emptyList();
 private int firstTick=-1,quotaTick=-1,sentThisTick,total,pacedTick=-1;private long nextSlot;private Stage stage=Stage.READY;
 void reset(){style=null;required=Collections.emptyList();firstTick=quotaTick=pacedTick=-1;sentThisTick=total=0;nextSlot=0;stage=Stage.READY;}
 void plan(CombatCore.Style wanted,List<Integer> items,Set<Integer> worn,Set<Integer> pending){
  if(style!=wanted||!required.equals(items)){firstTick=-1;total=0;stage=Stage.READY;style=wanted;required=new ArrayList<>(items);}
  if(worn.containsAll(required))stage=Stage.READY;
  else if(OlmGearSwitch.next(required,worn,pending)<0)stage=Stage.PRAYER_AND_VERIFY;
  else if(stage==Stage.READY||stage==Stage.PRAYER_AND_VERIFY)stage=Stage.INVENTORY;
 }
 boolean allowed(int tick,long remainingMs){
  if(firstTick<0&&remainingMs<150){stage=Stage.NEXT_TICK;return false;}
  if(quotaTick!=tick){quotaTick=tick;sentThisTick=0;}
  if(sentThisTick>=4){stage=Stage.NEXT_TICK;return false;}
  stage=Stage.INVENTORY;return true;
 }
 void sent(int tick){if(firstTick<0)firstTick=tick;if(quotaTick!=tick){quotaTick=tick;sentThisTick=0;}sentThisTick++;total++;}
 boolean allowed(int tick,long elapsedMs,long periodMs){
  long spacing=spacing(periodMs);if(elapsedMs<0||periodMs<200)return false;
  if(firstTick<0&&periodMs-elapsedMs<3*spacing+60){stage=Stage.NEXT_TICK;return false;}
  if(!allowed(tick,Math.max(0,periodMs-elapsedMs)))return false;
  if(pacedTick!=tick){pacedTick=tick;nextSlot=0;}
  if(periodMs-elapsedMs<40){stage=Stage.NEXT_TICK;return false;}
  return elapsedMs>=nextSlot;
 }
 void sent(int tick,long elapsedMs,long periodMs){sent(tick);pacedTick=tick;nextSlot=elapsedMs+spacing(periodMs);}
 private static long spacing(long periodMs){return Math.max(80,Math.min(150,periodMs/5));}
 boolean prayerPhase(){return stage==Stage.PRAYER_AND_VERIFY||stage==Stage.READY;}
 Stage stage(){return stage;}
 String status(int tick){return "Gear sequence "+style+": "+stage+", "+sentThisTick+"/4 requests this game tick, "+total+" submitted"+(firstTick<0?"; waiting for a usable tick window":(" over "+(tick-firstTick+1)+" observed ticks"))+(firstTick>=0&&tick-firstTick>=2&&stage!=Stage.READY?"; two-tick target exceeded, recovery continues":"")+".";}
}
