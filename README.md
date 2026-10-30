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
./gradlew run --args="fetch-cache 241.4"   # only if 241.4 is OpenRS2's newest build; else exit 3, nothing changed
./gradlew run --args=cache-info    # summarise an existing download
```

`data/cache-source.txt` records which OpenRS2 cache was used. A fetch keeps the cache it replaces in `data/cache.previous`
(its source in `data/cache.previous-source.txt`). To roll back, stop the server and swap `data/cache` with
`data/cache.previous`, and `data/cache-source.txt` with `data/cache.previous-source.txt`, so the source line names the
cache that is running. The server won't start when that line names another revision than the one it plays
(`Revision.MAJOR`), as clients of one revision can't use the other's files. A cache with no source line isn't checked.

## Run the server

```bash
./gradlew run --args=serve          # JS5 and game on port 43594
./gradlew run --args="serve 50000"  # another port (launcher files on 50001)
```

RSProx assumes the game port is 43594. For another port, add `game_server_port: 50000` to the RSProx target below.

The login RSA key is `data/rsa.key`, never committed; each start writes its modulus to `data/rsa-modulus.txt`. The live
server's modulus is committed too, in `src/main/resources/rsa-modulus.txt`, because our launcher's releases carry it. So
`serve` won't start when `data/rsa.key` is missing or isn't that key: a new key would quietly lock every friend's
launcher out. Changing the key is a reviewed commit of that file. For a local server with a key of its own, set
`OSRSPS_LOCAL_KEY=1`; the first start then generates a 1024-bit key. Any username and password log in, and you appear
in Lumbridge.

## Keep it running with Docker

On the VM, Docker keeps the server up across crashes and reboots. `data/` is mounted into the container, so the
cache and RSA key are shared with the Gradle commands above.

```bash
docker compose up -d --build   # build and start; rerun after pulling changes
docker compose logs -f         # follow the log
docker compose down            # stop
```

After the one-time setup in [`deploy/`](deploy/README.md), the VM redeploys itself: a timer checks `main` every
minute and rebuilds when it moved, and `http://<public host>:43595/version` says which commit is up. A merge that only
changes docs, tests, the RuneLite plugins or the launcher doesn't restart the server; `/version` then says that commit
all the same. A branch can be tried before it merges on the staging server next to it: push it as the branch `staging`
(`git push origin my-branch:staging`) and within a minute it runs on the LAN-only ports 44594 and 44595 with a data
folder of its own (`deploy/staging.sh`).

## Connect RuneLite through RSProx

The server also hosts the launcher files on the HTTP port (game port + 1): `jav_config.ws` and `world_list.ws`,
both pointing at the host the client fetched them from (its HTTP Host header), or at `OSRSPS_PUBLIC_HOST` (default
`127.0.0.1`) when a request has no usable one. RSProx reads them to find the world and connects to that host on the game
port, so players on the LAN and players coming in through a VPN or relay at another address each get their own. The
relay must forward the game and HTTP ports unchanged (43594 and 43595 by default), and may put a PROXY protocol header
in front of each connection so the server knows the player's own address (see Real addresses and IP bans).

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
    runelite_bootstrap_commithash: 1e634e64717418713898b06061a189ec1cdf948d   # RuneLite 1.13.4, see below
    # game_server_port: 50000   # only when the server runs on a port other than 43594
```

Or let RSProx write it: the server also serves that target, modulus filled in, at
`http://<public host>:<HTTP port>/proxy-targets.yaml` (the HTTP port is the game port + 1, so 43595 by default). In RSProx press **+** next to the proxy target selector, choose
**From URL…**, paste that address, and restart RSProx.

Launch RuneLite from RSProx with that target. The first login as a name claims it with the password you type; after
that it needs the same password (see Accounts).

### Letting friends test

Friends reach the server at `odinsgate.net`, a VPS that relays TCP 43594 and 43595 (and nothing else) to it; the Home
Lab project runs the relay. They install Odin's Gate, our launcher, from the download page,
https://samsjosten.github.io/OSRSPS/ (`docs/` in this repo, served by GitHub Pages once it is switched on in the
repo's settings: Pages, deploy from a branch, `main`, `/docs`): one button per system, a guide for each, and the
first-start steps for an app no paid certificate signs. The launcher downloads RuneLite from RuneLite's own servers,
patches it in memory to our server and key, and updates itself from this repo's releases (`launcher/README.md`).
Each friend logs in with a name and password of their own, never any other game's login; the first login as a name
claims it (see Accounts). CI's `friend-page` job holds the page to its links (https, known hosts, every installer the
release publishes exactly once) and to the names rule (`.github/scripts/friend-page.js`).

- A friend who used RSProx before: the launcher keeps its own game settings, so RuneLite settings made under RSProx
  don't carry over; the name and password do. RSProx stays the developer route (above) and still works for friends on
  Windows or Linux: import `http://odinsgate.net:43595/proxy-targets.yaml` with **+** → **From URL…**, restart RSProx,
  pick **OSRSPS**. RSProx reads that address again each time it starts.
