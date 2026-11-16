package owl.nh;

import com.openosrs.client.game.NPCStats;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.api.Prayer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import static owl.nh.CombatCore.Style;

/** Deterministic combat recommendation derived from Orion's local NPC stat table. */
final class SlayerNpcProfile {
 private static final Map<String,NPCStats> STATS_BY_NAME=loadStatsByName();
 static final class Result {
  final Style attackStyle;final Prayer offensivePrayer,protectionPrayer;
  final long meleeDefence,rangedDefence,magicDefence,meleeThreat,rangedThreat,magicThreat;
  Result(Style attackStyle,Prayer offensivePrayer,Prayer protectionPrayer,long meleeDefence,long rangedDefence,long magicDefence,long meleeThreat,long rangedThreat,long magicThreat){this.attackStyle=attackStyle;this.offensivePrayer=offensivePrayer;this.protectionPrayer=protectionPrayer;this.meleeDefence=meleeDefence;this.rangedDefence=rangedDefence;this.magicDefence=magicDefence;this.meleeThreat=meleeThreat;this.rangedThreat=rangedThreat;this.magicThreat=magicThreat;}
  String summary(){return "Auto profile: use "+attackStyle+" / "+offensivePrayer.name().replace('_',' ')+"; "+protectionPrayer.name().replace('_',' ')+". Defence rolls M/R/M "+meleeDefence+"/"+rangedDefence+"/"+magicDefence+"; threat "+meleeThreat+"/"+rangedThreat+"/"+magicThreat+".";}
 }
 static Result choose(NPCStats stats){
  if(stats==null)return null;
  // The configured melee weapon is a Scythe, so its relevant accuracy check is slash defence.
  long meleeDef=roll(stats.getDefenceLevel()+9,stats.getSlashDef()+64);
  long rangedDef=roll(stats.getDefenceLevel()+9,stats.getRangeDef()+64);
  int magicDefLevel=(int)Math.floor(stats.getMagicLevel()*.7+stats.getDefenceLevel()*.3);
  long magicDef=roll(magicDefLevel+9,stats.getMagicDef()+64);
  Style attack=meleeDef<=rangedDef&&meleeDef<=magicDef?Style.MELEE:rangedDef<=magicDef?Style.RANGED:Style.MAGIC;
  Prayer offense=attack==Style.MELEE?Prayer.PIETY:attack==Style.RANGED?Prayer.RIGOUR:Prayer.AUGURY;

  int meleeAccuracy=Math.max(stats.getStab(),Math.max(stats.getSlash(),stats.getCrush()));
  long meleeThreat=threat(stats.getAttackLevel(),meleeAccuracy,stats.getStrengthLevel()+stats.getBonusStrength());
  long rangedThreat=threat(stats.getRangeLevel(),stats.getRange(),stats.getRangeLevel()+stats.getBonusRangeStrength());
  long magicThreat=threat(stats.getMagicLevel(),stats.getMagic(),stats.getMagicLevel()+stats.getBonusMagicDamage());
  Prayer protection=meleeThreat>=rangedThreat&&meleeThreat>=magicThreat?Prayer.PROTECT_FROM_MELEE:rangedThreat>=magicThreat?Prayer.PROTECT_FROM_MISSILES:Prayer.PROTECT_FROM_MAGIC;
  return new Result(attack,offense,protection,meleeDef,rangedDef,magicDef,meleeThreat,rangedThreat,magicThreat);
 }
 static Result chooseByName(String name){return choose(STATS_BY_NAME.get(normalise(name)));}
 private static Map<String,NPCStats> loadStatsByName(){
  Map<String,NPCStats> result=new HashMap<>();
  try(InputStream stream=NPCStats.class.getClassLoader().getResourceAsStream("npc_stats.json")){
   if(stream==null)return Collections.emptyMap();
   JsonObject root=new JsonParser().parse(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
   for(Map.Entry<String,JsonElement> entry:root.entrySet()){
    NPCStats candidate=NPCStats.NPC_STATS_TYPE_ADAPTER.fromJsonTree(entry.getValue());
    if(candidate==null||normalise(candidate.getName()).isEmpty())continue;
    String key=normalise(candidate.getName());NPCStats current=result.get(key);
    if(current==null||candidate.getCombatLevel()>current.getCombatLevel())result.put(key,candidate);
   }
  }catch(RuntimeException|java.io.IOException ignored){return Collections.emptyMap();}
  return Collections.unmodifiableMap(result);
 }
 private static String normalise(String name){return name==null?"":name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
 private static long roll(int level,int bonus){return (long)Math.max(1,level)*Math.max(1,bonus);}
 private static long threat(int level,int accuracy,int damage){return roll(level+8,accuracy+64)*Math.max(64,damage+64L);}
}
