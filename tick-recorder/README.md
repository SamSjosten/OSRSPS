# Tick Recorder (RuneLite plugin)

Records what the RuneLite client already sees, one game tick at a time, to a local file. The
recordings are reference data for rebuilding live behaviour 1:1 on our server: which animation an
NPC starts on which tick, when its projectile leaves and lands, when the hitsplat shows, and so on.

It only reads state RuneLite exposes to every plugin. It sends nothing anywhere, changes nothing in
the game, and never touches network traffic.

## Using it

Until it is on the Plugin Hub, run it only against our own server, never on Jagex's worlds.

- Turn on **Recording** in the plugin's settings (or bind the toggle hotkey). Each start writes a
  new file to `%USERPROFILE%\.runelite\tick-recorder\<UTC time>.jsonl` and says so in the chatbox.
- Turn it off to close the file.

Settings: radius around you (default 16 tiles), and switches for other players (off by default),
objects, varps and varbits, your menu clicks and sounds.

## File format (version 1)

JSON Lines: one JSON object per line, field `t` gives its type. Every line has:

- `tick`: game tick number since the recording started. A `tick` line is written at the end of each
  game tick; events carry the number of the `tick` line that follows them, which is the tick whose
  server update produced them. Clicks belong to the tick in which the server will act on them.
- `cycle`: the client's 20 ms cycle counter (`client.getGameCycle()`), for sub-tick timing such as
  projectile flight.

Actors are referenced as `{"self":true}`, `{"npc":<index>,"id":<npc id>}` or
`{"player":<index>}`. Player names are never written.

Locations are `{"x","y","plane"}`. Inside an instance they also carry `tx`, `ty`, `tplane`, the
template tile the instance was built from, which stays the same between visits.

| `t` | When | Fields |
| --- | --- | --- |
| `start` | recording begins | `format`, `runelite`, `world`, `time`, `radius` |
| `tick` | each game tick | `clientTick`, `self`, `npcs[]`, `players[]` (if enabled); each actor has `at`, `orientation`, `anim`, `pose`, `graphic`, `healthRatio`, `healthScale`, `target` |
| `anim` | actor animation changes | `actor`, `anim` |
| `graphic` | actor spot animation changes | `actor`, `graphic`, `height` |
| `hitsplat` | hitsplat shown | `actor`, `type`, `amount`, `mine`, `healthRatio`, `healthScale` |
| `interact` | actor changes target | `actor`, `target` |
| `say` | NPC overhead text | `actor`, `text` |
| `npcSpawn` / `npcDespawn` / `npcChange` | NPC appears, leaves or transforms | `actor`, `id`, `name`, `size`, `combatLevel`, `at`, `oldId` |
| `projectile` | first tick a projectile is seen | `id`, `from`, `startCycle`, `endCycle`, `startHeight`, `endHeight`, `slope`, `target` |
| `spotanim` | graphic on a tile | `id`, `at`, `startCycle` |
| `objectSpawn` / `objectDespawn` / `wallSpawn` / `wallDespawn` | object changes nearby | `id`, `at`, `orientation` |
| `message` | game message (never player chat) | `type`, `text` |
| `stat` | your skill changes | `skill`, `xp`, `level`, `boosted` |
| `items` | an item container of yours changes | `container`, `items` as `[id, quantity]` |
| `var` | your varp or varbit changes | `varp`, `varbit`, `value` |
| `click` | you click a menu option | `option`, `target`, `action`, `id`, `param0`, `param1` |
| `widgetOpen` / `widgetClose` | interface opens or closes | `group` |
| `sound` / `areaSound` | sound effect | `id`, `delay`, `source` or `at` and `range` |
| `gameState` | login state changes | `state` |
| `stop` | recording ends | `time` |

## Building

From the repository root: `./gradlew -p tick-recorder build` (PowerShell: `.\gradlew -p tick-recorder build`).
It needs `repo.runelite.net`, which CI can reach.

`src/test/java/.../TickRecorderPluginTest.java` starts a development RuneLite with the plugin
loaded. Point it only at our server.

## Plugin Hub

The Hub builds plugins from their own public GitHub repository at a pinned commit, so publishing
means copying this folder into a new public repo and opening a pull request on
`runelite/plugin-hub`. That happens only with Sam's go. This folder already follows the Hub's
example-plugin layout (`build.gradle`, `settings.gradle`, `runelite-plugin.properties`), uses no
extra dependencies, reflection or network access, and writes only under `.runelite`.
