/** Local NH starter environment using upstream browser combat, not DMM FastSim. */
import { createInterface } from 'node:readline';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createRuntimePlayerCombatState, advanceRuntimePlayerCombat, applyRuntimePlayerCombatPreMovementHits, syncRuntimePlayerCombatStateToInput, runtimePlayerCombatDefaultLevels, runtimePlayerCombatActiveOverheadPrayer, runtimePlayerCombatSpellDefinitions, setRuntimePlayerCombatLoadout, setRuntimePlayerCombatPrayers, setRuntimePlayerCombatAutocast, setRuntimePlayerCombatAttackSet, toggleRuntimePlayerCombatSpecial, consumeRuntimePlayerCombatSupply, requestRuntimePlayerCombatAttack, runtimePlayerCombatDamageEstimate, type RuntimePlayerCombatState } from '../src/sim/runtimePlayerCombat';
import { createNhDuelControllerContext, type NhDuelActorState } from '../src/sim/nh/duel';
import { nhGearProfileCandidateEquipmentByStyle } from '../src/sim/nh/gearProfile';
import { consumableDefinitions, canConsume } from '../src/sim/items/consumables';
import equipmentRows from '../src/generated/equipment-bonuses.json';
import { aggregateVisibleEquipmentBonuses } from '../src/sim/equipment/equipment';
import { prayerDefinitions, type PrayerId } from '../src/sim/prayer/prayers';
import { nhWeaponProfiles } from '../src/sim/combat/player-combat';
import { createNhPolicyFeatureState, encodeNhPolicyInput, commitNhPolicyDecisionState } from '../src/sim/nh/policy-features';
import { nhOffenceStyles, nhDefencePrayers, nhMovementIntents, nhMovementDeltas, type NhPolicyAction } from '../src/sim/nh/policy-bridge';
import { inferNhSelectedGearProfile, nhGearProfileWeaponIdForEquipment } from '../src/sim/nh/gearProfile';
import { getAttackDelayStatus } from '../src/sim/combat/timers';
import { movementGate } from '../src/sim/entity/locks';
import { buildNhSceneCollision } from '../src/render/nhSceneCollision';
import { RUNTIME_NH_STAKE_INVENTORY_SLOTS, RUNTIME_NH_STAKE_EQUIPMENT_ENTRIES, runtimeNhStakeSupplies, runtimeInventorySlotsAfterConsumedSupplies } from '../src/ui/runtimeSetupPresets';
import type { RuntimeInventorySlot, RuntimeActorId, RuntimeTile } from '../src/render/runtimeScene';
import type { VisibleEquipment, EquipmentSlot } from '../src/sim/clientView';
export const CONTRACT = 'nh-browser-starter-sb3-v2';
export const NVEC = [8, 6, 26, 6, 2];
export const ACTIONS = { combat: ['WAIT', 'MAGIC_ATTACK', 'RANGED_ATTACK', 'MELEE_ATTACK', 'AGS_SPECIAL', 'MAGIC_EQUIP', 'RANGED_EQUIP', 'MELEE_EQUIP'], prayer: [...nhDefencePrayers, 'none'], movement: nhMovementIntents, supply: ['none', 'safe_eat', 'double_eat', 'brew_only', 'restore_reboost', 'panic_full'], equipment: ['style_loadout', 'weapon_only'] };
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
let lastMovement=[[0,0],[0,0]],lastReward=[0,0],rewardTotal=[0,0];
let damage = [0, 0], lastActions = ['Starting', 'Starting'], lastEvents: any[] = [], pendingActions: any[] = [], frames: any[] = [];
type Action = Omit<NhPolicyAction,'defencePrayer'> & {defencePrayer:PrayerId|null,keepEquipment?:boolean};
let validTarget=true, operationAudit:any[]=[],episodeDone=false,prayerFraction=[0,0];
const actorIndex=(id:RuntimeActorId)=>actorIds.indexOf(id);
function record(side:number,operation:string,status:string,reason:string,details:any={}) {operationAudit.push({side,operation,status,reason,tick:state.tick,...details});}
function tile(x:number,y:number):RuntimeTile{return collision.worldToSceneTile({x,y,plane:0});}
function distance(a:RuntimeTile,b:RuntimeTile){return Math.max(Math.abs(a.x-b.x),Math.abs(a.z-b.z))/.5;}
function route(from:RuntimeTile,target:RuntimeTile,reach:number,side=0){
 if(reach>0&&distance(from,target)===0){
  const offsets=side===0?[[-.5,0],[0,-.5],[.5,0],[0,.5]]:[[.5,0],[0,.5],[-.5,0],[0,-.5]];
  for(const [dx,dz] of offsets){const candidate={x:from.x+dx,z:from.z+dz};if(collision.canStep(from,candidate))return candidate;}
  return null;
 }if(distance(from,target)<=reach)return null;const dx=Math.sign(target.x-from.x)*.5,dz=Math.sign(target.z-from.z)*.5;for(const candidate of [{x:from.x+dx,z:from.z+dz},{x:from.x+dx,z:from.z},{x:from.x,z:from.z+dz}])if(collision.canStep(from,candidate))return candidate;return null;}
