# Local learning-agent integration

Installed locally: Python 3.12, CPU PyTorch, Stable-Baselines3 PPO and SB3-Contrib Recurrent PPO. Exact installed versions are in `installed.json` and `requirements.lock.txt`. The environment lives in `.venv`; no global Python or PATH change was made.

These are training algorithms, not pretrained Orion fighters. Both example policies in `models/` were trained for 4096 timesteps in the included **toy style-selection simulator**. They do not simulate actual Orion damage, eating, special attacks, freezes, movement or network timing. Their toy legal-action rate is not PvP performance.

## Activate in the application

1. Reopen `OrionNH.exe` and select **Learning agent**.
2. Choose **Load trusted local policy**, then `models/ppo-demo.zip` or `models/recurrent-ppo-demo.zip`. Wait for the loaded status.
3. Select **Activate recommendations**. With a locked opponent, the app sends normalized state to the local Python worker once per observed tick or prayer change. No names, screenshots or actor objects are sent. Requests/responses remain in memory and do not leave the computer.
4. The latest recommendation appears in the Agent tab and Observe/run status. **Test one offline policy sample** lets you check inference without a live opponent; this is a synthetic fixed example, not a combat test.
5. Choose **Deactivate** to stop policy recommendations. Closing the application terminates its inference and training workers. F9 disarms combat execution; it does not cancel an independently requested training job. Cancel training has a separate button.

The demo checkpoints are observation-only. **Use Orion-trained policy for decisions** requires an independently trained compatible checkpoint with metadata declaring `environment: orion-training-v1`, `live_control: true`, and `validated_orion: true`. Those declarations are user-provided attestations, not automatic proof of validation. The application does not supply such a checkpoint or an actual Orion training environment yet. Activating control never arms combat by itself; the existing Arm action is still required.

In policy-control mode the learned policy can choose only WAIT, MAGIC, RANGED or MELEE. The existing controller rejects protected styles, unavailable gear/spells, invalid targets, stale predictions and expired deadlines. It handles execution and cooldown checks. A stopped worker disarms policy-controlled combat rather than silently substituting the rule engine. There is no learned eating, movement, defensive-prayer prediction or special-attack policy in this integration.

## Prompt / information window

Click **Open prompt / information window**. Paste verified facts using these formats:

```text
weapon 1234 = RANGED
enemy animation 5678 = MAGIC
attack animation 810 = 5
freeze graphic 369 = 32
counter RANGED = MAGIC
magic gear = 1001,1002,1003
magic speed = 5
magic prayer = AUGURY
note: My preferred counter to ranged protection is magic.
```

All numbers above are format examples, not verified Orion data. Preview shows recognized entries and errors. Applying is blocked when unrecognized lines remain unless you explicitly choose to apply only recognized lines. No pasted shell commands, scripts or arbitrary code are executed.

PPO is not a language model: this parser imports supported facts into the app's rule configuration. Freeform `note:` text is kept in memory as a note; neither PPO nor Recurrent PPO interprets it. Applying information disarms the controller and resets recurrent context. It does **not** retrain the model, change learned weights or automatically generate training demonstrations. Save preset explicitly persists imported rules; freeform notes are not included.

## Train a demo

Select PPO or RecurrentPPO, choose a step count, then **Train toy demo**. Choose an output `.zip`; an adjacent `.json` describes its exact schema and toy training provenance. This invokes `agent_worker.py --train-demo` on the CPU and writes only the explicitly chosen model outputs. Cancel training terminates the training child process. An existing loaded model remains unchanged until you choose to load another checkpoint.

To teach actual PvP, the next task is an accurate simulator or an authorized local training server, synchronized action/state demonstrations, meaningful rewards and held-out evaluation. Weapon information alone does not provide those.

## Protocol and privacy

`LearningAgent.java` communicates over process stdin/stdout with `agent_worker.py`. Inference is asynchronous; the game thread does not wait on Python. Predictions are tagged with target-lock generation, prayer state, tick and sample age; stale results are rejected. Recurrent state resets on a new target, stop, configuration change or policy activation. Captures and live gameplay datasets are not recorded by this feature.

SB3 checkpoints can contain pickle data capable of executing code. Load your own trusted local policies, not arbitrary downloaded checkpoints. Loading uses adjacent `.json` schema metadata and checks actual observation/action dimensions. No checkpoints are fetched from external model hubs.

`install.ps1` can recreate the project-local environment from official uv/PyTorch/PyPI distributions. Installation downloads software; it does not upload gameplay. CPU-only packages avoid GPU-specific configuration. `verify_install.py` checks actual imports/tensor execution. Java self-tests also load both demo checkpoints and exercise real subprocess inference, prompt import, state guards and shutdown.
