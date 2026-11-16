package turbo.raid;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;

/** Shared setup source, separately compiled into each raid module. No static raid state. */
public final class RaidSetup implements AutoCloseable {
 private static final int MAX_BYTES = 4 * 1024 * 1024;
 private static final List<String> STYLES = Arrays.asList("MAGIC", "MELEE", "RANGED", "SPEC");
 private static final List<String> SUPPLIES = Arrays.asList("overload", "enhance", "aid", "revitalisation");
 private static final List<String> PRE_POT = Arrays.asList(
  "overload", "enhance", "aid", "aid", "aid", "aid", "revitalisation");

 private final String raid;
 private final Path root;
 private final Client client;
 private final ClientThread thread;
 private final Supplier<String> gear;
 private final Supplier<String> profileDefaults;
 private final Consumer<String> status;
 private JDialog dialog;
 private JTextArea editor;
 private JsonObject data;
 private volatile boolean saving;

 /** Compatibility constructor used by the observation-only TOB module. */
 public RaidSetup(
  String raid,
  Path root,
  Client client,
  ClientThread thread,
  Supplier<String> gear,
  Consumer<String> status) {
  this(raid, root, client, thread, gear, () -> "{}", status);
 }

 /**
  * Creates a raid setup. For TOA, profileDefaults is a sanitized, read-only copy of the
  * Chambers resource baseline; this class copies only gear, supplies and offensive prayers into
  * the independent TOA profile. It never copies Olm mechanics or learning data.
  */
 public RaidSetup(
  String raid,
  Path root,
  Client client,
  ClientThread thread,
  Supplier<String> gear,
  Supplier<String> profileDefaults,
  Consumer<String> status) {
  if (!raid.equals("toa") && !raid.equals("tob")) {
   throw new IllegalArgumentException("Unknown raid");
  }
  this.raid = raid;
  this.root = root;
  this.client = client;
  this.thread = thread;
  this.gear = gear;
  this.profileDefaults = profileDefaults == null ? () -> "{}" : profileDefaults;
  this.status = status;
  Path saved = root.resolve("instance-data.json");
  if (Files.isRegularFile(saved)) {
   try {
    if (Files.size(saved) > MAX_BYTES) throw new IllegalArgumentException("Saved instance data exceeds 4 MB");
    JsonObject loaded = new JsonParser().parse(Files.readString(saved)).getAsJsonObject();
    this.data = raid.equals("toa") ? prepareToa(loaded, false) : validate(raid, loaded.toString());
   } catch (Exception error) {
    this.data = null;
    this.status.accept("Saved " + raid.toUpperCase(Locale.ROOT)
     + " instance data could not be preloaded: " + error.getMessage());
   }
  }
 }

 public static JsonObject validate(String raid, String text) {
  if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
   throw new IllegalArgumentException("Instance data exceeds 4 MB");
  }
  JsonObject data;
  try {
   data = new JsonParser().parse(text).getAsJsonObject();
  } catch (RuntimeException error) {
   throw new IllegalArgumentException("Instance data is not valid JSON", error);
  }
  String expectedSchema = "turbo-" + raid + "-setup-v1";
  if (!data.has("schema") || !expectedSchema.equals(data.get("schema").getAsString())) {
   throw new IllegalArgumentException(
    "Choose " + raid.toUpperCase(Locale.ROOT) + " instance data; other raid profiles are kept separate.");
  }
  if (data.has("raid") && !raid.equals(data.get("raid").getAsString())) {
   throw new IllegalArgumentException("Instance data belongs to a different raid");
  }
  data.addProperty("raid", raid);

