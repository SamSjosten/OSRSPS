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
  run.

## Installing on the test VM

```powershell
Copy-Item C:\RSPS\RSProx.cmd C:\RSPS\RSProx.cmd.bak
Copy-Item .\test-runner\launch\RSProx.cmd C:\RSPS\RSProx.cmd
Copy-Item .\test-runner\launch\RsproxAutoLaunch.java, .\test-runner\launch\start-client.ps1 C:\RSPS\bin\
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
