/** Local NH starter environment using upstream browser combat, not DMM FastSim. */
import { createInterface } from 'node:readline';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createRuntimePlayerCombatState, advanceRuntimePlayerCombat, applyRuntimePlayerCombatPreMovementHits, syncRuntimePlayerCombatStateToInput, runtimePlayerCombatDefaultLevels, runtimePlayerCombatActiveOverheadPrayer, setRuntimePlayerCombatLoadout, setRuntimePlayerCombatPrayers, setRuntimePlayerCombatAutocast, setRuntimePlayerCombatAttackSet, toggleRuntimePlayerCombatSpecial, consumeRuntimePlayerCombatSupply, requestRuntimePlayerCombatAttack, type RuntimePlayerCombatState } from '../src/sim/runtimePlayerCombat';
import { applyRuntimeOpponentPolicyAction, type RuntimePolicyOpponentActorView } from '../src/sim/nh/runtime-policy-opponent';
import { createNhPolicyFeatureState, encodeNhPolicyInput, commitNhPolicyDecisionState } from '../src/sim/nh/policy-features';
import { nhOffenceStyles, nhDefencePrayers, nhMovementIntents, nhMovementDeltas, type NhPolicyAction } from '../src/sim/nh/policy-bridge';
import { inferNhSelectedGearProfile, nhGearProfileWeaponIdForEquipment } from '../src/sim/nh/gearProfile';
import { getAttackDelayStatus } from '../src/sim/combat/timers';
import { movementGate } from '../src/sim/entity/locks';
import { buildNhSceneCollision } from '../src/render/nhSceneCollision';
import { RUNTIME_NH_STAKE_INVENTORY_SLOTS, RUNTIME_NH_STAKE_EQUIPMENT_ENTRIES, runtimeNhStakeSupplies, runtimeInventorySlotsAfterConsumedSupplies } from '../src/ui/runtimeSetupPresets';
import type { RuntimeInventorySlot, RuntimeActorId, RuntimeTile } from '../src/render/runtimeScene';
import type { VisibleEquipment, EquipmentSlot } from '../src/sim/clientView';
export const CONTRACT = 'nh-browser-starter-sb3-v1';
export const NVEC = [8, 5, 26, 6, 2];
export const ACTIONS = { combat: ['WAIT', 'MAGIC_ATTACK', 'RANGED_ATTACK', 'MELEE_ATTACK', 'AGS_SPECIAL', 'MAGIC_EQUIP', 'RANGED_EQUIP', 'MELEE_EQUIP'], prayer: nhDefencePrayers, movement: nhMovementIntents, supply: ['none', 'safe_eat', 'double_eat', 'brew_only', 'restore_reboost', 'panic_full'], equipment: ['style_loadout', 'weapon_only'] };
const slots: Record<string, number> = { head: 0, cape: 1, amulet: 2, weapon: 3, body: 4, shield: 5, legs: 7, hands: 9, feet: 10, ring: 12, ammo: 13 };
const actorIds: RuntimeActorId[] = ['local-player', 'opponent'];
const assets = process.env.NH_RUNTIME_ASSET_ROOT!;
const load = (path: string) => JSON.parse(readFileSync(resolve(assets, path), 'utf8'));
const cacheItems = load('assets/defs/cache-items.json');
const arena = load('render/maps/inferno_arena.json');
const collision = buildNhSceneCollision(arena, load('render/maps/inferno_arena_objects.json'), { x: 0, y: 0, z: 0 });
const starterEquipment: VisibleEquipment = Object.fromEntries(RUNTIME_NH_STAKE_EQUIPMENT_ENTRIES.map(([slot, id]) => [Object.keys(slots).find(key => slots[key] === slot), { itemId: id, name: cacheItems[String(id)].name }])) as VisibleEquipment;
const allowed = new Set([...RUNTIME_NH_STAKE_EQUIPMENT_ENTRIES.map(e => Number(e[1])), ...RUNTIME_NH_STAKE_INVENTORY_SLOTS.flatMap(s => s ? [s.itemId] : [])]);
const allowedGear = new Set([...RUNTIME_NH_STAKE_EQUIPMENT_ENTRIES.map(e => Number(e[1])), 4736, 21902, 4759, 22322, 11802, 12006]);
const profile = inferNhSelectedGearProfile({ equipment: starterEquipment, inventoryItems: RUNTIME_NH_STAKE_INVENTORY_SLOTS.flatMap(s => s ? [{ itemId: s.itemId, name: cacheItems[String(s.itemId)]?.name ?? String(s.itemId) }] : []) });
let state: RuntimePlayerCombatState, inventory: (readonly (RuntimeInventorySlot | null)[])[] = [], features = createNhPolicyFeatureState(), episode = 0, maxTicks = 256, opponent = 'fixed-ranged', seed = 42;
let damage = [0, 0], lastActions = ['Starting', 'Starting'], lastEvents: any[] = [], pendingActions: any[] = [], frames: any[] = [];
function flip(value: any): any { if (typeof value === 'string')
    return value === 'local-player' ? 'opponent' : value === 'opponent' ? 'local-player' : value; if (Array.isArray(value))
    return value.map(flip); if (value && typeof value === 'object')
    return Object.fromEntries(Object.entries(value).map(([key, v]) => [key === 'local-player' ? 'opponent' : key === 'opponent' ? 'local-player' : key, flip(v)])); return value; }
