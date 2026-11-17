package turbo.gauntlet;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit Orion evidence registry. Unknown IDs remain unknown; OSRS references never arm control. */
final class GauntletSignals {
 static final String[] CRITICAL={"instance","startRoom","bossRoom","rewardRoom","hunlleffNpc",
  "hunlleffMagicProjectile","hunlleffRangedProjectile","hunlleffFloorWarning","hunlleffFloorActive",
  "tornadoGraphic","rewardChest","exitObject","foodItem","potionItem","weaponItems","armorItems"};
 private final Map<String,Entry> entries=new LinkedHashMap<>();
 static final class Entry {GauntletState.Confidence confidence;Set<Integer> ids=new LinkedHashSet<>();String note="";}
 GauntletSignals(){for(String key:CRITICAL){Entry e=new Entry();e.confidence=GauntletState.Confidence.UNKNOWN;entries.put(key,e);}}
 Entry get(String key){return entries.computeIfAbsent(key,k->{Entry e=new Entry();e.confidence=GauntletState.Confidence.UNKNOWN;return e;});}
 boolean matches(String key,int id){Entry e=get(key);return e.confidence==GauntletState.Confidence.VERIFIED_RUNTIME&&e.ids.contains(id);}
 boolean controlReady(){for(String key:CRITICAL){Entry e=get(key);if(e.confidence!=GauntletState.Confidence.VERIFIED_RUNTIME||e.ids.isEmpty())return false;}return true;}
 List<String> missing(){List<String> out=new ArrayList<>();for(String key:CRITICAL){Entry e=get(key);if(e.confidence!=GauntletState.Confidence.VERIFIED_RUNTIME||e.ids.isEmpty())out.add(key);}return out;}
 String json(){JsonObject root=new JsonObject();root.addProperty("schema","turbo-gauntlet-signals-v1");JsonObject values=new JsonObject();for(Map.Entry<String,Entry> pair:entries.entrySet()){JsonObject e=new JsonObject();e.addProperty("confidence",pair.getValue().confidence.name());JsonArray ids=new JsonArray();for(int id:pair.getValue().ids)ids.add(id);e.add("ids",ids);e.addProperty("note",pair.getValue().note);values.add(pair.getKey(),e);}root.add("signals",values);return new GsonBuilder().setPrettyPrinting().create().toJson(root);}
 void load(Path file)throws Exception{JsonObject root=new JsonParser().parse(Files.readString(file)).getAsJsonObject();JsonObject values=root.getAsJsonObject("signals");if(values==null)throw new IllegalArgumentException("Missing signals object");for(Map.Entry<String,JsonElement> pair:values.entrySet()){JsonObject raw=pair.getValue().getAsJsonObject();Entry e=get(pair.getKey());e.ids.clear();if(raw.has("ids"))for(JsonElement id:raw.getAsJsonArray("ids"))e.ids.add(id.getAsInt());try{e.confidence=GauntletState.Confidence.valueOf(raw.get("confidence").getAsString());}catch(Exception ignored){e.confidence=GauntletState.Confidence.UNKNOWN;}e.note=raw.has("note")?raw.get("note").getAsString():"";}}
 void save(Path file)throws Exception{Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),"gauntlet-signals-",".tmp");try{Files.writeString(tmp,json(),StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
}
