"""Gym bridge to the pinned browser's NH combat with its exact starter items."""
from pathlib import Path
import hashlib
import json
import os
import queue
import subprocess
import threading
import gymnasium as gym
import numpy as np
from gymnasium import spaces

ROOT=Path(__file__).resolve().parent
CONTRACT='nh-browser-starter-sb3-v2'
ENVIRONMENT='nh-trainer-browser-starter-v2'
NVEC=[8,6,26,6,2]
INPUT_SIZE=114
NODE=ROOT.parent/'tools/node-v22.22.0-win-x64/node.exe'
WORKER=ROOT/'browser-source/nh-starter/worker.mjs'

class NHStarterEnv(gym.Env):
    metadata={'render_modes':[]}
    def __init__(self,opponent='fixed-ranged',max_ticks=256,observer=None,trace_output=None,engine_revision=2,worker_path=None):
        if opponent not in ['fixed-ranged','fixed-magic','fixed-melee','seeded-rapid-switch'] and not (worker_path is not None and opponent in ['stationary-ranged','seeded-random-prayer','mirror']):
            raise ValueError('Unsupported NH opponent')
        if not 32<=max_ticks<=1200:
            raise ValueError('Fight length must be 32..1200 ticks')
        self.engine_revision=engine_revision
        if engine_revision not in [1,2]:raise ValueError('Unsupported simulator revision')
        self.worker_path=WORKER if engine_revision==2 else WORKER.with_name('worker-v1.mjs')
        if worker_path is not None:
            candidate=Path(worker_path).resolve()
            if candidate!=(ROOT/"browser-source/nh-starter/worker-stage3.mjs").resolve() or engine_revision!=2 or trace_output is not None:
                raise ValueError("Only the isolated Stage 3 bridge is supported; no Stage 1 tracing")
            self.worker_path=candidate
        self.nvec=NVEC if engine_revision==2 else [8,5,26,6,2]
        self.contract=CONTRACT if engine_revision==2 else 'nh-browser-starter-sb3-v1'
        if engine_revision==1 and trace_output is not None:raise ValueError('Legacy adapter does not provide Stage 1 telemetry')
        self.opponent,self.max_ticks,self.observer=opponent,int(max_ticks),observer
        self.action_space=spaces.MultiDiscrete(np.array(self.nvec,dtype=np.int64))
        self.observation_space=spaces.Box(-np.inf,np.inf,(INPUT_SIZE,),dtype=np.float32)
        self.trace=None
        self.done=True;self.messages=queue.Queue(maxsize=2)
        env=dict(os.environ,NH_RUNTIME_ASSET_ROOT=str(ROOT/'browser-source/fixtures'))
        self.process=subprocess.Popen([str(NODE),str(self.worker_path)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,text=True,encoding='utf-8',env=env,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        self.reader=threading.Thread(target=self._read,name='nh-starter-json',daemon=True)
        self.reader.start()
        try:
            self.descriptor=self._request({'command':'describe'})
            if self.descriptor['contract']!=self.contract or self.descriptor['nvec']!=self.nvec or self.descriptor['input_size']!=INPUT_SIZE:
                raise ValueError('NH starter engine contract mismatch')
            self.signature=hashlib.sha256(json.dumps(self.descriptor,sort_keys=True,separators=(',',':')).encode()).hexdigest()
            if trace_output is not None:
                from fight_trace import FightTrace
                self.trace=FightTrace(trace_output,self.descriptor,self.signature,WORKER,self.opponent,self.max_ticks)
        except Exception:
            self.close();raise
    def _read(self):
        try:
            for line in self.process.stdout:
                if len(line)>131072:
                    self.messages.put(ValueError('NH starter response too large'));break
                self.messages.put(json.loads(line))
        except Exception as error:
            self.messages.put(error)
        finally:
            self.messages.put(RuntimeError('NH starter worker stopped'))
    def _request(self,command):
        if self.process.poll() is not None:
            raise RuntimeError('NH starter worker is not running')
        self.process.stdin.write(json.dumps(command,separators=(',',':'))+'\n');self.process.stdin.flush()
        try:result=self.messages.get(timeout=30)
        except queue.Empty as error:
            self.close();raise RuntimeError('NH starter engine response timed out') from error
        if isinstance(result,Exception):raise result
        if 'error' in result:raise RuntimeError(result['error'])
        return result
    def _result(self,result,reset=False):
        obs=np.asarray(result['observation'],dtype=np.float32)
        reward=float(result['reward']);terminated=bool(result['terminated']);truncated=bool(result['truncated'])
        if obs.shape!=(INPUT_SIZE,) or not np.isfinite(obs).all() or not np.isfinite(reward):
            raise RuntimeError('Invalid NH observation/reward')
        info=result['info'];self.done=terminated or truncated
        self.current_tick=info['tick'];self.episode=info['frame']['episode']
        self.latest_info=info
        if self.trace is not None:
            from fight_trace import capture
            payload={'type':'reset','backend_seed':self.backend_seed,'options':self.reset_options} if reset else {'type':'step','action':self.requested_action}
            self.trace.write({**payload,'result':capture(obs,reward,terminated,truncated,info)})
        if self.observer is not None:self.observer(obs,reward,terminated,truncated,info,reset)
        return obs,reward,terminated,truncated,info
    def reset(self,*,seed=None,options=None):
        super().reset(seed=seed)
        actual_seed=int(self.np_random.integers(0,2**31-1))
        obs,_,_,_,info=self.reset_from_trace(actual_seed,options or {})
        return obs,info
    def reset_from_trace(self,backend_seed,options):
        self.backend_seed=int(backend_seed);self.reset_options=options
        return self._result(self._request({'command':'reset','seed':self.backend_seed,'maxTicks':self.max_ticks,'opponent':self.opponent,'options':options}),True)
    def step(self,action):
        if self.done:raise RuntimeError('Reset the NH fight before stepping')
        action=np.asarray(action)
        if not self.action_space.contains(action):raise ValueError('Invalid NH starter action vector')
        self.requested_action=action.astype(int).tolist()
        return self._result(self._request({'command':'step','action':action.astype(int).tolist(),'expectedTick':self.current_tick,'episode':self.episode}))
    def legal_action_mask(self):
        return self.latest_info['legal_action_mask']
    def action_masks(self):
        return np.concatenate([np.asarray(channel,dtype=bool) for channel in self.legal_action_mask()['channels']])
    def structured_observation(self):
        return self.latest_info['structured']
    def close(self):
        if getattr(self,'trace',None) is not None:self.trace.close()
        process=getattr(self,'process',None)
        if process is not None:
            if process.poll() is None:process.kill()
            try:process.wait(timeout=3)
            except subprocess.TimeoutExpired:pass
            if process.stdin:process.stdin.close()
            if process.stdout:process.stdout.close()
