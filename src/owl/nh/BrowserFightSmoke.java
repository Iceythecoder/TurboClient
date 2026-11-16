package owl.nh;
import java.io.*;
/** Owns only an offline simulator job; used by the browser integration check. */
public final class BrowserFightSmoke {
 public static void main(String[] args)throws Exception {
  try(NHTrainingConnection connection=new NHTrainingConnection(CombatRuntime.projectRoot())) {
   connection.check();UiSmoke.awaitJob(connection);CoreTests.check(connection.connected,"simulator connected");
   System.out.println("SPECTATOR_URI="+connection.browserUri());System.out.flush();
   if(args.length>0&&args[0].equals("--scripted"))connection.watchScripted("fixed-ranged",2,32,400);else connection.evaluate("nh-starter",new File(CombatRuntime.projectRoot(),"build/stage1-checks/models/nh-starter-ppo.zip"),"fixed-ranged",2,32,true,400);
   new BufferedReader(new InputStreamReader(System.in)).readLine();
  }
 }
}
