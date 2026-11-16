"""Local stdin/stdout policy protocol. No sockets, uploads, live training or recordings."""
import argparse
import json
import os
from pathlib import Path
import sys

os.environ.setdefault("MPLCONFIGDIR", str(Path(__file__).parent / "cache" / "matplotlib"))
import numpy as np
import torch
from stable_baselines3 import PPO
from sb3_contrib import RecurrentPPO
from combat_env import CounterDemoEnv, SCHEMA, ACTIONS, FEATURES

torch.set_num_threads(1)

def emit(value):
    print(json.dumps(value, allow_nan=False), flush=True)

def algorithm(name):
    return {"PPO": PPO, "RecurrentPPO": RecurrentPPO}[name]

def train_demo(args):
    destination = Path(args.output).resolve()
    if destination.suffix.lower() != ".zip":
        raise ValueError("Model output must end in .zip")
    destination.parent.mkdir(parents=True, exist_ok=True)
    steps = args.steps
    if not 256 <= steps <= 1_000_000:
        raise ValueError("Demo steps must be 256..1000000")
    recurrent = args.algorithm == "RecurrentPPO"
    kwargs = dict(net_arch=[32], lstm_hidden_size=32) if recurrent else dict(net_arch=[32, 32])
    model = algorithm(args.algorithm)("MlpLstmPolicy" if recurrent else "MlpPolicy", CounterDemoEnv(),
                                    n_steps=128, batch_size=64, n_epochs=4, learning_rate=.001,
                                    policy_kwargs=kwargs, seed=42, device="cpu", verbose=0)
    emit({"event": "training", "algorithm": args.algorithm, "steps": steps, "environment": "toy-counter-v1"})
    model.learn(total_timesteps=steps)
    env = CounterDemoEnv()
    obs, _ = env.reset(seed=879)
    state = None
    start = np.array([True])
    correct = 0
    for _ in range(256):
        if recurrent:
            chosen, state = model.predict(obs, state=state, episode_start=start, deterministic=True)
        else:
            chosen, _ = model.predict(obs, deterministic=True)
        correct += CounterDemoEnv.reward(obs, int(chosen)) > 0
        obs, _, done, _, _ = env.step(chosen)
        start = np.array([done])
        if done:
            obs, _ = env.reset()
    # These are explicit user-requested outputs; no real combat observations are persisted.
    model.save(str(destination))
    metadata = {"schema": SCHEMA, "features": FEATURES, "actions": ACTIONS, "algorithm": args.algorithm,
                "environment": "toy-counter-v1", "live_control": False,
                "training_steps": int(model.num_timesteps), "demo_legal_action_rate": correct / 256,
                "description": "Toy counter-selection policy. Not trained or validated for Orion PvP."}
    destination.with_suffix(".json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    emit({"event": "trained", "output": str(destination), "metadata": metadata})

def serve(args):
    path = Path(args.model).resolve()
    metadata = json.loads(path.with_suffix(".json").read_text(encoding="utf-8"))
    if metadata.get("schema") != SCHEMA or metadata.get("features") != FEATURES or metadata.get("actions") != ACTIONS:
        raise ValueError("Incompatible observation/action schema")
    name = metadata["algorithm"]
    # SB3 checkpoints can contain pickle objects: load only your own trusted local checkpoints.
    model = algorithm(name).load(str(path), device="cpu")
    if tuple(model.observation_space.shape) != (len(FEATURES),) or model.action_space.n != len(ACTIONS):
        raise ValueError("Model tensor dimensions do not match metadata")
    emit({"event": "ready", "algorithm": name, "metadata": metadata})
    memory = None
    previous_generation = None
    previous_tick = None
    for line in sys.stdin:
        if len(line) > 16384:
            raise ValueError("Request too large")
        request = json.loads(line)
        if request.get("command") == "close":
            break
        obs = np.asarray(request["observation"], dtype=np.float32)
        if obs.shape != (len(FEATURES),) or not np.isfinite(obs).all() or np.any(obs < -1) or np.any(obs > 1):
            raise ValueError("Invalid observation")
        generation, tick = request["generation"], request["tick"]
        reset = request.get("reset", False) or generation != previous_generation or (previous_tick is not None and tick < previous_tick)
        if reset:
            memory = None
        if name == "RecurrentPPO":
            action, memory = model.predict(obs, state=memory, episode_start=np.array([reset]), deterministic=True)
        else:
            action, _ = model.predict(obs, deterministic=True)
        chosen = int(action)
        if not 0 <= chosen < len(ACTIONS):
            raise ValueError("Invalid model action")
        emit({"event": "prediction", "sequence": request["sequence"], "generation": generation,
              "tick": tick, "action": ACTIONS[chosen]})
        previous_generation, previous_tick = generation, tick

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model")
    parser.add_argument("--train-demo", action="store_true")
    parser.add_argument("--algorithm", choices=["PPO", "RecurrentPPO"], default="PPO")
    parser.add_argument("--steps", type=int, default=8192)
    parser.add_argument("--output")
    args = parser.parse_args()
    try:
        if args.train_demo:
            train_demo(args)
        elif args.model:
            serve(args)
        else:
            raise ValueError("Choose --model or --train-demo")
    except Exception as exc:
        emit({"event": "error", "message": type(exc).__name__ + ": " + str(exc)})
        sys.exit(1)

if __name__ == "__main__":
    main()
