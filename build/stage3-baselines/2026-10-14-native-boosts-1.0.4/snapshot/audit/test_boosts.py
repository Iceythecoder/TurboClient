"""Real potion inputs and brew/restore/reboost behavior; no game-stat overrides."""
import copy
import unittest
from scripted_fighter import StarterTeacherEnv,ScriptedStarterFighter,SupplyManager

class BoostTests(unittest.TestCase):
    def setUp(self):
        self.env=StarterTeacherEnv(max_ticks=128)
        self.fighter=ScriptedStarterFighter()
        _,self.info=self.env.reset(seed=9400)
    def tearDown(self):self.env.close()
    def selected(self,item):
        while not self.env.visible_estimates()["supply_eligibility"][{"sanfew_serum":"restore"}.get(item,item)]:
            _,_,_,_,self.info=self.env.step_dual([0,5,0,0,0],[0,5,0,0,0])
        _,_,_,_,self.info=self.env.step_dual([0,5,0,4,0],[0,5,0,0,0],item)
        return self.info
    def test_explicit_super_combat_and_bastion_use_real_doses(self):
        caps=self.env.visible_estimates()["potion_targets"]
        self.selected("super_combat")
        me=self.info["structured"]["fighters"][0]
        self.assertEqual(me["levels"]["attack"],caps["super_combat"]["attack"])
        self.assertEqual(me["levels"]["strength"],caps["super_combat"]["strength"])
        self.assertEqual(me["supplies"]["super_combat"],3)
        self.selected("bastion")
        me=self.info["structured"]["fighters"][0]
        self.assertEqual(me["levels"]["ranged"],caps["bastion"]["ranged"])
        self.assertEqual(me["supplies"]["bastion"],3)
    def test_policy_drinks_both_and_does_not_spam_full_boosts(self):
        used=[]
        for _ in range(12):
            decision=self.fighter.decide(self.env,self.info)
            if decision.inventory_use:used.append(decision.inventory_use)
            _,_,_,_,self.info=self.env.step_dual(decision.action,[0,5,0,0,0],decision.inventory_use)
        self.assertEqual(used.count("super_combat"),1)
        self.assertEqual(used.count("bastion"),1)
    def test_brew_reduces_actual_boosts_restore_then_reboost(self):
        self.selected("super_combat");self.selected("bastion")
        before=self.info["structured"]["fighters"][0]["levels"].copy()
        while not self.env.visible_estimates()["supply_eligibility"]["brew"]:
            _,_,_,_,self.info=self.env.step_dual([0,5,0,0,0],[0,5,0,0,0])
        _,_,_,_,self.info=self.env.step_dual([0,5,0,3,0],[0,5,0,0,0])
        brewed=self.info["structured"]["fighters"][0]["levels"]
        for stat in ("attack","strength","ranged","magic"):self.assertLess(brewed[stat],before[stat])
        used=[]
        for _ in range(15):
            decision=self.fighter.decide(self.env,self.info)
            if decision.inventory_use:used.append(decision.inventory_use)
            _,_,_,_,self.info=self.env.step_dual(decision.action,[0,5,0,0,0],decision.inventory_use)
        self.assertEqual(used[0],"sanfew_serum")
        self.assertIn("super_combat",used[1:]);self.assertIn("bastion",used[1:])
        caps=self.env.visible_estimates()["potion_targets"]
        levels=self.info["structured"]["fighters"][0]["levels"]
        self.assertEqual(levels["attack"],caps["super_combat"]["attack"])
        self.assertEqual(levels["ranged"],caps["bastion"]["ranged"])
    def test_native_cooldown_rejects_second_potion_without_spending_dose(self):
        self.selected("super_combat")
        stock=self.info["structured"]["fighters"][0]["supplies"]["bastion"]
        _,_,_,_,self.info=self.env.step_dual([0,5,0,4,0],[0,5,0,0,0],"bastion")
        self.assertEqual(self.info["structured"]["fighters"][0]["supplies"]["bastion"],stock)
        self.assertTrue(any(e["operation"]=="supply" and e["status"]=="rejected" for e in self.info["transition"]["operations"] if e["side"]==0))
    def test_emergency_food_and_restoration_take_priority(self):
        _,info=self.env.reset(seed=9401,options={"hp":[35,99],"prayer":[0,99],"magic":[80,99]})
        d=self.fighter.decide(self.env,info)
        self.assertEqual(d.action[3],5);self.assertIsNone(d.inventory_use)
    def test_missing_potion_does_not_generate_selection(self):
        native=self.env.visible_estimates()
        native["supply_eligibility"]["super_combat"]=False
        native["supply_eligibility"]["bastion"]=False
        self.assertIsNone(SupplyManager().boost_item(self.info,native,"ranged"))
    def test_invalid_inventory_input_rejected_without_advancing(self):
        tick=self.env.current_tick
        with self.assertRaises(RuntimeError):self.env.step_dual([0,5,0,4,0],[0,5,0,0,0],"unknown_potion")
        self.assertEqual(self.env.current_tick,tick)
        with self.assertRaises(RuntimeError):self.env.step_dual([0,5,0,0,0],[0,5,0,0,0],"bastion")
        self.assertEqual(self.env.current_tick,tick)

if __name__=="__main__":unittest.main(verbosity=2)
