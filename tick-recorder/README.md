# Tick Recorder (RuneLite plugin)

Records what the RuneLite client already sees, one game tick at a time, to a local file. The
recordings are reference data for rebuilding live behaviour 1:1 on our server: which animation an
NPC starts on which tick, when its projectile leaves and lands, when the hitsplat shows, and so on.

It only reads state RuneLite exposes to every plugin. It sends nothing anywhere, changes nothing in
the game, and never touches network traffic.

## Using it

Until it is on the Plugin Hub, run it only against our own server, never on Jagex's worlds.

- Turn on **Recording** in the plugin's settings (or bind the toggle hotkey). Each start writes a
  new file to `<RuneLite folder>\plugin-data\tickrecorder\<UTC time>.jsonl` and says so in the
  chatbox. An existing file is never overwritten.
- Turn it off to close the file. If the file can't be written, recording turns itself off and the
  chatbox says why.

Settings: radius around you (default 16 tiles), and switches for other players (off by default),
objects and ground items, varps and varbits, your menu clicks (off by default: turn it on when a piece needs the
input timing) and sounds.

### In our test client

RSProx starts its RuneLite with `--developer-mode` and its RuneLite folder is `C:\RSPS\.rlcustom`.
In developer mode RuneLite loads every `.jar` in `<RuneLite folder>\sideloaded-plugins` at startup,
so:

