"""Deterministic Stage 3 starter teacher; simulator only, no implicit recordings."""
from dataclasses import dataclass
from pathlib import Path
import time
import copy
from collections import Counter
import numpy as np
from nh_starter_env import NHStarterEnv
from starting_rules import StartingRules,Evidence,STYLES,GEAR,WEAPON_STYLE

FIGHTER_VERSION="nh-scripted-starter-1.0.4"
SWITCH_COST_PER_ITEM=0.25
REBOOST_MISSING_LEVELS=5
DEADLINE_MS=600
WORKER=Path(__file__).parent/"browser-source/nh-starter/worker-stage3.mjs"
STYLE_EQUIPMENT={
    "magic":{"weapon":11791,"body":4091,"legs":4093,"shield":12831},
    "ranged":{"weapon":21902,"body":4736,"legs":4759,"shield":12831},
    "melee":{"weapon":12006,"body":4736,"legs":4759,"shield":22322},
}

class StarterTeacherEnv(NHStarterEnv):
    def __init__(self,**kwargs):
        super().__init__(worker_path=WORKER,**kwargs)
    def visible_estimates(self,side=0):
        if self.done:raise RuntimeError("Reset before requesting estimates")
        return self._request({"command":"visible_estimates","expectedTick":self.current_tick,"episode":self.episode,"side":side})

    def step_dual(self,action,opponent_action,inventory_use=None,opponent_inventory_use=None):
        if self.done:raise RuntimeError("Reset before stepping")
        for vector in (action,opponent_action) if opponent_action is not None else (action,):
            if not self.action_space.contains(np.asarray(vector)):raise ValueError("Invalid paired action")
        self.requested_action=list(action)
        command={"command":"step","action":list(action),"expectedTick":self.current_tick,"episode":self.episode}
        if opponent_action is not None:command["opponentAction"]=list(opponent_action)
        if inventory_use is not None:command["inventoryUse"]=inventory_use
        if opponent_inventory_use is not None:command["opponentInventoryUse"]=opponent_inventory_use
        return self._result(self._request(command))

@dataclass(frozen=True)
class Decision:
    action: tuple
    tick: int
    generation: int
    rules_hash: str
    reasons: tuple
    preflight_rejections: tuple
    elapsed_ms: float
    deadline_missed: bool
    controller_side: int=0
    inventory_use: str | None=None

class VisibleEvidence:
    def build(self,info,native):
        s=info["structured"]
        if (native["tick"],native["generation"])!=(s["tick"],s["target"]["generation"]):
            raise ValueError("Stale native estimates")
        equipment=s["fighters"][0]["equipment"]
        costs={style:SWITCH_COST_PER_ITEM*sum(equipment.get(k)!=v for k,v in desired.items())
               for style,desired in STYLE_EQUIPMENT.items()}
        return Evidence(s["tick"],s["target"]["generation"],native["source"],
            {style:row["expectedDamage"] for style,row in native["estimates"].items()},
            costs,native["incoming_max_hit"],native["ags_expected_damage"])

class GearManager:
    def preferred(self,info,evidence):
        me,other=info["structured"]["fighters"]
        owned=set(me["equipment"].values())|{x["itemId"] for x in me["inventory"] if x}
        protection=next((x for x in other["active_prayers"] if x.startswith("protect_from_")),"none")
        options={"protect_from_missiles":["magic"],"protect_from_magic":["ranged","melee"],
                 "protect_from_melee":["magic","ranged"],"none":list(STYLES)}.get(protection,[])
        options=[x for x in options if GEAR[x]<=owned]
        if not options:return None
        return max(options,key=lambda x:(evidence.expected_damage[x]-evidence.switch_cost[x],-STYLES.index(x)))
    def action(self,info,style):
        if style is None:return 0
        equipment=info["structured"]["fighters"][0]["equipment"]
        if all(equipment.get(k)==v for k,v in STYLE_EQUIPMENT[style].items()):return 0
        code=5+STYLES.index(style)
        return code if info["legal_action_mask"]["channels"][0][code] else 0

