package owl.nh;
import java.lang.reflect.*;import java.util.*;
/** Reflection-only ABI preflight. Never initializes Orion, logs in or issues inputs. */
public final class ClientCompatibility {
 static final List<String> missing=new ArrayList<>();
 static Class<?> type(String name)throws ClassNotFoundException{return Class.forName(name,false,ClientCompatibility.class.getClassLoader());}
 static void method(String owner,String name,String... parameters){try{Class<?>[] types=new Class<?>[parameters.length];for(int i=0;i<types.length;i++)types[i]=parameters[i].equals("int")?int.class:type(parameters[i]);type(owner).getMethod(name,types);}catch(Throwable error){missing.add(owner+"."+name+"("+String.join(",",parameters)+")");}}
 public static void main(String[] args){
  if(Runtime.version().feature()<11){System.err.println("Java 11 or newer is required.");System.exit(2);}
  String client="net.runelite.api.Client",actor="net.runelite.api.Actor",widget="net.runelite.api.widgets.Widget";
  for(String name:new String[]{"getTickCount","getGameCycle","getLocalPlayer","getNpcs","getPlayers","getGameState","getCanvas","getMenuEntries","getQueuedMenu","getMouseCanvasPosition","getItemContainer","getBaseX","getBaseY","getPlane"})if(!name.equals("getItemContainer"))method(client,name);
  method(client,"getItemContainer","net.runelite.api.InventoryID");method(client,"getWidget","net.runelite.api.widgets.WidgetInfo");method(client,"getWidget","int","int");method(client,"getVarcIntValue","int");method(client,"getBoostedSkillLevel","net.runelite.api.Skill");
  for(String name:new String[]{"getWorldLocation","getWorldArea","getAnimation","getGraphic","getInteracting","getHealthRatio","getHealthScale","isDead"})method(actor,name);
  for(String name:new String[]{"getBounds","isVisible","isHidden","isSelfHidden","getParent","getActions","getItemId","getItemQuantity","getChildren","getWidgetItems"})method(widget,name);
  method("net.runelite.api.NPC","interact","java.lang.String");method("net.runelite.api.TileObject","interact","java.lang.String");
  for(String name:new String[]{"getSpecEnergy","isSpecEnabled"})method("net.unethicalite.api.game.Combat",name);
  method("net.unethicalite.api.items.Inventory","getFirst","[I");method("net.unethicalite.api.widgets.Dialog","canContinue");method("net.unethicalite.api.widgets.Dialog","chooseOption","int");method("net.unethicalite.api.widgets.Prayers","toggle","net.runelite.api.Prayer");
  for(String name:new String[]{"net.runelite.client.RuneLite","net.runelite.client.callback.ClientThread","net.runelite.client.eventbus.EventBus","net.runelite.client.ui.overlay.OverlayManager","net.runelite.api.events.GameTick","net.unethicalite.api.widgets.Tab","net.runelite.rs.api.RSActor"})try{type(name);}catch(Throwable e){missing.add(name);}
  for(String name:new String[]{"COMBAT_SPECIAL_ATTACK_CLICKBOX","COMBAT_SPECIAL_ATTACK","INVENTORY","PROTECT_FROM_MAGIC"})try{if(name.equals("PROTECT_FROM_MAGIC"))type("net.runelite.api.Prayer").getField(name);else type("net.runelite.api.widgets.WidgetInfo").getField(name);}catch(Throwable e){missing.add("Widget/prayer constant "+name);}
  if(!missing.isEmpty()){System.err.println("Client API compatibility check failed. Missing requirements:");for(String entry:missing)System.err.println(" - "+entry);System.exit(3);}
  System.out.println("Client API preflight passed on Java "+Runtime.version().feature()+". No Orion startup or game actions. This checks API compatibility, not gameplay equivalence.");
 }
}
