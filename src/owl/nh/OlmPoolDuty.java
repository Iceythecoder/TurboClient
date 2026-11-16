package owl.nh;
import java.util.*;

/** Counts observed occupancy, never requested movement or unobserved ticks. */
final class OlmPoolDuty {
 private boolean active;private int start=-1,lastTick=-1,insideTicks,lastInside,windows,met,missed;
 private OlmSpecials.Cell selected;
 private final Set<OlmSpecials.Cell> locations=new HashSet<>();
 void reset(){active=false;start=lastTick=-1;insideTicks=lastInside=windows=met=missed=0;selected=null;locations.clear();}
 void cancel(){active=false;selected=null;locations.clear();}
 void observe(int tick,OlmSpecials.Cell player,Set<OlmSpecials.Cell> visible){
  if(visible.isEmpty()&&(!active||tick-start>=10)){if(active){lastInside=insideTicks;windows++;if(insideTicks>=8)met++;else missed++;}active=false;selected=null;locations.clear();return;}
  if(!active){active=true;start=tick;lastTick=-1;insideTicks=0;}
  if(!visible.isEmpty()){locations.clear();locations.addAll(visible);}
  if(tick!=lastTick){if(tick>=start&&tick-start<10&&locations.contains(player))insideTicks++;lastTick=tick;}
  if(selected==null||!locations.contains(selected))selected=locations.contains(player)?player:locations.stream().min(Comparator.comparingInt(player::distance).thenComparingInt(c->c.x).thenComparingInt(c->c.y)).orElse(null);
 }
 boolean active(){return active;}
 Set<OlmSpecials.Cell> locations(){return new HashSet<>(locations);}
 OlmSpecials.Cell selected(){return selected;}
 void select(OlmSpecials.Cell cell){selected=cell;}
 int occupied(){return insideTicks;}
 boolean canArrive(int tick,int travelTicks){return !active||travelTicks==0||tick+travelTicks<start+10&&insideTicks+Math.max(0,start+10-tick-travelTicks)>=8;}
 Map<String,Object> diagnostics(int tick){Map<String,Object> out=new LinkedHashMap<>();out.put("active",active);out.put("windowStartTick",start);out.put("elapsedObservedTicks",active?Math.max(0,tick-start):0);out.put("occupiedTicks",insideTicks);out.put("requiredTicks",8);out.put("occupancyMeaning","Observed position samples on identified pool tiles during the user-defined 10-tick window; missing tick samples are never filled.");out.put("windowTicks",10);out.put("minimumStillPossible",!active||insideTicks+Math.max(0,10-(tick-start+1))>=8);out.put("completedWindows",windows);out.put("windowsMeetingMinimum",met);out.put("windowsBelowMinimum",missed);out.put("lastWindowOccupiedTicks",lastInside);return out;}
}
