package owl.nh;
import java.util.*;

/** Reserve observed wave trajectories, including independent opposing lanes. */
final class OlmElectricForecast {
 private static final class Track {OlmSpecials.Cell cell;int cycle,changed,dx,dy;double period;Track(OlmSpecials.Cell p,int c){cell=p;cycle=changed=c;}}
 private static final class Motion {int dx,dy,cycle;double period;Motion(int x,int y,int c,double p){dx=x;dy=y;cycle=c;period=p;}}
 private final IdentityHashMap<Object,Track> tracks=new IdentityHashMap<>();
 private Set<OlmSpecials.Cell> previous=new HashSet<>();private Map<OlmSpecials.Cell,Motion> motions=new HashMap<>();
 private final Map<OlmSpecials.Cell,Integer> born=new HashMap<>();
 void clear(){tracks.clear();previous.clear();motions.clear();born.clear();}
 OlmSpecials.Cell crossingTarget(OlmSpecials.Cell player,int cycle){for(OlmSpecials.Cell p:previous){Motion m=motions.get(p);if(m==null||m.dx!=0&&m.dy!=0||cycle-m.cycle>6||player.distance(p)!=2)continue;int dx=player.x-p.x,dy=player.y-p.y;if(dx*m.dy==dy*m.dx&&dx*m.dx+dy*m.dy>0)return p;}return null;}
 void observe(Object identity,OlmSpecials.Cell cell,int cycle){Track t=tracks.get(identity);if(t==null){if(tracks.size()<256)tracks.put(identity,new Track(cell,cycle));return;}t.cycle=cycle;if(t.cell.equals(cell))return;int dx=cell.x-t.cell.x,dy=cell.y-t.cell.y;if(t.cell.plane==cell.plane&&cycle-t.changed<=60&&Math.max(Math.abs(dx),Math.abs(dy))<=4){t.dx=dx;t.dy=dy;double sample=(cycle-t.changed)/(double)Math.max(Math.abs(dx),Math.abs(dy));t.period=sample>0?(t.period>0?Math.min(t.period,sample):sample):0;}else t.dx=t.dy=0;t.cell=cell;t.changed=cycle;}
 private static OlmSpecials.Cell uniqueNearest(OlmSpecials.Cell from,Set<OlmSpecials.Cell> candidates){OlmSpecials.Cell nearest=null;int distance=4;boolean tie=false;for(OlmSpecials.Cell c:candidates){int d=from.distance(c);if(c.plane!=from.plane||d==0||d>3||c.x!=from.x&&c.y!=from.y)continue;if(d<distance){nearest=c;distance=d;tie=false;}else if(d==distance)tie=true;}return tie?null:nearest;}
 private Motion laneMotion(OlmSpecials.Cell cell,Set<OlmSpecials.Cell> current,int cycle){Motion chosen=null;int nearest=4;for(OlmSpecials.Cell other:current){Motion motion=motions.get(other);if(motion==null||cycle-motion.cycle>60||!(motion.dx==0&&other.x==cell.x||motion.dy==0&&other.y==cell.y)||cell.distance(other)>3)continue;if(chosen!=null&&Integer.signum(chosen.dx)!=Integer.signum(motion.dx)||chosen!=null&&Integer.signum(chosen.dy)!=Integer.signum(motion.dy))return null;int distance=cell.distance(other);if(distance<nearest){chosen=motion;nearest=distance;}}return chosen;}
 void forecast(OlmSpecials.View view,int cycle){
  Set<OlmSpecials.Cell> current=new HashSet<>();int radius=0;for(OlmSpecials.Hazard h:view.hazards)if(h.kind==OlmSpecials.HazardKind.ELECTRIC){current.add(h.cell);radius=Math.max(radius,h.radius);}if(current.isEmpty()){clear();return;}
  if(!current.equals(previous)){
   Set<OlmSpecials.Cell> lost=new HashSet<>(previous);lost.removeAll(current);Set<OlmSpecials.Cell> added=new HashSet<>(current);added.removeAll(previous);Map<OlmSpecials.Cell,Motion> next=new HashMap<>();
   for(OlmSpecials.Cell c:current){if(motions.containsKey(c))next.put(c,motions.get(c));if(!added.contains(c))continue;OlmSpecials.Cell old=uniqueNearest(c,lost);if(old==null)old=uniqueNearest(c,previous);if(old!=null&&c.equals(uniqueNearest(old,added)))next.put(c,new Motion(c.x-old.x,c.y-old.y,cycle,(cycle-born.getOrDefault(old,cycle))/(double)Math.max(Math.abs(c.x-old.x),Math.abs(c.y-old.y))));}
   born.keySet().retainAll(current);for(OlmSpecials.Cell c:current)born.putIfAbsent(c,cycle);motions=next;previous=current;
  }
  tracks.entrySet().removeIf(e->cycle-e.getValue().cycle>30);
  for(Track t:tracks.values())if(t.cycle==cycle&&(t.dx!=0||t.dy!=0)&&cycle-t.changed<=60)motions.put(t.cell,new Motion(t.dx,t.dy,t.changed,t.period));
  Set<OlmSpecials.Cell> predicted=new HashSet<>();for(OlmSpecials.Cell p:current){Motion m=motions.get(p);if(m==null)m=laneMotion(p,current,cycle);if(m==null){if(cycle-born.getOrDefault(p,cycle)<=30)for(int d=1;d<=2;d++){view.uncertainElectric.add(new OlmSpecials.Cell(p.x+d,p.y,p.plane));view.uncertainElectric.add(new OlmSpecials.Cell(p.x-d,p.y,p.plane));view.uncertainElectric.add(new OlmSpecials.Cell(p.x,p.y+d,p.plane));view.uncertainElectric.add(new OlmSpecials.Cell(p.x,p.y-d,p.plane));}continue;}if(cycle-m.cycle>60&&view.electricLookaheadCycles<=0)continue;int scale=Math.max(Math.abs(m.dx),Math.abs(m.dy));if(scale==0)continue;
   // Unknown or stale cadence keeps the full conservative trajectory. Measured melee windows reserve only imminent arrivals.
   int horizon=48;if(view.electricLookaheadCycles>0&&m.period>=1&&cycle-m.cycle<=60)horizon=Math.min(48,Math.max(1,(int)Math.ceil((view.electricLookaheadCycles+cycle-m.cycle)/m.period)));
   for(int i=1;i<=horizon;i++){OlmSpecials.Cell q=new OlmSpecials.Cell(p.x+(int)Math.round(m.dx*i/(double)scale),p.y+(int)Math.round(m.dy*i/(double)scale),p.plane);if(view.player.distance(q)<=24)predicted.add(q);}
  }
  List<OlmSpecials.Cell> ordered=new ArrayList<>(predicted);ordered.sort(Comparator.comparingInt(view.player::distance));for(OlmSpecials.Cell p:ordered)if(!current.contains(p))view.hazards.add(new OlmSpecials.Hazard(p,OlmSpecials.HazardKind.ELECTRIC,radius));
 }
}
