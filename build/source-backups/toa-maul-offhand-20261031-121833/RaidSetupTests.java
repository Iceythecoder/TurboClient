package turbo.raid;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RaidSetupTests {
 private static String toa(boolean combat) {
  return "{"
   + "\"schema\":\"turbo-toa-setup-v1\","
   + "\"raid\":\"toa\","
   + "\"observedGear\":{"
   + "\"MAGIC\":[101],\"MELEE\":[102],\"RANGED\":[103],\"SPEC\":[104]},"
   + "\"weapons\":{\"rapierId\":102,\"elderMaulId\":104},"
   + "\"combatImplemented\":" + combat + ","
   + "\"eatAtHp\":80,\"restorePrayerAt\":40,\"potionCooldownTicks\":3,"
   + "\"prePot\":[\"overload\",\"enhance\",\"aid\",\"aid\",\"aid\",\"aid\",\"revitalisation\"],"
   + "\"supplies\":{"
   + "\"overload\":[201],\"enhance\":[202],\"aid\":[203],\"revitalisation\":[204]}"
   + "}";
 }

 public static void main(String[] args) throws Exception {
  Path root = Files.createTempDirectory("raid-setup-isolation-");
  String toa = toa(true);
  String tob = "{\"schema\":\"turbo-tob-setup-v1\","
   + "\"raid\":\"tob\",\"observedGear\":{\"MAGIC\":[456]},"
   + "\"combatImplemented\":false}";
  Path a = root.resolve("toa/instance-data.json");
  Path b = root.resolve("tob/instance-data.json");
  try {
   RaidSetup.write(a, "toa", toa);
   RaidSetup.write(b, "tob", tob);
   if (!Files.readString(a).contains("\"combatImplemented\": true")
    || !Files.readString(a).contains("\"restorePrayerAt\": 40")
    || !Files.readString(b).contains("456")) {
    throw new AssertionError("Separate raid persistence or TOA combat readiness");
   }

   boolean rejected = false;
   try {
    RaidSetup.validate("toa", tob);
   } catch (IllegalArgumentException expected) {
    rejected = true;
   }
   if (!rejected) {
    throw new AssertionError("Cross-raid setup accepted");
   }

   rejected = false;
   try {
    RaidSetup.validate("tob", tob.replace("false", "true"));
   } catch (IllegalArgumentException expected) {
    rejected = true;
   }
   if (!rejected) {
    throw new AssertionError("Unimplemented TOB combat enabled");
   }

   rejected = false;
   try {
    RaidSetup.validate("toa", toa.replace("\"aid\":[203]", "\"aid\":[]"));
   } catch (IllegalArgumentException expected) {
    rejected = true;
   }
   if (!rejected) {
    throw new AssertionError("TOA combat enabled without verified supply families");
   }

   rejected = false;
   try {
    RaidSetup.validate("toa", toa.replace("\"restorePrayerAt\":40", "\"restorePrayerAt\":41"));
   } catch (IllegalArgumentException expected) {
    rejected = true;
   }
   if (!rejected) {
    throw new AssertionError("TOA accepted a non-Chambers prayer guard");
   }

   rejected = false;
   try {
    RaidSetup.validate("toa", toa.replace("\"elderMaulId\":104", "\"elderMaulId\":null"));
   } catch (IllegalArgumentException expected) {
    rejected = true;
   }
   if (!rejected) {
    throw new AssertionError("TOA accepted a combat profile without an observed Elder maul");
   }

   if (RaidSetup.validate("toa", toa(false)).get("combatImplemented").getAsBoolean()) {
    throw new AssertionError("A setup explicitly saved as observation-only became combat ready");
   }
   System.out.println(
    "Raid setup checks passed: independent TOA combat profile, exact resource guards, "
     + "separate persistence, cross-raid rejection and disabled TOB combat.");
  } finally {
   Files.deleteIfExists(a);
   Files.deleteIfExists(b);
   Files.deleteIfExists(a.getParent());
   Files.deleteIfExists(b.getParent());
   Files.deleteIfExists(root);
  }
 }
}
