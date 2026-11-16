package owl.nh;
/** A toggle request is not activation; allow its acknowledgement before retrying. */
final class OlmSpecActivation {
 private int requestedAt=-1000;private boolean waiting;private int requests,confirmations;
 void reset(){requestedAt=-1000;waiting=false;requests=confirmations=0;}
 void observe(boolean enabled){if(enabled&&waiting){confirmations++;waiting=false;}}
 boolean mayRequest(int tick){return tick-requestedAt>=3;}
 void requested(int tick){requestedAt=tick;waiting=true;requests++;}
 java.util.Map<String,Integer> diagnostics(){java.util.Map<String,Integer> out=new java.util.LinkedHashMap<>();out.put("activationRequests",requests);out.put("observedActivations",confirmations);return out;}
}
