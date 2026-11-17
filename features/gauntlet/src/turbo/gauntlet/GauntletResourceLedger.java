package turbo.gauntlet;

import java.util.*;

/** Run-scoped resource ledger. Reservations prevent crafting plans double-spending materials. */
final class GauntletResourceLedger {
 static final class Entry {
  final String key; int required,collected,reserved,spent; String source="unknown"; double confidence;
  Entry(String key){this.key=key;}
  int available(){return Math.max(0,collected-reserved-spent);}
  int shortage(){return Math.max(0,required-(collected-spent));}
 }
 private final Map<String,Entry> entries=new LinkedHashMap<>();
 Entry get(String key){return entries.computeIfAbsent(key,Entry::new);}
 void require(String key,int amount){get(key).required=Math.max(0,amount);}
 void observe(String key,int amount,String source,double confidence){Entry e=get(key);e.collected=Math.max(0,amount);e.source=source==null?"unknown":source;e.confidence=Math.max(0,Math.min(1,confidence));}
 boolean reserve(String key,int amount){Entry e=get(key);if(amount<0||e.available()<amount)return false;e.reserved+=amount;return true;}
 boolean spendReserved(String key,int amount){Entry e=get(key);if(amount<0||e.reserved<amount)return false;e.reserved-=amount;e.spent+=amount;return true;}
 void release(String key,int amount){Entry e=get(key);e.reserved=Math.max(0,e.reserved-Math.max(0,amount));}
 boolean ready(){for(Entry e:entries.values())if(e.shortage()>0)return false;return true;}
 Map<String,Integer> shortages(){Map<String,Integer> result=new LinkedHashMap<>();for(Entry e:entries.values())if(e.shortage()>0)result.put(e.key,e.shortage());return result;}
 void reset(){entries.clear();}
}
