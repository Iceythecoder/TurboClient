"""Boundary tests plus real simulator integration; does not train or control Orion."""
import copy
import json
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path
from starting_rules import StartingRules,Evidence,DEFAULT_RULES
from nh_starter_env import NHStarterEnv

class RulesTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.env=NHStarterEnv(max_ticks=64)
        cls.rules=StartingRules()
        _,cls.initial=cls.env.reset(seed=9200)
    @classmethod
    def tearDownClass(cls): cls.env.close()
    def info(self, hp=99, prayer=99, overhead="protect_from_missiles", distance=1):
        info=copy.deepcopy(self.initial)
        me,other=info["structured"]["fighters"]
        me["hp"],me["prayer"]=hp,prayer
        other["active_prayers"]=[overhead] if overhead!="none" else []
        other["x"]=me["x"]+distance
        other["y"]=me["y"]
        info["legal_action_mask"]["channels"]=[[True]*8,[True]*6,[True]*26,[True]*6,[True]*2]
        return info
    def evidence(self, info, damage=None, cost=None, threat=None, ags=None):
        s=info["structured"]
        return Evidence(s["tick"],s["target"]["generation"],"test-visible-estimate",
                        damage or {"magic":10,"ranged":12,"melee":8},
                        cost or {"magic":0,"ranged":0,"melee":0},threat,ags)
    def test_food_threshold_inclusive(self):
        self.assertEqual(self.rules.advise(self.info(hp=56)).supply,0)
        self.assertEqual(self.rules.advise(self.info(hp=55)).supply,1)
        self.assertEqual(self.rules.advise(self.info(hp=36)).supply,1)
        self.assertEqual(self.rules.advise(self.info(hp=35)).supply,5)
    def test_critical_supply_wins_restore_and_ags(self):
        a=self.rules.advise(self.info(hp=35,prayer=20))
        self.assertEqual(a.supply,5)
        self.assertIsNone(a.style)
        self.assertFalse(a.ags)
    def test_restore_threshold_and_spell_threshold(self):
        self.assertEqual(self.rules.advise(self.info(prayer=21)).supply,0)
        self.assertEqual(self.rules.advise(self.info(prayer=20)).supply,4)
        i=self.info()
        i["structured"]["fighters"][0]["levels"]["magic"]=93
        self.assertEqual(self.rules.advise(i).supply,4)
        i["structured"]["fighters"][0]["levels"]["magic"]=94
        self.assertEqual(self.rules.advise(i).supply,0)
    def test_restore_does_not_use_reboost_when_sanfew_missing(self):
        i=self.info(prayer=0)
        i["structured"]["fighters"][0]["supplies"]["sanfew_serum"]=0
        self.assertEqual(self.rules.advise(i).supply,0)
        self.assertIn("restore_unavailable",self.rules.advise(i).reasons)
    def test_supply_lock_fallback_and_no_stock(self):
        i=self.info(hp=30)
        i["legal_action_mask"]["channels"][3]=[True,False,False,True,False,False]
        self.assertEqual(self.rules.advise(i).supply,3)
        i["legal_action_mask"]["channels"][3]=[True]+[False]*5
        self.assertEqual(self.rules.advise(i).supply,0)
    def test_threat_margin_and_no_invented_threat(self):
        i=self.info(hp=70)
        self.assertEqual(self.rules.advise(i).supply,0)
        self.assertEqual(self.rules.advise(i,self.evidence(i,threat=57)).supply,0)
        self.assertEqual(self.rules.advise(i,self.evidence(i,threat=58)).supply,5)
    def test_counter_selection_and_switch_cost(self):
        self.assertEqual(self.rules.advise(self.info()).style,"magic")
        i=self.info(overhead="protect_from_magic")
        self.assertEqual(self.rules.advise(i).style,"ranged")
        self.assertEqual(self.rules.advise(i,self.evidence(i,damage={"ranged":12,"melee":15},cost={"ranged":0,"melee":1})).style,"melee")
        self.assertEqual(self.rules.advise(i,self.evidence(i,damage={"ranged":12,"melee":15},cost={"ranged":0,"melee":4})).style,"ranged")
        i=self.info(overhead="protect_from_melee")
        self.assertIsNone(self.rules.advise(i).style)
        self.assertEqual(self.rules.advise(i,self.evidence(i)).style,"ranged")
    def test_unavailable_counter_and_cooldown_hold(self):
        i=self.info()
        i["legal_action_mask"]["channels"][0][1]=False
        self.assertIsNone(self.rules.advise(i).style)
        i["legal_action_mask"]["channels"][0]=[True,False,False,False,False,True,True,True]
        self.assertIsNone(self.rules.advise(i).style)
    def test_freeze_and_immunity_exact_boundaries(self):
        i=self.info(distance=4)
        i["structured"]["tick"]=10
        i["legal_action_mask"]["tick"]=10
        other=i["structured"]["fighters"][1]
        for until,expected in [(13,"already_frozen"),(12,"prepare"),(10,"prepare"),(9,"immune"),(5,"immune"),(4,"prefer_barrage"),(-1,"prefer_barrage")]:
            other["freeze_until_tick"]=until
            self.assertEqual(self.rules.advise(i).freeze,expected)
        other["active_prayers"]=["protect_from_magic"]
        self.assertEqual(self.rules.advise(i).style,"ranged")
        self.assertIn("counter_over_freeze",self.rules.advise(i).reasons)
    def test_ags_energy_hp_reach_and_protection(self):
        i=self.info(hp=60)
        me,other=i["structured"]["fighters"]
        other["hp"]=35
        me["special_energy"]=50
        self.assertTrue(self.rules.advise(i).ags)
        for field,value in [("special_energy",49),("hp",59)]:
            old=me[field];me[field]=value
            self.assertFalse(self.rules.advise(i).ags)
            me[field]=old
        other["hp"]=36
        self.assertFalse(self.rules.advise(i).ags)
        other["hp"]=35;other["x"]=me["x"]+2
        self.assertFalse(self.rules.advise(i).ags)
        other["x"]=me["x"]+1;other["active_prayers"]=["protect_from_melee"]
        self.assertFalse(self.rules.advise(i).ags)
        self.assertFalse(self.rules.advise(i,self.evidence(i,ags=34)).ags)
        self.assertTrue(self.rules.advise(i,self.evidence(i,ags=35)).ags)
    def test_defence_visible_weapon_and_depleted_prayer(self):
        i=self.info()
        for weapon,prayer in [(11791,0),(21902,1),(12006,2),(11802,2),(99999,5)]:
            i["structured"]["fighters"][1]["equipment"]["weapon"]=weapon
            self.assertEqual(self.rules.advise(i).defence_prayer,prayer)
        i["legal_action_mask"]["channels"][1]=[False]*5+[True]
        self.assertEqual(self.rules.advise(i).defence_prayer,5)
    def test_target_invalid_and_dead_hold(self):
        i=self.info()
        i["structured"]["target"]["valid"]=False
        a=self.rules.advise(i)
        self.assertIsNone(a.style);self.assertFalse(a.ags)
        i=self.info(hp=0)
        a=self.rules.advise(i)
        self.assertEqual(a.supply,0);self.assertIsNone(a.style)
    def test_evidence_rejects_stale_unattributed_nonfinite(self):
        i=self.info()
        e=self.evidence(i)
        for bad in [replace(e,tick=e.tick+1),replace(e,generation=e.generation+1),replace(e,source=""),replace(e,incoming_max_hit=float("nan")),replace(e,expected_damage={"magic":-1})]:
            with self.assertRaises(ValueError):self.rules.advise(i,bad)
    def test_preflight_stale_mask_ownership_supply_conflict(self):
        i=self.info()
        a=self.rules.advise(i)
        self.assertEqual(self.rules.preflight(i,a,[1,0,0,0,0]),())
        self.assertEqual(self.rules.preflight(i,replace(a,tick=a.tick+1),[1,0,0,0,0]),("stale_decision",))
        self.assertIn("supply_attack_conflict",self.rules.preflight(i,a,[1,0,0,1,0]))
        self.assertEqual(self.rules.preflight(i,a,[8,0,0,0,0]),("invalid_action_shape",))
        i["legal_action_mask"]["channels"][0][1]=False
        self.assertIn("masked_channel_0",self.rules.preflight(i,a,[1,0,0,0,0]))
        i["structured"]["fighters"][0]["inventory"]=[None]*28
        self.assertIn("unowned_loadout",self.rules.preflight(i,a,[4,0,0,0,0]))
    def test_wrong_masks_fail_closed(self):
        i=self.info()
        i["legal_action_mask"]["tick"]+=1
        with self.assertRaises(ValueError):self.rules.advise(i)
    def test_configuration_conflicts_rejected_and_hash_changes(self):
        original=json.loads(DEFAULT_RULES.read_text())
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/"rules.json"
            for edit in [
                lambda c:c["hard_constraints"].update(special="ignore"),
                lambda c:c["learnable_preferences"]["eating"].update(critical_hp=80),
                lambda c:c["learnable_preferences"]["ags"].update(minimum_energy=49),
                lambda c:c["learnable_preferences"]["freeze"].update(respect_immunity=False),
                lambda c:c["learnable_preferences"]["restoration"].update(restore_before_reboost=False),
                lambda c:c["learnable_preferences"]["eating"].update(normal_hp=True),
                lambda c:c.update(unknown=True),
                lambda c:c["learnable_preferences"]["eating"].update(extra=1),
            ]:
                c=copy.deepcopy(original);edit(c);path.write_text(json.dumps(c))
                with self.assertRaises(ValueError):StartingRules(path)
            c=copy.deepcopy(original);c["version"]="nh-starting-rules-test";c["learnable_preferences"]["eating"]["normal_hp"]=54
            path.write_text(json.dumps(c))
            changed=StartingRules(path)
            self.assertNotEqual(changed.hash,self.rules.hash)
            self.assertEqual(changed.advise(self.info(hp=55)).supply,0)
    def test_editable_waste_and_overheal_preferences(self):
        original=json.loads(DEFAULT_RULES.read_text())
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/"rules.json"
            original["learnable_preferences"]["eating"]["normal_hp"]=90
            path.write_text(json.dumps(original))
            r=StartingRules(path)
            self.assertEqual(r.advise(self.info(hp=90)).supply,0)
            original["learnable_preferences"]["eating"]["avoid_waste"]=False
            path.write_text(json.dumps(original))
            self.assertEqual(StartingRules(path).advise(self.info(hp=90)).supply,1)
            original["learnable_preferences"]["eating"]["normal_hp"]=55
            original["learnable_preferences"]["eating"]["allow_overheal_only_for_credible_ko_threat"]=False
            path.write_text(json.dumps(original))
            i=self.info(hp=90)
            self.assertEqual(StartingRules(path).advise(i,self.evidence(i,threat=85)).supply,0)
    def test_real_native_masks_restore_food_cooldown(self):
        for options,supply in [({"hp":[35,99]},5),({"prayer":[20,99]},4),({"magic":[93,99]},4)]:
            _,info=self.env.reset(seed=9201,options=options)
            a=self.rules.advise(info)
            self.assertEqual(a.supply,supply)
            action=[0,a.defence_prayer,0,a.supply,0]
            self.assertEqual(self.rules.preflight(info,a,action),())
            _,_,_,_,next_info=self.env.step(action)
            confirmed=[x for x in next_info["transition"]["operations"] if x["side"]==0 and x["operation"]=="supply" and x["status"]=="accepted" and x["reason"]=="consumed"]
            self.assertTrue(confirmed)
        _,info=self.env.reset(seed=9202,options={"cooldown":[3,0],"prayer":[0,99]})
        a=self.rules.advise(info)
        self.assertIsNone(a.style)
        self.assertEqual(a.defence_prayer,5)

if __name__=="__main__":unittest.main(verbosity=2)
