<div align="center">

# ⚔️ OSRSPS

**A private Old School RuneScape test server, built from the ground up for testing RuneLite plugins against 1:1 recreations of live content.**

[![build](https://github.com/SamSjosten/OSRSPS/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/SamSjosten/OSRSPS/actions/workflows/build.yml)
![OSRS revision](https://img.shields.io/badge/OSRS-revision%20242-c8aa6e?style=flat)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7f52ff?logo=kotlin&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-e76f00?logo=openjdk&logoColor=white)
[![RSProt](https://img.shields.io/badge/protocol-RSProt-4c8eda)](https://github.com/blurite/rsprot)
![Private](https://img.shields.io/badge/server-private%20test%20only-555)

[Quick start](#-quick-start) · [Build and run](#️-build-and-run) · [VM 214](#️-on-vm-214) · [Connect RuneLite](#-connect-runelite) · [Testing tools](#-testing-tools) · [Commands](#-developer-commands)

</div>

---

Our own engine in Kotlin on Java 21, with [RSProt](https://github.com/blurite/rsprot) for the network protocol. Every
behaviour is matched tick for tick from the OSRS Wiki and recordings of the live game, and anything guessed is marked
in the code. Current RuneLite connects unchanged.

> [!NOTE]
> Revision **242**, set in `src/main/kotlin/osrsps/Revision.kt`. The cache build VM 214 runs is in
> `deploy/cache-build.txt`.

### 🗺️ What's in it

| | |
|---|---|
| 🏰 **Areas** | Lumbridge, Draynor, Varrock, Al Kharid, Edgeville, the swamp and Misthalin's dungeons, the Wilderness |
| ⛏️ **Skills** | Gathering, artisan and combat skills, Agility, Thieving, Magic, Farming, Hunter, Runecraft, Slayer |
| 🐉 **Bosses** | The Fractured Archive raid, Misthalin's bosses and the Wilderness bosses |
| 🧪 **Testing** | Pause and step ticks, fixed seeds, one-command scenarios, a headless client and a RuneLite test runner |

### 🚀 Quick start

```bash
./gradlew run --args=fetch-cache   # once: download the live cache into data/
./gradlew run --args=serve         # game on 43594, launcher files on 43595
```

Then point RSProx at `http://<host>:43595/proxy-targets.yaml` ([Connect RuneLite](#-connect-runelite)) and log in
with any new name.

### 📚 More docs

| File | What's in it |
|------|--------------|
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How pull requests move: auto-merge, the queue, conflicts, revision bumps |
| [`AGENTS.md`](AGENTS.md) | Review guidelines, sourcing (`// Guessed:`, `// Inferred:`), what never goes in git |
| [`deploy/README.md`](deploy/README.md) | How VM 214 deploys itself, cache swaps, the staging server |
| [`test-runner/README.md`](test-runner/README.md) | The RuneLite test runner, its script format, headless runs |
| [`TRIAGE.md`](TRIAGE.md) | How filed problem reports are triaged |
| [`NOTES.md`](NOTES.md) | Each step's build notes as it merged. Not kept in step with the code; the code wins |

## 🚫 Never commit Jagex files

> [!CAUTION]
> The game cache, the client, XTEA keys, RSA keys, gameplay recordings and the words NPCs say stay out of git. They live
> under `data/` (git-ignored) on the machine that runs the server.

## 🛠️ Build and run

Needs JDK 21; the Gradle wrapper fetches Gradle.

```bash
./gradlew build                       # sandbox VM (bash)
./gradlew run --args=serve            # game and JS5 on 43594, HTTP files on 43595
./gradlew run --args="serve 50000"    # another port (HTTP on 50001)
```

On Windows use `.\gradlew.bat` the same way. The other entry points:

| `--args=` | What it does |
|-----------|--------------|
| `fetch-cache [build]` | Downloads the revision's cache and XTEA keys from [OpenRS2](https://archive.openrs2.org) into `data/cache/`, keeping the old one in `data/cache.previous`. With a build (`242`), only if that is OpenRS2's newest; else exits 3 |
| `cache-info` | Summarises the cache in `data/` |
| `collision x z [level]` | Prints what the server thinks is walkable around a tile |
| `dialogue-keys` | Writes `data/dialogue.txt` with every key, keeping the words already there (see Dialogue words) |
| `set-password name` | Sets an account's password (see Accounts) |
| `import-model in.obj out.dat [--scale f] [--mirror-x] [--flip-faces] [--priority n] [--alpha material=n]` | Converts a Wavefront OBJ to a client model for `data/models/<id>.dat` |
| `import-sprite in.png out.dat` | Converts an image to a client sprite for `data/sprites/<id>.dat` |

`OSRSPS_DATA` points at another data folder. `data/cache-source.txt` names the cache in use, and the server won't start
on a cache of another revision. To roll back a cache, stop the server and swap `data/cache` with `data/cache.previous`
and `data/cache-source.txt` with `data/cache.previous-source.txt`.

The login RSA key is `data/rsa.key`. Its modulus is committed in `src/main/resources/rsa-modulus.txt`, because the
launcher's releases carry it, so `serve` refuses a missing or different key. For a local server with its own key, set
`OSRSPS_LOCAL_KEY=1` and the first start generates one.

Models, sprites and loc, var and animation types of our own (ids from 65,000) are served on top of the cache from
`data/models/`, `data/sprites/` and code (`game/content/Custom*.kt`, `osrsps.cache.overlay`). The cache files on disk
stay live's.

## 🖥️ On VM 214

The server runs in Docker in `/srv/rsps/OSRSPS`, with `data/` mounted in. A timer redeploys every merge to `main`
within a few minutes; a merge that only touches top-level docs, tests, the plugins, `docs/`, `deploy/` or `.github/`
doesn't restart it. `http://192.168.0.210:43595/version` names the running commit, and `/status` adds the cache and
frame checks.

```bash
docker compose up -d --build      # build and start by hand
docker compose logs -f            # follow the log
docker compose run --rm osrsps collision 3222 3218
```

Settings are in `compose.yaml`'s `environment:`:

| Variable | Meaning |
|----------|---------|
| `OSRSPS_ADMINS` | Names that may run admin commands (comma separated). Unset means everyone, with a warning in the log |
| `OSRSPS_PUBLIC_HOST` | Host the launcher files point at when a request has no usable Host header |
| `OSRSPS_RELAYS` | Relay addresses allowed to send a PROXY protocol header (the VPS relay, 192.168.0.216) |
| `OSRSPS_PROXY_PROTOCOL` | `optional` (default), `required` or `off`, for relay connections |
| `OSRSPS_SEED` | Fixed random seed at startup; unset picks one and logs it |
| `OSRSPS_REPORTS_REPO` | Repository problem reports are filed in (default this one) |

Any branch can run on the staging server next to the live one (`git push origin my-branch:staging`, ports 44594 and
44595); see `deploy/README.md`. Changes to the VM's system, the Proxmox host or the network go through the Home Lab
project.

## 🔌 Connect RuneLite

### Through RSProx

The developer route. Over the LAN nothing needs to run on the PC. Away from it, keep an SSH tunnel
open (PowerShell):

```powershell
ssh -J root@192.168.0.210 -L 43594:127.0.0.1:43594 -L 43595:127.0.0.1:43595 sam@10.99.0.10
```

In RSProx press **+** next to the proxy target selector, **From URL…**, paste
`http://192.168.0.210:43595/proxy-targets.yaml` (`http://127.0.0.1:43595/...` through the tunnel) and restart RSProx.
The server fills in the modulus and the RuneLite build pinned for its revision (`src/main/resources/runelite-pins.json`).

### Letting friends test

Friends reach the server at `odinsgate.net`, a VPS relay of 43594 and 43595 run by the Home Lab project, with Odin's
Gate, our launcher. The launcher, its download page and releases live in the OdinsGate repository; `docs/` here only
forwards to that page (CI's `friend-page` job checks it). The launcher reads `/status.json`, `/rl/worlds.js` and
`/launcher/*.ws` from the HTTP port. A revision bump adds the new RuneLite build to both pin tables first
(`CONTRIBUTING.md`).

### Problem reports

The launcher's "Send problem report" posts to `POST /report`. The server scrubs each one, saves it under
`data/reports/`, and every minute files waiting reports as issues here labelled `bug-report` (a repeat of an open issue
becomes a comment there), at most 20 a day. Filing needs `data/github-report-token.txt`: a fine-grained token with
Issues read and write on this repository only, put there by hand. Without it, reports wait. A scheduled task triages
new issues by `TRIAGE.md`.

## 👤 Accounts and admins

The first login as a name claims it with the password typed; after that only that password works. Names ignore case
and treat spaces as underscores. Names with `@` and names that break live's display name rules are refused. Five
wrong passwords lock a name for 5 minutes. A name in `OSRSPS_ADMINS` can't be claimed, so claim it first, then list it.

Accounts are `data/accounts/<name>.json` (a PBKDF2 hash) and saves are `data/saves/<name>.json`, neither backed up.
To set a password (12 characters or more, asked twice, no restart):

```bash
cd /srv/rsps/OSRSPS && docker compose exec -e JAVA_OPTS=-Xmx64m osrsps bin/osrsps set-password <name>
```

Deleting a name's account file resets it. `::ipban` bans an address (kept in `data/ip-bans.txt`); behind the relay
that is the player's own address from the PROXY header.

## 💬 Dialogue words

Dialogue, examine text and game messages are Jagex's, so code holds keys (`[hans.greeting]`) and the words live in
`data/dialogue.txt` on the server, one `key = words` a line. A key with no words shows as the key in brackets, so
everything runs and is tested without them. `dialogue-keys` rewrites the file with every key in place (the old one goes
to `dialogue.txt.bak`), `::dialogue` reloads it without a restart, and `::keys` shows keys instead of words. Each
conversation is a `Lines` object listed in `DialogueLines.ALL`; examines come from `src/main/resources/examines.json`.

## 🧪 Testing tools

- ⏱️ **Tick control.** The game ticks every 600 ms in fixed phases (`TickPhase`). `::pause` freezes it after the current
  tick, `::step [n]` runs n ticks, `::resume` restarts it. While paused, commands still answer; clicks wait for the
  next tick.
- 🎲 **Random seeds.** Every roll comes from `World.random` (`GameRandom`, xoshiro256\*\*), and a test fails if `src/main`
  uses anything else. `OSRSPS_SEED` or `::seed n` fixes it; `::pause`, `::seed n`, set up, then `::step` repeats a
  run.
- 🎬 **Scenarios.** `::scenario name` sets position, stats, inventory, equipment, bank and vars in one go; `::reset` puts
  you back as you were before your first one, across logouts too. They are JSON files in
  `src/main/resources/scenarios/`, listed in `Scenarios.FILES`, format in `game/Scenario.kt`.
- 🥔 **The rotten potato.** `::item 5733`. Its Eat, Slice, Peel and Mash menus run the `::` commands (stats, wipe, heal,
  teleports, spawns, god mode, sure hits, XP rate, scenarios, logout, death), so admins can test from the menus.
- 🤖 **Headless test client.** `src/test/kotlin/osrsps/testclient/` speaks the real protocol, and `TestServer` runs the
  world with no cache and 100 ms ticks, so content is tested in `./gradlew test`. `./gradlew headlessScripts` plays
  the runner's scripts against the live cache without RuneLite (`test-runner/README.md`).
- 🏃 **The RuneLite test runner.** `test-runner/` is a sideloaded plugin that plays JSON scripts in the real client on
  the test VMs (215, 217), for test accounts only. `tick-recorder/` records what the client sees each tick.

## ⌨️ Developer commands

Type them in the chatbox. Click a group to open it. Only admins (`OSRSPS_ADMINS`) can run them, except those marked **everyone**, which only
read the world or only show you something. A dead player can run only those. An unknown command lists the ones you
can use. Commands live in `src/main/kotlin/osrsps/game/command/`; register new ones on `World.commands`, next to
related ones.

<details open>
<summary><b>🧭 Position and world</b></summary>

| Command | What it does |
|---------|--------------|
| `::pos` | **everyone.** Your `x, z, level`, the region and your tile in it |
| `::tele x z [level]` | Teleports there; `::pos` output pastes in |
| `::teleto name` | Teleports onto a player |
| `::wildy level` | Jumps to that Wilderness level north of Edgeville |
| `::lodestone [site]` | **everyone.** Opens the lodestone map, or casts to an activated stone |
| `::instance [radius]` / `::instance leave` | A private copy of the map around you, or back out of it |
| `::locs [radius]` | **everyone.** Locs near you: id, name, tile, shape, angle, and whether they differ from the cache |
| `::addloc id [shape] [angle]` / `::delloc` | Puts a loc on your tile, or removes every loc there |
| `::resetlocs [radius]` | Puts nearby locs back as the cache has them |
| `::los npcIndex` / `::los dx dz` | **everyone.** Distance, line of sight and hit delays to an NPC or tile |

</details>


<details>
<summary><b>⏱️ Ticks, seeds and scripts</b></summary>

| Command | What it does |
|---------|--------------|
| `::tick` | **everyone.** The tick number and whether the clock is paused |
| `::pause` / `::step [n]` / `::resume` | Freezes the clock, runs n ticks (up to 1000), starts it again |
| `::seed [n]` | Shows the seed and rolls drawn, or restarts the rolls from seed n |
| `::queue weak\|normal\|strong\|soft [delay]` | Queues a test script on you |
| `::delay [ticks]` / `::timer normal\|soft interval [times]` / `::timer off` | Delays you, or starts or stops a test timer |
| `::scripts` | **everyone.** What you have queued, delayed and on timers |
| `::scenario [name]` / `::reset` | Starts a scenario (alone, lists them), or ends it |

</details>


<details>
<summary><b>🎒 Items, stats and vars</b></summary>

| Command | What it does |
|---------|--------------|
| `::item id\|name [count]` | Adds items by id, menu name or gameval name (`::item coins 10k`) |
| `::empty` | Empties your inventory |
| `::openbank` | Opens your bank anywhere (`::bank` never reaches the server) |
| `::itemprices [refresh]` | Reloads the Grand Exchange price snapshot, or downloads a new one |
| `::setlevel skill\|all level` | Sets a skill's level and XP |
| `::addxp skill xp` | Adds exact XP (one decimal), ignoring the XP rate |
| `::boost skill level` | Sets only the current level (0 to 255) |
| `::stats` | **everyone.** Boosted and drained stats and ticks until each moves back |
| `::statcycles` | Restarts the boost and drain cycles |
| `::xprate [rate\|restore]` | Server-wide XP rate, 0.1 to 1000 (kept in `data/xp-rate.txt`); 1 is live's |
| `::varp id\|name [value]` / `::varbit id\|name [value]` | Shows or sets a var; our own vars go by name (`::varp lodestones`) |
| `::slayer [task <id\|name> [amount] [master] \| points n \| streak n \| clear \| superior]` | Shows or sets your Slayer task, points and streak |
| `::farmtick [ticks]` / `::farmclock pause\|run` / `::farmdisease patch\|off\|on` | Moves, holds or runs your farming clock; makes a patch diseased or stops disease |
| `::nest [seeds\|ring\|red\|green\|blue]` | Drops a bird's nest as a tree would |

</details>


<details>
<summary><b>⚔️ Combat</b></summary>

| Command | What it does |
|---------|--------------|
| `::combat [index\|name]` | **everyone.** Attack rolls and max hits against an NPC |
| `::fight` | **everyone.** What you are fighting and ticks to your next swing |
| `::hp` | **everyone.** Your hitpoints and who is attacking you |
| `::drops [index\|name]` | **everyone.** An NPC's drop table |
| `::god [on\|off]` / `::sure [on\|off\|max]` | You can't be hurt; your attacks can't miss (`max`: melee hits max too) |
| `::heal` / `::hit damage [index\|name\|me]` / `::kill [index\|name]` | Heals you, hits an NPC or you, kills an NPC |
| `::prayers` / `::pray points` | **everyone** for `::prayers`. Active prayers and points; sets points |
| `::spec [percent]` | Shows or sets special attack energy |
| `::poison [severity\|venom\|cure]` / `::poison npc severity\|venom [index\|name]` | Poisons, envenoms or cures you or an NPC |
| `::drain stat levels\|percent% [index\|name]` | Drains an NPC's stat up to its cap |
| `::stun [ticks\|end]` | Stuns you, or ends it |
| `::multi [on\|off]` | Marks your zone multi-way, or single, until a restart |
| `::spellbook [name]` | Shows or switches your spellbook |
| `::blowpipe [dart darts scales]` / `::shadow [charges]` / `::tome [charges]` | Charges a blowpipe or powered staff without the items |
| `::aggro [ticks]` | Your aggression tolerance count, or sets it |
| `::skull [on\|off]` / `::pj` | Gives or removes a skull; clears your PJ timer |
| `::wboss [name]` | Lists or spawns a Wilderness boss |

</details>


<details>
<summary><b>👹 NPCs and ground items</b></summary>

| Command | What it does |
|---------|--------------|
| `::npc id\|name [wander]` | Spawns an NPC on your tile until a restart |
| `::npcs [radius] [mine]` | **everyone.** Nearby NPCs: index, tile, spawn, state and who each is fighting |
| `::delnpc index\|mine` / `::hidenpc index\|mine` | Removes an NPC, or hides and shows it |
| `::npchp hitpoints index\|mine` | Sets an NPC's hitpoints, past its maximum too |
| `::follow index` | An NPC follows you; again to stop |
| `::respawns` | **everyone.** Killed NPCs waiting to respawn and ticks left |
| `::groundobj id\|name [count]` | Puts a public item on your tile |
| `::objspawn id\|name [count [respawn ticks]]` | An item spawn on your tile until a restart |
| `::grounditems` / `::cleartile` | Lists the items on your tile, or clears them |

</details>


<details>
<summary><b>🏛️ Raid</b></summary>

| Command | What it does |
|---------|--------------|
| `::raidparty [invite\|accept\|kick name \| leave]` | Shows or manages your raid party |
| `::raid boss [phase=] [party=] [seed=]` | Starts a boss fight on your tile; alone, shows the fight or lists bosses |
| `::raid log\|digest\|stop\|run\|tp\|travel\|loot\|clear\|hold` | The fight's log, stops it; in an Archive run, shows it, goes to a room, travels, loots, clears or holds |
| `::raidnpc id\|name [party size]` | Puts in a raid NPC scaled for a party size |

</details>


<details>
<summary><b>🤝 Social, Grand Exchange and admin</b></summary>

| Command | What it does |
|---------|--------------|
| `::friends` / `::ignores` | **everyone.** Your lists as the server keeps them |
| `::clearfriends` / `::clearignores` | Empties a list |
| `::clan` / `::guestclan` | **everyone.** Your clan or guest clan |
| `::clanmake name` / `::clanjoin name [recruiter]` / `::clandisband [name]` | Makes, joins or deletes a test clan |
| `::clanguests` / `::clanapplicant` / `::clanbanned` | Shows guest settings, adds the test applicant, shows bans |
| `::clanfounding [founders hours]` | Shows or sets what founding a clan takes |
| `::geoffer buy\|sell item quantity price` / `list` / `abort\|collect slot` | Grand Exchange offers without its screens |
| `::ge` / `::gecollect` / `::gefill slot [amount]` / `::gemarket [on\|off]` | Opens the GE screen or collection box, fills an offer at its price, stops or starts the bot market |
| `::reports` | The newest player reports |
| `::ip [name]` / `::ipban name\|address [note]` / `::ipunban address` / `::ipbans` | Addresses and bans |

</details>


<details>
<summary><b>✨ Client and effects</b></summary>

| Command | What it does |
|---------|--------------|
| `::keys [on\|off]` | **everyone.** Dialogue shows keys instead of words; test scripts turn it on |
| `::dialogue` | Reloads `data/dialogue.txt` and reports problems |
| `::buttons [on\|off]` | **everyone.** Echoes which interface clicks reach the server |
| `::if id [side]` | Opens an interface bare |
| `::ifevents interface component start end events1 [events2]` | Sends IF_SETEVENTS as given |
| `::anim id [delay]` / `::spotanim id [height] [delay] [slot]` | Plays an animation or spotanim on you |
| `::gfx id [height] [delay]` / `::proj id [dx dz]` | Plays a spotanim on your tile, or fires a projectile |
| `::sound [id] [loops] [delay]` / `::jingle id` | **everyone.** Plays a sound or jingle to you |
| `::areasound id [radius] [loops]` | Plays a sound from your tile to everyone in range |

</details>
