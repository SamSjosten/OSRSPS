<#
Starts the test client with nobody at the screen and waits until it is logged in.

  .\test-runner\launch\start-client.ps1             # start it unless RuneLite already runs
  .\test-runner\launch\start-client.ps1 -Restart    # close RSProx and RuneLite first, e.g. after a plugin jar swap
  .\test-runner\launch\start-client.ps1 -Commit 3c1b194   # first wait until the server runs that commit

1. Waits for our server to answer on its game port and jav_config (after a redeploy the server takes a while). With
   -Commit, also until its /version (the commit of main deploy/auto-deploy.sh deployed) starts with that commit.
2. Closes RSProx's and RuneLite's JVMs when -Restart is given, or when RSProx is open without a RuneLite (nobody pressed
   Launch Session). With RuneLite already running and no -Restart, it only does step 4. RuneLite is asked to close its
   window first, so plugins see the client exit (the Tick Recorder writes its stop line); one still running after
   -CloseSeconds is killed. Then each RuneLite profile in -Profiles gets Exit warning set to Never, as the warning
   popup would keep the next close from finishing.
3. Runs the RSProx scheduled task, whose RSProx.cmd starts RSProx and presses Launch Session on the OSRSPS target
   (RsproxAutoLaunch.java). Waits for its line in C:\RSPS\.rsprox\logs\autolaunch.txt and for the RuneLite JVM.
4. Writes a one-step waitForLogin script into the tests folder until the test runner has passed it, so the plugin has
   started, auto login has logged in and the next scripts dropped there will run. Rewritten every 20 s, because the
   runner ignores scripts that were there before it started watching the folder; the script gives up after 15 s, so
   the copies never pile up. Stops when the client's log says auto login is off (a key pressed or a name typed on the
   login screen) or gave up (logged as its 5th try starts) and a check started after that line still fails: then
   nothing will log in by itself.

Each stage may take -TimeoutSeconds (600 by default). Exits with 0 when the client is logged in, 1 otherwise.
#>
param(
	[switch] $Restart,
	[string] $Commit,
	[string] $Server = '192.168.0.210',
	[int] $GamePort = 43594,
	[string] $Task = 'RSProx',
	[string] $Account = 'test',
	[string] $Tests = 'C:\RSPS\.rlcustom\tests',
	[string] $Results = 'C:\RSPS\.rlcustom\test-results',
	[string] $AutoLaunchStatus = 'C:\RSPS\.rsprox\logs\autolaunch.txt',
	[string] $ClientLog = 'C:\RSPS\.runelite\logs\client.log',
	# Only JVMs whose command line names this folder belong to the test client.
	[string] $ClientHome = 'C:\RSPS',
	[string] $Profiles = 'C:\RSPS\.rlcustom\profiles2',
	# How long RuneLite may take to close its window before it is killed.
	[int] $CloseSeconds = 20,
	# How long each stage (server, closing the old client, RSProx and RuneLite starting, login) may take.
	[int] $TimeoutSeconds = 600
)
$ErrorActionPreference = 'Stop'
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
function Reset-Deadline { $script:deadline = (Get-Date).AddSeconds($TimeoutSeconds) }

# Where to read the client's log from: the whole of it while a running client is kept (its auto login may have been
# turned off before this script started), its end once an old client is closed.
function Get-LogEnd { if (Test-Path -LiteralPath $ClientLog) { (Get-Item -LiteralPath $ClientLog).Length } else { 0 } }
$logStart = 0

# The test runner's lines saying auto login is off or gave up, logged by the plugin that runs now: only lines after
# the log's last RuneLite start or test runner start ("watching <folder>", which resets auto login) count.
function Get-AutoLoginLines
{
	if (-not (Test-Path -LiteralPath $ClientLog)) { return @() }
	$stream = [IO.File]::Open($ClientLog, 'Open', 'Read', 'ReadWrite')
	try
	{
		# A client started since then may have begun a new log.
		$from = if ($stream.Length -ge $logStart) { $logStart } else { 0 }
		[void] $stream.Seek($from, 'Begin')
		$text = (New-Object IO.StreamReader($stream)).ReadToEnd()
	}
	finally { $stream.Dispose() }
	$lines = $text -split "`r?`n"
	for ($i = $lines.Count - 1; $i -ge 0; $i--)
	{
		if ($lines[$i] -match 'RuneLite .* starting up|Test runner: watching ') { $lines = $lines[$i..($lines.Count - 1)]; break }
	}
	@($lines | Where-Object { $_ -match 'Test runner: .*(auto login is off|auto login gave up)' })
}

function Step([string] $text) { Write-Host ("[{0:HH:mm:ss}] {1}" -f (Get-Date), $text) }
function Fail([string] $text) { Step "FAILED: $text"; exit 1 }

