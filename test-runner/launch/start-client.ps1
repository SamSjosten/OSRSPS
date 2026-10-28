<#
Starts the test client with nobody at the screen and waits until it is logged in.

  .\test-runner\launch\start-client.ps1             # start it unless RuneLite already runs
  .\test-runner\launch\start-client.ps1 -Restart    # close RSProx and RuneLite first, e.g. after a plugin jar swap

1. Waits for our server to answer on its game port and jav_config (after a redeploy the server takes a while).
2. Closes RSProx's and RuneLite's JVMs when -Restart is given, or when RSProx is open without a RuneLite (nobody pressed
   Launch Session). With RuneLite already running and no -Restart, it only does step 4.
3. Runs the RSProx scheduled task, whose RSProx.cmd starts RSProx and presses Launch Session on the OSRSPS target
   (RsproxAutoLaunch.java). Waits for its line in C:\RSPS\.rsprox\logs\autolaunch.txt and for the RuneLite JVM.
4. Writes a one-step waitForLogin script into the tests folder until the test runner has run it, so the plugin has
   started, auto login has logged in and the next scripts dropped there will run. Rewritten every 20 s, because the
   runner ignores scripts that were there before it started watching the folder.

Exits with 0 when the client is logged in, 1 otherwise.
#>
param(
	[switch] $Restart,
	[string] $Server = '192.168.0.210',
	[int] $GamePort = 43594,
	[string] $Task = 'RSProx',
	[string] $Account = 'test',
	[string] $Tests = 'C:\RSPS\.rlcustom\tests',
	[string] $Results = 'C:\RSPS\.rlcustom\test-results',
	[string] $AutoLaunchStatus = 'C:\RSPS\.rsprox\logs\autolaunch.txt',
	[int] $TimeoutSeconds = 600
)
$ErrorActionPreference = 'Stop'
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

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
# command lines. Only JVMs that belong to the C:\RSPS client setup.
function Get-ClientJvms
{
	$all = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' or Name = 'javaw.exe'"
	[pscustomobject]@{
		RSProx   = @($all | Where-Object { $_.CommandLine -match 'net\.rsprox\.gui\.ProxyToolGuiKt|RsproxAutoLaunch|rsprox-launcher\.jar' })
		RuneLite = @($all | Where-Object { $_.CommandLine -match 'net\.runelite\.|--socket_id=' })
	}
}

# 1. The server.
$javConfig = "http://${Server}:$($GamePort + 1)/jav_config.ws"
Step "Waiting for the server at ${Server}:$GamePort and $javConfig"
while ($true)
{
	$up = $false
	if (Test-Port $Server $GamePort)
	{
		try { $up = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 $javConfig).StatusCode -eq 200 } catch { }
	}
	if ($up) { break }
	if ((Get-Date) -gt $deadline) { Fail "the server did not answer within $TimeoutSeconds s" }
	Start-Sleep -Seconds 3
}
Step 'Server is up'

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
		foreach ($p in $stop) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
		while ((Get-ClientJvms).RSProx.Count + (Get-ClientJvms).RuneLite.Count -gt 0)
		{
			if ((Get-Date) -gt $deadline) { Fail 'RSProx/RuneLite did not close' }
			Start-Sleep -Seconds 1
		}
	}

	# 3. RSProx, which launches RuneLite by itself.
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
$body = @{ name = 'The test client is logged in'; account = $Account; steps = @(@{ step = 'waitForLogin' }) } | ConvertTo-Json -Depth 4
New-Item -ItemType Directory -Force -Path $Tests | Out-Null
Step "Waiting for $Account to be logged in and the test runner to run $id.json"
$written = [DateTime]::MinValue
while ($true)
{
	if ((Test-Path -LiteralPath $result) -and (Get-Item -LiteralPath $result).LastWriteTimeUtc -gt $before)
	{
		$r = Get-Content -LiteralPath $result -Raw -Encoding UTF8 | ConvertFrom-Json
		if ($r.result -eq 'pass') { Step "Logged in as $Account; the client is ready"; exit 0 }
		# A finished run that did not pass (e.g. auto login gave up): try once more by rewriting the script.
		Step "The login check ended with $($r.result): $($r.failure)"
		$before = (Get-Item -LiteralPath $result).LastWriteTimeUtc
		$written = [DateTime]::MinValue
	}
	if ((Get-Date) -gt $deadline) { Fail "$Account was not logged in within $TimeoutSeconds s" }
	if (((Get-Date) - $written).TotalSeconds -ge 20)
	{
		# Written fresh each time, so the runner sees a change once it watches the folder.
		[IO.File]::WriteAllText($script, $body, $utf8)
		$written = Get-Date
	}
	Start-Sleep -Seconds 1
}
