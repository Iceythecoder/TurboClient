"""Local NH Trainer connection check, PPO training and simulator evaluation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import time
from collections import deque

ROOT = Path(__file__).resolve().parent
os.environ.setdefault("MPLCONFIGDIR", str(ROOT.parent / "cache" / "matplotlib"))
import numpy as np
import torch
from stable_baselines3 import PPO
from stable_baselines3.common.callbacks import BaseCallback
from sb3_contrib import RecurrentPPO
from nh_env import NHTrainerEnv, CONTRACT, NVEC, OPPONENTS, schema

torch.set_num_threads(1)

def emit(value):
    print(json.dumps(value, allow_nan=False), flush=True)

def manifest():
    return json.loads((ROOT / "source-manifest.json").read_text(encoding="utf-8-sig"))

def verify_source(profile="dmm"):
    provenance = manifest()
    if profile.startswith("nh-starter"):
        source_root=ROOT/'browser-source'
        tree=json.loads((source_root/'renderer-provenance.json').read_text(encoding='utf-8'))
        if tree['commit']!=provenance['commit']:
            raise ValueError('NH source commit mismatch')
        for relative,digest in tree['files'].items():
            if relative.startswith(('src/sim/','src/generated/','src/bot/')) or relative=='src/ui/runtimeSetupPresets.ts':
                data=(source_root/relative).read_bytes()
                actual=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
                if actual!=digest:raise ValueError('NH source integrity mismatch: '+relative)
        return provenance
    for relative, digest in provenance["sha256"].items():
        path = ROOT / "upstream" / relative
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError("Upstream source integrity mismatch: " + relative)
    return provenance

def make_env(args,observer=None,record=True):
    if args.profile.startswith('nh-starter'):
        from nh_starter_env import NHStarterEnv
        return NHStarterEnv(opponent=args.opponent,max_ticks=args.fight_ticks,observer=observer,trace_output=args.trace_output if record else None,engine_revision=1 if args.profile=='nh-starter-v1' else 2)
    return NHTrainerEnv(opponent=args.opponent,max_ticks=args.fight_ticks,observer=observer)

def layout(env,profile):
    if profile.startswith('nh-starter'):
        from nh_starter_env import CONTRACT as nh_contract,ENVIRONMENT
        return {'schema':env.contract,'environment':ENVIRONMENT if env.engine_revision==2 else 'nh-trainer-browser-starter-v1','input_size':114,
                'action_dimensions':env.action_space.nvec.astype(int).tolist(),
                'starter_signature':env.signature,'action_labels':env.descriptor['actions'],
                'allowed_gear':env.descriptor['allowed_gear'],'starter_equipment':env.descriptor['equipment'],
                'starter_inventory':env.descriptor['inventory'],'training_profile':'nh-starter'}
    return {'schema':CONTRACT,'environment':'nh-trainer-fastsim-v1','input_size':schema.INPUT_SIZE,
            'action_dimensions':NVEC,'upstream_action_ids':list(schema.CURRENT_ACTION_IDS),'training_profile':'dmm'}

def check(args):
    provenance = verify_source(args.profile)
    with_env = make_env(args)
    try:
        obs, info = with_env.reset(seed=args.seed)
        with_env.action_space.seed(args.seed)
        for _ in range(8):
            # Original engine chooses only legal actions from submitted channel scores.
            obs, reward, terminated, truncated, info = with_env.step(with_env.action_space.sample())
            if terminated or truncated:
                break
        emit({"event": "connected", "source_url": provenance["website"], "commit": provenance["commit"],
              **layout(with_env,args.profile), "checked_ticks": info["tick"], "logical_tick_ms": 600,
              "live_orion_validated": False})
    finally:
        with_env.close()

class LiveMonitor:
    def __init__(self, mode, watch=False, tick_delay_ms=0):
        self.mode, self.watch, self.delay = mode, watch, tick_delay_ms / 1000
        self.steps = self.wins = self.losses = self.draws = 0
        self.episode_return = 0.0
        self.recent = deque(maxlen=20)
        self.phase = "Starting"
        self.last_emit = 0.0
    def set_phase(self, phase):
        self.phase = phase
        emit({"event": "phase", "phase": phase})
    def observe(self, obs, reward, terminated, truncated, info, reset):
        if reset:
            self.episode_return = 0.0
        else:
            self.steps += 1
            self.episode_return += reward
        finished = terminated or truncated
        if finished:
            winner = info.get("winner", -1)
            self.wins += winner == 0
            self.losses += winner == 1
            self.draws += winner < 0
            self.recent.append(self.episode_return)
            emit({"event": "episode", "episode": info["frame"]["episode"], "steps": self.steps,
                  "wins": self.wins, "losses": self.losses, "draws": self.draws,
                  "return": self.episode_return, "recent_mean_return": float(np.mean(self.recent)),
                  "mode": self.mode})
        now = time.monotonic()
        if self.watch and (self.delay or reset or finished or now - self.last_emit >= .05):
            frame = info["frame"]
            if getattr(self,"scripted",False):
                a,b=frame["fighters"]
                distance=max(abs(a["x"]-b["x"]),abs(a["y"]-b["y"]))
                dd="DD confirmed: same tile" if distance==0 and b["freezeTicks"]>0 else f"DD not active: {distance} tiles apart"
                self.phase="Stage 3: "+dd+" | "+self.phase.split(" | ")[-1]
            frame.update({"mode": self.mode, "phase": self.phase, "steps": self.steps,
                          "tickDelayMs": int(self.delay * 1000),
                          "finished": finished, "wins": self.wins, "losses": self.losses, "draws": self.draws,
                          "recentMeanReturn": float(np.mean(self.recent)) if self.recent else 0.0})
            emit({"event": "frame", "frame": frame})
            self.last_emit = now
        if self.watch and self.delay and not reset:
            # Pacing only; game rules still advance one 600 ms logical tick.
            time.sleep(self.delay)

class Progress(BaseCallback):
    def __init__(self, total, monitor):
        super().__init__()
        self.total, self.last, self.monitor = total, 0, monitor
    def _on_rollout_start(self):
        self.monitor.set_phase("Collecting experience")
    def _on_rollout_end(self):
        self.monitor.set_phase("Updating policy weights")
    def _on_step(self):
        if self.num_timesteps - self.last >= 128 or self.num_timesteps >= self.total:
            self.last = self.num_timesteps
            emit({"event": "progress", "steps": self.num_timesteps, "requested_steps": self.total})
        return True

def train(args):
    provenance = verify_source(args.profile)
    if not 128 <= args.steps <= 10_000_000:
        raise ValueError("Training steps must be 128..10000000")
    output = Path(args.output).resolve()
    if output.suffix.lower() != ".zip":
        raise ValueError("Choose a .zip checkpoint output")
    if output.exists() or output.with_suffix('.json').exists():
        raise ValueError('Choose a new checkpoint filename; existing checkpoints are preserved')
    output.parent.mkdir(parents=True, exist_ok=True)
    monitor = LiveMonitor("training", args.watch, args.tick_delay_ms)
    env = make_env(args,monitor.observe)
    recurrent = args.algorithm == "RecurrentPPO"
    kind = RecurrentPPO if recurrent else PPO
    kwargs = {"net_arch": [64], "lstm_hidden_size": 64} if recurrent else {"net_arch": [64, 64]}
    try:
        model = kind("MlpLstmPolicy" if recurrent else "MlpPolicy", env, n_steps=128,
                     batch_size=64, n_epochs=4, policy_kwargs=kwargs, learning_rate=.0003,
                     seed=args.seed, device="cpu", verbose=0)
        emit({"event": "training", "algorithm": args.algorithm, "opponent": args.opponent,
              "steps": args.steps, **layout(env,args.profile)})
        model.learn(total_timesteps=args.steps, callback=Progress(args.steps, monitor))
        model.save(str(output))
        metadata = {**layout(env,args.profile), "algorithm": args.algorithm, "source_commit": provenance["commit"],
                    "source_url": provenance["website"], "opponent": args.opponent,
                    "training_steps": int(model.num_timesteps), "max_fight_ticks": args.fight_ticks,
                    "seed": args.seed, "device": "cpu", "reward": "terminal_win_loss_only",
                    "live_control": False, "validated_orion": False,
                    "description": "NH Trainer "+args.profile+" checkpoint; requires a separate validated Orion adapter."}
        output.with_suffix(".json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
        emit({"event": "trained", "output": str(output), "steps": int(model.num_timesteps), "live_control": False})
    finally:
        env.close()

def evaluate(args):
    provenance = verify_source(args.profile)
    checkpoint = Path(args.model).resolve()
    metadata = json.loads(checkpoint.with_suffix(".json").read_text(encoding="utf-8"))
    env=make_env(args,record=False)
    try:
        expected=layout(env,args.profile)
        for key,value in expected.items():
            if key=='training_profile' and key not in metadata and args.profile=='dmm':continue
            if metadata.get(key)!=value:
                raise ValueError('Checkpoint setup/adapter revision differs ('+key+'). Keep the original checkpoint. Use --profile nh-starter-v1 for explicit legacy NH comparisons, or train a new corrected NH v2 checkpoint.')
        if metadata.get('source_commit')!=provenance['commit']:raise ValueError('Checkpoint source commit mismatch')
    finally:env.close()
    kind = {"PPO": PPO, "RecurrentPPO": RecurrentPPO}[metadata["algorithm"]]
    model = kind.load(str(checkpoint), device="cpu")
    if tuple(model.observation_space.shape) != (expected['input_size'],) or list(model.action_space.nvec) != expected['action_dimensions']:
        raise ValueError("Actual checkpoint tensor layout mismatch")
    wins, losses, draws, ticks = 0, 0, 0, 0
    monitor = LiveMonitor("evaluation", args.watch, args.tick_delay_ms)
    monitor.phase = "Testing fixed policy (no learning)"
    env = make_env(args,monitor.observe)
    try:
        for fight in range(args.fights):
            obs, _ = env.reset(seed=args.seed + fight)
            memory = None
            start = np.array([True])
            done = False
            while not done:
                if kind is RecurrentPPO:
                    action, memory = model.predict(obs, state=memory, episode_start=start, deterministic=True)
                else:
                    action, _ = model.predict(obs, deterministic=True)
                obs, _, terminated, truncated, info = env.step(action)
                start = np.array([False])
                done = terminated or truncated
            winner = info.get("winner", -1)
            wins += winner == 0
            losses += winner == 1
            draws += winner < 0
            ticks += info["tick"]
            emit({"event": "evaluation", "fights": fight + 1, "wins": wins, "losses": losses, "draws": draws})
        emit({"event": "evaluated", "fights": args.fights, "wins": wins, "losses": losses,
              "draws": draws, "ticks": ticks, "opponent": args.opponent, "validated_orion": False})
    finally:
        env.close()


def scripted(args):
    if args.profile!='nh-starter' or args.trace_output:
        raise ValueError("Scripted viewer requires NH starter and does not record traces")
    verify_source('nh-starter')
    from scripted_fighter import StarterTeacherEnv,ScriptedStarterFighter
    monitor=LiveMonitor("evaluation",args.watch,args.tick_delay_ms)
    monitor.scripted=True
    monitor.phase="Stage 3 scripted fighter (no learning)"
    env=StarterTeacherEnv(opponent=args.opponent,max_ticks=args.fight_ticks,observer=monitor.observe)
    try:
        for index in range(args.fights):
            fighter=ScriptedStarterFighter()
            mirror=ScriptedStarterFighter(side=1) if args.opponent=='mirror' else None
            _,info=env.reset(seed=args.seed+index)
            done=False
            while not done:
                decision=fighter.decide(env,info)
                other=mirror.decide(env,info) if mirror else None
                monitor.phase="Stage 3: "+", ".join(decision.reasons)
                _,_,terminated,truncated,info=fighter.execute(env,decision,other)
                done=terminated or truncated
            emit({"event":"evaluation","fights":index+1,"wins":monitor.wins,"losses":monitor.losses,"draws":monitor.draws})
        emit({"event":"evaluated","fights":args.fights,"wins":monitor.wins,"losses":monitor.losses,"draws":monitor.draws,
              "opponent":args.opponent,"validated_orion":False,"fighter":"Stage 3 scripted"})
    finally:
        env.close()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["check", "train", "evaluate", "scripted"])
    parser.add_argument('--profile',choices=['nh-starter','dmm','nh-starter-v1'],default='nh-starter')
    parser.add_argument("--algorithm", choices=["PPO", "RecurrentPPO"], default="PPO")
    parser.add_argument("--steps", type=int, default=8192)
    parser.add_argument("--opponent", choices=list(OPPONENTS)+["stationary-ranged","seeded-random-prayer","mirror"], default="fixed-ranged")
    parser.add_argument("--fight-ticks", type=int, default=256)
    parser.add_argument("--seed", type=int, default=None)
    parser.add_argument("--output")
    parser.add_argument("--model")
    parser.add_argument("--fights", type=int, default=5)
    parser.add_argument("--watch", action="store_true")
    parser.add_argument("--tick-delay-ms", type=int, default=0)
    parser.add_argument('--trace-output',help='Explicit new JSONL path for synthetic NH v2 telemetry; default does not record')
    args = parser.parse_args()
    if args.seed is None:args.seed=9300 if args.command=="scripted" else 42 if args.command=="train" else 20042 if args.command=="evaluate" else 123
    try:
        if args.trace_output and args.profile!='nh-starter':raise ValueError('Stage 1 recording requires the corrected NH starter profile')
        if not 1 <= args.fights <= 1000:
            raise ValueError("Evaluation fights must be 1..1000")
        if not 0 <= args.tick_delay_ms <= 2000:
            raise ValueError("Viewer tick delay must be 0..2000 ms")
        {"check": lambda: check(args), "train": lambda: train(args), "evaluate": lambda: evaluate(args), "scripted": lambda: scripted(args)}[args.command]()
    except Exception as error:
        emit({"event": "error", "message": type(error).__name__ + ": " + str(error)})
        sys.exit(1)

if __name__ == "__main__":
    main()
