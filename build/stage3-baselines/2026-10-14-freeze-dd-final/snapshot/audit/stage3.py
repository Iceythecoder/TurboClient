"""Fixed 50-fight Stage 3 baseline. Outputs only to an explicitly chosen NEW directory."""
import argparse
from collections import Counter
from dataclasses import asdict
import hashlib
import json
from pathlib import Path
import statistics
import shutil
from scripted_fighter import StarterTeacherEnv,ScriptedStarterFighter,FIGHTER_VERSION,WORKER,SWITCH_COST_PER_ITEM
from starting_rules import DEFAULT_RULES
from fight_trace import canonical

ROOT=Path(__file__).resolve().parents[1]
OPPONENTS=("stationary-ranged","fixed-magic","fixed-melee","seeded-rapid-switch","seeded-random-prayer","mirror")
SEEDS=tuple(range(9300,9350))

def file_hash(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def fight(seed,opponent,max_ticks=256):
    fighter=ScriptedStarterFighter()
    mirror=ScriptedStarterFighter(side=1) if opponent=="mirror" else None
    env=StarterTeacherEnv(opponent=opponent,max_ticks=max_ticks)
    operations=Counter();rejections=Counter();actions=Counter();movement_actions=Counter()
    damage=[0,0];attacks=[0,0];supply_used=Counter()
    digest=hashlib.sha256()
    durations=[];preflight=deadline=steps=0;reward=0
    wasted=gap=longest_gap=0
    try:
        _,info=env.reset(seed=seed)
        start=info["structured"]["fighters"][0]["supplies"].copy()
        terminal=truncated=False
        while not (terminal or truncated):
            decision=fighter.decide(env,info)
            preflight+=bool(decision.preflight_rejections)
            deadline+=decision.deadline_missed
            durations.append(decision.elapsed_ms)
            actions[env.descriptor["actions"]["combat"][decision.action[0]]]+=1
            movement_actions[env.descriptor["actions"]["movement"][decision.action[2]]]+=1
            other_decision=mirror.decide(env,info) if mirror else None
            obs,r,terminal,truncated,info=fighter.execute(env,decision,other_decision)
            reward+=r;steps+=1
            gap+=1
            for event in info["transition"]["operations"]:
                if event["side"]!=0:continue
                wasted+=bool(event.get("wasted",False))
                operations[event["operation"]+":"+event["status"]]+=1
                if event["status"]=="rejected":rejections[event["reason"]]+=1
                if event["operation"]=="supply" and event["status"]=="accepted":supply_used[event["item"]]+=1
            for event in info["transition"]["confirmed_events"]:
                if event["kind"]=="attack":
                    attacks[0 if event["attackerId"]=="local-player" else 1]+=1
                    if event["attackerId"]=="local-player":
                        longest_gap=max(longest_gap,gap);gap=0
                if event["kind"]=="hitsplat":damage[0 if event["attackerId"]=="local-player" else 1]+=event["damage"]
            # Wall time is excluded from deterministic identity.
            stable=asdict(decision);stable.pop("elapsed_ms")
            digest.update(canonical({"decision":stable,"observation":obs.tolist(),"reward":r,
                "terminated":terminal,"truncated":truncated,"info":info}).encode())
        final=info["structured"]["fighters"][0]["supplies"]
        winner=info["winner"]
        return {"seed":seed,"opponent":opponent,"steps":steps,"outcome":"win" if winner==0 else "loss" if winner==1 else "draw",
            "reward":reward,"hp":info["hp"],"damage_dealt":damage[0],"damage_received":damage[1],
            "confirmed_attacks":attacks,"supply_uses":dict(supply_used),
            "supplies_start":start,"supplies_remaining":final,"actions":dict(actions),
            "movement_actions":dict(movement_actions),"operation_counts":dict(operations),"execution_rejections":dict(rejections),
            "preflight_rejections":preflight,"deadline_misses":deadline,
            "wasted_supply_uses":wasted,"longest_attack_gap_ticks":max(longest_gap,gap),
            "decision_ms_median":statistics.median(durations),"decision_ms_max":max(durations),
            "deterministic_sha256":digest.hexdigest()}
    finally:env.close()

def run(output):
    output=Path(output).resolve()
    output.mkdir(parents=True,exist_ok=False) # never overwrite an earlier baseline
    fighter=ScriptedStarterFighter()
    paths=[ROOT/"scripted_fighter.py",ROOT/"starting_rules.py",ROOT/"nh_starter_env.py",DEFAULT_RULES,WORKER,
           ROOT/"browser-source/nh-starter/worker-stage3.ts",ROOT/"audit/test_stage3.py",ROOT/"browser-source/renderer-provenance.json",Path(__file__)]
    manifest={"schema":"nh-stage3-baseline-v1","fighter_version":FIGHTER_VERSION,"rules_hash":fighter.rules.hash,
        "seeds":list(SEEDS),"opponents":list(OPPONENTS),"opponent_schedule":"opponents[index % len(opponents)]",
        "max_ticks":256,"logical_tick_ms":600,"switch_cost_per_item":SWITCH_COST_PER_ITEM,
        "files":{str(p.relative_to(ROOT)):file_hash(p) for p in paths},
        "privacy":"explicit local synthetic metrics; no player account or screenshot content",
        "limitations":["local scripted opponents only","ordinary visible weapon threat, not special/hidden switches",
                       "AGS expected KO under melee prayer is deliberately unavailable","not an Orion policy"]}
    probe=StarterTeacherEnv()
    try:
        manifest.update(engine_contract=probe.contract,starter_signature=probe.signature,allowed_gear=probe.descriptor["allowed_gear"],
                        dd_policy="confirmed freeze, once per freeze, verified one-tile entry, exit before attack")
    finally:probe.close()
    (output/"manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
    snapshot=output/"snapshot"
    for source in paths:
        destination=snapshot/source.relative_to(ROOT)
        destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(source,destination)
    rows=[]
    with (output/"episodes.jsonl").open("x",encoding="utf-8") as stream:
        for index,seed in enumerate(SEEDS):
            row=fight(seed,OPPONENTS[index%len(OPPONENTS)]);rows.append(row)
            stream.write(canonical(row)+"\n");stream.flush()
            print(f"{index+1}/50 seed={seed} {row['opponent']} {row['outcome']} ticks={row['steps']}",flush=True)
    rejections=Counter()
    for row in rows:rejections.update(row["execution_rejections"])
    summary={"fights":len(rows),"outcomes":dict(Counter(x["outcome"] for x in rows)),
        "steps":sum(x["steps"] for x in rows),"execution_rejections":dict(rejections),
        "preflight_rejections":sum(x["preflight_rejections"] for x in rows),
        "deadline_misses":sum(x["deadline_misses"] for x in rows),
        "confirmed_attacks":sum(x["confirmed_attacks"][0] for x in rows),
        "damage_dealt":sum(x["damage_dealt"] for x in rows),"damage_received":sum(x["damage_received"] for x in rows),
        "decision_ms_max":max(x["decision_ms_max"] for x in rows),
        "wasted_supply_uses":sum(x["wasted_supply_uses"] for x in rows),
        "longest_attack_gap_ticks":max(x["longest_attack_gap_ticks"] for x in rows)}
    (output/"summary.json").write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
    # Integrity manifest plus exclusive creation make saved baselines immutable by convention.
    files=["manifest.json","episodes.jsonl","summary.json"]+[str(p.relative_to(output)).replace("\\\\","/") for p in snapshot.rglob("*") if p.is_file()]
    (output/"integrity.json").write_text(json.dumps({p:file_hash(output/p) for p in files},indent=2)+"\n",encoding="utf-8")
    print(json.dumps(summary,indent=2),flush=True)
    if summary["preflight_rejections"] or summary["execution_rejections"] or summary["deadline_misses"] or summary["wasted_supply_uses"] or summary["longest_attack_gap_ticks"]>64:
        raise RuntimeError("Baseline reliability gate failed; retain diagnostics and fix before promotion")
    print("Stage 3 reliability gate passed.",flush=True)

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output",required=True)
    run(parser.parse_args().output)
