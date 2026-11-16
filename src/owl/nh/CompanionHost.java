package owl.nh;
import com.google.inject.Injector;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Stable, loopback-only reload host. Never restarts or attaches to another JVM. */
public final class CompanionHost {
 static final String ENDPOINT="runtime/companion-session.txt"; // legacy/default endpoint
 private final Path root;private final Injector injector;private final ServerSocket server;
 private final String endpoint;
 private final String token;private Object session;private ReleaseLoader loader;private String carriedState="";
 private final ExecutorService requests=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"owl-companion-reload");t.setDaemon(true);return t;});
 private CompanionHost(Path root,Injector injector)throws IOException{
  this.root=root.toAbsolutePath().normalize();this.injector=injector;
  endpoint=safeRelativeProperty("owl.companion.session",ENDPOINT);
  byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
  server=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
 }
 public static CompanionHost start(Injector injector,Runnable ready,Consumer<Throwable> failed)throws IOException{
  CompanionHost host=new CompanionHost(Paths.get(System.getProperty("owl.project.root",".")),injector);
  host.publish();Runtime.getRuntime().addShutdownHook(new Thread(host::shutdown,"owl-reload-host-stop"));
  Thread accept=new Thread(host::accept,"owl-reload-accept");accept.setDaemon(true);accept.start();
  host.requests.execute(()->{try{host.reload();ready.run();}catch(Throwable e){failed.accept(e);}});return host;
 }
 private void publish()throws IOException{
  Path file=root.resolve(endpoint);Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),".session-",".tmp");
  try{Files.writeString(temp,"OWL_RELOAD_V1\n"+ProcessHandle.current().pid()+"\n"+server.getLocalPort()+"\n"+token+"\n",StandardCharsets.UTF_8);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
  finally{Files.deleteIfExists(temp);}
 }
 static Path selected(Path root)throws IOException{
  return selected(root,safeRelativeProperty("owl.release.pointer","release-current.txt"));
 }
 static Path selected(Path root,String pointer)throws IOException{
  Path base=root.toAbsolutePath().normalize();Path marker=base.resolve(pointer).normalize();if(!marker.startsWith(base)||!Files.isRegularFile(marker))throw new IOException("Invalid companion release pointer");String value=Files.readString(marker,StandardCharsets.UTF_8).trim();Path jar=base.resolve(value).normalize();
  if(!jar.startsWith(base)||!Files.isRegularFile(jar)||!jar.getFileName().toString().endsWith(".jar"))throw new IOException("Invalid companion release selection");return jar;
 }
 private static String safeRelativeProperty(String property,String fallback){String value=System.getProperty(property,fallback).replace('\\','/').trim();Path path=Paths.get(value).normalize();if(path.isAbsolute()||value.isEmpty()||value.contains(":" )||path.startsWith(".."))throw new IllegalArgumentException("Invalid local path property: "+property);return path.toString();}
 private void accept(){while(!server.isClosed())try{Socket socket=server.accept();socket.setSoTimeout(3000);requests.execute(()->handle(socket));}catch(IOException|RejectedExecutionException ignored){if(server.isClosed())return;}}
 private void handle(Socket socket){try(Socket s=socket){BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));String line=boundedLine(in);PrintWriter out=new PrintWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8),true);
  if(!("RELOAD "+token).equals(line)){out.println("ERROR unauthorized reload request");return;}
  try{reload();out.println("OK companion reloaded; Orion session retained");}catch(Throwable e){out.println("ERROR companion reload failed; Orion is still running: "+cause(e).getClass().getSimpleName()+" - "+String.valueOf(cause(e).getMessage()).replaceAll("[\\r\\n]"," "));}
 }catch(IOException ignored){}}
 static String boundedLine(Reader in)throws IOException{StringBuilder line=new StringBuilder();for(int i=0;i<256;i++){int c=in.read();if(c<0||c=='\n')return line.toString();if(c!='\r')line.append((char)c);}throw new IOException("Reload request too long");}
 private void reload()throws Exception{
  Path jar=selected(root);ReleaseLoader next=new ReleaseLoader(jar.toUri().toURL(),CompanionHost.class.getClassLoader());
  Class<?> entry;try{entry=next.loadClass("owl.nh.CompanionSession");entry.getMethod("open",Injector.class,String.class,Runnable.class,Consumer.class);}catch(Throwable e){next.close();throw new IOException("Release is not compatible with companion reload",e);}
  try{
   if(session!=null){await((ok,bad)->{try{session.getClass().getMethod("close",Runnable.class,Consumer.class).invoke(session,ok,bad);}catch(Throwable e){bad.accept(cause(e));}});carriedState=(String)session.getClass().getMethod("state").invoke(session);session=null;loader.close();loader=null;}
   final Object[] opened={null};try{await((ok,bad)->{try{opened[0]=entry.getMethod("open",Injector.class,String.class,Runnable.class,Consumer.class).invoke(null,injector,carriedState,ok,bad);}catch(Throwable e){bad.accept(cause(e));}});}catch(Exception error){if(opened[0]!=null)try{Object failed=opened[0];await((ok,bad)->{try{failed.getClass().getMethod("close",Runnable.class,Consumer.class).invoke(failed,ok,bad);}catch(Throwable e){bad.accept(cause(e));}});}catch(Exception ignored){}throw error;}
   session=opened[0];loader=next;carriedState="";
  }finally{if(loader!=next)next.close();}
 }
 private interface Action {void run(Runnable ok,Consumer<Throwable> bad);}
 private static void await(Action action)throws Exception{CompletableFuture<Void> done=new CompletableFuture<>();action.run(()->done.complete(null),done::completeExceptionally);done.get(30,TimeUnit.SECONDS);}
 private static Throwable cause(Throwable e){while((e instanceof java.lang.reflect.InvocationTargetException||e instanceof ExecutionException)&&e.getCause()!=null)e=e.getCause();return e;}
 private void shutdown(){try{server.close();}catch(IOException ignored){}requests.shutdownNow();if(loader!=null)try{loader.close();}catch(IOException ignored){}Path file=root.resolve(endpoint);try{if(Files.isRegularFile(file)&&Files.readString(file).contains(token))Files.deleteIfExists(file);}catch(IOException ignored){}}
 static final class ReleaseLoader extends URLClassLoader {
  ReleaseLoader(URL jar,ClassLoader parent){super(new URL[]{jar},parent);}
  protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
   if(!name.startsWith("owl.nh.")||name.equals(CompanionHost.class.getName())||name.startsWith(CompanionHost.class.getName()+"$")||name.equals(Bootstrap.class.getName()))return super.loadClass(name,resolve);
   Class<?> found=findLoadedClass(name);if(found==null)found=findClass(name);if(resolve)resolveClass(found);return found;
  }
 }
}
