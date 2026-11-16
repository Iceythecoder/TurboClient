package owl.nh;
import java.io.*;
import java.util.*;
import static owl.nh.CombatCore.*;
/** Only explicit user-selected saves persist presets. No targets or combat data. */
public final class ConfigManager {
 private static Properties properties(Settings c) {
  Properties p=new Properties();p.setProperty("autoLock",""+c.autoLock);for(Style s:new Style[]{Style.MAGIC,Style.RANGED,Style.MELEE,Style.SPEC}){p.setProperty("gear."+s,join(c.gear.get(s)));p.setProperty("speed."+s,""+c.speeds.get(s));}
  for(Style s:new Style[]{Style.MAGIC,Style.RANGED,Style.MELEE}){p.setProperty("prayer."+s,c.offensive.get(s));p.setProperty("enabled."+s,""+c.enabled.contains(s));}
  for(Protection prayer:new Protection[]{Protection.MAGIC,Protection.RANGED,Protection.MELEE,Protection.NONE})p.setProperty("counter."+prayer,c.counters.get(prayer).name());
  p.setProperty("offensive",""+c.offensiveEnabled);p.setProperty("defensive",""+c.defensiveEnabled);p.setProperty("autoAttack",""+c.autoAttack);p.setProperty("freeze",""+c.freezeEnabled);p.setProperty("spec",""+c.specEnabled);p.setProperty("deadlineTicks",""+c.deadlineTicks);p.setProperty("refreezeTicks",""+c.refreezeTicks);p.setProperty("specHp",""+c.specHp);p.setProperty("minSpec",""+c.minSpec);p.setProperty("specStyle",c.specStyle.name());
  saveMap(p,"enemyWeapon",c.enemyWeapons);saveMap(p,"enemyAnimation",c.enemyAnimations);saveMap(p,"attackAnimation",c.attackAnimations);saveMap(p,"freezeGraphic",c.freezeGraphics);
  return p;
 }
 public static void save(File file,Settings c)throws IOException {try(OutputStream out=new FileOutputStream(file)){properties(c).store(out,"Orion NH explicit preset save; execution arm is never saved");}}
 static String snapshot(Settings c)throws IOException {StringWriter out=new StringWriter();properties(c).store(out,"In-memory companion settings; execution arm excluded");return out.toString();}
 static Settings fromSnapshot(String text)throws IOException {if(text==null||text.length()>200000)throw new IOException("Settings transfer too large");Properties p=new Properties();p.load(new StringReader(text));return parse(p);
 }
 public static Settings load(File file)throws IOException {
  if(file.length()>200000)throw new IOException("Preset file too large");Properties p=new Properties();try(InputStream in=new FileInputStream(file)){p.load(in);}return parse(p);
 }
 private static Settings parse(Properties p)throws IOException {Settings c=new Settings();c.autoLock=Boolean.parseBoolean(p.getProperty("autoLock","true"));
  for(Style s:new Style[]{Style.MAGIC,Style.RANGED,Style.MELEE,Style.SPEC}){c.gear.put(s,ids(p.getProperty("gear."+s,"")));c.speeds.put(s,number(p,"speed."+s,c.speeds.get(s),1,20));}
  for(Style s:new Style[]{Style.MAGIC,Style.RANGED,Style.MELEE}){String prayer=p.getProperty("prayer."+s,c.offensive.get(s));if(!prayer.equals("NONE"))net.runelite.api.Prayer.valueOf(prayer);c.offensive.put(s,prayer);if(!Boolean.parseBoolean(p.getProperty("enabled."+s,"true")))c.enabled.remove(s);}
  for(Protection prayer:new Protection[]{Protection.MAGIC,Protection.RANGED,Protection.MELEE,Protection.NONE}){Style style=Style.valueOf(p.getProperty("counter."+prayer,c.counters.get(prayer).name()));if(style==Style.SPEC||style==Style.UNKNOWN)throw new IOException("Invalid counter style");c.counters.put(prayer,style);}
  c.offensiveEnabled=flag(p,"offensive");c.defensiveEnabled=flag(p,"defensive");c.autoAttack=flag(p,"autoAttack");c.freezeEnabled=flag(p,"freeze");c.specEnabled=flag(p,"spec");c.deadlineTicks=number(p,"deadlineTicks",1,1,3);c.refreezeTicks=number(p,"refreezeTicks",3,0,50);c.specHp=number(p,"specHp",30,1,100);c.minSpec=number(p,"minSpec",50,1,100);c.specStyle=Style.valueOf(p.getProperty("specStyle","MELEE"));if(c.specStyle==Style.SPEC||c.specStyle==Style.UNKNOWN)throw new IOException("Invalid spec style");
  for(String key:p.stringPropertyNames()){if(key.startsWith("enemyWeapon."))c.enemyWeapons.put(suffix(key),Style.valueOf(p.getProperty(key)));if(key.startsWith("enemyAnimation."))c.enemyAnimations.put(suffix(key),Style.valueOf(p.getProperty(key)));if(key.startsWith("attackAnimation."))c.attackAnimations.put(suffix(key),bounded(p.getProperty(key),1,20));if(key.startsWith("freezeGraphic."))c.freezeGraphics.put(suffix(key),bounded(p.getProperty(key),1,100));}
  return c;
 }
 public static List<Integer> ids(String text) {List<Integer> result=new ArrayList<>();if(text.trim().isEmpty())return result;for(String token:text.split("[,\\s]+")){int id=bounded(token,1,Integer.MAX_VALUE);if(result.contains(id))throw new IllegalArgumentException("Duplicate item ID");result.add(id);}if(result.size()>14)throw new IllegalArgumentException("At most 14 equipped items");return result;}
 static boolean flag(Properties p,String key){return Boolean.parseBoolean(p.getProperty(key,"false"));}
 static int suffix(String key){return bounded(key.substring(key.indexOf('.')+1),0,Integer.MAX_VALUE);}
 static int number(Properties p,String k,int value,int lo,int hi){return bounded(p.getProperty(k,""+value),lo,hi);}
 static int bounded(String value,int lo,int hi){int n=Integer.parseInt(value);if(n<lo||n>hi)throw new IllegalArgumentException("Value out of range: "+value);return n;}
 static String join(List<Integer> values){StringJoiner j=new StringJoiner(",");for(int n:values)j.add(""+n);return j.toString();}
 static void saveMap(Properties p,String prefix,Map<Integer,?> values){for(Map.Entry<Integer,?> e:values.entrySet())p.setProperty(prefix+"."+e.getKey(),e.getValue().toString());}
}
