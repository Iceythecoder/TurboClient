package owl.nh;
import com.google.inject.Injector;
import com.google.gson.*;
import java.util.function.Consumer;

/** Loaded afresh with each companion release; Orion stays in its original loader. */
public final class CompanionSession {
 private final CombatRuntime runtime;private String transfer="";
 private CompanionSession(Injector injector){runtime=new CombatRuntime(injector);}
 public static CompanionSession open(Injector injector,String state,Runnable ready,Consumer<Throwable> failed){
  CompanionSession session=new CompanionSession(injector);long since=System.nanoTime();
  session.runtime.thread.invokeLater(()->{
   try{java.awt.Canvas canvas=session.runtime.client.getCanvas();if(canvas==null||!canvas.isDisplayable()||canvas.getWidth()==0||canvas.getHeight()==0){if(System.nanoTime()-since<java.util.concurrent.TimeUnit.SECONDS.toNanos(20))return false;throw new IllegalStateException("Orion game canvas is not ready");}
    session.restore(state);session.runtime.attach(ready,failed);return true;
   }catch(Throwable error){failed.accept(error);return true;}
  });return session;
 }
 private void restore(String state)throws java.io.IOException{if(state==null||state.isEmpty())return;JsonObject data=new JsonParser().parse(state).getAsJsonObject();Gson gson=new Gson();if(data.has("settings"))runtime.settings(ConfigManager.fromSnapshot(data.get("settings").getAsString()));if(data.has("olm"))runtime.olm.loadPreset(data.get("olm").toString());if(data.has("demonstration"))runtime.demonstration.restore(data.get("demonstration").toString());}
 public String state(){return transfer;}
 public void close(Runnable ready,Consumer<Throwable> failed){
  runtime.armed=false;runtime.chambers.armed=false;runtime.olm.running=false;
  runtime.thread.invoke(()->{try{
   runtime.stop("Companion reload");JsonObject data=new JsonObject();data.addProperty("settings",ConfigManager.snapshot(runtime.config));if(runtime.demonstration.hasData())data.add("demonstration",new JsonParser().parse(runtime.demonstration.export()));String profile=runtime.olm.profileText();try{OlmPresetStore.parse(profile);data.add("olm",new JsonParser().parse(profile));}catch(IllegalArgumentException ignored){}transfer=data.toString();
   java.util.concurrent.CompletableFuture.runAsync(()->{try{long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);while(runtime.olm.attemptSaving()&&System.nanoTime()<until)Thread.sleep(10);if(runtime.olm.attemptSaving())throw new IllegalStateException("Attempt save is still pending; reload cancelled");runtime.olm.requireSavedForReload();runtime.disposeForReload(()->{try{OlmFighter.finishAttemptWrites();ready.run();}catch(Throwable error){failed.accept(error);}},failed);}catch(Throwable error){failed.accept(error);}});
  }catch(Throwable error){failed.accept(error);}});
 }
}