class MovementPlanner:
    def __init__(self):self.dd_used=set();self.dd_active=None;self.last_reason=None;self.pending_dd=None
    def observe(self,info):
        if self.pending_dd is None:return
        me,other=info["structured"]["fighters"]
        key=(info["structured"]["target"]["generation"],other["freeze_until_tick"],me["attack_timer"]["lastAttackTick"])
        if (key==self.pending_dd and me["x"]==other["x"] and me["y"]==other["y"]
                and other["freeze_until_tick"]>=info["structured"]["tick"]):
            self.dd_used.add(key);self.dd_active=key
        self.pending_dd=None
    def choose(self,info,native,style,descriptor,allow_dd=True):
        self.last_reason=None
        me,other=info["structured"]["fighters"]
        if me["hp"]<=0 or not info["structured"]["target"]["valid"]:return 0
        distance=max(abs(me["x"]-other["x"]),abs(me["y"]-other["y"]))
        tick=info["structured"]["tick"]
        freeze_remaining=other["freeze_until_tick"]-tick+1
        key=(info["structured"]["target"]["generation"],other["freeze_until_tick"],me["attack_timer"]["lastAttackTick"])
        names=descriptor["actions"]["movement"]
        mask=info["legal_action_mask"]["channels"][2]
        if distance==0:
            # The agent must submit ordinary legal input; the game adapter cannot move it for free.
            if me["cooldown"]>1 and freeze_remaining>1:return 0
            exits=[]
            for row in native["movement_candidates"]:
                if row["name"] not in names:continue
                index=names.index(row["name"])
                dx,dy=abs(row["x"]-other["x"]),abs(row["y"]-other["y"])
                if row["valid"] and mask[index] and dx+dy==1:
                    exits.append(index)
            if exits:
                self.last_reason="DD_exit_requested_with_legal_movement; attack_after_execution"
                return max(exits) if info.get("controller_side",0)==1 else min(exits)
            return 0
        if distance>0:self.dd_active=None
        # Approach during cooldown instead of waiting at ranged distance forever.
        # Only verified frozen time and native collision/movement eligibility permit DD.
        if (allow_dd and me["cooldown"]>=2 and freeze_remaining>=max(me["cooldown"]+2,(distance+1)//2+3)
                and key not in self.dd_used):
            candidates=[]
            for row in native["movement_candidates"]:
                if row["name"] not in names:continue
                index=names.index(row["name"])
                d=max(abs(row["x"]-other["x"]),abs(row["y"]-other["y"]))
                if row["valid"] and mask[index] and d<distance:
                    candidates.append((d,index))
            if candidates:
                remaining,index=min(candidates,key=lambda c:(c[0],-c[1] if info.get("controller_side",0)==1 else c[1]))
                if remaining==0:
                    self.pending_dd=key
                    self.last_reason="DD_entry_requested_after_confirmed_freeze; verify_executed_tile"
                    if distance<=1 and mask[names.index("stand_under")]:
                        return names.index("stand_under")
                else:
                    self.last_reason="DD_approach_after_confirmed_freeze"
                return index
        # Safe melee pressure; otherwise keep visible opponents at four tiles.
        desired=1 if style=="melee" else 4
        if distance>=1 and abs(distance-desired)<=1:return 0
        names=descriptor["actions"]["movement"]
        mask=info["legal_action_mask"]["channels"][2]
        candidates=[]
        for row in native["movement_candidates"]:
            if row["name"] not in names:continue
            index=names.index(row["name"])
            d=max(abs(row["x"]-other["x"]),abs(row["y"]-other["y"]))
            if row["valid"] and mask[index] and abs(d-desired)<abs(distance-desired):
                # Avoid stepping underneath the target or out of known attack reach.
                if d>=1:candidates.append((abs(d-desired),index))
        return min(candidates,key=lambda c:(c[0],-c[1] if info.get("controller_side",0)==1 else c[1]))[1] if candidates else 0

class TargetManager:
    def __init__(self):self.lock=None
    def validate(self,info,side):
        target=info["structured"]["target"]
        identity=(side,target["generation"],target["id"])
        if self.lock is None or self.lock[1]!=identity[1]:self.lock=identity
        if self.lock!=identity:raise ValueError("Locked target changed inside episode")
        return target["valid"]

class StateTracker:
    def __init__(self):self.last=None
    def observe(self,info):
        state=info["structured"]
        if self.last and state["target"]["generation"]==self.last[0] and state["tick"]<self.last[1]:
            raise ValueError("Observation tick moved backwards")
        self.last=(state["target"]["generation"],state["tick"])

class TickManager:
    deadline_ms=DEADLINE_MS
    def elapsed(self,started):
        elapsed=(time.perf_counter()-started)*1000
        return elapsed,elapsed>=self.deadline_ms

class PrayerManager:
    def choose(self,advice):return advice.defence_prayer

class SpellManager:
    def validate(self,info,combat):
        if combat==1 and info["structured"]["fighters"][0]["levels"]["magic"]<94:
            raise ValueError("Ice Barrage level unavailable")
        return combat # Native gear/attack operation selects Ice Barrage; no separate spell channel.

class AttackManager:
    def choose(self,advice):
        return 4 if advice.ags else 1+STYLES.index(advice.style) if advice.style else 0

class SupplyManager:
    def __init__(self,boosting_enabled=True):self.boosting_enabled=boosting_enabled
    def choose(self,advice,native):
        code=advice.supply
        ready=native["supply_eligibility"]
        if code in (2,5):
            if ready["food"] and ready["brew"]:return code
            if ready["food"]:return 1
            if ready["brew"]:return 3
            return 0
        return code

    def boost_item(self,info,native,preferred):
        if not self.boosting_enabled:return None
        me=info["structured"]["fighters"][0]
        if me["hp"]<=0 or not info["structured"]["target"]["valid"]:return None
        levels=me["levels"];ready=native["supply_eligibility"];base=native["base_levels"]
        # Brewing already drains actual native stats. Restore those before reboosting.
        if any(levels[k]<base[k] for k in levels):
            return "sanfew_serum" if ready["restore"] else None
        caps=native["potion_targets"]
        combat_missing=any(caps["super_combat"][k]-levels[k]>=REBOOST_MISSING_LEVELS for k in ("attack","strength"))
        ranged_missing=caps["bastion"]["ranged"]-levels["ranged"]>=REBOOST_MISSING_LEVELS
        order=("bastion","super_combat") if preferred=="ranged" else ("super_combat","bastion")
        for item in order:
            missing=combat_missing if item=="super_combat" else ranged_missing
            if missing and ready[item]:return item
        return None

class ActionScheduler:
    def validate(self,env,decision,rules,side=0):
        if decision.controller_side!=side:raise ValueError("Decision belongs to a different controller")
        if (env.current_tick,env.episode)!=(decision.tick,decision.generation):
            raise ValueError("Stale fighter decision")
        if decision.rules_hash!=rules.hash:raise ValueError("Rules changed after decision")

class TelemetryManager:
    def __init__(self):self.operations=Counter();self.rejections=Counter()
    def record(self,info):
        for event in info["transition"]["operations"]:
            if event["side"]!=0:continue
            self.operations[event["operation"]+":"+event["status"]]+=1
            if event["status"]=="rejected":self.rejections[event["reason"]]+=1

class DecisionEngine:
    def __init__(self,rules=None,side=0,boosting_enabled=True):
        self.rules=rules or StartingRules()
        self.evidence=VisibleEvidence()
        self.gear=GearManager()
        self.side=side
        self.target=TargetManager()
        self.state=StateTracker()
        self.tick=TickManager()
        self.prayer=PrayerManager()
        self.spell=SpellManager()
        self.attack=AttackManager()
        self.supply=SupplyManager(boosting_enabled)
        self.scheduler=ActionScheduler()
        self.telemetry=TelemetryManager()
        self.movement=MovementPlanner()
    def decide(self,env,info):
        started=time.perf_counter()
        native=env.visible_estimates(self.side)
        if self.side:info=native["controller_info"]
        self.target.validate(info,self.side)
        self.state.observe(info)
        self.movement.observe(info)
        info=copy.deepcopy(info)
        supplies=info["legal_action_mask"]["channels"][3]
        eligible=native["supply_eligibility"]
        supplies[1]=supplies[1] and eligible["food"]
        supplies[3]=supplies[3] and eligible["brew"]
        supplies[4]=supplies[4] and any(eligible[k] for k in ("restore","super_combat","bastion"))
        for i in (2,5):supplies[i]=supplies[i] and (eligible["food"] or eligible["brew"])
        evidence=self.evidence.build(info,native)
        advice=self.rules.advise(info,evidence)
        reasons=list(advice.reasons)
        mask=info["legal_action_mask"]["channels"]
        me=info["structured"]["fighters"][0]
        action=[0,self.prayer.choose(advice),0,self.supply.choose(advice,native),0]
        preferred=advice.style or self.gear.preferred(info,evidence)
        inventory_use="sanfew_serum" if action[3]==4 else None
        if not action[3]:
            inventory_use=self.supply.boost_item(info,native,preferred)
            if inventory_use:
                action[3]=4
                reasons.append("restore_drained_stats_before_boost" if inventory_use=="sanfew_serum" else "boost_"+inventory_use+"_from_actual_levels")
        action[0]=self.attack.choose(advice)
        if not action[0] and me["cooldown"]<=1:
            action[0]=self.gear.action(info,preferred)
        action[0]=self.spell.validate(info,action[0])
        other=info["structured"]["fighters"][1]
        if me["x"]==other["x"] and me["y"]==other["y"]:
            action[0]=0
            reasons.append("stacked_target; use_legal_movement_when_ready")
        if action[3]:
            # WAIT preserves current equipment while consuming/restoring.
            action[0]=0
        if not action[0] or action[0]>=5:
            action[2]=self.movement.choose(info,native,preferred,env.descriptor,allow_dd=not action[3])
        if action[2] and self.movement.last_reason:
            reasons.append(self.movement.last_reason)
        if action[2]==env.descriptor["actions"]["movement"].index("stand_under"):
            reasons.append("DD_entry_requested; verify_executed_tile")
        # Never pair an attack with movement: attack range stays valid in native recheck.
        rejection=self.rules.preflight(info,advice,action)
        if rejection:
            reasons.append("preflight_hold")
            action=[0,advice.defence_prayer,0,0,0]
            inventory_use=None
        elapsed,missed=self.tick.elapsed(started)
        if missed:
            action=[0,advice.defence_prayer,0,0,0]
            inventory_use=None
            reasons.append("decision_deadline_missed")
        if any(not mask[i][v] for i,v in enumerate(action)):
            raise RuntimeError("Unsafe fallback: mask does not allow hold")
        return Decision(tuple(action),advice.tick,advice.generation,self.rules.hash,
                        tuple(reasons),rejection,elapsed,missed,self.side,inventory_use)
    def execute(self,env,decision,opponent_decision=None):
        self.scheduler.validate(env,decision,self.rules)
        if opponent_decision is not None:
            self.scheduler.validate(env,opponent_decision,self.rules,side=1)
            result=env.step_dual(decision.action,opponent_decision.action,decision.inventory_use,opponent_decision.inventory_use)
        else:
            result=env.step_dual(decision.action,None,decision.inventory_use)
        self.telemetry.record(result[4])
        return result

class ScriptedStarterFighter(DecisionEngine):
    """Public fighter facade composed from the Stage 3 managers."""
