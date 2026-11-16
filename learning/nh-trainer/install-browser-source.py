"""Fetch pinned renderer code/assets; verify Git blob hashes before local use."""
from pathlib import Path
import concurrent.futures, hashlib, json, urllib.request
root = Path(__file__).resolve().parent
tree = json.loads((root / 'upstream-tree.json').read_text(encoding='utf-8-sig'))
dest = root / 'browser-source'
entries = [e for e in tree['tree'] if e['type'] == 'blob' and
           (e['path'].startswith('src/render/') or e['path'].startswith('src/sim/') or
            e['path'].startswith('src/assets/') or e['path'].startswith('src/source/') or
            e['path'].startswith('src/generated/') or e['path'].startswith('src/bot/') or
            e['path']=='src/ui/runtimeSetupPresets.ts' or
            e['path'].startswith('fixtures/assets/') or e['path'].startswith('fixtures/render/') or
            e['path'].startswith('fixtures/external-items/') or e['path'] in ('LICENSE','NOTICE.md'))]
def get(e):
    path = dest / e['path']
    def valid(data): return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest() == e['sha']
    if path.exists() and valid(path.read_bytes()): return
    data = urllib.request.urlopen('https://raw.githubusercontent.com/NotCoco/nh-trainer/'+tree['sha']+'/'+e['path'], timeout=60).read()
    if not valid(data): raise RuntimeError('Source integrity mismatch: '+e['path'])
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(data)
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    list(pool.map(get, entries))
(dest/'renderer-provenance.json').write_text(json.dumps({'commit':tree['sha'],'files':{e['path']:e['sha'] for e in entries}},indent=2))
print('Pinned renderer files verified:',len(entries))
