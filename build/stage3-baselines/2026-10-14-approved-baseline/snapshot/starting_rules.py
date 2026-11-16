"""Stage 2 advisory rules. No execution, training, network, or implicit recording."""
from dataclasses import dataclass
from pathlib import Path
import hashlib
import json
import math

DEFAULT_RULES = Path(__file__).parent / "rules/nh-starter-1.0.0.json"
STYLES = ("magic", "ranged", "melee")
WEAPON_STYLE = {11791: "magic", 21902: "ranged", 12006: "melee", 11802: "melee"}
PRAYERS = {"magic": 0, "ranged": 1, "melee": 2}
GEAR = {"magic": {11791,4091,4093,12831}, "ranged": {21902,4736,4759,12831},
        "melee": {12006,4736,4759,22322}, "ags": {11802,4736,4759}}
COUNTER_DESCRIPTIONS = {"protect_from_missiles":"magic/ice barrage","protect_from_magic":"ranged; melee when reachable and advantageous","protect_from_melee":"magic or ranged by expected damage and switch cost","none":"expected damage with range/cooldown/switch cost"}
HARD = {
    "items": "actual owned NH starter items only",
    "target": "same valid target identity/generation and current tick",
    "attacks": "recheck cooldown, range, spell level, target health and execution confirmation",
    "supplies": "available stock and native food/potion locks",
    "prayers": "required level and positive points; allow explicit OFF",
    "special": "AGS owned, shield stow space, at least 50 energy; enable idempotently",
    "movement": "freeze/collision gates; one explicit movement budget per tick",
    "joint_actions": "validate after movement and resource use; factorized masks do not prove full-vector legality",
}

@dataclass(frozen=True)
class Evidence:
    """Caller-computed visible estimates, NOT sampled future hits.
    Must match the current target generation and tick. Missing estimates stay missing.
    """
    tick: int
    generation: int
    source: str
    expected_damage: dict
    switch_cost: dict
    incoming_max_hit: float | None = None
    ags_expected_damage: float | None = None

@dataclass(frozen=True)
class Advice:
    rules_version: str
    rules_hash: str
    tick: int
    target_id: str
    generation: int
    style: str | None
    defence_prayer: int
    supply: int
    freeze: str
    ags: bool
    reasons: tuple
    execution_required: str = "Revalidate the joint action in the native simulator; advice is not execution confirmation."

