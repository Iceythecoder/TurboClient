package owl.nh;
import java.io.*;
import static owl.nh.CombatCore.*;
public final class LearningTests {
 public static void main(String[] args)throws Exception {
  Settings config=new Settings();KnowledgePrompt.Preview imported=KnowledgePrompt.parse("weapon 1234 = RANGED\nenemy animation 8 = MAGIC\nattack animation 9 = 5\nfreeze graphic 10 = 20\ncounter RANGED = MAGIC\nmagic prayer = AUGURY\nnote: context only",config);
  CoreTests.check(imported.applied.size()==6&&imported.notes.size()==1,"prompt interpretation");CoreTests.check(config.enemyWeapons.isEmpty(),"prompt preview cannot mutate active settings");CoreTests.check(imported.settings.enemyWeapons.get(1234)==Style.RANGED,"weapon knowledge import");
  CoreTests.check(!KnowledgePrompt.parse("attack animation 9 = 0\nrun powershell\nlearn to eat",config).unrecognized.isEmpty(),"unsupported prompt rejection");
  State state=new State();state.tick=12;state.valid=true;state.generation=3;state.protection=Protection.RANGED;state.canBarrage=true;state.distance=4;state.localHp=80;state.prayerPoints=60;state.opponentHpPercent=-1;state.inventory.add(100);config.gear.get(Style.MAGIC).add(100);
  double[] vector=LearningAgent.encode(state,config,new AttackManager());CoreTests.check(vector.length==10&&vector[0]==.5&&vector[3]==-1&&vector[7]==1,"policy observation contract");
  DecisionEngine engine=new DecisionEngine();CoreTests.check(engine.evaluateForced(config,state,new AttackManager(),new FreezeManager(),Style.RANGED)==null,"illegal model choice veto");CoreTests.check(engine.evaluateForced(config,state,new AttackManager(),new FreezeManager(),Style.MAGIC)!=null,"legal model choice admitted");
  LearningAgent.Prediction prediction=new LearningAgent.Prediction(Style.MAGIC,state,1_000_000_000L);CoreTests.check(prediction.matches(state,1_100_000_000L),"fresh prediction");CoreTests.check(!prediction.matches(state,1_700_000_000L),"stale prediction rejected");state.generation++;CoreTests.check(!prediction.matches(state,1_100_000_000L),"target generation rejected");state.generation--;
  File root=CombatRuntime.projectRoot();for(String filename:new String[]{"ppo-demo.zip","recurrent-ppo-demo.zip"}) {
   try(LearningAgent agent=new LearningAgent(root)) {
    CoreTests.check(agent.installed(),"local RL runtime installed");agent.load(new File(root,"learning/models/"+filename));
    long deadline=System.nanoTime()+30000000000L;while(!agent.ready&&!agent.status.startsWith("Load failed")&&System.nanoTime()<deadline)Thread.sleep(50);
    CoreTests.check(agent.ready,"policy load: "+agent.status);CoreTests.check(!agent.liveEligible,"toy policy cannot control Orion");boolean blocked=false;try{agent.activate(LearningAgent.Mode.CONTROL);}catch(IllegalStateException expected){blocked=true;}CoreTests.check(blocked,"demo control gate");
    agent.activate(LearningAgent.Mode.SHADOW);CoreTests.check(agent.acceptingRequests(),"ready policy accepts its first request");agent.request(state,config,new AttackManager());deadline=System.nanoTime()+5000000000L;while(agent.prediction==null&&agent.mode!=LearningAgent.Mode.DISABLED&&System.nanoTime()<deadline)Thread.sleep(20);
    CoreTests.check(agent.prediction!=null,"real Python inference: "+agent.status);CoreTests.check(agent.prediction.generation==state.generation&&agent.prediction.tick==state.tick,"inference tagging");agent.resetContext();CoreTests.check(agent.prediction==null,"reset clears suggestions");agent.deactivate();CoreTests.check(agent.mode==LearningAgent.Mode.DISABLED,"agent deactivate");
   }
  }
  System.out.println("Learning checks passed: both Python policies, prompt import, model gates, stale-state rejection and shutdown.");
 }
}
