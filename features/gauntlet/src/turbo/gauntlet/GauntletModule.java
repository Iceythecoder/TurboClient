package turbo.gauntlet;

import com.google.gson.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.*;
import owl.nh.RaidInputBridge;

/** Independent Gauntlet observer and guarded controller shell. */
public final class GauntletModule implements AutoCloseable {
 private static final int MAX_TICKS=18000,MAX_EVENTS=24000;
 private final Client client;private final ClientThread thread;private final EventBus bus;private final Path root;
 private final Runnable stopOther;private final RaidInputBridge input;private final JDialog window;private final JTextArea live=new JTextArea();
 private final JComboBox<GauntletState.Mode> mode=new JComboBox<>(GauntletState.Mode.values());
 private final GauntletSignals signals=new GauntletSignals();private final GauntletState machine=new GauntletState();private final GauntletPlanner planner=new GauntletPlanner();
 private final GauntletActionContract actions=new GauntletActionContract();private final GauntletResourceLedger resources=new GauntletResourceLedger();private final HunllefTracker hunllefTracker=new HunllefTracker();
 private final Set<Projectile> countedBossProjectiles=Collections.newSetFromMap(new IdentityHashMap<Projectile,Boolean>());
 private final Set<Integer> seenNpcs=new TreeSet<>(),seenProjectiles=new TreeSet<>(),seenGraphics=new TreeSet<>(),seenObjects=new TreeSet<>(),seenItems=new TreeSet<>();
 private final List<Map<String,Object>> frames=new ArrayList<>(),events=new ArrayList<>();
 private volatile boolean observing,requestedControl,closed;private volatile String status="Ready. Start with Observation & Diagnose to map Orion's Gauntlet signals.";private int lastTick=-1;
 private String previousRoom;
 private final javax.swing.Timer refresh;

