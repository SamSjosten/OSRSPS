# OSRSPS

A private Old School RuneScape test server for developing RuneLite plugins against 1:1 recreations of live content.
Our own engine in Kotlin on Java 21, using [RSProt](https://github.com/blurite/rsprot) for the network protocol.

Target revision: **241** (see `src/main/kotlin/osrsps/Revision.kt`).

## Never commit Jagex files

The game cache, the client, XTEA keys, RSA keys and gameplay recordings stay out of this repo, and so do the words
NPCs say (see [Dialogue words](#dialogue-words)). They live under `data/` (git-ignored) on the machine that runs the server.

## Build and run

Needs JDK 21. The Gradle wrapper downloads Gradle itself.

Sandbox VM (bash):

```bash
./gradlew build run
```

Windows (PowerShell):

```powershell
.\gradlew.bat build run
```

A successful run logs `OSRSPS server skeleton, revision 241`.

## Get the cache

The server downloads the live revision 241 cache and its XTEA keys from the [OpenRS2 archive](https://archive.openrs2.org)
into `data/cache/` (set `OSRSPS_DATA` to use another folder). Run this on the sandbox VM:

```bash
./gradlew run --args=fetch-cache   # download, then print a summary
./gradlew run --args=cache-info    # summarise an existing download
```

`data/cache-source.txt` records which OpenRS2 cache was used.

## Run the server

```bash
./gradlew run --args=serve          # JS5 and game on port 43594
./gradlew run --args="serve 50000"  # another port (launcher files on 50001)
```

RSProx assumes the game port is 43594. For another port, add `game_server_port: 50000` to the RSProx target below.

The first start generates a 1024-bit RSA key in `data/rsa.key` and writes its modulus to `data/rsa-modulus.txt`.
Any username and password log in, and you appear in Lumbridge.

## Keep it running with Docker

On the VM, Docker keeps the server up across crashes and reboots. `data/` is mounted into the container, so the
cache and RSA key are shared with the Gradle commands above.

```bash
docker compose up -d --build   # build and start; rerun after pulling changes
docker compose logs -f         # follow the log
docker compose down            # stop
```

## Connect RuneLite through RSProx

The server also hosts the launcher files on the HTTP port (game port + 1): `jav_config.ws` and `world_list.ws`,
both pointing at `OSRSPS_PUBLIC_HOST` (default `127.0.0.1`). RSProx reads them to find the world and connects to that
host on the game port.

**Over the LAN** (once the Proxmox host forwards 43594 and 43595 to the VM; the compose file sets
`OSRSPS_PUBLIC_HOST=192.168.0.210`): nothing needs to run on your PC.

**Over an SSH tunnel** (PowerShell, keep the window open; the default public host 127.0.0.1 matches this):

```powershell
ssh -J root@192.168.0.210 -L 43594:127.0.0.1:43594 -L 43595:127.0.0.1:43595 sam@10.99.0.10
```

Then add a target to `%USERPROFILE%\.rsprox\proxy-targets.yaml`, with the modulus from `data/rsa-modulus.txt`
on the VM and the host you use above:

```yaml
config:
  - name: OSRSPS
    jav_config_url: http://192.168.0.210:43595/jav_config.ws
    revision: 241.1
    modulus: <contents of data/rsa-modulus.txt>
    # game_server_port: 50000   # only when the server runs on a port other than 43594
```

Or let RSProx write it: the server also serves that target, modulus filled in, at
`http://<public host>:43595/proxy-targets.yaml`. In RSProx press **+** next to the proxy target selector, choose
**From URL…**, paste that address, and restart RSProx.

Launch RuneLite from RSProx with that target. The first login as a name claims it with the password you type; after
that it needs the same password (see Accounts).

### Letting friends test

Friends need a route to the server's two ports and RSProx; there is nothing of ours to install. The route belongs to the
Home Lab project (the plan is Tailscale limited to TCP 43594-43595); keep the server off the internet. Then each friend:

1. Installs RSProx from its [releases](https://github.com/blurite/rsprox/releases). The installer brings its own Java
   and keeps RSProx up to date. Windows or Linux only: RSProx's custom targets don't work on macOS.
2. Imports `http://192.168.0.210:43595/proxy-targets.yaml` with **+** → **From URL…**, restarts RSProx, picks
   **OSRSPS** and launches RuneLite.
3. Logs in with a name and password of their own, never their Jagex login.

RSProx downloads RuneLite and the game client itself, so we hand out no Jagex files. Its RuneLite keeps settings in the
friend's own `.runelite` folder, shared with a normal RuneLite install, in developer mode.

## Walking

Click the game view or minimap to walk. The run orb toggles running, ctrl-click inverts walk/run for one click, and
ctrl+shift-click teleports to the clicked tile (a testing tool). Collision is built from the cache's map files at
startup; the log line `Loaded collision for N map squares` confirms it.

To see what the server thinks is walkable around a tile (north up, `###` blocked, `[ ] ^ _` walls):

```bash
docker compose run --rm osrsps collision 3222 3218   # x z [level]; default is the Lumbridge spawn
```

Movement rules and where they come from:

| Rule | Source |
|------|--------|
| Route search: the client's 128x128 breadth-first search, neighbour order W, E, S, N, SW, SE, NW, NE | client behaviour |
| Unreachable target: closest reached tile within 10, by distance then path length | client behaviour |
| Walk 1 tile a tick, run 2; an odd last tile is walked | OSRS Wiki |
| Run drain ⌊60 + 67 × weight / 64⌋ × (1 − agility / 300), restore ⌊agility / 10⌋ + 15 a tick | OSRS Wiki, Energy |
| Run switches off at 0 energy; one-tile ticks don't drain | guessed |
| Map rebuild within 16 tiles of the 104x104 build area's edge | inferred, not measured |
| Routes cut to 25 turns | guessed |

## Game frame

Logging in with a fixed-size client opens the fixed frame; a resizable client gets resizable classic. The settings
tab's "Game client layout" dropdown switches between fixed, resizable classic and resizable modern. Walking, Esc and
a modal's close button close it; the logout tab's button logs out. Every interface and component id in
`game/GameFrame.kt` is checked against the cache's own names at startup: the log line
`Game frame ids match the cache's names` confirms it, and a revision that moves something logs a `Game frame:` warning.

| Behaviour | Source |
|-----------|--------|
| Interface and component ids | 241 cache gameval names |
| Dropdown options 1, 2, 3 are fixed, classic, modern | RSMod (rev 233), not checked against live |
| Layout change: window mode script 3998 first, interfaces move 2 ticks later (1 between the resizable layouts) | RSMod's emulation of live |
| Fixed to modern passes through classic when the stone arrangement differs | RSMod's emulation of live |
| Zoom limits script 626 on login | inferred from the 241 client scripts, not seen on live |
| Walking closes modals | RSMod; well known from live, not measured |

## Developer commands

Type these in the chatbox; the answer shows as a game message. Commands that only touch you (your position, stats,
items, vars, scenario) are for every player. The ones marked **admin** change the world for everyone (the clock, the
random seed, NPCs, locs, ground items, effects others see, the dialogue file), so only the names in `OSRSPS_ADMINS`
(comma separated, matched like save names) can run them; anyone else is told so, and the list an unknown command
prints leaves them out. With `OSRSPS_ADMINS` unset, everyone is an admin, as before, and the server log warns. Names
are claimed with a password at first login (see Accounts), so log in as each admin name before anyone else joins.

| Command | What it does |
|---------|--------------|
| `::pos` | Your coordinates as `x, z, level`, plus the map region and your tile inside it |
| `::tele x z [level]` | Teleports there; the level stays the same when left out. `::tele 3222, 3218, 0` also works, so `::pos` output pastes in |
| `::tick` | The server's current tick number, and whether the clock is paused |
| `::pause` | **admin.** Freezes the game once the current tick has finished |
| `::step [n]` | **admin.** While paused, runs n ticks (default 1, up to 1000), one every 600 ms, then says where it stopped |
| `::resume` | **admin.** Starts the clock again |
| `::varp id [value]` | Shows a varp, or sets it. `::varp 173 1` turns the run orb on |
| `::varbit id [value]` | Shows a varbit with the varp and bits it lives in, or sets it |
| `::dialogue` | **admin.** Reads `data/dialogue.txt` again, so new words show from the next line said, and says how many lines have words and what is wrong with the file |
| `::keys [on\|off]` | Your dialogue and examine lines show their keys (`[cook.greeting]`) instead of the file's words, until off or logout; no argument flips it. Test scripts in git turn it on, so they pass with or without words |
| `::if id [side]` | Opens interface `id` bare in the main game area (or the side panel with `side`), for checking it opens, moves with a layout change and closes |
| `::setlevel skill\|all level` | Sets a skill's XP to what the level needs and its current level to match. `::setlevel attack 99` |
| `::addxp skill xp` | Adds XP, one decimal place allowed (`::addxp cooking 17.5`); levelling raises the current level too |
| `::boost skill level` | Sets only the current (boosted or drained) level, 0 to 255. `::boost hp 1` for 1 hitpoint |
| `::seed [n]` | **admin.** Shows the random seed and how many numbers have been drawn since it was set, or restarts the rolls from seed n |
| `::gfx id [height] [delay]` | **admin.** Plays spotanim `id` on your tile for everyone nearby. Delay is in client cycles (20 ms) |
| `::proj id [dx dz]` | **admin.** Fires spotanim `id` as a projectile from your tile to the tile dx, dz away (default 5 tiles north) |
| `::groundobj id\|name [count]` | **admin.** Puts an item on your tile for everyone at once, with no timers, until taken, `::cleartile` or a restart |
| `::objspawn id\|name [count [respawn]]` | **admin.** Makes an item spawn on your tile that comes back `respawn` ticks (default 100) after it's taken, until a restart |
| `::grounditems` | Lists the objs on your tile: whose they are, ticks until public and until gone, and whether it's a spawn |
| `::cleartile` | **admin.** Removes every obj from your tile; item spawns there come back as if taken |
| `::anim id [delay]` | Plays animation (seq) `id` on you, seen by everyone nearby; `-1` stops it. Delay is 0 to 255 client cycles. `::anim 866` dances |
| `::spotanim id [height] [delay] [slot]` | Plays spotanim `id` on you rather than on your tile; `-1` clears the slot (0 to 255) |
| `::sound [id] [loops] [delay]` | Plays sound effect `id` to you alone (default 3813, the town crier's bell) |
| `::areasound id [radius] [loops]` | **admin.** Plays sound effect `id` from your tile, heard by everyone within `radius` tiles (default 5, up to 31) |
| `::jingle id` | Plays jingle `id` to you alone |
| `::item id\|name [count]` | Puts items in your inventory, by id, menu name or gameval name: `::item 1351`, `::item bronze axe 2`, `::item coins 10k`. Adds what fits |
| `::empty` | Empties your inventory |
| `::locs [radius]` | Lists the locs within radius tiles of you (default 1): id, name, tile, shape and angle, and whether they differ from the cache |
| `::addloc id [shape] [angle]` | **admin.** Puts loc `id` on your tile (default shape 10, a ground loc, angle 0), replacing what was on its layer |
| `::delloc` | **admin.** Removes every loc on your tile |
| `::resetlocs [radius]` | **admin.** Puts the locs within radius tiles of you (default 0, your tile) back as the cache has them |
| `::npc id\|name [wander]` | **admin.** Spawns an NPC on your tile, by id, menu name or gameval name: `::npc hans`, `::npc cow2_calf 3`. With a wander range it wanders that far from the tile, otherwise it stands still. Gone on restart |
| `::npcs [radius]` | Lists the NPCs within radius tiles (default 15) with their index, tile, spawn tile and wander range |
| `::delnpc index` | **admin.** Removes NPC `index` (from `::npcs`) |
| `::follow index` | **admin.** NPC `index` (from `::npcs`) follows you and stops beside you, as an NPC chasing its target does; again to stop |
| `::scenario [name]` | Starts a testing scenario: position, stats, inventory, equipment and vars in one go. Alone, lists the scenarios. `::scenario lumbridge-cows` |
| `::reset` | Puts you back as you were before your first `::scenario` |

Commands live in `src/main/kotlin/osrsps/game/command/`; register new ones on `World.commands`.

Varps and varbits are per player (`Player.vars`). Varbit layouts come from cache config 14 and the number of varps
from config 16; a changed varp is sent once at the end of the cycle, with its value then, so `::varp` also shows while
paused. The run orb is varp 173.

Skills are per player too (`Player.skills`): all 24, Sailing included, by name, short name (`hp`, `rc`, `wc`) or id.
XP is kept in tenths and sent whole; a changed skill is sent once at the end of the cycle (so also while paused), every
skill on the first one, and the combat level goes out in the appearance block when it changes. Current hitpoints are
the Hitpoints skill's current level (`Player.hitpoints`).

| Skills behaviour | Source |
|------------------|--------|
| XP table, 200M XP cap, XP kept to one decimal place | OSRS Wiki, Experience |
| Combat level ⌊¼ (Defence + Hitpoints + ⌊Prayer / 2⌋) + 13⁄40 max(Attack + Strength, ⌊3 Ranged / 2⌋, ⌊3 Magic / 2⌋)⌋ | OSRS Wiki, Combat level |
| New account: 10 Hitpoints, 1 elsewhere | OSRS Wiki |
| A level gained while boosted or drained raises the current level by one | inferred, not measured |
| The stat packet's invisible boost equals the current level | guessed |

## Items and inventory

Item types come from cache config 10 at startup (`ObjTypeLoader`), with their gameval names from index 24 for
`::item`. Every player has the 28-slot inventory, inv 93 (`Player.inventory`); it is sent in full on the first cycle
and then once a cycle with the changed slots. Item options reach content as `Handlers.onObj(id, option)` with the slot
as `Op.sub`. The inventory is saved with the player.

| Inventory behaviour | Source |
|---------------------|--------|
| Rev 241 item opcodes 44 to 54 (4-byte models), 15, 160 and 251 (no data) | found by decoding all 34,646 items in the 2026-10-14 cache; meanings of 15, 160, 251 unknown |
| Slot events, button ops 2, 3, 4, 6, 7 = iop1 to iop5, 10 = Examine | RSMod (rev 233) |
| Examine runs client script 6003 with the item's examine text and values | RSMod; argument types from the 241 cache |
| The examine's Grand Exchange price is the item's value | guessed: the server has no market prices |
| Notes: the item's name and value, stackable, "Swap this note at any bank for the equivalent item." | client genCert; the text from memory of live |
| Dragging swaps two slots; a click on a slot that holds something else is dropped | RSMod / inferred |
| An option or "Use" nothing handles says "Nothing interesting happens." | from memory of live |
| Stacks stop at 2,147,483,647; `::item` adds what fits | client limit; adding part is ours |

Check: `::item 1351` puts a bronze axe in your inventory; drag it to another slot; Examine it.

## Equipment

Every player has the worn inventory, inv 94 (`Player.equipment`), one slot per wearpos (`WearPos`). Wear, Wield and
Equip move an item there when no content handler takes the option; the equipment tab (wornitems, 387) removes it and
opens Equipment Stats (84, with the side inventory 85). The worn items show on the player's model, the weapon sets the
stance and the combat tab's weapon category, and equipment is saved with the player. Rules are in `Equipment.kt`, the
interfaces in `EquipmentTab.kt` and the weapon tables in `Weapons.kt`.

| Equipment behaviour | Source |
|---------------------|--------|
| Slot (wearpos1), covered slots (wearpos2/3: hair, jaw, arms; the shield for two-handed weapons) | 241 cache; RSProt hides covered slots on the model |
| A two-handed weapon takes off the weapon and the shield; a shield takes off a two-handed weapon; the swapped item goes into the slot the new one left | well known; slots from memory of live |
| Bonuses: params 0 to 9 attack and defence, 10 melee strength, 11 prayer, 12 ranged strength, 299 magic damage in tenths of a percent, 14 attack speed in ticks | 241 cache params, matched against items with well-known stats |
| Level requirements: params 434/436 and 435/437 (skill, level), checked against base levels | 241 cache (whip 0/70, granite maul 0/50 + 2/50); base levels from memory |
| Standard metal items without those params (mithril scimitar, rune dagger...) need 5/10/20/30/40/60 for steel to dragon | from memory of the OSRS Wiki, not checked; halberds left out |
| Combat tab: varbit 357 (combat_weapon_category) picks the tab from dbtable 78's rows; style buttons set varp 43 (com_mode) | 241 cache |
| Which items get which weapon category: by the item's category (obj opcode 94), e.g. 35 axes, 21 scimitars and longswords | inferred from item names; unknown weapons show as unarmed |
| A style the new weapon lacks falls back to the first | guessed |
| Stances (standing, walking and running animations) for 2h swords, godswords, staves, halberds, whips, mauls, crossbows, chinchompas, banners, the bulwark | animation ids from their gameval names; which weapon uses which is guessed |
| Worn ops: 1 Remove, 2 to 9 the item's params 451 to 458 (`Handlers.onWorn`), 10 Examine | 241 cache click masks and params; op numbering after RSMod |
| Equipment Stats text ("Stab: +4", "Melee strength: +5", "Magic damage: +5.0%", "Base: 2.4s") and the side inventory script 149 arguments | from memory / RSMod, not checked |
| "You are not a high enough level to use this item.", "You don't have enough free inventory space to do that." | from memory of live, not checked |
| Not built: equip sounds, weight from worn items, set effects, special attack bar, autocast | later tasks |

Check: `::item bronze axe`, click Wield: the axe shows in your hand, in the equipment tab's weapon slot, and the combat
tab says Axe with Chop, Hack, Smash and Block. `::item bronze 2h sword` and `::item wooden shield`, wear the shield,
then wield the 2h sword: the shield comes off. "View equipment stats" shows the bonuses; Remove puts items back.

## Tick control

Every 600 ms the game thread runs one tick, in fixed phases (`TickPhase`): input (logins, disconnects, client packets),
scripts (empty until the scripting framework), movement, then updates (player info, flush). Each phase runs for every
player before the next one starts; how live interleaves players and NPCs is not checked yet (guessed).

Every tick ends with the tick-end packet (`ServerTickEnd`), the one RuneLite waits for before it fires its game tick, so
the Tick Recorder gets one tick line per server tick (check: record 60 s standing still, then
`grep -c '"t":"tick"'` on the file gives about 100). A paused cycle is not a tick and sends no tick-end packet, so the
client sees no ticks until the next `::step`.

While paused, no game time passes, but the server still reads packets and sends unchanged player info every 600 ms, so
commands answer and the client stays connected. Clicks, buttons and public chat made while paused are held and run at
the start of the next tick, as if they had arrived then. Commands run straight away, so `::tele` while paused moves you,
and you see it on the next step.

Check: `::pause`, click a tile and nothing moves; each `::step` then moves you one tile (two with run on).

## Random seeds

Every random roll in the game (drops, NPC wandering, hit rolls, once they exist) comes from one seeded source,
`World.random` (`GameRandom`). The same seed with the same input on the same ticks gives the same rolls, so a run can be
repeated. A test fails if code under `src/main` uses any other random source.

The seed is set at startup from `OSRSPS_SEED` (add it under `environment:` in `compose.yaml`), or picked at random and
logged as `Random seed N` when unset. `::seed n` restarts the rolls from seed n at any time; to repeat a run from a known
point, `::pause`, `::seed n`, set things up, then `::step` or `::resume`. `::seed` alone shows the seed and how many
numbers have been drawn since, so two runs can be compared.

The generator is xoshiro256\*\* seeded through SplitMix64, written out in the repo so a seed gives the same rolls after
JDK or Kotlin upgrades. It is not the generator live uses: only repeatability matters here. With more than one player,
rolls are made in player index order within each tick phase, so the same seed also needs the same players in the same
slots (inferred from the tick loop; nothing rolls yet).

## Ground items

Everything on the ground goes through `World.groundObjs` (`game/GroundObjs.kt`): Drop in the inventory, walking to
an item and taking it, the timers, and item spawns. The clients are sent it through the zone updates below. Each obj
tells the client whose it is and the ticks until it turns public and until it disappears, which RuneLite's ground item
plugins read.

| Ground item behaviour | Source |
|-----------------------|--------|
| A drop is only the dropper's for 100 ticks (60 s) | OSRS Wiki, Drop and Dropped items |
| A tradeable drop then turns public and disappears 300 ticks (3 minutes) after the drop | OSRS Wiki, Drop ("visible after 60 s, disappear after another 120 s"); the Dropped items page's 200 ticks is for monster drops |
| An untradeable drop never turns public and disappears after 300 ticks; its timer stops while its owner is logged out | OSRS Wiki, Drop |
| Spawn respawn time ⌊BaseRate × (4000 − players) / 4000⌋, counted when taken | OSRS Wiki, Item spawn; each spawn's BaseRate is content's, default 100 ticks guessed |
| Timers run once a tick before players' input | guessed phase |
| A stackable drop joins the dropper's private stack on that tile, which keeps its timers | RSMod merges; keeping the timers is guessed (the count update carries no times) |
| Take is op3; the player walks onto the item and takes it the tick they arrive | the cache's default ground ops; tick from the RS2 world cycle, inferred |
| An item on a tile you can't stand on (a table) is taken from the tile beside it, north/south/east/west with no wall between, a tick later, with animation 832 | RSMod and inferred |
| Drop plays sound 2739, a take sound 2582 | RSMod's symbol names (put_down, pick2), not checked against the 241 cache |
| "I can't reach that!", "Too late - it's gone!", "You don't have enough inventory space to hold that item." | RSMod (rev 233), not checked against live |
| An item that vanished while you walked to it just ends the walk | guessed |
| Ground Examine shows the inventory examine (script 6003) | guessed |
| The obj times are in ticks and 0 means none | inferred from RSProt and RuneLite's TileItem |

Not built yet: the warning before dropping an item worth 30,000 coins or more (needs dialogue, task 18), the Wilderness rules,
items placed on tables, the 128-items-a-tile limit, and "Use" of an item on a ground item.

Content adds item spawns in tab-separated files under `src/main/resources/obj-spawns/`, one per area:

```
obj	count	x	z	level	respawn	# source
bronze_axe	1	3225	3218	0	100	# wiki item spawn list; respawn guessed
```

`obj` is a gameval name or id, `respawn` the BaseRate in ticks; the format is in `ObjSpawnFiles`. Content can also
handle a ground option with `Handlers.onGroundObj(id, option)`, which gets the obj as `Op.groundObj`; one on op3
replaces Take for that item.

Check with two clients near each other: `::item bronze axe`, then Drop it on the first. `::grounditems` there shows it
private, public in 100 ticks. The second client sees nothing until 60 s later, when the axe appears for it; take it
with the second client. RuneLite's Ground Items plugin with its despawn timer on shows the times counting down.

## NPCs

NPC types come from cache config 9 at startup (`NpcTypeLoader`), with gameval names from index 24 archive 1. NPC
spawns are not in the cache: they live in `src/main/resources/npc-spawns/`, one JSON file per area listed in
`NpcSpawnFile.FILES`, and every spawn names its source. Examine texts, like dialogue, are Jagex's words and live in the
dialogue file (see [Dialogue words](#dialogue-words)); `src/main/resources/examines.json` lists which NPC and loc types
have one, by type id. NPCs are sent through RSProt's NPC info; the tick moves every NPC (`World.npcs`, in
`game/npc/`) before any player's scripts.

`lumbridge.json` has the 21 cows and 8 calves of the Lumbridge cow field east of the river, on the wiki's tiles.
`lumbridge-castle.json` has 28 NPCs, Lumbridge Castle's and the three just outside its gate: Hans walking his lap round
the keep, the Cook, Duke Horacio, the tutors, the bankers, Perdu, men, women, an imp and ten rats (no combat yet, so they
can't be fought).

A spawn can walk a `patrol`, a list of tiles it walks to in turn, each `[x, z]` or `[x, z, wait]` with the ticks it
stands there; and `restrict` keeps it `indoors` (on tiles with a roof) or `outdoors` (on tiles without one), as the
2004Scape project's RS2 engine does with its NPCs' `moverestrict`. A tile counts as roofed when the map marks it so (tile
setting 4), which covers the castle's keep and towers.

| NPC behaviour | Source |
|---------------|--------|
| NPC type opcodes; all 16,631 rev 241 types decode | RuneLite's NpcLoader, checked against the 2026-10-14 cache |
| Spawn tiles and ids of Hans, cows and calves; where their examine texts are | OSRS Wiki pages Hans, Cow, Cow calf (2026-10-21) |
| Which cow or calf type stands on which wiki tile | guessed: the wiki maps give tiles per name, so the types take turns |
| Wander: pick a tile in the square of the wander radius around the spawn (even from outside it), walk there | OSRS Wiki, Wander radius |
| NPCs walk naively: diagonally, then straight | OSRS Wiki, Pathfinding |
| An idle NPC picks a new tile 1 tick in 8 | guessed, from the 2004Scape project's RS2 engine; not measured |
| Every wander range is 5 | guessed until recorded |
| A blocked diagonal tries the x step, then the z step; with neither it gives up on the tile | guessed |
| NPCs don't walk into each other; players don't block them | inferred / guessed |
| A 2x2 NPC moves as its four tiles would, so it can't straddle a fence | inferred from the one-tile rules |
| NPCs move before players' scripts each tick | inferred from Mod Ash's descriptions; not recorded |
| Examine answers with chat type 28 (NPC_EXAMINE) | RuneLite's ChatMessageType |
| Hans faces south when he spawns, as does every NPC without a direction | guessed |
| The castle NPCs' tiles | RSMod's Lumbridge spawns (rev 233) and the 2004Scape project's map, checked against the Wiki's maps |
| Hans's lap: clockwise round the keep inside the outer wall, waiting 10 ticks in the courtyard, 94 ticks a lap | the 2004Scape project's patrol for Hans; walked on our 241 map in a test; "about a minute" a lap on the Wiki |
| A patrol stop's wait: arrive on tick t, stand t+1 to t+wait, leave on the next | the 2004Scape project's engine |
| The Cook keeps under the roof; Hans keeps out from under it | the 2004Scape project's moverestrict; which tiles count as roofed is inferred from the map |

Check: in the cow field east of the River Lum (`::tele 3253 3275`) the cows and calves wander inside the fences,
`::npcs` lists them, and Examine on one gives its text from the dialogue file (its key, such as `[examine.npc_2790]`,
until the file has words for it). Hans walks his lap round the castle's keep (`::tele 3219
3226` is on it; he passes about once a minute), and the Cook stays under the castle's roof (`::tele 3209 3214`). Same seed, same
paths: start the server twice with the same `OSRSPS_SEED`, `::pause`, `::step` until `::tick` reads the same number
(500, say) and `::npcs` lists the same tiles both times.

## NPC interaction

Clicking an NPC option (Talk-to and op1 to op5) or using an inventory item on an NPC turns you to the NPC and walks you
beside it: along one of its sides, never a corner and never under it (a click from under an NPC steps you out first).
Its handler runs once you are there, from `World.handlers.onNpc(typeId, option) { op -> ... }` or, for items,
`World.handlers.onObjOnNpc(typeId, objId) { op -> ... }`, with `op.npc` the NPC (and `op.obj`, `op.sub` the item and its
slot). An NPC beside you is used on the tick the click is read; if it walks off you chase it, re-routing whenever it
moves. When the option runs, the NPC turns to you and stands still while you are busy with it (the handler waits on a
`delay`, or a dialogue is open), then goes back to wandering. A multi-NPC uses the type your vars show.

Walking, clicking something else or a teleport drops the interaction; the NPC going away ends it quietly. With no
handler you get "Nothing interesting happens.", and an NPC you can't get to says "I can't reach that!" once you stop.

`World.npcs.follow(npc, player)` has an NPC follow a player and stop beside them, naively, as NPCs walk; it faces them
while it follows (`::follow` tries it). Pets trail a tile behind instead, which isn't modelled yet.

| Behaviour | Source |
|-----------|--------|
| Reached from a side, not a corner or from under it; a wall on that side blocks it | the client's entity reach check as open-source route finders give it (rsmod); not checked on live |
| Option tried before and after the player's step; re-route each tick the NPC moved | 2004Scape's RS2 world cycle, as for locs; not checked on OSRS |
| The player turns to the NPC on the click; the NPC turns to the player when the option runs | live, from memory; that every option turns the NPC is guessed |
| Face-entity walk mode 0 (look where you walk, turn back on stopping) | RSProt's mapping of the old face-entity block; inferred |
| The NPC stands still while you are busy with it, then is let go a tick later | guessed |
| A following NPC steps out from under its target west, east, south, north; gives up beyond 32 tiles | guessed |
| An NPC can be clicked within 15 tiles | RSProt's NPC info range |
| The two messages | live, from memory |

Check: Talk-to Hans (`::tele 3222 3218`, he is in the courtyard). You walk to beside him and he turns to face you, then
his dialogue opens (see Dialogue below). Click Talk-to while he walks away and you follow him. `::npcs 5`, then `::follow` his index: he walks after you and stops beside you.

## Dialogue

Content shows chat dialogues from a handler with `World.dialogues.start(this, npc) { ... }`, whose block calls:

- `chatNpc(ChatAnim.NEUTRAL, "text")`: the NPC talks, its head on the left (chat_left, 231)
- `chatPlayer(ChatAnim.HAPPY, "text")`: you talk, your head on the right (chat_right, 217)
- `choice("Option one", "Option two", ...)`: "Select an option" with up to five options (chatmenu, 219); returns the one picked, from 1
- `mesbox("text")`, `objbox(obj, "text")`, `doubleobjbox(obj1, obj2, "text")`: messagebox (229) and the objectboxes (193, 11)

Each opens in the chatbox's chatmodal (162:568) and the script waits for "Click here to continue" or an option
(resume_pausebutton), then carries on at once; if it shows nothing more, the dialogue closes. While a dialogue is open
you count as busy, so the NPC you talk to stays turned to you. Walking, clicking anything else, Esc, a strong script,
`::tele`, ctrl+shift-click, `::scenario` or `::reset` closes the dialogue and drops the script. Text is wrapped with the cache's dialogue font (497) to count its
lines: that picks the chathead animation (`chatneu1` to `chatneu4` and so on) and the line spacing, and text past four
lines goes onto another page. Every dialogue page also goes to the chat history as a game message of type 114 (dialogue)
or 115 (message box), which RuneLite plugins see as `ChatMessageType.DIALOG` and `MESBOX`.

Content passes a line's key, not its words: `chatNpc(ChatAnim.NEUTRAL, HansLines.GREETING)`, `choice(IN_CHARGE, KILL, ...)`,
`chatNpc(anim, TIME_PLAYED, "days" to 2, ...)`. The words come from the dialogue file (below).

Hans (`game/content/Hans.kt`) is the example: Talk-to and Age, as the OSRS Wiki's transcript has them. Your time played
(game ticks logged in) and account age are saved, for his answer.

| Behaviour | Source |
|-----------|--------|
| Interface, component, varbit and seq ids | the 241 cache's gameval names, checked again on every start |
| Packets for each box and their order, line spacings, the chat history copy, closing once the script ends | RSMod's captures of live (rev 233); client script argument counts checked against the 241 cache; not compared with our own recording |
| Where lines break | inferred from the client's behaviour, with the cache's font widths |
| The script carries on in the input phase, as soon as the click arrives | 2004Scape's engine |
| `trigger_ondialogabort` when a dialogue is closed before it is answered | RSMod; inferred |
| Hans's conversation: its lines, options and order | OSRS Wiki, Transcript:Hans |
| Hans's expressions, Age giving the time-played lines alone, where he runs when threatened | guessed |

Lumbridge Castle's Cook (`Cook.kt`), Duke Horacio (`DukeHoracio.kt`), men and women (`Townsfolk.kt`), Perdu
(`Perdu.kt`), the Crafting tutor (`CraftingTutor.kt`), the bankers (`Bankers.kt`), and outside the gate the Doomsayer
(`Doomsayer.kt`), Hatius Cosaintus (`Hatius.kt`) and the Lumbridge Guide (`LumbridgeGuide.kt`) talk as they do once
every quest is done, since every account here has done them all. The Duke gives an anti-dragon shield to a player
carrying or wearing none, one talk with a man or woman in 128 hands over a flier, and the Guide gives a security book to
a player carrying none. Not built yet, so their options end the talk or do nothing: the bank, PIN settings, collection
box and bank space; Perdu's Lost Property Shop; the Doomsayer's warnings (Toggle warnings); achievement diaries; Attack
and Pickpocket; the Guide opening the account management tab's links, and his lost amulet, locket, clue and random
event talks.

| Castle NPC behaviour | Source |
|----------------------|--------|
| The Cook's and the Duke's conversations: lines, options, order, when the shield is offered | OSRS Wiki, Transcript:Cook (Lumbridge) and Transcript:Duke Horacio |
| The Cook's answer to "other quests" after Recipe for Disaster on a members' world (the free-to-play answer, with no line from the player) | guessed: the Wiki leaves it out |
| Men's and women's 23 conversations, the 1 in 128 flier | OSRS Wiki, Transcript:Man, Transcript:Woman, Flier |
| Perdu's, the Crafting tutor's, the banker's, the Banker tutor's, the Doomsayer's and Hatius's conversations | OSRS Wiki, their transcripts |
| Hatius leaving out "another ring" for a player with no diary done | guessed: the Wiki lists it with no condition |
| The Lumbridge Guide's conversation: lines, menus, where each option leads, the icon boxes | OSRS Wiki, Transcript:Lumbridge Guide; icon items are the 241 cache's `*_icon_dummy` objs |
| The levels the Guide's answers change at (combat 20, Smithing 20, Fishing 20); the quest box's icon; the money menu's smithing and monsters options skipping straight to those answers; "has a weapon" meaning worn or carried in the weapon slot | guessed: the Wiki has "X" and "same as above" |
| The Duke's first menu's order; skipping the "which dragon" menu once only one option is left; an item handed over landing at your feet with a full inventory; the other 22 townsfolk talks being equally likely; where the tutors' menus lead after a topic; every expression | guessed |

Check: Talk-to Hans. He greets you; continue, pick each option in turn (talk to him again for each), and walk away
mid-dialogue to see it close. Right-click him, Age, for your time played. With no words in the dialogue file, each line
shows its key, such as `[hans.greeting]`.

### Dialogue words

The Wiki's transcripts are Jagex's text, so the repo has each conversation's tree and a key for every line, and the words
are in `data/dialogue.txt` on the machine that runs the server, outside git. A line with no words there shows its key
in brackets, with any amounts after it (`[hans.time_played days=2 hours=3 minutes=7 days_ago=10]`), so every
conversation runs, and is tested, without them.

```
# Talk-to: Hans's first line
hans.greeting = <his words, from the Wiki>
```

One line per key, `key = words`, in UTF-8; blank lines and lines starting with `#` are notes. `{days}` and the like
are filled in by the server: the note above a line lists the ones it has. A key with nothing after `=` has no words yet;
words for it further down the file count too.

Each conversation declares its lines in a `Lines` object (`object HansLines : Lines("hans", "Transcript:Hans")`), with
where on the Wiki page each line is, and is listed in `DialogueLines.ALL`. Examine texts of NPCs and locs are in the same
file, `examine.npc_<id>` and `examine.loc_<id>` (the `Examines` object, from `src/main/resources/examines.json`), each
with its name and where on the Wiki its text is. To get a file with every key in it:

```bash
docker compose run --rm osrsps dialogue-keys   # on 214; or ./gradlew run --args=dialogue-keys
```

It writes `data/dialogue.txt` with each conversation's Wiki address and each key under where its words are, keeping
the words already there (the old file is kept as `dialogue.txt.bak`). Rerun it after an update that adds dialogue: new
keys go in their place, and words for keys the server no longer has move to the end. It stops without writing if a line
can't be read. Notes you add to the file aren't kept. The container writes the file as root, so on 214 run
`sudo chown sam data/dialogue.txt` once to edit it as yourself.

The server reads the file when it starts, and `::dialogue` reads it again while it runs. Both log how many lines have
words and anything wrong: a line that isn't `key = words`, a key the server doesn't have, or a `{placeholder}` the line
doesn't fill.

## Zone updates

The map is split into zones of 8x8 tiles per level, and everything other players must see happen on the ground goes
through `World.zones` (`game/zone/Zones.kt`) by zone: locs that differ from the cache, objs on the ground, spotanims on
tiles, projectiles, area sounds and loc animations.

Each player watches the 7x7 block of zones around their own, on their level, cut to their build area. When a zone comes
into view (login, walking 8 tiles, a level change, a map rebuild) it is wiped on the client (`UpdateZoneFullFollows`)
and its lasting changes are sent again. Each cycle, what happened in a watched zone goes out as one
`UpdateZonePartialEnclosed`, encoded once and shared by everyone watching. An obj only one player can see goes to that
player alone. Zone updates are sent on paused cycles too, so `::gfx` typed while paused shows straight away.

| Zone behaviour | Source |
|----------------|--------|
| 8x8 zones, the packets and their order after the infos | RSProt |
| 7x7 zones in view, cut to the build area | RSMod's reading of the client, not measured on live |
| A zone that just came into view gets no graphics or sounds from that cycle, only its lasting state | RSMod, not checked on live |
| Every zone in view is sent again after a map rebuild | guessed safe: the client may keep them, but resending is right either way |
| Private obj updates follow the shared ones in a cycle | guessed |
| Every right-click option shown on added objs and locs | guessed, until item and loc types |
| `::proj` flight values (heights, timing, arc) | guessed |

Check with two clients logged in near each other: `::gfx 85` on one shows the graphic on both (any spotanim id works;
85 is `failedspell_impact`, the magic splash, in the 241 cache's names). `::groundobj 1351` shows a bronze axe to both at once; walk the second client 40
tiles away and back, or log it out and in, and the axe is still there.

## Animations, spotanims and sounds

Content plays these on a player with `Player.anim`, `Player.spotanim`, `Player.sound` and `Player.jingle`
(`game/PlayerEffects.kt`). They are collected during the cycle and sent at its end: the animation and spotanims in the
player's player info, so everyone who can see them sees them, the sound effects and jingle to that player alone. Like
vars, they also go out on paused cycles, so `::anim` typed while paused plays straight away. Sounds others hear, and
graphics on a tile, go through `World.zones` (`::areasound`, `::gfx`). Delays are in client cycles (20 ms).

The commands only take ids the cache has: seqs from config 12, spotanims from config 13, sound effects (synths) from
index 4 and jingles from index 11 (`cache/EffectIdLoader.kt`). The 241 cache has 14,514 seqs, 4,023 spotanims, 12,163
synths and 315 jingles.

| Effects behaviour | Source |
|-------------------|--------|
| Animation and spotanim (with slot, height and delay) in player info, sound and jingle packets | RSProt |
| The last animation set in a cycle is the one sent; RS2 is thought to compare seq priorities instead | guessed, seq types are not decoded yet (comes with combat) |
| One spotanim per slot per cycle, every sound effect in order, the last jingle | guessed from the packets' shape |
| A jingle's length is sent as 0 | RSProt: the client no longer reads it |

Check: `::anim 866` and you dance; a second client nearby sees it too. `::sound` plays a bell. `::spotanim 85` shows the
splash on you and follows you as you walk, where `::gfx 85` stays on the tile.

## Locs and doors

Every loc the cache's maps place (about 4.9 million in 241) is loaded at startup with its type from config archive 6:
name, options, size, collision and the sides it can't be used from. `World.locs` (`game/loc/Locs.kt`) keeps what has
changed since: `add`, `remove`, `replace` and `restore` update collision and tell nearby players through the zones, and
take an optional number of ticks after which the tile goes back how it was.

Clicking a loc option walks you to the nearest tile that reaches the loc, then runs its handler, registered with
`World.handlers.onLoc(id, option) { op -> ... }`; `op.loc` is the loc clicked. A loc next to you is used on the tick
the click is read, one further away on the tick you arrive. With no handler you get "Nothing interesting happens.",
and if the route can't get there, "I can't reach that!" when you stop. Changing level from a ladder or staircase is a
handler that calls `World.teleport` with the new level, after playing the climb with `Player.anim` and a `delay`.

Single doors open and close without any content: a closed door type `x` with an Open option and a type `xopen` or
`x_open` with a Close option (Jagex's gameval names, also in the cache) pair up, which covers about 140 door types,
Lumbridge Castle's `poordoor` (1535), `elfdoor` (1543) and `poordoor_m` (56376) among them. A handler for a door id
takes over from this. Doors whose names don't pair (the castle kitchen door, 12348) and double doors are left to
content, which can pair a door with `World.doors.register(closed, open)`.

| Loc behaviour | Source |
|---------------|--------|
| Loc type fields, map loc file format, shapes and layers | the 241 cache, decoded with RuneLite's loader's opcode table; all 62,534 loc types decode |
| Which tiles reach a wall, wall decoration or ground loc, and turning the blocked sides with the loc | the client's reach checks as open-source route finders document them, not checked on live |
| An unreachable loc walks you to the nearest tile to its footprint (within 10 tiles) | the client's route finder fallback, as above |
| Used before moving and again after, in the movement phase | the 2004Scape reconstruction of the RS2 world cycle, not checked on OSRS |
| "Nothing interesting happens." and "I can't reach that!" | live messages, from memory |
| The player turns to face the loc when using it | guessed |
| A loc that changes before you get there ends the interaction; you finish the walk | guessed |
| Door pairs by gameval name | the 241 cache's own names |
| Where an opened door goes (a quarter turn clockwise, onto the next tile) | RS2 engine rule; 151 of the 191 open doors the 241 maps place line up with it, 19 with the opposite turn (mostly double-door leaves) |
| Doors stay open until closed, with no sound yet | guessed; the door sound ids aren't known yet (a recording will give them) |
| Examine shows the loc's text from the dialogue file, as type 29 (object examine) chat; a loc not in `examines.json` shows its name and id | live's examine texts are not in the cache |
| Timed loc changes run out at the start of the movement phase | guessed |

Check after a redeploy: `::tele 3222 3214` puts you in the castle courtyard, four tiles west of the door at 3226,
3214. Click Open on it: you walk up to it and it swings open onto the tile east of it. Walk through to 3228, 3214,
then Close it from there: it shuts, and clicking 3225, 3214 no longer takes you through it. `::locs 2` next to it lists
the door, back at 3226, 3214 and no longer marked changed.

## Other players

What one player sees of another goes through RSProt's player info: walking, running and teleports, appearance and worn
items, combat level, public chat, animations and spotanims. Ground items follow the visibility rules under Ground items.
Right-clicking another player offers Follow, Trade with and Report (`game/PlayerOps.kt`); only Follow works yet
(`game/Follow.kt`).

| Rule | Source |
|------|--------|
| Player options at login: slot 3 Follow, 4 Trade with, 8 Report, slots 2 and 5 cleared, none with priority over Walk here | RSMod's login script (rev 233), not checked against a live login |
| Follow walks to the tile the target was on before their last step (the tile between a run's two steps), as it stood when the tick's movement began, so the follower ends one tile behind | RSMod (rev 233) |
| A target who hasn't stepped since logging in or teleporting is followed to the tile west of them | RSMod |
| The follower gets a new route only once their current one is down to its last waypoint and the follow tile has moved, at the orb's speed (a ctrl-click only flips the walk to where the target was) | RSMod; keeping a route that still fits is ours |
| The follower takes a route to that tile, so a wall doesn't stop them; RSMod steps straight at it | ours, guessed |
| The follower faces the target (walk mode 0: they look where they walk and turn back when they stop) | RSProt docs and RSMod, inferred |
| Clicking an NPC while following turns the player to the NPC; clicking a player option while talking to an NPC lets the NPC go | ours, as for any other click |
| A follow stops when the follower clicks elsewhere or teleports, or the target logs out, changes level or gets more than 15 tiles away; the follower then stops where they are | the 15 is how far players see each other; when live gives up is guessed |
| Trade with and Report stop any walk or follow and say they aren't built yet | ours, not live |

Not built yet: trading, reporting, Attack (Wilderness and PvP worlds), and a player hiding another by standing on them
is the client's own business.

Check with two clients, logged in as two different names: walk, run (orb on), type in the chatbox, `::anim 866` and
`::spotanim 85` on one and watch them on the other. Right-click the second player on the first and Follow: the first walks
up behind them and keeps one tile behind as the second walks and runs, turning to face them whenever they stop.

## Chat

Anything typed without `::` is public chat: it shows over your head and in the chatbox of every player who can see you, yourself included. Colour and effect prefixes (`red:`, `wave:`, `pattern...`) work. Code is in `src/main/kotlin/osrsps/game/PublicChat.kt`.

| Rule | Source |
|------|--------|
| 80 characters at most; types 0 (normal) and 1 (autotyper) are public chat | RSProt / client behaviour |
| A colour, effect or pattern the client can't send falls back to plain yellow | guessed |
| Friends, clan and guest channel messages get "There are no chat-channels on this server yet." | ours, no channels yet |
| Public, private and trade filter changes are echoed back so the tab labels update; nothing is filtered on the server | inferred |
| "Welcome to Old School RuneScape." after login | from memory of live, not a recording |
| Varbit 8119 (`has_displayname_transmitter`) set to 1 at login, or the client says "You must set a name before you can chat" and blocks typing | var name from RuneLite's VarbitID; that the chatbox checks it is inferred |

## Saving and reconnect

Each player is saved to `data/saves/<name>.json` (on the VM, `/srv/rsps/OSRSPS/data/saves/`, which is not backed up).
Names ignore case and treat spaces as underscores, so `Test` and `test` share a save. A save holds position, run energy,
every varp that isn't 0 (the run orb and the resizable layout among them), each skill's XP and current level, and the
inventory's and equipment's slots; the bank joins it when it exists. Code is in `src/main/kotlin/osrsps/game/PlayerSave.kt` and `SaveStore.kt`.

| Rule | Source |
|------|--------|
| Saved on logout, when a lost connection gives up, every 3 minutes of real time (paused or not) and on shutdown (skipped if the game thread is stuck, leaving the last autosave) | ours |
| A save that can't be read refuses the login rather than starting afresh and overwriting it | ours |
| A dropped connection keeps the player in the world for 100 ticks (60 s) for the client to reconnect | wiki (Logout button) gives the 60 s for combat; outside combat is inferred |
| Logging in while that player is still in the world, waiting for a reconnect included, gets "already logged in" | inferred from live |
| A reconnect must send back the ISAAC seed of the connection it lost | RSProt / client protocol |
| After a reconnect every varp and stat is sent again and the map is rebuilt; interfaces are left as the client has them | inferred, not checked against live |
| All varps are saved; live keeps some only for the session, which ours doesn't know about yet | guessed |

### Accounts

The first login as a name claims it with the password typed; after that the name only logs in with that password, and
anything else gets the client's "Invalid username or password". Names match the way saves do, so `Sam` and `sam ` are
one account. This is our own rule for a shared test server, not live's: live accounts come from Jagex's login servers.
Launcher (token) logins are refused, as only Jagex can check a token.

Each account is `data/accounts/<name>.json` (on the VM, `/srv/rsps/OSRSPS/data/accounts/`), holding a salted
PBKDF2-HMAC-SHA256 hash (600,000 rounds), never the password. Checking takes a fraction of a second, so it runs on its
own login thread, not the game thread. A save with no account file (every save from before accounts) is claimed by its
next login. To reset a forgotten password, delete that name's file on the VM; the next login claims it again:

```bash
rm /srv/rsps/OSRSPS/data/accounts/<name>.json
```

## Scenarios

A scenario sets a player up for a test in one go: where they stand, their skills, inventory, equipment, run energy and
any varps or varbits. Scenarios are JSON files in `src/main/resources/scenarios/`, named after the scenario and listed in
`Scenarios.FILES`; the format is in `game/Scenario.kt`. They are testing tools, not live content.

| Scenario behaviour | Source |
|--------------------|--------|
| What a scenario leaves out is reset: unnamed skills are a new account's, run energy is full, slots it doesn't fill are empty | ours, so a scenario always starts the same |
| Varps and varbits it doesn't name, and the run orb unless it says, are kept | ours: they hold settings and quest states |
| Starting one, or `::reset`, closes interfaces and drops queued scripts, timers, delays and clicks held while paused | ours |
| Items are checked against the cache before anything changes; level requirements aren't checked | ours |

`::reset` puts back the player as they were before their first `::scenario`: a second scenario still resets to before
the first. The way back is kept in the save (`beforeScenario`), so a logout in the middle loses nothing. The server log
warns at startup about any scenario naming an item the cache lacks.

`lumbridge-cows` stands you in the Lumbridge cow field (3259, 3263, a tile picked from the cow spawns) with 20 Attack,
Strength, Defence and Hitpoints, iron armour, an iron scimitar, an amulet of strength, five lobsters, a bronze axe, a
tinderbox and 100 coins, with run on.

Check: `::scenario lumbridge-cows` puts you by the cows in iron with the loadout above; `::reset` puts you back where
you were with your own stats and items.

## Headless test client

`src/test/kotlin/osrsps/testclient/` holds a scripted game client for tests, so features can be checked without
RuneLite. It speaks the real protocol over a socket: it fetches the cache CRCs over JS5, logs in with the RSA and
XTEA login block, and decodes ISAAC opcodes. It reads player info (positions and moves of every player in view), NPC
info (each NPC's index, type and tile), game messages, interface opens, moves and closes, component text and client
scripts, and from those the chatbox dialogue showing at the end of each tick. It also keeps inventories, stats (level
and XP), varps and run energy as the server sends them, and from zone updates the ground objs and loc changes (an
opened door, say) in the build area. It sends walks, `::` commands, NPC and
loc ops 1-5, ground obj ops 1-5 (3 is Take), and dialogue continues and menu picks. Every other packet is kept raw. It only ever connects to our own
server.

`TestServer` starts our network stack and `World` on a free local port with no cache: an empty generated JS5 store and
open ground in Lumbridge's map square, with 100 ms cycles so tests run fast (the tick logic is the same as live's
600 ms). Pass a `world` builder to give it NPCs, locs, scenarios and so on.

```kotlin
TestServer(tempDir).use { server ->
    server.login("bob").use { client ->
        client.walk(3226, 3218)
        client.await("arriving") { client.position?.takeIf { it.x == 3226 } }
        client.commandReply("pos") { it.contains("(region") } // "3226, 3218, 0 (region 12850, ...)"

        // With a world builder that spawns Hans; see HeadlessInteractionTest.
        val hans = client.await("Hans") { client.npcs().firstOrNull { it.id == Hans.ID } }
        client.npcOp(hans.index, Hans.TALK_TO)
        client.awaitDialogue("the greeting") // Chat(231, "Hans", "[hans.greeting]") with no dialogue file
        client.continueDialogue()
        client.awaitDialogue("the menu") { it is DialogueView.Menu }
        client.chooseOption(3)

        client.command("item 995 100")
        client.await("coins") { client.inventory(93).firstOrNull() } // InvObj(995, 100)
        client.stat(3) // Stat(level = 10, xp = 1154, ...)
    }
}
```

Not read yet: extended info (appearance, chat, animations, NPC faces).
`RSProt` has only the server's side of each packet, so each one the client reads is written here from RSProt's encoder.

Check (bash or PowerShell): `./gradlew test --tests 'osrsps.testclient.*'` passes fourteen tests: login at the spawn
with the welcome message, a four-tile walk one tile a tick, a second player seen walking, a `::tele` that arrives as a
teleport, NPCs seen with their type and tile and one walking its patrol, a whole Hans dialogue through the menu, and
each of the five NPC and loc ops reaching its handler, a new account's stats and run energy, `::item` filling the
backpack (a 100000 coin stack too), `::setlevel` and `::varp` reaching the client, a ground stack seen by a later login and gone once taken, a
rebuild resending the zones still in view, and an opened door seen as moved, by a later login too.
