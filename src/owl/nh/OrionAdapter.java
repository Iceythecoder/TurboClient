package owl.nh;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.*;
import net.unethicalite.api.items.Inventory;
import net.unethicalite.api.magic.Magic;
import net.unethicalite.api.magic.SpellBook;
import net.unethicalite.api.widgets.Prayers;
import net.unethicalite.api.game.Combat;
import static owl.nh.CombatCore.*;

/** All installed-client interaction is isolated here. Uses signatures inspected locally. */
public final class OrionAdapter implements OrionClientAdapter {
 public final Client client;public Settings settings;public final TargetManager targets;public java.util.function.BooleanSupplier executionAllowed=()->false;
 public final OpponentStateTracker opponents=new OpponentStateTracker();
 public final LocalPlayerStateTracker local=new LocalPlayerStateTracker();
 private long confirmedAt=-1;private Object confirmedTarget;private final Map<String,Integer> prayerSent=new HashMap<>();
 public OrionAdapter(Client c,Settings settings,TargetManager target){client=c;this.settings=settings;targets=target;}
 public Set<Integer> container(InventoryID which) {
  Set<Integer> items=new LinkedHashSet<>();ItemContainer container=client.getItemContainer(which);
  if(container!=null)for(Item i:container.getItems())if(i!=null&&i.getId()>0)items.add(i.getId());return items;
 }
 public State snapshot(TargetManager target) {
  State s=new State();s.target=target.actor;s.targetIndex=target.index;s.generation=target.generation;s.tick=client.getTickCount();
  Player p=client.getLocalPlayer();local.read(client,p,s);s.equipped=container(InventoryID.EQUIPMENT);s.inventory=container(InventoryID.INVENTORY);
  if(p!=null&&target.actor instanceof Player)opponents.read((Player)target.actor,p,settings,s);
  s.valid=validActor(s,false);s.canBarrage=canBarrage();return s;
 }
 private boolean validActor(State s,boolean focus) {
  if(client.getGameState()!=GameState.LOGGED_IN||!(s.target instanceof Player)||!targets.matches(s.target,s.targetIndex,s.generation))return false;
  Player self=client.getLocalPlayer(),enemy=(Player)s.target;
  if(self==null||self.isDead()||enemy.isDead()||enemy.getHealthRatio()==0||client.getBoostedSkillLevel(Skill.HITPOINTS)<=0||enemy==self)return false;
  if(focus&&!client.getCanvas().isFocusOwner())return false;
  if(self.getInteracting()!=enemy)return false;
  boolean present=false;for(Player p:client.getPlayers())if(p==enemy&&p.getIndex()==s.targetIndex){present=true;break;}
  if(!present)return false;
  return Arrays.stream(client.getPlayerOptions()).anyMatch(option->option!=null&&option.equalsIgnoreCase("Attack"));
 }
 public boolean valid(State lock) {
  if(!executionAllowed.getAsBoolean()||!validActor(lock,true))return false;
  return opponents.prayer((Player)lock.target)==lock.protection;
 }
 public boolean canBarrage() {
  if(client.getGameState()!=GameState.LOGGED_IN)return false;
  try{return SpellBook.Ancient.ICE_BARRAGE.canCast()&&client.getWidget(WidgetInfo.SPELL_ICE_BARRAGE)!=null;}catch(RuntimeException unavailable){return false;}
 }
 public boolean gearConfirmed(Style style) {return container(InventoryID.EQUIPMENT).containsAll(settings.gear.get(style));}
 public boolean ready(Action a,Decision d) {
  if(a.kind==Kind.SELECT_BARRAGE&&!canBarrage())throw new IllegalStateException("Ice Barrage unavailable (spellbook, level, runes or widget)");
  if(a.kind==Kind.OFFENSIVE_PRAYER||a.kind==Kind.DEFENSIVE_PRAYER) {
   if(a.value==null||a.value.equals("NONE"))return true;
   Prayer p=Prayer.valueOf(a.value);if(client.isPrayerActive(p))return true;
   if(client.getBoostedSkillLevel(Skill.PRAYER)<=0)throw new IllegalStateException("No prayer points");
   if(client.getWidget(p.getWidgetInfo())==null)throw new IllegalStateException("Prayer widget unavailable: "+a.value);
   return prayerSent.getOrDefault(a.value,-1)!=client.getTickCount();
  }
  if(a.kind==Kind.ATTACK) {
   Style style=Style.valueOf(a.value);String prayer=settings.offensive.get(style);
   if(settings.offensiveEnabled&&prayer!=null&&!prayer.equals("NONE")&&!client.isPrayerActive(Prayer.valueOf(prayer)))return false;
   Player enemy=(Player)targets.actor,self=client.getLocalPlayer();int distance=self.getWorldLocation().distanceTo(enemy.getWorldLocation());
   if(style==Style.MELEE&&distance>1)throw new IllegalStateException("Target outside melee distance");
   if(style!=Style.MELEE&&distance>10)throw new IllegalStateException("Target beyond prototype attack range");
   if(style==Style.MAGIC&&!canBarrage())throw new IllegalStateException("Barrage no longer available");
  }
  return true;
 }
 public void perform(Action action,State lock) {
  if(!valid(lock))throw new IllegalStateException("Target no longer valid");
  switch(action.kind) {
   case EQUIP:
    if(container(InventoryID.EQUIPMENT).contains(action.item))return;
    Item item=Inventory.getFirst(action.item);if(item==null)throw new IllegalStateException("Item disappeared: "+action.item);
    if(!item.hasAction("Wield","Wear","Equip"))throw new IllegalStateException("Item is not equippable: "+action.item);
    item.interact("Wield","Wear","Equip");break;
   case OFFENSIVE_PRAYER:case DEFENSIVE_PRAYER:activatePrayer(action.value);break;
   case SELECT_BARRAGE:if(!Magic.isSpellSelected(SpellBook.Ancient.ICE_BARRAGE))Magic.selectSpell(SpellBook.Ancient.ICE_BARRAGE);break;
   case ENABLE_SPEC:
    if(Combat.getSpecEnergy()<settings.minSpec)throw new IllegalStateException("Special energy below threshold");
    if(!Combat.isSpecEnabled())Combat.toggleSpec();break;
   case ATTACK:
    if(action.value.equals("MAGIC")) {
     if(!Magic.isSpellSelected(SpellBook.Ancient.ICE_BARRAGE))throw new IllegalStateException("Barrage selection not confirmed");
     ((Player)lock.target).interact(0,MenuAction.WIDGET_TARGET_ON_PLAYER.getId());
    }else ((Player)lock.target).interact("Attack");break;
   default:throw new IllegalStateException("Unknown action");
  }
 }
 public void activatePrayer(String name) {
  if(!executionAllowed.getAsBoolean()||name==null||name.equals("NONE"))return;Prayer p=Prayer.valueOf(name);
  if(client.isPrayerActive(p))return;
  if(prayerSent.getOrDefault(name,-1)==client.getTickCount())return;
  if(client.getBoostedSkillLevel(Skill.PRAYER)<=0)throw new IllegalStateException("No prayer points");
  if(client.getWidget(p.getWidgetInfo())==null)throw new IllegalStateException("Prayer widget missing");
  Prayers.toggle(p);prayerSent.put(name,client.getTickCount());
 }
 public void confirmedAttack(Object target,long timestamp){confirmedTarget=target;confirmedAt=timestamp;}
 public long lastConfirmedAt(){return confirmedAt;}
 public boolean attackConfirmed(State lock,int sinceTick) {return confirmedTarget==lock.target && confirmedAt>=0 && confirmedAt>=AttackStamp.submittedAt;}
 /** Runtime supplies the submission boundary so older animations cannot confirm new attacks. */
 static final class AttackStamp {static long submittedAt=Long.MAX_VALUE;}
}

