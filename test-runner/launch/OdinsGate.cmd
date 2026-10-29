@echo off
rem The Odin's Gate launcher on the test VM, run by the OdinsGate scheduled task (test-runner\launch\README.md), so the
rem client runs on the signed-in desktop and outlives the session that started it. Everything the launcher makes
rem lives under C:\RSPS\launcher: app\ (the installed layout CI builds: bin\ and lib\), manifest.json (written by
rem write-dev-manifest.ps1), status.json (what start-client.ps1 -Launcher waits on), runelite\ (the pinned RuneLite),
rem home\ (the game's own home folder, so .runelite\ with the sideloaded plugins, tests and logs is under it) and
rem logs\ (launcher.log and game.log from the launcher, both rotated by it; console.log from this file: its own two
rem lines and whatever the launcher's JVM prints before or besides its log, such as a crash).
rem Extra arguments go through to the launcher, for example --server 192.168.0.210:44594 for the staging server; the
rem scheduled task's own arguments are fixed, so start-client.ps1 -Launcher writes the ones that change (that --server,
rem for -Server and -GamePort other than the live server's) as the one line of launch-args.txt, read here, and removes
rem the file for the live server.
set "LAUNCHER=C:\RSPS\launcher"
if not exist "%LAUNCHER%\logs" mkdir "%LAUNCHER%\logs"
if not exist "%LAUNCHER%\app\bin\odinsgate-launcher.bat" (
	echo [%date% %time%] no launcher at %LAUNCHER%\app\bin\odinsgate-launcher.bat >> "%LAUNCHER%\logs\console.log"
	exit /b 66
)
set "EXTRA="
if exist "%LAUNCHER%\launch-args.txt" set /p EXTRA=<"%LAUNCHER%\launch-args.txt"
echo [%date% %time%] OdinsGate.cmd: starting the launcher %EXTRA% %* >> "%LAUNCHER%\logs\console.log"
call "%LAUNCHER%\app\bin\odinsgate-launcher.bat" --auto --dev --home "%LAUNCHER%" --status-file "%LAUNCHER%\status.json" --manifest "%LAUNCHER%\manifest.json" %EXTRA% %* >> "%LAUNCHER%\logs\console.log" 2>&1
echo [%date% %time%] OdinsGate.cmd: the launcher exited with %ERRORLEVEL% >> "%LAUNCHER%\logs\console.log"
exit /b %ERRORLEVEL%
