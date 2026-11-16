# Saving a setup and running another raid

In **Advanced settings â†’ Olm baseline**, apply any edits, then select **Save Olm
profile**. This saves the applied rules, current equipment presets, and current
learned model to `profiles/olm-profile.json`. **Load saved Olm profile** restores
that snapshot while the bot is stopped. Saving a setup does not require a raid
completion or manual loot confirmation. Check the status for save errors.

**Run bot** reuses the current in-memory setup and learning. On a fresh launch,
it restores the newer of the explicitly saved profile and latest attempt.
Attempts continue to write local checkpoints automatically. You can enter the
next raid yourself and press **Run bot** again; there is no separate learning
button needed for bot attempts. Learned updates are approximate outcome-based
adjustments, not a guarantee of better performance on each run.

With `finishRaidAutomatically` enabled (the default), observed head death starts
the post-fight sequence instead of immediately stopping:

1. Search the reward chest. Chest object `30028` and reward container `35323909`
   come from the user's second recorded demonstration, not guessed UI IDs.
2. Click each visible reward slot and observe that item's inventory quantity
   increase. If space is needed, drop one configured item whose live name also
   identifies it as Xeric's aid. Existing stackable rewards need no new slot.
3. Wait for the visible reward container to become empty and all requested
   rewards to be acknowledged, then close it.
4. Use nearby steps with an available Climb-up/Climb/Exit/Leave action and handle
   recognized exit confirmations. `raidExitObjectId` can restrict discovery to
   a particular object if needed.
5. Confirm departure from the raid and wait one sampled random delay of 1-5 seconds.
6. Right-click the nearby Bank chest and choose its actual Last-preset menu entry.
   Inventory item quantities and equipped item quantities must exactly match the
   snapshot captured when Run bot was pressed, on two distinct game ticks.
   Unconfirmed loads retry at most three times; no next entry is attempted while
   the setup differs. Start with the intended full preset before pressing Run bot.
7. Left-click the Chambers of Xeric entrance when Enter is its primary action.
   Confirm a transition into a configured Chambers region, then stop and save.
   This does not start another Olm fight or enable automatic looping.

Attempt outcome `COMPLETED` means head death; `LOOTED_EXITED` means loot and exit
were both observed. RESTOCKED_REENTERED means the starting loadout and return
entry were additionally verified. A click alone does not establish either. Stop/F9 remains
available throughout. If post-fight actions cannot be confirmed within 200 game
ticks (with a separate 150-tick limit for restock/return), the bot stops and saves the partial result rather than declaring success.
The reward/exit sequence has offline mock-client coverage; live Orion completion
has not been verified in this development session.

