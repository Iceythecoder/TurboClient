package owl.nh;

import java.awt.Frame;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import javax.swing.JFrame;

/** Loads only the ToA module; its state and walkthroughs never enter Olm's stores. */
final class ToaFeature implements AutoCloseable {
 private final CombatRuntime runtime;
 private URLClassLoader loader;
 private Object module;

 ToaFeature(CombatRuntime runtime){this.runtime=runtime;}

 Window window(JFrame owner){
  try{
   if(module==null)load(owner);
   return (Window)module.getClass().getMethod("window").invoke(module);
  }catch(Throwable error){
   while(error instanceof InvocationTargetException&&error.getCause()!=null)error=error.getCause();
   throw new IllegalStateException("ToA window could not open: "+error.getMessage(),error);
  }
 }

 private void load(JFrame owner)throws Exception {
  Path root=CombatRuntime.projectRoot().toPath().toAbsolutePath().normalize();
  Path marker=root.resolve("release-toa.txt");
  String selected=Files.isRegularFile(marker)?Files.readString(marker).trim():"TurboTOA.jar";
  Path jar=root.resolve(selected).normalize();
  if(!jar.startsWith(root)||!Files.isRegularFile(jar))throw new IllegalStateException("TurboTOA.jar is missing. Rebuild or update both raid modules.");
  loader=new URLClassLoader(new URL[]{jar.toUri().toURL()},getClass().getClassLoader());
  try{
   Class<?> type=Class.forName("turbo.toa.ToaModule",true,loader);
   Runnable stopOther=()->runtime.stop("Starting ToA prototype");
   Supplier<String> gearSource=runtime::raidGearSnapshot;
   try{
    module=type.getConstructor(net.runelite.api.Client.class,net.runelite.client.callback.ClientThread.class,net.runelite.client.eventbus.EventBus.class,Frame.class,Path.class,Runnable.class,Supplier.class,RaidInputBridge.class)
      .newInstance(runtime.client,runtime.thread,runtime.busForModules(),owner,root.resolve("profiles/toa"),stopOther,gearSource,runtime.raidInputBridge);
   }catch(NoSuchMethodException legacyModule){
    // A mixed-version installation remains observation-only until it is updated.
    module=type.getConstructor(net.runelite.api.Client.class,net.runelite.client.callback.ClientThread.class,net.runelite.client.eventbus.EventBus.class,Frame.class,Path.class,Runnable.class,Supplier.class)
      .newInstance(runtime.client,runtime.thread,runtime.busForModules(),owner,root.resolve("profiles/toa"),stopOther,gearSource);
   }
  }catch(Throwable error){
   loader.close();loader=null;throw error;
  }
 }

 void stop(String reason){
  if(module!=null)try{module.getClass().getMethod("stop",String.class).invoke(module,reason);}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot stop ToA observation",e);}
 }

 @Override public void close(){
  try{if(module!=null)module.getClass().getMethod("close").invoke(module);}catch(ReflectiveOperationException ignored){}
  finally{module=null;if(loader!=null)try{loader.close();}catch(java.io.IOException ignored){}loader=null;}
 }
}
