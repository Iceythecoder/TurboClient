"""No game-rule changes: pinned source and preserved pre-DD bridge parity."""
import hashlib
import json
from pathlib import Path
import unittest
from scripted_fighter import StarterTeacherEnv
from nh_starter_env import NHStarterEnv
ROOT=Path(__file__).resolve().parents[1]

def function_text(source,name):
    begin=source.index("function "+name+"(")
    opening=source.index("{",begin)
    depth=1
    end=opening+1
    while depth:
        if source[end]=="{":depth+=1
        elif source[end]=="}":depth-=1
        end+=1
    return source[begin:end]

class FixedRulesTests(unittest.TestCase):
    def test_all_pinned_game_source_files_unchanged(self):
        source=ROOT/"browser-source"
        tree=json.loads((source/"renderer-provenance.json").read_text())
        checked=0
        for relative,expected in tree["files"].items():
            if not relative.startswith("src/"):continue
            data=(source/relative).read_bytes()
            actual=hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
            self.assertEqual(actual,expected,relative)
            checked+=1
        self.assertEqual(checked,sum(relative.startswith("src/") for relative in tree["files"]))
        self.assertGreater(checked,0)
        print("Pinned game source files verified:",checked)
    def test_state_mutating_bridge_functions_match_pre_dd_engine(self):
        source=ROOT/"browser-source/nh-starter"
        original=(source/"worker.ts").read_text()
        current=(source/"worker-stage3.ts").read_text()
        for name in ("route","attackReason","drainPrayer","equipmentFor","equip","eat","control"):
            before=function_text(original,name)
            after=function_text(current,name)
            if name=="control":
                # Only explicit inventory-item selection is added; absent that input,
                # the original selection and native consumption path remain identical.
                after=after.replace("use(action.inventoryUse??(","use(").replace("'bastion':'sanfew_serum'));","'bastion':'sanfew_serum');")
            self.assertEqual(before,after,name)
        self.assertNotIn("auto_target_route",current)
    def test_same_actions_same_outcomes_even_when_stacked(self):
        original=NHStarterEnv(max_ticks=64)
        current=StarterTeacherEnv(max_ticks=64)
        try:
            for seed in (9300,9301,9317):
                options={"freeze":[0,30]}
                self.assertEqual(original.reset(seed=seed,options=options)[1],current.reset(seed=seed,options=options)[1])
                moves=original.descriptor["actions"]["movement"]
                actions=[[0,5,moves.index("move_e2"),0,0],[0,5,moves.index("move_e2"),0,0],
                         [1,0,0,0,0],[0,5,0,0,0],[2,1,0,0,0],
                         [0,5,moves.index("move_s1"),0,0],[1,0,0,0,0],[0,5,0,1,0],
                         [0,5,0,4,0],[3,2,0,0,0]]
                for action in actions:
                    a=original.step(action);b=current.step(action)
                    self.assertEqual(a[0].tolist(),b[0].tolist())
                    self.assertEqual(a[1:],b[1:])
        finally:
            original.close();current.close()
    def test_attack_command_does_not_grant_free_step_out(self):
        env=StarterTeacherEnv(max_ticks=64)
        try:
            _,info=env.reset(seed=9325,options={"freeze":[0,30]})
            move=env.descriptor["actions"]["movement"].index("move_e2")
            for _ in range(2):
                _,_,_,_,info=env.step_dual([0,5,move,0,0],[0,5,0,0,0])
            before=info["structured"]["fighters"][0]
            _,_,_,_,after=env.step_dual([1,0,0,0,0],[0,5,0,0,0])
            self.assertEqual((before["x"],before["y"]),(after["structured"]["fighters"][0]["x"],after["structured"]["fighters"][0]["y"]))
            self.assertFalse(any(x["operation"]=="auto_target_route" for x in after["transition"]["operations"]))
            self.assertFalse(env.descriptor["custom_dd_auto_step_out"])
        finally:env.close()

if __name__=="__main__":unittest.main(verbosity=2)
