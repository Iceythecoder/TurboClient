package owl.nh;
import java.util.*;
/** Session-only loop state. Credentials never enter profile serialization. */
final class OlmLoopPlan {
 boolean active,reconnect,next;int goal,completed,loginRequests;long disconnectedAt=-1,nextLoginAt;
 private char[] password=new char[0];
 void start(int kills,boolean auto,char[] secret){if(kills<1||kills>1000)throw new IllegalArgumentException("Choose 1-1000 kills");if(auto&&(secret==null||secret.length==0))throw new IllegalArgumentException("Enter the reconnect password for this session");cancel();goal=kills;completed=0;active=true;reconnect=auto;password=secret==null?new char[0]:secret.clone();loginRequests=0;}
 void cancel(){active=false;next=false;reconnect=false;disconnectedAt=-1;Arrays.fill(password,'\0');password=new char[0];}
 void completedRaid(){if(!active)return;completed++;if(completed>=goal)cancel();else next=true;}
 void disconnected(long now){if(disconnectedAt<0){disconnectedAt=now;nextLoginAt=now+30000000000L;loginRequests=0;}}
 boolean mayLogin(long now){return active&&reconnect&&disconnectedAt>=0&&now>=nextLoginAt&&loginRequests<3;}
 void submitted(long now){loginRequests++;nextLoginAt=now+30000000000L;}
 void connected(){disconnectedAt=-1;loginRequests=0;}
 String password(){return new String(password);}
 String progress(){return completed+" / "+goal+" completed cycles";}
}
