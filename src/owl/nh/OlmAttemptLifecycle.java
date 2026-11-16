package owl.nh;

/** Counts lack of observed progress, never repeated requested clicks as success. */
final class OlmAttemptLifecycle {
 private int changedTick,lastTick=-1;private String signature;private boolean active;
 void start(int tick){active=true;changedTick=tick;lastTick=-1;signature=null;}
 boolean active(){return active;}
 boolean end(){boolean was=active;active=false;return was;}
 boolean observe(int tick,boolean actionable,String next){
  if(!active)return false;
  if(!actionable||lastTick>=0&&tick<lastTick){changedTick=tick;signature=next;}
  if(!next.equals(signature)){signature=next;changedTick=tick;}
  lastTick=tick;return actionable&&tick-changedTick>=100;
 }
}
