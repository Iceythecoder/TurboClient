package turbo.toa;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;

/** Synthetic module lifecycle and state capture; no game startup or inputs. */
public final class ToaModuleTests {
 private static void check(boolean value, String message) {
  if (!value) {
   throw new AssertionError(message);
  }
 }

 public static void main(String[] args) throws Exception {
  Map<String, Object> playerFields = new HashMap<>();
  playerFields.put("getWorldLocation", new WorldPoint(3210, 3210, 0));
  playerFields.put("getLocalLocation", new LocalPoint(1280, 1280));
  Player player = fake(Player.class, playerFields);
  Map<String, Object> fields = new HashMap<>();
  fields.put("getGameState", GameState.LOGIN_SCREEN);
  fields.put("getLocalPlayer", player);
  fields.put("getBaseX", 3200);
  fields.put("getBaseY", 3200);
  fields.put("getNpcs", Collections.emptyList());
  fields.put("getTickCount", 10);
  Client client = fake(Client.class, fields);
  Constructor<ClientThread> constructor = ClientThread.class.getDeclaredConstructor(Client.class);
  constructor.setAccessible(true);
  Path root = Files.createTempDirectory("turbo-toa-module-");
  ToaModule[] module = {null};
  SwingUtilities.invokeAndWait(() -> {
   module[0] = new ToaModule(client, construct(constructor, client), new EventBus(), null, root, () -> {});
   module[0].window().setLocation(-10000, -10000);
  });
  ToaModule value = module[0];
  try {
   boolean blocked = false;
   try {
    value.start();
   } catch (IllegalStateException expected) {
    blocked = true;
   }
   check(blocked, "logout must block observation");
   fields.put("getGameState", GameState.LOGGED_IN);
   value.start();
   value.onGameTick(new GameTick());
   value.onGameTick(new GameTick());
   check(value.summary().contains("ticks: 1"), "duplicate game ticks must not duplicate frames");
   value.mark("Purple orb: explain required protection during walkthrough");
   value.stop("Test stop");
   fields.put("getTickCount", 11);
   value.onGameTick(new GameTick());
   check(value.summary().contains("ticks: 1") && value.summary().contains("notes: 1"),
    "stop retains bounded observations without recording more");
   check(!Files.exists(root.resolve("walkthroughs")), "observations must not auto-save");
   JButton run = find((Container) value.window(), "Run TOA fighter");
   check(run != null && !run.isEnabled(),
    "legacy observer constructor must not expose combat without the visible-input bridge");
   value.discard();
   check(value.summary().contains("ticks: 0"), "discard clears observations");
   System.out.println("ToA module checks passed: independent UI, observer lifecycle, no automatic saves "
    + "and bridge-gated prototype. No game input.");
  } finally {
   value.close();
   Files.delete(root);
  }
  System.exit(0);
 }

 private static ClientThread construct(Constructor<ClientThread> constructor, Client client) {
  try {
   return constructor.newInstance(client);
  } catch (Exception error) {
   throw new RuntimeException(error);
  }
 }

 private static JButton find(Container root, String text) {
  for (Component component : root.getComponents()) {
   if (component instanceof JButton && text.equals(((JButton) component).getText())) {
    return (JButton) component;
   }
   if (component instanceof Container) {
    JButton found = find((Container) component, text);
    if (found != null) {
     return found;
    }
   }
  }
  return null;
 }

 @SuppressWarnings("unchecked")
 private static <T> T fake(Class<T> type, Map<String, Object> values) {
  return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[] {type}, (object, method, arguments) -> {
   if (method.getName().equals("equals")) {
    return object == arguments[0];
   }
   if (method.getName().equals("hashCode")) {
    return System.identityHashCode(object);
   }
   if (values.containsKey(method.getName())) {
    return values.get(method.getName());
   }
   Class<?> result = method.getReturnType();
   if (result == boolean.class) {
    return false;
   }
   if (result == int.class) {
    return 0;
   }
   if (result == long.class) {
    return 0L;
   }
   return null;
  });
 }
}
