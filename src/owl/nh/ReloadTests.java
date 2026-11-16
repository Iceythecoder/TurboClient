package owl.nh;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;

/** Offline lifecycle and real release-loader checks. No Orion process, login or inputs. */
public final class ReloadTests {
 private static void check(boolean value,String message){CoreTests.check(value,"Reload: "+message);}
 public static void main(String[] args)throws Exception{
  URL release=ReloadTests.class.getProtectionDomain().getCodeSource().getLocation();
  try(CompanionHost.ReleaseLoader first=new CompanionHost.ReleaseLoader(release,ReloadTests.class.getClassLoader());CompanionHost.ReleaseLoader second=new CompanionHost.ReleaseLoader(release,ReloadTests.class.getClassLoader())){
   Class<?> a=first.loadClass("owl.nh.CombatRuntime"),b=second.loadClass("owl.nh.CombatRuntime");check(a!=b&&a.getClassLoader()==first&&b.getClassLoader()==second,"each companion uses fresh classes");check(first.loadClass(Client.class.getName())==Client.class&&second.loadClass(Client.class.getName())==Client.class,"client API stays in original shared loader");check(first.loadClass(Bootstrap.class.getName())==Bootstrap.class,"bootstrap is never reloaded");
   first.loadClass("owl.nh.CompanionSession").getMethod("open",com.google.inject.Injector.class,String.class,Runnable.class,java.util.function.Consumer.class);
   check("disposed".equals(first.loadClass(ReloadTests.class.getName()).getMethod("lifecycle").invoke(null)),"old runtime is removed before replacement");
   check("disposed".equals(second.loadClass(ReloadTests.class.getName()).getMethod("lifecycle").invoke(null)),"replacement has an independent working lifecycle");
  }
  Path temp=Files.createTempDirectory(CombatRuntime.projectRoot().toPath().resolve("build"),"reload-checks-");try{Path chambers=temp.resolve("chambers.jar"),slayer=temp.resolve("slayer.jar"),toa=temp.resolve("toa-host.jar");Files.write(chambers,new byte[]{1});Files.write(slayer,new byte[]{2});Files.write(toa,new byte[]{3});Files.writeString(temp.resolve("release-chambers.txt"),"chambers.jar");Files.writeString(temp.resolve("release-slayer.txt"),"slayer.jar");Files.writeString(temp.resolve("release-toa-host.txt"),"toa-host.jar");check(CompanionHost.selected(temp,"release-chambers.txt").equals(chambers),"Chambers release pointer selected independently");check(CompanionHost.selected(temp,"release-slayer.txt").equals(slayer),"Slayer release pointer selected independently");check(CompanionHost.selected(temp,"release-toa-host.txt").equals(toa),"TOA host release pointer selected independently");Files.writeString(temp.resolve("release-chambers.txt"),"../outside.jar");boolean rejected=false;try{CompanionHost.selected(temp,"release-chambers.txt");}catch(IOException e){rejected=true;}check(rejected,"release selection cannot escape project");}finally{try(java.util.stream.Stream<Path> files=Files.walk(temp)){for(Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
  check(CompanionHost.boundedLine(new StringReader("RELOAD token\r\n")).equals("RELOAD token"),"protocol handles Windows line endings");boolean rejected=false;try{CompanionHost.boundedLine(new StringReader(String.join("",Collections.nCopies(300,"x"))));}catch(IOException e){rejected=true;}check(rejected,"oversized commands rejected");
  System.out.println("Reload checks passed: isolated releases, shared Orion API, listener cleanup, stopped controls, repeat disposal and bounded local protocol. No live inputs.");
 }
 public static String lifecycle()throws Exception{
  Map<String,Object> fields=new HashMap<>();fields.put("isClientThread",true);fields.put("getGameState",GameState.LOGIN_SCREEN);fields.put("getNpcs",Collections.emptyList());fields.put("getPlayers",Collections.emptyList());fields.put("getPlayerOptions",new String[0]);fields.put("getTickCount",1);
  Client client=ChambersTests.fake(Client.class,fields);java.lang.reflect.Constructor<ClientThread> constructor=ClientThread.class.getDeclaredConstructor(Client.class);constructor.setAccessible(true);EventBus bus=new EventBus();CombatRuntime runtime=new CombatRuntime(client,constructor.newInstance(client),bus,null);bus.register(runtime);bus.post(new GameTick());check(runtime.ticks.tick==1,"fixture receives events before disposal");runtime.armed=true;runtime.chambers.armed=true;runtime.olm.running=true;
  CompletableFuture<Void> closed=new CompletableFuture<>();runtime.disposeForReload(()->closed.complete(null),closed::completeExceptionally);closed.get(5,TimeUnit.SECONDS);check(!runtime.armed&&!runtime.chambers.armed&&!runtime.olm.running,"inputs stopped during reload");fields.put("getTickCount",2);bus.post(new GameTick());check(runtime.ticks.tick==1,"event listener unregistered");CompletableFuture<Void> twice=new CompletableFuture<>();runtime.disposeForReload(()->twice.complete(null),twice::completeExceptionally);twice.get(5,TimeUnit.SECONDS);OlmFighter.finishAttemptWrites();return "disposed";
 }
}
