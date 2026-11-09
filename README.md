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

### Custom models

`import-model` writes a Wavefront OBJ (from Blender, or the RS3 rip in the project files) as a model in the client's
own format (`osrsps.cache.model`: the newest model format, which 35,124 of the 62,051 live models use, read and
written as RuneLite's loader reads it). Every face gets one flat colour, from the material's colour or its texture
times the vertex colours, snapped to the client's palette; y is turned over, as the client's y grows downwards.

```bash
./gradlew run --args="import-model lodestone.obj data/models/lodestone.dat --scale 0.25"   # RS3's 512 units a tile to 128
```

Options: `--scale f`, `--mirror-x`, `--flip-faces` (for a model that shows mirrored or inside out), `--priority n`
(0 to 10) and `--alpha material=n` (0 opaque to 254) per material. The output is a plain model file.

The server serves every `data/models/<id>.dat` as model `<id>` on top of the cache (`osrsps.cache.overlay`): at start
it adds each to index 7's reference table, in memory, with the CRC of the container it sends, and puts the table's new
CRC in the master index, so a client fetches the table again and then the model when a loc or NPC points at it. The
cache files on disk stay the live cache. An id has to be above every live model (65,000 and up is safe for revision
241) and below 65,536, which is all a JS5 request carries. A changed file is served at the next start; a client that
had the old one sees the new CRC and fetches it again. Converted Jagex art stays in `data/`, outside git, like the cache.

Sprites and interfaces of our own go the same way. `import-sprite` writes an image (PNG, or anything ImageIO reads) as
a sprite in the client's format (`osrsps.cache.SpriteCodec`; 255 colours, reduced when the image has more, alpha kept),
and the server serves every `data/sprites/<id>.dat` as sprite `<id>` on top of the cache's sprite index (8). The
lodestone map (`game/content/LodestoneMap.kt`) is the first interface of ours: its components are code
(`osrsps.cache.InterfaceCodec` writes the if3 form every OSRS interface uses: layers, rectangles, text, graphics and
lines, with no client scripts; what each does is the server's, through IF_SETEVENTS, IF_SETTEXT and IF_SETHIDE) and
the overlay serves them as interface 30,000 (above the cache's own, below 32,768, as a packed component id is a signed
int), with its sprites (65,000 to 65,004; 65,004 is the stones' minimap icon) drawn in code too. A file in
`data/sprites` under one of those ids takes the drawn sprite's place, which is how a real map image goes in:

```bash
./gradlew run --args="import-sprite misthalin.png data/sprites/65003.dat"   # 300 by 262 for the lodestone map
./gradlew run --args="import-sprite lodestone-icon.png data/sprites/65004.dat"   # 15 by 15, the minimap icon
```

Loc types of our own that use such models are code: `game/content/CustomLocs.kt` lists them (ids from 65,000), each a
`LocTypeEncoder.Definition` (name, models, options, footprint, whether players walk over it) that the overlay adds to
the cache's loc types group (config archive 6) as a file, packed again with the cache's own loc types, and that the
server decodes back for itself, so the client and the server see the same loc. The lodestones (`Lodestones.kt`) are
the first: the server places a stone at each site at start. A loc's minimap icon is a map element (config archive
35, `osrsps.cache.MapElementCodec`: a sprite and the flags the cache's icons carry) that the loc names with opcode 82;
the overlay adds ours to that group too, and the client draws the sprite where the loc stands (the runner's
`lodestone-network.json` reads the element and its sprite off the client's loc config on both looks, `assertMapIcon`).
The world map's icons are built into the world map index with the map, so a loc of ours gets none there. A loc that
looks different per player is a multiloc: its
definition's `transform` names a varbit and the child locs the client draws for each value, and the server answers
the one loc's options by the player's varbit. Varps and varbits of our own are code too: `game/content/CustomVars.kt`
lists them, the overlay adds their types to config archives 16 and 14 so the client reads them like the cache's, and
the world's var types include them, so content sets them like any other. A save keeps them by name (`"customVars":
{"lodestones": 3}`), not by id, and drops a numeric varp past the cache's own, so a cache swap gives them new ids
without a player's saved value landing on another var. Their ids are
given at startup, right after the loaded cache's (its varp count and varbit count), because OpenRS2's builds of one
revision end at different ids (241's varps end at 5,890 in one build and 5,995 in another) and the overlay refuses an
id the cache has; so nothing names them by number: content and tests go through `CustomVars`, and `::varp lodestones`
and `::varbit lodestone.varrock` take the names in the chatbox and the runner's scripts.

Animations and graphics of our own are code as well: `game/content/CustomAnims.kt` lists sequences (config archive 12,
`osrsps.cache.SeqCodec`) and spotanims (13, `osrsps.cache.SpotanimCodec`) from id 65,000, above the cache's, and the
overlay adds them to those groups. The client has no frames of ours, so a sequence of ours plays frames the cache
already has, at our own pace and in our own order (as the cache's own reversed teleport, 715, plays 714's backwards),
and a spotanim of ours is a cache graphic's model on a sequence with its colours swapped. The lodestone cast is the
first (step 8): the arms-raised pose while a white pulse builds, then Tele Other's rise drawn white in the last three
ticks, and the same frames backwards on the arrival tile, after RS3's player who "flies into the air as white pulse
and flies back down at the lodestone". `::anim 65000` and `::spotanim 65000` play them, and `lodestone-network.json`
reads the cast and the landing off the real client after the Teleport click.

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
Lab project runs the relay. They install Odin's Gate, our launcher, from its download page,
https://samsjosten.github.io/OdinsGate/. The launcher, the page and every release live in the public repository
OdinsGate (`github.com/SamSjosten/OdinsGate`, its `launcher/README.md`) since launcher 1.0.2, this repository's last
launcher release: 1.0.2 raised the minimum shell version above every launcher installed from here (1.0.0, 1.0.1), so
each shows "Please download the new installer." with the old page, `docs/` here, which now only forwards to
OdinsGate's (CI's `friend-page` job holds it to that, `.github/scripts/friend-page.js`). The launcher downloads RuneLite
from RuneLite's own servers and patches it in memory to our server and key. Each friend logs in with a name and
password of their own, never any other game's login; the first login as a name claims it (see Accounts).

- A friend who used RSProx before: the launcher keeps its own game settings, so RuneLite settings made under RSProx
  don't carry over; the name and password do. RSProx stays the developer route (above) and still works for friends on
  Windows or Linux: import `http://odinsgate.net:43595/proxy-targets.yaml` with **+** → **From URL…**, restart RSProx,
  pick **OSRSPS**. RSProx reads that address again each time it starts.
- Nothing of the game's is handed out: the launcher and RSProx both download RuneLite and the game client themselves.
- The launcher pins the RuneLite build per revision in `src/main/resources/runelite-pins.json` (a commit of RuneLite's
  [static.runelite.net history](https://github.com/runelite/static.runelite.net/commits/gh-pages/bootstrap.json); the
  signed manifest carries OdinsGate's copy of the table, and RSProx's target names the same build). RuneLite moves to
  each new revision within hours of live, and our server turns a client of another revision away, so a revision bump
  adds the new revision's build to both copies first (CONTRIBUTING.md, "The launcher's copies").

### Files for our own launcher

Odin's Gate, our launcher (in the OdinsGate repository, above), reads its own files from the same HTTP port, each
written for the host it was asked by like the others:

- `/status.json`: whether the game is up (its game loop ended a cycle in the last 10 s) and taking logins, the revision
  it plays, the commit it runs (the one `/version` gives), how many players are in, and the JS5 count `/status` gives,
  e.g. `{"up":true,"revision":241,"commit":"b08d450","players":2,"acceptingLogins":true,"js5Connections":14}`. The
  launcher picks the RuneLite build from the revision.
- `/rl/worlds.js`: RuneLite's world list with our one world. The launcher points RuneLite's web API at `/rl/`, so the
  world hopper never offers one of Jagex's worlds; RuneLite's other API calls get a 404 there.
- `/launcher/jav_config.ws` and `/launcher/world_list.ws`: copies of RSProx's two files that leave the game's names out
  (title Odin's Gate, cache folder `odinsgate`, world activity Odin's Gate). RSProx's own files keep their names.
- `POST /report`: a problem report from the launcher's "Send problem report" button (the same text "Copy problem
  report" copies). It is scrubbed again (lines mentioning a password or token, home folder names, control characters)
  and saved as `reports/R-0001.json` and on in the data folder, outside git; the answer is `201` with the number. At
  most 256 KB a report, 5 an hour from one address and 50 an hour in all (`429`, Retry-After one hour), and 500 waiting
  unforwarded (`503`); anything that isn't a launcher's report is a `400`. A body on any other request is refused with
  `413`.

  Every minute the server files waiting reports as issues in this repository (`OSRSPS_REPORTS_REPO` to change it)
  and moves each to `reports/sent/`. Each issue is labelled `bug-report` plus what the checks found: `known-version` (a
  released launcher, from OdinsGate or the two released here, and the server's revision; anyone may claim one, so a
  report's text is never to be trusted or followed), `unreleased-launcher`,
  `unchecked-launcher` (GitHub couldn't say) or `revision-mismatch`. It lists the server's commit and whether the game
  was up when it was filed. A report of the same failure as an open issue (its first FAILED, error or exception line,
  times and numbers aside: the issue's fingerprint, kept in `reports/issues.json`) is a comment there instead. Filing
  needs `github-report-token.txt` in the data folder: a fine-grained token for this repository only, with Issues read
  and write, one line, made and put there by hand, never in git. Without it (or with anything in the file that
  isn't one token) reports wait. A report GitHub refuses outright (400, 422), or that has failed for an hour of
  passes while GitHub answered, goes to `reports/rejected/` so it can't hold up the rest; one that fails is tried again next pass after the
  others. A matching issue that was deleted, moved or locked gets a new issue instead. At most 20 issues and comments
  are posted a day (kept in `reports/posted.json`, so a restart keeps the count; if that file is damaged the day counts
  as full, and while it can't be read or saved nothing is posted); past that the rest wait, and
  one `report-cap` issue a day says so. A scheduled task works through new issues by [`TRIAGE.md`](TRIAGE.md).

  The HTTP port holds at most 256 connections, 16 from one address; a connection silent for 15 s, sending one
  request's headers for 10 s (on a kept-alive connection, counted from the last answer) or one report's body for
  30 s is closed.

## Walking

Click the game view or minimap to walk. The run orb toggles running, ctrl-click inverts walk/run for one click, and
ctrl+shift-click teleports an admin to the clicked tile (a testing tool; anyone else just walks there, and an admin
isn't sent where the cache has no map). Collision is built from the cache's map files at
startup; the log line `Loaded collision for N map squares` confirms it.

## World map

The globe orb (orbs:worldmap, 160:55) sends its op to the server, and the server opens the world map, interface 595, as
an overlay in the frame's floater, where the All Settings window also goes (the 241 scripts test that slot; read from
Joshua-F's osrs-dumps, not seen on live). The orb again, the map's close button or Esc closes it, and walking leaves it
open. The map itself is the client's: it draws it from the cache's worldmap index, which JS5 serves like the rest, and
sizes, zooms, searches and floats or docks the window in its own scripts. `test-runner/examples/world-map.json` clicks
it in the real client.

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
| Agility in those sums is the current level, a boost counting | inferred |
| Graceful pieces worn restore energy faster: hood 3%, top 4%, legs 4%, gloves 3%, boots 3%, cape 3% (the Agility cape counts as the cape), the whole set 30%; the restore is ⌊R × (100 + bonus) / 100⌋ | OSRS Wiki, Graceful outfit and Energy (read 2026-11-02); every colour by its cache name; the Agility cape's own 3% inferred |
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
| `::teleto name` | Teleports onto the player called that, on their level |
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
| `::poison [severity\|venom\|cure]`, `::poison npc severity\|venom [index\|name]` | Says what the poison varp (102) means now: poisoned, envenomed or immune, and the ticks to the next hit. A severity (20 for a poisoned weapon, 30 for p++) poisons you, `venom` envenoms you, and `cure` ends poison, venom and immunity. `npc` poisons or envenoms an NPC instead (by index from `::npcs` or by name, the nearest one you can fight by default), unless it is immune: it is hit every 30 ticks with no XP, and `::npcs` shows it |
| `::ignores` | **everyone.** Your ignore list as the server keeps it |
| `::clearignores` | Empties your ignore list, for tests that start from none |
| `::stun [ticks\|end]` | Says whether you are stunned and for how many more ticks. A number of ticks (9 for a failed pickpocket, up to 100) stuns you: the birds show, and you can't walk, attack or click anything in the world until it ends, though you can still eat and use interfaces. `end` ends it |
| `::drain stat levels\|percent% [index\|name]` | Drains an NPC's attack, defence, strength, ranged or magic (the one you fight, or by index from `::npcs` or by name) by that many levels, or by a percentage of its level now, as far as its drain cap lets it, and says its level now. Its combat rolls use the drained level (`::combat` shows them), it gets a level back a minute, and `::npcs` shows its drained stats |
| `::spec [percent]` | Says your special attack energy (varp 300), whether the bar is lit, and the ticks until 10% comes back (every 50 ticks). A percentage, 0 to 100, sets the energy. The combat tab's bar and the minimap orb light the bar for the next swing; the dragon warhammer's Smash (50% energy: 50% more damage and 30% of the target's Defence drained, down to its cap) and the Bandos godsword's Warstrike (50%: double accuracy against slash, the max hit up 10% twice, and the target's Defence, Strength, Attack, Magic and Ranged drained by the damage in turn) and Dinh's bulwark's Shield Bash (50%: 20% more accurate against crush, and in a multi-way area up to 10 NPCs within 5 tiles hit, the target twice, each losing 5% of its highest offensive stat) are in. The bulwark's Block style can't attack and, 8 ticks after it is wielded, lets 80% of monsters' melee hits through, and `::combat` shows a special's attack roll and max hit |
| `::aggro [ticks]` | Shows how many ticks you have spent in the two areas aggressive monsters' tolerance is counted in, and whether they leave you alone yet (after 1000 ticks, ten minutes); with a number, sets the count first. `::npcs` shows who each NPC is fighting |
| `::seed [n]` | Shows the random seed and how many numbers have been drawn since it was set, or restarts the rolls from seed n |
| `::gfx id [height] [delay]` | Plays spotanim `id` on your tile for everyone nearby. Delay is in client cycles (20 ms) |
| `::proj id [dx dz]` | Fires spotanim `id` as a projectile from your tile to the tile dx, dz away (default 5 tiles north) |
| `::los npcIndex` / `::los dx dz` | **everyone.** Distance, line of sight and hit delays from you to an NPC or a tile (see Line of sight) |
| `::instance [radius]` / `::instance leave` | Makes a private copy of the map around you, `radius` zones each way (default 6, 13 x 13 zones), and puts you on the same spot in it; `leave` takes you back to where you made it (see Instances) |
| `::raidparty` / `::raidparty invite\|accept\|kick name` / `::raidparty leave` | Shows your raid party, or invites a player to it (starting one you lead), joins the party of a leader who invited you, puts a member out, or leaves (see Raid parties) |
| `::raid boss [phase=name\|n] [party=n] [seed=n]` / `::raid` / `::raid log [lines]` / `::raid digest [ticks]` / `::raid stop` | Starts a boss fight on your tile at a phase, scaled for a party size, on a seed; shows the fight you're in (or lists the bosses), its last log lines, a digest of its log to a tick, or stops it (see Encounters) |
| `::raidnpc id\|name [party size]` | Puts in a raid NPC on your tile with its hitpoints for a party of that size, or of your raid party's size, or of one alone (see Raid parties); `::delnpc mine` takes it out |
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
| `::openbank` | Opens your bank wherever you are (not `::bank`: the client turns that into a chat line and never sends it) |
| `::empty` | Empties your inventory |
| `::locs [radius]` | **everyone.** Lists the locs within radius tiles of you (default 1): id, name, tile, shape and angle, and whether they differ from the cache |
| `::addloc id [shape] [angle]` | Puts loc `id` on your tile (default shape 10, a ground loc, angle 0), replacing what was on its layer |
| `::delloc` | Removes every loc on your tile |
| `::resetlocs [radius]` | Puts the locs within radius tiles of you (default 0, your tile) back as the cache has them; a loc the server itself placed for good (a lodestone) stays |
| `::npc id\|name [wander]` | Spawns an NPC on your tile, by id, menu name or gameval name: `::npc hans`, `::npc cow2_calf 3`. With a wander range it wanders that far from the tile, otherwise it stands still. Gone on restart |
| `::npcs [radius]` | **everyone.** Lists the NPCs within radius tiles (default 15) with their index, tile, spawn tile and wander range, the party size and hitpoints of a raid NPC, and whether each is frozen, hurt (its hitpoints left), poisoned, envenomed or drained, and who it is fighting ("no one" for an idle one that can fight) or retreating from |
| `::delnpc index` | Removes NPC `index` (from `::npcs`) |
| `::respawns` | **everyone.** Lists the killed NPCs waiting to come back: type, spawn tile and ticks left (each type's Wiki respawn time from the killing blow, at least the 4 ticks the body lies) |
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
words under `crafting.*`, numbers in `skills/crafting.json`). A needle on leather, hard leather or green to black dragon
leather opens the box over what it makes; thread is held, and one is used up every 5 items (the count is kept with the
account). Steel studs on a leather body or chaps stud them. A chisel on an uncut gem cuts it; opal, jade and red topaz
can crush into a crushed gemstone, rolled as the gathering skills roll. A gold bar or a mould used on a furnace opens
`crafting_gold` (interface 446), and a silver bar or silver mould `silver_crafting` (6): both lay themselves out from
the inventory, so the server answers the item buttons and keeps the quantity buttons' choice in `makexcrafting`, as at
the anvil. A ball of wool strings an amulet. Soda ash or a bucket of sand on a furnace makes molten glass (the bucket
comes back), and a glassblowing pipe blows it. Water on clay softens it; soft clay on a potter's wheel shapes pots,
cups, pie dishes, bowls, plant pots and lids, and a pottery oven fires them, some cracking below level 13. The making is the artisan loop in `Making.kt`.

| Crafting behaviour | Source |
|--------------------|--------|
| Ball of wool level 1, 2.5 XP; bow string level 10, 15 XP; 3 ticks each | OSRS Wiki, Ball of wool and Bow string |
| The first 2 ticks after the choice | inferred from fletching's "Make-X is 2, then 3 ticks" |
| Animation 466 (`spinningwheel`) as the player's | inferred from its gameval name |
| Leather and d'hide levels, XP, leathers per item; 3 ticks an item, hard leather 2; thread's 5 uses | OSRS Wiki, Leather, Hardleather body, Green dragon leather and the d'hide pages, Thread |
| Studs 3 ticks; studded body 41, 40 XP; studded chaps 44, 42 XP | OSRS Wiki, Studded body and Studded chaps |
| Gem levels, XP, 2 ticks a gem; opal, jade and red topaz's crush chances (pairs worked back from each page's formula and checked against its chart, `CraftingDataTest`) and crushed XP | OSRS Wiki, each gem's page |
| Each gem's cutting animation (`human_*cutting`) | cache 241 gamevals |
| Gold and silver jewellery levels and XP; 3 ticks an item; stringing an amulet 4 XP, 2 ticks | OSRS Wiki, Jewellery, Silver bar, Gold ring, Gold amulet |
| The two furnace interfaces' item and quantity buttons | decompiled interfaces crafting_gold and silver_crafting (osrs-dumps) |
| The furnace animation, silver taking gold's ticks, which interface a shared mould opens | guessed |
| Molten glass 1, 20 XP, 2 ticks; glassblowing levels and XP, 3 ticks; soft clay 2 ticks, no XP | OSRS Wiki, Molten glass, Glassblowing pipe, Vial, Soft clay |
| Pottery levels and both XPs; 3 ticks at the wheel, 7 in the oven, firing at any level; the oven's crack chart, which the pair 180/800 gives exactly (`CraftingDataTest`) | OSRS Wiki, Crafting (Pottery), Unfired pie dish, Pie dish and the other pots' pages |
| A cracked pot lost with no XP; the oven animation; the other glassblowing items' ticks | guessed |
| Spin with nothing saying Nothing interesting happens; the box opening even for one | guessed |
| Out: sinew, magic roots, yak hair and the other members' spinning; d'hide shields, snakeskin, the costume needle; the jeweller's chisel, amethyst; the slayer ring, quest silver items, silver bolts, enchanting | out of scope |

### Herblore

Clean on a grimy herb cleans that one at once (`game/skill/Herblore.kt`, words under `herblore.*`, numbers in
`skills/herblore.json`). A clean herb and a vial of water, either used on the other, open the Make-X box for its
unfinished potion. A pestle and mortar on a unicorn horn, chocolate bar, kebbit teeth, bird nest, desert goat horn or
blue dragon scale opens the box for the ground item; the first is ground as the choice is made. An empty vial used on a
sink, fountain or water pump fills every vial held, one a tick. A secondary on its unfinished potion opens the box for
the finished potion (23 of them, attack potion to Saradomin brew), a (3) dose each. Making goes through `Making.kt`.

| Herblore behaviour | Source |
|--------------------|--------|
| The 15 herbs' cleaning levels and XP; Clean taking no time | OSRS Wiki, each grimy herb's page |
| The cleaning message naming the grimy herb | OSRS Wiki, Grimy guam leaf |
| Unfinished potions' levels, no XP, 1 tick | OSRS Wiki, each unfinished potion's page |
| Grinding 0, then 2, then 3 ticks | OSRS Wiki, Unicorn horn dust ("Auto-crushing ... is 0, then 2, then 3 ticks") |
| A vial fills in 1 tick at any water but a well | OSRS Wiki, Vial of water |
| Animations 363 (`human_herbing_vial`) and 364 (`human_herbing_grind`) | cache 241 gamevals |
| Finished potions' levels, XP, secondaries, 2 ticks, a (3) dose | OSRS Wiki, Herblore (potions table) and each potion's page |
| Which locs count as water; filling with no box; the filling animation (832); no animation for cleaning; the first potion 2 ticks after the choice | guessed |
| Out: potions of three or more ingredients (super combat, stamina, sanfew, divine), coconut-milk potions, Varlamore's, herb sacks, tar, barbarian mixes, ashes, other ground items | out of scope |

### Agility

Obstacles and courses live in `skills/agility.json` (`game/skill/Agility.kt`, words under `agility.*`). The option on an obstacle walks the player to its start, checks the
level, then plays its moves in order while clicks do nothing: an animation and a wait, a teleport (a climb's end,
another level too), a walk along a path one tile a tick through anything with the obstacle's animation as the whole
stance (a log or a tightrope), and a slide (the client's exact move, a jump across a gap). The XP comes once across.
An obstacle that can fail rolls its success pair against the current Agility level; a failure plays its own moves (a
fall) and deals its damage, with no XP. A course's first obstacle starts a lap and its last ends one: with every
obstacle crossed in between, the lap counts (saved with the account) and its count is shown.

Courses built: the Draynor Village Rooftop Course (from the rough wall north of the bank to the crate off Ned's
house; level 1, 120 XP a lap, nothing fails since the May 2024 rework) and the Al Kharid Rooftop Course (from the
rough wall on Ellis the tanner's building to the gap east of Zeke's; level 20, 216 XP a lap spread over its eight
obstacles, Tightrope 1 and the zip line failing for 1 to 5 damage) and the Varrock Rooftop Course (from the rough wall
east of the general store to the edge south of Lowe's; level 30, 269.7 XP a lap, the clothes line failing for 3 to 8
and the wall for 2 to 5) and the Gnome Stronghold Agility Course (from the log balance to the pipes; level 1, 110.5 XP
a lap with its 50 XP lap bonus, nothing fails; reached by `::tele 2474 3438`). An obstacle can be several loc types
(the Gnome Stronghold's two pipes, its rope's pieces) and several locs (its three nets), each route naming the locs
it is for; of the routes for the loc clicked, the one starting nearest the player is taken (a fence's two sides).

Shortcuts (on no course, both ways): the Varrock south fence (13), the Grand Exchange underwall tunnel (21), the
Champions' Guild stepping stone (31, 3 XP; a failure still reaches the far bank, for 1 to 3 damage and 1 XP), the
Draynor underwall tunnel (42), Draynor Manor's broken fence (49, 1.5 XP) and the Champions' Guild south stepping stones
(52, 2.5 XP on the first stone and 2.5 on the third). The stones are jumped onto from the bank, as their sides are
water: a click walks to the nearest bank tile, not beside the stone. Left for later: the Lumbridge Swamp stepping stone, the River Lum raft, the Varrock Palace
trellis (a quest), the Edgeville Dungeon pipes and the Paterdomus railing.

Marks of grace: once three whole minutes of the clock have gone since a player's last mark, each lap they finish has
the course's chance of one (2/6 on Draynor), a fifth of that when their unboosted Agility is 20 or more over the
course's level. It lands on one of the course's spots, for them alone, and goes after ten minutes; another on the same
tile stacks and starts the ten minutes again. The minute of the last mark is saved with the account. Grace sells the
graceful outfit for them in the Rogues' Den, under the Toad and Chicken in Burthorpe (`game/content/RoguesDen.kt`:
the trapdoor behind the bar, the den's door, and the passageway back up; the maze is left out), and buys it back at 80%.

| Agility behaviour | Source |
|-------------------|--------|
| XP on completing an obstacle, none on a failure; some obstacles fail and hurt | OSRS Wiki, Agility and each course's page |
| No clicks while on an obstacle; walking to its start can't be cut short | inferred from live |
| Each move starting on the tick after the last ends; a teleport landing before a walk's first step | inferred |
| Draynor rooftop: its locs and where they stand; the roofs, from the level 3 tiles with a floor | cache 241 map |
| Draynor rooftop: levels, XP, no failing | OSRS Wiki, Draynor Village Rooftop Course |
| Draynor rooftop: each start, path and landing tile | inferred from the map |
| Draynor rooftop: the animations (by their cache names), each obstacle's ticks | guessed until recorded |
| Al Kharid rooftop: its locs and where they stand; the roofs on levels 1 to 3 (the zip line ends on level 1, the tree swings to level 2) | cache 241 map |
| Al Kharid rooftop: level 20, each obstacle's XP, which two fail and for how much, the five mark spots | OSRS Wiki, Al Kharid Rooftop Course and the Mark of grace spawns table (read 2026-11-02) |
| Al Kharid rooftop: each start, path and landing tile | inferred from the map |
| Al Kharid rooftop: the animations (human_ropeswing_long, zipline_bite and zipline_slide, by their cache names), tick counts, the fail chance (225 to 255 out of 256) and where a fall lands | guessed until recorded |
| Varrock rooftop: its locs and where they stand; the roofs on levels 1 and 3 (the first gap drops to level 1, the wall climbs back) | cache 241 map |
| Varrock rooftop: level 30, each obstacle's XP, which two fail and for how much, the five mark spots | OSRS Wiki, Varrock Rooftop Course and the Mark of grace spawns table (read 2026-11-02) |
| Varrock rooftop: each start, path and landing tile; two mark spots moved to the nearest floor tile | inferred from the map |
| Varrock rooftop: the animations, the wall's route along its top, tick counts, the fail chance (225 to 255 out of 256) and where a fall lands | guessed until recorded |
| Gnome Stronghold: its locs and where they stand (the log balance one level down off its bridge; the nets to level 1, the platforms on level 2) | cache 241 map |
| Gnome Stronghold: level 1, each obstacle's XP and the 50 XP lap bonus (the pipe at 7.5, as the course's 110.5 adds up; its own page says 10), the mark spot | OSRS Wiki, Gnome Stronghold Agility Course and the Mark of grace spawns table (read 2026-11-02) |
| Gnome Stronghold: each start, path and landing tile; the mark spot moved onto the platform's nearest floor tile | inferred from the map |
| Gnome Stronghold: the animations (human_walk_logbalance, human_reachforladder, human_doublepipesqueeze, by their cache names), tick counts | guessed until recorded |
| Gnome Stronghold: the gnome trainers' shouts | not built |
| Shortcuts: their locs and where they stand | cache 241 map |
| Shortcuts: levels and XP (the south stones' 2.5 on the first and third stone); the stone's failure landing on the far bank with damage and 1 XP | OSRS Wiki, Shortcuts and each shortcut's page (read 2026-11-02) |
| Shortcuts: each side's tile; the stones used from the bank (the river around them is blocked in the map) | inferred from the map |
| Shortcuts: the animations (human_jump_hurdle, human_longcrawl, human_steppingstonejump, human_walk_crumbledwall), tick counts, the stone's fail chance and 1 to 3 damage | guessed until recorded |
| A lap needing every obstacle; the lap progress kept across a logout | inferred, guessed |
| The level message as a game message; fail chances (the Wiki gives none); the hit after the fall; the real tile moving when a slide's ticks are up | guessed |
| Marks: the three-minute timer in whole minutes from the last mark, kept across a logout; 2/6 a lap, a fifth at 20 over; private, ten minutes, stacking resets the time | OSRS Wiki, Mark of Grace (read 2026-11-02) |
| Marks: Draynor's four spots (the start's roof, Diango's Workshop, the Slayer shop, Ned's rope shop) | the Wiki's Mark of grace spawns table |
| Marks: the spot picked at random; a failed roll leaving the timer alone | inferred |
| Not built: marks pausing on the ground while the player is logged out (ours keep counting down), the Achievement Diary rerolls | |
| Grace (`rooftops_grace`) at 3050,4962 in the den; her shop's stock, prices and 80% buy-back | mejrs NPCList_OSRS; OSRS Wiki, Grace's Graceful Clothing |
| Grace's shop inv, roguesden_shop (279); the tiles the trapdoor, door and passageway take the player to; the passageway being the way out | guessed (the 241 map shows no other exit) |

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
| A shop with a `currency` in shops.json (Grace's: marks of grace) is paid in that item and pays out in it, at the same percentages of the item's value; it buys back the untradeable items it stocks, and won't buy its currency. Its own messages name the currency (`shop.costs_in`, `shop.buys_for_in`, `shop.not_enough_currency`, `shop.cant_sell_currency`) | the Wiki's Grace's Graceful Clothing (Buys at 80%); the messages guessed |

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
| 6 giant spiders (`giantspider1`, 3017): level 2, 5 hitpoints, stab for 1, back in 30 ticks, 50% weak to fire, aggressive ([NPC aggression](#npc-aggression)); no bones, only the beginner clue at 1/128 | OSRS Wiki, Giant spider (Level 2) |
| Barfy Bill (1326) talks about who he is and about canoes, by the player's Woodcutting level: below 12, 12 to 26 (log), 27 to 41 (dugout), 42 to 56 (stable dugout), 57 and up (waka) | RSMod's script; the Wiki's Transcript:Barfy Bill and Canoeing |
| The fight animations (the squatting goblin's and the updated spider's, by the stand and walk animations their types have), the wander ranges (5, Bill 2) and Bill's chathead expressions | guessed |

Left out: canoes and Bill's Store-axe wait for canoeing. The river's fishing spots are the gathering skills'. What Bill says, and the
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
| 23 giant rats: 10 of level 3 (`giantrat`, 2856, 5 hitpoints) and 13 of level 6 (`giantrat1_2` and `_3`, 2863 and 2864, 10 hitpoints), with 4 more of level 3 (the grey `giantrat_grey` to `3`) and 4 rats west of the castle; they stab for 1 at speed 4, are aggressive ([NPC aggression](#npc-aggression); the Level 6 ones taken to match the Level 3 infobox, guessed) and come back in 30 ticks; bones and raw rat meat every time, the beginner clue at 1/128 | OSRS Wiki, Giant rat (Level 3 and 6); the DPS calculator |
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

The unicorn retreats at a quarter of its hitpoints ([NPC aggression](#npc-aggression), guessed threshold). The tutors' offers to show a place on the world
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
| Step 3: the market's shops in `shops.json`: Fortunato's Fine Wine (without the jug of vinegar, as after Rag and Bone Man II), Diango's Chronicle one to a player and the Seed Market's 1% overstock decay, the Draynor Seed Market (Olivia) and Diango's Toy Store, with their talks (`game/content/DraynorMarket.kt`) | the Wiki's shop pages and transcripts (read 2026-10-30); the shops' invs and sizes the 241 cache's |
| Diango's Disk of Returning sells at the Wiki's 21,000 coins, a fixed `price` in the shop file (the percentages give 180) | the Wiki's Diango's Toy Store; that the price doesn't move with the stock is guessed |
| Step 2: the shore's two Net/Bait spots (`0_48_50_saltfish`, 1525, whose net option is "Small Net"), fished as the swamp's, moving between four tiles | tiles from mejrs's list; the move tiles 2004Scape's `fishing_movement_draynor_village_enum` |
| The five willows by the shore chop with Woodcutting's willow data | the 241 map (ids 10819, 10829, 10831, 10833) |
| Draynor's permanent fire south of the bank (`fire_cook`, 43475): Cook asks which food as a range does, then cooks over the fire (fire burn chart and animation) | the Wiki's Permanent fire page (a Draynor fire with Cook, free-to-play) |
| Take-axe on the logs by the willows (`log_withaxe`, 5581) gives a bronze axe and leaves plain logs for 30 seconds; the same type works wherever the map has it | the Wiki's Bronze axe page ("Bronze axes on logs respawn in 30 seconds"); no message and a full inventory keeping the axe on the logs are guessed |
| Step 4: Ned (`game/content/Ned.kt`) sells a rope for 15 coins or makes one from 4 balls of wool; after buying (or trying to) he tells the yak hair story and gives a free rope with a slot for it, as after Dragon Slayer I and The Fremennik Isles; Trade opens Ned's Handmade Rope at its post-Isles 90% (16 coins) | the Wiki's Transcript:Ned and shop page (read 2026-10-30); the shop's inv is borrowed (`dmm_all_star_friends_shop`), since no 241 inv is plainly Ned's |
| Aggie (`game/content/Aggie.kt`) makes red, yellow and blue dye for 3 redberries, 2 onions or 2 woad leaves and 5 coins, from her talk, her Dyes option or an ingredient used on her, and takes 5 coins (21 to 100 held), 20 coins (101 or more) or a pot of flour for an insult | the Wiki's Transcript:Aggie; the coin check and using ingredients on her from 2004Scape's script |
| Every account has met Aggie (varbit 13597, `gobdip_met_aggie`, set at login with the quests in `Quests.AFTER_QUESTS`), so her multi-NPC shows `aggie_2ops` with Dyes | the 241 cache's NPC 4284 |
| Step 5: the jail's four guards (`jailguard` 4276 and its three patrol types, Attack only in 241) fight as `npc-combat.json` has them (Wiki infobox) and drop bones; they wander, as the patrols' routes aren't known, and are aggressive | the Wiki's Jail guard page; tiles from mejrs's list; animations guessed |
| Joe, Lady Keli and the jailed Prince Ali aren't spawned: the 241 cache's `joe`, `lady_keli` and `prince_ali_prison` show nothing after Prince Ali Rescue (varp 273) | the 241 cache's multi-NPCs |
| The cell door (`alidoor` 2881, `game/content/DraynorJail.kt`) lets the player out from inside, swinging as `poshdooropen` and shutting behind them, and is locked from outside | 2004Scape's quest_prince script; the swing and two ticks open guessed |
| Leela's talk after Prince Ali Rescue; Morgan's first talk after Vampyre Slayer, then his later one (multi-NPC on varbit 15611, `morgan_postquest_dialogue`) | the Wiki's transcripts; the varbit's use inferred from its name |

| Step 10: the Draynor Sewers' two trapdoors (`6434`, west of the jail and north of the market) open for everyone into `6435` with Climb-down, for 300 ticks, and the ladders below climb back up (`game/content/DraynorSewers.kt`); the east ladder is a tile south of the dungeon's 6400, so each climb keeps the player's side of the trapdoor or ladder | the 241 map; how long a trapdoor stays open, the open animation and where a climb lands guessed |
| The sewers' seven zombies (13 and 24), four skeletons (22 and 25) and ten rats (`npc-spawns/draynor-sewers.json`) fight and drop as the Wiki has them, aggressive and with their elemental weaknesses; the gem drop table is in `npc-drops.json` (its rows add up to 131, with the mega-rare table giving nothing) | the Wiki's Zombie, Skeleton, Gem drop table and Draynor Sewers pages (read 2026-11-01); tiles from mejrs's list; animations from the NPCs' own stand animations, the unarmed skeleton's attack guessed |
| Ruantun's talk after Desert Treasure I; the house's anvil and fire smith and cook as any other | the Wiki's Transcript:Ruantun; the 241 types |
| Step 11: Draynor Manor's grounds (`game/content/DraynorManorGrounds.kt`). The fence has no gate on the 241 map, only the gap where Veronica stands; she gives her talk after Ernest the Chicken. The front doors let the player in from the south, slam shut behind them and won't open from inside. The compost heap won't be searched by hand and gives a spade nothing; the fountain is full of dead fish | the 241 map; the Wiki's Transcript:Veronica; 2004Scape's quest_haunted script for the doors, the heap and the fountain (the doors' swing guessed) |
| The 14 undead trees (`nasty_tree` 4418, `npc-spawns/draynor-manor.json`) show as Undead tree with Chop after Animal Magnetism; they never move, can't be attacked, and swipe a player beside them for 0 to 3 unless tolerated; Chop gives the Wiki's no-axe and bounce messages | the Wiki's Undead tree page and map; how often and how far they swipe, and the even damage, guessed; the blessed axe's undead twigs left out (an Animal Magnetism item) |
| The vines south-east of the manor whip a player for 1 every 5 ticks, with `whippingplant_attack` on their tile, from entering the 8 by 8 tiles at 3112, 3336 until they are more than 9 tiles from that corner | 2004Scape's manor_vines script |

Left out until their steps: the market's stalls and guards, the Wise Old Man's talk,
the bank guard, Miss Schism, the town crier, the dark wizards,
and the rooftop course (Agility isn't built). Diango's Holiday Item Retrieval and Redeem-code are left out (no account has
holiday items to reclaim), and Olivia's refusal after a theft comes with thieving. The wheat field picks as Lumbridge's does (`Crops`).
Spria isn't spawned: after A Porcine of Interest she is a Slayer master (`slayer_master_9_active`), and Slayer isn't built.

Check: `test-runner/examples/draynor-village.json` opens and shuts the pig pen's gate, climbs both staircases, takes
garlic from Morgan's cupboard, opens Aggie's wardrobe and sees her cheese and tomato, and opens the bank from a banker
and from a booth; `HeadlessDraynorTest` does the gate, the stairs, the cupboard and the bankers through the headless
client, and `GatesTest` turns a gate every way round. `test-runner/examples/draynor-willows.json` takes the axe off
the logs, chops a willow, nets a shrimp and cooks it on the permanent fire; `HeadlessFishingTest` and `HeadlessCookingTest`
do the Small Net spots and the fire. `test-runner/examples/draynor-ned-aggie.json` buys Ned's rope both ways and gets
Aggie's red dye and fine; `NedAndAggieTest` walks every branch of both talks. `test-runner/examples/draynor-jail.json`
does Leela, the cell door, a jail guard and Morgan; `DraynorTalkTest` and `HeadlessDraynorTest` cover them too. `test-runner/examples/draynor-sewers.json`
goes down and up both sewer entrances, fights a zombie and talks to Ruantun; `HeadlessDraynorSewersTest` does the
entrances and the talk through the headless client. `test-runner/examples/draynor-manor-grounds.json` talks to Veronica,
chops at and is swiped by an undead tree, is whipped by a vine, searches the compost heap and the fountain and goes in
by the front doors; `HeadlessDraynorManorGroundsTest` does all of them through the headless client.
`test-runner/examples/draynor-manor-inside.json` climbs both staircases, takes the top floor's tinderbox, goes through
the bookcase passage and out by its lever, finds the closet locked and leaves by the back door; `HeadlessDraynorManorTest`
does the doors, the stairs, the passage and the closet's key and rubber tube through the headless client.

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
| The bank: Bank on any booth (`bankbooth` 10355) opens it; five Bankers (`kharidbanker1`/`2`, 3090/3091) behind the booths at x 3267 bank across them, by Bank or through their talk, which is the castle banker's | the engine's booths (any loc with Bank); OSRS Wiki, Banker and Transcript:Banker; 2004Scape map m51_49 spawns |
| Seven shops: the General Store (Shop keeper 2817, Shop assistant 2818), Zeke's Superior Scimitars, Dommik's Crafting Store, Louie's Armoured Legs Bazaar, Ranael's Super Skirt Store, the Gem Trader and Ali's Discount Wares (Ali Morrisane's basic stock), each with its stock, restock times and percentages; the General Store won't buy silk; leaving Ali's basic stock with 10 coins and a free slot buys one of his five pieces of junk at random (`ali_morrisane.junk`; the 10-coin price and that it happens as the shop closes are guessed) | OSRS Wiki, each shop's page; invs from the 241 cache (the General Store's generalshop3 inferred from its keepers' names) |
| Talk-to on each keeper but Ali Morrisane, as after Monkey Madness I and Rogue Trader, opening the shop where the Wiki has it open; Zeke's "Do you want to trade?" ends with his answer, as the Wiki has it | OSRS Wiki, each keeper's transcript; expressions from 2004Scape where it has the line, else guessed |
| The keepers on 2004Scape's tiles, indoors; Ali Morrisane by his stall | 2004Scape maps m51_49/m51_50; Ali's tile guessed from the 241 map's stall and The Feud's quest icon |
| Karim (`kebab_seller`, 2877) sells a kebab for 1 coin through his talk; the Silk trader (2873) sells silk for 3 gp, or 2 after haggling; both as after Rogue Trader; no message when Karim sells | OSRS Wiki, Transcript:Karim and Transcript:Silk trader; tiles from 2004Scape map m51_49/m51_50 |
| Eating a kebab: "You eat the kebab", then out of 32: 1 heals 7 + 24% of the Hitpoints level and raises melee by 2, 8 heal 6 + 14%, 20 heal 3 + 7%, 1 does nothing, 1 drains a random skill by 3, 1 drains melee by 3 and a random skill by 4; with the eat and attack delays of other food | OSRS Wiki, Kebab (also the random skill's floor of 2 and no "heals some health" at full Hitpoints); rounding down, the +2 cap at base + 2 and the melee drain's floor of 1 guessed |
| Ellis (`ellis_tanner`, 3231): his talk (with hides carried, an offer to tan them; without, two topics and Ali Morrisane); Trade, or a hide used on him, opens the tanning screen (interface 324); leather or any other item used on him gets a line | OSRS Wiki, Ellis and Transcript:Ellis; tile from 2004Scape map m51_49 |
| Tanning: soft leather 1, hard leather 3, snakeskin 15 (snake hide) or 20 (swamp snake hide), each dragon leather 20 coins a hide; Tan 1, 5, X and All take as many of the slot's hides as held and paid for, at once | OSRS Wiki, Ellis; 2004Scape's tan_leather and its messages; the screen's components by their 241 names; the slots' order, model zoom, names, price form and closing the screen after a tanning guessed |
| Chancellor Hassan (4285) in the throne room: his one line after Prince Ali Rescue; Enter on a big window (`kharid_bigwindow` 33348) gets his refusal and a box saying the shortcut needs the hard Desert diary and 70 Agility (diaries aren't built, so it always refuses) | OSRS Wiki, Transcript:Chancellor Hassan; tile from 2004Scape map m51_49 |
| Osman (4286) outside the palace, as after Contact!: the Sophanem talk with a Keris (10581) for 20,000 coins and Menaphos, and the Sorceress's Garden talk; he takes every sq'irk juice held for Thieving experience (350, 1,350, 2,350, 3,000 a glass of winter, spring, autumn, summer); no free Keris (every account counts as having taken Kaleef's, guessed) | OSRS Wiki, Transcript:Osman and Sq'irk juice; tile from 2004Scape map m51_49; a full backpack refused only when paying won't free a slot, guessed |
| Prince Ali home in the throne room: the map's `prince_ali_palace` (11645) shows as Prince Ali (11580) once varp 273 is 100 or more; two lines and the player's answer | 241 cache; OSRS Wiki, Transcript:Prince Ali Rescue; his tile guessed (the Wiki gives none) |
| The palace's item spawns: a cabbage in the west room, a jug of water in the east room; its chests, drawers and ladder work as Lumbridge Castle's | OSRS Wiki, Cabbage and Jug of water; tiles from 2004Scape map m51_49; respawn guessed |
| Fighters: nine Al Kharid warriors (3292) in the palace (slash, max hit 2, back in 25 ticks), six men (`al_kharid_man` 3261, fighting and dropping as any man), nine level 14 scorpions (3024) in the mine and one south of the bank (stab, max hit 2, back in 35 ticks); the warriors drop the man's table, scorpions only an ensouled head (1/25) and a beginner clue (1/100) | OSRS Wiki, Al Kharid warrior, Man and Scorpion, and the DPS calculator; tiles from 2004Scape maps m51_49 to m51_51; the warrior's block animation and the level 14 scorpion's respawn guessed |
| Camels: the plain Camel (2835) north of town and Al, Elly, Ollie, Cam and Alice (Alice is `alice_the_camel_multinpc` 5953, by varbit 1353); Talk-to without a camulet is a menu of three: dung (it doesn't understand), something unpleasant (one of three lines and one of three reactions, at random) or neither | OSRS Wiki, Camel, each camel's page and Transcript:Camel; tiles are 2004Scape's camel tiles matched to the Wiki's words for each (guessed) |
| The Al Kharid mine's silver rocks (level 20, 40 XP, 25/200) and gold rocks (level 40, 65 XP, 7/75), each back in a minute; its other rocks, the furnace and the ranges work as elsewhere | OSRS Wiki, Silver rocks and Gold rocks (the pairs fit their charts' two points exactly) |
| Two Small Net/Bait fishing spots (`0_51_49_saltfish` 1528) by the sea south-west of town, moving between six tiles; Small Net is Lumbridge Swamp's Net | 241 cache; 2004Scape's spawn tiles and fishing_movement_al_kharid_enum (live's tiles since March 2021) |
| Search on the boxes (`boxes3` 361) by Ali Morrisane's stall: nothing in them | 2004Scape's crates.rs2 |
| The Shantay Pass, north side: Shantay's talk (first talk and later), the outlaw arrested into the cell west of his hut and fined 5 coins (from the backpack, else the bank, else the Port Sarim prison), Trade (the Shantay Pass Shop: its 20 items, 100%/70%/1%) and Buy-pass (5 coins); the cell door locked while jailed | OSRS Wiki, Transcript:Shantay and Shantay Pass Shop; 2004Scape's shantay.rs2 for the flow; the cell's inside tile and the narrated lines as boxes guessed |
| The Port Sarim prison: the player lands in a cell; Pick-lock on its door (`port_sarim_jaildoor` 9565) gets them out, 4 Thieving XP (0.5 on a fail), the chance rising from 0% to 82% over four tries at level 1 and from 39% to 127% at 99 | OSRS Wiki, Door (Port Sarim jail); the arrival tile is 2004Scape's; how the chance rises between tries and levels guessed; the messages' words to come from a recording |
| The gate (4031) and its guard (`shantay_guard_still` 4648, Talk-to and Pass): no pass, the guard sends the player to Shantay; with one, the desert isn't built yet, so the gate says so (our words) and the pass is kept; from the desert side the gate always lets the player north; Look-at shows the poster | OSRS Wiki, Transcript:Shantay Guard; 2004Scape's shantay_pass.rs2 (Look-at's boxes guessed for live) |
| Three Shantay Guards (4643, level 22: stab, max hit 3, back in 50 ticks, bones), one line each; the bank chest's Use opens the bank and its Collect the collection box; Irena (as after Tourist Trap) one line | OSRS Wiki, Shantay Guard, Transcript:Shantay Guard and Transcript:Irena; tiles from 2004Scape map m51_48 |
| The Emir's Arena and the glider as stubs: Captain Dalbur (`pilot_al_kharid` 6089, shown as after The Grand Tree) talks, and his Glider, Glider-to and the glider answer in his talk say the gliders don't fly anywhere built yet (our words) in place of the map; Arena Guard Fronk (11664), the Duel Guide (11673) and the 1v1 Tournament Guide (11672) talk; the recruitment boards, staging area steps and scoreboard say the Arena isn't open yet (our words); the fire altar's ruins are scenery | OSRS Wiki, Transcript:Captain Dalbur, Transcript:Duel Guide, Transcript:1v1 Tournament Guide, Transcript:Arena Guard Fronk; Dalbur's tile from mejrs's live NPC list; the Arena people's tiles guessed by the 241 cache's boards and gate |
| The palace's large double doors (`bankdoor_l`/`_r` 1513/1511, open `openbankdoor_l`/`_r` 1516/1512) open together; the town's curtains (`desertdoorclosed` 1533 / `desertdooropen` 1534) open and close | 241 cache names; the double-door swing is the engine's (see Doors) |

Left out: the gate's sound (no recorded id yet), the Pay-toll option and the 10 coins (before-quest only); Ali
Morrisane's talk and his Rogue Trader shops (blackjacks, runes, desert and Menaphite gear); the Al Kharid flyer used on a
keeper, and the keepers' treasure trail steps. The words of
the guards' talk and the examines of the guards and the gate are in the dialogue file (keys `border_guard.*`,
`examine.npc_4287`, `examine.loc_44050`, `examine.npc_3090`), as are the shop keepers' (`al_kharid_shop_keeper.*`,
`al_kharid_shop_assistant.*`, `zeke.*`, `dommik.*`, `louie_legs.*`, `ranael.*`, `gem_trader.*` and each keeper's examine), Karim's and the Silk trader's (`karim.*`, `silk_trader.*`) and the kebab's
messages (`kebab.*`), and Ellis's talk and the tanning screen's words (`ellis.*`, `tanning.*`), the palace's (`hassan.*`, `osman.*`, `prince_ali.*`), the camels' (`camel.*`), the boxes' (`search.boxes_empty`), and the Shantay Pass's (`shantay.*`, `shantay_guard.*`, `shantay_gate.*`, `irena.*`, `port_sarim_jail.*`), and the Arena's (`dalbur.*`, `duel_guide.*`, `tournament_guide.*`, `fronk.*`; `arena_stub.*` are ours).
Also left out from the palace: filling vessels at the fountain (no water sources are built yet), the big-window shortcut itself, Osman's Crack the Clue step, and the Emir, who is never seen. From the fighters and camels: Pickpocket on men and warriors (Thieving comes with the Draynor piece), the warriors joining in when one is attacked (NPC aggression, the Varrock piece), the camulet's talk and camel dung (members, Enakhra's Lament and My Arm's Big Adventure), and Cam's treasure trail step. From the mine: its agility shortcut (`alkharid_mine_sc_*`). From the Shantay Pass: the desert beyond the gate (the poster, the disclaimer and the pass handed over come with the Desert piece), the Tourist Trap lines, treasure trail steps, the Kalphite cave, the cacti, and the guards' aggression. From the Arena: its fights (PvP Arena worlds only), the five other guards' talks, Joby and Beni, Chris and his Lost Property shop, and the glider map itself (none of its destinations is built).

Check: `test-runner/examples/al-kharid-toll-gate.json` goes through the gate both ways with no coins, through a Border
Guard's talk, and opens and shuts the palace's west doors and a curtain; `al-kharid-bank.json` banks at a booth, through a banker's
Bank and through a banker's talk; `al-kharid-shops.json` buys and sells at every shop; `al-kharid-traders.json` buys and eats a kebab and haggles for
silk; `al-kharid-tanning.json` tans cowhides at Ellis's; `al-kharid-palace.json` talks to Hassan and Prince Ali, tries a big
window and buys a Keris from Osman; `al-kharid-fighters.json` examines and kills the fighters and talks to Al the Camel;
`al-kharid-mine-fish.json` mines gold and silver, nets shrimps and searches the boxes; `al-kharid-shantay-pass.json` is sent to Port Sarim's prison and picks its lock, buys a pass, tries the gate both ways and uses the bank chest; `al-kharid-arena-glider.json` talks to Captain Dalbur and the Arena's people and gets the stubs;
`HeadlessAlKharidShopsTest` opens the Gem trader's shop from his
talk, tries silk at the General Store and buys Ali's junk; `HeadlessAlKharidTradersTest` buys a kebab from Karim and eats it,
and haggles for silk; `HeadlessEmirsArenaTest`, `HeadlessShantayPassTest`, `HeadlessAlKharidTest` and `HeadlessAlKharidFightersTest` do the same through the headless client, with the talk in
`BorderGuardTest` and `AlKharidShopkeepersTest`, Karim, the Silk trader and the kebab's outcomes in `AlKharidTradersTest`, Ellis and tanning in `EllisTest` and, through the headless client, `HeadlessEllisTest`, the palace's talk in `AlKharidPalaceTest` and Osman's Keris in `HeadlessOsmanTest`, the camels' in `AlKharidCamelsTest`, combat and drops in `NpcCombatTest` and `NpcDropsTest`, the rocks' chances in `MiningTest` and their ores in `HeadlessMiningTest`, the boxes in `HeadlessAlKharidBoxesTest`, the Shantay Pass's talk and jail in `ShantayPassTest`, the Arena's talk in `EmirsArenaTest`, Small Net in `HeadlessFishingTest`, and
the prices in `ShopTest`.

## Edgeville

The Misthalin roadmap's Edgeville and Barbarian Village piece, built a step at a time
([plan](https://claude.ai/artifact/VuHFKCTxpc1H5eMeBBP6LS)). Step 1 is the town and its bank: `npc-spawns/edgeville.json`,
`obj-spawns/edgeville.tsv`, `game/content/Edgeville.kt` and, for every haystack, `game/content/Haystacks.kt`.

| Behaviour | Source |
|-----------|--------|
| Four bankers (`banker1` 1613, `banker2` 1618) behind the booths at 3095, 3489 to 3491 and 3096 to 3098, 3493, with the castle banker's talk and Bank option; the booths and the deposit box work as every booth does | the 241 cache's types and booths; tiles from mejrs's data_osrs NPC list (NPCs seen on live); the Wiki's Edgeville page for the count |
| Seven men in the grange north of the bank, three rats and three level 2 giant spiders round the house east of the willows, four imps round the town | as above; the Wiki's Edgeville page (seven men) |
| The grange's front doors (`castledoubledoorl`/`r` 1521/1524, open 1522/1525) open together | 241 cache names; the double-door swing is the engine's (see Doors) |
| Search on a haystack or hay bales (`haystack`, `haystack2`, `haystack3`, `haybail`: 298 to 300 and 304), here and on the farms north of Lumbridge: a needle 10% of the time, a prick for 1 hitpoint 2% of the time, else nothing | OSRS Wiki, Haystack (it names haystacks; the hay bales sharing it is inferred); one roll for all three, prick first, no needle with a full backpack, no animation: guessed |
| The mausoleum's coffins (`coffin` 398) open into `coffin_open` (3577, Close only) and close again; the east house's wardrobes (`spookywardrobe` 388/389) open, are empty and shut | 241 cache; opening and shutting as other furniture does (`Searching`) |
| A pair of leather gloves south of the bank and an iron mace in the jail, back 100 ticks after taking | the Wiki's Edgeville spawn list; tiles from 2004Scape's map m48_54; respawn guessed |
| Cross on the Wilderness Ditch (`ditch_wilderness_cover` 23271, along the whole southern border) refuses with a message of our own: the Wilderness isn't built | ours, as Shantay Pass refuses the desert |
| Evil Dave's and the other houses' doors, Doris's yard gate, the grange's ladders, crates and boxes, the bookcases | the engine's doors by name, `Gates`, and the castle's and Draynor's types |
| Wander ranges (3 indoors in the grange, 5 outside) | guessed |

Left out until their steps: Perdu,
the nettles, the well, Doris, Lucien and Hari, the
outlaws, the Basement of Doom, the monastery, the trapdoor to the Edgeville Dungeon, and the lever, fairy ring, Soul
Wars portal, Death's Domain and canoe station. The words are in the dialogue file (keys `edgeville.*`, `haystack.*`).

Check: `test-runner/examples/edgeville-town.json` opens the grange's doors, searches the haystack, shuts the doors,
opens and shuts a coffin, takes the leather gloves, opens and searches a wardrobe, is refused at the ditch, and opens
the bank from a banker's talk and from a booth; `HeadlessEdgevilleTest` does the doors, all three haystack outcomes by
seed, the coffin, the wardrobe and the ditch through the headless client, `NpcSpawnFileTest` the spawns and
`ObjSpawnFilesTest` the item spawns.

Step 2 is the town's shops, `game/content/EdgevilleShops.kt` for their talk and `shops.json` for their stock.

| Behaviour | Source |
|-----------|--------|
| The Edgeville General Store (inv `generalshop5`, keepers `generalshopkeeper5`/`generalassistant5` 2821/2822): Al Kharid's stock with a knife after the hammer, sells at 130%, buys at 40%, 3% change; the keepers' talk is every general store's | OSRS Wiki, Edgeville General Store (read 2026-10-30); the inv name is inferred from the keepers' |
| Oziach's Armour (inv `runeplateshop`, 3 slots): 2 rune platebodies, 2 green d'hide bodies, 35 anti-dragon shields, 130%/40%/3%; Trade, or Talk-to's first option, opens it (every account has Dragon Slayer I done); his other three options answer and end | OSRS Wiki, Oziach's Armour and Transcript:Oziach; the Wiki notes no expressions, so those are guessed |
| Richard's Wilderness Cape Shop (`wilderness_capeseller_6` 2200, inv `wildernesscapeshop6`): 100 each of the team-6, 16, 26, 36 and 46 capes for 50 coins, 100%/60%/1%; his talk explains the capes and asks again, and yes opens the shop | OSRS Wiki, Richard (merchant) and his transcript; the inv name is inferred |
| Evil Dave's stove in Doris's house west of the bank (`100_dave_stove` 12269, 3077, 3495) cooks as a range; the range upstairs in the General Store (`range` 26181) already did | 241 cache (Cook option); range rules guessed as every range's |
| The furnace (16469), yews and willows work as they did already | `Smithing.FURNACES` and `woodcutting.json` |
| Six guards (`guard1` 3254) along the Wilderness Ditch north of the town, fighting and dropping as Varrock's guards | tiles from mejrs's NPC list; the Wiki's Edgeville page (six); combat and drops are the Varrock piece's Guard; wander range guessed |
| Perdu stands upstairs in the General Store with no talk yet | mejrs's NPC list for the tile; his lost-property shop is left out |

The words are in the dialogue file (keys `edgeville_shop_keeper.*`, `edgeville_shop_assistant.*`, `oziach.*`,
`richard.*`). Check: `test-runner/examples/edgeville-shops.json` buys and sells a knife at the General Store, talks to
its assistant into the shop, buys and sells an anti-dragon shield at Oziach's, takes two of his talk's options, and
talks to Richard into his shop for a cape, then chops a yew, smelts a bronze bar and cooks on Evil Dave's stove; `EdgevilleShopsTest` does each talk's options, `HeadlessEdgevilleShopsTest` the talk into each shop through the headless client, `HeadlessCookingTest` the stove, and `ShopTest` the stock and
the Wiki's prices.

Step 3a is the town's people and the places not built yet, `game/content/EdgevillePeople.kt`.

| Behaviour | Source |
|-----------|--------|
| Doris (`hundred_dave_mum` 4808) in her house west of the bank: four options, each answered once | OSRS Wiki, Transcript:Doris; mejrs's NPC list for the tile; expressions guessed |
| The Basement of Doom below Doris's house: the trapdoor (`100_dave_celler_trapdoor` 12266, 3077, 3493) is each player's own on varbit 1888 (`hundred_dave_trapdoor`), Open, Close and Go-down; the cellar stairs (12265) climb back up | 241 cache (multiloc, children 12267/12268, ops); the landing tiles (3077, 9893 and 3078, 3493) and the open/close animations are guessed |
| Evil Dave (`hundred_evil_dave` 4806) talks as after Recipe for Disaster: the basement and demon-summoning menu, giving up evil, the time-travel menu (looping), and one of five random farewells | OSRS Wiki, Transcript:Evil Dave (which box is whose, from the transcript); expressions guessed |
| 18 hell-rats and four Hell-Rat Behemoths in the basement, with no options (they wait for cats) | mejrs's NPC list for the tiles; 241 cache (no ops); the Wiki's Basement of Doom page (four behemoths) |
| Hari (`canoeing_hari` 1325) by the canoe station: who he is, and canoes by the Woodcutting level (Barfy Bill's bands) | OSRS Wiki, Transcript:Hari |
| Lucien (`ikov_lucien2` 13644, a multi-NPC on varbit 10753 `ikov_lucien_vis`) in the house east of the willows, Talk-to only, as after Temple of Ikov on his side; a new Pendant of Lucien (86) if the player owns none | OSRS Wiki, Transcript:Lucien; that 1 means "after the quest, his side" is inferred, and the side is our pick |
| Refusals, in our own words: the Wilderness lever (26761), the fairy ring (29495, all four options), the Soul Wars portal (40474), Death's Domain (39547) and the canoe station (12166) | ours: none of those places is built yet |

The words are in the dialogue file (keys `doris.*`, `evil_dave.*`, `hari.*`, `lucien.*`, `edgeville_closed.*`). Check:
`test-runner/examples/edgeville-people.json` talks to Doris, opens, shuts and goes down the trapdoor, talks to Evil
Dave, climbs the stairs, talks to Lucien and Hari, and tries each refused place; `EdgevillePeopleTest` does each talk's
branches and `HeadlessEdgevillePeopleTest` the trapdoor, stairs, refusals, Doris and Lucien through the headless client.

Step 3b is the outlaws' camp south-east of the town: spawns in `npc-spawns/edgeville.json`, combat and drops in
`npc-combat.json` and `npc-drops.json`.

| Behaviour | Source |
|-----------|--------|
| Ten level 32 outlaws (`surok_outlaw1` to `10`, 4167 to 4176), one of each, in the camp at about 3116 to 3124, 3470 to 3477; not aggressive | mejrs's NPC list for the tiles; 241 cache (Attack only); the Wiki's Outlaw page; wander range guessed |
| They crush for up to 3 (Strength 30, strength bonus -21), attack speed 4, no defence bonuses, back 4 ticks after the killing blow (the Wiki's 2, raised to the time the body stays, as for rats) | OSRS Wiki, Outlaw and the DPS calculator's 4167 (read 2026-11-01); the punch and block animations guessed |
| The Wiki's XP bonus of -97.5%: a fortieth of the combat XP for their damage, Hitpoints' too, rounded down in tenths each hit (`xpBonus` in `npc-combat.json`, for any NPC) | OSRS Wiki, Outlaw; that it covers Hitpoints and not a spell's base XP is inferred |
| Bones always and one roll: rope 46/128, the herb table 32/128, fishing bait, coins (5, 15, 25), cabbage, runes, a bronze med helm, copper ore, a knife | OSRS Wiki, Outlaw (members: herbs for the free-to-play 10 coins); its lines come to 127/128, so 1/128 of nothing is inferred |

Left out: the shout before an attack, whose accuracy boost the Wiki has not confirmed. Check:
`test-runner/examples/edgeville-outlaws.json` attacks an outlaw and kills it for its bones; `NpcCombatTest` and
`NpcDropsTest` check the numbers, `CombatMathTest` the XP bonus, and `HeadlessEdgevilleOutlawsTest` kills one through
the headless client for its 2 Attack XP.

Step 4 is Edgeville Monastery, west of the town, `game/content/EdgevilleMonastery.kt`.

| Behaviour | Source |
|-----------|--------|
| Six monks (`monk` 2579: four in the courtyard, two upstairs), Abbot Langley (2577), Brother Althric (1397) in the rose garden and Brother Jered (2578) upstairs | mejrs's NPC list for the tiles (it saw six monks; the Wiki says five); wander ranges guessed |
| A monk's talk: three options; the heal gives 2 and a fifth of the Hitpoints level, to the level at most | OSRS Wiki, Monk and Transcript:Monk; rounding down guessed |
| Monks fight back: level 5, 15 hitpoints, crush for up to 1, back in 25 ticks, bones | OSRS Wiki, Monk and the DPS calculator's 2579; the punch animations guessed; their heal of 2 now and then in a fight is left out (the Wiki gives no chance) |
| Abbot Langley: the heal (as a monk's, guessed), where the player is, going upstairs (until joined) and the spirit shields (each spectral, arcane or elysian spirit shield in the backpack broken for its sigil) | OSRS Wiki, Transcript:Abbot Langley; with no shield, the yes does nothing (guessed) |
| Joining the order at 31 Prayer, kept on varbit 4907 (`monastery_enter`); until then the south-eastern ladder (2641) brings the Abbot's line and join menu and the south-western one a monk's line; once joined both climb | OSRS Wiki, Transcript:Abbot Langley and Transcript:Monk; that varbit 4907 is the joined flag is inferred from its name |
| Brother Jered: blesses an unblessed symbol (1716) into a holy symbol (1718), and at 99 Prayer sells the Skillcape of Prayer and hood for 99,000 coins (trimmed when another skill is 99) | OSRS Wiki, Transcript:Brother Jered |
| Brother Althric's talk about his roses | OSRS Wiki, Transcript:Brother Althric (its standard talk) |
| The upstairs altar (2640): prayer back to 2 above the level | OSRS Wiki, Edgeville Monastery |
| A monk's robe top and bottom upstairs, back 100 ticks after taking; the garden's cabbages (1161) picked, back in 60 ticks, as every cabbage field's now | the Wiki's item pages; tiles from 2004Scape's map m47_54; the Cabbage page's 40 to 80 ticks, 60 guessed |

Left out: the roses' Take-seed (Garden of Tranquillity), the Abbot's clue, letter and Corrupted Helm talk, the
Kharid scorpion and the ghost (quests). The words are in the dialogue file (keys `edgeville_monk.*`,
`abbot_langley.*`, `brother_jered.*`, `brother_althric.*`, `crop.cabbage_*`). Check:
`test-runner/examples/edgeville-monastery.json` is refused at the ladder, joins, climbs, takes a robe, prays, talks to
each of the people and picks a cabbage; `EdgevilleMonasteryTest` does each talk's branches and
`HeadlessEdgevilleMonasteryTest` the ladders, joining and the altar through the headless client.

Step 5 is Barbarian Village's buildings and people, `game/content/BarbarianVillage.kt`, `npc-spawns/barbarian-village.json`
and `obj-spawns/barbarian-village.tsv`.

| Behaviour | Source |
|-----------|--------|
| Peksa's Helmet Shop (Peksa 2872, inv `helmetshop` 10): bronze to adamant med and full helms at 100%, bought at 60%; his yes opens it, his no says goodbye | OSRS Wiki, Peksa's Helmet Shop (ShopTest checks every sold-at price) and Transcript:Peksa |
| The talk of Tassie Slipcast (1352, her studio), Hunding (3063, the top of the lookout tower; both his menus), Litara (`sos_barb` 3363, before the Stronghold is finished), Checkal (`bim_checkal_barb` 10645) and Sigurd (1324, by the canoe station; his story and his teaching by Woodcutting level) | OSRS Wiki, each one's transcript; Checkal as after Below Ice Mountain; the yes after Sigurd's story going into the teaching is inferred from the Wiki's "Same as below" |
| Atlas (10658) in the longhall, as after Below Ice Mountain: his offer of a workout for 25,000 coins; with the coins, a message of ours (the workout is a cutscene that isn't built) and no coins taken | OSRS Wiki, Transcript:Atlas |
| The longhall's double doors (`fai_barbarian_large_doubledoor_l`/`r` 11621/11620, open 11625/11624) open together; the huts' doors by name; the lookout tower's ladders (`ladder_directional` 17026, `laddertop_directional` 16685) over three levels; Peksa's small crates (11600) searched empty | 241 cache types and places |
| Three beers and two cooked meats on the longhall's tables, a bronze pickaxe in the spinning hut and a pot in Peksa's shop, back 100 ticks after taking | the Wiki's Barbarian Village spawn list; tiles from 2004Scape's map m48_53; respawn guessed |
| The spinning hut's wheel, Tassie's potter's wheels and pottery oven, and the fires in Peksa's shop and the longhall, work as everywhere | `Crafting` and `Cooking` by name |
| Tiles, wander ranges (1 to 2) and expressions | mejrs's NPC list for the tiles; the rest guessed |

Left out: Sigurd's Store-axe, Hunding's beginner clue, the barbarian anvil (members' Barbarian Smithing), Litara's talk
after the Stronghold (step 14), and the barbarians, Gunthor and the unicorns (step 7). The words are in the dialogue
file (keys `peksa.*`, `tassie_slipcast.*`, `hunding.*`, `atlas.*`, `checkal.*`, `litara.*`, `sigurd.*`). Check:
`test-runner/examples/barbarian-village.json` buys a helmet, spins wool, throws and fires a pot, climbs the tower to
Hunding and talks to each of the others; `BarbarianVillageTest` does each talk's branches and
`HeadlessBarbarianVillageTest` the shop, the longhall's doors and the tower through the headless client.

Step 6 is the real multi-way areas and the client's multi-way icon, `game/combat/MultiCombat.kt` and
`multi-combat.tsv` (one box a line, with where it came from; other pieces add theirs as their content comes).

| Behaviour | Source |
|-----------|--------|
| The whole of Barbarian Village is multi-way: map square 48_53 (3072, 3392 to 3135, 3455), ground level | OSRS Wiki, Barbarian Village ("The entire village is a multicombat area"); the box from 2004Scape's `data/src/maps/multiway.csv`; that live's box is the same is inferred |
| The Stronghold of Security's first floor is multi-way: map square 29_81 (1856, 5184 to 1919, 5247), which holds no other floor | OSRS Wiki, Multicombat area ("The first level of the Stronghold of Security"); the whole square is inferred. The Stronghold itself comes in step 12 |
| Edgeville, the Monastery and the land between stay single-way | OSRS Wiki, Multicombat area (none of them listed) |
| The client's crossed swords show while the player stands in a multi-way area (`multiway_indicator` 4605, bit 5 of varp 1021), set every tick and sent when it changes, so a step, a teleport or `::multi` all show at once | 241 cache varbit; when live sends it is inferred |

`::multi` still marks a zone multi-way until the server restarts, on top of the file's areas. Check:
`test-runner/examples/edgeville-multi.json` reads the icon's varbit in Barbarian Village, at Edgeville bank and back
again after a walk in; `MultiCombatTest` checks the boxes' edges and `HeadlessMultiIndicatorTest` the varbit through
the headless client.

Step 7 is Barbarian Village's fighters: `npc-combat.json`, `npc-drops.json`, `npc-spawns/barbarian-village.json` and
the barbarians' and Gunthor's talk in `game/content/BarbarianVillage.kt`.

| Behaviour | Source |
|-----------|--------|
| 17 barbarians (`fai_barbarian_1` to `_14` and `_female_1` to `_4`, 3055 to 3072 less Hunding): levels 9, 10, 15 and 17, crush at speed 4 (Aitan, level 15, shoots), max hits 2 and 3, respawn 50 | OSRS Wiki, Barbarian, and the DPS calculator's entries; tiles from mejrs's live NPC list |
| Their attack animations by what they stand with: a greataxe's crush (`barrow_dharok_ready`), a two-handed weapon's (`human_dh_weapon_ready`), a staff's pound (`human_staffready`), Aitan's bow with bronze arrows | inferred from their stand animations; the blocks, Aitan's arrows and his 7-tile range guessed |
| Talk-to: one of six lines, picked at random, then he attacks; walking away mid-line stops the attack | OSRS Wiki, Transcript:Barbarian; even chances guessed |
| Gunthor the brave (299) in the longhall: level 29, slash at speed 6, max hit 4, aggressive, respawn 50; one of six talks at random, then he attacks | OSRS Wiki, Gunthor the Brave and Transcript:Gunthor the brave; his sword's animations guessed |
| Drops: levels 9 and 10 the Wiki's Level 8, 9 and 10 table (beginner clue 1/75); Gunthor his own (beginner clue 1/30); levels 15 and 17 Gunthor's table with a 1/55 beginner clue. The gem table's talisman is the nature talisman above ground (`gems_surface`) | OSRS Wiki, Barbarian and Gunthor the Brave. The Wiki's Level 15 and 17 section couldn't be read in full: their table is inferred from Gunthor's and osrsreboxed-db's 2021 lists, which match it row for row bar the tin ore |
| Four level 5 goblins east of the village (`fai_barbarian_goblin_armed_1` to `_4`, five spawns) fight as the level 5 goblin and drop goblin table 1; two unicorns north of the village fight and drop as Lumbridge's | the Wiki's Goblin and Unicorn pages; the goblins' stats and table inferred (the DPS calculator has no entry for them; osrsreboxed-db's 2021 list matches table 1) |

Left out: the medium clue's key (that clue step isn't built), and the mega-rare drop table's rune spear, shield left half
and dragon spear (it isn't built; its roll gives nothing). The words are in the dialogue file (keys `barbarian.*`,
`gunthor.*`). Check: `test-runner/examples/barbarian-fighters.json` talks to a barbarian and is attacked, attacks
Gunthor and is fought back, and sees the goblins and unicorns; `BarbarianVillageTest` does every line and talk,
`NpcCombatTest` the max hits and `HeadlessBarbarianVillageTest` a barbarian's attack once his line is over.

Step 8 is Barbarian Village's mine, rod fishing and permanent fire.

| Behaviour | Source |
|-----------|--------|
| The mine's four tin and four coal rocks (`tinrock1`/`2`, `coalrock1`/`2`) mine as every rock of their types | 241 map; `Mining.ROCKS` |
| Two Lure/Bait spots (`0_48_53_freshfish` 1526) on the River Barbarian, trout and salmon by Lure and pike by Bait as the River Lum's, moving between five tiles | mejrs's live NPC list for the spawn tiles; 2004Scape's `fishing_movement_barbarian_village_enum` for the five |
| The permanent fire by the fishing spots (`fire_cook` 43475, 3106, 3432) cooks from its Cook option, as Draynor's; the longhall's two fires (`fire` 26185) take food used on them | OSRS Wiki, Permanent fire (two in the main table, one with Cook); the 241 map |
| The lookout tower's ladders climb only on the tower's tiles: the same directional ladder types elsewhere give "nothing interesting" until their own areas come | the exploit review's round 11 (R11C-1); 44 instances in the 241 map |

Check: `test-runner/examples/barbarian-mine-fish.json` mines tin and coal, lures a fish and cooks it on the fire, and
climbs the tower; `NpcSpawnFileTest` puts the spots on their area's tiles and `HeadlessBarbarianVillageTest` refuses
the ladder type away from the tower.

Step 9 is the Cooks' Guild and the land between Barbarian Village and Varrock. Content is in
`game/content/CooksGuild.kt`, spawns in `npc-spawns/cooks-guild.json` and `obj-spawns/cooks-guild.tsv`.

| Behaviour | Source |
|-----------|--------|
| The front door (`chefdoor` 24958) lets in 32 Cooking wearing a chef's hat or golden chef's hat, a Cooking cape or hood, Varrock armour 3 or 4, or a max cape or hood; the Head chef says one of three refusals otherwise; the way out has no check | OSRS Wiki, Cooks' Guild and Transcript:Head chef; the max capes are inferred (every `skillcape_max` kind in the cache) |
| The Head chef's talk: his cape below 99; at 99 the Skillcape of Cooking and hood for 99,000 coins (trimmed with another 99), and a free hood for an owner | Transcript:Head chef; expressions guessed |
| The north-east room's door (`fai_varrock_cook_guild_door` 10045) lets 99 Cooking through either way, with a message for the rest; the hard Varrock Diary way in isn't built | the Wiki; the message's words not yet found (key `cooks_guild.bank_door`), its walk-through guessed |
| The spiral stairs over three floors, only on the guild's tile; the ranges (`fai_varrock_range` 7183) cook as every range | 241 map; the exploit review's rule on types that stand elsewhere |
| The hopper, its controls and the flour bin make flour as Mill Lane Mill's, the bin's flour shared | the Wiki, Windmill |
| Romily Weaklax's Trade opens the Pie Shop; his Talk-to and pie tasks aren't built | OSRS Wiki, Pie Shop (read 2026-11-02); inv `shop_pies` 352 |
| The Head chef, Romily, the two bankers, and the squirrels and raccoons in the woods west of the village (scenery, Examine only) | mejrs's live NPC list |
| The tables' item spawns: a chocolate bar, cooking apples, a bowl, a cake tin, a pie dish, a jug, a pot and grapes | 2004Scape's map m49_53; respawns guessed |

Left out: the dairy churn, Marley and the recruiters. Check: `test-runner/examples/cooks-guild.json` does the door,
the stairs, the flour, the north-east door, the range and the Pie Shop; `CooksGuildTest` every line of the Head chef
and the door check, and `HeadlessCooksGuildTest` the doors, the stairs (and their type elsewhere) and the Pie Shop.

Step 10 is the Edgeville Dungeon's north and middle, south of the gate into the Wilderness's part (z 9917). Content is
in `game/content/EdgevilleDungeon.kt`, spawns in `npc-spawns/edgeville-dungeon.json`.

| Behaviour | Source |
|-----------|--------|
| The trapdoor south of the bank (`trapdoor` 1579, 3097, 3468) opens into `trapdoor_open` 1581 and closes; Climb-down lands north of the ladder below (3097, 9868), whose type climbs back up as the Lumbridge cellar's; the trapdoor type elsewhere climbs nowhere | 241 map (the ladder stands against the passage's south wall); the landing tile guessed |
| The metal gates (`metalgateclosedl`/`_r` 1568/1569 into `metalgateopenl`/`_r` 1571/1572) at 3103, 9909 and 3145, 9870 open and close both leaves | 241 cache names; the swing is the double doors' |
| The odd looking wall (`oddwall_l`/`_r` 1736/1734, 3094, 9895 and 9896): Push opens both halves for two ticks and puts the player on the other side | 241 cache (the open types have no options); timing and the pass guessed |
| The north gate (`membergatel`/`_r` 1727/1728, 3131 and 3132, 9917) refuses in our own words (key `edgeville_dungeon.gate_closed`) until the Wilderness is built | ours |
| Zombies (18: `zombie2`, `_b`, `_c`; 24), skeletons (21: `skeleton_unagressive*`; 22; 25), hobgoblins (28 and 42), rats, giant rats and giant spiders fight and drop as their Wiki infoboxes and drop tables; the level 21 skeletons' and the level 42 hobgoblin's tables are partly inferred (see the files' sources) | OSRS Wiki, the DPS calculator, osrsreboxed; mejrs's live NPC list for the tiles |
| The mine's copper, tin, iron, coal, silver, mithril and adamantite rocks mine by type; Vannaka stands without talk | 241 map; `Mining.ROCKS` |

Left out: the Wilderness half (chaos druids, the thug and everything past the north gate), Slayer (Vannaka's talk),
the Agility pipes and monkey bars, and Obor. Check:
`test-runner/examples/edgeville-dungeon.json` climbs down, kills a zombie, mines iron, opens a gate, is refused at the
north gate, pushes through the wall and climbs back; `HeadlessEdgevilleDungeonTest` does the same through the headless
client, `NpcCombatTest` and `NpcDropsTest` the new NPCs' numbers and tables.

Step 11 is the hill giants' room, in the same file and `npc-spawns/edgeville-dungeon.json`, with the dungeon's item
spawns in `obj-spawns/edgeville-dungeon.tsv`.

| Behaviour | Source |
|-----------|--------|
| The shed west of the Cooks' Guild: its door (`brasskeydoor` 1804) is locked from both sides; the brass key used on it, kept, unlocks it and takes the player through (`edgeville_dungeon.door_locked` and `.door_unlock`) | 2004Scape's edgeville_dungeon script; the door not drawn open, guessed |
| The shed's ladder (`ladder_cellar` 17384 at 3116, 3452) climbs down the dungeon's 6400 beside the ladder that climbs back up; elsewhere the type is nothing interesting | 241 map (the type is in a dozen other places) |
| Twelve hill giants (28) fight as the Wiki's infobox and drop big bones, its members table, the giant key (a 1/128 pre-roll that takes the table's place) and a beginner clue (1/50) | OSRS Wiki, Hill Giant; osrsreboxed for the clue; mejrs's live NPC list for the tiles |
| The brass key north of the hill giants (3131, 9862, 100 ticks), and the dungeon's coins, bones, bronze arrows, brass necklace and red spiders' eggs | 2004Scape's maps 48,154, 49,154 and 48,153, the kinds the Wiki lists |
| The gate to Obor's lair (`hillgiant_boss_entrance_l`/`_r`) refuses (`edgeville_dungeon.obor_closed`) until Obor is built | ours |

Check: `test-runner/examples/hill-giants.json` takes the key, is refused and then let through the shed's door, climbs
down, kills a hill giant, is refused at Obor's gate and climbs back; `HeadlessEdgevilleDungeonTest` does the same
through the headless client, `ObjSpawnFilesTest` the spawns and `NpcDropsTest` the giants' table.

Step 12 is the Stronghold of Security's entrance and first floor, the Vault of War, in
`game/content/StrongholdOfSecurity.kt` and `npc-spawns/stronghold-of-security.json`.

| Behaviour | Source |
|-----------|--------|
| The entrance in the Barbarian Village mine (`Entrance` 20790) climbs down beside the floor's ladder; that ladder and the reward room's climb back up beside the entrance | 241 map; the landing tiles guessed |
| The Gates of War (19206 and 19207) take the player through at once. The second door of a pair, opened from the passage between them, may ask one of the 33 security questions (the gate's head speaks, `stronghold_of_security.qN`, `.qN_optionM`, `.qN_replyM`): only the right answer lets them through. No questions once the floor's reward is claimed | OSRS Wiki, Stronghold of Security (Doors) and Gate of War; how often a door asks (1 in 2) and which question it picks guessed |
| The Dead Explorer gives the Stronghold notes to a player not carrying them | OSRS Wiki, Stronghold notes; reading the book is left for later |
| The Gift of Peace, once: 2,000 coins, the Flap emote (varbit `sos_emote_flap`), Hitpoints and Prayer to full, its jingle; opened again it says so | OSRS Wiki, Gift of Peace; every account counts as having two-factor authentication |
| The portal by the entrance takes a player with the reward claimed, or of combat level 26 and up, to the reward room | OSRS Wiki, Stronghold of Security; the landing guessed |
| Goblins (5 to 25), minotaurs (12 and 27), rats and wolves (11 and 14) fight and drop as their Wiki pages, not aggressive; the floor is multi-way. The minotaurs drop the right skull half | OSRS Wiki, the DPS calculator; mejrs's live NPC list for the tiles (39 of the Wiki's 45 minotaurs) |

Left out: the ladder down to the second floor (step 13), reading the notes, and playing the Flap emote (emotes aren't
built). Check: `test-runner/examples/stronghold-of-security.json` climbs down, takes the notes, uses the portal,
claims the Gift of Peace, passes a pair of gates without a question and climbs back; `HeadlessStrongholdOfSecurityTest`
does the same through the headless client and answers a question wrongly and then rightly, `StrongholdOfSecurityTest`
checks every pair in the cache has one asking side, `NpcCombatTest` and `NpcDropsTest` the new NPCs.

Step 13a is the second floor, the Catacomb of Famine, in the same files: the floors are data
(`StrongholdOfSecurity.FLOORS`), each with its doors, door head, reward and portal.

| Behaviour | Source |
|-----------|--------|
| The Vault of War's ladder down lands beside the Catacomb of Famine's ladder up, which climbs back beside it; the floor's four ropes climb back to the start of the floor | Boney ladder (a floor's first ladder leads to the floor above); the ropes from Jagex's 2011 Catacomb of Famine guide ("return you to the start of the level"), inferred for OSRS; the landings guessed |
| The Rickety doors ask as the Gates of War (the door of famine's head), and stop once the Grain of Plenty is searched | as step 12 |
| The Grain of Plenty, once: 3,000 coins, the Slap Head emote (`sos_emote_doh`), Hitpoints and Prayer to full, its jingle | OSRS Wiki, Grain of Plenty |
| The portal lets through at combat level 51 or with the grain searched; every portal says `stronghold_of_security.portal_enter` | OSRS Wiki, Stronghold of Security |
| Flesh crawlers (28, 35, 41; the bottom of the sceptre among their drops), zombies (30, 44, 53), giant rats and rats as their Wiki pages; the crawlers aggressive to any level, the zombies aggressive | OSRS Wiki, the DPS calculator; mejrs's live NPC list (148 spawns, the Wiki's counts) |

Check: `test-runner/examples/catacomb-of-famine.json`; `HeadlessStrongholdOfSecurityTest`,
`StrongholdOfSecurityTest`, `NpcCombatTest` and `NpcDropsTest` as for step 12.

Step 13b is the third floor, the Pit of Pestilence, in the same files, and a melee NPC's spell (`NpcMeleeSpell`).

| Behaviour | Source |
|-----------|--------|
| The Catacomb's ladder down lands by the Pit's dripping vine up, which climbs back beside it; the goo covered vine in the reward room climbs back to the start of the floor, as the Catacomb's ropes | the 241 cache's locs (`sos_fam_ladd_down`, `sos_pest_ladd_up`, `sos_pest_rope_up`); the landings guessed |
| The oozing barriers ask as the Gates of War (the door of pestilence's head), and stop once the Box of Health is opened | as step 12 |
| The Box of Health, once: 5,000 coins, the Idea emote (`sos_emote_idea`), every lowered stat back to its level (a boost stays), its jingle | OSRS Wiki, Box of Health ("all stats including hitpoints, prayer points and strength restored") |
| The portal lets through at combat level 76 or with the box opened; every floor's portal works at its level "even if the reward hasn't been claimed before" | OSRS Wiki, Stronghold of Security (verified) |
| Catablepons (49, 64, 68) fight with stab and, one turn in 4 in reach, a Weaken glare: Strength down 5% of its level from where it is, no hitsplat, blocked by Protect from Magic, and no more glares once 8% + 3 is drained; the top of the sceptre among their drops | OSRS Wiki, Catablepon (the threshold verified; the chance, the 5% and the graphics guessed) |
| Giant spiders (50, aggressive to any level), spiders (24) and scorpions (37, 59) as their Wiki pages, all aggressive; no main drop table, only their beginner clues (and the scorpions' ensouled heads) | OSRS Wiki, the DPS calculator; mejrs's live NPC list (114 spawns) |
| A door opened from the tile beside it along its wall asks as from its own row (exploit audit R12B-2) | the passage is checked in the row the player crosses in |

Left out: the dripping vine down to the fourth floor (step 14) and the swamp bubbles' Suck. Check:
`test-runner/examples/pit-of-pestilence.json`; `HeadlessStrongholdOfSecurityTest`, `StrongholdOfSecurityTest`,
`NpcCombatTest`, `NpcDropsTest`, `NpcMeleeSpellTest` and `HeadlessCatableponTest` (a real catablepon's Weaken through
the client).

Step 14a is the fourth floor, the Sepulchre of Death, in the same files.

| Behaviour | Source |
|-----------|--------|
| The Pit's dripping vine down lands by the Sepulchre's Boney ladder, which climbs back beside the vine; its two bone chains climb back to the start of the floor, as the Catacomb's ropes | the 241 cache's locs (`sos_pest_ladd_down`, `sos_death_ladd_up`, `sos_death_rope_up`); OSRS Wiki, Boney ladder ("leads back up to the third floor"); the landings guessed, the chains inferred |
| The portals of death ask as the Gates of War (the door of death's head), and stop once the Cradle of Life is searched | as step 12 |
| The Cradle of Life: the first search gives the Stamp emote (`sos_emote_stamp`), hitpoints and prayer back, its jingle (`sos_life`) and no coins; every search gives a pair of boots, fancy or fighting. The fancier boots need a Jagex Account, which no account here is | OSRS Wiki, Cradle of Life and Stronghold of Security ("can be used again for more boots"); the stronghold's 10,000 coins is the first three rewards' 2,000 + 3,000 + 5,000 (inferred) |
| The portal lets through only once the Cradle is searched, at any combat level | OSRS Wiki, Portal (Stronghold of Security) and Stronghold of Security (no level for the fourth; inferred) |
| The notice by the Cradle can be read | the 241 cache (`sos_thankplayers`); the words wait for the Words thread |
| Ankou (75, 82, 86), ghosts (76, 77) and skeletons (60, 68, 85), aggressive; the ankou's table with the left skull half (3/100), the skeletons' bones, nothing from the ghosts | OSRS Wiki, Ankou, Ghost, Skeleton (Stronghold of Security), the DPS calculator; mejrs's live NPC list (94 spawns). The ghosts' respawn is inferred and their aggression guessed |
| The anti-macro shades and zombie (159) by the ladder, not aggressive, give 1 XP for 10 damage; the shades drop their robes, the zombie big bones and its bundles of bones together (a drop line may now give several items) | OSRS Wiki, Shade, Zombie (Sepulchre of Death) |

Left out: the skull sceptre and Solztun (step 14b), and the ghosts' and skeletons' tertiaries (the champion scroll).
Check: `test-runner/examples/sepulchre-of-death.json`; `HeadlessStrongholdOfSecurityTest`, `StrongholdOfSecurityTest`,
`NpcCombatTest` and `NpcDropsTest`.

Step 14b is the skull sceptre and Solztun, in `game/content/SkullSceptre.kt`.

| Behaviour | Source |
|-----------|--------|
| The left skull half on the right makes the strange skull, the top of the sceptre on the bottom the runed sceptre, and those two the skull sceptre, with 10 charges | OSRS Wiki, Skull sceptre (no level, a tick each) |
| Invoke takes the player to the Stronghold's entrance in Barbarian Village for a charge, up to level 20 Wilderness; the last charge crumbles a sceptre that isn't imbued. Divine shows the charges in a message box | OSRS Wiki, Skull sceptre; the standard teleport's animation, graphic and 3 ticks guessed |
| The charges are the player's, as the blowpipe's: two sceptres share them | guessed |
| Solztun, in the reward room, talks as the Wiki's transcript; once shown the Cradle's boots he imbues every skull sceptre carried. Every account counts as having the authenticator, so its refusals are left out | OSRS Wiki, Solztun and Transcript:Solztun |
| The imbued sceptre stays when empty and takes bone fragments (a charge each) and pieces (3, the left skull half 5), never past 10; a chisel breaks a piece into that many fragments | OSRS Wiki, Skull sceptre (the Varrock Diary's extra charges wait for the diary) |

Check: `test-runner/examples/skull-sceptre.json` and `HeadlessSkullSceptreTest`.

## Varrock

The Misthalin roadmap's Varrock piece, built a step at a time ([plan](https://claude.ai/artifact/UnqHb7sdn2TizdxP16Mvxu)).
Content is in `game/content/Varrock.kt`, spawns in `npc-spawns/varrock.json`. Every account has finished every quest.
Everything west of the Grand Exchange and the west gate (the Cooks' Guild among it) is the Edgeville and Barbarian
Village piece's.

| Behaviour | Source |
|-----------|--------|
| Step 1: the city's doors (`fai_varrock_door`, `_poor_door`, `_castle_door`, `_shanty_door`, `_door_taller`) open and close by name; the fence gate by the Grand Exchange (`ii_fencegate_l`/`_r` 15510/15512) opens into `ii_fencegate_lc`/`_rc`, the rule for any fence gate with no `…openfencegate_…` type | 241 cache names; the swings are the engine's (see Locs and doors) |
| Straight stairs (`fai_varrock_stairs` 11796, `_taller` 11797, `_woodenstairs_castle` 11798, `_taller_new_fix` 11807, top `fai_varrock_stairs_top` 11799) climb the way their angle points (north, east, south, west for 0 to 3): up onto the tile past the far end, down onto the tile before the near end, in the player's column | the 241 map's eleven straight staircases, whose tops all sit where this puts them; not watched on live (inferred) |
| Spiral stairs (`varrock_spiralstairs` 11789, `_taller` 11790, `_middle_taller` 11792 with its Climb menu, top 11793) and ladders (`fai_varrock_ladder` 11794/11795, `_taller` 11801/11802) work as Lumbridge's | as Lumbridge Castle's stairs and ladders (RSMod) |
| Furniture: chests (5108/5109), drawers (7194/7195) and posh drawers (7442/7443) open, search empty and shut; crates (5106, 5107, 5110, 5113, 5115), boxes (5111), bookshelves (7170, 7171) and bookcases (7176 to 7181, 7431) search empty | as the castle's furniture (2004Scape's timings); the bookshelves' and bookcases' words from the Wiki |
| Townsfolk: 37 guards (the six 2022 `fai_varrock_guard02` looks and the captain's, 13 in the city, 2 on the walls, 22 in the palace), men and women (Varrock's own `varrock_man1`/`varrock_woman1` talk, fight and drop as any man or woman), imps, rats and the five stray dogs | OSRS Wiki, Guard (counts) and Stray dog (three grey, two brown); tiles from mejrs's live NPC list (recorded before November 2022, when the guards still showed as 3010/3011), so which guard look stands where and every wander range are guessed |
| Step 2: the West Bank's six bankers (five behind its booths at x 3186, `banker1_new`/`banker2_new` 2897/2898 and the Deadman booth's multi-NPC 6521 showing 2897, and one behind its locked door, 8666, who has no options) and the East Bank's six (behind its booths at z 3419, 6522 at the Deadman booth); booths and the deposit box by name; they talk and bank as the castle's banker | OSRS Wiki, Varrock West Bank (six bankers) and Transcript:Banker; tiles from mejrs's live NPC list |
| Step 3: the Grand Exchange's four clerks (`ge_clerk_1` to `_4`, 2148 to 2151, behind the north and south booths): Exchange opens the offers screen, History the trade history, Talk-to their menu (how to use the Exchange, seven pages then the question again; setting up offers; item sets and "I'm fine" end the talk) | OSRS Wiki, Transcript:Grand Exchange Clerk; expressions and which options the player says guessed |
| The Grand Exchange's four bankers (`banker1_west` 1633, the Deadman booth's 6528 showing 1613, `banker1_east` 1634, `banker2_east` 3089) talk and bank as the castle's; every standard banker's Collect (op4, and the talk's collect option) and every bank booth's or Grand Exchange booth's Collect open the Grand Exchange collection box, and an exchange booth's Exchange the offers screen | OSRS Wiki, Grand Exchange and Banker; tiles from mejrs's live NPC list |
| Knock-at on the West Bank's locked door (`fai_varrock_bank_locked_door` 11788): the banker behind it won't let the player in (`varrock_bank_door.knock`) | OSRS Wiki, Varrock West Bank; one line and its expression guessed |
| Guards: Varrock's seven (`fai_varrock_guard02` and its looks, the captain) crush and Edgeville's `guard1` (3254) stabs, each for up to 3, attack speed 4, back in 50 ticks; both drop bones and one roll on a shared table (coins, iron bolts, runes and arrows, an iron dagger, a body talisman, 18/128 the general seed table, 8/128 nothing) and a medium clue 1 in 128 | OSRS Wiki, Guard and the DPS calculator; the coins and weapons lines from the osrsreboxed copy of the page (2023) and the 8/128 of nothing what is left of 128 (guessed); the animations guessed from the attack styles |
| The general seed table: its first subtable (4 potato, onion or cabbage seeds, 3 tomato or sweetcorn, 2 strawberry, watermelon or snape grass), which is all a monster below level 49 ever rolls | OSRS Wiki, Drop table (General seed drop table) |
| A stray dog's Shoo-away whines (`stray_dog.whine`, sound 911) and Pet barks (`stray_dog.woof`, sound 652) | OSRS Wiki, Stray dog |
| Step 4: the weapon and armour shops: Varrock Swordshop (Shop keeper 2884 and assistant 2885, 18 swords, longswords and daggers), Horvik's Armour Shop (Horvik 2882, 12 bodies and legs), Lowe's Archery Emporium (Lowe 2883, 15 arrows, bolts and bows) and Zaff's Superior Staffs! (Zaff 2880, 7 staves); Trade opens each, and each keeper's Talk-to (`varrock_swordshop.*`, `horvik.*`, `lowe.*`, `zaff.*`) opens the shop or ends with their parting lines | OSRS Wiki, each shop's page (stock, counts and percentages; ShopTest checks every sold-at price) and the keepers' transcripts; tiles from mejrs's live NPC list; the assistant's talk (he has no transcript), every expression and the wander ranges guessed |
| Step 5: the other shops: the Varrock General Store (Shop keeper 2815 and assistant 2816, 13 items), Aubury's Rune Shop (Aubury, the multi-NPC 2886 showing `aubury_3op` 11435 as every account has done Rune Mysteries, 14 runes and rune packs), Thessalia's Fine Clothes (Thessalia, 534 showing `thessalia_normal` 10477, 12 items), the Fancy Clothes Store (Asyff 2887, 22 items, the chef's hat and feathers starting at none) and Ye olde Tea Shoppe (the Tea Seller 8687, by his stall); Trade opens each, and each keeper's Talk-to (`varrock_shop_keeper.*`, `aubury.*`, `thessalia.*`, `asyff.*`, `tea_seller.*`) every way it goes, into the shop or to their parting lines | OSRS Wiki, each shop's page (ShopTest checks every sold-at price) and the keepers' transcripts; tiles from mejrs's live NPC list; the assistant's talk, Aubury's one parting line for both noes, the tea's restock time, every expression and the wander ranges guessed |
| Step 5b: the traders who sell through their talk: Benny (`qip_soa_newspaperseller` 5216) sells the Varrock Herald for 50 coins; Baraek (2881) sells a fur for 20, or 18 to a player who can't pay 20, and buys every grey wolf fur at 120 and every fur and bear fur at 12; the Apothecary (5036) talks gossip, hair potions and free potions and back to his menu (`benny.*`, `baraek.*`, `apothecary.*`) | OSRS Wiki, Transcript:Benny, Transcript:Baraek and Transcript:Apothecary (after Romeo & Juliet); tiles from mejrs's live NPC list; that 18 or 19 coins get the lower price, which fur the hand-over box shows when the player holds both, every expression and the wander ranges guessed |
| Step 5c: the Apothecary's potions screen (`apothecary_potions`, interface 556), from his Potions and from asking in his talk: Buy-1, -5, -X and -All make a strength potion(4) (limpwurt root, red spiders' eggs, 5 coins), an energy potion(4) (two limpwurt roots, chocolate dust) or an antipoison(4) (limpwurt root, cadava berries, 5 coins) for as many as the player holds ingredients and coins for; the screen's titles and ingredient texts and its messages are `apothecary_potions.*` | OSRS Wiki, Strength potion, Energy potion and Antipoison (the Apothecary's recipes); the screen's components from the 241 cache; the energy potion's 5 coins, every message and the model zoom guessed |
| Step 6: Varrock Palace's people: King Roald (5215) with his advisor Aeonisig Raispher (3774) speaking up in his talk, Queen Ellamaria (1390) in her garden, Sir Prysin (5083), who sells a lost Silverlight back for 500 coins to a player with none held, worn or banked, Captain Rovin (5085) at the top of the palace, Reldo (multi-NPC 6203, both types) in the library, Ambassador Ferrnook (2557), the training Captain (`fai_varrock_guard_trainer02` 11906), who shouts at his five Trainee Guards (the 2022 looks, 11907 to 11910), and the caged Monk of Zamorak (528); their talk every way it goes (`king_roald.*`, `aeonisig.*`, `ellamaria.*`, `prysin.*`, `rovin.*`, `reldo.*`, `ferrnook.*`, `guard_captain.*`); the library's quest bookcases and its north-east alcove's two (`palace_library.*`). The floors, stairs and furniture are step 1's, the altar (409) is Lumbridge church's | OSRS Wiki, each NPC's transcript (King Roald after The Blood Moon Rises, Reldo after Shield of Arrav, Queen Ellamaria after Garden of Tranquillity), Varrock Palace Library and Monk of Zamorak (the cage) and Sir Prysin (Silverlight 500 coins, Darklight 1000); tiles from mejrs's live NPC list; which of the King's after-quest talks shows, which trainee look stands where, which alcove bookcase says which, the Captain's 15 to 30 ticks between shouts, every expression and the wander ranges guessed |
| Step 7: Varrock's churches and inns: Pray-at at the church's altar (`fai_varrock_church_altar` 14860) and the Zamorakian chapel's chaos altar (61), each as Lumbridge's; Father Lawrence (5038) and Elsie (2900, upstairs), who tells her story for a cup of tea used on her; the Mage of Zamorak (2582, spawned as the type the multi-NPC 3229 shows after Rune Mysteries); the Blue Moon's (1312), Dancing Donkey's (1311) and Jolly Boar's (1310) bartenders, each selling a beer for 2 coins; the Blue Moon's cook (2895), who sells a cabbage for 1, and the Jolly Boar's (2896); their talk every way it goes (`father_lawrence.*`, `elsie.*`, `mage_of_zamorak.*`, `bluemoon_bartender.*`, `donkey_bartender.*`, `jollyboar_bartender.*`, `bluemoon_cook.*`, `jollyboar_cook.*`) | OSRS Wiki, each NPC's transcript (Father Lawrence after Romeo & Juliet, the Mage of Zamorak after Enter the Abyss); tiles from mejrs's live NPC list; which lines of the long passages the player says, that the Mage always gives his asked-before incantation answer, that a beer bought with a full backpack lands at the player's feet, that the Jolly Boar's cook always opens with his busy line, every expression and the wander ranges guessed |
| Step 8a: Varrock's townsfolk talking: Romeo (5037), Juliet (5035, upstairs) and her parents Draul Leptoc (5039) and Phillipa (5040, upstairs), Gertrude (7723, the type her multi-NPC 3500 shows after Gertrude's Cat), who sells a kitten of a random colour for 100 coins through her talk or her Kitten option but not while a kitten or cat is held or banked, the children Shilop (3501), Wilough (3503), Philop (3502) and Kanel (3504), Aris (11868), Charlie the Tramp (5209) and a tramp (3255), each begging a coin, Dr Harlow (16277) and Jonny the Beard (14139) in the Blue Moon Inn, Iffie (2899) and the Town Crier (276), who talks as on live today (the Wiki's transcript is his historical talk); their talk every way it goes (`romeo.*`, `juliet.*`, `draul_leptoc.*`, `phillipa.*`, `gertrude.*`, `shilop.*`, `wilough.*`, `playing_child.*`, `aris.*`, `charlie.*`, `tramp.*`, `dr_harlow.*`, `jonny_the_beard.*`, `iffie.*`, `town_crier.*`) | OSRS Wiki, each NPC's transcript (after each one's quest) and Gertrude (kittens 100 coins, one at a time); tiles from mejrs's live NPC list; Charlie's alley talk for a Black Arm Gang member, that Gertrude's talk also refuses while a kitten or pet cat is owned (not an overgrown, lazy or wily cat), her kitten in an item box, the tramp's grumble a tick after the talk, every expression and the wander ranges guessed |
| Step 8b: Dimintheis (4984) in his house, who hands a lost pair of family gauntlets back for free (naming the pair, with a key for each), but not while any of the four pairs is held, worn or banked; his son Johnathon (5445, the type his multi-NPC 4988 shows after Family Crest) upstairs in the Jolly Boar Inn, who turns family gauntlets into chaos gauntlets for free and cooking or goldsmith gauntlets for 25,000 coins (the worn pair before one carried), through his talk or his Gauntlets option, after which Dimintheis hands back chaos gauntlets if they're lost; Old Man Yarlo (10425); their talk every way it goes (`dimintheis.*`, `johnathon.*`, `yarlo.*`) | OSRS Wiki, each NPC's transcript (after Family Crest and Daddy's Home); tiles from mejrs's live NPC list; where the last enchanted pair is kept (with charges), a plain pair going straight to his chant without a menu, which pairs count as owned, the worn pair first, Johnathon's lines without a pair or the coins, every expression and the wander ranges guessed |
| Step 9a: the Varrock Museum: its stairs down to the Natural History floor (24428) and back up (24427), the back entrance's double doors (24565/24567, opening apart), the cleaning area's gate (24536) through its guard (1910), who asks on the first Open or talk after login (Wiki), and out again freely; Curator Haig Halen (5214), the information clerk (1909, behind her booth, who hands out a museum map) and the back entrance's guard (1912), their talk every way it goes as once every quest is done and with no Kudos (`curator.*`, `museum_clerk.*`, `museum_guard.*`); an examine key for every museum loc and the four NPCs | OSRS Wiki, each NPC's transcript (after Bone Voyage and The Dig Site, no Kudos); the 241 cache's museum locs and the basement's walls; tiles from mejrs's live NPC list; the stairs' landing tiles, the gate guard letting the player through without a word once he has asked, leaving the cleaning area freely, every expression and the curator's wander range guessed |
| Step 9b: the museum's other people: Historian Minas (1902) and Torrcs (6019) on the first floor, Art Critic Jacques (1901) on the second, Marfet (6020) in the cleaning area, the teachers and pupils and school visitors on every floor, and on the Natural History floor its four natural historians (6002 to 6005, one a section, each with his section's lectures), Orlando Smith (6001) and the twelve animal displays (NPCs with no options); their talk every way it goes as once every quest is done (`historian_minas.*`, `art_critic_jacques.*`, `museum_adventurer.*`, `museum_teacher.*`, `museum_schoolgirl.*`, `museum_schoolboy.*`, `orlando_smith.*`, `natural_historian.*`), a natural historian's first talk once per account | OSRS Wiki, each NPC's transcript; tiles from mejrs's live NPC list; every expression, the wander ranges, the teachers below the second floor saying the ground floor's pair, and the natural historians' first talk going straight to the menu guessed |
| Step 10: the Champions' Guild: its door (every account has the 32 quest points; the Guildmaster greets whoever comes in, `guildmaster.welcome`), the Guildmaster (814) and his talk (`guildmaster.*`), Scavvo (2871) and Valaine (2870) upstairs with their talk (`scavvo.*`, `valaine.*`) and shops (Scavvo's Rune Store; Valaine's Shop of Champions, which buys anything as a general store does), and the trapdoor by the stairs (Open, Close, Climb-down) to the basement and its ladder back up | OSRS Wiki, Transcript:Guildmaster, Transcript:Scavvo, Transcript:Valaine and the two shops' pages (prices, stock, restock times); the 241 cache for the door, trapdoor, ladder and the shops' invs; tiles from mejrs's live NPC list; every expression, that the Guildmaster greets only those coming in, Scavvo's inv (runiteshop), the wander ranges and where the trapdoor and ladder put the player guessed |
| Step 11: mines and skilling spots: the south-east and south-west mines (copper, tin, iron, clay and silver rocks, already Mining's), the anvils (already Smithing's), the houses' ranges (7184) and the posh oven (7421) as ranges, the oaks and yews (already Woodcutting's) and the dying trees (3648, as regular trees, to their stump 3649); buckets, jugs and bowls filling at every sink, fountain and water pump by the cache's name, and buckets at wells (`water.*`; vials stay Herblore's); the training dummies (1764: 5 Attack XP a hit below level 8 with `dummy.hit`, then `dummy.nothing_more`; ranged styles refused, `dummy.no_ranged`) | OSRS Wiki, the Dying tree, Water source, Dummy and Range pages; 2004Scape's dummy messages; the 241 cache for the ids and names; that the oven cooks as a range, the filling animation and one a tick (as Herblore's vials), that a well fills only buckets, and the dummy's swing (the weapon's attack animation, no faster than its attack speed) guessed |
| Step 12: the city's fighters: muggers (513, aggressive to players of twice their level or less), thieves (3252 and the Varrock gang's 5217, 5219 and 5220), the Black Knight north of the Jolly Boar Inn (516), the Blue Moon Inn's barbarian (3262) and her talk (`blue_moon_barbarian.*`; her scream over her head too), Varrock Palace's warrior women (3260), the grizzly bear in its pen east of the palace (2838, aggressive), giant rats by the south-east mine (2862, 2864) and a spider (3019): combat, drops and spawns | OSRS Wiki, each monster's page (stats, respawn, aggression, drops) and the DPS calculator's bonuses; tiles from mejrs's live NPC list; every fighting animation from the stand animation (punches for the unarmed, sword and shield for the knight and warriors), the mugger's respawn (25, as the thieves'), the gang thieves' drops (the standard thief's) and the barbarian's expressions and screaming order guessed; the player's scream shows only in the chatbox (no overhead text for players yet) |

Left out from step 1: the guards' Pickpocket (Thieving, step 13) and their joining in when another guard is attacked,
the general seed table's higher subtables (no monster yet is level 49 or more), the player's own overhead lines when shooing or petting a dog, the dog
following the player after Pet or after being fed and running off when shooed, the staircases down to basements and the sewers (`fai_varrock_bank_stairs`, `fai_varrock_ladder_deep`,
the sewers' step 15), the West Bank's basement room behind the locked door, its poll booth, the Grand Exchange's item sets (Sets and the clerks' item sets option), the clerks' collection reminders and combat path voucher options, Brugsen Bursen, the item experts and the Grand Exchange tutor (step 8's talkers), and the Phoenix and Black Arm gangs' doors. Left out from step 4: Horvik's corrupted platebody, Lowe's and Zaff's combat path voucher, the keepers' treasure trail and diary talk, and Zaff's daily battlestaves, What Lies Below ring and Bryophyta's essence options. Left out from step 5: Aubury's Teleport and its talk option (no rune essence mine yet) and his Runecraft cape stock, Thessalia's Makeover and its change top and legwear options (no makeover screen yet) and her scythe, bunny ears and frog token options, Asyff's Fur Clothing, bird costume, hunting clothing and priest gown options, and the Tea Seller's shouts at a thief (Thieving, step 13). Left out from step 5b: the Apothecary's quest topics, Baraek's Ring of Charos(a) option and treasure trail clue, and Benny's overhead shouts. Left out from step 6: Reldo's Wise Old Man message, Darklight, the Monk of Zamorak's combat (he casts; the fighters' step), the Varrock Census, Surok Magis (gone once What Lies Below is done), and the White Tree's fruit and Treznor's tree patch (Farming). Left out from step 7: the chaos altar's Check, the church's sleeping church-goers, and the Mage of Zamorak's treasure trail talk. Left out from step 8a: Gertrude's pick-your-own kitten option and the Ring of Charos(a) colour screen, Shilop's and Wilough's talk once paid, Charlie's treasure trail requests, Jonny the Beard's Attack (the fighters' step), the tramp's second-time opening, Iffie's talk after the Halloween 2021 event, the Town Crier's overheads, the sleeping church-goers Martina Scorsby and Jeremy Clerksin, and Toby (the Achievement Diary). Left out from step 8b: Old Man Yarlo's Construction cutscene (the player only thanks him), and Avan's and Caleb's own gauntlet changes. Left out from step 9a: the displays' Study (their screens), the cleaning activity, Kudos and the clerk's rewards, the curator's relics, certificates and medallion trade and his Pickpocket (Thieving, step 13), and the Dig Site workman's gate guard. Left out from step 9b: Historian Minas's quest information (it gives Kudos and lamps; he hears no new quests), the lectures' walk to the display and the historian's pose, Orlando Smith's quiz (he asks every time), Marfet's talk about The Dig Site, the cleaning area's archaeologists (the cleaning activity) and The Collector. Left out from step 10: the Guildmaster's Champion's scroll and maze key options, the basement's Champions' Challenge (its portcullis, ladder and Champion Statue), and the chickens and Evil Chicken in the garden. The furniture's and dogs' words are in the dialogue
file (`varrock_search.*`, `stray_dog.*`, `varrock_bank_door.*`, `ge_clerk.*`, `varrock_swordshop.*`, `horvik.*`, `lowe.*`, `zaff.*`, `varrock_shop_keeper.*`, `aubury.*`, `thessalia.*`, `asyff.*`, `tea_seller.*`, `benny.*`, `baraek.*`, `apothecary.*`, `apothecary_potions.*`, and steps 6's, 7's, 8a's, 8b's, 9a's, 9b's, 10's, 11's and 12's keys above).

Check: `test-runner/examples/varrock-city.json` walks in through the south gate, opens and shuts a house's door, climbs
both kinds of straight stairs and a ladder, searches drawers, pets a stray dog and talks to a man; `varrock-banks.json` banks at both banks and knocks at the locked door; `varrock-guards.json` kills a Varrock and an Edgeville guard for their bones; `varrock-grand-exchange.json` uses the Grand Exchange's clerks, booths and a banker; `varrock-shops.json` and `varrock-stores.json` buy and sell back at each shop and talk into each; `varrock-traders.json` buys Benny's newspaper and a fur from Baraek, sells it back, talks to the Apothecary and buys a strength potion on his potions screen; `varrock-palace.json` climbs every palace floor (the roof by ladder), talks to each of its people, searches the library's bookcases and prays at the altar; `HeadlessVarrockTest`
does the stairs, the ladder, the drawers and Knock-at on the locked door through the headless client, `VarrockTest` the stairs' rule each way round and a stray dog's Shoo-away and Pet (overhead text and sound),
`GatesTest` the Grand Exchange's fence gate, `NpcSpawnFileTest` the counts, `NpcCombatTest` and `NpcDropsTest` the
guards' numbers and 10,000 kills against the table, and `HeadlessGrandExchangeBuildingTest` the clerks', a banker's and a booth's options, `HeadlessVarrockShopsTest` and `HeadlessVarrockStoresTest` each keeper's talk into their shop and turned down, `HeadlessVarrockTradersTest` Benny's, Baraek's and the Apothecary's talk every way it goes and his potions screen's Buy-All and Buy-X, `HeadlessVarrockPalaceTest` the palace's talk every way, Sir Prysin's Silverlight sale, the bookcases and the Captain's shouting, `ShopTest` the shops' prices, and `HeadlessGuardsTest` a guard of each kind attacked through the
headless client, fought to its death and leaving its bones.

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
`delay`, or a dialogue is open), then goes back to wandering. If someone attacks it meanwhile, it fights them: it walks
to them and hits back, and your dialogue stays open. A multi-NPC uses the type your vars show.

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
| An NPC you are busy with still walks to and fights a player attacking it, and your dialogue stays open | guessed; live NPCs aren't held by one player's dialogue, but it isn't checked |
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
a player carrying none. The bankers' collect (asked or op4 Collect) opens the Grand Exchange collection box (since
the Varrock piece's step 3). Not built yet, so their options end the talk or do nothing: bank space; Perdu's Lost Property Shop; the Doomsayer's warnings (Toggle warnings); achievement diaries; Attack
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
with its name and where on the Wiki its text is. The locs listed are every named type in the castle and, outside it,
every named type in the rest of Lumbridge (map squares 49 to 51 by 49 to 51, west of the Al Kharid fence). To get a
file with every key in it:

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

## Instances

An instance is a private copy of part of the map, as raids and bosses give each party (raid step 10).
`game/instance/Instances.kt` gives each one a block of map squares in the dynamic area, from x 6400 east where the cache
has no maps. It copies each zone of the original into the block, on every level: the terrain's collision, and the cache
locs with their collision. An instance can also be laid out from parts, each taking zones of one level to a place and a
level of its own, and a part can be laid again over zones it has, for a room that changes as a raid goes on (raid
step 15). Walking, doors and line of sight then work as in the original. A player in an instance gets a
REBUILD_REGION, which tells the client to draw each zone of the block from the original's map data. Outside one, they get
the usual REBUILD_NORMAL, also after a step into or out of one.

An instance ends once nobody is in it, with its NPCs, ground items and loc changes (a door on its edge opened onto the
tile beyond included), and the respawns, shots and fires due there later; a grave in it moves to its exit. A player who
dies in one has their grave raised at its exit, where they can get to it, and comes back at the spawn (exploit review
round 9, INST-1); the raid has its own rules. A trade in
one saves both players at its exit, as any save does. A player who loses their connection
still counts until they are removed, so a reconnect finds it. Nobody is saved in an instance: a save, a logout included,
puts them at its exit. Blocks are kept a map square apart, so nobody in one sees anyone in another.

| Instance behaviour | Source |
|--------------------|--------|
| Zones drawn as other zones of the map with REBUILD_REGION; empty zones left out | RSProt's RebuildRegionV2 (the client's dynamic regions) |
| The dynamic area from map square 100 (x 6400); a free map square between blocks | guessed |
| An instance starts as the cache has it: changes in the original (an open door) aren't copied | guessed |
| The client told to reload the region on each REBUILD_REGION | guessed |
| An instance ending as soon as nobody is in it; saves at its exit | guessed; raids that keep a place for a disconnected player come with parties |
| A grave left in an ended instance moving to its exit, with its items and time left | guessed |
| Only doors and gates working on copied locs, and the raid's own locs; content's handlers, written for the original's tiles (the Al Kharid toll gate), not run there, so the player is told "Nothing interesting happens." | guessed |
| A death in an instance raising its grave at the instance's exit | guessed; a grave inside was out of its owner's reach |
| A part laid again over an instance's zones losing the flags a neighbouring zone's loc put in them | guessed safe: the raid's nexus has walls all round |
| Unrotated copies only | no room built so far needs rotating |

`::instance` makes one around you for testing. Check: `./gradlew test --tests 'osrsps.game.instance.*'
--tests 'osrsps.testclient.HeadlessInstanceTest'` (two players in copies of the same spot can't see each other, and
leaving puts them back), and in RuneLite `test-runner/examples/instance-castle.json` (the castle's large doors in a copy:
shut, they block line of sight; opened, they don't; then back out).

## Raid parties

The Fractured Archive is fought by a party of 1 to 8 (raid step 11). `game/raid/Parties.kt` keeps who is in which party:
the first member leads, and a party lasts until its last member leaves, so a wipe doesn't break it up. Every boss's
hitpoints scale to the players in the raid, with a player alone counted as two (OSRS Wiki, The Fractured Archive).
`game/raid/PartyScaling.kt` reads each raid NPC's hitpoints for parties of 1 to 8 from
`src/main/resources/party-scaling.json`, copied from the NPC's Wiki page, as the Wiki gives no formula. Only the Archive
Guardian is in it so far; each boss's step adds its own.

| Party behaviour | Source |
|-----------------|--------|
| 1 to 8 players a party | Wiki, The Fractured Archive (participants) |
| Hitpoints scaled to the party, a player alone counted as two | Wiki, The Fractured Archive |
| The Archive Guardian's hitpoints: flying 550, 550, 800, 1,030, 1,260, 1,490, 1,720, 1,950 and grounded 1,100, 1,100, 1,600, 2,060, 2,520, 2,980, 3,440, 3,910 for 1 to 8 players | Wiki, Archive Guardian |
| A party not disbanding after a wipe | Wiki, The Fractured Archive (28 October 2026 update) |
| `::raidparty` instead of the lobby's Grouping Board, whose workings the Wiki doesn't give; its messages are this server's own | stand-in, until a recording shows the board |
| Only the leader inviting and kicking; an invite lasting until it is taken up or the party ends; one party at a time | guessed |
| A member who logs out leaving the party; a leader who leaves handing it to the member who joined next | guessed |
| The party's hitpoints orbs | not in yet: waits for a recording of the raid's interface |

`::raidnpc tfa_guardian 3` puts in the Archive Guardian scaled for 3, and `::npcs` shows the party size and hitpoints.
Check: `./gradlew test --tests 'osrsps.game.raid.*' --tests 'osrsps.game.command.PartyCommandsTest'
--tests 'osrsps.testclient.HeadlessPartyTest'` (the Guardian's hitpoints at 1, 2, 3 and 8 players are the Wiki's; three
players form a party, the Guardian's hitpoints follow its size, it lasts through a death, and a member who logs out
leaves), and in RuneLite `test-runner/examples/party-scaling.json` (the Guardian's hitpoints alone, for 3 and for 8).

## The Fractured Archive's raid flow

A run of The Fractured Archive (raid step 15) is `game/raid/ArchiveRaid.kt`. The lobby's Archive Door takes the
player's party into its own instance: the cache's map squares 63 to 65 by 86 to 88, level 0, with the nexus (64,87) on
one of its four levels and the lobby's square left out. The party gathers in the nexus's entrance. The Archive Guardian
is fought in the nexus's hall, where the cache lays its corpse on the nexus's later levels. Then come three rows of
doors, each with a room behind its west and east door: Ezekiel or Bork and the Smorks, then Moia or Dreadnoth the
Jellied, then the Dagon'hai Elder or Xarth. Last, the nexus's Passageway north opens to Ocellus's arena. Each room's
Passageway goes back out to the nexus, and so does its Archive transporter once the room is done. A room whose boss
isn't built yet is done as soon as the first party member walks in: that is the run through empty rooms. Each boss's
step, from 16 on, adds its fight and ends its room when the boss dies. The Guardian (step 16) waits in its hall from
the run's start and its fight begins when a member touches it. The whole instance is multi-way.

A room's contribution points are shared by damage among those who did any, or evenly among who was there if nobody
did. A party member who dies while someone still stands keeps everything and is down until the room is done. The
last of the party to fall wipes it: everyone goes back to the lobby, in front of the Archive Door, and what each
loses goes in a grave there. The party lasts through the wipe.

| Raid flow behaviour | Source |
|---------------------|--------|
| The Guardian first, one room of each of three rows by its left or right door, Ocellus last; the pairs Ezekiel or Bork, Moia or Dreadnoth, Xarth or the Dagon'hai Elder | Wiki, The Fractured Archive, and "Everything You Need To Know" |
| Each room's map square (Ezekiel 63,86, Bork 65,87, Moia 63,88, Dreadnoth 65,88, the Dagon'hai Elder 63,87, Xarth 65,86), told by its locs' names; each west door to the west room of its pair | 241.4 cache; which room is whose inferred from the names, the doors from the map's columns |
| The nexus's doors (63088 to 63105, Enter and Quick-enter), the rooms' Passageways (62765, Exit) and Archive transporters (63087, 62610, 63134, Use and Quick-use), the nexus's Passageway north (62766) | 241.4 cache |
| The Guardian fought in the nexus's hall; Ocellus in the arena north of it (64,88), past the nexus's Passageway | inferred: the Guardian's corpse lies in the hall on the nexus's later levels, and the Passageway is there on the last |
| The nexus on level 0 of the cache at the start, 2 (the Guardian's corpse, a camp of crates and barricades) once the Guardian is done, 3 (and the Passageway north) once the fourth room is done | inferred from what each level holds; level 1 isn't used |
| Contribution: each room a fixed amount, shared by damage, minions included; the two rooms of a pair the same | Wiki, "Everything You Need To Know" |
| Each room's points 1,000, or shared evenly when nobody did damage | guessed: the Wiki gives no number |
| A wipe sending the party to the lobby with an ordinary gravestone; the party not breaking up | Wiki, The Fractured Archive (Theatre of Blood's system; 28 October 2026 update) |
| The wipe's tile and graves in front of the Archive Door (4127,5560); each member's grave on it | guessed |
| A member dying with the party standing keeping everything, down in front of the room's door until it is done, and sent back out if they walk in | guessed (Theatre of Blood's system); spectating isn't built |
| Any member opening the run, a player in no party as a party of one; others following until the Guardian's fight begins, nobody after | guessed; the Wiki says supplies can't be banked mid-raid |
| A row's other door shut once one is gone through; a row opening once the room before is done; Enter and Quick-enter, Use and Quick-use, the same | guessed, until a recording shows a run |
| Passageway out at any time; the transporter once the room is done; both to the nexus in front of the room's door | guessed |
| A room's fight beginning as the first party member stands in it, and the Guardian's in the hall north of the entrance | guessed |
| The Guardian's fight beginning on Touch on the waiting "Inert Guardian" (`tfa_guardian_start`, 16657) in the middle of the hall, where its corpse lies later; scaled to everyone standing in the run then | 241.4 cache (the NPC and its Touch); where it waits inferred from the corpse; the party size guessed |
| The whole of a run's instance multi-way | inferred: a raid of up to eight fights one boss |
| A member who leaves the party sent to the lobby; a logout saving them there, as any instance's exit | guessed |
| A member down who gets out (a teleport such as Home Teleport or a lodestone, leaving the party, or a logout once out of combat) dying of it, with a grave in the lobby, as if they had been the last standing; a reconnect within the linger no escape | Sam's decision, 8 November 2026 (exploit review RAID-2); the death's place guessed |
| Every room done as it begins, until its boss's step | stand-in (all but the Guardian's) |
| The bosses after the Guardian, the Quartermaster and Supply Credits, the party's orbs, the reward room, deaths' item costs | not in yet: steps 17 to 31 |

`::raid run` shows the run you're in, `::raid tp nexus` or `::raid tp ezekiel` goes to its nexus or a room, and
`::raid clear` marks the room you're in done, as if its boss died; a room whose fight never began (the Guardian before it is touched) gives its points to whoever of the party stands in it. Check: `./gradlew test --tests 'osrsps.game.instance.*'
--tests 'osrsps.game.combat.DeathsTest' --tests 'osrsps.testclient.HeadlessArchiveRaidTest'` (a run through the empty
rooms by the west doors and one by the east; rows opening in order and a row's other door shut; a death that leaves the
party standing, a player too late to come in, the wipe to the lobby with graves, and a member down who teleports out,
leaves the party or logs out dying of it), and in RuneLite
`test-runner/examples/archive-raid-flow.json` (both runs, solo) and `archive-raid-wipe.json` (a death in the raid back to
the lobby, with a grave).

## Encounters

A boss fight is written as an encounter (raid step 12): `game/encounter/Encounter.kt` has the boss, its phases, and each
phase's attack cycle, steps a set number of ticks apart that repeat. A phase ends when the boss's hitpoints fall to a
share of its maximum, or after so many ticks, and the next begins. A step can hit a player, put down tile hazards (a
warning graphic now, then damage each tick to whoever stands on the tiles), or put in adds.
`game/encounter/Encounters.kt` runs every fight once a tick. A fight ends when its boss dies or leaves the world, and its
adds and hazards go with it. Each fight keeps a log of what it did, by tick. Every roll comes from the world's random, so
a fight started on a seed plays the same way each time.

| Encounter behaviour | Source |
|---------------------|--------|
| Fights running in the NPC phase, after NPC scripts | guessed, until a recording shows a boss |
| A phase changing at the start of the tick its threshold or time is reached, before that tick's steps | guessed |
| A fight's first tick running as it starts (steps with no delay, hazards due at once), paused or not | guessed |
| Hazards hurting from no one, so standing in one doesn't start a fight or make the player hit back | guessed |
| A hazard's hit still counting as combat: it cuts a Home Teleport or lodestone cast short and holds a logout, as the boss's own hit would (exploit review round 11, ENC-1) | guessed |
| Adds and hazards going when the boss dies or leaves | guessed; each boss's step changes it if live keeps them |
| A phase also ending when its own test says so (a shield broken); steps put off a few ticks (a laser aimed now, landing later) running after the cycle and before the hazards, never once the fight is over | guessed |
| A boss whose fight picks its attacks (the Guardian) never swinging, shooting, walking after anyone or retreating by itself, and keeping the first player it took on while they last | guessed |
| A fight outside an instance covering 32 tiles round the boss's spawn, for the starter and their raid party only (so another test account standing in Lumbridge isn't drawn in), ending if the starter dies or logs out (else a script stopped mid-fight leaves them dying again and again at Lumbridge's spawn) | testing only: the raid's rooms are instances |
| The Archive Guardian (step 16); the others each come with their own step, from 17 on, with its room and gear sets for `::raid` | not in yet: steps 17 to 27 |

`::raid boss phase=2 party=3 seed=42` starts a fight where you stand, and `::raid digest 100` sums up its first 100
ticks. Check: `./gradlew test --tests 'osrsps.game.encounter.*' --tests 'osrsps.game.command.RaidCommandsTest'
--tests 'osrsps.testclient.HeadlessEncounterTest'` (a test boss plays the same 100 ticks on two runs with the same seed
and differently on another; its phases change at half hitpoints and on time, with an add and a hazard, and the fight
ends when the boss leaves).

## The Archive Guardian

The raid's first boss (raid step 16a) is `game/raid/ArchiveGuardian.kt`, an encounter on the cache's two forms. It
waits in the nexus's hall as the "Inert Guardian" until a member touches it. It wakes levitating, with its shield's
hitpoints, shooting the player it took on every 5 ticks and slamming anyone who stands under it instead; only ranged,
magic and halberds reach it. With the shield gone it falls to the ground, blasting whoever is under or beside it, with
its grounded hitpoints, and swings at a player beside it. At 30% it takes off again for good, with no shield, and fires
a laser at a random player every 10 ticks, its damage shared by everyone on the laser's line. Its death does the room.
While its shield is up, and between lasers at the end, it grows a mushroom two tiles from each player; stepping beside one
bursts it, poisoning and wearying (no running, attacks a tick slower), and the burst splashes anyone two tiles off.
Grounded, a vine rises on each player's side away from it every 3 ticks and swipes the tiles round it three times.
Four inert guardians stand round the hall (step 16c). Under its shield three are awake: beaten to 0 one is charged,
taking 40% off the shield, and spent. The south-east one never wakes; in the last phase the laser breaks the first one
standing behind its target, halving the laser and hurting the Guardian, but never into kill range.

| Guardian behaviour | Source |
|--------------------|--------|
| Stats, bonuses, both forms' hitpoints by party size (shield 550 to 1,950, grounded 1,100 to 3,910), 5-tick attacks, max hits 25 and 99+, 80% fire weakness, poison and venom immunity, Defence drained to 60% at most | Wiki, Archive Guardian (read 2026-11-03) |
| Two forms: `tfa_guardian` (16659) levitating, `tfa_guardian_unshielded` (16660) grounded with slash defence 90 not 110; the animations and spotanims by name | 241.4 cache |
| Levitating, only ranged, magic and halberds hitting it; shots at the player it chose; a slam on whoever stands under it; the shield's break grounding it; a blast on those under or next to it; the last phase at 30% with no shield; the laser "fatal if more than two players are alive and it isn't reduced", shared by standing in line, "slightly" cut by Protect from Magic | Wiki, The Fractured Archive/Strategies, as a guide site quotes it (the Wiki's own page is too long to read past its equipment) |
| The grounded form's 40% fire weakness | a guide (osrsbestinslot); the Wiki lists 80% and 40% without saying which form has which |
| Ranged shots (Protect from Missiles), a crush swing grounded (Protect from Melee), the slam blocked by Protect from Melee | guessed for the slam; the prayers for the others from the attack styles |
| The first attack 5 ticks into each phase; the laser every 10 ticks, landing 3 after it is aimed; its line from the boss's middle through its target, 32 tiles on | guessed |
| The slam up to 25, the blast 10 to 20, the laser 80 for one or two players and 40 for each player from three, Protect from Magic taking a tenth | guessed, to fit the Strategies page's words |
| It never moves, and keeps to one tank till they fall or go out of its range or sight, when it turns at once on someone in it (holding its fire only when nobody is); grounded, those who stand off get its vines | guessed |
| The words for melee refused on a flying monster (`combat.flying`) | guessed |
| Mushrooms (locs 62578 to 62580, `tfa_archive_guardian_mushroom_spawn`), the Vine (16664, `archive_guardian_tentacle`, its spawn, swipe and despawn animations and `vfx_guardian_tentacle_intro`) | 241.4 cache |
| Mushrooms in the shield phase and alternating with the laser at the end; their 3 to 10, poison and weariness (slower attacks, no running); a run beside one and on popping it, a walk leaving a weaker splash; vines from 6 ticks into the grounded phase, then every 3, each on the player's side away from the boss and swiping a 3x3 three times on a 3-tick cycle, Protect from Melee capping a swipe at 11 | guides (osrstoolkit, runescapeguides, bald.gg; read 2026-11-03) |
| A mushroom round each player every 15 ticks, 2 tiles off, growing for 2 and bursting after 20 if no one comes; the splash 1 to 5 at 2 tiles; poison severity 20 (hits of 4); weary 10 ticks, a tick on each attack; a vine swipe up to 22 unprayed | guessed |
| An antipoison beforehand avoiding "the poison damage only" (so weariness comes all the same) | Wiki, The Fractured Archive/Strategies, its equipment table (read 2026-11-03) |
| The inert guardians' three forms (`tfa_guardian_battery_inactive`, `_active` with Attack, `_destroyed`; 2 by 2), the awake one's 180 hitpoints, Defence and Magic 50, and its stab, slash, crush and magic defence (0, 20, -20, -20, its params 5 to 8, which give the Wiki's figures on the Guardian) | 241.4 cache |
| Four round the room, charged by attacking them; charging taking ~40% off the shield (596 of 1,490); the south-east one only hit by the beam, which damages the boss's health; the beam destroying the one behind its target; lining it up reducing the laser; no damage to the boss once it is in kill range | guides (runescapeguides, osrstoolkit; read 2026-11-03) |
| Where they stand (level with its middle 9 tiles west and 8 east, and 10 south); the awake ones put to sleep as it falls; the laser halved; a broken one taking 10% of its most hitpoints off it, never below kill range at 10%; their ranged defences, 0; a swing on a spent one landing for 0 | guessed |
| The step-under timing, a guide's "firebreath" enrage with braziers, the Serpentine helm shortening weariness, which guardians melee reaches and which only ranged (a guide's earth weakness, with no figure) | not in yet (the enrage isn't on the Strategies page; there is no Serpentine helm yet) |

`::raid guardian party=3` starts it where you stand, `phase=grounded` or `phase=airborne` from a later phase. Check:
`./gradlew test --tests 'osrsps.testclient.HeadlessArchiveGuardianTest' --tests 'osrsps.game.combat.NpcCombatTest' --tests 'osrsps.game.MovementTest'`
(touched awake in its hall, through its three phases to its death doing the room, the instance multi-way; the laser
shared by two on its line; melee refused while it levitates but for a halberd; the laser's line and damage; a mushroom
bursting with poison and weariness, then splashing; a vine's swipes held to 11 by Protect from Melee; a weary player
walking with run on; the inert guardians charged into the shield, three grounding it, and broken by the laser, which
they halve, never into kill range), and in RuneLite `test-runner/examples/archive-guardian.json` (the whole fight solo
with the right prayers, moved on with `::hit`), `test-runner/examples/archive-guardian-hazards.json` (a vine and a
mushroom) and `test-runner/examples/archive-guardian-batteries.json` (an inert guardian charged).

## The Fractured Archive lobby

The raid's lobby (raid step 14) is map square 64,86 of the 241.4 cache, entered from the Chasm of Tears through the
cavity at 4050,4567. `game/content/ArchiveLobby.kt` has the way in and out, the banker and three NPCs who talk, and the
lobby's bank chest goes through `game/content/Banking.kt` as every bank chest does. Their spawns are in
`npc-spawns/fractured-archive-lobby.json`. What they say is in the dialogue file (keys `general_montoya.*`,
`record_keeper.*`, `tfa_wizard_apprentice.*` and `tfa_banker.*`; her first four options are `banker.*`). Players can't
reach the chasm yet: `::tele 4054 4567` stands by the cavity.

| Lobby behaviour | Source |
|-----------------|--------|
| The cavity (`luc2_raid_entry_multi`, 62542) a multiloc on varbit 16354 (`wgs_tfa_shortcut_unlocked`), 1 showing the Opening's Crawl-through; the varbit set at login as after While Guthix Sleeps | 241.4 cache; the varbit's meaning inferred from its name |
| The miniquest that starts at the first entry, and the quest needed, left out | no quests on this server |
| Crawl-through landing north of the lobby's Opening (4127,5508), and the Opening's Enter landing east of the cavity (4054,4567), with no animation either way | guessed, until a recording shows them |
| The bank chest's Use opening the bank and Collect the collection box; the banker's Talk-to, Bank and Collect | 241.4 cache (options); Wiki, Transcript:Banker (The Fractured Archive) |
| General Montoya's, the Record Keeper's and the Wizard apprentice's talks | Wiki transcripts (Montoya's before the raid is done; the Record Keeper's first talk) |
| The apprentice's first talk then his later one, on varbit 16427 (`tfa_wizard_apprentice_met`) | Wiki transcript; the varbit inferred from its name |
| The Record Keeper's introduction each time, and Yes to his statistics closing the talk | guessed: the var live keeps for it isn't known, and his statistics screen isn't built |
| Montoya's and the Record Keeper's menus coming back after each topic; every chathead expression | guessed |
| Where the four NPCs stand | guessed from the lobby's map, beside the locs they tend |
| The Grouping Board, Alliance records, Strategy board, Ascension rocks, Loot Wheelbarrow, and the guards, druids and Alliance builders | not in yet: they wait for a recording or are left out of the first version (the Archive Door is the raid flow's) |

Check: `./gradlew test --tests 'osrsps.game.content.ArchiveLobbyTest' --tests 'osrsps.testclient.HeadlessArchiveLobbyTest'`
(each talk line by line; the cavity open from login and crawled through, the Opening back, the bank chest and banker
banking, the apprentice remembering his first talk), and in RuneLite `test-runner/examples/archive-lobby.json`.

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

## Home teleport

Home Teleport, the standard spellbook's first spell (`game/content/HomeTeleport.kt`): on this server Cast opens the
lodestone map (`Lodestones.kt` and `LodestoneMap.kt`, as RS3's does: each stone's icon where it stands, lit once the
player has activated it, the stones' names beside, Teleport on the activated ones, and Previous destination for the
stone last teleported to) and a pick plays the cast's stages on the spot and lands the player on that stone 17 ticks
later, free and with no cooldown. OSRS's own cast, to the castle courtyard 24 ticks after the click with the
30-minute cooldown in varp 892 (which RuneLite's home teleport timer reads), is the class's default and stays tested,
but no world uses it: `HomeTeleport.target` is the seam the lodestone network takes when it registers. Not in yet:
the other books' home teleports and teleport animation overrides.

| Home teleport behaviour | Source |
|-------------------------|--------|
| Free, no Magic level, no XP; a 30-minute cooldown shared by every home teleport | Wiki, Lumbridge Home Teleport and Home Teleport |
| The default animation lasts 24 ticks | Wiki, Teleport override |
| Not castable in combat or for 10 seconds after (as logging out, 16 ticks); combat interrupts it | Wiki, Home Teleport |
| Varp 892 (`aide_tele_timer`) holds the minute (since 1970) of the last home teleport | RuneLite, Timers and Buffs plugin |
| 218:6 (`magic_spellbook:teleport_home_standard`), op1 Cast; the stages' seqs 4847, 4850, 4853, 4855, 4857 and spotanims 800 to 804 (`aide_*`) | 241 cache |
| The stages starting 0, 7, 13, 16 and 19 ticks in; landing on 3222, 3218; casting stopping a queued activity; only the standard book casting it; the cooldown starting on landing, so a cast cut short costs nothing; minutes left rounded down; a second click while casting doing nothing | guessed |
| The two messages' words (`home_teleport.cooldown`, `home_teleport.in_combat`) | not on the Wiki; to be found |

Check: `./gradlew test --tests 'osrsps.game.content.HomeTeleportTest' --tests 'osrsps.testclient.HeadlessHomeTeleportTest'`,
and in RuneLite `test-runner/examples/magic-home-teleport.json`.

## NPC aggression

Monsters with `"aggressive"` in `npc-combat.json` go for players unprovoked (`game/combat/Aggression.kt`), each tick
before NPCs fight; once one has found a player it chases and fights them as if they had hit it. The giant spiders east
of the river and the swamp's giant rats are aggressive so far. `::aggro` shows the tolerance count; `::npcs` shows who
each NPC fights.

| Aggression behaviour | Source |
|----------------------|--------|
| A player of a combat level at most twice the monster's; level 63 and up, and monsters that ignore levels (bosses), attack anyone | OSRS Wiki, Aggressiveness |
| The hunt range counts from the monster's south-west tile, not its size; the aggression range (max range 7 plus attack range) from its spawn point, counting its size | OSRS Wiki, Aggressiveness and Retreat |
| After ten minutes (1000 ticks) in an area tolerant monsters leave the player alone; leaving and coming back starts again, logging out doesn't. Bosses can be made never tolerant | OSRS Wiki, Aggressiveness |
| The two 21x21 areas the ten minutes are counted in, moved on when the player leaves both, and a jump of more than 40 tiles (a teleport) starting again | RuneLite's NPC Aggression Timer plugin |
| Not a player another monster is fighting, in single-way combat | OSRS Wiki, Single-way combat |
| Not a player fighting another monster (attacking one, or hit one in the last 8 ticks), in single-way combat; an NPC is someone else's while it took on or swung at them in the last 8 ticks | guessed |
| Only a player in its line of sight, as a ranged attack needs ([Line of sight](#line-of-sight)), from its tile closest to them, so a big monster sees round its own bulk; a player under it counts as seen | RS2's hunt modes have a check_vis; guessed that every aggressive monster uses it |
| A monster fighting a player who stands outside its aggression range, where it can't follow, lets go once it hasn't swung at them, nor they hit it, for 8 ticks, and walks back to its spawn | the aggression range "defines the outer limit of where the player can stand and still be attacked" (OSRS Wiki, Aggressiveness); the 8 ticks and the walk home guessed |
| The hunt range of 5 (the 2004Scape engine's default), hunting every tick, picking the nearest player with ties at random, melee's 1 tile as everyone's attack range, the count running in logged-in ticks only, the Level 6 giant rats being aggressive | guessed |

Retreat (`MeleeCombat.hitBy`): a monster attacked from outside its aggression range, or at or below its
`"retreatsAt"` share of its hitpoints (the unicorn and chickens, 25%), walks away from the player instead of fighting
back.

| Retreat behaviour | Source |
|-------------------|--------|
| Attacked from outside its aggression range (where the player stood as they swung, fired or cast), or low on hitpoints, it retreats | OSRS Wiki, Retreat |
| Retreating, it keeps within its max range (7) of its spawn, until the player leaves its interaction range (7 + 11) | OSRS Wiki, Retreat |
| A retreat also ends 8 ticks after the player last hit it, so one standing near without hitting doesn't keep it from hunting others | guessed |
| The unicorn and chickens retreat at low hitpoints | OSRS Wiki, Retreat (its list) |
| The 25% threshold, rounded up; walking straight away, diagonals first; a hit from within the aggression range ending a retreat its hitpoints don't call for; a retreating aggressive monster not hunting | guessed |

Not in yet: monsters that attack from afar (the Draynor dark wizards' step).

Check: `./gradlew test --tests 'osrsps.game.combat.AggressionTest' --tests 'osrsps.testclient.HeadlessAggressionTest'`
(two players: the spider goes for the nearer, then for the other once the nearer is tolerated; a player behind a
wall left alone until it goes; and one who lured it off past its range no longer keeping it from the other),
`RetreatTest` and `HeadlessRetreatTest`, and in RuneLite `test-runner/examples/aggro.json`, `aggro-sight.json` (the
castle's shut large doors, then opened), `aggro-leash.json` and `retreat.json`.

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

`agility` stands you at the Draynor Village Rooftop Course's rough wall (3103, 3279) with 52 Agility, the most anything
built needs, so every course and shortcut is open; run on and full, and a graceful set in the bank to try its run energy
bonus. Hitpoints stay a new account's 10, so a fall's damage shows.

`lumbridge-cows` stands you in the Lumbridge cow field (3259, 3263, a tile picked from the cow spawns) with 20 Attack,
Strength, Defence and Hitpoints, iron armour, an iron scimitar, an amulet of strength, five lobsters, a bronze axe, a
tinderbox and 100 coins, with run on.

`lumbridge-artisan` stands you beside the furnace north of Lumbridge Castle (3225, 3254) with 50 Crafting, Fletching
and Herblore and a full backpack: a knife, three logs and a bow string, 30 feathers and 15 bronze arrowheads; a needle,
five thread and two leather, a chisel and an uncut sapphire, a gold bar and a ring mould, soda ash, a bucket of sand, a
glassblowing pipe, clay and a bucket of water; a pestle and mortar, a unicorn horn, two grimy guams, two vials of water
and two eyes of newt. Something of every artisan skill to try in one place.

`lumbridge-bare` stands you on the Lumbridge spawn (3222, 3218) with a new account's stats and nothing worn or carried:
the clean start the equipment test scripts use.

`lumbridge-bank-tabs` stands you by the castle's top floor bank booths (3209, 3220, 2) carrying nothing, with logs and
a bronze axe in bank tab 1 and 1,000 coins in the main tab: for the tab script, as the test runner can't drag.

`lumbridge-run-low` stands you on the Lumbridge spawn with run on and no run energy, the energy threshold to re-enable
running at 5% and Ctrl+click to invert run mode on Run -> Walk only: for the run settings script, as nothing else sets
run energy.

Check: `::scenario lumbridge-cows` puts you by the cows in iron with the loadout above; `::reset` puts you back where
you were with your own stats and items.

## The rotten potato

Live's J-Mod tool, obj 5733, as an admins' tool: `::item 5733` gives one. The cache gives it Eat, Slice, Peel and Mash,
as live, so RuneLite shows it as it does there. Each option opens a menu of testing tools that run the `::` commands, so
every pick is logged and a non-admin is refused as for a typed command. Every menu is done; Use (an item on the potato, the potato on a player) comes next.

| Eat | What it does | Source |
|-----|--------------|--------|
| Set all stats | Asks for a level, then sets one skill a tick (each through `::setlevel`, with a message), then a last message | Wiki; the order (skill ids) and the player being delayed meanwhile are guessed |
| Wipe inventory | Deletes everything carried but rotten potatoes | Wiki |
| Heal me | `::heal`, in place of live's Setup POH: there are no houses | ours |
| Teleport to player | Asks for a name, then `::teleto` | Wiki |
| Spawn aggressive NPC | Asks for an id or name, then `::npc`; the NPC is as aggressive as its type is | Wiki; the prompts' words are guessed |

| Peel | What it does | Source |
|------|--------------|--------|
| Bank menu | `::openbank`: opens your bank wherever you are | Wiki |
| Teleport to a tile | Asks for `x z [level]`, then `::tele` | ours, in place of Teleport to RARE!: there are no rares |
| Spawn an item | Asks for `id\|name [count]`, then `::item` | ours, in place of Spawn RARE! |

Live's AMEs for all! is left out: there are no random events.

| Slice | What it does | Source |
|-------|--------------|--------|
| God mode on/off | `::god` | ours: live's Slice starts random events, which there are none of |
| Sure hits on/off | `::sure` | ours |
| Set the XP rate | Asks for a rate or `restore`, then `::xprate` (for everyone) | ours |
| Start a scenario | Asks for a name, then `::scenario`; the potato goes back in the inventory the scenario sets, or with no room there you are told `::reset` ends it | ours |
| Reset (end the scenario) | `::reset` | ours |

| Mash | What it does | Source |
|------|--------------|--------|
| Kick me out | Logs you out as the logout button does: at the end of the tick, and not within 16 ticks of a hit | Wiki; the combat lock is guessed |
| Kill me | `::hit` of every hitpoint you have, so the death is the game's own (grave, items kept); `::god` stops it | Wiki |

Live's Keep me logged in turns off an idle logout we don't have, and Transmogrify me isn't built.

| Use | What it does | Source |
|-----|--------------|--------|
| Any item on the potato, or the potato on it | Deletes that item | Wiki |
| The potato on a player | Lists their inventory in your chatbox, one line a stack | Wiki that it shows it; the chatbox list is ours. Runs where you stand, with no walk to them (guessed) |

A non-admin who clicks one loses it with a message (guessed: what live does isn't known). The options' words are live's,
so they are keys under `rotten_potato.` in the dialogue file, except our own (Heal me, Peel's Teleport to a tile and Spawn an item, and all of Slice).

Check: `::item 5733`, then Eat. Set all stats with 50 sets each skill to 50 one a tick; Wipe inventory leaves only the
potato; Teleport to player with a name online puts you on them; Spawn aggressive NPC with `goblin` spawns one on your
tile. Peel's Bank menu opens the bank. `test-runner/examples/rotten-potato-eat.json` runs Set all stats, Heal me and Wipe inventory in the real client.

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
