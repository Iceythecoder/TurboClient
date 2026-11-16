package owl.nh;
import java.util.*;

/** Post-fight acknowledgements; a requested chest click is never loot evidence. */
final class OlmRaidFinish {
 enum Stage { IDLE, CHEST, LOOT, EXIT, DONE }
 Stage stage=Stage.IDLE;int started,lastRequest=-1000,lootRequest=-1,pendingRewardId;boolean lootObserved;Map<Integer,Integer> before=new HashMap<>();
 void reset(){stage=Stage.IDLE;started=0;lastRequest=-1000;lootRequest=-1;pendingRewardId=0;lootObserved=false;before.clear();}
 void start(int tick){if(stage==Stage.IDLE){stage=Stage.CHEST;started=tick;}}
 boolean active(){return stage!=Stage.IDLE&&stage!=Stage.DONE;}
 boolean due(int tick){return tick-lastRequest>=(stage==Stage.LOOT&&pendingRewardId==0?1:5);}
 void chestRequested(int tick,Map<Integer,Integer> inventory){before=new HashMap<>(inventory);lastRequest=tick;if(lootRequest<0)lootRequest=tick;stage=Stage.LOOT;}
 void rewardRequested(int tick,Map<Integer,Integer> inventory,int id){chestRequested(tick,inventory);lootRequest=tick;pendingRewardId=id;}
 boolean observeLoot(int tick,Map<Integer,Integer> inventory){if(stage!=Stage.LOOT||pendingRewardId<=0||tick<=lootRequest)return false;if(inventory.getOrDefault(pendingRewardId,0)>before.getOrDefault(pendingRewardId,0)){pendingRewardId=0;lootObserved=true;return true;}return false;}
 boolean emptyRewardsObserved(boolean visible,boolean empty){if(stage==Stage.LOOT&&lootObserved&&pendingRewardId==0&&visible&&empty){stage=Stage.EXIT;return true;}return false;}
 boolean exited(boolean inRaid){if(stage==Stage.EXIT&&!inRaid){stage=Stage.DONE;return true;}return false;}
 boolean timedOut(int tick){return active()&&tick-started>=200;}
 static String objectAction(boolean chest,int configuredId,int id,String name,String[] actions){if(actions==null)return null;String n=name==null?"":name.toLowerCase(Locale.ROOT);if(configuredId>=0?id!=configuredId:chest?!n.contains("chest"):!(n.contains("exit")||n.contains("stairs")||n.contains("steps")||n.contains("rope")))return null;for(String preferred:chest?new String[]{"Search","Loot","Open","Collect"}:new String[]{"Leave","Exit","Climb-up","Climb"})for(String a:actions)if(preferred.equalsIgnoreCase(a))return a;return null;}
}
