package owl.nh;
import java.util.*;

/** Counts observed living-to-dead transitions; absence and repeated zero bars are not kills. */
final class OlmPhaseTracker {
 private static final class Life {boolean alive,counted,damaged,mage;int hp=-1,seen=-1000,targeted=-1000;}
 private final Map<String,Life> lives=new HashMap<>();private int magic,melee,recoveredMagic,recoveredMelee;
 void reset(){lives.clear();magic=0;melee=0;recoveredMagic=0;recoveredMelee=0;}
 void observe(boolean mage,int index,int x,int y,boolean knownAlive,boolean knownDead){
  String key=(mage?"M":"L")+":"+index+":"+x+":"+y;
  Life life=lives.get(key);if(life==null){if(lives.size()>=256)return;life=new Life();lives.put(key,life);}
  life.mage=mage;
  if(knownAlive){if(life.counted){life.hp=-1;life.damaged=false;}life.alive=true;life.counted=false;}
  if(knownDead&&life.alive&&!life.counted){if(mage)magic++;else melee++;life.counted=true;}
 }
 void health(boolean mage,int index,int x,int y,int hp,int tick,boolean targeted){Life life=lives.get((mage?"M":"L")+":"+index+":"+x+":"+y);if(life==null)return;life.seen=tick;if(targeted)life.targeted=tick;if(hp>=0){if(life.hp>hp)life.damaged=true;life.hp=hp;}}
 void finishFrame(int tick){for(Life life:lives.values())if(life.alive&&!life.counted&&life.damaged&&life.hp>=0&&life.hp<=10&&tick>life.seen&&tick-life.seen<=2&&tick-life.targeted<=6){life.counted=true;if(life.mage){magic++;recoveredMagic++;}else{melee++;recoveredMelee++;}}}
 int recoveredMagic(){return recoveredMagic;}int recoveredMelee(){return recoveredMelee;}
 int magicKills(){return magic;}int meleeKills(){return melee;}
 OlmStrategy.Phase phase(boolean anyHandAlive){if(magic>=3&&melee>=3&&!anyHandAlive)return OlmStrategy.Phase.HEAD;if(magic>=2&&melee>=2)return OlmStrategy.Phase.FINAL_HANDS;return OlmStrategy.Phase.REGULAR;}
}
