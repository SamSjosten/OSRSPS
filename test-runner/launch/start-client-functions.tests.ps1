<#
Checks start-client-functions.ps1 without a VM: run by CI's launch-scripts job with pwsh on Linux, or by hand with
  pwsh -NoProfile -File .\test-runner\launch\start-client-functions.tests.ps1
Exits with 0 when every check passed, 1 otherwise, each failure printed.
#>
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'start-client-functions.ps1')

$script:failures = 0
function Check([string] $what, [bool] $ok)
{
	if ($ok) { Write-Host "  ok   $what" } else { Write-Host "  FAIL $what"; $script:failures++ }
}
function Throws([string] $what, [scriptblock] $block, [string] $containing)
{
	try { & $block | Out-Null; Check "$what (threw nothing)" $false }
	catch { Check "$what ($($_.Exception.Message))" ($_.Exception.Message -like "*$containing*") }
}
function Jvm([int] $id, [string] $commandLine) { [pscustomobject]@{ ProcessId = $id; CommandLine = $commandLine } }

Write-Host 'Select-ClientJvms'
$rsproxGui = Jvm 11 'java -cp C:\RSPS\.rsprox\launcher\repository\* C:\RSPS\bin\RsproxAutoLaunch.java'
$rsproxClient = Jvm 12 'java -cp C:\RSPS\.runelite\repository2\client-1.13.4.jar net.runelite.client.RuneLite --socket_id=4 --developer-mode'
$launcherMain = Jvm 13 '"C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe" -classpath "C:\RSPS\launcher\app\lib\odinsgate-launcher-0.1.0.jar;C:\RSPS\launcher\app\lib\gson-2.13.1.jar" net.odinsgate.launcher.Main --auto --dev --home C:\RSPS\launcher --status-file C:\RSPS\launcher\status.json --manifest C:\RSPS\launcher\manifest.json'
$launcherGame = Jvm 14 'C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe -Duser.home=C:\RSPS\launcher\home -Drunelite.http-service.url=http://192.168.0.210:43595/rl/ -ea:net.runelite.client.RuneLite -cp C:\RSPS\launcher\app\lib\odinsgate-launcher-0.1.0.jar net.odinsgate.launcher.Loader --jar C:\RSPS\launcher\runelite\1e634e64\client-1.13.4.jar --modulus ab --port 43594 --main net.runelite.client.RuneLite -- --developer-mode'
$stagingGame = Jvm 19 'C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe -Duser.home=C:\RSPS\launcher\home -Drunelite.http-service.url=http://192.168.0.210:44595/rl/ -ea:net.runelite.client.RuneLite -cp C:\RSPS\launcher\app\lib\odinsgate-launcher-0.1.0.jar net.odinsgate.launcher.Loader --jar C:\RSPS\launcher\runelite\1e634e64\client-1.13.4.jar --modulus ab --port 44594 --main net.runelite.client.RuneLite -- --developer-mode'
$otherRuneLite = Jvm 15 'C:\Users\admin\AppData\Local\RuneLite\jre\bin\java.exe -cp C:\Users\admin\.runelite\repository2\client-1.13.4.jar net.runelite.client.RuneLite'
$gradle = Jvm 16 'java -cp C:\RSPS\OSRSPS\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build'
$noCommandLine = Jvm 17 $null
$jvms = Select-ClientJvms @($rsproxGui, $rsproxClient, $launcherMain, $launcherGame, $otherRuneLite, $gradle, $noCommandLine) 'C:\RSPS'
Check 'RSProx is its GUI only' ((@($jvms.RSProx.ProcessId) -join ',') -eq '11')
Check "the launcher is its Main only, not the game it started" ((@($jvms.Launcher.ProcessId) -join ',') -eq '13')
Check "RuneLite is RSProx's client and the launcher's Loader child, not the launcher's Main" ((@($jvms.RuneLite.ProcessId) -join ',') -eq '12,14')
$none = Select-ClientJvms @($otherRuneLite, $gradle) 'C:\RSPS'
Check 'a RuneLite outside the client home and a Gradle daemon are nobody' ($none.RSProx.Count + $none.Launcher.Count + $none.RuneLite.Count -eq 0)
$empty = Select-ClientJvms @() 'C:\RSPS'
Check 'no processes at all gives three empty lists' ($empty.RuneLite.Count -eq 0 -and $empty.Launcher.Count -eq 0)
$lower = Select-ClientJvms @((Jvm 18 'java -cp c:\rsps\launcher\app\lib\x.jar net.odinsgate.launcher.Main')) 'C:\RSPS'
Check 'the client home is matched whatever its case' ($lower.Launcher.Count -eq 1)

