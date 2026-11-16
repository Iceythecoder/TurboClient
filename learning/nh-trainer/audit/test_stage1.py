"""Stage 1 acceptance tests use the real local simulator, not mocks."""
import json,tempfile,unittest
from pathlib import Path
import numpy as np
from nh_starter_env import NHStarterEnv
from fight_trace import replay,read_trace

class Stage1Tests(unittest.TestCase):
 def setUp(self):self.env=NHStarterEnv(max_ticks=64)
 def tearDown(self):self.env.close()
 def action(self,combat=0,prayer=5,move=0,supply=0,gear=0):return np.array([combat,prayer,move,supply,gear])
 def reasons(self,info):return [r['reason'] for r in info['transition']['operations'] if r['side']==0]
 def test_observation_matches_real_resources_and_cooldown(self):
  obs,info=self.env.reset(seed=7,options={'prayer':[0,99],'magic':[80,99],'cooldown':[3,0]})
  self.assertEqual(obs[3],0);self.assertEqual(obs[9],0)
  self.assertEqual(info['structured']['fighters'][0]['prayer'],0)
  self.assertEqual(info['structured']['fighters'][0]['cooldown'],3)
  self.assertFalse(info['legal_action_mask']['channels'][0][1])
  self.assertEqual(info['legal_action_mask']['channels'][1],[False]*5+[True])
 def test_movement_budget_and_feedback(self):
  _,info=self.env.reset(seed=91);start=info['frame']['fighters'][0]['x']
  m=self.env.descriptor['actions']['movement'].index('move_e2')
  obs,_,_,_,info=self.env.step(self.action(3,move=m))
  self.assertEqual(info['frame']['fighters'][0]['x']-start,2)
  self.assertEqual(info['structured']['fighters'][0]['movement'],[2,0])
  self.assertEqual(obs[16],1);self.assertAlmostEqual(float(obs[27]),.5)
  self.assertIn('out_of_range',self.reasons(info))
 def test_invalid_spell_and_target_never_execute(self):
  self.env.reset(seed=8,options={'magic':[93,99]})
  _,_,_,_,info=self.env.step(self.action(1))
  self.assertIn('spell_level',self.reasons(info))
  self.assertFalse(any(a['side']==0 for a in info['frame']['attacks']))
  self.env.reset(seed=8,options={'target_valid':False})
  _,_,_,_,info=self.env.step(self.action(2))
  self.assertIn('invalid_target',self.reasons(info));self.assertFalse(info['frame']['attacks'])
 def test_no_silent_ags_rewrite_and_idempotent_special(self):
  self.env.reset(seed=9)
  _,_,_,_,info=self.env.step(self.action(4))
  self.assertEqual(info['transition']['decoded']['specIntent'],'spec_armadyl_godsword')
  self.assertIn('equipment_block',self.reasons(info))
  self.env.reset(seed=9,options={'cooldown':[4,0]})
  self.env.step(self.action(supply=1))
  moves=self.env.descriptor['actions']['movement']
  self.env.step(self.action(move=moves.index('move_e2')))
  self.env.step(self.action(move=moves.index('move_e1')))
  enabled=0;noops=0;executed=0
  for _ in range(8):
   _,_,_,_,info=self.env.step(self.action(4))
   ops=[r for r in info['transition']['operations'] if r['side']==0 and r['operation']=='special']
   enabled+=sum(r['status']=='accepted' for r in ops);noops+=sum(r['reason']=='already_enabled' for r in ops)
   executed+=sum(a['side']==0 and a['special'] for a in info['frame']['attacks'])
  self.assertGreater(noops,0);self.assertGreater(executed,0)
 def test_prayer_cost_depletion_and_restore(self):
  self.env.reset(seed=10,options={'prayer':[1,99]})
  for _ in range(8):_,_,_,_,info=self.env.step(self.action(prayer=0))
  self.assertEqual(info['structured']['fighters'][0]['prayer'],0)
  self.assertEqual(info['structured']['fighters'][0]['active_prayers'],[])
  obs,_,_,_,info=self.env.step(self.action(supply=4))
  self.assertGreater(info['structured']['fighters'][0]['prayer'],0)
  self.assertAlmostEqual(float(obs[3]),info['structured']['fighters'][0]['prayer']/99,places=6)
 def test_stale_and_invalid_requests_do_not_advance(self):
  self.env.reset(seed=11);tick=self.env.current_tick
  with self.assertRaisesRegex(RuntimeError,'stale_tick'):self.env._request({'command':'step','action':[0,5,0,0,0],'expectedTick':99})
  with self.assertRaises(ValueError):self.env.step(np.array([-1,5,0,0,0]))
  with self.assertRaisesRegex(RuntimeError,'stale_target_generation'):self.env._request({'command':'step','action':[0,5,0,0,0],'episode':99})
  self.assertEqual(self.env.current_tick,tick)
 def test_opt_in_trace_replay_and_tamper_detection(self):
  parent=Path(__file__).resolve().parents[3]/'build/stage1-test-temp';parent.mkdir(parents=True,exist_ok=True)
  with tempfile.TemporaryDirectory(dir=parent) as folder:
   path=Path(folder)/'fight.jsonl';env=NHStarterEnv(max_ticks=32,trace_output=path)
   try:
    for seed in [100,200]:
     env.reset(seed=seed)
     for _ in range(12):env.step(self.action(2,prayer=0))
   finally:env.close()
   result=replay(path);self.assertEqual(result['steps'],24);self.assertEqual(result['episodes'],2)
   rows=path.read_text().splitlines();row=json.loads(rows[2]);row['action'][0]=1;rows[2]=json.dumps(row);path.write_text('\n'.join(rows)+'\n')
   with self.assertRaisesRegex(ValueError,'integrity'):read_trace(path)
 def test_reset_validation_is_atomic(self):
  self.env.reset(seed=19);self.env.step(self.action());tick=self.env.current_tick
  with self.assertRaisesRegex(RuntimeError,'invalid_starting_option'):self.env._request({'command':'reset','seed':42,'maxTicks':64,'opponent':'fixed-ranged','options':{'private_text':'must not be recorded'}})
  self.env.step(self.action());self.assertEqual(self.env.current_tick,tick+1)
 def test_default_does_not_record(self):
  self.env.reset(seed=1);self.env.step(self.action());self.assertIsNone(self.env.trace)

if __name__=='__main__':unittest.main(verbosity=2)
