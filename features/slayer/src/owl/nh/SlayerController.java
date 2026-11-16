package owl.nh;
import java.awt.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.*;
/** Basic local NPC fighter. No routes, gear changes, learned model or persisted account data. */
final class SlayerController {
 enum MobMode { STANDARD, TORMENTED_DEMONS }
 volatile MobMode mobMode=MobMode.STANDARD;volatile boolean automaticProfile;final SlayerSurvival survival=new SlayerSurvival();final SlayerBoost boost=new SlayerBoost();private int supplyId=-1;
 volatile Prayer offensivePrayer,defensivePrayer;private int lastPrayerRequest=-1000;private Prayer requestedPrayer;private int teleportSelectFailures;
 final CombatRuntime runtime;final SlayerCounter counter=new SlayerCounter();final SlayerTaskLoop taskLoop=new SlayerTaskLoop();volatile boolean picking;volatile String status="Select an NPC, choose a kill count, then Start.",selected="None";private int type=-1,lastKilled=-1,lastRequest=-1000,retries;private String kindName,lastKilledName;private NPC target,manualTarget,hover,preview;private int progressTick=-1000,lastHealth=-1;private int manualTick=-1000;private WorldPoint anchor;private final Map<NPC,Integer> retryAfter=new IdentityHashMap<>();private final LinkedHashSet<String> superiorNames=new LinkedHashSet<>();private boolean superiorPending;private int superiorSignalTick=-1000;private NPC superiorTarget;private int faultTick=-1,faultTicks,profiledNpcId=-1;private SlayerNpcProfile.Result npcProfile;private CombatCore.Style activeGearStyle=CombatCore.Style.UNKNOWN;private final Map<Integer,Integer> pendingGear=new LinkedHashMap<>();private final OlmGearBatch gearBatch=new OlmGearBatch();private boolean gearGateReleased,gearFallbackReported;private int gearGateStarted=-1;private final LinkedHashSet<LootMark> lootQueue=new LinkedHashSet<>();private final Set<String> lootTraceSeen=new HashSet<>();private int lootRequestTick=-1000,lootEatTick=-1000,lootItemId=-1,lootBefore=-1,lootPriorityUntil=-1000,noTaskTicks,homeMethodAttempts;private WorldPoint lootTile;private boolean finishingLoot;private String finishStatus="";final Overlay overlay;private SlayerPanel window;
 private static final class LootMark {final int id;final WorldPoint tile;LootMark(int id,WorldPoint tile){this.id=id;this.tile=tile;}public boolean equals(Object other){if(!(other instanceof LootMark))return false;LootMark mark=(LootMark)other;return id==mark.id&&Objects.equals(tile,mark.tile);}public int hashCode(){return 31*id+(tile==null?0:tile.hashCode());}}
 SlayerController(CombatRuntime runtime){this.runtime=runtime;loadSuperiorNames();overlay=new Overlay(){ {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);}public Dimension render(Graphics2D g){NPC n=picking?hover:counter.running?target:preview;if(n!=null&&!n.isDead()&&n.getHealthRatio()!=0&&n.getConvexHull()!=null){g.setColor(new Color(0,255,150,65));g.fill(n.getConvexHull());g.setColor(Color.GREEN);g.draw(n.getConvexHull());}return null;}};}
 boolean running(){return counter.running||taskLoop.running()||finishingLoot;}
 boolean settlingLoot(){return finishingLoot;}
 void select(NPC npc){if(npc==null||!npc.hasAction("Attack"))throw new IllegalArgumentException("Choose an NPC with an Attack action");String name=normalise(npc.getName());if(name.isEmpty())throw new IllegalArgumentException("The selected NPC has no usable name");preview=npc;type=npc.getId();kindName=name;selected=npc.getName()+" (all matching NPCs)";picking=false;hover=null;resolveNpcProfile(type);status="Selected every nearby "+npc.getName()+". Selection did not issue an attack. "+profileStatus();}
 void pick(){runtime.stop("Selecting Slayer NPC");picking=true;status="Hover an NPC to highlight it, then click it to select. F9 cancels.";}
 void lastKilled(){if(lastKilled<0)throw new IllegalStateException("No NPC death observed after your own attack in this session yet");runtime.stop("Selecting last killed NPC");type=lastKilled;kindName=normalise(lastKilledName);selected=lastKilledName+" (all matching NPCs)";resolveNpcProfile(type);status="Selected all NPCs matching the last kill: "+selected+". "+profileStatus();}
 void start(int kills){if(mobMode==MobMode.TORMENTED_DEMONS)throw new IllegalStateException("Tormented demons profile needs Orion attack/projectile, special and equipment mappings before it can run. Standard mode has no demon-specific defenses.");if(type<0||kindName==null||kindName.isEmpty())throw new IllegalStateException("Select an NPC first");Client c=runtime.client;if(c.getGameState()!=GameState.LOGGED_IN||c.getLocalPlayer()==null)throw new IllegalStateException("Log in and stand near the NPCs first");if(automaticProfile){resolveNpcProfile(type);applyNpcProfile();}runtime.stop("Starting Slayer fighter");anchor=c.getLocalPlayer().getWorldLocation();survival.reset();boost.reset();resetGear();supplyId=-1;requestedPrayer=null;lastPrayerRequest=-1000;target=null;lastRequest=-1000;retries=0;retryAfter.clear();faultTick=-1;faultTicks=0;noTaskTicks=0;finishingLoot=false;int remaining=helmetRemaining(),goal=selectedGoal(kills,remaining);counter.start(type,goal);boolean taskGoal=kills==1&&remaining>0;status="Running: "+selected+". "+(taskGoal?"The amount is 1, so the equipped Slayer helmet's "+remaining+" remaining kills become the goal.":"Using the entered goal of "+kills+" kills.")+" Manual selected-NPC mode will stop locally and will never enter the Nieve teleport loop. HP 60, prayer 40 and combat boosts are maintained when supplies are available. "+profileStatus();diagnostic("START");}
 static int selectedGoal(int requested,int helmetRemaining){return requested==1&&helmetRemaining>0?helmetRemaining:requested;}
 void startTaskLoop(){Client c=runtime.client;if(c.getGameState()!=GameState.LOGGED_IN||c.getLocalPlayer()==null)throw new IllegalStateException("Log in with the charged Slayer helmet equipped first");int remaining=helmetRemaining();String known=kindName,chosen=selected;runtime.stop("Starting Nieve Slayer task loop");automaticProfile=true;taskLoop.start();counter.stop();survival.reset();boost.reset();supplyId=-1;requestedPrayer=null;lastPrayerRequest=-1000;target=null;anchor=null;lastRequest=-1000;retries=0;noTaskTicks=0;finishingLoot=false;if(remaining>0&&known!=null&&!known.isEmpty()){taskLoop.acceptKnown(chosen,known,remaining,SlayerTaskLoop.Stage.GLIDER,c.getTickCount());selected=known+" (current task)";status="Read "+remaining+" remaining directly from the equipped Slayer helmet; finding the Gnome glider. Automatic NPC-stat gear and prayer selection is enabled for this loop.";}else if(nearestNpc("nieve",null)!=null){taskLoop.stage(SlayerTaskLoop.Stage.ASSIGNMENT,c.getTickCount());selected="Nieve assignment pending";status=(remaining>0?"The helmet shows "+remaining+" remaining, so asking Nieve to identify the current task.":"No active task was found, so requesting a fresh assignment from Nieve.")+" Automatic NPC-stat gear and prayer selection is enabled for this loop.";}else beginHome(remaining>0?"The current task name is unknown away from Nieve":"No active Slayer task was detected away from Nieve");}
 void startSelectedTask(){if(type<0||kindName==null||kindName.isEmpty())throw new IllegalStateException("Select an NPC first");Client c=runtime.client;if(c.getGameState()!=GameState.LOGGED_IN||c.getLocalPlayer()==null)throw new IllegalStateException("Log in with the charged Slayer helmet equipped first");int remaining=helmetRemaining();String chosen=selected,known=kindName;runtime.stop("Starting selected Slayer task");if(remaining<1){beginHome("The equipped Slayer helmet does not show an active task");return;}taskLoop.startSelected();taskLoop.acceptKnown(chosen,known,remaining,SlayerTaskLoop.Stage.HUNT,c.getTickCount());counter.stop();survival.reset();boost.reset();supplyId=-1;requestedPrayer=null;lastPrayerRequest=-1000;target=null;anchor=null;lastRequest=-1000;retries=0;noTaskTicks=0;finishingLoot=false;selected=chosen;status="Read "+remaining+" remaining directly from the equipped Slayer helmet. Finishing every matching "+known+".";}
 void stop(String reason){boolean was=counter.running||taskLoop.running()||finishingLoot||picking;if(was)diagnostic("STOP "+safe(reason));counter.stop();taskLoop.stop();finishingLoot=false;picking=false;hover=null;target=null;resetSuperior();if(was)status="Stopped: "+reason+". "+progress();}
 private void resetCompletedTask(){counter.stop();survival.reset();boost.reset();resetGear();runtime.combatUi.stopUi();picking=false;finishingLoot=false;finishStatus="";lootPriorityUntil=-1000;noTaskTicks=0;homeMethodAttempts=0;supplyId=-1;requestedPrayer=null;lastPrayerRequest=-1000;type=-1;kindName=null;lastKilled=-1;lastKilledName=null;npcProfile=null;target=null;manualTarget=null;hover=null;preview=null;anchor=null;lastRequest=-1000;retries=0;progressTick=-1000;lastHealth=-1;manualTick=-1000;selected="None";}
 String progress(){if(taskLoop.running()){String task=taskLoop.task==null?"waiting for assignment":taskLoop.task.display+" ("+counter.kills+" / "+taskLoop.task.remaining+")";return "Nieve loop: "+task+" | "+taskLoop.stage.name().replace('_',' ').toLowerCase(Locale.ROOT);}if(counter.goal<1)return "No run started";return counter.kills+" / "+counter.goal+" target deaths";}
 void chat(ChatMessageType messageType,String message){if(messageType==null||message==null)return;if(messageType!=ChatMessageType.GAMEMESSAGE&&messageType!=ChatMessageType.ENGINE&&messageType!=ChatMessageType.DIALOG)return;if(counter.running&&superiorAnnouncement(message)){superiorPending=true;superiorSignalTick=runtime.client.getTickCount();superiorTarget=findSuperiorCandidate();status=superiorNames.isEmpty()?"Superior announcement observed, but the superior-name list is empty. Add its exact NPC name in the Slayer window.":"Superior announcement observed. Finishing the current target, then prioritising the first visible NPC matching the saved superior list.";diagnostic("SUPERIOR_SIGNAL");}if(running()&&SlayerTaskLoop.noTask(message)){beginHome("Orion reported that no Slayer task is active");return;}if(!taskLoop.running()||taskLoop.stage!=SlayerTaskLoop.Stage.WAIT_ASSIGNMENT)return;if(taskLoop.accept(message)){acceptTaskStatus("Orion's assignment message");}else if(taskLoop.observeTarget(message)&&taskLoop.acceptObservedCount(helmetRemaining()))acceptTaskStatus("Nieve's task name and the equipped helmet count");}
 static boolean superiorAnnouncement(String message){return normalise(SlayerTaskLoop.clean(message)).contains("a superior foe has appeared");}
 void menu(MenuOptionClicked e){if(!picking||e.isConsumed()||e.getMenuAction()==null||!e.getMenuAction().name().contains("NPC"))return;for(NPC npc:runtime.client.getNpcs())if(npc.getIndex()==e.getId()&&npc.hasAction("Attack")){e.consume();select(npc);return;}}
 void animation(Actor actor){Client c=runtime.client;if(actor!=c.getLocalPlayer()||actor==null||actor.getAnimation()<0||!(actor.getInteracting() instanceof NPC))return;NPC n=(NPC)actor.getInteracting();if(!n.isDead()&&n.getHealthRatio()!=0&&n.hasAction("Attack")){manualTarget=n;manualTick=c.getTickCount();if(counter.running&&selectedKind(n)){counter.attack(n,type);progressTick=c.getTickCount();if(target==null)target=n;}}}
 void died(Actor actor){if(actor==runtime.client.getLocalPlayer()){stop("Player died");return;}if(!(actor instanceof NPC))return;NPC npc=(NPC)actor;if(npc==manualTarget&&runtime.client.getTickCount()-manualTick<=50){lastKilled=npc.getId();lastKilledName=npc.getName();manualTarget=null;}if(npc==superiorTarget){int tick=runtime.client.getTickCount();superiorPending=false;superiorTarget=null;target=null;lootPriorityUntil=tick+4;resetAutomaticGear();requestedPrayer=null;lastPrayerRequest=-1000;status="Superior defeated without consuming a task-kill count; checking its loot before returning to "+selected+". "+progress();diagnostic("SUPERIOR_DEFEATED "+safe(npc.getName()));return;}boolean was=counter.running;if(occupied(npc))counter.contested(npc);if(selectedKind(npc)&&counter.died(npc,type)){int tick=runtime.client.getTickCount();lootPriorityUntil=tick+4;Player self=runtime.client.getLocalPlayer();if(counter.running&&self!=null)anchor=self.getWorldLocation();diagnostic(counter.running?"KILL_CONTINUE":"KILL_GOAL");if(taskLoop.running()&&!counter.running){homeMethodAttempts=0;taskLoop.stage(SlayerTaskLoop.Stage.HOME,tick);status="Assignment complete. Waiting for purple-or-higher loot before returning home"+(taskLoop.selectedOnly?" and then stopping.":" before requesting the next task.");}else if(!counter.running){finishingLoot=true;finishStatus="Requested count completed normally";status="Final requested kill confirmed; checking for purple-or-higher loot before stopping.";}else status=progress()+"; confirmed kill; purple-or-higher loot now has priority over the next NPC.";if(npc==target)target=null;}else if(was&&npc==target){target=null;status="Target died without an unambiguous observed local attack; not counted. "+progress();diagnostic("DEATH_NOT_COUNTED");}}
 void spawned(NPC npc){counter.spawned(npc);if(counter.running&&listedSuperior(npc)){superiorPending=true;superiorTarget=npc;superiorSignalTick=runtime.client.getTickCount();diagnostic("SUPERIOR_LIST_SPAWN "+safe(npc.getName()));}}
 private void resetSuperior(){superiorPending=false;superiorSignalTick=-1000;superiorTarget=null;}
 private boolean listedSuperior(NPC npc){return npc!=null&&npc.hasAction("Attack")&&superiorNames.contains(normalise(npc.getName()));}
 private NPC findSuperiorCandidate(){Client c=runtime.client;Player self=c.getLocalPlayer();if(self==null||superiorNames.isEmpty())return null;NPC best=null;int bestDistance=Integer.MAX_VALUE;for(NPC npc:c.getNpcs()){if(!listedSuperior(npc)||npc.isDead()||npc.getHealthRatio()==0||occupied(npc))continue;WorldPoint point=npc.getWorldLocation();if(point==null||point.getPlane()!=self.getWorldLocation().getPlane())continue;int distance=point.distanceTo(self.getWorldLocation());if(distance<=24&&distance<bestDistance){best=npc;bestDistance=distance;}}return best;}
 String superiorNamesText(){return String.join(System.lineSeparator(),superiorNames);}
 void updateSuperiorNames(String text){LinkedHashSet<String> parsed=parseSuperiorNames(text);superiorNames.clear();superiorNames.addAll(parsed);saveSuperiorNames();if(counter.running){NPC listed=findSuperiorCandidate();if(listed!=null){superiorPending=true;superiorTarget=listed;superiorSignalTick=runtime.client.getTickCount();}}status="Saved "+superiorNames.size()+" exact superior NPC name"+(superiorNames.size()==1?"":"s")+". Listed NPCs are prioritised after the current kill.";}
 static LinkedHashSet<String> parseSuperiorNames(String text){LinkedHashSet<String> names=new LinkedHashSet<>();if(text==null)return names;for(String value:text.split("[,;\\r\\n]+")){String name=normalise(value);if(!name.isEmpty())names.add(name);}return names;}
 void replaceSuperiorNamesForSession(String text){superiorNames.clear();superiorNames.addAll(parseSuperiorNames(text));}
 private Path superiorNamesFile(){return runtime.projectRoot().toPath().resolve("runtime/slayer-superior-names.txt");}
 private void loadSuperiorNames(){try{Path file=superiorNamesFile();if(Files.isRegularFile(file))superiorNames.addAll(parseSuperiorNames(Files.readString(file,StandardCharsets.UTF_8)));}catch(RuntimeException|java.io.IOException ignored){}}
 private void saveSuperiorNames(){try{Path file=superiorNamesFile();Files.createDirectories(file.getParent());Path temporary=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temporary,superiorNamesText()+(superiorNames.isEmpty()?"":System.lineSeparator()),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}}catch(RuntimeException|java.io.IOException error){status="Could not save the superior-name list: "+safe(error.getMessage());}}
 private boolean occupied(NPC n){for(Player p:runtime.client.getPlayers())if(p!=runtime.client.getLocalPlayer()&&p.getInteracting()==n)return true;return false;}
 private void beginHome(String reason){int tick=runtime.client.getTickCount();counter.stop();target=null;manualTarget=null;retryAfter.clear();resetSuperior();finishingLoot=false;noTaskTicks=0;homeMethodAttempts=0;lootPriorityUntil=Math.max(lootPriorityUntil,tick+2);if(!taskLoop.running())taskLoop.startSelected();taskLoop.stage(SlayerTaskLoop.Stage.HOME,tick);status=reason+". Combat output stopped; collecting purple-or-higher loot before returning home.";diagnostic("NO_TASK_HOME "+safe(reason));}
 void pulse(){try{pulseImpl();faultTicks=0;}catch(RuntimeException error){int tick=runtime.client.getTickCount();if(tick!=faultTick){faultTick=tick;faultTicks++;}String detail=error.getClass().getSimpleName()+": "+safe(error.getMessage());status="Slayer runtime fault "+faultTicks+"/5; retrying next tick. "+detail+". "+progress();diagnostic("FAULT "+detail);if(faultTicks>=5)stop("Repeated Slayer runtime fault: "+detail);}}
 private void pulseImpl(){Client c=runtime.client;Player self=c.getLocalPlayer();if(picking){hover=null;MenuEntry[] entries=c.getMenuEntries();if(entries!=null)for(int i=entries.length-1;i>=0;i--){NPC n=entries[i].getNpc();if(n!=null&&n.hasAction("Attack")){hover=n;break;}}}if(!running())return;if(c.getGameState()!=GameState.LOGGED_IN||self==null){stop("Logged out or disconnected");return;}if(self.isDead()||c.getBoostedSkillLevel(Skill.HITPOINTS)<=0){stop("Player died");return;}if(!runtime.ticks.fresh()||c.getCanvas()==null||!c.getCanvas().isFocusOwner()){status="Paused: focus the game and wait for fresh ticks. "+progress();return;}if(taskLoop.running()&&!counter.running){taskPulse(c,self);return;}if(finishingLoot){if(collectHighlightedLoot())return;if(c.getTickCount()<=lootPriorityUntil){status="Final kill confirmed; waiting briefly for purple-or-higher loot to appear.";return;}finishingLoot=false;status=finishStatus+"; purple-or-higher loot check completed and Slayer stopped.";return;}if(taskLoop.running()&&counter.running){if(helmetRemaining()<1)noTaskTicks++;else noTaskTicks=0;if(noTaskTicks>=2){beginHome("The equipped Slayer helmet confirms that no Slayer task is active");taskPulse(c,self);return;}}if(anchor==null)anchor=self.getWorldLocation();if(self.getWorldLocation().getPlane()!=anchor.getPlane()||target==null&&self.getWorldLocation().distanceTo(anchor)>32){stop("Left the selected hunting area");return;}
   if(superiorTarget!=null&&(superiorTarget.isDead()||superiorTarget.getHealthRatio()==0||!c.getNpcs().contains(superiorTarget)))superiorTarget=null;NPC listedSuperior=findSuperiorCandidate();if(listedSuperior!=null){superiorPending=true;superiorTarget=listedSuperior;}if(superiorPending&&superiorTarget==null&&c.getTickCount()-superiorSignalTick>40){superiorPending=false;diagnostic("SUPERIOR_NOT_FOUND");}
   boolean selectedKillActive=target!=null&&selectedKind(target)&&!target.isDead()&&target.getHealthRatio()!=0&&(counter.engaged(target)||self.getInteracting()==target);
   if(superiorPending&&superiorTarget!=null&&!selectedKillActive&&target!=superiorTarget){target=superiorTarget;lastRequest=-1000;retries=0;progressTick=c.getTickCount();lastHealth=target.getHealthRatio();status="Current kill is complete; prioritising superior "+target.getName()+" before the next "+selected+".";diagnostic("SUPERIOR_TARGET "+safe(target.getName()));}
   queueHighlightedLoot();
   if(preparePrayers())return;
  if(restoreSupplies())return;
   if(maintainCombatBoost())return;
   boolean currentKill=target!=null&&!target.isDead()&&target.getHealthRatio()!=0&&(counter.engaged(target)||target==superiorTarget);if(!currentKill&&collectHighlightedLoot())return;
   if(target==null&&c.getTickCount()<=lootPriorityUntil){status="Confirmed kill; holding target acquisition for purple-or-higher loot to appear. "+progress();return;}
  if(target!=null){boolean present=false;for(NPC n:c.getNpcs())if(n==target){present=true;break;}if(!present||target.isDead()||target.getHealthRatio()==0){target=null;}else {if(occupied(target))counter.contested(target);if(target.getHealthRatio()!=lastHealth){lastHealth=target.getHealthRatio();progressTick=c.getTickCount();}if(self.getInteracting()==target){if(c.getTickCount()-progressTick>=20){retryAfter.put(target,c.getTickCount()+20);target=null;retries=0;status="Target made no observed combat progress; trying another matching NPC. "+progress();diagnostic("TARGET_STALE");return;}if(!continueAutomaticGearDuringCombat(target))return;status="Fighting "+selected+". "+progress()+(lootQueue.isEmpty()?"":" | queued purple-or-higher loot: "+lootQueue.size());return;}if(self.isMoving()||c.getTickCount()-lastRequest<3)return;if(retries>=3){retryAfter.put(target,c.getTickCount()+20);target=null;retries=0;status="Attack was not confirmed; trying another matching NPC instead of stopping the run. "+progress();diagnostic("ATTACK_UNCONFIRMED");return;}}}
  if(target==null&&superiorPending&&superiorTarget==null&&c.getTickCount()-superiorSignalTick<=10){status="Superior announcement observed; holding the next task target briefly while its spawned NPC is identified.";return;}
  if(target==null){int distance=17;retryAfter.entrySet().removeIf(e->e.getValue()<=c.getTickCount());for(NPC n:c.getNpcs()){if(!selectedKind(n)||n.isDead()||n.getHealthRatio()==0||!n.hasAction("Attack")||occupied(n)||retryAfter.containsKey(n))continue;int d=n.getWorldLocation().distanceTo(self.getWorldLocation());if(d>16)continue;if(d<distance){distance=d;target=n;}}retries=0;progressTick=c.getTickCount();lastHealth=target==null?-1:target.getHealthRatio();if(target==null){status="Waiting for a free "+selected+" within 16 tiles of the player. "+progress();return;}}
  if(!prepareTargetProfile(target))return;
  if(runtime.combatUi.claim()){target.interact("Attack");lastRequest=c.getTickCount();retries++;status="Attack requested after scanning the target's defence and outgoing attack style; waiting for observed combat. "+progress();diagnostic("ATTACK_REQUEST "+safe(target.getName()));}
 }
 private void taskPulse(Client c,Player self){
  int tick=c.getTickCount();
  if(handleNieveDialogue(c,tick))return;
   if(taskLoop.stage==SlayerTaskLoop.Stage.GLIDER&&teleportsOpen(c)){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_TELEPORTS,tick);status="The Teleports interface is already open; confirming it remains open for two game ticks before Search.";}
  switch(taskLoop.stage){
   case ASSIGNMENT:{
    NPC nieve=nearestNpc("nieve","Assignment");if(nieve==null){status="Waiting near Nieve. Her Assignment action is not currently available.";return;}
    if(c.isMenuOpen()){
     if(VisibleCombatUi.hasNpcMenu(c,nieve,"Assignment")&&runtime.combatUi.chooseNpcMenu(c,nieve,"Assignment")){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_ASSIGNMENT,tick);status="Assignment requested from Nieve; waiting for its task-name message and the helmet count.";}else status="Waiting for Nieve's visible Assignment menu entry.";return;
    }
    if(taskLoop.due(tick,3)&&runtime.combatUi.rightClickNpc(c,nieve)){taskLoop.requested(tick);status="Opening Nieve's menu for the exact Assignment action.";}return;
   }
   case WAIT_ASSIGNMENT:
     if(taskLoop.pendingTarget!=null&&taskLoop.acceptObservedCount(helmetRemaining())){acceptTaskStatus("Nieve's task name and the equipped helmet count");return;}if(taskLoop.due(tick,40))stop("Nieve's assignment was not observed in either the chat stream or the visible dialogue");else status="Waiting for Nieve's assignment message or visible dialogue.";return;
   case GLIDER:{
    if(c.isMenuOpen()){runtime.combatUi.escape(c);status="Closing a menu before using the Gnome glider.";return;}
    NPC gliderNpc=nearestNpc("gnome glider","Travel");net.runelite.api.TileObject gliderObject=nearestObject("gnome glider","Travel");
    if(gliderNpc==null&&gliderObject==null){status="Task confirmed, but a loaded Gnome glider with Travel is not visible. Turn toward it or move it into the loaded scene.";return;}
    if(!taskLoop.due(tick,3)||!runtime.combatUi.claim())return;if(gliderNpc!=null)gliderNpc.interact("Travel");else gliderObject.interact("Travel");taskLoop.stage(SlayerTaskLoop.Stage.WAIT_TELEPORTS,tick);status="Travel selected on the Gnome glider; waiting for Teleports.";return;
   }
    case WAIT_TELEPORTS:
     boolean teleportsVisible=teleportsOpen(c);if(taskLoop.stable(teleportsVisible,tick,2)){taskLoop.stage(SlayerTaskLoop.Stage.SEARCH,tick);status="Teleports and its Search control remained visible for two full game ticks; the Search step is confirmed.";}else if(teleportsVisible){status="Teleports observed; holding for two game ticks to confirm the complete interface before Search.";}else if(taskLoop.due(tick,30)){taskLoop.stage(SlayerTaskLoop.Stage.GLIDER,tick);status="Teleports was not confirmed; retrying only the glider step.";}return;
   case SEARCH:{
     Widget search=teleportWidget(c,"Search",true);boolean searchReady=teleportsOpen(c)&&search!=null&&VisibleCombatUi.effectivelyVisible(search);if(!taskLoop.stable(searchReady,tick,2)){if(taskLoop.due(tick,12)){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_TELEPORTS,tick);status="Search was not confirmed visible; retrying the current Teleports confirmation step.";}else status="Confirming the visible Search control for two game ticks before clicking it.";return;}
     if(c.isMenuOpen()){runtime.combatUi.escape(c);status="Closing an unrelated menu; the confirmed Search step will then retry.";return;}if(runtime.combatUi.clickWidget(c,search)){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_SEARCH_PROMPT,tick);status="Search clicked once; waiting for the chatbox input to be visibly confirmed before typing.";}return;
   }
    case WAIT_SEARCH_PROMPT:{
     boolean inputOpen=net.unethicalite.api.widgets.Dialog.isEnterInputOpen();if(taskLoop.stable(inputOpen,tick,2)){if(taskLoop.due(tick,2)&&runtime.combatUi.typeText(c,taskLoop.task.search,true)){teleportSelectFailures=0;taskLoop.stage(SlayerTaskLoop.Stage.WAIT_RESULTS,tick);status="Search prompt stayed open for two game ticks; submitted "+taskLoop.task.search+" and now allowing the filtered result to settle.";}return;}if(inputOpen){status="Search prompt observed; confirming it remains open for two game ticks before typing.";return;}if(taskLoop.due(tick,10)){taskLoop.stage(SlayerTaskLoop.Stage.SEARCH,tick);status="Search prompt was not confirmed; retrying only the Search click step.";}else status="Waiting for visible confirmation of the teleport search prompt.";return;
    }
   case WAIT_RESULTS:{
     if(c.isMenuOpen()){
      if(VisibleCombatUi.hasMenuOption(c,"Select")){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_RESULT_MENU,tick);status="The filtered result's Select menu is already open; preserving it and confirming the menu before clicking.";return;}
      if(taskLoop.due(tick,2)&&runtime.combatUi.escape(c))status="Closing an unrelated menu before retrying the filtered teleport result.";return;
     }
     Rectangle panel=teleportPanelBounds(c),selectTarget=teleportSelectTarget(panel);boolean filteredReady=selectTarget!=null&&teleportsOpen(c)&&!net.unethicalite.api.widgets.Dialog.isEnterInputOpen();
     // The filtered result is rendered by Orion but is not consistently published as a
     // widget.  Click the visible Select button at its position relative to the Teleports
     // panel instead of waiting forever for a dynamic widget that may never exist.
     if(teleportSelectFailures==0){
      if(taskLoop.stable(filteredReady,tick,2)&&taskLoop.due(tick,2)){
       if(runtime.combatUi.clickBounds(c,selectTarget)){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_DESTINATION,tick);status="The filtered view remained stable for two ticks; clicked its panel-relative visible Select button and is waiting for destination confirmation.";}else status="The visible Select position is confirmed; waiting for its normal left-click to be delivered.";return;
      }
      if(filteredReady){status="Filtered Teleports view observed; confirming the panel-relative Select button for two game ticks.";return;}
     }
     // A confirmed direct-click failure gets one context-menu fallback on the result card.
     // This is the same right-click -> Select path that works manually.
     Rectangle result=teleportResultCardTarget(panel);boolean fallbackReady=result!=null&&teleportsOpen(c)&&!net.unethicalite.api.widgets.Dialog.isEnterInputOpen();
     if(taskLoop.stable(fallbackReady,tick,2)&&taskLoop.due(tick,2)){
      if(runtime.combatUi.rightClickBounds(c,result)){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_RESULT_MENU,tick);status="The direct Select click was not confirmed; right-clicked the panel-relative first result card and is waiting for its Select menu entry.";}else status="Fallback result-card position confirmed; hovering it before the guarded right-click.";return;
     }
     if(fallbackReady){status="Confirming the panel-relative first result card before the context-menu fallback.";return;}
     if(taskLoop.due(tick,16)){taskLoop.stage(SlayerTaskLoop.Stage.SEARCH,tick);status="The filtered result was not confirmed; retrying the current Search step.";}else status="Waiting for the filtered teleport result to render.";return;
    }
    case WAIT_RESULT_MENU:{
     boolean selectVisible=c.isMenuOpen()&&VisibleCombatUi.hasMenuOption(c,"Select");if(taskLoop.stable(selectVisible,tick,2)){if(taskLoop.due(tick,2)&&runtime.combatUi.chooseMenuOption(c,"Select")){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_DESTINATION,tick);status="Select remained visible in the result menu for two game ticks and was clicked once; waiting for destination confirmation.";}else status="Select is confirmed in the context menu; waiting for its physical menu-row click to be delivered.";return;}if(selectVisible){status="Select appeared in the context menu; confirming it remains visible for two game ticks.";return;}if(c.isMenuOpen()&&taskLoop.due(tick,3)){if(runtime.combatUi.escape(c)){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_RESULTS,tick);status="The context menu did not contain Select; closed it and retrying the same fixed result step.";}return;}if(taskLoop.due(tick,8)){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_RESULTS,tick);status="The right-click menu was not confirmed; retrying the same fixed result step.";}else status="Waiting for the fixed-position context menu to expose Select.";return;
   }
    case WAIT_DESTINATION:{
     NPC arrived=nearestTaskNpc(self);boolean closeToTask=!teleportsOpen(c)&&arrived!=null&&arrived.getWorldLocation().distanceTo(self.getWorldLocation())<=24;if(taskLoop.stable(closeToTask,tick,2)){teleportSelectFailures=0;taskLoop.stage(SlayerTaskLoop.Stage.HUNT,tick);status="Teleport closure and nearby "+taskLoop.task.target+" NPCs were confirmed for two game ticks; combat setup may begin.";}else if(closeToTask){status="Matching task NPCs are visible nearby; confirming the destination for two game ticks before combat.";}else if(teleportsOpen(c)&&taskLoop.due(tick,8)){teleportSelectFailures++;taskLoop.stage(SlayerTaskLoop.Stage.WAIT_RESULTS,tick);status="The visible Select click did not close Teleports; switching to the panel-relative context-menu fallback.";}else status="Waiting for both Teleports to close and a matching task NPC to appear nearby; combat remains blocked.";return;
    }
   case HUNT:{
    if(helmetRemaining()<1){beginHome("The Slayer task ended before combat began");return;}NPC npc=nearestTaskNpc(self);if(npc==null){status="At the task destination, waiting for a loaded "+taskLoop.task.target+" with an Attack action.";return;}
    type=npc.getId();kindName=normalise(taskLoop.task.target);selected=taskLoop.task.target+" (all matching task NPCs)";preview=npc;resolveNpcProfile(type);applyNpcProfile();anchor=self.getWorldLocation();counter.start(type,taskLoop.task.remaining);target=null;lastRequest=-1000;retries=0;progressTick=tick;lastHealth=-1;survival.reset();boost.reset();resetGear();status="Task combat started for all "+taskLoop.task.remaining+" "+taskLoop.task.display+". Auto profile scanned the destination NPC for offensive gear/style and defensive prayer. "+profileStatus();return;
   }
   case HOME:{
    if(collectHighlightedLoot())return;if(tick<=lootPriorityUntil){status="Combat stopped; waiting briefly for purple-or-higher loot before returning home.";return;}if(nearestNpc("nieve",null)!=null){taskLoop.stage(SlayerTaskLoop.Stage.WAIT_HOME,tick);status="The player is already in Nieve's home area; confirming the stopped run.";return;}if(c.isMenuOpen()){runtime.combatUi.escape(c);status="Closing the current menu before returning home.";return;}
    if(homeMethodAttempts==0&&!taskLoop.due(tick,6)){if(!runtime.combatUi.ready(net.unethicalite.api.widgets.Tab.MAGIC)){status="Opening the spellbook for its visible Home teleport.";return;}net.unethicalite.api.magic.Spell home=currentHomeSpell();Widget homeWidget=home==null?null:c.getWidget(home.getWidget());if(homeWidget!=null&&VisibleCombatUi.effectivelyVisible(homeWidget)&&runtime.combatUi.clickWidget(c,homeWidget)){homeMethodAttempts=1;taskLoop.stage(SlayerTaskLoop.Stage.WAIT_HOME,tick);status="Clicked the visible Home teleport; waiting for Nieve's home area.";}else status="The current spellbook Home teleport is not visibly usable; preparing the ::home fallback.";return;}
    if(taskLoop.due(tick,2)&&runtime.combatUi.typeText(c,"::home",true)){homeMethodAttempts=2;taskLoop.stage(SlayerTaskLoop.Stage.WAIT_HOME,tick);status="Submitted ::home through the chatbox with Enter; waiting for Nieve's home area.";}return;
   }
   case WAIT_HOME:
    if(nearestNpc("nieve",null)!=null){boolean selectedRun=taskLoop.selectedOnly;taskLoop.stop();resetCompletedTask();if(selectedRun){status="No active Slayer task remains and the home transition was confirmed. All Slayer combat state was reset; stopped.";}else{taskLoop.start();taskLoop.stage(SlayerTaskLoop.Stage.ASSIGNMENT,tick);selected="Nieve assignment pending";status="Home confirmed. The completed task, NPC selection, counters and retries were fully reset; requesting a fresh assignment from Nieve.";}}else if(taskLoop.due(tick,30)){taskLoop.stage(SlayerTaskLoop.Stage.HOME,tick);status=homeMethodAttempts==1?"Visible Home teleport was not confirmed; falling back to ::home through the chatbox.":"Home transition was not confirmed; retrying ::home through the chatbox.";}return;
   default:return;
  }
 }
 private boolean handleNieveDialogue(Client c,int tick){
  Widget dialogue=c.getWidget(WidgetInfo.DIALOG_NPC_TEXT);if(dialogue==null||!VisibleCombatUi.effectivelyVisible(dialogue))return false;
  Widget name=c.getWidget(WidgetInfo.DIALOG_NPC_NAME);String speaker=name==null?"":normalise(SlayerTaskLoop.clean(name.getText()));if(!speaker.isEmpty()&&!speaker.equals("nieve"))return false;
  String text=SlayerTaskLoop.clean(dialogue.getText());boolean accepted=false;
  if(taskLoop.stage==SlayerTaskLoop.Stage.WAIT_ASSIGNMENT){
   if(taskLoop.accept(text)){acceptTaskStatus("Nieve's visible dialogue");accepted=true;}
   else if(taskLoop.observeTarget(text)&&taskLoop.acceptObservedCount(helmetRemaining())){acceptTaskStatus("Nieve's visible dialogue and the equipped helmet count");accepted=true;}
  }
  Widget next=findWidget(c,"Click here to continue",false);if(next!=null&&taskLoop.due(tick,1)&&runtime.combatUi.clickWidget(c,next)){taskLoop.requested(tick);status=(accepted||taskLoop.task!=null?"Task confirmed; ":"")+"closing Nieve's dialogue before continuing.";}else if(!accepted)status="Reading Nieve's visible dialogue before continuing.";
  return true;
 }
 private void acceptTaskStatus(String source){selected=taskLoop.task.target+" (Nieve task)";status="Assignment confirmed from "+source+": "+taskLoop.task.display+", "+taskLoop.task.remaining+" remaining. Closing the dialogue, then finding the Gnome glider.";}
 private boolean teleportsOpen(Client c){return teleportGroup(c)>=0;}
 private int teleportGroup(Client c){Widget title=findWidget(c,"Teleports",true);if(title!=null){int group=title.getId()>>>16;if(findWidget(c,"Search",true,group)!=null)return group;}Widget marker=findWidget(c,"Favorites",true);if(marker!=null){int group=marker.getId()>>>16;if(findWidget(c,"Monsters",true,group)!=null&&findWidget(c,"Search",true,group)!=null)return group;}return -1;}
 private Widget teleportWidget(Client c,String expected,boolean exact){int group=teleportGroup(c);return group<0?null:findWidget(c,expected,exact,group);}
 private Widget teleportDescendant(Client c,String expected,boolean exact){
  Widget title=teleportWidget(c,"Teleports",true);if(title==null)return null;Widget root=title;Set<Widget> parents=Collections.newSetFromMap(new IdentityHashMap<>());while(root.getParent()!=null&&parents.add(root))root=root.getParent();ArrayDeque<Widget> queue=new ArrayDeque<>();Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());queue.add(root);String needle=normalise(expected);
  while(!queue.isEmpty()&&seen.size()<10000){Widget widget=queue.remove();if(!seen.add(widget))continue;add(queue,widget.getChildren());add(queue,widget.getStaticChildren());add(queue,widget.getDynamicChildren());add(queue,widget.getNestedChildren());if(VisibleCombatUi.effectivelyVisible(widget)&&widgetMatches(widget,needle,exact))return widget;}return null;
 }
 private Widget teleportResultWidget(Client c){
  Widget title=teleportWidget(c,"Teleports",true);if(title==null||taskLoop.task==null)return null;Widget root=title;Set<Widget> parents=Collections.newSetFromMap(new IdentityHashMap<>());while(root.getParent()!=null&&parents.add(root))root=root.getParent();String target=normalise(taskLoop.task.target);ArrayDeque<Widget> queue=new ArrayDeque<>();Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());queue.add(root);Widget best=null;int bestScore=Integer.MIN_VALUE;
  while(!queue.isEmpty()&&seen.size()<10000){Widget widget=queue.remove();if(!seen.add(widget))continue;add(queue,widget.getChildren());add(queue,widget.getStaticChildren());add(queue,widget.getDynamicChildren());add(queue,widget.getNestedChildren());if(!VisibleCombatUi.effectivelyVisible(widget))continue;Rectangle bounds=widget.getBounds();if(bounds==null||bounds.isEmpty())continue;
   String text=normalise(SlayerTaskLoop.clean(widget.getText())),name=normalise(SlayerTaskLoop.clean(widget.getName()));boolean named=target.equals(normalise(SlayerTaskLoop.singular(text)))||target.equals(normalise(SlayerTaskLoop.singular(name)));boolean selectable=false;String[] actions=widget.getActions();if(actions!=null)for(String action:actions)if("select".equals(normalise(SlayerTaskLoop.clean(action)))){selectable=true;break;}if(!named&&!selectable)continue;int score=(named?200:0)+(selectable?100:0)+Math.min(50,bounds.width*bounds.height/100);if(score>bestScore){best=widget;bestScore=score;}
  }
  return best;
 }
 private Rectangle teleportPanelBounds(Client c){
  Widget title=teleportWidget(c,"Teleports",true),search=teleportWidget(c,"Search",true);if(title==null||search==null||title.getBounds()==null||search.getBounds()==null)return null;Rectangle titleBounds=title.getBounds(),searchBounds=search.getBounds(),best=null;long bestArea=Long.MAX_VALUE;
  // Prefer the smallest visible ancestor that contains both stable anchors.  This is the
  // actual Teleports panel on Orion and remains correct when the client is moved/resized.
  Set<Widget> parents=Collections.newSetFromMap(new IdentityHashMap<>());for(Widget widget=title;widget!=null&&parents.add(widget);widget=widget.getParent()){Rectangle bounds=widget.getBounds();if(VisibleCombatUi.effectivelyVisible(widget)&&bounds!=null&&bounds.contains(titleBounds)&&bounds.contains(searchBounds)){long area=(long)bounds.width*bounds.height;if(area<bestArea){best=new Rectangle(bounds);bestArea=area;}}}
  if(best!=null&&best.width>=300&&best.height>=200)return best;
  // Some Orion builds detach the title from the background widget.  In that case use the
  // visible group bounds, which still yields a client-relative panel rectangle.
  int group=teleportGroup(c);ArrayDeque<Widget> queue=new ArrayDeque<>();Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());Widget[] roots=c.getWidgetRoots();if(roots!=null)for(Widget root:roots)if(root!=null)queue.add(root);Rectangle union=null;
  while(!queue.isEmpty()&&seen.size()<10000){Widget widget=queue.remove();if(!seen.add(widget))continue;add(queue,widget.getChildren());add(queue,widget.getStaticChildren());add(queue,widget.getDynamicChildren());add(queue,widget.getNestedChildren());if((widget.getId()>>>16)!=group||!VisibleCombatUi.effectivelyVisible(widget))continue;Rectangle bounds=widget.getBounds();if(bounds==null||bounds.isEmpty())continue;if(union==null)union=new Rectangle(bounds);else union.add(bounds);}
  if(union!=null&&union.width>=300&&union.height>=200)return union;
  // Last-resort geometry still anchors to the visible Search control, never to the desktop.
  int width=Math.max(300,searchBounds.width*4),height=Math.max(200,searchBounds.height*13);return new Rectangle(searchBounds.x-Math.max(8,searchBounds.width/6),searchBounds.y-height+searchBounds.height+Math.max(8,searchBounds.height/3),width,height);
 }
 static Rectangle teleportSelectTarget(Rectangle panel){if(panel==null||panel.width<100||panel.height<100)return null;int x=panel.x+(int)Math.round(panel.width*.515),y=panel.y+(int)Math.round(panel.height*.386),w=Math.max(12,(int)Math.round(panel.width*.08)),h=Math.max(8,(int)Math.round(panel.height*.05));return new Rectangle(x-w/2,y-h/2,w,h);}
 static Rectangle teleportResultCardTarget(Rectangle panel){if(panel==null||panel.width<100||panel.height<100)return null;int x=panel.x+(int)Math.round(panel.width*.515),y=panel.y+(int)Math.round(panel.height*.25),w=Math.max(20,(int)Math.round(panel.width*.12)),h=Math.max(12,(int)Math.round(panel.height*.08));return new Rectangle(x-w/2,y-h/2,w,h);}
 private NPC nearestNpc(String name,String action){Player self=runtime.client.getLocalPlayer();NPC best=null;int distance=Integer.MAX_VALUE;for(NPC npc:runtime.client.getNpcs()){if(npc==null||!normalise(name).equals(normalise(npc.getName()))||action!=null&&!npc.hasAction(action))continue;int d=self==null?0:npc.getWorldLocation().distanceTo(self.getWorldLocation());if(d<distance){distance=d;best=npc;}}return best;}
 private NPC nearestTaskNpc(Player self){NPC best=null;int distance=Integer.MAX_VALUE;for(NPC npc:runtime.client.getNpcs()){if(npc==null||npc.isDead()||npc.getHealthRatio()==0||!npc.hasAction("Attack")||!normalise(taskLoop.task.target).equals(normalise(SlayerTaskLoop.singular(npc.getName()))))continue;int d=npc.getWorldLocation().distanceTo(self.getWorldLocation());if(d<distance){distance=d;best=npc;}}return best;}
 private net.runelite.api.TileObject nearestObject(String name,String action){net.runelite.api.TileObject best=null;Player self=runtime.client.getLocalPlayer();int distance=Integer.MAX_VALUE;for(net.runelite.api.TileObject object:net.unethicalite.api.entities.TileObjects.getAll()){if(object==null||!normalise(name).equals(normalise(object.getName()))||action!=null&&!object.hasAction(action))continue;int d=self==null?0:object.getWorldLocation().distanceTo(self.getWorldLocation());if(d<distance){distance=d;best=object;}}return best;}
 private net.unethicalite.api.magic.Spell currentHomeSpell(){net.unethicalite.api.magic.SpellBook book=net.unethicalite.api.magic.SpellBook.getCurrent();if(book==net.unethicalite.api.magic.SpellBook.ANCIENT)return net.unethicalite.api.magic.SpellBook.Ancient.EDGEVILLE_HOME_TELEPORT;if(book==net.unethicalite.api.magic.SpellBook.LUNAR)return net.unethicalite.api.magic.SpellBook.Lunar.LUNAR_HOME_TELEPORT;if(book==net.unethicalite.api.magic.SpellBook.NECROMANCY)return net.unethicalite.api.magic.SpellBook.Necromancy.ARCEUUS_HOME_TELEPORT;return net.unethicalite.api.magic.SpellBook.Standard.HOME_TELEPORT;}
 private static Widget findWidget(Client client,String expected,boolean exact){return findWidget(client,expected,exact,-1);}
 private static Widget findWidget(Client client,String expected,boolean exact,int group){ArrayDeque<Widget> queue=new ArrayDeque<>();Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());Widget[] roots=client.getWidgetRoots();if(roots!=null)for(Widget root:roots)if(root!=null)queue.add(root);String needle=normalise(expected);while(!queue.isEmpty()&&seen.size()<10000){Widget widget=queue.remove();if(!seen.add(widget))continue;add(queue,widget.getChildren());add(queue,widget.getStaticChildren());add(queue,widget.getDynamicChildren());add(queue,widget.getNestedChildren());if((group<0||widget.getId()>>>16==group)&&VisibleCombatUi.effectivelyVisible(widget)&&widgetMatches(widget,needle,exact))return widget;}return null;}
 private static boolean widgetMatches(Widget widget,String needle,boolean exact){if(valueMatches(widget.getText(),needle,exact)||valueMatches(widget.getName(),needle,exact))return true;String[] actions=widget.getActions();if(actions!=null)for(String action:actions)if(valueMatches(action,needle,exact))return true;return false;}
 private static boolean valueMatches(String value,String needle,boolean exact){String text=normalise(SlayerTaskLoop.clean(value));return !text.isEmpty()&&(exact?text.equals(needle):text.equals(needle)||text.contains(needle));}
 private static void add(ArrayDeque<Widget> queue,Widget[] widgets){if(widgets!=null)for(Widget widget:widgets)if(widget!=null)queue.add(widget);}
 boolean selectedKind(NPC npc){return npc!=null&&kindName!=null&&kindName.equals(normalise(npc.getName()));}
 private static String normalise(String name){return name==null?"":name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
 private int helmetRemaining(){Item helmet=net.unethicalite.api.items.Equipment.getFirst(i->normalise(i.getName()).contains("slayer helmet"));return helmet==null?0:Math.max(0,helmet.getQuantity());}
 private int amount(int id){int total=0;for(Item item:net.unethicalite.api.items.Inventory.getAll(id))total+=item.getQuantity();return total;}
 private void resolveNpcProfile(int npcId){String name=kindName;if(runtime.client!=null)for(NPC npc:runtime.client.getNpcs())if(npc!=null&&npc.getId()==npcId&&npc.getName()!=null){name=npc.getName();break;}resolveNpcProfile(npcId,name);}
 private void resolveNpcProfile(int npcId,String name){profiledNpcId=npcId;npcProfile=null;if(runtime.npcManager!=null&&npcId>=0)try{npcProfile=SlayerNpcProfile.choose(runtime.npcManager.getStats(npcId));}catch(RuntimeException ignored){npcProfile=null;}if(npcProfile==null)npcProfile=SlayerNpcProfile.chooseByName(name);}
 private void applyNpcProfile(){if(npcProfile==null)return;offensivePrayer=npcProfile.offensivePrayer;defensivePrayer=npcProfile.protectionPrayer;}
 private String profileStatus(){if(!automaticProfile)return npcProfile==null?"Automatic style selection is off; manual prayers remain active.":"Automatic style selection is available but off; manual prayers remain active.";return npcProfile==null?"No Orion NPC-stat record was found; retaining manual prayers and current gear.":npcProfile.summary();}
 private void resetAutomaticGear(){pendingGear.clear();gearBatch.reset();activeGearStyle=CombatCore.Style.UNKNOWN;gearGateReleased=false;gearFallbackReported=false;gearGateStarted=-1;}
 private void resetGear(){resetAutomaticGear();resetSuperior();lootQueue.clear();lootTraceSeen.clear();lootRequestTick=lootEatTick=-1000;lootItemId=lootBefore=-1;lootTile=null;}
 static boolean mayAttackAfterGearCheck(boolean automaticProfile,boolean observedGearComplete,int elapsedTicks){return !automaticProfile||observedGearComplete||elapsedTicks>=3;}
 private boolean prepareTargetProfile(NPC npc){
  if(!automaticProfile||npc==null)return true;
  if(profiledNpcId!=npc.getId()||npcProfile==null){resolveNpcProfile(npc.getId(),npc.getName());applyNpcProfile();resetAutomaticGear();requestedPrayer=null;lastPrayerRequest=-1000;status="Scanned "+npc.getName()+" immediately before attack. "+profileStatus();diagnostic("PROFILE "+safe(npc.getName()));return false;}
  if(gearGateReleased)return true;
  int tick=runtime.client.getTickCount();if(gearGateStarted<0)gearGateStarted=tick;
  boolean observedGearComplete=prepareAutomaticGear();
  if(observedGearComplete){gearGateReleased=true;diagnostic("GEAR_READY "+safe(npc.getName()));return true;}
  int elapsed=tick-gearGateStarted;if(!mayAttackAfterGearCheck(true,false,elapsed))return false;
  // Orion custom items do not always expose RuneLite equipment metadata or a
  // prompt acknowledgement. Keep retrying the switch on later pulses, but do
  // not turn that missing metadata into a permanent no-combat state.
  if(!gearFallbackReported){gearFallbackReported=true;diagnostic("GEAR_RETRY_NONBLOCKING "+safe(npc.getName()));}
  return true;
 }
 private boolean continueAutomaticGearDuringCombat(NPC npc){
  if(!automaticProfile||gearGateReleased||npcProfile==null)return true;
  if(prepareAutomaticGear()){gearGateReleased=true;diagnostic("GEAR_READY_DURING_COMBAT "+safe(npc==null?null:npc.getName()));return true;}
  // The actor is already fighting, so returning from this controller pulse does
  // not cancel or delay its current attack. It only reserves this UI cycle for
  // the remaining visible equipment click and retries on the next pulse.
  return false;
 }
 private int equipmentSlot(int id){if(runtime.itemManager!=null){net.runelite.http.api.item.ItemStats stats=runtime.itemManager.getItemStats(id,false);if(stats!=null&&stats.getEquipment()!=null)return stats.getEquipment().getSlot();}return -1;}
 private String itemName(int id){ItemComposition definition=runtime.client.getItemDefinition(id);return definition==null?"":definition.getName();}
 private Set<Integer> availableGear(){Set<Integer> available=new LinkedHashSet<>(runtime.adapter.container(InventoryID.EQUIPMENT));available.addAll(runtime.adapter.container(InventoryID.INVENTORY));return available;}
 private boolean prepareAutomaticGear(){
  CombatCore.Style style=npcProfile.attackStyle;if(activeGearStyle!=style){pendingGear.clear();gearBatch.reset();activeGearStyle=style;}
  java.util.List<Integer> configured=runtime.config.gear.get(style);if(configured==null||configured.isEmpty()){status="Auto profile chose "+style+", but that gear preset is empty. Load the three-style Chambers gear data or turn Auto off.";return false;}
  Set<Integer> available=availableGear();java.util.function.IntFunction<String> names=this::itemName;
  OlmGearSwitch.recogniseStyleGloves(style,configured,available,names);
  if(style==CombatCore.Style.MAGIC)OlmGearSwitch.recogniseSeersRingI(configured,available,names,id->equipmentSlot(id)==EquipmentInventorySlot.RING.getSlotIdx());
  if(style==CombatCore.Style.RANGED){OlmGearSwitch.recogniseHolyCards(configured,available,names);OlmGearSwitch.recogniseArchersRingI(configured,available,names,id->equipmentSlot(id)==EquipmentInventorySlot.RING.getSlotIdx());}
  if(style==CombatCore.Style.MELEE)OlmGearSwitch.recogniseChargedScythe(configured,available,names,id->equipmentSlot(id)==EquipmentInventorySlot.WEAPON.getSlotIdx(),id->equipmentSlot(id)==EquipmentInventorySlot.SHIELD.getSlotIdx());
  java.util.List<Integer> required=new ArrayList<>(configured);OlmGearSwitch.keepAvailable(required,available);required.removeIf(id->equipmentSlot(id)==EquipmentInventorySlot.HEAD.getSlotIdx());
  Set<Integer> worn=runtime.adapter.container(InventoryID.EQUIPMENT);int tick=runtime.client.getTickCount();pendingGear.entrySet().removeIf(e->worn.contains(e.getKey())||tick-e.getValue()>=3);
  gearBatch.plan(style,required,worn,pendingGear.keySet());int id=OlmGearSwitch.next(required,worn,pendingGear.keySet());if(id<0)return worn.containsAll(required);
  if(!gearBatch.allowed(tick,runtime.ticks.elapsedMs(),runtime.ticks.observedPeriodMs())){status=gearBatch.status(tick);return false;}
  Item item=net.unethicalite.api.items.Inventory.getFirst(id);if(item==null||!item.hasAction("Wield","Wear","Equip")){status="Auto "+style+" setup needs "+itemName(id)+" ("+id+") in Inventory. No unrelated gear will be substituted.";return false;}
  if(!runtime.combatUi.ready(net.unethicalite.api.widgets.Tab.INVENTORY)){status="Opening Inventory for the optional auto "+style+" switch. "+gearBatch.status(tick);return false;}
  if(!VisibleCombatUi.inventoryVisible(runtime.client,item)){status="Waiting for the visible auto-style gear item.";return false;}
  if(!runtime.combatUi.inventoryAction(runtime.client,item,"Wield","Wear","Equip")){status=runtime.combatUi.status;return false;}
  pendingGear.put(id,tick);gearBatch.sent(tick,runtime.ticks.elapsedMs(),runtime.ticks.observedPeriodMs());status=gearBatch.status(tick);return false;
 }
 private int inventoryCount(int id){int total=0;for(Item item:net.unethicalite.api.items.Inventory.getAll(id))total+=item.getQuantity();return total;}
 private boolean purpleOrHigherLoot(TileItem item){
  if(item==null)return false;if(SlayerLoot.matchesPurpleOrAbove(runtime.lootFiltersPlugin,item))return true;
  if(runtime.itemManager==null||runtime.groundItemsConfig==null)return false;ItemComposition definition=runtime.itemManager.getItemComposition(item.getId());if(definition==null)return false;int canonical=definition.getNote()!=-1?definition.getLinkedNoteId():item.getId();int ge=canonical==995?1:runtime.itemManager.getItemPrice(canonical),ha=canonical==995?1:definition.getHaPrice();return SlayerLoot.matchesHighOrAbove(ge,ha,runtime.groundItemsConfig);
 }
 private int queueHighlightedLoot(){
  Player self=runtime.client.getLocalPlayer();if(self==null)return lootQueue.size();
  for(TileItem item:net.unethicalite.api.entities.TileItems.getAll()){
   if(item==null||item.getTile()==null)continue;boolean accepted=purpleOrHigherLoot(item);java.awt.Color filterColor=SlayerLoot.resolvedFilterColor(runtime.lootFiltersPlugin,item);
   if(!accepted){if(filterColor!=null)traceLootOnce("FILTER_REJECT",item,filterColor);continue;}WorldPoint point=item.getWorldLocation();if(point==null)continue;
   int d=point.distanceTo(self.getWorldLocation());if(d<=16){boolean added=lootQueue.add(new LootMark(item.getId(),point));if(added)traceLoot("QUEUED",item,filterColor);while(lootQueue.size()>64)lootQueue.remove(lootQueue.iterator().next());}
  }
  return lootQueue.size();
 }
 private boolean sameLoot(TileItem item,LootMark mark){return item!=null&&mark!=null&&item.getId()==mark.id&&Objects.equals(item.getWorldLocation(),mark.tile);}
 private TileItem nearestHighlightedLoot(){
  queueHighlightedLoot();Player self=runtime.client.getLocalPlayer();if(self==null)return null;
  java.util.List<TileItem> liveItems=new ArrayList<>();for(TileItem item:net.unethicalite.api.entities.TileItems.getAll())if(item!=null)liveItems.add(item);
  TileItem best=null;int distance=Integer.MAX_VALUE;Iterator<LootMark> marks=lootQueue.iterator();
  while(marks.hasNext()){
   LootMark mark=marks.next();TileItem live=null;for(TileItem item:liveItems)if(sameLoot(item,mark)&&purpleOrHigherLoot(item)){live=item;break;}
   if(live==null){marks.remove();continue;}int d=mark.tile.distanceTo(self.getWorldLocation());
   if(d<=16&&d<distance){net.runelite.api.Point click=live.getClickPoint();if(click!=null||VisibleCombatUi.groundPoint(runtime.client,mark.tile)!=null){best=live;distance=d;}}
  }
  return best;
 }
 private boolean groundItemPresent(int id,WorldPoint tile){if(id<0||tile==null)return false;for(TileItem item:net.unethicalite.api.entities.TileItems.getAll())if(item!=null&&item.getId()==id&&Objects.equals(item.getWorldLocation(),tile))return true;return false;}
 private boolean collectHighlightedLoot(){
  Client c=runtime.client;int tick=c.getTickCount();
   if(lootItemId>0){boolean gained=inventoryCount(lootItemId)>lootBefore,present=groundItemPresent(lootItemId,lootTile);if(gained){traceLoot("PICKUP_CONFIRMED",lootItemId,lootTile,null);lootQueue.remove(new LootMark(lootItemId,lootTile));status="Queued purple-or-higher loot entered Inventory; draining remaining queue before combat. "+progress();lootItemId=-1;lootTile=null;return !lootQueue.isEmpty();}if(!present){traceLoot("GROUND_ITEM_GONE",lootItemId,lootTile,null);lootQueue.remove(new LootMark(lootItemId,lootTile));lootItemId=-1;lootTile=null;return !lootQueue.isEmpty();}if(tick-lootRequestTick<5){status="Waiting for the queued loot pickup to be observed. "+progress();return true;}traceLoot("PICKUP_TIMEOUT",lootItemId,lootTile,null);lootItemId=-1;lootTile=null;}
  TileItem loot=nearestHighlightedLoot();if(loot==null){lootEatTick=-1000;return false;}
  boolean existingStack=loot.isStackable()&&net.unethicalite.api.items.Inventory.contains(loot.getId());
  if(net.unethicalite.api.items.Inventory.isFull()&&!existingStack){
   if(net.unethicalite.api.items.Inventory.getFreeSlots()>0){lootEatTick=-1000;}else if(lootEatTick>=0&&tick-lootEatTick<4){status="Waiting for the angler to be consumed before looting "+loot.getName()+". "+progress();return true;}else{
    Item angler=net.unethicalite.api.items.Inventory.getFirst(i->i.hasAction("Eat")&&normalise(i.getName()).equals("anglerfish"));if(angler==null){status="Purple-or-higher loot is present, but Inventory is full and no anglerfish is available.";return true;}
    if(!runtime.combatUi.ready(net.unethicalite.api.widgets.Tab.INVENTORY)){status="Opening Inventory to eat one angler before high-value loot.";return true;}
    if(!VisibleCombatUi.inventoryVisible(c,angler)||!runtime.combatUi.inventoryAction(c,angler,"Eat")){status=runtime.combatUi.status;return true;}lootEatTick=tick;status="Inventory full: eating one angler before collecting "+loot.getName()+".";return true;
   }
  }
   if(c.isMenuOpen()){if(!VisibleCombatUi.hasGroundItemMenu(c,loot,"Take")){traceLoot("UNRELATED_MENU",loot,null);runtime.combatUi.escape(c);status="Closing an unrelated menu before collecting highlighted loot.";return true;}if(runtime.combatUi.chooseGroundItemMenu(c,loot,"Take")){lootItemId=loot.getId();lootTile=loot.getWorldLocation();lootBefore=inventoryCount(lootItemId);lootRequestTick=tick;traceLoot("TAKE_SELECTED",loot,null);status="Selected Take for purple-or-higher loot: "+loot.getName()+".";}else status="Waiting for the visible Take entry for "+loot.getName()+".";return true;}
   if(tick-lootRequestTick<3)return true;if(runtime.combatUi.rightClickGroundItem(c,loot)){lootRequestTick=tick;traceLoot("RIGHT_CLICK_SENT",loot,null);status="Right-clicking purple-or-higher loot: "+loot.getName()+"; waiting for Take.";}else traceLootOnce("RIGHT_CLICK_BLOCKED",loot,null);return true;
 }
 private boolean restoreSupplies(){
  Client c=runtime.client;int hp=c.getBoostedSkillLevel(Skill.HITPOINTS),pp=c.getBoostedSkillLevel(Skill.PRAYER);
  SlayerSurvival.Need pending=survival.pending();int observed=pending==SlayerSurvival.Need.PRAYER?pp:hp;
  if(survival.waiting(c.getTickCount(),supplyId<0?0:amount(supplyId),observed)){status="Waiting for supply consumption/cooldown. "+progress();return true;}
  if(survival.failed()){stop("Three supply requests were not confirmed; check the inventory");return true;}
  int hpTarget=Math.min(survival.hp,c.getRealSkillLevel(Skill.HITPOINTS)),ppTarget=Math.min(survival.prayer,c.getRealSkillLevel(Skill.PRAYER));
  SlayerSurvival.Need need=SlayerSurvival.need(hp,pp,hpTarget,ppTarget);if(need==SlayerSurvival.Need.NONE)return false;
  Item item=need==SlayerSurvival.Need.HEALTH?net.unethicalite.api.items.Inventory.getFirst(i->i.hasAction("Eat")&&normalise(i.getName()).equals("anglerfish")):net.unethicalite.api.items.Inventory.getFirst(i->i.hasAction("Drink")&&SlayerSurvival.restore(i.getName()));
  if(item==null&&need==SlayerSurvival.Need.HEALTH)item=net.unethicalite.api.items.Inventory.getFirst(i->i.hasAction("Eat"));
  if(item==null){stop(need==SlayerSurvival.Need.HEALTH?"Below HP target with no edible food. Brews are not automatically used.":"Below prayer target with no recognized prayer restore");return true;}
  if(!runtime.combatUi.ready(net.unethicalite.api.widgets.Tab.INVENTORY)){status="Opening Inventory to restore "+(need==SlayerSurvival.Need.HEALTH?"HP":"prayer")+". "+progress();return true;}
  if(!VisibleCombatUi.inventoryVisible(c,item)){status="Waiting for visible supply item.";return true;}
  if(!runtime.combatUi.inventoryAction(c,item,need==SlayerSurvival.Need.HEALTH?"Eat":"Drink")){status=runtime.combatUi.status;return true;}supplyId=item.getId();survival.requested(need,c.getTickCount(),amount(supplyId),need==SlayerSurvival.Need.HEALTH?hp:pp);status="Requested "+(need==SlayerSurvival.Need.HEALTH?"food":"prayer restore")+"; waiting for observed consumption. "+progress();return true;
 }
 private boolean maintainCombatBoost(){
  Client c=runtime.client;int attack=c.getBoostedSkillLevel(Skill.ATTACK),strength=c.getBoostedSkillLevel(Skill.STRENGTH),defence=c.getBoostedSkillLevel(Skill.DEFENCE),lowest=SlayerBoost.lowest(attack,strength,defence),tick=c.getTickCount();
  if(boost.waiting(tick,lowest)){status="Waiting for the Bottomless super combat flask boost/cooldown. "+progress();return true;}
  if(boost.failed()){boost.defer(tick);status="Bottomless super combat flask was not confirmed after three attempts; combat continues and it will retry shortly. "+progress();return false;}
  if(!SlayerBoost.needs(attack,strength,defence,boost.threshold))return false;
  Item flask=net.unethicalite.api.items.Inventory.getFirst(i->i.hasAction("Drink")&&normalise(i.getName()).equals("bottomless super combat flask"));
  if(flask==null){status="Combat boost reached "+lowest+", but no Bottomless super combat flask is available; continuing combat. "+progress();return false;}
  if(!runtime.combatUi.ready(net.unethicalite.api.widgets.Tab.INVENTORY)){status="Opening Inventory to renew combat boosts at "+lowest+". "+progress();return true;}
  if(!VisibleCombatUi.inventoryVisible(c,flask)){status="Waiting for the visible Bottomless super combat flask. "+progress();return true;}
  if(!runtime.combatUi.inventoryAction(c,flask,"Drink")){status=runtime.combatUi.status;return true;}
  boost.requested(tick,lowest);status="Drinking Bottomless super combat flask at lowest combat stat "+lowest+"; waiting for the observed stat increase. "+progress();return true;
 }
 boolean preparePrayers(){
  Client c=runtime.client;
  if(c.getBoostedSkillLevel(Skill.PRAYER)<=0)return false;
  Prayer desired=defensivePrayer!=null&&!c.isPrayerActive(defensivePrayer)?defensivePrayer:offensivePrayer!=null&&!c.isPrayerActive(offensivePrayer)?offensivePrayer:null;
  if(desired==null){requestedPrayer=null;return false;}
  if(requestedPrayer==desired&&c.getTickCount()-lastPrayerRequest<2)return false;
  net.runelite.api.widgets.Widget widget=c.getWidget(desired.getWidgetInfo());
  if(!runtime.combatUi.ready(net.unethicalite.api.widgets.Tab.PRAYER,100)){status="Opening Prayer book for "+desired.name().replace('_',' ')+". "+progress();return true;}
  if(!VisibleCombatUi.effectivelyVisible(widget)){requestedPrayer=desired;lastPrayerRequest=c.getTickCount();status="Selected prayer unavailable: "+desired.name().replace('_',' ');return false;}
  if(!runtime.combatUi.activatePrayer(c,desired)){status=runtime.combatUi.status;return true;}requestedPrayer=desired;lastPrayerRequest=c.getTickCount();status="Activating "+desired.name().replace('_',' ')+"; waiting for observed activation. "+progress();return true;
 }
 private void diagnostic(String event){
  try{
   Path file=runtime.projectRoot().toPath().resolve("runtime/slayer-last-status.txt");Files.createDirectories(file.getParent());Client c=runtime.client;
   int hp=c.getGameState()==GameState.LOGGED_IN?c.getBoostedSkillLevel(Skill.HITPOINTS):-1,prayer=c.getGameState()==GameState.LOGGED_IN?c.getBoostedSkillLevel(Skill.PRAYER):-1;
   String profile=npcProfile==null?"none":npcProfile.attackStyle.name();
   String offense=offensivePrayer==null?"none":offensivePrayer.name();
   String defence=defensivePrayer==null?"none":defensivePrayer.name();
   String line="SLAYER_DIAGNOSTIC_V2\nevent="+safe(event)+"\ntick="+c.getTickCount()+"\nkills="+counter.kills+"\ngoal="+counter.goal+"\nrunning="+counter.running+"\nhp="+hp+"\nprayer="+prayer+"\nautomaticProfile="+automaticProfile+"\nprofiledNpcId="+profiledNpcId+"\nattackStyle="+profile+"\noffensivePrayer="+offense+"\ndefensivePrayer="+defence+"\n";
   Files.writeString(file,line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
  }catch(RuntimeException|java.io.IOException ignored){}
 }
 private void traceLootOnce(String event,TileItem item,java.awt.Color color){
  if(item==null)return;String key=event+":"+item.getId()+":"+item.getWorldLocation()+":"+(color==null?"none":color.getRGB());if(lootTraceSeen.add(key))traceLoot(event,item,color);
 }
 private void traceLoot(String event,TileItem item,java.awt.Color color){if(item!=null)traceLoot(event,item.getId(),item.getWorldLocation(),color);}
 private void traceLoot(String event,int id,WorldPoint tile,java.awt.Color color){
  try{
   Path file=runtime.projectRoot().toPath().resolve("runtime/slayer-loot-trace.txt");Files.createDirectories(file.getParent());String rgb=color==null?"none":String.format(Locale.ROOT,"#%06X",color.getRGB()&0xFFFFFF);String line="tick="+runtime.client.getTickCount()+" event="+safe(event)+" id="+id+" tile="+(tile==null?"none":tile.getX()+","+tile.getY()+","+tile.getPlane())+" color="+rgb+" queued="+lootQueue.size()+System.lineSeparator();if(Files.exists(file)&&Files.size(file)>65536)Files.writeString(file,"SLAYER_LOOT_TRACE_V1"+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);Files.writeString(file,line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  }catch(RuntimeException|java.io.IOException ignored){}
 }
 private static String safe(String value){if(value==null)return "none";String clean=value.replace('\r',' ').replace('\n',' ').trim();return clean.length()>180?clean.substring(0,180):clean;}
 JFrame window(){if(window==null||!window.isDisplayable())window=new SlayerPanel(runtime,this);return window;}
}



