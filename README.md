# OSRSPS

A private Old School RuneScape test server for developing RuneLite plugins against 1:1 recreations of live content.
Our own engine in Kotlin on Java 21, using [RSProt](https://github.com/blurite/rsprot) for the network protocol.

Target revision: **241** (see `src/main/kotlin/osrsps/Revision.kt`).

## Never commit Jagex files

The game cache, the client, XTEA keys, RSA keys and gameplay recordings stay out of this repo.
They live under `data/` (git-ignored) on the machine that runs the server.

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

Launch RuneLite from RSProx with that target and log in with any username and password.

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

Type these in the chatbox; the answer shows as a game message. Every player can use them.

| Command | What it does |
|---------|--------------|
| `::pos` | Your coordinates as `x, z, level`, plus the map region and your tile inside it |
| `::tele x z [level]` | Teleports there; the level stays the same when left out. `::tele 3222, 3218, 0` also works, so `::pos` output pastes in |
| `::tick` | The server's current tick number, and whether the clock is paused |
| `::pause` | Freezes the game once the current tick has finished |
| `::step [n]` | While paused, runs n ticks (default 1, up to 1000), one every 600 ms, then says where it stopped |
| `::resume` | Starts the clock again |
| `::varp id [value]` | Shows a varp, or sets it. `::varp 173 1` turns the run orb on |
| `::varbit id [value]` | Shows a varbit with the varp and bits it lives in, or sets it |
| `::if id [side]` | Opens interface `id` bare in the main game area (or the side panel with `side`), for checking it opens, moves with a layout change and closes |
| `::setlevel skill\|all level` | Sets a skill's XP to what the level needs and its current level to match. `::setlevel attack 99` |
| `::addxp skill xp` | Adds XP, one decimal place allowed (`::addxp cooking 17.5`); levelling raises the current level too |
| `::boost skill level` | Sets only the current (boosted or drained) level, 0 to 255. `::boost hp 1` for 1 hitpoint |
| `::seed [n]` | Shows the random seed and how many numbers have been drawn since it was set, or restarts the rolls from seed n |
| `::gfx id [height] [delay]` | Plays spotanim `id` on your tile for everyone nearby. Delay is in client cycles (20 ms) |
| `::proj id [dx dz]` | Fires spotanim `id` as a projectile from your tile to the tile dx, dz away (default 5 tiles north) |
| `::groundobj id\|name [count]` | Puts an item on your tile for everyone at once, with no timers, until taken, `::cleartile` or a restart |
| `::objspawn id\|name [count [respawn]]` | Makes an item spawn on your tile that comes back `respawn` ticks (default 100) after it's taken, until a restart |
| `::grounditems` | Lists the objs on your tile: whose they are, ticks until public and until gone, and whether it's a spawn |
| `::cleartile` | Removes every obj from your tile; item spawns there come back as if taken |
| `::anim id [delay]` | Plays animation (seq) `id` on you, seen by everyone nearby; `-1` stops it. `::anim 866` dances |
| `::spotanim id [height] [delay] [slot]` | Plays spotanim `id` on you rather than on your tile; `-1` clears the slot (0 to 255) |
| `::sound [id] [loops] [delay]` | Plays sound effect `id` to you alone (default 3813, the town crier's bell) |
| `::areasound id [radius] [loops]` | Plays sound effect `id` from your tile, heard by everyone within `radius` tiles (default 5, up to 31) |
| `::jingle id` | Plays jingle `id` to you alone |
| `::item id\|name [count]` | Puts items in your inventory, by id, menu name or gameval name: `::item 1351`, `::item bronze axe 2`, `::item coins 10k`. Adds what fits |
| `::empty` | Empties your inventory |

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
as `Op.sub`. Inventories are not in the save file yet; a follow-up to task 8 adds them.

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
| A stackable drop joins the dropper's private stack on that tile and its timers start again | RSMod merges; restarting the timers is guessed |
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
inventory's slots; equipment and the bank join it when they exist. Code is in `src/main/kotlin/osrsps/game/PlayerSave.kt` and `SaveStore.kt`.

| Rule | Source |
|------|--------|
| Saved on logout, when a lost connection gives up, every 3 minutes of real time (paused or not) and on shutdown (skipped if the game thread is stuck, leaving the last autosave) | ours |
| A save that can't be read refuses the login rather than starting afresh and overwriting it | ours |
| A dropped connection keeps the player in the world for 100 ticks (60 s) for the client to reconnect | wiki (Logout button) gives the 60 s for combat; outside combat is inferred |
| Logging in while that player is still in the world, waiting for a reconnect included, gets "already logged in" | inferred from live |
| A reconnect must send back the ISAAC seed of the connection it lost | RSProt / client protocol |
| After a reconnect every varp and stat is sent again and the map is rebuilt; interfaces are left as the client has them | inferred, not checked against live |
| All varps are saved; live keeps some only for the session, which ours doesn't know about yet | guessed |
