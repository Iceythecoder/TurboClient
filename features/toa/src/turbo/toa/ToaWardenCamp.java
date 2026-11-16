package turbo.toa;

import net.runelite.api.coords.WorldPoint;

/** Stable ranged firing tiles for the opening Warden. */
final class ToaWardenCamp {
 private ToaWardenCamp() {}

 static WorldPoint choose(WorldPoint warden, WorldPoint approach, int attackRange) {
  if (warden == null) return null;
  int distance = Math.max(2, Math.min(Math.max(2, attackRange - 1), 7));
  int dx = approach == null ? 1 : approach.getX() - warden.getX();
  int dy = approach == null ? 0 : approach.getY() - warden.getY();
  int scale = Math.max(Math.abs(dx), Math.abs(dy));
  if (scale == 0) { dx = 1; scale = 1; }
  int x = warden.getX() + (int)Math.round((double)dx * distance / scale);
  int y = warden.getY() + (int)Math.round((double)dy * distance / scale);
  return new WorldPoint(x, y, warden.getPlane());
 }

 /**
  * Mirrors the orb-blocking tile across the live obelisk. This places the
  * player on the opposite stream, facing the guardian which becomes active.
  * If that exact mirror is just outside weapon range, advance only enough to
  * make the target attackable while retaining the same straight lane.
  */
 static WorldPoint opposite(
  WorldPoint obelisk, WorldPoint blockedStream, WorldPoint warden, int attackRange) {
  if (obelisk == null || blockedStream == null || obelisk.getPlane() != blockedStream.getPlane()) return null;
  int x = obelisk.getX() + (obelisk.getX() - blockedStream.getX());
  int y = obelisk.getY() + (obelisk.getY() - blockedStream.getY());
  WorldPoint result = new WorldPoint(x, y, obelisk.getPlane());
  if (warden == null || warden.getPlane() != result.getPlane()) return result;
  int range = Math.max(1, attackRange);
  while (result.distanceTo(warden) > range) {
   int dx = Integer.signum(warden.getX() - result.getX());
   int dy = Integer.signum(warden.getY() - result.getY());
   if (dx == 0 && dy == 0) break;
   result = new WorldPoint(result.getX() + dx, result.getY() + dy, result.getPlane());
  }
  return result;
 }
}
