package owl.nh;
import java.util.Locale;
/** Death is a failed attempt, never a kill. Requires observed respawn and outside evidence. */
final class OlmDeathRecovery {
 enum Stage { IDLE, RESPAWN, EXIT, OUTSIDE }
 Stage stage=Stage.IDLE;private OlmSpecials.Cell origin;private int started,stableAt=-1,lastStable=-1;private boolean stableOutside;
 int lastRequest=-1000,ropeRequests;
 void start(int tick,OlmSpecials.Cell deathTile){reset();stage=Stage.RESPAWN;started=tick;origin=deathTile;}
 void reset(){stage=Stage.IDLE;origin=null;stableAt=lastStable=-1;lastRequest=-1000;ropeRequests=0;}
 void confirmedPreRoom(){origin=null;}
 void reconnected(int tick){started=tick;stableAt=lastStable=-1;lastRequest=tick;}
 void observe(int tick,boolean alive,OlmSpecials.Cell position,boolean preRoom,boolean outside){
  if(stage==Stage.IDLE||stage==Stage.OUTSIDE)return;
  boolean relocated=origin==null||position!=null&&origin.distance(position)>=4;
  boolean recognized=alive&&(outside||preRoom&&(stage==Stage.EXIT||relocated));
  if(!recognized){stableAt=lastStable=-1;return;}
  if(stableAt<0||stableOutside!=outside||tick<lastStable){stableAt=tick;stableOutside=outside;}
  if(tick>stableAt){if(outside)stage=Stage.OUTSIDE;else if(stage==Stage.RESPAWN)stage=Stage.EXIT;}
  lastStable=tick;
 }
 boolean due(int tick){return stage==Stage.EXIT&&tick-lastRequest>=5;}
 boolean exhausted(int tick){return ropeRequests>=3&&due(tick);}
 void ropeRequested(int tick){lastRequest=tick;ropeRequests++;}
 boolean timedOut(int tick){return stage!=Stage.IDLE&&stage!=Stage.OUTSIDE&&tick-started>=200;}
 static boolean onOutsideSide(OlmSpecials.Cell player,OlmSpecials.Cell gate,OlmSpecials.Cell outside){return player!=null&&gate!=null&&outside!=null&&player.plane==gate.plane&&outside.plane==gate.plane&&(long)(player.x-gate.x)*(outside.x-gate.x)+(long)(player.y-gate.y)*(outside.y-gate.y)>0;}
 static String ropeAction(String name,String[] actions){if(name==null||!name.replaceAll("<[^>]*>","").toLowerCase(Locale.ROOT).contains("rope")||actions==null)return null;for(String preferred:new String[]{"Climb","Climb-up","Leave","Exit"})for(String action:actions)if(preferred.equalsIgnoreCase(action))return action;return null;}
 static boolean confirmLeave(String text){if(text==null)return false;String s=text.replaceAll("<[^>]*>","").trim().toLowerCase(Locale.ROOT);if(s.matches("^(no|cancel|stay|don't|do not)\\b.*"))return false;return s.matches("^(yes|leave|exit)\\b.*")||s.contains("leave the raid")&&!s.contains("not")&&!s.contains("don't");}
}
