# Cox restocking and connection diagnostics

Post-raid reset now always uses Use Bank chest, the visible Preset Manager
control, and right-click Cox > Load. Last-preset is no longer used. UI discovery
matches actual visible widget names/actions and the real Cox context-menu entry;
no preset widget IDs are inferred from screenshot coordinates. It never chooses
Overwrite, Delete, Rename or Set as default. An unavailable layout waits/stops
with a stage diagnostic rather than selecting a different preset.

After Load, inventory quantities and equipped gear must match the starting setup
on two separate game ticks. The fixed boots slot is excluded from equipment
comparison. Save Cox with your intended inventory before starting a loop. The
bank/preset interface is closed before the Chambers entry click.

The inspected latest attempt ended as DEATH. Its retained 80-tick tail contained
87 recorded input requests, with at most one recorded input per client cycle.
These records are not network packet counts and do not establish the cause of
the user's reported disconnect. The located client log predates that attempt.
The death can be an aftermath of the disconnect.

Redundant pool destination requests now wait for observed travel progress;
pool-hold cancellation is retried no faster than every two game ticks. Urgent
hazard reactions retain their normal priority. This removes needless inputs but
has not been shown to prevent a network disconnect.

New attempt checkpoints include a bounded connectionTransitions list: game
state, focus, tick freshness, client cycle/tick, relative elapsed time, and
cumulative UI action slots claimed. A slot reservation is not proof of a sent
packet or completed action. Game-state events request a checkpoint while the
attempt is active. No raw game/login messages, account names or credentials are
added. Offline tests cover exact Cox menu selection, destructive-action rejection,
retry pacing and transition deduplication. Live UI and disconnect behavior still
need verification.
