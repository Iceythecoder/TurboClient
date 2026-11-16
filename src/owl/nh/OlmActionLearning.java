package owl.nh;

import java.util.*;

/** Learns bounded command acknowledgement timing, not damage causality or fight rewards. */
final class OlmActionLearning {
 private static final class Timing {int count,latency;}
 private final Map<String,Timing> timings=new HashMap<>();
 private int confirmed,timeouts;
 synchronized void clear(){timings.clear();confirmed=0;timeouts=0;}
 synchronized void confirmed(String action,int elapsed){
  if(elapsed<0||elapsed>12)return;
  Timing t=timings.get(action);if(t==null){if(timings.size()>=32)return;t=new Timing();timings.put(action,t);}
  int sample=Math.max(1,elapsed);t.latency=t.count==0?sample:(t.latency*3+sample+3)/4;t.count=Math.min(100000,t.count+1);confirmed++;
 }
 synchronized int waitTicks(String action,int initial){Timing t=timings.get(action);return t==null||t.count<2?initial:Math.max(2,Math.min(8,t.latency+2));}
 synchronized void timeout(){timeouts++;}
 static final class Checkpoint {Map<String,int[]> timings=new HashMap<>();int confirmed,timeouts;}
 synchronized Checkpoint checkpoint(){Checkpoint c=new Checkpoint();c.confirmed=confirmed;c.timeouts=timeouts;timings.forEach((key,t)->c.timings.put(key,new int[]{t.count,t.latency}));return c;}
 synchronized void restore(Checkpoint c){if(c==null||c.timings==null||c.timings.size()>32||c.confirmed<0||c.timeouts<0)throw new IllegalArgumentException("Invalid timing checkpoint");Map<String,Timing> next=new HashMap<>();for(Map.Entry<String,int[]> e:c.timings.entrySet()){int[] a=e.getValue();if(!e.getKey().matches("gear|drink:(overload|enhance|aid|revitalisation)")||a==null||a.length!=2||a[0]<0||a[0]>100000||a[1]<0||a[1]>12)throw new IllegalArgumentException("Invalid learned timing");Timing t=new Timing();t.count=a[0];t.latency=a[1];next.put(e.getKey(),t);}timings.clear();timings.putAll(next);confirmed=c.confirmed;timeouts=c.timeouts;}
 synchronized String status(){return "Action timing learning: "+confirmed+" confirmed gear/potion changes, "+timeouts+" unconfirmed requests. Saved only in confirmed presets; not evidence of damage avoided.";}
}
