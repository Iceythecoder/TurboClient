# Stage 1 audit tools

Run from the Orion-NH-Prototype directory. These tools use only the local synthetic simulator.

## Acceptance checks

```powershell
$env:PYTHONPATH=(Resolve-Path learning\nh-trainer).Path
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\test_stage1.py
```

## Explicit diagnostic recording and replay

Choose a new output folder; existing recordings are preserved.

```powershell
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\stage1.py run --output build\my-new-stage1-run
& learning\.venv\Scripts\python.exe learning\nh-trainer\audit\stage1.py replay --trace build\my-new-stage1-run\diagnostics.jsonl
```

For real model evaluation, explicitly opt into recording:

```powershell
& learning\.venv\Scripts\python.exe learning\nh-trainer\nh_worker.py evaluate --profile nh-starter --model build\stage1-checks\models\nh-starter-ppo.zip --fights 2 --fight-ticks 32 --trace-output build\my-new-evaluation.jsonl
```

Recording is disabled by default. JSONL stores only local simulator observations, masks, requested/decoded actions, actual operation results, confirmed native events, reward components, seeds, configuration and timing. It has no player/account names, screenshots, remote processing or browser storage. Test fixtures are synthetic; temporary test recordings are removed on success and failure. An interrupted opt-in recording is preserved as a partial diagnostic output, not silently labeled a complete episode. Hash chains detect accidental modification; they do not authenticate hostile files. Replay requires the matching simulator bundle and preset signature, and rejects mismatches. These recordings are not yet a train/validation/test imitation dataset.

The numeric observation is 114 floats from actual state. The structured state and factorized masks are available through `structured_observation()`, `legal_action_mask()` and flattened `action_masks()`. Joint actions are revalidated during execution after movement/resources change. PPO/RecurrentPPO do not yet consume these masks during sampling; that belongs to Stage 6. A rejected action is not a confirmed attack; see `transition.operations` versus `transition.confirmed_events`.

The v2 contract adds prayer OFF and uses [8,6,26,6,2]. Old NH checkpoints are not relabeled. Use `--profile nh-starter-v1` for explicit original-adapter comparisons; its known bugs remain frozen there. DMM stays separate. Default training/evaluation seeds are now 42/20042; all can be explicitly set.

Stage 2 preferences exist only as a proposed JSON file and are not loaded by the application. Do not implement/activate them until approval.
