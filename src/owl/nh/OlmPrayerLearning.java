package owl.nh;

import java.util.*;
import net.runelite.api.Prayer;

/** Bounded timing evidence. Estimated flight completion is not proof of a protected hit. */
final class OlmPrayerLearning {
 static final class Flight {
  long uid;int projectileId,launchCycle,firstSeen,impactCycle,requestCycle=-1,activeCycle=-1;
  int hpAtDetection;double x,y,z,classificationConfidence=-1;String prayer,classification="CONFIGURED_UNVERIFIED",source="UNAVAILABLE",target="LOCAL_PLAYER";
  boolean timely,late,observed,overlap;String result="IN_FLIGHT";
 }
 static final class Checkpoint {
  int version=1,marginCycles=5,ackSamples,ackMaxCycles;
  long tracked,timely,late,unobserved,unknown,conflicts,damageEvents,damageAmount;
 }
 private final ArrayDeque<Flight> resolved=new ArrayDeque<>();
 private Checkpoint stats=new Checkpoint();private long sequence;private int cleanStreak;
 Flight flight(int id,int launch,int seen,int end,Prayer prayer,int hp,double x,double y,double z){
  Flight f=new Flight();f.uid=++sequence;f.projectileId=id;f.launchCycle=launch;f.firstSeen=seen;f.impactCycle=end;f.hpAtDetection=hp;f.x=x;f.y=y;f.z=z;f.prayer=prayer==null?null:prayer.name();if(prayer==null){f.classification="UNKNOWN";stats.unknown++;}stats.tracked++;return f;
 }
 void observe(Flight f,int cycle,boolean active){
  if(f.prayer==null||cycle>f.impactCycle)return;
  f.observed=true;
  if(active){if(f.activeCycle<0)f.activeCycle=cycle;if(cycle<f.impactCycle)f.timely=true;}
  else if(cycle>=f.impactCycle-margin())f.late=true;
  // Keep the latest sampled state, so lightning/prayer changes invalidate an earlier activation.
  if(cycle==f.impactCycle-1)f.result=active?"ACTIVE_NEAR_ESTIMATED_IMPACT":"INACTIVE_NEAR_ESTIMATED_IMPACT";
  if(cycle==f.impactCycle&&!active)f.result="INACTIVE_NEAR_ESTIMATED_IMPACT";
 }
 void requested(Flight f,int cycle){if(f.requestCycle<0)f.requestCycle=cycle;}
 void acknowledged(int latency){if(latency<0||latency>150)return;stats.ackSamples++;stats.ackMaxCycles=Math.max(stats.ackMaxCycles,latency);stats.marginCycles=Math.max(stats.marginCycles,Math.min(50,latency+2));}
 void finish(Flight f,int cycle,boolean cancelled){
  if(cancelled)f.result="CANCELLED_OR_TARGET_CHANGED";
  else if(f.prayer==null)f.result="UNKNOWN_CLASSIFICATION";
  else if(cycle-f.impactCycle>2||!f.result.endsWith("NEAR_ESTIMATED_IMPACT")){f.result="IMPACT_NOT_OBSERVED";stats.unobserved++;}
  else if(f.result.equals("ACTIVE_NEAR_ESTIMATED_IMPACT")&&f.timely){stats.timely++;if(++cleanStreak>=20){stats.marginCycles=Math.max(Math.min(50,Math.max(2,stats.ackMaxCycles+2)),stats.marginCycles-1);cleanStreak=0;}}
  else {stats.late++;cleanStreak=0;stats.marginCycles=Math.min(50,stats.marginCycles+2);}
  if(f.overlap)stats.conflicts++;
  if(resolved.size()==128)resolved.removeFirst();resolved.addLast(f);
 }
 void damage(int amount){if(amount>0){stats.damageEvents++;stats.damageAmount+=amount;}}
 int margin(){return stats.marginCycles;}
 String status(){return "Prayer timing: "+stats.timely+" active near estimated impact, "+stats.late+" inactive, "+stats.unobserved+" unobserved; unknown "+stats.unknown+". Learned safety margin "+margin()+" cycles; ack samples "+stats.ackSamples+". Damage events "+stats.damageEvents+" (cause unassigned). Protection efficacy UNVERIFIED.";}
 String death(){return "Death observed. Recent prayer timing: "+status()+". No single cause assigned without a server hit-to-projectile link.";}
 Checkpoint checkpoint(){Checkpoint copy=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(stats),Checkpoint.class);return copy;}
 List<Flight> outcomes(){return new ArrayList<>(resolved);}
 void restore(Checkpoint c){
  if(c==null||c.version!=1||c.marginCycles<1||c.marginCycles>50||c.ackSamples<0||c.ackMaxCycles<0||c.ackMaxCycles>150||c.tracked<0||c.timely<0||c.late<0||c.unobserved<0||c.unknown<0||c.conflicts<0||c.damageEvents<0||c.damageAmount<0)throw new IllegalArgumentException("Invalid prayer timing checkpoint");
  stats=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(c),Checkpoint.class);resolved.clear();cleanStreak=0;sequence=stats.tracked;
 }
 void clear(){stats=new Checkpoint();resolved.clear();sequence=0;cleanStreak=0;}
 static void saveNew(java.nio.file.Path target,String json)throws java.io.IOException{
  boolean created=false,complete=false;try{java.nio.file.Files.createFile(target);created=true;java.nio.file.Files.write(target,json.getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.file.StandardOpenOption.WRITE);complete=true;}finally{if(created&&!complete)java.nio.file.Files.deleteIfExists(target);}
 }
}
