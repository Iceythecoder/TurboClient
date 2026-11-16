"""Exercise actual NH starter inventory, switches, combat timing and specials."""
from collections import Counter
import os
import subprocess
import sys
import numpy as np
from nh_starter_env import NHStarterEnv

if '--owned-child-check' in sys.argv:
    env=NHStarterEnv(max_ticks=32)
    try:
        print(env.process.pid,flush=True)
        sys.stdin.read()
    finally:env.close()
    sys.exit(0)

def tally(fighter,allowed):
    worn=[i for i in fighter['equipment'] if i>0]
    carried=[s['itemId'] for s in fighter['inventory'] if s is not None]
    assert set(worn)<=allowed
    return Counter(i for i in worn+carried if i in allowed)

with_env=NHStarterEnv(max_ticks=128)
try:
    allowed=set(with_env.descriptor['allowed_gear'])
    assert not allowed.intersection({26382,27238,26386,22647,26243,26245,26374,27690,22613,29796,4153})
    obs,info=with_env.reset(seed=142)
    assert info['frame']['fighters'][0]['weapon']==11791
    assert len(info['frame']['fighters'][0]['inventory'])==28
    assert info['frame']['fighters'][0]['food']==14
    expected=[tally(f,allowed) for f in info['frame']['fighters']]
    attacks=[]
    for _ in range(16):
        obs,reward,done,cut,info=with_env.step(np.array([1,1,0,0,0]))
        attacks.extend(a for a in info['frame']['attacks'] if a['side']==0)
        for i,f in enumerate(info['frame']['fighters']):assert tally(f,allowed)==expected[i]
    assert [a['tick'] for a in attacks]==[0,5,10,15],attacks
    assert all(a['weapon']==11791 for a in attacks)
    assert info['frame']['fighters'][1]['weapon']==21902
    with_env.reset(seed=143)
    moves=with_env.descriptor['actions']['movement']
    with_env.step(np.array([0,1,0,1,0]))  # Eat to make room to stow shield.
    with_env.step(np.array([0,1,moves.index('move_e2'),0,0]))
    with_env.step(np.array([0,1,moves.index('move_e1'),0,0]))
    saw_spec=False
    for _ in range(12):
        _,_,_,_,info=with_env.step(np.array([4,1,0,0,0]))
        saw_spec |= any(a['side']==0 and a['special'] and a['weapon']==11802 for a in info['frame']['attacks'])
        for i,f in enumerate(info['frame']['fighters']):assert tally(f,allowed)==expected[i]
    assert saw_spec,'Actual AGS special did not launch'
    rng=np.random.default_rng(50)
    for fight in range(3):
        with_env.reset(seed=fight)
        for _ in range(128):
            action=np.array([rng.integers(n) for n in with_env.action_space.nvec])
            _,_,done,cut,info=with_env.step(action)
            for i,f in enumerate(info['frame']['fighters']):assert tally(f,allowed)==expected[i]
            if done or cut:break
    def replay():
        states=[];obs,info=with_env.reset(seed=452)
        for _ in range(32):
            obs,reward,done,cut,info=with_env.step(np.array([2,0,0,0,0]))
            states.append((obs.copy(),reward,info['hp'],info['frame']['fighters'][0]['equipment']))
        return states
    a,b=replay(),replay()
    for first,second in zip(a,b):
        np.testing.assert_array_equal(first[0],second[0]);assert first[1:]==second[1:]
finally:with_env.close()
assert with_env.process.poll() is not None
if os.name=='nt':
    import ctypes
    from ctypes import wintypes
    child=subprocess.Popen([sys.executable,__file__,'--owned-child-check'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
    try:
        node_pid=int(child.stdout.readline())
        kernel=ctypes.WinDLL('kernel32',use_last_error=True)
        kernel.OpenProcess.restype=wintypes.HANDLE
        kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
        kernel.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD]
        kernel.CloseHandle.argtypes=[wintypes.HANDLE]
        handle=kernel.OpenProcess(0x100000,False,node_pid)
        child.kill();child.wait(timeout=3)
        if handle:
            try:assert kernel.WaitForSingleObject(handle,5000)==0,'Node did not exit after its owner was cancelled'
            finally:kernel.CloseHandle(handle)
    finally:
        if child.poll() is None:child.kill();child.wait(timeout=3)
        child.stdin.close();child.stdout.close()
print('NH starter checks passed: exact preset, both fighters restricted, item conservation, 5-tick Barrage, real AGS specials, randomized swaps, deterministic resets and worker shutdown.')
