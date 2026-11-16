package turbo.toa;

/**
 * Hierarchical encounter state derived only from evidence already visible to the client.
 * Combat policy remains separate so a missing or unverified mechanic cannot invent an action.
 */
final class ToaStateMachine {
 enum State {
  IDLE,
  PREPARING,
  ENTERING_RAID,
  WAITING_FOR_OBELISK,
  OBELISK_COMBAT,
  OBELISK_SPECIAL,
  WARDEN_ACTIVATION,
  WARDEN_COMBAT,
  WARDEN_SPECIAL,
  CORE_EXPOSED,
  CORE_DPS,
  FINAL_WARDEN,
  ENERGY_SIPHON,
  PHANTOM_ATTACK,
  ENRAGE,
  BOSS_DEFEATED,
  REWARD_COLLECTION,
  EXITING,
  RAID_COMPLETED,
  RECOVERY,
  FAILED
 }

 static final class Evidence {
  boolean loggedIn = true;
  boolean preparationComplete;
  boolean entryRequested;
  boolean encounterActive;
  boolean obelisk;
  boolean obeliskSpecial;
  boolean warden;
  boolean wardenSpecial;
  boolean core;
  boolean finalWarden;
  boolean siphon;
  boolean phantom;
  boolean enrage;
  boolean bossDefeated;
  boolean reward;
  boolean exiting;
  boolean complete;
  int hp = 99;
 }

 private State state = State.IDLE;

 State state() { return state; }
 void reset() { state = State.IDLE; }
 void fail() { state = State.FAILED; }

 State update(Evidence e) {
  if (e == null) return state;
  if (!e.loggedIn || e.hp <= 0) return state = State.RECOVERY;
  if (e.complete) return state = State.RAID_COMPLETED;
  if (e.exiting) return state = State.EXITING;
  if (e.reward) return state = State.REWARD_COLLECTION;
  if (e.bossDefeated) return state = State.BOSS_DEFEATED;
  if (e.enrage) return state = State.ENRAGE;
  if (e.phantom) return state = State.PHANTOM_ATTACK;
  if (e.siphon) return state = State.ENERGY_SIPHON;
  if (e.finalWarden) return state = State.FINAL_WARDEN;
  if (e.core) {
   return state = state == State.CORE_EXPOSED || state == State.CORE_DPS
    ? State.CORE_DPS : State.CORE_EXPOSED;
  }
  if (e.wardenSpecial) return state = State.WARDEN_SPECIAL;
  if (e.warden) {
   return state = state == State.WARDEN_ACTIVATION || state == State.WARDEN_COMBAT
    ? State.WARDEN_COMBAT : State.WARDEN_ACTIVATION;
  }
  if (e.obeliskSpecial) return state = State.OBELISK_SPECIAL;
  if (e.obelisk) return state = State.OBELISK_COMBAT;
  if (e.encounterActive) return state = State.WAITING_FOR_OBELISK;
  if (e.entryRequested) return state = State.ENTERING_RAID;
  return state = e.preparationComplete ? State.ENTERING_RAID : State.PREPARING;
 }
}
