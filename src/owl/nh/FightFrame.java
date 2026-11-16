package owl.nh;

/** Immutable-by-publication snapshot decoded from the simulator's own state. */
public final class FightFrame {
 public int episode,tick,steps,wins,losses,draws,tickDelayMs;public boolean finished;public double recentMeanReturn;
 public String mode,phase,profile;public Arena arena;public Fighter[] fighters;
 public Attack[] attacks;public Hit[] hits;
 public static final class Attack {public int side,tick,impactTick,specKind,weapon,fromX,fromY,toX,toY;public String style,sequence;public boolean special;}
 public static final class Hit {public int side,damage,sourceTick;}
 public static final class Arena {public int minX,maxX,minY,maxY;}
 public static final class InventorySlot {public int itemId,quantity;}
 public static final class Fighter {
  public int x,y,hp,maxHp,prayer,food,freezeTicks,cooldown,weapon;public double spec,damage;
  public String weaponName,style,overhead,lastIntent;
  public int[] equipment;public InventorySlot[] inventory;
 }
 public void validate() {
  if(arena==null||fighters==null||fighters.length!=2||episode<1||tick<0||steps<0||arena.maxX<arena.minX||arena.maxY<arena.minY||arena.maxX-arena.minX>100||arena.maxY-arena.minY>100)throw new IllegalArgumentException("Invalid simulator frame");
  for(Fighter fighter:fighters)if(fighter==null||fighter.maxHp<=0||!Double.isFinite(fighter.spec)||!Double.isFinite(fighter.damage))throw new IllegalArgumentException("Invalid simulator fighter");
 }
}
