"""Real simulator tests for the modular Stage 3 fighter."""
import copy
from dataclasses import replace
import unittest
from scripted_fighter import StarterTeacherEnv,ScriptedStarterFighter,VisibleEvidence,MovementPlanner
from nh_starter_env import NHStarterEnv
from stage3 import fight,OPPONENTS,SEEDS

class Stage3Tests(unittest.TestCase):
    def setUp(self):
        self.env=StarterTeacherEnv(max_ticks=64)
        self.fighter=ScriptedStarterFighter()
        _,self.info=self.env.reset(seed=9300)
    def tearDown(self):self.env.close()
    def test_estimates_do_not_advance_or_sample_rng(self):
        original=NHStarterEnv(max_ticks=64)
        try:
            _,original_info=original.reset(seed=9300)
            self.assertEqual(self.info,original_info)
            a=self.env.visible_estimates()
            self.assertEqual(a,self.env.visible_estimates())
            self.assertEqual(a["tick"],0)
            self.assertEqual(set(a["estimates"]),{"magic","ranged","melee"})
            self.assertIsNone(a["ags_expected_damage"])
            for action in ([1,0,0,0,0],[0,1,0,0,0],[2,0,0,0,0]):
                self.assertEqual(self.env.step(action)[4],original.step(action)[4])
        finally:original.close()
    def test_native_evidence_stale_request_rejected(self):
        with self.assertRaises(RuntimeError):
            self.env._request({"command":"visible_estimates","expectedTick":1,"episode":self.env.episode})
        self.assertEqual(self.env.current_tick,0)
        native=self.env.visible_estimates();native["tick"]+=1
        with self.assertRaises(ValueError):VisibleEvidence().build(self.info,native)
    def test_counters_and_actual_supply_priority(self):
        _,info=self.env.reset(seed=9301,options={"hp":[35,99],"prayer":[10,99]})
        d=self.fighter.decide(self.env,info)
        self.assertEqual(d.action[0],0);self.assertEqual(d.action[3],5)
        _,_,_,_,after=self.fighter.execute(self.env,d)
        self.assertGreater(after["hp"][0],35)
        self.assertTrue(any(x["operation"]=="supply" and x["status"]=="accepted" for x in after["transition"]["operations"] if x["side"]==0))
    def test_cooldown_holds_attack_and_freeze_holds_movement(self):
        _,info=self.env.reset(seed=9301,options={"cooldown":[3,0],"freeze":[10,0]})
        d=self.fighter.decide(self.env,info)
        self.assertIn(d.action[0],(0,5,6,7))
        self.assertEqual(d.action[2],0)
        self.assertFalse(d.preflight_rejections)
    def test_stale_decision_and_changed_rules_rejected(self):
        d=self.fighter.decide(self.env,self.info)
        with self.assertRaises(ValueError):self.fighter.execute(self.env,replace(d,generation=d.generation+1))
        with self.assertRaises(ValueError):self.fighter.execute(self.env,replace(d,rules_hash="bad"))
    def test_every_output_masked_and_no_attack_movement_combo(self):
        info=self.info
        for _ in range(64):
            d=self.fighter.decide(self.env,info)
            for index,value in enumerate(d.action):
                self.assertTrue(info["legal_action_mask"]["channels"][index][value])
            if d.action[0] in (1,2,3,4):self.assertEqual(d.action[2],0)
            _,_,terminal,truncated,info=self.fighter.execute(self.env,d)
            if terminal or truncated:break
    def test_seeded_fights_reproduce_decisions_and_events(self):
        for opponent in ("fixed-ranged","seeded-rapid-switch","seeded-random-prayer","mirror"):
            a=fight(9310,opponent,64);b=fight(9310,opponent,64)
            self.assertEqual(a["deterministic_sha256"],b["deterministic_sha256"])
            self.assertEqual(a["execution_rejections"],b["execution_rejections"])
    def test_partial_supply_does_not_request_locked_potion(self):
        _,info=self.env.reset(seed=9312,options={"hp":[35,99]})
        d=self.fighter.decide(self.env,info)
        _,_,_,_,info=self.fighter.execute(self.env,d)
        # Native locks now prohibit another potion; food may become ready first.
        for _ in range(10):
            d=self.fighter.decide(self.env,info)
            _,_,done,truncated,info=self.fighter.execute(self.env,d)
            self.assertFalse(any(x["status"]=="rejected" and x["operation"]=="supply" for x in info["transition"]["operations"] if x["side"]==0))
            if done or truncated:break
    def test_target_identity_and_backwards_tick_fail_closed(self):
        self.fighter.decide(self.env,self.info)
        changed=copy.deepcopy(self.info)
        changed["structured"]["target"]["id"]="different-target"
        with self.assertRaises(ValueError):self.fighter.decide(self.env,changed)
        self.fighter.state.last=(self.env.episode,10)
        with self.assertRaises(ValueError):self.fighter.decide(self.env,self.info)
    def test_cooldown_does_not_repeat_gear_requests(self):
        _,info=self.env.reset(seed=9301,options={"cooldown":[4,0]})
        d=self.fighter.decide(self.env,info)
        self.assertEqual(d.action[0],0)
    def test_mirror_side_decisions_cannot_be_misrouted(self):
        mirror=ScriptedStarterFighter(side=1)
        decision=mirror.decide(self.env,self.info)
        with self.assertRaises(ValueError):self.fighter.execute(self.env,decision)
    def test_baseline_schedule_covers_all_required_opponents(self):
        schedule=[OPPONENTS[i%len(OPPONENTS)] for i in range(len(SEEDS))]
        self.assertEqual(len(schedule),50)
        self.assertEqual(set(schedule),set(OPPONENTS))
        self.assertIn("mirror",schedule);self.assertIn("seeded-random-prayer",schedule)
    def test_ags_is_confirmed_by_native_attack_not_just_intent(self):
        _,info=self.env.reset(seed=9320,options={"hp":[70,35],"freeze":[0,20]})
        moves=self.env.descriptor["actions"]["movement"]
        for index,name in enumerate(("move_e2","move_e1")):
            _,_,_,_,info=self.env.step_dual([0,1,moves.index(name),1 if index==0 else 0,0],[0,5,0,0,0])
        while info["structured"]["fighters"][0]["cooldown"]>0:
            _,_,_,_,info=self.env.step_dual([0,1,0,0,0],[0,5,0,0,0])
        decision=self.fighter.decide(self.env,info)
        self.assertEqual(decision.action[0],4)
        _,_,_,_,after=self.fighter.execute(self.env,decision,ScriptedStarterFighter(side=1).decide(self.env,info))
        attacks=[x for x in after["transition"]["confirmed_events"] if x["kind"]=="attack" and x["attackerId"]=="local-player"]
        self.assertTrue(any(x.get("specialAttack")=="armadyl_godsword" for x in attacks))
        self.assertEqual(after["structured"]["fighters"][0]["special_energy"],50)
    def test_stacked_mirror_fighters_separate(self):
        moves=self.env.descriptor["actions"]["movement"]
        _,_,_,_,info=self.env.step_dual([0,5,moves.index("move_e2"),0,0],[0,5,moves.index("move_w2"),0,0])
        a,b=info["structured"]["fighters"]
        self.assertEqual((a["x"],a["y"]),(b["x"],b["y"]))
        own=self.fighter.decide(self.env,info)
        other=ScriptedStarterFighter(side=1).decide(self.env,info)
        self.assertEqual(own.action[0],0);self.assertEqual(other.action[0],0)
        _,_,_,_,info=self.fighter.execute(self.env,own,other)
        a,b=info["structured"]["fighters"]
        self.assertNotEqual((a["x"],a["y"]),(b["x"],b["y"]))
    def test_previous_mirror_deadlock_seed_is_fixed(self):
        row=fight(9317,"mirror",256)
        self.assertLessEqual(row["longest_attack_gap_ticks"],64)
        self.assertEqual(row["execution_rejections"],{})
    def test_deliberate_dd_requires_confirmed_freeze_and_exits(self):
        _,info=self.env.reset(seed=9325,options={"freeze":[0,20],"cooldown":[4,0]})
        moves=self.env.descriptor["actions"]["movement"]
        for name in ("move_e2","move_e1"):
            _,_,_,_,info=self.env.step_dual([0,5,moves.index(name),0,0],[0,5,0,0,0])
        own=self.fighter.decide(self.env,info)
        self.assertEqual(own.action[2],moves.index("stand_under"))
        self.assertEqual(own.action[0],0)
        _,_,_,_,info=self.env.step_dual(own.action,[0,5,0,0,0])
        a,b=info["structured"]["fighters"]
        self.assertEqual((a["x"],a["y"]),(b["x"],b["y"]))
        exit_decision=self.fighter.decide(self.env,info)
        self.assertNotEqual(exit_decision.action[2],0)
        self.assertEqual(exit_decision.action[0],0)
        _,_,_,_,info=self.env.step_dual(exit_decision.action,[0,5,0,0,0])
        a,b=info["structured"]["fighters"]
        self.assertNotEqual((a["x"],a["y"]),(b["x"],b["y"]))
    def test_dd_is_not_requested_without_freeze(self):
        _,info=self.env.reset(seed=9325,options={"cooldown":[4,0]})
        moves=self.env.descriptor["actions"]["movement"]
        for name in ("move_e2","move_e1"):
            _,_,_,_,info=self.env.step_dual([0,5,moves.index(name),0,0],[0,5,0,0,0])
        decision=self.fighter.decide(self.env,info)
        self.assertNotEqual(decision.action[2],moves.index("stand_under"))
    def test_fighter_uses_only_native_starter_items(self):
        allowed=set(self.env.descriptor["allowed_gear"])
        info=self.info
        for _ in range(64):
            d=self.fighter.decide(self.env,info)
            _,_,terminal,truncated,info=self.fighter.execute(self.env,d)
            self.assertLessEqual(set(info["structured"]["fighters"][0]["equipment"].values()),allowed)
            if terminal or truncated:break

if __name__=="__main__":unittest.main(verbosity=2)
