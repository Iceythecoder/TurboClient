package owl.nh;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
/** Visible heal effects plus a bounded health-rise fallback, scoped to one hand life. */
final class OlmHealingWatch {
 private static final int HEAL_CLEAR_TICKS=8; private NPC hand,eventHand;private int hp=-1,until=-1,eventUntil=-1,visualUntil=-1;
 void reset(){hand=eventHand=null;hp=until=eventUntil=visualUntil=-1;}
 void graphic(Actor actor,int tick){if(actor instanceof NPC&&actor.getGraphic()==GraphicID.OLM_HEAL){eventHand=(NPC)actor;eventUntil=tick+HEAL_CLEAR_TICKS;}}
 boolean active(NPC npc,Client client){if(npc==null){hand=null;hp=-1;until=visualUntil=-1;return false;}if(hand!=npc){hand=npc;hp=-1;until=visualUntil=-1;}int ratio=npc.getHealthRatio(),scale=npc.getHealthScale(),now=ratio>=0&&scale>0?ratio*100/scale:-1,tick=client.getTickCount();if(now>=0){if(hp>=0&&now>hp)until=tick+HEAL_CLEAR_TICKS-1;hp=now;}if(npc.getGraphic()==GraphicID.OLM_HEAL)visualUntil=tick+HEAL_CLEAR_TICKS;if(eventHand==npc)visualUntil=Math.max(visualUntil,eventUntil);
  if(client.getGraphicsObjects()!=null)for(GraphicsObject g:client.getGraphicsObjects())if(!g.finished()&&g.getId()==GraphicID.OLM_HEAL&&g.getLevel()==client.getPlane()){WorldPoint point=WorldPoint.fromLocal(client,g.getLocation());if(point!=null&&npc.getWorldArea()!=null&&npc.getWorldArea().distanceTo(point)<=1)visualUntil=tick+HEAL_CLEAR_TICKS;}
  // Orion's aura flashes. Require eight full game ticks without any visual, event, nearby graphic or HP-rise signal.
  // Repeated client polls cannot shorten this window or briefly re-enable melee between flashes.
  return tick<=until||tick<visualUntil;
 }
}
