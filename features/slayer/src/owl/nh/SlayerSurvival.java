package owl.nh;
import java.util.Locale;
/** Request/observation bookkeeping; retry spacing is a conservative client policy, not a food mechanic. */
final class SlayerSurvival {
 enum Need { NONE, HEALTH, PRAYER }
 int hp=60,prayer=40,retryTicks=3;private int requestedTick=-1000,beforeAmount,beforeStat,failures;private Need pending=Need.NONE;private int lastAction=-1000;
 static Need need(int hp,int prayer,int hpTarget,int prayerTarget){return hpTarget>0&&hp<hpTarget?Need.HEALTH:prayerTarget>0&&prayer<prayerTarget?Need.PRAYER:Need.NONE;}
 static boolean restore(String name){String s=name==null?"":name.toLowerCase(Locale.ROOT);return s.startsWith("prayer potion(")||s.startsWith("super restore(")||s.startsWith("sanfew serum(")||s.startsWith("revitalisation(")||s.startsWith("revitalisation potion(")||s.startsWith("revitalisation(+)(");}
 void reset(){pending=Need.NONE;requestedTick=-1000;lastAction=-1000;failures=0;}
 void requested(Need need,int tick,int amount,int stat){pending=need;requestedTick=tick;lastAction=tick;beforeAmount=amount;beforeStat=stat;}
 boolean waiting(int tick,int amount,int stat){if(pending==Need.NONE)return tick-lastAction<retryTicks;if(amount<beforeAmount||stat>beforeStat){pending=Need.NONE;failures=0;return tick-lastAction<retryTicks;}if(tick-requestedTick<retryTicks)return true;pending=Need.NONE;failures++;return false;}
 boolean failed(){return failures>=3;}
 Need pending(){return pending;}
}
