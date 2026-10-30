# AGENTS.md

OSRSPS is a private Old School RuneScape test server: our own engine in Kotlin on Java 21, RSProt for the protocol,
revision 241 (`src/main/kotlin/osrsps/Revision.kt`). Content is recreated 1:1 from the OSRS Wiki and recordings of the
live game, so RuneLite plugins can be tested against it. See `README.md` for building, running and the commands, and
`CONTRIBUTING.md` for how pull requests move.

Agents that open pull requests: turn on auto-merge (merge commit) on every pull request you open, right after
opening it, and pull your branch before each push, as the automerge workflow may have merged `main` into it
(`CONTRIBUTING.md`, Auto-merge).

## Review guidelines

Review every pull request thoroughly. Read the whole diff and the code it calls, not only the changed lines, and report
every real problem you find with an honest priority, P2 and P3 included. Several pull requests are open at once and
merge as soon as CI is green, so a finding left for later often ships. Don't pad a review with style opinions; do flag
anything below that the diff gets wrong.

### Behaviour and tick timing

- 1:1 means tick for tick: the game ticks every 600 ms (`TickClock`), and the order of work inside a tick matters
  (player input, then NPCs, then players, then what the client is sent). Flag anything that moves an action, delay,
  timer or animation a tick early or late compared with the Wiki or the comment it cites.
- Behaviour taken from the Wiki or a recording should say where it came from. Anything not sourced must be marked with
  a `// Guessed:` comment (`// Inferred:` when it is reasoned from other live behaviour). Flag unmarked guesses, and a
  `Guessed:` that the Wiki answers.
- Check bounds and off-by-one errors in tile maths, region and zone coordinates, levels (planes), inventory slots,
  stack sizes and `Int` overflow on coins and experience (experience is capped at 200,000,000).

### Randomness and tests

- `World.random` (`GameRandom`) is the only source of randomness in game code, so seeded tests replay exactly. Flag
  `kotlin.random.Random`, `java.util.Random`, `Math.random` or `UUID.randomUUID` in `src/main`. Tests may use them for
  filler bytes.
- Content needs headless tests (`src/test/kotlin/osrsps/testclient`, `TestServer`) and, for anything a player sees,
  RuneLite runner scripts. Flag a feature without tests, tests that can't fail, and tests that depend on wall-clock
  time or on the order of a `HashMap`.

### Protocol and the client

- The client is the live 241 client and must connect unchanged. Check packet fields, varp and varbit ids, interface
  and component ids against the cache and `osrs-dumps`, not against older revisions or other servers.
- `IF_SETEVENTS`: ops go in events2 (op n is bit n-1). Bits 1-10 of events1 are old op bits the 241 client ignores;
  events1 keeps pause (bit 0), targets (11-16) and drag (17-21).
- When the client changes a setting or state by itself (a varp, a dropdown), the server has to mirror it or the client
  snaps back. Flag a client-side change the server doesn't record.

### Exploits and trust

Treat everything a client sends as hostile. Flag:

- missing checks on distance, line of sight, the player's state (dead, in combat, busy, interface open) and whether the
  slot, item or target still exists when the packet arrives, a tick or more after the click;
- item duplication or loss: an item that can end up in two places, or none, across trades, banks, drops, deaths,
  graves, logout and a save that fails halfway;
- anything one player can do to stall the tick, flood a zone or fill memory for everyone (unbounded lists, per-packet
  allocations without a cap, loops a client controls);
- logouts that skip the combat lock, and saves that aren't written atomically.

### Files that must never be committed

Never commit Jagex files: the game cache, the client, XTEA or RSA private keys, recordings, `*.jar` (except the Gradle
wrapper), or the words NPCs say. Flag any of these in a diff.

Dialogue and examine text is Jagex's, so code holds keys only (`[cook.greeting]`) and the words live in
`data/dialogue.txt` outside git. A missing word is expected, not a bug. Flag the opposite: English dialogue or examine
text written into code, resources or tests.

### Quests and content scope

There are no quests: every account counts as having completed them all (`Quests.kt`). Don't ask for quest checks or
quest state. Content is built only as requested, one piece at a time, so missing neighbouring content is not a finding
unless the pull request says it adds it.

### Code

- Match the surrounding code: its naming, comment density and idiom. Comments explain why, in full sentences.
- Kotlin on Java 21, package `osrsps`, dependencies only through `gradle/libs.versions.toml`.
- Don't copy code from other private servers (Kronos, RSMod, Alter and so on); RSMod is a structural reference only.
- Flag dead code, a public API nothing calls, swallowed exceptions and logging that could print a key or password.
