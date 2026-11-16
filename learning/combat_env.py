"""Small style-selection simulator for installation/integration tests, not Orion PvP."""
import gymnasium as gym
import numpy as np
from gymnasium import spaces

SCHEMA = "orion-style-v1"
ACTIONS = ["WAIT", "MAGIC", "RANGED", "MELEE"]
FEATURES = ["protection", "local_hp", "prayer_points", "opponent_hp_percent", "distance",
            "can_barrage", "attack_ready", "magic_available", "ranged_available", "melee_available"]

class CounterDemoEnv(gym.Env):
    """Randomized counter scenarios teach choosing a legal style or waiting.

    No damage, movement, eating, tick networking or special-attack simulation.
    Rewards are authored rules, not evidence of winning real combat.
    """
    def __init__(self):
        self.observation_space = spaces.Box(-1.0, 1.0, (len(FEATURES),), dtype=np.float32)
        self.action_space = spaces.Discrete(len(ACTIONS))
        self.steps = 0

    def observation(self):
        prayer = int(self.np_random.integers(0, 4))  # NONE, MAGIC, RANGED, MELEE
        distance = int(self.np_random.integers(1, 11))
        available = self.np_random.random(3) > .12
        self.obs = np.array([prayer / 4, self.np_random.uniform(.2, 1), self.np_random.uniform(0, 1),
                             self.np_random.uniform(.1, 1), distance / 10,
                             float(self.np_random.random() > .1), float(self.np_random.random() > .2),
                             *available.astype(float)], dtype=np.float32)
        return self.obs.copy()

    def reset(self, *, seed=None, options=None):
        super().reset(seed=seed)
        self.steps = 0
        return self.observation(), {}

    @staticmethod
    def legal(obs):
        prayer = round(float(obs[0]) * 4)
        return [False,
                bool(obs[7] and obs[5] and prayer != 1),
                bool(obs[8] and prayer != 2),
                bool(obs[9] and obs[4] <= .10001 and prayer != 3)]

    @staticmethod
    def reward(obs, action):
        legal = CounterDemoEnv.legal(obs)
        if not obs[6] or not any(legal):
            return 1.0 if action == 0 else -1.0
        if action == 0:
            return -.3
        return 1.0 if legal[action] else -1.0

    def step(self, action):
        reward = self.reward(self.obs, int(action))
        self.steps += 1
        return self.observation(), reward, self.steps >= 32, False, {}
