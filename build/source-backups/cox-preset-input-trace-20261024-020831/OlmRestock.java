package owl.nh;
import java.util.*;
/** Acknowledged outside restock; never starts another encounter. */
final class OlmRestock {
 enum Stage { IDLE, WAIT, BANK, PRESET, DOOR, ENTRY, DONE }
 Stage stage=Stage.IDLE; long readyAt; int started,lastRequest=-1000,stableTick=-1,doorRegion; boolean doorInstance; int presetRequests;
 Map<Integer,Integer> before=new HashMap<>(),stable=new HashMap<>();
 void reset(){stage=Stage.IDLE;before.clear();stable.clear();stableTick=-1;lastRequest=-1000;presetRequests=0;}
 void start(int tick,long now,int delayMs){if(delayMs<1000||delayMs>5000)throw new IllegalArgumentException("Delay outside 1-5 seconds");stage=Stage.WAIT;started=tick;readyAt=now+delayMs*1000000L;}
 boolean waiting(long now){if(stage==Stage.WAIT&&now>=readyAt)stage=Stage.BANK;return stage==Stage.WAIT;}
 void requested(int tick,Map<Integer,Integer> bag){presetRequests++;before=new HashMap<>(bag);stable.clear();stableTick=-1;lastRequest=tick;stage=Stage.PRESET;}
 boolean loaded(int tick,Map<Integer,Integer> current,Map<Integer,Integer> expected){
  if(stage!=Stage.PRESET||tick<=lastRequest||expected.isEmpty()||!current.equals(expected)){stable.clear();stableTick=-1;return false;}
  if(!current.equals(stable)){stable=new HashMap<>(current);stableTick=tick;return false;}
  if(tick<=stableTick)return false;stage=Stage.DOOR;return true;
 } void doorRequested(int tick,int region,boolean instance){lastRequest=tick;doorRegion=region;doorInstance=instance;stage=Stage.ENTRY;}
 boolean entered(int region,boolean instance){if(stage==Stage.ENTRY&&(region!=doorRegion||instance!=doorInstance)){stage=Stage.DONE;return true;}return false;}
 boolean timedOut(int tick){return stage!=Stage.IDLE&&stage!=Stage.DONE&&tick-started>=150;}
 static boolean matches(boolean bank,String name,String[] actions){if(name==null||actions==null)return false;String expected=bank?"Bank chest":"Chambers of Xeric",action=bank?"Last-preset":"Enter";return expected.equalsIgnoreCase(name)&&Arrays.stream(actions).anyMatch(action::equalsIgnoreCase);}
}


