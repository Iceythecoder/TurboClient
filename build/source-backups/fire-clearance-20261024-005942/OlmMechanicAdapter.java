package owl.nh;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.ProjectileMoved;
import net.unethicalite.api.entities.TileObjects;
import net.unethicalite.api.magic.Magic;
import net.unethicalite.api.magic.SpellBook;

/** Maps calibrated live signals to the pure mechanics policy. All movement uses native pathing. */
final class OlmMechanicAdapter {
 enum WallMode { UNCONFIGURED, CLICK, CAST_LUNAR, TARGET_LUNAR }
 static final class Config {
  Map<Integer,OlmSpecials.Orb> orbProjectiles=new HashMap<>();
  Set<Integer> underfootObjects=new HashSet<>(),underfootNpcs=new HashSet<>(),underfootGraphics=new HashSet<>(),fallingProjectiles=new HashSet<>(),fallingGraphics=new HashSet<>(),wallObjects=new HashSet<>(),wallNpcs=new HashSet<>();
  int underfootRadius=0,fallingRadius=0,maxStep=1;
  boolean projectileDestinationsVerified,tickWalkInHead=true,wallInteractionVerified;
  boolean holdSafeTile=true,useOrionCrystalSignals=true;
  boolean healingPoolsEnabled=true;
  // RuneLite gameval: OLM_HEALME_SPIT=1355, OLM_HEALME_SPOTANIM=1363.
  Set<Integer> healingPoolProjectiles=new HashSet<>(Arrays.asList(1355)),healingPoolGraphics=new HashSet<>(Arrays.asList(1363));
  boolean orionActorCrystal2856Verified=false;
  boolean exactLightningTiles=true,runAcrossLightning=true;
  boolean lightningCrossingTimingVerified=false;
  boolean useInstalledCrystalSignals=true,autoNamedFlameWalls=true;
  boolean useStandardOrbSignals=true,useStandardFirewallNpc=true;
  boolean useInstalledSpecialSignals=true;int bombRadius=4,electricRadius=1;
  Set<Integer> bombObjects=new HashSet<>(),bombNpcs=new HashSet<>(),bombGraphics=new HashSet<>(),electricObjects=new HashSet<>(),electricNpcs=new HashSet<>(),electricGraphics=new HashSet<>();
  WallMode wallMode=WallMode.UNCONFIGURED;String lunarSpell="",wallAction="AUTO";
  void validate(){
   if(orbProjectiles==null||orbProjectiles.size()>64||orbProjectiles.values().stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Invalid orb mapping");
   for(Set<Integer> ids:Arrays.asList(underfootObjects,underfootNpcs,underfootGraphics,fallingProjectiles,fallingGraphics,wallObjects,wallNpcs,healingPoolProjectiles,healingPoolGraphics,bombObjects,bombNpcs,bombGraphics,electricObjects,electricNpcs,electricGraphics))if(ids==null||ids.size()>128||ids.stream().anyMatch(id->id==null||id<0))throw new IllegalArgumentException("Invalid hazard IDs");
   if(bombRadius<1||bombRadius>8||electricRadius<0||electricRadius>2)throw new IllegalArgumentException("Invalid bomb/electric radius");
   if(orbProjectiles.keySet().stream().anyMatch(id->id==null||id<0)||underfootRadius<0||underfootRadius>2||fallingRadius<0||fallingRadius>2||maxStep<1||maxStep>2||wallMode==null)throw new IllegalArgumentException("Invalid hazard geometry");
   if(wallMode!=WallMode.UNCONFIGURED){if(!wallInteractionVerified)throw new IllegalArgumentException("Verify Orion's wall interaction before enabling it");if(wallObjects.isEmpty()&&wallNpcs.isEmpty())throw new IllegalArgumentException("No verified wall IDs");if(wallMode==WallMode.CLICK){if(wallAction==null||!wallAction.matches("[A-Za-z -]{1,40}"))throw new IllegalArgumentException("Configure the actual wall action");}else{if(lunarSpell==null||lunarSpell.contains("TELEPORT"))throw new IllegalArgumentException("Configure a non-teleport Lunar wall spell");SpellBook.Lunar.valueOf(lunarSpell);}}
  }
 }
 private final Client client;private final OlmSpecials policy=new OlmSpecials();private Config config=new Config();private OlmFighter.Profile profile;
 private final OlmPoolDuty poolDuty=new OlmPoolDuty();private final IdentityHashMap<Projectile,OlmSpecials.Cell> poolLandings=new IdentityHashMap<>();
 private boolean poolPriority,inHealingPool;private int poolMoveCycle=-1000,poolPlanTick=-1;private OlmSpecials.Cell poolMoveTarget;private final OlmTravel poolTravel=new OlmTravel();
 boolean poolPriority(){return poolPriority;}boolean inHealingPool(){return inHealingPool;}
 private boolean headAttackWindow;private int headWeaveTick=-1000,headShotUntil=-1000;private OlmSpecials.Cell headShotAnchor;
 boolean headAttackWindow(){return headAttackWindow;}
 private long remainingTickMs=Long.MAX_VALUE;void tickRemainingMs(long value){remainingTickMs=Math.max(0,value);}
 static boolean healingPoolGraphic(Config c,int id,OlmStrategy.Phase phase){return c.healingPoolsEnabled&&phase==OlmStrategy.Phase.HEAD&&c.healingPoolGraphics.contains(id);}
 private final IdentityHashMap<Projectile,OlmSpecials.Hazard> falling=new IdentityHashMap<>();
 private final OlmCrystalMarker actorCrystal=new OlmCrystalMarker();private final OlmElectricForecast electricMotion=new OlmElectricForecast();private final OlmTravel travel=new OlmTravel();private int travelClicks;private long moveEpoch;long moveEpoch(){return moveEpoch;}
 private final Map<OlmStrategy.Phase,ArrayDeque<Map<String,Object>>> phaseFrames=new EnumMap<>(OlmStrategy.Phase.class);
 private OlmSpecials.Cell crossing;private int crossingCycle=-1000;
 static boolean crossingEnabled(Config config){return config.runAcrossLightning&&config.lightningCrossingTimingVerified;}
 private final ArrayDeque<Map<String,Object>> actionEvents=new ArrayDeque<>();
 void action(String kind,OlmSpecials.Cell destination){Map<String,Object> e=new LinkedHashMap<>();e.put("tick",client.getTickCount());e.put("cycle",client.getGameCycle());e.put("action",kind);if(client.getLocalPlayer()!=null)e.put("from",new int[]{client.getLocalPlayer().getWorldLocation().getX(),client.getLocalPlayer().getWorldLocation().getY()});if(destination!=null)e.put("to",new int[]{destination.x,destination.y});e.put("runEnabled",client.getVarpValue(173)!=0);e.put("energy",client.getEnergy());actionEvents.addLast(e);while(actionEvents.size()>96)actionEvents.removeFirst();}
 static int electricRadius(Config config){return config.exactLightningTiles?0:config.electricRadius;}
 static boolean crossingClear(OlmSpecials.View view,List<int[]> actual,OlmSpecials.Cell destination){if(view.player.distance(destination)!=2||view.oneTileFalling)return false;int dx=Integer.signum(destination.x-view.player.x),dy=Integer.signum(destination.y-view.player.y);for(int step=1;step<=2;step++){OlmSpecials.Cell p=new OlmSpecials.Cell(view.player.x+dx*step,view.player.y+dy*step,view.player.plane);if(view.walls.contains(p))return false;for(OlmSpecials.Hazard h:view.hazards)if(h.kind!=OlmSpecials.HazardKind.ELECTRIC&&(h.contains(p)||h.contains(view.player)))return false;for(int[] c:actual)if(c[0]==p.x&&c[1]==p.y&&!p.equals(destination))return false;}return true;}
 private final Set<Integer> observedActorGraphics=new TreeSet<>();private final ArrayDeque<Map<String,Object>> diagnosticFrames=new ArrayDeque<>();private int diagnosticTick=-1,dodgeRequests;
 Map<String,Object> diagnostics(){Map<String,Object> result=new LinkedHashMap<>();result.put("healingPool",poolDuty.diagnostics(client.getTickCount()));result.put("actionEvents",new ArrayList<>(actionEvents));result.put("activeFallingTargets",falling.size());result.put("actorGraphics",new ArrayList<>(observedActorGraphics));result.put("sceneGraphics",new ArrayList<>(observedGraphics));result.put("projectiles",new ArrayList<>(observedProjectiles));result.put("dodgeClicksSubmitted",dodgeRequests);result.put("recentFrames",new ArrayList<>(diagnosticFrames));Map<String,Object> phases=new LinkedHashMap<>();phaseFrames.forEach((phase,frames)->phases.put(phase.name(),new ArrayList<>(frames)));result.put("phaseFrames",phases);return result;}
 void actorGraphic(Actor actor){actorGraphic(actor,false);}
 void actorGraphic(Actor actor,boolean changed){if(profile==null||actor==null||actor!=client.getLocalPlayer())return;int id=actor.getGraphic();if(id>=0&&observedActorGraphics.size()<64)observedActorGraphics.add(id);actorCrystal.observe(actorFallingGraphic(config,id),cell(actor.getWorldLocation()),client.getTickCount(),changed);}
 private void recordFrame(OlmSpecials.View view,Player player,OlmStrategy.Phase phase,List<int[]> rawElectric,Set<OlmSpecials.Cell> pools){if(diagnosticTick==view.tick)return;diagnosticTick=view.tick;Map<String,Object> frame=new LinkedHashMap<>();frame.put("phase",phase.name());frame.put("rawElectric",rawElectric);List<int[]> poolTiles=new ArrayList<>();for(OlmSpecials.Cell p:pools)if(poolTiles.size()<16)poolTiles.add(new int[]{p.x,p.y,p.plane});frame.put("healingPools",poolTiles);frame.put("inVisibleHealingPool",pools.contains(view.player));frame.put("tick",view.tick);frame.put("cycle",client.getGameCycle());frame.put("overheadMagic",client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC));frame.put("overheadRange",client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES));frame.put("overheadMelee",client.isPrayerActive(Prayer.PROTECT_FROM_MELEE));frame.put("runEnabled",client.getVarpValue(173)!=0);frame.put("decisionBeforeScan",message);frame.put("actorGraphic",player.getGraphic());frame.put("hp",client.getBoostedSkillLevel(Skill.HITPOINTS));frame.put("position",new int[]{view.player.x,view.player.y,view.player.plane});List<int[]> hazards=new ArrayList<>();for(OlmSpecials.Hazard h:view.hazards){if(hazards.size()>=32)break;hazards.add(new int[]{h.kind.ordinal(),h.cell.x,h.cell.y,h.radius});}frame.put("hazards",hazards);ArrayDeque<Map<String,Object>> history=phaseFrames.computeIfAbsent(phase,key->new ArrayDeque<>());history.addLast(frame);while(history.size()>32)history.removeFirst();diagnosticFrames.addLast(frame);while(diagnosticFrames.size()>32)diagnosticFrames.removeFirst();}
 private NPC stopMelee;
 void stopMelee(NPC target){stopMelee=target;}
 private boolean moved,dangerActive;private boolean retrySoon,urgentMovementPending;
 boolean urgentMovementPending(){return urgentMovementPending;}
 boolean retrySoon(){return retrySoon;}private volatile String message="Specials are awaiting calibrated IDs.";
 private OlmSpecials.Kind lastKind=OlmSpecials.Kind.NONE;
 private final VisibleCombatUi ui;
 private OlmSpecials.Cell lastMove;private int lastMoveCycle=-1000,lastMoveTick=-1000;
 private int lastWalkTick=-1000,lastCancelCycle=-1000;
 private final Set<Integer> observedGraphics=Collections.synchronizedSortedSet(new TreeSet<>()),observedProjectiles=Collections.synchronizedSortedSet(new TreeSet<>());
 OlmMechanicAdapter(Client client){this(client,new VisibleCombatUi(client));}
 OlmMechanicAdapter(Client client,VisibleCombatUi ui){this.client=client;this.ui=ui;}
 void configure(OlmFighter.Profile next){poolDuty.reset();poolLandings.clear();poolPriority=inHealingPool=false;profile=next;config=next.mechanics;falling.clear();electricMotion.clear();policy.clear();observedGraphics.clear();observedProjectiles.clear();}
 void start(){headAttackWindow=false;headWeaveTick=headShotUntil=-1000;headShotAnchor=null;poolDuty.reset();poolLandings.clear();poolPriority=inHealingPool=false;poolPlanTick=-1;poolMoveCycle=-1000;poolMoveTarget=null;poolTravel.reset();crossing=null;actorCrystal.reset();actionEvents.clear();diagnosticFrames.clear();phaseFrames.clear();observedActorGraphics.clear();diagnosticTick=-1;dodgeRequests=0;urgentMovementPending=false;retrySoon=false;travel.reset();travelClicks=0;falling.clear();electricMotion.clear();policy.resetEncounter();moved=false;lastMove=null;lastMoveCycle=-1000;lastMoveTick=-1000;lastWalkTick=-1000;}
 void pause(){poolDuty.cancel();poolLandings.clear();poolPriority=inHealingPool=false;poolTravel.reset();crossing=null;actorCrystal.reset();urgentMovementPending=false;retrySoon=false;travel.reset();falling.clear();electricMotion.clear();policy.resetEncounter();moved=false;lastMove=null;lastMoveCycle=-1000;lastMoveTick=-1000;lastWalkTick=-1000;}
 void clear(){poolDuty.reset();poolLandings.clear();poolPriority=inHealingPool=false;poolTravel.reset();crossing=null;actorCrystal.reset();actionEvents.clear();diagnosticFrames.clear();phaseFrames.clear();observedActorGraphics.clear();urgentMovementPending=false;travel.reset();profile=null;falling.clear();electricMotion.clear();policy.clear();observedGraphics.clear();observedProjectiles.clear();}
 String decisionStatus(){return message;}
 String status(){return message+"\n"+(config.useInstalledCrystalSignals?"Installed API crystal signals enabled (Orion variants unverified). ":"")+"Orb mappings: "+effectiveOrbs(config).size()+"; named flame-wall detection: "+config.autoNamedFlameWalls+".\nSeen player graphics "+observedActorGraphics+"; scene graphics "+observedGraphics+"; projectiles "+observedProjectiles+"\n"+policy.learningStatus();}
 String exportLearning(){return policy.exportLearning();}
 void restoreLearning(String json){policy.restoreLearning(json);}
 void feedback(boolean good){policy.feedback(good);}
 boolean needsAttackRefresh(){return moved;}
 void attacked(){if(headAttackWindow){headWeaveTick=client.getTickCount();headShotUntil=client.getGameCycle()+1;headShotAnchor=client.getLocalPlayer()==null?null:cell(client.getLocalPlayer().getWorldLocation());}moved=false;lastMove=null;lastMoveCycle=-1000;lastMoveTick=-1000;lastWalkTick=-1000;}
 boolean tickWalking(OlmStrategy.Phase phase){return !config.holdSafeTile&&config.tickWalkInHead&&phase==OlmStrategy.Phase.HEAD;}
 boolean holdingOutside(){return policy.holdingOutside();}
 boolean inventoryAllowed(){return lastKind==OlmSpecials.Kind.MOVE;}
 void observe(){Player p=client.getLocalPlayer();if(p!=null)policy.observe(cell(p.getWorldLocation()),client.getTickCount());}
 void projectile(ProjectileMoved event){
  Projectile p=event.getProjectile();if(profile!=null&&p!=null&&config.healingPoolsEnabled&&config.healingPoolProjectiles.contains(p.getId())&&p.getFloor()==client.getPlane()&&eventLanding(event)!=null&&poolLandings.size()<16){WorldPoint landing=WorldPoint.fromLocal(client,eventLanding(event));if(landing!=null&&inside(landing))poolLandings.put(p,cell(landing));}if(profile!=null&&p!=null&&observedProjectiles.size()<64)observedProjectiles.add(p.getId());LocalPoint destination=eventLanding(event);if(profile==null||p==null||!fallingProjectile(config,p.getId())||(!config.projectileDestinationsVerified&&!installedFalling(config,p.getId()))||destination==null||p.getFloor()!=client.getPlane())return;
  if(falling.size()>=128&&!falling.containsKey(p))return;
  WorldPoint target=WorldPoint.fromLocal(client,destination);if(target!=null&&inside(target))falling.put(p,new OlmSpecials.Hazard(cell(target),OlmSpecials.HazardKind.FALLING,config.fallingRadius));
 }
 // ProjectileMoved.position is the destination, including one-shot ground targets.
 static LocalPoint eventLanding(ProjectileMoved event){return event.getPosition()!=null?event.getPosition():event.getProjectile()==null?null:event.getProjectile().getTarget();}
 boolean dangerActive(){return dangerActive;}
 static boolean installedFalling(Config c,int id){return c.useInstalledCrystalSignals&&(id==ProjectileID.OLM_FALLING_CRYSTAL||id==ProjectileID.OLM_FALLING_CRYSTAL_TRAIL)||c.useOrionCrystalSignals&&id==2857;}
 static boolean fallingProjectile(Config c,int id){return c.fallingProjectiles.contains(id)||installedFalling(c,id);}
 static boolean underfootObject(Config c,int id){return c.underfootObjects.contains(id)||(c.useInstalledCrystalSignals&&(id==ObjectID.SMALL_CRYSTALS||id==ObjectID.LARGE_CRYSTALS));}
 static int fallingRadius(Config c,OlmStrategy.Phase phase){return phase==OlmStrategy.Phase.FINAL_HANDS||phase==OlmStrategy.Phase.HEAD?Math.max(2,c.fallingRadius):c.fallingRadius;}
 static boolean fallingGraphic(Config c,int id){return c.fallingGraphics.contains(id)||(c.useInstalledCrystalSignals&&(id==GraphicID.OLM_FALLING_CRYSTAL||id==GraphicID.OLM_FALLING_CRYSTAL_TRAIL));}
 static boolean actorFallingGraphic(Config c,int id){return fallingGraphic(c,id)||c.useOrionCrystalSignals&&c.orionActorCrystal2856Verified&&id==2856;}
 static boolean underfootGraphic(Config c,int id){return c.underfootGraphics.contains(id)||(c.useInstalledCrystalSignals&&id==GraphicID.OLM_CRYSTAL);}
 static boolean namedFlame(Config c,String name){if(!c.autoNamedFlameWalls||name==null)return false;String n=name.toLowerCase(Locale.ROOT);return n.equals("flame")||n.equals("flames")||n.contains("flame wall")||n.contains("wall of fire");}
 static boolean wallNpc(Config c,int id,String name){return c.wallNpcs.contains(id)||(c.useStandardFirewallNpc&&id==7558)||namedFlame(c,name);}
 static boolean namedBomb(String name){return name!=null&&name.replaceAll("<[^>]*>","").toLowerCase(Locale.ROOT).contains("crystal bomb");}
 static boolean namedElectric(String name){if(name==null)return false;String n=name.replaceAll("<[^>]*>","").toLowerCase(Locale.ROOT);return n.equals("lightning")||n.contains("electric wall")||n.contains("lightning wall");}
 static boolean bombObject(Config c,int id,String name){return c.bombObjects.contains(id)||c.useInstalledSpecialSignals&&(id==ObjectID.CRYSTAL_BOMB||namedBomb(name));}
 static boolean electricObject(Config c,int id,String name){return c.electricObjects.contains(id)||c.useInstalledSpecialSignals&&(id==ObjectID.LIGHTNING||id==ObjectID.LIGHTNING_16191||namedElectric(name));}
 static boolean electricGraphic(Config c,int id){return c.electricGraphics.contains(id)||c.useInstalledSpecialSignals&&id==GraphicID.OLM_LIGHTNING;}
 static Map<Integer,OlmSpecials.Orb> effectiveOrbs(Config c){Map<Integer,OlmSpecials.Orb> map=new HashMap<>();if(c.useStandardOrbSignals){map.put(1341,OlmSpecials.Orb.PURPLE);map.put(1343,OlmSpecials.Orb.GREEN);map.put(1345,OlmSpecials.Orb.RED);}map.putAll(c.orbProjectiles);return map;}
 boolean handle(OlmStrategy.Phase phase,boolean attackDue,NPC target,int attackRange){return handle(phase,attackDue,true,target,attackRange);}
 boolean handle(OlmStrategy.Phase phase,boolean attackDue,boolean allowCooldownWalking,NPC target,int attackRange){return handle(phase,attackDue,allowCooldownWalking,target,attackRange,null);}
 boolean handle(OlmStrategy.Phase phase,boolean attackDue,boolean allowCooldownWalking,NPC target,int attackRange,WorldPoint approach){
  return handle(phase,attackDue,allowCooldownWalking,target,attackRange,approach,()->{});
 }
 boolean handle(OlmStrategy.Phase phase,boolean attackDue,boolean allowCooldownWalking,NPC target,int attackRange,WorldPoint approach,Runnable protection){
  if(profile==null||client.getLocalPlayer()==null)return false;
  headAttackWindow=false;retrySoon=false;urgentMovementPending=false;Player player=client.getLocalPlayer();WorldPoint origin=player.getWorldLocation();OlmSpecials.View view=new OlmSpecials.View();view.player=cell(origin);view.tick=client.getTickCount();view.holdSafeTile=config.holdSafeTile;view.tickWalking=!config.holdSafeTile&&tickWalking(phase)&&allowCooldownWalking;view.attackDue=attackDue;if(target!=null)view.target=cell(target.getWorldLocation());
  // Destination data remains independent of a white spot animation masking a red marker.
  if(client.getProjectiles()!=null)for(Projectile p:client.getProjectiles()){if(config.healingPoolsEnabled&&config.healingPoolProjectiles.contains(p.getId())&&p.getFloor()==client.getPlane()&&p.getTarget()!=null&&!poolLandings.containsKey(p)&&poolLandings.size()<16){WorldPoint landing=WorldPoint.fromLocal(client,p.getTarget());if(landing!=null&&inside(landing))poolLandings.put(p,cell(landing));}
   if(observedProjectiles.size()<64)observedProjectiles.add(p.getId());
   if(!fallingProjectile(config,p.getId())||(!config.projectileDestinationsVerified&&!installedFalling(config,p.getId()))||p.getTarget()==null||p.getFloor()!=client.getPlane()||falling.size()>=128&&!falling.containsKey(p))continue;
   WorldPoint landing=WorldPoint.fromLocal(client,p.getTarget());if(landing!=null&&inside(landing)&&!falling.containsKey(p))falling.put(p,new OlmSpecials.Hazard(cell(landing),OlmSpecials.HazardKind.FALLING,config.fallingRadius));
  }
  falling.entrySet().removeIf(e->e.getKey().getEndCycle()<client.getGameCycle());for(OlmSpecials.Hazard h:falling.values())view.hazards.add(new OlmSpecials.Hazard(h.cell,h.kind,Math.max(h.radius,fallingRadius(config,phase))));
  Set<OlmSpecials.Cell> healingPools=new HashSet<>();Map<OlmSpecials.Cell,Object> walls=new HashMap<>();
  for(TileObject object:TileObjects.getAll(o->underfootObject(config,o.getId())||config.wallObjects.contains(o.getId())||namedFlame(config,o.getName())||bombObject(config,o.getId(),o.getName())||electricObject(config,o.getId(),o.getName()))){if(!inside(object.getWorldLocation()))continue;OlmSpecials.Cell c=cell(object.getWorldLocation());if(config.wallObjects.contains(object.getId())||namedFlame(config,object.getName())){view.walls.add(c);walls.put(c,object);}if(underfootObject(config,object.getId()))view.hazards.add(new OlmSpecials.Hazard(c,OlmSpecials.HazardKind.UNDERFOOT,config.underfootRadius));if(bombObject(config,object.getId(),object.getName()))view.hazards.add(new OlmSpecials.Hazard(c,OlmSpecials.HazardKind.CRYSTAL_BOMB,config.bombRadius));if(electricObject(config,object.getId(),object.getName())){view.hazards.add(new OlmSpecials.Hazard(c,OlmSpecials.HazardKind.ELECTRIC,electricRadius(config)));electricMotion.observe(object,c,client.getGameCycle());}}
  for(NPC npc:client.getNpcs()){if(!inside(npc.getWorldLocation()))continue;OlmSpecials.Cell c=cell(npc.getWorldLocation());if(wallNpc(config,npc.getId(),npc.getName())){view.walls.add(c);walls.put(c,npc);}if(config.bombNpcs.contains(npc.getId())||config.useInstalledSpecialSignals&&namedBomb(npc.getName()))view.hazards.add(new OlmSpecials.Hazard(c,OlmSpecials.HazardKind.CRYSTAL_BOMB,config.bombRadius));if(config.electricNpcs.contains(npc.getId())||config.useInstalledSpecialSignals&&namedElectric(npc.getName())){view.hazards.add(new OlmSpecials.Hazard(c,OlmSpecials.HazardKind.ELECTRIC,electricRadius(config)));electricMotion.observe(npc,c,client.getGameCycle());}if(config.underfootNpcs.contains(npc.getId()))view.hazards.add(new OlmSpecials.Hazard(c,OlmSpecials.HazardKind.UNDERFOOT,config.underfootRadius));}
  if(client.getGraphicsObjects()!=null)for(GraphicsObject graphic:client.getGraphicsObjects()){if(graphic.finished()||graphic.getLevel()!=client.getPlane())continue;WorldPoint pos=WorldPoint.fromLocal(client,graphic.getLocation());if(pos==null||!inside(pos))continue;if(observedGraphics.size()<64)observedGraphics.add(graphic.getId());if(healingPoolGraphic(config,graphic.getId(),phase))healingPools.add(cell(pos));if(config.bombGraphics.contains(graphic.getId()))view.hazards.add(new OlmSpecials.Hazard(cell(pos),OlmSpecials.HazardKind.CRYSTAL_BOMB,config.bombRadius));if(electricGraphic(config,graphic.getId())){view.hazards.add(new OlmSpecials.Hazard(cell(pos),OlmSpecials.HazardKind.ELECTRIC,electricRadius(config)));electricMotion.observe(graphic,cell(pos),client.getGameCycle());}if(underfootGraphic(config,graphic.getId()))view.hazards.add(new OlmSpecials.Hazard(cell(pos),OlmSpecials.HazardKind.UNDERFOOT,config.underfootRadius));if(fallingGraphic(config,graphic.getId()))view.hazards.add(new OlmSpecials.Hazard(cell(pos),OlmSpecials.HazardKind.FALLING,fallingRadius(config,phase)));}
  actorGraphic(player);actorCrystal.add(view,fallingRadius(config,phase));
  List<int[]> rawElectric=new ArrayList<>();for(OlmSpecials.Hazard h:view.hazards)if(h.kind==OlmSpecials.HazardKind.ELECTRIC&&rawElectric.size()<32)rawElectric.add(new int[]{h.cell.x,h.cell.y,h.radius});electricMotion.forecast(view,client.getGameCycle());recordFrame(view,player,phase,rawElectric,healingPools);
  if(target!=null&&attackRange==1&&target.getWorldArea()!=null){WorldArea area=target.getWorldArea();for(OlmSpecials.Cell camp:OlmSpecials.meleeTiles(new OlmSpecials.Cell(area.getX(),area.getY(),area.getPlane()),area.getWidth(),area.getHeight())){WorldPoint point=new WorldPoint(camp.x,camp.y,camp.plane);if(!inside(point))continue;boolean blocked=false;for(NPC n:client.getNpcs())if(n.getWorldArea()!=null&&n.getWorldArea().contains(point)){blocked=true;break;}if(!blocked)view.attackTiles.add(camp);}}
  view.oneTileFalling=view.hazards.stream().anyMatch(h->h.kind==OlmSpecials.HazardKind.FALLING);
  urgentMovementPending=view.hazards.stream().anyMatch(h->h.contains(view.player)&&(h.kind==OlmSpecials.HazardKind.FALLING||h.kind==OlmSpecials.HazardKind.UNDERFOOT||h.kind==OlmSpecials.HazardKind.ELECTRIC));
  if(handleHealingPools(phase,view,healingPools))return true;if(!urgentMovementPending)protection.run();
  view.stopAttack=stopMelee!=null&&player.getInteracting()==stopMelee;if(view.stopAttack)view.target=cell(stopMelee.getWorldLocation());
  int searchSteps=config.maxStep;for(OlmSpecials.Hazard hazard:view.hazards)if((hazard.kind==OlmSpecials.HazardKind.CRYSTAL_BOMB||hazard.kind==OlmSpecials.HazardKind.ELECTRIC)&&hazard.contains(view.player))searchSteps=Math.max(searchSteps,hazard.radius+1);
  if(view.oneTileFalling)searchSteps=1;
  if(crossing!=null){if(view.player.equals(crossing)||client.getGameCycle()-crossingCycle>=30)crossing=null;else if(!policy.safe(crossing,view)){action("CROSSING_ABORT_UNSAFE",crossing);crossing=null;}}
  OlmSpecials.Cell cross=crossingEnabled(config)&&client.getVarpValue(173)!=0&&client.getEnergy()>0?electricMotion.crossingTarget(view.player,client.getGameCycle()):null;
  if(cross!=null&&crossingClear(view,rawElectric,cross)&&(target==null||target.getWorldArea()!=null&&target.getWorldArea().distanceTo(new WorldPoint(cross.x,cross.y,cross.plane))<=attackRange)){
   WorldPoint from=origin;boolean clear=true;int dx=Integer.signum(cross.x-view.player.x),dy=Integer.signum(cross.y-view.player.y);for(int step=1;step<=2;step++){WorldPoint to=new WorldPoint(origin.getX()+dx*step,origin.getY()+dy*step,origin.getPlane());if(!inside(to)||!new WorldArea(from,1,1).canTravelInDirection(client,dx,dy)){clear=false;break;}for(NPC n:client.getNpcs())if(!namedElectric(n.getName())&&!config.electricNpcs.contains(n.getId())&&n.getWorldArea()!=null&&n.getWorldArea().contains(to)){clear=false;break;}from=to;}
   if(clear&&ui.clickGround(client,new WorldPoint(cross.x,cross.y,cross.plane))){action("LIGHTNING_CROSS",cross);crossing=cross;crossingCycle=client.getGameCycle();moveEpoch++;moved=true;urgentMovementPending=false;message="Approaching lightning exactly two tiles away: clicked its current tile with run enabled.";return true;}
  }
  for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){if(dx==0&&dy==0)continue;WorldPoint previous=origin;for(int distance=1;distance<=searchSteps;distance++){WorldPoint next=new WorldPoint(origin.getX()+dx*distance,origin.getY()+dy*distance,origin.getPlane());if(!inside(next)||view.walls.contains(cell(next))||!new WorldArea(previous,1,1).canTravelInDirection(client,dx,dy))break;boolean actorBlocked=false;for(NPC npc:client.getNpcs())if(!wallNpc(config,npc.getId(),npc.getName())&&npc.getWorldArea()!=null&&npc.getWorldArea().contains(next)){actorBlocked=true;break;}if(actorBlocked||electricCrossing(view,cell(previous),cell(next)))break;if(!OlmSpecials.escapeStep(view,cell(previous),cell(next)))break;view.reachable.add(cell(next));previous=next;}}
  if(target!=null&&attackRange>1&&target.getWorldArea()!=null)for(OlmSpecials.Cell c:view.reachable){int distance=target.getWorldArea().distanceTo(new WorldPoint(c.x,c.y,c.plane));view.attackDistance.put(c,distance);if(distance<=attackRange)view.attackTiles.add(c);}
  if(view.hazards.stream().anyMatch(h->h.kind==OlmSpecials.HazardKind.FALLING)&&allowCooldownWalking&&!config.holdSafeTile)view.tickWalking=true;if(view.hazards.stream().anyMatch(h->h.kind==OlmSpecials.HazardKind.ELECTRIC))view.tickWalking=false;dangerActive=!view.hazards.isEmpty()||!view.walls.isEmpty();view.needsApproach=target!=null&&player.getWorldArea().distanceTo(target.getWorldArea())>attackRange;if(approach!=null&&target==null){view.target=cell(approach);view.needsApproach=!view.player.equals(view.target);}
  if(travel.destination()!=null&&dangerActive){
   OlmSpecials.Cell redirect=null;for(OlmSpecials.Cell c:view.reachable)if(view.player.distance(c)==1&&policy.safe(c,view)&&!electricCrossing(view,view.player,c)&&(redirect==null||c.distance(view.target==null?view.player:view.target)<redirect.distance(view.target==null?view.player:view.target)))redirect=c;
   OlmSpecials.Cell to=redirect==null?view.player:redirect;
   if(ui.clickGround(client,new WorldPoint(to.x,to.y,to.plane))){action("TRAVEL_ABORT",to);travel.reset();lastMove=to;lastMoveCycle=client.getGameCycle();lastMoveTick=view.tick;moved=true;moveEpoch++;urgentMovementPending=false;}else retrySoon=true;
   message="Interrupted long travel for newly observed hazards; replan next client cycle.";return true;
  }
  if(lastMove!=null&&!view.player.equals(lastMove)&&pendingDodgeSafe(view,lastMove)&&awaitingStep(view.player,lastMove,view.tick,lastMoveTick)){urgentMovementPending=false;message="Single-tile dodge requested; other compatible actions may proceed while tracking arrival.";return true;}
  if(attackRange>1&&!view.needsApproach&&canWeaveHead(view,attackDue)){headWeaveTick=view.tick;headAttackWindow=true;urgentMovementPending=false;message="Ranged or magic attack slot after an observed dodge step; imminent impacts still take priority.";return false;}if(view.needsApproach&&!view.stopAttack&&view.hazards.isEmpty()&&view.walls.isEmpty()){if(travel(view,target,attackRange,approach))return true;}else travel.reset();
  OlmSpecials.Choice choice=policy.choose(view);message=choice.reason;lastKind=choice.kind;
  switch(choice.kind){
   case CANCEL_ATTACK:
    if(client.getGameCycle()-lastCancelCycle<5)return false;
    if(ui.clickGround(client,origin)){lastCancelCycle=client.getGameCycle();message="Cancelled melee attack with a visible Walk here click; preparing magic or waiting for healing to fade.";}else retrySoon=true;return false;
   case NONE:return false;
   case HOLD:return true;
   case MOVE:
    if(choice.context.equals("FALLING_WALK")&&lastWalkTick==view.tick)return true;
    if(choice.cell.equals(lastMove)&&awaitingStep(view.player,lastMove,view.tick,lastMoveTick))return true;
    boolean accepted=ui.clickGround(client,new WorldPoint(choice.cell.x,choice.cell.y,choice.cell.plane));if(!accepted){retrySoon=true;message="Dodge awaiting a visible ground click; rescan next client cycle.";}else {action("DODGE_"+choice.context,choice.cell);dodgeRequests++;policy.requested(choice,view.player,view.tick);urgentMovementPending=false;moved=true;moveEpoch++;lastMove=choice.cell;lastMoveCycle=client.getGameCycle();lastMoveTick=view.tick;lastWalkTick=view.tick;}return true;
   case BREAK_WALL:
    Object wall=walls.get(choice.cell);if(wall==null)return false;
    boolean autoClick=(wall instanceof NPC&&config.useStandardFirewallNpc&&((NPC)wall).getId()==7558)||namedFlame(config,wall instanceof NPC?((NPC)wall).getName():((TileObject)wall).getName());if(config.wallMode==WallMode.UNCONFIGURED&&!autoClick){message="Wall detected; gap interaction is not configured. Normal observer continues.";return false;}
    if(config.wallMode==WallMode.CLICK||autoClick){String action=autoClick?"AUTO":config.wallAction;if(action.equals("AUTO")){String[] actions=wall instanceof NPC?(((NPC)wall).getComposition()==null?null:((NPC)wall).getComposition().getActions()):((TileObject)wall).getActions();action=null;if(actions!=null)for(String option:actions)if(option!=null&&!option.trim().isEmpty()&&!option.equalsIgnoreCase("Examine")){action=option;break;}}if(action==null){message="No usable wall click action exposed; observer continues.";return false;}if(!ui.claim()){retrySoon=true;message="Wall interaction waits for the current interface action.";return true;}if(wall instanceof NPC){NPC npc=(NPC)wall;if(npc.hasAction(action))npc.interact(action);else{message="Configured wall action unavailable";return false;}}else{TileObject object=(TileObject)wall;if(object.hasAction(action))object.interact(action);else{message="Configured wall action unavailable";return false;}}return true;}
    if(!ui.ready(net.unethicalite.api.widgets.Tab.MAGIC)){message="Opening Magic before the configured Lunar action.";return true;}
    SpellBook.Lunar spell=SpellBook.Lunar.valueOf(config.lunarSpell);if(!spell.canCast()){message="Configured Lunar wall spell unavailable (spellbook, level or runes); observer continues.";return false;}
    if(config.wallMode==WallMode.CAST_LUNAR)Magic.cast(spell);else if(wall instanceof NPC)Magic.cast(spell,(NPC)wall);else Magic.cast(spell,(TileObject)wall);return true;
   default:return false;
  }
 }
 private int poolPlanHash;private List<OlmSpecials.Cell> poolRoute=Collections.emptyList();
 boolean canWeaveHead(OlmSpecials.View view,boolean attackDue){
  if(lastMove!=null&&!view.player.equals(lastMove)||view.stopAttack||!view.walls.isEmpty()||view.uncertainElectric.contains(view.player))return false;
  boolean arrived=lastMove!=null&&view.player.equals(lastMove)&&attackDue;
  boolean shotPending=client.getGameCycle()<headShotUntil&&view.player.equals(headShotAnchor);
  if(!arrived&&!shotPending)return false;
  for(OlmSpecials.Hazard hazard:view.hazards)if(hazard.contains(view.player)){
   if(remainingTickMs<100)return false;
   if(hazard.kind!=OlmSpecials.HazardKind.FALLING||hazard.cell.equals(view.player))return false;
   boolean future=false;for(Map.Entry<Projectile,OlmSpecials.Hazard> projectile:falling.entrySet())if(projectile.getValue().cell.equals(hazard.cell)&&projectile.getKey().getEndCycle()-client.getGameCycle()>=8){future=true;break;}if(!future)return false;
  }return true;
 }
 boolean handleHealingPools(OlmStrategy.Phase phase,OlmSpecials.View view,Set<OlmSpecials.Cell> visible){
  headAttackWindow=false;poolLandings.entrySet().removeIf(e->e.getKey().getEndCycle()<client.getGameCycle()-2);poolPriority=false;inHealingPool=false;
  if(!config.healingPoolsEnabled||phase!=OlmStrategy.Phase.HEAD){poolDuty.cancel();poolLandings.clear();return false;}
  poolDuty.observe(view.tick,view.player,visible);Set<OlmSpecials.Cell> candidates=poolDuty.locations();if(candidates.isEmpty())candidates.addAll(poolLandings.values());
  if(candidates.isEmpty()){poolMoveTarget=null;poolRoute=Collections.emptyList();return false;}
  poolPriority=true;inHealingPool=poolDuty.active()&&poolDuty.locations().contains(view.player);travel.reset();crossing=null;lastMove=null;urgentMovementPending=!inHealingPool;lastKind=inHealingPool?OlmSpecials.Kind.HOLD:OlmSpecials.Kind.MOVE;
  message="HEALING POOL FIRST: "+poolDuty.occupied()+" / 8 required observed ticks in the 10-tick window; "+(inHealingPool?"holding until the pool disappears.":"moving to a pool before other actions.");
  if(inHealingPool&&OlmHeadCombat.poolRisk(view.player,view)==0)return holdPoolForHead(view);
  if(!inHealingPool&&candidates.contains(view.player)){message="HEALING POOL FIRST: on the projectile landing tile, waiting for the visible pool before counting occupancy.";return true;}
  int hazardHash=view.player.hashCode()+candidates.hashCode();for(OlmSpecials.Hazard hazard:view.hazards)hazardHash+=31*hazard.cell.hashCode()+17*hazard.kind.ordinal()+hazard.radius;if(poolPlanTick!=view.tick||poolPlanHash!=hazardHash||poolMoveTarget==null||!candidates.contains(poolMoveTarget)){
   poolPlanTick=view.tick;poolPlanHash=hazardHash;poolRoute=Collections.emptyList();Map<OlmSpecials.Cell,List<OlmSpecials.Cell>> routes=new LinkedHashMap<>();Map<OlmSpecials.Cell,Integer> steps=new LinkedHashMap<>();List<OlmSpecials.Cell> ordered=new ArrayList<>(candidates);ordered.sort(Comparator.comparingInt(view.player::distance).thenComparingInt(c->c.x).thenComparingInt(c->c.y));
   for(OlmSpecials.Cell destination:ordered){List<OlmSpecials.Cell> route=OlmTravel.path(view.player,c->c.equals(destination),(from,to)->{WorldPoint point=new WorldPoint(to.x,to.y,to.plane);if(!inside(point)||!new WorldArea(new WorldPoint(from.x,from.y,from.plane),1,1).canTravelInDirection(client,to.x-from.x,to.y-from.y))return false;for(NPC n:client.getNpcs())if(n.getWorldArea()!=null&&n.getWorldArea().contains(point))return false;return true;});if(!route.isEmpty()){routes.put(destination,route);steps.put(destination,route.size()-1);}}OlmSpecials.Cell selected=OlmHeadCombat.choosePool(steps,view,poolDuty,client.getVarpValue(173)!=0&&client.getEnergy()>0);if(selected!=null){poolRoute=routes.get(selected);poolMoveTarget=selected;poolDuty.select(selected);}
  }
  if(inHealingPool&&(poolRoute.isEmpty()||view.player.equals(poolMoveTarget)))return holdPoolForHead(view);if(poolRoute.isEmpty()){message+=" No collision-valid route currently visible; keep scanning.";return true;}
  if(client.getGameCycle()-poolMoveCycle<10){urgentMovementPending=false;return true;}
  int nearest=0;for(int i=1;i<poolRoute.size();i++)if(poolRoute.get(i).distance(view.player)<=poolRoute.get(nearest).distance(view.player))nearest=i;
  for(int i=poolRoute.size()-1;i>nearest;i--){OlmSpecials.Cell destination=poolRoute.get(i);WorldPoint point=new WorldPoint(destination.x,destination.y,destination.plane);if(VisibleCombatUi.groundPoint(client,point)==null)continue;if(ui.clickGround(client,point)){poolMoveCycle=client.getGameCycle();urgentMovementPending=false;moved=true;moveEpoch++;action("POOL_ENTER",destination);}else retrySoon=true;return true;}
  if(poolTravel.mayZoom(client.getGameCycle())&&ui.zoomOut(client))poolTravel.zoomed(client.getGameCycle());retrySoon=true;message+=" Pool off-screen: zooming out through the viewport.";return true;
 }
 private boolean holdPoolForHead(OlmSpecials.View view){
  urgentMovementPending=false;headAttackWindow=!client.getLocalPlayer().isMoving();message+=" In-place ranged attacks allowed; no automatic chase.";
  if(client.getLocalPlayer().isMoving()&&client.getGameCycle()-poolMoveCycle>=10&&ui.clickGround(client,new WorldPoint(view.player.x,view.player.y,view.player.plane))){poolMoveCycle=client.getGameCycle();action("POOL_HOLD_CANCEL_OLD_ROUTE",view.player);}return true;
 }
 private boolean travel(OlmSpecials.View view,NPC target,int range,WorldPoint approach){
  String goal=target!=null?target.getIndex()+":"+target.getWorldLocation()+":"+range:String.valueOf(approach);travel.goal(goal);lastKind=OlmSpecials.Kind.MOVE;
  if(travel.walking(view.player,view.tick)){message="Travelling to the hand with one destination click; monitoring for hazards.";return true;}
  if(travel.waitingForZoom(client.getGameCycle())){message="Waiting for normal camera zoom before the destination click.";return true;}
  List<OlmSpecials.Cell> route=OlmTravel.path(view.player,c->target==null?c.equals(view.target):target.getWorldArea().distanceTo(new WorldPoint(c.x,c.y,c.plane))<=range,(a,b)->{
   WorldPoint to=new WorldPoint(b.x,b.y,b.plane);if(!inside(to)||!new WorldArea(new WorldPoint(a.x,a.y,a.plane),1,1).canTravelInDirection(client,b.x-a.x,b.y-a.y))return false;
   for(NPC npc:client.getNpcs())if(npc.getWorldArea()!=null&&npc.getWorldArea().contains(to))return false;return true;
  });
  if(route.size()<2)return false;
  OlmSpecials.Cell destination=route.get(route.size()-1);WorldPoint tile=new WorldPoint(destination.x,destination.y,destination.plane);
  if(VisibleCombatUi.groundPoint(client,tile)==null&&!travel.zoomExhausted()){
   if(travel.mayZoom(client.getGameCycle())&&ui.zoomOut(client))travel.zoomed(client.getGameCycle());message="Zooming out through the viewport to reach the other hand with fewer clicks.";return true;
  }
  for(int i=route.size()-1;i>0;i--){OlmSpecials.Cell c=route.get(i);WorldPoint point=new WorldPoint(c.x,c.y,c.plane);if(VisibleCombatUi.groundPoint(client,point)==null)continue;
   if(ui.clickGround(client,point)){action("TRAVEL",c);travel.sent(view.player,c,view.tick);travelClicks++;moved=true;moveEpoch++;message="Requested one visible destination click toward the hand; travel clicks "+travelClicks+".";}else message="Hand travel waits for the current interface action.";return true;
  }return false;
 }
 static boolean electricCrossing(OlmSpecials.View view,OlmSpecials.Cell from,OlmSpecials.Cell to){
  if(!OlmSpecials.electricProgress(view,from,to))return true;
  for(OlmSpecials.Hazard h:view.hazards)if(h.kind==OlmSpecials.HazardKind.ELECTRIC){
   if(h.contains(to)&&(!h.contains(from)||h.cell.distance(to)<h.cell.distance(from)))return true;
   if(from.x!=to.x&&from.y!=to.y&&!h.contains(from)&&(h.contains(new OlmSpecials.Cell(from.x,to.y,from.plane))||h.contains(new OlmSpecials.Cell(to.x,from.y,from.plane))))return true;
  }return false;
 }
 static boolean awaitingStep(OlmSpecials.Cell current,OlmSpecials.Cell destination,int tick,int requestedTick){return destination!=null&&!destination.equals(current)&&tick>=requestedTick&&tick-requestedTick<2;}
 static boolean pendingDodgeSafe(OlmSpecials.View view,OlmSpecials.Cell destination){return destination!=null&&view.player.distance(destination)==1&&OlmSpecials.escapeStep(view,view.player,destination)&&!electricCrossing(view,view.player,destination);}
 private boolean inside(WorldPoint point){if(profile==null||point.getPlane()!=client.getPlane())return false;if(profile.provisionalTest){Player self=client.getLocalPlayer();return self!=null&&self.getWorldLocation().distanceTo(point)<=24;}LocalPoint local=LocalPoint.fromWorld(client,point);if(local==null)return false;WorldPoint template=WorldPoint.fromLocalInstance(client,local);return template.getPlane()==profile.plane&&profile.olmRegions.contains(template.getRegionID())&&template.getX()>=profile.roomMinX&&template.getX()<=profile.roomMaxX&&template.getY()>=profile.roomMinY&&template.getY()<=profile.roomMaxY;}
 private static OlmSpecials.Cell cell(WorldPoint point){return new OlmSpecials.Cell(point.getX(),point.getY(),point.getPlane());}
}


