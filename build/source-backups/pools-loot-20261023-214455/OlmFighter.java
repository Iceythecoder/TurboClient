package owl.nh;

import com.google.gson.Gson;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.unethicalite.api.entities.TileObjects;
import net.unethicalite.api.items.Inventory;
import net.unethicalite.api.game.Combat;
import net.unethicalite.api.widgets.Tab;
import net.runelite.api.events.ProjectileMoved;
import static owl.nh.CombatCore.*;

/** Actual client inputs with a bounded observer that survives recoverable action failures. */
final class OlmFighter {
 static final class Profile {
  boolean verifiedForOrion,magicAutocastVerified,provisionalTest;
  boolean offensivePrayersEnabled=true;
  boolean finishRaidAutomatically=true;
  // Search and Take IDs recorded in the user's second Chambers demonstration.
  int rewardChestObjectId=30028,raidExitObjectId=-1,rewardContainerWidgetId=35323909;
  Map<String,String> offensivePrayers=OlmOffensivePrayers.defaults();
  Map<Integer,Integer> attackAnimations=new HashMap<>();
  Map<String,int[]> mageApproaches=new HashMap<>();
  Set<Integer> chamberRegions=new HashSet<>(),olmRegions=new HashSet<>(),mageHands=new HashSet<>(),meleeHands=new HashSet<>(),heads=new HashSet<>();
  int entryObjectId=-1;String entryAction="Enter";
  int plane=0;int potionCooldownTicks=3;int finalHandFinishWindowTicks=50;
  int roomMinX=-1,roomMaxX=-1,roomMinY=-1,roomMaxY=-1;
  Map<Integer,String> headNpcPhases=new HashMap<>(),specialProjectiles=new HashMap<>();
  Map<String,List<Integer>> supplies=new HashMap<>();
  Map<Integer,String> normalProjectiles=new HashMap<>();
  OlmMechanicAdapter.Config mechanics=new OlmMechanicAdapter.Config();
  void validate(){
   if(rewardChestObjectId< -1||raidExitObjectId< -1||rewardContainerWidgetId< -1)throw new IllegalArgumentException("Invalid reward chest, widget or raid exit ID");
   if(finalHandFinishWindowTicks<1||finalHandFinishWindowTicks>200)throw new IllegalArgumentException("Invalid final hand finish window");OlmOffensivePrayers.validate(offensivePrayers);
   OlmArenaPlan.validate(mageApproaches);
   if(attackAnimations==null||attackAnimations.size()>128||attackAnimations.entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey()<0||e.getValue()==null||e.getValue()<1||e.getValue()>20))throw new IllegalArgumentException("Invalid verified attack animation/speed mapping");
   for(Set<Integer> ids:Arrays.asList(chamberRegions,olmRegions,mageHands,meleeHands,heads))if(ids==null||ids.isEmpty()||ids.size()>128||ids.stream().anyMatch(i->i==null||i<0))throw new IllegalArgumentException("All region and NPC role sets need verified numeric IDs");
   if(!chamberRegions.containsAll(olmRegions)||!Collections.disjoint(mageHands,meleeHands)||!Collections.disjoint(mageHands,heads)||!Collections.disjoint(meleeHands,heads))throw new IllegalArgumentException("Room regions must be inside raid regions; NPC roles must not overlap");
   if(entryObjectId< -1||!Arrays.asList("Enter","Pass","Open","Climb-down").contains(entryAction))throw new IllegalArgumentException("Invalid entry object/action");
   if(roomMinX<0||roomMaxX<roomMinX||roomMinY<0||roomMaxY<roomMinY)throw new IllegalArgumentException("Configure the verified Olm room tile bounds (not just its region)");
   if(potionCooldownTicks<3||potionCooldownTicks>10)throw new IllegalArgumentException("Potion cooldown must be 3..10 server ticks");if(plane<0||plane>3)throw new IllegalArgumentException("Invalid template plane");
   if(supplies==null||headNpcPhases==null||normalProjectiles==null||specialProjectiles==null||normalProjectiles.isEmpty())throw new IllegalArgumentException("Configure potion, phase and projectile mappings");
   for(String key:Arrays.asList("overload","enhance","aid","revitalisation")){List<Integer> ids=supplies.get(key);if(ids==null||ids.isEmpty()||ids.size()>16||ids.stream().anyMatch(i->i==null||i<0))throw new IllegalArgumentException("Missing verified potion IDs: "+key);}
   for(String phase:headNpcPhases.values())OlmStrategy.Phase.valueOf(phase);
   for(String prayer:normalProjectiles.values())if(!Arrays.asList("MAGIC","RANGED","MELEE").contains(prayer))throw new IllegalArgumentException("Projectile style must be MAGIC, RANGED or MELEE");
   if(normalProjectiles.size()>64||specialProjectiles.size()>64||headNpcPhases.size()>128)throw new IllegalArgumentException("Profile mappings too large");
   if(mechanics==null)throw new IllegalArgumentException("Missing mechanics configuration");mechanics.validate();
   for(Map.Entry<Integer,OlmSpecials.Orb> orb:mechanics.orbProjectiles.entrySet()){String style=orbStyle(orb.getValue());if(normalProjectiles.containsKey(orb.getKey())&&!style.equals(normalProjectiles.get(orb.getKey())))throw new IllegalArgumentException("Conflicting normal/orb projectile mapping");}
  }
 }
 private final CombatRuntime runtime;private final Client client;private final OlmStrategy strategy=new OlmStrategy();
 private Profile profile;volatile boolean running,observing;volatile String status="No Olm profile configured",trace="";
 private final OlmRaidFinish raidFinish=new OlmRaidFinish();private boolean finishStartedInInstance;
 private final ArrayDeque<String> observations=new ArrayDeque<>();private int lastTick=-1,lastRequest=-100,manualPhase;
 private final OlmBuffTracker buffs=new OlmBuffTracker();
 private final OlmOverheadRestore overheadRestore=new OlmOverheadRestore();
 private final OlmHealingWatch healingWatch=new OlmHealingWatch();
 private final OlmPotionFlow potions=new OlmPotionFlow();private final OlmEntryFlow entryFlow=new OlmEntryFlow();private WorldPoint entryGatePosition,entryOutsidePosition;private boolean entered,entryCombatObserved,entryDialoguePending;
 private boolean pendingSpecial;private String lastSituation;private final Map<String,String> corrections=new HashMap<>();private int previousHp=-1;
 private final Set<Projectile> seen=Collections.newSetFromMap(new IdentityHashMap<>());
 private final OlmPhaseTracker phases=new OlmPhaseTracker();
 private final OlmMechanicAdapter special;
 private final OlmOffensivePrayers offensive;
 private long observedMoveEpoch,lastAttackMoveEpoch=-1;private int lastRunToggleCycle=-1000;private int lastAttackRequest=-100;private Style lastAttackStyle=Style.RANGED;
 private final OlmAttackTiming attackTiming=new OlmAttackTiming();
 private final OlmGearBatch gearBatch=new OlmGearBatch();
 private int entryPrayerTick=-1000;
 private String lastMageSpawn;private int completedPairs;private boolean transitionPending;private int[] transitionTile;
 private final Set<NPC> deadActors=Collections.newSetFromMap(new IdentityHashMap<>());
 private OlmStrategy.Phase lastObservedPhase=OlmStrategy.Phase.UNKNOWN;
 private String desiredTarget="magic";
 private final OlmActionLearning actionLearning=new OlmActionLearning();
 private final Map<Integer,Integer> pendingGear=new HashMap<>();private int lastCycle=-1,lastMechanicCycle=-1000;private boolean cachedMechanicAction;
 private volatile OlmTargetNetwork targetNetwork;
 private volatile String targetLearning="Neural target model not seeded.";
 private double[] lastTargetState;
 private final OlmRunLearning runLearning=new OlmRunLearning();
 private volatile String presetStatus="No attempt saved in this session yet.";private volatile boolean savingPreset;
 private OlmPrayerLearning.Checkpoint restoredPrayerTiming;
 OlmFighter(CombatRuntime runtime){this.runtime=runtime;client=runtime.client;special=new OlmMechanicAdapter(client,runtime.combatUi);offensive=new OlmOffensivePrayers(client,()->runtime.combatUi.ready(Tab.PRAYER,20),net.unethicalite.api.widgets.Prayers::toggle);}
 void configure(String json){if(running)throw new IllegalStateException("Stop Olm before changing its profile");if(json.length()>65536)throw new IllegalArgumentException("Profile exceeds 64 KB");Profile next=new Gson().fromJson(json,Profile.class);if(next==null)throw new IllegalArgumentException("Empty profile");next.validate();profile=next;buffs.clear();restoredPrayerTiming=null;offensive.reset();targetNetwork=null;lastTargetState=null;targetLearning="Neural target model not seeded.";actionLearning.clear();runLearning.clear();pendingGear.clear();special.configure(next);corrections.clear();lastSituation=null;status="Orion profile configured. Live verification still required.";}
 static String template(){Profile p=new Profile();for(String key:Arrays.asList("overload","enhance","aid","revitalisation"))p.supplies.put(key,new ArrayList<>());return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(p);}
 private final OlmAttemptLifecycle attempt=new OlmAttemptLifecycle();
 private String attemptId,attemptOutcome="STOPPED";private int lastCheckpointTick;
 java.nio.file.Path attemptRoot=CombatRuntime.projectRoot().toPath();
 private final java.util.concurrent.atomic.AtomicInteger pendingAttemptWrites=new java.util.concurrent.atomic.AtomicInteger();
 private volatile boolean savingAttempt;private volatile String retryAttempt;
 private static final java.util.concurrent.ExecutorService ATTEMPT_WRITER=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"olm-local-attempt-save");t.setDaemon(true);return t;});
 private static final Thread ATTEMPT_SHUTDOWN_HOOK=new Thread(()->{ATTEMPT_WRITER.shutdown();try{ATTEMPT_WRITER.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}},"olm-attempt-flush");
 static {Runtime.getRuntime().addShutdownHook(ATTEMPT_SHUTDOWN_HOOK);}
 static void finishAttemptWrites()throws InterruptedException{ATTEMPT_WRITER.shutdown();if(!ATTEMPT_WRITER.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Attempt writer did not finish; reload cancelled");try{Runtime.getRuntime().removeShutdownHook(ATTEMPT_SHUTDOWN_HOOK);}catch(IllegalStateException ignored){}}
 void requireSavedForReload(){if(retryAttempt!=null)throw new IllegalStateException("Attempt save failed. Retry save before reloading the companion");}
 void startMain(){
  if(running)throw new IllegalStateException("The Olm fighter is already running");
  if(savingAttempt)throw new IllegalStateException("The previous attempt is still being saved; try Run bot again in a moment");
  if(retryAttempt!=null)throw new IllegalStateException("The previous save failed. Use Retry save before starting another attempt");
  Map<Style,List<Integer>> selectedGear=new EnumMap<>(Style.class);
  for(Style style:Arrays.asList(Style.MAGIC,Style.MELEE,Style.RANGED,Style.SPEC))if(!runtime.config.gear.get(style).isEmpty())selectedGear.put(style,new ArrayList<>(runtime.config.gear.get(style)));
  if(profile==null){
   final String saved;try{saved=OlmAttemptStore.latest(attemptRoot);}catch(java.io.IOException e){throw new IllegalStateException("Cannot restore previous learning: "+e.getMessage(),e);}
   java.nio.file.Path savedProfile=attemptRoot.resolve("profiles/olm-profile.json"),latestAttempt=OlmAttemptStore.directory(attemptRoot).resolve("latest.json");boolean preferProfile=false;try{preferProfile=java.nio.file.Files.isRegularFile(savedProfile)&&(saved==null||!java.nio.file.Files.exists(latestAttempt)||java.nio.file.Files.getLastModifiedTime(savedProfile).compareTo(java.nio.file.Files.getLastModifiedTime(latestAttempt))>0);}catch(java.io.IOException e){throw new IllegalStateException("Cannot inspect saved profile",e);}if(preferProfile){loadSavedProfile();}else if(saved!=null){try{loadPreset(OlmAttemptStore.recover(attemptRoot,saved));}catch(java.io.IOException e){throw new IllegalStateException("Cannot save recovered attempt",e);}presetStatus="Previous attempt restored; continuing its learned checkpoint.";}
   else {loadObservedSetup();}
  }
  // The main runner replaces the former observed test, including live door discovery.
  // Keep the selected setup and learned checkpoint; do not reload or reseed it here.
  selectedGear.forEach((style,ids)->runtime.config.gear.put(style,new ArrayList<>(ids)));
  profile.provisionalTest=true;
  boolean seed=targetNetwork==null;start();if(seed)seedTargetNetwork();
 }
 void startObservedTest(){loadObservedSetup();start();seedTargetNetwork();}
 private void loadObservedSetup(){
  if(runtime.demonstration.recording)throw new IllegalStateException("Stop and save your demonstration before starting the bot.");
  java.io.File file=new java.io.File(CombatRuntime.projectRoot(),"profiles/olm-demo2-candidate.json");
  final String json;try{json=new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new IllegalStateException("Cannot load observed profile: "+file.getAbsolutePath(),e);}
  configure(json);
  profile.provisionalTest=true;
  applyObservedGear(json);profile.provisionalTest=true;
 }
 private void seedTargetNetwork(){
  final String seedAttempt=attemptId;final Profile selected=profile;targetLearning="Training target imitation from your second demonstration locally...";
  java.util.concurrent.CompletableFuture.runAsync(()->{try{
   java.io.File file=new java.io.File(System.getProperty("user.home"),"Documents/orion-chambers-demonstration2.json");if(file.length()>16*1024*1024)throw new IllegalArgumentException("Demonstration too large");
   ChambersDemonstration.Saved data;try(java.io.Reader reader=new java.io.InputStreamReader(new java.io.FileInputStream(file),java.nio.charset.StandardCharsets.UTF_8)){data=new Gson().fromJson(reader,ChambersDemonstration.Saved.class);}
   Map<String,int[]> approaches=OlmArenaPlan.learn(data,selected);OlmTargetNetwork learned=new OlmTargetNetwork();learned.seed(data,selected);runtime.thread.invoke(()->{if(running&&profile==selected&&java.util.Objects.equals(seedAttempt,attemptId)){selected.mageApproaches.putAll(approaches);targetNetwork=learned;runLearning.attach(learned);targetLearning="Target model seeded. Suggestions only; baseline controls actions.";}});
  }catch(Exception error){runtime.thread.invoke(()->{if(profile==selected)targetLearning="Target training unavailable: "+error.getClass().getSimpleName()+". Basic fighter continues.";});}});
 }
 void start(){
  if(savingAttempt||retryAttempt!=null)throw new IllegalStateException("Wait for the pending attempt save, or use Retry save after a failure");
  if(attempt.active())throw new IllegalStateException("Stop the current attempt before starting another");
  if(profile==null)throw new IllegalStateException("Configure the Olm baseline profile in Advanced settings first. No Orion IDs have been guessed.");
  if(!profile.provisionalTest&&(!profile.verifiedForOrion||!profile.magicAutocastVerified))throw new IllegalStateException("Verify this custom profile and magic/autocast settings before Run bot. The default observed setup is available when no custom profile is loaded.");
  if(runtime.demonstration.recording)throw new IllegalStateException("Stop the manual demonstration before running the fighter");
  if(!raid())throw new IllegalStateException("Player must be alive inside a configured Chambers instance");
  if(!profile.provisionalTest&&!inRoom()&&profile.entryObjectId<0)throw new IllegalStateException("Entry action was not captured. Configure the real entry object, or enter manually before Run bot.");
  for(Style style:Arrays.asList(Style.MAGIC,Style.MELEE,Style.RANGED))if(runtime.config.gear.get(style).isEmpty())throw new IllegalStateException("Capture the "+style+" equipment preset first");
  overheadRestore.reset();healingWatch.reset();strategy.reset();if(targetNetwork==null)targetNetwork=new OlmTargetNetwork();runLearning.start();runLearning.attach(targetNetwork);entered=false;potions.reset(client.getTickCount());entryFlow.reset();entryGatePosition=entryOutsidePosition=null;entryCombatObserved=false;entryDialoguePending=false;pendingSpecial=false;lastSituation=null;previousHp=-1;lastRequest=-100;lastTick=-1;observations.clear();
  phases.reset();offensive.reset();attackTiming.reset();gearBatch.reset();entryPrayerTick=-1000;completedPairs=0;transitionPending=false;transitionTile=null;lastMageSpawn=null;deadActors.clear();
  special.start();lastAttackRequest=-100;lastCycle=-1;lastMechanicCycle=-1000;cachedMechanicAction=false;
  desiredTarget="magic";lastObservedPhase=OlmStrategy.Phase.UNKNOWN;
  StringBuilder maps=new StringBuilder();profile.normalProjectiles.forEach((id,style)->maps.append(id).append('=').append(style).append('\n'));
  OlmMechanicAdapter.effectiveOrbs(profile.mechanics).forEach((id,colour)->{if(!profile.normalProjectiles.containsKey(id))maps.append(id).append('=').append(orbStyle(colour)).append('\n');});
  Set<Integer> roles=new HashSet<>(profile.heads);roles.addAll(profile.mageHands);roles.addAll(profile.meleeHands);
  OlmPrayerLearning.Checkpoint prayerTiming=restoredPrayerTiming!=null?restoredPrayerTiming:runtime.chambers.learning.checkpoint();runtime.chambers.configure(ChambersController.parse(join(profile.olmRegions),join(roles),maps.toString(),5));runtime.chambers.learning.restore(prayerTiming);runtime.chambers.start();runtime.chambers.tolerant=true;runtime.chambers.trustedContext=profile.provisionalTest;if(runtime.chambers.encounterVerified()&&client.getBoostedSkillLevel(Skill.PRAYER)>0)runtime.chambers.arm();restoredPrayerTiming=null;
  raidFinish.reset();running=true;observing=true;attempt.start(client.getTickCount());attemptId=java.util.UUID.randomUUID().toString();attemptOutcome="RUNNING";lastCheckpointTick=client.getTickCount();status="Olm fighter active; learning and saving attempts locally. Focus the game.";checkpointAttempt(false);
  if(profile.mageApproaches.isEmpty())loadArenaPlan();
 }
 private void loadArenaPlan(){final Profile selected=profile;java.util.concurrent.CompletableFuture.runAsync(()->{try{java.io.File file=new java.io.File(System.getProperty("user.home"),"Documents/orion-chambers-demonstration2.json");if(file.length()>16*1024*1024)return;ChambersDemonstration.Saved data;try(java.io.Reader reader=new java.io.InputStreamReader(new java.io.FileInputStream(file),java.nio.charset.StandardCharsets.UTF_8)){data=new Gson().fromJson(reader,ChambersDemonstration.Saved.class);}Map<String,int[]> plan=OlmArenaPlan.learn(data,selected);runtime.thread.invoke(()->{if(profile==selected){selected.mageApproaches.putAll(plan);note("Loaded "+plan.size()+" observed mage approach positions; movement still checks current collision and hazards.");}});}catch(Exception error){runtime.thread.invoke(()->{if(profile==selected)note("Recorded mirrored approach positions unavailable; waiting for observed targets instead of inventing coordinates.");});}});}
 void stop(String reason){runLearning.finish(false);running=false;runtime.chambers.armed=false;runtime.chambers.tolerant=false;runtime.chambers.trustedContext=false;if(attempt.end()){if(attemptOutcome.equals("RUNNING"))attemptOutcome=reason.equals("Logout")?"LOGOUT":reason.equals("Closed")?"CLOSED":"STOPPED";checkpointAttempt(true);}special.pause();pendingGear.clear();status="Actions stopped: "+reason+". Observer "+(observing?"continues":"idle")+".";potions.cancel();}
 private void endAttempt(String outcome,String reason){if(!attempt.active())return;if(outcome.equals("COMPLETED")&&profile.finishRaidAutomatically){if(!raidFinish.active()){attemptOutcome="COMPLETED";runLearning.bossDefeated();raidFinish.start(client.getTickCount());finishStartedInInstance=client.isInInstancedRegion();runtime.chambers.armed=false;special.pause();status="Olm defeated; looking for the reward chest.";checkpointAttempt(true);}return;}attemptOutcome=outcome;if(outcome.equals("DEATH"))runLearning.failed();else if(outcome.equals("COMPLETED"))runLearning.bossDefeated();stop(reason);}
 private String attemptJson(){com.google.gson.JsonObject data=presetData(true);data.remove("lootConfirmation");com.google.gson.JsonObject meta=new com.google.gson.JsonObject();meta.addProperty("id",attemptId);meta.addProperty("outcome",attemptOutcome);meta.addProperty("magicClears",phases.magicKills());meta.addProperty("meleeClears",phases.meleeKills());meta.addProperty("inferredMagicClears",phases.recoveredMagic());meta.addProperty("inferredMeleeClears",phases.recoveredMelee());meta.addProperty("lastPhase",lastObservedPhase.name());meta.addProperty("entered",entered);meta.addProperty("entryStage",entryFlow.stage().name());meta.addProperty("entryDialoguePending",entryDialoguePending);meta.addProperty("mechanicDecision",special.decisionStatus());meta.add("hazardDiagnostics",new Gson().toJsonTree(special.diagnostics()));meta.addProperty("lastDecision",status.length()>300?status.substring(0,300):status);meta.addProperty("savedAtUtc",java.time.Instant.now().toString());meta.addProperty("meaning","RUNNING is a recovery checkpoint, not a completed fight. COMPLETED means observed head death, not confirmed chest loot.");data.add("attempt",meta);return new Gson().toJson(data);}
 private void checkpointAttempt(boolean terminal){if(attemptId==null||profile==null||savingAttempt&&!terminal)return;final String json;try{json=attemptJson();}catch(RuntimeException e){presetStatus="Attempt snapshot failed; learning retained in memory.";return;}saveAttempt(json);}
 private void saveAttempt(String json){final java.nio.file.Path root=attemptRoot;final String id=attemptId;pendingAttemptWrites.incrementAndGet();savingAttempt=true;presetStatus="Saving local attempt...";ATTEMPT_WRITER.execute(()->{try{java.nio.file.Path path=OlmAttemptStore.save(root,id,json);retryAttempt=null;presetStatus="Saved attempt: "+path.toAbsolutePath();}catch(Exception e){retryAttempt=json;presetStatus="Attempt save failed; learning retained. Use Retry save.";}finally{savingAttempt=pendingAttemptWrites.decrementAndGet()>0;}});}
 boolean attemptSaving(){return savingAttempt;}
 void retryAttemptSave(){if(savingAttempt)return;if(retryAttempt==null)throw new IllegalStateException("No failed attempt save to retry");saveAttempt(retryAttempt);}
 void close(){stop("Closed");offensive.reset();restoredPrayerTiming=null;observing=false;observations.clear();corrections.clear();seen.clear();special.clear();actionLearning.clear();runLearning.clear();targetNetwork=null;lastTargetState=null;pendingGear.clear();trace="";profile=null;}
 void observe(){observing=true;status="Observer active. No action output unless Run bot is started.";}
 void applyObservedGear(String json){if(running)throw new IllegalStateException("Stop Olm before applying gear");com.google.gson.JsonObject root=new com.google.gson.JsonParser().parse(json).getAsJsonObject();if(!root.has("observedGear"))throw new IllegalArgumentException("No observed gear in this profile");Map<Style,List<Integer>> gear=new EnumMap<>(Style.class);for(Map.Entry<String,com.google.gson.JsonElement> entry:root.getAsJsonObject("observedGear").entrySet()){Style style=Style.valueOf(entry.getKey());if(!Arrays.asList(Style.MAGIC,Style.MELEE,Style.RANGED,Style.SPEC).contains(style))throw new IllegalArgumentException("Unsupported gear style");List<Integer> ids=new ArrayList<>();for(com.google.gson.JsonElement value:entry.getValue().getAsJsonArray()){int id=value.getAsInt();if(id<=0||ids.size()>=14)throw new IllegalArgumentException("Invalid observed gear");ids.add(id);}gear.put(style,ids);}gear.forEach((style,ids)->runtime.config.gear.put(style,new ArrayList<>(ids)));status="Observed gear copied into equipment presets. Verify it against your inventory before running.";}
 void manualPhase(int phase){manualPhase=phase;}
 void correctTarget(String target){if(lastSituation==null)throw new IllegalStateException("No current attack decision to correct");if(!Arrays.asList("magic","melee","head").contains(target))throw new IllegalArgumentException("Unsupported correction");if(corrections.size()>=128&&!corrections.containsKey(lastSituation))throw new IllegalStateException("Correction limit reached");corrections.put(lastSituation,target);if(targetNetwork!=null&&lastTargetState!=null)targetNetwork.correct(lastTargetState,target);pendingSpecial=false;note("User correction learned: target "+target+" for context "+lastSituation+". Not independently validated.");}
 private static String situation(OlmStrategy.View s){return s.phase+":"+s.magicAlive+":"+s.meleeAlive+":"+s.headAlive+":"+(s.magicHp/20)+":"+(s.meleeHp/20);}
 private static String orbStyle(OlmSpecials.Orb colour){switch(colour){case PURPLE:return "MAGIC";case RED:return "MELEE";default:return "RANGED";}}
 void clientCycle(){if(observing&&client.getGameState()==GameState.LOGGED_IN)try{special.observe();tick(runtime.ticks.fresh());}catch(RuntimeException error){status="Movement observation temporarily unavailable; observer will retry.";}}
 void actorDeath(Actor actor){if(!observing||profile==null)return;if(actor==client.getLocalPlayer()){endAttempt("DEATH","Player death observed");return;}if(!(actor instanceof NPC))return;NPC npc=(NPC)actor;if(running&&profile.heads.contains(npc.getId())&&phase(npc,alive(target(profile.mageHands))||alive(target(profile.meleeHands)))==OlmStrategy.Phase.HEAD){endAttempt("COMPLETED","Olm head death observed");return;}boolean magic=profile.mageHands.contains(npc.getId()),melee=profile.meleeHands.contains(npc.getId());if(!magic&&!melee)return;if(deadActors.size()<128)deadActors.add(npc);WorldPoint p=WorldPoint.fromLocalInstance(client,npc.getLocalLocation());phases.observe(magic,npc.getIndex(),p.getX(),p.getY(),false,true);note("Observed "+(magic?"mage":"melee")+" hand death event.");}
 void npcSpawned(NPC npc){deadActors.remove(npc);}
 void animation(Actor actor){if(!running||profile==null||actor==null||actor!=client.getLocalPlayer()||!(actor.getInteracting() instanceof NPC))return;NPC npc=(NPC)actor.getInteracting();if(!profile.mageHands.contains(npc.getId())&&!profile.meleeHands.contains(npc.getId())&&!profile.heads.contains(npc.getId()))return;Integer speed=profile.attackAnimations.getOrDefault(actor.getAnimation(),runtime.config.attackAnimations.get(actor.getAnimation()));if(speed!=null){attackTiming.observed(client.getTickCount(),speed);note("Observed configured attack animation "+actor.getAnimation()+" at server tick "+client.getTickCount()+"; cooldown "+speed+" ticks.");}}
 void projectile(ProjectileMoved event){projectile(event.getProjectile());if(observing)special.projectile(event);}
 String specialStatus(){OlmTargetNetwork model=targetNetwork;return buffs.status(client.getTickCount(),java.time.Instant.now())+"\n"+gearBatch.status(client.getTickCount())+"\n"+attackTiming.status(client.getTickCount())+" Observed GameTick interval ~"+runtime.ticks.observedPeriodMs()+" ms; remaining estimate "+runtime.ticks.remainingMs()+" ms.\n"+presetStatus+"\n"+runLearning.status()+"\n"+runtime.combatUi.status+"\n"+offensive.status+"\n"+runtime.chambers.learning.status()+"\n"+special.status()+"\n"+actionLearning.status()+"\n"+targetLearning+(model==null?"":"\n"+model.status());}
 void specialFeedback(boolean good){special.feedback(good);}
 String exportSpecialLearning(){return special.exportLearning();}
 void restoreSpecialLearning(String json){special.restoreLearning(json);}
 String presetStatus(){return presetStatus;}
 void graphicChanged(net.runelite.api.events.GraphicChanged event){if(observing){special.actorGraphic(event.getActor(),true);healingWatch.graphic(event.getActor(),client.getTickCount());}}
 String movementStatus(){return special.decisionStatus();}
 String briefStatus(){OlmTargetNetwork model=targetNetwork;return (running?"Learning automatically: ":"Last attempt: ")+runLearning.samples()+" experiences; model "+(model==null?0:model.updateCount())+" neural updates.";}
 private com.google.gson.JsonObject presetData(boolean includeRun){
  if(profile==null)throw new IllegalStateException("No Olm profile loaded");
  Gson gson=new Gson();com.google.gson.JsonObject root=gson.toJsonTree(profile).getAsJsonObject();root.addProperty("schema",OlmPresetStore.SCHEMA);
  Map<String,List<Integer>> gear=new LinkedHashMap<>();for(Style style:Arrays.asList(Style.MAGIC,Style.MELEE,Style.RANGED,Style.SPEC))gear.put(style.name(),new ArrayList<>(runtime.config.gear.get(style)));root.add("observedGear",gson.toJsonTree(gear));
  root.add("targetCorrections",gson.toJsonTree(corrections));root.add("attackSpeeds",gson.toJsonTree(runtime.config.speeds));root.add("actionTiming",gson.toJsonTree(actionLearning.checkpoint()));root.add("movementLearning",new com.google.gson.JsonParser().parse(special.exportLearning()));
  if(targetNetwork!=null)root.add("targetNetwork",gson.toJsonTree(targetNetwork.checkpoint()));
  root.add("prayerTiming",gson.toJsonTree(runtime.chambers.learning.checkpoint()));
  if(includeRun)root.add("prayerOutcomes",gson.toJsonTree(runtime.chambers.learning.outcomes()));
  if(includeRun){root.addProperty("lootConfirmation","Loot confirmation is recorded in runExperience. A chest click alone is not confirmation.");root.add("runExperience",gson.toJsonTree(runLearning.export()));}
  return root;
 }
 String profileText(){return profile==null?template():new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(presetData(false));}
 void saveProfile(){
  if(running)throw new IllegalStateException("Stop the run before saving its setup");final String json=profileText();OlmPresetStore.parse(json);presetStatus="Saving Olm profile...";
  ATTEMPT_WRITER.execute(()->{try{presetStatus="Saved profile: "+OlmPresetStore.saveProfile(attemptRoot,json).toAbsolutePath();}catch(Exception e){presetStatus="Profile save failed: "+e.getMessage();}});
 }
 void loadSavedProfile(){try{loadPreset(OlmAttemptStore.read(attemptRoot.resolve("profiles/olm-profile.json")));}catch(java.io.IOException e){throw new IllegalStateException("Cannot load saved Olm profile: "+e.getMessage(),e);}}
 private Map<Integer,Integer> inventoryAmounts(){Map<Integer,Integer> amounts=new HashMap<>();ItemContainer container=client.getItemContainer(InventoryID.INVENTORY);if(container!=null)for(net.runelite.api.Item item:container.getItems())if(item!=null&&item.getId()>0)amounts.merge(item.getId(),item.getQuantity(),Integer::sum);return amounts;}
 private List<net.runelite.api.widgets.Widget> widgetTree(net.runelite.api.widgets.Widget root){List<net.runelite.api.widgets.Widget> result=new ArrayList<>();Set<net.runelite.api.widgets.Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());ArrayDeque<net.runelite.api.widgets.Widget> todo=new ArrayDeque<>();todo.add(root);while(!todo.isEmpty()&&result.size()<2000){net.runelite.api.widgets.Widget w=todo.removeFirst();if(!seen.add(w))continue;result.add(w);for(net.runelite.api.widgets.Widget[] children:new net.runelite.api.widgets.Widget[][]{w.getChildren(),w.getDynamicChildren(),w.getStaticChildren(),w.getNestedChildren()})if(children!=null)for(net.runelite.api.widgets.Widget child:children)if(child!=null)todo.add(child);}return result;}
 List<net.runelite.api.widgets.Widget> rewardWidgets(){net.runelite.api.widgets.Widget recorded=profile.rewardContainerWidgetId>=0?client.getWidget(profile.rewardContainerWidgetId):null;if(!VisibleCombatUi.effectivelyVisible(recorded))return Collections.emptyList();net.runelite.api.widgets.Widget root=recorded;Set<net.runelite.api.widgets.Widget> ancestors=Collections.newSetFromMap(new IdentityHashMap<>());while(root.getParent()!=null&&ancestors.add(root)&&ancestors.size()<32)root=root.getParent();return widgetTree(root);}
 private void finishRaid(boolean fresh){
  int tick=client.getTickCount();WorldPoint templatePosition=WorldPoint.fromLocalInstance(client,client.getLocalPlayer().getLocalLocation());boolean stillInRaid=(!finishStartedInInstance||client.isInInstancedRegion())&&templatePosition!=null&&profile.chamberRegions.contains(templatePosition.getRegionID());if(raidFinish.exited(stillInRaid)){attemptOutcome="LOOTED_EXITED";stop("Loot collected and raid exit observed; learning saved for next run");return;}
  if(raidFinish.timedOut(tick)){stop("Post-raid timed out at "+raidFinish.stage+"; learning saved, loot/exit not assumed");return;}
  if(!fresh||client.getCanvas()==null||!client.getCanvas().isFocusOwner()){status="Post-raid: waiting for game focus and fresh ticks.";return;}
  raidFinish.observeLoot(tick,inventoryAmounts());
  List<net.runelite.api.widgets.Widget> rewards=rewardWidgets();List<net.runelite.api.widgets.WidgetItem> items=new ArrayList<>();net.runelite.api.widgets.Widget container=profile.rewardContainerWidgetId>=0?client.getWidget(profile.rewardContainerWidgetId):null;if(VisibleCombatUi.effectivelyVisible(container)&&container.getWidgetItems()!=null)for(net.runelite.api.widgets.WidgetItem item:container.getWidgetItems())if(item.getId()>0&&item.getQuantity()>0)items.add(item);
  if(raidFinish.emptyRewardsObserved(!rewards.isEmpty()&&container.getWidgetItems()!=null,items.isEmpty())){runLearning.finish(true);checkpointAttempt(true);raidFinish.lastRequest=tick-5;}if(!raidFinish.due(tick))return;
  if(!items.isEmpty()){
   // The window may contain several rewards. Collect each before leaving.
   raidFinish.stage=OlmRaidFinish.Stage.LOOT;net.runelite.api.widgets.WidgetItem reward=items.get(0);Map<Integer,Integer> amounts=inventoryAmounts();int occupied=0;ItemContainer bag=client.getItemContainer(InventoryID.INVENTORY);if(bag!=null)for(net.runelite.api.Item item:bag.getItems())if(item!=null&&item.getId()>0)occupied++;
   ItemComposition rewardDefinition=client.getItemDefinition(reward.getId());boolean stacks=rewardDefinition!=null&&rewardDefinition.isStackable()&&amounts.containsKey(reward.getId());if(occupied>=28&&!stacks){List<Integer> aids=profile.supplies.get("aid");net.runelite.api.Item aid=Inventory.getFirst(aids.stream().mapToInt(Integer::intValue).toArray());if(aid==null||aid.getName()==null||!aid.getName().toLowerCase(Locale.ROOT).contains("xeric")||!aid.getName().toLowerCase(Locale.ROOT).contains("aid")||!aid.hasAction("Drop")){status="Reward inventory full: no droppable Xeric's aid; keep remaining loot in the chest.";return;}if(!runtime.combatUi.ready(Tab.INVENTORY))return;if(!VisibleCombatUi.inventoryVisible(client,aid))return;aid.interact("Drop");raidFinish.lastRequest=tick;status="Dropping one Xeric's aid to make reward space.";return;}
   if(runtime.combatUi.clickBounds(client,reward.getCanvasBounds())){raidFinish.rewardRequested(tick,amounts,reward.getId());status="Collecting a visible chest reward; awaiting inventory increase.";}return;
  }
  if(raidFinish.stage==OlmRaidFinish.Stage.EXIT){
   if(!rewards.isEmpty()){for(net.runelite.api.widgets.Widget w:rewards)if(w.getActions()!=null)for(String a:w.getActions())if("Close".equalsIgnoreCase(a)&&runtime.combatUi.clickWidget(client,w)){raidFinish.lastRequest=tick;status="Closing the empty reward window before leaving.";return;}status="Rewards collected; waiting for reward window to close.";return;}
   if(client.getScene()!=null&&net.unethicalite.api.widgets.Dialog.canContinue()){if(runtime.combatUi.claim()){net.unethicalite.api.widgets.Dialog.continueSpace();raidFinish.lastRequest=tick;}return;}
   if(client.getScene()!=null&&net.unethicalite.api.widgets.Dialog.isViewingOptions()){for(net.runelite.api.widgets.Widget w:net.unethicalite.api.widgets.Dialog.getOptions()){String text=w.getText();if(text!=null&&text.toLowerCase(Locale.ROOT).matches(".*(yes|leave|exit).*" )&&runtime.combatUi.clickWidget(client,w)){raidFinish.lastRequest=tick;return;}}status="Waiting for a recognized raid-exit confirmation.";return;}
  }
  boolean chest=raidFinish.stage!=OlmRaidFinish.Stage.EXIT;TileObject best=null;String action=null;int distance=Integer.MAX_VALUE;for(TileObject object:TileObjects.getAll(o->true)){int d=object.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation());String a=OlmRaidFinish.objectAction(chest,chest?profile.rewardChestObjectId:profile.raidExitObjectId,object.getId(),object.getName(),object.getActions());if(a!=null&&d<distance&&d<=32){best=object;distance=d;action=a;}}
  if(best==null){status=chest?"Olm defeated: looking for a usable reward chest.":"Loot collected: looking for a usable raid exit.";return;}
  if(!runtime.combatUi.claim())return;best.interact(action);if(chest)raidFinish.chestRequested(tick,inventoryAmounts());else raidFinish.lastRequest=tick;status=chest?"Opening reward chest; waiting for its items.":"Leaving raid; waiting for observed exit.";
 }
 void applyProfileOrPreset(String json){com.google.gson.JsonObject root=new com.google.gson.JsonParser().parse(json).getAsJsonObject();if(root.has("schema")&&OlmPresetStore.SCHEMA.equals(root.get("schema").getAsString()))loadPreset(json);else configure(json);}
 void loadPreset(String json){
  if(running)throw new IllegalStateException("Stop the fighter before loading a saved run");
  OlmPresetStore.Loaded next=OlmPresetStore.parse(json);Profile parsed=new Gson().fromJson(json,Profile.class);
  configure(new Gson().toJson(parsed));applyObservedGear(next.json);if(next.movement!=null)special.restoreLearning(next.movement);corrections.putAll(next.corrections);next.speeds.forEach((style,speed)->runtime.config.speeds.put(Style.valueOf(style),speed));if(next.timing!=null)actionLearning.restore(next.timing);targetNetwork=next.network;
  restoredPrayerTiming=next.prayerTiming;if(next.prayerTiming!=null)runtime.chambers.learning.restore(next.prayerTiming);
  targetLearning=targetNetwork==null?"Preset loaded without a neural checkpoint.":"Saved neural target model restored; no demonstration retraining required.";presetStatus="Learned preset loaded. Run bot uses this setup.";
 }
 void confirmLootAndSave(){
  if(savingPreset)throw new IllegalStateException("A preset is already being saved");if(runLearning.samples()==0)throw new IllegalStateException("No bot attack experiences recorded in this run yet");
  runLearning.finish(true);stop("User confirmed chest looted");String json=new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(presetData(true));
  savingPreset=true;presetStatus="Saving confirmed run locally...";
  java.util.concurrent.CompletableFuture.runAsync(()->{try{java.nio.file.Path saved=OlmPresetStore.save(CombatRuntime.projectRoot().toPath(),json);presetStatus="Saved preset: "+saved.toAbsolutePath();}catch(Exception e){presetStatus="Preset save failed: "+e.getMessage()+". Learning remains in memory; retry saving.";}finally{savingPreset=false;}});
 }
 void projectile(Projectile p){if(!observing||p==null||p.getInteracting()!=client.getLocalPlayer()||seen.contains(p)||seen.size()>=128)return;seen.add(p);if(running&&profile!=null&&(profile.normalProjectiles.containsKey(p.getId())||OlmMechanicAdapter.effectiveOrbs(profile.mechanics).containsKey(p.getId())))entryCombatObserved=true;OlmSpecials.Orb orb=profile==null?null:OlmMechanicAdapter.effectiveOrbs(profile.mechanics).get(p.getId());String type=profile==null?null:profile.specialProjectiles.get(p.getId());if(orb!=null)note("Orb "+orb+" -> "+OlmSpecials.prayer(orb));else if(type!=null)note("Special projectile "+p.getId()+": "+type+". Calibrated mechanics mappings determine the response; observer continues.");else if(profile==null||!profile.normalProjectiles.containsKey(p.getId()))note("Unclassified projectile "+p.getId()+"; observer continues without inventing a response.");}
 void tick(boolean fresh){
  if(!observing)return;
  try{
   if(raidFinish.active()&&client.getGameState()==GameState.LOADING){status="Post-raid: waiting for area loading.";return;}if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null){if(running)stop("Logout");return;}
   int tick=client.getTickCount(),cycle=client.getGameCycle();if(cycle==lastCycle)return;lastCycle=cycle;boolean newTick=tick!=lastTick;lastTick=tick;
   seen.removeIf(p->p.getEndCycle()<=client.getGameCycle());
   Player self=client.getLocalPlayer();if(newTick){int hp=client.getBoostedSkillLevel(Skill.HITPOINTS);note("Tick "+tick+" HP "+hp+" prayer "+client.getBoostedSkillLevel(Skill.PRAYER)+" animation "+self.getAnimation());if(previousHp>=0&&hp<previousHp)note("HP loss observed: "+(previousHp-hp)+". Cause unassigned; not automatically treated as a wrong decision.");previousHp=hp;}
   observeHands();
   if(!pendingGear.isEmpty()){Set<Integer> wornNow=runtime.adapter.container(InventoryID.EQUIPMENT);java.util.Iterator<Map.Entry<Integer,Integer>> gearRequests=pendingGear.entrySet().iterator();while(gearRequests.hasNext()){Map.Entry<Integer,Integer> request=gearRequests.next();if(wornNow.contains(request.getKey())){actionLearning.confirmed("gear",tick-request.getValue());gearRequests.remove();}else if(tick-request.getValue()>=actionLearning.waitTicks("gear",4)){actionLearning.timeout();gearRequests.remove();}}}
   if(!running){if(!newTick)return;WorldPoint pos=WorldPoint.fromLocalInstance(client,self.getLocalLocation());note("Template region "+pos.getRegionID()+" plane "+pos.getPlane()+"; current NPC target "+(self.getInteracting() instanceof NPC?((NPC)self.getInteracting()).getId():"none"));return;}
   if(self.isDead()||client.getBoostedSkillLevel(Skill.HITPOINTS)<=0){note(runtime.chambers.learning.death());endAttempt("DEATH","Player died");return;}if(raidFinish.active()){finishRaid(fresh);return;}if(!raid()){stop("Left configured raid");return;}

   NPC mage=target(profile.mageHands),melee=target(profile.meleeHands),head=target(profile.heads);
   boolean actionable=fresh&&client.getCanvas()!=null&&client.getCanvas().isFocusOwner();
   String progress=self.getWorldLocation().toString()+":"+client.getBoostedSkillLevel(Skill.HITPOINTS)+":"+health(mage)+":"+health(melee)+":"+health(head)+":"+strategy.preparationStep()+":"+entryFlow.stage()+":"+runtime.adapter.container(InventoryID.EQUIPMENT).hashCode();
   if(attempt.observe(tick,actionable,progress)){endAttempt("STALLED","No observed progress for 100 game ticks");return;}
   if(newTick&&tick-lastCheckpointTick>=10){lastCheckpointTick=tick;checkpointAttempt(false);}
   if(phase(head,alive(mage)||alive(melee))==OlmStrategy.Phase.HEAD&&!alive(mage)&&!alive(melee)){for(NPC n:client.getNpcs())if(profile.heads.contains(n.getId())&&n.getWorldLocation().distanceTo(self.getWorldLocation())<=32&&(n.isDead()||n.getHealthRatio()==0)){endAttempt("COMPLETED","Olm head death observed");return;}}
   updateTransition(mage,melee);
   if(newTick)runLearning.observe(tick,percent(Skill.HITPOINTS),health(mage),health(melee),health(head));
   if(!fresh||client.getCanvas()==null||!client.getCanvas().isFocusOwner()){status="Waiting for fresh game ticks / game focus; observer continues.";return;}

   confirmEntry(head!=null?head:mage!=null?mage:melee);
   if(entered&&!profile.provisionalTest&&!inRoom()){stop("Left the configured Olm room");return;}
   if(!runtime.chambers.armed&&runtime.chambers.encounterVerified()&&client.getBoostedSkillLevel(Skill.PRAYER)>0)runtime.chambers.arm();
   observePotion(tick);boolean[] prayerPulsed={false};Runnable prayerPulse=()->{if(!prayerPulsed[0]){prayerPulsed[0]=true;runtime.chambers.pulse(fresh);}};
   OlmStrategy.Phase activePhase=phase(head,alive(mage)||alive(melee));lastObservedPhase=activePhase;
   if(newTick)buffs.observe(runtime.infoBoxes,client.getVarbitValue(Varbits.COX_OVERLOAD_REFRESHES_REMAINING),java.time.Instant.now());
   boolean meleeHealing=alive(melee)&&healingWatch.active(melee,client);
   special.stopMelee(meleeHealing||activePhase==OlmStrategy.Phase.FINAL_HANDS&&alive(mage)&&strategy.withholdMelee()?melee:null);
   boolean attackDue=attackTiming.ready(tick);
   NPC mechanicalTarget=meleeHealing?(alive(mage)&&health(mage)>10?mage:null):activePhase==OlmStrategy.Phase.HEAD?head:desiredTarget.equals("melee")&&alive(melee)?melee:alive(mage)?mage:melee;int mechanicalRange=mechanicalTarget==melee?1:10;
   WorldPoint transitionApproach=transitionPending&&magicSetupActive()?transitionApproach():null;
   if(entered&&(cycle-lastMechanicCycle>=1||special.retrySoon())){cachedMechanicAction=special.handle(activePhase==OlmStrategy.Phase.UNKNOWN?strategy.lastKnownPhase():activePhase,attackDue,attackTiming.walkingAllowed(tick),mechanicalTarget,mechanicalRange,transitionApproach,prayerPulse);lastMechanicCycle=cycle;}if(observedMoveEpoch!=special.moveEpoch()){observedMoveEpoch=special.moveEpoch();attackTiming.movementInterrupted();attackDue=attackTiming.ready(tick);}prayerPulse.run();boolean mechanicAction=entered&&cachedMechanicAction;
   finishEntryDialogue();
   if(runtime.chambers.protectionRequired()&&client.getBoostedSkillLevel(Skill.PRAYER)>0){status="PRAYER FIRST: "+runtime.chambers.priorityReason();return;}

   Prayer observedOverhead=null;for(Prayer prayer:new Prayer[]{Prayer.PROTECT_FROM_MAGIC,Prayer.PROTECT_FROM_MISSILES,Prayer.PROTECT_FROM_MELEE})if(client.isPrayerActive(prayer))observedOverhead=prayer;overheadRestore.observed(observedOverhead);Prayer restore=overheadRestore.desired(observedOverhead!=null,runtime.chambers.incomingPrayer()!=null,client.getBoostedSkillLevel(Skill.PRAYER),cycle);if(entered&&restore!=null&&runtime.combatUi.ready(Tab.PRAYER,100)){net.runelite.api.widgets.Widget widget=client.getWidget(restore.getWidgetInfo());if(VisibleCombatUi.effectivelyVisible(widget)){net.unethicalite.api.widgets.Prayers.toggle(restore);special.action("RESTORE_OVERHEAD_"+restore,null);overheadRestore.requested(cycle);status="Restoring overhead knocked off during the fight: "+restore;return;}}
   if(entered&&client.getVarpValue(173)==0&&client.getEnergy()>0&&cycle-lastRunToggleCycle>=20&&runtime.combatUi.clickWidget(client,client.getWidget(net.runelite.api.widgets.WidgetInfo.MINIMAP_TOGGLE_RUN_ORB)))lastRunToggleCycle=cycle;
   if(special.urgentMovementPending()){status="CRYSTAL DODGE FIRST: "+special.decisionStatus();return;}
   if(cycle<lastRequest)return; // UI gate serializes client actions; retain only the recoverable-error backoff.
   OlmStrategy.View view=new OlmStrategy.View();view.inRaid=true;view.alive=true;view.focused=true;view.tick=tick;view.entered=entered;
   view.hp=client.getBoostedSkillLevel(Skill.HITPOINTS);view.hpPercent=percent(Skill.HITPOINTS);view.prayerPercent=percent(Skill.PRAYER);view.prayerPoints=client.getBoostedSkillLevel(Skill.PRAYER);view.meleeHealing=meleeHealing;view.overloadExpired=buffs.expired("overload",tick,java.time.Instant.now());view.enhanceExpired=buffs.expired("enhance",tick,java.time.Instant.now());view.magicAlive=alive(mage);view.meleeAlive=alive(melee);view.headAlive=alive(head);view.magicHp=health(mage);view.meleeHp=health(melee);
   view.potionPending=potions.pending()||!potions.ready(tick);view.phase=activePhase;view.finishWindowTicks=profile.finalHandFinishWindowTicks;
   TileObject entry=null;if(!entered&&profile.provisionalTest)discoverEntry();if(!entered)for(TileObject object:TileObjects.getAll(profile.entryObjectId))if(object.getWorldLocation().distanceTo(self.getWorldLocation())<=12){entry=object;break;}view.entryAvailable=entry!=null||entryFlow.stage()!=OlmEntryFlow.Stage.FIRST_GATE||net.unethicalite.api.widgets.Dialog.isViewingOptions();if(!entered&&entry==null&&profile.provisionalTest)note("Looking for Olm actors or a nearby usable door/barrier. "+locationDiagnostic());
   if(strategy.preparationStep()==0&&client.getVarbitValue(Varbits.COX_OVERLOAD_REFRESHES_REMAINING)>0)strategy.overloadAlreadyActive();if(strategy.preparationStep()==1&&buffs.active("enhance",java.time.Instant.now()))strategy.enhanceAlreadyActive();OlmStrategy.Choice choice=client.getBoostedSkillLevel(Skill.PRAYER)<=0?new OlmStrategy.Choice(OlmStrategy.Kind.DRINK,"revitalisation","Restore prayer points so protection can be activated"):strategy.choose(view);
   if(choice.kind==OlmStrategy.Kind.ATTACK||choice.kind==OlmStrategy.Kind.SPECIAL){lastTargetState=OlmTargetNetwork.encode(view);OlmTargetNetwork model=targetNetwork;if(model!=null){String predicted=model.predict(view);targetLearning="Neural target: "+(predicted==null?"abstain":predicted)+"; automatic outcome learning active.";}}
   if(choice.kind==OlmStrategy.Kind.ATTACK||choice.kind==OlmStrategy.Kind.SPECIAL){lastSituation=situation(view);if(!meleeHealing&&activePhase!=OlmStrategy.Phase.FINAL_HANDS&&corrections.containsKey(lastSituation))choice=new OlmStrategy.Choice(OlmStrategy.Kind.ATTACK,corrections.get(lastSituation),"Applying your learned target correction");desiredTarget=choice.value;}
   else lastSituation=null;
   if(choice.kind==OlmStrategy.Kind.SPECIAL)pendingSpecial=true;
   status=choice.reason+" | mage clears "+phases.magicKills()+", melee clears "+phases.meleeKills();lastRequest=cycle;
   switch(choice.kind){
    case EQUIP:if(!equip(Style.MAGIC))return;if(profile.offensivePrayersEnabled)offensive.ready(profile.offensivePrayers,Style.MAGIC,runtime.chambers.protectionRequired());return;
    case WAIT:if(entered&&transitionPending)prepareTransition();return;
    case DRINK:
     if(potions.pending()){status="Dose confirmation pending; movement and compatible combat continue.";return;}
     List<Integer> ids=profile.supplies.get(choice.value);Item item=Inventory.getFirst(ids.stream().mapToInt(Integer::intValue).toArray());
     if(item==null||!item.hasAction("Drink")){status="Missing usable "+choice.value+"; waiting for supplies, observer continues.";return;}
     if(!potions.ready(tick)){status="Potion cooldown: "+potions.remaining(tick)+" ticks. Prayer handling remains first.";return;}if(!runtime.combatUi.ready(Tab.INVENTORY)){status="Opening Inventory before drinking.";return;}if(!VisibleCombatUi.inventoryVisible(client,item)){status="Waiting for visible inventory item.";return;}int doses=doseCount(choice.value);item.interact("Drink");potions.requested(choice.value,doses,tick,choice.preparationCredit);note("Requested sip "+choice.value+"; waiting for exactly one dose to be consumed.");return;
    case ENTER: if(!mechanicAction&&prepareEntry())driveEntry(entry);return;
    case SPECIAL:case ATTACK:
     if(choice.value.equals("melee")&&(meleeHealing||activePhase==OlmStrategy.Phase.FINAL_HANDS&&view.magicAlive&&strategy.withholdMelee())){status="Melee attack withheld: healing or parked at ten percent.";return;}
     NPC enemy=choice.value.equals("magic")?mage:choice.value.equals("melee")?melee:head;if(!alive(enemy)||!enemy.hasAction("Attack")){status="Target currently unavailable; waiting and observing.";return;}
     boolean useSpec=shouldSpecial(alive(mage),choice.value.equals("melee")&&alive(melee),Combat.getSpecEnergy(),!runtime.config.gear.get(Style.SPEC).isEmpty());
     Style style=useSpec?Style.SPEC:choice.value.equals("magic")?Style.MAGIC:choice.value.equals("head")?Style.RANGED:Style.MELEE;
     boolean gearConfirmed=equip(style);if(!gearConfirmed&&!gearBatch.prayerPhase())return;
     if(profile.offensivePrayersEnabled&&!offensive.ready(profile.offensivePrayers,style,runtime.chambers.protectionRequired())){status=offensive.status;return;}
     if(!gearConfirmed){status=gearBatch.status(tick)+" Waiting for equipment confirmation after Prayer preparation.";return;}
     if(mechanicAction)return;
     if(!attackDue){status=attackTiming.status(tick);return;}
     if((special.holdingOutside()||special.dangerActive())&&self.getWorldArea().distanceTo(enemy.getWorldArea())>(style==Style.MELEE||style==Style.SPEC?1:10)){status="Wait for safe attack range instead of chasing through known hazards.";return;}
     if(useSpec&&!Combat.isSpecEnabled()){if(!runtime.combatUi.ready(Tab.COMBAT)){status="Opening Combat before special attack.";return;}if(!Combat.isSpecEnabled()){net.runelite.api.widgets.Widget specWidget=client.getWidget(593,36);if(specWidget==null||!specWidget.isVisible()){status="Waiting for visible special attack button.";return;}Combat.toggleSpec();note("Requested special attack toggle; waiting for confirmation.");return;}}
     if((tick!=lastAttackRequest||lastAttackStyle!=style||lastAttackMoveEpoch!=special.moveEpoch())&&(useSpec||self.getInteracting()!=enemy||special.needsAttackRefresh()||special.tickWalking(activePhase)||attackTiming.retry(tick))){if(!runtime.combatUi.claim())return;enemy.interact("Attack");special.action("ATTACK_"+style,null);attackTiming.requested(tick);runLearning.requested(view,choice.value,tick);runLearning.headHealth(health(enemy));lastAttackRequest=tick;lastAttackMoveEpoch=special.moveEpoch();lastAttackStyle=style;special.attacked();note("Requested attack NPC "+enemy.getId()+" using "+style+"; awaiting configured attack animation.");}pendingSpecial=false;return;
    default:return;
   }
  }catch(RuntimeException error){status="Recoverable action error: "+error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage())+". Observer continues; retry is delayed.";lastRequest=client.getGameCycle()+10;note(status);}
 }
 static boolean shouldSpecial(boolean mageAlive,boolean meleeTarget,int energy,boolean hasSpecGear){return !mageAlive&&meleeTarget&&energy>=50&&hasSpecGear;}
 private boolean magicSetupActive(){Prayer boost=OlmOffensivePrayers.selected(profile.offensivePrayers,Style.MAGIC);return runtime.adapter.container(InventoryID.EQUIPMENT).containsAll(runtime.config.gear.get(Style.MAGIC))&&boost!=null&&client.isPrayerActive(boost);}
 private void updateTransition(NPC mage,NPC melee){
  if(alive(mage)){WorldPoint p=WorldPoint.fromLocalInstance(client,mage.getLocalLocation());String spawn=OlmArenaPlan.key(p.getX(),p.getY(),p.getPlane());if(transitionPending&&!spawn.equals(lastMageSpawn)){transitionPending=false;transitionTile=null;desiredTarget="magic";attackTiming.reset();}lastMageSpawn=spawn;}
  int pairs=Math.min(phases.magicKills(),phases.meleeKills());if(pairs>completedPairs&&!alive(mage)&&!alive(melee)){completedPairs=pairs;if(pairs<3){transitionPending=true;transitionTile=OlmArenaPlan.opposite(profile.mageApproaches,lastMageSpawn);desiredTarget="magic";note("Both hands confirmed down; preparing magic for the opposite recorded mage spawn.");}else{transitionPending=false;transitionTile=null;}}
 }
 private WorldPoint transitionApproach(){if(transitionTile==null)transitionTile=OlmArenaPlan.opposite(profile.mageApproaches,lastMageSpawn);if(transitionTile==null)return null;WorldPoint template=new WorldPoint(transitionTile[0],transitionTile[1],transitionTile[2]);if(!client.isInInstancedRegion())return template;WorldPoint best=null;int distance=65;for(WorldPoint p:WorldPoint.toLocalInstance(client,template)){int d=p.distanceTo(client.getLocalPlayer().getWorldLocation());if(d<distance){distance=d;best=p;}}return best;}
 private void prepareTransition(){if(!equip(Style.MAGIC))return;Prayer boost=OlmOffensivePrayers.selected(profile.offensivePrayers,Style.MAGIC);if(boost==null){status="Transition needs a configured magic offensive prayer.";return;}if(!client.isPrayerActive(boost)){offensive.ready(profile.offensivePrayers,Style.MAGIC,runtime.chambers.protectionRequired());status="Transition: preparing magic boost. "+offensive.status;return;}status=transitionApproach()==null?"Magic ready; no recorded opposite approach available yet. Waiting for the next mage hand.":"Magic ready; repositioning toward the opposite recorded mage-hand approach, with hazard checks.";}
 private boolean equip(Style style){
  Set<Integer> worn=runtime.adapter.container(InventoryID.EQUIPMENT);List<Integer> required=runtime.config.gear.get(style);
  if(style==Style.RANGED){Set<Integer> available=new HashSet<>(worn);available.addAll(runtime.adapter.container(InventoryID.INVENTORY));OlmGearSwitch.recogniseHolyCards(required,available,id->{ItemComposition def=client.getItemDefinition(id);return def==null?null:def.getName();});}
  Map<Integer,Integer> upgrades=OlmGearSwitch.recogniseUpgrades(required,worn,runtime.adapter.container(InventoryID.INVENTORY),pendingGear.keySet());if(!upgrades.isEmpty())note("Recognised present ancestral cosmetic upgrade(s) in "+style+" setup: "+upgrades+". Updated setup will be included in the next attempt checkpoint.");
  gearBatch.plan(style,required,worn,pendingGear.keySet());int id=OlmGearSwitch.next(required,worn,pendingGear.keySet());
  if(id>=0){if(!gearBatch.allowed(client.getTickCount(),runtime.ticks.elapsedMs(),runtime.ticks.observedPeriodMs())){status=gearBatch.status(client.getTickCount());return false;}Item item=Inventory.getFirst(id);if(item==null||!item.hasAction("Wield","Wear","Equip")){ItemComposition missing=client.getItemDefinition(id);String name=missing==null?"Unknown item":missing.getName();status="Equipment blocked: "+style+" needs "+name+" ("+id+"). Check weapon charges or update this gear preset. Hazard dodging continues.";return false;}if(!runtime.combatUi.ready(Tab.INVENTORY)){status="Opening Inventory before equipping gear. "+gearBatch.status(client.getTickCount());return false;}if(!VisibleCombatUi.inventoryVisible(client,item)){status="Waiting for visible gear item.";return false;}pendingGear.put(id,client.getTickCount());item.interact("Wield","Wear","Equip");gearBatch.sent(client.getTickCount(),runtime.ticks.elapsedMs(),runtime.ticks.observedPeriodMs());status=gearBatch.status(client.getTickCount());note("Requested equipment "+id+" at client cycle "+client.getGameCycle()+", server tick "+client.getTickCount());return false;}
  boolean confirmed=worn.containsAll(required);if(!confirmed)status="All missing "+style+" items submitted; waiting for observed equipment confirmation.";return confirmed;
 }
 private OlmStrategy.Phase phase(NPC head,boolean handsAlive){OlmStrategy.Phase result=head!=null&&profile.headNpcPhases.containsKey(head.getId())?OlmStrategy.Phase.valueOf(profile.headNpcPhases.get(head.getId())):manualPhase==1?OlmStrategy.Phase.FINAL_HANDS:manualPhase==2?OlmStrategy.Phase.REGULAR:manualPhase==3?OlmStrategy.Phase.HEAD:OlmStrategy.Phase.UNKNOWN;OlmStrategy.Phase counted=phases.phase(handsAlive);return counted==OlmStrategy.Phase.HEAD||counted==OlmStrategy.Phase.FINAL_HANDS&&result!=OlmStrategy.Phase.HEAD?counted:result;}
 private void observeHands(){if(profile==null)return;Player self=client.getLocalPlayer();for(NPC npc:client.getNpcs()){boolean mage=profile.mageHands.contains(npc.getId()),melee=profile.meleeHands.contains(npc.getId());if((mage||melee)&&npc.getWorldLocation().distanceTo(self.getWorldLocation())<=32){WorldPoint pos=WorldPoint.fromLocalInstance(client,npc.getLocalLocation());phases.observe(mage,npc.getIndex(),pos.getX(),pos.getY(),!deadActors.contains(npc)&&!npc.isDead()&&npc.getHealthRatio()!=0,deadActors.contains(npc)||npc.isDead()||npc.getHealthRatio()==0);phases.health(mage,npc.getIndex(),pos.getX(),pos.getY(),health(npc),client.getTickCount(),self.getInteracting()==npc);}}phases.finishFrame(client.getTickCount());}
 private NPC target(Set<Integer> ids){Player self=client.getLocalPlayer();for(NPC npc:client.getNpcs())if(ids.contains(npc.getId())&&npc.getWorldLocation().distanceTo(self.getWorldLocation())<=32&&!deadActors.contains(npc)&&!npc.isDead()&&npc.getHealthRatio()!=0)return npc;return null;}
 private boolean alive(NPC npc){return npc!=null&&!deadActors.contains(npc)&&!npc.isDead()&&npc.getHealthRatio()!=0;}
 private void observePotion(int tick){if(!potions.pending())return;OlmPotionFlow.Result result=potions.observe(doseCount(potions.kind()),tick,profile.potionCooldownTicks);if(result==OlmPotionFlow.Result.CONFIRMED){buffs.consumed(potions.confirmedKind(),tick,java.time.Instant.now());actionLearning.confirmed("drink:"+potions.confirmedKind(),potions.latency(tick));if(potions.preparationCredit())strategy.drinkConfirmed();note("Confirmed one consumed dose of "+potions.confirmedKind()+"; tick-aligned shared potion cooldown is "+profile.potionCooldownTicks+" ticks.");}else if(result==OlmPotionFlow.Result.TIMED_OUT){actionLearning.timeout();note("Sip was not confirmed; preparation did not advance. Retry is deferred, not spammed.");}}
 private int doseCount(String kind){List<Integer> family=profile.supplies.get(kind);ItemContainer inventory=client.getItemContainer(InventoryID.INVENTORY);int count=0;if(inventory!=null)for(Item item:inventory.getItems())if(item!=null){int index=family.indexOf(item.getId());if(index>=0)count+=Math.max(1,family.size()-index)*item.getQuantity();}return count;}
 private boolean inRecordedRoom(){Player p=client.getLocalPlayer();if(p==null||profile==null)return false;WorldPoint t=WorldPoint.fromLocalInstance(client,p.getLocalLocation());return t.getPlane()==profile.plane&&t.getX()>=profile.roomMinX&&t.getX()<=profile.roomMaxX&&t.getY()>=profile.roomMinY&&t.getY()<=profile.roomMaxY;}
 private net.runelite.api.widgets.Widget beginWidget(){
  for(net.runelite.api.widgets.Widget widget:net.unethicalite.api.widgets.Dialog.getOptions())if(widget!=null&&widget.isVisible()&&OlmEntryFlow.beginText(widget.getText()))return widget;
  java.util.ArrayDeque<net.runelite.api.widgets.Widget> queue=new java.util.ArrayDeque<>();net.runelite.api.widgets.Widget[] roots=client.getWidgetRoots();if(roots!=null)for(net.runelite.api.widgets.Widget root:roots)if(root!=null)queue.add(root);
  Set<net.runelite.api.widgets.Widget> visited=Collections.newSetFromMap(new IdentityHashMap<>());while(!queue.isEmpty()&&visited.size()<2048){net.runelite.api.widgets.Widget w=queue.removeFirst();if(!visited.add(w)||!w.isVisible())continue;if(OlmEntryFlow.beginText(w.getText()))return w;for(net.runelite.api.widgets.Widget[] children:new net.runelite.api.widgets.Widget[][]{w.getChildren(),w.getDynamicChildren(),w.getStaticChildren(),w.getNestedChildren()})if(children!=null)for(net.runelite.api.widgets.Widget child:children)if(child!=null)queue.add(child);}
  return null;
 }
 private boolean prepareEntry(){
  Prayer incoming=runtime.chambers.incomingPrayer();if(incoming!=null&&incoming!=Prayer.PROTECT_FROM_MAGIC){status="Entry preparation waits for the incoming "+incoming+" threat; defensive prayer retains priority.";return false;}
  Set<Integer> equipped=runtime.adapter.container(InventoryID.EQUIPMENT);Prayer boost=OlmOffensivePrayers.selected(profile.offensivePrayers,Style.MAGIC);
  OlmEntryPreparation.Need need=OlmEntryPreparation.need(strategy.preparationStep()>=7,equipped.containsAll(runtime.config.gear.get(Style.MAGIC)),client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC),boost!=null&&client.isPrayerActive(boost));
  switch(need){
   case FINISH_POTTING:status="Entry waits for confirmed preparation.";return false;
   case MAGIC_GEAR:status="Preparing magic equipment before entry.";equip(Style.MAGIC);return false;
   case MAGIC_PROTECTION:
    status="Preparing Protect from Magic before entry; awaiting observed activation.";
    if(client.getTickCount()-entryPrayerTick<2)return false;
    if(!runtime.combatUi.ready(Tab.PRAYER,100))return false;net.runelite.api.widgets.Widget prayerWidget=client.getWidget(Prayer.PROTECT_FROM_MAGIC.getWidgetInfo());if(prayerWidget==null||!prayerWidget.isVisible())return false;
    net.unethicalite.api.widgets.Prayers.toggle(Prayer.PROTECT_FROM_MAGIC);entryPrayerTick=client.getTickCount();note("Requested pre-entry Protect from Magic; no readiness credit until active.");return false;
   case MAGIC_BOOST:
    if(boost==null){status="Entry needs a configured magic offensive prayer; offensivePrayers.MAGIC is NONE or missing.";return false;}
    offensive.ready(profile.offensivePrayers,Style.MAGIC,runtime.chambers.protectionRequired());status="Preparing "+boost+" before entry. "+offensive.status;return false;
   default:return true;
  }
 }
 private WorldPoint template(net.runelite.api.coords.LocalPoint local,WorldPoint fallback){return local==null?fallback:WorldPoint.fromLocalInstance(client,local);}
 private static OlmSpecials.Cell cell(WorldPoint p){return p==null?null:new OlmSpecials.Cell(p.getX(),p.getY(),p.getPlane());}
 private void confirmEntry(NPC boss){
  if(entered)return;boolean dialogue=client.getScene()!=null&&(net.unethicalite.api.widgets.Dialog.canContinue()||net.unethicalite.api.widgets.Dialog.isViewingOptions());
  Player self=client.getLocalPlayer();Actor interaction=self.getInteracting();boolean fighting=interaction instanceof NPC&&(profile.heads.contains(((NPC)interaction).getId())||profile.mageHands.contains(((NPC)interaction).getId())||profile.meleeHands.contains(((NPC)interaction).getId()))&&(profile.attackAnimations.containsKey(self.getAnimation())||runtime.config.attackAnimations.containsKey(self.getAnimation()));
  if(entryGatePosition==null&&profile.entryObjectId>=0&&client.getScene()!=null)for(TileObject object:TileObjects.getAll(profile.entryObjectId))if(object.getWorldLocation().distanceTo(self.getWorldLocation())<=12){entryGatePosition=template(object.getLocalLocation(),object.getWorldLocation());break;}
  WorldPoint position=template(self.getLocalLocation(),self.getWorldLocation()),head=boss==null?null:template(boss.getLocalLocation(),boss.getWorldLocation());
  boolean crossed=OlmEntryFlow.pastGate(cell(position),cell(entryGatePosition),cell(head))||OlmEntryFlow.crossedFromOutside(cell(position),cell(entryGatePosition),cell(entryOutsidePosition));
  if(fighting||entryCombatObserved||crossed||!dialogue&&entryFlow.insideEvidence(self.getLocalLocation()!=null&&inRecordedRoom()&&inRoom(),false)){entered=true;entryDialoguePending=dialogue;entryFlow.confirmInside();note("Entry latched from "+(fighting||entryCombatObserved?"observed Olm combat":"gate-side / room evidence")+"; gate interactions disabled for this attempt.");}
 }
 private void finishEntryDialogue(){
  if(!entered||!entryDialoguePending)return;boolean more=net.unethicalite.api.widgets.Dialog.canContinue(),options=net.unethicalite.api.widgets.Dialog.isViewingOptions();
  if(!more&&!options){entryDialoguePending=false;return;}int cycle=client.getGameCycle();if(!entryFlow.insideDialogueDue(cycle))return;
  if(more){if(runtime.combatUi.claim()){net.unethicalite.api.widgets.Dialog.continueSpace();entryFlow.sentContinue(cycle);}return;}
  List<String> text=new ArrayList<>();for(net.runelite.api.widgets.Widget option:net.unethicalite.api.widgets.Dialog.getOptions())text.add(option!=null&&option.isVisible()?option.getText():null);int number=OlmEntryFlow.entryOption(text);
  if(number>=1&&number<=9&&runtime.combatUi.claim()&&net.unethicalite.api.widgets.Dialog.chooseOption(number))entryFlow.sentContinue(cycle);
 }
 private void driveEntry(TileObject gate){
  if(entered||entryFlow.stage()==OlmEntryFlow.Stage.INSIDE)return;
  int cycle=client.getGameCycle();Player player=client.getLocalPlayer();net.runelite.api.widgets.Widget begin=beginWidget();boolean canContinue=net.unethicalite.api.widgets.Dialog.canContinue();boolean dialogue=begin!=null||net.unethicalite.api.widgets.Dialog.isOpen();
  OlmEntryFlow.Action action=entryFlow.next(cycle,gate!=null,begin!=null,canContinue,dialogue,entered);
  if(entryFlow.stage()==OlmEntryFlow.Stage.INSIDE){entered=true;status="Observed entry into the fight.";return;}
  status="Entry: "+entryFlow.stage()+" ("+entryFlow.attempts()+"). Finish chatbox dialogue before another gate action.";
  if(action==OlmEntryFlow.Action.CONTINUE&&runtime.combatUi.claim()){net.unethicalite.api.widgets.Dialog.continueSpace();entryFlow.sentContinue(cycle);note("Pressed Continue for visible entry dialogue; gate clicks remain deferred until dialogue clears.");}
  else if(action==OlmEntryFlow.Action.CHOOSE_OPTION&&net.unethicalite.api.widgets.Dialog.isViewingOptions()){java.util.List<String> options=new ArrayList<>();for(net.runelite.api.widgets.Widget option:net.unethicalite.api.widgets.Dialog.getOptions())options.add(option!=null&&option.isVisible()?option.getText():null);int number=OlmEntryFlow.entryOption(options);if(number>=1&&number<=9&&runtime.combatUi.claim()&&net.unethicalite.api.widgets.Dialog.chooseOption(number)){entryFlow.sentOption(cycle);note("Confirmed visible entry dialogue option "+number+"; waiting for the next dialogue or observed crossing.");}}
  else if(action==OlmEntryFlow.Action.CLICK_BEGIN){
   java.util.List<String> texts=new ArrayList<>();for(net.runelite.api.widgets.Widget option:net.unethicalite.api.widgets.Dialog.getOptions())texts.add(option!=null&&option.isVisible()?option.getText():null);int number=OlmEntryFlow.beginOption(texts);
   boolean numbered=number>=1&&number<=9&&net.unethicalite.api.widgets.Dialog.isViewingOptions();
   if((!entryFlow.keyboardConfirmation()||!numbered)&&runtime.combatUi.clickWidget(client,begin)){entryFlow.sentBegin(cycle);note("Clicked the visible Begin the raid option; waiting for the dialogue to close.");}
   else if(numbered&&runtime.combatUi.claim()&&net.unethicalite.api.widgets.Dialog.chooseOption(number)){entryFlow.sentBegin(cycle);note("Confirmed visible Begin the raid using chatbox option key "+number+"; waiting for observed dismissal. This also covers unavailable click bounds.");}
  }
  else if(action==OlmEntryFlow.Action.CLICK_GATE&&gate!=null&&gate.hasAction(profile.entryAction)&&runtime.combatUi.claim()){if(entryGatePosition==null)entryGatePosition=template(gate.getLocalLocation(),gate.getWorldLocation());if(entryOutsidePosition==null)entryOutsidePosition=template(player.getLocalLocation(),player.getWorldLocation());gate.interact(profile.entryAction);entryFlow.sentGate(cycle);note("Clicked gate "+gate.getId()+" using "+profile.entryAction+"; entry stage "+entryFlow.stage());}
 }
 private static int health(NPC npc){return npc==null||npc.getHealthScale()<=0||npc.getHealthRatio()<0?-1:npc.getHealthRatio()*100/npc.getHealthScale();}
 static String entryAction(String name,String[] actions){
  if(name==null||actions==null)return null;String lower=name.toLowerCase(Locale.ROOT);
  if(!(lower.contains("door")||lower.contains("barrier")||lower.contains("gate")||lower.contains("entrance")))return null;
  for(String wanted:Arrays.asList("Enter","Pass","Open"))for(String action:actions)if(wanted.equalsIgnoreCase(action))return action;return null;
 }
 private void discoverEntry(){
  Player self=client.getLocalPlayer();TileObject best=null;String chosen=null;int distance=13;boolean tied=false;
  for(TileObject object:TileObjects.getAll(o->true)){int d=object.getWorldLocation().distanceTo(self.getWorldLocation());if(d>12)continue;String action=entryAction(object.getName(),object.getActions());if(action==null)continue;if(d<distance){best=object;chosen=action;distance=d;tied=false;}else if(d==distance&&best!=null&&object.getId()!=best.getId())tied=true;}
  if(best!=null&&!tied){profile.entryObjectId=best.getId();profile.entryAction=chosen;note("Discovered nearby "+best.getName()+" ID "+best.getId()+" action "+chosen+". Native interaction only.");}
  else if(tied)note("Multiple equally near door candidates; waiting rather than selecting an arbitrary exit.");
 }
 private String locationDiagnostic(){Player p=client.getLocalPlayer();if(p==null)return "No local player.";WorldPoint t=WorldPoint.fromLocalInstance(client,p.getLocalLocation());Set<Integer> npcs=new TreeSet<>();for(NPC n:client.getNpcs())if(n.getWorldLocation().distanceTo(p.getWorldLocation())<=32&&npcs.size()<32)npcs.add(n.getId());return "Template "+t.getX()+","+t.getY()+","+t.getPlane()+" region "+t.getRegionID()+"; nearby NPC IDs "+npcs;}
 private int percent(Skill skill){return client.getBoostedSkillLevel(skill)*100/Math.max(1,client.getRealSkillLevel(skill));}
 private boolean raid(){Player p=client.getLocalPlayer();if(profile==null||client.getGameState()!=GameState.LOGGED_IN||p==null||p.isDead()||client.getBoostedSkillLevel(Skill.HITPOINTS)<=0)return false;if(profile.provisionalTest)return true;if(!client.isInInstancedRegion())return false;WorldPoint point=WorldPoint.fromLocalInstance(client,p.getLocalLocation());return point.getPlane()==profile.plane&&profile.chamberRegions.contains(point.getRegionID());}
 private boolean inRoom(){Player p=client.getLocalPlayer();if(p==null||profile==null)return false;if(profile.provisionalTest){for(NPC npc:client.getNpcs())if((profile.mageHands.contains(npc.getId())||profile.meleeHands.contains(npc.getId())||profile.heads.contains(npc.getId()))&&npc.getWorldLocation().distanceTo(p.getWorldLocation())<=24&&p.getWorldArea().hasLineOfSightTo(client,npc.getWorldArea()))return true;return false;}WorldPoint t=WorldPoint.fromLocalInstance(client,p.getLocalLocation());return profile.olmRegions.contains(t.getRegionID())&&t.getX()>=profile.roomMinX&&t.getX()<=profile.roomMaxX&&t.getY()>=profile.roomMinY&&t.getY()<=profile.roomMaxY;}
 
 private void note(String text){if(observations.size()>=200)observations.removeFirst();observations.addLast(text);trace=String.join("\n",observations);}
 private static String join(Collection<Integer> ids){StringBuilder b=new StringBuilder();for(int id:ids)b.append(id).append(',');if(b.length()>0)b.setLength(b.length()-1);return b.toString();}
}
