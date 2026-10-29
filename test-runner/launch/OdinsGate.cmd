@echo off
rem The Odin's Gate launcher on the test VM, run by the OdinsGate scheduled task (test-runner\launch\README.md), so the
rem client runs on the signed-in desktop and outlives the session that started it. Everything the launcher makes
rem lives under C:\RSPS\launcher: app\ (the installed layout CI builds: bin\ and lib\), manifest.json (written by
rem write-dev-manifest.ps1), status.json (what start-client.ps1 -Launcher waits on), runelite\ (the pinned RuneLite),
rem home\ (the game's own home folder, so .runelite\ with the sideloaded plugins, tests and logs is under it) and
rem logs\ (launcher.log from this file, game.log from the launcher).
rem Extra arguments go through to the launcher, for example --server 192.168.0.210:44594 for the staging server.
set "LAUNCHER=C:\RSPS\launcher"
if not exist "%LAUNCHER%\logs" mkdir "%LAUNCHER%\logs"
if not exist "%LAUNCHER%\app\bin\odinsgate-launcher.bat" (
	echo [%date% %time%] no launcher at %LAUNCHER%\app\bin\odinsgate-launcher.bat >> "%LAUNCHER%\logs\launcher.log"
	exit /b 66
)
echo [%date% %time%] OdinsGate.cmd: starting the launcher %* >> "%LAUNCHER%\logs\launcher.log"
call "%LAUNCHER%\app\bin\odinsgate-launcher.bat" --auto --dev --home "%LAUNCHER%" --status-file "%LAUNCHER%\status.json" --manifest "%LAUNCHER%\manifest.json" %* >> "%LAUNCHER%\logs\launcher.log" 2>&1
echo [%date% %time%] OdinsGate.cmd: the launcher exited with %ERRORLEVEL% >> "%LAUNCHER%\logs\launcher.log"
exit /b %ERRORLEVEL%
