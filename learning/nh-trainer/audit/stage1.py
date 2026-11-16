"""Run diagnostic fights or replay an explicitly saved synthetic trace."""
import argparse,json
from collections import Counter
from pathlib import Path
import sys
import numpy as np
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from nh_starter_env import NHStarterEnv
from fight_trace import replay

def run(output):
 output=Path(output);output.mkdir(parents=True,exist_ok=True)
 trace=output/'diagnostics.jsonl';env=NHStarterEnv(opponent='seeded-rapid-switch',max_ticks=64,trace_output=trace)
 summary={'episodes':0,'steps':0,'confirmed_attacks':0,'requested_rejections':{},'wasted_supplies':0,'results':[]}
 rejects=Counter()
 try:
  for index in range(8):
   seed=9100+index;rng=np.random.default_rng(seed);policy=['hold','magic','ranged','melee'][index%4]
   _,info=env.reset(seed=seed)
   done=False;attacks=0;total=0
   while not done:
    combat={'hold':0,'magic':1,'ranged':2,'melee':3}[policy]
    action=np.array([combat,5 if policy=='hold' else 0,0,0,0])
    _,reward,term,cut,info=env.step(action);done=term or cut;total+=reward;summary['steps']+=1
    attacks+=sum(a['side']==0 for a in info['frame']['attacks'])
    for row in info['transition']['operations']:
     if row['side']==0 and row['status']=='rejected':rejects[row['reason']]+=1
     summary['wasted_supplies']+=row['side']==0 and row.get('wasted',False)
   summary['episodes']+=1;summary['confirmed_attacks']+=attacks
   summary['results'].append({'seed':seed,'diagnostic_policy':policy,'return':total,'winner':info['winner'],'ticks':info['tick'],'agent_attacks':attacks,'hp':info['hp']})
 finally:env.close()
 summary['requested_rejections']=dict(rejects);summary['replay']=replay(trace)
 (output/'summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
 print(json.dumps(summary,indent=2))

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('command',choices=['run','replay']);parser.add_argument('--output');parser.add_argument('--trace');args=parser.parse_args()
 if args.command=='run':run(args.output)
 else:print(json.dumps(replay(args.trace),indent=2))