function Test-Port([string] $hostName, [int] $port)
{
	$client = New-Object System.Net.Sockets.TcpClient
	try { $client.ConnectAsync($hostName, $port).Wait(2000) -and $client.Connected }
	catch { $false }
	finally { $client.Dispose() }
}

# RSProx's GUI (started by its launcher or by RsproxAutoLaunch.java) and the RuneLite it launches, told apart by their
# command lines. Only JVMs of the client in $ClientHome: another RuneLite on this PC is left alone.
function Get-ClientJvms
{
	$all = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' or Name = 'javaw.exe'" |
		Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($ClientHome, [StringComparison]::OrdinalIgnoreCase) -ge 0 })
	[pscustomobject]@{
		RSProx   = @($all | Where-Object { $_.CommandLine -match 'net\.rsprox\.gui\.ProxyToolGuiKt|RsproxAutoLaunch|rsprox-launcher\.jar' })
		RuneLite = @($all | Where-Object { $_.CommandLine -match 'net\.runelite\.|--socket_id=' })
	}
}

# 1. The server.
$javConfig = "http://${Server}:$($GamePort + 1)/jav_config.ws"
$versionUrl = "http://${Server}:$($GamePort + 1)/version"
Step ("Waiting for the server at ${Server}:$GamePort and $javConfig" + $(if ($Commit) { ", running $Commit" } else { '' }))
while ($true)
{
	$up = $false
	$running = ''
	if (Test-Port $Server $GamePort)
	{
		try
		{
			$up = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 $javConfig).StatusCode -eq 200
		}
		catch { $up = $false }
		if ($up -and $Commit)
		{
			# A server from before /version answers 404, which Invoke-WebRequest throws.
			try { $running = "$((Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 $versionUrl).Content)".Trim() }
			catch { $running = "no /version ($($_.Exception.Message))" }
			$up = $running.StartsWith($Commit, [StringComparison]::OrdinalIgnoreCase)
		}
	}
	if ($up) { break }
	if ((Get-Date) -gt $deadline)
	{
		if ($running) { Fail "after $TimeoutSeconds s the server runs $running, not $Commit" }
		Fail "the server did not answer within $TimeoutSeconds s"
	}
	Start-Sleep -Seconds 3
}
Step ('Server is up' + $(if ($Commit) { " and runs $running" } else { '' }))

# 2. What is running already.
$jvms = Get-ClientJvms
if ($jvms.RuneLite.Count -gt 0 -and -not $Restart)
{
	Step "RuneLite is already running (pid $($jvms.RuneLite.ProcessId -join ', ')); not restarting it"
}
else
{
	$stop = @($jvms.RSProx) + @($jvms.RuneLite)
	if ($stop.Count -gt 0)
	{
		Step "Closing RSProx/RuneLite (pid $($stop.ProcessId -join ', '))"
		# RuneLite first, by its window: a kill skips its shutdown, so plugins never hear the client exit.
		$asked = @(foreach ($p in $jvms.RuneLite)
		{
			$process = Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue
			if ($process -and $process.CloseMainWindow()) { $p.ProcessId }
		})
		$closeBy = (Get-Date).AddSeconds($CloseSeconds)
		while ($asked.Count -gt 0 -and (Get-Date) -lt $closeBy -and
			@((Get-ClientJvms).RuneLite | Where-Object { $asked -contains $_.ProcessId }).Count -gt 0)
		{
			Start-Sleep -Seconds 1
		}
		$left = @((Get-ClientJvms).RuneLite)
		foreach ($p in $left)
		{
			# CloseMainWindow finds windows only on this session's desktop: run from another session (ssh, a service),
			# it finds none.
			$why = if ($asked -contains $p.ProcessId) { "did not close in $CloseSeconds s (its Exit warning popup?)" }
				else { 'has no window on this desktop to close' }
			Step "RuneLite (pid $($p.ProcessId)) $why; killing it"
		}
		Reset-Deadline
		foreach ($p in @((Get-ClientJvms).RSProx) + $left) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
		while ((Get-ClientJvms).RSProx.Count + (Get-ClientJvms).RuneLite.Count -gt 0)
		{
			if ((Get-Date) -gt $deadline) { Fail 'RSProx/RuneLite did not close' }
			Start-Sleep -Seconds 1
		}
	}

	# No exit warning popup, so the next close by window finishes. RuneLite writes its profile while it runs, so only
	# now. Latin-1 maps every byte to one char and back, so the rest of the file is written back unchanged.
	$latin1 = [Text.Encoding]::GetEncoding(28591)
	foreach ($file in @(Get-ChildItem -LiteralPath $Profiles -Filter '*.properties' -ErrorAction SilentlyContinue))
	{
		$lines = @([IO.File]::ReadAllLines($file.FullName, $latin1))
		if ($lines -contains 'runelite.warningOnExit=NEVER') { continue }
		$lines = @($lines | Where-Object { $_ -notmatch '^runelite\.warningOnExit=' }) + 'runelite.warningOnExit=NEVER'
		[IO.File]::WriteAllLines($file.FullName, [string[]] $lines, $latin1)
		Step "Set RuneLite's Exit warning to Never in $($file.Name)"
	}

	# What a closed client logged doesn't count: the new one's auto login starts afresh.
	$logStart = Get-LogEnd

	# 3. RSProx, which launches RuneLite by itself. The task ignores a run while its last run still counts as running,
	# which lasts a moment after its JVM is gone.
	Reset-Deadline
	while ((schtasks /query /tn $Task /fo CSV /nh | ConvertFrom-Csv -Header Name, Next, Status).Status -eq 'Running')
	{
		if ((Get-Date) -gt $deadline) { Fail "scheduled task $Task still counts as running" }
		Start-Sleep -Seconds 1
	}
	$launchedAfter = Get-Date
	Step "Running scheduled task $Task"
	schtasks /run /tn $Task | Out-Null
	if ($LASTEXITCODE -ne 0) { Fail "schtasks /run /tn $Task exited with $LASTEXITCODE" }

	while ($true)
	{
		if ((Test-Path -LiteralPath $AutoLaunchStatus) -and (Get-Item -LiteralPath $AutoLaunchStatus).LastWriteTime -gt $launchedAfter)
		{
			$status = (Get-Content -LiteralPath $AutoLaunchStatus -Raw).Trim()
			if ($status.StartsWith('launched')) { Step "RSProx: $status"; break }
			Fail "RSProx: $status"
		}
		if ((Get-Date) -gt $deadline)
		{
			Fail "no new line in $AutoLaunchStatus within $TimeoutSeconds s (did RSProx.cmd fall back to its manual launcher?)"
		}
		Start-Sleep -Seconds 2
	}
	while ((Get-ClientJvms).RuneLite.Count -eq 0)
	{
		if ((Get-Date) -gt $deadline) { Fail 'RSProx pressed Launch Session but no RuneLite JVM started' }
		Start-Sleep -Seconds 2
	}
	Step 'RuneLite started'
}

