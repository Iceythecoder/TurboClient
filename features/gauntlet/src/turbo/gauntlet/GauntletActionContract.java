package turbo.gauntlet;

import java.util.*;

/**
 * Tick-based action acknowledgement contract. Submitting an action never changes
 * gameplay state; callers advance only after an observed postcondition confirms it.
 */
final class GauntletActionContract {
 enum Status { PENDING, CONFIRMED, RETRY, FAILED, CANCELLED }
 enum Type { INTERACT, MOVE, ATTACK, EQUIP, DRINK, EAT, PRAY, CRAFT, DROP, PICKUP }

 interface Observation { boolean confirmed(String expectedSignal); }

 static final class Request {
  final long id,generation; final Type type; final int submittedTick,deadlineTick,maxRetries;
  final String target,expectedSignal; int retries; Status status=Status.PENDING;
  Request(long id,long generation,Type type,String target,String expectedSignal,int tick,int timeout,int maxRetries){
   this.id=id;this.generation=generation;this.type=Objects.requireNonNull(type);this.target=target==null?"":target;
   this.expectedSignal=expectedSignal==null?"":expectedSignal;this.submittedTick=tick;
   this.deadlineTick=tick+Math.max(1,timeout);this.maxRetries=Math.max(0,maxRetries);
  }
 }

 private long generation=1,nextId=1; private final LinkedHashMap<Long,Request> pending=new LinkedHashMap<>();
 long generation(){return generation;}
 Collection<Request> pending(){return Collections.unmodifiableCollection(pending.values());}
 Request submit(Type type,String target,String expectedSignal,int tick,int timeoutTicks,int retries){
  Request r=new Request(nextId++,generation,type,target,expectedSignal,tick,timeoutTicks,retries);pending.put(r.id,r);return r;
 }
 Status observe(Request r,int tick,Observation observation){
  if(r==null||r.generation!=generation){if(r!=null)r.status=Status.CANCELLED;return Status.CANCELLED;}
  if(r.status==Status.CONFIRMED||r.status==Status.FAILED||r.status==Status.CANCELLED)return r.status;
  if(observation!=null&&observation.confirmed(r.expectedSignal)){r.status=Status.CONFIRMED;pending.remove(r.id);return r.status;}
  if(tick<r.deadlineTick)return Status.PENDING;
  if(r.retries<r.maxRetries){r.retries++;r.status=Status.RETRY;return r.status;}
  r.status=Status.FAILED;pending.remove(r.id);return r.status;
 }
 void markResubmitted(Request r){if(r!=null&&r.generation==generation&&r.status==Status.RETRY)r.status=Status.PENDING;}
 void reset(){generation++;for(Request r:pending.values())r.status=Status.CANCELLED;pending.clear();}
}