class StartingRules:
    def __init__(self, path=DEFAULT_RULES):
        self.config = json.loads(Path(path).read_text(encoding="utf-8"))
        c = self.config
        if set(c) != {"schema_version","version","environment","status","hard_constraints","learnable_preferences","limitations"}:
            raise ValueError("Unknown or missing rules fields")
        if c["schema_version"] != 1 or c["environment"] != "nh-trainer-browser-starter-v2" or c["status"] != "approved":
            raise ValueError("Unsupported or unapproved rules")
        if not isinstance(c["version"],str) or not c["version"].strip():
            raise ValueError("A rules version is required")
        if c["hard_constraints"] != HARD:
            raise ValueError("Hard constraints cannot be changed by preferences")
        p = c["learnable_preferences"]
        required = {
            "counters": {"protect_from_missiles","protect_from_magic","protect_from_melee","none"},
            "eating": {"normal_hp","critical_hp","threat_margin_hp","avoid_waste","allow_overheal_only_for_credible_ko_threat"},
            "restoration": {"prayer_threshold","restore_magic_if_below_barrage_level","restore_before_reboost"},
            "freeze": {"prefer_if_opponent_free_and_distance_requires_control","refreeze_window_ticks","respect_immunity"},
            "ags": {"minimum_energy","opponent_hp_at_most","own_hp_at_least","require_melee_reach","avoid_protect_melee_unless_estimated_ko"},
        }
        if set(p) != set(required):
            raise ValueError("Unknown preference group")
        for group, keys in required.items():
            if not isinstance(p[group],dict) or set(p[group]) != keys:
                raise ValueError("Unknown or missing preference: "+group)
            for name, value in p[group].items():
                if group == "counters":
                    if not isinstance(value,str) or not value: raise ValueError("Invalid counter description")
                elif name in {"normal_hp","critical_hp","threat_margin_hp","prayer_threshold","refreeze_window_ticks","minimum_energy","opponent_hp_at_most","own_hp_at_least"}:
                    if type(value) is not int or not 0 <= value <= 100: raise ValueError("Invalid numeric preference: "+name)
                elif type(value) is not bool: raise ValueError("Invalid boolean preference: "+name)
        if p["eating"]["critical_hp"] > p["eating"]["normal_hp"]:
            raise ValueError("Critical HP must not exceed normal HP")
        if p["ags"]["minimum_energy"] < 50:
            raise ValueError("AGS preference cannot bypass its native 50 energy requirement")
        # Mandatory native immunity/range and safe ordering cannot be disabled.
        if not p["freeze"]["respect_immunity"] or not p["ags"]["require_melee_reach"] or not p["restoration"]["restore_before_reboost"]:
            raise ValueError("Preference conflicts with mandatory safe execution")
        if p["counters"] != COUNTER_DESCRIPTIONS:
            raise ValueError("Counter changes require a new evaluator version")
        self.hash = hashlib.sha256(json.dumps(c,sort_keys=True,separators=(",",":")).encode()).hexdigest()
        self.version = c["version"]
        self.preferences = p

    @staticmethod
    def _snapshot(info):
        s, mask = info["structured"], info["legal_action_mask"]
        if s["tick"] != mask["tick"] or s["target"]["generation"] != mask["target_generation"]:
            raise ValueError("Stale observation or mask")
        channels = mask["channels"]
        if [len(x) for x in channels] != [8,6,26,6,2] or any(type(v) is not bool for x in channels for v in x):
            raise ValueError("Wrong action mask contract")
        me, other = s["fighters"]
        if me["id"] != "local-player" or other["id"] != s["target"]["id"]:
            raise ValueError("Target identity mismatch")
        return s,channels,me,other

    def advise(self, info, evidence=None):
        s,mask,me,other = self._snapshot(info)
        tick,target = s["tick"],s["target"]
        reasons=[]
        if evidence is not None:
            if evidence.tick != tick or evidence.generation != target["generation"] or not evidence.source.strip():
                raise ValueError("Stale or unattributed estimate")
            for values in [evidence.expected_damage,evidence.switch_cost]:
                if set(values) - set(STYLES): raise ValueError("Unknown estimated style")
                for value in values.values():
                    if type(value) not in (float,int) or not math.isfinite(value) or value < 0:
                        raise ValueError("Invalid estimate")
            for value in [evidence.incoming_max_hit,evidence.ags_expected_damage]:
                if value is not None and (type(value) not in (float,int) or not math.isfinite(value) or value < 0):
                    raise ValueError("Invalid threat estimate")
        p=self.preferences
        dist=max(abs(me["x"]-other["x"]),abs(me["y"]-other["y"]))
        owned=set(me["equipment"].values()) | {i["itemId"] for i in me["inventory"] if i}
        visible_style=WEAPON_STYLE.get(other["equipment"].get("weapon"))
        prayer=PRAYERS.get(visible_style,5)
        if not mask[1][prayer]:
            prayer=5
            reasons.append("protection_unavailable")
        elif visible_style is None:
            reasons.append("unknown_visible_weapon; do_not_guess")
        alive=me["hp"]>0
        threat=(evidence is not None and evidence.incoming_max_hit is not None
                and me["hp"] <= evidence.incoming_max_hit+p["eating"]["threat_margin_hp"])
        critical=me["hp"] <= p["eating"]["critical_hp"]
        supply=0
        if alive:
            if critical or (threat and (me["hp"] <= p["eating"]["normal_hp"] or p["eating"]["allow_overheal_only_for_credible_ko_threat"])):
                for candidate in (5,1,3):
                    if mask[3][candidate]:
                        supply=candidate
                        break
                reasons.append("critical_hp" if critical else "credible_visible_ko_threat")
            need_restore=(me["prayer"] <= p["restoration"]["prayer_threshold"]
                          or (p["restoration"]["restore_magic_if_below_barrage_level"] and me["levels"]["magic"] < 94))
            # restore_reboost can be intrinsically legal through combat/bastion alone;
            # it cannot actually restore without Sanfew, so inspect stock as well.
            if not supply and need_restore:
                if me["supplies"].get("sanfew_serum",0)>0 and mask[3][4]:
                    supply=4
                    reasons.append("restore_before_reboost")
                else:
                    reasons.append("restore_unavailable")
            if not supply and me["hp"] <= p["eating"]["normal_hp"]:
                if mask[3][1]:
                    supply=1
                    reasons.append("normal_hp")
            if not supply and not (critical or threat):
                reasons.append("avoid_wasted_food" if p["eating"]["avoid_waste"] else "no_supply_trigger")
        if supply in (1,3,5) and me["hp"] > 77 and p["eating"]["avoid_waste"] and not threat:
            supply=0
            reasons.append("avoid_partial_waste_or_unjustified_overheal")
        style=None
        freeze="none"
        ags=False
        if alive and target["valid"] and other["hp"]>0:
            reachable=[style for i,style in enumerate(STYLES,1) if mask[0][i] and GEAR[style]<=owned]
            overhead=next((x for x in other["active_prayers"] if x.startswith("protect_from_")), "none")
            candidates={"protect_from_missiles":["magic"],"protect_from_magic":["ranged","melee"],
                        "protect_from_melee":["magic","ranged"],"none":list(STYLES)}[overhead]
            choices=[x for x in candidates if x in reachable]
            if not choices:
                reasons.append("preferred_counter_unavailable; hold")
            elif evidence and all(x in evidence.expected_damage and x in evidence.switch_cost for x in choices):
                style=max(choices,key=lambda x:(evidence.expected_damage[x]-evidence.switch_cost[x],-STYLES.index(x)))
                reasons.append("counter_expected_damage_minus_switch_cost")
            elif len(choices)==1:
                style=choices[0]
                reasons.append("only_legal_counter")
            elif overhead=="protect_from_magic" and "ranged" in choices:
                style="ranged"
                reasons.append("default_ranged_counter; melee_advantage_unknown")
            else:
                reasons.append("damage_estimates_required; hold")
            until=other["freeze_until_tick"]
            if until>=tick:
                freeze="prepare" if until-tick+1<=p["freeze"]["refreeze_window_ticks"] else "already_frozen"
            elif until>=0 and until+5>=tick:
                freeze="immune"
            elif dist>1 and p["freeze"]["prefer_if_opponent_free_and_distance_requires_control"]:
                if "magic" in reachable:
                    freeze="prefer_barrage"
                    # Counter rule wins if magic would hit the opponent's protection.
                    if overhead!="protect_from_magic":
                        style="magic"
                        reasons.append("freeze_control")
                    else:
                        reasons.append("counter_over_freeze")
                else:
                    freeze="unavailable"
            a=p["ags"]
            ags=(mask[0][4] and GEAR["ags"]<=owned and dist<=1 and me["special_energy"]>=a["minimum_energy"]
                 and other["hp"]<=a["opponent_hp_at_most"] and me["hp"]>=a["own_hp_at_least"])
            if ags and overhead=="protect_from_melee" and a["avoid_protect_melee_unless_estimated_ko"]:
                ags=bool(evidence and evidence.ags_expected_damage is not None and evidence.ags_expected_damage>=other["hp"])
                if not ags: reasons.append("ags_blocked_by_melee_protection")
            if ags: reasons.append("ags_ko_window")
        else:
            reasons.append("dead_or_invalid_target; hold")
        if supply:
            style=None
            ags=False
            reasons.append("supply_over_attack; native_food_delay_and_brew_recheck")
        return Advice(self.version,self.hash,tick,target["id"],target["generation"],style,prayer,supply,freeze,ags,tuple(reasons))

    def preflight(self,info,advice,action):
        """Reject stale/masked/unsafe proposals; native execution is still mandatory."""
        s,mask,me,other=self._snapshot(info)
        if (advice.rules_hash,advice.tick,advice.target_id,advice.generation)!=(self.hash,s["tick"],s["target"]["id"],s["target"]["generation"]):
            return ("stale_decision",)
        if len(action)!=5 or any(type(v) is not int or not 0<=v<len(mask[i]) for i,v in enumerate(action)):
            return ("invalid_action_shape",)
        reasons=[f"masked_channel_{i}" for i,v in enumerate(action) if not mask[i][v]]
        combat=action[0]
        owned=set(me["equipment"].values())|{x["itemId"] for x in me["inventory"] if x}
        if combat:
            name="ags" if combat==4 else STYLES[(combat-1 if combat<4 else combat-5)]
            if not GEAR[name]<=owned: reasons.append("unowned_loadout")
        if combat in (1,2,3,4):
            if not s["target"]["valid"] or other["hp"]<=0: reasons.append("invalid_target")
            if action[3]: reasons.append("supply_attack_conflict")
        return tuple(reasons)
