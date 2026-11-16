package owl.nh;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.MenuOptionClicked;

/** End-to-end recorder -> learner -> prediction checks on synthetic observations. */
public final class ChambersDemonstrationTests {
 private static void check(boolean value,String message){CoreTests.check(value,"Demonstration: "+message);}
 private static MenuOptionClicked action(String option){return new MenuOptionClicked(null){
  @Override public String getMenuOption(){return option;}
  @Override public String getMenuTarget(){throw new AssertionError("Raw menu targets must not be read");}
  @Override public MenuAction getMenuAction(){return MenuAction.ITEM_FIRST_OPTION;}
  @Override public int getItemId(){return option.equals("Eat")?202:201;}
  @Override public int getId(){return 1;}
  @Override public int getParam0(){return 2;}
  @Override public int getParam1(){return 3;}
 };}
 public static void main(String[] args){
  check(ChambersDemonstration.allowed("NPC_FIRST_OPTION","Attack"),"NPC attack allowed");
  check(!ChambersDemonstration.allowed("PLAYER_FIRST_OPTION","Attack"),"player actions excluded");
  check(!ChambersDemonstration.allowed("CC_OP","Message"),"messaging excluded");
  Map<String,Object> playerFields=new HashMap<>();playerFields.put("getLocalLocation",new LocalPoint(128,128));playerFields.put("getName","PRIVATE_PLAYER_NAME");
  Player player=ChambersTests.fake(Player.class,playerFields);Map<String,Object> fields=new HashMap<>();
  fields.put("getGameState",GameState.LOGGED_IN);fields.put("getLocalPlayer",player);fields.put("getNpcs",Collections.emptyList());fields.put("getBaseX",3200);fields.put("getBaseY",3200);fields.put("getTickCount",1);fields.put("getGameCycle",30);fields.put("getBoostedSkillLevel",99);
  ChambersDemonstration demo=new ChambersDemonstration(ChambersTests.fake(Client.class,fields));
  demo.menu(action("Drink"));check(!demo.hasData(),"no recording before explicit start");
  demo.start();boolean rejected=false;try{demo.learn();}catch(IllegalStateException e){rejected=true;}check(rejected,"learning blocked during recording");
  for(int i=0;i<6;i++){fields.put("getBoostedSkillLevel",i%2==0?40:99);demo.menu(action(i%2==0?"Eat":"Drink"));fields.put("getTickCount",i+2);demo.tick();}
  demo.phase(1);demo.mark("Mage hand down");demo.stop("Test finished");
  check(demo.trainingCopy().size()==6,"actions paired with next tick observations");
  demo.learn();demo.phase(0);fields.put("getBoostedSkillLevel",40);demo.tick();check(demo.prediction.contains("eat"),"low HP state retrieves demonstrated eat action");
  fields.put("getBoostedSkillLevel",99);demo.tick();check(demo.prediction.contains("drink"),"different state retrieves different demonstrated action");
  fields.put("getBaseX",6400);demo.tick();check(demo.prediction.contains("Abstain"),"unseen region abstains");fields.put("getBaseX",3200);
  String json=demo.export();check(!json.contains("PRIVATE_PLAYER_NAME")&&!json.contains("menuTarget"),"names and raw menu targets absent from export");
  ChambersDemonstration loaded=new ChambersDemonstration(ChambersTests.fake(Client.class,fields));loaded.restore(json);loaded.learn();loaded.tick();check(loaded.prediction.contains("drink"),"saved demonstration relearns after loading");
  rejected=false;try{loaded.start();}catch(IllegalStateException e){rejected=true;}check(rejected,"new recording cannot overwrite data silently");
  loaded.clear();rejected=false;try{loaded.restore("{\"schema\":\"wrong\"}");}catch(IllegalArgumentException e){rejected=true;}check(rejected&&!loaded.hasData(),"malformed schema rejected atomically");
  loaded.start();fields.put("getGameState",GameState.LOGIN_SCREEN);loaded.tick();check(!loaded.recording,"logout stops recording");
  demo.clear();check(!demo.hasData()&&!demo.recording,"discard clears data and model");
  System.out.println("Chambers demonstration checks passed: explicit recording, menu filtering, action/state pairs, learned predictions, abstention, save/load roundtrip, privacy and logout. No live input.");
 }
}
