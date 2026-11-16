package owl.nh;
/** A toggle request is not activation; allow its acknowledgement before retrying. */
final class OlmSpecActivation {
 private int interfaceSince=-1;private int enabledCycle=-1,hitTick=-1,hitEnergy,spent,unconfirmed;private int requestedAt=-1000;private boolean waiting;private int requests,confirmations;
 void reset(){interfaceSince=-1;enabledCycle=hitTick=-1;spent=unconfirmed=0;requestedAt=-1000;waiting=false;requests=confirmations=0;}
 void observe(boolean enabled){if(enabled&&waiting){confirmations++;waiting=false;}}
 static boolean allowedPhase(OlmStrategy.Phase phase){return phase!=OlmStrategy.Phase.FINAL_HANDS&&phase!=OlmStrategy.Phase.HEAD;}

 boolean interfaceReady(int cycle,boolean ready){if(!ready){interfaceSince=-1;return false;}if(interfaceSince<0){interfaceSince=cycle;return false;}return cycle-interfaceSince>=2;}
 void cancelPending(){hitTick=-1;enabledCycle=-1;waiting=false;}
 boolean mayRequest(int tick){return tick-requestedAt>=3;}
 boolean activationSettled(int cycle,boolean enabled){if(!enabled){enabledCycle=-1;return false;}if(enabledCycle<0){enabledCycle=cycle;return false;}return cycle-enabledCycle>=2;}
 void attackRequested(int tick,int energy){hitTick=tick;hitEnergy=energy;}
 boolean awaitingHit(int tick,int energy){if(hitTick<0)return false;if(energy<hitEnergy){spent++;hitTick=-1;enabledCycle=-1;return false;}if(tick-hitTick>=3){unconfirmed++;hitTick=-1;return false;}return true;}
 void requested(int tick){interfaceSince=-1;enabledCycle=-1;requestedAt=tick;waiting=true;requests++;}
 java.util.Map<String,Integer> diagnostics(){java.util.Map<String,Integer> out=new java.util.LinkedHashMap<>();out.put("energySpendObserved",spent);out.put("unconfirmedSpecialAttacks",unconfirmed);out.put("activationRequests",requests);out.put("observedActivations",confirmations);return out;}
}
