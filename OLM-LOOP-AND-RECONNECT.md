# Chambers loop and reconnect

The main Chambers window has Kills to complete (1-1000), Reconnect after 30
seconds, and a masked session password field. Enter the password once before
Run bot. The username is Turbo; a matching existing username is left alone.
The application does not embed or serialize the password in source, profiles,
attempts or logs. Stop/F9, completion and reload clear the retained session copy.

A counted cycle requires the existing confirmed boss/loot/exit/restock/re-entry
sequence. Only then does it schedule the next attempt. It waits for the prior
checkpoint save to finish before starting, retaining the in-memory learned model.
The final cycle also completes looting/reset/re-entry, then stops. Death, an
unrecoverable stall or setup failure stops the loop rather than counting a kill.

A disconnect pauses combat. The 30-second monotonic delay never sleeps on the
client thread. With game focus, the client opens its normal Existing user form,
uses the session credentials and normal Enter submission, then waits for a
confirmed login and fresh state before continuing. It does not bypass login,
authentication or account restrictions. Unrecognized login screens require
manual attention. Failed submissions are spaced by 30 seconds and capped at
three. The game's own automatic reconnect may complete before a submission.

If reconnect returns to the raid, transient input/attack observations are reset
before resuming. If it returns outside, the fighter attempts the usual visible
Bank chest > Preset Manager > Cox > Load and Chambers entrance sequence, then retries the interrupted
fight without counting a completed kill. Missing objects or failed setup checks
still stop with saved progress. Progress is included in attempt metadata, but a
new application session requires an explicit new Run bot command and password.

State and mock-runtime checks pass. Live Orion login layout, disconnect recovery
and multiple consecutive raids still require validation; offline checks cannot
establish successful unattended operation.
