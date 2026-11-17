package turbo.gauntlet;

import java.util.*;

/** Pure, deterministic Gauntlet state model. It contains no Orion input calls. */
final class GauntletState {
 enum Mode { AUTO_DETECT, REGULAR, CORRUPTED }
 enum Phase {
  IDLE, VALIDATE_INSTANCE, INITIALIZE_RUN, MAP_START_ROOM, EXPLORE_FOR_RESOURCES,
  GATHER_INITIAL_RESOURCES, RETURN_TO_BASE, CRAFT_FIRST_WEAPON, CRAFT_ARMOR,
  MAKE_VIALS, GATHER_HERBS_AND_SHARDS, MAKE_POTIONS, HUNT_DEMIBOSSES,
  UPGRADE_WEAPONS, FINAL_RESOURCE_CHECK, ENTER_HUNLLEF, HUNLLEF_COMBAT,
  LOOT_CHEST, EXIT_INSTANCE, COMPLETE, RECOVERY, STOPPED
 }
 enum Confidence { VERIFIED_RUNTIME, OSRS_REFERENCE, HYPOTHESIS, UNKNOWN }
 enum ActionKind { OBSERVE, MOVE, INTERACT, ATTACK, EQUIP, PRAY, EAT, DRINK, CRAFT, LOOT, EXIT, STOP }

 static final class Snapshot {
  final int tick, hp, prayer, food, potions, weaponTier, armorPieces, shards;
  final boolean loggedIn, gauntletEvidence, corruptedEvidence, startRoom, preparation,
    bossRoom, rewardRoom, hunllefPresent, hunllefDead, rewardChest, exitVisible,
    imminentFloorDanger, tornadoDanger;
  Snapshot(int tick,int hp,int prayer,int food,int potions,int weaponTier,int armorPieces,int shards,
    boolean loggedIn,boolean gauntletEvidence,boolean corruptedEvidence,boolean startRoom,
    boolean preparation,boolean bossRoom,boolean rewardRoom,boolean hunllefPresent,
    boolean hunllefDead,boolean rewardChest,boolean exitVisible,boolean imminentFloorDanger,
    boolean tornadoDanger){this.tick=tick;this.hp=hp;this.prayer=prayer;this.food=food;this.potions=potions;
   this.weaponTier=weaponTier;this.armorPieces=armorPieces;this.shards=shards;this.loggedIn=loggedIn;
   this.gauntletEvidence=gauntletEvidence;this.corruptedEvidence=corruptedEvidence;this.startRoom=startRoom;
   this.preparation=preparation;this.bossRoom=bossRoom;this.rewardRoom=rewardRoom;this.hunllefPresent=hunllefPresent;
   this.hunllefDead=hunllefDead;this.rewardChest=rewardChest;this.exitVisible=exitVisible;
   this.imminentFloorDanger=imminentFloorDanger;this.tornadoDanger=tornadoDanger;}
 }

 static final class Decision {
  final ActionKind kind; final String reason; final int priority;
  Decision(ActionKind kind,String reason,int priority){this.kind=kind;this.reason=reason;this.priority=priority;}
  static Decision of(ActionKind kind,String reason,int priority){return new Decision(kind,reason,priority);}
 }

 private Phase phase=Phase.IDLE; private Mode mode=Mode.AUTO_DETECT; private int enteredTick;
 Phase phase(){return phase;} Mode mode(){return mode;}
 void reset(Mode selected){mode=selected;phase=Phase.VALIDATE_INSTANCE;enteredTick=-1;}
 void stop(){phase=Phase.STOPPED;}