 public GauntletModule(Client client,ClientThread thread,EventBus bus,Frame owner,Path root,Runnable stopOther,RaidInputBridge input){
  this.client=Objects.requireNonNull(client);this.thread=Objects.requireNonNull(thread);this.bus=Objects.requireNonNull(bus);this.root=root.toAbsolutePath().normalize();this.stopOther=stopOther;this.input=input;
  window=new JDialog((Frame)null,"Turbo Gauntlet",false);window.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
  JPanel content=new JPanel(new BorderLayout(9,9));content.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
  JPanel header=new JPanel(new BorderLayout());JLabel title=new JLabel("Turbo Gauntlet — reactive runner");title.setFont(title.getFont().deriveFont(Font.BOLD,17f));header.add(title,BorderLayout.WEST);header.add(mode,BorderLayout.EAST);content.add(header,BorderLayout.NORTH);
  live.setEditable(false);live.setLineWrap(true);live.setWrapStyleWord(true);live.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));content.add(new JScrollPane(live),BorderLayout.CENTER);
  JPanel controls=new JPanel(new GridLayout(0,2,7,7));JButton observe=new JButton("Observation & Diagnose"),run=new JButton("Run guarded Gauntlet"),stop=new JButton("Stop / F9"),signalsButton=new JButton("Signal registry"),save=new JButton("Save diagnostic trace…"),clear=new JButton("Clear unsaved trace");
  for(JButton b:new JButton[]{observe,run,stop,signalsButton,save,clear})controls.add(b);content.add(controls,BorderLayout.SOUTH);
  observe.addActionListener(e->thread.invoke(()->start(false)));run.addActionListener(e->thread.invoke(()->start(true)));stop.addActionListener(e->thread.invoke(()->stop("User stop")));signalsButton.addActionListener(e->showSignals());save.addActionListener(e->saveTrace());clear.addActionListener(e->thread.invoke(this::clear));
  window.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){stop("Gauntlet window hidden");}});window.setContentPane(content);window.setSize(680,520);window.setMinimumSize(new Dimension(580,420));window.setLocationRelativeTo(owner);
  Path saved=this.root.resolve("gauntlet-signals.json");if(Files.isRegularFile(saved))try{signals.load(saved);status="Saved signal registry loaded. Control ready: "+signals.controlReady();}catch(Exception e){status="Saved signal registry is invalid; observation only: "+e.getMessage();}
  bus.register(this);refresh=new javax.swing.Timer(200,e->refresh());refresh.start();
 }
 public Window window(){return window;}
 private synchronized void start(boolean control){
  if(closed)throw new IllegalStateException("Gauntlet module is closed.");if(observing)throw new IllegalStateException("A Gauntlet session is already active.");if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null){status="Log into Orion before starting.";return;}
  stopOther.run();requestedControl=control;lastTick=-1;previousRoom=null;actions.reset();resources.reset();hunllefTracker.reset();countedBossProjectiles.clear();machine.reset((GauntletState.Mode)mode.getSelectedItem());observing=true;
  if(control&&!signals.controlReady())status="CONTROL BLOCKED. Observation continues; missing verified runtime signals: "+String.join(", ",signals.missing());
  else if(control)status="Control profile is verified, but this build still requires target/action acknowledgement before sending each action.";
  else status="Observation active. Play manually; candidate IDs and state evidence stay in memory until you save.";
 }
 public synchronized void stop(String reason){if(observing){observing=false;requestedControl=false;machine.stop();if(input!=null)input.cancelPendingControl();status=reason+". Unsaved diagnostic data remains in memory.";}}
 private synchronized void clear(){if(observing){status="Stop before clearing.";return;}frames.clear();events.clear();seenNpcs.clear();seenProjectiles.clear();seenGraphics.clear();seenObjects.clear();seenItems.clear();status="Unsaved Gauntlet diagnostic data cleared.";}

 @Subscribe public synchronized void onGameTick(GameTick ignored){
  if(!observing)return;if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null){stop("Logout/loading observed");return;}int tick=client.getTickCount();if(tick==lastTick)return;lastTick=tick;if(frames.size()>=MAX_TICKS){stop("Trace limit reached");return;}
  Player self=client.getLocalPlayer();WorldPoint pos=self.getWorldLocation();scanScene();for(NPC npc:client.getNpcs())if(npc!=null&&npc.getWorldLocation().distanceTo(pos)<=40)seenNpcs.add(npc.getId());
  String room=(pos.getX()>>3)+":"+(pos.getY()>>3)+":"+pos.getPlane();planner.observe(room).visited=true;if(previousRoom!=null&&!previousRoom.equals(room))planner.connect(previousRoom,room);previousRoom=room;
  ItemContainer inv=client.getItemContainer(InventoryID.INVENTORY);if(inv!=null)for(Item item:inv.getItems())if(item!=null&&item.getId()>0)seenItems.add(item.getId());
  boolean instance=matchesAny("instance",seenObjects)||matchesAny("hunlleffNpc",seenNpcs)||matchesAny("startRoom",seenObjects)||matchesAny("bossRoom",seenObjects)||matchesAny("rewardRoom",seenObjects);
  boolean corrupted=matchesAny("corruptedInstance",seenObjects)||matchesAny("corruptedInstance",seenNpcs);boolean start=matchesAny("startRoom",seenObjects),boss=matchesAny("bossRoom",seenObjects),reward=matchesAny("rewardRoom",seenObjects),hunllef=matchesAny("hunlleffNpc",seenNpcs);
  int hp=client.getBoostedSkillLevel(Skill.HITPOINTS),prayer=client.getBoostedSkillLevel(Skill.PRAYER);int food=count("foodItem",inv),pots=count("potionItem",inv),weapons=count("weaponItems",inv),armor=count("armorItems",inv);resources.observe("food",food,"inventory",1);resources.observe("potions",pots,"inventory",1);resources.observe("weapons",weapons,"inventory",1);resources.observe("armor",armor,"inventory",1);
  boolean floor=matchesAny("hunlleffFloorWarning",seenGraphics)||matchesAny("hunlleffFloorActive",seenGraphics),tornado=matchesAny("tornadoGraphic",seenGraphics);
  GauntletState.Snapshot snapshot=new GauntletState.Snapshot(tick,hp,prayer,food,pots,weapons>0?3:0,armor,0,true,instance,corrupted,start,!boss&&!reward,boss,reward,hunllef,false,matchesAny("rewardChest",seenObjects),matchesAny("exitObject",seenObjects),floor,tornado);
  GauntletState.Decision decision=machine.step(snapshot,false); // execution remains gated until the Orion selector adapter is verified.
  Map<String,Object> f=new LinkedHashMap<>();f.put("tick",tick);f.put("cycle",client.getGameCycle());f.put("position",new int[]{pos.getX(),pos.getY(),pos.getPlane()});f.put("hp",hp);f.put("prayer",prayer);f.put("phase",machine.phase().name());f.put("mode",machine.mode().name());f.put("decision",decision.kind+":"+decision.reason);f.put("bossAttackStyle",hunllefTracker.currentStyle().name());f.put("bossAttacksInStyle",hunllefTracker.attacksInStyle());f.put("expectedProtection",hunllefTracker.requiredProtection());f.put("expectedNextBossStyle",hunllefTracker.expectedNext().name());f.put("actionGeneration",actions.generation());f.put("pendingActions",actions.pending().size());f.put("npcs",new ArrayList<>(seenNpcs));f.put("objects",new ArrayList<>(seenObjects));f.put("projectiles",new ArrayList<>(seenProjectiles));f.put("graphics",new ArrayList<>(seenGraphics));f.put("items",new ArrayList<>(seenItems));frames.add(f);
  status=(requestedControl?"GUARDED RUN":"OBSERVATION")+" | "+machine.mode()+" | "+machine.phase()+"\n"+decision.kind+": "+decision.reason;
 }
 @Subscribe public synchronized void onNpcSpawned(NpcSpawned e){if(observing&&e.getNpc()!=null){seenNpcs.add(e.getNpc().getId());record("NPC_SPAWN",e.getNpc().getId());}}
 @Subscribe public synchronized void onProjectileMoved(ProjectileMoved e){if(observing&&e.getProjectile()!=null){Projectile p=e.getProjectile();seenProjectiles.add(p.getId());record("PROJECTILE",p.getId());if(countedBossProjectiles.add(p)){HunllefTracker.Style style=signals.matches("hunlleffMagicProjectile",p.getId())?HunllefTracker.Style.MAGIC:signals.matches("hunlleffRangedProjectile",p.getId())?HunllefTracker.Style.RANGED:HunllefTracker.Style.UNKNOWN;if(style!=HunllefTracker.Style.UNKNOWN)hunllefTracker.observeBossAttack(client.getTickCount(),HunllefTracker.EventKind.PROJECTILE_ATTACK,style);}}}
 @Subscribe public synchronized void onGraphicsObjectCreated(GraphicsObjectCreated e){if(observing&&e.getGraphicsObject()!=null){seenGraphics.add(e.getGraphicsObject().getId());record("GRAPHIC",e.getGraphicsObject().getId());}}
 @Subscribe public synchronized void onGameObjectSpawned(GameObjectSpawned e){object("GAME_OBJECT",e.getGameObject());}
 @Subscribe public synchronized void onGroundObjectSpawned(GroundObjectSpawned e){object("GROUND_OBJECT",e.getGroundObject());}
 @Subscribe public synchronized void onWallObjectSpawned(WallObjectSpawned e){object("WALL_OBJECT",e.getWallObject());}
 @Subscribe public synchronized void onDecorativeObjectSpawned(DecorativeObjectSpawned e){object("DECORATIVE_OBJECT",e.getDecorativeObject());}
 @Subscribe public synchronized void onItemSpawned(ItemSpawned e){if(observing&&e.getItem()!=null){seenItems.add(e.getItem().getId());record("GROUND_ITEM",e.getItem().getId());}}
 @Subscribe public synchronized void onMenuOptionClicked(MenuOptionClicked e){if(!observing)return;Map<String,Object> m=event("MENU");m.put("option",e.getMenuOption());m.put("action",e.getMenuAction().name());m.put("id",e.getId());events.add(m);}
 private void object(String type,TileObject object){if(observing&&object!=null){seenObjects.add(object.getId());record(type,object.getId());}}
 private void record(String type,int id){if(events.size()>=MAX_EVENTS)return;Map<String,Object> e=event(type);e.put("id",id);events.add(e);}
 private Map<String,Object> event(String type){Map<String,Object> e=new LinkedHashMap<>();e.put("type",type);e.put("tick",client.getTickCount());e.put("cycle",client.getGameCycle());return e;}
 private boolean matchesAny(String key,Set<Integer> ids){for(int id:ids)if(signals.matches(key,id))return true;return false;}
 private int count(String key,ItemContainer bag){if(bag==null)return 0;int n=0;for(Item i:bag.getItems())if(i!=null&&signals.matches(key,i.getId()))n+=Math.max(1,i.getQuantity());return n;}
 private void scanScene(){try{Scene scene=client.getScene();if(scene==null||scene.getTiles()==null)return;Tile[][][] tiles=scene.getTiles();int plane=client.getPlane();if(plane<0||plane>=tiles.length||tiles[plane]==null)return;for(Tile[] column:tiles[plane]){if(column==null)continue;for(Tile tile:column){if(tile==null)continue;GameObject[] games=tile.getGameObjects();if(games!=null)for(GameObject o:games)if(o!=null)seenObjects.add(o.getId());if(tile.getGroundObject()!=null)seenObjects.add(tile.getGroundObject().getId());if(tile.getWallObject()!=null)seenObjects.add(tile.getWallObject().getId());if(tile.getDecorativeObject()!=null)seenObjects.add(tile.getDecorativeObject().getId());}}}catch(RuntimeException ignored){}}
 private void refresh(){StringBuilder b=new StringBuilder(status);b.append("\n\nControl ready: ").append(signals.controlReady()).append("\nMissing: ").append(String.join(", ",signals.missing()));b.append("\nHunllef: ").append(hunllefTracker.currentStyle()).append(" ").append(hunllefTracker.attacksInStyle()).append("/4 | protect ").append(hunllefTracker.requiredProtection()).append(" | next ").append(hunllefTracker.expectedNext());b.append("\nAction generation: ").append(actions.generation()).append(" | pending acknowledgements: ").append(actions.pending().size());b.append("\n\nObserved candidate IDs (not automatically trusted)\nNPC ").append(seenNpcs).append("\nObjects ").append(seenObjects).append("\nProjectiles ").append(seenProjectiles).append("\nGraphics ").append(seenGraphics).append("\nItems ").append(seenItems);b.append("\n\nTicks: ").append(frames.size()).append(" | events: ").append(events.size()).append("\nNo player names, chat, credentials, or external uploads are recorded.");live.setText(b.toString());}
 private void showSignals(){JTextArea editor=new JTextArea(signals.json(),28,70);int result=JOptionPane.showConfirmDialog(window,new JScrollPane(editor),"Gauntlet signal registry — only mark IDs VERIFIED after Orion observation",JOptionPane.OK_CANCEL_OPTION);if(result!=JOptionPane.OK_OPTION)return;Path file=root.resolve("gauntlet-signals.json");try{Files.createDirectories(root);Files.writeString(file,editor.getText(),StandardCharsets.UTF_8);signals.load(file);status="Signal registry loaded. Control ready: "+signals.controlReady();}catch(Exception e){status="Signal registry rejected: "+e.getMessage();}}
 private void saveTrace(){if(observing){status="Stop the session before saving.";return;}if(frames.isEmpty()){status="There is no diagnostic trace to save.";return;}JFileChooser chooser=new JFileChooser(root.resolve("diagnostics").toFile());chooser.setSelectedFile(root.resolve("diagnostics/gauntlet-"+System.currentTimeMillis()+".json").toFile());if(chooser.showSaveDialog(window)!=JFileChooser.APPROVE_OPTION)return;Path target=chooser.getSelectedFile().toPath();Map<String,Object> data=new LinkedHashMap<>();data.put("schema","turbo-gauntlet-diagnostic-v1");data.put("createdAt",Instant.now().toString());data.put("frames",new ArrayList<>(frames));data.put("events",new ArrayList<>(events));data.put("signals",new JsonParser().parse(signals.json()));try{Files.createDirectories(target.toAbsolutePath().getParent());Files.writeString(target,new GsonBuilder().setPrettyPrinting().create().toJson(data),StandardCharsets.UTF_8);status="Diagnostic trace saved: "+target.getFileName();}catch(Exception e){status="Save failed: "+e.getMessage();}}
 @Override public void close(){closed=true;stop("Gauntlet module closed");bus.unregister(this);refresh.stop();synchronized(this){frames.clear();events.clear();}window.dispose();}
}
