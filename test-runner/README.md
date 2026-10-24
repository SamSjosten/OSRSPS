# Test Runner (RuneLite plugin)

Runs scripted tests in our test client against our own server and writes a pass/fail result per script, so a
repeatable check needs nobody watching the screen. A script says what to do (send `::tele`, walk to a tile, Talk-to an
NPC, continue a dialogue) and what should then be true (where the player stands, what the dialogue says, which chat
message arrived). Every step has a timeout.

It acts through RuneLite's client API: each click is the menu entry a real click would make, so the client sends the
same packets. Nothing is matched on pixels.

## Only our server, only test accounts

The plugin clicks for you. That is botting on Jagex's worlds, so:

- It is only ever sideloaded into the RSProx test client pointed at OSRSPS. It is not for the Plugin Hub or any other
  RuneLite.
- A script names its `account`, and only runs while that account is logged in. The account must also be in the
  plugin's **Test accounts** setting (default `test, test2`). Never add a real Jagex account there.

## Installing

RSProx starts its RuneLite in developer mode with the RuneLite folder `C:\RSPS\.rlcustom`, and developer mode loads every
jar in `sideloaded-plugins` at startup.

1. Download `test-runner-jar` from the latest `build` run on GitHub Actions and unzip it.
2. Copy `test-runner-<version>.jar` to `C:\RSPS\.rlcustom\sideloaded-plugins\` (remove an older test-runner jar).
3. Start `C:\RSPS\RSProx.cmd`. **OSRSPS Test Runner** is on by default; its folders are created on first start.

## Running a script

Scripts are `*.json` files in `C:\RSPS\.rlcustom\tests\`. A script runs:

- when it is copied into that folder or changed there (setting **Run dropped scripts**). Files already there when the
  client starts don't run; save one again to rerun it.
- after each login, when the script has `"runOnLogin": true` (setting **Run login scripts**).

Scripts run one at a time, in the order they were found. A script waits for a login before any step but
`waitForLogin`. The chatbox says when a script starts and how it ended.

```powershell
Copy-Item .\test-runner\examples\hans-demo.json C:\RSPS\.rlcustom\tests\
Get-Content C:\RSPS\.rlcustom\test-results\hans-demo.json
```

## Results

Results go to `C:\RSPS\.rlcustom\test-results\`:

- `<script>.json`: the last run, replaced by each run (written whole, then moved into place).
- `<script>.png`: the client's picture when that run failed (setting **Screenshot on failure**). A passing run deletes
  it, so a png is always the picture of the result next to it.
- `history\<UTC time>_<script>.json`: every run (time to the millisecond).

`<script>` is the file name without `.json`.

```json
{
  "script": "hans-demo",
  "name": "Teleport, walk and talk to Hans",
  "result": "fail",
  "failure": "step 6 (assert dialogue from Hans contains \"Hello. What are you doing here?\"): timed out after 20000 ms: no dialogue open",
  "startedAt": "2026-10-24T20:00:00Z",
  "ms": 23140,
  "ticks": 38,
  "trigger": "file",
  "runner": "0.1.0",
  "screenshot": "hans-demo.png",
  "steps": [
    {"n": 1, "step": "waitForLogin", "does": "wait for login", "result": "pass", "detail": "logged in as test", "ms": 0, "ticks": 0},
    {"n": 6, "step": "assertDialogue", "does": "...", "result": "fail", "detail": "timed out after 20000 ms: no dialogue open", "ms": 20000, "ticks": 33},
    {"n": 7, "step": "continueDialogue", "does": "continue dialogue", "result": "skipped"}
  ]
}
```

`result` is `pass`, `fail`, or `error` when the file could not be read as a script (then `failure` says why and no step
ran). `ms` and `ticks` are wall time and game ticks seen; after a failing step the rest are `skipped`.

## Script format

```json
{
  "name": "Teleport, walk and talk to Hans",
  "account": "test",
  "runOnLogin": false,
  "timeoutMs": 10000,
  "steps": [
    {"step": "waitForLogin"},
    {"step": "command", "text": "::tele 3222 3218", "expectMessage": "Teleported to 3222, 3218"},
    {"step": "assertLocation", "x": 3222, "y": 3218, "plane": 0}
  ]
}
```

`account` and `steps` are required. `timeoutMs` (default 10000) is the timeout of every step that doesn't set its own.
Any object may carry a `comment`. Unknown fields are an error, so a typo can't be silently ignored. Tiles are
`x`, `y`, `plane` as RuneLite shows them (`y` is our server's `z`).

The runner polls the current step every client cycle (20 ms). A step that acts does so once, as soon as it can, and
then waits to see it worked; an assert passes as soon as it holds. So an assert right after an action waits for the
server's answer instead of failing on what was there before.

| `step` | Fields | Does |
| --- | --- | --- |
| `waitForLogin` | | Waits until a player is logged in. Default timeout 120 s. |
| `command` | `text`, `expectMessage` | Puts `text` in the chat input and presses Enter. `::` is added if missing. With `expectMessage`, waits for a chat message containing it. |
| `chat` | `text`, `expectMessage` | Same, without adding `::`. |
| `assertLocation` | `x`, `y`, `plane` | Waits until the player stands on the tile. Without `plane`, any plane. |
| `walk` | `x`, `y` | Clicks Walk here on the tile, then waits until the player stands on it. The tile must be on screen. Default timeout 30 s. |
| `npcOption` | `npc` (name) or `npcId`, `option` | Clicks the option (such as `Talk-to`) on the nearest such NPC, retrying until one is in view. Doesn't wait for the result: follow it with an assert. |
| `assertDialogue` | `contains`, `speaker` | Waits until an open dialogue's text contains `contains` and its name is `speaker` (both optional). Colour tags and line breaks are removed first. |
| `continueDialogue` | | Waits for a dialogue with Click here to continue, clicks it, and waits until that dialogue has changed or closed. |
| `chooseOption` | `text` | Waits for an option menu, clicks the option equal to `text` (else the first containing it), and waits until the menu has gone. |
| `assertNoDialogue` | | Waits until no dialogue is open. |
| `assertMessage` | `contains` | Waits for a chat message containing `contains`, counting only messages since the last step that acted. |
| `waitTicks` | `ticks` | Waits that many game ticks. |

Dialogues are the chatbox's chat_left (NPC), chat_right (player), chatmenu (options), messagebox and objectbox.

## Known limits

- Chat is sent by putting the text in the chat input and sending an Enter key to the client's canvas. If a key
  remapping plugin is on, it may swallow the Enter.
- `walk` needs the tile on screen: it clicks the 3D view, as a player does. Walk in stages for far tiles.
- Logging in is not scripted yet: a script waits for whoever logs in, which must be its account.

## Building

`./gradlew -p test-runner build` (CI's `test-runner` job). The tests run without a client: steps, the runner, script
parsing and the result files are plain Java, behind the `Game` interface that `ClientGame` implements over RuneLite.
