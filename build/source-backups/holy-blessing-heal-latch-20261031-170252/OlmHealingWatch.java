package owl.nh;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
/** Visible heal effects plus a bounded health-rise fallback, scoped to one hand life. */
final class OlmHealingWatch {
 private static final int HEAL_CLEAR_TICKS=4; private NPC hand,eventHand;private int hp=-1,until=-1,eventUntil=-1,visualUntil=-1;
 void reset(){hand=eventHand=null;hp=until=eventUntil=visualUntil=-1;}
 void graphic(Actor actor,int tick){if(actor instanceof NPC&&actor.getGraphic()==GraphicID.OLM_HEAL){eventHand=(NPC)actor;eventUntil=tick+HEAL_CLEAR_TICKS;}}
 boolean active(NPC npc,Client client){if(npc==null){hand=null;hp=-1;until=visualUntil=-1;return false;}if(hand!=npc){hand=npc;hp=-1;until=visualUntil=-1;}int ratio=npc.getHealthRatio(),scale=npc.getHealthScale(),now=ratio>=0&&scale>0?ratio*100/scale:-1,tick=client.getTickCount();if(now>=0){if(hp>=0&&now>hp)until=tick+3;hp=now;}if(npc.getGraphic()==GraphicID.OLM_HEAL)visualUntil=tick+HEAL_CLEAR_TICKS;if(eventHand==npc)visualUntil=Math.max(visualUntil,eventUntil);
  if(client.getGraphicsObjects()!=null)for(GraphicsObject g:client.getGraphicsObjects())if(!g.finished()&&g.getId()==GraphicID.OLM_HEAL&&g.getLevel()==client.getPlane()){WorldPoint point=WorldPoint.fromLocal(client,g.getLocation());if(point!=null&&npc.getWorldArea()!=null&&npc.getWorldArea().distanceTo(point)<=1)visualUntil=tick+HEAL_CLEAR_TICKS;}
  // Release only after four full game ticks without a heal signal. Repeated client polls cannot shorten this window.
  return tick<=until||tick<visualUntil;
 }
}
