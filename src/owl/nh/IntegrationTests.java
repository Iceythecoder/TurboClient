package owl.nh;
import java.io.*;
import java.lang.reflect.Method;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.unethicalite.api.magic.SpellBook;
import static owl.nh.CombatCore.*;
public final class IntegrationTests {
 public static void main(String[] args)throws Exception {
  // Check the actual installed API without connecting to a server or issuing input.
  Client.class.getMethod("getTickCount");Client.class.getMethod("getLocalPlayer");Client.class.getMethod("getItemContainer",InventoryID.class);Player.class.getMethod("getOverheadIcon");Player.class.getMethod("interact",String.class);Player.class.getMethod("interact",int.class,int.class);GameTick.class.getDeclaredConstructor();SpellBook.Ancient.class.getMethod("canCast");
  Settings s=new Settings();s.autoLock=false;s.gear.get(Style.MAGIC).add(1234);s.attackAnimations.put(4321,5);s.freezeGraphics.put(987,20);File file=File.createTempFile("nh-test-",".properties");
  try{ConfigManager.save(file,s);Settings loaded=ConfigManager.load(file);CoreTests.check(loaded.gear.get(Style.MAGIC).equals(s.gear.get(Style.MAGIC)),"gear persistence");CoreTests.check(loaded.attackAnimations.equals(s.attackAnimations),"animation persistence");CoreTests.check(loaded.freezeGraphics.equals(s.freezeGraphics),"freeze persistence");}finally{if(!file.delete())file.deleteOnExit();}
  CoreTests.check(!s.autoAttack&&!s.specEnabled&&!s.defensiveEnabled,"automation defaults disabled");
  AutoLockTracker tracking=new AutoLockTracker();Object enemy=new Object();tracking.request(enemy,7,1000);CoreTests.check(tracking.confirmed(enemy,7),"attack actor confirmation");CoreTests.check(!tracking.confirmed(new Object(),7),"reused player index rejected");CoreTests.check(!tracking.confirmed(enemy,8),"wrong player index rejected");CoreTests.check(tracking.expired(2000001000L),"unconfirmed intent timeout");tracking.clear();CoreTests.check(tracking.candidate==null,"pending target cleared");
  CoreTests.check(AutoLockTracker.attackMenu(MenuAction.PLAYER_SECOND_OPTION,"Attack",false),"player attack recognized");CoreTests.check(!AutoLockTracker.attackMenu(MenuAction.PLAYER_SECOND_OPTION,"Follow",false),"follow cannot auto-lock");CoreTests.check(!AutoLockTracker.attackMenu(MenuAction.NPC_FIRST_OPTION,"Attack",false),"NPC excluded from PvP lock");CoreTests.check(AutoLockTracker.attackMenu(MenuAction.WIDGET_TARGET_ON_PLAYER,"Cast",true),"barrage target recognized");CoreTests.check(!AutoLockTracker.attackMenu(MenuAction.WIDGET_TARGET_ON_PLAYER,"Cast",false),"noncombat spell excluded");
  boolean invalid=false;try{PrototypePanel.parseStyles("99=SPEC",s.enemyWeapons);}catch(IllegalArgumentException e){invalid=true;}CoreTests.check(invalid,"invalid defensive style rejection");
  System.out.println("Installed API and configuration checks passed. No client login or live actions performed.");
 }
}
