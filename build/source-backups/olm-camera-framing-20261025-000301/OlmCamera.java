package owl.nh;
import java.awt.*;
import java.awt.event.KeyEvent;
import net.runelite.api.*;
import net.runelite.api.coords.*;

/** Ordinary wheel/arrow inputs only. No camera setters, pitch unlocks, or game-state mutation. */
final class OlmCamera {
 private final Client client;private final VisibleCombatUi ui;private final Zoom zoom=new Zoom();
 private int lastKeyCycle=-1000;private volatile int held;private volatile long generation;private javax.swing.Timer release;
 OlmCamera(Client client,VisibleCombatUi ui){this.client=client;this.ui=ui;}
 static int yaw(int dx,int dy){return ((int)Math.round(Math.atan2(-dx,dy)*1024/Math.PI)+2048)&2047;}
 static int difference(int wanted,int current){return ((wanted-current+3072)&2047)-1024;}
 static final class Zoom {
  int lastCycle=-1000,lastValue=Integer.MIN_VALUE,unchanged,requests,settledValue=Integer.MIN_VALUE;boolean resized,initialized;
  boolean due(int cycle,int value,boolean nextResized){if(!initialized||resized!=nextResized){reset();initialized=true;resized=nextResized;}if(settledValue!=Integer.MIN_VALUE){if(value==settledValue)return false;reset();initialized=true;resized=nextResized;}return cycle-lastCycle>=8;}
  void sent(int cycle,int value){if(value==lastValue)unchanged++;else unchanged=0;lastValue=value;lastCycle=cycle;requests++;if(unchanged>=3||requests>=32)settledValue=value;}
  void reset(){lastCycle=-1000;lastValue=settledValue=Integer.MIN_VALUE;unchanged=requests=0;}
 }
 void pulse(boolean enabled,boolean fresh,NPC boss,WorldPoint escapeFocus){
  Canvas canvas=client.getCanvas();if(!enabled||!fresh||client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null||canvas==null||!canvas.isFocusOwner()){stop();return;}
  int cycle=client.getGameCycle();int value=client.getVarcIntValue(client.isResized()?VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT:VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT);
  // Maintenance wheel actions use spare interface slots; they never displace an escape click.
  if(zoom.due(cycle,value,client.isResized())&&ui.zoomOut(client))zoom.sent(cycle,value);
  if(held!=0||cycle-lastKeyCycle<8)return;
  WorldPoint point=escapeFocus!=null?escapeFocus:boss==null?null:boss.getWorldLocation();if(point==null)return;
  LocalPoint local=LocalPoint.fromWorld(client,point),self=client.getLocalPlayer().getLocalLocation();if(local==null||self==null)return;
  boolean visible;if(escapeFocus!=null)visible=VisibleCombatUi.groundPoint(client,point)!=null;else{Shape shape=boss.getConvexHull();Rectangle viewport=new Rectangle(client.getViewportXOffset()+24,client.getViewportYOffset()+24,Math.max(1,client.getViewportWidth()-48),Math.max(1,client.getViewportHeight()-48));visible=shape!=null&&viewport.contains(shape.getBounds().getCenterX(),shape.getBounds().getCenterY());}
  int key=0;if(client.getCameraPitch()<300)key=KeyEvent.VK_UP;else if(!visible){int delta=difference(yaw(local.getX()-self.getX(),local.getY()-self.getY()),client.getCameraYaw());if(Math.abs(delta)>64)key=delta>0?KeyEvent.VK_LEFT:KeyEvent.VK_RIGHT;}
  if(key!=0){lastKeyCycle=cycle;tap(canvas,key);}
 }
 private void tap(Canvas canvas,int key){long token=generation;EventQueue.invokeLater(()->{if(token!=generation||!canvas.isShowing()||!canvas.isFocusOwner()||held!=0)return;held=key;canvas.dispatchEvent(new KeyEvent(canvas,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,key,KeyEvent.CHAR_UNDEFINED));release=new javax.swing.Timer(120,e->release(canvas));release.setRepeats(false);release.start();});}
 private void release(Canvas canvas){if(release!=null){release.stop();release=null;}int key=held;held=0;if(key!=0)canvas.dispatchEvent(new KeyEvent(canvas,KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,key,KeyEvent.CHAR_UNDEFINED));}
 void stop(){generation++;Canvas canvas=client.getCanvas();if(held!=0&&canvas!=null)EventQueue.invokeLater(()->release(canvas));}
}
