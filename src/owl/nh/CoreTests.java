package owl.nh;
import java.util.*;
import static owl.nh.CombatCore.*;
public final class CoreTests {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args) {
  long[] now={1000000000L};TickManager ticks=new TickManager(()->now[0]);check(ticks.remainingMs()==0,"unsynced clock");ticks.onTick(10);now[0]+=123000000L;check(ticks.remainingMs()==477,"tick budget");
  TargetManager target=new TargetManager();Object actor=new Object();target.lock(actor,7);long generation=target.generation;check(target.matches(actor,7,generation),"identity lock");check(target.prayerChanged(Protection.RANGED),"first prayer");check(!target.prayerChanged(Protection.RANGED),"prayer duplicate");check(target.prayerChanged(Protection.MAGIC),"prayer edge");target.lock(new Object(),7);check(!target.matches(actor,7,generation),"index reuse");
  Settings config=new Settings();config.gear.get(Style.MAGIC).addAll(Arrays.asList(101,102));config.gear.get(Style.RANGED).add(201);config.gear.get(Style.MELEE).add(301);
  State state=new State();state.valid=true;state.target=actor;state.tick=10;state.protection=Protection.RANGED;state.canBarrage=true;state.distance=4;state.inventory.addAll(Arrays.asList(102,201,301));state.equipped.add(101);
  GearManager gear=new GearManager();check(gear.required(config,state,Style.MAGIC).equals(Arrays.asList(102)),"minimal switches");state.inventory.remove(102);boolean missing=false;try{gear.required(config,state,Style.MAGIC);}catch(IllegalStateException e){missing=true;}check(missing,"missing gear");state.inventory.add(102);
  AttackManager attacks=new AttackManager();FreezeManager freeze=new FreezeManager();Decision d=new DecisionEngine().evaluate(config,state,attacks,freeze);check(d.style==Style.MAGIC,"range protection counter");check(d.actions.stream().noneMatch(a->a.kind==Kind.ATTACK),"manual click default");config.autoAttack=true;d=new DecisionEngine().evaluate(config,state,attacks,freeze);check(d.actions.stream().filter(a->a.kind==Kind.ATTACK).count()==1,"one attack action");
  attacks.submitted(10,5,now[0]);check(!attacks.ready(15),"confirmation required");attacks.observed(11,5);check(!attacks.ready(15)&&attacks.ready(16),"attack cooldown");attacks.reset();
  freeze.observe(actor,10,20);check(freeze.remaining(actor,15)==15,"freeze estimate");check(freeze.remaining(actor,31)==0,"freeze expiry");
  boolean[] valid={true},equipped={false};List<Kind> sent=new ArrayList<>();
  OrionClientAdapter fake=new OrionClientAdapter(){public State snapshot(TargetManager t){return state;}public boolean valid(State s){return valid[0];}public boolean ready(Action a,Decision d){return true;}public void perform(Action a,State s){sent.add(a.kind);}public boolean gearConfirmed(Style s){return equipped[0];}public boolean attackConfirmed(State s,int t){return true;}};
  TelemetryManager telemetry=new TelemetryManager();ActionScheduler scheduler=new ActionScheduler(ticks,telemetry,fake,attacks);scheduler.begin(d,state,config);scheduler.advance();check(sent.size()==1,"one action per state event");scheduler.advance();scheduler.advance();check(!sent.contains(Kind.ATTACK),"attack waits for gear");equipped[0]=true;for(int i=0;i<5;i++)scheduler.advance();check(sent.stream().filter(k->k==Kind.ATTACK).count()==1,"single attack");scheduler.advance();check(sent.stream().filter(k->k==Kind.ATTACK).count()==1,"no duplicate attack");
  scheduler.begin(d,state,config);valid[0]=false;scheduler.advance();check(!scheduler.busy(),"target loss cancellation");valid[0]=true;scheduler.begin(d,state,config);now[0]+=700000000L;scheduler.advance();check(!scheduler.busy(),"deadline cancellation");
  System.out.println("Core checks passed: ticks, target identity, prayer edges, gear, decisions, cooldown, freeze, scheduler.");
 }
}
