"""Explicit opt-in local synthetic telemetry with integrity and deterministic replay."""
import hashlib,json
from pathlib import Path

SCHEMA='nh-fight-trace-v1'
def canonical(value):return json.dumps(value,sort_keys=True,separators=(',',':'),allow_nan=False)
def digest(value):return hashlib.sha256(canonical(value).encode('utf-8')).hexdigest()
def capture(observation,reward,terminated,truncated,info):
    return {'observation':observation.tolist(),'reward':float(reward),'terminated':bool(terminated),'truncated':bool(truncated),'info':info}

class FightTrace:
    def __init__(self,path,descriptor,signature,worker_path,opponent,max_ticks):
        self.path=Path(path).resolve();self.path.parent.mkdir(parents=True,exist_ok=True)
        self.stream=self.path.open('x',encoding='utf-8',newline='\n');self.previous='';self.rows=0
        self.write({'type':'header','schema':SCHEMA,'descriptor':descriptor,'signature':signature,
            'worker_sha256':hashlib.sha256(Path(worker_path).read_bytes()).hexdigest(),
            'opponent':opponent,'max_ticks':max_ticks,'privacy':'local synthetic fights; explicit recording; no names/accounts/screenshots'})
    def write(self,payload):
        payload=json.loads(canonical(payload));payload['previous_hash']=self.previous
        value_hash=digest(payload);payload['hash']=value_hash
        self.stream.write(canonical(payload)+'\n');self.stream.flush();self.previous=value_hash;self.rows+=1
    def close(self):
        if not self.stream.closed:self.stream.close()

def read_trace(path):
    previous='';rows=[]
    with Path(path).open(encoding='utf-8') as stream:
        for index,line in enumerate(stream,1):
            if len(line)>1048576:raise ValueError('Trace row too large')
            row=json.loads(line);actual=row.pop('hash')
            if row.get('previous_hash')!=previous or digest(row)!=actual:raise ValueError('Trace integrity error at row '+str(index))
            previous=actual;rows.append(row)
    if not rows or rows[0].get('schema')!=SCHEMA or rows[0].get('type')!='header':raise ValueError('Unsupported trace header')
    if not any(row.get('type')=='step' for row in rows):raise ValueError('Trace contains no completed transitions')
    return rows

def replay(path):
    from nh_starter_env import NHStarterEnv,WORKER
    rows=read_trace(path);header=rows[0]
    env=NHStarterEnv(opponent=header['opponent'],max_ticks=header['max_ticks'])
    episodes=steps=0
    try:
        if env.signature!=header['signature'] or hashlib.sha256(WORKER.read_bytes()).hexdigest()!=header['worker_sha256']:
            raise ValueError('Trace engine/version mismatch; use the original recorded bundle')
        for row in rows[1:]:
            if row['type']=='reset':
                result=env.reset_from_trace(row['backend_seed'],row['options']);episodes+=1
            elif row['type']=='step':
                import numpy as np
                result=env.step(np.array(row['action'],dtype=np.int64));steps+=1
            else:raise ValueError('Unsupported trace record type')
            current=capture(*result)
            if canonical(current)!=canonical(row['result']):
                raise AssertionError('Replay diverged at episode '+str(episodes)+' tick '+str(env.current_tick))
        return {'passed':True,'episodes':episodes,'steps':steps,'exact_observations':True,'exact_events':True,'exact_rewards':True}
    finally:env.close()
