import json,numpy as np
from nh_starter_env import NHStarterEnv
E=NHStarterEnv(opponent='fixed-melee',max_ticks=128,engine_revision=1)
found=False
try:
 for seed in range(1,25):
  E.reset(seed=seed)
  for _ in range(128):
   obs,_,done,cut,info=E.step(np.array([0,4,0,0,0]))
   real=info['frame']['fighters'][0]['prayer']
   if abs(float(obs[3])-real/99)>1e-5:
    print(json.dumps({'seed':seed,'tick':info['tick'],'real_prayer':real,'encoded_prayer_normalized':float(obs[3]),'expected_normalized':real/99}));found=True;break
   if done or cut:break
  if found:break
finally:E.close()
if not found:print('No natural mismatch encountered; code-path risk remains documented rather than asserted as reproduced.')
