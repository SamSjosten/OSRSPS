# TFA Helper (RuneLite plugin)

Room and run timers for The Fractured Archive, for Sam's own RuneLite. Not for the Plugin Hub: RuneLite isn't taking
new high-end PvM boss plugins.

## Our server only, for anything that reads a boss

Features that read a boss (its timers, counts, prayers or tiles) break Jagex's
[third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1) on the
live game, so each draws only while `OwnServer.connected` says the client is on our own server, by the host RuneLite
reports (`Client.getWorldHost()`): 192.168.0.210, 10.99.0.10 or loopback (RSProx). Any other host, a Jagex world among
them, turns them off. The room and run timers read only where you stand and work anywhere. The plugin never sends input
or adds menu actions.

## What it does

- **Timers** (top left, while you're in the raid): the area you are in and your time there, the run's time, and your
  time in each area so far. Areas are the nexus (the Guardian's hall is part of it), the six side rooms, Ocellus's
  arena and the reward vault.
- **Splits in chat**: when you leave the raid (a win, a wipe or a teleport out), one line in your chatbox with the run's
  time and each area's. Only you see it.

- **Ocellus final stand** (top right, our server only): the 60-tick loop's tick, counted from each time he rises, the
  next event and the ticks to it, and how many blasts this loop. The loop's events are the server's own (raid step 27).

It knows where you are from your own tile, mapped back to the raid's map in the cache
(`WorldPoint.fromLocalInstance`). It reads nothing about the bosses. Times are game ticks, 0.6 s each.

## Running it

- **On our server's test client**: download `tfa-helper-jar` from the latest `build` run on GitHub Actions, copy
  `tfa-helper-<version>.jar` to `C:\RSPS\.rlcustom\sideloaded-plugins\`, start `C:\RSPS\RSProx.cmd`, then
  `::raid` into a run.
- **From the repo** (PowerShell, Java 21): `.\gradlew -p tfa-helper run` starts RuneLite in developer mode with the plugin
  loaded. To log in with a Jagex account there, follow RuneLite's
  [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

`.\gradlew -p tfa-helper build` runs the tests and builds the jar.
