# Starting the test client with nobody at the screen

RSProx has no option that launches a client by itself: after it opens, someone picks the proxy target and clicks
**Launch Session**. These files do that click, so the test client on the test VM (rl-client) can be (re)started by a
script, for example after a test-runner jar swap.

- `RsproxAutoLaunch.java` runs RSProx's own GUI class in its JVM, waits for the window, selects the `OSRSPS` proxy
  target and the RuneLite client type, and calls the Launch Session button's click through Swing. It needs no screen
  position or focus. The outcome goes to `C:\RSPS\.rsprox\logs\autolaunch.txt` (`launched ...` or `failed ...`). If a
  later RSProx renames those controls it writes what it could not find and leaves RSProx open for a person to click.
  Written against RSProx 1.0.5.
- `RSProx.cmd` replaces `C:\RSPS\RSProx.cmd`. It starts RSProx through `RsproxAutoLaunch.java`, run by Java's
  single-file source launcher on the jars RSProx's launcher downloaded to `C:\RSPS\.rsprox\launcher\repository`.
  `RSProx.cmd --manual` starts RSProx's own launcher as before, which also updates RSProx, and leaves the click to you.
  That is also what it does until the launcher has downloaded RSProx once.
- `start-client.ps1` waits for our server to answer, closes RSProx and RuneLite (with `-Restart`, or when RSProx is
  open without a client), runs the `RSProx` scheduled task, waits for RuneLite, and then waits until the test runner
  has logged in and run a one-step `waitForLogin` script. After it exits with 0, scripts dropped into the tests folder
  run. It closes RuneLite by its window, so plugins see the client exit (the Tick Recorder's `stop` line with reason
  `clientExit`), and kills it only if it is still running 20 s later. So that the close needs no click, it sets
  RuneLite's Exit warning to Never in each profile under `C:\RSPS\.rlcustom\profiles2` while the client is closed.
  The first restart after installing this version still kills the client, because the warning is still on then.

## Installing on the test VM

```powershell
Copy-Item C:\RSPS\RSProx.cmd C:\RSPS\RSProx.cmd.bak
Copy-Item .\test-runner\launch\RSProx.cmd C:\RSPS\RSProx.cmd
Copy-Item .\test-runner\launch\RsproxAutoLaunch.java, .\test-runner\launch\start-client.ps1, `
  .\test-runner\launch\start-client-functions.ps1 C:\RSPS\bin\
```

The `RSProx` scheduled task already runs `C:\RSPS\RSProx.cmd`, so `schtasks /run /tn RSProx` now brings up a logged-in
client. The scheduled task matters: a client started from it runs on the signed-in desktop and keeps running when the
session that started it ends.

## Running

```powershell
powershell -ExecutionPolicy Bypass -File C:\RSPS\bin\start-client.ps1 -Restart   # after a jar swap
powershell -ExecutionPolicy Bypass -File C:\RSPS\bin\start-client.ps1            # make sure a client is up
```

It prints each stage with the time and exits with 1 and the reason when a stage doesn't finish within
`-TimeoutSeconds` (600 by default).

## The Odin's Gate launcher instead of RSProx

The launcher (`launcher/README.md`) is what friends will get instead of RSProx, and the VM runs it the same way:
through a scheduled task, so the client runs on the signed-in desktop and outlives the session that started it.

- `OdinsGate.cmd` is that task's command. It runs the installed launcher at `C:\RSPS\launcher\app\bin` with
  `--auto --dev --home C:\RSPS\launcher --status-file C:\RSPS\launcher\status.json --manifest
  C:\RSPS\launcher\manifest.json`, its console appended to `C:\RSPS\launcher\logs\console.log`. Arguments given to
  it go through to the launcher (`--server 192.168.0.210:44594` for the staging server). Everything the launcher makes
  is under `C:\RSPS\launcher`: `runelite\` (the pinned RuneLite), `logs\launcher.log` (the launcher's own lines)
  and `logs\game.log` (the game's output), both moved aside at 1 MB (`.1`, `.2`, `.3`), and `home\`, the game's own
  home folder, so RuneLite's folder is `C:\RSPS\launcher\home\.runelite` (sideloaded plugins, the test-runner's
  `tests` and `test-results`, `logs\client.log`, `profiles2`), not `C:\RSPS\.rlcustom`.
- `write-dev-manifest.ps1` writes `C:\RSPS\launcher\manifest.json` from the live server, until releases ship the signed
  manifest: the server's host and ports, the key it serves in `/proxy-targets.yaml`, and the pin table in the script
  (which RuneLite release serves revision 241). It warns when the server's `/status.json` names a revision the table
  doesn't pin. Run it again after a key change or a revision bump.
- `start-client.ps1 -Launcher` does the same stages through the launcher: runs the `OdinsGate` task instead of
  `RSProx`, waits for the status file to say `RUNNING` (and fails with the launcher's own reason when it says
  `FAILED`), and moves `-Tests`, `-Results`, `-ClientLog` and `-Profiles` under `C:\RSPS\launcher\home\.runelite`
  unless given. `-LauncherVersion 0.1.0` fails the run at once when the launcher that starts is another version, so an
  install that didn't take is caught before the game runs. `run-scripts.ps1` needs the same `-Tests` and `-Results`.
- `start-client-functions.ps1` holds the parts of `start-client.ps1` that need no VM (telling the JVMs apart, reading
  and waiting on the status file); `start-client-functions.tests.ps1` checks them, in CI's `launch-scripts` job with
  PowerShell 7 on Linux or by hand with `pwsh -NoProfile -File .\test-runner\launch\start-client-functions.tests.ps1`.

Installing it, once per launcher build (the `odinsgate-launcher` artifact of CI's `launcher` job is the installed
layout, `bin\` and `lib\`; nightly.link serves it without a login):

```powershell
Expand-Archive .\odinsgate-launcher.zip C:\RSPS\launcher\app -Force
Copy-Item .\test-runner\launch\OdinsGate.cmd, .\test-runner\launch\write-dev-manifest.ps1 C:\RSPS\bin\
powershell -ExecutionPolicy Bypass -File C:\RSPS\bin\write-dev-manifest.ps1
# The sideloaded plugins the launcher's RuneLite loads (developer mode), from RSProx's folder.
New-Item -ItemType Directory -Force C:\RSPS\launcher\home\.runelite\sideloaded-plugins | Out-Null
Copy-Item C:\RSPS\.rlcustom\sideloaded-plugins\*.jar C:\RSPS\launcher\home\.runelite\sideloaded-plugins\
# The scheduled task, a copy of RSProx's (same user, interactive, no run-time limit) with our command.
[xml] $task = (schtasks /query /tn RSProx /xml | Out-String).Trim()
$task.Task.Actions.Exec.Command = 'C:\RSPS\bin\OdinsGate.cmd'   # and clear its Arguments, if the task has any
$task.Save('C:\RSPS\launcher\OdinsGate.task.xml')
schtasks /create /tn OdinsGate /xml C:\RSPS\launcher\OdinsGate.task.xml /f
```

Then `powershell -ExecutionPolicy Bypass -File C:\RSPS\bin\start-client.ps1 -Restart -Launcher -LauncherVersion 0.1.0`
and, once it exits with 0, `run-scripts.ps1 -Tests C:\RSPS\launcher\home\.runelite\tests -Results
C:\RSPS\launcher\home\.runelite\test-results .\test-runner\examples`. The launcher's own lines are in
`C:\RSPS\launcher\logs\launcher.log`, the game's in `logs\game.log` next to it, and what the launcher's JVM printed to
the console (a crash, say) in `logs\console.log`.