function duelActor(side:number):NhDuelActorState {
 const id=actorIds[side],a=state.actors[id],p=collision.sceneToWorldTile(a.tile);
 const weaponId=nhGearProfileWeaponIdForEquipment(a.equipment)??'staff_of_the_dead';
 const stats:any={};for(const key of ['attack','strength','defence','ranged','magic'])stats[key]={current:(a.levels as any)[key],fixed:(a.fixedLevels as any)[key]};
 stats.hitpoints={current:a.hitpoints,fixed:a.maxHitpoints};stats.prayer={current:a.prayerPoints,fixed:a.maxPrayerPoints};
 const hits=lastEvents.filter(e=>e.kind==='hitsplat'),supplies=lastEvents.filter(e=>e.kind==='supply'&&e.actorId===id);
 const mv=lastMovement[side];
 return {id:side===0?'self':'opponent',label:side===0?'Local agent':'Local opponent',tile:{x:p.x,y:p.y,plane:0},stats,activePrayers:a.activePrayers,weaponId,previousWeaponId:weaponId,loadoutId:a.loadoutId,equipment:a.equipment,gearProfile:profile,candidateEquipmentByStyle:nhGearProfileCandidateEquipmentByStyle(a.equipment,profile),inventorySlots:inventory[side],strippedEquipmentSlots:[],attackTimer:a.attackTimer,locks:a.locks,supplies:a.supplies,supplyDelays:a.supplyDelays,gmaul:a.gmaul,specialActive:a.specialActive,movedThisTick:mv[0]!==0||mv[1]!==0,lastMoveDx:mv[0],lastMoveDy:mv[1],ateFoodLastTick:supplies.some(e=>['food','karambwan'].includes(consumableDefinitions[e.item as keyof typeof consumableDefinitions].kind)),drankPotionLastTick:supplies.some(e=>!['food','karambwan'].includes(consumableDefinitions[e.item as keyof typeof consumableDefinitions].kind)),lastDealtHit:hits.filter(e=>e.attackerId===id).reduce((v,e)=>v+e.damage,0),lastTakenHit:hits.filter(e=>e.targetActorId===id).reduce((v,e)=>v+e.damage,0),rewardDelta:lastReward[side],rewardTotal:rewardTotal[side],rewardDps:state.tick?damage[side]/state.tick:0,observedInfoKnown:true,gmaulSpecsUsed:a.gmaulSpecsUsed,voidwakerSpecsUsed:a.voidwakerSpecsUsed,vlsSpecsUsed:a.vlsSpecsUsed,lastSpecKind:a.lastSpecKind,previousSpecKind:a.previousSpecKind,lastSpecTick:a.lastSpecTick,lastOffenceStyle:a.policyOffenceStyle??(nhWeaponProfiles[weaponId].style==='magic'?'magic':nhWeaponProfiles[weaponId].style==='ranged'?'ranged':'melee'),lastVisibleOpponentStyle:state.actors[actorIds[1-side]].policyOffenceStyle??null};
}
function contextFor(side:number){return createNhDuelControllerContext(state.tick,duelActor(side),duelActor(1-side),{rewardEpisodeId:episode,rewardEpisodeActive:true});}
function apply(side:number,choose:(context:any)=>Action,probe=false){const context=contextFor(side),action=choose(context);if(!probe){control(side,action);lastActions[side]=(action.keepEquipment?'wait':action.offenceStyle)+' / '+action.supplyIntent+' / '+action.specIntent;assertOwned();}return {context,action};}
function attackReason(side:number,style:string,special:boolean){const a=state.actors[actorIds[side]],other=state.actors[actorIds[1-side]];if(!validTarget||other.hitpoints<=0)return 'invalid_target';if(a.hitpoints<=0)return 'actor_dead';if(getAttackDelayStatus(a.attackTimer,state.tick).delayed)return 'attack_cooldown';if(style==='magic'&&a.levels.magic<runtimePlayerCombatSpellDefinitions['ice-barrage'].requiredMagicLevel)return 'spell_level';if(special&&a.gmaul.specialEnergy<50)return 'special_energy';const reach=special?1:style==='magic'?runtimePlayerCombatSpellDefinitions['ice-barrage'].attackRange:style==='ranged'?nhWeaponProfiles.dragon_crossbow.attackRange:1;if(distance(a.tile,other.tile)>reach)return 'out_of_range';return null;}
function legalMask(side=0){const a=state.actors[actorIds[side]],alive=a.hitpoints>0,target=validTarget&&state.actors[actorIds[1-side]].hitpoints>0;const combat=Array(8).fill(alive);combat[0]=true;for(let c=1;c<=4;c++)combat[c]=alive&&target&&!attackReason(side,c===4?'melee':nhOffenceStyles[c-1],c===4);if(a.equipment.shield&&inventory[side].every(s=>s!==null))combat[4]=false;const prayer=prayerDefinitions;const prayers=nhDefencePrayers.map(id=>alive&&a.prayerPoints>0&&a.maxPrayerPoints>=prayer[id].level);prayers.push(true);const movement=nhMovementIntents.map((m,i)=>i===0||alive&&!movementGate(a.locks,state.tick).blocked);const food=a.supplies.manta_ray+a.supplies.anglerfish>0,potion=state.tick>=a.supplyDelays.potionDelayUntilTick,eatReady=state.tick>=a.supplyDelays.eatDelayUntilTick;const supply=[true,alive&&food&&eatReady,alive&&((food&&eatReady)||(a.supplies.saradomin_brew>0&&potion)),alive&&a.supplies.saradomin_brew>0&&potion,alive&&(a.supplies.sanfew_serum+a.supplies.super_combat+a.supplies.bastion>0)&&potion,alive&&((food&&eatReady)||(a.supplies.saradomin_brew>0&&potion))];return {channels:[combat,prayers,movement,supply,[true,true]],scope:'factorized_intrinsic; execution rechecks joint action after movement',tick:state.tick,target_generation:episode};}
function drainPrayer(){for(let side=0;side<2;side++){const id=actorIds[side],a=state.actors[id];if(a.hitpoints<=0||a.prayerPoints<=0)continue;const rate=a.activePrayers.reduce((n,p)=>n+prayerDefinitions[p].drain,0);const bonus=aggregateVisibleEquipmentBonuses(a.equipment,equipmentRows as any).prayer_bonus;prayerFraction[side]+=rate/(60+2*bonus);const debit=Math.floor(prayerFraction[side]+1e-12);prayerFraction[side]-=debit;if(debit){const points=Math.max(0,a.prayerPoints-debit);state=syncRuntimePlayerCombatStateToInput(state,{tiles:{},prayerPoints:{[id]:{current:points}}});record(side,'prayer_drain','confirmed','osrs_average_model',{points:debit,rate,bonus});if(points===0){state=setRuntimePlayerCombatPrayers(state,id,[]);state={...state,actors:{...state.actors,[id]:{...state.actors[id],policyDefencePrayerPrevious:null,policyDefencePrayerCurrent:null,policyDefencePrayerSwitchTick:-1}}};}}}}
function structured(){return {tick:state.tick,logical_tick_ms:600,target:{id:'opponent',generation:episode,valid:validTarget&&state.actors.opponent.hitpoints>0},visibility:'full local simulator state; not an Orion observation contract',fighters:actorIds.map((id,i)=>{const a=state.actors[id],p=collision.sceneToWorldTile(a.tile);return {id,x:p.x,y:p.y,hp:a.hitpoints,prayer:a.prayerPoints,levels:a.levels,equipment:Object.fromEntries(Object.entries(a.equipment).map(([k,v])=>[k,v.itemId])),inventory:inventory[i],supplies:a.supplies,active_prayers:a.activePrayers,cooldown:getAttackDelayStatus(a.attackTimer,state.tick).remainingTicks,special_energy:a.gmaul.specialEnergy,special_active:a.specialActive,freeze_until_tick:a.locks.freezeUntilTick,target_id:a.targetId,attack_timer:a.attackTimer,movement:lastMovement[i]};})};}

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
    state = setRuntimePlayerCombatLoadout(state, id, special ? 'ags-bandos' : style === 'magic' ? 'kodai-robes' : style === 'ranged' ? 'acb-hides' : 'tentacle-bandos', next);
    state = syncRuntimePlayerCombatStateToInput(state, { tiles: {}, equipment: { [id]: next }, gearProfiles: { [id]: profile } });
    return true;
}
function eat(side: number, supply: any) { const id = actorIds[side], before = state; const result = consumeRuntimePlayerCombatSupply(state, id, supply); state = result.state; if (result.consumed)
    inventory[side] = runtimeInventorySlotsAfterConsumedSupplies(inventory[side], [supply]); return result.consumed; }
