package owl.nh;
import java.util.*;
/** Acknowledged outside restock; never starts another encounter. */
final class OlmRestock {
 enum Stage { IDLE, WAIT, BANK, MANAGER, PRESET, DOOR, ENTRY, DONE }
 Stage stage=Stage.IDLE; long readyAt; int started,lastRequest=-1000,stableTick=-1,doorRegion; boolean doorInstance; int presetRequests,doorRequests;
 Map<Integer,Integer> before=new HashMap<>(),stable=new HashMap<>();
 void reset(){stage=Stage.IDLE;before.clear();stable.clear();stableTick=-1;lastRequest=-1000;presetRequests=doorRequests=0;}
 void start(int tick,long now,int delayMs){if(delayMs<1000||delayMs>5000)throw new IllegalArgumentException("Delay outside 1-5 seconds");stage=Stage.WAIT;started=tick;readyAt=now+delayMs*1000000L;}
 boolean waiting(long now){if(stage==Stage.WAIT&&now>=readyAt)stage=Stage.BANK;return stage==Stage.WAIT;}
 void requested(int tick,Map<Integer,Integer> bag){presetRequests++;before=new HashMap<>(bag);stable.clear();stableTick=-1;lastRequest=tick;stage=Stage.PRESET;}
 boolean loaded(int tick,Map<Integer,Integer> current,Map<Integer,Integer> expected){
  if(stage!=Stage.PRESET||tick<=lastRequest||expected.isEmpty()||!current.equals(expected)){stable.clear();stableTick=-1;return false;}
  if(!current.equals(stable)){stable=new HashMap<>(current);stableTick=tick;return false;}
  if(tick<=stableTick)return false;stage=Stage.DOOR;return true;
 } void doorRequested(int tick,int region,boolean instance){doorRequests++;lastRequest=tick;doorRegion=region;doorInstance=instance;stage=Stage.ENTRY;}
 boolean retryDoor(int tick,boolean moving){if(stage==Stage.ENTRY&&!moving&&tick-lastRequest>=5&&doorRequests<3){stage=Stage.DOOR;return true;}return false;}
 boolean doorFailed(int tick,boolean moving){return stage==Stage.ENTRY&&!moving&&doorRequests>=3&&tick-lastRequest>=5;}
 static List<Map<String,Integer>> differences(Map<Integer,Integer> current,Map<Integer,Integer> expected){Set<Integer> keys=new TreeSet<>(expected.keySet());keys.addAll(current.keySet());List<Map<String,Integer>> out=new ArrayList<>();for(int key:keys){int want=expected.getOrDefault(key,0),have=current.getOrDefault(key,0);if(want==have)continue;Map<String,Integer> d=new LinkedHashMap<>();d.put("itemId",Math.abs(key));d.put("equipment",key<0?1:0);d.put("expected",want);d.put("observed",have);out.add(d);if(out.size()>=40)break;}return out;} boolean entered(int region,boolean instance){if(stage==Stage.ENTRY&&(region!=doorRegion||instance!=doorInstance)){stage=Stage.DONE;return true;}return false;}
 boolean timedOut(int tick){return stage!=Stage.IDLE&&stage!=Stage.DONE&&tick-started>=150;}
 static int comparableEquipmentQuantity(int slot,int quantity){
  // Ammunition is consumed during a successful raid and Orion's Cox preset can
  // preserve the remaining equipped stack.  Its identity/presence is part of
  // the setup, but an exact arrow count is not.  Keep every other equipment and
  // inventory quantity exact so a partially loaded preset still cannot pass.
  return slot==net.runelite.api.EquipmentInventorySlot.AMMO.getSlotIdx()&&quantity>0?1:quantity;
 }
 static boolean matches(boolean bank,String name,String[] actions){if(name==null||actions==null)return false;String expected=bank?"Bank chest":"Chambers of Xeric",action=bank?"Use":"Enter";return expected.equalsIgnoreCase(name)&&Arrays.stream(actions).anyMatch(action::equalsIgnoreCase);}
}




