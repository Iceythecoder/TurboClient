package owl.nh;

import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

/**
 * Narrow parent-loader contract used by independently packaged raid modules.
 *
 * <p>The implementation owns the single visible-input serializer.  A raid
 * module may request normal game controls through this interface, but it does
 * not receive access to Chambers targets, mechanics, learning data or mutable
 * settings.</p>
 */
public interface RaidInputBridge {
 int tick();
 long tickElapsedMs();
 long observedTickPeriodMs();

 /** A detached, sanitised copy of reusable gear, supplies and guard values. */
 String baselineProfile();

 boolean inventoryAction(int itemId,String... actions);
 boolean activatePrayer(Prayer prayer,int priority);
 boolean activateSpecial();
 boolean move(WorldPoint tile);
 boolean npcAction(NPC npc,String action);
 boolean npcMenuAction(NPC npc,String option);
 boolean objectAction(TileObject object,String action);
 void cancelPendingControl();
 String status();
}
