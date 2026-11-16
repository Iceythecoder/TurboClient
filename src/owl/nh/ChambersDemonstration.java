package owl.nh;

import com.google.gson.Gson;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;

/** In-memory demonstrations and a deliberately small, observation-only exemplar learner. */
final class ChambersDemonstration {
 static final int MAX_FRAMES=12000,MAX_ACTIONS=16000;
 static final class Frame {
  int tick,cycle,region,x,y,plane,hp,prayer,animation,phase;
  int[] inventory,equipment,levels;final List<int[]> npcs=new ArrayList<>(),projectiles=new ArrayList<>();
 }
 static final class Action {
  Frame before,after;String option,menu;int id,param0,param1,itemId,targetNpcId=-1;
  String label(){
   if(menu.equals("WALK"))return "walk to scene tile "+param0+", "+param1;
   if(menu.contains("WIDGET")||menu.startsWith("CC_OP"))for(Prayer p:Prayer.values())if(p.getWidgetInfo()!=null&&p.getWidgetInfo().getId()==param1)return option+" "+p.name();
   return option+" / "+menu+" / "+(targetNpcId>=0?"NPC "+targetNpcId:itemId>0?"item "+itemId:"ID "+id)+" / params "+param0+", "+param1;
  }
 }
 static final class Marker {int tick,phase;String note;Marker(int tick,int phase,String note){this.tick=tick;this.phase=phase;this.note=note;}}
 static final class Saved {String schema;List<Frame> frames;List<Action> actions;List<Marker> markers;int learnedExampleCount;}
 static final class Model {
  final List<Action> examples;
  Model(List<Action> examples){this.examples=Collections.unmodifiableList(new ArrayList<>(examples));}
  String predict(Frame now){
   Action best=null;double nearest=Double.POSITIVE_INFINITY;
   for(Action a:examples){double d=distance(now,a.before);if(d<nearest){nearest=d;best=a;}}
   if(best==null||nearest>3)return "Abstain: no similar demonstrated state.";
   return "Observed example suggests: "+best.label()+"\nDistance "+String.format(Locale.ROOT,"%.2f",nearest)+" (similarity, not confidence). Demonstrated at tick "+best.before.tick+". No action sent.";
  }
 }
 private final Client client;private final List<Frame> frames=new ArrayList<>();private final List<Action> actions=new ArrayList<>();private final List<Marker> markers=new ArrayList<>();
 volatile boolean recording;volatile String status="No demonstration recorded.",prediction="No model trained.";
 private volatile Model model;private Frame current;private int phase,sessionTick=-1;
 ChambersDemonstration(Client client){this.client=client;}
 synchronized void start(){
  if(recording)throw new IllegalStateException("Already recording");
  if(!frames.isEmpty()||!actions.isEmpty())throw new IllegalStateException("Save the current demonstration if needed, then discard it before starting a new one.");
  if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null)throw new IllegalStateException("Log into Orion before recording.");
  recording=true;sessionTick=client.getTickCount();try{current=capture();frames.add(current);updateStatus();}catch(RuntimeException error){recording=false;throw error;}
 }
 synchronized void stop(String reason){if(recording){recording=false;status="Stopped: "+reason+". "+frames.size()+" frames / "+actions.size()+" actions. Data stays in memory.";}}
 synchronized void clear(){recording=false;frames.clear();actions.clear();markers.clear();current=null;model=null;phase=0;sessionTick=-1;status="Demonstration and learned examples discarded.";prediction="No model trained.";}
 synchronized boolean hasData(){return !frames.isEmpty()||!actions.isEmpty()||model!=null;}
 synchronized void phase(int value){phase=value;if(recording&&markers.size()<200)mark("Manual neck cue changed");}
 synchronized void mark(String text){
  if(!recording)throw new IllegalStateException("Start recording before adding a marker.");
  if(markers.size()>=200)throw new IllegalStateException("Marker limit reached");
  if(text.trim().isEmpty()||text.length()>160)throw new IllegalArgumentException("Marker must contain 1..160 characters");
  markers.add(new Marker(client.getTickCount(),phase,text.trim()));
 }
 synchronized void tick(){
  if(!recording&&model==null)return;
  try{
   if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null){stop("Logout or player unavailable");prediction="No prediction while logged out.";return;}
   if(recording&&client.getTickCount()<sessionTick){stop("Game tick reset; start a new demonstration");return;}
   Frame next=capture();
   if(recording){
    for(int i=actions.size()-1;i>=0;i--){Action a=actions.get(i);if(a.after!=null)break;if(a.before.tick<next.tick)a.after=next;}
    if(frames.size()>=MAX_FRAMES){stop("Frame limit reached");return;}
    frames.add(next);current=next;updateStatus();
   }
   if(!recording&&model!=null)prediction=model.predict(next);
  }catch(RuntimeException error){stop("Observation failed: "+error.getClass().getSimpleName());prediction="Observation unavailable; no action sent.";}
 }
 synchronized void menu(MenuOptionClicked event){
  if(!recording||event.isConsumed()||!allowed(event.getMenuAction().name(),event.getMenuOption()))return;
  try{
   if(client.getGameState()!=GameState.LOGGED_IN){stop("Logout");return;}
   if(actions.size()>=MAX_ACTIONS){stop("Action limit reached");return;}
   Action a=new Action();a.before=capture();a.option=event.getMenuOption().toLowerCase(Locale.ROOT);a.menu=event.getMenuAction().name();a.id=event.getId();a.param0=event.getParam0();a.param1=event.getParam1();a.itemId=event.getItemId();
   if(a.menu.contains("NPC"))for(NPC npc:client.getNpcs())if(npc.getIndex()==a.id){a.targetNpcId=npc.getId();break;}
   actions.add(a);updateStatus();
  }catch(RuntimeException error){stop("Action observation failed: "+error.getClass().getSimpleName());}
 }
 static boolean allowed(String menu,String option){
  if(menu==null||option==null||menu.contains("PLAYER")||menu.contains("EXAMINE"))return false;
  boolean source=menu.equals("WALK")||menu.startsWith("NPC_")||menu.contains("ON_NPC")||menu.startsWith("GAME_OBJECT_")||menu.startsWith("ITEM_")||menu.startsWith("WIDGET_")||menu.equals("CC_OP")||menu.equals("CC_OP_LOW_PRIORITY");
  return source&&Arrays.asList("walk here","attack","cast","drink","eat","wield","wear","equip","remove","activate","deactivate","open","enter","climb","climb-up","climb-down","pass","use","take","search").contains(option.toLowerCase(Locale.ROOT));
 }
 private Frame capture(){
  Player self=client.getLocalPlayer();if(self==null)throw new IllegalStateException("No player");Frame f=new Frame();WorldPoint tile=WorldPoint.fromLocalInstance(client,self.getLocalLocation());
  f.tick=client.getTickCount();f.cycle=client.getGameCycle();f.region=tile.getRegionID();f.x=tile.getX();f.y=tile.getY();f.plane=tile.getPlane();f.hp=client.getBoostedSkillLevel(Skill.HITPOINTS);f.prayer=client.getBoostedSkillLevel(Skill.PRAYER);f.animation=self.getAnimation();f.phase=phase;
  f.inventory=items(InventoryID.INVENTORY);f.equipment=items(InventoryID.EQUIPMENT);
  for(NPC npc:client.getNpcs()){
   if(f.npcs.size()>=32)break;if(npc.getWorldLocation().distanceTo(self.getWorldLocation())>32)continue;
   WorldPoint n=WorldPoint.fromLocalInstance(client,npc.getLocalLocation());
   f.npcs.add(new int[]{npc.getId(),n.getX(),n.getY(),npc.getAnimation(),npc.getHealthRatio(),npc.getHealthScale(),npc.getIndex()});
  }
  if(client.getProjectiles()!=null)for(Projectile p:client.getProjectiles())if(p.getInteracting()==self&&p.getEndCycle()>f.cycle&&f.projectiles.size()<32)f.projectiles.add(new int[]{p.getId(),p.getEndCycle()-f.cycle});
  f.levels=new int[]{client.getBoostedSkillLevel(Skill.ATTACK),client.getBoostedSkillLevel(Skill.STRENGTH),client.getBoostedSkillLevel(Skill.DEFENCE),client.getBoostedSkillLevel(Skill.RANGED),client.getBoostedSkillLevel(Skill.MAGIC)};
  return f;
 }
 private int[] items(InventoryID id){ItemContainer c=client.getItemContainer(id);if(c==null)return new int[0];Item[] items=c.getItems();int[] result=new int[Math.min(items.length,64)*2];for(int i=0;i<result.length/2;i++){result[2*i]=items[i]==null?-1:items[i].getId();result[2*i+1]=items[i]==null?0:items[i].getQuantity();}return result;}
 private void updateStatus(){status="Recording YOUR manual run: "+frames.size()+" frames / "+actions.size()+" actions / "+markers.size()+" markers. Memory only. No automatic raid actions."+(actions.isEmpty()?"":"\nLast recorded: "+actions.get(actions.size()-1).label());}
 synchronized List<Action> trainingCopy(){
  if(recording)throw new IllegalStateException("Stop recording before learning");
  List<Action> usable=new ArrayList<>();for(Action a:actions)if(a.after!=null)usable.add(a);
  if(usable.size()<5)throw new IllegalStateException("Need at least five recorded menu actions followed by a game tick.");return usable;
 }
 synchronized void learn(){List<Action> examples=trainingCopy();model=new Model(examples);prediction="Learned "+examples.size()+" local state/action examples. Live observation predictions enabled. No validation score or autonomous control.";status="Demonstration stopped. Exemplar model ready ("+examples.size()+" actions).";}
 synchronized String export(){
  if(recording)throw new IllegalStateException("Stop recording before saving");
  Map<String,Object> data=new LinkedHashMap<>();data.put("schema","orion-chambers-demonstration-v1");data.put("source","real-client-api; manual menu events; no screenshots");data.put("limitations","Manual raid/phase context; actions are intentions, not confirmed success. Next-tick state is not proof of causation. No external-player names or chat captured. No independent evaluation.");data.put("frames",frames);data.put("actions",actions);data.put("markers",markers);data.put("learnedExampleCount",model==null?0:model.examples.size());return new Gson().toJson(data);
 }
 synchronized void restore(String json){
  if(recording||hasData())throw new IllegalStateException("Stop and discard the current demonstration before loading another.");
  Saved saved=new Gson().fromJson(json,Saved.class);
  if(saved==null||!"orion-chambers-demonstration-v1".equals(saved.schema)||saved.frames==null||saved.actions==null||saved.markers==null||saved.frames.size()>MAX_FRAMES||saved.actions.size()>MAX_ACTIONS||saved.markers.size()>200)throw new IllegalArgumentException("Invalid or oversized demonstration");
  for(Frame f:saved.frames)validate(f);
  for(Action a:saved.actions){if(a==null||!allowed(a.menu,a.option))throw new IllegalArgumentException("Unsupported recorded action");validate(a.before);if(a.after!=null)validate(a.after);}
  for(Marker m:saved.markers)if(m==null||m.note==null||m.note.length()>160||m.phase<0||m.phase>2)throw new IllegalArgumentException("Invalid marker");
  frames.addAll(saved.frames);actions.addAll(saved.actions);markers.addAll(saved.markers);status="Loaded "+frames.size()+" frames / "+actions.size()+" actions. Click Learn from demonstration.";prediction="No model trained in this session.";
 }
 private static void validate(Frame f){
  if(f==null||f.phase<0||f.phase>2||f.npcs==null||f.npcs.size()>32||f.projectiles==null||f.projectiles.size()>32||f.inventory==null||f.inventory.length>128||f.equipment==null||f.equipment.length>128||f.levels!=null&&f.levels.length>5)throw new IllegalArgumentException("Invalid recorded state");
  for(int[] n:f.npcs)if(n==null||n.length!=7)throw new IllegalArgumentException("Invalid NPC state");for(int[] p:f.projectiles)if(p==null||p.length!=2)throw new IllegalArgumentException("Invalid projectile state");
 }
 static double distance(Frame a,Frame b){
  if(a.region!=b.region||a.plane!=b.plane||a.phase!=b.phase)return Double.POSITIVE_INFINITY;
  double d=Math.abs(a.hp-b.hp)/25.0+Math.abs(a.prayer-b.prayer)/25.0+Math.min(5,Math.max(Math.abs(a.x-b.x),Math.abs(a.y-b.y))/4.0);
  if(a.animation!=b.animation)d+=.5;
  Set<Integer> ids=new HashSet<>();for(int[] n:a.npcs)ids.add(n[0]);Set<Integer> other=new HashSet<>();for(int[] n:b.npcs)other.add(n[0]);if(!ids.equals(other))d+=2;
  for(int[] n:a.npcs)for(int[] m:b.npcs)if(n[0]==m[0]){if(n[5]>0&&m[5]>0&&n[4]>=0&&m[4]>=0)d+=Math.abs((double)n[4]/n[5]-(double)m[4]/m[5]);if(n[3]!=m[3])d+=.2;break;}
  int p=a.projectiles.isEmpty()?-1:a.projectiles.get(0)[0],q=b.projectiles.isEmpty()?-1:b.projectiles.get(0)[0];if(p!=q)d+=2;
  if(a.levels!=null&&b.levels!=null)for(int i=0;i<Math.min(a.levels.length,b.levels.length);i++)d+=Math.abs(a.levels[i]-b.levels[i])/50.0;
  if(!Arrays.equals(a.inventory,b.inventory))d+=.5;if(!Arrays.equals(a.equipment,b.equipment))d+=.5;return d;
 }
}