function control(side:number,action:Action){const id=actorIds[side],other=actorIds[1-side];if(state.actors[id].hitpoints<=0){record(side,'action','rejected','actor_dead');return;}
 const beforeGear=JSON.stringify(state.actors[id].equipment),special=action.specIntent==='spec_armadyl_godsword';const equipped=action.keepEquipment?true:equip(side,equipmentFor(action.offenceStyle,special),special,action.offenceStyle,action.equipmentIntent==='weapon_only');
 record(side,'gear',equipped?(beforeGear===JSON.stringify(state.actors[id].equipment)?'noop':'accepted'):'rejected',equipped?'equipment_confirmed':'inventory_or_ownership_block');
 const a=state.actors[id],prayers=action.defencePrayer&&a.prayerPoints>0?[action.defencePrayer,action.offenceStyle==='magic'?'augury':action.offenceStyle==='ranged'?'rigour':'piety']:[];
 state=setRuntimePlayerCombatPrayers(state,id,prayers as PrayerId[]);record(side,'prayer',action.defencePrayer&&a.prayerPoints<=0?'rejected':'accepted',action.defencePrayer&&a.prayerPoints<=0?'prayer_depleted':'state_confirmed');
 const use=(item:any)=>{const old=state.actors[id],result=consumeRuntimePlayerCombatSupply(state,id,item);state=result.state;if(result.consumed)inventory[side]=runtimeInventorySlotsAfterConsumedSupplies(inventory[side],[item]);record(side,'supply',result.consumed?'accepted':'rejected',result.consumed?'consumed':result.reason??'unconfirmed',{item,healed:result.healed,wasted:result.consumed&&result.healed===0&&['manta_ray','anglerfish','saradomin_brew'].includes(item)});};
 if(['safe_eat','double_eat','panic_full'].includes(action.supplyIntent))use(state.actors[id].supplies.manta_ray>0?'manta_ray':'anglerfish');
 if(['brew_only','double_eat','panic_full'].includes(action.supplyIntent))use('saradomin_brew');
 if(action.supplyIntent==='restore_reboost'){const a=state.actors[id];use(a.prayerPoints<50||Object.keys(a.levels).some(k=>(a.levels as any)[k]<(a.fixedLevels as any)[k])?'sanfew_serum':a.supplies.super_combat>0?'super_combat':a.supplies.bastion>0?'bastion':'sanfew_serum');}
 const delta=nhMovementDeltas[action.movementIntent],old=state.actors[id].tile;
 if(action.movementIntent!=='none'){if(movementGate(state.actors[id].locks,state.tick).blocked)record(side,'movement','rejected','movement_lock');else{let destination:RuntimeTile|null=null;if(action.movementIntent==='stand_under'||action.movementIntent==='pressure')destination=route(old,state.actors[other].tile,action.movementIntent==='stand_under'?0:1);else if(delta){let current=old;for(let n=0;n<Math.max(Math.abs(delta[0]),Math.abs(delta[1]));n++){const candidate={x:current.x+(n<Math.abs(delta[0])?Math.sign(delta[0])*.5:0),z:current.z+(n<Math.abs(delta[1])?Math.sign(delta[1])*.5:0)};if(!collision.canStep(current,candidate))break;current=candidate;}destination=current;}if(destination)state=syncRuntimePlayerCombatStateToInput(state,{tiles:{[id]:destination}});record(side,'movement',destination&&distance(old,destination)>0?'accepted':'rejected',destination&&distance(old,destination)>0?'tile_confirmed':'collision_or_no_step');}}
 if(action.attackIntent==='attack'&&equipped&&validTarget&&state.actors[other].hitpoints>0&&
    distance(state.actors[id].tile,state.actors[other].tile)===0&&distance(old,state.actors[id].tile)===0&&
    !movementGate(state.actors[id].locks,state.tick).blocked){
  const destination=route(state.actors[id].tile,state.actors[other].tile,1,side);
  if(destination){state=syncRuntimePlayerCombatStateToInput(state,{tiles:{[id]:destination}});
   record(side,'auto_target_route','accepted','attack_request_step_out');}
 }
 const reason=!equipped?'equipment_block':attackReason(side,action.offenceStyle,special);
 if(equipped&&!action.keepEquipment){state=setRuntimePlayerCombatAutocast(state,id,action.offenceStyle==='magic'&&!special&&state.actors[id].levels.magic>=runtimePlayerCombatSpellDefinitions['ice-barrage'].requiredMagicLevel?'ice-barrage':null);if(action.offenceStyle==='ranged')state=setRuntimePlayerCombatAttackSet(state,id,1);state={...state,actors:{...state.actors,[id]:{...state.actors[id],policyOffenceStyle:action.offenceStyle}}};}
 if(special&&equipped){if(state.actors[id].gmaul.specialEnergy<50)record(side,'special','rejected','special_energy');else if(state.actors[id].specialActive)record(side,'special','noop','already_enabled');else{const result=toggleRuntimePlayerCombatSpecial(state,id);state=result.state;record(side,'special',state.actors[id].specialActive?'accepted':'rejected',state.actors[id].specialActive?'enabled':'native_toggle_rejected');}}
 if(action.attackIntent==='hold'||reason){state={...state,actors:{...state.actors,[id]:{...state.actors[id],ordinaryAttackSuppressedTick:state.tick}}};record(side,'attack_intent',action.attackIntent==='hold'?'noop':'rejected',action.attackIntent==='hold'?'hold':reason!);}
 else{state=requestRuntimePlayerCombatAttack(state,id,other);record(side,'attack_intent','accepted','target_request_confirmed; execution pending');}
}
function decode(vector:number[],context:any):Action {const code=vector[0];const style=code>=5?nhOffenceStyles[code-5]:code>=1&&code<=3?nhOffenceStyles[code-1]:context.self.lastOffenceStyle??'magic';return {offenceStyle:code===4?'melee':style,defencePrayer:vector[1]===5?null:nhDefencePrayers[vector[1]],movementIntent:nhMovementIntents[vector[2]],supplyIntent:ACTIONS.supply[vector[3]] as any,specIntent:code===4?'spec_armadyl_godsword':'none',attackIntent:code===0||code>=5?'hold':'attack',equipmentIntent:vector[4]===0?'style_loadout':'weapon_only',extendedSupplyAction:false,keepEquipment:code===0};}

