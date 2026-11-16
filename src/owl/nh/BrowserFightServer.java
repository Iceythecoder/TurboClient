package owl.nh;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only spectator, bound to loopback with an unguessable per-session path. */
final class BrowserFightServer implements AutoCloseable {
 private final HttpServer server;private final ExecutorService executor;
 private final Path assets,dist;private final String prefix;private final NHTrainingConnection connection;
 BrowserFightServer(File root,NHTrainingConnection connection)throws IOException {
  this.connection=connection;Path source=new File(root,"learning/nh-trainer/browser-source").toPath();assets=source.resolve("fixtures").toRealPath();dist=source.resolve("spectator-dist").toRealPath();
  if(!Files.isRegularFile(dist.resolve("index.html")))throw new IOException("Browser spectator build is missing");
  prefix="/"+UUID.randomUUID().toString()+"/";server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),0);executor=Executors.newFixedThreadPool(3,LearningAgent.daemon("nh-spectator-http"));server.setExecutor(executor);server.createContext("/",this::handle);server.start();
 }
 URI uri(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+prefix);}
 private void handle(HttpExchange exchange) {
  try {
   String host=exchange.getRequestHeaders().getFirst("Host");if(!("127.0.0.1:"+server.getAddress().getPort()).equals(host)){reply(exchange,403,"text/plain","Invalid host".getBytes(StandardCharsets.UTF_8));return;}
   if(!exchange.getRequestMethod().equals("GET")){reply(exchange,405,"text/plain",new byte[0]);return;}
   String path=exchange.getRequestURI().getPath();if(!path.startsWith(prefix)){reply(exchange,404,"text/plain",new byte[0]);return;}
   String relative=path.substring(prefix.length());headers(exchange);
   if(relative.equals("api/frame")){reply(exchange,200,"application/json",connection.browserSnapshot().getBytes(StandardCharsets.UTF_8));return;}
   if(relative.isEmpty())relative="index.html";
   Path base=relative.startsWith("render/")||relative.startsWith("external-items/")||relative.startsWith("assets/defs/")||relative.startsWith("assets/models/")||relative.startsWith("assets/animations/")?assets:dist;
   Path file=base.resolve(relative).normalize();if(!file.startsWith(base)||!Files.isRegularFile(file)||!file.toRealPath().startsWith(base)){reply(exchange,404,"text/plain",new byte[0]);return;}
   exchange.getResponseHeaders().set("Content-Type",mime(file));exchange.sendResponseHeaders(200,Files.size(file));try(InputStream in=Files.newInputStream(file);OutputStream out=exchange.getResponseBody()){in.transferTo(out);}
  }catch(IOException ignored){/* Browser disconnects never affect training. */}finally{exchange.close();}
 }
 private void headers(HttpExchange e){e.getResponseHeaders().set("Cache-Control","no-store");e.getResponseHeaders().set("X-Content-Type-Options","nosniff");e.getResponseHeaders().set("Referrer-Policy","no-referrer");e.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'");}
 private void reply(HttpExchange e,int code,String type,byte[] body)throws IOException {headers(e);e.getResponseHeaders().set("Content-Type",type);e.sendResponseHeaders(code,body.length);try(OutputStream out=e.getResponseBody()){out.write(body);}}
 private static String mime(Path file){String name=file.getFileName().toString();if(name.endsWith(".html"))return "text/html; charset=utf-8";if(name.endsWith(".js"))return "text/javascript; charset=utf-8";if(name.endsWith(".css"))return "text/css; charset=utf-8";if(name.endsWith(".json"))return "application/json";if(name.endsWith(".png"))return "image/png";if(name.endsWith(".glb"))return "model/gltf-binary";return "application/octet-stream";}
 @Override public void close(){server.stop(0);executor.shutdownNow();}
}
