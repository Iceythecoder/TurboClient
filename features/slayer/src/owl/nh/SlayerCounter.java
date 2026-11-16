package owl.nh;
import java.util.*;
/** ActorDeath counts only after our attack was observed, once per NPC lifetime. */
final class SlayerCounter {
 volatile int type=-1,goal,kills;volatile boolean running;
 private final Set<Object> engaged=Collections.newSetFromMap(new IdentityHashMap<>()),counted=Collections.newSetFromMap(new IdentityHashMap<>()),contested=Collections.newSetFromMap(new IdentityHashMap<>());
 void start(int type,int goal){if(type<0||goal<1||goal>10000)throw new IllegalArgumentException("Choose an NPC and 1-10000 kills");this.type=type;this.goal=goal;kills=0;running=true;engaged.clear();counted.clear();contested.clear();}
 void attack(Object actor,int type){if(running&&type==this.type)engaged.add(actor);}
 void contested(Object actor){if(running&&engaged.contains(actor))contested.add(actor);}
 boolean engaged(Object actor){return running&&actor!=null&&engaged.contains(actor);}
 boolean died(Object actor,int type){if(!running||this.type!=type||!engaged.remove(actor)||!counted.add(actor))return false;if(counted.size()>256){counted.clear();counted.add(actor);}if(contested.remove(actor))return false;kills++;if(kills>=goal)running=false;return true;}
 void spawned(Object actor){engaged.remove(actor);counted.remove(actor);contested.remove(actor);}
 void stop(){running=false;engaged.clear();contested.clear();}
}

