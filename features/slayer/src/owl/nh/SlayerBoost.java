package owl.nh;

/** Confirmation bookkeeping for the reusable Slayer combat flask. */
final class SlayerBoost {
 int threshold=108,retryTicks=3;
 private int requestedTick=-1000,beforeLevel=-1,failures,lastAction=-1000;
 private boolean pending;

 static boolean needs(int attack,int strength,int defence,int threshold){
  return Math.min(attack,Math.min(strength,defence))<=threshold;
 }
 static int lowest(int attack,int strength,int defence){
  return Math.min(attack,Math.min(strength,defence));
 }
 void reset(){pending=false;requestedTick=-1000;beforeLevel=-1;failures=0;lastAction=-1000;}
 void requested(int tick,int level){pending=true;requestedTick=tick;lastAction=tick;beforeLevel=level;}
 boolean waiting(int tick,int level){
  if(!pending)return tick-lastAction<retryTicks;
  if(level>beforeLevel){pending=false;failures=0;return tick-lastAction<retryTicks;}
  if(tick-requestedTick<retryTicks)return true;
  pending=false;failures++;return false;
 }
 boolean failed(){return failures>=3;}
 void defer(int tick){pending=false;failures=0;lastAction=tick;}
}
