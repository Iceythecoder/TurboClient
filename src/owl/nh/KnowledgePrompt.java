package owl.nh;

import java.util.*;
import java.util.regex.*;
import static owl.nh.CombatCore.*;

/** A constrained information parser, not an LLM. Never executes pasted commands/code. */
public final class KnowledgePrompt {
 public static final String EXAMPLE="# IDs below are examples: replace with verified Orion IDs.\nweapon 1234 = RANGED\nenemy animation 5678 = MAGIC\nattack animation 810 = 5\nfreeze graphic 369 = 32\ncounter RANGED = MAGIC\nmagic speed = 5\nmagic prayer = AUGURY\n# Optional: magic gear = 1001,1002,1003\nnote: My magic setup is for countering ranged protection.\n";
 public static final class Preview {
  public final Settings settings;public final List<String> applied=new ArrayList<>(),unrecognized=new ArrayList<>(),notes=new ArrayList<>();
  Preview(Settings settings){this.settings=settings;}
  public String summary(){return "Recognized configuration entries: "+applied.size()+"\n"+String.join("\n",applied)+"\n\nNotes (memory only; not model training): "+notes.size()+"\n\nUnrecognized/invalid lines: "+unrecognized.size()+"\n"+String.join("\n",unrecognized);}
 }
 public static Preview parse(String text,Settings current) {
  if(text.length()>65536)throw new IllegalArgumentException("Prompt is limited to 64 KB");
  Settings cloned=copy(current);Preview result=new Preview(cloned);int lineNumber=0;
  for(String original:text.split("\\R")) {
   lineNumber++;String line=original.trim();if(line.isEmpty()||line.startsWith("#"))continue;
   if(line.toLowerCase(Locale.ROOT).startsWith("note:")){result.notes.add(line.substring(5).trim());continue;}
   try {
    Matcher m;
    if((m=match("(?:weapon|enemy weapon)\\s+(\\d+)\\s*(?:=|:)\\s*(magic|ranged|range|melee)",line))!=null)cloned.enemyWeapons.put(id(m.group(1)),style(m.group(2)));
    else if((m=match("enemy animation\\s+(\\d+)\\s*(?:=|:)\\s*(magic|ranged|range|melee)",line))!=null)cloned.enemyAnimations.put(id(m.group(1)),style(m.group(2)));
    else if((m=match("(?:attack|local attack) animation\\s+(\\d+)\\s*(?:=|:)\\s*(\\d+)",line))!=null)cloned.attackAnimations.put(id(m.group(1)),number(m.group(2),1,20));
    else if((m=match("freeze graphic\\s+(\\d+)\\s*(?:=|:)\\s*(\\d+)",line))!=null)cloned.freezeGraphics.put(id(m.group(1)),number(m.group(2),1,100));
    else if((m=match("counter\\s+(magic|ranged|range|melee|none)\\s*(?:=|:)\\s*(magic|ranged|range|melee)",line))!=null)cloned.counters.put(Protection.valueOf(m.group(1).equalsIgnoreCase("range")?"RANGED":m.group(1).toUpperCase(Locale.ROOT)),style(m.group(2)));
    else if((m=match("(magic|ranged|range|melee|spec) gear\\s*(?:=|:)\\s*([0-9,\\s]*)",line))!=null)cloned.gear.put(style(m.group(1)),ConfigManager.ids(m.group(2)));
    else if((m=match("(magic|ranged|range|melee|spec) speed\\s*(?:=|:)\\s*(\\d+)",line))!=null)cloned.speeds.put(style(m.group(1)),number(m.group(2),1,20));
    else if((m=match("(magic|ranged|range|melee) prayer\\s*(?:=|:)\\s*([A-Z_]+)",line))!=null){String prayer=m.group(2).toUpperCase(Locale.ROOT);if(!prayer.equals("NONE"))net.runelite.api.Prayer.valueOf(prayer);cloned.offensive.put(style(m.group(1)),prayer);}
    else {result.unrecognized.add("Line "+lineNumber+": "+line);continue;}
    result.applied.add(line);
   }catch(RuntimeException invalid){result.unrecognized.add("Line "+lineNumber+": "+line+" ("+invalid.getMessage()+")");}
  }
  return result;
 }
 static Settings copy(Settings source) {
  Settings c=new Settings();c.autoLock=source.autoLock;for(Style style:source.gear.keySet())c.gear.put(style,new ArrayList<>(source.gear.get(style)));
  c.offensive.putAll(source.offensive);c.counters.putAll(source.counters);c.speeds.putAll(source.speeds);c.enemyWeapons.putAll(source.enemyWeapons);c.enemyAnimations.putAll(source.enemyAnimations);c.attackAnimations.putAll(source.attackAnimations);c.freezeGraphics.putAll(source.freezeGraphics);c.enabled=source.enabled.clone();
  c.offensiveEnabled=source.offensiveEnabled;c.defensiveEnabled=source.defensiveEnabled;c.specEnabled=source.specEnabled;c.freezeEnabled=source.freezeEnabled;c.autoAttack=source.autoAttack;c.deadlineTicks=source.deadlineTicks;c.minRemainingMs=source.minRemainingMs;c.refreezeTicks=source.refreezeTicks;c.specHp=source.specHp;c.minSpec=source.minSpec;c.specStyle=source.specStyle;return c;
 }
 static Matcher match(String regex,String value){Matcher m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE).matcher(value);return m.matches()?m:null;}
 static Style style(String value){return Style.valueOf(value.equalsIgnoreCase("range")?"RANGED":value.toUpperCase(Locale.ROOT));}
 static int id(String value){return number(value,0,Integer.MAX_VALUE);}
 static int number(String value,int lo,int hi){int n=Integer.parseInt(value);if(n<lo||n>hi)throw new IllegalArgumentException("value out of range");return n;}
}
