package owl.nh;
import java.util.*;
import java.util.function.*;

/** Bounded local path planning and click suppression for ordinary hand-to-hand travel. */
final class OlmTravel {
 private OlmSpecials.Cell destination,lastPosition;private int progressTick;
 private String goal;private int zooms,lastZoom=-1000;
 void reset(){destination=null;lastPosition=null;goal=null;zooms=0;lastZoom=-1000;}
 void goal(String next){if(!Objects.equals(goal,next)){reset();goal=next;}}
 boolean walking(OlmSpecials.Cell position,int tick){if(destination==null)return false;if(destination.equals(position)){destination=null;return false;}if(!position.equals(lastPosition)){lastPosition=position;progressTick=tick;}if(tick-progressTick>=4){destination=null;return false;}return true;}
 void sent(OlmSpecials.Cell from,OlmSpecials.Cell to,int tick){destination=to;lastPosition=from;progressTick=tick;}
 boolean mayZoom(int cycle){return zooms<4&&cycle-lastZoom>=8;}
 void zoomed(int cycle){zooms++;lastZoom=cycle;}
 boolean waitingForZoom(int cycle){return zooms>0&&cycle-lastZoom<8;}
 boolean zoomExhausted(){return zooms>=4;}
 OlmSpecials.Cell destination(){return destination;}
 static List<OlmSpecials.Cell> path(OlmSpecials.Cell from,Predicate<OlmSpecials.Cell> arrived,BiPredicate<OlmSpecials.Cell,OlmSpecials.Cell> canStep){
  Map<OlmSpecials.Cell,OlmSpecials.Cell> parent=new HashMap<>();ArrayDeque<OlmSpecials.Cell> queue=new ArrayDeque<>();parent.put(from,null);queue.add(from);
  while(!queue.isEmpty()&&parent.size()<=2500){OlmSpecials.Cell c=queue.removeFirst();if(arrived.test(c)){List<OlmSpecials.Cell> path=new ArrayList<>();for(OlmSpecials.Cell p=c;p!=null;p=parent.get(p))path.add(p);Collections.reverse(path);return path;}
   for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){if(dx==0&&dy==0)continue;OlmSpecials.Cell next=new OlmSpecials.Cell(c.x+dx,c.y+dy,c.plane);if(from.distance(next)>24||parent.containsKey(next)||!canStep.test(c,next))continue;parent.put(next,c);queue.addLast(next);}
  }return Collections.emptyList();
 }
}
