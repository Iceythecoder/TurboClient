package owl.nh;

import java.util.*;
import java.util.function.LongSupplier;

/** Pure combat logic. No Orion or screen-coordinate dependencies. */
public final class CombatCore {
 public enum Style { MAGIC, RANGED, MELEE, SPEC, UNKNOWN }
 public enum Protection { NONE, MAGIC, RANGED, MELEE, UNKNOWN }
 public enum Phase { IDLE, ANALYZE, SWITCH_GEAR, SWITCH_PRAYER, SELECT_SPELL, ENABLE_SPEC, EXECUTE_ATTACK, VERIFY, COOLDOWN, FAILED }
 public enum Kind { EQUIP, OFFENSIVE_PRAYER, DEFENSIVE_PRAYER, SELECT_BARRAGE, ENABLE_SPEC, ATTACK }
 public static final class TickManager {
  private final LongSupplier clock; public int tick=-1; public long start; private boolean synced;private long periodMs=600;private final ArrayDeque<Long> intervals=new ArrayDeque<>();
  public TickManager(LongSupplier clock) { this.clock=clock; }
  public void onTick(int number) {long now=clock.getAsLong();if(synced&&number==tick)return;if(synced&&number>tick){long interval=(now-start)/1000000L/(number-tick);if(interval>=200&&interval<=1200){if(intervals.size()==8)intervals.removeFirst();intervals.addLast(interval);if(intervals.size()>=3){List<Long> sorted=new ArrayList<>(intervals);Collections.sort(sorted);periodMs=sorted.get(sorted.size()/2);}}}else if(synced){intervals.clear();periodMs=600;}tick=number;start=now;synced=true;}
  public long elapsedMs() { return (clock.getAsLong()-start)/1000000L; }
  public long remainingMs() { return synced ? Math.max(0,periodMs-elapsedMs()) : 0; }
  public long observedPeriodMs(){return periodMs;}
  public long deadline(int windows) { if(!synced)throw new IllegalStateException("No GameTick received");return start+windows*periodMs*1000000L; }
  public long now() { return clock.getAsLong(); }
  public boolean fresh() { return synced && elapsedMs()>=0&&elapsedMs()<Math.max(1200,periodMs*2); }
 }
 public static final class TargetManager {
  public Object actor; public int index=-1; public long generation; public Protection lastPrayer=Protection.UNKNOWN;
  public void lock(Object actor,int index) { if(actor!=this.actor) {this.actor=actor;this.index=index;generation++;lastPrayer=Protection.UNKNOWN;} }
  public void clear() { lock(null,-1); }
  public boolean matches(Object actor,int index,long generation) { return this.actor==actor && this.index==index && this.generation==generation && actor!=null; }
  public boolean prayerChanged(Protection prayer) { boolean changed=prayer!=lastPrayer;lastPrayer=prayer;return changed && prayer!=Protection.UNKNOWN; }
 }
 public static final class State {
  public Object target;public int targetIndex,tick,distance,opponentHpPercent=-1,localHp,prayerPoints,specEnergy;
  public long generation;public Protection protection=Protection.UNKNOWN;public Style intent=Style.UNKNOWN;
  public boolean valid,canBarrage,moving;public Boolean canMove=null; public Set<Integer> equipped=new HashSet<>(),inventory=new HashSet<>();
 }
 public static final class Settings {
  public EnumMap<Style,List<Integer>> gear=new EnumMap<>(Style.class);
  public EnumMap<Style,String> offensive=new EnumMap<>(Style.class);
  public EnumMap<Protection,Style> counters=new EnumMap<>(Protection.class);
  public EnumMap<Style,Integer> speeds=new EnumMap<>(Style.class);
  public Map<Integer,Style> enemyWeapons=new HashMap<>(),enemyAnimations=new HashMap<>();
  public Map<Integer,Integer> attackAnimations=new HashMap<>(),freezeGraphics=new HashMap<>();
  public EnumSet<Style> enabled=EnumSet.of(Style.MAGIC,Style.RANGED,Style.MELEE);
  public boolean offensiveEnabled,defensiveEnabled,specEnabled,freezeEnabled,autoAttack;
  public boolean autoLock=true;
  public int deadlineTicks=1,minRemainingMs=100,refreezeTicks=3,specHp=30,minSpec=50;
  public Style specStyle=Style.MELEE;
  public Settings() {
   for(Style s:new Style[]{Style.MAGIC,Style.RANGED,Style.MELEE,Style.SPEC})gear.put(s,new ArrayList<>());
   offensive.put(Style.MAGIC,"AUGURY");offensive.put(Style.RANGED,"RIGOUR");offensive.put(Style.MELEE,"PIETY");
   counters.put(Protection.RANGED,Style.MAGIC);counters.put(Protection.MAGIC,Style.RANGED);counters.put(Protection.MELEE,Style.MAGIC);counters.put(Protection.NONE,Style.RANGED);
   speeds.put(Style.MAGIC,5);speeds.put(Style.RANGED,4);speeds.put(Style.MELEE,4);speeds.put(Style.SPEC,4);
  }
 }
 public static final class Action {
  public final Kind kind;public final int item,priority;public final String value;
  public Action(Kind k,int item,String value,int priority) {kind=k;this.item=item;this.value=value;this.priority=priority;}
 }
 public static final class Decision {
  public Style style;public final List<Action> actions=new ArrayList<>();public String reason;
 }
 public static final class GearManager {
  public List<Integer> required(Settings config,State state,Style style) {
   List<Integer> configured=config.gear.get(style);if(configured==null || configured.isEmpty())throw new IllegalStateException("Capture "+style+" equipment first");
   List<Integer> switches=new ArrayList<>();Set<Integer> seen=new HashSet<>();
   for(int id:configured) {if(id<=0||!seen.add(id))throw new IllegalStateException("Invalid/duplicate gear item");if(!state.equipped.contains(id)){if(!state.inventory.contains(id))throw new IllegalStateException("Configured item missing: "+id);switches.add(id);}}
   return switches;
  }
 }
 public static final class FreezeManager {
  private final Map<Object,Integer> expiry=new IdentityHashMap<>();
  public void observe(Object actor,int tick,int duration) {if(duration>0)expiry.put(actor,tick+duration);}
  public int remaining(Object actor,int tick) {return Math.max(0,expiry.getOrDefault(actor,tick)-tick);}
  public void clear() {expiry.clear();}
 }
 public static final class AttackManager {
  public int nextTick=-1,lastTick=-1;public boolean awaiting;public long submittedAt;
  public boolean ready(int tick) {return !awaiting && tick>=nextTick;}
  public void submitted(int tick,int speed,long now) {lastTick=tick;nextTick=tick+speed;awaiting=true;submittedAt=now;}
  public void observed(int tick,int speed) {lastTick=tick;nextTick=tick+speed;awaiting=false;}
  public void reset() {nextTick=-1;lastTick=-1;awaiting=false;}
 }
 public static final class SpecialAttackManager {
  public boolean eligible(Settings c,State s) {return c.specEnabled && s.opponentHpPercent>=0 && s.opponentHpPercent<=c.specHp && s.specEnergy>=c.minSpec && (c.specStyle!=Style.MAGIC || s.canBarrage) && (c.specStyle!=Style.MELEE || s.distance<=1) && s.protection!=Protection.valueOf(c.specStyle.name());}
 }
 public static final class DecisionEngine {
  private final GearManager gear=new GearManager();private final SpecialAttackManager spec=new SpecialAttackManager();
  public Decision evaluate(Settings c,State s,AttackManager attacks,FreezeManager freezes) {
   return evaluate(c,s,attacks,freezes,null);
  }
  public Decision evaluateForced(Settings c,State s,AttackManager attacks,FreezeManager freezes,Style requested) {
   if(requested==null||requested==Style.UNKNOWN||requested==Style.SPEC)return null;
   return evaluate(c,s,attacks,freezes,requested);
  }
  private Decision evaluate(Settings c,State s,AttackManager attacks,FreezeManager freezes,Style forced) {
   if(!s.valid||s.protection==Protection.UNKNOWN)return null;
   Style preferred=c.counters.get(s.protection),selected=null;int best=Integer.MIN_VALUE;
   for(Style style:new Style[]{Style.MAGIC,Style.RANGED,Style.MELEE}) {
    if(forced!=null&&style!=forced)continue;
    if(!c.enabled.contains(style) || (style==Style.MAGIC&&!s.canBarrage) || (style==Style.MELEE&&s.distance>1) || s.protection==Protection.valueOf(style.name()))continue;
    try {int count=gear.required(c,s,style).size();int score=100-count*3+(style==preferred?30:0);
     if(c.freezeEnabled&&style==Style.MAGIC&&freezes.remaining(s.target,s.tick)<=c.refreezeTicks)score+=15;
     if(score>best){best=score;selected=style;}
    }catch(IllegalStateException unavailable) { /* An unavailable setup cannot win. */ }
   }
   if(selected==null)return null;
   Decision d=new Decision();d.style=selected;boolean useSpec=spec.eligible(c,s);
   if(useSpec)try {gear.required(c,s,Style.SPEC);d.style=Style.SPEC;}catch(IllegalStateException missing){useSpec=false;}
   Style attackStyle=useSpec?c.specStyle:selected;
   if(c.defensiveEnabled&&s.intent!=Style.UNKNOWN)d.actions.add(new Action(Kind.DEFENSIVE_PRAYER,0,"PROTECT_FROM_"+(s.intent==Style.RANGED?"MISSILES":s.intent.name()),100));
   for(int id:gear.required(c,s,d.style))d.actions.add(new Action(Kind.EQUIP,id,null,70));
   if(c.offensiveEnabled)d.actions.add(new Action(Kind.OFFENSIVE_PRAYER,0,c.offensive.get(attackStyle),60));
   if(useSpec)d.actions.add(new Action(Kind.ENABLE_SPEC,0,null,50));
   if(attackStyle==Style.MAGIC)d.actions.add(new Action(Kind.SELECT_BARRAGE,0,null,50));
   if(c.autoAttack&&attacks.ready(s.tick))d.actions.add(new Action(Kind.ATTACK,0,attackStyle.name(),40));
   d.reason="Counter "+s.protection+"; score "+best;return d;
  }
 }
 public interface OrionClientAdapter {
  State snapshot(TargetManager target);boolean valid(State lock);boolean ready(Action action,Decision decision);
  void perform(Action action,State lock);boolean gearConfirmed(Style style);boolean attackConfirmed(State lock,int sinceTick);
 }
 public static final class TelemetryManager {
  private final ArrayDeque<String> lines=new ArrayDeque<>();public Phase phase=Phase.IDLE;public String result="Waiting for a target";
  public void event(TickManager ticks,String text) {String line="T"+ticks.tick+" +"+ticks.elapsedMs()+"ms "+text;lines.addLast(line);while(lines.size()>80)lines.removeFirst();result=text;}
  public List<String> lines() {return new ArrayList<>(lines);}
 }
 public static final class ActionScheduler {
  public final TickManager ticks;public final TelemetryManager telemetry;public Decision decision;public State lock;
  private final OrionClientAdapter adapter;private final AttackManager attacks;private Settings settings;private int cursor;private long deadline;private boolean submitted;
  public ActionScheduler(TickManager t,TelemetryManager telemetry,OrionClientAdapter adapter,AttackManager attacks){ticks=t;this.telemetry=telemetry;this.adapter=adapter;this.attacks=attacks;}
  public boolean busy(){return decision!=null;}
  public void cancel(String reason){if(decision!=null)telemetry.event(ticks,"Aborted: "+reason);decision=null;lock=null;submitted=false;telemetry.phase=Phase.IDLE;}
  public void begin(Decision decision,State state,Settings settings) {
   if(busy())throw new IllegalStateException("Sequence already active");
   if(ticks.remainingMs()<settings.minRemainingMs) {telemetry.event(ticks,"Skipped: insufficient tick budget");return;}
   this.decision=decision;lock=state;this.settings=settings;cursor=0;deadline=ticks.deadline(settings.deadlineTicks);submitted=false;telemetry.phase=Phase.ANALYZE;telemetry.event(ticks,"Decision: "+decision.style+" / "+decision.reason);
  }
  public void advance() {
   if(!busy())return;
   try {
    if(ticks.now()>=deadline){telemetry.phase=Phase.FAILED;telemetry.event(ticks,"Deadline exceeded; sequence cancelled (partial gear may remain)");decision=null;lock=null;return;}
    if(!adapter.valid(lock)){cancel("Target/focus/state changed");return;}
    while(cursor<decision.actions.size()) {
     if(ticks.now()>=deadline || !adapter.valid(lock)){cancel("Action precondition expired");return;}
     Action a=decision.actions.get(cursor);
     if(a.kind==Kind.ATTACK && !adapter.gearConfirmed(decision.style)){telemetry.phase=Phase.VERIFY;return;}
     if(!adapter.ready(a,decision))return;
     telemetry.phase=a.kind==Kind.EQUIP?Phase.SWITCH_GEAR:a.kind==Kind.SELECT_BARRAGE?Phase.SELECT_SPELL:a.kind==Kind.ENABLE_SPEC?Phase.ENABLE_SPEC:a.kind==Kind.ATTACK?Phase.EXECUTE_ATTACK:Phase.SWITCH_PRAYER;
     adapter.perform(a,lock);telemetry.event(ticks,"Submitted "+a.kind+(a.item>0?" item "+a.item:""));cursor++;
     if(a.kind==Kind.ATTACK){submitted=true;attacks.submitted(ticks.tick,settings.speeds.get(decision.style),ticks.now());}
     // Yield to the next client state event so interaction helpers cannot overwrite a burst of pending actions.
     if(cursor<decision.actions.size())return;
    }
    if(!adapter.gearConfirmed(decision.style)){telemetry.phase=Phase.VERIFY;return;}
    if(submitted&&!adapter.attackConfirmed(lock,attacks.lastTick)){telemetry.phase=Phase.VERIFY;return;}
    telemetry.phase=submitted?Phase.COOLDOWN:Phase.IDLE;telemetry.event(ticks,"Verified "+decision.style+"; same observed tick: "+(ticks.tick==lock.tick));decision=null;lock=null;
   }catch(RuntimeException e){telemetry.phase=Phase.FAILED;telemetry.event(ticks,"Failed: "+e.getClass().getSimpleName()+" / "+e.getMessage());decision=null;lock=null;}
  }
 }
 private CombatCore() {}
}
