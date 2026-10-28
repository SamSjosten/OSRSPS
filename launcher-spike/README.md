# Launcher spike

A throwaway check before the custom launcher is built, in a draft pull request that is never merged. It answers one
question: can the pinned RuneLite be pointed at our server by patching its classes in memory as they load, with no
proxy and no jar rewritten on disk? If it can, the launcher downloads RuneLite from RuneLite itself on the friend's PC,
starts it the way RuneLite's own launcher does on a Mac, and nothing of RuneLite's or Jagex's is ever hosted by us.

The patched client is only ever pointed at our server, never at Jagex's.

## Files

- `SpikeLoader.java`: one file, ASM 9.11 its only library.
  - `scan` reads RuneLite's real jars and reports every patch point it needs, without starting anything.
  - `run` scans the same way, refuses to start when a patch point is missing, and then starts RuneLite through a
    patching class loader (parent: the platform class loader, so RuneLite can't see the spike's own classes). It
    logs every place Java is asked to connect to as `[spike] connect <scheme>://<host>:<port><path>`: plain sockets,
    URL connections and OkHttp all ask the default `ProxySelector` first, which SpikeLoader replaces.
- `SelfTest.java`: builds a fake RuneLite and a fake injected client with javac, then runs SpikeLoader on them in a
  JVM of its own (37 checks, loopback only). `java SelfTest.java launcher-spike <path to the two ASM jars>`.
- `spike.ps1`: the run on the test VM, below.

## What gets patched

The same points RSProx patches (its `RuneLitePatcher.kt` and `ClientLoaderPatcher.kt`), done with ASM as each class
loads:

| Where | What | Why |
| --- | --- | --- |
| injected client, the one class that also holds the exponent `10001` | the RSA modulus (ours may not be longer) | the login block is encrypted to our server's key |
| injected client | `"127.0.0.1"` where `String.endsWith` takes it, made `""` | the client's host check, so any host passes |
| `ClientLoader.updateVanilla`, when the jar has it | returns at once | not in RuneLite's public source; it downloads a gamepack |
| `ClientLoader.downloadConfig` | `".jagex.com"`, when the jar has it, made `""` | the released jar checks the jav_config's host |
| `RuneLite` | `".runelite"` made `".rlcustom"` (test VM only) | the RSProx client's folder, with the test runner and its profile |

And without patching anything, through RuneLite's own settings:

- `-Drunelite.http-service.url` and the session, static, ws and plugin hub URLs point at a stub on 127.0.0.1. Its
  `worlds.js` answers with our world 301; every other call is logged and answered 404.
- `-Drunelite.rtconf` names a local runtime config (`{}`), so RuneLite's remote config, with its outdated-client
  lists, never applies. RuneLite 1.13.4 reads every field of it null-safely.
- `--jav_config` is our server's, `--noupdate` skips the launcher update, `--disable-telemetry`.
- No `runelite.launcher.version` property, so developer mode is RuneLite's own `--developer-mode` with assertions on
  in its `RuneLite` class (as RSProx gets it by patching).

The output names jars, classes, methods, counts and key lengths, never class bytes or the client's own constants.

## On the test VM

```powershell
powershell -ExecutionPolicy Bypass -File .\launcher-spike\spike.ps1               # everything, about 25 minutes
powershell -ExecutionPolicy Bypass -File .\launcher-spike\spike.ps1 -Mode scan    # download, check and scan only
```

`spike.ps1` downloads the pinned RuneLite's Windows x64 jars from RuneLite's repository and checks each against the
bootstrap's SHA-256, compiles SpikeLoader, and scans. Then it closes RSProx and starts the spike's client from a
scheduled task (on the signed-in desktop, like RSProx's):

1. Developer mode, with the test runner: `test` logs in by itself, five examples run (castle doors, Hans, equipment,
   ground items, combat), then 20 logouts and logins.
2. Without developer mode, as a friend runs it: the server's `/status` must count a new cache (JS5) connection, and a
   screenshot shows the login screen.

During both runs only our server's two ports and 127.0.0.1 may appear among the places SpikeLoader logged a connection
to, and among the TCP connections Windows lists for the client (sampled twice a second, for anything that bypasses
Java's `ProxySelector`). It also records the Jagex, RuneLite and Wiki names Windows looks up, and checks which planned
patches were made. A gamepack download fails the run: a jar in the connection log, a gamepack or vanilla client line in
RuneLite's log, or a jar written under the client's home while it ran. It also
checks that the jars of RSProx's older pins (revisions 223 and 236) are still on RuneLite's repository, which the
launcher's pin table depends on. At the end it starts RSProx's client again (`start-client.ps1 -Restart`).

Results go to `C:\RSPS\spike\results\<UTC time>\`: `summary.txt` (one line per check: PASS, FAIL, WARN or INFO),
`summary.json`, and each step's output. It exits with 0 when no check failed.

## Not covered here

- Mac. Nothing here is Windows-only except `spike.ps1`; RuneLite's own launcher starts it the same way there.
- The launcher itself: its download and update manifest, signing, the jlink runtime, the installer.
- Blocking Jagex hosts inside the client. RuneLite's own block list (`runelite.jagex.domainblock`) only applies
  outside Jagex's live environment and can't be set from outside; for now the connection log and watch are the check.
