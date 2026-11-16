package owl.nh;
import java.util.Arrays;
/** One recovery per confirmed aid sequence; no guessed refresh clock or sip count. */
final class OlmAidRecovery {
 private final int[] peaks=new int[5];private boolean afterAid,needed;
 void reset(){Arrays.fill(peaks,0);afterAid=needed=false;}
 void consumed(String kind){if("aid".equals(kind))afterAid=true;else if("revitalisation".equals(kind)){afterAid=needed=false;}else if("overload".equals(kind))reset();}
 void observe(boolean overloadActive,int[] base,int[] boosted){
  if(!overloadActive){reset();return;}boolean low=false,full=true,tracked=false;
  for(int i=0;i<peaks.length&&i<base.length&&i<boosted.length;i++){
   peaks[i]=Math.max(peaks[i],boosted[i]);if(peaks[i]<100&&base[i]<99)continue;
   tracked=true;if(boosted[i]<100)low=true;if(boosted[i]<Math.max(100,peaks[i]))full=false;
  }
  if(afterAid&&tracked&&full)afterAid=false;
  needed=afterAid&&low;
 }
 boolean needed(){return needed;}
}
