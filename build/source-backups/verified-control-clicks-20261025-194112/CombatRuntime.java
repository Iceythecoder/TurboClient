package owl.nh;

import com.google.inject.Injector;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.*;
import static owl.nh.CombatCore.*;

/** Client-thread event handlers; UI exchanges immutable display strings and explicit commands. */
public final class CombatRuntime {
 public final Client client;public final ClientThread thread;public Settings config=new Settings();
 public final TickManager ticks=new TickManager(System::nanoTime);public final TargetManager target=new TargetManager();
 public final TelemetryManager telemetry=new TelemetryManager();public final AttackManager attacks=new AttackManager();
 public final FreezeManager freeze=new FreezeManager();public final DecisionEngine decisions=new DecisionEngine();
 public final OrionAdapter adapter;public final ActionScheduler scheduler;
 public final LearningAgent agent;public volatile String agentPrompt="",agentNotes="";
 public final NHTrainingConnection nhTraining;
 final ChambersController chambers;
 final ChambersDemonstration demonstration;
 net.runelite.client.ui.overlay.infobox.InfoBoxManager infoBoxes;net.runelite.client.game.ItemManager itemManager;
 final OlmConnectionTrace connectionTrace=new OlmConnectionTrace();final OlmFighter olm;final OlmLoopController olmLoop;
 final VisibleCombatUi combatUi;final KioskPrivacy privacy;final SlayerController slayer;
 public volatile boolean armed;private boolean waitingForFocus,controlAtArm;private long armedAt;public volatile String display="Waiting for login and first GameTick",log="";public volatile String[] overlayLines={"Orion NH: observation only"};
 private int lastDecisionTick=-1;private Protection previous=Protection.UNKNOWN;private Style lastIntent=Style.UNKNOWN;private boolean pendingReevaluation;
 private JFrame panel;private NhOverlay overlay;private volatile boolean overlayRegistered;public volatile String overlayStatus="Not attached";private final EventBus bus;private final OverlayManager overlays;
 private KeyEventDispatcher stopKey;
 private final AutoLockTracker autoLock=new AutoLockTracker();
 public CombatRuntime(Injector injector) {
  this(injector.getInstance(Client.class),injector.getInstance(ClientThread.class),injector.getInstance(EventBus.class),injector.getInstance(OverlayManager.class));
  infoBoxes=injector.getInstance(net.runelite.client.ui.overlay.infobox.InfoBoxManager.class);itemManager=injector.getInstance(net.runelite.client.game.ItemManager.class);
 }
 CombatRuntime(Client client,ClientThread thread,EventBus bus,OverlayManager overlays) {
  this.client=Objects.requireNonNull(client,"Orion game API is unavailable");this.thread=Objects.requireNonNull(thread,"Orion client-thread service is unavailable");this.bus=Objects.requireNonNull(bus,"Orion event bus is unavailable");this.overlays=overlays;
  adapter=new OrionAdapter(client,config,target);adapter.executionAllowed=()->armed;scheduler=new ActionScheduler(ticks,telemetry,adapter,attacks);
  agent=new LearningAgent(projectRoot());
  nhTraining=new NHTrainingConnection(projectRoot());
  combatUi=new VisibleCombatUi(client);privacy=new KioskPrivacy(client);
  chambers=new ChambersController(client,net.unethicalite.api.widgets.Prayers::toggle,()->combatUi.ready(net.unethicalite.api.widgets.Tab.PRAYER,100));
  demonstration=new ChambersDemonstration(client);
  olm=new OlmFighter(this);olmLoop=new OlmLoopController(this);slayer=new SlayerController(this);
 }
 static java.io.File projectRoot(){String configured=System.getProperty("owl.project.root");if(configured!=null&&!configured.trim().isEmpty())return new java.io.File(configured);try{java.io.File code=new java.io.File(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());return code.isFile()?code.getParentFile():new java.io.File(System.getProperty("user.dir"));}catch(Exception error){return new java.io.File(System.getProperty("user.dir"));}}
 public void attach() {
  attach(()->{},Bootstrap::report);
 }
 public void attach(Runnable ready,java.util.function.Consumer<Throwable> failed) {
  bus.register(this);overlay=new NhOverlay(this);
  stopKey=e->{if(e.getID()==KeyEvent.KEY_PRESSED&&e.getKeyCode()==KeyEvent.VK_F9){armed=false;chambers.armed=false;olm.running=false;thread.invoke(()->stop("F9 emergency stop"));return true;}return false;};
  KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(stopKey);
  SwingUtilities.invokeLater(()->{
   // Orion's native XP overlay reads unavailable varbits when layers are rebuilt on
   // the game thread before login. Register/remove overlays on Swing's UI thread,
   // matching the client's normal plugin lifecycle; HUD failure must not block UI.
   try{overlays.add(overlay);overlayRegistered=true;overlayStatus="Attached";}catch(RuntimeException hudError){overlayStatus="Unavailable: "+hudError.getClass().getSimpleName();try{overlays.remove(overlay);}catch(RuntimeException ignored){}overlayRegistered=false;}
   try{privacy.attach(overlays);overlays.add(slayer.overlay);}catch(RuntimeException maskingError){privacy.status="Unavailable - restart Turbo Client";Bootstrap.report(maskingError);}
   try{panel=SlayerLaunchRequest.consume(projectRoot().toPath())?slayer.window():new PrototypePanel(this);panel.setVisible(true);ready.run();}catch(RuntimeException error){armed=false;agent.close();failed.accept(error);}
  });
 }
 public void stop(String reason) {slayer.stop(reason);armed=false;chambers.stop(reason);demonstration.stop(reason);olm.stop(reason);waitingForFocus=false;controlAtArm=false;autoLock.clear();scheduler.cancel(reason);attacks.reset();agent.resetContext();OrionAdapter.AttackStamp.submittedAt=Long.MAX_VALUE;telemetry.event(ticks,reason);updateDisplay();}
 public void setAutoLock(boolean enabled){stop("Auto-lock "+(enabled?"enabled; attack a player to acquire them":"disabled"));config.autoLock=enabled;}
 @Subscribe public void onMenuOptionClicked(MenuOptionClicked event) {
  slayer.menu(event);if(event.isConsumed())return;demonstration.menu(event);
  if(demonstration.recording)return;
  if(!config.autoLock||event.isConsumed()||client.getGameState()!=GameState.LOGGED_IN)return;
  boolean barrageSelected=client.getSelectedSpellWidget()==net.runelite.api.widgets.WidgetInfo.SPELL_ICE_BARRAGE.getId();
  if(!AutoLockTracker.attackMenu(event.getMenuAction(),event.getMenuOption(),barrageSelected))return;
  Player self=client.getLocalPlayer();if(self==null)return;
  for(Player enemy:client.getPlayers())if(enemy!=self&&enemy.getIndex()==event.getId()&&!enemy.isDead()) {
   if(target.actor==enemy&&target.index==enemy.getIndex())return;
   scheduler.cancel("Attack intent changed to a different player");target.clear();freeze.clear();agent.resetContext();attacks.awaiting=false;OrionAdapter.AttackStamp.submittedAt=Long.MAX_VALUE;
   previous=Protection.UNKNOWN;lastIntent=Style.UNKNOWN;lastDecisionTick=-1;pendingReevaluation=true;
   autoLock.request(enemy,enemy.getIndex(),System.nanoTime());telemetry.event(ticks,"Awaiting confirmation of selected attack target");return;
  }
 }
 private boolean acquireAutomaticTarget() {
  if(autoLock.candidate==null)return true;
  Player self=client.getLocalPlayer();boolean present=false;
  for(Player enemy:client.getPlayers())if(enemy==autoLock.candidate&&enemy.getIndex()==autoLock.index&&!enemy.isDead()){present=true;break;}
  if(!present||autoLock.expired(System.nanoTime())){autoLock.clear();stop("Auto-lock attack target disappeared or was not confirmed");target.clear();return false;}
  if(self!=null&&self.getInteracting() instanceof Player&&autoLock.confirmed(self.getInteracting(),((Player)self.getInteracting()).getIndex())) {
   target.lock(autoLock.candidate,autoLock.index);autoLock.clear();previous=Protection.UNKNOWN;pendingReevaluation=true;telemetry.event(ticks,"Automatically locked attacked player; index "+target.index);return true;
  }
  updateDisplay();return false;
 }
 private final java.util.concurrent.atomic.AtomicBoolean disposing=new java.util.concurrent.atomic.AtomicBoolean();
 private final java.util.concurrent.CompletableFuture<Void> disposed=new java.util.concurrent.CompletableFuture<>();
 public void dispose(){disposeForReload(()->{},Bootstrap::report);}
 void disposeForReload(Runnable ready,java.util.function.Consumer<Throwable> failed){
  disposed.whenCompleteAsync((ignored,error)->{if(error==null)ready.run();else failed.accept(error);});
  if(!disposing.compareAndSet(false,true))return;
  armed=false;chambers.armed=false;olm.running=false;
  try{agent.close();nhTraining.close();agentPrompt="";agentNotes="";KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(stopKey);
   thread.invoke(()->{try{stop("Companion reload/close");demonstration.clear();olm.close();chambers.close();chambers.forgetLocations();bus.unregister(this);
    SwingUtilities.invokeLater(()->{try{privacy.close(overlays);if(overlays!=null)overlays.remove(slayer.overlay);if(overlayRegistered&&overlays!=null)overlays.remove(overlay);overlayRegistered=false;if(panel!=null){closeOwned(panel);panel.dispose();}
     SwingUtilities.invokeLater(()->thread.invoke(()->disposed.complete(null)));
    }catch(Throwable e){disposed.completeExceptionally(e);}});
   }catch(Throwable e){disposed.completeExceptionally(e);}});
  }catch(Throwable e){disposed.completeExceptionally(e);}
 }
 private static void closeOwned(Window owner){for(Window child:owner.getOwnedWindows()){closeOwned(child);child.dispose();}}
 public void settings(Settings settings) {stop("Settings changed; observation only");config=settings;adapter.settings=settings;previous=Protection.UNKNOWN;lastDecisionTick=-1;}
 public void lockCurrent() {
  stop("Locking current opponent");Player self=client.getLocalPlayer();
  if(self==null||!(self.getInteracting() instanceof Player)){target.clear();throw new IllegalStateException("Interact with your intended player first, then lock current opponent.");}
  Player enemy=(Player)self.getInteracting();target.lock(enemy,enemy.getIndex());freeze.clear();previous=Protection.UNKNOWN;lastDecisionTick=-1;telemetry.event(ticks,"Opponent actor locked; index "+enemy.getIndex());updateDisplay();
 }
 public void arm() {
  if(olm.running)throw new IllegalStateException("Stop Olm before arming PvP controls");
  if(demonstration.recording)throw new IllegalStateException("Stop demonstration recording before arming automation");
  if(chambers.enabled)throw new IllegalStateException("Close Chambers monitoring before arming PvP controls");
  if(!ticks.fresh())throw new IllegalStateException("No recent GameTick. Log in first.");
  if(agent.mode==LearningAgent.Mode.CONTROL&&(!agent.ready||!agent.liveEligible))throw new IllegalStateException("No eligible Orion-trained agent loaded");
  State s=adapter.snapshot(target);if(!s.valid)throw new IllegalStateException("Lock a valid current player opponent first.");
  if(config.autoAttack&&config.attackAnimations.isEmpty())throw new IllegalStateException("Record verified local attack animation IDs before automatic attacks. Gear/spell preparation can be tested without this.");
  if(config.freezeEnabled&&config.freezeGraphics.isEmpty())throw new IllegalStateException("Freeze estimates require explicit graphic ID -> duration mappings.");
  if(config.defensiveEnabled&&config.enemyWeapons.isEmpty()&&config.enemyAnimations.isEmpty())throw new IllegalStateException("Defensive prediction requires explicit enemy weapon/animation mappings.");
  if(config.specEnabled&&config.gear.get(Style.SPEC).isEmpty())throw new IllegalStateException("Capture special-attack equipment first.");
  if(decisions.evaluate(config,s,attacks,freeze)==null)throw new IllegalStateException("No available counter setup. Capture equipment, return missing gear to inventory, and check spell availability.");
  armed=true;controlAtArm=agent.mode==LearningAgent.Mode.CONTROL;waitingForFocus=true;armedAt=System.nanoTime();previous=Protection.UNKNOWN;lastDecisionTick=-1;lastIntent=Style.UNKNOWN;pendingReevaluation=true;telemetry.event(ticks,"ARMED, awaiting game focus (10s). F9 stops.");
 }
 private void sampleConnection(){privacy.observe();connectionTrace.observe(String.valueOf(client.getGameState()),client.getTickCount(),client.getGameCycle(),client.getCanvas()!=null&&client.getCanvas().isFocusOwner(),ticks.fresh(),combatUi.actionSlots());}
 @Subscribe public void onConnectionTransition(GameStateChanged event){sampleConnection();if(olm.running)olm.checkpointConnection();} @Subscribe public void onGameTick(GameTick event) {
  ticks.onTick(client.getTickCount());sampleConnection();if(olmLoop.pulse()){process(false);return;}
  slayer.pulse();if(slayer.running()){process(false);return;}demonstration.tick();
  if(!demonstration.recording&&!olm.running)chambers.pulse(ticks.fresh());
  olm.tick(ticks.fresh());
  if(client.getGameState()!=GameState.LOGGED_IN){stop("Not logged in");target.clear();freeze.clear();return;}
  process(true);
 }
 @Subscribe public void onClientTick(ClientTick event) {sampleConnection();slayer.pulse();if(slayer.running()){process(false);return;}if(olmLoop.pulse()){process(false);return;}if(!demonstration.recording&&!olm.running)chambers.pulse(ticks.fresh());olm.clientCycle();process(false);}
 @Subscribe public void onProjectileMoved(ProjectileMoved event) {chambers.projectile(event.getProjectile());if(!demonstration.recording&&!olm.running&&chambers.armed)chambers.pulse(ticks.fresh());olm.projectile(event);}
 @Subscribe public void onOlmGraphicChanged(GraphicChanged event){olm.graphicChanged(event);}
 @Subscribe public void onChambersHitsplat(HitsplatApplied event) {if(event.getActor()==client.getLocalPlayer()&&event.getHitsplat()!=null)chambers.damage(event.getHitsplat().getAmount());}
 private void process(boolean newTick) {
  try {
   if(slayer.running()){display="Turbo Slayer: "+slayer.progress()+"\n"+slayer.status;overlayLines=new String[]{"Turbo Slayer: ACTIVE",slayer.selected,slayer.progress(),"F9: stop"};return;}if(demonstration.recording)return;
   if(olm.running){display="Olm fighter active\n"+olm.status+"\n"+olm.specialStatus();String message=olm.status;overlayLines=new String[]{"Olm fighter: ACTIVE",message.length()>42?message.substring(0,42):message,message.length()>42?message.substring(42,Math.min(message.length(),84)):"","Tick "+ticks.tick,"F9 / floating STOP: stop"};return;}
   if(chambers.enabled){if(armed)stop("Chambers monitoring owns controls");return;}
   if(!ticks.fresh()) {if(armed)stop("GameTick stream stale");return;}
   if(armed&&controlAtArm&&(agent.mode!=LearningAgent.Mode.CONTROL||!agent.ready)){stop("Policy stopped; rule fallback is disabled until you explicitly re-arm");return;}
   if(!acquireAutomaticTarget())return;
   if(target.actor==null){updateDisplay();return;}
   State state=adapter.snapshot(target);
   if(!state.valid){if(armed||scheduler.busy())stop("Locked opponent no longer current/valid");target.clear();previous=Protection.UNKNOWN;updateDisplay();return;}
   boolean edge=target.prayerChanged(state.protection);if(edge)pendingReevaluation=true;
   if(newTick||edge)agent.request(state,config,attacks);
   if(state.protection!=previous&&scheduler.busy())scheduler.cancel("Opponent prayer changed");previous=state.protection;
   if(armed&&!client.getCanvas().isFocusOwner()) {if(waitingForFocus&&System.nanoTime()-armedAt<10000000000L){updateDisplay();return;}stop(waitingForFocus?"Timed out waiting for game focus":"Game canvas lost focus");return;}
   if(armed)waitingForFocus=false;
   if(armed&&attacks.awaiting&&ticks.now()-attacks.submittedAt>1800000000L){stop("No configured attack-animation confirmation within 1.8 seconds");return;}
   if(armed&&config.defensiveEnabled&&state.intent!=Style.UNKNOWN&&state.intent!=lastIntent){adapter.activatePrayer("PROTECT_FROM_"+(state.intent==Style.RANGED?"MISSILES":state.intent.name()));telemetry.event(ticks,"Defensive prediction: "+state.intent);}lastIntent=state.intent;
   if(scheduler.busy()){scheduler.advance();OrionAdapter.AttackStamp.submittedAt=attacks.submittedAt;if(telemetry.phase==Phase.FAILED)stop("Sequence failed; re-arm after inspecting telemetry");updateDisplay();return;}
   boolean readyAttack=config.autoAttack&&attacks.ready(ticks.tick);
   if((edge||pendingReevaluation||newTick&&readyAttack)&&lastDecisionTick!=ticks.tick) {
    Decision decision;
    if(agent.mode==LearningAgent.Mode.CONTROL) {
     if(!agent.ready||!agent.liveEligible){stop("Learning agent unavailable; no silent rule fallback");return;}
     LearningAgent.Prediction prediction=agent.prediction;
     if(prediction==null||!prediction.matches(state,System.nanoTime())){pendingReevaluation=true;updateDisplay();return;}
     decision=decisions.evaluateForced(config,state,attacks,freeze,prediction.style);
    }else decision=decisions.evaluate(config,state,attacks,freeze);
    lastDecisionTick=ticks.tick;pendingReevaluation=false;
    if(decision==null)telemetry.event(ticks,"No available valid counter decision");
    else if(!armed)telemetry.event(ticks,"Observe: "+decision.style+", "+decision.actions.size()+" proposed actions");
    else {scheduler.begin(decision,state,config);scheduler.advance();OrionAdapter.AttackStamp.submittedAt=attacks.submittedAt;if(telemetry.phase==Phase.FAILED)stop("Sequence failed; re-arm after inspecting telemetry");}
   }
   updateDisplay();
  }catch(RuntimeException e){stop("Runtime stopped: "+e.getClass().getSimpleName()+" / "+e.getMessage());}
 }
 @Subscribe public void onAnimationChanged(AnimationChanged event) {
  slayer.animation(event.getActor());olm.animation(event.getActor());
  Actor actor=event.getActor();if(actor!=client.getLocalPlayer()||actor==null)return;
  Integer speed=config.attackAnimations.get(actor.getAnimation());
  if(speed!=null) {long stamp=System.nanoTime();adapter.confirmedAttack(actor.getInteracting(),stamp);attacks.observed(client.getTickCount(),speed);telemetry.event(ticks,"Configured attack animation observed: "+actor.getAnimation()+"; cooldown "+speed+" ticks");}
 }
 @Subscribe public void onOlmActorDeath(ActorDeath event){slayer.died(event.getActor());olm.actorDeath(event.getActor());}
 @Subscribe public void onOlmNpcSpawned(NpcSpawned event){slayer.spawned(event.getNpc());olm.npcSpawned(event.getNpc());}
 @Subscribe public void onGraphicChanged(GraphicChanged event) {
  if(!config.freezeEnabled)return;Actor actor=event.getActor();if(actor!=target.actor&&actor!=client.getLocalPlayer())return;
  Integer duration=config.freezeGraphics.get(actor.getGraphic());if(duration!=null){freeze.observe(actor,client.getTickCount(),duration);telemetry.event(ticks,"Configured freeze graphic observed; estimated duration "+duration+" ticks");}
 }
 public void updateDisplay() {
  Player self=client.getLocalPlayer();Player enemy=target.actor instanceof Player?(Player)target.actor:null;
  String name=enemy==null?"none":enemy.getName();String prayer=enemy==null?"UNKNOWN":adapter.opponents.prayer(enemy).name();
  String targetDetails=enemy==null?"No locked opponent":("Target: "+name+" | index "+enemy.getIndex()+" | prayer "+prayer+"\nEnemy animation "+enemy.getAnimation()+" | weapon "+(enemy.getPlayerComposition()==null?-1:enemy.getPlayerComposition().getEquipmentId(net.runelite.api.kit.KitType.WEAPON))+" | health bar "+enemy.getHealthRatio()+"/"+enemy.getHealthScale());
  String localDetails=self==null?"Not logged in":("Local HP "+client.getBoostedSkillLevel(Skill.HITPOINTS)+" | prayer "+client.getBoostedSkillLevel(Skill.PRAYER)+" | animation "+self.getAnimation()+" | moving "+self.isMoving());
  String policy="Agent: "+agent.mode+(agent.prediction==null?"":" / suggestion "+(agent.prediction.style==Style.UNKNOWN?"WAIT":agent.prediction.style));
  display=(armed?"ARMED":"OBSERVATION ONLY")+" | "+telemetry.phase+"\n"+targetDetails+"\n"+localDetails+"\nGameTick "+ticks.tick+" | elapsed "+ticks.elapsedMs()+" ms | estimated remaining "+ticks.remainingMs()+" ms\nNext attack tick "+attacks.nextTick+" | confirmation pending "+attacks.awaiting+"\nFreeze estimates: local "+freeze.remaining(self,ticks.tick)+", opponent "+freeze.remaining(enemy,ticks.tick)+" ticks\n"+policy+"\n"+telemetry.result;
  log=String.join("\n",telemetry.lines());overlayLines=new String[]{armed?"NH prototype: ARMED":"NH prototype: OBSERVE","Target: "+name,"Prayer: "+prayer,"State: "+telemetry.phase,"Tick "+ticks.tick+" / remaining ~"+ticks.remainingMs()+"ms","Next attack: "+attacks.nextTick,"F9: stop"};
 }
}

final class NhOverlay extends Overlay {
 private final CombatRuntime runtime;
 NhOverlay(CombatRuntime runtime){this.runtime=runtime;setPosition(OverlayPosition.TOP_LEFT);}
 @Override public Dimension render(Graphics2D g) {
  String[] lines=runtime.overlayLines;int width=280,height=lines.length*18+12;g.setColor(new Color(15,20,30,190));g.fillRoundRect(0,0,width,height,8,8);g.setFont(new Font("SansSerif",Font.PLAIN,12));
  for(int i=0;i<lines.length;i++){g.setColor(i==0?(runtime.armed?Color.ORANGE:Color.CYAN):Color.WHITE);g.drawString(lines[i],8,18+i*18);}return new Dimension(width,height);
 }
}