# 4. Logged in, with the test runner watching the tests folder.
$id = 'start-client-login'
$script = Join-Path $Tests "$id.json"
$result = Join-Path $Results "$id.json"
$before = if (Test-Path -LiteralPath $result) { (Get-Item -LiteralPath $result).LastWriteTimeUtc } else { [DateTime]::MinValue }
$utf8 = New-Object System.Text.UTF8Encoding $false
$body = @{ name = 'The test client is logged in'; account = $Account; steps = @(@{ step = 'waitForLogin'; timeoutMs = 15000 }) } |
	ConvertTo-Json -Depth 4
New-Item -ItemType Directory -Force -Path $Tests | Out-Null
Reset-Deadline
Step "Waiting for $Account to be logged in and the test runner to run $id.json"
$written = [DateTime]::MinValue
$lastFailure = 'the test runner never ran it (is the plugin on?)'
$offSeen = $null
$logRead = [DateTime]::MinValue
while ($true)
{
	if ((Test-Path -LiteralPath $result) -and (Get-Item -LiteralPath $result).LastWriteTimeUtc -gt $before)
	{
		$r = Get-Content -LiteralPath $result -Raw -Encoding UTF8 | ConvertFrom-Json
		if ($r.result -eq 'pass') { Step "Logged in as $Account; the client is ready"; exit 0 }
		# Not logged in within the script's 15 s: the next rewrite tries again.
		$lastFailure = "$($r.result): $($r.failure)"
		$before = (Get-Item -LiteralPath $result).LastWriteTimeUtc
		# A whole 15 s check that began after the line was seen, and still no login: nothing more will try. (A client
		# logged in by hand since passes its check instead; a 5th try still under way gets its 15 s.)
		if ($offSeen -and $before -gt $offSeen.ToUniversalTime().AddSeconds(15))
		{
			Fail "auto login is off or gave up, so $Account won't log in by itself. Log in by hand or run with -Restart. Client log: $offLine"
		}
	}
	if (-not $offSeen -and ((Get-Date) - $logRead).TotalSeconds -ge 5)
	{
		$logRead = Get-Date
		$lines = @(Get-AutoLoginLines)
		if ($lines.Count -gt 0)
		{
			$offSeen = Get-Date
			$offLine = $lines[-1]
		}
	}
	if ((Get-Date) -gt $deadline) { Fail "$Account was not logged in within $TimeoutSeconds s (last check: $lastFailure)" }
	if (((Get-Date) - $written).TotalSeconds -ge 20)
	{
		# Written fresh each time, so the runner sees a change once it watches the folder.
		[IO.File]::WriteAllText($script, $body, $utf8)
		$written = Get-Date
	}
	Start-Sleep -Seconds 1
}
