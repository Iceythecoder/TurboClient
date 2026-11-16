package owl.nh;
import com.google.gson.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Typed, validated sharing format; deliberately excludes raw attempts and identity data. */
final class OlmLearningExchange {
 static String sanitize(String json){
  JsonObject input=new JsonParser().parse(json).getAsJsonObject();if(!input.has("schema"))input.addProperty("schema",OlmPresetStore.SCHEMA);OlmPresetStore.Loaded loaded=OlmPresetStore.parse(input.toString());Gson gson=new Gson();JsonObject root=gson.toJsonTree(gson.fromJson(loaded.json,OlmFighter.Profile.class)).getAsJsonObject();root.addProperty("schema",OlmPresetStore.SCHEMA);
  JsonObject raw=new JsonParser().parse(loaded.json).getAsJsonObject();JsonObject gear=new JsonObject();for(String style:new String[]{"MAGIC","MELEE","RANGED","SPEC"})if(raw.getAsJsonObject("observedGear").has(style)){JsonArray ids=new JsonArray();for(JsonElement id:raw.getAsJsonObject("observedGear").getAsJsonArray(style))ids.add(id.getAsInt());gear.add(style,ids);}root.add("observedGear",gear);
  if(loaded.network!=null)root.add("targetNetwork",gson.toJsonTree(loaded.network.checkpoint()));if(loaded.timing!=null)root.add("actionTiming",gson.toJsonTree(loaded.timing));if(loaded.prayerTiming!=null)root.add("prayerTiming",gson.toJsonTree(loaded.prayerTiming));if(loaded.movement!=null){OlmSpecials movement=new OlmSpecials();movement.restoreLearning(loaded.movement);root.add("movementLearning",new JsonParser().parse(movement.exportLearning()));}root.add("targetCorrections",gson.toJsonTree(loaded.corrections));root.add("attackSpeeds",gson.toJsonTree(loaded.speeds));String result=new GsonBuilder().setPrettyPrinting().create().toJson(root);OlmPresetStore.parse(result);return result;
 }
 static void write(Path destination,String json)throws java.io.IOException{
  String clean=sanitize(json);Path target=destination.toAbsolutePath(),parent=target.getParent();Files.createDirectories(parent);Path temp=Files.createTempFile(parent,"turbo-learning-",".tmp");try{Files.writeString(temp,clean,StandardCharsets.UTF_8);try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
 }
 public static void main(String[] args)throws Exception{if(args.length!=2)throw new IllegalArgumentException("Input and output paths required");Path input=Paths.get(args[0]);if(Files.size(input)>OlmPresetStore.MAX_BYTES)throw new IllegalArgumentException("Input exceeds 4 MB");write(Paths.get(args[1]),Files.readString(input));System.out.println("Validated learning export saved without attempt history or identity fields.");}
}
