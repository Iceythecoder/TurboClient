"""Verify installed training algorithms without connecting to Orion or creating a model."""
import importlib.metadata
import json
from pathlib import Path
import sys
import torch
from stable_baselines3 import PPO
from sb3_contrib import RecurrentPPO

assert torch.ones(2).sum().item() == 2
info = {"python": sys.version.split()[0], "device": "cpu",
        "packages": {name: importlib.metadata.version(name) for name in
                     ["torch", "stable-baselines3", "sb3-contrib", "gymnasium", "numpy"]},
        "algorithms": [PPO.__name__, RecurrentPPO.__name__],
        "orion_trained_policy_installed": False}
Path(__file__).with_name("installed.json").write_text(json.dumps(info, indent=2), encoding="utf-8")
print(json.dumps(info))
