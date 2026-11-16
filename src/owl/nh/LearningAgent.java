package owl.nh;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static owl.nh.CombatCore.*;

/** Asynchronous, local-only Python policy bridge. Never waits on the game thread. */
public final class LearningAgent implements AutoCloseable {
 public enum Mode { DISABLED, SHADOW, CONTROL }
 public static final String SCHEMA="orion-style-v1";
 public static final String[] FEATURES={"protection","local_hp","prayer_points","opponent_hp_percent","distance","can_barrage","attack_ready","magic_available","ranged_available","melee_available"};
 public static final String[] ACTIONS={"WAIT","MAGIC","RANGED","MELEE"};
 public volatile Mode mode=Mode.DISABLED;public volatile String status="No policy loaded",trainingStatus="Not training";public volatile boolean ready,liveEligible;
 public volatile String algorithm="",environment="";public volatile Prediction prediction;
 private final File root,python,worker;private final Gson gson=new Gson();
 private final ExecutorService io=Executors.newSingleThreadExecutor(daemon("nh-policy"));
 private final ExecutorService training=Executors.newSingleThreadExecutor(daemon("nh-training"));
 private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(daemon("nh-policy-watchdog"));
 private volatile Process process,trainer;private BufferedReader reader;private BufferedWriter writer;private volatile boolean inFlight,resetNext=true;private volatile long busyAt;private long sequence;private final java.util.concurrent.atomic.AtomicLong context=new java.util.concurrent.atomic.AtomicLong();
 public static final class Prediction {
  public final Style style;public final long generation,sampledAt;public final int tick;public final Protection protection;
  Prediction(Style style,State state,long sample){this.style=style;generation=state.generation;tick=state.tick;protection=state.protection;sampledAt=sample;}
  public boolean matches(State state,long now){return generation==state.generation&&tick==state.tick&&protection==state.protection&&now-sampledAt<600000000L;}
 }
 public LearningAgent(File root) {
  this.root=root;python=new File(root,"learning/.venv/Scripts/python.exe");worker=new File(root,"learning/agent_worker.py");
  watchdog.scheduleAtFixedRate(()->{if(inFlight&&System.nanoTime()-busyAt>10000000000L){status="Policy worker timed out; deactivated";deactivate();destroyWorker();}},1,1,TimeUnit.SECONDS);
 }
 static ThreadFactory daemon(String name){return task->{Thread thread=new Thread(task,name);thread.setDaemon(true);return thread;};}
 public boolean installed(){return python.isFile()&&worker.isFile();}
 public void load(File model) {
  deactivate();destroyWorker();status="Loading trusted local policy...";
  io.execute(()->{
   Process candidate=null;boolean initialized=false;
   try {
    if(!installed())throw new IOException("Local learning runtime is not installed");
    if(!model.isFile()||!model.getName().toLowerCase(Locale.ROOT).endsWith(".zip"))throw new IOException("Choose a local SB3 .zip checkpoint");
    File metadataFile=new File(model.getParentFile(),model.getName().substring(0,model.getName().length()-4)+".json");
    if(!metadataFile.isFile()||metadataFile.length()>32768)throw new IOException("Missing/oversized adjacent model .json metadata");
    JsonObject metadata=gson.fromJson(Files.readString(metadataFile.toPath(),StandardCharsets.UTF_8),JsonObject.class);validateMetadata(metadata);
    candidate=builder("--model",model.getAbsolutePath()).start();process=candidate;reader=new BufferedReader(new InputStreamReader(candidate.getInputStream(),StandardCharsets.UTF_8));writer=new BufferedWriter(new OutputStreamWriter(candidate.getOutputStream(),StandardCharsets.UTF_8));
    busyAt=System.nanoTime();inFlight=true;JsonObject response=read();if(!response.get("event").getAsString().equals("ready"))throw new IOException("Worker did not initialize");
    algorithm=metadata.get("algorithm").getAsString();environment=metadata.get("environment").getAsString();
    liveEligible=environment.equals("orion-training-v1")&&flag(metadata,"live_control")&&flag(metadata,"validated_orion");
    initialized=true;status=algorithm+" loaded / "+environment+(liveEligible?" / eligible for explicit control":" / observation only; not Orion validated");
   }catch(Exception e){if(candidate!=null)candidate.destroyForcibly();ready=false;status="Load failed: "+e.getClass().getSimpleName()+" / "+e.getMessage();}
   finally{inFlight=false;ready=initialized&&candidate!=null&&candidate==process&&candidate.isAlive();}
  });
 }
 public boolean acceptingRequests(){return ready&&!inFlight&&mode!=Mode.DISABLED;}
 public void activate(Mode requested) {
  if(!ready)throw new IllegalStateException("Load a trained local policy first");
  if(requested==Mode.CONTROL&&!liveEligible)throw new IllegalStateException("This is not an Orion-validated policy. Activate recommendations to test it; demo policies cannot control live combat.");
  mode=requested;resetContext();
 }
 public void deactivate(){mode=Mode.DISABLED;resetContext();}
 public void resetContext(){context.incrementAndGet();prediction=null;resetNext=true;}
 public void request(State state,Settings settings,AttackManager attacks) {
  if(!ready||mode==Mode.DISABLED||inFlight)return;
  final double[] observation=encode(state,settings,attacks);final long sampled=System.nanoTime();final boolean reset=resetNext;resetNext=false;
  final long id=++sequence,epoch=context.get();State tag=new State();tag.generation=state.generation;tag.tick=state.tick;tag.protection=state.protection;
  inFlight=true;busyAt=sampled;
  io.execute(()->{
   try {
    JsonObject message=new JsonObject();message.addProperty("sequence",id);message.addProperty("generation",tag.generation);message.addProperty("tick",tag.tick);message.addProperty("reset",reset);message.add("observation",gson.toJsonTree(observation));
    writer.write(gson.toJson(message));writer.newLine();writer.flush();JsonObject reply=read();
    if(!reply.get("event").getAsString().equals("prediction")||reply.get("sequence").getAsLong()!=id||reply.get("generation").getAsLong()!=tag.generation||reply.get("tick").getAsInt()!=tag.tick)throw new IOException("Policy response did not match request");
    String action=reply.get("action").getAsString();if(!Arrays.asList(ACTIONS).contains(action))throw new IOException("Unknown policy action");
    Style style=action.equals("WAIT")?Style.UNKNOWN:Style.valueOf(action);if(epoch==context.get()&&mode!=Mode.DISABLED)prediction=new Prediction(style,tag,sampled);
   }catch(Exception e){status="Inference failed: "+e.getClass().getSimpleName();deactivate();destroyWorker();}
   finally{inFlight=false;}
  });
 }
 public void trainDemo(String algorithm,int steps,File output) {
  if(trainer!=null&&trainer.isAlive())throw new IllegalStateException("Training is already running");
  if(!algorithm.equals("PPO")&&!algorithm.equals("RecurrentPPO"))throw new IllegalArgumentException("Unknown training algorithm");
  if(!installed())throw new IllegalStateException("Learning runtime not installed");if(steps<256||steps>1000000)throw new IllegalArgumentException("Steps must be 256..1000000");
  trainingStatus="Starting toy simulator training (not Orion PvP)...";
  training.execute(()->{
   Process job=null;try {
    job=builder("--train-demo","--algorithm",algorithm,"--steps",""+steps,"--output",output.getAbsolutePath()).start();trainer=job;
    try(BufferedReader lines=new BufferedReader(new InputStreamReader(job.getInputStream(),StandardCharsets.UTF_8))) {
     String line;while((line=lines.readLine())!=null){JsonObject message=gson.fromJson(line,JsonObject.class);String event=message.get("event").getAsString();if(event.equals("error"))throw new IOException(message.get("message").getAsString());trainingStatus=event.equals("trained")?"Demo policy saved. Toy legal-action rate: "+message.getAsJsonObject("metadata").get("demo_legal_action_rate").getAsDouble():"Training "+algorithm+" on toy-counter-v1, "+steps+" steps";}
    }
    if(job.waitFor()!=0)throw new IOException("Trainer exited unsuccessfully");
   }catch(Exception error){trainingStatus="Training stopped: "+error.getClass().getSimpleName()+" / "+error.getMessage();}finally{if(job!=null&&job.isAlive())job.destroyForcibly();trainer=null;}
  });
 }
 public void cancelTraining(){Process job=trainer;if(job!=null)job.destroyForcibly();trainingStatus="Training cancelled";}
 private ProcessBuilder builder(String... args) {
  List<String> command=new ArrayList<>();command.add(python.getAbsolutePath());command.add("-u");command.add(worker.getAbsolutePath());command.addAll(Arrays.asList(args));
  ProcessBuilder builder=new ProcessBuilder(command);builder.directory(root);builder.redirectError(ProcessBuilder.Redirect.DISCARD);
  builder.environment().put("MPLCONFIGDIR",new File(root,"learning/cache/matplotlib").getAbsolutePath());builder.environment().put("PYTHONDONTWRITEBYTECODE","1");return builder;
 }
 private JsonObject read()throws IOException {
  String line=reader.readLine();if(line==null||line.length()>32768)throw new IOException("Worker stopped or invalid response");
  JsonObject response=gson.fromJson(line,JsonObject.class);if(response==null||!response.has("event"))throw new IOException("Invalid worker protocol");if(response.get("event").getAsString().equals("error"))throw new IOException(response.get("message").getAsString());return response;
 }
 private void destroyWorker(){Process child=process;if(child!=null)child.destroyForcibly();process=null;ready=false;liveEligible=false;}
 static boolean flag(JsonObject m,String key){return m.has(key)&&m.get(key).getAsBoolean();}
 public static void validateMetadata(JsonObject m) {
  if(m==null||!m.has("schema")||!SCHEMA.equals(m.get("schema").getAsString())||!m.has("environment")||!m.has("algorithm"))throw new IllegalArgumentException("Incompatible policy metadata");
  if(!Arrays.asList("PPO","RecurrentPPO").contains(m.get("algorithm").getAsString()))throw new IllegalArgumentException("Unsupported algorithm");
  if(!m.has("features")||!m.has("actions"))throw new IllegalArgumentException("Missing policy schema");
  Gson gson=new Gson();if(!Arrays.equals(gson.fromJson(m.get("features"),String[].class),FEATURES)||!Arrays.equals(gson.fromJson(m.get("actions"),String[].class),ACTIONS))throw new IllegalArgumentException("Observation/action schema mismatch");
 }
 public static double[] encode(State s,Settings c,AttackManager attacks) {
  int prayer=s.protection==Protection.NONE?0:s.protection==Protection.MAGIC?1:s.protection==Protection.RANGED?2:s.protection==Protection.MELEE?3:4;
  return new double[]{prayer/4.0,unit(s.localHp/100.0),unit(s.prayerPoints/100.0),s.opponentHpPercent<0?-1:unit(s.opponentHpPercent/100.0),unit(s.distance/10.0),s.canBarrage?1:0,attacks.ready(s.tick)?1:0,available(c,s,Style.MAGIC),available(c,s,Style.RANGED),available(c,s,Style.MELEE)};
 }
 static double unit(double value){return Math.max(0,Math.min(1,value));}
 static double available(Settings c,State s,Style style){if(!c.enabled.contains(style))return 0;try{new GearManager().required(c,s,style);return 1;}catch(IllegalStateException missing){return 0;}}
 @Override public void close(){deactivate();destroyWorker();cancelTraining();io.shutdownNow();training.shutdownNow();watchdog.shutdownNow();}
}
