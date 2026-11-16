"""Regression checks at the upstream engine's real tick/decision boundary."""
import numpy as np
from nh_env import NHTrainerEnv, schema, NVEC

def main():
    env = NHTrainerEnv(max_ticks=32)
    try:
        first, _ = env.reset(seed=987)
        assert first.shape == (114,) and env.engine.world_tick == 0
        assert len(NVEC) == schema.CAUSAL_UNIT_COUNT
        action = np.zeros(len(NVEC), dtype=np.int64)
        following, reward, terminated, truncated, info = env.step(action)
        # At the next policy pause the native record has received the exact
        # next-decision input, not an approximate post-action observation.
        native = env.engine._pending
        np.testing.assert_array_equal(native.inputs[0].astype(np.float32), first)
        np.testing.assert_array_equal(native.next_inputs[0].astype(np.float32), following)
        assert reward == float(native.reward[0])
        assert info["tick"] == 1 and env.engine.world_tick == 1
        assert info["logical_tick_ms"] == 600
        assert info["applied_core_actions"]["combat"] == schema.COMBAT_NO_ATTACK
        ticks = 1
        while not (terminated or truncated):
            _, _, terminated, truncated, info = env.step(action)
            ticks += 1
        assert ticks <= 32 and "winner" in info
        second, _ = env.reset(seed=987)
        np.testing.assert_array_equal(first, second)
        rejected = False
        try:
            env.step(np.zeros(2, dtype=np.int64))
        except ValueError:
            rejected = True
        assert rejected
    finally:
        env.close()
        assert env.thread is None
    print("NH adapter checks passed: native decision alignment, rewards, tick order, seed reset, terminal state and cancellation.")

if __name__ == "__main__":
    main()