final class OpponentStateTracker {
 Protection prayer(Player p) {
  HeadIcon icon=p.getOverheadIcon();if(icon==null)return Protection.NONE;
  switch(icon){case RANGED:return Protection.RANGED;case MAGIC:return Protection.MAGIC;case MELEE:return Protection.MELEE;default:return Protection.UNKNOWN;}
 }
 void read(Player enemy,Player self,Settings c,State s) {
  s.protection=prayer(enemy);s.distance=self.getWorldLocation().distanceTo(enemy.getWorldLocation());
  if(enemy.getHealthScale()>0&&enemy.getHealthRatio()>=0)s.opponentHpPercent=enemy.getHealthRatio()*100/enemy.getHealthScale();
  int weapon=enemy.getPlayerComposition()==null?-1:enemy.getPlayerComposition().getEquipmentId(KitType.WEAPON);
  s.intent=c.enemyAnimations.getOrDefault(enemy.getAnimation(),c.enemyWeapons.getOrDefault(weapon,Style.UNKNOWN));
 }
}
final class LocalPlayerStateTracker {
 void read(Client client,Player p,State s) {
  if(p==null)return;s.localHp=client.getBoostedSkillLevel(Skill.HITPOINTS);s.prayerPoints=client.getBoostedSkillLevel(Skill.PRAYER);s.moving=p.isMoving();
  s.specEnergy=Combat.getSpecEnergy();
 }
}