function tile(x: number, y: number): RuntimeTile { return collision.worldToSceneTile({ x, y, plane: 0 }); }
function view(localState: RuntimePlayerCombatState, id: RuntimeActorId, inv: readonly (RuntimeInventorySlot | null)[]): RuntimePolicyOpponentActorView { const a = localState.actors[id]; return { tile: a.tile, loadoutId: a.loadoutId, equipment: a.equipment, gearProfile: profile, inventorySlots: inv, inventoryItems: inv.flatMap(s => s ? [{ itemId: s.itemId, name: cacheItems[String(s.itemId)]?.name ?? String(s.itemId) }] : []) }; }
function route(from: RuntimeTile, target: RuntimeTile, reach: number) { const distance = Math.max(Math.abs(target.x - from.x), Math.abs(target.z - from.z)) / .5; if (distance <= reach)
    return null; const dx = Math.sign(target.x - from.x) * .5, dz = Math.sign(target.z - from.z) * .5; for (const candidate of [{ x: from.x + dx, z: from.z + dz }, { x: from.x + dx, z: from.z }, { x: from.x, z: from.z + dz }])
    if (collision.canStep(from, candidate))
        return candidate; return null; }
function apply(side: number, choose: (context: any) => NhPolicyAction, probe = false) {
    const swapped = side === 0, localState = swapped ? flip(state) : state, own = inventory[side], other = inventory[1 - side];
    const previous = localState.actors.opponent;
    let context: any, selected: NhPolicyAction | undefined;
    const result = applyRuntimeOpponentPolicyAction({ state: localState, controller: { id: 'owl-nh-starter', defencePrayerStrictModelChoice: true, chooseAction(c) { context = c; selected = choose(c); return selected; } }, localActor: view(localState, 'local-player', other), opponentActor: view(localState, 'opponent', own), selfPlayMode: true, allowSourceLoadoutSync: false, inPvpCombatArea: true, tileScale: .5, canStep: (from, to) => collision.canStep(from, to), targetRouteStep: route });
    // Use the upstream helper only to capture its observation context. Apply the
    // selected actions below without its scripted counter/freeze/style overrides.
    if (!probe) {
        control(side, selected!);
        lastActions[side] = `${selected!.offenceStyle} / ${selected!.supplyIntent} / ${selected!.specIntent}`;
        assertOwned();
    }
    return { context, action: selected!, result };
}
function equipmentFor(style: string, special = false): VisibleEquipment { const changes = special ? { weapon: 11802, body: 4736, legs: 4759 } : style === 'magic' ? { weapon: 11791, body: 4091, legs: 4093, shield: 12831 } : style === 'ranged' ? { weapon: 21902, body: 4736, legs: 4759, shield: 12831 } : { weapon: 12006, body: 4736, legs: 4759, shield: 22322 }; const target: any = { ...starterEquipment }; for (const [slot, id] of Object.entries(changes))
    target[slot] = { itemId: id, name: cacheItems[String(id)].name }; if (special)
    delete target.shield; return target; }
