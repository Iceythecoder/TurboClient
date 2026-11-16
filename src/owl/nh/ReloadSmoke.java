package owl.nh;
import com.google.inject.Injector;
import java.awt.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;

/** Full local host/UI reload with a synthetic API. Never opens Orion or logs in. */
public final class ReloadSmoke {
 static void check(boolean value,String message){CoreTests.check(value,"Reload smoke: "+message);}
 public static void main(String[] args)throws Exception{
  Path original=CombatRuntime.projectRoot().toPath(),temp=Files.createTempDirectory(original.resolve("build"),"reload-smoke-");String previous=System.getProperty("owl.project.root");CompanionHost host=null;ScheduledExecutorService pump=Executors.newSingleThreadScheduledExecutor(LearningAgent.daemon("reload-fixture-ticks"));
  try{
   Files.copy(CompanionHost.selected(original),temp.resolve("companion.jar"));Files.writeString(temp.resolve("release-current.txt"),"companion.jar");System.setProperty("owl.project.root",temp.toString());
   Map<String,Object> fields=new HashMap<>();fields.put("isClientThread",true);fields.put("getGameState",GameState.LOGIN_SCREEN);fields.put("getNpcs",Collections.emptyList());fields.put("getPlayers",Collections.emptyList());fields.put("getPlayerOptions",new String[0]);fields.put("getCanvas",new Canvas(){public boolean isDisplayable(){return true;}public int getWidth(){return 800;}public int getHeight(){return 600;}});
   Client client=ChambersTests.fake(Client.class,fields);Constructor<ClientThread> constructor=ClientThread.class.getDeclaredConstructor(Client.class);constructor.setAccessible(true);ClientThread thread=constructor.newInstance(client);EventBus bus=new EventBus();
   Injector injector=(Injector)java.lang.reflect.Proxy.newProxyInstance(Injector.class.getClassLoader(),new Class<?>[]{Injector.class},(object,method,values)->{if(method.getName().equals("getInstance")){if(values[0]==Client.class)return client;if(values[0]==ClientThread.class)return thread;if(values[0]==EventBus.class)return bus;return null;}return null;});
   pump.scheduleAtFixedRate(thread::invoke,0,20,TimeUnit.MILLISECONDS);CompletableFuture<Void> ready=new CompletableFuture<>();host=CompanionHost.start(injector,()->ready.complete(null),ready::completeExceptionally);ready.get(20,TimeUnit.SECONDS);
   Field sessionField=CompanionHost.class.getDeclaredField("session");sessionField.setAccessible(true);Object first=sessionField.get(host);Field runtimeField=first.getClass().getDeclaredField("runtime");runtimeField.setAccessible(true);Object firstRuntime=runtimeField.get(first);Object config=firstRuntime.getClass().getField("config").get(firstRuntime);config.getClass().getField("autoLock").setBoolean(config,false);long pid=ProcessHandle.current().pid();
   java.util.List<String> endpoint=Files.readAllLines(temp.resolve(CompanionHost.ENDPOINT));int port=Integer.parseInt(endpoint.get(2));String token=endpoint.get(3);
   try(Socket socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(30000);PrintWriter output=new PrintWriter(socket.getOutputStream(),true);output.println("RELOAD "+token);String response=new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();check(response!=null&&response.startsWith("OK "),"authenticated host reload succeeds: "+response);}
   Object second=sessionField.get(host);runtimeField=second.getClass().getDeclaredField("runtime");runtimeField.setAccessible(true);Object secondRuntime=runtimeField.get(second);check(first!=second&&first.getClass()!=second.getClass(),"UI/runtime replaced with fresh release classes");check(secondRuntime.getClass().getField("client").get(secondRuntime)==client&&ProcessHandle.current().pid()==pid,"same client object and JVM retained");config=secondRuntime.getClass().getField("config").get(secondRuntime);check(!config.getClass().getField("autoLock").getBoolean(config),"settings transferred across reload");check(!secondRuntime.getClass().getField("armed").getBoolean(secondRuntime),"replacement remains disarmed");
   try(Socket socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(3000);PrintWriter output=new PrintWriter(socket.getOutputStream(),true);output.println("RELOAD wrong-token");check(new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine().startsWith("ERROR unauthorized"),"unauthenticated reload rejected");}check(sessionField.get(host)==second,"rejected request leaves session intact");
   CompletableFuture<Void> closed=new CompletableFuture<>();second.getClass().getMethod("close",Runnable.class,java.util.function.Consumer.class).invoke(second,(Runnable)()->closed.complete(null),(java.util.function.Consumer<Throwable>)closed::completeExceptionally);closed.get(15,TimeUnit.SECONDS);
   System.out.println("Full reload smoke passed: real host socket, isolated session replacement, settings transfer, same synthetic client/JVM, stopped controls and authorization. No Orion process or live input.");
  }finally{
   if(host!=null){Method shutdown=CompanionHost.class.getDeclaredMethod("shutdown");shutdown.setAccessible(true);shutdown.invoke(host);}pump.shutdownNow();SwingUtilities.invokeAndWait(()->{for(Window window:Window.getWindows())window.dispose();});if(previous==null)System.clearProperty("owl.project.root");else System.setProperty("owl.project.root",previous);
   try(java.util.stream.Stream<Path> files=Files.walk(temp)){for(Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}
  }
 }
}
