package turbo.gauntlet;

/** Authoritative observed attack-cycle tracker; animation alone is never treated as an attack. */
final class HunllefTracker {
 enum Style { MAGIC, RANGED, UNKNOWN }
 enum EventKind { PROJECTILE_ATTACK, STOMP, TORNADO_CAST, PRAYER_DISABLE, OTHER }
 private Style current=Style.UNKNOWN; private int qualifyingAttacks; private int lastEventTick=Integer.MIN_VALUE;
 private int offPrayerHits; private String observedOverhead="UNKNOWN";
 void reset(){current=Style.UNKNOWN;qualifyingAttacks=0;lastEventTick=Integer.MIN_VALUE;offPrayerHits=0;observedOverhead="UNKNOWN";}
 void observeBossAttack(int tick,EventKind kind,Style style){
  if(tick==lastEventTick||kind!=EventKind.PROJECTILE_ATTACK||style==Style.UNKNOWN)return;
  lastEventTick=tick;
  if(current==Style.UNKNOWN||style!=current){current=style;qualifyingAttacks=1;}else qualifyingAttacks++;
 }
 Style currentStyle(){return current;}
 int attacksInStyle(){return qualifyingAttacks;}
 boolean switchExpected(){return qualifyingAttacks>=4;}
 Style expectedNext(){if(!switchExpected())return current;return current==Style.MAGIC?Style.RANGED:current==Style.RANGED?Style.MAGIC:Style.UNKNOWN;}
 String requiredProtection(){return current==Style.MAGIC?"PROTECT_FROM_MAGIC":current==Style.RANGED?"PROTECT_FROM_MISSILES":"UNKNOWN";}
 void observePlayerAttack(boolean correctPrayer,String overhead){
  if(overhead!=null&&!overhead.isEmpty())observedOverhead=overhead;
  if(correctPrayer)offPrayerHits=0;else offPrayerHits++;
 }
 int offPrayerHits(){return offPrayerHits;}
 boolean overheadSwitchExpected(){return offPrayerHits>=6;}
 String observedOverhead(){return observedOverhead;}
}
