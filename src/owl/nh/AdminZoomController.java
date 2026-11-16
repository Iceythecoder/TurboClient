package owl.nh;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Scene;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.client.callback.ClientThread;

/** Reversible local camera aid owned exclusively by the Admin Tool. */
final class AdminZoomController {
 static final int EXTENDED_ZOOM=-400,EXTENDED_DRAW_DISTANCE=90;
 private final Client client;private final ClientThread thread;private volatile boolean enabled;private boolean snapshot;
 private int fixed,resizable,drawDistance;private Scene originalScene;
 AdminZoomController(Client client,ClientThread thread){this.client=client;this.thread=thread;}
 boolean isEnabled(){return enabled;}
 void enable(){enabled=true;thread.invoke(this::apply);}
 void disable(){enabled=false;thread.invoke(this::restore);}
 void pulse(){if(enabled)apply();}
 void apply(){if(!enabled||client.getGameState()!=GameState.LOGGED_IN)return;Scene scene=client.getScene();if(scene==null)return;if(!snapshot){fixed=client.getVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT);resizable=client.getVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT);drawDistance=scene.getDrawDistance();originalScene=scene;snapshot=true;client.runScript(ScriptID.CAMERA_DO_ZOOM,EXTENDED_ZOOM,EXTENDED_ZOOM);}if(scene.getDrawDistance()<EXTENDED_DRAW_DISTANCE)scene.setDrawDistance(EXTENDED_DRAW_DISTANCE);}
 void restore(){if(!snapshot)return;client.setVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT,fixed);client.setVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT,resizable);Scene current=client.getScene();if(current!=null)current.setDrawDistance(drawDistance);if(originalScene!=null&&originalScene!=current)originalScene.setDrawDistance(drawDistance);snapshot=false;originalScene=null;}
}