function equip(side: number, target: VisibleEquipment, special: boolean, style: string, weaponOnly: boolean) {
    const id = actorIds[side], actor = state.actors[id], next: any = { ...actor.equipment };
    const inv = [...inventory[side]];
    const selected = weaponOnly ? ['weapon'] : ['weapon', 'body', 'legs', 'shield'];
    if (special && next.shield) {
        const free = inv.findIndex(s => s === null);
        if (free < 0)
            return false;
        inv[free] = { itemId: next.shield.itemId, quantity: 1 };
        delete next.shield;
    }
    for (const slot of selected) {
        const wanted = (target as any)[slot];
        if (!wanted || next[slot]?.itemId === wanted.itemId)
            continue;
        const index = inv.findIndex(s => s?.itemId === wanted.itemId);
        if (index < 0)
            return false;
        inv[index] = next[slot] ? { itemId: next[slot].itemId, quantity: 1 } : null;
        next[slot] = wanted;
    }
    inventory[side] = inv;
    state = setRuntimePlayerCombatLoadout(state, id, special ? 'ags-bandos' : style === 'magic' ? 'kodai-robes' : style === 'ranged' ? 'acb-hides' : 'tentacle-bandos');
    state = syncRuntimePlayerCombatStateToInput(state, { tiles: {}, equipment: { [id]: next }, gearProfiles: { [id]: profile } });
    return true;
}
function eat(side: number, supply: any) { const id = actorIds[side], before = state; const result = consumeRuntimePlayerCombatSupply(state, id, supply); state = result.state; if (result.consumed)
    inventory[side] = runtimeInventorySlotsAfterConsumedSupplies(inventory[side], [supply]); return result.consumed; }
function control(side: number, action: NhPolicyAction) {
    const id = actorIds[side], other = actorIds[1 - side];
    const actor = state.actors[id];
    const special = action.specIntent === 'spec_armadyl_godsword';
    let equipped = equip(side, equipmentFor(action.offenceStyle, special), special, action.offenceStyle, action.equipmentIntent === 'weapon_only');
    state = setRuntimePlayerCombatPrayers(state, id, [action.defencePrayer, action.offenceStyle === 'magic' ? 'augury' : action.offenceStyle === 'ranged' ? 'rigour' : 'piety']);
    if (action.supplyIntent === 'safe_eat' || action.supplyIntent === 'double_eat' || action.supplyIntent === 'panic_full')
        eat(side, state.actors[id].supplies.manta_ray > 0 ? 'manta_ray' : 'anglerfish');
    if (action.supplyIntent === 'brew_only' || action.supplyIntent === 'double_eat' || action.supplyIntent === 'panic_full')
        eat(side, 'saradomin_brew');
    if (action.supplyIntent === 'restore_reboost') {
        const a = state.actors[id];
        eat(side, a.prayerPoints < 50 || Object.keys(a.levels).some(k => (a.levels as any)[k] < (a.fixedLevels as any)[k]) ? 'sanfew_serum' : a.supplies.super_combat > 0 ? 'super_combat' : a.supplies.bastion > 0 ? 'bastion' : 'sanfew_serum');
    }
    const delta = nhMovementDeltas[action.movementIntent];
    if (!movementGate(state.actors[id].locks, state.tick).blocked) {
        let destination: RuntimeTile | null = null;
        if (action.movementIntent === 'stand_under' || action.movementIntent === 'pressure')
            destination = route(state.actors[id].tile, state.actors[other].tile, action.movementIntent === 'stand_under' ? 0 : 1);
        else if (delta) {
            let current = state.actors[id].tile;
            for (let n = 0; n < Math.max(Math.abs(delta[0]), Math.abs(delta[1])); n++) {
                const candidate = { x: current.x + (n < Math.abs(delta[0]) ? Math.sign(delta[0]) * .5 : 0), z: current.z + (n < Math.abs(delta[1]) ? Math.sign(delta[1]) * .5 : 0) };
                if (!collision.canStep(current, candidate))
                    break;
                current = candidate;
            }
            destination = current;
        }
        if (destination)
            state = syncRuntimePlayerCombatStateToInput(state, { tiles: { [id]: destination } });
    }
    state = setRuntimePlayerCombatAutocast(state, id, action.offenceStyle === 'magic' && !special ? 'ice-barrage' : null);
    if (action.offenceStyle === 'ranged')
        state = setRuntimePlayerCombatAttackSet(state, id, 1);
    if (special && equipped)
        state = toggleRuntimePlayerCombatSpecial(state, id).state;
    if (action.attackIntent === 'hold' || !equipped)
        state = { ...state, actors: { ...state.actors, [id]: { ...state.actors[id], ordinaryAttackSuppressedTick: state.tick } } };
    else
        state = requestRuntimePlayerCombatAttack(state, id, other);
    state = { ...state, actors: { ...state.actors, [id]: { ...state.actors[id], policyOffenceStyle: action.offenceStyle } } };
}
function decode(vector: number[], context: any): NhPolicyAction {
    let code = vector[0];
    let style = code >= 5 ? nhOffenceStyles[code - 5] : code >= 1 && code <= 3 ? nhOffenceStyles[code - 1] : context.self.lastOffenceStyle ?? 'magic';
    const inv = context.self.inventorySlots ?? [];
    if (code === 4 && context.self.equipment.shield && inv.every((s: any) => s !== null))
        code = 0;
    return { offenceStyle: code === 4 ? 'melee' : style, defencePrayer: nhDefencePrayers[vector[1]], movementIntent: nhMovementIntents[vector[2]], supplyIntent: ACTIONS.supply[vector[3]] as any, specIntent: code === 4 ? 'spec_armadyl_godsword' : 'none', attackIntent: code === 0 || code >= 5 ? 'hold' : 'attack', equipmentIntent: vector[4] === 0 ? 'style_loadout' : 'weapon_only', extendedSupplyAction: false };
}
function scripted(context: any): NhPolicyAction { let style = opponent === 'fixed-magic' ? 'magic' : opponent === 'fixed-melee' ? 'melee' : 'ranged'; if (opponent === 'seeded-rapid-switch')
    style = nhOffenceStyles[((state.tick * 1103515245 + seed) >>> 0) % 3]; return { offenceStyle: style as any, defencePrayer: context.opponent.lastOffenceStyle === 'magic' ? 'protect_from_magic' : context.opponent.lastOffenceStyle === 'ranged' ? 'protect_from_missiles' : 'protect_from_melee', movementIntent: style === 'melee' ? 'pressure' : 'none', supplyIntent: context.self.stats.hitpoints.current < 45 ? 'safe_eat' : 'none', specIntent: 'none', extendedSupplyAction: false, equipmentIntent: 'style_loadout' }; }
