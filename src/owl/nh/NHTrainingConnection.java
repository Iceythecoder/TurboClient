package owl.nh;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Jobs run against the pinned local nh-train.com project's simulator, not Orion. */
public final class NHTrainingConnection implements AutoCloseable {
 public static final String WEBSITE="https://nh-train.com/";
 public volatile String status="Not checked",details="Local NH Trainer simulator; no gameplay uploads";
 public volatile boolean connected;private final AtomicBoolean busy=new AtomicBoolean();private final AtomicLong generation=new AtomicLong();
 public volatile FightFrame fightFrame;public volatile String phase="Waiting for a job";private final ArrayDeque<Double> returns=new ArrayDeque<>();
 private long frameSequence;private BrowserFightServer browser;
 private final File root,python,worker;private volatile Process process;
 private final ExecutorService jobs=Executors.newSingleThreadExecutor(LearningAgent.daemon("nh-trainer-job"));
 private final Gson gson=new Gson();
 public NHTrainingConnection(File root){this.root=root;python=new File(root,"learning/.venv/Scripts/python.exe");worker=new File(root,"learning/nh-trainer/nh_worker.py");}
 public boolean installed(){return python.isFile()&&worker.isFile()&&new File(root,"learning/nh-trainer/source-manifest.json").isFile();}
 public boolean busy(){return busy.get();}
 public void check(){check("nh-starter");}
 public void check(String profile){validateProfile(profile);connected=false;start("Checking "+profile+" source and tick connection",Arrays.asList("check","--profile",profile));}
 public void train(String algorithm,String opponent,int steps,int fightTicks,File output) {
  train(algorithm,opponent,steps,fightTicks,output,false,0);
 }
 public void train(String algorithm,String opponent,int steps,int fightTicks,File output,boolean watch,int tickDelayMs) {
  train("dmm",algorithm,opponent,steps,fightTicks,output,watch,tickDelayMs);
 }
 public void train(String profile,String algorithm,String opponent,int steps,int fightTicks,File output,boolean watch,int tickDelayMs) {
  validateProfile(profile);
  if(!connected)throw new IllegalStateException("Run Check training connection first");
  if(!Arrays.asList("PPO","RecurrentPPO").contains(algorithm))throw new IllegalArgumentException("Unsupported algorithm");
  if(steps<128||steps>10000000)throw new IllegalArgumentException("Steps must be 128..10000000");
  if(!output.getName().toLowerCase(Locale.ROOT).endsWith(".zip"))throw new IllegalArgumentException("Checkpoint must end in .zip");
  File metadata=new File(output.getParentFile(),output.getName().substring(0,output.getName().length()-4)+".json");
  if(output.exists()||metadata.exists())throw new IllegalArgumentException("Choose a new checkpoint filename; existing models are preserved");
  validateOpponent(opponent);validateTicks(fightTicks);
  List<String> command=new ArrayList<>(Arrays.asList("train","--profile",profile,"--algorithm",algorithm,"--opponent",opponent,"--steps",""+steps,"--fight-ticks",""+fightTicks,"--output",output.getAbsolutePath()));viewArguments(command,watch,tickDelayMs);start("Training "+algorithm+" / "+profile+" in NH Trainer",command);
 }
 public void evaluate(File checkpoint,String opponent,int fights,int fightTicks) {
  evaluate(checkpoint,opponent,fights,fightTicks,false,0);
 }
 public void evaluate(File checkpoint,String opponent,int fights,int fightTicks,boolean watch,int tickDelayMs) {
  evaluate("dmm",checkpoint,opponent,fights,fightTicks,watch,tickDelayMs);
 }
 public void evaluate(String profile,File checkpoint,String opponent,int fights,int fightTicks,boolean watch,int tickDelayMs) {
  validateProfile(profile);
  if(!connected)throw new IllegalStateException("Run Check training connection first");if(fights<1||fights>1000)throw new IllegalArgumentException("Fights must be 1..1000");validateOpponent(opponent);validateTicks(fightTicks);
  List<String> command=new ArrayList<>(Arrays.asList("evaluate","--profile",profile,"--model",checkpoint.getAbsolutePath(),"--opponent",opponent,"--fights",""+fights,"--fight-ticks",""+fightTicks));viewArguments(command,watch,tickDelayMs);start("Evaluating saved "+profile+" simulator policy",command);
 }
 public void watchScripted(String opponent,int fights,int ticks,int delay) {
  if(!Arrays.asList("fixed-ranged","fixed-magic","fixed-melee","seeded-rapid-switch","stationary-ranged","seeded-random-prayer","mirror").contains(opponent))throw new IllegalArgumentException("Unsupported scripted opponent");
  if(fights<1||fights>1000)throw new IllegalArgumentException("Fights must be 1..1000");
  validateTicks(ticks);
  List<String> command=new ArrayList<>(Arrays.asList("scripted","--profile","nh-starter","--opponent",opponent,"--fights",""+fights,"--fight-ticks",""+ticks));
  viewArguments(command,true,delay);details="Watching the restored-rules NH starter fighter locally. No checkpoint needed; no learning during this fight.";start("Watching NH starter fighter / restored rules",command);
 }
 static void viewArguments(List<String> command,boolean watch,int delay){if(delay<0||delay>2000)throw new IllegalArgumentException("Viewer delay must be 0..2000 ms");if(watch){command.add("--watch");command.add("--tick-delay-ms");command.add(""+delay);}}
 public synchronized List<Double> episodeReturns(){return new ArrayList<>(returns);}
 public synchronized java.net.URI browserUri()throws IOException {if(browser==null)browser=new BrowserFightServer(root,this);return browser.uri();}
 public synchronized String browserSnapshot(){JsonObject snapshot=new JsonObject();snapshot.addProperty("sequence",frameSequence);snapshot.addProperty("busy",busy());snapshot.addProperty("status",status);snapshot.addProperty("phase",phase);snapshot.add("frame",gson.toJsonTree(fightFrame));return gson.toJson(snapshot);}
 private void start(String label,List<String> arguments) {
  if(!installed())throw new IllegalStateException("NH Trainer runtime/source is not installed");
  if(!busy.compareAndSet(false,true))throw new IllegalStateException("An NH Trainer job is already running");
  long id=generation.incrementAndGet();status=label;phase="Starting";synchronized(this){fightFrame=null;frameSequence++;returns.clear();}
  jobs.execute(()->{
   Process child=null;boolean finalEvent=false;
   try {
    if(id!=generation.get())return;
    List<String> command=new ArrayList<>();command.add(python.getAbsolutePath());command.add("-u");command.add(worker.getAbsolutePath());command.addAll(arguments);
    ProcessBuilder builder=new ProcessBuilder(command);builder.directory(root);builder.redirectError(ProcessBuilder.Redirect.DISCARD);builder.environment().put("PYTHONDONTWRITEBYTECODE","1");builder.environment().put("MPLCONFIGDIR",new File(root,"learning/cache/matplotlib").getAbsolutePath());
    child=builder.start();process=child;if(id!=generation.get()){child.destroyForcibly();return;}
    try(BufferedReader reader=new BufferedReader(new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8))) {
     String line;while((line=reader.readLine())!=null) {
      if(id!=generation.get())break;if(line.length()>32768)throw new IOException("Invalid NH Trainer protocol response");JsonObject event=gson.fromJson(line,JsonObject.class);String kind=event.get("event").getAsString();
      if(kind.equals("error"))throw new IOException(event.get("message").getAsString());
      if(kind.equals("frame")){FightFrame frame=gson.fromJson(event.get("frame"),FightFrame.class);frame.validate();synchronized(this){fightFrame=frame;frameSequence++;}if(frame.mode.equals("evaluation"))phase=frame.phase;}
      if(kind.equals("phase"))phase=event.get("phase").getAsString();
      if(kind.equals("episode")){synchronized(this){returns.addLast(event.get("return").getAsDouble());while(returns.size()>120)returns.removeFirst();}}
      if(kind.equals("connected")){connected=true;finalEvent=true;String profile=event.has("training_profile")?event.get("training_profile").getAsString():"dmm";status="Connected to local NH Trainer / "+profile;details="Source: "+event.get("source_url").getAsString()+"\nCommit: "+event.get("commit").getAsString()+"\nInputs: "+event.get("input_size").getAsInt()+" / channels: "+event.get("action_dimensions")+"\nVerified simulation ticks: "+event.get("checked_ticks").getAsInt()+" / logical tick: 600 ms\n"+(profile.equals("nh-starter")?"NH starter inventory only: Mystic/SOTD, Karil's/DCB, Tentacle/defender, AGS.\n":"DMM equipment profile.\n")+"Orion compatibility: not validated";}
      if(kind.equals("training"))status="Training "+event.get("algorithm").getAsString()+" against "+event.get("opponent").getAsString();
      if(kind.equals("progress"))status="Training: "+event.get("steps").getAsInt()+" / "+event.get("requested_steps").getAsInt()+" steps";
      if(kind.equals("trained")){finalEvent=true;status="Simulator checkpoint saved; "+event.get("steps").getAsInt()+" training steps";details="Saved: "+event.get("output").getAsString()+"\nUse Evaluate checkpoint to test it.\nSimulator-only checkpoint; do not load as an Orion control policy.";}
      if(kind.equals("evaluation")||kind.equals("evaluated")){status="Evaluation: "+event.get("fights").getAsInt()+" fights / "+event.get("wins").getAsInt()+" wins / "+event.get("losses").getAsInt()+" losses / "+event.get("draws").getAsInt()+" draws";if(kind.equals("evaluated")){finalEvent=true;details="Evaluation against "+event.get("opponent").getAsString()+" in NH Trainer.\nThese results do not validate Orion combat behavior.";}}
     }
    }
    int exit=child.waitFor();if(id==generation.get()&&(exit!=0||!finalEvent))throw new IOException("Job did not complete successfully (exit "+exit+")");
   }catch(Exception error){if(id==generation.get()){status="NH Trainer job failed: "+error.getMessage();if(arguments.get(0).equals("check"))connected=false;}}
   finally{if(child!=null&&child.isAlive())child.destroyForcibly();process=null;busy.set(false);}
  });
 }
 static void validateOpponent(String name){if(!Arrays.asList("fixed-ranged","fixed-magic","fixed-melee","seeded-rapid-switch").contains(name))throw new IllegalArgumentException("Unsupported simulator opponent");}
 static void validateProfile(String profile){if(!Arrays.asList("nh-starter","dmm").contains(profile))throw new IllegalArgumentException("Unsupported training profile");}
 static void validateTicks(int ticks){if(ticks<32||ticks>1200)throw new IllegalArgumentException("Fight length must be 32..1200 ticks");}
 public void cancel(){generation.incrementAndGet();Process child=process;if(child!=null)child.destroyForcibly();status="NH Trainer job cancelled";}
 @Override public void close(){cancel();jobs.shutdownNow();synchronized(this){if(browser!=null){browser.close();browser=null;}}}
}
