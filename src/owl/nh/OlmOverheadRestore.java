package owl.nh;
import net.runelite.api.Prayer;
/** Restore a previously observed overhead when it is knocked off; incoming threats win. */
final class OlmOverheadRestore {
 private Prayer last;private int retryAt;
 void reset(){last=null;retryAt=0;}
 void observed(Prayer active){if(active!=null){last=active;retryAt=0;}}
 Prayer desired(boolean overheadActive,boolean incoming,int points,int cycle){return !overheadActive&&!incoming&&points>0&&cycle>=retryAt?last:null;}
 void requested(int cycle){retryAt=cycle+30;}
}
