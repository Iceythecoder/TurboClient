"""Gymnasium adapter for the pinned upstream NH Trainer fight engine.

Engine.score is paused at its actual decision boundary. This avoids returning a
pre-timer observation and subsequently applying an action to a different tick.
"""
from pathlib import Path
import queue
import sys
import threading
import gymnasium as gym
import numpy as np
from gymnasium import spaces

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "upstream" / "fastsim"))
from fastsim import engine, gear, schema
from fastsim.scripted_policy import ScriptedPolicy

CONTRACT = "nh-fastsim-sb3-v1"
CORE_ROWS = [list(range(base, base + count)) for base, count in
             [(schema.COMBAT_BASE, schema.COMBAT_COUNT), (schema.DEFENCE_BASE, schema.DEFENCE_COUNT),
              (schema.MOVEMENT_BASE, schema.MOVEMENT_COUNT), (schema.SUPPLY_BASE, schema.SUPPLY_COUNT)]]
# Zero means the engine's virtual NONE for an optional gear slot. Positive
# choices select the original upstream row; ammo's causal channel is inactive.
GEAR_ROWS = [([] if schema.CHANNEL_GEAR_BASE + i in schema.INACTIVE_CAUSAL_UNITS
              else list(gear.GEAR_ROWS_BY_SLOT[slot])) for i, slot in enumerate(schema.OPTIONAL_GEAR_SLOTS)]
NVEC = [len(rows) for rows in CORE_ROWS] + [len(rows) + 1 for rows in GEAR_ROWS]
OPPONENTS = ["fixed-ranged", "fixed-magic", "fixed-melee", "seeded-rapid-switch"]

class _Cancelled(Exception):
    pass

class _VisualEngine(engine.Engine):
    """Observe executed native attacks without altering decisions or physics."""
    def __init__(self, **kwargs):
        self.visual_attacks = []
        super().__init__(**kwargs)

    def _queue_hit(self, damage, hit_style, delay_ticks, active, *args, **kwargs):
        s = self.state
        spec_kind = kwargs.get('spec_kind')
        if spec_kind is None and len(args) > 1:
            spec_kind = args[1]
        for side in range(2):
            style = int(hit_style[0, side])
            if bool(active[0, side]) and 0 <= style < 3:
                other = 1-side
                spec = -1 if spec_kind is None else int(spec_kind[0, side])
                self.visual_attacks.append({
                    'side': side, 'tick': int(s.tick[0]),
                    'style': schema.STYLE_NAMES[style],
                    'special': spec >= 0, 'specKind': spec,
                    'impactTick': int(s.tick[0])+int(delay_ticks[0, side]),
                    'weapon': int(s.equipped_ids[0, side, gear.SLOT_WEAPON]),
                    'fromX': int(s.x[0, side]), 'fromY': int(s.y[0, side]),
                    'toX': int(s.x[0, other]), 'toY': int(s.y[0, other])})
        return super()._queue_hit(damage, hit_style, delay_ticks, active, *args, **kwargs)

class _ExternalPolicy:
    input_size = schema.INPUT_SIZE
    action_count = schema.ACTION_COUNT
    action_ids = schema.CURRENT_ACTION_IDS
    defence_prayer_head_version = 1

    def __init__(self, env):
        self.env = env

    def score(self, inputs):
        env = self.env
        previous = env.engine._pending
        reward = 0.0 if previous is None else float(previous.reward[0])
        env._publish((inputs[0].astype(np.float32).copy(), reward, False, False, env._info(previous)))
        while not env.cancelled.is_set():
            try:
                action = env.actions.get(timeout=.1)
                break
            except queue.Empty:
                continue
        else:
            raise _Cancelled()
        scores = np.full((inputs.shape[0], schema.ACTION_COUNT), -100.0, dtype=np.float32)
        for i, rows in enumerate(CORE_ROWS):
            scores[0, rows[int(action[i])]] = 100.0
        for i, rows in enumerate(GEAR_ROWS):
            pick = int(action[len(CORE_ROWS) + i])
            if pick:
                scores[0, rows[pick - 1]] = 100.0
        return scores, np.zeros(inputs.shape[0], dtype=np.float32)

