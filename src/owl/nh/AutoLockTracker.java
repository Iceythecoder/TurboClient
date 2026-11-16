package owl.nh;
import net.runelite.api.MenuAction;

/** An explicit attack intent must be confirmed against the same actor, not only its reused index. */
final class AutoLockTracker {
 Object candidate;int index=-1;long requestedAt;
 void request(Object actor,int index,long now){candidate=actor;this.index=index;requestedAt=now;}
 boolean confirmed(Object interacting,int index){return candidate!=null&&candidate==interacting&&this.index==index;}
 boolean expired(long now){return candidate!=null&&now-requestedAt>=2000000000L;}
 void clear(){candidate=null;index=-1;requestedAt=0;}
 static boolean attackMenu(MenuAction action,String option,boolean barrageSelected) {
  if(action==null)return false;
  if(action==MenuAction.WIDGET_TARGET_ON_PLAYER)return barrageSelected;
  if(option==null||!option.replaceAll("<[^>]*>","").trim().equalsIgnoreCase("Attack"))return false;
  switch(action){case PLAYER_FIRST_OPTION:case PLAYER_SECOND_OPTION:case PLAYER_THIRD_OPTION:case PLAYER_FOURTH_OPTION:case PLAYER_FIFTH_OPTION:case PLAYER_SIXTH_OPTION:case PLAYER_SEVENTH_OPTION:case PLAYER_EIGHTH_OPTION:return true;default:return false;}
 }
}
