package owl.nh;
import java.awt.*;
import java.awt.event.KeyEvent;
import net.runelite.api.*;
import net.runelite.api.coords.*;

/** Ordinary wheel/arrow inputs only. No camera setters, pitch unlocks, or game-state mutation. */
final class OlmCamera {
 private final Client client;private final VisibleCombatUi ui;private final Zoom zoom=new Zoom();
 private int targetIndex=-1;private int lastKeyCycle=-1000,beforeYaw,beforePitch,lastDirection,unanswered;private boolean leftIncreases=true;volatile String status="Waiting for live Olm.";private volatile int held;private volatile long generation;private javax.swing.Timer release;
 OlmCamera(Client client,VisibleCombatUi ui){this.client=client;this.ui=ui;}
 static int yaw(int dx,int dy){return ((int)Math.round(Math.atan2(-dx,dy)*1024/Math.PI)+2048)&2047;}
 static int difference(int wanted,int current){return ((wanted-current+3072)&2047)-1024;}
 static final class Zoom {
  int lastCycle=-1000,lastValue=Integer.MIN_VALUE,unchanged,requests,settledValue=Integer.MIN_VALUE;boolean resized,initialized;
  boolean due(int cycle,int value,boolean nextResized){if(!initialized||resized!=nextResized){reset();initialized=true;resized=nextResized;}if(settledValue!=Integer.MIN_VALUE){if(value==settledValue)return false;reset();initialized=true;resized=nextResized;}return cycle-lastCycle>=8;}
  void sent(int cycle,int value){if(value==lastValue)unchanged++;else unchanged=0;lastValue=value;lastCycle=cycle;requests++;if(unchanged>=3||requests>=32)settledValue=value;}
  void reset(){lastCycle=-1000;lastValue=settledValue=Integer.MIN_VALUE;unchanged=requests=0;}
 }
 static boolean active(boolean running,boolean entered,boolean postRaid,NPC boss){return running&&entered&&!postRaid&&boss!=null&&!boss.isDead();}
 static int turnKey(boolean visible,int pitch,int delta,boolean leftIncreases){if(visible)return 0;if(pitch<300)return KeyEvent.VK_UP;if(Math.abs(delta)>64)return (delta>0)==leftIncreases?KeyEvent.VK_LEFT:KeyEvent.VK_RIGHT;return pitch<360?KeyEvent.VK_UP:0;}
 void pulse(boolean enabled,boolean fresh,NPC boss){
  Canvas canvas=client.getCanvas();if(!enabled||boss==null||boss.isDead()||!fresh||client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null||canvas==null||!canvas.isFocusOwner()){stop();return;}
  if(targetIndex!=boss.getIndex()){targetIndex=boss.getIndex();unanswered=0;lastDirection=0;}int cycle=client.getGameCycle();int value=client.getVarcIntValue(client.isResized()?VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT:VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT);
  if(zoom.due(cycle,value,client.isResized())&&ui.zoomOut(client))zoom.sent(cycle,value);
  if(held!=0||cycle-lastKeyCycle<14)return;
  if(lastDirection!=0){int change=difference(client.getCameraYaw(),beforeYaw);boolean moved=lastDirection==KeyEvent.VK_UP?Math.abs(client.getCameraPitch()-beforePitch)>1:Math.abs(change)>1;if(moved){unanswered=0;if(lastDirection!=KeyEvent.VK_UP)leftIncreases=lastDirection==KeyEvent.VK_LEFT?change>0:change<0;}else unanswered++;lastDirection=0;}
  LocalPoint local=boss.getLocalLocation(),self=client.getLocalPlayer().getLocalLocation();if(local==null||self==null)return;
  Rectangle viewport=new Rectangle(client.getViewportXOffset()+24,client.getViewportYOffset()+24,Math.max(1,client.getViewportWidth()-48),Math.max(1,client.getViewportHeight()-48));
  net.runelite.api.Point centre=Perspective.localToCanvas(client,local,client.getPlane(),Math.max(0,boss.getLogicalHeight()/2));Shape shape=boss.getConvexHull();
  boolean visible=centre!=null&&viewport.contains(centre.getX(),centre.getY())&&shape!=null&&shape.intersects(viewport);
  if(visible){unanswered=0;status="Olm visible; camera angle held.";return;}
  if(unanswered>=3){status="Camera input was not acknowledged; automatic turns paused.";return;}
  int delta=difference(yaw(local.getX()-self.getX(),local.getY()-self.getY()),client.getCameraYaw());int key=turnKey(false,client.getCameraPitch(),delta,leftIncreases);
  if(key!=0){beforeYaw=client.getCameraYaw();beforePitch=client.getCameraPitch();lastDirection=key;lastKeyCycle=cycle;status="Framing live Olm; waiting for camera response.";tap(canvas,key);}
 }
 static void key(Canvas canvas,int id,int code){KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(canvas,new KeyEvent(canvas,id,System.currentTimeMillis(),0,code,KeyEvent.CHAR_UNDEFINED,KeyEvent.KEY_LOCATION_STANDARD));}
 java.util.Map<String,Object> diagnostics(){java.util.Map<String,Object> data=new java.util.LinkedHashMap<>();data.put("status",status);data.put("unacknowledgedTurns",unanswered);data.put("yaw",client.getCameraYaw());data.put("pitch",client.getCameraPitch());data.put("zoomRequests",zoom.requests);return data;}
 private void tap(Canvas canvas,int key){long token=generation;EventQueue.invokeLater(()->{if(token!=generation||!canvas.isShowing()||!canvas.isFocusOwner()||held!=0)return;held=key;key(canvas,KeyEvent.KEY_PRESSED,key);release=new javax.swing.Timer(220,e->release(canvas));release.setRepeats(false);release.start();});}
 private void release(Canvas canvas){if(release!=null){release.stop();release=null;}int key=held;held=0;if(key!=0)key(canvas,KeyEvent.KEY_RELEASED,key);}
 void stop(){generation++;lastDirection=0;unanswered=0;status="Camera idle outside active Olm combat.";Canvas canvas=client.getCanvas();if(held!=0&&canvas!=null)EventQueue.invokeLater(()->release(canvas));}
}
