"""Rendering instrumentation must not alter the native combat trajectory."""
import numpy as np
import nh_env
from nh_env import NHTrainerEnv, engine

class BaselineEngine(engine.Engine):
    def __init__(self, **kwargs):
        self.visual_attacks=[]
        super().__init__(**kwargs)

def trajectory(cls):
    original=nh_env._VisualEngine
    nh_env._VisualEngine=cls
    env=NHTrainerEnv(opponent='seeded-rapid-switch',max_ticks=128)
    samples=[]; attacks=hits=0
    try:
        observation,info=env.reset(seed=674)
        rng=np.random.default_rng(93)
        for _ in range(128):
            action=np.array([rng.integers(n) for n in nh_env.NVEC])
            observation,reward,terminated,truncated,info=env.step(action)
            samples.append((observation.copy(),reward,terminated,truncated,info['hp']))
            frame=info['frame']
            attacks+=len(frame['attacks']);hits+=len(frame['hits'])
            assert all(len(f['equipment'])==14 for f in frame['fighters'])
            assert all(0<=a['side']<2 and a['impactTick']>a['tick'] for a in frame['attacks'])
            assert all(h['damage']>=0 for h in frame['hits'])
            if terminated or truncated:break
        return samples,attacks,hits
    finally:
        env.close();nh_env._VisualEngine=original

native,_,_=trajectory(BaselineEngine)
observed,attacks,hits=trajectory(nh_env._VisualEngine)
assert len(native)==len(observed)
for baseline,visual in zip(native,observed):
    np.testing.assert_array_equal(baseline[0],visual[0])
    assert baseline[1:]==visual[1:]
assert attacks>0 and hits>0
print('Visual observer passed: exact native observations, rewards, HP and terminal outcomes preserved; executed attacks and landed hits exported.')
