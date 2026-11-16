package owl.nh;
import java.time.Instant;
import java.util.*;
import net.runelite.client.ui.overlay.infobox.*;

/** Observed client timers, not guessed server potion durations. No renewal from an unknown state. */
final class OlmBuffTracker {
 private final Map<String,Instant> ends=new HashMap<>(),consumed=new HashMap<>();private final Map<String,Integer> suppressUntil=new HashMap<>();
 private boolean overloadSeen;private int overloadValue;
 void clear(){ends.clear();consumed.clear();suppressUntil.clear();overloadSeen=false;overloadValue=0;}
 void observeOverload(int value){overloadValue=value;if(value>0)overloadSeen=true;}
 void timer(String name,Instant end,Instant now){String kind="OVERLOAD_RAID".equals(name)?"overload":"PRAYER_ENHANCE".equals(name)?"enhance":null;if(kind==null||end==null||!end.isAfter(consumed.getOrDefault(kind,Instant.MIN)))return;ends.put(kind,end);}
 void observe(InfoBoxManager manager,int value,Instant now){observeOverload(value);if(manager==null)return;for(InfoBox box:new ArrayList<>(manager.getInfoBoxes()))if(box instanceof net.runelite.client.ui.overlay.infobox.Timer)timer(box.getName(),((net.runelite.client.ui.overlay.infobox.Timer)box).getEndTime(),now);}
 boolean active(String kind,Instant now){if(kind.equals("overload")&&overloadValue>0)return true;Instant end=ends.get(kind);return end!=null&&end.isAfter(now);}
 boolean expired(String kind,int tick,Instant now){if(tick<suppressUntil.getOrDefault(kind,-1)||active(kind,now))return false;return kind.equals("overload")&&overloadSeen&&overloadValue==0||ends.containsKey(kind)&&!ends.get(kind).isAfter(now);}
 void consumed(String kind,int tick,Instant now){if(!kind.equals("overload")&&!kind.equals("enhance"))return;ends.remove(kind);consumed.put(kind,now);suppressUntil.put(kind,tick+3);if(kind.equals("overload")){overloadSeen=false;overloadValue=0;}}
 String status(int tick,Instant now){return "Buff evidence: overload "+state("overload",tick,now)+", prayer enhance "+state("enhance",tick,now)+". Unknown timers do not trigger repeat sips.";}
 private String state(String kind,int tick,Instant now){return active(kind,now)?"active":expired(kind,tick,now)?"expired":tick<suppressUntil.getOrDefault(kind,-1)?"awaiting renewed timer":"unknown";}
}
