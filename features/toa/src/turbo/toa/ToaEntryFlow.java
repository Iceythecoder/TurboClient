package turbo.toa;

import java.util.List;
import java.util.Locale;

/** Acknowledged Osmumten -> dialogue -> attackable-obelisk entry flow. */
final class ToaEntryFlow {
 enum Stage { READY, WAITING_FOR_START, ACTIVE }

 private Stage stage = Stage.READY;
 private int requestTick = -1000;
 private int lastDialogueCycle = -1000;
 private int menuRequestCycle = -1000;
 private int requests;

 void reset() {
  stage = Stage.READY;
  requestTick = lastDialogueCycle = menuRequestCycle = -1000;
  requests = 0;
 }

 Stage stage() { return stage; }
 boolean ready() { return stage == Stage.READY; }
 boolean waiting() { return stage == Stage.WAITING_FOR_START; }
 boolean active() { return stage == Stage.ACTIVE; }
 int requests() { return requests; }

 boolean awaitingMenu(int cycle) {
  return ready() && menuRequestCycle >= 0 && cycle - menuRequestCycle < 30;
 }

 boolean menuRequestDue(int cycle) {
  return ready() && !awaitingMenu(cycle);
 }

 void menuRequested(int cycle) { menuRequestCycle = cycle; }

 void menuObserved() { menuRequestCycle = -1000; }

 void sentBegin(int tick) {
  stage = Stage.WAITING_FOR_START;
  requestTick = tick;
  menuRequestCycle = -1000;
  requests++;
 }

 boolean dialogueDue(int cycle) {
  return waiting() && cycle - lastDialogueCycle >= 15;
 }

 void sentDialogue(int cycle, int tick) {
  lastDialogueCycle = cycle;
  requestTick = tick;
 }

 boolean retryDue(int tick, boolean beginAvailable) {
  if (waiting() && beginAvailable && tick - requestTick >= 8) {
   stage = Stage.READY;
   return true;
  }
  return false;
 }

 void confirmActive() { stage = Stage.ACTIVE; }

 static int positiveOption(List<String> options) {
  if (options == null) return -1;
  int fallback = -1;
  for (int index = 0; index < options.size(); index++) {
   String text = clean(options.get(index)).replaceFirst("^[1-9][.):]\\s*", "");
   if (text.isEmpty() || text.matches("^(?:no|leave|exit|cancel|abandon|restart)\\b.*")) continue;
   if (fallback < 0) fallback = index + 1;
   if (text.matches(".*\\b(?:begin|start|yes|ready|proceed|continue)\\b.*")) return index + 1;
  }
  return fallback;
 }

 private static String clean(String value) {
  return value == null ? "" : value.replaceAll("<[^>]*>", " ").replace('\u00a0', ' ')
   .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
 }
}
