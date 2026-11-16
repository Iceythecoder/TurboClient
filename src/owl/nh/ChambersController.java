package owl.nh;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.unethicalite.api.widgets.Prayers;

/** Olm milestone 1-3: game-thread only observation and explicitly armed prayer output. */
final class ChambersController {
 static final class Config {
  final Set<Integer> regions,npcs;final Map<Integer,Prayer> prayers;final int marginCycles;
  Config(Set<Integer> regions,Set<Integer> npcs,Map<Integer,Prayer> prayers,int marginCycles){
   this.regions=Collections.unmodifiableSet(new HashSet<>(regions));this.npcs=Collections.unmodifiableSet(new HashSet<>(npcs));this.prayers=Collections.unmodifiableMap(new HashMap<>(prayers));this.marginCycles=marginCycles;
   if(marginCycles<1||marginCycles>50)throw new IllegalArgumentException("Safety margin must be 1..50 client cycles");
  }
 }
 static final class Threat {
  final int id,end;final Prayer prayer;
  Threat(int id,int end,Prayer prayer){this.id=id;this.end=end;this.prayer=prayer;}
 }
 static Threat earliest(Collection<Threat> threats,int cycle){return threats.stream().filter(t->t.end>cycle&&t.prayer!=null).min(Comparator.comparingInt((Threat t)->t.end).thenComparingInt(t->t.id)).orElse(null);}
 static boolean conflict(Collection<Threat> threats,Threat first){return first!=null&&threats.stream().anyMatch(t->t.end==first.end&&t.prayer!=null&&t.prayer!=first.prayer);}
 static boolean raidValid(boolean alive,int region,Set<Integer> nearby,Config c){return alive&&c.regions.contains(region)&&nearby.stream().anyMatch(c.npcs::contains);}
 private final Client client;
 private final java.util.function.Consumer<Prayer> output;private java.util.function.Predicate<Prayer> verifiedOutput;void verifiedOutput(java.util.function.Predicate<Prayer> next){verifiedOutput=next;}
 private final java.util.function.BooleanSupplier prayerUi;private boolean visibleUiRequired=true;
 private final IdentityHashMap<Projectile,Threat> incoming=new IdentityHashMap<>();
 private final IdentityHashMap<Projectile,OlmPrayerLearning.Flight> flights=new IdentityHashMap<>();
 final OlmPrayerLearning learning=new OlmPrayerLearning();
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private Config config=new Config(Collections.emptySet(),Collections.emptySet(),Collections.emptyMap(),5);
 volatile String lastEvent="No prayer output requested";volatile boolean enabled,armed;volatile String display="Not monitoring",history="";
 boolean tolerant,trustedContext;private int nextPrayerCycle;private Prayer lastRequestedPrayer;
 private Prayer pending;private int pendingAt;
 private boolean waitingForFocus;private long armedAt;
 private final Set<String> taughtLocations=new HashSet<>();
 private String locationKey(){Player self=client.getLocalPlayer();if(self==null)return "";WorldPoint point=WorldPoint.fromLocalInstance(client,self.getLocalLocation());return point.getRegionID()+":"+point.getPlane();}
 String teachCurrentLocation(){
  if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null||!client.isInInstancedRegion())throw new IllegalStateException("Log in and stand inside the Chambers instance before teaching this location.");
  if(taughtLocations.size()>=32&&!taughtLocations.contains(locationKey()))throw new IllegalStateException("Location limit reached");
  taughtLocations.add(locationKey());return "Learned current template region/plane "+locationKey()+" from your confirmation. Nearby NPC IDs: "+nearby()+". NPCs have NOT been labeled as Olm.";
 }
 boolean taughtLocationMatches(){return client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null&&client.isInInstancedRegion()&&taughtLocations.contains(locationKey());}
 void forgetLocations(){taughtLocations.clear();}
 ChambersController(Client client){this(client,Prayers::toggle,visiblePrayer(client));}
 ChambersController(Client client,java.util.function.Consumer<Prayer> output){this(client,output,()->true);visibleUiRequired=false;}
 ChambersController(Client client,java.util.function.Consumer<Prayer> output,java.util.function.BooleanSupplier prayerUi){this.client=client;this.output=output;this.prayerUi=prayerUi;}
 private static java.util.function.BooleanSupplier visiblePrayer(Client client){VisibleCombatUi ui=new VisibleCombatUi(client);return ()->ui.ready(net.unethicalite.api.widgets.Tab.PRAYER);} void configure(Config next){stop("Settings changed");config=next;incoming.clear();flights.clear();learning.clear();}
 void start(){stop("Observation started");enabled=true;incoming.clear();flights.clear();nextPrayerCycle=0;lastRequestedPrayer=null;}
 void close(){enabled=false;armed=false;pending=null;incoming.clear();flights.clear();learning.clear();events.clear();config=new Config(Collections.emptySet(),Collections.emptySet(),Collections.emptyMap(),5);display="Not monitoring";history="";trustedContext=false;}
 void stop(String reason){armed=false;pending=null;if(enabled)note("Stopped: "+reason);}
 private int region(){Player self=client.getLocalPlayer();return self==null?-1:WorldPoint.fromLocalInstance(client,self.getLocalLocation()).getRegionID();}
 private Set<Integer> nearby(){Set<Integer> ids=new HashSet<>();Player self=client.getLocalPlayer();if(self!=null)for(NPC npc:client.getNpcs())if(npc.getWorldLocation().getPlane()==self.getWorldLocation().getPlane()&&npc.getWorldLocation().distanceTo(self.getWorldLocation())<=64)ids.add(npc.getId());return ids;}
 private boolean valid(){Player self=client.getLocalPlayer();return client.getGameState()==GameState.LOGGED_IN&&self!=null&&!self.isDead()&&client.getBoostedSkillLevel(Skill.HITPOINTS)>0&&(trustedContext||raidValid(true,region(),nearby(),config));}
 void arm(){
  if(!enabled||!valid())throw new IllegalStateException("Observe inside the configured Chambers region with a configured Olm NPC nearby first.");
  if(config.prayers.isEmpty())throw new IllegalStateException("Record and configure verified projectile IDs first.");
  if(client.getBoostedSkillLevel(Skill.PRAYER)<=0)throw new IllegalStateException("No prayer points");
  if(!tolerant){incoming.clear();flights.clear();}pending=null;waitingForFocus=true;armedAt=System.nanoTime();armed=true;note("Prayer switching armed. Focus the Orion game canvas within 10 seconds; F9 stops.");
 }
 boolean encounterVerified(){return valid();}
 Prayer incomingPrayer(){Threat first=earliest(incoming.values(),client.getGameCycle());return first==null?null:first.prayer;}
 boolean protectionRequired(){Threat first=earliest(incoming.values(),client.getGameCycle());return enabled&&first!=null&&!client.isPrayerActive(first.prayer)&&(pending!=first.prayer||first.end-client.getGameCycle()<=safetyMargin());}
 String priorityReason(){Threat first=earliest(incoming.values(),client.getGameCycle());return first==null?"No mapped incoming hit":first.prayer+" for projectile "+first.id+" ("+(first.end-client.getGameCycle())+" cycles to estimated impact); "+lastEvent;}
 void projectile(Projectile p){
  if(!enabled||client.getGameState()!=GameState.LOGGED_IN||p==null)return;
  Player self=client.getLocalPlayer();if(self==null||p.getInteracting()!=self||p.getFloor()!=client.getPlane())return;
  int end=p.getEndCycle(),cycle=client.getGameCycle();if(end<=cycle)return;
  Threat prior=incoming.get(p);if(prior==null){
   if(incoming.size()>=128){stop("Projectile tracking limit reached");return;}
   note("Incoming projectile ID "+p.getId()+" -> "+config.prayers.getOrDefault(p.getId(),null)+"; remaining "+(end-cycle)+" cycles");
   flights.put(p,learning.flight(p.getId(),p.getStartCycle(),cycle,end,config.prayers.get(p.getId()),client.getBoostedSkillLevel(Skill.HITPOINTS),p.getX(),p.getY(),p.getZ()));
  }
  OlmPrayerLearning.Flight flight=flights.get(p);if(flight!=null){flight.impactCycle=end;flight.x=p.getX();flight.y=p.getY();flight.z=p.getZ();}
  incoming.put(p,new Threat(p.getId(),end,config.prayers.get(p.getId())));
 }
 void pulse(boolean freshTicks){
  if(!enabled)return;
  try{
   if(client.getGameState()!=GameState.LOGGED_IN){stop("Not logged in");incoming.clear();flights.clear();display="Not logged in / observation only";return;}
   int cycle=client.getGameCycle();
   for(Iterator<Map.Entry<Projectile,Threat>> it=incoming.entrySet().iterator();it.hasNext();){Map.Entry<Projectile,Threat> entry=it.next();Threat t=entry.getValue();OlmPrayerLearning.Flight f=flights.get(entry.getKey());boolean changed=entry.getKey().getInteracting()!=client.getLocalPlayer();if(f!=null){if(!changed)learning.observe(f,cycle,t.prayer!=null&&client.isPrayerActive(t.prayer));f.overlap|=conflict(incoming.values(),t);if(t.end<=cycle||changed){learning.finish(f,cycle,changed);note("Projectile "+t.id+": "+f.result+"; damage protection remains unverified");flights.remove(entry.getKey());}}if(t.end<=cycle||changed)it.remove();}
   boolean inside=valid();Threat first=earliest(incoming.values(),cycle);
   display=(armed?"ARMED":"OBSERVE")+" | Chambers check: "+(inside?"matched configured region + NPC":"NOT VERIFIED")+"\nTemplate region: "+region()+" | nearby NPC IDs: "+nearby()+"\nTracked incoming: "+incoming.size()+"\n"+(first==null?"No mapped incoming projectile":("Next: ID "+first.id+" -> "+first.prayer+" | "+(first.end-cycle)+" client cycles / ~"+((first.end-cycle)*20)+" ms to estimated impact"))+"\nMargin: "+safetyMargin()+" cycles. Switch immediately; margin flags late detection.\n"+learning.status();
   if(!armed)return;
   if(!freshTicks){stop("GameTick stream stale");return;}
   if(!inside){stop("Left configured Chambers encounter or player unavailable");return;}
   if(client.getCanvas()==null||!client.getCanvas().isFocusOwner()){if(!waitingForFocus||System.nanoTime()-armedAt>10000000000L)stop("Game focus lost or focus timeout");return;}
   waitingForFocus=false;
   if(client.getBoostedSkillLevel(Skill.PRAYER)<=0){stop("Prayer depleted");return;}
   if(incoming.values().stream().anyMatch(t->t.prayer==null)&&!tolerant){stop("Unmapped incoming projectile; classify it before re-arming");return;}
   if(conflict(incoming.values(),first)&&!tolerant){stop("Conflicting prayers at identical estimated impact time");return;}
   if(pending!=null){if(client.isPrayerActive(pending)){learning.acknowledged(cycle-pendingAt);note("Prayer confirmed: "+pending+" after "+(cycle-pendingAt)+" client cycles");pending=null;nextPrayerCycle=cycle;lastRequestedPrayer=null;}else if(first!=null&&first.prayer!=pending&&cycle>pendingAt){note("New imminent threat supersedes the pending prayer request");pending=null;}else if(cycle-pendingAt>=(tolerant?35:5)){if(tolerant){pending=null;note("Prayer not confirmed after one tick; retrying when its interface is ready");}else{stop("Prayer activation not confirmed within 5 client cycles");return;}}else return;}
   if(first==null||client.isPrayerActive(first.prayer))return;
   if(tolerant&&cycle<nextPrayerCycle&&first.prayer==lastRequestedPrayer)return;
   if(!prayerUi.getAsBoolean())return;
   if(client.getWidget(first.prayer.getWidgetInfo())==null){if(tolerant)note("Prayer tab opened; waiting for its widget to load");else stop("Prayer widget unavailable");return;}
   if(first.end-cycle<=safetyMargin())note("Late detection: within calibrated safety margin; protection not guaranteed");
   if(visibleUiRequired&&!client.getWidget(first.prayer.getWidgetInfo()).isVisible()){note("Waiting for visible prayer widget");return;}
   if(verifiedOutput!=null){if(!verifiedOutput.test(first.prayer))return;}else output.accept(first.prayer);for(Map.Entry<Projectile,Threat> entry:incoming.entrySet())if(entry.getValue().prayer==first.prayer){OlmPrayerLearning.Flight f=flights.get(entry.getKey());if(f!=null)learning.requested(f,cycle);}pending=first.prayer;pendingAt=cycle;nextPrayerCycle=cycle+35;lastRequestedPrayer=first.prayer;note("Requested prayer: "+first.prayer);
  }catch(RuntimeException error){stop("Client observation/action error: "+error.getClass().getSimpleName());}
 }
 private int safetyMargin(){return Math.max(config.marginCycles,learning.margin());}
 void damage(int amount){if(enabled)learning.damage(amount);}
 private void note(String text){lastEvent=text;if(events.size()==40)events.removeFirst();events.addLast(text);history=String.join("\n",events);}
 static Config parse(String regions,String npcs,String mappings,int margin){
  Set<Integer> r=ids(regions),n=ids(npcs);Map<Integer,Prayer> map=new HashMap<>();
  for(String line:mappings.split("\\R")){if(line.trim().isEmpty())continue;String[] pair=line.trim().split("\\s*=\\s*");if(pair.length!=2)throw new IllegalArgumentException("Expected projectile ID=MAGIC or RANGED or MELEE");
   int id=Integer.parseInt(pair[0]);if(id<0)throw new IllegalArgumentException("Invalid projectile ID");
   Prayer prayer;switch(pair[1].toUpperCase(Locale.ROOT)){case "MAGIC":prayer=Prayer.PROTECT_FROM_MAGIC;break;case "RANGED":prayer=Prayer.PROTECT_FROM_MISSILES;break;case "MELEE":prayer=Prayer.PROTECT_FROM_MELEE;break;default:throw new IllegalArgumentException("Unknown projectile style");}
   if(map.put(id,prayer)!=null)throw new IllegalArgumentException("Duplicate projectile ID");
  }return new Config(r,n,map,margin);
 }
 private static Set<Integer> ids(String text){Set<Integer> out=new HashSet<>();if(text.trim().isEmpty())return out;for(String value:text.trim().split("[\\s,]+")){int id=Integer.parseInt(value);if(id<0)throw new IllegalArgumentException("IDs must be nonnegative");out.add(id);}return out;}
}