function scripted(context: any): Action { let style = opponent === 'fixed-magic' ? 'magic' : opponent === 'fixed-melee' ? 'melee' : 'ranged'; if (opponent === 'seeded-rapid-switch')
    style = nhOffenceStyles[((state.tick * 1103515245 + seed) >>> 0) % 3]; return { offenceStyle: style as any, defencePrayer: opponent==='seeded-random-prayer'?nhDefencePrayers[((state.tick*1664525+seed)>>>0)%3]:context.opponent.lastOffenceStyle === 'magic' ? 'protect_from_magic' : context.opponent.lastOffenceStyle === 'ranged' ? 'protect_from_missiles' : 'protect_from_melee', movementIntent: style === 'melee' ? 'pressure' : 'none', supplyIntent: context.self.stats.hitpoints.current < 45 ? 'safe_eat' : 'none', specIntent: 'none', extendedSupplyAction: false, equipmentIntent: 'style_loadout' }; }
function assertOwned() { for (const id of actorIds)
    for (const item of Object.values(state.actors[id].equipment))
        if (item && !allowedGear.has(item.itemId))
            throw new Error('Non-starter gear rejected: ' + item.itemId); }
function observed() { const capture = apply(0, context => decode([0, 0, 0, 0, 1], context), true); const observation=encodeNhPolicyInput(capture.context, features);if(!validTarget)observation[8]=0;return observation; }
function reset(command: any) {
    if(!Number.isInteger(command.seed)||command.seed<0||command.seed>4294967295)throw new Error('invalid_seed');
    if(!Number.isInteger(command.maxTicks)||command.maxTicks<32||command.maxTicks>1200)throw new Error('invalid_tick_limit');
    if(!['fixed-ranged','fixed-magic','fixed-melee','seeded-rapid-switch','stationary-ranged','seeded-random-prayer','mirror'].includes(command.opponent))throw new Error('invalid_opponent');
    const limits:any={hp:99,prayer:99,magic:99,special:100,cooldown:8,freeze:64};
    const opt=command.options??{};if(!opt||typeof opt!=='object'||Array.isArray(opt))throw new Error('invalid_options');
    for(const key of Object.keys(opt)){if(key==='target_valid'){if(typeof opt[key]!=='boolean')throw new Error('invalid_target_option');continue;}if(!(key in limits)||!Array.isArray(opt[key])||opt[key].length!==2||opt[key].some((v:any)=>!Number.isInteger(v)||v<0||v>limits[key]))throw new Error('invalid_starting_option:'+key);}

    seed = command.seed >>> 0;
    maxTicks = command.maxTicks;
    opponent = command.opponent;
    episode++;
    features = createNhPolicyFeatureState();
    damage = [0, 0];
    lastActions = ['Starting', 'Starting'];
    lastEvents = [];
    operationAudit=[];lastMovement=[[0,0],[0,0]];lastReward=[0,0];rewardTotal=[0,0];validTarget=true;episodeDone=false;prayerFraction=[0,0];
    inventory = [structuredClone(RUNTIME_NH_STAKE_INVENTORY_SLOTS), structuredClone(RUNTIME_NH_STAKE_INVENTORY_SLOTS)];
    state = createRuntimePlayerCombatState({ localTile: tile(3096, 3532), opponentTile: tile(3100, 3532), localLoadoutId: 'kodai-robes', opponentLoadoutId: 'kodai-robes', localSupplies: runtimeNhStakeSupplies(), opponentSupplies: runtimeNhStakeSupplies(), localVengeanceTrinketCharges: 0, opponentVengeanceTrinketCharges: 0, localRecoilRingsRemaining: 0, opponentRecoilRingsRemaining: 0, combatStartTick: 0, seed });
    state = syncRuntimePlayerCombatStateToInput(state, { tiles: {}, equipment: { 'local-player': starterEquipment, opponent: starterEquipment }, gearProfiles: { 'local-player': profile, opponent: profile }, levels: { 'local-player': runtimePlayerCombatDefaultLevels, opponent: runtimePlayerCombatDefaultLevels } });
    const options=command.options??{};validTarget=options.target_valid!==false;for(let i=0;i<2;i++){const id=actorIds[i],actor=state.actors[id];const pick=(name:string,maximum:number,current:number)=>{if(options[name]===undefined)return current;const v=options[name][i];if(!Number.isInteger(v)||v<0||v>maximum)throw new Error("Invalid starting "+name);return v;};const hp=pick("hp",99,actor.hitpoints),prayer=pick("prayer",99,actor.prayerPoints),magic=pick("magic",99,actor.levels.magic),energy=pick("special",100,actor.gmaul.specialEnergy),cooldown=pick("cooldown",8,0),frozen=pick("freeze",64,0);state={...state,actors:{...state.actors,[id]:{...actor,hitpoints:hp,prayerPoints:prayer,levels:{...actor.levels,magic},gmaul:{...actor.gmaul,specialEnergy:energy},attackTimer:{...actor.attackTimer,lastAttackTick:0,weaponCooldownTicks:cooldown},locks:{...actor.locks,freezeUntilTick:frozen-1}}}};}
    assertOwned();
    return snapshot(observed(), 0, false, false);
}
function step(command: any) {
    if(episodeDone)throw new Error('episode_finished');
    const vector = command.action;
    if (!Array.isArray(vector) || vector.length !== NVEC.length || vector.some((v: any, i: number) => !Number.isInteger(v) || v < 0 || v >= NVEC[i]))
        throw new Error('Invalid NH starter action vector');
    if(command.expectedTick!==undefined&&command.expectedTick!==state.tick)throw new Error('stale_tick');if(command.episode!==undefined&&command.episode!==episode)throw new Error('stale_target_generation');
    if(command.opponentAction!==undefined&&(!Array.isArray(command.opponentAction)||command.opponentAction.length!==NVEC.length||command.opponentAction.some((v:any,i:number)=>!Number.isInteger(v)||v<0||v>=NVEC[i])))throw new Error('invalid_opponent_action');
    if(opponent==='mirror'&&command.opponentAction===undefined)throw new Error('mirror_requires_simultaneous_actions');
    operationAudit=[];const maskBefore=legalMask();const beforeStructured=structured();
    const tick = state.tick;
    const oldIds = new Set(state.events.map(e => e.id));
    const oldTiles = { 'local-player': state.actors['local-player'].tile, opponent: state.actors.opponent.tile };
    const chosen={context:contextFor(0),action:null as Action|null};chosen.action=decode(vector,chosen.context);const otherAction=command.opponentAction?decode(command.opponentAction,contextFor(1)):scripted(contextFor(1));
    control(0,chosen.action);control(1,otherAction);lastActions=[chosen.action.keepEquipment?'wait':chosen.action.offenceStyle,otherAction.offenceStyle];
    commitNhPolicyDecisionState(features, chosen.context, chosen.action as NhPolicyAction);
    let advance = advanceRuntimePlayerCombat(state, { tiles: { 'local-player': state.actors['local-player'].tile, opponent: state.actors.opponent.tile }, preMovementTiles: oldTiles, tileScale: .5 });
    const explicitMoved=actorIds.map(id=>distance(oldTiles[id],state.actors[id].tile)>0);
    state = advance.state;
    for (const request of advance.routeRequests) {
        const a = state.actors[request.actorId];
        if (!explicitMoved[actorIndex(request.actorId)] && a.hitpoints>0 && !movementGate(a.locks, tick).blocked) {
            const destination = route(a.tile, request.targetTile, request.attackRange,actorIndex(request.actorId));
            if (destination)
                state = syncRuntimePlayerCombatStateToInput(state, { tiles: { [request.actorId]: destination } });
        }
    }
    state = applyRuntimePlayerCombatPreMovementHits(state, { tiles: {}, tileScale: .5 }).state;
    drainPrayer();
    lastEvents = state.events.filter(e => !oldIds.has(e.id));
    for (const event of lastEvents)
        if (event.kind === 'hitsplat')
            damage[actorIds.indexOf(event.attackerId)] += event.damage;
    assertOwned();
    const hp = actorIds.map(id => state.actors[id].hitpoints), terminated = hp.some(h => h <= 0), truncated = !terminated && state.tick >= maxTicks;
    lastMovement=actorIds.map(id=>[(state.actors[id].tile.x-oldTiles[id].x)/.5,(state.actors[id].tile.z-oldTiles[id].z)/.5]);
    const reward = terminated ? (hp[0] <= 0 ? (hp[1] <= 0 ? 0 : -1) : 1) : 0;
    episodeDone=terminated||truncated;
    lastReward=[reward,-reward];rewardTotal=rewardTotal.map((v,i)=>v+lastReward[i]);
    const result=snapshot(observed(), reward, terminated, truncated);return {...result,info:{...result.info,transition:{requested:vector,decoded:chosen.action,legal_mask_before:maskBefore,before:beforeStructured,operations:operationAudit,confirmed_events:lastEvents,reward_components:{terminal:reward},tick_before:tick,tick_after:state.tick,target_generation:episode}}};
}
function snapshot(observation: number[], reward: number, terminated: boolean, truncated: boolean) {
    const fighters = actorIds.map((id, i) => { const a = state.actors[id], pos = collision.sceneToWorldTile(a.tile), equipment = Array(14).fill(-1); Object.entries(a.equipment).forEach(([slot, item]) => equipment[slots[slot]] = item.itemId); return { x: pos.x, y: pos.y, hp: a.hitpoints, maxHp: a.maxHitpoints, prayer: a.prayerPoints, food: a.supplies.manta_ray + a.supplies.anglerfish, freezeTicks: Math.max(0, a.locks.freezeUntilTick - state.tick + 1), cooldown: getAttackDelayStatus(a.attackTimer, state.tick).remainingTicks, weapon: equipment[3], weaponName: a.equipment.weapon?.name ?? 'None', equipment, style: a.policyOffenceStyle?.toUpperCase() ?? 'MAGIC', overhead: runtimePlayerCombatActiveOverheadPrayer(a)?.toUpperCase() ?? 'NONE', spec: a.gmaul.specialEnergy, damage: damage[i], lastIntent: lastActions[i], inventory: inventory[i] }; });
    const attacks = lastEvents.filter(e => e.kind === 'attack').map(e => { const from = collision.sceneToWorldTile(e.attackerTile), to = collision.sceneToWorldTile(e.defenderTile); return { side: actorIds.indexOf(e.attackerId), tick: e.tick, impactTick: e.tick + e.hitDelayTicks, style: e.style === 'magic' ? 'MAGIC' : e.style === 'ranged' ? 'RANGED' : 'MELEE', special: !!e.specialAttack, specKind: e.specialAttack === 'armadyl_godsword' ? 2 : -1, weapon: e.attackerEquipment.weapon?.itemId ?? -1, fromX: from.x, fromY: from.y, toX: to.x, toY: to.y, sequence: e.sequenceName }; });
    const hits = lastEvents.filter(e => e.kind === 'hitsplat').map(e => ({ side: actorIds.indexOf(e.targetActorId), damage: e.damage, sourceTick: e.tick }));
    const winner = terminated ? (fighters[0].hp <= 0 ? (fighters[1].hp <= 0 ? -1 : 1) : 0) : -1;
    return { observation, reward, terminated, truncated, info: { tick: state.tick, hp: fighters.map(f => f.hp), winner, logical_tick_ms: 600, opponent, structured:structured(),legal_action_mask:legalMask(), frame: { episode, tick: state.tick, fighters, attacks, hits, profile: 'nh-starter', arena: { minX: arena.bounds.west, maxX: arena.bounds.east, minY: arena.bounds.south, maxY: arena.bounds.north } } } };
}

