"""Read-only rules preview against a seeded native simulator reset."""
import argparse
from dataclasses import asdict
import json
from starting_rules import StartingRules,DEFAULT_RULES
from nh_starter_env import NHStarterEnv

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--rules",default=str(DEFAULT_RULES))
    p.add_argument("--seed",type=int,default=9200)
    p.add_argument("--hp",type=int,default=99)
    p.add_argument("--prayer",type=int,default=99)
    p.add_argument("--magic",type=int,default=99)
    args=p.parse_args()
    rules=StartingRules(args.rules)
    env=NHStarterEnv(max_ticks=64)
    try:
        _,info=env.reset(seed=args.seed,options={"hp":[args.hp,99],"prayer":[args.prayer,99],"magic":[args.magic,99]})
        print(json.dumps(asdict(rules.advise(info)),indent=2))
    finally:
        env.close()
if __name__=="__main__":main()
