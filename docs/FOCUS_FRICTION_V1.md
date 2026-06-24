# Focus Friction V1

V1 is an offline-only focus system. It is not DRM, anti-cheat, or secure device
lockdown. If a user clears app data or edits a backup, they can bypass it. That
is fine: this is local friction for a self-control tool.

V1 is opt-in. Until the user enables `Opt into Dystopia` under Personalization,
existing Pomodoro behavior stays unchanged and the credit, breach, and lockdown
commands stay inactive.

## Scope

- Retui Credits: local points used to end focus gates early.
- Breach Keys: rare local tokens used to bypass a focus gate.
- Pomodoro escape: spend credits to terminate early.
- Lockdown: timer-driven full-screen overlay that takes over the launcher UI
  while active.
- Breach: small Cyberpunk-style sequence puzzle used to earn credits or escape
  when the user cannot pay.
- Alarm integration: later feature using the same escape rules.

Skipped for V1: shops, levels, leaderboards, daily rewards, sync, cloud state,
inflation, anti-tamper, and analytics.

## Local State

Persist in one SharedPreferences file, probably `retui_focus_friction`:

| Key | Type | Meaning |
| --- | --- | --- |
| `credits` | int | Current Retui Credit balance. |
| `breach_keys` | int | Current breach key count. |
| `grant_version` | int | Last local grant version applied. |
| `dystopia_enabled` | boolean | Whether the local focus-friction system is active. |
| `lockdown_end_elapsed` | long | Active lockdown end time, or `-1`. |
| `lockdown_total_ms` | long | Active lockdown total duration. |
| `lockdown_reason` | string | Optional user label. |

Initial grant: when `grant_version < 1`, add `1000` credits and set it to `1`.
Do not grant on every app update unless we intentionally bump the grant version.

Enablement flow:

1. User opens Personalization.
2. User selects `Opt into Dystopia`.
3. Dialog explains that this enables local credits, breach keys, paid Pomodoro
   exits, breach puzzles, and Lockdown.
4. User must hold the fingerprint icon for `3` seconds.
5. On completion, set `dystopia_enabled = true` and apply the one-time grant.
6. Turning it off disables the system without deleting the stored wallet.

## Economy Rules

| Event | Credit change | Key change | Notes |
| --- | ---: | ---: | --- |
| First V1 grant | `+1000` | `0` | One-time local grant. |
| Stop Pomodoro early | `-500` | `0` | If balance is high enough. |
| Stop Lockdown early | `-500` | `0` | If balance is high enough. |
| Stop Lockdown with key | `0` | `-1` | If key count is high enough. |
| Normal breach success | `+500` | rare `+1` | V1 fixed chance, likely `5%`. |
| Normal breach failure | `-1` | `0` | Floor credits at `0`. |
| Emergency breach success | `+1000` | rare `+1` | Used when locked and unable to pay. |
| Emergency breach failure | `-1` | `0` | Retry allowed even at `0`. |
| Alarm dismiss by credits | `-500` | `0` | Later feature. |
| Alarm dismiss by key | `0` | `-1` | Later feature. |
| Alarm dismiss by breach | `0` | `0` | Later feature; no reward to avoid farming. |

## Breach Minigame

Reference mechanic:
https://devcry.heiho.net/html/2025/20250504-cphack.html

The article recreates Cyberpunk 2077 Breach Protocol as a square matrix of hex
codes, a limited buffer, and required code sequences. Selection starts from the
top row, then alternates between the selected column and selected row.

V1 rules:

1. Generate a small grid of hex-like tokens: `1C`, `55`, `7A`, `BD`, `E9`,
   `FF`, etc.
2. Start selection on row `0`.
3. User chooses any available cell in the active row.
4. Add that token to the buffer and mark the cell used.
5. Next choice must come from the selected cell's column.
6. After that, next choice must come from the selected cell's row.
7. Continue alternating row, column, row, column.
8. Win if the target sequence fills the buffer.
9. Lose if the buffer fills without the full target sequence.

V1 difficulty:

| Mode | Grid | Buffer | Targets | Reward |
| --- | --- | --- | --- | --- |
| Normal | `4x4` | `4` | `1` sequence of length `4` | `500` credits |
| Emergency | `4x4` | `5` | `1` sequence of length `5` | `1000` credits |
| Later hard mode | `5x5` or `6x6` | `5-6` | `3` sequences | Not V1 |

Implementation rule: keep game logic independent from UI. The article makes the
same practical point: input/rendering should be swappable around a plain game
state.

## User Flows

### Pomodoro Early Stop

1. User starts Pomodoro.
2. User chooses terminate.
3. If credits >= `500`, show credit cost and confirm.
4. On confirm, subtract `500` and stop.
5. If credits < `500`, offer Breach to earn enough credits first.

### Lockdown

1. User starts `lockdown [duration] [reason]`.
2. Re:T-UI shows a full-screen lockdown surface until the timer ends.
3. The overlay hides the launcher chrome, input, suggestions, drawers, and
   normal command surface while active.
4. Early exit choices:
   - spend `500` credits
   - spend `1` breach key
   - run emergency breach
5. Emergency breach success ends lockdown and grants `1000` credits.

### Alarm Later

1. Alarm fires inside Re:T-UI.
2. User may mute sound/vibration immediately for free.
3. Dismissal still requires credits, key, or breach.
4. Alarm breach gives no reward.

## Expected Code Shape

- `RetuiCreditManager`: owns credits, keys, grant version, spend/add helpers.
- `BreachManager`: owns puzzle generation, selection rules, win/loss checks.
- `LockdownManager`: owns active lockdown timer and escape decisions.
- `PomodoroManager`: calls the credit escape path instead of stopping directly.
- `BackupManager` and `SpaceManager`: include the new prefs file.
- Commands:
  - `credits` or `wallet`: show credits and keys.
  - `breach`: start normal breach.
  - `lockdown`: start/status/stop lockdown.

Keep UI code as rendering glue. Do not put economy or lockdown policy in
`UIManager`.

V1 visual rule: use the warning-label/sticker shapes from the reference image,
but take colors from the active launcher suggestion buttons and text. Do not
hardcode the acid green/black palette.

## V1 Build Order

1. Add local credit/key manager with one-time grant.
2. Add headless breach game logic and one tiny self-check.
3. Add a plain breach screen.
4. Wire normal breach rewards.
5. Gate Pomodoro early stop.
6. Add Lockdown timer and escape screen.
7. Add backup/Space coverage.