 Decision step(Snapshot s, boolean controlReady){
  if(phase==Phase.STOPPED||phase==Phase.COMPLETE)return Decision.of(ActionKind.STOP,phase.name(),0);
  if(!s.loggedIn){phase=Phase.RECOVERY;return Decision.of(ActionKind.OBSERVE,"Waiting for logged-in state",100);}
  if(s.imminentFloorDanger||s.tornadoDanger)return Decision.of(ActionKind.MOVE,"Immediate Hunllef hazard",1000);
  if(phase==Phase.RECOVERY){phase=Phase.VALIDATE_INSTANCE;enteredTick=s.tick;}
  if(enteredTick<0)enteredTick=s.tick;
  switch(phase){
   case VALIDATE_INSTANCE:
    if(!s.gauntletEvidence)return Decision.of(ActionKind.OBSERVE,"No verified Gauntlet instance evidence",900);
    if(mode==Mode.AUTO_DETECT)mode=s.corruptedEvidence?Mode.CORRUPTED:Mode.REGULAR;
    return enter(Phase.INITIALIZE_RUN,s,"Instance and mode observed");
   case INITIALIZE_RUN: return enter(Phase.MAP_START_ROOM,s,"Initialize per-run map and ledger");
   case MAP_START_ROOM:
    if(!s.startRoom)return Decision.of(ActionKind.OBSERVE,"Locate verified start room",500);
    return enter(Phase.EXPLORE_FOR_RESOURCES,s,"Start room mapped");
   case EXPLORE_FOR_RESOURCES: return enter(Phase.GATHER_INITIAL_RESOURCES,s,"Plan cheapest unvisited resource room");
   case GATHER_INITIAL_RESOURCES:
    if(s.shards<40)return action(controlReady,ActionKind.INTERACT,"Gather verified resource node",360);
    return enter(Phase.RETURN_TO_BASE,s,"Initial resource threshold reached");
   case RETURN_TO_BASE: return enter(Phase.CRAFT_FIRST_WEAPON,s,"Return path through discovered graph");
   case CRAFT_FIRST_WEAPON:
    if(s.weaponTier<1)return action(controlReady,ActionKind.CRAFT,"Craft first verified weapon",410);
    return enter(Phase.CRAFT_ARMOR,s,"First weapon observed");
   case CRAFT_ARMOR:
    if(s.armorPieces<3)return action(controlReady,ActionKind.CRAFT,"Craft missing armor pieces",400);
    return enter(Phase.MAKE_VIALS,s,"Armor target observed");
   case MAKE_VIALS:return enter(Phase.GATHER_HERBS_AND_SHARDS,s,"Prepare potion containers");
   case GATHER_HERBS_AND_SHARDS:
    if(s.potions<2)return enter(Phase.MAKE_POTIONS,s,"Potion ingredients required");
    return enter(Phase.HUNT_DEMIBOSSES,s,"Potion target observed");
   case MAKE_POTIONS:
    if(s.potions<2)return action(controlReady,ActionKind.CRAFT,"Make verified Gauntlet potion",420);
    return enter(Phase.HUNT_DEMIBOSSES,s,"Potions observed");
   case HUNT_DEMIBOSSES:
    if(s.weaponTier<3)return action(controlReady,ActionKind.ATTACK,"Hunt verified demi-boss for missing weapon frame",430);
    return enter(Phase.UPGRADE_WEAPONS,s,"Weapon components observed");
   case UPGRADE_WEAPONS:
    if(s.weaponTier<3)return action(controlReady,ActionKind.CRAFT,"Upgrade preferred weapon",430);
    return enter(Phase.FINAL_RESOURCE_CHECK,s,"Weapon target observed");
   case FINAL_RESOURCE_CHECK:
    if(s.food<8||s.potions<1)return enter(Phase.EXPLORE_FOR_RESOURCES,s,"Final ledger below safe target");
    return enter(Phase.ENTER_HUNLLEF,s,"Final supplies observed");
   case ENTER_HUNLLEF:
    if(!s.bossRoom)return action(controlReady,ActionKind.INTERACT,"Enter verified Hunllef door",700);
    return enter(Phase.HUNLLEF_COMBAT,s,"Boss room observed");
   case HUNLLEF_COMBAT:
    if(s.hunllefDead||s.rewardRoom)return enter(Phase.LOOT_CHEST,s,"Hunllef defeat observed");
    if(s.hp>0&&s.hp<=35&&s.food>0)return action(controlReady,ActionKind.EAT,"Emergency HP threshold",850);
    if(s.prayer<=20&&s.potions>0)return action(controlReady,ActionKind.DRINK,"Emergency prayer threshold",820);
    return action(controlReady,ActionKind.ATTACK,"Reactive Hunllef attack opportunity",300);
   case LOOT_CHEST:
    if(!s.rewardChest&&s.tick>enteredTick)return enter(Phase.EXIT_INSTANCE,s,"Reward chest interaction acknowledged");
    if(!s.rewardChest)return Decision.of(ActionKind.OBSERVE,"Waiting for verified reward chest",650);
    return action(controlReady,ActionKind.LOOT,"Loot reward chest",700);
   case EXIT_INSTANCE:
    if(!s.gauntletEvidence){phase=Phase.COMPLETE;return Decision.of(ActionKind.STOP,"Gauntlet exit observed; run complete",0);}
    if(!s.exitVisible)return Decision.of(ActionKind.OBSERVE,"Waiting for verified exit",600);
    return action(controlReady,ActionKind.EXIT,"Exit Gauntlet",700);
   default:return Decision.of(ActionKind.OBSERVE,"State has no automatic action",0);
  }
 }
 private Decision enter(Phase next,Snapshot s,String reason){phase=next;enteredTick=s.tick;return Decision.of(ActionKind.OBSERVE,reason,100);}
 private Decision action(boolean ready,ActionKind kind,String reason,int priority){return ready?Decision.of(kind,reason,priority):Decision.of(ActionKind.OBSERVE,"CONTROL GATED: "+reason,priority);}
}
