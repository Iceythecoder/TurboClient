package owl.nh;

/** Server-tick cooldown starts at a configured attack animation, never at an input request. */
final class OlmAttackTiming {
 private int requested=-1000,confirmed=-1000,speed=4;private boolean pending,known;
 void reset(){requested=confirmed=-1000;pending=known=false;speed=4;}
 void requested(int tick){requested=tick;pending=true;}
 void movementInterrupted(){pending=false;}
 void observed(int tick,int actualSpeed){if(actualSpeed<1||actualSpeed>20)return;confirmed=tick;speed=actualSpeed;known=true;pending=false;}
 boolean pending(int tick){if(pending&&tick-requested>=2)pending=false;return pending;}
 boolean ready(int tick){return !pending(tick)&&(!known||tick-confirmed>=speed);}
 boolean walkingAllowed(int tick){return known&&!pending(tick)&&tick>confirmed&&tick-confirmed<speed;}
 boolean retry(int tick){return !pending(tick)&&tick-requested>=2;}
 String status(int tick){return "Attack clock: "+(pending(tick)?"request pending; no attack confirmed":known?"observed attack T"+confirmed+", next ready T"+(confirmed+speed):"unverified: configure attack animations; optional between-attack walking suspended")+". Server tick "+tick+".";}
}