function visibleEstimates(command:any) {
 const side=command.side??0;if(side!==0&&side!==1)throw new Error('invalid_controller_side');
 if(command.expectedTick!==state.tick||command.episode!==episode)throw new Error('stale_estimate_request');
 const self=state.actors[actorIds[side]],other=state.actors[actorIds[1-side]];
 const estimates:any={};
 for(const style of ['magic','ranged','melee']){
  const candidate={...self,equipment:equipmentFor(style),activePrayers:self.prayerPoints>0?[style==='magic'?'augury':style==='ranged'?'rigour':'piety']:[],attackSetIndex:style==='ranged'?1:0};
  estimates[style]=runtimePlayerCombatDamageEstimate(candidate as any,other,style==='melee'?'slash':style as any,30);
 }
 const visible=nhWeaponProfiles[nhGearProfileWeaponIdForEquipment(other.equipment)??"staff_of_the_dead"]?.style??'magic';
 const incoming=runtimePlayerCombatDamageEstimate(other,self,visible,30);
 const origin=self.tile, moves:any[]=[];
 for(const [name,delta] of Object.entries(nhMovementDeltas)){
  let current=origin,valid=!movementGate(self.locks,state.tick).blocked;
  for(let n=0;valid&&n<Math.max(Math.abs(delta[0]),Math.abs(delta[1]));n++){
   const next={...current,x:current.x+(n<Math.abs(delta[0])?Math.sign(delta[0])*.5:0),z:current.z+(n<Math.abs(delta[1])?Math.sign(delta[1])*.5:0)};
   if(!collision.canStep(current,next)){valid=false;break;}current=next;
  }
  const world=collision.sceneToWorldTile(current);
  moves.push({name,valid,x:world.x,y:world.y});
 }

 const view=structured(),fighters=side===0?view.fighters:[view.fighters[1],view.fighters[0]];
 const controllerInfo={controller_side:side,physical_actor_ids:[actorIds[side],actorIds[1-side]],structured:{...view,fighters:[{...fighters[0],id:'local-player'},{...fighters[1],id:'opponent'}]},legal_action_mask:legalMask(side)};
 const can=(item:any)=>self.supplies[item as keyof typeof self.supplies]>0&&canConsume({item,delays:self.supplyDelays,currentTick:state.tick,noFoodRule:false,noDrinksRule:false}).ok;
 const foodReady=can('manta_ray')||can('anglerfish'),brewReady=can('saradomin_brew'),restoreReady=can('sanfew_serum');
 return {controller_info:controllerInfo,supply_eligibility:{food:foodReady,brew:brewReady,restore:restoreReady},tick:state.tick,generation:episode,source:'pinned runtimePlayerCombatDamageEstimate; actual starter candidates; ordinary visible weapon only',
  estimates,movement_candidates:moves,incoming_max_hit:incoming.maxDamage,ags_expected_damage:null,
  limitations:['ordinary weapon threat excludes unseen switches and special multipliers','static EV does not forecast delayed prayer history or future rolls']};
}

const rl = createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on('line', line => { try {
    const command = JSON.parse(line);
    if(command.command==='visible_estimates'){console.log(JSON.stringify(visibleEstimates(command)));return;}
      if (command.command === 'describe') {
        console.log(JSON.stringify({ contract: CONTRACT, adapter_revision:2, passive_prayer_drain:'osrs_average_drain_v1; source rates/(60+2*equipment prayer bonus); NH/Orion tick parity unverified', observation_visibility:'full local state', input_size: 114, nvec: NVEC, actions: ACTIONS, equipment: RUNTIME_NH_STAKE_EQUIPMENT_ENTRIES, inventory: RUNTIME_NH_STAKE_INVENTORY_SLOTS, allowed_gear: [...allowedGear].sort((a, b) => a - b) }));
        return;
    }
    console.log(JSON.stringify(command.command === 'reset' ? reset(command) : command.command === 'step' ? step(command) : (() => { throw new Error('Unknown command'); })()));
}
catch (error) {
    console.log(JSON.stringify({ error: error instanceof Error ? error.message : String(error) }));
} });
