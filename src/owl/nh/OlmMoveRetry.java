package owl.nh;
import java.util.*;
/** Short-lived retry exclusions describe ignored requests, never proven collision. */
final class OlmMoveRetry {
 private final Map<OlmSpecials.Cell,Integer> excluded=new LinkedHashMap<>();
 private OlmSpecials.Cell from,to;private int progressTick;
 void reset(){excluded.clear();from=to=null;}
 void requested(OlmSpecials.Cell origin,OlmSpecials.Cell destination,int tick){from=origin;to=destination;progressTick=tick;}
 Set<OlmSpecials.Cell> observe(OlmSpecials.Cell current,int tick){
  excluded.entrySet().removeIf(e->tick>=e.getValue());
  if(to!=null){if(current.equals(to)){from=to=null;}else if(!current.equals(from)){from=current;progressTick=tick;}else if(tick-progressTick>=2){if(excluded.size()>=32)excluded.remove(excluded.keySet().iterator().next());excluded.put(to,tick+6);from=to=null;}}
  return new HashSet<>(excluded.keySet());
 }
}
