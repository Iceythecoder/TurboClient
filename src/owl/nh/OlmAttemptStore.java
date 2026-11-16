package owl.nh;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** User-requested local attempt outputs. Atomic JSON checkpoints; no chat or player names. */
final class OlmAttemptStore {
 static Path directory(Path project){return project.resolve("profiles").resolve("attempts");}
 static Path save(Path project,String id,String json)throws IOException{
  if(!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid attempt identifier");
  OlmPresetStore.parse(json);
  Path dir=directory(project);Files.createDirectories(dir);
  Path out=dir.resolve("olm-"+id+".json");
  write(out,json);write(dir.resolve("latest.json"),json);return out;
 }
 private static void write(Path out,String json)throws IOException{
  Path temp=Files.createTempFile(out.getParent(),".checkpoint-",".tmp");
  try{Files.write(temp,json.getBytes(StandardCharsets.UTF_8));try{Files.move(temp,out,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,out,StandardCopyOption.REPLACE_EXISTING);}}
  finally{Files.deleteIfExists(temp);}
 }
 static String read(Path file)throws IOException{
  if(Files.size(file)>OlmPresetStore.MAX_BYTES)throw new IOException("Attempt exceeds 4 MB");
  String json=Files.readString(file,StandardCharsets.UTF_8);OlmPresetStore.parse(json);return json;
 }
 static String recover(Path project,String json)throws IOException{
  JsonObject root=new JsonParser().parse(json).getAsJsonObject();if(root.has("attempt")){JsonObject meta=root.getAsJsonObject("attempt");if("RUNNING".equals(meta.get("outcome").getAsString())){meta.addProperty("outcome","INTERRUPTED");meta.addProperty("recoveredAtUtc",java.time.Instant.now().toString());json=root.toString();save(project,meta.get("id").getAsString(),json);}}return json;
 }
 static String latest(Path project)throws IOException{
  Path dir=directory(project),latest=dir.resolve("latest.json");if(!Files.isDirectory(dir))return null;
  if(Files.isRegularFile(latest))try{return read(latest);}catch(IOException|RuntimeException ignored){}
  List<Path> candidates=new ArrayList<>();try(DirectoryStream<Path> files=Files.newDirectoryStream(dir,"olm-*.json")){for(Path file:files)candidates.add(file);}
  candidates.sort(Comparator.comparingLong(OlmAttemptStore::modified).reversed());
  for(int i=0;i<Math.min(128,candidates.size());i++)try{return read(candidates.get(i));}catch(IOException|RuntimeException ignored){}
  if(Files.exists(latest)||!candidates.isEmpty())throw new IOException("No valid saved attempt; existing files were preserved");return null;
 }
 private static long modified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException e){return 0;}}
}
