from nh_starter_env import NHStarterEnv
import numpy as np,json
E=NHStarterEnv(max_ticks=128,engine_revision=1)
try:
 _,info=E.reset(seed=91)
 start=info['frame']['fighters'][0]['x']
 m=E.descriptor['actions']['movement'].index('move_e2')
 _,_,_,_,info=E.step(np.array([3,1,m,0,0]))
 print(json.dumps({'case':'move_e2_and_melee','requested_tiles':2,'actual_tiles':info['frame']['fighters'][0]['x']-start,'before_x':start,'after_x':info['frame']['fighters'][0]['x']}))
finally:E.close()
