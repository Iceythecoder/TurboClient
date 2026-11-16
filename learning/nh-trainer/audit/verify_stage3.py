"""Verify archived Stage 3 baselines without overwriting them."""
import argparse
import json
from pathlib import Path
from stage3 import file_hash,fight,ROOT

def verify(path,replay_seed=None):
    path=Path(path).resolve()
    integrity=json.loads((path/"integrity.json").read_text())
    for relative,expected in integrity.items():
        target=(path/relative).resolve()
        if not target.is_relative_to(path) or not target.is_file() or file_hash(target)!=expected:
            raise ValueError("Baseline integrity verification failed")
    manifest=json.loads((path/"manifest.json").read_text())
    if manifest["schema"]!="nh-stage3-baseline-v1":raise ValueError("Wrong baseline schema")
    for relative,expected in manifest["files"].items():
        target=(path/"snapshot"/relative).resolve()
        if not target.is_relative_to(path/"snapshot") or file_hash(target)!=expected:
            raise ValueError("Archived source does not match baseline manifest")
    rows=[json.loads(line) for line in (path/"episodes.jsonl").read_text().splitlines()]
    if [row["seed"] for row in rows]!=manifest["seeds"] or len(rows)!=50:
        raise ValueError("Wrong seed schedule")
    for index,row in enumerate(rows):
        if row["opponent"]!=manifest["opponents"][index%len(manifest["opponents"])]:
            raise ValueError("Wrong opponent schedule")
    summary=json.loads((path/"summary.json").read_text())
    if (summary["fights"]!=50 or summary["preflight_rejections"] or summary["execution_rejections"]
            or summary["deadline_misses"] or summary["wasted_supply_uses"] or summary["longest_attack_gap_ticks"]>64):
        raise ValueError("Baseline acceptance gate did not pass")
    if replay_seed is not None:
        for relative,expected in manifest["files"].items():
            target=(ROOT/relative).resolve()
            if not target.is_relative_to(ROOT) or file_hash(target)!=expected:
                raise ValueError("Current source differs; replay with the archived source and matching dependencies")
        original=next((row for row in rows if row["seed"]==replay_seed),None)
        if original is None:raise ValueError("Seed not in baseline")
        actual=fight(replay_seed,original["opponent"],manifest["max_ticks"])
        if actual["deterministic_sha256"]!=original["deterministic_sha256"]:
            raise ValueError("Fight decisions or outcomes changed")
    return {"files_verified":len(integrity),"fights":len(rows),"replayed_seed":replay_seed}

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("baseline")
    p.add_argument("--replay-seed",type=int)
    a=p.parse_args()
    print(json.dumps(verify(a.baseline,a.replay_seed),indent=2))