- Nothing of the game's is handed out: the launcher and RSProx both download RuneLite and the game client themselves.
- The launcher pins the RuneLite build per revision in `src/main/resources/runelite-pins.json` (a commit of RuneLite's
  [static.runelite.net history](https://github.com/runelite/static.runelite.net/commits/gh-pages/bootstrap.json); the
  signed manifest carries the table, and RSProx's target names the same build). RuneLite moves to each new revision
  within hours of live, and our server turns a client of another revision away, so a revision bump adds the new
  revision's build to that file first: the playbook is in `launcher/README.md`, "Revision bump".
- The first release is the 1.0.0 version bump of 2026-10-30; a launcher version is published by the merge that bumps
  `launcher/version.txt`, after Sam's approval of the release Environment (`launcher/README.md`, "Releases").

### Files for our own launcher

Odin's Gate, our launcher (being built to replace RSProx for friends; its code is `launcher/`, its own Gradle build
like the plugins'), reads its own files from the same HTTP port, each written for the host it was asked by like the
others:

- `/status.json`: whether the game is up (its game loop ended a cycle in the last 10 s) and taking logins, the revision
  it plays, the commit it runs (the one `/version` gives), how many players are in, and the JS5 count `/status` gives,
  e.g. `{"up":true,"revision":241,"commit":"b08d450","players":2,"acceptingLogins":true,"js5Connections":14}`. The
  launcher picks the RuneLite build from the revision.
- `/rl/worlds.js`: RuneLite's world list with our one world. The launcher points RuneLite's web API at `/rl/`, so the
  world hopper never offers one of Jagex's worlds; RuneLite's other API calls get a 404 there.
- `/launcher/jav_config.ws` and `/launcher/world_list.ws`: copies of RSProx's two files that leave the game's names out
  (title Odin's Gate, cache folder `odinsgate`, world activity Odin's Gate). RSProx's own files keep their names.

## Walking

Click the game view or minimap to walk. The run orb toggles running, ctrl-click inverts walk/run for one click, and
ctrl+shift-click teleports an admin to the clicked tile (a testing tool; anyone else just walks there, and an admin
isn't sent where the cache has no map). Collision is built from the cache's map files at
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

Type these in the chatbox; the answer shows as a game message. They are testing tools, so only the names in
`OSRSPS_ADMINS` (comma separated, matched like save names) can run them, except the ones marked **everyone**, which
only read the world or only show you something. Commands that touch only you (items, stats, vars, position, scenarios)
are admin ones too: in an ordinary player's hands they make items and skip the game's rules. Anyone else is told so,
and the list an unknown command prints leaves them out. A dead player can run only the **everyone** ones. With
`OSRSPS_ADMINS` unset, everyone is an admin, as before, and the server log warns. Names are claimed with a password at
first login (see Accounts), but a name in `OSRSPS_ADMINS` can't be: a stranger logging in first as an admin name nobody
has used would otherwise become an admin. So log in as a name first, then add it to the list.

| Command | What it does |
|---------|--------------|
| `::pos` | **everyone.** Your coordinates as `x, z, level`, plus the map region and your tile inside it |
| `::tele x z [level]` | Teleports there; the level stays the same when left out. `::tele 3222, 3218, 0` also works, so `::pos` output pastes in |
| `::tick` | **everyone.** The server's current tick number, and whether the clock is paused |
| `::pause` | Freezes the game once the current tick has finished |
| `::step [n]` | While paused, runs n ticks (default 1, up to 1000), one every 600 ms, then says where it stopped |
| `::resume` | Starts the clock again |
| `::varp id [value]` | Shows a varp, or sets it. `::varp 173 1` turns the run orb on |
| `::varbit id [value]` | Shows a varbit with the varp and bits it lives in, or sets it |
| `::dialogue` | Reads `data/dialogue.txt` again, so new words show from the next line said, and says how many lines have words and what is wrong with the file |
| `::ip [name]` | The address a player connects from (their own, behind the relay), or everyone's with no name |
| `::ipban name\|address [note]` | Bans that address from logging in, for good (see Real addresses and IP bans), and logs out anyone on it |
| `::ipunban address` | Lifts a ban |
| `::ipbans` | Lists the banned addresses and their notes |
| `::keys [on\|off]` | **everyone.** Your dialogue and examine lines show their keys (`[cook.greeting]`) instead of the file's words, until off or logout; no argument flips it. Test scripts in git turn it on, so they pass with or without words |
| `::if id [side]` | Opens interface `id` bare in the main game area (or the side panel with `side`), for checking it opens, moves with a layout change and closes |
| `::setlevel skill\|all level` | Sets a skill's XP to what the level needs and its current level to match. `::setlevel attack 99` |
| `::addxp skill xp` | Adds XP, one decimal place allowed (`::addxp cooking 17.5`); levelling raises the current level too. Exact: the XP rate doesn't apply |
| `::xprate [rate\|restore]` | Shows or sets the server-wide XP rate, 0.1 to 1000 in tenths (`::xprate 5`, `::xprate 2.5`); every XP drop is multiplied by it, rounded down to a tenth. Kept in data/xp-rate.txt (outside git), so it lasts across restarts and deploys. `restore` goes back to the rate before the last change. 1 is live's |
| `::boost skill level` | Sets only the current (boosted or drained) level, 0 to 255. `::boost hp 1` for 1 hitpoint |
| `::stats` | Lists the boosted and drained stats as current/base, and the ticks until each cycle next moves them a level back (boosts on the combat, other and Hitpoints cycles, drains on the restore cycle) |
| `::statcycles` | Starts the boost and drain cycles again, so the next levels move a minute from now; for tests that read boosts and mustn't have one wear off part way |
| `::friends` | **everyone.** Your friends list as the server keeps it, each friend's world or offline |
| `::clearfriends` | Empties your friends list, for tests that start from none (the Friends tab shows it after a relog) |
| `::poison [severity\|venom\|cure]` | Says what the poison varp (102) means now: poisoned, envenomed or immune, and the ticks to the next hit. A severity (20 for a poisoned weapon, 30 for p++) poisons you, `venom` envenoms you, and `cure` ends poison, venom and immunity |
| `::seed [n]` | Shows the random seed and how many numbers have been drawn since it was set, or restarts the rolls from seed n |
| `::gfx id [height] [delay]` | Plays spotanim `id` on your tile for everyone nearby. Delay is in client cycles (20 ms) |
| `::proj id [dx dz]` | Fires spotanim `id` as a projectile from your tile to the tile dx, dz away (default 5 tiles north) |
| `::los npcIndex` / `::los dx dz` | **everyone.** Distance, line of sight and hit delays from you to an NPC or a tile (see Line of sight) |
| `::groundobj id\|name [count]` | Puts an item on your tile for everyone at once, with no timers, until taken, `::cleartile` or a restart |
| `::objspawn id\|name [count [respawn]]` | Makes an item spawn on your tile that comes back `respawn` ticks (default 100) after it's taken, until a restart |
| `::grounditems` | Lists the objs on your tile: whose they are, ticks until public and until gone, and whether it's a spawn |
| `::cleartile` | Removes every obj from your tile; item spawns there come back as if taken |
| `::anim id [delay]` | Plays animation (seq) `id` on you, seen by everyone nearby; `-1` stops it. Delay is 0 to 255 client cycles. `::anim 866` dances |
| `::spotanim id [height] [delay] [slot]` | Plays spotanim `id` on you rather than on your tile; `-1` clears the slot (0 to 255) |
| `::sound [id] [loops] [delay]` | **everyone.** Plays sound effect `id` to you alone (default 3813, the town crier's bell) |
| `::areasound id [radius] [loops]` | Plays sound effect `id` from your tile, heard by everyone within `radius` tiles (default 5, up to 31) |
| `::jingle id` | **everyone.** Plays jingle `id` to you alone |
| `::item id\|name [count]` | Puts items in your inventory, by id, menu name or gameval name: `::item 1351`, `::item bronze axe 2`, `::item coins 10k`. Adds what fits |
| `::empty` | Empties your inventory |
| `::locs [radius]` | **everyone.** Lists the locs within radius tiles of you (default 1): id, name, tile, shape and angle, and whether they differ from the cache |
| `::addloc id [shape] [angle]` | Puts loc `id` on your tile (default shape 10, a ground loc, angle 0), replacing what was on its layer |
| `::delloc` | Removes every loc on your tile |
| `::resetlocs [radius]` | Puts the locs within radius tiles of you (default 0, your tile) back as the cache has them |
| `::npc id\|name [wander]` | Spawns an NPC on your tile, by id, menu name or gameval name: `::npc hans`, `::npc cow2_calf 3`. With a wander range it wanders that far from the tile, otherwise it stands still. Gone on restart |
| `::npcs [radius]` | **everyone.** Lists the NPCs within radius tiles (default 15) with their index, tile, spawn tile and wander range |
| `::delnpc index` | Removes NPC `index` (from `::npcs`) |
| `::follow index` | NPC `index` (from `::npcs`) follows you and stops beside you, as an NPC chasing its target does; again to stop |
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

A level gained through XP (not `::setlevel`) shows the level-up (`game/skill/LevelUps.kt`): a chat message, the
fireworks, the skill's jingle and the levelup_display box in the chatbox, then the same for the combat level when it went
up too. The words are in the dialogue file under `levelup.*`.

| Level-up behaviour | Source |
|--------------------|--------|
| levelup_display 233: text1, text2, continue 3, one hidden layer per skill (woodcutting 53, combat 55) | 241 cache via osrs-dumps |
| Fireworks: spotanim levelup_anim 199, levelup_99_anim 1388 at 99 | 241 gamevals |
| Jingles: each skill's "Level Up!", its "(Unlocks)" version (the next id) at levels in skill_features (db table 213) | OSRS Wiki jingle pages; ids for Prayer, Ranged, Runecraft, Slayer and Thieving inferred from the alphabetical run |
| Order: message, fireworks, jingle, box; combat level's box after the skill's | 2004Scape levelup script; combat order guessed |
| Queued as a normal script in the script phase after the XP; fireworks height 124; no Sailing jingle | guessed |
| The "Level N Skill: unlock" chat lines live adds | not sent yet |

Gathering actions share a skill clock (`Player.actionDelay`, `game/skill/SkillClock.kt`): a handler re-runs its own loc
option every tick with `World.repeatOp` (RS2's `p_oploc`) and rolls only on the ticks the clock comes due, so a new
click doesn't reset the cycle. Rolls use the Wiki's skilling success formula (`SuccessRoll`, a low and high value per
action out of 256), and `SkillTools` picks the best axe or pickaxe the player can use from the backpack or weapon slot.
The shared clock is the 2004Scape project's `%action_delay` model, inferred to hold on OSRS from tick manipulation.

### Woodcutting

Chop down on a tree, or an axe used on one, chops it (`game/skill/Woodcutting.kt`, data in
`src/main/resources/skills/woodcutting.json`): every regular, dead, evergreen, oak, willow, maple, yew and magic tree in
the world, with the bronze to crystal axes. Words are under `woodcutting.*` in the dialogue file.

| Woodcutting behaviour | Source |
|-----------------------|--------|
| Levels, XP, a roll every 4 ticks, respawn times, tree timers (oak 45 ticks, willow 50, maple 100, yew 190, magic 390) | OSRS Wiki tree pages |
| Each tree's success pair per axe tier, checked against the Wiki charts' points (`WoodcuttingDataTest`) | OSRS Wiki tree pages; crystal pairs picked inside the range the charts allow |
| Regular, dead and evergreen trees fall after one log and grow back in 60 to 100 ticks | OSRS Wiki |
| Best usable axe from the backpack or weapon slot picks the animation and pair | OSRS Wiki axe pages; animations from the 241 gamevals |
| Which stump each tree becomes | RSMod's table, matched by gameval name (not in the cache) |
| Swing message on the click, the animation again on each roll, the checks each tick | 2004Scape woodcut script |
| The timer runs down one a tick while anyone chops and back up one a tick while nobody does; the tree falls at the first log after it runs out | guessed (the Wiki gives only totals) |
| No chopping sound | the 241 synth ids have no names to find it by |
| A level-up box ends the action | 2004Scape engine's busy() check; guessed that live ends rather than waits |

Each log has a 1/256 chance of a bird nest (`game/skill/BirdNests.kt`, words under `birdnest.*`), 11/2560 with a
Woodcutting cape worn; `::nest [seeds|ring|red|green|blue]` drops one as a tree does. Search on a nest leaves an empty
nest in its slot and puts its seed, ring or egg in the backpack.

| Bird nest behaviour | Source |
|---------------------|--------|
| 1/256 a log, members only (every account counts as members), 10% more with the cape | OSRS Wiki, Bird nest |
| Seed nest 65 in 100, ring nest 32, each egg nest 1; the seed (out of 1,011) and ring (out of 100) tables | OSRS Wiki nest pages |
| Lands on a free tile next to the player, the west one twice as likely | guessed from the Wiki's "biased west" |
| The player's alone for 100 ticks, gone after 1,200 (four times a drop's 300) | guessed from the Wiki's June 2022 change |
| Searching needs a free slot, whatever is in the nest (so a full backpack can't reroll it) | guessed |
| Out: clue nests, Forestry, the strung rabbit foot, twitcher's gloves | out of scope |

### Firemaking

A tinderbox used on logs drops them and lights them on the player's tile; Light on logs on the ground lights them
there (`game/skill/Firemaking.kt`, words under `firemaking.*`). A lit fire is loc 26185 and leaves ashes when it goes
out. Ops on items on the ground can now re-run each tick too (`World.repeatOp`, `GroundObjs.repeat`).

| Firemaking behaviour | Source |
|----------------------|--------|
| Levels and XP for every log from normal to redwood | OSRS Wiki, Firemaking |
| 65/256 at level 1 to 513/256 at 99 (sure from 43), the 64/512 pair | OSRS Wiki (crowdsourced there), 2004Scape's script rolls the same |
| The player steps west, else east, south, north | OSRS Wiki |
| A strike, then a roll every 4 ticks; the next log can roll 4 ticks after a fire is lit | 2004Scape firemaking script |
| No fire on a tile with a loc on it or one that blocks walking | 2004Scape |
| A fire burns 100 to 199 ticks, then ashes for 100 ticks | 2004Scape; the Wiki says only "a short while" |
| The step off the fire is walked (2004Scape moves the player at once); fires allowed in banks | guessed |
| Out: bows as tinderboxes, coloured logs, Forester's campfires, a tinderbox used on logs on the ground | out of scope |

### Mining

Mine on a rock mines it with the best pickaxe the player can use (`game/skill/Mining.kt`, words under `mining.*`):
clay, copper, tin, iron, coal, mithril and adamantite rocks, with the bronze to crystal pickaxes. A mined rock turns into
an empty rock (`rocks1`/`rocks2`) until it respawns.

| Mining behaviour | Source |
|------------------|--------|
| Levels, XP, respawn times (clay 2 ticks, copper and tin 4, iron 9, coal 50, mithril 200, adamantite 400) | OSRS Wiki rock pages |
| Each rock's success pair, fitted to and checked against the Wiki's chart points (`MiningTest`) | OSRS Wiki rock pages |
| The pickaxe sets only the ticks between rolls (bronze 8 ... rune 3; dragon, infernal and 3rd age 3 with a 1/6 chance of 2, crystal 1/4) | OSRS Wiki, Pickaxe |
| The mining gem table, 1/256 a roll: a gem (or nothing) in place of ore, no XP, the rock stays | OSRS Wiki, Mining |
| The click starts the clock and the first roll comes a whole interval later; the swing message and animation | 2004Scape mining script |
| Which empty rock each rock becomes (rockN to rocksN); swinging on the click's own tick | guessed |
| Out: the glory's gem chance, mining gloves and other extra-ore items, the prospector kit, gem rocks, essence | out of scope |

### Fishing

Fishing spots are NPCs (`game/skill/Fishing.kt`, words under `fishing.*`): the Lumbridge Swamp's Net/Bait spots
(shrimps and anchovies with a small net, sardines and herring with a rod and bait) and the River Lum's Lure/Bait spots
(trout and salmon with a fly fishing rod and feathers, pike with a rod and bait). Their spawns are in
`npc-spawns/lumbridge.json`. The option re-runs every tick on the NPC (`World.repeatOp`, as RS2's `p_opnpc`).

| Fishing behaviour | Source |
|-------------------|--------|
| Levels, XP, tools and bait (one bait or feather a catch) | OSRS Wiki fish pages, Fishing |
| A roll every 5 ticks; pike recasts 1 roll in 5 (two casts a tick apart), the next roll a tick later | OSRS Wiki, Raw pike |
| Each fish's success pair, the better fish tried first, fitted to and checked against the Wiki's charts (`FishingTest`) | OSRS Wiki fish pages |
| Spots move every 250 to 530 ticks to a free tile of their area | OSRS Wiki, Fishing spot |
| The river spots' tiles; the cast message and animation on the click, the first roll 4 ticks later, the last bait freeing its slot | 2004Scape fishing scripts |
| Spawn tiles | RSMod rev 233 Lumbridge spawns |
| The swamp spots' area (their spawn tiles, so they stay put); the player stopping when the spot moves | guessed |
| Out: pearl and barbarian rods, stripy feathers, big nets, harpoons, cages, outfits and other catch boosts, whirlpools | out of scope |

### Cooking

Raw food used on a fire or range, or Cook on a range, cooks it (`game/skill/Cooking.kt`, words under `cooking.*`):
shrimps, anchovies, sardines, herring, trout, pike, salmon, beef, rat and bear meat, chicken, and bread dough (range
only). With more than one to cook, the chatbox's Make-X box (`skillmulti`, interface 270, set up by client script
2046) asks which and how many; a resume_pausebutton from one of its items starts the job. Lumbridge Castle's kitchen
range (114) burns less. The cooking re-runs every tick on the loc (`World.repeatOp`).

| Cooking behaviour | Source |
|-------------------|--------|
| Levels and XP | OSRS Wiki food pages |
| 4 ticks a piece | OSRS Wiki, Raw shrimps |
| Each food's burn pair on a fire or range and on the Lumbridge range, fitted to and checked against the Wiki's charts (`CookingTest`) | OSRS Wiki food pages |
| The Make-X box: its components, client script 2046's arguments and the pause button carrying the quantity | decompiled client scripts (osrs-dumps) |
| Cook with nothing raw saying Nothing interesting happens; one piece cooking with no box; the box closing as the first piece starts; the level-up box ending the job as for the other skills | guessed |
| Out: cooking gauntlets, the cooking cape, Hosidius ranges, pies, stews, pizzas, wines and every other recipe | out of scope |

### Smithing

Smelting at a furnace and smithing at an anvil (`game/skill/Smithing.kt`, words under `smithing.*`). Smelt, or ore used
on a furnace, opens the Make-X box (as cooking's) over the free-to-play bars. Smith, or a bar used on an anvil, opens
the anvil (`smithing`, interface 312): the client lays it out itself from varbit `smithing_bar_type`, so the server only
sets that, keeps the quantity buttons' choice in varp `makexcrafting`, and answers the item buttons. The anvil's items
(`AnvilItems.kt`) are the client's own `smithing_setup` list, with levels, bars and counts from the client's enums.

| Smithing behaviour | Source |
|--------------------|--------|
| Bar levels, ores and smelting XP; iron's 50% at a furnace, the ore lost | OSRS Wiki, Smelting and Iron bar |
| Smelting 4 ticks then 5 a bar, a single bar 6 | OSRS Wiki, Bronze bar |
| 5 ticks an item on the anvil; a hammer needed; 12.5 XP a bar a tier | OSRS Wiki, Smithing |
| Lumbridge's rusted anvil smiths only bronze | OSRS Wiki, Anvil |
| Each bar's anvil items, levels, bars and counts | client scripts and enums 844 to 846 (cache 241), checked against the Wiki's bronze and iron tables (`SmithingTest`) |
| The furnace box showing every free-to-play bar and suggesting All; Smith opening the best bar the level can use; a cancelled X smithing one; the rusted anvil's message for other bars; the messages' arguments | guessed |
| Out: the ring of forging, goldsmith gauntlets, the imcando hammer, smiths' uniform, Blast Furnace, cannonballs, jewellery, blurite and lovakite | out of scope |

### Fletching

A knife used on logs, or logs on a knife (`game/skill/Fletching.kt`, words under `fletching.*`), opens the Make-X box
over what those logs make: arrow shafts, javelin shafts, the shortbow and longbow (u), and the crossbow stock, from
logs to magic logs, plus teak and mahogany stocks and redwood shafts. A bow string on an unstrung bow strings it. Feathers on arrow shafts
make headless arrows, and arrowtips on those make arrows, up to 15 at a time (the box counts sets). A feather on dart
tips makes 10 darts on every click, with no box. The numbers are in `skills/fletching.json`.

The making itself is the loop all three artisan skills share (`game/skill/Making.kt`, the box's title under
`make.title`): each product waits in a weak queue, so walking off, closing an interface or using something else ends
it, as do running out, the count being done, the level being too low and a level-up.

| Fletching behaviour | Source |
|---------------------|--------|
| Levels, XP and how many shafts a log makes | OSRS Wiki, Fletching and each product's page |
| The first product 2 ticks after the choice, then one every 3 | OSRS Wiki, Arrow shaft ("Make-X is 2, then 3 ticks") and Wooden stock; the bows (u) inferred to match |
| Stringing 2 ticks a bow; arrows 2 ticks a set of 15 with XP per arrow; darts 10 a click with no wait | OSRS Wiki, Shortbow, Headless arrow, Bronze arrow, Arrows, Dart and Bronze dart |
| Animations `human_fletching` (1248) and each bow's `stringing_*` (6678 to 6689) | cache 241 gamevals |
| No animation for arrows and darts; the first bow strung 2 ticks after the choice | guessed |
| The box opening even for one log, its order, the quantity starting on as many as can be made, the animation as each product starts, a boost wearing off stopping it without a word | guessed |
| Out: the redwood shield; the crossbows and javelins that stocks and javelin shafts go into | out of scope |

### Crafting

Spin on a spinning wheel (every loc the cache calls one, Lumbridge Castle's first-floor wheel among them) opens the
Make-X box over the wool and flax held; either used on a wheel opens it over that alone (`game/skill/Crafting.kt`,
words under `crafting.*`, numbers in `skills/crafting.json`). The making is the artisan loop in `Making.kt`.

| Crafting behaviour | Source |
|--------------------|--------|
| Ball of wool level 1, 2.5 XP; bow string level 10, 15 XP; 3 ticks each | OSRS Wiki, Ball of wool and Bow string |
| The first 2 ticks after the choice | inferred from fletching's "Make-X is 2, then 3 ticks" |
| Animation 466 (`spinningwheel`) as the player's | inferred from its gameval name |
| Spin with nothing saying Nothing interesting happens; the box opening even for one | guessed |
| Out: sinew, magic roots, yak hair and the other members' spinning | out of scope |

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
| An option or "Use" nothing handles says live's "nothing interesting" message (`game.nothing_interesting`) | from memory of live |
| Bury on bones: a message (`bones.dig`), seq 827, the player stops; a tick later the bones go, the Prayer XP comes and a second message (`bones.buried`) (bones, burnt bones 4.5 XP, big bones 15, dragon bones 72) | XP from the Wiki; the steps from the 2004Scape project's script; no sound yet (its 241 synth id isn't known) |
| Stacks stop at 2,147,483,647; `::item` adds what fits | client limit; adding part is ours |

Check: `::item 1351` puts a bronze axe in your inventory; drag it to another slot; Examine it. `::item bones 2`, then Bury both: Prayer XP goes to 9
(`test-runner/examples/lumbridge-bury-bones.json` does it in the real client).

## Bank

Every loc with a Bank option (the castle's booths: op2), and the bankers' Bank (op3) or asking them for the bank in
their talk, open the bank: bankmain (12) over the game view and bankside (15) beside it, as RSMod opens them. The bank
is inv 95, 1,410 slots, of which a members' account holds 900 (`Player.bank`, saved with the player); it is sent only
while open. The rules are in `game/bank/Bank.kt`, the interface in `game/bank/BankScreen.kt`. The menus are the
client's own scripts', built from the varbits the buttons set.

| Bank behaviour | Source |
|----------------|--------|
| Withdraw-1, -5, -10, -X, -All, -All-but-1 and the requested amount (ops 1 to 8, the left-click one by the quantity buttons); Examine is op 11 | 241 client scripts (script669, script2579) |
| Deposit-1, -5, -10, -X, -All (ops 2 to 8) on the inventory beside it; Examine op 10. A deposit takes from the item's other slots too | 241 client script bankside_drawitem; RSMod |
| -X asks "Enter amount:" (client script meslayer_mode7); the amount becomes the requested quantity the menus offer next. Walking or closing the bank closes the prompt | client scripts; closing the prompt is guessed |
| Everything stacks; notes go in as their item; Note withdraws a noted stack, or the item itself with a message (`bank.cannot_note`) | RSMod |
| Tabs 1 to 9 come first, then the main tab; their sizes are varbits bank_tab_1 to 9, and bank_currenttab is the tab viewed (0 shows all) | 241 client scripts (bank_gettabrange, bankmain_build) |
| The bank has no gaps: every change closes them, and a tab that empties goes, the later ones moving down; the tab viewed follows its tab | RSMod; RSMod's main tab can keep a gap after a drag, ours can't |
| A new item goes at the end of the tab viewed (the main tab when viewing all) | RSMod |
| Drag an item onto a tab, onto the space after a tab's last item, or onto New tab (the icon after the last tab); an insert into another tab goes in front of the item dropped on | 241 client scripts (bankmain_tabicon, script509); RSMod |
| Tab ops: View tab, Collapse tab (its items go to the end of the main tab), Remove placeholders (that tab's only, the main tab's too); drag a tab onto another to move it; a click on New tab says how to make one (`bank.new_tab`) | 241 client scripts; RSMod |
| Search filters in the client alone (meslayer mode 11); the server views all items and closes the search line with the bank | 241 client scripts; RSMod |
| Settings menu: the toggles, tab display, Release all placeholders and the fillers (1, 10, 50, X, All, then Fill), each "are you sure" in popupoverlay. Fillers fill free space at the end of the main tab; Clear and Clear-All take them | 241 client scripts (script1428, enum_1907, script4212); RSMod's messages |
| The incinerator burns the item it is given | RSMod |
| Placeholders: the Placeholder option, or Always set placeholders, leave one; a deposit fills it; Release takes it away | 241 client scripts; RSMod |
| Swap or Insert for drags; a drag past the end moves the item to the end. Drags in the inventory beside it swap | 241 client script bankmain_reorder; RSMod |
| Deposit inventory and Deposit worn items, a message (`bank.nothing_to_deposit`) when empty | RSMod |
| Messages and the confirm boxes' words: keys under `bank.` (`BankLines`), with the words in `data/dialogue.txt` on 214, outside git | RSMod, from live |
| Opening the bank always starts on withdrawing items, not notes | RSMod; not checked |
| A member's bank holds 900 | OSRS Wiki, Bank (2026-10-29) |
| Worn items view: the client switches to it itself; the server fills in the bonus lines ("Melee STR", "Magic DMG", "Undead: +0% ") and answers the worn slots (Remove, Bank, Examine) and the inventory beside it (Wear, Deposit, Examine) | 241 client scripts (bankmain_viewbuttons, wear_updateslot, bankside_worn_drawitem); the labels RSMod's |
| The inventory's extra option (op 9) wears what can be worn: varp if2 has a bit per slot; the settings menu's toggle hides it | 241 client script bankside_extraop; RSMod |
| Deposit boxes (Deposit on every Bank deposit box, the castle's top floor one too; Deposit-box on Deadman booths): bank_depositbox (192), op 1 the amount its buttons pick (1, 5, 10, X, All), ops 2 to 6 Deposit-1 to -All, op 9 Wear, the worn slots as above, Deposit inventory and Deposit worn items | 241 client scripts (bank_depositbox_drawslot, storage_create_quantitybuttons) |
| An item used on a deposit box goes in; with more than one, it asks for an amount | OSRS Wiki, Bank deposit box; asking with the amount prompt is guessed |
| A banker's PIN settings opens Bank PIN Settings (14) with no PIN: "No PIN set", "7 days", "Stay unlocked"; its buttons say "Bank PINs aren't available on this world." | the screen from the 241 cache; its words guessed; PINs not built |

Not built at all yet: the inventory's Eat and Drink in the bank (once there is food), potion storage, deposit locks,
containers, bank tags' storage, PINs themselves, Collect and buying bank space.

Check: `::item logs 5`, Bank at a castle booth, Deposit-All, Withdraw-X 2, then Note and Withdraw-All: two logs and
three noted logs. Log out and in: what you banked is still there (`test-runner/examples/lumbridge-bank.json` does most
of it in the real client; it can't type an amount, so -X is left to the headless test). For tabs, `::scenario
lumbridge-bank-tabs` starts at the booths with logs and an axe in tab 1: Placeholder on the logs, deposit them back
and they are in slot 0 again; Collapse tab puts them after the coins (`test-runner/examples/lumbridge-bank-tabs.json`,
which also fills and clears fillers; the runner can't drag yet, so dragging into tabs is left to the headless test).
At the deposit box on the castle's top floor, `::item logs 5` and `::item bronze axe`: Deposit-1, then the 5 button and
a click take the rest; Wield the axe from the box and Bank it from its worn slot. In the bank, Show worn items lists
the bonuses (`test-runner/examples/lumbridge-deposit-box.json`, with the banker's PIN settings).

## Shops

Trade on a shop keeper opens their shop: shopmain (300) over the game view and shopside (301) beside it. The shops,
their prices and stock are in `src/main/resources/shops.json` (the OSRS Wiki's shop pages), the stock and prices in
`game/shop/Shop.kt`, the interface in `game/shop/ShopScreen.kt`. Each shop's stock is the same for every player.

| Shop behaviour | Source |
|----------------|--------|
| Lumbridge General Store (the Shop keeper and Shop assistant, 130% / 40% / 3%, 15 items) and Bob's Brilliant Axes (Bob, 100% / 60% / 2%, 7 items) | OSRS Wiki, read 2026-10-29 |
| The client draws the shop (shop_main_init, script 1074): Buy 1, 5, 10 and 50 as ops 2 to 5, Examine op 10; op 1 is Value, or what the Quantity buttons pick (varbit shop_quantity), op 6 then Value | 241 client scripts (shop_main_update, shop_quantity) |
| The inventory beside it: Value, Sell 1, 5, 10 and 50 (interface_inv_init, script 149) | RSMod; that it doesn't follow the Quantity buttons is guessed |
| A price is the shop's percentage of the item's value, less its change for each item over its starting stock (more for each under), held between -500% and +100%, never under 10%; buying costs at least 1 coin. Each item of a Buy 5 or Sell 5 is priced at the stock it meets | 2004Scape's calc_shop_value; every price on the Wiki's two pages and its Bob example agree |
| A general store buys anything tradeable, into the first free slot; a specialist shop only what it stocks. Coins and untradeable items can't be sold; notes sell as their item | 2004Scape |
| Each stocked item comes back (or goes down when overstocked) by one every its restock time, as the Wiki gives it; an item the shop doesn't stock goes down by one every 100 ticks and its slot goes at 0, the later items moving up. A sold-out stocked item stays at 0 | the Wiki's restock times; the rest guessed (RSMod's 100 ticks) |
| Messages: keys under `shop.` (`ShopLines`): the value checks (a price of 1 coin has its own line), out of stock, not enough coins or space, the shop full, and the three refusals to buy; words in `data/dialogue.txt` on 214 | 2004Scape (RS2), "coins" for its "gp" as RSMod has it; not checked against live |

Not built yet: Bob's Repair (he says there is nothing to repair, as there is no degrading equipment yet), ironman
stock, noted buying (Leagues only) and every other shop.

Check: `::item coins 100` in the Lumbridge General Store, Trade with the Shop keeper, Buy 1 on the pot: 99 coins. Sell
it back for nothing (40% of 1). The Quantity 5 button makes a click buy five. At Bob's, a pot can't be sold and a
bronze axe can (`test-runner/examples/lumbridge-shops.json`).

## Lumbridge town centre

The town west of the River Lum, outside the castle: `lumbridge.json` has its NPCs on RSMod's spawn tiles (rev 233),
and the conversations are in `game/content/` (`LumbridgeShopkeepers.kt`, `ShearedRam.kt`, `DonieAndGee.kt`,
`LumbridgeTutors.kt`, `LumbridgeTownsfolk.kt`), each from the OSRS Wiki's transcript as a player with every quest done
sees it. The words are in the dialogue file, like every other line.

| Who | What they do |
|-----|--------------|
| The General Store's Shop keeper and Shop assistant, Bob | Talk-to; "what do you sell" opens their shop. Bob's Repair and an item used on him answer that nothing needs repairing |
| The Sheared Ram's Bartender | Sells a beer for 2 coins |
| Arthur the Clue Hunter | Explains every clue type (three pages of them) and turns clue vessels on and off (varbit 9365) |
| Donie and Gee | One of four opening menus at random, the quests, Lumbridge, and Gee's small monsters |
| The Cooking tutor, Smithing apprentice and Woodsman tutor | Advice by your level; the Woodsman tutor gives an axe and tinderbox when you have none and sells the Woodcutting cape at 99 (trimmed with another 99) |
| Abigaila, upstairs in the General Store | Her story; her scrying ball refuses a player carrying or wearing anything (the Theatre of Blood isn't built) |
| The Ironman tutor | Explains Ironman; this game has no Ironmen, so his armour and the account modes are refused or end the talk |
| Men, women and rats | As in the castle |

| Behaviour | Source |
|-----------|--------|
| Every line, option and its order, and which ones the levels pick | OSRS Wiki transcripts, read 2026-10-29 |
| The NPCs' tiles | RSMod's Lumbridge spawns (rev 233) |
| The menus' paging, Donie and Gee's four openings picked at random, the tutors' level bands | RSMod's scripts for these NPCs |
| Every chathead expression the Wiki doesn't give, that "what do you sell" opens the shop after the last line | guessed |

Not built yet: Veos and Barfy Bill (quest and travel NPCs), the overhead lines some of them say, the Theatre of Blood
and account modes, Bob's Repair.

Check: `test-runner/examples/lumbridge-talk-*.json` talk to each of them through every option with `::keys on`, so
the dialogue shows each line's key.

## Fred's farm

North-west of the castle, in `npc-spawns/lumbridge-farms.json` on RSMod's tiles: Fred the Farmer, his sheep pen's
sheep, rams and lambs, and the 33 chickens of the Wiki's Lumbridge West Farm. The code is `game/content/FredsFarm.kt`;
chickens and rams fight and drop through `npc-combat.json` and `npc-drops.json`.

| Behaviour | Source |
|-----------|--------|
| Fred's talk after Sheep Shearer: what you're doing on his land, then something to kill or being lost | OSRS Wiki, Transcript:Fred the Farmer, read 2026-10-29 |
| Shear (or shears used on a sheep) needs shears, plays human_shearing, and after two ticks gives a wool (dropped at your feet when the backpack is full); the sheep shows as its sheared type for 50 ticks, then grows its wool back. It never fails | RSMod's sheep script; the Wiki's Sheep page (50 ticks, no failure since 27 October 2019) |
| A sheep shows as its sheared type through the NPC info's transformation block, for every player; its options and examine are the sheared type's meanwhile (`Npcs.transform`) | RSMod's npcChangeType |
| Chickens: level 1, 3 hitpoints, stab for 0, back in 25 ticks; always bones and raw chicken, 5 feathers 64/128, 15 feathers 32/128, a beginner clue 1/300 | OSRS Wiki, Chicken |
| Rams: level 2, 8 hitpoints, crush for 1, back in 45 ticks, weak to fire (20%); always bones | OSRS Wiki, Ram |
| Pick on an onion takes it for 50 ticks and gives an onion, or says there's no room (`Crops.kt`) | the 2004Scape project's pickables script; the Wiki's Onion page (fields respawn in 30 seconds) |
| Messages: keys under `sheep.` (`SheepLines`: no shears, wool got, already shorn) and `crop.` (`CropLines`: picked, no room, for each crop); words in `data/dialogue.txt` on 214 | RSMod and the 2004Scape project, not checked against live |
| Fred's expressions, the chickens' and rams' fight animations (by name), every wander range (5) | guessed |

Not built yet: the onion seed members sometimes get from a field, the sheep's overhead bleat, The Thing and the penguin of Cold
War (and Fred's option about The Thing), and the farmers. The ducks are in their own section below.

Check: `test-runner/examples/lumbridge-freds-farm.json` talks to Fred, picks an onion, shears a sheep and kills
chickens for their feathers.

## Groats' farm and Mill Lane Mill

Gillie Groats and her pen's two dairy cows north of Lumbridge (`game/content/GroatsFarm.kt`), and Mill Lane Mill with
Millie Miller and its wheat field north-west of Fred's farm (`game/content/MillLaneMill.kt`). Gillie and Millie are in
`npc-spawns/lumbridge-farms.json` on RSMod's tiles; the cows, hopper, controls, bin, ladders and crops are the 241
map's locs. Wheat, potatoes and onions are picked through `game/content/Crops.kt`.

| Behaviour | Source |
|-----------|--------|
| Gillie's talk: who she is, how to milk a cow, or nothing; with no bucket, a dairy cow in her pen gets her talk under the name "Gillie Groats the Milkmaid" | OSRS Wiki, Transcript:Gillie Groats, read 2026-10-29 |
| Milk (or a bucket used on a dairy cow): two ticks, then a bucket of milk, milkit and a message (`dairy_cow.milked`), every eight ticks while buckets are left, until the player does anything else; anything else used on one: `dairy_cow.not_wanted` | RSMod's dairy cow script |
| Millie's talk: who she is, what the mill is, how to mill flour (three ways in), or nothing | OSRS Wiki, Transcript:Millie Miller, read 2026-10-29 |
| Pick on wheat gives a grain and takes it for 20 ticks; on a potato, a potato for 50 | OSRS Wiki, Wheat (20 ticks); the 2004Scape project's pickables script and RSMod (potato 50, and every message) |
| Fill on the hopper (or grain used on it) puts one grain in; Operate on the controls sends it to the flour bin; the bin holds 30 pots; Empty on the bin (or a pot used on it) fills a pot; the messages are keys under `mill.` (`MillLines`) | OSRS Wiki, Windmill and Transcript:Hopper; the 2004Scape project's windmill script |
| The bin's flour is the player's own, shared by every windmill: varbit mill_flour (4920), and mill_showflour (5325) shows the bin with flour in it | the 241 cache's flour bin (multivar mill_showflour) and varbits; OSRS Wiki, Windmill |
| The ladders: Climb-up, a Climb menu on the middle floor, Climb-down | the castle's ladders (RSMod) |
| The grain in the hopper is kept with the account by name, as charges are, since no var for it is known; Gillie's and Millie's expressions; human_pickuptable for filling and operating | guessed |

Not built yet: sweetcorn and cornflour (Farming), Steal-cowbell and the cowbell amulet
(Thieving, Cold War), and the seeds a field sometimes gives.

Check: `test-runner/examples/lumbridge-mill.json` talks to Millie and Gillie, picks wheat, climbs the mill, makes two
pots of flour and milks a dairy cow into two buckets.

## Ducks

The ducks of the River Lum and the pond by Fred's farm, and those that walk on land beside the pond south of Mill
Lane Mill and on Groats' farm, in `npc-spawns/lumbridge-ducks.json` on RSMod's tiles (each checked against the 241
map: the swimmers on water, the walkers on land). What they do is `game/content/Ducks.kt`, through the NPC scripts of
`game/npc/NpcScripts.kt` (timers, per-tick actions, deaths and queued actions for NPC types). They fight through
`npc-combat.json`.

| Behaviour | Source |
|-----------|--------|
| Drakes (1838) and ducks (1839) swim, keeping to the water: move restrict `blocked` steps only onto tiles whose floor can't be walked on, with walls and locs still in the way; wander range 35 | RSMod's duck config and its route finder's blocked collision strategy |
| A duck quacks over its head (`duck.quack`) with the quack sound (synth 413) 26 ticks after it appears, then every 50 to 100 ticks | RSMod's duck script |
| Ducklings (2002, and the attackable duckling 2001 at the pond) look every 11 ticks for a female duck within 2 tiles in sight and follow her; every 50 to 100 ticks a female within 10 tiles calls (`duck.call`) and the duckling answers (`duck.eep`) 2 ticks later | RSMod's duck script and hunt; the Wiki's Duckling and Ducklings pages (2001 follows female ducks too) |
| When a duck is killed, the ducklings within a tile cry (`duck.cheep`) 2 ticks later and swim up to 9 tiles off, then call (`duck.eep`); a drake beside her quacks | RSMod's duck script; the Wiki's Duck page |
| Ducks and ducklings: level 1, 3 hitpoints, stab for 0, back in 25 ticks, no drops; on the water they can't be reached to melee | OSRS Wiki, Duck and Duckling |
| Fired ammunition that lands on water is lost | OSRS Wiki, Duck ("Ammunition is lost if ducks are not on land"), taken to hold on any floor that can't be walked on |
| The walking duck (2003) and drake (2004) have no options and run from a player who comes close | OSRS Wiki, Duck (Common) and Drake (bird) |
| Which of the 11 ticks a duckling looks on, that a scattering duckling picks a water tile, that the 2001 duckling acts as 2002 does, the walkers' range (5) and how close a player comes (2 tiles) before they run 4 tiles off; ducks have no attack or block animation in the 241 cache, so they fight with none | guessed |

What they say is in the dialogue file (keys `duck.quack`, `duck.call`, `duck.eep`, `duck.cheep`).

Check: `test-runner/examples/lumbridge-ducks.json` examines each kind of duck, tries to reach a drake on the river and
watches a duck's death animation. The runner has no step that reads overhead text, so the quacks, the ducklings'
following and the walking ducks running off are checked through the headless client (`HeadlessDucksTest`, which reads
NPC info's say block), with the death cries and keeping to water in `DucksTest` and the water and arrows in
`HeadlessRangedTest`.

## East of the river

East of the River Lum, between the castle bridge and the cow field, in `npc-spawns/lumbridge-east.json` on RSMod's
tiles: the goblins of Lozar's ruined house and the land around it, the giant spiders among them, and Barfy Bill at the
canoe station. They fight through `npc-combat.json` and drop through `npc-drops.json`; Bill's talk is
`game/content/BarfyBill.kt`.

| Behaviour | Source |
|-----------|--------|
| 50 goblins (`goblin_unarmed_melee_1` to `_8`, ids 3029 to 3036): level 2, 5 hitpoints, crush for 1 at speed 4, back in 35 ticks, not aggressive | OSRS Wiki, Goblin (the Level 2 infobox); the DPS calculator |
| A goblin always drops bones, and rolls drop table 1 (128 slots: bronze gear, runes, coins, a hammer, goblin mail and the rest), with the beginner and easy clues, the ensouled goblin head and the goblin champion scroll on their own rolls | OSRS Wiki, Goblin, drop table 1 |
| 6 giant spiders (`giantspider1`, 3017): level 2, 5 hitpoints, stab for 1, back in 30 ticks, 50% weak to fire; no bones, only the beginner clue at 1/128 | OSRS Wiki, Giant spider (Level 2) |
| Barfy Bill (1326) talks about who he is and about canoes, by the player's Woodcutting level: below 12, 12 to 26 (log), 27 to 41 (dugout), 42 to 56 (stable dugout), 57 and up (waka) | RSMod's script; the Wiki's Transcript:Barfy Bill and Canoeing |
| The fight animations (the squatting goblin's and the updated spider's, by the stand and walk animations their types have), the wander ranges (5, Bill 2) and Bill's chathead expressions | guessed |

Left out: the giant spiders are aggressive on live, which waits for NPC aggression (the Varrock piece); canoes and
Bill's Store-axe wait for canoeing. The river's fishing spots are the gathering skills'. What Bill says, and the
examines of Bill, the goblins (one for all eight types) and the spider, are in the dialogue file (keys `barfy_bill.*`,
`examine.npc_1326`, `examine.npc_3029`, `examine.npc_3017`).

Check: `test-runner/examples/lumbridge-east.json` talks to Bill, examines the three kinds of NPC, and kills a goblin
for its bones; `HeadlessLumbridgeEastTest` fights a goblin to death and talks to Bill through the headless client, with
every branch of Bill's talk in `BarfyBillTest` and the drop rates in `NpcDropsTest`.

## Lumbridge Swamp

The swamp's surface south of the castle, and the land just west of the castle, in `npc-spawns/lumbridge-swamp.json` on
RSMod's tiles (each checked against the 241 map's floor): the giant rats, the frogs, a level 5 goblin, the unicorn and
a butterfly, with the rats west of the castle wall, and the swamp's talking NPCs and tutors. The fighters fight through
`npc-combat.json` and drop through `npc-drops.json`; the talks are `game/content/FatherUrhney.kt`,
`AdventurersCamp.kt`, `CandleSeller.kt` and `SwampTutors.kt`.

| Behaviour | Source |
|-----------|--------|
| 23 giant rats: 10 of level 3 (`giantrat`, 2856, 5 hitpoints) and 13 of level 6 (`giantrat1_2` and `_3`, 2863 and 2864, 10 hitpoints), with 4 more of level 3 (the grey `giantrat_grey` to `3`) and 4 rats west of the castle; they stab for 1 at speed 4 and come back in 30 ticks; bones and raw rat meat every time, the beginner clue at 1/128 | OSRS Wiki, Giant rat (Level 3 and 6); the DPS calculator |
| 82 frogs: 45 Frogs (level 5, 8702, max hit 1, 40% weak to earth), 29 Big frogs (level 10, 8701) and 8 Giant frogs (level 13, 8700), both max hit 2 and 25% weak to earth; all crush at speed 4 and come back in 50 ticks. Frogs and big frogs drop bones, a giant frog big bones, with their beginner clues (1/90, 1/70, 1/64) and the giant frog's long and curved bones | OSRS Wiki, Frog, Big frog and Giant frog; the DPS calculator |
| The level 5 goblin (`goblin_armed`, 3045): 12 hitpoints, stabs for 1 at speed 6 with +12 attack and strength, back in 35 ticks; bones and drop table 2 (with the herb table at 2/128) | OSRS Wiki, Goblin (Level 5, drop table 2); the DPS calculator |
| The unicorn (2837): level 15, stabs for 2, back in 90 ticks; bones and a unicorn horn every time, the ensouled head at 1/35 | OSRS Wiki, Unicorn |
| Father Urhney (923), in his house, as after The Restless Ghost: he sends the player off, and hands a spare ghostspeak amulet to one who has none carried, worn or banked and has a free slot | the Wiki's Transcript:Father Urhney |
| The adventurers' camp: the warrior (1158), archer (1157), monk (1159) and wizard (1160), each with their standard talk after Lost City | the Wiki's transcripts of each |
| The candle seller (1896) by the swamp caves sells a lit candle for 1000 coins, and tells how lanterns are made, which the first menu then offers again | the Wiki's Transcript:Candle seller |
| The Mining tutor (3222) by the east mine: the basics (Mining 1-24), tips (25-38) or advice (39-99), the quests (Doric and, on a members' world, the Dig Site), and the rocks and pickaxes menus; a bronze pickaxe for a player with none and a free slot | the Wiki's Transcript:Mining tutor |
| The Fishing tutor (3221) by the shore: the basics (Fishing 1-28), tips (29-38, with the trawler) or advice (39-99, naming no quest as every one is done), the fish menu and where to fish by level, as on a members' world; a small fishing net for a player with none and a free slot | the Wiki's Transcript:Fishing tutor |
| The fight animations (by the stand and walk animations each type has: the updated giant rat's, the giant frog's for all three frogs, the spear goblin's, the updated unicorn's), the curved bone's 1/5,012.5 kept as 1/5013, and every wander range (5) | guessed |
| For the tutors: that a banked pickaxe or net counts as owned (as for the Woodsman tutor's axe); the items in the boxes the Wiki doesn't name; the fish menu's one slot for the lobster pot (Fishing 29-38) or the harpoon (39 and up), as a menu holds five options; the fishing tutor one tile east of RSMod's, which the 241 map has as water; their wander ranges (2) | guessed |
| Every chathead expression; that a worn ghostspeak amulet counts as carried; the bank as the only store Urhney checks (no Death's Office or item retrieval yet); the candle landing at the player's feet with no free slot; having heard about lanterns kept with the account by name | guessed |

Left out: giant rats are aggressive on live and the unicorn retreats at low hitpoints, which wait for NPC aggression
and retreat (the Varrock piece; both are ranges from the spawn point). The tutors' offers to show a place on the world
map lead back to the first menu without opening the map, which isn't built yet; the fishing spots and the mines are the
gathering skills'. Hewey, Abigale and the two goblin sergeants, which the cache shows by quest varbits, are left out for
now. What the talking NPCs say and each new NPC's examine are in the dialogue file (keys `father_urhney.*`,
`lost_city_warrior.*`, `lost_city_archer.*`, `lost_city_monk.*`, `lost_city_wizard.*`, `candle_seller.*`,
`mining_tutor.*`, `fishing_tutor.*` and `examine.npc_<id>`, one for the level 3 giant rats and one for the level 6).

Check: `test-runner/examples/lumbridge-swamp.json` examines each kind and kills a giant rat and a giant frog for their
drops, then talks to Urhney, the adventurers, the candle seller and both tutors; `HeadlessLumbridgeSwampTest` fights a
giant rat to death, talks to Urhney and the camp's monk, buys a lit candle and gets a pickaxe and a net from the tutors
through the headless client, with every branch of the talks in `SwampTalkTest` and `SwampTutorsTest`, the stats in
`NpcCombatTest` and the drop rates in `NpcDropsTest`.
## Draynor Village

The village west of Lumbridge (the Draynor piece, plan in the project's Draynor Village 1:1 plan doc). Step 1 is the
village itself: `npc-spawns/draynor.json`, `obj-spawns/draynor.tsv` and `game/content/DraynorVillage.kt`.

| Behaviour | Source |
|-----------|--------|
| Three bankers (`banker1` 1613 twice, `banker2` 1618) behind the booths at 3091, 3242 to 3245, with the castle banker's talk and Bank option; the booths and the deposit box work as every booth does | the 241 cache's types; tiles from mejrs's data_osrs NPC list (NPCs seen on live); the Wiki's Banker page for the talk |
| A man in the north-east house, a woman upstairs in Morgan's house, two rats by the farm gate, an imp west of the market, the pig pen's four pigs and three piglets, the farmer in the wheat field | as above, and the Wiki's Draynor Village page (seven pigs, the man and the woman) |
| Morgan's and the Wise Old Man's straight stairs: up puts you past the top piece, down before the bottom piece (both run east) | read off the 241 map's walls; not seen on live |
| Morgan's cupboard upstairs opens, gives a clove of garlic each Search, and shuts | the Wiki's Garlic page ("an unlimited supply"); opening and shutting as other furniture does |
| Aggie's wardrobes and the drawers open and shut, and with the crates and the north-east house's bookcase say there is nothing in them | as the castle's furniture (`Searching`) |
| A cheese and a tomato in Aggie's house, two logs in Spria's, back 100 ticks after taking | the Wiki's Draynor Village spawn list for the items and rooms; tiles and respawn guessed |
| The pig pen's and the farm's gates | `Gates`, above |
| Wander ranges (2 in the pig pen and indoors, 3 and 5 elsewhere) | guessed |
| Step 2: the shore's two Net/Bait spots (`0_48_50_saltfish`, 1525, whose net option is "Small Net"), fished as the swamp's, moving between four tiles | tiles from mejrs's list; the move tiles 2004Scape's `fishing_movement_draynor_village_enum` |
| The five willows by the shore chop with Woodcutting's willow data | the 241 map (ids 10819, 10829, 10831, 10833) |
| Draynor's permanent fire south of the bank (`fire_cook`, 43475): Cook asks which food as a range does, then cooks over the fire (fire burn chart and animation) | the Wiki's Permanent fire page (a Draynor fire with Cook, free-to-play) |
| Take-axe on the logs by the willows (`log_withaxe`, 5581) gives a bronze axe and leaves plain logs for 30 seconds; the same type works wherever the map has it | the Wiki's Bronze axe page ("Bronze axes on logs respawn in 30 seconds"); no message and a full inventory keeping the axe on the logs are guessed |

Left out until their steps: the market and its stalls, the jail, Aggie's, Ned's, Morgan's and the Wise Old Man's talk,
the bank guard, Miss Schism, the town crier, the dark wizards, the sewers' trapdoors and
the rooftop course (Agility isn't built). The wheat field picks as Lumbridge's does (`Crops`).

Check: `test-runner/examples/draynor-village.json` opens and shuts the pig pen's gate, climbs both staircases, takes
garlic from Morgan's cupboard, opens Aggie's wardrobe and sees her cheese and tomato, and opens the bank from a banker
and from a booth; `HeadlessDraynorTest` does the gate, the stairs, the cupboard and the bankers through the headless
client, and `GatesTest` turns a gate every way round. `test-runner/examples/draynor-willows.json` takes the axe off
the logs, chops a willow, nets a shrimp and cooks it on the permanent fire; `HeadlessFishingTest` and `HeadlessCookingTest`
do the Small Net spots and the fire.

## Lumbridge church

The church east of the castle and its graveyard, in `npc-spawns/lumbridge-church.json` on RSMod's tiles, with the
altar's Pray-at; the talks and the altar are `game/content/Church.kt`. Every account is a member and has every quest
done.

| Behaviour | Source |
|-----------|--------|
| The altar (409) in the church: Pray-at fills prayer points up to the Prayer level and resets the drain counter; an item used on it is refused with a message | OSRS Wiki, Altar |
| Father Aereck (2812), as after The Restless Ghost: his thanks, and he has no other quest | the Wiki's Transcript:Father Aereck |
| The Prayer tutor (3223): how to train Prayer (with the 99 Prayer lines) and what it is for (which opens the prayer tab), as on a members' world; each answer offers the other | the Wiki's Transcript:Prayer tutor |
| Nigel (6093, a multi-NPC that shows as 8391 on a normal world) in the graveyard: the two pages of Deadman Mode questions, and the rewards store, which is shut | the Wiki's Transcript:Nigel |
| Count Check (7414): his advice counted out (with the questioned THREE), who he is and the Salve after Priest in Peril, the Stronghold of Security, to which he sends the player once (varbit `sos_teleported_by_count`), and his check, which fails | the Wiki's Transcript:Count Check |
| Nigel's normal world menu leaving out the Deadman worlds' options, his Trade saying the store is shut, and the check failing as no account is a Jagex Account and the bank has no PIN (so his lamp never comes) | readings |
| Every chathead expression; each answer going back to the menu it came from; the kneel (human_pray, 645) and the altar's messages; points above the level kept; the Stronghold tile (beside its entrance, 3081, 3421) and no teleport animation; the wander ranges (2, Father Aereck 5) | guessed |

Left out: the lightning on Count Check's square, whose spotanim isn't known; the church organ; the treasure trail
and Wise Old Man parts of Father Aereck's talk. What they say, the altar's messages and each new NPC's and the altar's
examine are in the dialogue file (keys `father_aereck.*`, `prayer_tutor.*`, `nigel.*`, `count_check.*`, `altar.*`,
`examine.npc_<id>` and `examine.loc_409`).

Check: `test-runner/examples/lumbridge-church.json` examines the four NPCs, prays at the altar with drained points,
and talks to each NPC; `HeadlessLumbridgeChurchTest` prays at an altar, talks to Father Aereck and Nigel (through his
multi-NPC) and is sent to the Stronghold by Count Check through the headless client, with every branch in
`ChurchTest`.

## Al Kharid

The Misthalin roadmap's Al Kharid piece, built a step at a time ([plan](https://claude.ai/artifact/Q7H1GLiHSg5A342NfsZn3f)).
Content is in `game/content/AlKharid.kt`, spawns in `npc-spawns/al-kharid.json`. Every account has finished every
quest, so Al Kharid is as after Prince Ali Rescue.

| Behaviour | Source |
|-----------|--------|
| The toll gate (multilocs 44598/44599 at 3268, 3227 and 3268, 3228) shows as 44050/44051, Open only, once varp 273 is 100 or more; the Pay-toll(10gp) types 44052/44053 are the before-quest ones | 241 cache; OSRS Wiki, Toll gate |
| After the quest Open takes the player through free with no talk, either way | OSRS Wiki, Toll gate (changed 27 April 2022) |
| Talk-to on a Border Guard (4287 Lumbridge side, 4288 Al Kharid side): the player asks to come through, the guard says they're a friend of Al-Kharid, and the player goes through free | OSRS Wiki, Transcript:Border Guard |
| Going through: both leaves swing open for 3 ticks as `inacmetalgateopenl`/`r` (1573/1574), the player moves to the far side at once; a player who talked to a guard first steps up to the gate | 2004Scape's border_gate.rs2; guessed until recorded |
| Four Border Guards, two each side, standing still | 2004Scape map m51_50 spawns; standing still guessed |
| The palace's large double doors (`bankdoor_l`/`_r` 1513/1511, open `openbankdoor_l`/`_r` 1516/1512) open together; the town's curtains (`desertdoorclosed` 1533 / `desertdooropen` 1534) open and close | 241 cache names; the double-door swing is the engine's (see Doors) |

Left out: the gate's sound (no recorded id yet), the Pay-toll option and the 10 coins (before-quest only). The words of
the guards' talk and the examines of the guards and the gate are in the dialogue file (keys `border_guard.*`,
`examine.npc_4287`, `examine.loc_44050`).

Check: `test-runner/examples/al-kharid-toll-gate.json` goes through the gate both ways with no coins, through a Border
Guard's talk, and opens and shuts the palace's west doors and a curtain; `HeadlessAlKharidTest` does the same through the headless
client, with the talk in `BorderGuardTest`.

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
| The can't-reach, too-late and no-space messages (`game.cant_reach`, `game.too_late`, `game.no_space`) | RSMod (rev 233), not checked against live |
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
handler you get the "nothing interesting" message, and an NPC you can't get to says the can't-reach one once you stop.

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
a player carrying none. Not built yet, so their options end the talk or do nothing: the collection box and bank
space; Perdu's Lost Property Shop; the Doomsayer's warnings (Toggle warnings); achievement diaries; Attack
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
conversation runs, and is tested, without them. Game messages work the same way (the engine's under `game.`, combat's
under `combat.` and `prayer.`, eating's under `food.`), so they show as keys too until their words are typed in.

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

## Line of sight

Ranged and magic attacks need the target in line of sight and within the weapon's range (OSRS Wiki, Line of sight).
`map/LineOfSight.kt` answers both, and `game/combat/HitDelay.kt` gives the ticks from an attack to its hitsplat by
distance. `::los` prints all three from where you stand.

| Line of sight behaviour | Source |
|-------------------------|--------|
| Distance is Chebyshev, edge to edge | Wiki, Attack range and Hit delay |
| A big target is seen through its closest tile | Wiki, Line of sight |
| The line: one step at a time along the longer axis, the other axis in 16.16 fixed point from the tile's middle; a tile blocks when it has a projectile wall on the side the line comes in by, or a projectile-blocking loc | RuneLite's `WorldArea.hasLineOfSightTo` (the client's trace); inferred that the server traces the same line |
| A big source (an NPC shooting) starts the line at its closest tile | inferred |
| Hit delays: bows and crossbows 1 + (3 + d) / 6, thrown 1 + d / 6, magic 1 + (1 + d) / 3 | Wiki, Hit delay (magic: its "likely" formula) |

Check: `::los 3 0` from 3222, 3218 is clear; from 3215, 3221 `::los -4 0` goes through the castle's shut large door and
is blocked (`test-runner/examples/los-castle.json`).

## Ranged combat

Bows and crossbows fire at NPCs (`game/combat/RangedCombat.kt`). Attack with a ranged style walks you only until the
NPC is in range and in sight, then you shoot every weapon-speed ticks. Which weapons fire which ammunition, their ranges
and graphics are in `src/main/resources/ranged-weapons.json` (items, animations and spotanims by gameval name; every
name there is in both 241 caches). `::combat` shows your ranged numbers with a bow; `::fight` covers ranged fights.
Not in yet: thrown weapons, crystal bows, the twisted bow, the dragon hunter crossbow, ballistas, enchanted bolts, Ava's
devices and special attacks.

| Ranged behaviour | Source |
|------------------|--------|
| Ranges: shortbows and crossbows 7, longbows and the dark bow 10, Armadyl and zaryte crossbows 8; longrange +2 up to 10 | Wiki, Attack range |
| Each weapon fires its own tier and lower ("up to and including iron bolts") | Wiki, each weapon's page |
| Attack roll (Ranged + 3 on accurate + 8) × (ranged attack + 64); max hit ((Ranged + 3 on accurate + 8) × (ranged strength + 64) + 320) / 640 | Wiki, Ranged and Damage per second/Ranged |
| The NPC defends with its light, standard or heavy bonus, as the style's tooltip names it (bows standard, crossbows heavy) | Wiki; 241 cache dbtable 78 |
| Rapid fires a tick sooner; longrange gives 2 XP a point to Ranged and Defence each, the others 4 to Ranged; Hitpoints 1.33 | Wiki, Ranged and Combat |
| The hit lands 1 + (3 + d) / 6 ticks later, plus one on an NPC | Wiki, Hit delay |
| XP is given as the shot is fired, for the damage rolled, even if less lands (another player's hit first) | inferred: RuneLite's XP drop "predicted hit" relies on the drop coming before the hitsplat; not recorded |
| One ammunition a shot, two for the dark bow (one when one is left); 1 in 5 breaks, the rest falls to the floor | Wiki, Ava's accumulator (20% broken without a device) and Dark bow |
| The empty-quiver message (`combat.no_ammo`) | Wiki, Ammunition |
| Fired ammunition lands under the NPC, yours for 100 ticks and gone after 300, as a drop | guessed |
| "You can't use that ammo with your bow." / "... crossbow." | guessed wording |
| Projectile heights, start, arc and the second dark bow arrow leaving 10 cycles later; the crossbow animation (xbows_human_fire_and_reload_pvn) | guessed |
| No or wrong ammunition ends the fight when you would fire, not on the click | guessed |

Check: `./gradlew test --tests 'osrsps.game.combat.Ranged*' --tests 'osrsps.testclient.HeadlessRangedTest'`, and in
RuneLite `test-runner/examples/combat-ranged-shortbow.json`.

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
the click is read, one further away on the tick you arrive. With no handler you get the "nothing interesting" message,
and if the route can't get there, the can't-reach one when you stop. An inventory item used on a loc (OPLOCT) walks
the same way and runs `World.handlers.onObjOnLoc(locId, objId) { op -> ... }`, with the item in `op.obj` and its slot
in `op.sub`; leave out `objId` to catch every item. Unhandled, it too does nothing interesting. Changing level from a ladder or staircase is a
handler that calls `World.teleport` with the new level, after playing the climb with `Player.anim` and a `delay`.

Single doors open and close without any content: a closed door type `x` with an Open option and a type `xopen` or
`x_open` with a Close option (Jagex's gameval names, also in the cache) pair up, which covers about 140 door types,
Lumbridge Castle's `poordoor` (1535), `elfdoor` (1543) and `poordoor_m` (56376) among them. A handler for a door id
takes over from this. Doors whose names don't pair (the castle kitchen door, 12348) and double doors are left to
content, which can pair a door with `World.doors.register(closed, open)`.

Fence gates open too: a left leaf `…fencegate_l` with an Open option pairs with `…openfencegate_l` (Close), and the
right leaf `…fencegate_r` with `…openfencegate_r`, by the same gameval names (`game/loc/Gates.kt`). Opening either leaf
opens both: each turns a quarter anticlockwise, the left one moving a tile outward and the right one two tiles outward
and one back along the fence, so they stand together against the left leaf's hinge. Closing puts both back. That
covers the plain picket gates (`fencegate_l`/`_r`, 1558/1560), the farming ones (1561/1562), the rustic ones and Fred
the Farmer's (`qip_sheep_shearer_fencegate_*`), and the field gates north of Lumbridge (`fai_varrock_gate_l`/`_r`,
which open into `_lc`/`_rc`): Draynor's pig pen and farm, and in and around Lumbridge the cow field's gates (3253, 3266
and the west fence's), Fred's farm and sheep pen, the Groats' pens, the fields to the west (3145, 3291 to 3198, 3282)
and north (3213, 3354 and 3254, 3347). Not covered: the cow field's `fencegate_*_cowboss_start` at 3262, 3294, whose
option is Release (a content gate of its own). The rule is RSMod's picket gate script (rev 233), not yet watched on live; gates stay open until closed,
as doors do (RSMod shuts them again after 500 ticks).

| Loc behaviour | Source |
|---------------|--------|
| Loc type fields, map loc file format, shapes and layers | the 241 cache, decoded with RuneLite's loader's opcode table; all 62,534 loc types decode |
| Which tiles reach a wall, wall decoration or ground loc, and turning the blocked sides with the loc | the client's reach checks as open-source route finders document them, not checked on live |
| An unreachable loc walks you to the nearest tile to its footprint (within 10 tiles) | the client's route finder fallback, as above |
| Used before moving and again after, in the movement phase | the 2004Scape reconstruction of the RS2 world cycle, not checked on OSRS |
| The "nothing interesting" and can't-reach messages | live messages, from memory |
| The player turns to face the loc when using it | guessed |
| A loc that changes before you get there ends the interaction; you finish the walk | guessed |
| Door pairs by gameval name | the 241 cache's own names |
| Where an opened door goes (a quarter turn clockwise, onto the next tile) | RS2 engine rule; 151 of the 191 open doors the 241 maps place line up with it, 19 with the opposite turn (mostly double-door leaves) |
| Doors stay open until closed, with no sound yet | guessed; the door sound ids aren't known yet (a recording will give them) |
| Gates: both leaves together, where each goes, by gameval name | RSMod's picket gate script (rev 233); not checked on live |
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
| The welcome message after login (`game.welcome`) | from memory of live, not a recording |
| Varbit 8119 (`has_displayname_transmitter`) set to 1 at login, or the client says "You must set a name before you can chat" and blocks typing | var name from RuneLite's VarbitID; that the chatbox checks it is inferred |

## Saving and reconnect

Each player is saved to `data/saves/<name>.json` (on the VM, `/srv/rsps/OSRSPS/data/saves/`, which is not backed up).
Names ignore case and treat spaces as underscores, so `Test` and `test` share a save. A save holds position, run energy,
every varp that isn't 0 (the run orb and the resizable layout among them), each skill's XP and current level, and the
inventory's, equipment's and bank's slots. Code is in `src/main/kotlin/osrsps/game/PlayerSave.kt` and `SaveStore.kt`.

| Rule | Source |
|------|--------|
| Saved on logout, when a lost connection gives up, every 3 minutes of real time (paused or not) and on shutdown (skipped if the game thread is stuck even after an interrupt, leaving the last autosave) | ours |
| Taking an item another player put down saves both of them at once, so a crash can't leave it with both | ours |
| A save the disk refuses is kept in memory, loaded in place of the file at the next login, and written again every 3 minutes until it goes | ours |
| A save is forced to the disk before it replaces the old one, so a power cut leaves one or the other whole | ours |
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
Launcher (token) logins are refused, as only Jagex can check a token. A name with an `@` in it, as an email address has,
is refused with "Invalid username or password" before it is logged, hashed or saved: it is most likely someone's real
game login, typed where a made-up name belongs. Any other name must keep to live's display name rules: 1 to 12
letters, numbers, spaces, hyphens and underscores (refused the same way, and not logged either), so no name can carry
look-alike letters or a line break into the log. A name listed in `OSRSPS_ADMINS` with no account yet can't be claimed;
claim it first, then list it.

Each account is `data/accounts/<name>.json` (on the VM, `/srv/rsps/OSRSPS/data/accounts/`), holding a salted
PBKDF2-HMAC-SHA256 hash (600,000 rounds), never the password. Checking takes a fraction of a second, so it runs on its
own login thread, not the game thread. A save with no account file (every save from before accounts) is claimed by its
next login. To change a password, or set one for a name nobody has claimed (an admin's included), run `set-password`
on the VM in the running container. It asks twice without echoing, so the password stays out of shell history and
logs, needs at least 12 characters, and takes effect at the next login with no restart:

```bash
cd /srv/rsps/OSRSPS && docker compose exec -e JAVA_OPTS=-Xmx64m osrsps bin/osrsps set-password <name>
```

Deleting that name's file on the VM also resets it, and the next login claims it again (an admin's name must come off
`OSRSPS_ADMINS` for that login, then go back on):

```bash
rm /srv/rsps/OSRSPS/data/accounts/<name>.json
```

Guessing is slowed by name: 5 wrong passwords in a row lock that name for 5 minutes (the client says "Too many login
attempts"), and at most 32 logins wait on a password check at once. A lock only lives in memory, so a restart clears it.
A relay connection that comes without a PROXY header carries the relay's address (see below), so the relay addresses
in `OSRSPS_RELAYS` (comma separated) are exempt from RSProt's limit of 10 connections per address; every other address,
a player's own address from a header included, keeps it.

### Real addresses and IP bans

Internet players come in through the VPS relay (HAProxy, then a WireGuard tunnel and NAT), so their connections arrive
from the relay's address, 192.168.0.216. HAProxy's `send-proxy-v2` starts each connection with a PROXY protocol header
naming the player's own address and port, which passes the tunnel and NAT unchanged. The server reads one (v1 or v2) on
both ports, the game and JS5 on 43594 and the client files on 43595, and from then on knows the connection by that
address: in the login log (`alice logged in from 203.0.113.7 as player 1 ...`), the connection limit, `::ip` and the
bans below.

Only the addresses in `OSRSPS_RELAYS` may send a header: a connection from anyone else that starts with one is dropped,
or anyone could claim any address. Everyone else (the LAN, the test VM) is a plain client known by their own address.
`OSRSPS_PROXY_PROTOCOL` says what a relay must do:

| `OSRSPS_PROXY_PROTOCOL` | A relay's connection |
|-------------------------|----------------------|
| `optional` (the default) | With or without a header; one without counts as the relay's own address. Lets HAProxy be switched over with nobody locked out |
| `required` | Must start with a header, or it is dropped |
| `off` | May not send one; nor may anyone else |

The server logs the setting at startup, e.g. `proxy protocol: optional from relays [192.168.0.216]`, and logs every
connection it drops for a header with the reason.

An admin bans an address with `::ipban <name or address> [note]`: a name bans the address that player is on now. Anyone
already on that address is logged out, and logins from it are refused with login response 26 (RSProt's `IPBlocked`, the
client's address-blocked message). Bans are kept
in `data/ip-bans.txt` (on the VM, `/srv/rsps/OSRSPS/data/ip-bans.txt`), one address and its note per line, so they last
across restarts; the file may be edited by hand while the server is down. `::ipunban <address>` lifts one and `::ipbans`
lists them. A relay's own address can't be banned, since every headerless internet player shares it.

Check: with the server up, `::ip` lists everyone online with their address; a player through the relay shows their own
public address once HAProxy sends the header, and a LAN player shows the LAN side's address.

## Scenarios

A scenario sets a player up for a test in one go: where they stand, their skills, inventory, equipment, run energy and
any varps or varbits. Scenarios are JSON files in `src/main/resources/scenarios/`, named after the scenario and listed in
`Scenarios.FILES`; the format is in `game/Scenario.kt`. They are testing tools, not live content.

| Scenario behaviour | Source |
|--------------------|--------|
| What a scenario leaves out is reset: unnamed skills are a new account's, run energy is full, slots it doesn't fill are empty, the bank holds only what it lists, in the tabs it names | ours, so a scenario always starts the same |
| Varps and varbits it doesn't name, and the run orb unless it says, are kept | ours: they hold settings and quest states |
| Starting one, or `::reset`, closes interfaces and drops queued scripts, timers, delays and clicks held while paused | ours |
| Items are checked against the cache before anything changes; level requirements aren't checked | ours |

`::reset` puts back the player as they were before their first `::scenario`: a second scenario still resets to before
the first. The way back is kept in the save (`beforeScenario`), so a logout in the middle loses nothing. The server log
warns at startup about any scenario naming an item the cache lacks.

`lumbridge-cows` stands you in the Lumbridge cow field (3259, 3263, a tile picked from the cow spawns) with 20 Attack,
Strength, Defence and Hitpoints, iron armour, an iron scimitar, an amulet of strength, five lobsters, a bronze axe, a
tinderbox and 100 coins, with run on.

`lumbridge-bare` stands you on the Lumbridge spawn (3222, 3218) with a new account's stats and nothing worn or carried:
the clean start the equipment test scripts use.

`lumbridge-bank-tabs` stands you by the castle's top floor bank booths (3209, 3220, 2) carrying nothing, with logs and
a bronze axe in bank tab 1 and 1,000 coins in the main tab: for the tab script, as the test runner can't drag.

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
