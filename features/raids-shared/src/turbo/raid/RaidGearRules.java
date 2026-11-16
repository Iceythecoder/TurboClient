package turbo.raid;

import java.util.Locale;

/** Shared equipment compatibility rules for raid gear presets. */
public final class RaidGearRules {
 private RaidGearRules() {}

 /** Elder maul is two-handed, so these named off-hand families cannot share its preset. */
 public static boolean isOffhandName(String value) {
  String name = value == null ? "" : value.replaceAll("<[^>]*>", " ")
   .replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
  return name.contains("defender") || name.contains("shield") || name.contains("ward")
   || name.contains("buckler") || name.contains("tome") || name.contains("book");
 }
}
