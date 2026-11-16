package owl.nh;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Explicitly confirmed local outputs; JSON only, no executable checkpoint deserialization. */
final class OlmPresetStore {
 static final String SCHEMA="orion-olm-learned-preset-v1";
 static final int MAX_BYTES=4*1024*1024;
 static final class Loaded {String json,movement;OlmTargetNetwork network;OlmActionLearning.Checkpoint timing;OlmPrayerLearning.Checkpoint prayerTiming;Map<String,String> corrections=new HashMap<>();Map<String,Integer> speeds=new HashMap<>();}
 static Loaded parse(String text){
  if(text==null||text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Preset exceeds 4 MB");
  JsonObject root=new JsonParser().parse(text).getAsJsonObject();if(!root.has("schema")||!SCHEMA.equals(root.get("schema").getAsString()))throw new IllegalArgumentException("Not a learned Olm preset");
  OlmFighter.Profile p=new Gson().fromJson(root,OlmFighter.Profile.class);p.validate();
  if(!root.has("observedGear"))throw new IllegalArgumentException("Preset needs equipment");
  JsonObject gear=root.getAsJsonObject("observedGear");if(gear.size()>4)throw new IllegalArgumentException("Invalid equipment count");
  for(Map.Entry<String,JsonElement> e:gear.entrySet()){if(!Arrays.asList("MAGIC","MELEE","RANGED","SPEC").contains(e.getKey())||!e.getValue().isJsonArray()||e.getValue().getAsJsonArray().size()>14)throw new IllegalArgumentException("Invalid gear preset");for(JsonElement id:e.getValue().getAsJsonArray())if(id.getAsInt()<=0)throw new IllegalArgumentException("Invalid item ID");}
  Loaded out=new Loaded();out.json=text;
  if(root.has("prayerTiming")){out.prayerTiming=new Gson().fromJson(root.get("prayerTiming"),OlmPrayerLearning.Checkpoint.class);new OlmPrayerLearning().restore(out.prayerTiming);}
  if(root.has("targetNetwork")&&!root.get("targetNetwork").isJsonNull())out.network=OlmTargetNetwork.restore(new Gson().fromJson(root.get("targetNetwork"),OlmTargetNetwork.Checkpoint.class));
  if(root.has("movementLearning")){out.movement=root.get("movementLearning").toString();new OlmSpecials().restoreLearning(out.movement);}
  if(root.has("actionTiming")){out.timing=new Gson().fromJson(root.get("actionTiming"),OlmActionLearning.Checkpoint.class);new OlmActionLearning().restore(out.timing);}
  if(root.has("targetCorrections")){JsonObject corrections=root.getAsJsonObject("targetCorrections");if(corrections.size()>128)throw new IllegalArgumentException("Too many target corrections");for(Map.Entry<String,JsonElement> e:corrections.entrySet()){String value=e.getValue().getAsString();if(!e.getKey().matches("(REGULAR|FINAL_HANDS|HEAD|UNKNOWN):(true|false):(true|false):(true|false):-?[0-9]{1,3}:-?[0-9]{1,3}")||!Arrays.asList("magic","melee","head").contains(value))throw new IllegalArgumentException("Invalid target correction");out.corrections.put(e.getKey(),value);}}
  if(root.has("attackSpeeds")){JsonObject speeds=root.getAsJsonObject("attackSpeeds");if(speeds.size()>4)throw new IllegalArgumentException("Too many attack speeds");for(Map.Entry<String,JsonElement> e:speeds.entrySet()){int speed=e.getValue().getAsInt();if(!Arrays.asList("MAGIC","MELEE","RANGED","SPEC").contains(e.getKey())||speed<1||speed>20)throw new IllegalArgumentException("Invalid attack speed");out.speeds.put(e.getKey(),speed);}}
  return out;
 }
 static Path saveProfile(Path project,String json)throws java.io.IOException{
  parse(json);Path dir=project.resolve("profiles");Files.createDirectories(dir);Path destination=dir.resolve("olm-profile.json"),temporary=Files.createTempFile(dir,"olm-profile-",".tmp");
  try{Files.write(temporary,json.getBytes(StandardCharsets.UTF_8));try{Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);}return destination;}finally{Files.deleteIfExists(temporary);}
 }
 static Path save(Path project,String json)throws java.io.IOException{
  parse(json);Path dir=project.resolve("profiles").resolve("learned");Files.createDirectories(dir);
  String name="olm-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())+"-"+UUID.randomUUID().toString().substring(0,8)+".json";
  Path destination=dir.resolve(name);boolean created=false,complete=false;
  try{Files.createFile(destination);created=true;Files.write(destination,json.getBytes(StandardCharsets.UTF_8),StandardOpenOption.WRITE);complete=true;return destination;}
  finally{if(created&&!complete)Files.deleteIfExists(destination);}
 }
}
