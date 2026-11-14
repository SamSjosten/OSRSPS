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
| `::farmtick [ticks]` | Moves your own farming clock on that many five-minute farming ticks (1 by default, up to 4,096) and grows your patches to it: allotments grow every second tick. For good: your crops keep growing from there |
| `::farmclock <pause\|run>` | Holds your farming clock where it is, so your crops grow only by `::farmtick` (for tests that check each stage), or runs it again, the crops catching up on the time it was held; a login runs it again too |
| `::farmdisease <patch\|off\|on>` | Makes the crop in one of your patches (by its save key, such as `falador_allotment_nw`) diseased now, to try a plant cure or let it die; `off` stops your crops falling ill on their own (for scripts that grow a crop through many stages) until `on` or your next login |
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
| `::npcs [radius]` | **everyone.** Lists the NPCs within radius tiles (default 15) with their index, tile, spawn tile and wander range, the party size and hitpoints of a raid NPC, and whether each is frozen, hidden, hurt (its hitpoints left), poisoned, envenomed or drained, and who it is fighting ("no one" for an idle one that can fight) or retreating from |
| `::delnpc index` | Removes NPC `index` (from `::npcs`) |
| `::hidenpc index\|mine` | Hides NPC `index` (from `::npcs`) where it stands, or shows it again if it is hidden: still in the world, but shown to no client, covering no tile, and neither clicked, hurt nor fighting. `mine` does it to every NPC you put in with `::npc`. What a boss fight does to a boss that is gone for a while |
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
`src/main/resources/skills/woodcutting.json`): every regular, dead, evergreen, oak, willow, teak, maple, mahogany, arctic
pine, yew, magic and redwood tree in the world, with the bronze to crystal axes. Words are under `woodcutting.*` in the
dialogue file. Trees outside Misthalin are where live has them and reached by ::tele: Chompy Marsh's teak
(`::tele 2334 3048`), the Hardwood Grove's teaks and mahoganies (`::tele 2821 3083`), Neitiznot's arctic pines
(`::tele 2318 3815`), and the Woodcutting Guild's redwood sections on its scaffolds (`::tele 1567 3493 1`, and level 2).

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
| Teak (35, 85 XP, timer 50, back in 15), mahogany (50, 125 XP, 100, 14), arctic pine (54, 40 XP, 140, 14), redwood sections (90, 380 XP, 440, 199), with each chart's pair per axe (`WoodcuttingG2Test`) | OSRS Wiki tree pages (read 2026-11-10) |
| Their stumps: teaktree to teak_stump and the like, redwoodtree_l/_r to their _cut sections | 242 gamevals, by name |
| Redwoods give no seed, ring or egg nests (only clue nests, which are out) | OSRS Wiki, Redwood tree |
| Out: the Woodcutting Guild (its +7, doors and the redwoods' ladders), the Hardwood Grove's trading sticks, Etceteria's trees giving no logs (Farming's patch trees are Farming's, with Woodcutting's rates) | out of scope; later pieces |

The River Lum's canoe stations (`game/skill/Canoes.kt`, words under `canoe.*`): Lumbridge, the Champions' Guild,
Barbarian Village, Edgeville and the Ferox Enclave. Each is a multiloc on a varbit of its own, so every player has their
own tree: Chop-down fells it, Shape-Canoe opens the canoeing box (416) with the canoes the level allows, Float pushes
the canoe in and Paddle opens the river map (953), where the client offers the stations that canoe reaches. The canoe
sinks on landing and the station is a tree again. Check: `test-runner/examples/canoes.json`, `HeadlessCanoeTest`.

| Canoe behaviour | Source |
|-----------------|--------|
| Log 12 (30 XP), dugout 27 (60), stable dugout 42 (90), waka 57 (150); the XP on shaping; shaping pairs by axe, whatever the canoe (bronze 32/100 to rune, dragon and crystal 112/350); the tree's pairs for bronze to black axes; one trip a canoe; an unused canoe kept until logout, and cleared by starting another station's tree | OSRS Wiki, Canoe and Canoe station (read 2026-11-14) |
| Which station each canoe reaches from each (log one on, dugout two, stable dugout three; only a waka to the Ferox Enclave or the Wilderness Pond); the box's level 12 check and list; the station states, interfaces and carving animations | 242 cache: client scripts `canoe_setup`, `canoe_paddle_*`, `canoe_location_setup_lum`, the locs, varbits and seqs |
| The tree always falling for mithril axes and up; the box's level 12 message left to the client; shaping rolling every 4 ticks; the fall and push taking 3 ticks and the trip 8; the player's push animation; the landing tiles (from the 2009scape project, not live), the Ferox Enclave's; no message on starting to chop or shape | guessed, to check against a recording |
| The Ferox Enclave and Wilderness Pond refusing a waka, in our own words, until the Wilderness is built | ours |
| Out: the travel cutscene, the sinking canoe at the landing, followers, the Wilderness warning, the Ardougne river's stations; the canoe experts' Store-axe | out of scope; later pieces |

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

### Light sources

A tinderbox used on an unlit light source (either way round) lights it at the Wiki's Firemaking level, and its
Extinguish option puts it out (`game/skill/LightSources.kt`, words under `light_source.*`). A candle goes into an empty
candle lantern, and an unlit torch lights on a fire or range. Dark areas ask `LightSources.hasLight` and swamp gas asks
`nakedFlames` and puts them out with `extinguish` (piece 7 step 2b).

| Light source behaviour | Source |
|------------------------|--------|
| Which items light (candles, black candles, torches, candle, oil and bullseye lanterns, oil lamps, the sapphire and emerald lanterns, the mining helmet), the level to light each, brightness, which set off gas and which effects can put out | OSRS Wiki, Light sources |
| Lights that are always lit: the Firemaking and max capes, the Bruma torch, the abyssal lantern lit with logs | OSRS Wiki, Light sources |
| A candle into an empty candle lantern, Firemaking 4 for a lit one, no XP | OSRS Wiki, Candle lantern |
| An unlit torch lights on anything that cooks | OSRS Wiki, Light sources |
| Item ids, names and the Extinguish option | 242 cache |
| No XP, animation or delay for lighting or putting out; the messages' words | guessed (no source; keys only) |
| Out: lamp oil (its still is in Rimmington), the worn mining helmet's Extinguish, dry sticks, spicy stews, lighting the abyssal lantern | out of scope |

### Mining

Mine on a rock mines it with the best pickaxe the player can use (`game/skill/Mining.kt`, words under `mining.*`):
clay, copper, tin, iron, coal, mithril, adamantite and runite rocks, amethyst crystals and gem rocks, with the bronze to
crystal pickaxes. A mined rock turns into an empty rock (`rocks1`/`rocks2`, amethyst an empty wall) until it respawns.
Rocks outside Misthalin are where live has them and reached by ::tele: the Mining Guild's runite and amethyst
(`::tele 3055 9721`, or `::scenario gathering`), Shilo Village's gem rocks (`::tele 2824 3001`), the Lava Maze's runite
(`::tele 3058 3885`).

| Mining behaviour | Source |
|------------------|--------|
| Levels, XP, respawn times (clay 2 ticks, copper and tin 4, iron 9, coal 50, mithril 200, adamantite 400) | OSRS Wiki rock pages |
| Each rock's success pair, fitted to and checked against the Wiki's chart points (`MiningTest`) | OSRS Wiki rock pages |
| The pickaxe sets only the ticks between rolls (bronze 8 ... rune 3; dragon, infernal and 3rd age 3 with a 1/6 chance of 2, crystal 1/4) | OSRS Wiki, Pickaxe |
| The mining gem table, 1/256 a roll: a gem (or nothing) in place of ore, no XP, the rock stays | OSRS Wiki, Mining |
| The click starts the clock and the first roll comes a whole interval later; the swing message and animation | 2004Scape mining script |
| Which empty rock each rock becomes (rockN to rocksN); swinging on the click's own tick | guessed |
| Runite rocks: 85, 125 XP, pair 1/18 (17/256 at 85, 19/256 from 97), 12 minutes back, 6 in the Mining Guild and the Wilderness | OSRS Wiki, Runite rocks |
| Amethyst crystals: 92, 240 XP, pair -18/10, 2 to 3 amethysts from one swing before an empty wall (`amethystrock_empty`) for 75 seconds | OSRS Wiki, Amethyst crystals |
| Gem rocks: 40, 65 XP, pair 28/70 (84/210 with a charged glory), one gem out of 128 (opal 60, jade 30, red topaz 15, sapphire 9, emerald 5, ruby 5, diamond 4), 99 ticks back, no mining gem table | OSRS Wiki, Gem rocks |
| Granite rocks: 45; 5kg (75 XP, pair 6/64), 2kg (60, 8/75), 500g (50, 16/100) rolled largest first, the first success given (42.0% any at 45, 68.2% at 99); sandstone rocks: 35; 10kg (60, 4/50), 5kg (50, 8/75), 2kg (40, 16/100), 1kg (30, 25/200) (56.5% to 92.7%). Each success empties the rock for 9 ticks; no gem table. Desert heat is not in yet | OSRS Wiki, Granite rocks, Sandstone rocks |
| Limestone rocks (Silvarea): 10; 26.5 XP, pair 95/350; a rock gives three limestone in three stages (limestone_rock1 to 3, then the pile of rock) and comes back whole 39, 19 or 9 ticks after its latest stage, the Wiki's 23.4, 11.4 and 5.4 seconds read as the time back to a full rock (inferred) | OSRS Wiki, Limestone rock, read 2026-11-11 |
| A charged amulet of glory (any, trimmed or eternal) adds a second 3/256 roll at the mining gem table | OSRS Wiki, mining gem table template |
| The Mining Guild's members' area: an invisible +7 on the roll (not on the level needed) and every respawn halved | OSRS Wiki, Mining Guild |
| The members' area's bounds (x 2998-3059, z 9695-9729), drawn around the Wiki's rock tiles; amethyst's 75 seconds not halved again | inferred |
| How the amethyst count is drawn (evenly, 2 or 3, on the first amethyst, kept until the wall empties); runite and gem rocks' empty rocks (by their shared models) | guessed |
| Out: mining gloves and other extra-ore items, the prospector kit, unidentified minerals, the guild's doors and level 60, essence (Runecraft), the visible-boost cap at 99 | out of scope |

### Fishing

Fishing spots are NPCs (`game/skill/Fishing.kt`, words under `fishing.*`): the Lumbridge Swamp's Net/Bait spots
(shrimps and anchovies with a small net, sardines and herring with a rod and bait) and the River Lum's Lure/Bait spots
(trout and salmon with a fly fishing rod and feathers, pike with a rod and bait). Their spawns are in
`npc-spawns/lumbridge.json`. The option re-runs every tick on the NPC (`World.repeatOp`, as RS2's `p_opnpc`).

Cages and harpoons (Fishing and Cooking 2, step FC1) fish Catherby's beach (`::tele 2850 3430`: Net/Bait 1518,
Cage/Harpoon 1519, Big Net/Harpoon 1520, whose Big Net comes later) and Musa Point on Karamja (`::tele 2924 3178`:
Small Net/Bait 1521, Cage/Harpoon 1522), built in place outside Misthalin; their spawns are in
`npc-spawns/fishing-spots.json`. A lobster pot cages lobsters from 40; a harpoon catches tuna from 35 and swordfish from
50 at Cage/Harpoon spots, sharks from 76 at Big Net/Harpoon spots. The barb-tail harpoon fishes as the harpoon does, the
dragon harpoon 20% better from 61; both work wielded. The big net (step FC2) fishes the Big Net/Harpoon spots from 16:
each of its nine catches (boots, seaweed, gloves, two mackerel, oyster, casket, cod from 23, bass from 46) is rolled on
its own, so one roll can bring up several. Karamja's karambwanji spots (step FC3, 4710, `::tele 2791 3018`) net
karambwanji from 5, level / 5 + 1 at a time, before shrimps; a karambwan vessel baited with them catches karambwan from 65
at the spots by the fairy ring DKP (4712 and 4713, `::tele 2899 3118`), each roll emptying the vessel, caught or not,
and the next baiting it again from the backpack. A small net catches monkfish from 62 at the Piscatoris Fishing Colony's
Net/Harpoon spots (step FC4, 4316, `::tele 2326 3699`, which harpoon tuna and swordfish as Catherby's do), and a fishing
rod baited with sandworms catches anglerfish from 82 at Port Piscarilius's Rod Fishing spots (6825, `::tele 1827 3769`),
a sandworm a catch. The `fishing-cooking` scenario starts there with everything built.

| Fishing behaviour | Source |
|-------------------|--------|
| Levels, XP, tools and bait (one bait or feather a catch) | OSRS Wiki fish pages, Fishing |
| A roll every 5 ticks; pike recasts 1 roll in 5 (two casts a tick apart), the next roll a tick later | OSRS Wiki, Raw pike |
| Each fish's success pair, the better fish tried first, fitted to and checked against the Wiki's charts (`FishingTest`) | OSRS Wiki fish pages |
| Spots move every 250 to 530 ticks to a free tile of their area | OSRS Wiki, Fishing spot |
| The river spots' tiles; the cast message and animation on the click, the first roll 4 ticks later, the last bait freeing its slot | 2004Scape fishing scripts |
| Spawn tiles | RSMod rev 233 Lumbridge spawns |
| The swamp spots' area (their spawn tiles, so they stay put); the player stopping when the spot moves | guessed |
| Lobster, tuna, swordfish and shark: levels, XP and success pairs, swordfish tried before tuna | OSRS Wiki fish pages (their charts give the pairs) |
| The dragon harpoon's 20% (each pair times 1.2, rounded down, which gives the Wiki's dragon harpoon charts) and its level 61; the barb-tail fishing as a harpoon | OSRS Wiki, Harpoon, Dragon harpoon, Barb-tail harpoon |
| Catherby's and Musa Point's spawn tiles; the tiles their spots move between | mejrs NPCList_OSRS and the Wiki's spot pages; 2004Scape's movement enums |
| The cage and harpoon cast messages and checks | 2004Scape rarefish and memberfish scripts |
| The big net's nine rolls, each on its own, in Mod Ash's order; mackerel, cod and bass pairs, levels and XP; the casket's rate | OSRS Wiki, Fishing spot (big net, harpoon) and fish pages; Mod Ash (3 October 2020) |
| The boots', seaweed's, gloves' and oyster's pairs and the junk's XP | 2004Scape's big net roll (the Wiki only calls them uncommon) |
| The harpoon's first roll as the other methods' (2004Scape has it a tick later); a dragon harpoon below 61 counting as no harpoon; a big net catch lost when the backpack fills mid-roll | guessed |
| Karambwanji: level 5, 5 XP, the count by level, the 100/250 pair tried before shrimps, no shrimps with the backpack full; karambwan: level 65, 50 XP, the 5/160 pair, the bait lost on every roll, the vessel baited again on its own; the spots' tiles and the karambwan spots staying put | OSRS Wiki, Raw karambwanji, Raw karambwan, Fishing spot (karambwanji) and (karambwan); mejrs NPCList_OSRS |
| The vessel's cast message and animation (human_octopus_pot); the baiting saying nothing; the karambwanji spots staying put | guessed |
| Monkfish: small net, level 62, 120 XP, the 48/90 pair; anglerfish: rod and sandworms, level 82, 120 XP, the 3/36 pair; the spots' tiles | OSRS Wiki, Raw monkfish, Raw anglerfish, Piscatoris Fishing Colony, Port Piscarilius; mejrs NPCList_OSRS |
| The Piscatoris and anglerfish spots staying put (the anglerfish spots' 8 to 830 tick moves are out); the monkfish and anglerfish messages as the small net's and rod's | guessed |
| Out: diabolic worms, pearl and barbarian rods, stripy feathers, big bass, swordfish and shark, the skills necklace casket boost, barehanded fishing, infernal and crystal harpoons, the dragon harpoon's special, outfits and other catch boosts, whirlpools | out of scope |

### Cooking

Raw food used on a fire or range, or Cook on a range, cooks it (`game/skill/Cooking.kt`, words under `cooking.*`):
shrimps, anchovies, sardines, herring, trout, pike, salmon, beef, rat and bear meat, chicken, and bread dough (range
only); lobster, tuna, swordfish and shark came with Fishing and Cooking 2's step FC1, mackerel, cod and bass with FC2, karambwan with FC3, monkfish and anglerfish with FC4. Step FC5a adds
multi-step food (`game/skill/CookingMixes.kt`, words under `cooking_mix.*`): flour and water into bread, pastry, pizza or
pitta dough, pie shells and every pie's fillings (three at a time for mud, garden, fish, admiral, wild and summer pies),
a pizza's tomato, cheese and topping, and a cake's egg, flour and milk, each pair through the Make-X box, then baked on a
range. Step FC5b adds stew (a potato and cooked meat or chicken in a bowl of water) and curry (spice, or three
curry leaves, in an uncooked stew), cooked on a fire or range; wine (grapes in a jug of water), which ferments 20 ticks
after the last jug is made, every unfermented wine in the backpack and the bank at once, a jug of wine for 200 XP or a jug
of bad wine (`game/skill/Wine.kt`; a login with unfermented wine starts the wait again); and the dairy churn
(`game/skill/Churn.kt`), whose Make-X box turns milk, cream or butter into cream, butter or cheese. With more than one to cook, the chatbox's Make-X box (`skillmulti`, interface 270, set up by client script
2046) asks which and how many; a resume_pausebutton from one of its items starts the job. Lumbridge Castle's kitchen
range (114) burns less. The cooking re-runs every tick on the loc (`World.repeatOp`).

| Cooking behaviour | Source |
|-------------------|--------|
| Levels and XP | OSRS Wiki food pages |
| 4 ticks a piece | OSRS Wiki, Raw shrimps |
| Each food's burn pair on a fire or range and on the Lumbridge range, fitted to and checked against the Wiki's charts (`CookingTest`) | OSRS Wiki food pages |
| Swordfish, shark and cod burning less on a range than on a fire; mackerel and cod helped by the Lumbridge range, lobster, tuna, swordfish, shark and bass not | OSRS Wiki food pages; Cooking range (Lumbridge Castle) |
| The Make-X box: its components, client script 2046's arguments and the pause button carrying the quantity | decompiled client scripts (osrs-dumps) |
| Karambwan: cooked from 30 for 190 XP or poison from 1 for 80, one burn chart for both (stops at 99), the box always asking with the cooked one first; a poison karambwan hitting for 5 from 6 hitpoints with its two messages | OSRS Wiki, Raw karambwan, Cooked karambwan, Poison karambwan |
| Monkfish: from 62 for 150 XP, 11/275 on a fire and 13/280 on a range (stops burning at 92 and 90); anglerfish: from 84 for 230 XP, 1/200 and 1/220 (never stops burning); the Lumbridge range cooking both as any range | OSRS Wiki, Raw monkfish, Monkfish, Raw anglerfish, Anglerfish |
| Pies, pizza, cake and pitta: levels, XP and each range pair (the Lumbridge range helping redberry, meat, botanical and mushroom pies), every pie burning to a burnt pie, the cake tin coming back | OSRS Wiki food pages; Cooking range (Lumbridge Castle) |
| The mixes: each step's level, materials, XP (only pizza toppings and chocolate cake give any), the pot, bucket, bowl or jug coming back, 1 tick a dough and 2 for the rest | OSRS Wiki item pages (Pastry dough, Pie shell, Part garden pie and the rest) |
| Meat pie and mushroom pie stopping burning at 53 and 88, where their charts reach 256, a level before the pages' text | the charts over the text |
| The first mix a tick after the choice; one pair with one to make skipping the box; the doughs' box order; no animation for mixing; a cake not baked when there is no slot for its tin; the level message as a chat line (2004Scape shows it in a box) | guessed |
| Cook with nothing raw saying Nothing interesting happens; one piece cooking with no box; the box closing as the first piece starts; the level-up box ending the job as for the other skills | guessed |
| Out: cooking gauntlets, the cooking cape, Hosidius ranges, the pies' 1-tick bake without the box, Bake Pie; stews, curries, wine, dairy, potatoes with toppings and kebabs (step FC5b) | out of scope |

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
| Cannonballs (`Cannonballs.kt`): a bar or the ammo mould used on a furnace with the mould held casts one bar into 4, 8 ticks then 10; bronze 5 (8.8 XP a bar), iron 20 (17.2), steel 35 (25.6), mithril 55 (34), adamant 75 (42.4), rune 90 (50.8); the double ammo mould casts two bars into 8 for twice the XP | OSRS Wiki, Steel cannonball and the other cannonball pages, Double ammo mould |
| Cannonballs: Dwarf Cannon and Sleeping Giants count as done; a double mould casting a last odd bar into 4; one message a cast; the animation | guessed |
| Out: the ring of forging, goldsmith gauntlets, the imcando hammer, smiths' uniform, Blast Furnace, granite cannonballs, the ancient furnace, jewellery, blurite and lovakite | out of scope |

### Fletching

A knife used on logs, or logs on a knife (`game/skill/Fletching.kt`, words under `fletching.*`), opens the Make-X box
over what those logs make: arrow shafts, javelin shafts, the shortbow and longbow (u), and the crossbow stock, from
logs to magic logs, plus teak and mahogany stocks and redwood shafts. A bow string on an unstrung bow strings it. Feathers on arrow shafts
make headless arrows, and arrowtips on those make arrows, up to 15 at a time (the box counts sets). A feather on dart
tips makes 10 darts on every click, with no box. The numbers are in `skills/fletching.json`.

Crossbows, bolts and javelins (Artisan 2, A1): crossbow limbs on a stock, with a hammer held, make the unstrung
crossbow (bronze on a wooden stock up to dragon on a magic stock), and a crossbow string strings it. A feather on
unfinished bolts makes 10 bolts a click, as darts do; the All Settings toggle "Make-X for darts and bolts" opens the box
for both. Bolt tips on bolts make tipped bolts, and javelin tips on javelin shafts make javelins, up to 10 and 15 at a
time. A chisel on a cut gem or oyster pearl cuts bolt tips. `::scenario artisan-2` stands you on the Lumbridge spawn
with 99 Fletching and the parts.

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
| Crossbows: levels, limbs and string XP; 2 ticks each, the first 1 tick after the choice; the hammer | OSRS Wiki, Template:Table/Fletching/Crossbows, Bronze crossbow (u), Bronze crossbow ("Make-X is 1, then 2 ticks") |
| Bolts: levels, XP per bolt, 10 a click with no wait; tipped bolts: levels, XP per bolt, 10 a set, 2 ticks a set; dragon bolts taking every gem tip | OSRS Wiki, Template:Table/Fletching/Bolts, Tipped bolts, Tipped dragon bolts, Bronze bolts, Opal bolts |
| Bolt tips: levels, XP per gem, 12 a gem (24 from an onyx or oyster pearls, 6 from an oyster pearl), 5 ticks a gem | OSRS Wiki, Template:Table/Fletching/Bolt tips, each tips page |
| Javelins: levels, XP per javelin, 15 a set, the first two sets a tick apart, then 2 ticks a set | OSRS Wiki, Template:Table/Fletching/Javelins, Bronze javelin |
| Animations: `xbows_fletching_*` for limbs, `xbows_stringing_crossbow_*`, `human_fletching_add_bolt_feathers_*` and `human_fletching_add_bolt_tips_*` by metal | cache 242 gamevals |
| The Make-X box for tipping bolts and for javelins, the bolts' sets-of-10 title; the gem's cutting animation for bolt tips; `human_fletching_add_arrow_tips` for javelins; no animation for broad bolts; the first tipped set and the first bolt tips 2 ticks after the choice, and the first javelins 1; broad bolts' Slayer unlock taken as unlocked | guessed |
| Out: the redwood shield; kebbit, antler and ascension bolts and the hunters' crossbows; the jeweller's chisel | out of scope |

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

Artisan 2, A3: an air, water, earth or fire orb on a battlestaff makes that elemental battlestaff. A chisel on a blamish snail
shell, round or pointed, makes its snelm, and on amethyst opens the box over amethyst bolt tips, arrowtips, javelin tips and
dart tips. Weave on a loom, or wool, jute or willow branches used on one, opens the box over a strip of cloth, an empty sack, a
drift net and a basket. Misthalin has no loom: South Falador Farm's (3039, 3287) is where live has it, reached by ::tele.

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
| Battlestaves 54/58/62/66, 100 to 137.5 XP, 'Make-X is 3t, then 2t'; snelms 15, 32.5 XP, 2 ticks; amethyst cuts 83/85/87/89, 60 XP, 15/15/5/8 a piece, 2 ticks; the loom's four at 10/21/26/36, 12/38/55/56 XP, 4 ticks then 3, a drift net 3 | OSRS Wiki, each battlestaff, snelm and amethyst tips page, Loom (read 2026-11-10) |
| The animations `human_battlestaff_crafting`, `human_snailshellcutting`, `human_amethystcutting` | cache 242 gamevals |
| The loom's animation (`farming_useloom`, by its name); the first snelm and amethyst cut 2 ticks after the choice; the battlestaff's flash left out | guessed |
| Out: sinew, magic roots, yak hair and the other members' spinning; d'hide shields, snakeskin, the costume needle; the jeweller's chisel; the slayer ring, quest silver items, silver bolts, enchanting; linen, canvas and cotton (Varlamore's yarns), the Vampyre snelm, combination battlestaves | out of scope |

### Herblore

