package turbo.gauntlet;

import java.util.*;
import net.runelite.api.coords.WorldPoint;

/** Run-local graph and short-horizon hazard planner. No fixed world map or guessed coordinates. */
final class GauntletPlanner {
 enum FloorState { SAFE, WARNING, ACTIVE, UNKNOWN }
 static final class TimedHazards {
  final Map<WorldPoint,FloorState> floor=new HashMap<>(); final Map<WorldPoint,Integer> activatesAt=new HashMap<>();
  final Set<WorldPoint> blocked=new HashSet<>(),tornadoNow=new HashSet<>(),tornadoPrevious=new HashSet<>();
  int startTick,horizon=6;
  boolean unsafe(WorldPoint p,int tick){
   if(p==null||blocked.contains(p))return true;
   FloorState f=floor.get(p);if(f==FloorState.ACTIVE)return true;
   Integer active=activatesAt.get(p);if(active!=null&&tick>=active)return true;
   if(tornadoNow.contains(p))return true;
   for(WorldPoint t:tornadoNow)if(t.distanceTo(p)<=Math.max(0,tick-startTick))return true;
   return false;
  }
 }
 static final class TimedStep {final WorldPoint tile;final int tick;TimedStep(WorldPoint tile,int tick){this.tile=tile;this.tick=tick;}}
 static final class Room {final String key;final Set<String> neighbours=new LinkedHashSet<>();boolean visited;Room(String key){this.key=key;}}
 private final Map<String,Room> rooms=new LinkedHashMap<>();
 Room observe(String key){return rooms.computeIfAbsent(key,Room::new);}
 void connect(String a,String b){observe(a).neighbours.add(b);observe(b).neighbours.add(a);}
 List<String> path(String from,String to){if(from==null||to==null||!rooms.containsKey(from)||!rooms.containsKey(to))return Collections.emptyList();Map<String,String> prev=new HashMap<>();ArrayDeque<String> q=new ArrayDeque<>();q.add(from);prev.put(from,null);while(!q.isEmpty()){String at=q.remove();if(at.equals(to))break;for(String n:rooms.get(at).neighbours)if(!prev.containsKey(n)){prev.put(n,at);q.add(n);}}if(!prev.containsKey(to))return Collections.emptyList();LinkedList<String> result=new LinkedList<>();for(String at=to;at!=null;at=prev.get(at))result.addFirst(at);return result;}
 WorldPoint safestAdjacent(WorldPoint player,Set<WorldPoint> blocked,Set<WorldPoint> danger){if(player==null)return null;WorldPoint best=null;int score=Integer.MIN_VALUE;for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){if(dx==0&&dy==0)continue;WorldPoint p=new WorldPoint(player.getX()+dx,player.getY()+dy,player.getPlane());if(blocked.contains(p)||danger.contains(p))continue;int s=0;for(WorldPoint d:danger)s+=Math.min(6,p.distanceTo(d));if(s>score){score=s;best=p;}}return best;}
 List<TimedStep> safePath(WorldPoint start,WorldPoint goal,TimedHazards h){
  if(start==null||goal==null||h==null||h.unsafe(start,h.startTick))return Collections.emptyList();
  final class Node {final WorldPoint p;final int tick;final Node prev;Node(WorldPoint p,int tick,Node prev){this.p=p;this.tick=tick;this.prev=prev;}}
  ArrayDeque<Node> q=new ArrayDeque<>();Set<String> seen=new HashSet<>();Node first=new Node(start,h.startTick,null);q.add(first);seen.add(key(start,h.startTick));Node found=null;
  while(!q.isEmpty()){Node n=q.remove();if(n.p.equals(goal)){found=n;break;}if(n.tick-h.startTick>=h.horizon)continue;
   for(int[] d:new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}}){WorldPoint p=new WorldPoint(n.p.getX()+d[0],n.p.getY()+d[1],n.p.getPlane());int tick=n.tick+1;String key=key(p,tick);if(seen.add(key)&&!h.unsafe(p,tick))q.add(new Node(p,tick,n));}
  }
  if(found==null)return Collections.emptyList();LinkedList<TimedStep> result=new LinkedList<>();for(Node n=found;n!=null;n=n.prev)result.addFirst(new TimedStep(n.p,n.tick));return result;
 }
 private static String key(WorldPoint p,int tick){return p.getX()+":"+p.getY()+":"+p.getPlane()+":"+tick;}
}