Write-Host 'Get-ClientServer'
Check "the launcher's client on the live server" ((Get-ClientServer $launcherGame) -eq '192.168.0.210:43594')
Check "the launcher's client on the staging server" ((Get-ClientServer $stagingGame) -eq '192.168.0.210:44594')
Check "RSProx's client is nobody's to tell" ((Get-ClientServer $rsproxClient) -eq '')
Check 'no command line is nothing' ((Get-ClientServer $noCommandLine) -eq '')
$noPort = Jvm 20 'java -Drunelite.http-service.url=http://192.168.0.210:43595/rl/ net.odinsgate.launcher.Loader --main x'
Check 'the URL without the loader port is nothing' ((Get-ClientServer $noPort) -eq '')

Write-Host 'Get-LauncherServerArgs'
Check 'the live server needs no arguments' ((Get-LauncherServerArgs '192.168.0.210' 43594 '192.168.0.210' 43594) -eq '')
$staging = Get-LauncherServerArgs '192.168.0.210' 44594 '192.168.0.210' 43594
Check 'the staging port is a --server' ($staging -eq '--server 192.168.0.210:44594')
$elsewhere = Get-LauncherServerArgs '10.99.0.10' 43594 '192.168.0.210' 43594
Check 'another host on the live port is a --server' ($elsewhere -eq '--server 10.99.0.10:43594')

