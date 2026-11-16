package turbo.toa;

import java.util.Collection;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/** Chooses the tile immediately between the obelisk and a charging Warden. */
final class ToaObeliskLane {
 private ToaObeliskLane() {}

 static WorldPoint leftStreamTile(WorldArea obelisk, Collection<WorldArea> chargingWardens) {
  if (obelisk == null) return null;
  int ox = obelisk.getX() + Math.max(0, (obelisk.getWidth() - 1) / 2);
  int oy = obelisk.getY() + Math.max(0, (obelisk.getHeight() - 1) / 2);
  WorldArea left = null;
  int leftX = Integer.MAX_VALUE;
  if (chargingWardens != null) for (WorldArea candidate : chargingWardens) {
   if (candidate == null || candidate.getPlane() != obelisk.getPlane()) continue;
   int centerX = candidate.getX() + Math.max(0, (candidate.getWidth() - 1) / 2);
   if (centerX < leftX) { leftX = centerX; left = candidate; }
  }
  int wx = left == null ? obelisk.getX() - 8 : left.getX() + Math.max(0, (left.getWidth() - 1) / 2);
  int wy = left == null ? oy : left.getY() + Math.max(0, (left.getHeight() - 1) / 2);
  int dx = wx - ox, dy = wy - oy;
  if (Math.abs(dx) >= Math.abs(dy)) {
   int x = dx <= 0 ? obelisk.getX() - 1 : obelisk.getX() + obelisk.getWidth();
   int y = clamp(wy, obelisk.getY(), obelisk.getY() + obelisk.getHeight() - 1);
   return new WorldPoint(x, y, obelisk.getPlane());
  }
  int x = clamp(wx, obelisk.getX(), obelisk.getX() + obelisk.getWidth() - 1);
  int y = dy <= 0 ? obelisk.getY() - 1 : obelisk.getY() + obelisk.getHeight();
  return new WorldPoint(x, y, obelisk.getPlane());
 }

 /** The demonstrated Orion route blocks the east/right orb stream. */
 static WorldPoint rightStreamTile(WorldArea obelisk, Collection<WorldArea> chargingWardens) {
  if (obelisk == null) return null;
  int ox = obelisk.getX() + Math.max(0, (obelisk.getWidth() - 1) / 2);
  int oy = obelisk.getY() + Math.max(0, (obelisk.getHeight() - 1) / 2);
  WorldArea right = null;
  int rightX = Integer.MIN_VALUE;
  if (chargingWardens != null) for (WorldArea candidate : chargingWardens) {
   if (candidate == null || candidate.getPlane() != obelisk.getPlane()) continue;
   int centerX = candidate.getX() + Math.max(0, (candidate.getWidth() - 1) / 2);
   if (centerX > rightX) { rightX = centerX; right = candidate; }
  }
  int wx = right == null ? obelisk.getX() + obelisk.getWidth() + 8
   : right.getX() + Math.max(0, (right.getWidth() - 1) / 2);
  int wy = right == null ? oy : right.getY() + Math.max(0, (right.getHeight() - 1) / 2);
  int dx = wx - ox, dy = wy - oy;
  if (Math.abs(dx) >= Math.abs(dy)) {
   int x = dx >= 0 ? obelisk.getX() + obelisk.getWidth() : obelisk.getX() - 1;
   int y = clamp(wy, obelisk.getY(), obelisk.getY() + obelisk.getHeight() - 1);
   return new WorldPoint(x, y, obelisk.getPlane());
  }
  int x = clamp(wx, obelisk.getX(), obelisk.getX() + obelisk.getWidth() - 1);
  int y = dy >= 0 ? obelisk.getY() + obelisk.getHeight() : obelisk.getY() - 1;
  return new WorldPoint(x, y, obelisk.getPlane());
 }

 private static int clamp(int value, int minimum, int maximum) {
  return Math.max(minimum, Math.min(maximum, value));
 }
}
