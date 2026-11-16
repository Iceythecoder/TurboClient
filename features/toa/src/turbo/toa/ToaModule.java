package turbo.toa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.kit.KitType;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.DecorativeObjectChanged;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.WallObjectChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.unethicalite.api.game.Combat;
import owl.nh.RaidInputBridge;
import turbo.raid.RaidGearRules;

/**
 * ToA walkthrough recorder and conservative visible-input prototype.
 *
 * <p>The module observes Orion state but can only send actions through the parent-owned
 * {@link RaidInputBridge}. Every potion, equipment, prayer, special and movement request therefore
 * uses the same visible-control serializer as Chambers. Unknown encounter signals are retained in
 * the walkthrough and never silently treated as verified.</p>
 */
public final class ToaModule implements AutoCloseable {
 private static final String BUILD_TAG = "toa-core-hold-cards-v3";
 private static final int MAX_FRAMES = 12000;
 private static final int MAX_ACTIONS = 16000;
 private static final int MAX_MARKERS = 500;
 private static final int MAX_SCENE_SIGNALS = 512;

 private final turbo.raid.RaidSetup setup;
 private final Client client;
 private final ClientThread thread;
 private final EventBus bus;
 private final Runnable stopOther;
 private final Path dataRoot;
 private final RaidInputBridge actionsBridge;
 private final JDialog window;
 private final javax.swing.Timer refresh;
 private final JButton runButton;
 private final JButton stopButton;
 private final List<Map<String, Object>> frames = new ArrayList<>();
 private final List<Map<String, Object>> actions = new ArrayList<>();
 private final List<Map<String, Object>> markers = new ArrayList<>();
 private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new IdentityHashMap<>());
 private final Map<Integer, Set<WorldPoint>> liveSceneSignals = new HashMap<>();
 private final ToaCombatPolicy policy = new ToaCombatPolicy();
 private final ToaEntryFlow entryFlow = new ToaEntryFlow();
 private final ToaStateMachine stateMachine = new ToaStateMachine();
 private final ToaRuntimeTrace runtimeTrace = new ToaRuntimeTrace();
 private final ToaGearBatch gearBatch = new ToaGearBatch();
 private final Map<Integer, Integer> pendingGear = new HashMap<>();

 private volatile boolean recording;
 private volatile boolean running;
 private volatile boolean closed;
 private volatile boolean saving;
 private volatile String status = "Ready for a ToA walkthrough or prototype run.";
 private int lastTick = -1;
 private int lastRuntimeTraceTick = -1;
 private int phaseEvidenceTick = -1000;

 private ToaProfile profile;
 private ToaCombatPolicy.Phase phase = ToaCombatPolicy.Phase.UNKNOWN;
 private ToaCombatPolicy.Style elidinisStyle = ToaCombatPolicy.Style.MAGIC;
 private boolean coreVisibleLastTick;
 private boolean guardianStyleAdvancePending;
 private boolean sawCore;
 private boolean finalDeathObserved;
 private boolean sawTumeken;
 private int lastTumekenTick = -1000;
 private int lastBeginRequest = -1000;
 private int beginApproachRequestTick = -1000;
 private WorldPoint beginApproachTile;
 private int lastAttackRequest = -1000;
 private boolean obeliskSpecPending;
 private int obeliskSpecEnergyBefore = -1;
 private int obeliskSpecRequestedTick = -1000;
 private int floorWarningUntil = -1000;
 private int immediateWarningUntil = -1000;
 private int lastFloorSignalTick = -1000;
 private int slamSequence;
 private WorldPoint floorSafeTile;
 private WorldPoint immediateSafeTile;
 private WorldPoint lastMoveTarget;
 private int lastMoveRequest = -1000;
 private WorldPoint blockMoveObservedPosition;
 private WorldPoint resolvedObeliskBlockTile;
 private WorldPoint resolvedWardenStartTile;
 private WorldPoint resolvedWardenCampTile;
 private int blockMoveAttempts;
 private boolean wardenMoveRequested;
 private int wardenMoveRequestCycle = -1000;
 private WorldPoint wardenMoveOrigin;
 private WorldPoint wardenMoveDestination;
 private boolean wardenStartReached;
 private String pendingProtection;
 private int protectionUntil = -1000;
 private boolean sawOpeningWarden;
 private boolean recoveringDeath;
 private int deathObservedTick = -1000;
 private String lastDecisionTrace = "";

 public ToaModule(
  Client client,
  ClientThread thread,
  EventBus bus,
  Frame owner,
  Path root,
  Runnable stopOther) {
  this(client, thread, bus, owner, root, stopOther, () -> "{}", null);
 }

 public ToaModule(
  Client client,
  ClientThread thread,
  EventBus bus,
  Frame owner,
  Path root,
  Runnable stopOther,
  Supplier<String> gearSource) {
  this(client, thread, bus, owner, root, stopOther, gearSource, null);
 }

 public ToaModule(
  Client client,
  ClientThread thread,
  EventBus bus,
  Frame owner,
  Path root,
  Runnable stopOther,
  Supplier<String> gearSource,
  RaidInputBridge bridge) {
  this.client = client;
  this.thread = thread;
  this.bus = bus;
  this.stopOther = stopOther;
  this.actionsBridge = bridge;
  this.dataRoot = root.toAbsolutePath().normalize();
  Supplier<String> baseline = bridge == null ? () -> "{}" : bridge::baselineProfile;
  this.setup = new turbo.raid.RaidSetup(
   "toa", this.dataRoot, client, thread, gearSource, baseline, text -> status = text);

  window = new JDialog((Frame) null, "AI TOA Fighter", false);
  window.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
  JPanel content = new JPanel(new BorderLayout(10, 10));
  content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
  JLabel heading = new JLabel("Tombs of Amascut - Warden prototype");
  heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD, 17f));
  content.add(heading, BorderLayout.NORTH);

  JTextArea live = new JTextArea(10, 58);
  live.setLineWrap(true);
  live.setWrapStyleWord(true);
  live.setEditable(false);
  live.setOpaque(false);
  content.add(live, BorderLayout.CENTER);

  JPanel controls = new JPanel(new GridLayout(0, 1, 7, 7));
  JPanel combat = new JPanel(new GridLayout(1, 3, 7, 7));
  runButton = new JButton("Run TOA fighter");
  stopButton = new JButton("Stop / F9");
  JButton settings = new JButton("Advanced settings");
  combat.add(runButton);
  combat.add(stopButton);
  combat.add(settings);
  controls.add(combat);

  JPanel observeRow = new JPanel(new GridLayout(1, 3, 7, 7));
  JButton observe = new JButton("Start walkthrough");
  JButton stopObserve = new JButton("Stop observing");
  JButton save = new JButton("Save walkthrough...");
  observeRow.add(observe);
  observeRow.add(stopObserve);
  observeRow.add(save);
  controls.add(observeRow);

  JTextField note = new JTextField();
  note.setToolTipText("Describe a mechanic, phase, prayer, or gear change.");
  JPanel notes = new JPanel(new BorderLayout(7, 0));
  notes.add(note, BorderLayout.CENTER);
  JButton mark = new JButton("Mark mechanic");
  notes.add(mark, BorderLayout.EAST);
  controls.add(notes);
  JButton discard = new JButton("Discard unsaved walkthrough");
  controls.add(discard);
  content.add(controls, BorderLayout.SOUTH);

  runButton.setEnabled(bridge != null);
  runButton.setToolTipText(bridge == null
   ? "Update TurboChambers.jar and TurboTOA.jar together to enable the prototype."
   : "Uses the loaded ToA instance profile and normal visible game controls.");
  settings.addActionListener(event -> {
   Window next = setup.window(owner);
   next.setVisible(true);
   next.toFront();
  });
  runButton.addActionListener(event -> thread.invoke(() -> {
   try {
    stopOther.run();
    startPrototype();
   } catch (RuntimeException error) {
    status = message(error);
   }
  }));
  stopButton.addActionListener(event -> stop("User stopped the ToA prototype"));
  observe.addActionListener(event -> thread.invoke(() -> {
   try {
    stopOther.run();
    start();
   } catch (RuntimeException error) {
    status = message(error);
   }
  }));
  stopObserve.addActionListener(event -> stop("User stopped observation"));
  mark.addActionListener(event -> {
   String text = note.getText();
   thread.invoke(() -> {
    try {
     mark(text);
     SwingUtilities.invokeLater(() -> note.setText(""));
    } catch (RuntimeException error) {
     status = message(error);
    }
   });
  });
  save.addActionListener(event -> save());
  discard.addActionListener(event -> {
   if (JOptionPane.showConfirmDialog(
     window,
     "Discard this unsaved ToA walkthrough?",
     "Discard walkthrough",
     JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
    thread.invoke(this::discard);
   }
  });
  window.addWindowListener(new WindowAdapter() {
   @Override public void windowClosing(WindowEvent event) {
    stop("ToA window closed; unsaved walkthrough data remains in memory");
   }
  });

  window.setContentPane(content);
  window.pack();
  window.setMinimumSize(new Dimension(650, 430));
  window.setLocationRelativeTo(owner);
  bus.register(this);
  refresh = new javax.swing.Timer(200, event -> {
   String bridgeText = actionsBridge == null
    ? "Prototype controls unavailable: update both Turbo JARs."
    : actionsBridge.status();
   live.setText(status + "\n\n" + summary() + "\n\n" + bridgeText
    + "\n\nFloor-slam movement has absolute priority over prayer, supplies, gear and DPS."
    + "\nPotion and gear steps advance only after observed game-state changes."
    + "\nRuntime mechanics evidence is saved locally without player names.");
  });
  refresh.start();
 }

 public Window window() {
  return window;
 }

 synchronized String summary() {
  return "Prototype: " + running
   + " | phase: " + phase
    + " | state: " + stateMachine.state()
   + " | entry: " + entryFlow.stage() + " (" + entryFlow.requests() + ")"
   + " | walkthrough: " + recording
   + " | ticks: " + frames.size()
   + " | actions: " + actions.size()
    + " | runtime events: " + runtimeTrace.size()
   + " | notes: " + markers.size();
 }

 synchronized void start() {
  if (closed || saving) {
   throw new IllegalStateException("Wait until the current operation finishes.");
  }
  if (recording || running) {
   throw new IllegalStateException("Stop the active ToA session first.");
  }
  if (!frames.isEmpty() || !actions.isEmpty()) {
   throw new IllegalStateException("Save or discard the previous walkthrough before starting another.");
  }
  requireLogin();
  lastTick = -1;
  seenProjectiles.clear();
  recording = true;
  status = "Observing your manual ToA run. F9 stops observation and bot actions.";
 }

 private synchronized void startPrototype() {
  if (actionsBridge == null) {
   throw new IllegalStateException("The visible-input bridge is unavailable. Update both Turbo JARs together.");
  }
  if (closed || saving || recording || running) {
   throw new IllegalStateException("Stop the current ToA session before starting the prototype.");
  }
  requireLogin();
  profile = ToaProfile.parse(setup.snapshot());
  policy.reset();
  entryFlow.reset();
  stateMachine.reset();
  runtimeTrace.reset();
  runtimeTrace.event("BUILD", client.getTickCount(), "tag", BUILD_TAG);
  phase = ToaCombatPolicy.Phase.UNKNOWN;
  lastRuntimeTraceTick = -1;
  phaseEvidenceTick = -1000;
  elidinisStyle = ToaCombatPolicy.Style.MAGIC;
  coreVisibleLastTick = false;
  guardianStyleAdvancePending = false;
  sawCore = false;
  finalDeathObserved = false;
  sawTumeken = false;
  lastTumekenTick = -1000;
  lastBeginRequest = -1000;
  beginApproachRequestTick = -1000;
  beginApproachTile = null;
  lastAttackRequest = -1000;
  obeliskSpecPending = false;
  obeliskSpecEnergyBefore = -1;
  obeliskSpecRequestedTick = -1000;
  floorWarningUntil = immediateWarningUntil = lastFloorSignalTick = -1000;
  slamSequence = 0;
  floorSafeTile = immediateSafeTile = lastMoveTarget = null;
  blockMoveObservedPosition = null;
  resolvedObeliskBlockTile = null;
  resolvedWardenStartTile = null;
  resolvedWardenCampTile = null;
  blockMoveAttempts = 0;
  wardenMoveRequested = false;
  wardenMoveRequestCycle = -1000;
  wardenMoveOrigin = null;
  wardenMoveDestination = null;
  wardenStartReached = false;
  pendingProtection = null;
  protectionUntil = -1000;
  gearBatch.reset();
  pendingGear.clear();
  sawOpeningWarden = false;
  recoveringDeath = false;
  deathObservedTick = -1000;
  liveSceneSignals.clear();
  lastDecisionTrace = "";
  running = true;
  status = profile.hasCalibratedFloorSignal()
   ? "ToA prototype active. Loaded calibrated floor-slam signals."
    : "ToA prototype active. Unknown Warden animations are being recorded, not guessed as floor slams.";
 }

 private void requireLogin() {
  if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) {
   throw new IllegalStateException("Log into Orion before starting ToA.");
  }
 }

 public synchronized void stop(String reason) {
  boolean wasActive = recording || running;
  boolean wasRunning = running;
  recording = false;
  running = false;
  if (actionsBridge != null) {
   actionsBridge.cancelPendingControl();
  }
  gearBatch.reset();
  pendingGear.clear();
  if (wasActive) {
   status = reason + ". Walkthrough observations remain in memory until saved or discarded.";
  }
  if (wasRunning && runtimeTrace.size() > 0) {
   JsonObject trace = runtimeTrace.snapshot(reason, stateMachine.state());
   CompletableFuture.runAsync(() -> {
    try {
     ToaRuntimeTrace.save(dataRoot, trace);
    } catch (Exception error) {
     status = "ToA stopped, but its local runtime trace could not be saved: " + message(error);
    }
   });
  }
 }

 synchronized void discard() {
  recording = false;
  frames.clear();
  actions.clear();
  markers.clear();
  seenProjectiles.clear();
  liveSceneSignals.clear();
  gearBatch.reset();
  pendingGear.clear();
  lastTick = -1;
  status = "Walkthrough discarded; ready for a new run.";
 }

 synchronized void mark(String text) {
  if (!recording) {
   throw new IllegalStateException("Start observing before adding a mechanic note.");
  }
  String clean = text == null ? "" : text.trim();
  if (clean.isEmpty() || clean.length() > 1000) {
   throw new IllegalArgumentException("Use a mechanic note of 1-1000 characters.");
  }
  if (markers.size() >= MAX_MARKERS) {
   throw new IllegalStateException("Mechanic-note limit reached.");
  }
  Map<String, Object> marker = new LinkedHashMap<>();
  marker.put("tick", client.getTickCount());
  marker.put("note", clean);
  markers.add(marker);
  status = "Mechanic note added.";
 }

 static int[] items(ItemContainer bag) {
  if (bag == null) {
   return new int[0];
  }
  Item[] values = bag.getItems();
  int[] out = new int[values.length * 2];
  for (int index = 0; index < values.length; index++) {
   Item item = values[index];
   if (item != null) {
    out[index * 2] = item.getId();
    out[index * 2 + 1] = item.getQuantity();
   }
  }
  return out;
 }

 @Subscribe
 public synchronized void onGameTick(GameTick event) {
  if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) {
   if (recording || running) {
    stop("Logout or loading observed");
   }
   return;
  }
  if (recording) {
   recordFrame();
  }
  if (running) {
   combatCycle(true);
   recordRuntimeFrame();
  }
 }

 @Subscribe
 public synchronized void onClientTick(ClientTick event) {
  if (running && client.getGameState() == GameState.LOGGED_IN) {
   combatCycle(false);
  }
 }

 private void recordFrame() {
  if (frames.size() >= MAX_FRAMES) {
   stop("Observation limit reached");
   return;
  }
  int tick = client.getTickCount();
  if (tick == lastTick) {
   return;
  }
  lastTick = tick;
  Player self = client.getLocalPlayer();
  if (self.getLocalLocation() == null) {
   return;
  }
  WorldPoint position = WorldPoint.fromLocalInstance(client, self.getLocalLocation());
  if (position == null) {
   return;
  }
  Map<String, Object> frame = new LinkedHashMap<>();
  frame.put("tick", tick);
  frame.put("cycle", client.getGameCycle());
  frame.put("sceneBase", new int[] {client.getBaseX(), client.getBaseY()});
  frame.put("position", new int[] {position.getX(), position.getY(), position.getPlane()});
  frame.put("hp", client.getBoostedSkillLevel(Skill.HITPOINTS));
  frame.put("prayer", client.getBoostedSkillLevel(Skill.PRAYER));
  frame.put("animation", self.getAnimation());
  frame.put("graphic", self.getGraphic());
  frame.put("inventory", items(client.getItemContainer(InventoryID.INVENTORY)));
  frame.put("equipment", items(client.getItemContainer(InventoryID.EQUIPMENT)));
  List<int[]> npcs = new ArrayList<>();
  for (NPC npc : client.getNpcs()) {
   if (nearSelf(npc, 32) && npcs.size() < 100) {
    WorldPoint point = instance(npc.getLocalLocation());
    if (point != null) {
     npcs.add(new int[] {npc.getId(), npc.getIndex(), point.getX(), point.getY(), point.getPlane(),
      npc.getAnimation(), npc.getGraphic(), npc.getHealthRatio(), npc.getHealthScale()});
    }
   }
  }
  frame.put("npcs", npcs);
  List<int[]> projectiles = new ArrayList<>();
  if (client.getProjectiles() != null) {
   for (Projectile projectile : client.getProjectiles()) {
    if (projectiles.size() >= 128) {
     break;
    }
    projectiles.add(new int[] {projectile.getId(), projectile.getStartCycle(), projectile.getEndCycle(),
     projectile.getFloor(), projectile.getTarget() == null ? -1 : projectile.getTarget().getX(),
     projectile.getTarget() == null ? -1 : projectile.getTarget().getY()});
   }
  }
  frame.put("projectiles", projectiles);
  List<int[]> graphics = new ArrayList<>();
  if (client.getGraphicsObjects() != null) {
   for (GraphicsObject graphic : client.getGraphicsObjects()) {
    if (graphics.size() >= 128 || graphic.finished() || graphic.getLevel() != client.getPlane()) {
     continue;
    }
    WorldPoint point = WorldPoint.fromLocal(client, graphic.getLocation());
    if (point != null && point.distanceTo(client.getLocalPlayer().getWorldLocation()) <= 32) {
     graphics.add(new int[] {graphic.getId(), point.getX(), point.getY(), point.getPlane()});
    }
   }
  }
  frame.put("graphics", graphics);
  List<String> prayers = new ArrayList<>();
  for (Prayer prayer : Prayer.values()) {
   if (client.isPrayerActive(prayer)) {
    prayers.add(prayer.name());
   }
  }
  frame.put("activePrayers", prayers);
  frames.add(frame);
 }

 private void recordRuntimeFrame() {
  int tick = client.getTickCount();
  if (lastRuntimeTraceTick == tick || client.getLocalPlayer() == null) return;
  lastRuntimeTraceTick = tick;
  Player self = client.getLocalPlayer();
  WorldPoint player = currentPosition();
  List<Map<String, Object>> npcs = new ArrayList<>();
  for (NPC npc : client.getNpcs()) {
   if (!nearSelf(npc, 40) || npcs.size() >= 96) continue;
   Map<String, Object> value = new LinkedHashMap<>();
   value.put("id", npc.getId());
   value.put("name", cleanNpcName(npc));
   value.put("index", npc.getIndex());
   value.put("animation", npc.getAnimation());
   value.put("graphic", npc.getGraphic());
   value.put("healthRatio", npc.getHealthRatio());
   value.put("healthScale", npc.getHealthScale());
   value.put("attackable", npc.hasAction("Attack"));
   value.put("beginAvailable", npc.hasAction("Begin"));
   WorldPoint point = npc.getWorldLocation();
   if (point != null) value.put("position", new int[] {point.getX(), point.getY(), point.getPlane()});
   npcs.add(value);
  }
  List<String> prayers = new ArrayList<>();
  for (Prayer prayer : Prayer.values()) if (client.isPrayerActive(prayer)) prayers.add(prayer.name());
  runtimeTrace.event("FRAME", tick,
   "cycle", client.getGameCycle(),
   "state", stateMachine.state().name(),
   "phase", phase.name(),
   "position", player == null ? null : new int[] {player.getX(), player.getY(), player.getPlane()},
   "hp", client.getBoostedSkillLevel(Skill.HITPOINTS),
   "prayer", client.getBoostedSkillLevel(Skill.PRAYER),
   "attack", client.getBoostedSkillLevel(Skill.ATTACK),
   "strength", client.getBoostedSkillLevel(Skill.STRENGTH),
   "defence", client.getBoostedSkillLevel(Skill.DEFENCE),
   "ranged", client.getBoostedSkillLevel(Skill.RANGED),
   "magic", client.getBoostedSkillLevel(Skill.MAGIC),
   "animation", self.getAnimation(),
   "graphic", self.getGraphic(),
   "activePrayers", prayers,
   "npcs", npcs);
 }

 private void combatCycle(boolean serverTick) {
  if (!running || profile == null || actionsBridge == null) {
   return;
  }
  try {
   Player self = client.getLocalPlayer();
   if (self == null || self.getLocalLocation() == null) {
    stop("Local player state became unavailable");
    return;
   }
   int tick = client.getTickCount();
   int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
   if (hp <= 0) {
    if (!recoveringDeath) {
     recoveringDeath = true;
     deathObservedTick = tick;
     pendingGear.clear();
     gearBatch.reset();
     actionsBridge.cancelPendingControl();
     status = "Death confirmed at 0 HP; waiting ten seconds for the pre-room respawn.";
    }
    return;
   }
   if (recoveringDeath) {
    if (tick - deathObservedTick < 17) {
     status = "Death recovery pause: " + Math.max(0, 17 - (tick - deathObservedTick))
      + " game ticks before restarting preparation.";
     return;
    }
    resetAfterDeath();
    status = "Pre-room respawn confirmed; restarting pre-pot and Osmumten entry without counting the failed attempt.";
   }
   if (serverTick) {
    observePotion(tick);
    observeObeliskSpecial(tick);
    observeGear(tick);
    updateThreats(tick);
   }
   // Attackability and NPC spawn/despawn transitions are visible between
   // server ticks. Chambers reacts from this same client-cycle path; delaying
   // ToA phase recognition here added as much as 600 ms to every transition.
   updatePhase(tick);
    updateEncounterState(tick);

   if (policy.preparationComplete() && entryFlow.waiting() && handlePendingEntry(tick)) {
    return;
   }

   ToaCombatPolicy.Observation observation = observation(tick);
   ToaCombatPolicy.Decision decision = policy.decide(observation);
   traceDecision(decision, tick);
   if (decision.kind == ToaCombatPolicy.Kind.MOVE_FLOOR_SLAM) {
    handleAbsoluteFloorMove(tick);
    return;
   }
   if (decision.kind == ToaCombatPolicy.Kind.STOP) {
    stop(decision.value);
    return;
   }
   if (decision.kind == ToaCombatPolicy.Kind.MOVE_IMMEDIATE_HAZARD) {
    actionsBridge.cancelPendingControl();
    handleMove(immediateSafeTile, tick, "Dodging immediate ToA hazard");
    return;
   }
   // VisibleCombatUi verifies a control over multiple client cycles: hover,
   // observe the fresh default menu entry, then press. Chambers advances that
   // handshake from its client-cycle loop. Restricting ToA to one call per
   // 600 ms server tick made pre-potting appear frozen and multiplied every
   // potion delay. State credit (dose changes and phase transitions) remains
   // server-tick-only above; only the visible-control handshake runs here.
   execute(decision, observation, tick);
  } catch (RuntimeException error) {
   runtimeTrace.event("RECOVERED_EXCEPTION", client.getTickCount(),
    "class", error.getClass().getName(), "message", message(error));
   status = "ToA prototype recovered from " + message(error) + "; observing state before retry.";
  }
 }

 private ToaCombatPolicy.Observation observation(int tick) {
  ToaCombatPolicy.Observation value = new ToaCombatPolicy.Observation();
  value.tick = tick;
  value.hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
  value.prayer = client.getBoostedSkillLevel(Skill.PRAYER);
  value.combatStatsBelow100 = combatStatsBelow100();
  value.phase = phase;
  value.wornStyle = wornStyle();
  value.elidinisStyle = elidinisStyle;
  value.floorSlamWarning = tick <= floorWarningUntil;
  value.floorSafeTileKnown = floorSafeTile != null;
  value.immediateMovementHazard = tick <= immediateWarningUntil;
  value.immediateSafeTileKnown = immediateSafeTile != null;
  value.protectionPrayer = tick <= protectionUntil ? pendingProtection : null;
  Prayer desiredProtection = protection(value.protectionPrayer);
  value.protectionPrayerActive = desiredProtection != null && client.isPrayerActive(desiredProtection);
  NPC osmumten = findOsmumten();
  value.beginAvailable = osmumten != null && osmumten.hasAction("Begin");
  value.siphonAvailable = findSiphon() != null;
  ToaCombatPolicy.Style desiredStyle = desiredCombatStyle(value.siphonAvailable);
  Prayer desiredOffensive = desiredStyle == null ? null : profile.offensive.get(
   desiredStyle == ToaCombatPolicy.Style.SPEC ? "MELEE" : desiredStyle.name());
  value.offensivePrayer = desiredOffensive == null ? null : desiredOffensive.name();
  value.offensivePrayerActive = desiredOffensive != null && client.isPrayerActive(desiredOffensive);
  NPC target = phaseTarget();
  value.targetAvailable = target != null;
  value.specialAvailable = Combat.getSpecEnergy() >= 50;
  value.specialActive = Combat.isSpecEnabled();
  value.specialAttackPending = obeliskSpecPending;
  WorldPoint required = resolveObeliskBlockTile();
  value.atRequiredPosition = phase != ToaCombatPolicy.Phase.OBELISK
   || required != null && currentPosition().distanceTo(required) == 0;
  if (phase == ToaCombatPolicy.Phase.ELIDINIS) {
   WorldPoint start = resolveWardenStartTile();
   WorldPoint camp = resolveWardenCampTile();
   WorldPoint current = currentPosition();
   if (start != null && current != null && current.equals(start)) wardenStartReached = true;
   value.wardenCampKnown = start != null && camp != null;
    value.atWardenCamp = atWardenCombatPosition(current, camp);
   if (value.atWardenCamp) {
    wardenMoveRequested = false;
    wardenMoveOrigin = current;
    wardenMoveDestination = null;
   } else if (wardenMoveRequested && current != null && current.equals(wardenMoveDestination)) {
    wardenMoveRequested = false;
    wardenMoveOrigin = current;
    wardenMoveDestination = null;
   } else if (wardenMoveRequested && client.getLocalPlayer() != null
    && !client.getLocalPlayer().isMoving()
    && client.getGameCycle() - wardenMoveRequestCycle >= 30) {
    wardenMoveRequested = false;
    wardenMoveDestination = null;
   }
   value.wardenMoveRequested = wardenMoveRequested;
  }
  return value;
 }

 private ToaCombatPolicy.Style desiredCombatStyle(boolean siphonAvailable) {
  switch (phase) {
   case BEGIN:
    return ToaCombatPolicy.Style.SPEC;
   case OBELISK:
   case CORE:
    return ToaCombatPolicy.Style.MELEE;
   case ELIDINIS:
    return elidinisStyle;
   case TUMEKEN:
    return siphonAvailable ? ToaCombatPolicy.Style.MELEE : ToaCombatPolicy.Style.RANGED;
   default:
    return null;
  }
 }

 private void traceDecision(ToaCombatPolicy.Decision decision, int tick) {
  String value = decision == null ? "null" : decision.toString();
  if (value.equals(lastDecisionTrace)) return;
  lastDecisionTrace = value;
  runtimeTrace.event("DECISION", tick, "decision", value, "phase", phase.name(),
   "state", stateMachine.state().name());
 }

 private boolean combatStatsBelow100() {
  return client.getBoostedSkillLevel(Skill.ATTACK) < 100
   || client.getBoostedSkillLevel(Skill.STRENGTH) < 100
   || client.getBoostedSkillLevel(Skill.DEFENCE) < 100
   || client.getBoostedSkillLevel(Skill.RANGED) < 100
   || client.getBoostedSkillLevel(Skill.MAGIC) < 100;
 }

 private void resetAfterDeath() {
  policy.reset();
  entryFlow.reset();
  stateMachine.reset();
  gearBatch.reset();
  pendingGear.clear();
  phase = ToaCombatPolicy.Phase.UNKNOWN;
  phaseEvidenceTick = -1000;
  elidinisStyle = ToaCombatPolicy.Style.MAGIC;
  coreVisibleLastTick = false;
  guardianStyleAdvancePending = false;
  sawOpeningWarden = false;
  sawCore = false;
  sawTumeken = false;
  finalDeathObserved = false;
  obeliskSpecPending = false;
  pendingProtection = null;
  protectionUntil = -1000;
  lastDecisionTrace = "";
  resolvedObeliskBlockTile = null;
  resolvedWardenStartTile = null;
  resolvedWardenCampTile = null;
  beginApproachRequestTick = -1000;
  beginApproachTile = null;
  recoveringDeath = false;
 }

 private void execute(
  ToaCombatPolicy.Decision decision,
  ToaCombatPolicy.Observation observation,
  int tick) {
  switch (decision.kind) {
   case PROTECTION_PRAYER:
    Prayer protection = protection(decision.value);
    if (protection != null && client.isPrayerActive(protection)) {
     runtimeTrace.event("ACTION_CONFIRMED", tick, "action", "PROTECTION_PRAYER",
      "value", protection.name(), "phase", phase.name());
     status = protection + " confirmed before impact.";
    } else if (protection != null && actionsBridge.activatePrayer(protection, 100)) {
     runtimeTrace.event("ACTION_REQUESTED", tick, "action", "PROTECTION_PRAYER",
      "value", protection.name(), "phase", phase.name());
     status = "Requested visible " + protection + "; awaiting observed activation.";
    } else {
     runtimeTrace.event("ACTION_BLOCKED", tick, "action", "PROTECTION_PRAYER",
      "value", String.valueOf(decision.value), "phase", phase.name(),
      "bridge", actionsBridge.status());
     status = "Opening Prayer for " + decision.value + ". " + actionsBridge.status();
    }
    return;
   case OFFENSIVE_PRAYER:
    Prayer offensive = prayer(decision.value);
    if (offensive != null && client.isPrayerActive(offensive)) {
     runtimeTrace.event("ACTION_CONFIRMED", tick, "action", "OFFENSIVE_PRAYER",
      "value", offensive.name(), "phase", phase.name());
     status = offensive + " confirmed for " + phase + ".";
    } else if (offensive != null && actionsBridge.activatePrayer(offensive, 90)) {
     runtimeTrace.event("ACTION_REQUESTED", tick, "action", "OFFENSIVE_PRAYER",
      "value", offensive.name(), "phase", phase.name());
     status = "Requested visible offensive prayer " + offensive + " for " + phase + ".";
    } else {
     runtimeTrace.event("ACTION_BLOCKED", tick, "action", "OFFENSIVE_PRAYER",
      "value", decision.value, "bridge", actionsBridge.status());
     status = "Opening Prayer for " + decision.value + ". " + actionsBridge.status();
    }
    return;
   case DRINK:
    drink(decision.value, tick);
    return;
   case MOVE_POSITION:
    if ("warden-camp".equals(decision.value)) handleWardenCampMove(tick);
    else handleObeliskMove(tick);
    return;
   case EQUIP:
    equip(decision.style, tick);
    return;
   case ACTIVATE_SPECIAL:
    if (actionsBridge.activateSpecial()) {
     status = "Elder maul special selected; waiting for the observed active special bar.";
    } else {
     status = "Opening Combat and waiting for the real special bar. " + actionsBridge.status();
    }
    return;
   case SKIP_SPECIAL:
    policy.markObeliskSpecialAttempted();
    status = decision.value + "; continuing with charged-Scythe melee.";
    return;
   case BEGIN:
    begin(tick);
    return;
   case ATTACK:
    attack(decision.value, tick);
    return;
   case WAIT:
    status = decision.value;
    return;
   case STOP:
    stop(decision.value);
    return;
   default:
    status = "Waiting for a safe ToA action.";
  }
 }

 private void begin(int tick) {
  NPC osmumten = findOsmumten();
  if (osmumten == null || !osmumten.hasAction("Begin")) {
   status = "Looking for Osmumten's visible Begin option.";
   return;
  }
  Player self = client.getLocalPlayer();
  WorldArea area = osmumten.getWorldArea();
  WorldPoint player = self == null ? null : self.getWorldLocation();
  if (area == null || player == null) {
   status = "Waiting for Osmumten and the player's live positions before entry.";
   return;
  }
  if (area.distanceTo(player) > 2) {
   if (client.isMenuOpen()) {
    if (actionsBridge.closeMenu()) status = "Closing the context menu before walking close to Osmumten.";
    else status = "Waiting to close the context menu before approaching Osmumten. " + actionsBridge.status();
    return;
   }
   WorldPoint approach = nearestApproachTile(area, player);
   if (approach == null) {
    status = "No nearby Osmumten approach tile is available yet.";
    return;
   }
   boolean sameDestination = approach.equals(beginApproachTile);
   if (sameDestination && (self.isMoving() || tick - beginApproachRequestTick < 4)) {
    status = "Walking close to Osmumten; no duplicate movement or right-click request.";
    return;
   }
   if (actionsBridge.move(approach) || actionsBridge.moveMinimap(approach)) {
    beginApproachTile = approach;
    beginApproachRequestTick = tick;
    entryFlow.menuObserved();
    status = "One movement request sent to a nearby Osmumten interaction tile.";
   } else {
    status = "Waiting for a visible nearby Osmumten approach tile. " + actionsBridge.status();
   }
   return;
  }
  beginApproachTile = null;
  beginApproachRequestTick = -1000;
  boolean menuOpen = client.isMenuOpen();
  int cycle = client.getGameCycle();
  if (!menuOpen && entryFlow.awaitingMenu(cycle)) {
   status = "Osmumten was right-clicked once; waiting for the visible Begin menu.";
   return;
  }
  if (!menuOpen && !entryFlow.menuRequestDue(cycle)) return;
  if (menuOpen) entryFlow.menuObserved();
  if (actionsBridge.npcMenuAction(osmumten, "Begin")) {
   if (menuOpen) {
    lastBeginRequest = tick;
    entryFlow.sentBegin(tick);
    status = "Selected Osmumten -> Begin through the visible context menu.";
   } else {
    entryFlow.menuRequested(cycle);
    status = "Right-clicked Osmumten once; waiting for the visible Begin option.";
   }
  } else {
   status = menuOpen
    ? "The Osmumten menu is open; waiting to select Begin. " + actionsBridge.status()
    : "Waiting to right-click Osmumten once. " + actionsBridge.status();
  }
 }

 private static WorldPoint nearestApproachTile(WorldArea area, WorldPoint player) {
  if (area == null || player == null || area.getPlane() != player.getPlane()) return null;
  WorldPoint best = null;
  int bestDistance = Integer.MAX_VALUE;
  int minX = area.getX() - 1, maxX = area.getX() + area.getWidth();
  int minY = area.getY() - 1, maxY = area.getY() + area.getHeight();
  for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) {
   if (x > minX && x < maxX && y > minY && y < maxY) continue;
   WorldPoint candidate = new WorldPoint(x, y, area.getPlane());
   int distance = candidate.distanceTo(player);
   if (distance < bestDistance) { best = candidate; bestDistance = distance; }
  }
  return best;
 }

 private boolean handlePendingEntry(int tick) {
  NPC obelisk = findObelisk();
  if (obelisk != null && obelisk.hasAction("Attack")) {
   entryFlow.confirmActive();
   phase = ToaCombatPolicy.Phase.OBELISK;
   status = "Attackable obelisk confirmed; encounter is active and tile movement may begin.";
   return false;
  }

  int cycle = client.getGameCycle();
  if (!entryFlow.dialogueDue(cycle)) {
   status = "Waiting for the Osmumten confirmation page to change.";
   return true;
  }
  if (net.unethicalite.api.widgets.Dialog.canContinue()) {
   if (actionsBridge.continueDialogue()) {
    entryFlow.sentDialogue(cycle, tick);
    status = "Advanced the visible Osmumten confirmation dialogue; still waiting for an attackable obelisk.";
   } else {
    status = "Waiting to advance the visible Osmumten confirmation dialogue. " + actionsBridge.status();
   }
   return true;
  }
  if (net.unethicalite.api.widgets.Dialog.isViewingOptions()) {
   List<String> options = new ArrayList<>();
   List<net.runelite.api.widgets.Widget> visibleOptions = net.unethicalite.api.widgets.Dialog.getOptions();
   if (visibleOptions != null) for (net.runelite.api.widgets.Widget option : visibleOptions) {
    options.add(option != null && option.isVisible() ? option.getText() : null);
   }
   int choice = ToaEntryFlow.positiveOption(options);
   if (choice > 0 && actionsBridge.chooseDialogueOption(choice)) {
    entryFlow.sentDialogue(cycle, tick);
    status = "Selected positive Osmumten confirmation option " + choice
     + "; still waiting for an attackable obelisk.";
   } else {
    status = choice > 0 ? "Waiting to select Osmumten confirmation option " + choice + ". " + actionsBridge.status()
     : "Osmumten dialogue is visible but has no safe positive option; no blind choice was made.";
   }
   return true;
  }

  NPC osmumten = findOsmumten();
  boolean beginAvailable = osmumten != null && osmumten.hasAction("Begin");
  if (entryFlow.retryDue(tick, beginAvailable)) {
   phase = ToaCombatPolicy.Phase.BEGIN;
   status = "Begin produced no attackable obelisk or dialogue; retrying the visible Osmumten interaction.";
   return false;
  }
  status = "Begin submitted; waiting only for dialogue or the obelisk's real Attack action.";
  return true;
 }

 private void drink(String kind, int tick) {
  if (policy.pendingPotion() != null) {
   status = "Waiting for the observed " + policy.pendingPotion() + " dose decrease.";
   return;
  }
  List<Integer> family = profile.supply(kind);
  Item item = firstInventory(family);
  if (item == null) {
   status = "Missing usable " + kind + "; prototype remains active for urgent hazards.";
   return;
  }
  int before = doseCount(kind);
  if (actionsBridge.inventoryAction(item.getId(), "Drink")) {
   policy.potionRequested(kind, before, tick);
   status = "Requested one visible " + kind + " sip; no credit until its dose decreases.";
  } else {
   status = "Opening Inventory and targeting " + kind + ". " + actionsBridge.status();
  }
 }

 private void observePotion(int tick) {
  String pending = policy.pendingPotion();
  if (pending == null) {
   return;
  }
  boolean confirmed = policy.observePotion(doseCount(pending), tick, profile.potionCooldownTicks);
  if (confirmed) {
   status = "Confirmed one consumed dose of " + pending + "; shared cooldown started from this tick.";
  }
 }

 private void observeGear(int tick) {
  if (pendingGear.isEmpty()) return;
  Set<Integer> worn = equipmentIds();
  java.util.Iterator<Map.Entry<Integer, Integer>> requests = pendingGear.entrySet().iterator();
  while (requests.hasNext()) {
   Map.Entry<Integer, Integer> request = requests.next();
   if (requiredItemReady(request.getKey(), worn) || tick - request.getValue() >= 4) {
    requests.remove();
   }
  }
 }

 private int doseCount(String kind) {
  List<Integer> family = profile.supply(kind);
  ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
  int total = 0;
  if (inventory != null) {
   for (Item item : inventory.getItems()) {
    if (item == null) {
     continue;
    }
    int index = family.indexOf(item.getId());
    if (index >= 0) {
     total += Math.max(1, family.size() - index) * item.getQuantity();
    }
   }
  }
  return total;
 }

 private void equip(ToaCombatPolicy.Style style, int tick) {
  if (style == null) {
   return;
  }
  List<Integer> required = styleItems(style);
  Set<Integer> worn = equipmentIds();
  Set<Integer> confirmed = new LinkedHashSet<>(worn);
  for (int itemId : required) if (requiredItemReady(itemId, worn)) confirmed.add(itemId);
  pendingGear.keySet().removeIf(confirmed::contains);
  gearBatch.plan(style, required, confirmed, pendingGear.keySet());
  if (styleReady(style, worn)) {
   if (style == ToaCombatPolicy.Style.SPEC && phase == ToaCombatPolicy.Phase.BEGIN) {
    status = "Elder maul confirmed for entry; special remains off until the obelisk tile.";
    return;
   }
   Prayer boost = profile.offensive.get(style == ToaCombatPolicy.Style.SPEC ? "MELEE" : style.name());
   if (boost != null && !client.isPrayerActive(boost)) {
    if (actionsBridge.activatePrayer(boost, 30)) {
     status = "Requested visible offensive prayer " + boost + ".";
    } else {
     status = "Opening Prayer for " + boost + ". " + actionsBridge.status();
    }
    return;
   }
   status = style + " gear and offensive prayer confirmed.";
   return;
  }

  long elapsed = actionsBridge.tickElapsedMs();
  long period = Math.max(400, actionsBridge.observedTickPeriodMs());
  int itemId = ToaGearBatch.next(required, confirmed, pendingGear.keySet());
  if (itemId < 0) {
   status = gearBatch.status(tick) + " Waiting for observed equipment confirmation.";
   return;
  }
  if (!gearBatch.allowed(tick, elapsed, period)) {
   status = gearBatch.status(tick);
   return;
  }
  Item item = firstInventory(Collections.singletonList(itemId));
  if (item == null) {
   String name = client.getItemDefinition(itemId) == null
    ? "item " + itemId : client.getItemDefinition(itemId).getName();
   status = "Missing " + style + " gear item " + name + " (" + itemId + "). Urgent dodges remain active.";
   return;
  }
  if (actionsBridge.inventoryAction(itemId, "Wield", "Wear", "Equip")) {
   pendingGear.put(itemId, tick);
   gearBatch.sent(tick, elapsed, period);
   status = gearBatch.status(tick);
  } else {
   status = "Opening Inventory for " + style + " gear. " + actionsBridge.status();
  }
 }

 private void attack(String purpose, int tick) {
  NPC target = purpose.equals("energy-siphon") ? findSiphon() : phaseTarget();
  if (target == null) {
   status = "Target for " + purpose + " is not visible.";
   return;
  }
  ToaCombatPolicy.Style style = purpose.equals("elidinis") ? elidinisStyle
   : purpose.equals("tumeken") ? ToaCombatPolicy.Style.RANGED
   : ToaCombatPolicy.Style.MELEE;
  Prayer boost = profile.offensive.get(style.name());
  if (boost != null && !client.isPrayerActive(boost)) {
   if (actionsBridge.activatePrayer(boost, 30)) {
    status = "Requested " + boost + " before " + purpose + ".";
   } else {
    status = "Opening Prayer for " + boost + ". " + actionsBridge.status();
   }
   return;
  }
   WorldPoint wardenCamp = purpose.equals("elidinis") ? resolveWardenCampTile() : null;
   if (purpose.equals("elidinis") && wardenCamp != null
    && !atWardenCombatPosition(currentPosition(), wardenCamp)) {
   handleWardenCampMove(tick);
   return;
  }
  if (purpose.equals("obelisk-special") && !Combat.isSpecEnabled()) {
   status = "Waiting for the visible special bar to confirm active.";
   return;
  }
  if (purpose.equals("obelisk-special") && obeliskSpecPending) {
   status = "Elder maul attack sent; waiting for observed special-energy consumption before the charged Scythe.";
   return;
  }
  Player self = client.getLocalPlayer();
  if (self.getInteracting() == target && tick - lastAttackRequest < 2) {
   status = "Maintaining " + purpose + " DPS; no redundant re-attack click.";
   return;
  }
  if (tick - lastAttackRequest < 1) {
   return;
  }
  if (actionsBridge.npcAction(target, "Attack")) {
   lastAttackRequest = tick;
   if (purpose.equals("obelisk-special")) {
    obeliskSpecPending = true;
    obeliskSpecEnergyBefore = Combat.getSpecEnergy();
    obeliskSpecRequestedTick = tick;
    status = "Elder maul attack sent; awaiting observed special-energy consumption.";
    return;
   }
   status = "Requested normal visible attack: " + purpose + ".";
  } else {
   status = "Waiting for a visible attack action on " + purpose + ". " + actionsBridge.status();
  }
 }

 private void updatePhase(int tick) {
  // The obelisk is visible before the raid starts.  Visibility alone must not
  // enter its combat branch, otherwise the prototype arms the special before
  // selecting Osmumten's Begin option.
  if (!entryFlow.active()) {
    NPC obelisk = findObelisk();
    NPC osmumten = findOsmumten();
   boolean obeliskAttackable = obelisk != null && obelisk.hasAction("Attack");
   boolean beginStillAvailable = osmumten != null && osmumten.hasAction("Begin");
   if (entryFlow.waiting() && obeliskAttackable) {
    entryFlow.confirmActive();
    status = "Observed the ToA encounter transition; moving to the obelisk tile before arming special.";
   } else if (entryFlow.waiting()) {
    if (entryFlow.retryDue(tick, beginStillAvailable)) {
     status = "Begin produced no attackable obelisk; retrying Osmumten without assuming combat started.";
    }
    phase = ToaCombatPolicy.Phase.UNKNOWN;
    return;
   } else {
    phase = ToaCombatPolicy.Phase.BEGIN;
    return;
   }
  }

  ToaCombatPolicy.Phase previous = phase;
  ToaCombatPolicy.Phase observed = null;
  boolean coreVisible = findCore() != null;
  NPC activeGuardian = findElidinis();
  // Orion leaves the defeated Guardian visible for roughly seven ticks before
  // the core becomes targetable. Begin the core preset at observed zero health
  // so Rapier/Avernic and Piety are ready when the core lands.
  boolean guardianCollapsed = activeGuardian != null && guardianCollapsed(activeGuardian.getHealthRatio());
  coreVisibleLastTick = coreVisible;
  if (coreVisible || guardianCollapsed) {
   sawCore = true;
   guardianStyleAdvancePending = true;
  }
  NPC finalCandidate = findAny(profile.tumeken);
  if (finalCandidate != null && finalCandidate.getHealthRatio() == 0) finalDeathObserved = true;
  NPC finalWarden = finalCandidate != null && !finalCandidate.isDead()
   && finalCandidate.getHealthRatio() != 0 ? finalCandidate : null;
  if (findSiphon() != null || finalWarden != null) {
   observed = ToaCombatPolicy.Phase.TUMEKEN;
   sawTumeken = true;
   lastTumekenTick = tick;
  } else if (coreVisible || guardianCollapsed) {
   observed = ToaCombatPolicy.Phase.CORE;
  } else if (activeGuardian != null) {
   observed = ToaCombatPolicy.Phase.ELIDINIS;
   sawOpeningWarden = true;
  } else if (!sawOpeningWarden && findObelisk() != null) {
   observed = ToaCombatPolicy.Phase.OBELISK;
  } else if (findOsmumten() != null && !sawTumeken) {
   observed = ToaCombatPolicy.Phase.BEGIN;
  }
  if (observed != null) {
   phase = observed;
   phaseEvidenceTick = tick;
  } else if (finalDeathObserved && sawTumeken && tick - lastTumekenTick >= 8) {
   phase = ToaCombatPolicy.Phase.COMPLETE;
  } else if (isCombatPhase(previous) && tick - phaseEvidenceTick <= 12) {
   // NPC transforms and attackability changes briefly remove the target from
   // the scene. Retain the last verified strategy instead of freezing UNKNOWN.
   phase = previous;
  } else {
   phase = ToaCombatPolicy.Phase.UNKNOWN;
  }
  if (phase != previous) runtimeTrace.event("PHASE_CHANGED", tick, "from", previous.name(), "to", phase.name());
  if (phase == ToaCombatPolicy.Phase.ELIDINIS && previous != ToaCombatPolicy.Phase.ELIDINIS) {
   if (guardianStyleAdvancePending) {
    elidinisStyle = elidinisStyle == ToaCombatPolicy.Style.MAGIC
     ? ToaCombatPolicy.Style.RANGED : ToaCombatPolicy.Style.MAGIC;
    guardianStyleAdvancePending = false;
    runtimeTrace.event("GUARDIAN_STYLE_ADVANCED", tick, "style", elidinisStyle.name());
   }
    // The first exposure still uses the demonstrated two-stage approach. Once
    // a core has appeared, keep the real post-core position beside the live
    // Guardian. Rebuilding this from the obelisk tile made the player run back
    // east after every core and interrupted the ranged switch.
    if (!sawCore) {
     resolvedWardenStartTile = null;
     resolvedWardenCampTile = null;
     wardenStartReached = false;
    } else {
     WorldPoint current = currentPosition();
     if (current != null) resolvedWardenCampTile = current;
     wardenStartReached = true;
    }
   wardenMoveRequested = false;
   wardenMoveRequestCycle = -1000;
   wardenMoveOrigin = null;
   wardenMoveDestination = null;
  } else if (phase != ToaCombatPolicy.Phase.ELIDINIS) {
   wardenMoveRequested = false;
   wardenMoveDestination = null;
  }
 }

 private static boolean isCombatPhase(ToaCombatPolicy.Phase value) {
  return value == ToaCombatPolicy.Phase.OBELISK || value == ToaCombatPolicy.Phase.ELIDINIS
   || value == ToaCombatPolicy.Phase.CORE || value == ToaCombatPolicy.Phase.TUMEKEN;
 }

 static boolean guardianCollapsed(int healthRatio) {
  return healthRatio == 0;
 }

 private void updateEncounterState(int tick) {
  ToaStateMachine.State before = stateMachine.state();
  ToaStateMachine.Evidence evidence = new ToaStateMachine.Evidence();
  evidence.loggedIn = client.getGameState() == GameState.LOGGED_IN;
  evidence.hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
  evidence.preparationComplete = policy.preparationComplete();
  evidence.entryRequested = entryFlow.waiting() || entryFlow.active();
  evidence.encounterActive = entryFlow.active();
  evidence.obelisk = phase == ToaCombatPolicy.Phase.OBELISK;
  evidence.obeliskSpecial = evidence.obelisk && (!policy.obeliskSpecialAttempted() || obeliskSpecPending);
  evidence.warden = phase == ToaCombatPolicy.Phase.ELIDINIS;
  evidence.wardenSpecial = evidence.warden && tick <= immediateWarningUntil;
  evidence.core = phase == ToaCombatPolicy.Phase.CORE;
  evidence.finalWarden = phase == ToaCombatPolicy.Phase.TUMEKEN && findSiphon() == null;
  evidence.siphon = phase == ToaCombatPolicy.Phase.TUMEKEN && findSiphon() != null;
  evidence.bossDefeated = finalDeathObserved;
  evidence.complete = phase == ToaCombatPolicy.Phase.COMPLETE;
  ToaStateMachine.State after = stateMachine.update(evidence);
  if (after != before) runtimeTrace.event("STATE_CHANGED", tick, "from", before.name(), "to", after.name());
 }

 private void updateThreats(int tick) {
  if (tick > protectionUntil) {
   pendingProtection = null;
  }
  if (tick > floorWarningUntil) {
   floorSafeTile = null;
  }
  if (tick > immediateWarningUntil) {
   immediateSafeTile = null;
  }
  if (phase == ToaCombatPolicy.Phase.TUMEKEN && configuredFloorObjectNearSelf()) {
   registerFloorWarning(tick, "configured floor object");
  }
  WorldPoint spinning = nearestSpinningLight();
  if (spinning != null) {
   registerImmediateWarning(spinning, tick, "Spinning Warden light");
  }
 }

 private WorldPoint nearestSpinningLight() {
  WorldPoint self = currentPosition();
  if (self == null || client.getGraphicsObjects() == null) return null;
  WorldPoint nearest = null;
  int distance = Integer.MAX_VALUE;
  for (GraphicsObject graphic : client.getGraphicsObjects()) {
   if (graphic == null || graphic.finished() || graphic.getLevel() != client.getPlane()
    || !ToaSignals.spinningLight(graphic.getId())) continue;
   WorldPoint point = instance(graphic.getLocation());
   int next = point == null ? Integer.MAX_VALUE : point.distanceTo(self);
   if (next <= 2 && next < distance) { nearest = point; distance = next; }
  }
  return nearest;
 }

 private void registerFloorWarning(int tick, String source) {
  if (tick != lastFloorSignalTick) {
   floorSafeTile = slamSafeTile(slamSequence++ % 3);
   lastFloorSignalTick = tick;
  }
  floorWarningUntil = Math.max(floorWarningUntil, tick + 2);
  status = "Floor slam warning from " + source + "; all other actions are suspended.";
 }

 private void registerImmediateWarning(WorldPoint source, int tick, String kind) {
  immediateSafeTile = adjacentSafeTile(source);
  immediateWarningUntil = Math.max(immediateWarningUntil, tick + 2);
  status = kind + " detected; urgent movement outranks prayer, supplies, gear and DPS.";
 }

 private void handleAbsoluteFloorMove(int tick) {
  if (floorSafeTile == null) {
   stop("Floor slam detected without a safe-tile solution");
   return;
  }
  actionsBridge.cancelPendingControl();
  WorldPoint current = currentPosition();
  if (current.equals(floorSafeTile)) {
   status = "Holding the floor-slam safe tile; every lower-priority action remains suspended.";
   return;
  }
  handleMove(floorSafeTile, tick, "Floor slam: moving to the calculated safe lane");
 }

 private void handleWardenCampMove(int tick) {
  WorldPoint start = resolveWardenStartTile();
  WorldPoint camp = resolveWardenCampTile();
  WorldPoint current = currentPosition();
  if (start == null || camp == null || current == null) {
   status = "Waiting for the live right-stream block and guardian firing tiles.";
   return;
  }
  if (current.equals(start)) wardenStartReached = true;
  if (current.equals(camp)) {
   wardenMoveRequested = false;
   wardenMoveDestination = null;
   status = "Yellow Warden firing tile confirmed; completing Holy Cards setup.";
   return;
  }
  WorldPoint destination = wardenStartReached ? camp : start;
  WorldPoint rendered = renderedInstanceTile(destination);
  if (rendered == null) {
   status = (wardenStartReached ? "The right-guardian firing tile" : "The right orb-stream blocking tile")
    + " is not rendered in this instance yet.";
   return;
  }
  if (actionsBridge.move(rendered) || actionsBridge.moveMinimap(rendered)) {
   wardenMoveRequested = true;
   wardenMoveRequestCycle = client.getGameCycle();
   wardenMoveOrigin = current;
   wardenMoveDestination = destination;
   lastMoveTarget = destination;
   lastMoveRequest = tick;
   status = wardenStartReached
    ? "One destination click sent from the block tile to the right-guardian firing tile."
    : "One destination click sent to the demonstrated right orb-stream blocking tile; switching ranged gear while moving.";
  } else {
   status = "Waiting for a visible route through the two-stage Warden approach. " + actionsBridge.status();
  }
 }

 private void handleObeliskMove(int tick) {
  WorldPoint target = resolveObeliskBlockTile();
  WorldPoint current = currentPosition();
  if (target == null || current == null) {
   status = "Obelisk blocking tile or player position is unavailable.";
   return;
  }
  if (current.equals(target)) {
   blockMoveAttempts = 0;
   blockMoveObservedPosition = current;
   lastMoveTarget = target;
    status = "Right orb-stream blocking tile confirmed; special attack may now begin.";
   return;
  }
  if (!target.equals(lastMoveTarget)) {
   lastMoveTarget = target;
   lastMoveRequest = -1000;
   blockMoveAttempts = 0;
   blockMoveObservedPosition = current;
  } else if (blockMoveObservedPosition == null || !current.equals(blockMoveObservedPosition)) {
   blockMoveObservedPosition = current;
    status = "Progress toward the right orb-stream blocking tile observed; allowing the current route to continue.";
   if (client.getLocalPlayer() != null && client.getLocalPlayer().isMoving()) {
    return;
   }
  }
  if (client.getLocalPlayer() != null && client.getLocalPlayer().isMoving()
    && tick - lastMoveRequest < 4) {
    status = "Walking toward the right orb-stream blocking tile; no duplicate destination click.";
   return;
  }
  if (tick - lastMoveRequest < 2) {
    status = "Awaiting observed progress toward the right orb-stream blocking tile.";
   return;
  }

  WorldPoint rendered = renderedInstanceTile(target);
  if (rendered == null) {
    status = "The live right orb-stream blocking tile does not resolve inside this raid instance.";
   return;
  }
  boolean minimap = blockMoveAttempts > 0;
  boolean sent = minimap ? actionsBridge.moveMinimap(rendered) : actionsBridge.move(rendered);
  if (!sent && !minimap) {
   sent = actionsBridge.moveMinimap(rendered);
   minimap = sent;
  }
  if (sent) {
   blockMoveAttempts++;
   lastMoveRequest = tick;
   blockMoveObservedPosition = current;
   status = (minimap ? "Minimap fallback" : "Visible ground click")
     + " sent to the live right orb-stream blocking tile; awaiting real movement.";
  } else {
    status = "Waiting for a visible route to the right orb-stream blocking tile. " + actionsBridge.status();
  }
 }

 private WorldPoint resolveObeliskBlockTile() {
  if (resolvedObeliskBlockTile != null) return resolvedObeliskBlockTile;
  NPC obelisk = findObelisk();
  if (obelisk == null || obelisk.getWorldArea() == null) return profile.blockTile;
  List<WorldArea> wardens = new ArrayList<>();
  for (NPC npc : client.getNpcs()) if (npc != null && profile.chargingWardens.contains(npc.getId()) && npc.getWorldArea() != null) wardens.add(npc.getWorldArea());
  WorldPoint live = ToaObeliskLane.rightStreamTile(obelisk.getWorldArea(), wardens);
  LocalPoint local = live == null ? null : LocalPoint.fromWorld(client, live);
  WorldPoint template = local == null ? null : instance(local);
  if (template != null) resolvedObeliskBlockTile = template;
  return resolvedObeliskBlockTile != null ? resolvedObeliskBlockTile : profile.blockTile;
 }

 private WorldPoint resolveWardenStartTile() {
  if (resolvedWardenStartTile != null) return resolvedWardenStartTile;
  // The first screenshot's right-stream block tile is already the correct
  // transition origin. Do not mirror it back across the obelisk.
  resolvedWardenStartTile = resolveObeliskBlockTile();
  return resolvedWardenStartTile;
 }

 private WorldPoint resolveWardenCampTile() {
  if (resolvedWardenCampTile != null) return resolvedWardenCampTile;
  NPC warden = findElidinis();
  if (warden == null || warden.getLocalLocation() == null) return profile.wardenCampTile;
  WorldPoint target = instance(warden.getLocalLocation());
  WorldPoint start = resolveWardenStartTile();
  // The second screenshot is the firing tile beside the active right guardian.
  // Resolve it from that live guardian after the right orb stream is blocked;
  // a saved coordinate is only a last-resort fallback.
  resolvedWardenCampTile = ToaWardenCamp.choose(target, start, profile.attackRange);
  if (resolvedWardenCampTile == null) resolvedWardenCampTile = profile.wardenCampTile;
  return resolvedWardenCampTile;
 }

 private boolean atWardenCombatPosition(WorldPoint current, WorldPoint camp) {
  if (current == null) return false;
  if (sawCore) {
   NPC guardian = findElidinis();
   WorldPoint guardianPoint = guardian == null || guardian.getLocalLocation() == null
    ? null : instance(guardian.getLocalLocation());
   int range = profile == null || profile.attackRange <= 0 ? 10 : profile.attackRange;
   if (guardianPoint != null && current.distanceTo(guardianPoint) <= range) return true;
   // Core interactions can pull the player a tile away from the original camp.
   // That is still a valid firing position and must not trigger backtracking.
   return camp != null && current.distanceTo(camp) <= 2;
  }
  return camp != null && current.equals(camp);
 }

 private void handleMove(WorldPoint tile, int tick, String reason) {
  if (tile == null) {
   status = reason + ": no rendered safe tile is available.";
   return;
  }
  if (currentPosition().equals(tile)) {
   status = reason + ": position confirmed.";
   return;
  }
  if (tile.equals(lastMoveTarget) && tick - lastMoveRequest < 2) {
   status = reason + "; awaiting observed movement without spam-clicking.";
   return;
  }
  WorldPoint rendered = renderedInstanceTile(tile);
  if (rendered == null) {
   status = reason + ": saved template tile is not present in this loaded instance.";
   return;
  }
  if (actionsBridge.move(rendered)) {
   lastMoveTarget = tile;
   lastMoveRequest = tick;
   status = reason + " using one destination click.";
  } else {
   status = reason + "; waiting for the tile to be rendered and unobstructed. " + actionsBridge.status();
  }
 }

 private WorldPoint renderedInstanceTile(WorldPoint template) {
  if (template == null || !client.isInInstancedRegion()) {
   return template;
  }
  Collection<WorldPoint> candidates = WorldPoint.toLocalInstance(client, template);
  Player self = client.getLocalPlayer();
  WorldPoint live = self == null ? null : self.getWorldLocation();
  WorldPoint best = null;
  int distance = Integer.MAX_VALUE;
  for (WorldPoint candidate : candidates) {
   if (candidate == null || live != null && candidate.getPlane() != live.getPlane()) continue;
   int next = live == null ? 0 : candidate.distanceTo(live);
   if (best == null || next < distance) {
    best = candidate;
    distance = next;
   }
  }
  return best;
 }

 private WorldPoint slamSafeTile(int step) {
  WorldPoint current = currentPosition();
  NPC warden = find(profile.tumeken);
  WorldPoint boss = warden == null ? null : warden.getWorldLocation();
  // Keep one fixed lane anchor for the three-part pattern; using the player's
  // last dodge tile as the next centre would slowly drift across the arena.
  WorldPoint center = profile.wardenCampTile != null ? profile.wardenCampTile
   : profile.blockTile != null ? profile.blockTile : current;
  if (boss == null) {
   return center;
  }
  int dx = center.getX() - boss.getX();
  int dy = center.getY() - boss.getY();
  boolean lateralX = Math.abs(dy) >= Math.abs(dx);
  int offset = step == 0 ? -2 : step == 1 ? 2 : 0;
  WorldPoint candidate = lateralX
   ? new WorldPoint(center.getX() + offset, center.getY(), center.getPlane())
   : new WorldPoint(center.getX(), center.getY() + offset, center.getPlane());
  return candidate.distanceTo(boss) <= profile.attackRange ? candidate : center;
 }

 private WorldPoint adjacentSafeTile(WorldPoint danger) {
  WorldPoint self = currentPosition();
  WorldPoint boss = null;
  NPC target = phaseTarget();
  if (target != null) {
   boss = target.getWorldLocation();
  }
  WorldPoint best = null;
  int bestScore = Integer.MIN_VALUE;
  for (int dx = -1; dx <= 1; dx++) {
   for (int dy = -1; dy <= 1; dy++) {
    if (Math.abs(dx) + Math.abs(dy) != 1) {
     continue;
    }
    WorldPoint candidate = new WorldPoint(self.getX() + dx, self.getY() + dy, self.getPlane());
    int hazardDistance = danger == null ? 2 : candidate.distanceTo(danger);
    int rangePenalty = boss == null || candidate.distanceTo(boss) <= profile.attackRange ? 0 : 20;
    int signalPenalty = configuredFloorObjectAt(candidate) ? 50 : 0;
    int spinningPenalty = spinningLightNear(candidate) ? 200 : 0;
    int score = hazardDistance * 10 - rangePenalty - signalPenalty - spinningPenalty;
    if (score > bestScore) {
     bestScore = score;
     best = candidate;
    }
   }
  }
  return best;
 }

 private boolean spinningLightNear(WorldPoint tile) {
  if (tile == null || client.getGraphicsObjects() == null) return false;
  for (GraphicsObject graphic : client.getGraphicsObjects()) {
   if (graphic == null || graphic.finished() || graphic.getLevel() != client.getPlane()
    || !ToaSignals.spinningLight(graphic.getId())) continue;
   WorldPoint point = instance(graphic.getLocation());
   if (point != null && point.distanceTo(tile) <= 1) return true;
  }
  return false;
 }

 private boolean configuredFloorObjectNearSelf() {
  WorldPoint self = currentPosition();
  for (int id : profile.floorObjects) {
   for (WorldPoint point : liveSceneSignals.getOrDefault(id, Collections.emptySet())) {
    if (point.distanceTo(self) <= 1) {
     return true;
    }
   }
  }
  return false;
 }

 private boolean configuredFloorObjectAt(WorldPoint tile) {
  for (int id : profile.floorObjects) {
   if (liveSceneSignals.getOrDefault(id, Collections.emptySet()).contains(tile)) {
    return true;
   }
  }
  return false;
 }

 private ToaCombatPolicy.Style wornStyle() {
  Set<Integer> worn = equipmentIds();
  for (ToaCombatPolicy.Style style : Arrays.asList(
    ToaCombatPolicy.Style.SPEC,
    ToaCombatPolicy.Style.MELEE,
    ToaCombatPolicy.Style.MAGIC,
    ToaCombatPolicy.Style.RANGED)) {
   List<Integer> required = styleItems(style);
   if (!required.isEmpty() && styleReady(style, worn)) {
    return style;
   }
  }
  return null;
 }

 private boolean styleReady(ToaCombatPolicy.Style style, Set<Integer> worn) {
  List<Integer> required = styleItems(style);
  if (required.isEmpty()) {
   return false;
  }
  for (int itemId : required) {
   if (!requiredItemReady(itemId, worn)) {
    return false;
   }
  }
  return true;
 }

 private List<Integer> styleItems(ToaCombatPolicy.Style style) {
  List<Integer> configured = style == ToaCombatPolicy.Style.MELEE
   && phase == ToaCombatPolicy.Phase.CORE ? profile.coreGear : profile.style(style);
  if (style != ToaCombatPolicy.Style.SPEC) {
    if (style == ToaCombatPolicy.Style.RANGED) {
     // Holy Cards is the attack-enabling item, so submit it in the first
     // equipment batch instead of letting an interrupted switch omit it.
     return ToaGearBatch.prioritize(configured, profile.rangedWeaponId);
    }
    if (style == ToaCombatPolicy.Style.MELEE && phase == ToaCombatPolicy.Phase.CORE) {
     // The core can be attacked only briefly. Put both weapon slots first while
     // still requiring every configured orb-melee item before styleReady.
     return ToaGearBatch.prioritize(configured, profile.rapierId, profile.avernicDefenderId);
    }
    return configured;
  }
  List<Integer> compatible = new ArrayList<>();
  for (int itemId : configured) {
   ItemComposition definition = client.getItemDefinition(itemId);
   String name = definition == null ? "" : definition.getName();
   if (itemId != profile.elderMaulId && RaidGearRules.isOffhandName(name)) {
    continue;
   }
   compatible.add(itemId);
  }
  return compatible;
 }

 private boolean requiredItemReady(int itemId, Set<Integer> worn) {
  if (worn.contains(itemId)) {
   return true;
  }
  if (itemId == profile.elderMaulId) {
   return equippedWeaponNamed("elder maul");
  }
  if (itemId == profile.meleeWeaponId) {
   return equippedChargedScythe();
  }
  if (itemId == profile.rapierId) {
   return equippedWeaponNamed("ghrazi rapier", "rapier");
  }
  if (itemId == profile.avernicDefenderId) {
   return equippedOffhandNamed("avernic defender");
  }
  if (itemId == profile.rangedWeaponId) {
   return equippedWeaponNamed("holy cards");
  }
  return false;
 }

 private boolean equippedWeaponNamed(String... expected) {
  Player self = client.getLocalPlayer();
  if (self == null || self.getPlayerComposition() == null) {
   return false;
  }
  int weaponId = self.getPlayerComposition().getEquipmentId(KitType.WEAPON);
  ItemComposition definition = weaponId < 0 ? null : client.getItemDefinition(weaponId);
  String name = definition == null ? "" : definition.getName();
  if (name == null) return false;
  String normalized = name.toLowerCase(Locale.ROOT);
  for (String candidate : expected) {
   if (candidate != null && normalized.contains(candidate.toLowerCase(Locale.ROOT))) return true;
  }
  return false;
 }
 private boolean equippedOffhandNamed(String expected) {
  Player self=client.getLocalPlayer();
  if(self==null||self.getPlayerComposition()==null)return false;
  int id=self.getPlayerComposition().getEquipmentId(KitType.SHIELD);
  ItemComposition definition=id<0?null:client.getItemDefinition(id);
  String name=definition==null||definition.getName()==null?"":definition.getName().toLowerCase(Locale.ROOT);
  return name.contains(expected.toLowerCase(Locale.ROOT));
 }
 private boolean equippedChargedScythe(){Player self=client.getLocalPlayer();if(self==null||self.getPlayerComposition()==null)return false;int weaponId=self.getPlayerComposition().getEquipmentId(KitType.WEAPON);ItemComposition definition=weaponId<0?null:client.getItemDefinition(weaponId);String name=definition==null||definition.getName()==null?"":definition.getName().replaceAll("<[^>]*>"," ").trim().toLowerCase(Locale.ROOT);return name.contains("scythe of vitur")&&!name.contains("uncharged")&&!name.matches(".*(?:\\(0\\)|0\\s*charges?).*");}

 private void observeObeliskSpecial(int tick) {
  if (!obeliskSpecPending) {
   return;
  }
  int energy = Combat.getSpecEnergy();
  if (obeliskSpecEnergyBefore >= 0 && energy < obeliskSpecEnergyBefore) {
   obeliskSpecPending = false;
   obeliskSpecEnergyBefore = -1;
   policy.markObeliskSpecialAttempted();
    status = "Elder maul special confirmed by energy consumption; switching to the charged Scythe.";
   return;
  }
  if (tick - obeliskSpecRequestedTick >= 5) {
   obeliskSpecPending = false;
   obeliskSpecEnergyBefore = -1;
   status = "Elder maul special was not confirmed; keeping the maul equipped and retrying safely.";
  }
 }

 private Set<Integer> equipmentIds() {
  Set<Integer> result = new LinkedHashSet<>();
  ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
  if (equipment != null) {
   for (Item item : equipment.getItems()) {
    if (item != null && item.getId() > 0) {
     result.add(item.getId());
    }
   }
  }
  return result;
 }

 private Item firstInventory(List<Integer> ids) {
  ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
  if (inventory == null) {
   return null;
  }
  for (Item item : inventory.getItems()) {
   if (item != null && ids.contains(item.getId())) {
    return item;
   }
  }
  return null;
 }

 private NPC phaseTarget() {
  switch (phase) {
   case OBELISK:
    return findObelisk();
   case CORE:
    return findCore();
   case ELIDINIS:
    return findElidinis();
   case TUMEKEN:
    NPC siphon = findSiphon();
    return siphon == null ? find(profile.tumeken) : siphon;
   default:
    return null;
  }
 }

 private NPC findOsmumten() { return findNamed(profile.osmumten, "osmumten"); }
 private NPC findObelisk() { return findNamed(profile.obelisk, "obelisk"); }
 private NPC findCore() { return findNamed(profile.core, "core"); }
 private NPC findSiphon() { return findNamed(profile.siphon, "energy siphon", "siphon"); }

 private NPC findNamed(Set<Integer> ids, String... names) {
  NPC configured = find(ids);
  if (configured != null) return configured;
  NPC best = null;
  int distance = Integer.MAX_VALUE;
  Player self = client.getLocalPlayer();
  if (self == null || self.getWorldLocation() == null) return null;
  for (NPC npc : client.getNpcs()) {
   if (npc == null || npc.isDead() || npc.getWorldLocation() == null || !nearSelf(npc, 40)) continue;
   String clean = cleanNpcName(npc);
   boolean matches = false;
   for (String name : names) if (clean.contains(name)) { matches = true; break; }
   if (!matches) continue;
   int next = npc.getWorldLocation().distanceTo(self.getWorldLocation());
   if (best == null || next < distance) { best = npc; distance = next; }
  }
  return best;
 }

 private static String cleanNpcName(NPC npc) {
  String name = npc == null || npc.getName() == null ? "" : npc.getName();
  return name.replaceAll("<[^>]*>", " ").replace('\u00a0', ' ')
   .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
 }

 private NPC findAny(Set<Integer> ids) {
  if (ids == null || ids.isEmpty() || client.getLocalPlayer() == null) return null;
  for (NPC npc : client.getNpcs()) if (npc != null && ids.contains(npc.getId()) && nearSelf(npc, 40)) return npc;
  return null;
 }

 private NPC findElidinis() {
  NPC best = null;
  int bestScore = Integer.MAX_VALUE;
  Player self = client.getLocalPlayer();
  if (self == null || self.getWorldLocation() == null) return null;
  for (NPC npc : client.getNpcs()) {
   if (npc == null || npc.isDead() || npc.getWorldLocation() == null || !nearSelf(npc, 40)
    || !npc.hasAction("Attack") || profile.chargingWardens.contains(npc.getId())
    || profile.obelisk.contains(npc.getId()) || profile.core.contains(npc.getId())
    || profile.siphon.contains(npc.getId()) || profile.tumeken.contains(npc.getId())) continue;
   String name = cleanNpcName(npc);
   if (!name.contains("warden") && !name.contains("guardian")) continue;
   int distance = npc.getWorldLocation().distanceTo(self.getWorldLocation());
   int score = distance;
   if (!profile.elidinis.contains(npc.getId())) score += 100;
   if (npc.getHealthRatio() <= 0) score += 25;
   if (best == null || score < bestScore) { best = npc; bestScore = score; }
  }
  return best;
 }

 private NPC find(Set<Integer> ids) {
  if (ids == null || ids.isEmpty() || client.getLocalPlayer() == null) {
   return null;
  }
  for (NPC npc : client.getNpcs()) {
   if (ids.contains(npc.getId()) && nearSelf(npc, 40) && !npc.isDead() && npc.getHealthRatio() != 0) {
    return npc;
   }
  }
  return null;
 }

 private boolean nearSelf(NPC npc, int distance) {
  Player self = client.getLocalPlayer();
  return npc != null && self != null && npc.getWorldLocation() != null
   && self.getWorldLocation() != null && npc.getWorldLocation().distanceTo(self.getWorldLocation()) <= distance;
 }

 private WorldPoint currentPosition() {
  Player self = client.getLocalPlayer();
  WorldPoint point = self == null || self.getLocalLocation() == null
   ? null : instance(self.getLocalLocation());
  return point == null && self != null ? self.getWorldLocation() : point;
 }

 private WorldPoint instance(LocalPoint local) {
  return local == null ? null : WorldPoint.fromLocalInstance(client, local);
 }

 private static Prayer protection(String style) {
  if (style == null) {
   return null;
  }
  switch (style.toUpperCase(Locale.ROOT)) {
   case "MAGIC":
    return Prayer.PROTECT_FROM_MAGIC;
   case "RANGED":
   case "MISSILES":
    return Prayer.PROTECT_FROM_MISSILES;
   case "MELEE":
    return Prayer.PROTECT_FROM_MELEE;
   default:
    return null;
  }
 }

 private static Prayer prayer(String name) {
  if (name == null || name.trim().isEmpty()) return null;
  try {
   return Prayer.valueOf(name.trim().toUpperCase(Locale.ROOT));
  } catch (IllegalArgumentException ignored) {
   return null;
  }
 }

 @Subscribe
 public synchronized void onProjectileMoved(ProjectileMoved event) {
  Projectile projectile = event.getProjectile();
  if (projectile == null) {
   return;
  }
  seenProjectiles.removeIf(old -> old.getEndCycle() < client.getGameCycle());
  boolean firstObservation = !seenProjectiles.contains(projectile) && seenProjectiles.size() < 256;
  if (firstObservation) seenProjectiles.add(projectile);
  if (recording && firstObservation && actions.size() < MAX_ACTIONS) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("type", "PROJECTILE_OBSERVED");
    data.put("tick", client.getTickCount());
    data.put("cycle", client.getGameCycle());
    data.put("id", projectile.getId());
    data.put("startCycle", projectile.getStartCycle());
    data.put("endCycle", projectile.getEndCycle());
    data.put("floor", projectile.getFloor());
    if (projectile.getTarget() != null) {
     data.put("targetLocal", new int[] {projectile.getTarget().getX(), projectile.getTarget().getY()});
    }
    actions.add(data);
  }
  if (running) {
   runtimeTrace.event("PROJECTILE", client.getTickCount(),
    "cycle", client.getGameCycle(), "id", projectile.getId(),
    "sourceLocal", new int[] {projectile.getX1(), projectile.getY1()},
    "targetLocal", projectile.getTarget() == null ? null
     : new int[] {projectile.getTarget().getX(), projectile.getTarget().getY()},
    "startCycle", projectile.getStartCycle(), "endCycle", projectile.getEndCycle(),
    "targetsPlayer", projectileTargetsSelf(projectile), "phase", phase.name());
  }
  if (running && profile != null) {
   boolean targetsSelf = projectileTargetsSelf(projectile);
   String prayer = profile.protectionProjectiles.get(projectile.getId());
   if (prayer != null && targetsSelf) {
    pendingProtection = prayer;
    protectionUntil = client.getTickCount() + 2;
   }
   if (firstObservation && targetsSelf && ToaSignals.bombProjectile(projectile.getId())) {
    WorldPoint landing = instance(projectile.getTarget());
    registerImmediateWarning(landing == null ? currentPosition() : landing,
     client.getTickCount(), "Warden bomb projectile " + projectile.getId());
   }
  }
 }

 private boolean projectileTargetsSelf(Projectile projectile) {
  Player self = client.getLocalPlayer();
  if (self == null || self.getLocalLocation() == null || projectile.getTarget() == null) {
   return false;
  }
  int dx = Math.abs(projectile.getTarget().getX() - self.getLocalLocation().getX());
  int dy = Math.abs(projectile.getTarget().getY() - self.getLocalLocation().getY());
  return dx <= 128 && dy <= 128;
 }

 @Subscribe public synchronized void onAnimationChanged(AnimationChanged event) {
  Actor actor = event.getActor();
  actorSignal(actor, "ANIMATION");
  if (!running || profile == null || !(actor instanceof NPC) || phase != ToaCombatPolicy.Phase.TUMEKEN) {
   return;
  }
  NPC npc = (NPC) actor;
  int animation = npc.getAnimation();
   if (profile.tumeken.contains(npc.getId()) && animation >= 0
    && profile.floorAnimations.contains(animation)) {
    registerFloorWarning(client.getTickCount(), "configured Tumeken animation " + animation);
  }
 }

 @Subscribe public synchronized void onGraphicChanged(GraphicChanged event) {
  Actor actor = event.getActor();
  actorSignal(actor, "GRAPHIC");
  if (!running || profile == null || actor == null) {
   return;
  }
  int graphic = actor.getGraphic();
  if (profile.floorGraphics.contains(graphic)) {
   registerFloorWarning(client.getTickCount(), "configured graphic " + graphic);
  } else if (actor == client.getLocalPlayer()
    && (profile.babaGraphics.contains(graphic) || profile.imprisonmentGraphics.contains(graphic))) {
   registerImmediateWarning(currentPosition(), client.getTickCount(), "Configured player hazard graphic " + graphic);
  }
 }

 @Subscribe public synchronized void onGraphicsObjectCreated(GraphicsObjectCreated event) {
  GraphicsObject graphic = event.getGraphicsObject();
  if (graphic == null) {
   return;
  }
  WorldPoint livePoint = WorldPoint.fromLocal(client, graphic.getLocation());
  WorldPoint point = instance(graphic.getLocation());
  if (livePoint == null || point == null || client.getLocalPlayer() == null
    || livePoint.distanceTo(client.getLocalPlayer().getWorldLocation()) > 32) {
   return;
  }
  if (recording && actions.size() < MAX_ACTIONS) {
   Map<String, Object> data = new LinkedHashMap<>();
   data.put("type", "GRAPHICS_OBJECT_CREATED");
   data.put("tick", client.getTickCount());
   data.put("cycle", client.getGameCycle());
   data.put("id", graphic.getId());
   data.put("position", new int[] {point.getX(), point.getY(), point.getPlane()});
   actions.add(data);
  }
  if (running) runtimeTrace.event("GRAPHICS_OBJECT", client.getTickCount(),
   "cycle", client.getGameCycle(), "id", graphic.getId(),
   "position", new int[] {point.getX(), point.getY(), point.getPlane()}, "phase", phase.name());
  if (!running || profile == null) {
   return;
  }
  if (profile.floorGraphics.contains(graphic.getId())) {
   registerFloorWarning(client.getTickCount(), "configured floor graphic " + graphic.getId());
  } else if (ToaSignals.spinningLight(graphic.getId())
    && point.distanceTo(currentPosition()) <= 2) {
   registerImmediateWarning(point, client.getTickCount(), "Spinning Warden light " + graphic.getId());
  } else if (profile.babaGraphics.contains(graphic.getId())
    || profile.imprisonmentGraphics.contains(graphic.getId())) {
   registerImmediateWarning(point, client.getTickCount(), "Configured ground hazard graphic " + graphic.getId());
  }
 }

 @Subscribe public synchronized void onNpcChanged(NpcChanged event) {
  NPC npc = event.getNpc();
  if (recording && actions.size() < MAX_ACTIONS) {
   Map<String, Object> data = new LinkedHashMap<>();
   data.put("type", "NPC_CHANGED");
   data.put("tick", client.getTickCount());
   data.put("id", npc == null ? -1 : npc.getId());
   data.put("index", npc == null ? -1 : npc.getIndex());
   actions.add(data);
  }
  if (running) runtimeTrace.event("NPC_CHANGED", client.getTickCount(),
   "id", npc == null ? -1 : npc.getId(), "name", cleanNpcName(npc),
   "index", npc == null ? -1 : npc.getIndex(),
   "animation", npc == null ? -1 : npc.getAnimation(),
   "healthRatio", npc == null ? -1 : npc.getHealthRatio(), "phase", phase.name());
 }

 private void actorSignal(Actor actor, String kind) {
  if ((!recording && !running) || actor == null
    || actor != client.getLocalPlayer() && !(actor instanceof NPC)) {
   return;
  }
  int ownerId = actor instanceof NPC ? ((NPC) actor).getId() : -1;
  int id = kind.equals("ANIMATION") ? actor.getAnimation() : actor.getGraphic();
  if (recording && actions.size() < MAX_ACTIONS) {
   Map<String, Object> data = new LinkedHashMap<>();
   data.put("type", kind);
   data.put("tick", client.getTickCount());
   data.put("cycle", client.getGameCycle());
   data.put("ownerNpcId", ownerId);
   data.put("id", id);
   actions.add(data);
  }
  if (running) runtimeTrace.event(kind, client.getTickCount(),
   "cycle", client.getGameCycle(), "ownerNpcId", ownerId,
   "ownerNpcName", actor instanceof NPC ? cleanNpcName((NPC) actor) : "local-player",
   "id", id, "phase", phase.name());
 }

 @Subscribe public synchronized void onGameObjectSpawned(GameObjectSpawned event) {
  sceneSignal("GAME_OBJECT_SPAWNED", event.getGameObject(), true);
 }
 @Subscribe public synchronized void onGameObjectChanged(GameObjectChanged event) {
  sceneSignal("GAME_OBJECT_CHANGED", event.getGameObject(), true);
 }
 @Subscribe public synchronized void onGameObjectDespawned(GameObjectDespawned event) {
  sceneSignal("GAME_OBJECT_DESPAWNED", event.getGameObject(), false);
 }
 @Subscribe public synchronized void onGroundObjectSpawned(GroundObjectSpawned event) {
  sceneSignal("GROUND_OBJECT_SPAWNED", event.getGroundObject(), true);
 }
 @Subscribe public synchronized void onGroundObjectChanged(GroundObjectChanged event) {
  sceneSignal("GROUND_OBJECT_CHANGED", event.getGroundObject(), true);
 }
 @Subscribe public synchronized void onGroundObjectDespawned(GroundObjectDespawned event) {
  sceneSignal("GROUND_OBJECT_DESPAWNED", event.getGroundObject(), false);
 }
 @Subscribe public synchronized void onWallObjectSpawned(WallObjectSpawned event) {
  sceneSignal("WALL_OBJECT_SPAWNED", event.getWallObject(), true);
 }
 @Subscribe public synchronized void onWallObjectChanged(WallObjectChanged event) {
  sceneSignal("WALL_OBJECT_CHANGED", event.getWallObject(), true);
 }
 @Subscribe public synchronized void onWallObjectDespawned(WallObjectDespawned event) {
  sceneSignal("WALL_OBJECT_DESPAWNED", event.getWallObject(), false);
 }
 @Subscribe public synchronized void onDecorativeObjectSpawned(DecorativeObjectSpawned event) {
  sceneSignal("DECORATIVE_OBJECT_SPAWNED", event.getDecorativeObject(), true);
 }
 @Subscribe public synchronized void onDecorativeObjectChanged(DecorativeObjectChanged event) {
  sceneSignal("DECORATIVE_OBJECT_CHANGED", event.getDecorativeObject(), true);
 }
 @Subscribe public synchronized void onDecorativeObjectDespawned(DecorativeObjectDespawned event) {
  sceneSignal("DECORATIVE_OBJECT_DESPAWNED", event.getDecorativeObject(), false);
 }

 private void sceneSignal(String type, TileObject object, boolean present) {
  if (object == null || client.getLocalPlayer() == null) {
   return;
  }
  WorldPoint point = object.getWorldLocation();
  if (point == null || point.distanceTo(client.getLocalPlayer().getWorldLocation()) > 32) {
   return;
  }
  Set<WorldPoint> points = liveSceneSignals.computeIfAbsent(object.getId(), ignored -> new LinkedHashSet<>());
  if (present) {
   if (liveSceneSignals.size() <= MAX_SCENE_SIGNALS) {
    points.add(point);
   }
  } else {
   points.remove(point);
   if (points.isEmpty()) {
    liveSceneSignals.remove(object.getId());
   }
  }
  if (recording && actions.size() < MAX_ACTIONS) {
   Map<String, Object> data = new LinkedHashMap<>();
   data.put("type", type);
   data.put("tick", client.getTickCount());
   data.put("id", object.getId());
   data.put("position", new int[] {point.getX(), point.getY(), point.getPlane()});
   actions.add(data);
  }
  if (running) runtimeTrace.event(type, client.getTickCount(), "id", object.getId(),
   "position", new int[] {point.getX(), point.getY(), point.getPlane()},
   "present", present, "phase", phase.name());
  if (running && profile != null && present && profile.floorObjects.contains(object.getId())) {
   registerFloorWarning(client.getTickCount(), "configured scene object " + object.getId());
  }
 }

 @Subscribe
 public synchronized void onMenuOptionClicked(MenuOptionClicked event) {
  if (!recording || client.getGameState() != GameState.LOGGED_IN || actions.size() >= MAX_ACTIONS) {
   return;
  }
  String type = event.getMenuAction().name();
  if (type.contains("PLAYER") || !(type.startsWith("NPC_") || type.startsWith("GAME_OBJECT_")
    || type.equals("WALK") || type.contains("WIDGET") || type.startsWith("CC_OP"))) {
   return;
  }
  Map<String, Object> action = new LinkedHashMap<>();
  action.put("tick", client.getTickCount());
  action.put("cycle", client.getGameCycle());
  action.put("type", type);
  action.put("option", event.getMenuOption());
  action.put("id", event.getId());
  action.put("param0", event.getParam0());
  action.put("param1", event.getParam1());
  action.put("itemId", event.getItemId());
  actions.add(action);
 }

 private void save() {
  final String snapshot;
  synchronized (this) {
   if (recording) {
    status = "Stop observing before saving your walkthrough.";
    return;
   }
   if (frames.isEmpty()) {
    status = "There are no ToA observations to save.";
    return;
   }
   if (saving) {
    return;
   }
   Map<String, Object> data = new LinkedHashMap<>();
   data.put("schema", "turbo-toa-walkthrough-v1");
   data.put("createdAt", Instant.now().toString());
   data.put("frames", frames);
   data.put("actions", actions);
   data.put("markers", markers);
   snapshot = new GsonBuilder().setPrettyPrinting().create().toJson(data);
  }
  JFileChooser chooser = new JFileChooser(dataRoot.resolve("walkthroughs").toFile());
  chooser.setSelectedFile(dataRoot.resolve(
   "walkthroughs/toa-walkthrough-" + System.currentTimeMillis() + ".json").toFile());
  if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) {
   return;
  }
  Path target = chooser.getSelectedFile().toPath().toAbsolutePath();
  if (Files.exists(target) && JOptionPane.showConfirmDialog(
    window, "Replace this walkthrough file?", "Save walkthrough", JOptionPane.YES_NO_OPTION)
    != JOptionPane.YES_OPTION) {
   return;
  }
  saving = true;
  CompletableFuture.runAsync(() -> {
   Path temporary = null;
   try {
    Files.createDirectories(target.getParent());
    temporary = Files.createTempFile(target.getParent(), "toa-walkthrough-", ".tmp");
    Files.writeString(temporary, snapshot, StandardCharsets.UTF_8);
    try {
     Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException error) {
     Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
    status = "Saved ToA walkthrough: " + target.getFileName();
   } catch (Exception error) {
    status = "Save failed: " + error.getClass().getSimpleName()
     + ". Observations remain in memory.";
   } finally {
    if (temporary != null) {
     try {
      Files.deleteIfExists(temporary);
     } catch (Exception ignored) {
      // The application will clean interrupted temporary files on a later maintenance pass.
     }
    }
    saving = false;
   }
  });
 }

 private static String message(Throwable error) {
  String value = error == null ? null : error.getMessage();
  return value == null || value.trim().isEmpty()
   ? (error == null ? "Unknown error" : error.getClass().getSimpleName()) : value;
 }

 @Override
 public void close() {
  setup.close();
  closed = true;
  stop("Companion closed");
  bus.unregister(this);
  synchronized (this) {
   frames.clear();
   actions.clear();
   markers.clear();
   seenProjectiles.clear();
   liveSceneSignals.clear();
  }
  SwingUtilities.invokeLater(() -> {
   refresh.stop();
   window.dispose();
  });
 }
}
