package turbo.toa;

import java.util.*;

/** Same four-requests-per-game-tick transaction used by the Chambers fighter. */
final class ToaGearBatch {
 enum Stage { INVENTORY, NEXT_TICK, PRAYER_AND_VERIFY, READY }
 private ToaCombatPolicy.Style style;
 private List<Integer> required=Collections.emptyList();
 private int firstTick=-1,quotaTick=-1,sentThisTick,total,pacedTick=-1;
 private long nextSlot;
 private Stage stage=Stage.READY;

 void reset(){style=null;required=Collections.emptyList();firstTick=quotaTick=pacedTick=-1;sentThisTick=total=0;nextSlot=0;stage=Stage.READY;}
 void plan(ToaCombatPolicy.Style wanted,List<Integer> items,Set<Integer> worn,Set<Integer> pending){
  if(style!=wanted||!required.equals(items)){firstTick=-1;total=0;stage=Stage.READY;style=wanted;required=new ArrayList<>(items);}
  if(worn.containsAll(required))stage=Stage.READY;
  else if(next(required,worn,pending)<0)stage=Stage.PRAYER_AND_VERIFY;
  else if(stage==Stage.READY||stage==Stage.PRAYER_AND_VERIFY)stage=Stage.INVENTORY;
 }
 static int next(List<Integer> required,Set<Integer> worn,Set<Integer> pending){for(int id:required)if(!worn.contains(id)&&!pending.contains(id))return id;return -1;}
 static List<Integer> prioritize(List<Integer> required,int... first){
  List<Integer> ordered=new ArrayList<>();
  if(first!=null)for(int id:first)if(id>0&&required.contains(id)&&!ordered.contains(id))ordered.add(id);
  for(int id:required)if(!ordered.contains(id))ordered.add(id);
  return ordered;
 }
 boolean allowed(int tick,long elapsedMs,long periodMs){
  long spacing=spacing(periodMs);if(elapsedMs<0||periodMs<200)return false;
  if(quotaTick!=tick){quotaTick=tick;sentThisTick=0;}
  if(firstTick<0&&periodMs-elapsedMs<3*spacing+60){stage=Stage.NEXT_TICK;return false;}
  if(sentThisTick>=4||periodMs-elapsedMs<40){stage=Stage.NEXT_TICK;return false;}
  if(pacedTick!=tick){pacedTick=tick;nextSlot=0;}
  stage=Stage.INVENTORY;return elapsedMs>=nextSlot;
 }
 void sent(int tick,long elapsedMs,long periodMs){if(firstTick<0)firstTick=tick;if(quotaTick!=tick){quotaTick=tick;sentThisTick=0;}sentThisTick++;total++;pacedTick=tick;nextSlot=elapsedMs+spacing(periodMs);}
 Stage stage(){return stage;}
 boolean prayerPhase(){return stage==Stage.PRAYER_AND_VERIFY||stage==Stage.READY;}
 String status(int tick){return "Gear sequence "+style+": "+stage+", "+sentThisTick+"/4 requests this game tick, "+total+" submitted"+(firstTick<0?"; waiting for a usable tick window":" over "+(tick-firstTick+1)+" observed ticks")+".";}
 private static long spacing(long periodMs){return Math.max(80,Math.min(150,periodMs/5));}
}