1. Download `tick-recorder-jar` from the latest `build` run on GitHub Actions and unzip it.
2. Copy `tick-recorder-<version>.jar` to `C:\RSPS\.rlcustom\sideloaded-plugins\`.
3. Start `C:\RSPS\RSProx.cmd`, log in, and turn on Recording in the plugin's settings.
4. Recordings land in `C:\RSPS\.rlcustom\plugin-data\tickrecorder\`. The first start after this
   change moves an older `C:\RSPS\.rlcustom\tick-recorder\` folder there.

## Privacy

- Other players are off by default. While off, nothing about another player is written: no
  positions, no events, no projectiles or sounds they cause, and clicks on them keep only the
  option, action and menu params.
- Player names are never written on purpose. Clicks on players drop the target and identifier.
  In NPC shouts, game messages and click targets, names of players currently in your scene are
  replaced with `{player}` and your own name with `{self}`. A name of someone no longer nearby can
  still appear in a game message.
- Player chat is never recorded. Trade offers from the other party are skipped.

## File format (version 2)

JSON Lines: one JSON object per line, field `t` gives its type. Every line has:

- `tick`: number of tick lines written so far plus one. A `tick` line is written at the end of each
  game tick; an event carries the number of the `tick` line that follows it, which is normally the
  tick whose server update produced it. Exceptions: animations and spotanims ending on their own
  (`anim` -1) come from the client, clicks belong to the first tick line after the click, and sounds
  with a `source` may be started by an animation frame. `tick` counts tick lines, not server ticks:
  logins, hops and stalls leave gaps that only `ms` and `gameState` lines show.
- `cycle`: the client's 20 ms cycle counter (`client.getGameCycle()`). Projectiles are picked up on
  the cycle they appear, so `startCycle - cycle` is their delay.

Within one tick, lines are not in packet order: group by `tick`, and apply spawns before other
events.

Actors are referenced as `{"self":true}`, `{"npc":<index>,"id":<npc id>}` or
`{"player":<index>}` (only with Other players on).

Locations are `{"x","y","plane"}` of the server tile (the SW tile for NPCs and objects bigger than
one tile). Inside an instance they also carry `tx`, `ty`, `tplane`, the template tile the instance
was built from, which stays the same between visits. Boats (Sailing) are out of scope: only the main
world view is recorded.

`-1` means none: no animation, no target, no health bar.

| `t` | When | Fields |
| --- | --- | --- |
| `start` | recording begins | `format`, `recorder` (plugin version), `runelite`, `revision`, `world`, `worldTypes`, `time`, `settings` |
| `tick` | each game tick | `clientTick`, `ms`, `self`, `npcs[]`, `players[]` (with Other players on). Each actor: `at`, `orientation`, `anim`, `frame`, `pose`, `spotanims[]`, `healthRatio`, `healthScale`, `target`; NPCs add `size`; `self` adds `energy`, `weight`, `destination` |
| `anim` | actor animation changes | `actor`, `anim`, `frame`. A repeat of the same animation fires no line; watch `frame` drop in tick lines |
| `spotanims` | actor spotanims change | `actor`, `spotanims[]` of `{slot, id, height, startCycle, frame}`, all current ones |
| `hitsplat` | hitsplat received | `actor`, `type` (hitsplat type id), `amount`, `mine`, `disappearsCycle`. Health after the hit is in the next tick line |
| `interact` | actor changes target | `actor`, `target` (absent when none) |
| `say` | NPC overhead text | `actor`, `text` |
| `npcSeen` | NPC first in range (spawned, walked in, or present at start) | `actor`, `id`, `name`, `size`, `combatLevel`, `at` |
| `npcSpawn` / `npcDespawn` | NPC appears or leaves while in range | same as `npcSeen` |
| `npcChange` | NPC transforms | same plus `oldId` |
| `projectile` | projectile appears | `id`, `from`, `sourceLevel`, `source`, `to`, `targetLevel`, `target`, `startCycle`, `endCycle`, `startHeight`, `endHeight`, `slope`, `startPos` |
| `spotanim` | graphic on a tile | `id`, `at`, `startCycle` |
| `object` / `objectSpawn` / `objectDespawn` | object present at start, appears or disappears | `id`, `at`, `kind` (`game`, `wall`, `ground`, `decor`), `type` (0-22), `rotation` (0-3); `game` adds `sizeX`, `sizeY`; `wall` adds `orientationA`, `orientationB` |
| `groundItem` / `groundItemGone` | ground item present at start, appears or disappears | `id`, `quantity`, `at`, `ownership`, `private`, `visibleTime`, `despawnTime` |
| `message` | game text (never player chat) | `type` (chat message type name), `text` |
| `stat` | your skill changes | `skill`, `xp`, `level`, `boosted` |
| `items` | an item container of yours changes | `container`, `items` as `[id, quantity]` per slot (`[-1, 0]` is empty) |
| `var` | your varp or varbit changes | `varp`, `varbit` (-1 for a whole varp), `value`. Some varps are set by the client ahead of the server, on the click's tick |
| `varps` | recording starts | `values`: every non-zero server varp by index, int and long varps alike (before 0.2.1 only the few long varps were read, so the line was nearly always empty) |
| `click` | you click a menu option | `option`, `action`, `param0`, `param1`, `consumed`; not on a player: `target`, `id`, `npc`; on a player: `player` (with Other players on) |
| `widgetOpen` / `widgetClose` | interface opens or closes | `group` |
| `sound` / `areaSound` | sound effect | `id`, `delay`, `source`; `areaSound` adds `at` and `range` |
| `gameState` | login state changes | `state`, `ms` |
| `world` | you change world | `world`, `worldTypes` |
| `config` | a setting changes while recording | `key`, `value` |
| `error` | a recorder handler threw | `where` (handler name), `error` (exception class), `count`. The first five per handler per recording; later ones are only counted |
| `health` | every 100 ticks, since 0.2.2 | `gameTickUsMax`, `gameTickUsP50` (this plugin's own time on a game tick, microseconds, over the last 100 ticks), `clientTickUsMax`, `lines` and `chars` (written so far), `queueMax` (most lines waiting for the disk at once since the last health line; 0.2.2 counted the per-tick flushes too), `errors` (handler errors so far) |
| `stop` | recording ends | `reason` (`stopped`, `pluginStopped`, `clientExit`; `writerBehind` when the disk fell 20000 lines behind, `writeFailed` when the file could not be written, in which case the stop line itself is a best effort; the reason is settled after every line queued before the stop is written, so a failure among them wins; since 0.2.3 a recording's failure is its own, never one the recording before or after ran into), `time` |

Lines written at the start of a recording carry `"baseline":true` (`items` for inventory and
equipment, every `stat`, `varps`, and `object` and `groundItem` in range). Object and ground item
lines written while a new scene loads carry `"sceneLoad":true`: they are the scene being built, not
the server adding them.

A recording that ends in a crash has no `stop` line, and its last line may be cut short; skip a
final line that doesn't parse.

Version policy: `format` is this table's version: new fields and line types keep it, changing what an
existing field means bumps it. `recorder` is the plugin version, bumped with every change to what is
written, so a reader knows which build made a file.

## Building

From the repository root: `./gradlew -p tick-recorder build` (PowerShell: `.\gradlew -p tick-recorder build`).
It needs `repo.runelite.net`, which CI can reach. CI uploads the jar as the `tick-recorder-jar`
artifact.

`src/test/java/.../TickRecorderPluginTest.java` starts a development RuneLite with the plugin
loaded. Point it only at our server.

## Plugin Hub

The Hub builds plugins from their own public GitHub repository at a pinned commit, so publishing
means copying this folder into a new public repo and opening a pull request on
`runelite/plugin-hub`. That happens only with Sam's go. Before that, the wording here and the
`osrsps` package name need a decision, since a plugin framed around a private server may be refused,
and the new repo needs a LICENSE. The plugin uses no extra dependencies, reflection or network
access, and writes only to its own folder through RuneLite's file API.
