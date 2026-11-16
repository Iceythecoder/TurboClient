"""Exercise the exact watched worker used by the preview app."""
import json
import subprocess
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
class DDWatchTests(unittest.TestCase):
    def test_full_watched_fight_shows_actual_dd(self):
        command=[str(ROOT/"learning/.venv/Scripts/python.exe"),str(ROOT/"learning/nh-trainer/nh_worker.py"),
                 "scripted","--opponent","fixed-ranged","--seed","9300","--fights","1",
                 "--fight-ticks","256","--watch","--tick-delay-ms","1"]
        result=subprocess.run(command,capture_output=True,text=True,timeout=60)
        self.assertEqual(result.returncode,0,result.stderr)
        events=[json.loads(line) for line in result.stdout.splitlines()]
        frames=[x["frame"] for x in events if x["event"]=="frame"]
        frozen=[f for f in frames if f["fighters"][1]["freezeTicks"]>0]
        dd=[f for f in frozen if f["fighters"][0]["x"]==f["fighters"][1]["x"]
            and f["fighters"][0]["y"]==f["fighters"][1]["y"]]
        self.assertGreater(len(frozen),0)
        self.assertGreater(len(dd),0,"Watch mode must show actual overlap, not merely a requested DD action")
        self.assertTrue(any("DD confirmed: same tile" in f["phase"] for f in dd))
        for frame in frames:
            a,b=frame["fighters"]
            if "DD confirmed: same tile" in frame["phase"]:
                self.assertEqual((a["x"],a["y"]),(b["x"],b["y"]))
                self.assertGreater(b["freezeTicks"],0)
        print("Watched frames:",len(frames),"frozen-opponent frames:",len(frozen),"actual DD frames:",len(dd))
if __name__=="__main__":unittest.main(verbosity=2)
