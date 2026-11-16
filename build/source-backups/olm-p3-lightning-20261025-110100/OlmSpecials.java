package owl.nh;

import java.util.*;
import net.runelite.api.Prayer;

/** Mechanics policy and a small online movement learner; no client state mutation here. */
final class OlmSpecials {
 enum Orb { PURPLE, RED, GREEN }
 enum HazardKind { UNDERFOOT, FALLING, CRYSTAL_BOMB, ELECTRIC }
 enum Kind { NONE, MOVE, BREAK_WALL, HOLD, CANCEL_ATTACK }
 static Prayer prayer(Orb colour){switch(colour){case PURPLE:return Prayer.PROTECT_FROM_MAGIC;case RED:return Prayer.PROTECT_FROM_MELEE;default:return Prayer.PROTECT_FROM_MISSILES;}}
 static final class Cell {
  final int x,y,plane;
  Cell(int x,int y,int plane){this.x=x;this.y=y;this.plane=plane;}
  int distance(Cell other){return plane==other.plane?Math.max(Math.abs(x-other.x),Math.abs(y-other.y)):Integer.MAX_VALUE;}
  @Override public boolean equals(Object obj){if(!(obj instanceof Cell))return false;Cell c=(Cell)obj;return x==c.x&&y==c.y&&plane==c.plane;}
  @Override public int hashCode(){return Objects.hash(x,y,plane);}
 }
 static final class Hazard {
  final Cell cell;final HazardKind kind;final int radius;
  Hazard(Cell cell,HazardKind kind,int radius){this.cell=cell;this.kind=kind;this.radius=radius;}
  boolean contains(Cell other){return cell.distance(other)<=radius;}
 }
 static final class View {
  Cell player,target,campAnchor;boolean rangeLocked;final Set<Cell> preferredCamps=new HashSet<>();int tick;boolean holdSafeTile,tickWalking,attackDue,needsApproach,oneTileFalling,stopAttack;
  final List<Cell> reachable=new ArrayList<>();final List<Hazard> hazards=new ArrayList<>();final Set<Cell> walls=new HashSet<>();
  final Set<Cell> attackTiles=new HashSet<>();
  final Map<Cell,Integer> attackDistance=new HashMap<>();
  final Set<Cell> uncertainElectric=new HashSet<>();
 }
 static Set<Cell> meleeTiles(Cell corner,int width,int height){Set<Cell> cells=new HashSet<>();for(int x=corner.x;x<corner.x+width;x++){cells.add(new Cell(x,corner.y-1,corner.plane));cells.add(new Cell(x,corner.y+height,corner.plane));}for(int y=corner.y;y<corner.y+height;y++){cells.add(new Cell(corner.x-1,y,corner.plane));cells.add(new Cell(corner.x+width,y,corner.plane));}return cells;}
 static final class Choice {
  final Kind kind;final Cell cell;final String context,reason;
  Choice(Kind kind,Cell cell,String context,String reason){this.kind=kind;this.cell=cell;this.context=context;this.reason=reason;}
 }
 static final class Score {int attempts,reached,correction;}
 private final Map<String,Score> learned=new HashMap<>();
 private Cell pendingFrom,pendingTo,wall,exit;private String pendingContext;private int pendingTick,lastBreak=-100;
 private int emptyWallsSince=-1;private int minX,maxX,minY,maxY,axis;private boolean outside;
 private String learning="No movement outcomes yet.";
 private String lastKey;private boolean lastGood;
 synchronized void resetEncounter(){pendingFrom=null;pendingTo=null;wall=null;exit=null;outside=false;lastBreak=-100;emptyWallsSince=-1;}
 synchronized void clear(){resetEncounter();learned.clear();lastKey=null;learning="Learning cleared.";}
 synchronized String learningStatus(){int attempts=0,reached=0;for(Score s:learned.values()){attempts+=s.attempts;reached+=s.reached;}return learning+" Confirmed arrivals "+reached+" / assessed "+attempts+". Based on arrivals and user feedback, not proven damage avoidance.";}
 synchronized void observe(Cell player,int tick){
  if(pendingTo==null)return;
  if(player.equals(pendingTo)){reward(true);learning="Confirmed movement reached the requested tile.";}
  else if(tick-pendingTick>=3){reward(false);learning="Requested tile not reached within 3 observed ticks; alternative directions get preference.";}
 }
 synchronized void requested(Choice choice,Cell from,int tick){if(choice.kind==Kind.MOVE&&(pendingTo==null||!pendingTo.equals(choice.cell))){pendingFrom=from;pendingTo=choice.cell;pendingContext=choice.context;pendingTick=tick;}}
 synchronized void feedback(boolean good){if(pendingTo!=null)reward(good);else if(lastKey!=null&&learned.containsKey(lastKey)){if(lastGood!=good){learned.get(lastKey).reached+=good?1:-1;lastGood=good;}}else throw new IllegalStateException("No movement to label");if(lastKey!=null&&learned.containsKey(lastKey))learned.get(lastKey).correction=good?1:-1;learning=good?"User marked movement good.":"User marked movement bad.";}
 private void reward(boolean good){String key=key(pendingContext,pendingFrom,pendingTo);Score score=learned.get(key);if(score==null&&learned.size()<128){score=new Score();learned.put(key,score);}if(score!=null&&score.attempts<100000){score.attempts++;if(good)score.reached++;lastKey=key;lastGood=good;}pendingTo=null;pendingFrom=null;pendingContext=null;}
 private static String key(String context,Cell from,Cell to){return context+":"+(to.x-from.x)+":"+(to.y-from.y);}
 private double preference(String context,Cell from,Cell to){Score score=learned.get(key(context,from,to));return score==null?0:(score.reached+1.0)/(score.attempts+2.0)-.5;}
 synchronized Choice choose(View view){
  observe(view.player,view.tick);
  refreshFire(view);
  if(wall==null&&!outside)trap(view);
  if(wall!=null&&!outside){
   if(view.walls.contains(wall)){
    if(view.player.distance(wall)>1){Cell step=best(view,"FIRE_APPROACH",wall,true);if(step==null)step=fireProgress(view,wall);return step==null?new Choice(Kind.HOLD,null,"FIRE_APPROACH","No safe approach to the flame wall"):new Choice(Kind.MOVE,step,"FIRE_APPROACH","Approach a wall tile before opening the gap");}
    if(view.tick-lastBreak>=3){return new Choice(Kind.BREAK_WALL,wall,"FIRE","Create a gap using configured Lunar/click interaction");}
    return new Choice(Kind.HOLD,null,"FIRE","Waiting to observe the wall gap");
   }
   Cell next=null;
   // Prefer a single validated destination 2-3 tiles beyond the opened wall.
   for(int clearance=3;clearance>=2&&next==null;clearance--){Cell candidate=axis==1?new Cell(wall.x,wall.y+(exit.y>wall.y?clearance:-clearance),wall.plane):new Cell(wall.x+(exit.x>wall.x?clearance:-clearance),wall.y,wall.plane);if(view.reachable.contains(candidate)&&safe(candidate,view))next=candidate;}
   if(next==null)next=best(view,"FIRE_ESCAPE",exit,true);if(next==null)next=fireProgress(view,exit);
   return next==null?new Choice(Kind.HOLD,null,"FIRE_ESCAPE","Gap seen but safe escape step unavailable"):new Choice(Kind.MOVE,next,"FIRE_ESCAPE","Move through the observed gap to a safe tile 2-3 tiles beyond the wall");
  }
  Set<HazardKind> active=EnumSet.noneOf(HazardKind.class);for(Hazard hazard:view.hazards)if(hazard.contains(view.player))active.add(hazard.kind);
  if(!active.isEmpty()){String context=active.size()>1?"COMBINED":active.iterator().next().name();Cell safe=best(view,context,null,false);if(safe==null)safe=combinedEscape(view);
   if(safe!=null)return new Choice(Kind.MOVE,safe,context,"Escape active hazards together: "+active);
   if(wall!=null&&!outside&&view.walls.contains(wall)&&view.player.distance(wall)<=1&&view.tick-lastBreak>=3){lastBreak=view.tick;return new Choice(Kind.BREAK_WALL,wall,"FIRE","Open a flame-wall gap while overlapping hazards block escape");}
   return new Choice(Kind.HOLD,null,context,"No verified escape from overlapping hazards; observer continues");}
  if(view.stopAttack)return new Choice(Kind.CANCEL_ATTACK,view.player,"STOP_MELEE","Cancel existing melee autoattack before changing gear or waiting for healing");
  if(view.needsApproach&&view.hazards.stream().anyMatch(h->h.kind==HazardKind.ELECTRIC)){Cell step=null;int distance=Integer.MAX_VALUE;for(Cell camp:view.attackTiles)if(safe(camp,view)){Cell candidate=best(view,"APPROACH",camp,true);if(candidate!=null&&candidate.distance(camp)<distance){step=candidate;distance=candidate.distance(camp);}}return step==null?new Choice(Kind.HOLD,null,"","No safe melee row reachable yet; wait for the wave"):new Choice(Kind.MOVE,step,"APPROACH","Approach a melee attack tile outside the projected electric lanes");}
  if(view.needsApproach&&view.target!=null){Cell safe=best(view,"APPROACH",view.target,true);return safe==null?new Choice(Kind.HOLD,null,"APPROACH","No safe approach step available"):new Choice(Kind.MOVE,safe,"APPROACH","Approach attack range without requesting an unsafe automatic chase");}
  if(!view.holdSafeTile&&view.tickWalking&&!view.attackDue&&view.hazards.stream().noneMatch(h->h.kind==HazardKind.ELECTRIC)){Cell safe=best(view,"FALLING_WALK",null,false);if(safe!=null)return new Choice(Kind.MOVE,safe,"FALLING_WALK","Walk between attack opportunities");}
  return new Choice(Kind.NONE,null,"","No urgent movement; allow normal combat");
 }
 synchronized void refreshFire(View view){
  if(view.walls.isEmpty()){if(emptyWallsSince<0)emptyWallsSince=view.tick;if(view.tick-emptyWallsSince>=2){wall=null;exit=null;outside=false;}}else emptyWallsSince=-1;
  if(exit!=null&&beyond(view.player)&&view.player.distance(wall)>=2)outside=true;
  if(wall==null&&!outside)trap(view);
 }
 synchronized boolean fireEscapePending(){return wall!=null&&!outside;}
 synchronized void wallRequested(int tick){lastBreak=tick;}
 synchronized Cell fireGoal(View view){return wall==null?null:view.walls.contains(wall)?wall:exit;}
 synchronized boolean fireArrival(Cell c,View view){return wall!=null&&(view.walls.contains(wall)?c.distance(wall)<=1: beyond(c)&&c.distance(wall)>=2&&safe(c,view));}
 private Cell fireProgress(View view,Cell goal){Cell result=null;for(Cell c:view.reachable)if(!c.equals(view.player)&&c.distance(goal)<view.player.distance(goal)&&escapeStep(view,view.player,c)&&(result==null||c.distance(goal)<result.distance(goal)))result=c;return result;}
 private void trap(View v){
  Cell north=null,south=null,east=null,west=null;
  for(Cell c:v.walls){if(c.plane!=v.player.plane)continue;
   if(c.x==v.player.x&&c.y>v.player.y&&(north==null||c.y<north.y))north=c;
   if(c.x==v.player.x&&c.y<v.player.y&&(south==null||c.y>south.y))south=c;
   if(c.y==v.player.y&&c.x>v.player.x&&(east==null||c.x<east.x))east=c;
   if(c.y==v.player.y&&c.x<v.player.x&&(west==null||c.x>west.x))west=c;
  }
  if(north!=null&&south!=null){axis=1;wall=v.player.distance(north)<=v.player.distance(south)?north:south;minY=south.y;maxY=north.y;minX=Integer.MAX_VALUE;maxX=Integer.MIN_VALUE;for(Cell c:v.walls){minX=Math.min(minX,c.x);maxX=Math.max(maxX,c.x);}exit=new Cell(wall.x,wall.y+(wall==north?3:-3),wall.plane);}
  else if(east!=null&&west!=null){axis=0;wall=v.player.distance(east)<=v.player.distance(west)?east:west;minX=west.x;maxX=east.x;minY=Integer.MAX_VALUE;maxY=Integer.MIN_VALUE;for(Cell c:v.walls){minY=Math.min(minY,c.y);maxY=Math.max(maxY,c.y);}exit=new Cell(wall.x+(wall==east?3:-3),wall.y,wall.plane);}
 }
 private boolean beyond(Cell c){return axis==1?(exit.y>maxY?c.y>maxY:c.y<minY):(exit.x>maxX?c.x>maxX:c.x<minX);}
 private boolean inside(Cell c){return axis==1?c.y>minY&&c.y<maxY&&c.x>=minX&&c.x<=maxX:c.x>minX&&c.x<maxX&&c.y>=minY&&c.y<=maxY;}
 boolean holdingOutside(){return outside;}
 boolean safe(Cell c,View view){if(view.uncertainElectric.contains(c)||view.walls.contains(c)||outside&&!beyond(c))return false;for(Hazard h:view.hazards)if(h.contains(c))return false;return true;}
 static boolean escapeStep(View v,Cell from,Cell to){if(v.walls.contains(to)||v.uncertainElectric.contains(to)&&!v.uncertainElectric.contains(from))return false;for(Hazard h:v.hazards)if(h.contains(to)){int before=h.cell.distance(from),after=h.cell.distance(to);if(!h.contains(from)||after<before)return false;}return electricProgress(v,from,to);}
 static boolean electricProgress(View v,Cell from,Cell to){int before=Integer.MAX_VALUE,after=Integer.MAX_VALUE;boolean stillInside=false;for(Hazard h:v.hazards)if(h.kind==HazardKind.ELECTRIC){before=Math.min(before,h.cell.distance(from));after=Math.min(after,h.cell.distance(to));if(h.contains(to)){if(!h.contains(from)||h.cell.distance(to)<h.cell.distance(from))return false;stillInside=true;}}return !stillInside||after>before;}
 private int combinedRisk(Cell c,View v){int risk=0;for(Hazard h:v.hazards)risk+=Math.max(0,h.radius+1-h.cell.distance(c));return risk;}
 private Cell combinedEscape(View v){Cell best=null;int initial=combinedRisk(v.player,v),risk=initial;for(Cell c:v.reachable){if(v.player.distance(c)!=1||outside&&!beyond(c)||!escapeStep(v,v.player,c))continue;int next=combinedRisk(c,v);if(next<risk||next==risk&&next<initial&&betterAttackTile(v,c,best)){risk=next;best=c;}}return best;}
 private boolean betterAttackTile(View v,Cell c,Cell old){if(old==null)return true;if(v.attackTiles.contains(c)!=v.attackTiles.contains(old))return v.attackTiles.contains(c);return v.attackDistance.getOrDefault(c,Integer.MAX_VALUE)<v.attackDistance.getOrDefault(old,Integer.MAX_VALUE);}
 private Cell best(View view,String context,Cell goal,boolean progress){
  Cell best=null;double score=-Double.MAX_VALUE;boolean safeFiringAvailable=!context.startsWith("FIRE")&&view.rangeLocked&&view.reachable.stream().anyMatch(c->view.attackTiles.contains(c)&&safe(c,view)&&(!view.oneTileFalling||view.player.distance(c)==1));
  for(Cell c:view.reachable){if(safeFiringAvailable&&!view.attackTiles.contains(c))continue;if(c.equals(view.player)||view.oneTileFalling&&!context.startsWith("FIRE")&&view.player.distance(c)!=1||!safe(c,view))continue;if(progress&&goal!=null&&c.distance(goal)>=view.player.distance(goal)&&(context.startsWith("FIRE")||!view.attackTiles.contains(c)))continue;
   // Arrival counts do not prove a dodge avoided damage. Never let those
   // historical direction scores pull a safe camp toward a hand or a wall.
   Score labelled=learned.get(key(context,view.player,c));double value=progress?preference(context,view.player,c)*4:labelled==null?0:labelled.correction*4;if(!context.startsWith("FIRE")&&view.attackTiles.contains(c))value+=1000;
   if(!context.startsWith("FIRE")){if(view.preferredCamps.contains(c))value+=120;if(view.campAnchor!=null)value-=Math.max(0,c.distance(view.campAnchor)-4)*80;}if(!progress)value-=view.player.distance(c)*100;
   if(goal!=null)value-=c.distance(goal)*(context.startsWith("FIRE")?100:1);
   if(best==null||value>score||value==score&&(c.x<best.x||c.x==best.x&&c.y<best.y)){best=c;score=value;}
  }return best;
 }
 synchronized String exportLearning(){Map<String,Object> data=new LinkedHashMap<>();data.put("schema","olm-movement-learning-v1");data.put("meaning","Movement arrivals/failures with explicit user corrections; not damage causality");data.put("scores",learned);return new com.google.gson.Gson().toJson(data);}
 static final class Saved {String schema;Map<String,Score> scores;}
 synchronized void restoreLearning(String json){if(json.length()>65536)throw new IllegalArgumentException("Movement learning exceeds 64 KB");Saved data=new com.google.gson.Gson().fromJson(json,Saved.class);if(data==null||!"olm-movement-learning-v1".equals(data.schema)||data.scores==null||data.scores.size()>128)throw new IllegalArgumentException("Invalid movement learning");for(Map.Entry<String,Score> entry:data.scores.entrySet()){Score s=entry.getValue();if(!entry.getKey().matches("(UNDERFOOT|FALLING|CRYSTAL_BOMB|ELECTRIC|FIRE_ESCAPE|FIRE_APPROACH|FALLING_WALK|APPROACH|STOP_MELEE|COMBINED):-?[0-9]:-?[0-9]")||s==null||s.attempts<0||s.attempts>100000||s.reached<0||s.reached>s.attempts||s.correction< -1||s.correction>1)throw new IllegalArgumentException("Invalid learned movement score");}learned.clear();learned.putAll(data.scores);lastKey=null;learning="Loaded movement preferences; live results remain unverified.";}
}