class NHTrainerEnv(gym.Env):
    metadata = {"render_modes": []}

    def __init__(self, opponent="fixed-ranged", max_ticks=256, observer=None):
        if opponent not in OPPONENTS:
            raise ValueError("Unsupported opponent curriculum")
        if not 32 <= max_ticks <= 1200:
            raise ValueError("Fight length must be 32..1200 logical ticks")
        self.opponent, self.max_ticks = opponent, int(max_ticks)
        self.action_space = spaces.MultiDiscrete(np.asarray(NVEC, dtype=np.int64))
        self.observation_space = spaces.Box(-np.inf, np.inf, (schema.INPUT_SIZE,), dtype=np.float32)
        self.thread = None
        self.engine = None
        self.cancelled = threading.Event()
        self.decisions = queue.Queue(maxsize=1)
        self.actions = queue.Queue(maxsize=1)
        self.done = True
        self.observer = observer
        self.episode = 0

    def reset(self, *, seed=None, options=None):
        super().reset(seed=seed)
        self.close()
        self.cancelled = threading.Event()
        self.decisions = queue.Queue(maxsize=1)
        self.actions = queue.Queue(maxsize=1)
        self.episode += 1
        actual_seed = int(self.np_random.integers(0, 2**31 - 1))
        self.engine = _VisualEngine(n_fights=1, policy=_ExternalPolicy(self),
                                    opponent_policy=ScriptedPolicy(self.opponent, seed=actual_seed),
                                    seed=actual_seed, epsilon=0.0, max_ticks=self.max_ticks,
                                    world_id=35, start_distance_min=1, start_distance_max=8,
                                    lane_radius=3)
        self.thread = threading.Thread(target=self._run, name="nh-fastsim-decision", daemon=True)
        self.thread.start()
        obs, _, _, _, info = self._receive()
        self.done = False
        if self.observer is not None:
            self.observer(obs, 0.0, False, False, info, True)
        return obs, info

    def step(self, action):
        if self.done:
            raise RuntimeError("Reset the fight before stepping")
        action = np.asarray(action)
        if not self.action_space.contains(action):
            raise ValueError("Action vector does not match the upstream channel layout")
        self.actions.put(action.copy(), timeout=10)
        result = self._receive()
        self.done = result[2] or result[3]
        if self.observer is not None:
            self.observer(*result, False)
        return result

    def _run(self):
        try:
            while not self.cancelled.is_set() and self.engine.has_work():
                self.engine.step()
            if self.cancelled.is_set():
                return
            final = self.engine.flush()
            winner = int(self.engine.state.winner[0])
            terminated = bool((self.engine.state.hp[0] <= 0).any())
            reward = 0.0 if final is None else float(final.reward[0])
            info = self._info(final)
            info["winner"] = winner
            obs = np.zeros(schema.INPUT_SIZE, dtype=np.float32) if final is None else final.next_inputs[0].astype(np.float32)
            self._publish((obs.copy(), reward, terminated, not terminated, info))
        except _Cancelled:
            pass
        except Exception as error:
            self._publish(error)

    def _publish(self, value):
        while not self.cancelled.is_set():
            try:
                self.decisions.put(value, timeout=.1)
                return
            except queue.Full:
                continue

    def _receive(self):
        try:
            value = self.decisions.get(timeout=30)
        except queue.Empty as error:
            self.close()
            raise RuntimeError("NH Trainer did not reach its next decision boundary") from error
        if isinstance(value, Exception):
            self.close()
            raise value
        if not np.isfinite(value[0]).all() or not np.isfinite(value[1]):
            self.close()
            raise RuntimeError("Simulator produced non-finite observation/reward")
        return value

    def _info(self, record):
        state = self.engine.state
        info = {"tick": int(state.tick[0]), "hp": state.hp[0].tolist(),
                "food": state.food_count[0].tolist(), "prayer": state.prayer_points[0].tolist(),
                "logical_tick_ms": 600, "opponent": self.opponent}
        if record is not None:
            info["applied_core_actions"] = {key: int(record.chosen[key][0])
                                            for key in ["combat", "defence", "movement", "supply"]}
        fighters = []
        for side in range(2):
            weapon_id = int(state.equipped_ids[0, side, gear.SLOT_WEAPON])
            weapon = gear.BY_ID.get(weapon_id)
            style = int(state.style[0, side])
            overhead = int(state.overhead[0, side])
            intent = "Starting"
            if record is not None:
                combat_choice = int(record.chosen["combat"][side])
                supply = int(record.chosen["supply"][side]) - schema.SUPPLY_BASE
                if combat_choice == schema.COMBAT_NO_ATTACK:
                    intent = "No attack command"
                elif combat_choice == schema.COMBAT_SPEC_NONE:
                    intent = "No special command"
                elif combat_choice < schema.COMBAT_SPEC_NONE:
                    selected_style = (combat_choice - schema.COMBAT_ATTACK_BASE) // 2
                    intent = schema.STYLE_NAMES[selected_style] + " attack intent"
                else:
                    spec = (combat_choice - schema.COMBAT_SPEC_BASE) // 2
                    intent = schema.SPEC_KIND_NAMES[spec] + " intent"
                if 0 < supply < len(schema.SUPPLY_NAMES):
                    intent += " / " + schema.SUPPLY_NAMES[supply]
            fighters.append({"x": int(state.x[0, side]), "y": int(state.y[0, side]),
                             "hp": int(state.hp[0, side]), "maxHp": 99,
                             "prayer": int(state.prayer_points[0, side]), "food": int(state.food_count[0, side]),
                             "freezeTicks": int(state.freeze_ticks[0, side]), "cooldown": int(state.attack_delay[0, side]),
                             "weapon": weapon_id, "weaponName": weapon.name if weapon else str(weapon_id),
                             "equipment": state.equipped_ids[0, side].astype(int).tolist(),
                             "style": schema.STYLE_NAMES[style] if 0 <= style < 3 else "NONE",
                             "overhead": schema.PRAYER_NAMES[overhead] if 0 <= overhead < len(schema.PRAYER_NAMES) else "NONE",
                             "spec": float(state.special_energy[0, side]) / 10,
                             "damage": float(state.damage_dealt[0, side]), "lastIntent": intent})
        hits = []
        for side in range(2):
            for index in range(int(state.last_hit_count[0, side])):
                hits.append({'side': side, 'damage': int(state.last_hit_damage[0, side, index]),
                             'sourceTick': int(state.last_hit_source_ticks[0, side, index])})
        info["frame"] = {"episode": self.episode, "tick": info["tick"], "fighters": fighters,
                         "attacks": self.engine.visual_attacks, "hits": hits,
                         "arena": {"minX": int(state.lane_min_x[0]), "maxX": int(state.lane_max_x[0]),
                                   "minY": int(state.lane_min_y[0]), "maxY": int(state.lane_max_y[0])}}
        self.engine.visual_attacks = []
        return info

    def close(self):
        self.cancelled.set()
        if self.thread is not None and self.thread.is_alive() and self.thread is not threading.current_thread():
            self.thread.join(timeout=2)
            if self.thread.is_alive():
                raise RuntimeError("Simulator worker did not stop")
        self.thread = None
        self.done = True
