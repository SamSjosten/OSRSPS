# TFA Helper (RuneLite plugin)

Room and run timers for The Fractured Archive, for Sam's own RuneLite. Not for the Plugin Hub: RuneLite isn't taking
new high-end PvM boss plugins.

## The rules it keeps

Every feature stays inside Jagex's [third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1),
written up for this plugin in the project's rules doc. In short, it may show what you can already see, what you mark
yourself and how long you've taken; it never predicts, counts or announces what a boss does (attack timing, projectiles,
prayers, phases, mechanics starting or ending), never sends input or adds menu actions, and never places markers by
itself. Check a new feature against that list before building it.

## What it does

- **Timers** (top left, while you're in the raid): the area you are in and your time there, the run's time, and your
  time in each area so far. Areas are the nexus (the Guardian's hall is part of it), the six side rooms, Ocellus's
  arena and the reward vault.
- **Splits in chat**: when you leave the raid (a win, a wipe or a teleport out), one line in your chatbox with the run's
  time and each area's. Only you see it.

It knows where you are from your own tile, mapped back to the raid's map in the cache
(`WorldPoint.fromLocalInstance`). It reads nothing about the bosses. Times are game ticks, 0.6 s each.

## Running it

- **On our server's test client**: fetch `tfa-helper-jar` with `powershell -ExecutionPolicy Bypass -File C:\RSPS\bin\repo-fetch.ps1 -Artifact tfa-helper-jar -OutFile C:\RSPS\downloads\tfa-helper-jar.zip`
  (it takes only this repository's own `main` builds; an artifact picked by hand from the Actions page could be a fork pull request's, and developer mode runs whatever jar is in `sideloaded-plugins`, exploit review R54C-1), unzip it, copy
  `tfa-helper-<version>.jar` to `C:\RSPS\.rlcustom\sideloaded-plugins\`, start `C:\RSPS\RSProx.cmd`, then
  `::raid` into a run.
- **From the repo** (PowerShell, Java 21): `.\gradlew -p tfa-helper run` starts RuneLite in developer mode with the plugin
  loaded. To log in with a Jagex account there, follow RuneLite's
  [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

`.\gradlew -p tfa-helper build` runs the tests and builds the jar.