function assertOwned() { for (const id of actorIds)
    for (const item of Object.values(state.actors[id].equipment))
        if (item && !allowedGear.has(item.itemId))
            throw new Error('Non-starter gear rejected: ' + item.itemId); }
function observed() { const capture = apply(0, context => decode([0, 0, 0, 0, 1], context), true); return encodeNhPolicyInput(capture.context, features); }
function reset(command: any) {
    seed = command.seed >>> 0;
    maxTicks = command.maxTicks;
    opponent = command.opponent;
    episode++;
    features = createNhPolicyFeatureState();
    damage = [0, 0];
    lastActions = ['Starting', 'Starting'];
    lastEvents = [];
    pendingActions = [];
    frames = [];
    inventory = [structuredClone(RUNTIME_NH_STAKE_INVENTORY_SLOTS), structuredClone(RUNTIME_NH_STAKE_INVENTORY_SLOTS)];
    state = createRuntimePlayerCombatState({ localTile: tile(3096, 3532), opponentTile: tile(3100, 3532), localLoadoutId: 'kodai-robes', opponentLoadoutId: 'kodai-robes', localSupplies: runtimeNhStakeSupplies(), opponentSupplies: runtimeNhStakeSupplies(), localVengeanceTrinketCharges: 0, opponentVengeanceTrinketCharges: 0, localRecoilRingsRemaining: 0, opponentRecoilRingsRemaining: 0, combatStartTick: 0, seed });
    state = syncRuntimePlayerCombatStateToInput(state, { tiles: {}, equipment: { 'local-player': starterEquipment, opponent: starterEquipment }, gearProfiles: { 'local-player': profile, opponent: profile }, levels: { 'local-player': runtimePlayerCombatDefaultLevels, opponent: runtimePlayerCombatDefaultLevels } });
    assertOwned();
    return snapshot(observed(), 0, false, false);
}
function step(command: any) {
    const vector = command.action;
    if (!Array.isArray(vector) || vector.length !== NVEC.length || vector.some((v: any, i: number) => !Number.isInteger(v) || v < 0 || v >= NVEC[i]))
        throw new Error('Invalid NH starter action vector');
    const tick = state.tick;
    const oldIds = new Set(state.events.map(e => e.id));
    const oldTiles = { 'local-player': state.actors['local-player'].tile, opponent: state.actors.opponent.tile };
    const chosen = apply(0, context => decode(vector, context));
    commitNhPolicyDecisionState(features, chosen.context, chosen.action);
    apply(1, scripted);
    let advance = advanceRuntimePlayerCombat(state, { tiles: { 'local-player': state.actors['local-player'].tile, opponent: state.actors.opponent.tile }, preMovementTiles: oldTiles, tileScale: .5 });
    state = advance.state;
    for (const request of advance.routeRequests) {
        const a = state.actors[request.actorId];
        if (!movementGate(a.locks, state.tick).blocked) {
            const destination = route(a.tile, request.targetTile, request.attackRange);
            if (destination)
                state = syncRuntimePlayerCombatStateToInput(state, { tiles: { [request.actorId]: destination } });
        }
    }
    state = applyRuntimePlayerCombatPreMovementHits(state, { tiles: {}, tileScale: .5 }).state;
    lastEvents = state.events.filter(e => !oldIds.has(e.id));
    for (const event of lastEvents)
        if (event.kind === 'hitsplat')
            damage[actorIds.indexOf(event.attackerId)] += event.damage;
    assertOwned();
    const hp = actorIds.map(id => state.actors[id].hitpoints), terminated = hp.some(h => h <= 0), truncated = !terminated && state.tick >= maxTicks;
    const reward = terminated ? (hp[0] <= 0 ? (hp[1] <= 0 ? 0 : -1) : 1) : 0;
    return snapshot(observed(), reward, terminated, truncated);
}
function snapshot(observation: number[], reward: number, terminated: boolean, truncated: boolean) {
    const fighters = actorIds.map((id, i) => { const a = state.actors[id], pos = collision.sceneToWorldTile(a.tile), equipment = Array(14).fill(-1); Object.entries(a.equipment).forEach(([slot, item]) => equipment[slots[slot]] = item.itemId); return { x: pos.x, y: pos.y, hp: a.hitpoints, maxHp: a.maxHitpoints, prayer: a.prayerPoints, food: a.supplies.manta_ray + a.supplies.anglerfish, freezeTicks: Math.max(0, a.locks.freezeUntilTick - state.tick + 1), cooldown: getAttackDelayStatus(a.attackTimer, state.tick).remainingTicks, weapon: equipment[3], weaponName: a.equipment.weapon?.name ?? 'None', equipment, style: a.policyOffenceStyle?.toUpperCase() ?? 'MAGIC', overhead: runtimePlayerCombatActiveOverheadPrayer(a)?.toUpperCase() ?? 'NONE', spec: a.gmaul.specialEnergy, damage: damage[i], lastIntent: lastActions[i], inventory: inventory[i] }; });
    const attacks = lastEvents.filter(e => e.kind === 'attack').map(e => { const from = collision.sceneToWorldTile(e.attackerTile), to = collision.sceneToWorldTile(e.defenderTile); return { side: actorIds.indexOf(e.attackerId), tick: e.tick, impactTick: e.tick + e.hitDelayTicks, style: e.style === 'magic' ? 'MAGIC' : e.style === 'ranged' ? 'RANGED' : 'MELEE', special: !!e.specialAttack, specKind: e.specialAttack === 'armadyl_godsword' ? 2 : -1, weapon: e.attackerEquipment.weapon?.itemId ?? -1, fromX: from.x, fromY: from.y, toX: to.x, toY: to.y, sequence: e.sequenceName }; });
    const hits = lastEvents.filter(e => e.kind === 'hitsplat').map(e => ({ side: actorIds.indexOf(e.targetActorId), damage: e.damage, sourceTick: e.tick }));
    const winner = terminated ? (fighters[0].hp <= 0 ? (fighters[1].hp <= 0 ? -1 : 1) : 0) : -1;
    return { observation, reward, terminated, truncated, info: { tick: state.tick, hp: fighters.map(f => f.hp), winner, logical_tick_ms: 600, opponent, frame: { episode, tick: state.tick, fighters, attacks, hits, profile: 'nh-starter', arena: { minX: arena.bounds.west, maxX: arena.bounds.east, minY: arena.bounds.south, maxY: arena.bounds.north } } } };
}
const rl = createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on('line', line => { try {
    const command = JSON.parse(line);
    if (command.command === 'describe') {
        console.log(JSON.stringify({ contract: CONTRACT, input_size: 114, nvec: NVEC, actions: ACTIONS, equipment: RUNTIME_NH_STAKE_EQUIPMENT_ENTRIES, inventory: RUNTIME_NH_STAKE_INVENTORY_SLOTS, allowed_gear: [...allowedGear].sort((a, b) => a - b) }));
        return;
    }
    console.log(JSON.stringify(command.command === 'reset' ? reset(command) : command.command === 'step' ? step(command) : (() => { throw new Error('Unknown command'); })()));
}
catch (error) {
    console.log(JSON.stringify({ error: error instanceof Error ? error.message : String(error) }));
} });
