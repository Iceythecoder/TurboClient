package owl.nh;
import java.util.*;
/** Bounded connection/focus transitions only; no usernames, login messages or secrets. */
final class OlmConnectionTrace {
 private final ArrayDeque<Map<String,Object>> events=new ArrayDeque<>();private long origin;private String state;private Boolean focused,fresh;
 void reset(){events.clear();origin=System.nanoTime();state=null;focused=fresh=null;}
 void observe(String next,int tick,int cycle,boolean focus,boolean ticksFresh,long uiSlots){if(Objects.equals(state,next)&&Objects.equals(focused,focus)&&Objects.equals(fresh,ticksFresh))return;if(origin==0)origin=System.nanoTime();Map<String,Object> e=new LinkedHashMap<>();e.put("state",next);e.put("tick",tick);e.put("cycle",cycle);e.put("elapsedMs",(System.nanoTime()-origin)/1000000L);e.put("focused",focus);e.put("ticksFresh",ticksFresh);e.put("uiActionSlotsClaimed",uiSlots);events.add(e);while(events.size()>64)events.removeFirst();state=next;focused=focus;fresh=ticksFresh;}
 List<Map<String,Object>> snapshot(){return new ArrayList<>(events);}
}