  boolean combat = data.has("combatImplemented") && data.get("combatImplemented").getAsBoolean();
  if (combat && !raid.equals("toa")) {
   throw new IllegalArgumentException("Combat logic is not implemented for this raid yet.");
  }
  validateGear(raid, data);
  if (combat) {
   validateToaCombat(data);
  }
  data.addProperty("combatImplemented", combat && raid.equals("toa"));
  return data;
 }

 private static void validateGear(String raid, JsonObject data) {
  if (!data.has("observedGear") || !data.get("observedGear").isJsonObject()) {
   throw new IllegalArgumentException("Instance data needs observedGear");
  }
  JsonObject gear = data.getAsJsonObject("observedGear");
  Set<String> allowed = new LinkedHashSet<>(STYLES);
  if ("toa".equals(raid)) allowed.add("CORE");
  if (gear.size() > allowed.size()) {
   throw new IllegalArgumentException("Too many gear styles");
  }
  for (Map.Entry<String, JsonElement> entry : gear.entrySet()) {
   if (!allowed.contains(entry.getKey())
    || !entry.getValue().isJsonArray()
    || entry.getValue().getAsJsonArray().size() > 14) {
    throw new IllegalArgumentException("Invalid gear style or size");
   }
   for (JsonElement id : entry.getValue().getAsJsonArray()) {
    if (id.getAsInt() <= 0) {
     throw new IllegalArgumentException("Gear item IDs must be positive");
    }
   }
  }
 }

 private static void validateToaCombat(JsonObject data) {
  JsonObject observedGear = data.getAsJsonObject("observedGear");
  for (String style : Arrays.asList("MAGIC", "MELEE", "RANGED")) {
   if (!observedGear.has(style)
    || !observedGear.get(style).isJsonArray()
    || observedGear.getAsJsonArray(style).size() == 0) {
    throw new IllegalArgumentException("Combat-ready TOA data needs the " + style + " gear preset");
   }
  }
  if (!data.has("eatAtHp") || data.get("eatAtHp").getAsInt() != 80) {
   throw new IllegalArgumentException("TOA must use the Chambers 80 HP guard");
  }
  if (!data.has("restorePrayerAt") || data.get("restorePrayerAt").getAsInt() != 40) {
   throw new IllegalArgumentException("TOA must use the Chambers 40 Prayer guard");
  }
  if (!data.has("potionCooldownTicks") || data.get("potionCooldownTicks").getAsInt() != 3) {
   throw new IllegalArgumentException("TOA must use the confirmed three-tick potion cooldown");
  }
  if (!data.has("prePot") || !data.get("prePot").isJsonArray()) {
   throw new IllegalArgumentException("TOA data needs the Chambers pre-pot sequence");
  }
  JsonArray prePot = data.getAsJsonArray("prePot");
  if (prePot.size() != PRE_POT.size()) {
   throw new IllegalArgumentException("TOA pre-pot sequence has the wrong length");
  }
  for (int i = 0; i < PRE_POT.size(); i++) {
   if (!PRE_POT.get(i).equals(prePot.get(i).getAsString())) {
    throw new IllegalArgumentException("TOA pre-pot sequence must match Chambers exactly");
   }
  }
  if (!data.has("supplies") || !data.get("supplies").isJsonObject()) {
   throw new IllegalArgumentException("TOA data needs copied Chambers supply families");
  }
  JsonObject supplies = data.getAsJsonObject("supplies");
  for (String name : SUPPLIES) {
   if (!supplies.has(name)
    || !supplies.get(name).isJsonArray()
    || supplies.getAsJsonArray(name).size() == 0
    || supplies.getAsJsonArray(name).size() > 16) {
    throw new IllegalArgumentException("Missing verified potion IDs: " + name);
   }
   for (JsonElement id : supplies.getAsJsonArray(name)) {
    if (id.getAsInt() <= 0) {
     throw new IllegalArgumentException("Potion item IDs must be positive");
    }
   }
  }
  if (!data.has("weapons") || !data.get("weapons").isJsonObject()) {
   throw new IllegalArgumentException("TOA data needs the observed raid weapon and equipment mappings");
  }
  JsonObject weapons = data.getAsJsonObject("weapons");
  int scythe = positiveWeapon(weapons, "meleeWeaponId", "positively charged Scythe of Vitur");
  int rapier = positiveWeapon(weapons, "rapierId", "Ghrazi rapier");
  int defender = positiveWeapon(weapons, "avernicDefenderId", "Avernic defender");
  int maul = positiveWeapon(weapons, "elderMaulId", "Elder maul");
  int cards = positiveWeapon(weapons, "rangedWeaponId", "Holy Cards");
  int boots = positiveWeapon(weapons, "bootsId", "Avernic Treads");
  int blessing = positiveWeapon(weapons, "blessingId", "blessing");
  if (!containsId(observedGear.getAsJsonArray("MELEE"), scythe)) {
   throw new IllegalArgumentException("TOA MELEE must contain the positively charged Scythe of Vitur");
  }
  if (!observedGear.has("CORE") || !observedGear.get("CORE").isJsonArray()
   || !containsId(observedGear.getAsJsonArray("CORE"), rapier)
   || !containsId(observedGear.getAsJsonArray("CORE"), defender)) {
   throw new IllegalArgumentException("TOA CORE must contain the Ghrazi rapier and Avernic defender");
  }
  if (!observedGear.has("SPEC")
   || !observedGear.get("SPEC").isJsonArray()
   || !containsId(observedGear.getAsJsonArray("SPEC"), maul)) {
   throw new IllegalArgumentException("TOA SPEC must contain the observed Elder maul");
  }
  if (!containsId(observedGear.getAsJsonArray("RANGED"), cards)) {
   throw new IllegalArgumentException("TOA RANGED must contain the observed Holy Cards");
  }
  for (String style : STYLES) {
   if (!containsId(observedGear.getAsJsonArray(style), boots) || !containsId(observedGear.getAsJsonArray(style), blessing)) {
    throw new IllegalArgumentException("Every TOA setup must contain Avernic Treads and the observed blessing");
   }
  }
 }

 private static int positiveWeapon(JsonObject weapons, String key, String label) {
  if (!weapons.has(key) || weapons.get(key).isJsonNull() || weapons.get(key).getAsInt() <= 0) {
   throw new IllegalArgumentException(label + " was not observed in the current inventory or equipment");
  }
  return weapons.get(key).getAsInt();
 }

 private static boolean containsId(JsonArray items, int id) {
  for (JsonElement item : items) {
   if (item.getAsInt() == id) {
    return true;
   }
  }
  return false;
 }

 public static void write(Path target, String raid, String text) throws java.io.IOException {
  String json = new GsonBuilder().setPrettyPrinting().create().toJson(validate(raid, text));
  Path destination = target.toAbsolutePath();
  Files.createDirectories(destination.getParent());
  Path temp = Files.createTempFile(destination.getParent(), "raid-setup-", ".tmp");
  try {
   Files.writeString(temp, json, StandardCharsets.UTF_8);
   try {
    Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
   } catch (AtomicMoveNotSupportedException error) {
    Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
   }
  } finally {
   Files.deleteIfExists(temp);
  }
 }

 private String schema() {
  return "turbo-" + raid + "-setup-v1";
 }

 public synchronized JsonObject snapshot() {
  if (data == null) return null;
  // A saved TOA profile may predate the current Scythe/Maul/Holy-Cards setup.
  // Run is invoked on the client thread, so refresh the live weapon identities
  // and rebuild the independent presets instead of trusting a stale false flag.
  if (raid.equals("toa")) data = prepareToa(data, true);
  return data.deepCopy();
 }

 public Window window(Frame owner) {
  if (dialog != null && dialog.isDisplayable()) {
   return dialog;
  }
  dialog = new JDialog(
   (Frame) null, raid.equals("toa") ? "AI TOA Fighter - settings" : "TOB AI Fighter - settings", false);
  dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
  JPanel panel = new JPanel(new BorderLayout(8, 8));
  panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
  JPanel buttons = new JPanel(new GridLayout(1, 3, 8, 0));
  JButton load = new JButton("Load instance data");
  JButton save = new JButton("Save instance data");
  JButton restore = new JButton("Load pre-saved data");
  buttons.add(load);
  buttons.add(save);
  buttons.add(restore);

  String detail = raid.equals("toa")
   ? "Load instance data copies the Chambers resource baseline into an independent TOA profile.\n"
    + "Save instance data applies your edits and saves TOA only.\n"
    + "Load pre-saved data restores a validated TOA profile for Run prototype."
   : "Load instance data captures room, NPC and gear candidates.\n"
    + "Save instance data applies your edits and saves this raid only.\n"
    + "Load pre-saved data restores a saved setup. Combat logic remains pending.";
  JTextArea guide = new JTextArea(detail);
  guide.setLineWrap(true);
  guide.setWrapStyleWord(true);
  guide.setEditable(false);
  guide.setOpaque(false);
  guide.setRows(4);
  JPanel top = new JPanel(new BorderLayout(8, 8));
  top.add(buttons, BorderLayout.NORTH);
  top.add(guide, BorderLayout.CENTER);
  panel.add(top, BorderLayout.NORTH);
  editor = new JTextArea(
   data == null ? "Load instance data to populate this raid's setup."
    : new GsonBuilder().setPrettyPrinting().create().toJson(data),
   20,
   75);
  panel.add(new JScrollPane(editor), BorderLayout.CENTER);
  JLabel message = new JLabel("Setup controls observe and save data; they never send combat inputs.");
  panel.add(message, BorderLayout.SOUTH);

  load.addActionListener(event -> thread.invoke(() -> {
   try {
    JsonObject candidate = loadCandidate();
    synchronized (this) {
     data = candidate;
    }
    show(candidate);
    if (raid.equals("toa")) {
     if (candidate.get("combatImplemented").getAsBoolean()) {
      status.accept("Combat-ready TOA instance data loaded with charged Scythe, Elder maul, Holy Cards, Avernic Treads and blessing.");
      SwingUtilities.invokeLater(() -> message.setText(
       "TOA profile loaded with the Chambers pre-pot, 80 HP guard and 40 Prayer guard."));
     } else {
      String missing = missingWeaponStatus(candidate);
      status.accept("TOA instance data loaded, but Run prototype is blocked: " + missing);
      SwingUtilities.invokeLater(() -> message.setText("Run prototype blocked: " + missing));
     }
    } else {
     status.accept("Instance data loaded for TOB. Combat logic pending.");
     SwingUtilities.invokeLater(() -> message.setText(
      "Room and available gear loaded. Verify the style presets for this raid."));
    }
   } catch (Exception error) {
    failed(error, message);
   }
  }));

  save.addActionListener(event -> {
   if (saving) {
    return;
   }
   final String text = editor.getText();
   try {
    JsonObject next = validate(raid, text);
    synchronized (this) {
     data = next;
    }
    saving = true;
    CompletableFuture.runAsync(() -> {
     try {
      write(root.resolve("instance-data.json"), raid, text);
      status.accept("Saved " + raid.toUpperCase(Locale.ROOT) + " instance data.");
      SwingUtilities.invokeLater(() -> message.setText(
       "Saved instance-data.json in this raid's own data folder."));
     } catch (Exception error) {
      failed(error, message);
     } finally {
      saving = false;
     }
    });
   } catch (Exception error) {
    failed(error, message);
   }
  });

  restore.addActionListener(event -> {
   JFileChooser chooser = new JFileChooser(root.toFile());
   chooser.setDialogTitle("Load " + raid.toUpperCase(Locale.ROOT) + " pre-saved data");
   chooser.setSelectedFile(root.resolve("instance-data.json").toFile());
   if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) {
    return;
   }
   Path path = chooser.getSelectedFile().toPath();
   CompletableFuture.runAsync(() -> {
    try {
     if (Files.size(path) > MAX_BYTES) {
      throw new IllegalArgumentException("Instance data exceeds 4 MB");
     }
     JsonObject loaded = new JsonParser().parse(Files.readString(path)).getAsJsonObject();
      JsonObject next = raid.equals("toa") ? prepareToa(loaded, true) : validate(raid, loaded.toString());
     synchronized (this) {
      data = next;
     }
     show(next);
      status.accept("Loaded pre-saved " + raid.toUpperCase(Locale.ROOT)
       + " instance data and refreshed the current live weapon mappings.");
     SwingUtilities.invokeLater(() -> message.setText(
      "Pre-saved instance data applied to this raid only."));
    } catch (Exception error) {
     failed(error, message);
    }
   });
  });

  dialog.setContentPane(panel);
  dialog.pack();
  dialog.setLocationRelativeTo(owner);
  return dialog;
 }

 private void show(JsonObject value) {
  String text = new GsonBuilder().setPrettyPrinting().create().toJson(value);
  SwingUtilities.invokeLater(() -> {
   if (editor != null) {
    editor.setText(text);
    editor.setCaretPosition(0);
   }
  });
 }

 private void failed(Exception error, JLabel label) {
  String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  status.accept("Instance setup: " + message);
  SwingUtilities.invokeLater(() -> {
   label.setText(message);
   JOptionPane.showMessageDialog(dialog, message, "Instance data", JOptionPane.INFORMATION_MESSAGE);
  });
 }

 private JsonObject loadCandidate() throws Exception {
  Path candidate = root.resolve("observed-candidate.json");
  if (Files.isRegularFile(candidate)) {
   if (Files.size(candidate) > MAX_BYTES) {
    throw new IllegalArgumentException("Candidates exceed 4 MB");
   }
   JsonObject observed = new JsonParser().parse(Files.readString(candidate)).getAsJsonObject();
   return raid.equals("toa") ? prepareToa(observed, true) : validate(raid, observed.toString());
  }

  Player self = client.getLocalPlayer();
  if (client.getGameState() != GameState.LOGGED_IN || self == null || self.getLocalLocation() == null) {
   throw new IllegalStateException(
    "Log into Orion in this raid's lobby or room before loading instance data.");
  }
  JsonObject result = new JsonObject();
  result.addProperty("schema", schema());
  result.addProperty("raid", raid);
  result.addProperty("observedAt", Instant.now().toString());
  result.addProperty("combatImplemented", false);
  result.add("observedGear", parseObject(gear.get(), "Observed gear is not valid JSON"));
  result.add("currentEquipment", items(client.getItemContainer(InventoryID.EQUIPMENT)));
  result.add("inventory", items(client.getItemContainer(InventoryID.INVENTORY)));
  WorldPoint position = WorldPoint.fromLocalInstance(client, self.getLocalLocation());
  if (position == null) {
   throw new IllegalStateException("Wait for the room to finish loading.");
  }
  JsonArray room = new JsonArray();
  room.add(position.getX());
  room.add(position.getY());
  room.add(position.getPlane());
  result.add("roomPosition", room);
  JsonArray npcs = new JsonArray();
  for (NPC npc : client.getNpcs()) {
   if (npc.getWorldLocation().distanceTo(self.getWorldLocation()) <= 32 && npcs.size() < 128) {
    npcs.add(npc.getId());
   }
  }
  result.add("npcCandidates", npcs);
  result.add("mechanics", new JsonObject());
  return raid.equals("toa") ? prepareToa(result, true) : validate(raid, result.toString());
 }

 /** Builds an independent TOA profile without carrying Olm mechanics or learning data across. */
 private JsonObject prepareToa(JsonObject captured, boolean refreshGear) {
  JsonObject result = captured.deepCopy();
  result.addProperty("schema", schema());
  result.addProperty("raid", raid);

  JsonObject defaults = parseObject(profileDefaults.get(), "Chambers baseline is not valid JSON");
  JsonObject resources = defaults.has("resources") && defaults.get("resources").isJsonObject()
   ? defaults.getAsJsonObject("resources") : defaults;
  copyIfMissingOrEmpty(resources, result, "supplies");
  copyIfMissingOrEmpty(resources, result, "offensivePrayers");
  if (!result.has("observedGear") && defaults.has("observedGear")) {
   result.add("observedGear", defaults.get("observedGear").deepCopy());
  }
  if (refreshGear) {
   JsonObject currentGear = parseObject(gear.get(), "Observed gear is not valid JSON");
   if (currentGear.size() > 0) {
    result.add("observedGear", currentGear);
   }
  }
  JsonObject weapons = refreshGear ? observedWeapons() : existingWeapons(result);
  result.add("weapons", weapons);
  boolean weaponsReady = weaponId(weapons, "meleeWeaponId") > 0 && weaponId(weapons, "rapierId") > 0
   && weaponId(weapons, "avernicDefenderId") > 0 && weaponId(weapons, "elderMaulId") > 0
   && weaponId(weapons, "rangedWeaponId") > 0 && weaponId(weapons, "bootsId") > 0
   && weaponId(weapons, "blessingId") > 0;
  if (weaponsReady) {
   buildToaGear(result, weaponId(weapons, "meleeWeaponId"), weaponId(weapons, "rapierId"),
    weaponId(weapons, "avernicDefenderId"), weaponId(weapons, "elderMaulId"),
    weaponId(weapons, "rangedWeaponId"), weaponId(weapons, "rangedRingId"), weaponId(weapons, "bootsId"), weaponId(weapons, "blessingId"));
  }
  ensureToaMechanics(result);

  result.addProperty("eatAtHp", 80);
  result.addProperty("restorePrayerAt", 40);
  result.addProperty("potionCooldownTicks", 3);
  JsonArray prePot = new JsonArray();
  for (String step : PRE_POT) {
   prePot.add(step);
  }
  result.add("prePot", prePot);
  result.addProperty("combatImplemented", weaponsReady);
  try {
   return validate(raid, result.toString());
  } catch (IllegalArgumentException error) {
   throw new IllegalStateException(
    "Cannot prepare TOA yet: " + error.getMessage() + ". Load a validated Chambers profile first.", error);
  }
 }

 private static void ensureToaMechanics(JsonObject result) {
  if (!result.has("blockTile") && result.has("roomPosition")
    && result.get("roomPosition").isJsonArray()) {
   result.add("blockTile", result.get("roomPosition").deepCopy());
  }
  JsonObject mechanics = result.has("mechanics") && result.get("mechanics").isJsonObject()
   ? result.getAsJsonObject("mechanics") : new JsonObject();
  if (!mechanics.has("attackRange")) {
   mechanics.addProperty("attackRange", 10);
  }
  if (!mechanics.has("npcIds") || !mechanics.get("npcIds").isJsonObject()) {
   mechanics.add("npcIds", new JsonObject());
  }
  JsonObject signals = mechanics.has("signals") && mechanics.get("signals").isJsonObject()
   ? mechanics.getAsJsonObject("signals") : new JsonObject();
  for (String key : Arrays.asList(
    "floorSlamAnimations", "floorSlamGraphics", "floorSlamObjects",
    "babaRockGraphics", "imprisonmentGraphics")) {
   if (!signals.has(key) || !signals.get(key).isJsonArray()) {
    signals.add(key, new JsonArray());
   }
  }
  if (!signals.has("protectionProjectiles")
    || !signals.get("protectionProjectiles").isJsonObject()) {
   signals.add("protectionProjectiles", new JsonObject());
  }
  mechanics.add("signals", signals);
  result.add("mechanics", mechanics);
 }

 private JsonObject observedWeapons() {
  Integer scythe = null;
  Integer rapier = null;
  Integer defender = null;
  Integer maul = null;
  Integer cards = null;
  Integer rangedRing = null;
  Integer boots = null;
  Integer blessing = null;
  Set<Integer> candidates = new LinkedHashSet<>();
  try {
   JsonObject configured = parseObject(gear.get(), "Observed gear is not valid JSON");
   for (Map.Entry<String, JsonElement> entry : configured.entrySet()) {
    if (!entry.getValue().isJsonArray()) continue;
    for (JsonElement value : entry.getValue().getAsJsonArray()) {
     int id = value.getAsInt();
     if (id > 0) candidates.add(id);
    }
   }
  } catch (RuntimeException ignored) {
   // Live containers below remain authoritative when the gear snapshot is unavailable.
  }
  for (InventoryID containerId : new InventoryID[] {InventoryID.EQUIPMENT, InventoryID.INVENTORY}) {
   ItemContainer container = client.getItemContainer(containerId);
   if (container == null || container.getItems() == null) {
    continue;
   }
   for (Item item : container.getItems()) if (item != null && item.getId() > 0) candidates.add(item.getId());
  }
  for (int itemId : candidates) {
    String name = normalizedName(itemId);
    if (scythe == null && isChargedScytheName(name)) {
     scythe = itemId;
    }
    if (maul == null && name.contains("elder maul")) {
     maul = itemId;
    }
    if (rapier == null && (name.contains("ghrazi rapier") || name.equals("rapier") || name.endsWith(" rapier"))) rapier = itemId;
    if (defender == null && name.contains("avernic defender")) defender = itemId;
    if (cards == null && name.contains("holy cards")) cards = itemId;
    if (rangedRing == null && name.contains("archers ring") && (name.contains("(i)") || name.matches(".*archers ring[ ]+i(?:$|[^a-z].*).*"))) rangedRing = itemId;
    if (boots == null && name.contains("avernic treads")) boots = itemId;
    if (blessing == null && name.contains("blessing")) blessing = itemId;
  }
  JsonObject result = new JsonObject();
  result.add("meleeWeaponId", scythe == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(scythe));
  result.add("rapierId", rapier == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(rapier));
  result.add("avernicDefenderId", defender == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(defender));
  result.add("elderMaulId", maul == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(maul));
  result.add("rangedWeaponId", cards == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(cards));
  result.add("rangedRingId", rangedRing == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(rangedRing));
  result.add("bootsId", boots == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(boots));
  result.add("blessingId", blessing == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(blessing));
  JsonArray missing = new JsonArray();
  if (scythe == null) missing.add("A positively charged Scythe of Vitur is not present in the current inventory or equipment.");
  if (rapier == null) missing.add("A Ghrazi rapier is not present for Warden core windows.");
  if (defender == null) missing.add("An Avernic defender is not present for Warden core windows.");
  if (maul == null) {
   missing.add("Elder maul is not present in the current inventory or equipment.");
  }
  if (cards == null) missing.add("Holy Cards are not present in the current inventory or equipment.");
  if (boots == null) missing.add("Avernic Treads are not present in the current inventory or equipment.");
  if (blessing == null) missing.add("A blessing is not present in the current inventory or equipment.");
  result.add("missing", missing);
  return result;
 }

 private JsonObject existingWeapons(JsonObject result) {
  if (result.has("weapons") && result.get("weapons").isJsonObject()) {
   return result.getAsJsonObject("weapons").deepCopy();
  }
  JsonObject missing = new JsonObject();
  missing.add("meleeWeaponId", JsonNull.INSTANCE);
  missing.add("rapierId", JsonNull.INSTANCE);
  missing.add("avernicDefenderId", JsonNull.INSTANCE);
  missing.add("elderMaulId", JsonNull.INSTANCE);
  missing.add("rangedWeaponId", JsonNull.INSTANCE);
  missing.add("rangedRingId", JsonNull.INSTANCE);
  missing.add("bootsId", JsonNull.INSTANCE);
  missing.add("blessingId", JsonNull.INSTANCE);
  JsonArray reasons = new JsonArray();
  reasons.add("The configured charged Scythe mapping is missing from this saved profile.");
  reasons.add("Elder maul mapping is missing from this saved profile.");
  reasons.add("Holy Cards mapping is missing from this saved profile.");
  reasons.add("Avernic Treads mapping is missing from this saved profile.");
  reasons.add("Blessing mapping is missing from this saved profile.");
  missing.add("missing", reasons);
  return missing;
 }

 private int weaponId(JsonObject weapons, String key) {
  return weapons.has(key) && !weapons.get(key).isJsonNull() ? weapons.get(key).getAsInt() : -1;
 }

 private static boolean isChargedScytheName(String name) {
  String value = name == null ? "" : name.toLowerCase(Locale.ROOT);
  return value.contains("scythe of vitur") && !value.contains("uncharged")
   && !value.matches(".*(?:\\(0\\)|0\\s*charges?).*");
 }

 private void buildToaGear(JsonObject result, int scythe, int rapier, int defender, int maul, int cards, int rangedRing, int boots, int blessing) {
  if (!result.has("observedGear") || !result.get("observedGear").isJsonObject()) {
   return;
  }
  JsonObject gearCopy = result.getAsJsonObject("observedGear").deepCopy();
  JsonArray source = gearCopy.has("MELEE") && gearCopy.get("MELEE").isJsonArray()
   ? gearCopy.getAsJsonArray("MELEE") : new JsonArray();
  JsonArray meleeBase = new JsonArray();
  for (JsonElement value : source) {
   int id = value.getAsInt();
   String name = normalizedName(id);
   if (isRaidMeleeWeaponName(name) || RaidGearRules.isOffhandName(name)) continue;
   if (!containsId(meleeBase, id)) meleeBase.add(id);
  }
  meleeBase.add(scythe);
  JsonArray melee = normaliseSharedSlots(meleeBase, boots, blessing);
  JsonArray core = new JsonArray();
  for (JsonElement value : melee) {
   int sourceId = value.getAsInt();
   if (sourceId == scythe || RaidGearRules.isOffhandName(normalizedName(sourceId))) continue;
   if (!containsId(core, sourceId)) core.add(sourceId);
  }
  core.add(rapier);
  core.add(defender);
  core = normaliseSharedSlots(core, boots, blessing);
  JsonArray spec = new JsonArray();
  for (JsonElement value : melee) {
   int sourceId = value.getAsInt();
   if (sourceId == scythe || RaidGearRules.isOffhandName(normalizedName(sourceId))) continue;
   if (!containsId(spec, sourceId)) spec.add(sourceId);
  }
  spec.add(maul);
  spec = normaliseSharedSlots(spec, boots, blessing);
  gearCopy.add("MELEE", melee);
  gearCopy.add("CORE", core);
  gearCopy.add("SPEC", spec);
  JsonArray rangedSource = gearCopy.has("RANGED") && gearCopy.get("RANGED").isJsonArray()
   ? gearCopy.getAsJsonArray("RANGED") : new JsonArray();
  JsonArray ranged = new JsonArray();
  for (JsonElement value : rangedSource) {
   int id = value.getAsInt();
   String name = normalizedName(id);
   if (id == cards || id == 27645 || name.contains("holy cards") || name.contains("eye of ayak") || name.contains("twisted bow") || rangedRing > 0 && name.contains("archers ring")) continue;
   if (!containsId(ranged, id)) {
    ranged.add(id);
   }
  }
  ranged.add(cards);
  if (rangedRing > 0 && !containsId(ranged,rangedRing)) ranged.add(rangedRing);
  gearCopy.add("RANGED", normaliseSharedSlots(ranged, boots, blessing));
  for (String style : Arrays.asList("MAGIC")) {
   JsonArray current = gearCopy.has(style) && gearCopy.get(style).isJsonArray() ? gearCopy.getAsJsonArray(style) : new JsonArray();
   gearCopy.add(style, normaliseSharedSlots(current, boots, blessing));
  }
  result.add("observedGear", gearCopy);
 }

 private JsonArray normaliseSharedSlots(JsonArray source, int boots, int blessing) {
  JsonArray result = new JsonArray();
  for (JsonElement value : source) {
   int id = value.getAsInt();String name = normalizedName(id);
   if (isBootName(name) || name.contains("blessing") || name.contains("dragon arrow")) continue;
   if (!containsId(result,id)) result.add(id);
  }
  if (!containsId(result,boots)) result.add(boots);
  if (!containsId(result,blessing)) result.add(blessing);
  return result;
 }

 private static boolean isRaidMeleeWeaponName(String name){String value=name==null?"":name;return value.contains("scythe of vitur")||value.contains("ghrazi rapier")||value.contains("dragon hunter lance")||value.endsWith(" rapier")||value.equals("rapier");}
 private static boolean isBootName(String name){String value=name==null?"":name;return value.matches(".*(?:boots|sandals|slippers|shoes|greaves|sabatons|footwraps|treads).*");}

 private String normalizedName(int itemId) {
  net.runelite.api.ItemComposition definition = client.getItemDefinition(itemId);
  String name = definition == null || definition.getName() == null ? "" : definition.getName();
  return name.replaceAll("<[^>]*>", " ").replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
 }

 private static String missingWeaponStatus(JsonObject candidate) {
  if (candidate.has("weapons") && candidate.get("weapons").isJsonObject()) {
   JsonObject weapons = candidate.getAsJsonObject("weapons");
   if (weapons.has("missing") && weapons.get("missing").isJsonArray()) {
    StringBuilder text = new StringBuilder();
    for (JsonElement reason : weapons.getAsJsonArray("missing")) {
     if (text.length() > 0) {
      text.append(' ');
     }
     text.append(reason.getAsString());
    }
    if (text.length() > 0) {
     return text.toString();
    }
   }
  }
  return "Charged Scythe, Elder maul, Holy Cards, Avernic Treads or blessing mapping is missing.";
 }

 private static void copyIfMissingOrEmpty(JsonObject source, JsonObject target, String key) {
  boolean empty = !target.has(key)
   || target.get(key).isJsonNull()
   || (target.get(key).isJsonObject() && target.getAsJsonObject(key).size() == 0)
   || (target.get(key).isJsonArray() && target.getAsJsonArray(key).size() == 0);
  if (empty && source.has(key)) {
   target.add(key, source.get(key).deepCopy());
  }
 }

 private static JsonObject parseObject(String text, String message) {
  try {
   return new JsonParser().parse(text == null ? "{}" : text).getAsJsonObject();
  } catch (RuntimeException error) {
   throw new IllegalArgumentException(message, error);
  }
 }

 private JsonArray items(ItemContainer bag) {
  JsonArray result = new JsonArray();
  if (bag != null) {
   for (Item item : bag.getItems()) {
    if (item != null && item.getId() > 0) {
     JsonArray slot = new JsonArray();
     slot.add(item.getId());
     slot.add(item.getQuantity());
     result.add(slot);
    }
   }
  }
  return result;
 }

 @Override
 public void close() {
  SwingUtilities.invokeLater(() -> {
   if (dialog != null) {
    dialog.dispose();
   }
   dialog = null;
   editor = null;
  });
  synchronized (this) {
   data = null;
  }
 }
}
