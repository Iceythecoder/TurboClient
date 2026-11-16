package owl.nh;
import java.util.*;
/** Scheduling decisions only. Does not invent attacks, positions or cooldowns. */
final class OlmHeadCombat {
 static final class ShotWindow {
  private OlmSpecials.Cell anchor;private int requestedTick=-1000;
  void requested(OlmSpecials.Cell tile,int tick){anchor=tile;requestedTick=tick;}
  boolean holds(OlmSpecials.Cell tile,int tick,boolean requestPending){return requestPending&&anchor!=null&&anchor.equals(tile)&&tick>=requestedTick&&tick-requestedTick<2;}
  void clear(){anchor=null;requestedTick=-1000;}
 }
 static boolean mayCancelPoolRoute(int tick,int lastRequest){return tick>=lastRequest&&tick-lastRequest>=2;}
 static boolean mayShoot(boolean head,boolean safeWindow,boolean movementPending,int distance,int range,boolean lineOfSight){return head&&safeWindow&&!movementPending&&distance<=range&&lineOfSight;}
 static int poolRisk(OlmSpecials.Cell pool,OlmSpecials.View view){int risk=0;for(OlmSpecials.Hazard h:view.hazards)if(h.contains(pool))risk+=h.kind==OlmSpecials.HazardKind.FALLING?100:1000;if(view.walls.contains(pool)||view.uncertainElectric.contains(pool))risk+=1000;return risk;}
 static OlmSpecials.Cell choosePool(Map<OlmSpecials.Cell,Integer> steps,OlmSpecials.View view,OlmPoolDuty duty,boolean run){
  boolean feasible=steps.entrySet().stream().anyMatch(e->duty.canArrive(view.tick,(e.getValue()+(run?1:0))/(run?2:1)));
  OlmSpecials.Cell best=null;int bestRisk=Integer.MAX_VALUE,bestSteps=Integer.MAX_VALUE;
  for(Map.Entry<OlmSpecials.Cell,Integer> e:steps.entrySet()){int ticks=(e.getValue()+(run?1:0))/(run?2:1);if(feasible&&!duty.canArrive(view.tick,ticks))continue;int risk=poolRisk(e.getKey(),view);boolean better=best==null||feasible&&(risk<bestRisk||risk==bestRisk&&e.getValue()<bestSteps)||!feasible&&(e.getValue()<bestSteps||e.getValue()==bestSteps&&risk<bestRisk);if(better){best=e.getKey();bestRisk=risk;bestSteps=e.getValue();}}
  return best;
 }
}

