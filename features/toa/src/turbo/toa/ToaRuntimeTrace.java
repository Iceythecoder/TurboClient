package turbo.toa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, local-only Orion mechanics evidence. It deliberately records no player names. */
final class ToaRuntimeTrace {
 private static final int MAX_EVENTS = 24000;
 private static final DateTimeFormatter FILE_TIME =
  DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
 private final List<Map<String, Object>> events = new ArrayList<>();
 private Instant startedAt;

 synchronized void reset() {
  events.clear();
  startedAt = Instant.now();
 }

 synchronized void event(String type, int tick, Object... pairs) {
  if (startedAt == null || events.size() >= MAX_EVENTS) return;
  Map<String, Object> event = new LinkedHashMap<>();
  event.put("type", type);
  event.put("tick", tick);
  for (int i = 0; pairs != null && i + 1 < pairs.length; i += 2) {
   event.put(String.valueOf(pairs[i]), pairs[i + 1]);
  }
  events.add(event);
 }

 synchronized int size() { return events.size(); }

 synchronized JsonObject snapshot(String reason, ToaStateMachine.State state) {
  JsonObject root = new JsonObject();
  root.addProperty("schema", "turbo-toa-runtime-v1");
  root.addProperty("startedAt", startedAt == null ? Instant.now().toString() : startedAt.toString());
  root.addProperty("endedAt", Instant.now().toString());
  root.addProperty("reason", reason == null ? "" : reason);
  root.addProperty("state", state == null ? "IDLE" : state.name());
  root.addProperty("containsPlayerNames", false);
  root.add("events", new GsonBuilder().create().toJsonTree(new ArrayList<>(events)));
  return root;
 }

 static void save(Path dataRoot, JsonObject trace) throws java.io.IOException {
  Path attempts = dataRoot.resolve("attempts").toAbsolutePath().normalize();
  Files.createDirectories(attempts);
  String json = new GsonBuilder().setPrettyPrinting().create().toJson(trace);
  Path latest = attempts.resolve("toa-runtime-latest.json");
  atomicWrite(latest, json);
  String started = trace.has("startedAt") ? trace.get("startedAt").getAsString() : Instant.now().toString();
  Instant when;
  try { when = Instant.parse(started); } catch (RuntimeException ignored) { when = Instant.now(); }
  atomicWrite(attempts.resolve("toa-runtime-" + FILE_TIME.format(when) + ".json"), json);
 }

 private static void atomicWrite(Path target, String text) throws java.io.IOException {
  Path temporary = Files.createTempFile(target.getParent(), "toa-runtime-", ".tmp");
  try {
   Files.writeString(temporary, text, StandardCharsets.UTF_8);
   try {
    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
   } catch (AtomicMoveNotSupportedException ignored) {
    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
   }
  } finally {
   Files.deleteIfExists(temporary);
  }
 }
}
