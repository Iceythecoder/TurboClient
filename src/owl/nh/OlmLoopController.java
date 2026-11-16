package owl.nh;
import net.runelite.api.*;
/** Runs on the client thread. No sleeping, packet injection or persisted login secret. */
final class OlmLoopController {
 private final CombatRuntime runtime;final OlmLoopPlan plan=new OlmLoopPlan();
 OlmLoopController(CombatRuntime runtime){this.runtime=runtime;}
 void start(int kills,boolean reconnect,char[] password){plan.start(kills,reconnect,password);}
 void cancel(){plan.cancel();}
 void retryInterruptedRaid(){if(plan.active){plan.next=true;runtime.olm.status="Interrupted fight reset; retrying without counting a kill. "+plan.progress();}}
 void completedRaid(){if(!plan.active)return;plan.completedRaid();runtime.olm.status=plan.active?"Cycle complete: "+plan.progress()+"; preparing next attempt.":"Requested cycles finished: "+plan.progress()+". Actions stopped.";}
 boolean pulse(){
  if(!plan.active)return false;
  Client c=runtime.client;GameState state=c.getGameState();long now=System.nanoTime();
  if(state!=GameState.LOGGED_IN&&state!=GameState.LOADING){
   if(!plan.reconnect){runtime.olm.stop("Disconnected; automatic reconnect was not enabled");return true;}
   plan.disconnected(now);runtime.olm.status="Loop paused: disconnected. "+plan.progress()+". Reconnect waits 30 seconds; focus the game to log in.";
   if(plan.loginRequests>=3&&now>=plan.nextLoginAt){runtime.olm.stop("Three login attempts were not confirmed; manual login required");return true;}
   if(!plan.mayLogin(now)||c.getCanvas()==null||!c.getCanvas().isFocusOwner())return true;
   if(state!=GameState.LOGIN_SCREEN&&state!=GameState.LOGIN_SCREEN_AUTHENTICATOR)return true;
   int index=c.getLoginIndex();if(index==4||index==3){runtime.olm.stop("Login needs manual attention; automatic retries cancelled");return true;}
   if(index==0){c.promptCredentials(false);plan.nextLoginAt=now+1000000000L;return true;}
   if(index!=2){runtime.olm.status="Reconnect waiting for the normal Existing user credentials screen.";return true;}
   if(!"Turbo".equalsIgnoreCase(c.getUsername()))c.setUsername("Turbo");
   c.setPassword(plan.password());net.unethicalite.api.input.Keyboard.sendEnter();net.unethicalite.api.input.Keyboard.sendEnter();plan.submitted(now);runtime.olm.status="Reconnect submitted; waiting for login confirmation. "+plan.progress();return true;
  }
  if(state==GameState.LOADING)return true;
  if(plan.disconnectedAt>=0){if(!runtime.ticks.fresh()||c.getLocalPlayer()==null)return true;plan.connected();if(!plan.next&&!runtime.olm.resumeLoopAfterDisconnect()){runtime.olm.stop("Reconnected outside the expected raid context; progress saved, manual positioning required");return true;}runtime.olm.status="Connection restored; resuming the current attempt. "+plan.progress();}
  if(plan.next){if(runtime.olm.attemptSaving()||!runtime.ticks.fresh()||c.getLocalPlayer()==null)return true;plan.next=false;try{runtime.olm.startMain();}catch(RuntimeException error){runtime.olm.stop("Next cycle could not start; verify setup and saved attempt");}return true;}
  return false;
 }
}



