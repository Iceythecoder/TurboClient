package owl.nh;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.ui.overlay.*;
/** Basic local NPC fighter. No routes, gear changes, learned model or persisted account data. */
final class SlayerController {
 enum MobMode { STANDARD, TORMENTED_DEMONS }
 volatile MobMode mobMode=MobMode.STANDARD;final SlayerSurvival survival=new SlayerSurvival();private int supplyId=-1;
 volatile Prayer offensivePrayer,defensivePrayer;private int lastPrayerRequest=-1000;private Prayer requestedPrayer;
 final CombatRuntime runtime;final SlayerCounter counter=new SlayerCounter();volatile boolean picking;volatile String status="Select an NPC, choose a kill count, then Start.",selected="None";private int type=-1,lastKilled=-1,lastRequest=-1000,retries;private String lastKilledName;private NPC target,manualTarget,hover,preview;private int progressTick=-1000,lastHealth=-1;private int manualTick=-1000;private WorldPoint anchor;final Overlay overlay;private SlayerPanel window;
 SlayerController(CombatRuntime runtime){this.runtime=runtime;overlay=new Overlay(){ {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);}public Dimension render(Graphics2D g){NPC n=picking?hover:counter.running?target:preview;if(n!=null&&!n.isDead()&&n.getHealthRatio()!=0&&n.getConvexHull()!=null){g.setColor(new Color(0,255,150,65));g.fill(n.getConvexHull());g.setColor(Color.GREEN);g.draw(n.getConvexHull());}return null;}};}
 boolean running(){return counter.running;}
 void select(NPC npc){if(npc==null||!npc.hasAction("Attack"))throw new IllegalArgumentException("Choose an NPC with an Attack action");preview=npc;type=npc.getId();selected=npc.getName()+" (type "+type+")";picking=false;hover=null;status="Selected "+selected+". Selection did not issue an attack.";}
 void pick(){runtime.stop("Selecting Slayer NPC");picking=true;status="Hover an NPC to highlight it, then click it to select. F9 cancels.";}
 void lastKilled(){if(lastKilled<0)throw new IllegalStateException("No NPC death observed after your own attack in this session yet");runtime.stop("Selecting last killed NPC");type=lastKilled;selected=lastKilledName+" (type "+type+")";status="Selected last killed NPC: "+selected;}
 void start(int kills){if(mobMode==MobMode.TORMENTED_DEMONS)throw new IllegalStateException("Tormented demons profile needs Orion attack/projectile, special and equipment mappings before it can run. Standard mode has no demon-specific defenses.");if(type<0)throw new IllegalStateException("Select an NPC first");Client c=runtime.client;if(c.getGameState()!=GameState.LOGGED_IN||c.getLocalPlayer()==null)throw new IllegalStateException("Log in and stand near the NPCs first");runtime.stop("Starting Slayer fighter");anchor=c.getLocalPlayer().getWorldLocation();counter.start(type,kills);survival.reset();supplyId=-1;requestedPrayer=null;lastPrayerRequest=-1000;target=null;lastRequest=-1000;retries=0;status="Running: "+selected+". Using current equipment and spell/autocast.";}
 void stop(String reason){boolean was=counter.running||picking;counter.stop();picking=false;hover=null;target=null;if(was)status="Stopped: "+reason+". "+progress();}
 String progress(){if(counter.goal<1)return "No run started";return counter.kills+" / "+counter.goal+" target deaths";}
 void menu(MenuOptionClicked e){if(!picking||e.isConsumed()||e.getMenuAction()==null||!e.getMenuAction().name().contains("NPC"))return;for(NPC npc:runtime.client.getNpcs())if(npc.getIndex()==e.getId()&&npc.hasAction("Attack")){e.consume();select(npc);return;}}
 void animation(Actor actor){Client c=runtime.client;if(actor!=c.getLocalPlayer()||actor==null||actor.getAnimation()<0||!(actor.getInteracting() instanceof NPC))return;NPC n=(NPC)actor.getInteracting();if(!n.isDead()&&n.getHealthRatio()!=0&&n.hasAction("Attack")){manualTarget=n;manualTick=c.getTickCount();if(n==target){counter.attack(n,n.getId());progressTick=c.getTickCount();}}}
 void died(Actor actor){if(actor==runtime.client.getLocalPlayer()){stop("Player died");return;}if(!(actor instanceof NPC))return;NPC npc=(NPC)actor;if(npc==manualTarget&&runtime.client.getTickCount()-manualTick<=50){lastKilled=npc.getId();lastKilledName=npc.getName();manualTarget=null;}boolean was=counter.running;if(occupied(npc))counter.contested(npc);if(counter.died(npc,npc.getId())){status=progress()+(counter.running?"; looking for the next NPC.":". Requested count completed; stopped.");if(npc==target)target=null;}else if(was&&npc==target){target=null;status="Target died without an unambiguous observed local attack; not counted. "+progress();}}
 void spawned(NPC npc){counter.spawned(npc);}
 private boolean occupied(NPC n){for(Player p:runtime.client.getPlayers())if(p!=runtime.client.getLocalPlayer()&&p.getInteracting()==n)return true;return false;}
 void pulse(){try{pulseImpl();}catch(RuntimeException error){stop("Client action unavailable; check the selected NPC and setup");}}
 private void pulseImpl(){Client c=runtime.client;Player self=c.getLocalPlayer();if(picking){hover=null;MenuEntry[] entries=c.getMenuEntries();if(entries!=null)for(int i=entries.length-1;i>=0;i--){NPC n=entries[i].getNpc();if(n!=null&&n.hasAction("Attack")){hover=n;break;}}}if(!counter.running)return;if(c.getGameState()!=GameState.LOGGED_IN||self==null){stop("Logged out or disconnected");return;}if(self.isDead()||c.getBoostedSkillLevel(Skill.HITPOINTS)<=0){stop("Player died");return;}if(anchor==null||self.getWorldLocation().getPlane()!=anchor.getPlane()||self.getWorldLocation().distanceTo(anchor)>16){stop("Left the selected hunting area");return;}if(!runtime.ticks.fresh()||c.getCanvas()==null||!c.getCanvas().isFocusOwner()){status="Paused: focus the game and wait for fresh ticks. "+progress();return;}
  if(preparePrayers())return;
  if(restoreSupplies())return;
  if(target!=null){boolean present=false;for(NPC n:c.getNpcs())if(n==target){present=true;break;}if(!present||target.isDead()||target.getHealthRatio()==0){target=null;}else {if(occupied(target))counter.contested(target);if(target.getHealthRatio()!=lastHealth){lastHealth=target.getHealthRatio();progressTick=c.getTickCount();}if(self.getInteracting()==target){if(c.getTickCount()-progressTick>=20){stop("No observed attack or damage for 20 ticks; check weapon, range and spell/autocast");return;}status="Fighting "+selected+". "+progress();return;}if(self.isMoving()||c.getTickCount()-lastRequest<3)return;if(retries>=3){stop("Attack requests were not confirmed; check range, weapon and autocast");return;}}}
  if(target==null){int distance=17;for(NPC n:c.getNpcs()){if(n.getId()!=type||n.isDead()||n.getHealthRatio()==0||!n.hasAction("Attack")||occupied(n)||n.getWorldLocation().distanceTo(anchor)>12)continue;int d=n.getWorldLocation().distanceTo(self.getWorldLocation());if(d<distance){distance=d;target=n;}}retries=0;progressTick=c.getTickCount();lastHealth=target==null?-1:target.getHealthRatio();if(target==null){status="Waiting for a free "+selected+" within 12 tiles of the starting area. "+progress();return;}}
  if(runtime.combatUi.claim()){target.interact("Attack");lastRequest=c.getTickCount();retries++;status="Attack requested; waiting for observed combat. "+progress();}
 }
 private int amount(int id){int total=0;for(Item item:net.unethicalite.api.items.Inventory.getAll(id))total+=item.getQuantity();return total;}
 private boolean restoreSupplies(){
  Client c=runtime.client;int hp=c.getBoostedSkillLevel(Skill.HITPOINTS),pp=c.getBoostedSkillLevel(Skill.PRAYER);
  SlayerSurvival.Need pending=survival.pending();int observed=pending==SlayerSurvival.Need.PRAYER?pp:hp;
  if(survival.waiting(c.getTickCount(),supplyId<0?0:amount(supplyId),observed)){status="Waiting for supply consumption/cooldown. "+progress();return true;}
  if(survival.failed()){stop("Three supply requests were not confirmed; check the inventory");return true;}
  int hpTarget=Math.min(survival.hp,c.getRealSkillLevel(Skill.HITPOINTS)),ppTarget=Math.min(survival.prayer,c.getRealSkillLevel(Skill.PRAYER));
  SlayerSurvival.Need need=SlayerSurvival.need(hp,pp,hpTarget,ppTarget);if(need==SlayerSurvival.Need.NONE)return false;
  Item item=net.unethicalite.api.items.Inventory.getFirst(i->need==SlayerSurvival.Need.HEALTH?i.hasAction("Eat"):i.hasAction("Drink")&&SlayerSurvival.restore(i.getName()));
  if(item==null){stop(need==SlayerSurvival.Need.HEALTH?"Below HP target with no edible food. Brews are not automatically used.":"Below prayer target with no recognized prayer restore");return true;}
  if(!runtime.combatUi.ready(net.unethicalite.api.widgets.Tab.INVENTORY)){status="Opening Inventory to restore "+(need==SlayerSurvival.Need.HEALTH?"HP":"prayer")+". "+progress();return true;}
  if(!VisibleCombatUi.inventoryVisible(c,item)){status="Waiting for visible supply item.";return true;}
  supplyId=item.getId();survival.requested(need,c.getTickCount(),amount(supplyId),need==SlayerSurvival.Need.HEALTH?hp:pp);item.interact(need==SlayerSurvival.Need.HEALTH?"Eat":"Drink");status="Requested "+(need==SlayerSurvival.Need.HEALTH?"food":"prayer restore")+"; waiting for observed consumption. "+progress();return true;
 }
 boolean preparePrayers(){
  Client c=runtime.client;
  if(c.getBoostedSkillLevel(Skill.PRAYER)<=0)return false;
  Prayer desired=defensivePrayer!=null&&!c.isPrayerActive(defensivePrayer)?defensivePrayer:offensivePrayer!=null&&!c.isPrayerActive(offensivePrayer)?offensivePrayer:null;
  if(desired==null){requestedPrayer=null;return false;}
  if(requestedPrayer==desired&&c.getTickCount()-lastPrayerRequest<2)return false;
  net.runelite.api.widgets.Widget widget=c.getWidget(desired.getWidgetInfo());
  if(!runtime.combatUi.ready(net.unethicalite.api.widgets.Tab.PRAYER,100)){status="Opening Prayer book for "+desired.name().replace('_',' ')+". "+progress();return true;}
  if(!VisibleCombatUi.effectivelyVisible(widget)){requestedPrayer=desired;lastPrayerRequest=c.getTickCount();status="Selected prayer unavailable: "+desired.name().replace('_',' ');return false;}
  net.unethicalite.api.widgets.Prayers.toggle(desired);requestedPrayer=desired;lastPrayerRequest=c.getTickCount();status="Activating "+desired.name().replace('_',' ')+"; waiting for observed activation. "+progress();return true;
 }
 JFrame window(){if(window==null||!window.isDisplayable())window=new SlayerPanel(runtime,this);return window;}
}