Clean on a grimy herb cleans that one at once (`game/skill/Herblore.kt`, words under `herblore.*`, numbers in
`skills/herblore.json`). A clean herb and a vial of water, either used on the other, open the Make-X box for its
unfinished potion. A pestle and mortar on a unicorn horn, chocolate bar, kebbit teeth, bird nest, desert goat horn or
blue dragon scale opens the box for the ground item; the first is ground as the choice is made. An empty vial used on a
sink, fountain or water pump fills every vial held, one a tick. A secondary on its unfinished potion opens the box for
the finished potion (23 of them, attack potion to Saradomin brew), a (3) dose each. Making goes through `Making.kt`.

Potions of more ingredients or made by the dose (Artisan 2, A2a, herblore.json's `mixing`): a torstol or torstol potion
(unf) on a super attack(4), strength(4) or defence(4), with all four held, makes a super combat potion(4); amylase
crystals on a super energy and lava scale shards on an antifire potion make stamina and extended antifire, one crystal
or shard a dose; unicorn horn dust, snake weed and nail beast nails, in that order, take a super restore of any dose to
sanfew serum.

Bob Barter (herbs) stands at the Grand Exchange (Artisan 2, A2b, `game/skill/Decanting.kt`, words under `bob_barter.*`).
Decant on him, or "Can you decant things for me?" in his talk, opens the decant interface; a dose button (or its number
key) decants every potion in the backpack to that many doses, with one potion of fewer for what is left over. Empty
vials he is short of are bought from the coins held.

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
| Super combat (90, 150 XP), stamina (77, 25.5 XP a dose), sanfew's three steps (65, 32 x doses + 64 XP in all), extended antifire (84, 27.5 XP a dose); 2 ticks each; sanfew's steps one potion a use with no Make-X box; super combat from a clean torstol giving 2 vials back | OSRS Wiki, Super combat potion, Stamina potion, Sanfew serum, Extended antifire, Money making guide/Making super combat potions |
| Super combat from a torstol potion (unf) giving 3 vials back (its own vial spare too) | inferred |
| Which item pairs start a super combat potion (the torstol on any of the three; the three on each other start nothing); Zogre Flesh Eaters counting as done | guessed |
| Out: divine potions, extended super antifire, coconut-milk potions, Varlamore's, herb sacks, tar, barbarian mixes, ashes, other ground items | out of scope |
| Bob Barter (herbs) at the Grand Exchange (5449, `ge_expert_herbs`; Talk-to, Prices, Decant), the decant interface (582) and its four dose buttons; any dose 1 to 4, noted potions too, the potions' own vials reused, spare vials back, missing ones bought at the vial's guide price + 2; results noted when any input was or when they won't fit; his lines | OSRS Wiki, Bob Barter (herbs), Decanting and Transcript:Bob Barter (herbs) (read 2026-11-10); cache 242 (NPC, interface 582), osrs-dumps decant.if3 and decant_init |
| His spot (3155, 3480, the Wiki's map pin, wandering 1); a potion being any tradeable item with Drink and all four "Name(n)" doses, not in a cup or keg (no Guthix rests, kegs or raid potions), and another id of the name decanting with it only when it is as tradeable; out of cash part way, potions decanted in backpack order; "short of inventory space" when even notes won't fit; 3 coins a vial with no guide price; the chatheads' expressions | guessed |
| Out: the herb and potion price guide (Prices, and its Talk-to option, end there), cups of tea, barbarian mixes, Guthix rests, oils, moth mixes; the free-to-play lines | out of scope |

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

### Magic: teleports

The standard spellbook's teleports to places, Varrock Teleport to Ape Atoll Teleport, and the teleport tablets for the
same places (`game/skill/magic/Teleports.kt`, landing areas and XP in `skills/magic-teleports.json`). Cast on a spell
checks the level and runes (a staff stands in for its runes, as for combat spells), takes them, gives the XP and lands
the player on a free tile of the spell's area 3 ticks later; Break on a tablet uses it up and lands 4 ticks later, with
no level needed and no XP. Above level 20 Wilderness both are refused with the two lines Home Teleport shows there,
and nothing is taken. Destinations whose towns aren't built land on live's tiles all the same; where the map isn't
loaded the player lands on the area's centre. The quests that unlock some of these on live count as done.

| Teleport behaviour | Source |
|--------------------|--------|
| The ten spells' levels, runes and magic tab components; Ape Atoll's banana (params 606 and 607); the eight `poh_tablet_*` tablets, Break as iop1 | cache 242 (osrs-dumps 2026-11-03-rev242) |
| Base XP; tablets needing no level and giving no XP; no teleport above level 20 Wilderness | OSRS Wiki, each spell's page, Magic tablet, Wilderness level |
| Landing areas for Lumbridge, Falador, Ardougne, Trollheim, Ape Atoll, Kourend Castle and Civitas illa Fortis | OSRS Wiki, each spell's map |
| Animation 714 with graphic 111 at height 92, the landing 3 ticks after the cast with the animation stopped, the player held meanwhile (being attacked doesn't stop it), runes and XP taken at the cast, Varrock and Camelot landing within 2 tiles of the Wiki's tile | inferred from 2004Scape's teleport script |
| Watchtower landing on its Wiki tile only, on level 2; the tablet's animations 4069 then 4071 with graphic 678 two ticks in, landing 4 ticks after the break | guessed |
| Not built: the Achievement Diary destinations and Configure (Grand Exchange, Seers' Village, Yanille), Teleport to House, Tele Other, Teleport Block, Ancient and other books' teleports | next steps |

`::scenario magic` stands you on the Lumbridge spawn with 99 Magic, a staff of air, runes for ten of every teleport
and two of each tablet.

Check: `./gradlew test --tests 'osrsps.game.skill.magic.*' --tests 'osrsps.testclient.HeadlessTeleportsTest'`, and in
RuneLite `test-runner/examples/magic-teleports.json`.

### Thieving: more pickpockets and their gear

Thieving 2 steps T1a to T1c (`game/skill/Pickpocketing.kt`, `game/skill/PickpocketGear.kt`). Pickpocket now works on the
farmers (nine on Misthalin's farms, `npc-spawns/lumbridge-farms.json`, with their combat and drops), Al Kharid's man and
warriors, Edgeville's guards, and Kandarin's and the Wilderness's rogues, Knights of Ardougne, watchmen, paladins, gnomes
and heroes, and the far pickpockets from Dorgesh-Kaan to Mor Ul Rek (all reached by `::tele`). Gloves of silence and the dodgy necklace work when worn.

| Pickpocketing behaviour | Source |
|-------------------------|--------|
| Farmers: level 10, 14.5 XP, 150 to 240 out of 256, 1 damage; a farmer's coin pouch (9 coins) 123/128 or a potato seed 5/128; level 7, speed 6, max hit 1, stab, respawn 25, the members' drop table | OSRS Wiki, Farmer, Coin pouch (read 2026-11-10) |
| The farmers' nine tiles and which look stands on each (farmer1 to farmer3, farmer1_f to farmer3_f, farmer4) | OSRS Wiki, Farmer (its map) |
| Al Kharid warriors: the warrior woman's level, XP, 100 to 240, damage and warrior's pouch; Al Kharid's man (3261) as a man; Edgeville's guards (3254) as guards, whose 50 to 240 the Guard page's chart now confirms | OSRS Wiki, Al Kharid warrior (Mod Ash: the same code), Guard, Thieving |
| Gloves of silence: the NPC's low and high times 1.05, rounded down; worn out by 62 failed pickpockets, with the Wiki's warning one before the end and its six Check messages | OSRS Wiki, Gloves of silence, each NPC's chart |
| Dodgy necklace: one failure in four costs no stun and no damage and uses a charge; the tenth crumbles it; Check gives the charges left | OSRS Wiki, Dodgy necklace |
| Both items' counts belong to the player, not the item (`player.charges`) | OSRS Wiki, both pages |
| The bands between the gloves' Check messages; after a saved failure the next pickpocket comes 2 ticks later; the gloves wear even when the necklace saves; the necklace's and the gloves' last messages; "farmer" and "Al Kharid warrior" in the messages and their caught line; the farmers' animations and wander | guessed |
| Rogues: level 32, 36.5 XP, 75 to 240, 2 damage; a table out of 144 (a pouch of 25 to 40 coins 123, 8 air runes 9, a jug of wine 6, a lockpick 5, a poisoned iron dagger 1); 22 in Rogues' Castle and south-west of the Mage Arena (`npc-spawns/rogues-castle.json`) | OSRS Wiki, Rogue, Coin pouch (read 2026-11-10); mejrs NPCList_OSRS.json |
| Knights of Ardougne: 55, 84.3 XP, 50 to 240, 3 damage, a pouch of 50, a 4.8-second stun (8 ticks) | OSRS Wiki, Knight of Ardougne |
| Watchmen: 65, 137.5 XP, 15 to 160, 3 damage, a pouch of 60 and a bread every time | OSRS Wiki, Watchman |
| Paladins: 70, 131.8 XP, 40 to 170, 3 damage, a pouch of 80 and 2 chaos runes every time, an 8-tick stun | OSRS Wiki, Paladin |
| Gnomes (the men, women and children alike, but for the women's and children's 133.5 XP): 75, 133.3 XP, 43 to 175, 1 damage; a table out of 128 (2 to 4 arrow shafts 56, a pouch of 300 30, a swamp toad 24, gold ore 8, an earth rune 5, a king worm 3, a fire orb 2) | OSRS Wiki, Gnome, Gnome woman, Gnome child |
| Heroes: 80, 163.3 XP, 39 to 160, 3 damage; a table out of 128 (a pouch of 200 to 300 105, 2 death runes 8, a jug of wine 6, a blood rune 5, a fire orb 2, a diamond 1, gold ore 1); a 6-second stun (10 ticks) | OSRS Wiki, Hero |
| Warriors: Ardougne's (the pre-2022 warrior woman type) and the 2022 types join the warrior's pocket | OSRS Wiki, Warrior (Thieving) |
| No room: a pickpocket needs a slot for a new pouch and one for each extra it gives that isn't a stack already held, and room in every stack a success could add to (an extra, or the most of a table item already held) | guessed (the Wiki gives the extras, not the room they need) |
| Where they stand: East Ardougne's 5 knights, 22 paladins (the castle's two floors among them), 3 heroes and 10 warriors, the Watchtower's 4 watchmen, the Tree Gnome Stronghold's 153 gnomes (`npc-spawns/thieving-kandarin.json`) | mejrs NPCList_OSRS.json (live, pre-Nov 2022) |
| Their combat: the Wiki's speeds, max hits, bonuses and respawns | OSRS Wiki, each NPC's infobox |
| The T1b NPCs' caught line (the townsfolk's), their names in the messages, their animations, their wander; the gnomes' max hit 1 (the Wiki's, where the formula gives 0) | guessed |
| Cave goblins (Dorgesh-Kaan): 36, 40 XP, 1 damage; a table out of 20 (a pouch of 10 to 50 coins 7, then 1 each: bat shish, coated frogs' legs, fingers, frogburger, frogspawn gumbo, green gloop soup, bullseye lantern, 1 to 2 cave goblin wire, 1 to 4 iron ore, oil lantern, swamp tar, tinderbox, unlit torch) | OSRS Wiki, Cave goblin (Dorgesh-Kaan), Coin pouch (read 2026-11-10) |
| Fremennik citizens (Rellekka's nine): 45, 65 XP, 2 damage, a pouch of 40 | OSRS Wiki, Fremennik citizen |
| Desert bandits (the Bandit Camp): 53, 79.4 XP, 50 to 240, 3 damage; a pouch of 30 5/7, an antipoison(1) 1/7, a lockpick 1/7 | OSRS Wiki, Bandit (Bandit Camp) |
| Wealthy citizens (Civitas illa Fortis): Pickpocket is their first op; 50, 96 XP, 35 to 200, 3 damage, a 4-second stun (7 ticks); a pouch of 85 79/85, house keys 5/85 | OSRS Wiki, Wealthy citizen |
| Vyres (Darkmeyer's thirty): 82, 306.9 XP, 8 to 128, 5 damage, a 10-tick stun; a table out of 132 (a pouch of 230 to 315 109, 2 death runes 8, a blood pint 6, an uncut ruby 5, 4 blood runes 2, a diamond 1, cooked mystery meat 1) | OSRS Wiki, Vyre |
| Elves (Lletya's three, Prifddinas's 49): 85, 353.3 XP, 6 to 100, 5 damage, a 10-tick stun; a table out of 128 (a pouch of 280 to 350 105, 2 death runes 8, a jug of wine 6, 3 nature runes 5, a fire orb 2, a diamond 1, gold ore 1) | OSRS Wiki, Elf (Thieving) |
| TzHaar-Hur (Mor Ul Rek's inner city): 90, 103.4 XP, -200 to 200, 4 damage, a 10-tick stun; 3 to 7 tokkul 182/195 or an uncut sapphire 5, emerald 4, ruby 3, diamond 1; their heat does 4 more on every success unless ice gloves are worn | OSRS Wiki, TzHaar-Hur |
| Where the far ones stand (`npc-spawns/thieving-far.json`): 174 spawns, the wealthy citizens on the Wiki map's nine tiles | mejrs NPCList_OSRS.json (live, pre-Nov 2022); OSRS Wiki, Wealthy citizen (its map) |
| The cave goblins' and Fremennik citizens' charts (75 to 240 and 65 to 240: the Wiki has none); which wealthy citizen look stands on each tile; the far NPCs' caught line and names in the messages; a Wiki table's coins read as the pouch the Coin pouch page gives that NPC; the wealthy citizens' last 1/85 (an easy clue, left out) giving nothing | guessed |
| Not built: repairing the gloves, the necklace's Break, the Ardougne diary, Shadow Veil, Rocky, the clue scrolls from pickpocketing; the T1b and T1c NPCs' drops and fights; West Ardougne, the Mourner Tunnels, Tree Gnome Village and the Battlefield; the urchins' distractions, Prifddinas's crystal shards and seeds, the quests and the fire cape that gate the far areas; Pollnivneach's bandits and the Menaphite thugs (blackjacking), the pirates | next steps |

`::scenario thieving` stands you beside a farmer on the farm south of Varrock with 99 Thieving and Hitpoints, 54
Hunter, gloves of silence and a dodgy necklace worn, a spare necklace and food.

### Magic: Enchant Crossbow Bolt

Cast on the magic tab's Enchant Crossbow Bolt (`game/skill/magic/BoltEnchant.kt`, the enchantments in
`skills/bolt-enchant.json`) opens the Make-X box over the enchantments whose bolts the player holds, each shown as the
bolt it makes. The choice casts that enchantment the picked number of times: each cast checks the level and runes (a
staff stands in for its runes), takes them and up to 10 bolts, and gives as many enchanted bolts and the spell's XP.
Walking off stops it, as making does, but it doesn't stop mining, fishing or the like: the Wiki gives it as zero-time
Magic. One cast a tick at most, however fast the clicks come. What the enchanted bolts do in combat is the next step.

| Bolt enchanting behaviour | Source |
|---------------------------|--------|
| The ten enchantments, opal to onyx: levels 4, 7, 14, 24, 27, 29, 49, 57, 68 and 87, XP 9, 17.5, 19, 29.5, 37, 33, 59, 67, 78 and 97; 10 bolts a cast; each also enchanting its dragon bolts, and diamond and onyx their ascension bolts; a box of spells | OSRS Wiki, Enchant Crossbow Bolt and each spell's page (read 2026-11-10) |
| The runes (the sub-spell objs `04_enchant_bolt_opal` on, params 365 to 370); the button `magic_spellbook:xbows_enchant` (218:13); the animation `human_xbow_enchant_arrowtip` (4462) and graphic `xbows_enchanting_tips_spot` (759) | 242 cache |
| Casting leaves gathering going, and clicks can cast faster than the pace | OSRS Wiki, Enchant Crossbow Bolt |
| A cast every 3 ticks, the first on the choice; gathering still stops if the Make-X box is open at its next roll, as for a level-up box (live's spell interface may not); the ordinary bolts before the dragon or ascension ones; fewer than 10 enchanted when fewer are held; only the held bolts' spells in the box; the box's title and the no-bolts message (`bolt_enchant.title`, `bolt_enchant.no_bolts`, words on the server) | guessed |

Check: `./gradlew test --tests 'osrsps.game.skill.magic.BoltEnchantDataTest' --tests 'osrsps.testclient.HeadlessBoltEnchantTest'`,
and in RuneLite `test-runner/examples/magic-bolt-enchant.json`.

### Thieving: chests and pick-lock doors

Thieving 2 step T2 (`game/skill/ThievingLocks.kt`). The chests and doors are the cache's, where the cache map puts them;
only their options are new.

| What | Source |
|---|---|
| Search for traps on a chest: the trap found, disabled, the chest opened and the loot given, two ticks apart; it stands open 4 ticks, then empty (`emptytrapchest` 11740) until it relocks. Below the level: "nothing". Open instead sets the trap off | OSRS Wiki, each chest's page; 2004Scape `trapped_chest.rs2` and `thieving.rs2` for the steps and words |
| 10 coins (11735): 13, 7.8 XP, 7 s. Nature runes (11736): 28, 25 XP, a nature rune and 3 coins, 15 s. 50 coins (11737): 43, 125 XP, 50 s. Blood runes (11738, Chaos Druid Tower dungeon): 59, 250 XP, 2 blood runes and 500 coins, 135 s, then a second trap sends the looter to East Ardougne (2584, 3337). Ardougne Castle (11739): 72, 500 XP, 1,000 coins, a raw shark, an adamantite ore and an uncut sapphire, 500 s, then Witchaven (2696, 3281) | OSRS Wiki, Chest (10 coins), (nature runes), (50 coins), (blood runes), (Ardougne Castle) |
| Steel arrowtips (11742): 47, 150 XP, 5 steel arrowtips and 20 coins, 90 s, empty as `emptypickchest` 11741. Open says it is locked; Search for traps, or a lockpick used on it, picks it every time with a lockpick held | OSRS Wiki, Chest (steel arrowtips); 2004Scape `attempt_locked_chest` |
| The traps: the nature rune chest's `floor(HP * 12 / 100) + 3` of current Hitpoints, the blood rune chest's `(9 * HP + 225) / 50` | OSRS Wiki, Chest (nature runes), Chest (blood runes) |
| Doors: Open from outside says it is locked; from inside, Open or Pick-lock lets the player through. Outside is the door tile's own side of its wall. Going through moves the player across and the door stays shut, so nobody walks in behind (exploit sweep round 25, R25B-1; 2004Scape swings it open for a moment) | 2004Scape `locked_door.rs2` (`check_axis_locactive`) |
| East Ardougne's north (11719, 1, 3.8 XP) and south (11720, 16, 15 XP) doors, 32 to 250. Ross' house (40178, 13, 15 XP, 4 to 40). The Chaos Druid Tower (11723, 46, 37.5 XP, 8 to 75). Ardougne Castle (11724, 61, 50 XP, 4 to 40) | OSRS Wiki, each door's page (levels, XP, East Ardougne's chart); 2004Scape's `locked_door.dbrow` (the other charts) |
| The trap before a pick on Ross', the tower's and the castle's doors (32 to 250, 220 to 280, 200 to 270): its blow is 10% of current Hitpoints (none at 10 or less), 6% of full, or 10% of full, and it ends the try. Ross' 32 to 250 gives the Wiki's 77% at 13 and 2% at 99 | OSRS Wiki, Door (Ross' House), (Chaos Druid Tower), (Ardougne Castle); 2004Scape's charts |
| The 10 and 50 coin and Ardougne Castle chests' trap damage (the Wiki says "substantial" and "high"; the nature rune chest's formula is used); Ross' door, a newer loc than 2004Scape's, opening the same way; refusing a chest whose loot wouldn't fit (2004Scape gives it regardless) | guessed |
| Not built: the Dorgesh-Kaan and Rogues' Castle chests, the doors that need a lockpick, the Ardougne sewer gates, the Ardougne diary, the gas random event | next steps |

### Thieving: East Ardougne's stalls

Thieving 2 step T3 (`game/skill/Thieving.kt`, `npc-spawns/ardougne-market.json`). The six stalls are the cache's, in the
market where the cache map puts them; stealing works as at Draynor's (a guard or keeper in sight stops it).

| What | Source |
|---|---|
| Bakery (11730): 5, 16 XP, 2.4 s, a cake 13/20, bread 5/20 or a chocolate slice 2/20. Silk (11729): 20, 24 XP, 4.8 s, silk. Fur (11732): 35, 45 XP, 7.2 s, a grey wolf fur. Silver (11734): 50, 205 XP, 19.2 s, silver ore 16/20, a silver bar 3/20 or a tiara 1/20. Spice (11733): 65, 92 XP, 6 s, spice. Gem (11731): 75, 408 XP, 60 s, an uncut sapphire 105/128, emerald 17, ruby 5 or diamond 1 | OSRS Wiki, Stall (Thievable) and each stall's page |
| The keepers (the two bakers, silk merchant, fur trader, silver merchant, spice seller and gem merchant, the `*_ardougne` types) and the market's ten guards (`ardougne_guard`, also pickpocketed as guards), where they stand | mejrs NPCList_OSRS.json (live, pre-Nov 2022) |
| The guards' fight | OSRS Wiki, Guard (Ardougne 1) |
| Who watches which stall (the guards the bakery; knights too the silk and fur; paladins too the silver, spice and gem), the empty stall (`market` 634), the keepers' 20-minute ban, the keepers standing still and the guards wandering 5 | guessed (the watchers 2004Scape's) |
| Not built: Keldagrim's, Kourend's, Prifddinas's, Civitas illa Fortis's, Port Roberts's, Rellekka's and Ape Atoll's stalls, the keepers' talk and shops, the Ardougne diary | next steps |

### Farming

Farming steps 1 to 5 (`game/skill/farming/`): the growth clock, each player's patches, allotments, herbs and flowers,
compost and disease, trees and fruit trees. Every patch is a
multiloc the map already places, drawn by one of the cache's `farming_transmit_a` to `e` varbits; each tick the
player's patches grow to the clock and the varbits of the area they stand in are set, the same varbits live sends for
the same map squares, so RuneLite's Time Tracking farming panel reads them. Crops grow on live's real-time farming
ticks (every 5 minutes: flowers every one, allotments every second, herbs every fourth), shifted by a fixed offset
per account, and keep growing while the player is logged out. The patches are saved with the player (`farming` in the
save); a `::scenario` starts with every patch overgrown, and `::reset` puts the player's own back.

Built so far: the two allotments, the flower patch and the herb patch south of Falador (reach them with `::tele 3054 3305`). Rake clears a weed stage at a
time for 4 XP a weed; three seeds and a seed dibber plant a raked patch; a watering can waters a growing crop until
its next stage; a grown crop is harvested with a spade, one crop at a time, until its three lives run out, each crop
keeping its life at the chance its seed page gives (more with magic secateurs); the planting XP comes with the last
crop, as on live. A herb takes one seed, can't be watered, and is picked with a spade a herb at a time the same way.
A flower takes one seed, can be watered, and is picked once by hand for its produce (woad three leaves, limpwurt three
roots and a random number below the Farming level, divided by ten, more), with its planting and harvest XP together.
Clicking a patch again two ticks into a harvest picks two crops at each roll from then, as on live. Bare soil grows a weed
stage every farming tick until it is overgrown again.

Compost, supercompost and ultracompost go on a raked patch or a growing crop, once, for 18, 26 and 36 XP and an empty
bucket: an allotment or herb then has 4, 5 or 6 harvest lives instead of 3, and every patch falls ill less often. Each
time a crop grows past its first stage it may fall ill instead (27 in 128 for herbs; compost halves that,
supercompost takes 80% off, ultracompost 90%, rounded up). A diseased crop stops growing; a plant cure (Cure, or the
cure used on it) makes it well and leaves the vial, and one left alone dies at its next tick of the next slower clock.
A dead crop is cleared with a spade, for the planting XP after all. A fully grown flower keeps the allotments beside it
well: marigolds potatoes, onions and tomatoes, rosemary cabbages, nasturtiums watermelons, a white lily everything.

Trees grow in the tree patches west of Lumbridge castle (`::tele 3193 3229`), in Varrock palace's garden, in Falador
park, in Taverley and in the Gnome Stronghold. An empty plant pot used on a bare patch is filled with soil (no trowel needed); a tree seed
used on the filled pot (with the trowel and the tree's Farming level) makes a seedling, and a watered seedling grows
into a sapling at the next farming tick, in the backpack or the bank. The sapling is planted in a raked tree patch
with a spade, the pot coming back. Trees grow every 40 minutes (oak 4 stages, willow 6, maple 8, yew 10 with a first
one lasting 5 minutes from planting, magic 12), and a grown tree is checked once (Check-health) for its check-health
and planting XP. It
is then chopped as a wild tree of its kind is (Woodcutting's level, rates, XP and bird nests) until it falls at 1 log
in 8, and its stump is dug up with a spade for its roots (one at the tree's level and one more every 8 levels, four at
most), a dig that can fail below level 99; a stump left alone grows back. A diseased tree is pruned with secateurs, 3
tries in 4 working (each try made when a Farming skill roll passes), for a leaf; one left ill dies and is cleared with a
spade, for the planting XP.

Fruit trees grow in the fruit tree patches of Catherby (`::tele 2860 3432`), the Gnome Stronghold, the Tree Gnome
Village and Brimhaven, from saplings made the same way (apple, banana, orange, curry, pineapple, papaya, palm and
dragonfruit). They grow through six 160-minute stages, falling ill at 18 in 128 (less with compost) in 4 of them, and
a grown tree is checked once for its check-health and planting XP. It then bears six fruit, picked one at a time for each tree's
per-fruit XP, and grows one more every 40 minutes up to six. With no fruit left it can be chopped down (one swing,
no logs) and its stump cleared with a spade. A diseased fruit tree is pruned with secateurs for leaves.

| Farming behaviour | Source |
|-------------------|--------|
| Patch locs, which varbit draws each, and which value draws which stage (weeds, growing, watered, grown, diseased, dead) | cache 242 (2026-11-11), the patch multilocs and the names of the locs they show |
| Which map squares send which patches' varbits (Falador: square 12083 north of z 3272) | RuneLite's Time Tracking plugin (FarmingWorld), worked out from live's varbits |
| 5-minute farming ticks, allotments every 10 minutes, crops growing while logged out; the offset of 0 to 31 minutes | OSRS Wiki, Farming (the 2018 update; Mod Ash) |
| Allotment levels, three seeds, planting and harvest XP (the planting XP on the last harvest), stages; 4 XP a weed and weeds back every 5 minutes; three harvest lives | OSRS Wiki, Allotment patch, Weeds, each seed's page |
| Herb levels and XP, herbs every 20 minutes, a spade to pick them, the planting XP with the final herb; flower levels, XP, yields and 5-minute growth, both XPs when picked; limpwurt's 3 roots plus a random 0 to level - 1, divided by ten, and magic secateurs helping it; a second click 2 ticks into a harvest picking two at a time (allotments and herbs) | OSRS Wiki, Herb patch, Flower patch, each seed's page, Limpwurt seed (Mod Ash) |
| The chance to keep a life (`(1 + low)/256` to `(1 + high)/256`, from each seed page's percentages), magic secateurs' 10% | OSRS Wiki, each seed's page, Variable crop yield |
| Animations farming_raking, farming_seed_dibbing, farming_watering, human_dig | cache 242 seq names |
| A rake roll every 4 ticks that always takes a weed; planting and watering 3 ticks after the click; a crop every 4 ticks; raking and harvesting stopping on a full inventory; the three grown values counting down the last lives; the offset taken from the account's name | guessed |
| Compost's XP and extra lives (Mod Ash: 4, 5, 6), its disease reduction rounded to a 128th, herbs' 27/128, disease only past the first stage and never when grown, dying at the next slower tick, the planting XP on clearing a dead patch; which flower protects which crop | OSRS Wiki, Compost, Supercompost, Ultracompost, Disease, Herb patch, Flower patch, Allotment patch |
| Allotments and flowers falling ill as often as herbs; watering halving the chance; curing, composting and clearing 3 ticks after the click; compost refused on a grown, diseased or dead crop | guessed |
| Huasca's chance to keep a life (58 of 256 at level 1, 80 at 99; the Wiki gives none); herbs and flowers picked with picking_low, a herb every 4 ticks, a flower 2 ticks after Pick; flower produce that doesn't fit dropping to the floor; magic secateurs' help on limpwurt as 10% more numbers to draw from; a second click on the first or second tick of a harvest counting | guessed |
| Tree levels, planting and check-health XP (the planting XP on Check-health or on clearing a dead tree), 40-minute growth and stages (a yew's first 5 minutes from planting); seedlings in a plant pot with a trowel, growing to saplings within 5 minutes when watered, banked too; saplings planted with a spade; 1 log in 8 felling a farmed tree; roots by Farming level; stumps that can fail to dig below 99 and never at 99; pruning with secateurs at 3 in 4 after a skill roll; maple's 13/128 and magic's 9/128 chances to fall ill | OSRS Wiki, Tree patch, Seedling, each tree's page, Disease (Mod Lenny), Roots (Mod Kieren) |
| Oak 17/128, willow 15 and yew 11 to fall ill; a stump growing back at the tree's next 40-minute tick; digging up a stump 129/256 at level 1, and the pruning skill roll the same; a try at a stump or a prune every 4 ticks; one leaf a prune; a pot filled and a sapling planted 3 ticks after the click; Check-health at once, with no animation; farming_filling_plantpot, farming_trowel_digging and pruning_mid as the animations | guessed |
| Fruit tree levels, planting, check-health and per-fruit XP, six 160-minute stages, six fruit once checked and one more every 40 minutes, 18/128 to fall ill in 4 of 6 growths, pruning with secateurs; Chop-down shown only with no fruit left | OSRS Wiki, Fruit tree patch, Disease, each fruit tree's page; cache 242, the fruit tree multiloc |
| The 4 risky growths being the second to the fifth; a fruit every 3 ticks with picking_high; chopping a fruit tree in one swing with any axe at any Woodcutting level, for no logs or XP; a fruit tree stump always cleared at the first dig, for no roots or XP, and never growing back; an axe on a tree with fruit refused | guessed |
| Not built yet: the scarecrow, digging up a healthy crop, hops, bushes, compost bins, the Tool Leprechauns and the gardeners' payments; Guide (op4) | next steps |

`::scenario farming` stands you beside the Falador allotments with 99 Farming, the tools, 30 of every allotment
seed and some herb and flower seeds (the rest in the bank, with a trowel, secateurs, a rune axe, plant pots, tree seeds
and oak saplings for the tree patches, fruit tree seeds and apple saplings for the fruit tree patches). `::farmtick [n]` moves your own farming clock on n five-minute ticks and grows your patches to it.

Check: `./gradlew test --tests 'osrsps.game.skill.farming.*' --tests 'osrsps.testclient.HeadlessFarmingTest' --tests 'osrsps.testclient.HeadlessFarmingTreeTest' --tests 'osrsps.testclient.HeadlessFarmingFruitTreeTest'`.

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
| Scatter on demonic ashes: fiendish 10 Prayer XP, vile 25, malicious 65, abyssal 85, infernal 110 | XP from the Wiki (Remains, read 2026-11-10); seq 2295 (farming_ingredient_sprinkle), the one-tick delay as a bury, and one message a tick later (`bones.scattered`) guessed |
| Stacks stop at 2,147,483,647; `::item` adds what fits | client limit; adding part is ours |

Check: `::item 1351` puts a bronze axe in your inventory; drag it to another slot; Examine it. `::item bones 2`, then Bury both: Prayer XP goes to 9
(`test-runner/examples/lumbridge-bury-bones.json` does it in the real client). `::item 25769`, then Scatter: 25 Prayer XP
(`scatter-ashes.json`).

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
`Feoras.kt`, `Wilfred.kt`, `LumbridgeTownsfolk.kt`), each from the OSRS Wiki's transcript as a player with every quest done
sees it. The words are in the dialogue file, like every other line.

| Who | What they do |
|-----|--------------|
| The General Store's Shop keeper and Shop assistant, Bob | Talk-to; "what do you sell" opens their shop. Bob's Repair and an item used on him answer that nothing needs repairing |
| The Sheared Ram's Bartender | Sells a beer for 2 coins |
| Arthur the Clue Hunter (16811), by the Grand Exchange's south wall since 242.1 (`varrock.json`) | Explains every clue type (three pages of them) and turns clue vessels on and off (varbit 9365) |
| Donie and Gee | One of four opening menus at random, the quests, Lumbridge, and Gee's small monsters |
| Feoras (16806, the smithing tutor since 242.1) in the smithy | Smithing advice with its icon boxes, a hammer for a player with none, and his shop (`lumbridge_blacksmith`, inv 1032, the Wiki's prices) |
| Wilfred (16808, the woodsman tutor since 242.1) | Woodcutting and Firemaking advice; an axe and tinderbox when you have none and room for them; the Woodcutting cape at 99 (trimmed with another 99) |
| Abigaila, upstairs in the General Store | Her story; her scrying ball refuses a player carrying or wearing anything (the Theatre of Blood isn't built) |
| The Ironman tutor | Explains Ironman; this game has no Ironmen, so his armour and the account modes are refused or end the talk. Since 242.1 the cache shows him to Ironmen only (311 is a multi-NPC on varp `ironman` showing 16814), so no account here sees him; both ids have his handlers |
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
| Monlum (16809, the Mining tutor until 242.1) by the east mine: his mining advice with its boxes, and a bronze pickaxe for a player with none (carried, worn or banked) and a free slot | the Wiki's Transcript:Monlum |
| Finlay (16810, the Fishing tutor until 242.1) by the shore: his fishing advice with its boxes, and a small fishing net for a player with none and a free slot | the Wiki's Transcript:Finlay |
| The fight animations (by the stand and walk animations each type has: the updated giant rat's, the giant frog's for all three frogs, the spear goblin's, the updated unicorn's), the curved bone's 1/5,012.5 kept as 1/5013, and every wander range (5) | guessed |
| For the tutors: that a banked pickaxe or net counts as owned (as for Wilfred's axe); the icon items in the boxes (the old tutors'); their wander ranges (Monlum 4, Finlay 3: their Wiki map radii); that with a full backpack they give nothing and say so in a line of their own (the Wiki has none) | guessed |
| Every chathead expression; that a worn ghostspeak amulet counts as carried; the bank as the only store Urhney checks (no Death's Office or item retrieval yet); the candle landing at the player's feet with no free slot; having heard about lanterns kept with the account by name | guessed |

The unicorn retreats at a quarter of its hitpoints ([NPC aggression](#npc-aggression), guessed threshold). The tutors' offers to show a place on the world
map lead back to the first menu without opening the map, which isn't built yet; the fishing spots and the mines are the
gathering skills'. Hewey, Abigale and the two goblin sergeants are in [the rest of the swamp](#the-rest-of-lumbridge-swamp).
What the talking NPCs say and each new NPC's examine are in the dialogue file (keys `father_urhney.*`,
`lost_city_warrior.*`, `lost_city_archer.*`, `lost_city_monk.*`, `lost_city_wizard.*`, `candle_seller.*`,
`monlum.*`, `finlay.*` and `examine.npc_<id>`, one for the level 3 giant rats and one for the level 6).

Check: `test-runner/examples/lumbridge-swamp.json` examines each kind and kills a giant rat and a giant frog for their
drops, then talks to Urhney, the adventurers, the candle seller and both tutors; `HeadlessLumbridgeSwampTest` fights a
giant rat to death, talks to Urhney and the camp's monk, buys a lit candle and gets a pickaxe and a net from the tutors
through the headless client, with every branch of the talks in `SwampTalkTest` and `SwampTutorsTest`, the stats in
`NpcCombatTest` and the drop rates in `NpcDropsTest`.

### The rest of Lumbridge Swamp

Misthalin piece 7 step 1: the swamp's shed, the Water Altar's ruins, the River Lum's broken raft, the stepping stone to
the desert, the swamp's four quest NPCs and the goblins and spiders between the castle and Draynor. The shed, the ruins
and the raft's Grapple are `game/content/LumbridgeSwamp.kt`; the raft's Jump-to and the stone are `skills/agility.json`;
the NPCs are `npc-spawns/lumbridge-swamp-quest.json` and `npc-spawns/lumbridge-west.json`.

| Behaviour | Source |
|-----------|--------|
| The shed's door (2406 at 3202,3169) lets a player in or out; with a dramen or lunar staff wielded it refuses with our own line, as Zanaris (where live takes them) is piece 8's. Live's Elite Lumbridge Diary does the same; there are no diaries | OSRS Wiki, Shed (Lumbridge Swamp) |
| The tools inside (10375) give a rake or a spade, whichever isn't held, and a choice of the two with neither held | OSRS Wiki, Tools (gardening) |
| The Water Altar's ruins (34815) refuse a water talisman or tiara, and Enter, with our own line: Runecraft isn't built | OSRS Wiki, Mysterious ruins; the 242 cache |
| The broken raft's Grapple (17068): 8 Agility, 19 Strength and 37 Ranged, a crossbow wielded and a mith grapple worn, from either bank's shortcut icon (3246,3179 and 3259,3179) to the other, no XP; the grapple breaks one time in 25 | OSRS Wiki, Broken Raft (the infobox's 19 Strength; its text says 17) and Mith grapple (Mod Ash's 1/25); the 242 map |
| The raft's Jump-to (17069 and 17070) at 48 Agility, no grapple, since 12 August 2026 | OSRS Wiki, Broken Raft |
| The stepping stone to the desert (16513 at 3214,3135) at 66 Agility, no diary since 8 May 2024, from 3214,3140 to 3214,3131 and back | OSRS Wiki, Stepping stone (Lumbridge Swamp); the bank tiles by flood fill over the 242 map |
| Abigale and Hewey (7428, 7429) and Sergeants Mossfists and Slimetoes (6278, 6279) stand where live has them, as the 242 cache's multi-NPCs, which show nothing after Misthalin Mystery and Another Slice of H.A.M. (every account has done both) | mejrs's live NPC list; osrs-dumps and the 242 cache |
| 11 level 2 goblins, 8 level 2 giant spiders and 7 level 1 spiders round the H.A.M. trapdoor, west of the castle | mejrs's live NPC list |
| The shed door standing open 3 ticks; the tools' menu words and messages, and nothing with both tools held; the raft's firing and swimming animations and their 2 and 6 ticks; the jumps going by way of the raft or the stone, two ticks each; the grapple's break rolled on the far bank; every wander range | guessed |

Words for the new lines (`lumbridge_swamp.*`, the two new `agility.*_level` lines) and the two new loc examines go
through the Words thread.

Check: `test-runner/examples/lumbridge-swamp-rest.json` goes through the shed and takes both tools, uses a water
talisman on the ruins, crosses the raft by Grapple and back by Jump-to, jumps the stone both ways and mines coal in the
west mine; `HeadlessLumbridgeSwampRestTest` does the shed, the tools, the ruins and the Grapple through the headless
client, `HeadlessShortcutsTest` the raft's Jump-to and the stone, `LumbridgeSwampTest` the rules, and
`NpcSpawnFileTest` the two spawn files.

### The Lumbridge Swamp Caves

Misthalin piece 7 step 2b (`game/content/LumbridgeSwampCaves.kt`, words under `swamp_caves.*`): the dark hole by the
candle seller wants a rope used on it once (varbit 279 remembers it), then Climb-down goes down and the climbing rope
below climbs back up. The caves are dark: with no light (`LightSources`) the insects warn after 15 ticks and bite for
1 every tick from 30, and a player within 3 tiles of the rope climbs out instead. In the north-west and south-middle
gas a naked flame flares and, 7 ticks later, explodes for a quarter of the Hitpoints level and goes out. The two
stepping stones are Agility shortcuts whose falls put the lights out. The tunnel to the Chasm of Tears refuses. The
caves' monsters are step 3 (below).

| Swamp caves behaviour | Source |
|-----------------------|--------|
| The dark tiles (`areas/lumbridge-swamp-caves-dark.txt`): every level 0 tile reachable from the climbing rope, and the far side of the south stone | 242 map, flood-filled |
| The gas tiles (`areas/lumbridge-swamp-caves-gas.txt`) | OSRS Wiki's hazard map, placed by its wall beast holes (within a tile) |
| Rope once, the 3-tile climb-out, 15 and 30 ticks, 1 a tick, the flare and the 7 ticks, a quarter of Hitpoints | OSRS Wiki, Lumbridge Swamp Caves, Darkness, Light sources (the caves page's older 12 damage not used) |
| Stepping stones: level 1, 3 XP, 1 XP for a fall, 51/256 to 252/256, a fall puts the lights out | OSRS Wiki, Stepping stone (Lumbridge Swamp Caves) (Mod Ash's chart), Light sources |
| Ids, options, varbit 279 and the tiles of each stone's sides | 242 cache and map |
| The explosion's message, the rope's messages, where an escape lands, a fall landing back where it started with no damage | guessed |
| Out: the screen's darkness (varbit 278), refusing attacks with no light, the Fire of Eternal Light, the cellar hole and Dorgesh-Kaan (piece 8) | out of scope |

#### The swamp caves' monsters and the Slayer level gate

Misthalin piece 7 step 3a (`npc-spawns/lumbridge-swamp-caves.json`, `game/content/SwampCaveGoblins.kt`, words under
`cave_goblin.*`, `combat.slayer_level` and `fishing.*`): the caves hold 21 cave bugs, 36 larvae, 17 cave slimes, 3
cave crawlers, 11 big frogs, 3 giant frogs and 4 cave goblins, with their stats, attacks and drops in
`npc-combat.json` and `npc-drops.json`. A monster with a `slayerLevel` refuses melee, ranged and magic from a player
below that Slayer level (cave bugs 7, crawlers 10, slimes 17), doesn't fight back against them and isn't hit by their
bouncing or area attacks. Cave bugs and crawlers heal 1 every 5 ticks (`regenTicks`). The cave goblins beg not to be
hurt the first time; after the promise they talk at random, and hand a lit torch to a player with no light or a
tinderbox to one without. Two fishing spots stay put: Small Net catches frog spawn (33) or swamp weed, Bait catches
cave eels (38) or slimy eels (28).

| Swamp caves monsters | Source |
|----------------------|--------|
| Spawns: tiles and types | mejrs data_osrs NPCList_OSRS.json (live, pre-Nov 2022), kept to the dark tiles; counts agree with the Wiki's Lumbridge Swamp Caves page |
| Stats, max hits, attack styles, weaknesses, respawns, Slayer levels, poison, aggression, the 5-tick heal | OSRS Wiki monster pages (Cave bug, Cave slime, Cave crawler, Big frog, Giant frog, Cave goblin), read 2026-11-10 |
| Drops (weights, tertiaries) | OSRS Wiki drop tables, same pages |
| That a Slayer monster refuses below its level (the message itself is unsourced) | OSRS Wiki, Slayer |
| The goblins' talk, the torch and tinderbox gifts and when they come up | OSRS Wiki, Transcript:Cave goblin (monster) |
| Frog spawn, swamp weed, cave and slimy eels: levels, XP, catch rates | OSRS Wiki, Frog spawn, Swamp weed, Cave eel, Slimy eel |
| Ids, options, animations | 242 cache |
| The promise kept until logout, the even chances among the goblins' talks, their expressions, a gift skipped with a full backpack, the threat's 8-tile cry, the cave bug's death animation, the frog spawn and swamp weed catch messages | guessed |
| Out: Slayer XP (no Slayer tasks yet), the threatened goblins running away | out of scope |

#### Rockslugs and wall beasts

Misthalin piece 7 step 3b (`game/content/SwampCaveSlayer.kt`, words under `swamp_slayer.*`): 5 rockslugs and 7 wall
beasts' holes join the caves' spawns. No hit takes a rockslug below 1 hitpoint (`minHitpoints` in `npc-combat.json`),
and down there it stops fighting and wanders off; a bag of salt used on one at 4 or less finishes it, and one used at
5 or more is wasted. A wielded brine sabre kills them outright. A wall beast sits in its hole (a `nomove` spawn) and
reaches for whoever steps onto the tile in front: a spiny helmet or any slayer helmet repels it, and it comes out to
fight; anyone else is grabbed, held for 4 ticks and hurt for up to 18, once each time they step there. Out of a fight
for 20 ticks it goes back in. Hurting rockslugs needs Slayer 20, wall beasts 35.

| Rockslugs and wall beasts | Source |
|---------------------------|--------|
| Spawns: tiles and types | mejrs data_osrs NPCList_OSRS.json (live, pre-Nov 2022); the Wiki's Wall beast page lists the same seven holes |
| Each hole grabs from the tile south of it | 242 map: the only cave tile beside each |
| Stats, max hits (4, and 18 for the grab), Slayer levels, the 1 hitpoint, the salt at 4 or less and the wasted bag, the brine sabre, the helmets, drops | OSRS Wiki, Rockslug, Bag of salt and Wall beast, read 2026-11-10 |
| "Your helmet repels the wall beast!" | OSRS Wiki, Wall beast |
| Ids, the hole and beast forms, animations | 242 cache |
| The grab's 4 ticks, its even roll from 0 to 18, one grab per step onto the tile, the 20 ticks before it goes back in, the 10-tick respawn, the salt and grab messages, no salting animation and no XP for the salt's kill | guessed |
| Out: the Slug salter perk, Slayer XP (no Slayer rewards or tasks yet) | out of scope |

#### The H.A.M. Hideout

Misthalin piece 7 step 4a (`game/content/HamHideout.kt`, `npc-spawns/ham-hideout.json`, words under `ham_hideout.*`
and `pickpocket.caught_ham`): the trapdoor west of Lumbridge's general store is locked. Pick-Lock opens it at 4% at
Thieving 1 to 98% at 99 (24% to 99% with a lockpick in the backpack) for 3.8 XP, and a fail numbs the fingers,
Thieving down 1 (never below 1). Picked open (varbit `ham_thief` shows it open), Climb-down takes the player to the foot of the ladder
and Close shuts it; the ladder climbs back up. Inside, 26 members can be pickpocketed (Thieving 15, 22.2 XP, loot or a
H.A.M. coin pouch), 11 sit, and 22 guards of levels 12, 18 and 22 fight and drop the Wiki's table. A caught thief
takes 1 to 3 damage and a concussion, unless Agility takes the blow; the third concussion bundles them out to the
surface or into Jimmy the Chisel's cell, 4% less likely for each piece of H.A.M. clothing worn; a catch stuns for 7
ticks (the Wiki's 4 seconds). The cell door is locked; one Pick-lock from inside keeps trying, 9 ticks a try, until the
player is out (4% to 99% at Thieving 1 to 99 on the first try, or 24% to 118% with a lockpick, rising in four equal
steps to 27% to 123%, or 47% to 141%; each fail 0.5 XP). A trapdoor pick takes 9 ticks.

| The H.A.M. Hideout | Source |
|--------------------|--------|
| Spawns: tiles and types (Sigmund, quest-only, left out) | mejrs data_osrs NPCList_OSRS.json (live, pre-Nov 2022) |
| The trapdoor's two forms and their options, the ladder, the cell door, ids, varbit 235 | 242 cache |
| Pick-Lock's rates at 1 and 99, the lockpick's, 3.8 XP, the numbing; no rise on repeated fails | OSRS Wiki, Trapdoor (H.A.M. Hideout), read 2026-11-10 (a Jagex moderator's word for the last) |
| The trapdoor pick's 9 ticks (4-tick manipulation left out) | OSRS Wiki, Tick manipulation, read 2026-11-10 |
| The cell door's rates at 1 and 99, with and without a lockpick, first and after the rise; four equal steps; a restart resetting it; 3.8 XP, 0.5 a fail; no picking from outside | OSRS Wiki, Door (H.A.M. Hideout Jail), read 2026-11-10 |
| Members: level 15, 22.2 XP, 136/256 to 240/256, 1 to 3 damage, the 4-second stun, Agility's 1/256 to 255/256 share of blows, the loot and pouch, concussions, the three quoted messages, 4% a clothing piece | OSRS Wiki, H.A.M. Member, read 2026-11-10 |
| Guards: stats, speeds, max hits, defences, drops (one table for the three) | OSRS Wiki, H.A.M. Guard, read 2026-11-10 |
| Linear chances between the Wiki's ends, the numbing stopping at 1, the cell door's 9 ticks a try and no numbing there, the stun's 7 ticks, the even surface or cell, the tiles, the guards' animations, the other messages | guessed |
| Out: the trapdoor's occasional 1 XP on a fail, the easy clue, Talk-to on guards, seated members, Johanhus and Jimmy, the quest-only archers and mages, the storerooms (step 4b, below) | out of scope |

#### The H.A.M. Storerooms

Misthalin piece 7 step 4b (`game/content/HamStorerooms.kt`, `npc-spawns/ham-storerooms.json`, words under
`ham_storerooms.*`): every account counts as having done Death to the Dorgeshuun, so the hidden trapdoor south of the
Deacon's podium shows open (varbit `dttd_ham_trapdoor_state` 2) and Climb-down leads to the storerooms' ladder, which
climbs back up. Four storerooms have locked doors: Pick-lock needs Thieving 23 and works at the Wiki's 10 to 254 out of
256 (60 to 300 with a lockpick), 3.8 XP, or 0.5 XP on a fail; from inside, Open lets the player out. The door stays shut
while the player steps through, so no one can follow a picker in. Two cracks lead into
the south-west room without a pick, and the large room to the north has an unlocked double door. Each small chest takes
its own key (bronze south-west, iron north-east, silver north-west, steel south-east and the large room's two), which
breaks, and rolls the Wiki's 150-slot table of coins and jewellery; a coin stack without room for the most a roll gives
(99) is refused before the roll, key kept. Five level 22 guards patrol the corridors; one who
sees a door being picked attacks, unless a failed pickpocket has sent it back to its post, after which it stays there
and leaves the player be. Pickpocketing a guard (Thieving 20, 22.2 XP, 1 to 3 damage, no concussions) gives a key of
each colour at 1/10 each, the H.A.M. coin pouch, or the rest of the Wiki's table.

| The H.A.M. Storerooms | Source |
|-----------------------|--------|
| Guards' tiles and types | mejrs data_osrs NPCList_OSRS.json (live, pre-Nov 2022); OSRS Wiki, Guard (H.A.M. Storerooms) |
| The trapdoor's forms and varbit 2270, the ladder, doors, cracks, chests, double door, keys, ids and tiles | 242 cache |
| Open after the quest (its later steps climb down without a pick) | inferred from OSRS Wiki, Death to the Dorgeshuun/Quick guide, read 2026-11-10 |
| Doors: Thieving 23, 3.8 XP, 0.5 a fail, the chart's 10 to 254 and 60 to 300 | OSRS Wiki, Door (H.A.M. Storerooms), read 2026-11-10 |
| Which key opens which chest; the key used up and its message | OSRS Wiki, H.A.M. Storerooms, Small chest and Steel key (H.A.M.), read 2026-11-10 |
| The chest table, 20 outcomes out of 150 | OSRS Wiki, Small chest, read 2026-11-10 |
| Guards: level 22, stats, speed 5, max hit 3, defences, respawn; pickpocket level 20, 22.2 XP, up to 3 damage, the table; attacking a door picker in sight unless a failed pickpocket has sent them back | OSRS Wiki, Guard (H.A.M. Storerooms) and H.A.M. Storerooms, read 2026-11-10 |
| The guards' patrols, sight range and pickpocket chance (the members' 135 to 239), the usual stun, a settled guard staying settled, the 9-tick door pick and one try a click, the door staying shut as the player steps through in a tick, Open from inside, the landing tiles, the 4 free slots a chest needs, 0 coins giving nothing, the messages but the key's | guessed |
| Out: the easy clue, the chests' open look, the crack's animation, Talk-to on the guards, aggression when spoken to without the robes | out of scope |

#### Farms and roads between the towns

Misthalin piece 7 step 5 (`game/content/FarmsRoads.kt`, `npc-spawns/misthalin-farms-roads.json`, words under
`farms_roads.*`, `gardener.*`, `tool_leprechaun.*`, `tarquin.*`, `rat_burgiss.*` and `seth_groats.*`): the NPCs live has
between Lumbridge, Draynor and Varrock that no other file spawned. The cow fields north-west of Lumbridge and north of
Fred's farm (53 cows, 16 calves), 41 ducks and drakes swimming on the River Lum north of them, 19 chickens south of the
Champions' Guild and north of Groats' farm, 53 level 2 goblins in the fields between Lumbridge and Draynor and a giant
spider, two Farmers tending the fields, the Master Farmer south of Varrock (pickpocketed as Draynor's is), three crows,
two swans, three imps, and the sheep pen south of Varrock (10 sheep to shear, 3 rams). On the Lumbridge to Varrock road:
Rat Burgiss, two unicorns, a black bear (level 19, which fights and drops as the Wiki has it) and a level 5 goblin.

The patch gardeners Dreven, Vasquen, Fayeth and Treznor show their four options; looking after crops, advice, the
Farming Supplies shop, Pay and Trade wait for the Farming skill thread and say so. A Tool Leprechaun by each of their
patches and by Draynor Manor's belladonna patch answers what he stores and where it goes, and asks again; his store,
Exchange and Deposit-all wait for Farming too. Tarquin at the Champions' Guild's canoe station talks about canoes by
Woodcutting level, as Barfy Bill does; his Store-axe and the canoes wait for step 11. Rat Burgiss is too busy to chat and
Seth Groats is off to milk his cows.

| Farms and roads | Source |
|-----------------|--------|
| Every NPC's type and tile | mejrs data_osrs NPCList_OSRS.json (live, pre-Nov 2022), checked against every other spawn file (none within 4 tiles of the same NPC) and the 242 map (swimmers on water, the rest on land) |
| The three sheep mejrs saw shorn spawned unshorn | inferred: a shorn sheep regrows its wool |
| The gardeners' menu | OSRS Wiki, Transcript:Dreven, read 2026-11-10 (taken to be every gardener's: inferred) |
| The Tool Leprechaun's talk | OSRS Wiki, Transcript:Tool Leprechaun, read 2026-11-10 |
| Tarquin's talk and its Woodcutting bands | OSRS Wiki, Transcript:Tarquin, read 2026-11-10 |
| Rat Burgiss's and Seth Groats's lines | OSRS Wiki, Transcript:Rat Burgiss (its members' version) and Transcript:Seth Groats, read 2026-11-10 |
| The black bear: speed 4, max hit 3, slash, no bonuses, respawn 50, weak to fire 30%, not aggressive; bones, bear fur, raw bear meat, the ensouled head and the beginner clue | OSRS Wiki, Black bear, read 2026-11-10 |
| Every wander range, the chathead expressions, the player repeating a gardener's option, the Farming refusal's words | guessed |
| Out: the Evil Chicken (a random event); the gardeners' payments and shop and the leprechauns' store (the Farming skill thread); canoes (step 11); stray sightings near Lumbridge and Varrock (a dwarf, a H.A.M. Member, Ali the Leaflet Dropper, Avan, Katrine, Sarah, Julie, Guidor: the 12a sweep) | out of scope |

#### The Lumber Yard and Tolna's rift

Misthalin piece 7 step 6 (`game/content/LumberYard.kt`, `npc-spawns/lumber-yard.json`, the `construction_supplies`
shop, the `shortcut_lumber_yard_fence` obstacle, words under `sawmill_operator.*`, `tolna.*` and `tolna_rift.*`): the
Lumber Yard north-east of Varrock, the Earth Altar's ruins south of it and the top of Tolna's rift.

The broken fence on the yard's south side is climbed over both ways. The Sawmill operator's talk follows the Wiki's
transcript: his first option opens the Make-X box of the seven planks, his second his Construction supplies shop (on
Trade too, his op 4, as his op 3 is Buy-plank), his third says goodbye. Buy-plank opens the box at once. Each plank chosen
takes one unnoted log of its kind and the Wiki's fee (100, 250, 500, 1500, 2500, 5000, 7500 coins); with none of the logs
he says so, and with too few coins he makes as many as they pay for and says what each costs. The six mewing crates and
the yard's crates hold nothing now that Gertrude's Cat is done; its barrels do nothing. The Earth Altar's ruins refuse
Enter and an earth talisman or tiara, as the Water Altar's do, until Runecraft is built.

Every account has done A Soul's Bane, so login sets the varbits that show Tolna at the top of his rift, the rope in it and
the five warning signs round it. Tolna talks as the Wiki has him after the quest; the rift's rope leads down into its
dungeon (below); the signs read as the Wiki says.

| Lumber Yard and Tolna's rift | Source |
|------------------------------|--------|
| The NPCs' types and tiles | mejrs data_osrs NPCList_OSRS.json (live, pre-Nov 2022); the Wiki's Sawmill operator and Tolna maps agree |
| The sawmill's Make-X box (since 16 April 2020), the planks, their logs and fees | OSRS Wiki, Sawmill, read 2026-11-10 |
| The operator's talk and his refusals | OSRS Wiki, Transcript:Sawmill operator, read 2026-11-10 (making as many as the coins pay for: inferred from its "for all of them") |
| Construction supplies: 130%, 50%, no change, the stock | OSRS Wiki, Construction supplies, read 2026-11-10 |
| The broken fence: Climb-over, no level, no XP | OSRS Wiki, Broken fence (Lumber Yard), read 2026-11-10 |
| Tolna's talk | OSRS Wiki, Transcript:Tolna (after A Soul's Bane), read 2026-11-10 |
| The signs' text | OSRS Wiki, Transcript:Warning sign (Tolna's rift), read 2026-11-10 |
| Launa left out | OSRS Wiki, Launa: she went home with Brana after the quest |
| The three varbits after A Soul's Bane, the crates finding nothing, the fence's two tiles | inferred |
| The fence's animation (the Varrock fence's), the barrels' "Nothing interesting happens", the cost line's words, the chathead expressions | guessed |
| Out: sawmill vouchers (Forestry), Gertrude's cat and kittens, the clue crate, the shop's top-up of bolts of cloth and bronze nails when opened low | out of scope |

#### Tolna's rift dungeon

Misthalin piece 7 step 6b (`game/content/TolnasRift.kt`, `npc-spawns/tolnas-rift.json`, the rift monsters' entries in
`npc-combat.json` and `npc-drops.json`, words under `tolna_rift.*`): the dungeon below Tolna's rift as it is after A
Soul's Bane.

Enter on the rift's rope climbs down into the lobby; the rope there climbs back up to beside Tolna. The rift's other holes
send the player to the rope. The lobby opens onto four rooms, each with its monsters, all aggressive and at their
post-quest levels: angry bears, unicorns, giant rats and goblins (level 47) in the Anger room to the north-west, fear
reapers (55) in the Fear room to the north-east, confusion beasts (63, no poison) in the Confusion room to the south-east
and hopeless creatures (71, weak to fire) in the Hopeless room to the south-west. None drops anything but the angry giant
rat nearest the entrance, which drops bones.

| Tolna's rift dungeon | Source |
|----------------------|--------|
| The lobby and four rooms, the monsters, no drops but the one rat's bones | OSRS Wiki, Tolna's rift and Angry giant rat, read 2026-11-11 |
| The other holes' line | OSRS Wiki, Rift (Tolna's rift), read 2026-11-11 (the page marks its wording as maybe wrong) |
| Speeds, max hits, styles, aggression, poison, respawn 25 for the confusion beasts and hopeless creatures | OSRS Wiki, the seven monsters' pages, read 2026-11-11; bonuses, the fear reaper's and confusion beast's max hit 6 and the fire weakness from the DPS calculator (weirdgloop/osrs-dps-calc) |
| The monsters' tiles | mejrs data_osrs NPCList_OSRS.json (live, pre-Nov 2022), moved with the dungeon from its old place (192 tiles east, 4544 north) to the 241 cache's; every one lands on open floor in its room |
| Which room is which, the post-quest dungeon being the square at x 3072 to 3135, z 5248 to 5311, any weapon hurting the monsters, the hopeless creatures dying once, the fight animations | inferred |
| The tiles the climbs land on (3105, 5278 and 3309, 3452), the climbs' animations and waits, respawn 25 for the other five, which rat id drops the bones | guessed |
| Out: the quest's rooms (weapon rack, fire wall, black hole, dark holes, confusing doors) and Tolna's room; the Varrock diary's re-entry task | out of scope |

#### Silvarea

Misthalin piece 7 step 7 (`game/content/Silvarea.kt`, `npc-spawns/silvarea.json`, the bat's entries in `npc-combat.json`
and `npc-drops.json`, the limestone rocks in `game/skill/Mining.kt`, words under `odd_old_man.*` and `silvarea.*`): the
valley between Varrock's east fence and the River Salve.

The members' gate in Varrock's east fence opens both leaves together. Thirteen bats flap about the valley: level 6, they
stab for 1 and drop bat bones. The limestone rocks give three limestone each in three stages and come back whole a short
while after (Mining's table above). Every account has done Rag and Bone Man II, and live lets a player who took one
reward get the other as well, so the Odd Old Man hands back a lost ram skull helm, then a lost bonesack, one a talk; with
both owned (carried, worn or banked) he talks about his "sorting" while the sack on his back mumbles. His wish-list reads
its one wish left. The trapdoor to Zemouregal's base shows Enter after Defender of Varrock and waits for the base.

| Silvarea | Source |
|----------|--------|
| The NPCs' types and tiles | mejrs data_osrs NPCList_OSRS.json (live, pre-Nov 2022); the Wiki's Bat page lists Silvarea |
| The bat: speed 4, max hit 1, stab, no bonuses, stab and slash +2, crush +5, magic +2, ranged 0, respawn 35, 35% weak to air, not aggressive; bat bones always | OSRS Wiki, Bat, read 2026-11-11 |
| The Odd Old Man's talk after the quest | OSRS Wiki, Transcript:Odd Old Man, read 2026-11-11 |
| The wish-list's text | OSRS Wiki, Wish-list, read 2026-11-11 |
| The gate's two leaves (`fai_varrock_member_gatel` and `_gater`) and the trapdoor's Enter after Defender of Varrock | the 242 cache; checked on the real map by `silvarea.json` |
| Every account owning both rewards, limestone's restore times, the bats' animations | inferred |
| The wish-list in a message box, the bats' wander range, the chathead expressions, the base's refusal | guessed |
| Out: Zemouregal's base and its armoured zombies, the Giantsoul amulet trade-in, the easy STASH, the hunting trail's plants and the pot-boiler (no options after the quests), Elias White (at the Jolly Boar Inn or Uzer once his quests are done, Wiki) | out of scope |

#### Paterdomus

Misthalin piece 7 step 8 (`game/content/Paterdomus.kt`, `npc-spawns/paterdomus.json`, `obj-spawns/paterdomus.tsv`,
words under `drezel.*`, `ivan_strom.*` and `paterdomus.*`): the temple on the River Salve after The Blood Moon Rises.

The monks of Zamorak are gone for good and the Temple Guardian with them. The front doors and the mausoleum's two gates
have no open types in the cache, so Open puts the player through to the other side. The spiral stairs climb from the
ground floor (only on the temple's own tiles: the top's type stands in 28 other places) and the castle's ladders reach the top floor, where a bucket lies. The trapdoor north of the temple climbs
down to the mausoleum. Drezel hands back a lost wolfbane dagger (not into a full backpack), then a lost temple library
key, and otherwise talks as after The Blood Moon Rises; Ivan Strom talks about the columbarium he is looking for and
hands over Drakan's medallion. The library's trapdoor, open since In Aid of the Myreque, climbs down to the secret
library and its ladder back up. The Holy barrier and the 65 Agility ornate railing refuse: Morytania waits for its own
piece. The ground floor's altar is the church's.

| Paterdomus | Source |
|------------|--------|
| No monks (cleared by The Blood Moon Rises), no Temple Guardian (never back after Priest in Peril) | OSRS Wiki, Paterdomus Temple and Temple Guardian, read 2026-11-11; the guardian's multi-NPC hides it after the quest's early stages (242 cache) |
| Drezel's tile, his talk and the dagger and key he hands back | mejrs data_osrs NPCList_OSRS.json and the Wiki's Drezel map; OSRS Wiki, Transcript:Drezel, read 2026-11-11 |
| Ivan Strom living here after the quest, his talk | OSRS Wiki, Ivan Strom, The Blood Moon Rises and Transcript:Ivan Strom, read 2026-11-11 |
| The top floor's bucket at 3414,3493 | OSRS Wiki, Bucket (added 30 June 2026) |
| The stairs' four types on their own tiles only; 16673 standing in 29 places | the cache's map; the exploit review's rule on types that stand elsewhere |
| The library trapdoor open after In Aid of the Myreque (`burgh_temple_trapdoor`, set at login) | OSRS Wiki, Temple library key; inferred from the varbit's name |
| Ivan's tile (beside Drezel), the doors and gates moving the player through, the landings below the trapdoors, Drakan's medallion handed over only when none is owned, the chathead expressions, the Morytania refusal | guessed; the tiles checked on the real map by `paterdomus.json` |
| Out: the monuments, the well, the stained glass, the plinths and bookcases upstairs, the coffin, the cell gate, the organ, the keyhole, the library's bookcases (a quest's books), the east trapdoor, the Temple Trekking sign; Morytania past the Salve | out of scope (12a's sweep, or Morytania's piece) |

#### Digsite

Misthalin piece 7 step 9a (`game/content/Digsite.kt`, `npc-spawns/digsite.json`, words under `digsite_people.*`,
`elissa.*`, `panning_guide.*`, `digsite_barge.*` and `digsite_sign.*`): the Digsite's people and the canal barge after
The Dig Site and Bone Voyage. Digging, the specimen trays and panning are step 9b.

The Digsite workmen greet the player as the great archaeologist, and each of the three students thanks them for the
item the quest found. Elissa and the Panning guide talk as their transcripts have them. The workmen's Steal-from and the
teddy student's Pickpocket are `Pickpocketing`'s (level 25; the workmen 10.4 XP and their table out of 11, the student
her teddy). At the barge, the canal's guard helps the player aboard by talk or Embark and the barge's guard helps them
off by talk or Disembark; Quick-Travel, the guard's Fossil Island option and the Lead Navigator refuse, since Fossil
Island is its own region, and the Junior Navigator sends the player to the Lead Navigator. The foreman thanks the player,
the barge workman and Ed Wood are too busy to talk, and Captain Errdo's glider east of Varrock has crashed. The workers'
gate east of Varrock wants 153 Kudos, which the museum doesn't count yet, so its guard turns everyone away. The
signposts read their site's level.

| Digsite | Source |
|---------|--------|
| The NPCs and their tiles; the barge's guard and the navigators on its deck (plane 1) | mejrs data_osrs NPCList_OSRS.json; the Wiki's Student map for which student is which |
| The talks, the busy lines, the guard's Kudos refusal and the signposts' text | OSRS Wiki transcripts (Digsite workman, Student, Elissa, Panning guide, Barge guard, Barge foreman, Barge workman, Ed Wood, Lead Navigator, Junior Navigator, Captain Errdo, Museum guard) and Signpost (Digsite), read 2026-11-11 |
| The barge's post-quest types (`fossilquest_progress` 50 shows them; John and David hidden) | the 242 cache's multi-NPCs; checked on the real map by `digsite.json` |
| The workmen's Steal-from (25, 10.4 XP, 1 damage, 4-second stun, coins 4/11, brush 3/11, rope, bucket, gloves, spade 1/11) and the student's Pickpocket (25, no XP, 100 to 240, a teddy) | OSRS Wiki, Digsite workman and Student, read 2026-11-11 |
| Nobody having 153 Kudos | inferred: the museum counts no Kudos yet (`VarrockMuseum`) |
| The workmen's pickpocket taking 4 ticks, twice the usual 2 | inferred from the Wiki's "roughly twice as long" (Digsite workman) |
| The workmen's success chart (the student's), the tiles boarding and leaving put the player on, Embark and Disembark skipping the talk, the patrolling workmen standing still, the chathead expressions, the Fossil Island refusal | guessed; the tiles checked on the real map by `digsite.json` |
| Out: digging, the trays, panning, the winches, sacks, bushes, barrel, chest and buried skeletons (step 9b and 10); the guard calling out as the player nears the gate; the Digsite pendant; Fossil Island | out of scope |

Misthalin piece 7 step 9b (`game/content/DigsiteDigs.kt`, `obj-spawns/digsite.tsv`, words under `digsite_dig.*`):
digging, the specimen trays and panning after The Dig Site, which every account has done, so every exam counts as
passed.

A dig is a tool used on soil, and the site is the one the player stands in, by the Wiki's site maps: the training digs
take a trowel (5 Mining XP), level 1 a trowel with leather gloves and boots worn (6 XP), level 2 a rock pick (7 XP) and
level 3 a trowel with a specimen brush and jar carried (8 XP). Each site rolls the Wiki's finds table, one find a dig.
The wrong tool, a spade or a brush brings the nearest workman's line, or shows it as a message when no workman is
within 10 tiles. A specimen tray searched with a jar gives 0.5 Mining XP and a find; without one a workman stops the
player. Pan at a panning point, or an empty panning tray used on it, fills the tray with mud for 2 Fishing and 2 Mining
XP, and Search on the muddy tray empties it for a find. One empty tray lies in the panning tent. The specimen-jar sacks
give a jar to a player carrying none; the other sacks and the buried skeletons hold nothing, and a trowel on a skeleton
brings a workman's line.

| Digsite digs | Source |
|--------------|--------|
| The tools for each site, the XP, the finds tables (out of 13, 12, 16 and 20), the specimen tray (out of 11) and the muddy tray (out of 40) | OSRS Wiki, Soil, Specimen tray, Panning tray and Panning, read 2026-11-11 |
| The site maps | OSRS Wiki, Soil's site maps; 451 of the 242 cache's 475 soil tiles fall inside them; the other 24 (3382-3385, 3416-3421) are on no site map and give no dig |
| The workmen's lines, the tray without a jar, the sacks, the skeleton and the full tray | OSRS Wiki transcripts (Digsite workman, Sacks (Digsite)) and Buried skeleton (Digsite), read 2026-11-11 |
| The tent's tray (3369,3378, 100 ticks) | OSRS Wiki, Panning tray, item spawns |
| Coins (3/20, 10 each) in place of level 3's ancient talisman; the special cup (3/40) as nothing | inferred: both are the quest's items |
| One find a dig after 8 ticks (slow on purpose until live is recorded: 3 gave about 13.7k Mining XP an hour from level 1), the animations, a workman within 10 tiles counting as near, a full backpack refusing first, and the dig, find, pan and search messages | guessed (the Wiki gives no time or messages) |
| Out: the winches and the Digsite Dungeon (step 10); the bushes, barrel and chest (the quest's); the museum's specimen cleaning; the gold panning tray; Fossil Island's soil | out of scope |

#### Exam Centre and Digsite Dungeon

Misthalin piece 7 step 10 (`game/content/ExamCentre.kt`, `npc-spawns/exam-centre.json`, `npc-spawns/digsite-dungeon.json`,
`obj-spawns/digsite-dungeon.tsv`, words under `exam_centre.*`, `terry.*` and `digsite_dungeon.*`): the Exam Centre south
of the Digsite and the dungeon under the digs, after The Dig Site.

The three examiners congratulate the player and give a lost trowel back to one carrying none (not into a full backpack). The Researcher talks about
the tools as the Panning guide does. Terry Balando, the archaeological expert, asks about Desert Treasure I or talks about
the Zaros altar, admires a wielded ancient staff, and has a line for every Digsite find used on him: three nuggets become a
gold ore, unidentified liquid becomes nitroglycerin, and a muddy panning tray comes back unsearched. The north cupboard
gives a rock pick and the south one a specimen jar, each to a player carrying none, and the bookcases' helpful books are
gone. The potter's wheel is Crafting's.

The west winch in the level 2 digs wants a rope used on it, which stays in the backpack; the private dig's winch needs
none. Both need 10 Agility and give 2 Agility XP. The map holds the dungeon twice: the copy without the quest's bricks is
the one after the quest. The private winch lands in Doug Deeping's chamber and the west winch by the way to the south
chamber, with its ten level 22 skeletons, five silver rocks and the bones, pickaxes, buckets and arcenia roots the Wiki
lists. Each rope climbs back up beside its winch. Doug congratulates the player and catches anyone stealing from him.

| Exam Centre and Digsite Dungeon | Source |
|---------------------------------|--------|
| The people and their tiles, the skeletons (ten, in the copy after the quest) and Doug | mejrs data_osrs NPCList_OSRS.json; the Wiki's maps |
| The talks, Terry's lines for each find, the nuggets exchange, the cupboards' and bookcases' boxes, the winches' messages and Doug's lines | OSRS Wiki transcripts (Examiner, Researcher, Terry Balando, Doug Deeping, The Dig Site), read 2026-11-11 |
| A rope for the west winch only, kept; 10 Agility and 2 XP for both; which winch lands where | OSRS Wiki, Winch and Digsite Dungeon; the landings checked on the real map by `exam-centre.json` |
| The cupboards' tools, one each for a player carrying none | OSRS Wiki, Cupboard (rock pick) and Cupboard (specimen jar) |
| The dungeon's and the digs' item spawns (100 ticks) | OSRS Wiki, Iron pickaxe, Bucket, Arcenia root and Bones |
| Terry's Desert Treasure I option always showing; the bookcases' quest-time box after the quest | inferred: every quest counts as done |
| The chathead expressions, the private winch's messages, the landing tiles, the XP on each way down, a rope every time, the cupboards' line with the tool carried | guessed |
| Out: the clue chest, the beaten book, Varmen's notes and the book on chemicals (the quests'), the barrel's liquid, Desert Treasure II's winch, the STASH units | out of scope |

#### The Misthalin sweep

Misthalin piece 7 step 12a (`game/content/MisthalinSweep.kt`, `npc-spawns/misthalin-sweep.json`, words under
`ham_talk.*`, `johanhus.*`, `jimmy_the_chisel.*` and `misthalin_sweep.*`): what a sweep of Misthalin's NPCs and loc
options found missing that belongs to this piece. The sweep listed every NPC on mejrs's Misthalin tiles with no spawn
within 2 tiles in any spawn file, and every loc and spawned NPC option that a player who has done every quest sees with
no handler behind it.

The new spawns are a camp of level 2 goblins north-west of Lumbridge Castle with two giant spiders and the woods'
butterflies, a goblin south-east of Draynor Village, one south of Draynor Manor and the level 5 goblin at the Digsite's
west edge, a giant spider between Falador and Draynor Manor, the highwayman south of Draynor Manor (aggressive, with the
Wiki's stats and drops), the mugger at the South-west Varrock mine, and three Black Knights. The H.A.M. Hideout's guards
and seated members answer their five-option menu, a guard greeting a player in the whole outfit; Johanhus Ulsbrecht and
Jimmy the Chisel talk. Nettles sting bare hands for 1 or 2 and are picked with gloves on; the Lumbridge farms' coops give
an egg each search, then nothing to that player for a minute; the Digsite barge's crates, the hideout's boxes and crates and the Digsite Dungeon's sacks are empty.

| The Misthalin sweep | Source |
|---------------------|--------|
| The new spawns' types and tiles | mejrs data_osrs NPCList_OSRS.json, each tile also on the NPC's Wiki location map (butterflies have none) |
| The highwayman's stats, aggression, respawn 100 and drops | OSRS Wiki, Highwayman (no hood), read 2026-11-11 |
| The H.A.M. talk, Johanhus's and Jimmy's lines | OSRS Wiki, Transcript:H.A.M. Guard, Transcript:H.A.M. Member (seated), Transcript:Johanhus Ulsbrecht, Transcript:Jimmy the Chisel, read 2026-11-11 |
| Nettles need gloves (not beekeeper's) and sting for up to 2; coops always give an egg | OSRS Wiki, Nettles (scenery), Nettles and Coop, read 2026-11-11 |
| The whole outfit taken as all seven pieces; nettles back after 13 ticks (the Nettles item's respawn) | inferred |
| Every wander range, the highwayman's punch, the nettles' and coops' messages, a sting never 0, the coops' one-minute wait after an egg, every chathead expression | guessed |
| Left out, with why: Nigel and Gelin by the church (Deadman forms only), Twiggy O'Korn (diaries aren't built), Spria (a Slayer master), the Draynor gulls (the 242 cache gave their old ids to Sailing NPCs), the General's Shadow scout; the highwayman's overhead shout (no NPC shouts yet); the swamp bubbles (Big Chompy Bird Hunting's ogre bellows), the swamp caves' symbols, the Digsite's bushes, barrel and chest (The Lost Tribe's and The Dig Site's) | out of scope |
| Found for other threads: the Varrock and Grand Exchange people and options, Draynor's and Edgeville's, the canoe stations, farming patches, STASH units, the Mage Training Arena and Al Kharid's arena | handed to those threads |

#### Walking Misthalin's roads

Misthalin piece 7 step 12b (`test-runner/examples/misthalin-walk.json`, checked by `WalkScriptsTest`): one runner
script walks the piece's roads in stages of at most 4 tiles on the server's own pathing, as `lumbridge-walk.json` does
for Lumbridge's. Leg 1 goes from Lumbridge Castle north past the farms into Varrock from the south; leg 2 from the castle
to the H.A.M. Hideout's trapdoor; leg 3 from outside Varrock's east gate north to the Lumber Yard, through the members'
gate and Silvarea to Paterdomus's front doors; leg 4 from the east gate again south through the members' gate in the
Digsite's west fence to the Exam Centre, then north through the digs to the canal barge's quay. The Lumbridge Swamp is
`lumbridge-walk.json`'s, and the River Lum's canoes wait for Gathering.

The script sets combat level 126 and `::god on` first, since a new player died by the dark wizards' circle south of
Varrock on the first headless run (the wizards taken to be the killers, inferred), and `::reset` puts the account
back at the end. It takes about four and a half minutes headless.

| Walking Misthalin's roads | Source |
|---------------------------|--------|
| The stages | the server's route finder on the real map between points picked on each road, the two gates opened on the way; every stage reached by `headlessScripts` |
| Where each road runs | the Wiki's maps; where the route finder cuts a corner off a road it is the shortest walkable way, not the road's own tiles (inferred) |

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
| Father Aereck (2812), as after The Restless Ghost and since 242.1, when he took over the removed Prayer tutor's tips: the prayer tips (with the altar icon and bones boxes), Saradomin (and claiming to be from another world), each back to his menu, whose goodbye is shorter once a topic was picked | the Wiki's Transcript:Father Aereck |
| Nigel (6093, a multi-NPC that shows as 8391 on a normal world), at the Grand Exchange since 242.1 (`varrock.json`): the two pages of Deadman Mode questions, and the rewards store, which is shut | the Wiki's Transcript:Nigel; his tile from the Wiki's map |
| Count Check (7414), as since 242.1: his check, which fails (the Wiki gives only a pass, so the old fail line is kept, then the menu a pass has), who he is and the Salve, and "No!" when asked what else he does, as on a main game world (so his once-only Barbarian Village teleport is never offered) | the Wiki's Transcript:Count Check |
| Nigel's normal world menu leaving out the Deadman worlds' options, his Trade saying the store is shut, and the check failing as no account is a Jagex Account and the bank has no PIN (so his lamp never comes) | readings |
| Every chathead expression; each answer going back to the menu it came from; the kneel (human_pray, 645) and the altar's messages; points above the level kept; the Stronghold tile (beside its entrance, 3081, 3421) and no teleport animation; the wander ranges (2, Father Aereck 5) | guessed |

Left out: the church organ; the treasure trail
and Wise Old Man parts of Father Aereck's talk. What they say, the altar's messages and each new NPC's and the altar's
examine are in the dialogue file (keys `father_aereck.*`, `nigel.*`, `count_check.*`, `altar.*`,
`examine.npc_<id>` and `examine.loc_409`).

Check: `test-runner/examples/lumbridge-church.json` examines Father Aereck and Count Check, prays at the altar with
drained points, and talks to both (`ge-nigel.json` does Nigel at the Grand Exchange); `HeadlessLumbridgeChurchTest` prays at an altar, talks to Father Aereck and Nigel (through his
multi-NPC) and fails Count Check's check through the headless client, with every branch in
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
| Refusals, in our own words: the Wilderness lever (26761), the fairy ring (29495, all four options), the Soul Wars portal (40474) and Death's Domain (39547) | ours: none of those places is built yet |

The words are in the dialogue file (keys `doris.*`, `evil_dave.*`, `hari.*`, `lucien.*`, `edgeville_closed.*`). The
canoe station is `Canoes.kt`'s (Gathering 2). Check:
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
| Kudos step 1: the museum's Kudos in the cache's own varbits (`vm_kudos` 3637, the count the client shows, worked out from what the player has done), the cleaning area's six archaeologists (1903 to 1908, at their tables) looking at each find by talk or by the item used on them (`museum_archaeologist.*`, `museum_case_find.*`), the Dig Site exhibit's five display cases (old coin 15484, ancient coin 12234, ancient symbol 12138, old symbol 12137, pottery 12139) taking their find once an archaeologist has sent the player there, for 10 Kudos each (`museum_case.*`), a symbol or coin taking every copy carried or banked, the clean necklace's secret (`vm_necklace`, every copy taken), jewellery for 5 coins, cleaning never turning up a find on display, carried, worn or banked (R29B-2), the clerk's Dig Site answer following the cases filled, and the information booth's Look at (the museum map) and Check Kudos (the Kudos List, `vm_kudos_info` 600); `::scenario museum-finds` clears the museum varbits and the archaeologists' memory, so a case run repeats on one account (`museum-kudos-cases.json`) | OSRS Wiki, Kudos, Uncleaned find, Clean necklace, Old symbol, Old coin, Pottery, the display case pages and the six archaeologists' transcripts (read 2026-11-14); the 242 cache (osrs-dumps 2026-11-11-rev242: the `vm_*` varbits, the cases' multilocs, the Kudos List's scripts and enums); tiles from mejrs's live NPC list; the archaeologist remembering where he sent the player (kept in saved charges) inferred from his "Back again?"; every expression, the cases' messages, the booth's Look at, the necklace's last two lines' place and a second necklace going the same way guessed |
| Kudos step 2: the natural history quiz on the Natural History floor: Orlando Smith takes on the player's help (`vm_nathis_agreed`), each of the fourteen plaques (`vm_plaque_*` 24605 to 24618, Study) opens `vm_natural_history` (533) with one of its display's six questions (`natural_history_quiz.*`), three right answers fill the display (`vm_nathis_*_display` 3672 to 3685, at 3) for 2 Kudos, a done plaque names its animal, Orlando's talk follows the displays and gives 1,000 Slayer and Hunter XP once all are done (`vm_nathis_reward_given`); the information clerk's three rewards at 51, 101 and 151 Kudos (`vm_reward_50`/`_100`/`_150`, XP given exactly) and her Natural History and "Would I qualify?" answers following the player; `::scenario museum-quiz` clears it all for a repeatable run (`museum-quiz.json`) | OSRS Wiki, Natural history quiz, Kudos, Transcript:Orlando Smith and Transcript:Information clerk (read 2026-11-14); the 242 cache (osrs-dumps 2026-11-11-rev242: the varbits, plaques, interface 533, enum 1741 and `vm_kudos_get_status`); the question asked (random, never the one just asked), the plaque closing on an answer with Orlando's remark as a chat box, the animals' names but the snail's, the qualifying 100 (Bone Voyage's) and exact XP guessed or inferred; the plaque's animal picture (server-set model ids unknown) and the historians' lectures left out |
| Kudos step 3: Historian Minas's Timeline exhibit: "I have some information" hears every untold quest story in the Wiki's order (`historian_minas_timeline.*`), each filling its timeline display (`vm_staff_of_armadyl`, `vm_salveknights`, `vm_observatory` to `vm_runes`, `vm_dov`, on varp 1011; the display cases follow) for 5 Kudos (Curse of the Empty Lord 10, 80 in all), with the cache's antique lamps (`vm_reward_*`) after Shield of Arrav, Merlin's Crystal, Making History, Curse of the Empty Lord and Defender of Varrock; What Lies Below only with the Dagon'hai history book, which he keeps; the clerk's Timeline answer following the player; the Digsite's workers' gate letting a player with 153 Kudos through (`digsite_barge.friend_of_the_museum`); `::scenario museum-timeline` clears the timeline for a repeatable run (`museum-timeline.json`) | OSRS Wiki, Transcript:Historian Minas, Historian Minas, Kudos, Transcript:Information clerk and Transcript:Museum guard (read 2026-11-14); the 242 cache (osrs-dumps 2026-11-11-rev242: the varbits, enums 1740 to 1742, `vm_kudos_get_status`, the lamps); every story told in one talk, Ikov on Lucien's side (the project's choice), Making History's and Curse of the Empty Lord's lamp lines, `vm_soa_vis` with Shield of Arrav, a lamp that doesn't fit dropped, and the gate's step-through guessed; rubbing the lamps and the displays' Study left out |
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
(`Perdu.kt`), Cadmus, the steward (the Crafting tutor until 242.1, `Cadmus.kt`), the bankers (`Bankers.kt`), and outside the gate the Doomsayer
(`Doomsayer.kt`), Mikasi, the diary taskmaster since 242.1 moved Hatius Cosaintus to Falador (`Mikasi.kt`), and the Lumbridge Guide (`LumbridgeGuide.kt`) talk as they do once
every quest is done, since every account here has done them all. The Duke gives an anti-dragon shield to a player
carrying or wearing none, and one talk with a man or woman in 128 hands over a flier. The bankers' collect (asked or op4 Collect) opens the Grand Exchange collection box (since
the Varrock piece's step 3). Not built yet, so their options end the talk or do nothing: bank space; Perdu's Lost Property Shop; the Doomsayer's warnings (Toggle warnings, and getting rid of signs, which ends after his answer); achievement diaries; Attack
and Pickpocket; the Guide's owed experience (242.1's quest XP), and his lost amulet, locket, clue and random
event talks.

| Castle NPC behaviour | Source |
|----------------------|--------|
| The Cook's and the Duke's conversations: lines, options, order, when the shield is offered; the Cook's cooking advice since 242.1 (the removed Cooking tutor's role), with its icon boxes, back to his menu | OSRS Wiki, Transcript:Cook (Lumbridge) and Transcript:Duke Horacio |
| The Cook's answer to "other quests" after Recipe for Disaster on a members' world (the free-to-play answer, with no line from the player) | guessed: the Wiki leaves it out |
| Men's and women's 23 conversations, the 1 in 128 flier | OSRS Wiki, Transcript:Man, Transcript:Woman, Flier |
| Perdu's, Cadmus's, the banker's (the Bank tutor is a normal banker since 242.1), the Doomsayer's and Mikasi's conversations | OSRS Wiki, their transcripts |
| Mikasi leaving out "another ring" for a player with no diary done | guessed: the Wiki lists it with no condition |
| The Lumbridge Guide's and the Doomsayer's conversations as since 242.1, which made them "more in-universe": their first-time talks (varbits `lumbridge_guide_met` and `doomsayer_intro`) and later ones, the Guide's menus, where each option leads, the icon boxes | OSRS Wiki, Transcript:Lumbridge Guide and Transcript:Doomsayer; icon items are the cache's `*_icon_dummy` objs |
| The Guide's quest answer (missing on the Wiki: the old line for a player with every quest done) and goals answer (the Wiki's, under an unknown condition, always); the Doomsayer's thanks branch (missing on the Wiki: the old "welcome") | readings |
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
its level 0 or 2, the level above that on the instance's level 1, and the lobby's square left out. The party gathers in the nexus's entrance. The Archive Guardian
is fought in the nexus's hall, where the cache lays its corpse on the nexus's later levels. Then come three rows of
doors, each with a room behind its west and east door: Ezekiel or Bork and the Smorks, then Moia or Dreadnoth the
Jellied, then the Dagon'hai Elder or Xarth. Last, the nexus's Archive Door north opens to Ocellus's arena. Each room's
Passageway goes back out to the nexus, and so does its Archive transporter once the room is done; from a boss's arena,
which is walled off from both, the way back is the Alliance Navigator, who comes to it three ticks after the room is
won. A room whose boss
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
| The nexus's doors (63088 to 63105, Enter and Quick-enter), the rooms' Passageways (62765, Exit) and Archive transporters (63087, 62610, 63134, Use and Quick-use) | 241.4 cache |
| The nexus's Archive Door north, 7 by 2 at 4124,5621, put there by the server: shut (63110, no options) from the start, open (63111, Enter and Quick-enter) once the fourth room is done | tsps capture notes; 241.4 cache for the two types and the gap between the pillars at 4123 and 4131; its angle 0 inferred from the cache's Passageway of the same size four tiles north; opening at once, with no 63109 animation between, guessed |
| A member down in Ocellus's room, or sent back out of it, at 4127,5620 in the nexus, the tile south of the door's middle | inferred: Sam's recording of 2026-11-09 reads "about 4127, 5622" before the fade, within a tile or two, and the door covers z 5621 and 5622 |
| The Guardian fought in the nexus's hall; Ocellus in the arena north of it (64,88), past the nexus's Archive Door | inferred: the Guardian's corpse lies in the hall on the nexus's later levels; Sam's recording of 2026-11-09 for the arena (4127,5637 on plane 1) |
| The nexus on level 0 of the cache at the start and 2 (the Guardian's corpse, a camp of crates and barricades) once the Guardian is done, for the rest of the run | level 2 on all three of a run's visits: Sam's recording of 2026-11-09; level 0 before the Guardian is done inferred from what it holds |
| The level above the nexus's on the instance's level 1 (the cache's 3 over its 2): the hall again 392 height units up, and at its north end, level with the floor, the south end of the vault's corridor, shut by the vault's Passageway (62766, 7 by 2 at 4124,5625) | tsps capture notes (a captured build area with levels 2 and 3 on the instance's 0 and 1); 241.4 cache for what the levels hold and their heights; the cache's 1 over its 0 before the Guardian is done inferred, the two pairs holding the same but for the corpse and camp |
| The nexus's square north of its Archive Door (z 5623 on) counted as Ocellus's room: nobody gets there on the nexus's level, and on the level above it is the vault's corridor | inferred from the 241.4 cache's collision: from the vault the corridor is walkable south to z 5627 and no further |
| Contribution: each room a fixed amount, shared by damage, minions included; the two rooms of a pair the same | Wiki, "Everything You Need To Know" |
| A room's points 100 and Ocellus's 300, 700 a run, shared among the party by damage dealt | OSRS Wiki, Chest (The Fractured Archive), read 10 November 2026 |
| A room's points shared evenly among who was there at its start when nobody did damage; for the Wiki's soft cap on damage to a boss's minions, a hard cap: damage to every NPC of a room's fight but its bosses (Bork and the Smorks, the Dagon'hai's three, Ocellus's Blade) counting at most 25% of everyone's damage to the bosses (exploit review round 26, R26A-3; round 27, R27A-1), a cap shared by the room and scaled to each by what they did once the party's damage to adds is over it (R27A-2); a hit on Ocellus's Blade counting for the 43% of it that reaches his bar, none past his floor, and hits on the Dagon'hai's pool for what the pool lost, shared by what each dealt (round 28, R28A-1 and R28A-2) | guessed |
| A wipe sending the party to the lobby with an ordinary gravestone; the party not breaking up | Wiki, The Fractured Archive (Theatre of Blood's system; 28 October 2026 update) |
| The wipe's tile and graves in front of the Archive Door (4127,5560); each member's grave on it | guessed |
| A member dying with the party standing keeping everything, down in front of the room's door until it is done, and sent back out if they walk in | guessed (Theatre of Blood's system); spectating isn't built |
| A won room bringing those down back standing in it, and the party's hitpoints and prayer back to full | Sam's recording of 2026-11-10 (clip 16-09-39, after Bork): the two who died in the fight stood in the room north-east of the arena 6 seconds after, and Sam read 99/99 hitpoints and prayer |
| That every room's end does it, for everyone in the run, boosts kept; those down put on the tile the Navigator comes to (the arena's way in), and one lying dying as the room is won getting up there too; for the Guardian's hall and Ocellus's arena, which have no such tile, those down stay where they are | inferred from that one kill; the tile guessed |
| The Navigator's line, then a menu asking whether the player is ready to proceed, its first option back to the nexus and the second staying; the chat line to the rest of the party in the run that the player has returned to the Nexus | Sam's recording of 2026-11-10 (clip 16-09-39 at 6:46) and of 2026-11-09 for the chat line (keys `tfa_navigator.ready`, `.proceed`, `.no` and `.returned`; the first option's words weren't read) |
| The menu after the fourth room's line too; Quick-follow skipping it; the chat line sent as the option is taken, for Follow and Quick-follow, and only for the Navigator | guessed |
| Any member opening the run, a player in no party as a party of one; others following until the Guardian's fight begins, nobody after | guessed; the Wiki says supplies can't be banked mid-raid |
| A row's other door shut once one is gone through; a row opening once the room before is done; Enter and Quick-enter, Use and Quick-use, the same | guessed, until a recording shows a run |
| Passageway out at any time; the transporter once the room is done; both to the nexus in front of the room's door | guessed |
| Coming back out of a room onto the nexus tile the player went through its door from; otherwise beside the door, on x 4140 east and x 4114 west, a row north of the door's south end | Sam's recording of 2026-11-09 for the east column (4140,5585 back from Bork, 4140,5598 and 4140,5612 left from and come back to for Dreadnoth and Xarth); inferred: the same tile back, and the west column mirrored; guessed: the row where the tile isn't known |
| The Alliance Navigator (16700, Follow and Quick-follow) coming to a won room's arena three ticks after, and following him leading back to the nexus beside the door taken, the move four ticks after the option or his line ("a fade, then the move four ticks later") | tsps capture notes (RSPSApp/tsps docs/fractured-archive.md); his two lines, one after the second room and one after the fourth, seen in Sam's recordings of 2026-11-09 (keys `tfa_navigator.onward` and `tfa_navigator.vault`, their words on the server, not in git) |
| The Navigator standing where the room's transporter puts a player (Sam's recording of 2026-11-10 has him at 4185,5599 by Bork's, a tile from it); his line coming with Follow and Quick-follow skipping it; only the one who follows going; the player not held during the four ticks before the move, and one who has left the room by then not moved; his fade-in and the move's fade not sent; the first line after the third room too; with no Navigator in the cache or no NPC slot for him, whoever is on the arena sent out instead (exploit review round 21, R21A-1: a won arena had no way out) | guessed |
| A move between the nexus and a room coming four ticks after its door (Enter, Quick-enter), the nexus's Archive Door north, a room's Passageway, its transporter once the room is done, or the Navigator: "a fade, then the move four ticks later" | tsps capture notes (moving between rooms); Sam's recording of 2026-11-09 (clip 16-55-42: the screen black for 1.5 to 2 seconds at each of three doors and three Navigators) |
| The same four ticks for a Passageway and for the transporter back out, which neither source shows; a second click on the way not starting the count again; the player not held meanwhile, and one who isn't where they set off from by then (the nexus for a door, the room for the way out), or has fallen, not moved; a door chosen for the party as it is clicked, not as the move lands; the fade itself not sent (what live sends for it isn't in the notes); the transporter's move onto the arena at once (the notes' estimate); the vault's Passageway out to the lobby at once | guessed / not sent yet |
| Nobody attacking an NPC in a room with an arena (Ezekiel's, Bork and the Smorks', Moia's, Dreadnoth's, Xarth's) from off that arena: a click, a spell, a shot, a hit spreading from another NPC and auto retaliate are all refused, a click with the line for a target that can't be reached (exploit review round 21, R21A-2: from the way in, out of the fight or out of its reach, a boss could be shot) | guessed: live's walls presumably stop it; whether the cache's stop a bow or a spell isn't known |
| The same for the Guardian's hall, all of which is its arena: nobody attacks the Guardian or an inert guardian from the nexus's entrance south of the hall, which is out of the fight (exploit review round 22, R22A-1: an inert guardian in the hall's south was within a bow's reach of the entrance, where no shot, mushroom, beam or vine comes). A player who walks in to punch is in the fight by then | guessed: where live's fight ends to the south isn't known, nor what it does with an attack from there |
| The same for Ocellus's arena, the floor within 12 tiles of his cross: nobody attacks him or anything of his from the corridor his room is come into by, south of the doorway at 4126-4128,5660. The corridor is in his room, so his fight goes on with someone there, but none of his attacks that take the whole arena reach it (exploit review round 23, R23B-1: he could be shot from it at no risk) | guessed: on live the entrance closes behind the party as his fight starts (tsps capture notes), which isn't built yet; the refusal stands in for it |
| A room's fight beginning as the first party member stands in it, and the Guardian's in the hall north of the entrance | guessed |
| The Guardian's fight beginning on Touch on the waiting "Inert Guardian" (`tfa_guardian_start`, 16657) in the middle of the hall, where its corpse lies later, and Yes to "Begin the challenge?"; scaled to everyone standing in the run then | 241.4 cache (the NPC and its Touch); Sam's recordings (the menu); where it waits inferred from the corpse; the party size guessed |
| The whole of a run's instance multi-way | inferred: a raid of up to eight fights one boss |
| A member who leaves the party sent to the lobby; a logout saving them there, as any instance's exit | guessed |
| A member down who gets out (a teleport such as Home Teleport or a lodestone, leaving the party, or a logout once out of combat) dying of it, with a grave in the lobby, as if they had been the last standing; a reconnect within the linger no escape | Sam's decision, 8 November 2026 (exploit review RAID-2); the death's place guessed |
| A member down can't trade or be traded with, and what they drop stays theirs alone (never turning public), so a standing friend can't be handed their items before they get out (exploit review round 18, R18A-1) | guessed: live's raid rules aren't known; nothing is said on a refused request |
| With no NPC slot free as the first of the party comes into Ezekiel's room, his room isn't started: each tick with one of the party in it tries again, rather than the run being stuck for good (exploit review round 18, R18A-3) | guessed |
| Every room done as it begins, until its boss's step | stand-in (none left since step 23; a world without a fight's definition, such as a headless test's, still does it) |
| Ezekiel (step 17a): his fight beginning with the first of the party into his arena, that player his tank; his stats, 3,000 to 10,650 hitpoints by party size, the 25% earth weakness, poison and venom immunity and the 60% Defence drain floor | OSRS Wiki, Ezekiel (2026-11-09) |
| Where he waits (4062,5537) and his arena (4056-4068 x 5528-5540); his room's transporter, between the way in and the arena, putting the player in it (4067,5535); where a player lands coming into his, Bork's, Dreadnoth's and Xarth's rooms (4079,5536, 4173,5599, 4174,5664, 4175,5535) | tsps capture notes (RSPSApp/tsps docs/fractured-archive.md; 2026-11-09) |
| Ezekiel walking after his tank, a melee swing every 5 ticks cleaving everyone round him but those on his side opposite the tank; lightning on everyone when his tank is out of reach; a cross of lightning centred on him after every second swing, bursting twice, and 3% special attack energy for a perfect dodge of a burst | guides (osrstoolkit, osrsbestinslot, runescapeguides, bald.gg; 2026-11-09) |
| Ezekiel's flying swords (step 17b, `tfa_ezekiel_sword`): three or four with the cross, flying straight across once their timer is up; one lined up with him hitting and stunning him, none doing so healing him; damage to a sword charging it, a charged one hitting him (up to 80) and the player in its path harder; weak to air spells, resisting ranged; 3% special attack energy for a perfect dodge of one | guides (runescapeguides, bald.gg, osrsbestinslot, osrstoolkit; 2026-11-09), the Wiki's Strategies inventory table (wind spells empower them), the cache's NPC |
| The safe spot being his whole far side (its corner for a tank at a corner), the lightning's damage (up to 30) and landing, the cross's width (his) and reach (6 tiles out), its 3-tick warning, the 2 ticks between its bursts and its damage (20 to 40), his melee's max hit being the Wiki's 77, slash; his lightning counting toward the cross and swords as a swing does, so kiting him still brings them (no source says; Sam's call, 2026-11-09) | guessed |
| The swords flying across his arena edge to edge, each standing wholly on it at an end of its lane; where he stands for the enrage (4059,5534), the three by three in front of him, east (4062-4064 x 5534-5536), three swords on his own tiles, and the spared tile being in the three by three's east column | tsps capture notes; before exploit review round 21 (R21A-3) the swords used a guessed 10 tiles round his spawn, which put most of them out of reach and one end at the room's way in, and he enraged two columns off his arena |
| The swords' lanes his width wide at random from a random end, the count at random, one volley out at a time, a volley flying together all along its lanes at once (live's fly across), their 15-tick timer, 100 points of charge, 20 to 80 on him, 10 to 25 on a player (twice that charged), the 8-tick stun, the 10% heal, the sword's type (rather than `tfa_ezekiel_sword_noop`), its stats, 50% air weakness and 300 ranged defence | guessed |
| The swords' red launch timer and yellow charge bars over them (their cache head-bar ids aren't known: a sword shows only the ordinary health bar, falling as it charges) | not in yet: until the ids are found or a recording shows them |
| Ezekiel's enrage at 35% (step 17c): every player in the fight shut into a three by three beside him, and volleys of three lightning strikes, each shown by which of three swords are struck and dodged on the tile of the one not struck; no sword of the fight before it hurting anyone through it, and no stun animation over its own | OSRS Wiki, Ezekiel and its changes of 28 October and 3 November 2026 (2026-11-09) |
| The enrage's arena: the three by three fenced on its north, east and south sides by 11 `tfa_ezekiel_sword_noop` (the capture notes count seven, not where they stand), the three marks on his column beside it, the safe tile across from the unstruck one, where in it each player is put, the fence holding them by putting back whoever steps off; the 6-tick transition; a volley every 11 ticks (bald.gg) of three strikes 3 ticks apart, landing 2 ticks after the marks are struck, for 30 to 60, halved by Protect from Magic; the marks' and impacts' spotanims picked by name; him neither moving nor swinging in it | guessed |
| Bork and the Smorks (steps 18a to 18c): both to die, either first, for the room to be done; their stats, Bork 1,500 to 5,330 and the Smorks 600 to 2,130 hitpoints by party size, Bork 4 by 4 and crush, the Smorks 3 by 3, ranged and magic, both every 7 ticks; Bork's flat armour of 8 to 14 and magic resistance of 0 to 6, 2 of each for every 25% the Smorks have lost, none while he is dazed, when melee always hits him; the oil's 6 to 7 a tick and its burn; his 40% and their 85% earth weakness, both immune to freeze, the 60% Defence drain floor on Bork | OSRS Wiki, Bork (The Fractured Archive) and Smorks (2026-11-09) |
| Where Bork (4194,5603) and the Smorks' tower (4190,5598) stand; the room's transporter putting the player on the arena's floor (4184,5599), Bork going for the first in; his gong 8 ticks in and every 7 after, two strikes 2 ticks apart, the first out to his tank 2 or 3 tiles off, the second round him; the Smorks' cycles (a volley 13 ticks in, napalm, one or two volleys, the heavy cannonball; later two or three volleys, napalm, volleys to four, the cannonball), a spacing between shots and two spacings less a tick from napalm's cast to the next volley; napalm's cast a tick after its call and its splash 4 after that, the 3 wide row two deep beyond its tile towards the tower (2 by 2 on a diagonal), oil a tick on for 199 ticks; the heavy cannonball shot 2 ticks after its call at a player Bork isn't after, landing 5 later; the 14-tick daze; the stomp's 4 shockwaves 3, 6, 9 and 12 ticks in over the arena (4184-4198 x 5591-5607 bar its corners) but for the tower's shadow (every line from his middle 2 by 2 to points 0.3 in from a tile's corners crossing the tower), his tank call at 15 and gong at 19, the Smorks' next cycle 39 ticks after the daze; the tempo, a tick off both their spacings at each of the Smorks' 75, 50 and 25% (7 down to 4), with their lines; Bork gone 7 ticks after he falls and the Smorks' frenzy 4 after that; the Smorks' tower going up 2 ticks after they fall and gone at 9, when the room is done (Sam's recording of 2026-11-09 shows both, and the stomp's four waves, its call and the frenzy's volleys and napalm as here), a volley at everyone every 2 ticks, napalm after 2 or 3 of them and then after every 5, the next volley 3 ticks after its cast, napalm by the tower splashing away from it; the Smorks dead first, Bork stomping over the whole room about every 45 ticks (their estimate), with Bork at the fastest tempo; every overhead line (keys in code, words in the dialogue file); the animations and spotanims by name | tsps capture notes (RSPSApp/tsps docs/fractured-archive.md: five spectator captures of live fights, rev 241.1; 2026-11-09) |
| Protect from Melee cutting his gong; the volleys and napalm at players Bork isn't after; luring him onto the heavy cannonball | guides (bald.gg, tonsofxp, osrsbestinslot, runescapeguides; 2026-11-09) |
| The Smorks' max hit of 7, bones and potions alike; a shockwave hitting for up to 34; through Protect from Melee a strike of Bork's gong never missing, the first of a gong's two doing 4 to 7 and the second 5 to 8; a strike with no prayer doing as little as 6 and as much as 32 | Sam's recordings of live parties of four and five (2026-11-09): the most of some 45 of the Smorks' hits seen, most of them in the frenzy; the one shockwave hit seen; 35 strikes on a tank with the prayer's icon overhead, both of a gong's his own (two swings, the same plain hitsplat), and four on one whose prayer had run out (6 and 32, then 16 and a killing blow) |
| His max hit 34 (the formula's from the Wiki's levels and bonuses); a strike with no prayer never missing either, and anything from the prayer's least up to his 34 (up to 50 when it reaches out, which through the prayer is as a first strike), and the tower's shadow keeping a strike off too; a volley's style at random, all of it the same; a spacing from a volley to napalm or the cannonball's call; napalm and the cannonball at a random player Bork isn't after, his tank when there is none; the splash's drip on its own tile, and more than twice as far one way as the other counting as straight; napalm on Bork hurting him once by 6 to 7; the burn's 2 every 4 ticks, 5 times, not again while it runs; the cannonball hurting no player; a missed cannonball's stomp 2 ticks after it lands, with the miss line rather than the daze's; a shockwave's least of 15, no prayer helping, all at once rather than rolling out from his feet; his next tank a random other player; the frenzy's first volley on the tick it begins; the tempo staying where it is if the Smorks heal; Bork's first lone stomp 45 ticks after the Smorks fall; the fight beginning as the first steps onto the floor (live: 6 ticks after); flat armour coming off as a hit is rolled (before what he has left caps it, and the XP) and not taking it below 0; the fight's anchor being `tfa_bork_smork_hp_ui_handler`, and the room done once both bodies have gone | guessed |
| In the frenzy, the Smorks calling out a player by name (key `tfa_bork.mine`, its words on the server) | Sam's recording of 2026-11-10 (clip 16-09-39 at 6:26): two players named one after the other as Bork died, both dead within seconds |
| That it is the frenzy's napalm calling, in place of its usual three calls, and going at the one named while they are still in the fight | guessed |
| The shared health bar and the Smorks' second bar; the gong's and shockwaves' dust on each tile | not in yet |
| Dreadnoth the Jellied (step 20a): his stats, 5 by 5, 2,100 to 7,460 hitpoints by party size, a swing every 4 ticks up to 45, the 85% fire weakness, poison immunity, his offensive stats drained by 50% at most; his crawl of about a tile every two ticks; his melee hitting everyone round him; the waves at 85%, 65% and 45% (a slam over the whole arena, then three ranged shots, one more slam each wave up to three), Protect from Melee for the slam and Protect from Missiles for the shots; the jellies they bring, more for a bigger party; jelly on the floor where jellies die, 6 to 8 a tick; a jelly's stats, 5-tick crush up to 17, 60% fire weakness, its hits draining prayer | OSRS Wiki, Dreadnoth the Jellied, Jelly (Dreadnoth) and The Fractured Archive/Strategies (2026-11-10) |
| Where he waits (4189,5661) and his arena (4181-4198 x 5656-5670); his room's transporter putting the player on its west edge (4184,5663), his fight beginning with the first there; the crawl's tile every two ticks, facing his target; the waves' volleys 6 ticks apart, each a jump, then a tick later three tiles of jelly in or by his footprint and three shots at each player landing 3, 4 and 5 ticks on; jelly gathering where they land, shown every tick, and a jelly rising there 8 ticks after the last volley, at most 12; the jellies going for the nearest player and walking through NPCs; a jelly's 75 hitpoints (the cache's too; the Wiki's infobox says 61); the animations, spotanims and the jelly tile by name | tsps capture notes (RSPSApp/tsps docs/fractured-archive.md: five spectator captures of live fights, rev 241.1; 2026-11-10) and the cache |
| His 3,930 hitpoints for a party of four and 4,810 for five | Sam's recordings of 2026-11-09 |
| Every damage figure: the slam up to 40 (Sam took a 38 as the first wave landed), a shot up to 15, his melee crush; both protection prayers blocking all of theirs; his target the first player in, then whoever he fights; his first swing 4 ticks in, waiting for someone to be round him; his standing still through a wave, and a wave waiting for the last one's jellies to rise; the shots following their player and gathering jelly on the tile they were thrown at; one jelly for each tile shots landed on; the three tiles' places; the jellies' walk at a tile a tick and first hit 5 ticks after they rise; a jelly's drain being its roll, at least 1, through Protect from Melee too (which blocks the hit); jelly on the floor lasting the fight, and none from his crawl | guessed |
| Dreadnoth's enrage at 25% (step 20b): his line (key `tfa_dreadnoth.glub`, its words on the server, not in git), no more melee or crawl; his jumps at a random player, slamming them for a lot if they're under him, hurting everyone else without Protect from Melee wherever they stand; a ranged shot and a jelly splatter at each player after; jellies he lands on absorbed, healing him | OSRS Wiki, The Fractured Archive/Strategies (2026-11-10) |
| The enrage's first jump 5 ticks after his line, then one every 8; landing centred on the player if within 5 tiles, else 5 tiles the nearest of eight ways towards them, never west of x 4184; the shadow where he lands, 3 ticks on (a 90-cycle move); one to three tiles of jelly in his footprint as he lands; the two projectiles at each player's tile, which becomes jelly 6 ticks later | tsps capture notes (2026-11-10) |
| The enrage's damage off him, up to 20 (a 0 with Protect from Melee); the shot's as a wave's; his landings kept on his arena; the splatter hurting nobody as it lands; the jump shown as a teleport as he lands (NPC exact moves aren't built) | guessed |
| The enrage's slam on a player under him, 36 to 40 with no prayer helping, and no heal off it; an absorbed jelly's heal, 85 to 145, not its hitpoints left; a blue 0 on a player off him with Protect from Melee | Sam's recording of 2026-11-10 (36, 40, 36, 37; heals 145, 85, 92 matching his bar; blue 0s); the ranges inferred |
| His death (step 20c): splitting into seven jellies of 37 hitpoints, the room won when the last one dies, the jelly on the floor cleared then | tsps capture notes (2026-11-10); the Wiki's Strategies say about ten at half their hitpoints, the captures taken over it |
| The split jellies rising on his footprint at once, past the twelve; nothing of his still in the air landing once he splits, and no more jellies from gathered jelly; every jelly, the waves' too, having to go before the room is won, and not before 6 ticks (his death animation); his going from sight as that animation ends | guessed |
| Dreadnoth's Wiki freeze and demonbane resistances | not in yet |
| Moia (step 19): her stats, 3 by 3, 1,900 to 6,750 hitpoints by party size, the 5-tick attack speed, ranged max hit 32 and magic max hit 44, the 80% air weakness, her Defence drained to 117 at most; an Undead binder's stats, 1 by 1, 95 hitpoints, its 5-tick attack, moving about her arena and attacking nearby players; her cough's rapidly ramping poison damage and prayer drain, which a sanfew serum helps with | OSRS Wiki, Moia, Undead binder and The Fractured Archive/Strategies' contents (2026-11-11; the Strategies' text on her wasn't reachable) |
| She starts out of reach of melee; her binders evenly spaced with one opening, shuffling, hitting anyone within two tiles; mostly fast ranged attacks; after a teleport a slow magic projectile, then a faster ranged one, to switch prayers between; the cough infecting one player and spreading to those and binders next to them; below a third, binders in a diamond and a charge over the arena that hurts the whole party a lot unless every binder dies first, repeating with fresh ones | guides (osrstoolkit, ironman.guide; 2026-11-11) |
| Her room: the round floor of square 63,88 (about 4046-4081 x 5646-5680) her arena, the walled floor in its middle (4059-4067 x 5659-5667, open west, north and south), the way in east of it with no wall between, so walking onto the floor begins her fight; the ids of her attacks, teleport, cough, binders and their power by name | 241.4 cache |
| Every timing, place and damage figure past the Wiki's max hits: her standing in the middle of the walled floor and the four other places she teleports to; an action every 5 ticks in the cycle dagger, dagger, cough, dagger, teleport, at a player picked at random; 2 ticks out of sight in a teleport, the magic dagger the tick after and a ranged one 2 later; the binders' ring of eight slots round the walled floor's middle, 4 tiles out, walking a slot on every 16 ticks, their max hit of 16 crush, rising again 30 ticks after they die; the cough landing 3 ticks on, the infection's six hits 3 ticks apart of 2, 4, 6 and on, draining as much prayer, spreading as it hurts, halved for a player immune to poison; the enrage at a third with her going back to the middle, the diamond 4 tiles out, a 30-tick charge, the burst's 50 to 70 with no prayer helping, a fresh diamond 20 ticks after; her daggers and cough going on through the enrage; the throw's animation for every attack | guessed |
| Moia's binders pushing out a crowd under one (28 October 2026), her shadow shield (varbit 16418), the Serpentine helm against her cough | not in yet |
| The Quartermaster (step 21): there after the third boss, Moia or Dreadnoth the Jellied, has been beaten; Resupply and Talk-to; his shop Alliance Supplies and its stock per player: 6 Saradomin brew(4), 4 Super restore(4), 4 Prayer potion(4), 10 Manta rays, 5 Halibut, 10 Giant krill, one each of Prayer potion(2), Ranging potion(2) and Super combat potion(2); Leftovers, free, offered only after a poor showing such as several deaths, healing 5; his Talk-to's menus | OSRS Wiki, Quartermaster (The Fractured Archive), Alliance Supplies, Leftovers and the Quartermaster's transcript (2026-11-11; the transcript is marked incomplete) |
| Him (`tfa_midraid_supplies_npc`, 16761) at 4129,5609 and his Supplies Wheelbarrow (63302) at 4130,5608, only on the nexus visit after that room | tsps capture notes (seen as a spectator) |
| The shop is the cache's omnishop row `tfa_midraid_supplies_shop` (17150), or `_failure` (17151) with Leftovers: its prices (brews and restores 3 Supply Credits, Prayer potion(4), Manta ray, Halibut and the two-dose Ranging and Super combat potions 2, krill and Prayer potion(2) 1), the Alliance log shown only, its buttons Buy 1, 2, 3 and 5, no selling, the credits in `tfa_supply_points` (16381, 0 to 127), stock counted from inv 1030; the omnishop window's protocol (varps 3874 and 3869, scripts 917, 7140, 7246 and 501, the list's twelve components an entry) | 241 cache (db tables 39 to 41, read by `OmnishopLoader`) and osrs-dumps' scripts; the open from the tsps notes' rsprox captures of other omnishops |
| Each member having 50 credits as he comes (guides speak of 50; the tsps notes guess 10 a room), and none left from an earlier run; each member's stock their own; him going as the fourth row's fight begins; the failure shop once the party has died twice in the run; 5 Leftovers; every chathead expression, and his talk ending at "I'll keep that in mind" from his second menu (his answer isn't written down); passing the shop's inv to `omnishop_main_init` (the captures pass -1) so the list redraws as stock runs down | guessed |
| His overhead lines, the inactive Quartermaster (16762, Talk-to only), the info panel's text sent from the server (the client draws an item's examine itself), and the words of his talk (with the Words thread) | not in yet |
| The Dagon'hai Elder and his two Monks (step 23): their stats, bonuses and 2 by 2 size, 640 hitpoints, magic max hit 30 and ranged 27, speed 6; the imp's 44 hitpoints; a demonic rift letting out a set number of imps, a player standing next to it stopping them and draining it | OSRS Wiki, Dagon'hai Elder, Dagon'hai Monk, Imp (Dagon'hai Trio) and Demonic rift (2026-11-11) |
| One pool the three share, each with their own Defence drains, a stunned one taking 15% more; the Elder's ranged shot and magic surge on one player, splashing onto those next to them; channels into the cauldron of 25% each, the matching standard spell landing stopping one and a splash not; at 100% the trio empowered, surges two at a time through prayer; the Monks' tomes of Bandos, Zamorak and Saradomin with their warning and gust tile, what each does blocked and not; each break taking 20% off the stability and opening two rifts of three imps, standing beside one closing it and draining prayer; after the fifth the cauldron bursting, a Monk dying and the other two left on 7 hitpoints | guides (osrsbestinslot, bald.gg, osrstoolkit, ironman.guide; 2026-11-11) |
| Their room: the floor of square 63,87 (4054-4075 x 5589-5609), walled off from the way in east of it, so the room's transporter puts the player on the floor (4074,5599), which begins their fight; the cauldron (`tfa_dagonhai_focus`, 1,000 hitpoints), the three's weakened forms on 7, the rift's 20; the ids of their attacks, channels, searches, throws, gusts, marks, deaths and the imps' by name | 241 cache |
| Where the cauldron (4063,5598) and the three stand; the Elder's attack every 6 ticks in a random style, at a player kept for four attacks, the splash a full shot of its own; a channel every 20 ticks from 12 in, 8 ticks long, by any of the three, the stun 5 ticks; a Monk's search every 30 ticks from 22 in, 3 ticks long, the tome landing 3 ticks after the throw, its gust tile two steps out from him towards the cauldron; every tome figure (Bandos 55 on the blocker and 10 to 20 on the rest, unblocked 30 to 40 on everyone; Zamorak 25 and a burn of 4 every 3 ticks three times, its fire 7 by 7 doing 8 to 15; Saradomin's lightning 20, its heal 10, the pool's 50); the charge and empowerment gone at each break; where the rifts open, an imp every 5 ticks, a rift closing after its third, its drain of 2 a tick and 1 prayer; the stability shown as the cauldron's hitpoints; the weakened two still attacking | guessed |
| The cauldron moving and its knock-back, player attacks on imps always hitting their max, the ritual's progress bar (the charge is in `::raid`), the shared health bar sent to the two not hit, the Book of Life and Death and the Guthix spellbook in the room, and the words of their shouts (with the Words thread) | not in yet |
| Xarth (step 19a): 3 by 3, never moving; his stats, 2,600 to 9,230 hitpoints by party size, magic only in phase one at the player closest to him (max hit 36 with no prayer), the 85% water weakness, poison and venom immunity and the 60% Defence drain floor; a fireball that hits setting the player alight, the burn starting again a stack hotter with each that hits while it runs; the bind on everyone before the blast; the wave's ~45 on whoever has no cover, and a player in front taking that for one behind, who takes ~7 | OSRS Wiki, Xarth and The Fractured Archive/Strategies (2026-11-10) |
| Where he (4190,5534) and his three 2 by 2 rubble heaps (4193,5530, 4196,5536, 4193,5540) stand, his footprint's nine blocking tiles; the room's transporter putting the player on his arena (4184,5535), which begins his fight; phase one's three 31-tick cycles of five fireballs 4 ticks apart, the AoE with a projectile at each player's tile, the flame blast 3 ticks after it and the wave 3 after that, rolling out a client cycle per tile; the wave's 90 degree wedge towards the heap that falls (a row out his width and a tile each side, a tile wider each row), all round the third time; the heaps falling south or north first, then east, then the other; the falling heap sheltering the tiles it hides from his middle tile (no wave) or only from his nearest footprint tile (the smaller explosion); its collapse for 3 ticks; his body gone 6 ticks after he falls; the animations and spotanims by name | tsps capture notes (RSPSApp/tsps docs/fractured-archive.md: five spectator captures of live fights, rev 241.1; 2026-11-10) |
| The set-alight chat message (`tfa_xarth.set_alight`, a key in code, the words in the dialogue file); his bar's 4,860 for four and 5,950 for five; the burn after it, 7 or 8 every 4 ticks, at least 6 of them; a wave's 100 on a player in the open (twice) and a 60 on another, and a player just south of a heap taking none; a fireball never missing through Protect from Missiles and doing 1 to 6, and landing 3 ticks after the wind-up from 6 or 7 tiles; the AoE's flames reaching the party 2 ticks on, 23 on Sam | Sam's recordings of live parties of four and five (2026-11-09), read by the Review thread's dense pass (2026-11-10) |
| The first cycle 2 ticks in; the AoE 4 ticks after the last fireball; the bind lasting till the flame blast (3 ticks), as a stun; the AoE's projectile hurting whoever is on its tile for 15 to 30, no prayer helping; the nearest player's tie broken by the lowest index; a fireball needing no line of sight; Protect from Magic cutting a fireball as Protect from Missiles does, and a prayed one setting no burn; the burn stopping after 6 and its stacks (3 more a stack, up to 3); the wave's 40 to 100 with no prayer helping, on the wave's own tick, 5 to 9 when screened, and nothing from the smaller explosion; his arena (4184-4198 x 5527-5543 bar its corners, as Bork's round its room's middle); the heaps blocking the way until they fall | guessed |
| Xarth's phase two (step 19b): fires on the floor that hurt and set alight whoever stands on one, spreading to the tiles next to them; no more magic, but ranged (+250, up to 34) that Protect from Missiles blocks, landing as it hits, and melee (+150 slash, up to 34); Corpses raised with his magic attack animation at the arena's edge, walking to him and healing him twice what they have left if they reach him, a second absorbed setting off the blast all round that sets those it hits alight, a player in front shielding one behind; the Corpse's 62 hitpoints, defence bonuses and 40% water weakness, and its freeze | OSRS Wiki, Xarth, Corpse (Xarth) and The Fractured Archive/Strategies (2026-11-10) |
| Phase two from the third wave: seven fires 1 to 7 tiles from his footprint, mostly 2 to 5; his shots 7 and 11 ticks in, then a 21-tick cycle from 15 of two Corpses raised 2 ticks apart (three for five players), four shots 4 ticks apart from 5, and three more fires at 12; a Corpse rising 3 ticks after it is raised and walking a tile a tick; one absorbed going 2 ticks on with its heal's hitsplat (124 for an unhurt one), one killed going 4 ticks on; the animations and spotanims by name | tsps capture notes (2026-11-10) |
| A fire setting a burn at all, and only on a player not burning already, the burn as a fireball's; where a new fire goes (next to a burning one); where on the edge a Corpse rises; his shot at a random player with a bow's flight, and a swing in place of a shot at a player beside him, Protect from Melee blocking it; a Corpse heading for the tile beside his nearest side; the blast after every second absorbed, the flame blast as its tell, with the wave's damage; three Corpses for a party of five or more in the fight; a fire not blocking the way | guessed |
| Phase two (step 19c): a fire doing no hit of its own, only burn splats every 4 ticks while standing in them; a Corpse's heal splat on him and his bar rising by it (72, 124 and 2); the blast 6 ticks after the second of two heals 10 ticks apart as its tell, and 1 tick after that over the whole floor, a lone third heal setting none off | Sam's recording 17-08-43 of 2026-11-10, read by the Review thread (2026-11-11) |
| The burning corpse piles; a Corpse doused by water spells; fire spells raising more; the "shield" the Wiki says the first explosion takes off; the blast on everyone when all hide too soon; the AoE's shadow; the fallen heap's blockrange; his death spotanim, and him and the Corpses lingering 6 ticks after he dies; the damage figures Sam's recordings don't show yet | not in yet: step 19c and later |
| Ocellus (step 24a): his arena upstairs, on level 1 of the cache's square 64,88, the party landing at 4128,5637 from the nexus's Passageway; the hooded figure (`tfa_ocellus_intro`, 16671, shown met or not met) waiting at 4126,5669 from the run's start; his first phase's stats, 2,500 to 8,880 hitpoints by party size, magic and ranged, poison and venom immunity and the 60% Defence drain floor | Sam's recording of 2026-11-09 (the landing: Grid Info); tsps capture notes (the level, the figure and its tile); OSRS Wiki, Ocellus (2026-11-10) |
| The fight beginning once everyone else in the party has come into his arena and readied at the figure, or 2 minutes after the first ready, with whoever is in the arena then; each ready telling the player how many of the party are ready and the rule (both the game's own chat from Sam's clip, the words kept in the dialogue file under keys); him taking the figure's place as his first phase with his line (key `tfa_ocellus.ascension`, its words on the server, not in git) | Sam's recordings of 2026-11-09; tsps capture notes |
| His first phase (the shield phase): walking from corner to corner of the arena anticlockwise, from a random first one, a tile a tick, under Protect from Melee (his head icon), a fireball or water ball at everyone every 5 ticks as he walks; at a corner, the prayer down and seven cracks 2 ticks apart from the tick after, each laid towards the middle as a start, then shown 2 ticks on, its energy flowing 5 ticks after it was laid; nobody on a crack charging him 2 of 96 (shown in `::raid` and his log), a player on it healed what they lost since it was laid and then hit for 2 to 6; one of his three lines 3 ticks after the last crack, his explosion 3 after that (two rings round him, the inner at once and the outer a tick later, 15 to 35), and on to the next corner 3 after it | tsps capture notes (RSPSApp/tsps docs/fractured-archive.md; 2026-11-09) and Sam's recordings (the charge bar's steps, the lines) |
| The arena's corners 6 tiles from its middle (3 north of the figure); the cracks' 7 lines (2 rows, 2 columns, 3 diagonals) 8 tiles long; Talk-to readying up as well as Proceed (Talk-to, and Proceed for a player who has met him, are the 242 cache's "Hooded Figure" and "Ocellus" options); the first shot 5 ticks after he begins walking; the heal being up to what was lost since the crack was laid; melee doing nothing through his prayer | guessed |
| The middle (step 24b): at two thirds and at a quarter of his blue bar, whatever he is doing, teleporting out and 4 ticks later in at the arena's middle (4126,5672); four sides clockwise from a random one, 8 ticks apart, each with two diagonal cracks 6 long from the ends of that side, 2 ticks apart, shown at once and flowing 4 ticks after each is laid (caught or charging him as a corner's do), and his explosion 6 ticks after each side begins; 8 ticks after the last side his line, the harder one from a charge of 12 (keys `tfa_ocellus.death_awaits` and `.annihilation`), 2 ticks later his charge expended on everyone, unavoidably (17 to 18, or 80 to 90), and the charge empty; on 7 ticks after that | tsps capture notes; Sam's recordings of 2026-11-09 (the softer line hitting 17 to 18 with the charge bar emptying); the 12 and the 80 to 90 are the capture notes' estimates |
| His blue bar run out, held at it with nothing hurting him; 3 ticks in his line (key `tfa_ocellus.will_disappear`) and his teleport to the middle if he isn't there, his transformation 3 ticks after the line, and his second phase 10 after it | tsps capture notes; Sam's recordings (the bar held 13 ticks) |
| At the middle: his first side a tick after he is in, on each side first the end a clockwise sweep round him reaches first (north: the north-west end, then the north-east), no shots and Protect from Melee down there, the annihilation landing as he expends the charge, walking on to the corner he was going to (or the next if he was at one), a second middle owed coming right after the first; held: Protect from Melee down, no cracks or shots, the teleport at once with the line | guessed |
| His teleports' fades and the middle's walk-block loc | not sent: the engine has no NPC fades yet, and the loc's id isn't in the capture notes |
| His second phase (step 25a): `tfa_ocellus_p2` (16675), with no Attack, on a bar of the remainder of his hitpoints (5,250 to 18,640 by party size); 2 ticks in, six orange portals on each arm of the cross every two tiles (E 4128-4138 and W 4115-4125 at 5673, N 5674-5684 and S 5661-5671 at 4127), blocking the way across; 7 ticks in his line (key `tfa_ocellus.your_time`) and the Blade (`tfa_ocellus_p2_sword_hitbox`, 16676, 1,000 hitpoints, its Defence drained to 0) on his tiles, 43% of whose damage comes off his bar; from then 42-tick turns, from the quadrant most of the party stands in, clockwise; in each, six volleys 5 ticks apart: on the two arms beside it every portal purple but one an arm (the capture notes' fixed pattern), firing 4 ticks later across the quadrant along its 2-tile strip, 12 tiles at 6 client cycles a tile, 20 to 25 on whoever it reaches, once a volley from each arm at most; the phase 123 ticks long | tsps capture notes; OSRS Wiki, Ocellus (2026-11-10: the remainder hitpoints, the stats and bonuses, the portal's max hit, one hit an axis); 242 cache (the Blade, the portals' size and collision) |
| Which portal id is orange (62684) and which purple (62685), and which way round the north and south arms' are turned; the first quadrant on a tie or with nobody in one (the first clockwise from the north-west); a quadrant's first volley as its turn begins; the shots' height, and their hit landing the tick they reach a player; no prayer against them; the Blade never hitting back, whole again each tick, its bar shown full, never regaining hitpoints by itself; no diminishing returns on the Blade's damage within a quadrant (the Wiki says there are, not how much) | guessed |
| The rest of a quadrant's turn (step 25b): 28 ticks in, purple pools on 10 to 12 of its tiles and on every player's, bursting 4 ticks later for 25; 31 in, every portal on its two arms purple; 33 in, an orange portal on its floor for 7 ticks with the Blade's discharge (`p2_safe_portal` spawn, idle each tick, despawn), stepping on which moves a player to the same spot in the next quadrant clockwise; 40 in, every purple portal fires across the quadrant, killing whoever is still in it; 123 ticks into the phase, in the third quadrant's turn, his line (key `tfa_ocellus.this_is_enough`) | tsps capture notes (the pools' 25 and the floor portal's destination are their estimates); OSRS Wiki's changes (an immunity window after the floor portal) |
| The pools' graphic (`vfx_ocellus_p2_aoe_dodge`), which tiles, and no prayer against them; the floor portal at the quadrant's middle (6 tiles out from each arm), so a player who stands where they land is on the next quadrant's floor portal; 3 ticks safe from his shots and pools after going through | guessed |
| The portals' spawn and idle animations | not sent yet |
| As the portals stand, whoever is on an arm of the cross or its middle moved a tile into a quadrant beside them (the party's, if one of the two); the last volley also taking whoever in the arena isn't in the next quadrant clockwise, so the middle or a quadrant out of turn is no shelter; his second phase barred to splash (an area spell or a chinchompa reaches only the Blade); freeze immunity in all three phases | guessed, closing exploit review round 22 (R22B-1 to R22B-3): live may never let a player stand there |
| His third phase (step 26a): a tick after his second phase's line, `tfa_ocellus_p3` (16677, with Attack) on the same bar, holding the quadrant the second phase ended in; a tick in the Blade's drop explodes and goes, 2 in the portals go; 4 in the arms crack (`tfa_wallkit_archive03_straight_collapse_01-04`, 3 by 11, blocking walking and shots), with the middle's 3 by 3 void (`tfa_ocellus_platform_void_noblockrange`), and whoever is on the cross is moved into a quadrant beside it; 9 in the arms fall in with explosions out from the middle every 3 tiles, 15 cycles apart; 12 in their empty forms (blocking walking only) with three stepping stones each ("Gap", Jump), and he takes his quadrant from its corner with its barriers up; the crossings SE to SW, SW to NW, NW to NE and NE to SE, a hop a tick (`human_steppingstonejump`); his tank the first to hit him while they stay in his quadrant, the party told he turns to them; every 3 ticks melee, melee and a slash on the tank, twice, the slash a wave 3 deep across his width on the tank's side, shown the tick after his swing and hitting a tick later; with no tank in reach a void shot at everyone; at 75, 50 and 25% of his bar his line (keys `obliteration`, `run`) and the barriers down, 11 ticks later his second (`this_end`, `everything_ends`, `cant_escape`), at 13 the quadrant falls with whoever is on it and the stones of the arms beside it, and at 17 he takes the next clockwise; stats and bonuses, speed 3, melee max 30 and magic 36 | tsps capture notes; OSRS Wiki, Ocellus (2026-11-10: the stats, bonuses, speed and max hits); 242 cache (the locs, their sizes and collision, the animation and graphic names); Sam's recordings of 2026-11-09 (the chat line naming his tank, key `turns_to`) |
| Which arm loc is which (north, east, south, west in id order); the stones' tiles, evenly between the captured ends of each crossing; one Jump carrying a player all the way over (live hops a stone a click), only clockwise and only with the barriers down (key `way_back_closed` otherwise: the capture notes' estimate), with nothing said while the barriers stand; where a player on the cross is moved; his corner in each quadrant (his first phase's there); his first attack 3 ticks after he takes a quadrant; no tank counting as none in reach; the slash a melee roll that Protect from Melee blocks; slash as his melee style and no attack bonuses (the Wiki's are unreadable); the void shot's graphics; no attacks while a quadrant falls; which of his lines he says (at random) | guessed |
| In his third phase no hit taking his bar past his next fall (75, 50 and 25%), or past where it is while a quadrant falls, until his final stand (`Npc.holdsAt`); his room's corridor south of the arena no shelter: the last volley, the cog, the void, the flares, Untether's eruptions, the final stand's blasts, lifeforce and void bursts reach whoever is there, and a quadrant's fall takes them with it | guessed, closing exploit review round 23 (R23B-1, R23B-2); the Raid thread bars attacks from the corridor |
| The barriers' locs (`tfa_ocellus_quadrant_barrier_01-03`, tiles unknown), his fade out and in (the engine has no NPC transparency yet), the fallen quadrant's floor locs, the stones crumbling, his third-phase head bar | not sent yet |
| His third phase's specials and void (step 26b): 3 ticks after he takes a quadrant, two void bombs in each quadrant still standing (`tfa_ocellus_void_degen_bomb`), void until it falls; whoever stands on void takes 6 to 7 a tick; after each six attacks a special, from the second quadrant on Insolence first, then the cog, the void and Untether in turn; Insolence (key `insolence`): three superior slashes (`npc_ocellus_phase3_melee_superior_slash`) 5 ticks apart, each a wave 3 wide running 8 tiles out towards a player, 2 client cycles a step, leaving void in three bands (`_aoe_insolence_1`, `_4`, `_7`) that crumbles after 15 ticks; the cog: its circle (`prayer_in`), then strikes 3 ticks apart for 40 on everyone, Protect from Melee blocking it unless an overhead prayer was on as the circle formed, debris where everyone stood on the second-last strike and void there 5 ticks later for 24; the void (keys `await_void`, `panic_futile`, `void_take_you`): 3 ticks after his line a void shot at everyone and the Void Vine (`tfa_ocellus_p3_void_vine`, 44 hitpoints) rooting someone other than the tank, its entangle graphics on them, crushing them for 50 if nobody else breaks it within 15 ticks; Untether (key `untether`): his rise (`unleash_in`), four eruptions 4 ticks apart on every player's tile among others for 40, the safe square under him 25 ticks in and his coming down (`unleash_out`) 4 later for 45 on his quadrant but his footprint and a tile round it | tsps capture notes; 242 cache (the ids by name, the Vine's hitpoints, the void locs walkable); Sam's recording of 2026-11-09 (the Vine's target box out of 44; key `untether`, `panic_futile`) |
| The bombs' void reaching a tile round them; where they fall (any tile of the quadrant but its edge); the cog's two strikes in the first quadrant, one more each quadrant after; the bulwark blocking the cog and Insolence; the player each superior slash is aimed at (one of the party at random) and its hit (a melee roll as his slash's, a tick after the swing); the bands' rows (1 to 3, 4 to 6, 7 and 8 out); nobody rooted when the tank is alone; the rooted player unable to break the vine themselves and held on its tile; the eruptions' first 3 ticks in, 8 other tiles each and landing 2 ticks after they show; void and its floor damage carrying no attacker; a quadrant's fall ending a special half done and freeing the rooted player; the graphics (`vfx_ocellus_quadrant_bomb_drop_01`, `_p3_shadow_slash_superior_spotanim01`, `_phase3_melee_prayer_in` and `_prayer`, `_falling_debris`, `_spell_entangle01_*`, `_p3_unleash_spotanim`, `_unleash_arena`) | guessed |
| His void flares (step 26c): two at a time in his quadrant, 44 hitpoints each, with a charge bar (headbar 75) under their health filling over 28 ticks, then detonating on everyone in the arena for 15 to 30 (their `_detonate` animation) unless destroyed; in his first quadrant Void Siphons (`tfa_ocellus_p3_bombportal_heal`) as each Untether ends, each healing him 1% every 4 ticks unless a player stands between them, who blocks it and is healed 6; in the second Void Augmenters (`_buff`) and in the third Void Rifts (`_basic`), with his first attack there and every 48 ticks after; their defences, weaknesses and immunities; whoever destroys one empowered (keys `empowered_siphon`, `empowered_augmenter`, `empowered_rift`): a Siphon heals everyone who hurt it half what they dealt it (22 for one alone), an Augmenter boosts their Attack and Strength to 118 and Magic and Ranged to 112 from 99, a Rift resets their attack's cooldown; the Siphon max hit by ranged, the Augmenter by magic, the Rift always by melee and by ranged and magic when they land | tsps capture notes (the timings, the count, the detonation, the Siphon's heal on him); OSRS Wiki, Void Siphon, Void Augmenter, Void Rift and The Fractured Archive/Strategies, 2026-11-10 (the stats, what destroying each gives, the styles that max them, flares with his first attack in the second and third quadrants); 242 cache (the ids, animations and graphics by name); Sam's recording of 2026-11-09 (the chat lines) |
| Where the flares stand (his quadrant, off the void and the cross, 2 tiles clear of him); the Siphon's heal landing as its graphic (`vfx_ocellus_voidflare_healing_projanim`) leaves; between as the tiles of a straight line from it to his middle, short of his footprint, the nearest to it blocking; a ranged hit on the Siphon and a magic hit on the Augmenter still having to land to max hit (the Wiki ties the Augmenter's to magic being the player's best accuracy); the line and graphic to the one whose hit destroyed it alone; the Augmenter's boosts as potions' from the base level (the Wiki gives only the 99 figures) and how long they last; a detonating Augmenter not strengthening him (the capture notes' estimate that it does is not built); the graphic (`vfx_human_ocellus_voidflare_*`); the flares going as his quadrant begins to fall; the detonation from no one; the Siphon's ranged defence (+0; the Wiki's is blank) | guessed |
| The censers (step 26d): from his third phase one in each quadrant (Bandosian 62693, Armadylean 62695, Zarosian 62697, Saradomin 62699), each with Interact once in the fight, then its inactive form (the id after it); Saradomin's restoring 40 hitpoints and 40 prayer points to the party, the Zarosian 50% special attack energy, the Armadylean making the next ranged attack on him a sure max hit and the Bandosian the next melee attack | OSRS Wiki, each censer and The Fractured Archive/Strategies, 2026-11-10 (the effects, once each); tsps capture notes (which censer in which quadrant); 242 cache (the ids, the inactive forms, Interact, 1 by 1) |
| Where each censer stands (its quadrant's outer corner; none is in the cache's map); their standing from his third phase's start and going as their quadrant falls; the Zarosian, Armadylean and Bandosian for the whole party (the Wiki's pages say a player's next); the next attack being one on him; any censer line or graphic | guessed |
| The shorter attack delay the Wiki's Strategies gives the Armadylean and Bandosian hit | not in yet |
| His final stand (step 27a): after the third fall he takes the last quadrant (key `end`) and 3 ticks later begins a 60-tick loop: at 0 he rises (`npc_ocellus_phase3_deflect_in`, `_loop` at 2) with Deflect Melee, melee on him doing nothing, the "range" deflect arena graphic round him (again every 3 ticks) and the tiles under him and one out void; at 21 he lands (`_deflect_out`), the arena and the void ending; at 24 and 52 his charged attack, a style at random shown by its pattern under him (`vfx_ocellus_phase3_melee_hybrid`, `_magic`, `_ranged`), 40 to 50 on everyone in the arena or 6 to 7 through the protection prayer against it, and a tick on Soul Split until his next deflect, healing him a fifth of what the blast deals; at 31 Deflect Range, ranged on him doing nothing, with the "melee" arena graphic and his quadrant void but for him and the tiles next to him, until 50; at 59 Void Rifts, two, then one more each loop up to five; he dies at the end of his bar, and the room is done | tsps capture notes (the loop's ticks, the deflects and their head icons 440:15 and 440:16, Soul Split 440:11, the void, the blast's damage, its styles and Soul Split's heal as estimates); OSRS Wiki, The Fractured Archive/Strategies, 2026-11-10 (the loop's steps, the void floor, the blasts' damage and patterns, the Rifts after the first loop); 242 cache (the animations and graphics by name) |
| The blast landing 2 ticks after its pattern; which turn of the arena graphics is each quadrant's; a deflected hit doing nothing (the capture notes say it turns back on the attacker: not in yet); the Rifts' count past the first; no void bombs, other flares or specials in the final stand | guessed |
| His lifeforce orbs and void bursts in the final stand (step 27b): at 3 and 34 in its loop a wind takes half of each player's hitpoints, rounded down, into an orb on a safe tile of his quadrant, shown to that player alone (`vfx_ocellus_soul_fragment_spawn_12_ticks`, `_idle`, `_despawn`), which once grown 12 ticks on gives them back to the player who walks onto it (`_player_regain`), and not taken by his landing (21) or the arena's end (50) heals him that much; meanwhile a void burst marks each player's tile for them alone every 2 ticks, 6 times (`vfx_void_chain_player_telegraph`), hitting anyone still on it 2 ticks on for 15 to 16 (`vfx_void_chain_explosion`), and a player who stepped to a tile beside it dodged it perfectly, 3 ticks off their attack's cooldown; while the bursts run, hits on him by weapons slower than 2 ticks cut by 33% (3 ticks), 50% (4) and 60% (5); `npc_ocellus_phase3_soul_fragment_start` risen and `_end` at the arena's end, his fragment graphic (`_ocellus_levitating_idle`, `_ocellus_idle`) every 2 ticks | OSRS Wiki, The Fractured Archive/Strategies, 2026-11-10 (half the hitpoints, the orb on a safe tile seen by its player alone, healing him if not taken, the bursts' six times, the slower weapons' cuts, the dodge's 3 ticks); tsps capture notes (the ticks, the orb's 12 ticks to grow, the bursts' spacing and landing, the animations, the fragment graphic's slot; the bursts' damage as an estimate); 242 cache (the graphics by name) |
| The orb 2 to 5 tiles off its player, never on their own tile or a censer's; an orb too late at his landing or the arena's end; an orb whose player died or left still healing him; the wind's graphic (`vfx_ocellus_deflect_wind_spotanim_01`) and its hit coming from him; attacks of 6 ticks and slower cut as 5 (an attack's ticks the cooldown it sets, rapid's tick and a manual cast's speed counted); the dodge taken off the cooldown already running | guessed |
| His death (step 27c): `npc_ocellus_phase3_death` with `vfx_ocellus_death`, `_death_skirt_spotanim01` and `_death_crack_spotanim01` and its sound (`tfa_ocellus_phase3_death_arm_strain_01`), his room done at once; 14 ticks later the Blade (`tfa_blade_npc_vault_entry`, 3 by 3, Commune and Travel) where he fell, and Travel taking the party in his room to the vault | tsps capture notes (the animation, graphics and sound, the Blade 14 ticks on where he fell, Travel to the vault); 242 cache (the ids by name, Travel) |
| The graphics' slots and the sound's reach; his body going as the Blade appears; Travel setting off everyone in his room at once, each by `ArchiveRaids.travel` to the reward vault four ticks on, with no graphic; Commune doing nothing yet | guessed |
| The raid's total time and the Combat Achievements kill at his death; his charge bar and health bars on screen | not in yet |
| The inner vault's Passageway (62766, Exit) leaving to the lobby in front of the Archive Door once the run is done, and doing nothing before | guessed, until a recording of a run's end shows what it does; a way out once the run is done was Sam's decision of 8 November 2026 (exploit review RAID-3), first as a stand-in `tfa_exit` in the corridor, now on the cache's own loc |
| The reward vault: level 2 of map square 64,90, the party coming in at 4127,5779 at the south end of its walk; its Rift (`tfa_vault_exit`, 63297, no options) at 4127,5773 and its Merit scroll (`tfa_vault_scoreboard`, 62775, Read) at 4129,5778, both put there by the server; the Rift out (16690, Leave and Quick-leave, 3 by 3) coming at 4126,5796 about 15 ticks after the party arrives and going back to the lobby | tsps capture notes; the 241.4 cache for the walk and the island (shut in by blocked floor), for neither loc being on the map, and for the Rift out's options and size |
| The vault's square on all four of its own levels in the run's instance, two squares north of Ocellus's as on the map (live gives each room an instance of its own); each member travelling by their own click, four ticks on, only from Ocellus's room once it is done (`ArchiveRaids.travel`, from the Blade's Travel of step 27c; `::raid travel` stands in for the click); the count to the Rift out starting with the first member in; the Rift's and the scroll's shape and angle; Leave and Quick-leave both leaving at once, to the lobby tile in front of the Archive Door | guessed, or inferred from the map |
| A death once the run is done (Ocellus's room cleared): nothing lost, nobody down, no wipe even alone, and not counted; the player is up again at the vault's arrival tile if they died in the reward vault, else at the nexus's entrance (exploit review round 23, R23A-1: with no room left to end, such a death left its victim down for good, where leaving or logging out is a death with a grave, and wiped a lone player out of a finished run) | guessed, all of it: what a death after the last room costs on live, and where it puts the player, isn't known |
| A death in a run counted as the player falls, not as they get up, so a fall in the ticks before Ocellus dies counts for the loot though they are up after; Ocellus dying with nobody of the party on their feet a wipe, with no loot (exploit review round 24, R24A-3: a death landing after the run's end was free, and a party all lying dying as he fell was paid) | guessed: what live does in either case isn't known |
| What a run pays (step 29a), rolled once as Ocellus's room is done: one roll for the party for a unique, 1 in 14 alone or as two, found by one member, each as likely as their share of the party's contribution, and which unique by weights of 15 (Rondache 5, Zorya's tome 4, Horn of Bran 3, Ascension crossbows 2, Obligator 1); for everyone else three rolls on the standard table (33 lines, weights of 330), each roll 1 in 51 to give five times as much, then an elite clue 1 in 10, the inert Archivist's folio 1 in 15 (never with one owned) and an elemental fragment 1 in 20 (never one owned, none with all four); a death costing a tenth of what one of the party is expected to earn of the run's 700 (`src/main/resources/archive-loot.json`, `ArchiveLoot`) | OSRS Wiki, Chest (The Fractured Archive), read 10 November 2026; the items' ids from the 241.4 cache |
| The unique's chance past two players: 4% of the base added for each (104 in 1,400 for three, 124 in 1,400 for eight); only the members with a share of the loot counting as players, so an idle alt adds nothing (exploit review round 24, R24A-2; guessed) | inferred: the Wiki calls it multiplicative, and its own figures (1/13.46 for three, 1/90.32 a player for eight) fit the sum, not 4% compounded |
| A member with no contribution left getting a cabbage alone, and nobody else one (the Wiki has a poor run give only cabbage, likelier with each death, with no numbers); whoever finds the unique getting nothing else; the fragment being one of those not owned, each as likely; a death's tenth rounded down; the pay going to the party's members who are in the run at its end, the party's size for a death's cost being theirs; owning a folio or fragment meaning in the inventory, the bank or worn, in the guide prices screen, offered in a trade, in the player's grave or held by a Grand Exchange offer (exploit review round 24, R24A-4), or the fragment broken (`tfa_mote_*_unlocked`); one dropped or given away no longer counting | guessed |
| The vault's chests: `tfa_rewards_chest_vault_1_closed` to `_8_open` (16736 to 16751), each a multi-NPC on `tfa_vault_own_chest` (16433) that shows a player's own apart from the others (Chest, Open; once opened Rift, Search); their eight tiles round the island, one a party slot; Open playing `chest_guthixian02_open01` (15101) and leaving the Rift | 241.4 cache; tsps capture notes for the tiles, the slots and what Open does |
| A chest's slot being its member's place in the party at the run's end; only the paid members' chests being there, from the run's end on; the Rift coming a tick after Open (the animation is 30 client cycles); Open on another's chest refusing with a line, whose words aren't known (it shows its key, `[tfa_vault.chest_not_yours]`) | guessed, but the tick, which is inferred from the animation, and that a player opens only their own chest, which is the Wiki's |
| Search on the Rift one's own chest leaves (16737 to 16751, Search) opening the chest's window, `tfa_chests` (973), over the player's loot on inv `tfa_chests` (1029, six slots): Bank-all (973:5, op 6), Take-all (973:7, op 1), Discard-all (973:9, op 6) and the item grid (973:11) drawn by `interface_inv_init`; loot that can be banked, taken or destroyed; loot left unclaimed held in the lobby's Loot Wheelbarrow (`tfa_rewards_chest_lobby`, 63299 at 4136,5551, a multiloc on `tfa_has_claimable_loot` that shows Search only with loot waiting) | 241.4 cache and osrs-dumps (`interface/tfa_chests.if3`) for the ids, ops and layout; OSRS Wiki, Chest (The Fractured Archive), for what the window does and the Wheelbarrow |
| An item's options Take, Take-5, Take-10 and Take-All; an item that doesn't stack coming out noted, and going into the bank as itself | the tsps capture notes' estimate ("as the Tombs of Amascut's") |
| The grid as two columns of three; Discard-all asking Yes or No first; the loot saved with the player (so a logout keeps it); the same window for the Wheelbarrow; the varbit set whenever loot waits, in the vault too; the Archive Door turning away a player with loot of an earlier run unclaimed (as the Theatre of Blood's chest holds a player to their last loot); another's Rift refusing Search with the line Open on another's chest gives (live took Search off others' Rifts on 3 November 2026); a chest never opened paying into the Wheelbarrow all the same | guessed |
| The unique's own chest on the northern platform (16730) and the purple braziers that show one was found; Orion the pet (1 in 750); the clue's better rate with the elite Combat Achievements; scroll boxes; the cabbage's chance by deaths; the loot's graphics; the collection log | not in yet |
| The Merit scroll's records, the braziers' fires, the vault's music, the Rift out's fade-in | not in yet |
| The uniques worn (step 30): their bonuses, slots and speeds the cache's, which are the Wiki's infoboxes (Ascension crossbows, Obligator and Zorya's tome two-handed; the Rondache a shield; the Horn of Bran not worn); their requirements 80 Ranged, 85 Attack, 80 Defence and 85 Magic (`item-requirements.tsv`); the Obligator's combat tab (dbtable 78's row 36: Thrust, Thrust and Smash, all crush) and Smash every 3 ticks where Thrust is 5; the tome's (row 37: Accurate, Accurate and Longrange) and its own spell as a powered staff's, a charge a cast of 3 astral and 2 nature runes, up to 20,000, 7 tiles and 9 on longrange; the crossbows firing ascension bolts and their gem-tipped forms only, which no other crossbow fires, 6 tiles and 8 on longrange; their animations and the bolts' and tome's graphics the cache's (`ArchiveUniquesAuditTest` checks the cache's numbers against the Wiki's) | OSRS Wiki, Ascension crossbows, Obligator, Rondache, Zorya's tome, Horn of Bran, Weapons/Types, read 10 November 2026; 241 cache |
| The tome's max hit, a third of the Magic level rounded down less 9 before its +15% (the Wiki's formula renders garbled); which of the Obligator's two attack animations is Thrust and which Smash; which tome graphic is the cast and which the travel; the uniques' turning and side-step animations (the cache has none of their own); the crossbows firing from one hand, where live alternates them; the uncharged tome's prayer bonus, 0 in the cache where the Wiki gives +2 (the cache's kept) | inferred or guessed |
| The uniques' effects: the Obligator's charges and its second hit and reach, the Rondache's armour and Shield Slam, Zorya's flow, the crossbows' halved bolt effects, the Horn of Bran's Zeal prayer; the female animations; the tome's Configure-Charges | not in yet |
| Death costs (step 31), for every grave: an item worth 100,000 coins a unit or more waits in the gravestone's paid list, most valuable first, until Unlock pays its fee: 1,000 a unit from 100,000, 10,000 from 1,000,000 and 100,000 from 10,000,000, the total capped at 500,000; an Ironman pays half; the fee is taken from the bank, Death's Coffer not being built; once paid, the button takes the paid list | OSRS Wiki, Gravestone, read 10 November 2026; the 241 cache's gravestone scripts (gravestone_generic_window_set, script1797) for the list's split, the bands and the halving |
| A grave made in the raid's map squares (proc script10182: the rooms, not the lobby or the reward vault) and not merged with an earlier one costing a quarter of that while the player has fewer than 5 clears (`total_completed_thefracturedarchive`, varp 5894, counted for each member paid at the vault's chests); every death in the raid losing a Hardcore Ironman their status | OSRS Wiki, The Fractured Archive ("reduced by 75% until the player has cleared the raid 5 times"; "always a dangerous death"); 241 cache, script1797 and script10182 |
| The quarter's cap a quarter of 500,000 too, and each item's fee capped at it; the clear counted when the chests pay out; the words of the Unlock refusal, the paid message and the Hardcore loss (keys here); too few coins in the bank taking none | inferred or guessed |
| The bosses after the Guardian, the Quartermaster and Supply Credits, the party's orbs, taking the loot out of the reward vault's chests, deaths' item costs | not in yet: steps 18 to 31 |

`::raid run` shows the run you're in and where you stand in it (the room, and the cache's tile as `::raid tp x z` takes it), `::raid tp nexus` or `::raid tp ezekiel` goes to its nexus or a room, `::raid tp x z`
goes to a cache tile of the raid mapped into the run (on its room's level; for scripts, which can't know where the
instance lies), `::raid loot` shows what the run's end paid (who found the unique, and what your own chest holds), and
`::raid clear` marks the room you're in done, as if its boss died; a room whose fight never began (the Guardian before it is touched) gives its points to whoever of the party stands in it. Check: `./gradlew test --tests 'osrsps.game.instance.*'
--tests 'osrsps.game.combat.DeathsTest' --tests 'osrsps.testclient.HeadlessArchiveRaidTest'` (a run through the empty
rooms by the west doors and one by the east; rows opening in order and a row's other door shut; a death that leaves the
party standing, a player too late to come in, the wipe to the lobby with graves, and a member down who teleports out,
leaves the party or logs out dying of it; the reward vault: Travel landing four ticks on, its Rift and Merit scroll, the
Rift out fifteen ticks after the first in, Leave and Quick-leave to the lobby; a death once the run is done, in
Ocellus's room and alone in the vault, leaving them standing with everything; the run's end paying the party's two
members in it by their contribution and not the one who stayed out, each one's own chest shown as theirs, opening a
tick after Open and refusing the other's click; Search on their own Rift opening the chest's window, Take, Take-5, Take-all with no room for the rest, Bank-all; the loot left behind waiting in the lobby's Loot Wheelbarrow, the Archive Door turning them away until it is claimed, Discard-all's No and Yes) and `osrsps.game.PlayerSaveTest` (unclaimed loot saved) and `--tests 'osrsps.game.raid.ArchiveLootTest'` (the loot's file as
the Wiki's table; the unique's chance by party size; a death's cost; the rolls' rates over thousands of seeded runs; no
second folio or fragment; the cabbage), and in RuneLite
`test-runner/examples/archive-raid-flow.json` (both runs, solo, the first out by the reward vault after opening its
chest there, Search on the Rift it leaves and Bank-all from the chest's window, and a death that costs nothing), `archive-ocellus-corridor.json` (a bow's Attack on Ocellus refused from his corridor and taken
from his arena) and `archive-raid-wipe.json` (a death in the raid back to
the lobby, with a grave). The uniques: `./gradlew test --tests 'osrsps.testclient.HeadlessArchiveUniquesTest'` (the
Obligator taking the Rondache off, its combat tab and Smash's 3 ticks; the crossbows refusing rune bolts and shooting
ascension bolts, 2 ticks on Rapid; the tome charged with astral and nature runes killing a man with its own spell),
`ArchiveUniquesAuditTest` with `OSRSPS_FLATCACHE` set (the cache's numbers against the Wiki's), and
`test-runner/examples/archive-uniques.json`. Death costs: `./gradlew test --tests 'osrsps.game.combat.GraveFeesTest' --tests 'osrsps.game.combat.DeathsTest'` (the bands, the cap, the halving and the raid's quarter; the paid list, Unlock refused and paid; the Hardcore loss) and `test-runner/examples/archive-raid-death-costs.json`. Ocellus: `./gradlew test --tests 'osrsps.testclient.HeadlessOcellusTest'` (the figure and the
ready check, alone, with two, and the 2 minutes; his walk, corners, cracks, charge and explosion; a crack caught; the middle at two thirds and a quarter; the hold and the
transformation; the second phase's portals, the Blade's damage reaching his bar, the quadrants' turns and volleys, a
shot reaching a player and the safe crossing; the pools, every portal purple, the floor portal taking her on and the
last volley; the third phase's fallen cross and stepping stones, his quadrant, his tank, melee, slash and void shot, the way
back closed, a jump over an arm, and the quadrants falling with whoever stays) and
`test-runner/examples/archive-ocellus.json`.

## Encounters

A boss fight is written as an encounter (raid step 12): `game/encounter/Encounter.kt` has the boss, its phases, and each
phase's attack cycle, steps a set number of ticks apart that repeat. A phase ends when the boss's hitpoints fall to a
share of its maximum, or after so many ticks, and the next begins, or the one the phase names (a fight that goes
round: a shield that comes back). A step can hit a player, put down tile hazards (a
warning graphic now, then damage each tick to whoever stands on the tiles), or put in adds.
`game/encounter/Encounters.kt` runs every fight once a tick. A fight ends when its boss dies or leaves the world, and its
adds and hazards go with it. Each fight keeps a log of what it did, by tick. Every roll comes from the world's random, so
a fight started on a seed plays the same way each time.

A boss that goes from sight in the middle of its fight and comes back (Dreadnoth, split into jellies) is hidden, not
removed: `World.hide(npc)` keeps it in the world under its index, with its fight, timers and hitpoints going on, while no
client is shown it (RSProt's inaccessible avatar), its tiles are free, it stands still (a fight's own steps and swings
for it do nothing), and nothing clicks, attacks or hurts it and it fights no one, so a player who was fighting it is
free for other monsters, and content that looks NPCs up (`Npcs.inPlay`, `Npcs.near`: a gate's guard, a stall's owner)
doesn't find it, while the engine's own loops (`Npcs.all`) still do; `World.show(npc)` brings it back where it stands (moved meanwhile with
`Npcs.teleport`, there). By hand: `::hidenpc`.

Check: `./gradlew test --tests 'osrsps.game.npc.NpcsTest' --tests 'osrsps.testclient.HeadlessHiddenNpcTest'`, and in
RuneLite `test-runner/examples/npc-hidden.json`.

| Encounter behaviour | Source |
|---------------------|--------|
| A hidden boss's poison waiting, its timer where it was, till it is shown, and hits already on their way to it when it is hidden doing nothing; its drained stats coming back and its hitpoints regenerating meanwhile, as its other timers go on | guessed |
| Fights running in the NPC phase, after NPC scripts | guessed, until a recording shows a boss |
| A phase changing at the start of the tick its threshold or time is reached, before that tick's steps | guessed |
| A fight's first tick running as it starts (steps with no delay, hazards due at once), paused or not | guessed |
| Hazards hurting from no one, so standing in one doesn't start a fight or make the player hit back | guessed |
| A hazard's hit still counting as combat: it cuts a Home Teleport or lodestone cast short and holds a logout, as the boss's own hit would (exploit review round 11, ENC-1) | guessed |
| Adds and hazards going when the boss dies or leaves | guessed; each boss's step changes it if live keeps them |
| A loc a fight places (a mushroom, a portal) standing until the fight takes it away or ends, which puts the tile back as the cache has it, its own loc and turn included; turned a quarter or more (Ocellus's portals, step 25a) it blocks its turned footprint | ours: the cache's loc must come back; turning as the cache's own locs turn |
| A phase also ending when its own test says so (a shield broken); steps put off a few ticks (a beam aimed now, landing later) running after the cycle and before the hazards, never once the fight is over | guessed |
| A phase naming the one that follows it, an earlier one too; a name the fight hasn't ending the fight, said in its log | ours: a mistake in a fight's own code, kept from throwing in the game tick |
| A boss whose fight picks its attacks (the Guardian) never swinging, shooting, walking after anyone or retreating by itself, and keeping the first player it took on while they last | guessed |
| A fight outside an instance covering 32 tiles round the boss's spawn, for the starter and their raid party only (so another test account standing in Lumbridge isn't drawn in), ending if the starter dies or logs out (else a script stopped mid-fight leaves them dying again and again at Lumbridge's spawn) | testing only: the raid's rooms are instances |
| The Archive Guardian (step 16); the others each come with their own step, from 17 on, with its room and gear sets for `::raid` | not in yet: steps 17 to 27 |

`::raid boss phase=2 party=3 seed=42` starts a fight where you stand, and `::raid digest 100` sums up its first 100
ticks. Check: `./gradlew test --tests 'osrsps.game.encounter.*' --tests 'osrsps.game.command.RaidCommandsTest'
--tests 'osrsps.testclient.HeadlessEncounterTest'` (a test boss plays the same 100 ticks on two runs with the same seed
and differently on another; its phases change at half hitpoints and on time, with an add and a hazard, and the fight
ends when the boss leaves; a phase goes back to the one it names, three times round, then on; a turned placed loc
blocking its turned footprint and the cache's loc back when the fight ends).

## The Archive Guardian

The raid's first boss (raid step 16a) is `game/raid/ArchiveGuardian.kt`, an encounter on the cache's two forms. It
waits in the nexus's hall as the "Inert Guardian" until a member touches it and answers Yes to "Begin the challenge?".
It wakes levitating, with its shield's
hitpoints, shooting the player it took on every 5 ticks and slamming anyone who stands under it instead; only ranged,
magic and halberds reach it. With the shield gone it holds at 0 for 2 ticks and falls to the ground, blasting whoever is
under or beside it, with its grounded hitpoints, and swings at a player beside it. After 35 ticks on the ground it lifts
off behind a whole shield, and when that is gone it comes down again with the hitpoints it had left, for 41 ticks each
time from then on. Once its hitpoints are at 30%, 4 ticks later it lifts off behind one more whole shield, and from
then to its death it sends a beam at a random player every 20 ticks, naming them in chat 9 ticks before it shows; it
reaches them a tick after that. The beam runs from its middle through that player and on. With an inert guardian
behind them, that one takes the beam (a splat of 330 for four players, 400 for five) and goes dark, the players on the
line take 8 to 10, and 4 ticks later the shield loses 40% of its most, or with no more than that left loses just what
it had and breaks. With none behind them, the players on the line share the whole beam and the shield is untouched.
That shield has no timer. When it is gone the Guardian is down for the last time, with the hitpoints it had left, and
the beams go on by the same count until it dies. Its death does the room.
While a shield is up it grows a mushroom two tiles from each player; stepping beside one
bursts it, poisoning and wearying (no running, attacks a tick slower), and the burst splashes anyone two tiles off.
Grounded, a vine rises on each player's side away from it every 3 ticks and swipes the tiles round it three times.
Four inert guardians stand round the hall (step 16c). Under a shield three are awake: beaten to 0 one is charged,
taking 40% off the shield, and spent. The south-east one never wakes, so only a beam spends it.

| Guardian behaviour | Source |
|--------------------|--------|
| Stats, bonuses, both forms' hitpoints by party size (shield 550 to 1,950, grounded 1,100 to 3,910), 5-tick attacks, max hits 25 and 99+, 80% fire weakness, poison and venom immunity, Defence drained to 60% at most | Wiki, Archive Guardian (read 2026-11-03) |
| Two forms: `tfa_guardian` (16659) levitating, `tfa_guardian_unshielded` (16660) grounded with slash defence 90 not 110; the animations and spotanims by name | 241.4 cache |
| Levitating, only ranged, magic and halberds hitting it; shots at the player it chose; a slam on whoever stands under it; the shield's break grounding it; a blast on those under or next to it; the last phase at 30%; the beam "fatal if more than two players are alive and it isn't reduced", shared by standing in line, "slightly" cut by Protect from Magic | Wiki, The Fractured Archive/Strategies, as a guide site quotes it (the Wiki's own page is too long to read past its equipment); its "no shield" in the last phase is not what the recordings show |
| The shield and the hitpoints by turns: the shield whole again each time it lifts off, the hitpoints carried over; a broken shield's 2 ticks at 0 (2 to 3 seen); its first time down 35 ticks (34, 36 and 35 seen, with 55%, 76% and 64% of its hitpoints left) and 41 each time after (41 and 40 to 41 seen); a time down ending 4 ticks after its hitpoints go under 30% (three fights, at 30.3%, 29.6% and 29.4%; guessed: the 4 ticks run their course even when its time on the ground would be up sooner) | Sam's recordings of three live fights (2026-11-09 and 10, parties of four and five), the bars read tick by tick |
| "Begin the challenge?", Yes or No, on Touch | Sam's recordings (the menu on each Touch, the fight after Yes); not in: the 5 or so ticks from Yes to its rise, it wakes at once |
| After 30%, one more whole shield, with beams; no beam before it in any fight; a beam 19, 20 and 21 ticks into that shield and each 20 ticks after it (seven beams), the same count going on once it is down; the inert guardian behind a beam's target taking 400 and going dark (three times, five players); 40% of the shield's most gone in one tick 4 to 6 ticks after such a beam (504 of 1,260, 412 of 1,030); that shield ending 4 to 5 ticks after a beam with less than 40% left (103, 123, 66), the hitpoints back on that tick with no ticks at 0; 8, 8 and 10 on players on such a beam's line; a beam with no inert guardian behind its target killing him, the shield untouched; no timer on that shield (43, 44 and 64 ticks); the time down after it lasting to the death or the wipe (22, about 29 and 63 ticks) | Sam's recordings (clip notes: tick pass, full-frame pass, H1 to H6) |
| The chat line, 9 ticks to the beam showing, 1 tick to its reaching the players on its line and the inert guardian behind them, 4 ticks to the 40% off the shield (14 in all), the lines 20 ticks apart; the inert guardian's splat 330 with four players; 10 on each of three players on one beam's line; a charge with less than 40% left taking just what was left (a 348 splat with 348 left) and the bar going straight to the hitpoints | Sam's recordings of 2026-11-10, read again by eye at 10 frames a second with the tick metronome in frame (three beams, the same each time) |
| The inert guardian's splat growing with the party as the shield does (330 with four, 400 with five: 1,030 and 1,260 of shield) | inferred; the other party sizes guessed at the same 32% of the shield |
| The beams beginning with the shield that follows 30%, not with the third shield | inferred: one fight's third shield, entered at 34.6%, had none, and its fourth, entered at 23%, did |
| A spent inert guardian's charge, once the Guardian is down for the last time, taking a tenth of its hitpoints off it but never into kill range (10%); shots, mushrooms and the inert guardians awake under the beams' shield as under the others | guessed (a guide: the beam's inert guardian "damages the boss's health", and in kill range "they stop dealing damage to the boss") |
| The chat line to a player who takes a whole beam (the game calls it the Guardian's "breath") | not in: its words weren't legible in the recording |
| The grounded form's 40% fire weakness | a guide (osrsbestinslot); the Wiki lists 80% and 40% without saying which form has which |
| Ranged shots (Protect from Missiles), a crush swing grounded (Protect from Melee), the slam blocked by Protect from Melee | guessed for the slam; the prayers for the others from the attack styles |
| The first attack 5 ticks into each phase; a beam's line from the boss's middle through its target, 32 tiles on, aimed where they stood as it showed | guessed |
| The slam up to 25, the blast 10 to 20, a whole beam 80 for one or two players and 40 for each player from three, Protect from Magic taking a tenth | guessed, to fit the Strategies page's words |
| It never moves, and keeps to one tank till they fall or go out of its range or sight, when it turns at once on someone in it (holding its fire only when nobody is); grounded, those who stand off get its vines | guessed |
| The words for melee refused on a flying monster (`combat.flying`) | guessed |
| Mushrooms (locs 62578 to 62580, `tfa_archive_guardian_mushroom_spawn`), the Vine (16664, `archive_guardian_tentacle`, its spawn, swipe and despawn animations and `vfx_guardian_tentacle_intro`) | 241.4 cache |
| Mushrooms while a shield is up; their 3 to 10, poison and weariness (slower attacks, no running); a run beside one and on popping it, a walk leaving a weaker splash; vines from 6 ticks into the grounded phase, then every 3, each on the player's side away from the boss and swiping a 3x3 three times on a 3-tick cycle, Protect from Melee capping a swipe at 11 | guides (osrstoolkit, runescapeguides, bald.gg; read 2026-11-03) |
| A mushroom round each player every 15 ticks, 2 tiles off, growing for 2 and bursting after 20 if no one comes; the splash 1 to 5 at 2 tiles; poison severity 20 (hits of 4); weary 10 ticks, a tick on each attack; a vine swipe up to 22 unprayed | guessed |
| An antipoison beforehand avoiding "the poison damage only" (so weariness comes all the same) | Wiki, The Fractured Archive/Strategies, its equipment table (read 2026-11-03) |
| The inert guardians' three forms (`tfa_guardian_battery_inactive`, `_active` with Attack, `_destroyed`; 2 by 2), the awake one's 180 hitpoints, Defence and Magic 50, and its stab, slash, crush and magic defence (0, 20, -20, -20, its params 5 to 8, which give the Wiki's figures on the Guardian) | 241.4 cache |
| Four round the room, charged by attacking them; charging taking ~40% off the shield (596 of 1,490); the south-east one only hit by the beam, which damages the boss's health; the beam destroying the one behind its target; lining it up reducing the beam; no damage to the boss once it is in kill range | guides (runescapeguides, osrstoolkit; read 2026-11-03); the recordings show players attacking them under the first two shields, one at 0 |
| Where they stand (level with its middle 9 tiles west and 8 east, and 10 south; the recordings show them as statues set in the hall's walls, one west at about 4113,5603 and one on the south wall, their count not read); the awake ones put to sleep as it falls; their ranged defences, 0; a swing on a spent one landing for 0 | guessed |
| The step-under timing, a guide's "firebreath" enrage with braziers, the Serpentine helm shortening weariness, which guardians melee reaches and which only ranged (a guide's earth weakness, with no figure) | not in yet (the enrage isn't on the Strategies page; there is no Serpentine helm yet) |

`::raid guardian party=3` starts it where you stand, `phase=grounded`, `phase=beams` or `phase=final` from a later phase. Check:
`./gradlew test --tests 'osrsps.testclient.HeadlessArchiveGuardianTest' --tests 'osrsps.game.combat.NpcCombatTest' --tests 'osrsps.game.MovementTest'`
(touched and told Yes in its hall, No leaving it waiting, through its four phases to its death doing the room, the
instance multi-way; its shield back after 35 ticks on the ground and after 41 the next time, its hitpoints carried
over; the beams' shield 4 ticks after 30%; a beam 20 ticks after that shield goes up and every 20 after, named 9 ticks
before, the count going on once it is down; a whole beam shared by two on its line, each told who it follows; melee
refused while it levitates but for a halberd; a beam's line and damage; a mushroom bursting with poison and weariness,
then splashing; a vine's swipes held to 11 by Protect from Melee; a weary player walking with run on; the inert
guardians charged into the shield, three grounding it; one behind a beam's target spent by it, 8 to 10 left for the
player, its charge taking 40% of the shield 4 ticks on or breaking it at once; and down for the last time a tenth of
its hitpoints, never into kill range), and in RuneLite `test-runner/examples/archive-guardian.json` (the whole fight
solo, Touch with No then Yes, one round of its shield coming back, the beams' shield with its chat line and beam, and
its last time down, moved on with `::hit`), `test-runner/examples/archive-guardian-hazards.json` (a vine and a
mushroom), `test-runner/examples/archive-guardian-batteries.json` (an inert guardian charged, and one spent by a
beam for 40% of the shield) and `test-runner/examples/archive-guardian-entrance.json` (a bow's Attack on an inert
guardian refused from the nexus's entrance and taken from the hall).

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
Thrown weapons (darts, knives and thrownaxes, every metal and poison) throw from the weapon slot, and the light and
heavy ballistas fire javelins from the ammo slot. Gem-tipped bolts fire from the crossbow of their metal, and
enchanted ones roll their effect on monsters (`game/combat/BoltEffects.kt`). Not in yet: crystal bows, the dragon
hunter crossbow, the thrown weapons' and ballistas' special attacks, and enchanted bolts against players.

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
| Darts range 3, knives and thrownaxes 4, ballistas 9; ballistas fire bronze to dragon javelins every 7 ticks, the hit landing 1 + (7 + d) / 6 ticks later (`HitDelay.BALLISTA`) | OSRS Wiki, Attack range, Light ballista, Heavy ballista and Hit delay |
| Thrown weapons fire from the weapon slot, one a throw; their launch and travel graphics and the dart, dragon knife and ballista animations by their 242 cache names | OSRS Wiki, Thrown weapons; 242 cache |
| A thrown weapon that doesn't break falls under the target, as arrows do, unless an Ava's device brings it back | inferred from arrows |
| The knife and thrownaxe throw animation (`human_stake2_pvn`); the ballista's wrong-ammunition message (`combat.wrong_javelins`, words on the server) | guessed |
| Gem-tipped bolts (opal to onyx) and the same enchanted, each at its metal's tier; their dragon bolts, enchanted or not, at the dragon bolts' tier | OSRS Wiki, each bolt's page |
| Enchanted bolts on monsters: opal 5% (Ranged / 10 more) and pearl 6% (Ranged / 20) hit or miss; sapphire 25% on a hit (an eighth of a tenth of the monster's Ranged as prayer); emerald 55% on a hit (poisoned, 5 at first, 75 in all); ruby 6% hit or miss (a fifth of its hitpoints, at most 100, for a tenth of yours, and not when you can't spare a hitpoint); diamond 10% hit or miss (a sure hit on a max hit 15% higher); dragonstone 6% on a hit (Ranged / 5 more); onyx 11% on a hit (20% more, healing a quarter); topaz only on players; each one's graphic (749 to 758) on the monster | OSRS Wiki, each (e) bolt's page (read 2026-11-10); 242 cache |
| The Zaryte crossbow's passive: each effect 10% stronger (opal Ranged / 9, pearl / 18, dragonstone 22%, onyx 132%, diamond 126%, ruby 22% to 110, emerald 6 at first; sapphire unchanged) | OSRS Wiki, Zaryte crossbow |
| The effect rolled after accuracy, a draw only for an enchanted bolt; onyx's 20% on the rolled damage; heal, prayer and ruby's cost as the bolt lands; jade's knockdown not on monsters; dragonfire-immune and undead monsters not told apart yet; the pearl's fiery bonus and the hard Kandarin Diary's 10% not counted | guessed, or not built |

Check: `./gradlew test --tests 'osrsps.game.combat.Ranged*' --tests 'osrsps.testclient.HeadlessRangedTest'`, and in
RuneLite `test-runner/examples/combat-ranged-shortbow.json`, `combat-thrown.json` and `combat-bolt-effects.json`;
thrown weapons and ballistas also in `HeadlessThrownTest`, the bolt effects in `BoltEffectsTest` and
`HeadlessBoltEffectsTest` (ruby's cost to the shooter as the client sees it).

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

`fishing-cooking` stands you on Catherby's beach (2850, 3430) with 99 Fishing, Cooking and Firemaking, a harpoon, a
lobster pot, a big fishing net, a small fishing net, a karambwan vessel, 50 raw karambwanji, a fishing rod, 50 sandworms, a
tinderbox and three logs, and the
dragon and barb-tail harpoons in the bank: every fish Fishing and Cooking 2 has built so far, caught and cooked on a fire.
Musa Point's spots are at `::tele 2924 3178`, Karamja's karambwanji spots at `::tele 2791 3018`, karambwan spots at
`::tele 2899 3118`, the Piscatoris monkfish spots at `::tele 2326 3699` and Port Piscarilius's anglerfish spots at
`::tele 1827 3769`. The bank holds flour, water, pie dishes, cooking apples, tomatoes, cheese, cake tins, eggs, milk and
chocolate for pies, pizzas and cakes, and bowls of water, potatoes, cooked meat, spice, curry leaves, grapes and jugs of
water for stews, curries and wine; the Lumbridge east farm's dairy churn is at `::tele 3226 3293`.

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

`magic` stands you on the Lumbridge spawn with 99 Magic and a staff of air, the runes for ten casts of every standard
teleport built (and a banana for Ape Atoll), and two of each teleport tablet: for the Magic steps.

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
