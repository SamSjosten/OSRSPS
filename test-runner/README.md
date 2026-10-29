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

## Logging in

Nobody needs to type a password. While the client is on the login screen, the plugin logs in the **Auto login
account** (default `test`, empty turns it off) with the **Auto login password** (default `test`). The server's first login
claims a name with its password, and on 214 the test accounts are admins, so set a private password here (never a real
one) before anyone else can reach the server. The account must be one of the **Test accounts**. It
waits 3 s after the login screen shows, retries every 15 s, and stops after 5 tries in a row until a login succeeds or
the plugin restarts.

A second client on the same machine runs the plugin too. To log another account in on it, just start typing: a key
pressed on the login screen, a name other than the one auto login typed found in the login box (a logout since
included), or a login under another name tells the plugin a person is at that client, and auto login leaves it alone
until the plugin restarts.

So starting `C:\RSPS\RSProx.cmd` (or its scheduled task) is enough: the client logs in, runs every `runOnLogin`
script, and then any script dropped into the tests folder.

RSProx itself still waits for a click on **Launch Session**. [`launch\`](launch/README.md) has an `RSProx.cmd` that
clicks it, and `start-client.ps1`, which (re)starts the client and waits until it is logged in.

## Running a script

Scripts are `*.json` files in `C:\RSPS\.rlcustom\tests\`. A script runs:

- when it is copied into that folder or changed there (setting **Run dropped scripts**). Files already there when the
  client starts don't run; save one again to rerun it.
- after each login, when the script has `"runOnLogin": true` (setting **Run login scripts**).

Scripts run one at a time, in the order they were found. A script waits for a login before any step but
`waitForLogin`. The chatbox says when a script starts and how it ended.

To run many and get one table of results, `run-scripts.ps1` writes them into the tests folder one at a time, each once
the last one's result is in, and prints each script's result, steps passed, time and failure:

```powershell
.\test-runner\run-scripts.ps1 .\test-runner\examples          # every example, by file name
.\test-runner\run-scripts.ps1 .\test-runner\examples\castle-talk-cook.json .\test-runner\examples\hans-demo.json
```

It exits with 0 only when every script passed. Where Windows won't run scripts, start it with
`powershell -ExecutionPolicy Bypass -File .\test-runner\run-scripts.ps1 ...`, which allows it for that one run only.

```powershell
Copy-Item .\test-runner\examples\hans-demo.json C:\RSPS\.rlcustom\tests\
Get-Content C:\RSPS\.rlcustom\test-results\hans-demo.json
```

`hans-demo.json` first sends `::keys on`, so Hans says each line's key (`[hans.greeting]`) instead of the words in the
server's `data/dialogue.txt`, and the script passes whether or not that file has his words. Scripts in git check keys,
never words: the words are Jagex's and stay out of git (see the main README's Dialogue words).

`examples\` also has the Lumbridge Castle checks. They need runner 0.4.2 or later (`locOption` came in 0.3.0;
`takeItem`, `assertInventory`, `finishDialogue` and Examine in 0.4.0; `assertLoc` in 0.4.2). The combat checks
(`combat-attack-man.json`) need 0.5.0, which brought `assertHitsplat`, `assertNpcHealth`, `assertStat`,
`assertGroundItem` and `assertAnimation`; `combat-die-grave.json` needs 0.6.1 (`assertInterface`, and `clickWidget` from 0.6.0). `combat-harlan.json` needs 0.6.3, which reads and continues the two-item box (objectbox_double). `combat-raid-bows.json`
(the twisted bow, and the Venator bow's bounce in a zone `::multi` makes multi-way) needs 0.5.0. `combat-blowpipe.json` (charged with `::blowpipe`, shot, checked worn, then taken off and uncharged) needs 0.6.0 for `wornOption`. `combat-chinchompa.json` (black chinchompas thrown at a man, and their blast catching the man beside him in a `::multi` zone) needs 0.5.0. `combat-magic-strike.json` (Fire Strike from the spellbook on a man: its animation, graphics, runes and XP, the refusals for too low a level and too few runes, and a staff of fire standing in for the fire runes) needs 0.7.0 for `castOnNpc` and `assertSpotanim`. `combat-magic-autocast.json` (Wind Strike picked for autocast on the combat tab with a staff of fire, a man fought from one Attack click, defensive autocast's Defence XP, and a style button turning autocast off) needs 0.7.0 for `assertSpotanim`. `combat-magic-weakness.json` (the courtyard imp's 10% water weakness raising autocast Water Bolt's max hit from 10 to 11 in `::combat`, then the bolt cast on the imp) needs 0.7.0 for `assertSpotanim`. `combat-magic-bind.json` (Bind holding a man where he stands, a second Bind refused while he is held, and the immunity after, read off `::npcs`) needs 0.7.0 for `castOnNpc` and `assertSpotanim`. `combat-magic-ice.json` (Ice Barrage from the Ancient book, switched to with `::spellbook`, on one of two imps spawned side by side in a `::multi` zone, both frozen, read off `::npcs`) needs 0.7.0 for `castOnNpc` and `assertSpotanim`. `combat-magic-shadow.json` (Tumeken's shadow charged with `::shadow`, checked, wielded, its own spell cast on a man from one Attack click with a charge a cast and no runes, then taken off and uncharged for the runes back) needs 0.7.0 for `assertSpotanim`. `prayer-protect.json` (Protect from Melee clicked on in the prayer book, a man's punches blocked, its drain, Thick Skin alongside, the Piety refusal, and every prayer off at no points, read off `::prayers`) needs 0.6.0 for `clickWidget`. `prayer-boosts.json` (Piety clicked on raising a man fight's attack roll, max hit and defence roll in `::combat` from the next tick, and back once it is off) needs 0.6.0 for `clickWidget`. `prayer-redemption.json` (Redemption clicked on, a `::hit` leaving 9 of 99 hitpoints healed by 24, and every prayer point gone) needs 0.6.0 for `clickWidget`. `prayer-quick.json` (quick prayers: the prayer orb refused with nothing picked, Piety and Protect from Melee picked in its Setup, Done putting the prayer book back, and the orb turning both on and every prayer off, read off `::prayers` and `::varbit 4103`) needs 0.6.1 for `clickWidget` with `op` and `assertInterface`. `food-combo.json` (a shark and a karambwan eaten one after the other healing 38, then a meat pie in two bites leaving its dish, read off `::hp`) needs 0.6.0 for `itemOption`.

- `lumbridge-castle-doors.json` (the smallest, run it first) and `castle-doors-rest.json`: every door.
- `lumbridge-castle-travel.json`, `castle-stairs-all.json` and `castle-ladders-all.json`: every option on the stairs,
  ladders, trapdoor and cellar ladder.
- `lumbridge-castle-search.json`, `castle-search-rest.json` and `castle-drawers.json`: every crate, sack, chest and
  drawers.
- `castle-talk-*.json`: Talk-to on each castle NPC. Like `hans-demo.json` they turn `::keys on` and expect each
  line's `[key]`, so they pass with or without words in `data/dialogue.txt`. The man's and woman's talks go a random
  way after the first line, so those two click through the rest with `finishDialogue`.
- `castle-examine.json`: Examine on every castle NPC and some locs, with `::keys on` (each answer is its
  `[examine....]` key).
- `castle-take-items.json`: empties the inventory, takes every castle item spawn and checks the inventory after each.
  An item taken less than 100 ticks before is not back yet, so each Take waits up to 70 s.

The equipment and ground item checks need 0.6.0 (`itemOption`, `wornOption`, `clickWidget`, `assertEquipment`,
`assertWidgetText`, `assertPose`, `logout`) and start from `::scenario lumbridge-bare`, ending with `::reset`:

- `equipment-wield.json`: Wield a bronze axe (worn, on the model, the combat tab says Axe), the stats screen's
  bonuses, Equip a shield from the stats screen, a two-handed sword taking the shield off, the whip's 70 Attack, the
  whip still worn after a logout, and Remove.
- `ground-drop-take.json`: drop an axe, see it on the ground, take it back.
- `lumbridge-bury-bones.json`: bury two bones, 4.5 Prayer XP each.
- `lumbridge-bank.json`: at a castle bank booth, deposit, withdraw 5 and as notes, deposit the inventory, log out and
  back in and take out what is still banked. It can't type an amount, so Withdraw-X isn't in it.
- `lumbridge-bank-tabs.json`: from `::scenario lumbridge-bank-tabs`, a tab with a placeholder that takes the logs back
  into their slot, Collapse tab, bank fillers and Clear-All's confirmation. The runner can't drag, so items aren't
  moved into tabs here.
- `lumbridge-deposit-box.json`: the castle's deposit box (Deposit-1, the 5 button, Wield from the extra option, a worn
  slot's Bank), then the bank's worn items view (its bonus lines, Wield, Remove and Deposit) and the banker's PIN
  settings screen.
- `lumbridge-shops.json`: the Lumbridge General Store (Value, Buy 1 and selling a pot back for nothing, the Quantity 5
  button, Sell 50) and Bob's Brilliant Axes (a pot refused, a bronze axe bought and sold back).
- `lumbridge-talk-shopkeepers.json`, `lumbridge-talk-sheared-ram.json`, `lumbridge-talk-donie-gee.json`,
  `lumbridge-talk-tutors.json`, `lumbridge-talk-townsfolk.json`: talk to each of the town centre's NPCs through every
  option with `::keys on`, checking each line's key (the Sheared Ram's needs 0.8.0 for `assertVarbit`).
- `lumbridge-freds-farm.json`: Fred the Farmer's talk (with `::keys on`), an onion picked from his patch, a sheep
  sheared for wool that the client then shows as sheared, and eight chickens killed for their feathers.
- `lumbridge-mill.json`: Millie Miller's and Gillie Groats' talks (with `::keys on`), wheat picked into grain, the
  grain up Mill Lane Mill's ladders into the hopper and through the controls into two pots of flour (checking
  varbits mill_flour and mill_showflour), Gillie's talk when milking with no bucket, and a dairy cow milked into two
  buckets. Needs 0.8.0 for `assertVarbit`.
- `lumbridge-take-items.json`: takes one item spawn in each area outside the castle (the general store's onion,
  Lozar's iron dagger, an egg and the shears at Fred's farm, the Groats' bucket, Father Urhney's leather gloves, swamp
  tar), teleporting onto each tile.
- `skills-levelup.json`: `::addxp` past a Woodcutting level, then an Attack level that raises the combat level: the
  chat message, the fireworks, the level-up box with each line's key (`::keys on`), and the combat level's box after
  the skill's. Walking closes each box. Needs only 0.7.0.
- `woodcutting-lumbridge.json`: Chop down with no axe, then a bronze axe on a tree north of the castle (one log, the
  stump, the tree back), then a level 99 dragon axe on the oak south of the castle (12 logs before its timer fells it).
  Needs only 0.5.0.
- `woodcutting-nests.json`: `::nest seeds` and `::nest ring` drop a nest next to the player as a log's 1/256 roll does;
  each is taken and searched, leaving an empty nest and the seed or ring. Needs only 0.6.0.
- `firemaking-lumbridge.json`: at the spawn, a tinderbox on oak logs (level 15 needed), then on logs (lit after a
  roll or more, a step west, 40 XP), Light on logs on the ground at level 43, and the first fire's ashes. Needs 0.10.0
  (`useItemOnItem`, `groundItemOption`).
- `mining-lumbridge.json`: at the Lumbridge Swamp mine, Mine with no pickaxe, then a bronze pickaxe on copper (one
  ore, the empty rock, the copper back) and tin, and mithril's level 55. Needs only 0.5.0.
- `ground-high-id-take.json`: an item with an id above 32767 (rosewood logs, 32910) goes from the ground when taken,
  which tells whether the client keeps all 16 bits of the id in OBJ_DEL. Needs only 0.5.0.

The settings checks need 0.8.0 (`assertVarp`, `assertVarbit`, `mouseClick`). They set each setting
to a known start with `::varp` and `::varbit` and put the account back at the end:

- `settings-side.json`: the settings side panel's three tabs, scroll-wheel zoom, accept aid, skull prevention, run,
  the player attack priority dropdown, the master and music sliders (a mouse click in the middle of each, 58%) and the
  master mute; every one checked in both the server's copy and the client's, then again after a relog.

## Results

Results go to `C:\RSPS\.rlcustom\test-results\`:

- `<script>.json`: the last run, replaced by each run (written whole, then moved into place).
- `<script>.png`: the client's picture when that run failed (setting **Screenshot on failure**). A passing run deletes
  it, so a png is always the picture of the result next to it.
- `history\<UTC time>_<script>.json`: every run (time to the millisecond).

`<script>` is the file name without `.json`, with any character but letters, digits, `.`, `_` and `-` written as `%XX` (so `my test.json` gives `my%20test`). If a failure's screenshot comes in after a newer run of the same script has finished, the older result only goes to `history`.

```json
{
  "script": "hans-demo",
  "name": "Teleport, walk and talk to Hans",
  "result": "fail",
  "failure": "step 7 (assert dialogue from Hans contains \"[hans.greeting]\"): timed out after 20000 ms: no dialogue open",
  "startedAt": "2026-10-24T20:00:00Z",
  "ms": 23140,
  "ticks": 38,
  "trigger": "file",
  "runner": "0.1.0",
  "screenshot": "hans-demo.png",
  "steps": [
    {"n": 1, "step": "waitForLogin", "does": "wait for login", "result": "pass", "detail": "logged in as test", "ms": 0, "ticks": 0},
    {"n": 7, "step": "assertDialogue", "does": "...", "result": "fail", "detail": "timed out after 20000 ms: no dialogue open", "ms": 20000, "ticks": 33},
    {"n": 8, "step": "continueDialogue", "does": "continue dialogue", "result": "skipped"}
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
| `assertLocation` | `x`, `y`, `plane` | Waits until the player stands on the tile, as the server has it and as the client draws it. Without `plane`, any plane. If the client draws the player on another tile, the step says where. |
| `walk` | `x`, `y` | Moves the mouse over the tile and, once the client has drawn a frame with it there, clicks the Walk here the client offers at that spot; then waits until the player stands on it. The tile must be on screen; until it is (right after a teleport the scene can lag a moment), it keeps trying, turning the camera toward the tile and tilting it down. If the player stops on another tile for 3 game ticks, it clicks again, and while they head for another tile the step says which. It doesn't click while the client draws the standing player on another tile than the server has them on, since the camera follows the drawn player; and it passes once the player is drawn on the tile. A tile that stays off screen is reported with the camera's angles and position. Default timeout 30 s. |
| `npcOption` | `npc` (name) or `npcId`, `option` | Clicks the option (such as `Talk-to`, or `Examine`, which every NPC has) on the nearest such NPC, retrying until one is in view. An NPC on a long walk can be out of view for a while (Hans's lap of the castle takes about a minute): give its step a `timeoutMs` that covers the walk. Doesn't wait for the result: follow it with an assert. |
| `locOption` | `loc` (name) or `locId`, `option`, `x`+`y` | Clicks the option (such as `Open`, `Climb-up`, `Search`, or `Examine`, which every loc has) on the nearest such loc on the player's plane, retrying until one is in the scene. With `x` and `y`, only a loc on that tile (a big loc on any tile it covers). A loc that changes with a varbit is matched, and examined, by what it is now. Doesn't wait for the result: follow it with an assert. |
| `assertLoc` | `loc` (name) or `locId`, `x`, `y`, `option` | Waits until such a loc is on the tile on the player's plane (a big loc on any tile it covers), with `option` when given: a door's `Open` once it has shut. Put one after a last Close before a teleport: a teleport cancels a click the server hasn't carried out yet, and the door would stay open for the next run. |
| `takeItem` | `item` (name) or `itemId`, `x`+`y` | Clicks Take on the nearest such item on the ground on the player's plane, retrying until one is in the scene. With `x` and `y`, only an item on that tile. Take is the third ground option, as the server has it. Doesn't wait for the result: follow it with `assertInventory`. |
| `assertInventory` | `item` (name) or `itemId`, `count` | Waits until the inventory holds exactly `count` of the item (all stacks added up), or any at all without `count`. `count` 0 waits until there are none. |
| `assertDialogue` | `contains`, `speaker` | Waits until an open dialogue's text contains `contains` and its name is `speaker` (both optional). Colour tags and line breaks are removed first. |
| `continueDialogue` | | Waits for a dialogue with Click here to continue, clicks it, and waits until that dialogue has changed or closed. |
| `chooseOption` | `text` | Waits for an option menu, clicks the option equal to `text` (else the first containing it), and waits until the menu has gone. |
| `assertNoDialogue` | | Waits until no dialogue is open. |
| `finishDialogue` | | Clicks through whatever dialogue is open until none is: continue on each page, the first option on a menu. Passes at once when none is open. For talks that go a random way. |
| `assertMessage` | `contains` | Waits for a chat message containing `contains`, counting only messages since the last step that acted. |
| `waitTicks` | `ticks` | Waits that many game ticks. |
| `assertHitsplat` | `npc` (name) or `self: true`, `amount` | Waits for a hitsplat on the NPC of that name (the one the player is fighting or talking to, else the nearest; several castle NPCs are a `Man`), or on the player, counting only hitsplats since the last step that acted. With `amount`, one showing exactly that; a miss shows 0. |
| `assertNpcHealth` | `npc` (name) or `npcId`, `min`, `max` | Waits until that NPC's health bar (the NPC chosen as for `assertHitsplat`) is within the bounds, in percent (`max: 99` is "hurt"). The client is only told an NPC's health once it is hit, so this also waits for the first hit to show, and says `no health bar` until then. |
| `assertStat` | `skill`, `level`, `xp` | Waits until the player's real (unboosted) level and experience in the skill are at least those given. `skill` is a name as the stats tab shows it, such as `Attack`. |
| `assertGroundItem` | `item` (name) or `itemId`, `x`+`y`, `absent` | Waits until such an item lies on the ground in the scene on the player's plane (on the tile, with `x` and `y`), or, with `absent: true`, until none does; absence on a tile counts only once that tile is in the loaded scene. |
| `assertAnimation` | `id`, `npc` (name) | Waits until the player, or the NPC of that name (chosen as for `assertHitsplat`), has started animation `id` (a cache sequence id, such as 422 for a punch), counting only animations since the last step that acted. |
| `assertSpotanim` | `id` or `ids`, `npc` (name) | (0.7.0) Waits until the player, or the NPC of that name (chosen as for `assertHitsplat`), has been given spotanim `id` (a cache spotanim id, such as 101 for Fire Strike's impact), or any of the list `ids`, counting only spotanims since the last step that acted. The client doesn't say which spotanim changed, so one the actor already had counts again when it gets another. |
| `castOnNpc` | `interfaceId`, `component`, `npc` (name) or `npcId` | (0.7.0) Picks the spell at that component (the standard spellbook is 218; Fire Strike is 218:19) and casts it on the NPC, chosen as for `assertHitsplat`, as clicking the spell and then Cast on the NPC would; retries until the interface is loaded and the NPC in view. Doesn't wait for the result. |
| `itemOption` | `item` (name) or `itemId`, `option` | Clicks the option (such as `Wield`, `Wear`, `Eat`, `Drop`, or `Examine`) on the first such item in the inventory, retrying until there is one. Doesn't wait for the result: follow it with an assert. |
| `wornOption` | `slot`, `option` | Clicks the option (`Remove`, `Examine`, or one of the item's own) on a slot of the equipment tab. `slot` is one of `head`, `cape`, `neck`, `weapon`, `body`, `shield`, `legs`, `hands`, `feet`, `ring`, `ammo`. |
| `clickWidget` | `interfaceId`, `component`, `slot`, `op` | Clicks op `op` (default 1) on an interface component, or on its slot `slot` (an item in an interface's inventory), as that menu entry would; retries until the interface is open. The equipment tab's View equipment stats is 387:1. |
| `assertEquipment` | `slot`, `item` (name) or `itemId`, `empty` | Waits until the slot of the worn inventory holds the item, or with `empty: true` nothing, and the player's model shows the same (rings and ammo aren't on the model). |
| `assertWidgetText` | `interfaceId`, `component`, `contains` | Waits until the component's text, with everything inside it, contains `contains`; without `component`, any text in the interface. Colour tags are removed first. The interface must be open. Text the server sets (IF_SETTEXT) is there on a side tab that isn't showing, but text a tab's own client scripts write (the combat tab's style buttons) only follows changes while that tab shows: open it first with `clickWidget` on its stone (fixed frame 548, stone0 64 for combat). |
| `assertPose` | `idle` | Waits until the player stands in that animation: the ready pose of their weapon's stance (808 is human_ready). |
| `logout` | | Clicks Click here to logout and waits until the client is logged out. Auto login logs the account back in; follow it with `waitForLogin`. |
| `assertInterface` | `interfaceId`, `absent` | Waits until the interface (by id, such as 672 for the gravestone) is open, or, with `absent: true`, until it is not. |
| `assertVarp` | `id`, `value` | (0.8.0) Waits until varp `id` holds `value` both as the server last sent it and in the client's own copy. The client's scripts change their copy first for most settings, so a setting the server ignored shows as the client alone holding it; the result shows both. |
| `assertVarbit` | `id`, `value` | (0.8.0) The same for varbit `id`, worked out from both copies of its varp with the client's varbit definition. |
| `runClientScript` | `id`, `args` | (0.8.0) Runs client script `id` from the cache with `args` (whole numbers and strings in the script's order; a component is interface × 65536 + component), as the client's interfaces would. For what the runner can't do by clicking. A script's `if_runscript` sends nothing when run this way, so a slider needs a `mouseClick` instead (VM, 2026-10-29). |
| `assertWeight` | `kg` | (0.9.0) Waits until the carried weight the server last sent is `kg` whole kilograms (rounded toward zero, so -4.535 kg is -4), as RuneLite's weight shows it. |
| `groundItemOption` | `item` (name) or `itemId`, `op`, `option`, `x`+`y` | (0.10.0) Clicks ground op `op` (1 to 5; Take is 3, logs' Light is 4) on the nearest such item on the ground, as `takeItem` does Take; `option` is the menu text. RuneLite doesn't give an item's ground options, so the script names the op. |
| `useItemOnItem` | `item` (name) or `itemId`, `onItem` or `onItemId` | (0.10.0) Clicks Use on the first such item in the inventory, then the first other slot holding the second item, as the server reads it (IF_BUTTONT). Retries until both are there; doesn't wait for the result. |
| `mouseClick` | `interfaceId`, `component`, `slot` | (0.8.0) Presses the left mouse button on the middle of the component (or its slot), for what opens on the mouse click itself rather than on a menu option, such as a dropdown's box. Assumes stretched mode is off. |

Dialogues are the chatbox's chat_left (NPC), chat_right (player), chatmenu (options), messagebox, objectbox and (0.6.3)
objectbox_double, the box with two items.

## Known limits

- Chat is sent by putting the text in the chat input and sending an Enter key to the client's canvas. If a key
  remapping plugin is on, it may swallow the Enter. When the client leaves the line in the input, the runner asks for
  the canvas's keyboard focus and presses Enter again, up to five times two ticks apart (0.5.1); the result says
  whether the canvas had focus.
- `walk` needs the tile on screen: it clicks the 3D view, as a player does. Walk in stages for far tiles. When the tile
  is off screen it turns the camera toward it, so a script can leave the camera however it finds it.
- Auto login starts the login by setting the client's game state to LOGGING_IN, as RuneLite's developer tools do.
  If it doesn't log in, the plugin log (`C:\RSPS\.runelite\logs\client.log`) says what it tried.

## Building

`./gradlew -p test-runner build` (CI's `test-runner` job). The tests run without a client: steps, the runner, script
parsing and the result files are plain Java, behind the `Game` interface that `ClientGame` implements over RuneLite.