Write-Host 'Read-LauncherStatus and Test-LauncherStatus'
$dir = Join-Path ([IO.Path]::GetTempPath()) ("start-client-tests-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $dir | Out-Null
$file = Join-Path $dir 'status.json'
function Write-Status([string] $json) { [IO.File]::WriteAllText($file, $json, (New-Object Text.UTF8Encoding $false)) }
$running = '{ "version": "0.1.0", "state": "RUNNING", "detail": "game process 4321, its log in C:\\RSPS\\launcher\\logs\\game.log", "revision": 241, "gamePid": 4321, "at": "2026-10-29T17:00:00Z" }'
try
{
	$since = (Get-Date).AddMinutes(-1)
	Check 'no file reads as null' ($null -eq (Read-LauncherStatus $file))
	Check 'no file is wait' ((Test-LauncherStatus $file $since '') -eq 'wait')
	Write-Status '{ "version": "0.1.0", "state": "STA'
	Check 'half a file reads as null' ($null -eq (Read-LauncherStatus $file))
	Check 'half a file is wait' ((Test-LauncherStatus $file $since '') -eq 'wait')
	Write-Status '{ "state": "RUNNING", "at": "2026-10-29T17:00:00Z" }'
	Check 'a file without a version is wait, not a crash' ((Test-LauncherStatus $file $since '') -eq 'wait')
	foreach ($state in 'STARTING', 'CHECKING', 'UPDATING', 'DOWNLOADING', 'LAUNCHING')
	{
		Write-Status "{ `"version`": `"0.1.0`", `"state`": `"$state`", `"detail`": `"x`", `"at`": `"2026-10-29T17:00:00Z`" }"
		Check "$state is wait" ((Test-LauncherStatus $file $since '0.1.0') -eq 'wait')
	}
	Write-Status $running
	$status = Read-LauncherStatus $file
	Check 'the status reads whole' ($status.state -eq 'RUNNING' -and $status.gamePid -eq 4321 -and $status.revision -eq 241)
	$got = Test-LauncherStatus $file $since ''
	Check 'RUNNING is the status, no version asked' ($got.state -eq 'RUNNING' -and $got.gamePid -eq 4321)
	$got = Test-LauncherStatus $file $since '0.1.0'
	Check 'RUNNING with the expected version is the status' ($got.version -eq '0.1.0')
	Throws 'RUNNING with another version is refused' { Test-LauncherStatus $file $since '0.2.0' } 'version 0.1.0, not 0.2.0'
	$later = (Get-Item -LiteralPath $file).LastWriteTime.AddSeconds(1)
	Check 'a file older than the launch is wait' ((Test-LauncherStatus $file $later '') -eq 'wait')
	Write-Status '{ "version": "0.1.0", "state": "FAILED", "detail": "the server is offline: the game port doesn''t answer", "at": "2026-10-29T17:00:00Z" }'
	Throws 'FAILED throws the detail' { Test-LauncherStatus $file $since '' } "offline: the game port doesn't answer"
	Write-Status '{ "version": "0.1.0", "state": "EXITED", "detail": "the game exited with 0", "revision": 241, "gamePid": 4321, "at": "2026-10-29T17:00:00Z" }'
	Throws 'EXITED throws' { Test-LauncherStatus $file $since '' } 'exited already: the game exited with 0'
	Throws 'the version is checked before the state' { Test-LauncherStatus $file $since '0.2.0' } 'not 0.2.0'

	Write-Host 'Set-LaunchArgsFile'
	$argsFile = Join-Path (Join-Path $dir 'launcher') 'launch-args.txt'
	Set-LaunchArgsFile $argsFile ''
	Check 'no arguments and no file is nothing to do' (-not (Test-Path -LiteralPath $argsFile))
	Set-LaunchArgsFile $argsFile '--server 192.168.0.210:44594'
	$bytes = [IO.File]::ReadAllBytes($argsFile)
	$text = [IO.File]::ReadAllText($argsFile)
	Check 'the arguments are the one line, made with its folder' ($text -eq "--server 192.168.0.210:44594`r`n")
	Check 'without a byte-order mark' ($bytes[0] -eq [byte][char]'-')
	Set-LaunchArgsFile $argsFile '--server 192.168.0.210:43594'
	Check 'a new line replaces the old' ([IO.File]::ReadAllText($argsFile) -eq "--server 192.168.0.210:43594`r`n")
	Set-LaunchArgsFile $argsFile ''
	Check 'no arguments removes the file' (-not (Test-Path -LiteralPath $argsFile))

	Write-Host 'Wait-LauncherStatus'
	Write-Status $running
	$got = Wait-LauncherStatus $file $since '0.1.0' (Get-Date).AddSeconds(30) 1
	Check 'returns at once with a running game' ($got.gamePid -eq 4321)
	Write-Status '{ "version": "0.1.0", "state": "LAUNCHING", "detail": "3 jars, developer mode", "revision": 241, "at": "2026-10-29T17:00:00Z" }'
	Throws 'a deadline that passed names the last state' { Wait-LauncherStatus $file $since '' (Get-Date).AddSeconds(-1) 1 } 'last: LAUNCHING: 3 jars'
	Remove-Item -LiteralPath $file
	Throws 'a deadline that passed with no file says so' { Wait-LauncherStatus $file $since '' (Get-Date).AddSeconds(-1) 1 } 'no status written since'
	Write-Status '{ "version": "0.1.0", "state": "FAILED", "detail": "RuneLite 1.13.4 could not be fetched: 503", "at": "2026-10-29T17:00:00Z" }'
	Throws 'FAILED ends the wait before the deadline' { Wait-LauncherStatus $file $since '' (Get-Date).AddSeconds(30) 1 } 'could not be fetched: 503'
}
finally { Remove-Item -LiteralPath $dir -Recurse -Force }

if ($script:failures -gt 0) { Write-Host "$($script:failures) check(s) failed"; exit 1 }
Write-Host 'all checks passed'
exit 0
