package owl.nh;
import java.util.*;

/** Bounded real-run experience; rewards are observed associations, never human labels. */
final class OlmRunLearning {
 static final class Transition {
  String source="bot-experience";double[] state;int action,tick,endTick,targetBefore,targetAfter,hpBefore,hpAfter;double reward;boolean assessed;
 }
 private final List<Transition> transitions=new ArrayList<>();private Transition pending;private OlmTargetNetwork network;
 private int frames,startTick=-1,endTick;private boolean active,completed,died,bossDefeated,successRewarded;private int learned;
 void start(){transitions.clear();pending=null;frames=0;startTick=-1;endTick=0;active=true;completed=false;died=false;bossDefeated=false;successRewarded=false;learned=0;}
 void attach(OlmTargetNetwork next){network=next;if(next!=null){for(Transition t:transitions)if(t.assessed)next.outcome(t.state,t.action,t.reward);learned=(int)transitions.stream().filter(t->t.assessed).count();}}
 void clear(){start();active=false;network=null;}
 void requested(OlmStrategy.View s,String target,int tick){if(!active||pending!=null||transitions.size()>=1024)return;Transition t=new Transition();t.state=OlmTargetNetwork.encode(s);t.action=target.equals("magic")?0:target.equals("melee")?1:2;t.tick=tick;t.hpBefore=s.hpPercent;t.targetBefore=t.action==0?s.magicHp:t.action==1?s.meleeHp:-1;pending=t;transitions.add(t);}
 void observe(int tick,int hp,int mage,int melee,int head){if(!active)return;if(tick==endTick&&frames>0)return;if(startTick<0)startTick=tick;endTick=tick;frames++;
  if(pending==null||tick<=pending.tick)return;int after=pending.action==0?mage:pending.action==1?melee:head;
  if(tick-pending.tick<4&&(after<0||pending.targetBefore<0||after>=pending.targetBefore))return;
  Transition t=pending;pending=null;t.endTick=tick;t.hpAfter=hp;t.targetAfter=after;
  if(t.targetBefore<0||after<0||after>t.targetBefore){t.assessed=false;return;}
  t.reward=Math.max(-1,Math.min(1,(t.targetBefore-after)/100.0-Math.max(0,t.hpBefore-hp)/200.0));t.assessed=true;
  if(network!=null){network.outcome(t.state,t.action,t.reward);learned++;}
 }
 void headHealth(int value){if(pending!=null&&pending.action==2&&pending.targetBefore<0)pending.targetBefore=value;}
 void failed(){if(!active||died)return;died=true;if(network!=null){int size=transitions.size();for(int i=Math.max(0,size-8);i<size;i++){Transition t=transitions.get(i);if(t.assessed){network.outcome(t.state,t.action,-.1*Math.pow(.9,size-i-1));learned++;}}}finish(false);}
 void bossDefeated(){if(died||bossDefeated)return;bossDefeated=true;finish(false);success();}
 private void success(){if(successRewarded)return;successRewarded=true;if(network!=null){int size=transitions.size();for(int i=Math.max(0,size-32);i<size;i++){Transition t=transitions.get(i);if(t.assessed){network.outcome(t.state,t.action,.05*Math.pow(.95,size-i-1));learned++;}}}}
 void finish(boolean lootConfirmed){if(died&&lootConfirmed)throw new IllegalStateException("A death attempt cannot be confirmed as a completed fight");if(completed)return;if(!active&&!lootConfirmed)return;active=false;pending=null;completed=lootConfirmed;if(lootConfirmed)success();}
 int samples(){return transitions.size();}
 Map<String,Object> export(){Map<String,Object> out=new LinkedHashMap<>();out.put("source","bot-experience, never manual demonstration");out.put("rewardMeaning","Approximate target health-bar progress minus player HP cost; other actors and mechanics can confound it. Confirmed head death or loot confirmation/death give small terminal reward/penalty, not proof that each action was correct/incorrect.");out.put("startTick",startTick);out.put("endTick",endTick);out.put("frames",frames);out.put("neuralUpdatesThisAttempt",learned);out.put("assessedExperiences",transitions.stream().filter(t->t.assessed).count());out.put("lootConfirmed",completed);out.put("deathObserved",died);out.put("bossDefeated",bossDefeated);out.put("transitions",new ArrayList<>(transitions));return out;}
 String status(){return "Run learning: "+frames+" observed ticks, "+transitions.size()+" target experiences, "+learned+" neural updates"+(completed?"; loot confirmed.":bossDefeated?"; head death observed.":died?"; death observed.":active?"; attempt active.":"; attempt ended.");}
}
