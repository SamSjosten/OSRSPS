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
