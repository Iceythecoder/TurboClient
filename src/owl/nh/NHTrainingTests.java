package owl.nh;
import java.io.File;

public final class NHTrainingTests {
 public static void main(String[] args)throws Exception {
  try(NHTrainingConnection connection=new NHTrainingConnection(CombatRuntime.projectRoot())) {
   CoreTests.check(connection.installed(),"NH Trainer local installation");connection.check();
   long deadline=System.nanoTime()+30000000000L;while(connection.busy()&&System.nanoTime()<deadline)Thread.sleep(25);
   CoreTests.check(connection.connected&&!connection.busy(),"real NH Trainer bridge check: "+connection.status);
   CoreTests.check(connection.details.contains("114")&&connection.details.contains("600 ms"),"actual simulator schema/timing reported");
   CoreTests.check(connection.details.contains("NH starter inventory only"),"default restricted NH setup");
   boolean rejected=false;try{connection.train("PPO","fixed-ranged",0,32,new File("invalid.zip"));}catch(IllegalArgumentException expected){rejected=true;}CoreTests.check(rejected,"invalid training job rejected");
   connection.check();connection.cancel();deadline=System.nanoTime()+10000000000L;while(connection.busy()&&System.nanoTime()<deadline)Thread.sleep(25);CoreTests.check(!connection.busy(),"connection job cancellation");
  }
  System.out.println("NH Trainer Java/Python bridge checks passed; no Orion input or public-site session used.");
 }
}
