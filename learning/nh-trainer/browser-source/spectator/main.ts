import './style.css';
import * as T from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { composeNhPlayerModel, updateNhPlayerModelPainterOrder, updateNhAnimatedTextures, type NhPlayerModelSources } from '../src/render/nhPlayerModel';
import { attachNhAnimationMetadata, applyNhActorAnimation, applyNhSequenceAnimation, nhRenderSequenceFromRawSequence, type NhAnimationFixtures } from '../src/render/nhSequencePlayback';
import { buildNhSceneCollision, type NhSceneCollision } from '../src/render/nhSceneCollision';
type Fighter = {
    x: number;
    y: number;
    hp: number;
    maxHp: number;
    prayer: number;
    food: number;
    spec: number;
    freezeTicks: number;
    cooldown: number;
    weapon: number;
    weaponName: string;
    style: string;
    overhead: string;
    lastIntent: string;
    equipment: number[];
};
type Attack = {
    sequence?: string;
    side: number;
    tick: number;
    impactTick: number;
    style: string;
    special: boolean;
    specKind: number;
    weapon: number;
    fromX: number;
    fromY: number;
    toX: number;
    toY: number;
};
type Frame = {
    profile?: string;
    episode: number;
    tick: number;
    steps: number;
    mode: string;
    phase: string;
    finished: boolean;
    wins: number;
    losses: number;
    draws: number;
    tickDelayMs: number;
    fighters: Fighter[];
    attacks: Attack[];
    hits: {
        side: number;
        damage: number;
        sourceTick: number;
    }[];
};
type Slot = {
    root: T.Group;
    model: T.Object3D | null;
    key: string;
    from: T.Vector3;
    to: T.Vector3;
    animation: string;
    actionAt: number;
    actionEnd: number;
};
const canvas = document.querySelector<HTMLCanvasElement>('#game')!, overlay = document.querySelector<HTMLCanvasElement>('#overlay')!, stage = document.querySelector<HTMLElement>('#stage')!, loading = document.querySelector<HTMLElement>('#loading')!;
const scene = new T.Scene();
scene.background = new T.Color(0x16221b);
scene.fog = new T.Fog(0x16221b, 8, 22);
const camera = new T.PerspectiveCamera(48, 1, .02, 100);
const renderer = new T.WebGLRenderer({ canvas, antialias: false, preserveDrawingBuffer: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
const ctx = overlay.getContext('2d')!;
const slots: Slot[] = [0, 1].map(() => ({ root: new T.Group(), model: null, key: '', from: new T.Vector3(), to: new T.Vector3(), animation: 'idle', actionAt: 0, actionEnd: 0 }));
slots.forEach(s => scene.add(s.root));
let sources: NhPlayerModelSources, animations: NhAnimationFixtures, collision: NhSceneCollision, current: Frame | null = null, previous: Frame | null = null, receivedAt = 0, sequence = -1, busy = false, failed = false;
let yaw = 2.75, pitch = .88, distance = 4, focus = new T.Vector3(4, 0, 7), drag: {
    x: number;
    y: number;
} | null = null;
const counters = { attacks: 0, hits: 0, prayers: 0, frames: 0 };
(window as any).__owlCounters = counters;
const hitSprites: {
    side: number;
    damage: number;
    start: number;
    index: number;
}[] = [];
const effects: {
    root: T.Object3D;
    start: number;
    duration: number;
    from: T.Vector3;
    to: T.Vector3;
    sequence: any;
    travel: boolean;
}[] = [];
const effectAssets = new Map<string, {
    scene: T.Object3D;
    metadata: any;
    sequence: any;
}>();
let prayerAtlas: HTMLImageElement, hitsAtlas: HTMLImageElement, prayerDefs: any, hitsDefs: any, itemDefs: any;
const warnings = new Set<string>();
async function json(path: string) { const r = await fetch(path, { cache: 'no-store' }); if (!r.ok)
    throw new Error(`${path}: HTTP ${r.status}`); return r.json(); }
function image(path: string): Promise<HTMLImageElement> { return new Promise((resolve, reject) => { const i = new Image(); i.onload = () => resolve(i); i.onerror = () => reject(new Error('Cannot load ' + path)); i.src = path; }); }
const glb = (path: string) => new GLTFLoader().loadAsync(path);
function world(x: number, y: number) { const p = collision.worldToSceneTile({ x, y, plane: 0 }); return new T.Vector3(p.x, collision.sampleHeight(p), p.z); }
function dispose(root: T.Object3D) { root.traverse(n => { if ((n as T.Mesh).isMesh) {
    const m = n as T.Mesh;
    m.geometry.dispose();
    for (const mat of Array.isArray(m.material) ? m.material : [m.material])
        mat.dispose();
} }); }
function staticMaterials(root: T.Object3D) { root.traverse(n => { const m = n as T.Mesh; if (!m.isMesh)
    return; const convert = (old: any) => { const mat = new T.MeshBasicMaterial({ map: old.map, color: old.color, vertexColors: old.vertexColors, transparent: old.transparent, opacity: old.opacity, side: old.side, alphaTest: old.alphaTest }); old.dispose(); return mat; }; m.material = Array.isArray(m.material) ? m.material.map(convert) : convert(m.material); }); }
function makeModel(side: number, f: Fighter) { const s = slots[side], ids = f.equipment.filter(id => id > 0), key = ids.join(','); if (key === s.key)
    return; const next = composeNhPlayerModel(sources, { itemIds: ids, bodyColors: [0, 0, 0, 0, 0] }); attachNhAnimationMetadata(next.scene, next.metadata); next.scene.scale.setScalar(1 / 256); if (s.model) {
    s.root.remove(s.model);
    dispose(s.model);
} s.model = next.scene; s.key = key; s.root.add(next.scene); }
function sequenceFor(a: Attack) { if (a.sequence && animations.sequences.has(a.sequence)) return a.sequence; if (a.special) {
    if (a.weapon === 11802)
        return 'ags_special';
    if (a.weapon === 4153 || a.weapon === 24225)
        return 'gmaul_special';
    if (a.weapon === 22613)
        return 'vesta_longsword_special';
    if (a.weapon === 27690)
        return 'voidwaker_special';
} if (a.style === 'MAGIC')
    return 'barrage_cast'; if (a.style === 'RANGED')
    return a.weapon === 27652 ? 'bow_attack' : 'crossbow_attack'; if (a.weapon === 12006 || a.weapon === 4151)
    return 'whip_attack'; if (a.weapon === 11802)
    return 'godsword_attack'; if (a.weapon === 4153 || a.weapon === 24225)
    return 'gmaul_attack'; return 'sword_slash_attack'; }
function ready(f: Fighter, moving: boolean, running=false) { if (moving)
    return running ? 'run' : 'walk'; if (f.style === 'MAGIC')
    return 'wand_ready'; if (f.style === 'RANGED')
    return 'crossbow_ready'; if (f.weapon === 11802)
    return 'godsword_ready'; return 'idle'; }
function cloneEffect(asset: {
    scene: T.Object3D;
    metadata: any;
    sequence: any;
}) { const root = asset.scene.clone(true); root.traverse(n => { const m = n as T.Mesh; if (m.isMesh) {
    m.geometry = m.geometry.clone();
    m.material = Array.isArray(m.material) ? m.material.map(x => x.clone()) : m.material.clone();
} }); attachNhAnimationMetadata(root, asset.metadata); root.scale.setScalar(1 / 256); return root; }
function addEffect(name: string, from: T.Vector3, to: T.Vector3, duration: number, travel: boolean, delay = 0) { const asset = effectAssets.get(name); if (!asset) {
    warnings.add('Unavailable effect: ' + name);
    return;
} const root = cloneEffect(asset); root.visible = delay <= 0; scene.add(root); effects.push({ root, from, to, duration, start: performance.now() + delay, sequence: asset.sequence, travel }); }
function accept(frame: Frame) {
    const now = performance.now(), reset = !current || frame.episode !== current.episode;
    previous = reset ? null : current;
    current = frame;
    receivedAt = now;
    loading.hidden = true;
    counters.frames++;
    counters.attacks += (frame.attacks ?? []).length;
    counters.hits += (frame.hits ?? []).length;
    if (reset) {
        hitSprites.length = 0;
        for (const e of effects) {
            scene.remove(e.root);
            dispose(e.root);
        }
        effects.length = 0;
        slots.forEach(s => { s.actionEnd = 0; });
    }
    const stacked=(f:Frame|null)=>!!f&&f.fighters[0].x===f.fighters[1].x&&f.fighters[0].y===f.fighters[1].y;
    
    frame.fighters.forEach((f, i) => { makeModel(i, f); const s = slots[i]; s.from.copy(reset ? world(f.x, f.y) : s.root.position); s.to.copy(world(f.x, f.y)); if (reset) { s.root.position.copy(s.to); s.from.copy(s.to); } const other = frame.fighters[1 - i]; s.root.rotation.y = Math.atan2(other.x - f.x, other.y - f.y); if (previous && f.food < previous.fighters[i].food) {
        s.animation = 'consume';
        s.actionAt = now;
        s.actionEnd = now + Math.max(200, frame.tickDelayMs);
    } });
    for (const a of frame.attacks ?? []) {
        const s = slots[a.side];
        s.animation = sequenceFor(a);
        s.actionAt = now;
        s.actionEnd = now + Math.max(200, (frame.tickDelayMs || 600) * 1.1);
        const from = world(a.fromX, a.fromY), to = world(a.toX, a.toY);
        from.y += .55;
        to.y += .45;
        const duration = Math.max(100, (a.impactTick - a.tick) * Math.max(1, frame.tickDelayMs));
        if (a.style === 'RANGED')
            addEffect(a.weapon === 27652 ? 'webweaver_arrow' : 'bolt_projectile', from, to, duration, true);
        // The upstream Ice Barrage visual skips travel: show its target effect.
        if (a.style === 'MAGIC' && !a.special)
            addEffect('ice_barrage_hit', world(a.toX, a.toY), world(a.toX, a.toY), Math.max(400, frame.tickDelayMs), false, Math.max(0, a.impactTick + 1 - frame.tick) * frame.tickDelayMs);
        if (a.special && a.weapon === 11802)
            addEffect('ags_special', world(a.fromX, a.fromY), world(a.fromX, a.fromY), 600, false);
    }
    const hitCounts = [0, 0];
    for (const hit of frame.hits ?? []) {
        hitSprites.push({ side: hit.side, damage: hit.damage, start: now, index: hitCounts[hit.side]++ });
    }
    if (reset) {
        focus.copy(slots[0].to).add(slots[1].to).multiplyScalar(.5);
        focus.y += .45;
    }
    cards(frame);
    (window as any).__owlFrame = { episode: frame.episode, tick: frame.tick, hp: frame.fighters.map(f => f.hp), equipment: frame.fighters.map(f => f.equipment), attacks: frame.attacks?.length ?? 0, hits: frame.hits?.length ?? 0 };
}
function text(parent: HTMLElement, label: string) { const line = document.createElement('div'); line.textContent = label; parent.append(line); }
function cards(f: Frame) { f.fighters.forEach((p, i) => { const card = document.getElementById(i === 0 ? 'agent' : 'opponent')!; card.replaceChildren(); const title = document.createElement('b'); title.textContent = i === 0 ? (f.phase.startsWith('Stage 3') ? 'Scripted fighter' : f.mode === 'training' ? 'Learning agent' : 'Saved checkpoint') : 'Opponent'; card.append(title); const bar = document.createElement('div'); bar.className = 'health'; const fill = document.createElement('span'); fill.style.width = Math.max(0, Math.min(100, p.hp / p.maxHp * 100)) + '%'; bar.append(fill); card.append(bar); text(card, `HP ${p.hp} · Prayer ${p.prayer} · Food ${p.food} · Special ${p.spec}%`); text(card, `${p.weaponName} · Overhead ${p.overhead.toLowerCase().replaceAll('_', ' ')} · Frozen ${p.freezeTicks} ticks`); const other=f.fighters[1-i]; const separation=Math.max(Math.abs(p.x-other.x),Math.abs(p.y-other.y));
 text(card, `Tile (${p.x}, ${p.y}) · Distance ${separation} tiles`);
 if(i===0){ const dd=document.createElement('div');dd.id='dd-status';dd.textContent=separation===0&&other.freezeTicks>0?'Entering DD…':'DD not active';card.append(dd); }
 text(card, `Attack cooldown ${p.cooldown} · ${p.lastIntent}`); const items = document.createElement('div'); items.className = 'equipment'; p.equipment.forEach(id => { if (id <= 0)
    return; const def = itemDefs.sprites.find((s: any) => s.itemId === id && s.variant === 'normal' && !s.selected); const icon = document.createElement('span'); icon.title = sources.cacheItems[String(id)]?.name ?? String(id); icon.className = 'item'; if (def) {
    icon.style.backgroundImage = 'url(render/sprites/item_sprites.png)';
    icon.style.backgroundPosition = `-${def.x}px -${def.y}px`;
}
else {
    icon.textContent = '?';
    warnings.add('No inventory icon for item ' + id);
} items.append(icon); }); card.append(items); }); document.getElementById('progress')!.textContent = `Fight ${f.episode} · Tick ${f.tick} · Decisions ${f.steps} · Wins ${f.wins} / Losses ${f.losses} / Draws ${f.draws} · ${f.mode === 'evaluation' ? 'Evaluation — weights fixed' : f.phase}`; }
function sprite(atlas: HTMLImageElement, def: any, x: number, y: number) { if (def)
    ctx.drawImage(atlas, def.x, def.y, def.width, def.height, Math.round(x - def.width / 2), Math.round(y - def.height / 2), def.width, def.height); }
function project(pos: T.Vector3) { const p = pos.clone().project(camera); return { x: (p.x + 1) / 2 * overlay.width, y: (1 - p.y) / 2 * overlay.height, visible: p.z < 1 }; }
function render(now: number) {
    requestAnimationFrame(render);
    if (!current)
        return;
    const tickMs = Math.max(1, current.tickDelayMs || 1), travelMs=Math.max(1,Math.min(350,tickMs*.65)), t = Math.min(1, (now - receivedAt) / travelMs);
    slots.forEach((s, i) => { const f = current!.fighters[i]; s.root.position.lerpVectors(s.from, s.to, t); const moving = s.from.distanceToSquared(s.to) > .001 && t < 1; if (s.model) {
        const active = now < s.actionEnd;
        const running=Math.max(Math.abs(s.from.x-s.to.x),Math.abs(s.from.z-s.to.z))>.505;
        const animation=moving?ready(f,true,running):active?s.animation:ready(f,false);
        const other=slots[1-i].root.position;
        s.root.rotation.y=moving?Math.atan2(s.to.x-s.from.x,s.to.z-s.from.z):Math.atan2(other.x-s.root.position.x,other.z-s.root.position.z);
        applyNhActorAnimation(s.model, animation, Math.max(0,(now-(moving?receivedAt:active?s.actionAt:0))/20), animations, moving?'loop':active?'primary':'loop', !moving&&active?ready(f,false):undefined, now/20, {interpolateFrames:true});
        (s as any).displayAnimation=animation;
        updateNhPlayerModelPainterOrder(s.model, camera);
        updateNhAnimatedTextures(s.model, Math.floor(now / 20));
    } });
    const a=current.fighters[0],b=current.fighters[1];
    const nativeDD=a.x===b.x&&a.y===b.y&&b.freezeTicks>0;
    const renderedDD=nativeDD&&Math.abs(slots[0].root.position.x-slots[1].root.position.x)<1e-6&&Math.abs(slots[0].root.position.z-slots[1].root.position.z)<1e-6;
    const ddLabel=renderedDD?'DD confirmed: same tile as frozen opponent':nativeDD?'Entering DD…':t<1&&previous&&previous.fighters[0].x===previous.fighters[1].x&&previous.fighters[0].y===previous.fighters[1].y?'Stepping out to attack…':'DD not active';
    const ddStatus=document.getElementById('dd-status');if(ddStatus)ddStatus.textContent=ddLabel;
    const connectionCaption=document.getElementById('connection');if(connectionCaption&&current.phase.startsWith('Stage 3'))connectionCaption.textContent=`NH starter · Tick ${current.tick} · ${ddLabel}`;
    (window as any).__owlMotion={tick:current.tick,progress:t,nativeDD,renderedDD,animations:slots.map(s=>(s as any).displayAnimation)};
    const target = slots[0].root.position.clone().add(slots[1].root.position).multiplyScalar(.5);
    target.y += .5;
    focus.lerp(target, .08);
    camera.position.copy(focus).add(new T.Vector3(Math.sin(yaw) * Math.cos(pitch) * distance, Math.sin(pitch) * distance, Math.cos(yaw) * Math.cos(pitch) * distance));
    camera.lookAt(focus);
    for (let i = effects.length - 1; i >= 0; i--) {
        const e = effects[i], p = (now - e.start) / e.duration;
        if (p < 0) continue;
        e.root.visible = true;
        if (p > 1) {
            scene.remove(e.root);
            dispose(e.root);
            effects.splice(i, 1);
            continue;
        }
        e.root.position.lerpVectors(e.from, e.to, e.travel ? p : 0);
        if (e.travel)
            e.root.position.y += Math.sin(p * Math.PI) * .15;
        if (e.sequence)
            applyNhSequenceAnimation(e.root, e.sequence, (now - e.start) / 20, animations, 'primary');
    }
    renderer.render(scene, camera);
    ctx.clearRect(0, 0, overlay.width, overlay.height);
    ctx.font = 'bold 12px Arial';
    ctx.textAlign = 'center';
    ctx.lineWidth = 3;
    slots.forEach((s, i) => { const f = current!.fighters[i], p = project(s.root.position.clone().add(new T.Vector3(0, .85, 0))); if (!p.visible)
        return; ctx.fillStyle = '#bb2525'; ctx.fillRect(p.x - 17, p.y, 34, 5); ctx.fillStyle = '#18d13b'; ctx.fillRect(p.x - 17, p.y, 34 * Math.max(0, Math.min(1, f.hp / f.maxHp)), 5); const prayer = f.overhead.toLowerCase(); if (prayerDefs.sprites.some((x: any) => x.alias === prayer))
        counters.prayers++; sprite(prayerAtlas, prayerDefs.sprites.find((x: any) => x.alias === prayer), p.x, p.y - 19); ctx.strokeStyle = '#000'; ctx.fillStyle = i === 0 ? '#61d2ff' : '#ffb36b'; ctx.strokeText(i === 0 ? 'Agent' : 'Opponent', p.x, p.y - 42); ctx.fillText(i === 0 ? 'Agent' : 'Opponent', p.x, p.y - 42); });
    for (let i = hitSprites.length - 1; i >= 0; i--) {
        const hit = hitSprites[i], age = now - hit.start;
        if (age > 1000) {
            hitSprites.splice(i, 1);
            continue;
        }
        const p = project(slots[hit.side].root.position.clone().add(new T.Vector3(0, .4, 0)));
        const x = p.x + (hit.index % 2 ? 18 : -6), y = p.y - Math.floor(hit.index / 2) * 24 - age / 80;
        const def = hitsDefs.sprites.find((d: any) => d.spriteId === (hit.damage > 0 ? 1359 : 1358));
        sprite(hitsAtlas, def, x, y);
        ctx.fillStyle = '#fff';
        ctx.strokeStyle = '#000';
        ctx.strokeText(String(hit.damage), x, y + 4);
        ctx.fillText(String(hit.damage), x, y + 4);
    }
    (window as any).__owlPositions = slots.map(s=>({x:s.root.position.x,z:s.root.position.z,toX:s.to.x,toZ:s.to.z}));
    (window as any).__owlRendered = true;
}
function resize() { const w = stage.clientWidth, h = stage.clientHeight; renderer.setSize(w, h, false); overlay.width = w; overlay.height = h; camera.aspect = w / h; camera.updateProjectionMatrix(); }
new ResizeObserver(resize).observe(stage);
canvas.addEventListener('pointerdown', e => { drag = { x: e.clientX, y: e.clientY }; canvas.setPointerCapture(e.pointerId); });
canvas.addEventListener('pointerup', () => drag = null);
canvas.addEventListener('pointermove', e => { if (!drag)
    return; yaw -= (e.clientX - drag.x) * .01; pitch = Math.max(.25, Math.min(1.45, pitch + (e.clientY - drag.y) * .006)); drag = { x: e.clientX, y: e.clientY }; });
canvas.addEventListener('wheel', e => { e.preventDefault(); distance = Math.max(2, Math.min(14, distance + e.deltaY * .006)); }, { passive: false });
async function setup() {
    const [cacheItems, kits, cacheModels, serverItems, bodyColors, textures, external, frameStore, rawSequences, terrain, objects, arena, placements, pd, hd, id, pi, hi, manifest, spotanims] = await Promise.all([json('assets/defs/cache-items.json'), json('assets/defs/kits.json'), json('assets/models/cache-models.json'), json('assets/defs/server-items.json'), json('assets/defs/body-colors.json'), json('assets/defs/textures.json'), json('external-items/dmm-gear.json'), json('assets/animations/frames.json'), json('assets/animations/sequences.json'), glb('render/maps/inferno_arena.glb'), glb('render/maps/inferno_arena_objects.glb'), json('render/maps/inferno_arena.json'), json('render/maps/inferno_arena_objects.json'), json('render/sprites/prayer_overheads.json'), json('render/sprites/hitsplats.json'), json('render/sprites/item_sprites.json'), image('render/sprites/prayer_overheads.png'), image('render/sprites/hitsplats.png'), json('assets/models/cache-glb-manifest.json'), json('assets/defs/spotanims.json')]);
    for (const item of external.items) {
        cacheItems[String(item.id)] = item.cacheItem;
        Object.assign(cacheModels, item.cacheModels);
        const index = serverItems.findIndex((s: any) => s.id === item.id);
        if (index >= 0)
            serverItems[index] = item.serverItem;
        else
            serverItems.push(item.serverItem);
    }
    sources = { cacheItems, kits, cacheModels, serverItems, bodyColors, textures };
    prayerAtlas = pi;
    hitsAtlas = hi;
    prayerDefs = pd;
    hitsDefs = hd;
    itemDefs = id;
    const sequences = new Map<string, any>();
    const named = ['idle', 'walk', 'consume', 'whip_attack', 'godsword_attack', 'ags_special', 'gmaul_special', 'sword_stab_attack', 'sword_slash_attack', 'crossbow_attack', 'blitz_cast', 'barrage_cast'];
    await Promise.all(named.map(async (name) => sequences.set(name, await json(`render/sequences/${name}.json`))));
    const aliases: Record<string, string> = { run: '824', consume: '829', wand_ready: '813', crossbow_ready: '4591', godsword_ready: '7053', vesta_longsword_special: '7515', voidwaker_special: '11275', bow_attack: '426', gmaul_attack: '1665' };
    for (const [name, id] of Object.entries(aliases))
        if (rawSequences[id])
            sequences.set(name, nhRenderSequenceFromRawSequence(rawSequences[id]));
    animations = { frameStore, sequences, sequencesById: new Map(Object.values(rawSequences).map((s: any) => [s.id, nhRenderSequenceFromRawSequence(s)])) };
    staticMaterials(terrain.scene);
    staticMaterials(objects.scene);
    scene.add(terrain.scene, objects.scene);
    collision = buildNhSceneCollision(arena, placements, { x: 0, y: 0, z: 0 });
    const effectNames = ['bolt_projectile', 'webweaver_arrow', 'ice_barrage_projectile', 'ice_barrage_hit', 'ags_special'];
    await Promise.all(effectNames.map(async (name) => { const e = manifest.exports.find((x: any) => x.output.endsWith('/' + name + '.glb')); if (!e)
        throw new Error('Missing effect ' + name); const [g, metadata] = await Promise.all([glb(e.output.replace('fixtures/', '')), json(e.meshMetadata.replace('fixtures/', ''))]); staticMaterials(g.scene); effectAssets.set(name, { scene: g.scene, metadata, sequence: animations.sequencesById.get(spotanims[String(e.spotanimId)]?.animationId) }); }));
    loading.textContent = 'Waiting for a watched training or evaluation fight.';
    resize();
    requestAnimationFrame(render);
    (window as any).__owlReady = true;
    poll();
}
async function poll() { try {
    const state = await json('api/frame?after=' + sequence);
    busy = state.busy;
    document.getElementById('connection')!.textContent = (busy ? 'Live · ' : 'Stopped · ') + state.status;
    if (state.sequence !== sequence) {
        sequence = state.sequence;
        if (state.frame) {
            accept(state.frame);
        }
        else {
            current = null;
            previous = null;
            loading.hidden = false;
            loading.textContent = 'Waiting for watched fight snapshots. Enable Watch current fight before starting a job.';
        }
    }
    if (current) {
        const caption = document.getElementById('progress')!;
        caption.textContent = `${current.profile === 'nh-starter' ? 'NH starter' : 'DMM'} · Fight ${current.episode} · Tick ${current.tick} · Decisions ${current.steps} · Wins ${current.wins} / Losses ${current.losses} / Draws ${current.draws} · ${busy ? (current.phase.startsWith('Stage 3') ? current.phase.replace('DD confirmed: same tile',(window as any).__owlMotion?.tick===current.tick&&(window as any).__owlMotion?.renderedDD?'DD confirmed: same tile':'Entering DD…') : current.mode === 'evaluation' ? 'Evaluation — weights fixed' : state.phase) : 'Job ended — last snapshot'}`;
    }
    if (warnings.size)
        document.getElementById('connection')!.textContent += ' · ' + Array.from(warnings).join('; ');
    failed = false;
}
catch (e) {
    failed = true;
    document.getElementById('connection')!.textContent = 'Local connection closed. The last snapshot remains visible.';
} setTimeout(poll, failed ? 1000 : 40); }
setup().catch(error => { loading.textContent = 'Game viewer could not load: ' + error.message; (window as any).__owlError = error.message; console.error(error); });
