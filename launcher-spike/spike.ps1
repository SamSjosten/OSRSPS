<#
Spike for the custom launcher (PR 2 of the plan, a draft that is never merged), run on the test VM. It starts the pinned
RuneLite through SpikeLoader, patched in memory and pointed straight at our server with no RSProx, and checks that it
plays like the RSProx client: test logs in, the test runner's examples pass, it logs out and back in 20 times, it also
starts as a friend would (no developer mode), and it connects to nothing but our server and its own stub on 127.0.0.1.
At the end RSProx's client is started again.

  powershell -ExecutionPolicy Bypass -File .\launcher-spike\spike.ps1               # everything
  powershell -ExecutionPolicy Bypass -File .\launcher-spike\spike.ps1 -Mode scan    # download, check and scan only

Modes, in the order "all" runs them:
- setup: RuneLite's jars for Windows x64, from the bootstrap.json that src/main/resources/runelite-pins.json pins, and
  ASM from Maven Central, each checked against its SHA-256; SpikeLoader compiled; our modulus from proxy-targets.yaml.
- scan: SpikeLoader's scan of the real jars (RuneLite isn't started).
- dev: the client in developer mode with the test runner: the login, the examples, the login loop.
- normal: the client without developer mode, as a friend would run it: its cache (JS5) connection and a screenshot.
- head: whether the jars of RSProx's older pins (revisions 223 and 236) are still on RuneLite's repository.
- restore: the spike's client closed and its scheduled task removed, RSProx's client started and logged in.
dev and normal run setup and scan first; "all" ends with restore even when a step failed. After dev or normal on their
own, run restore.

The client runs from a scheduled task, as RSProx's does, so it is on the signed-in desktop whoever starts this script.
The jars are downloaded to C:\RSPS\spike\repo, from RuneLite's own repository; nothing of RuneLite's is in git.
Results: C:\RSPS\spike\results\<UTC time>\, summary.txt and summary.json last. Exits with 0 when no check failed.
#>
param(
	[ValidateSet('all', 'setup', 'scan', 'dev', 'normal', 'head', 'restore')]
	[string] $Mode = 'all',
	# A JDK 21: SpikeLoader is compiled with its javac and the client runs on its javaw.
	[string] $Java = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe',
	[string] $Server = '192.168.0.210',
	[int] $GamePort = 43594,
	[string] $Work = 'C:\RSPS\spike',
	# RSProx starts its RuneLite with user.home here (so RuneLite's folder is .rlcustom in it); only JVMs whose command
	# line names this folder are the test client's.
	[string] $ClientHome = 'C:\RSPS',
	[string] $Results = 'C:\RSPS\.rlcustom\test-results',
	[string] $ClientLog = 'C:\RSPS\.runelite\logs\client.log',
	[string[]] $Examples = @('lumbridge-castle-doors.json', 'hans-demo.json', 'equipment-wield.json', 'ground-drop-take.json',
		'combat-attack-man.json'),
	[int] $Logins = 20,
	# How long the client runs without developer mode.
	[int] $NormalSeconds = 120,
	[string] $TaskName = 'OSRSPS launcher spike'
)
$ErrorActionPreference = 'Stop'
# Invoke-WebRequest's progress bar slows its downloads down a lot.
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

if (-not (Test-Path -LiteralPath $Java))
{
	# Another JDK 21 update than the one this was written against.
	$found = @(Get-ChildItem -LiteralPath 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
		Sort-Object Name | ForEach-Object { Join-Path $_.FullName 'bin\java.exe' } | Where-Object { Test-Path -LiteralPath $_ })
	if ($found.Count -gt 0) { $Java = $found[-1] }
}
$repoRoot = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding $false
$started = (Get-Date).ToUniversalTime()
$runDir = Join-Path $Work ('results\' + $started.ToString('yyyyMMdd-HHmmss') + 'Z')
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$javaw = Join-Path (Split-Path -Parent $Java) 'javaw.exe'
$javac = Join-Path (Split-Path -Parent $Java) 'javac.exe'
$javConfig = "http://${Server}:$($GamePort + 1)/jav_config.ws"
$classpathFile = Join-Path $Work 'classpath.txt'
$rtconf = Join-Path $Work 'rtconf.json'
$classes = Join-Path $runDir 'classes'
$asmJars = @(
	@{ Name = 'asm-9.11.jar'; Size = 131056; Sha256 = '5fe3b469b8646e5bc53a897840952d63b712d4923d2a7a26aebc585f1fec7676' },
	@{ Name = 'asm-tree-9.11.jar'; Size = 51954; Sha256 = 'da8ad24d0bf7efb0674a906f59a7d441692b641718371f3bce6762b191a169fd' }
)
$asmPath = (@($asmJars | ForEach-Object { Join-Path $Work "asm\$($_.Name)" })) -join ';'
# RSProx's pins for older revisions (ProxyService.kt in blurite/rsprox), for the head check.
$oldPins = [ordered]@{ '223' = 'b7c08f2a08be75cfbdb3a11b870b5a82c480267f'; '236' = '0357970b30978614936a80b725a27bdc538bef24' }

# Set as the steps go.
$bootstrap = $null
$modulus = $null
$scanPassed = $false
$clientPid = $null
$userHome = $ClientHome
$profileName = $null
$rsproxChecked = $false
$dnsFlushed = $false
$checks = New-Object System.Collections.ArrayList
$exitCode = 1

function Step([string] $text)
{
	$line = '[{0:HH:mm:ss}] {1}' -f (Get-Date), $text
	Write-Host $line
	[IO.File]::AppendAllText((Join-Path $runDir 'spike.log'), $line + "`r`n", $utf8)
}

# result: pass, fail (the spike's answer is no until it is fixed), warn (worth a look) or info.
function Check([string] $name, [string] $result, [string] $detail)
{
	[void] $checks.Add([pscustomobject]@{ check = $name; result = $result; detail = $detail })
	Step ('{0,-4} {1}: {2}' -f $result.ToUpperInvariant(), $name, $detail)
}

function Get-Sha256([string] $file) { (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLowerInvariant() }

# Downloads to $file unless it is already there with that hash; size 0 means any size.
function Get-Download([string] $url, [string] $file, [string] $sha256, [long] $size)
{
	if ((Test-Path -LiteralPath $file) -and (Get-Sha256 $file) -eq $sha256) { return 'already there' }
	$last = ''
	for ($try = 1; $try -le 4; $try++)
	{
		try
		{
			Invoke-WebRequest -UseBasicParsing -TimeoutSec 120 -Uri $url -OutFile "$file.part"
			$got = Get-Sha256 "$file.part"
			$length = (Get-Item -LiteralPath "$file.part").Length
			if ($got -ne $sha256 -or ($size -gt 0 -and $length -ne $size))
			{
				throw "got $length bytes with SHA-256 $got, expected $size bytes with $sha256"
			}
			Move-Item -Force -LiteralPath "$file.part" -Destination $file
			return 'downloaded'
		}
		catch
		{
			$last = $_.Exception.Message
			Start-Sleep -Seconds ([int] [math]::Pow(2, $try))
		}
	}
	throw "could not download ${url}: $last"
}

function Get-Text([string] $url)
{
	$response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 30 -Uri $url
	if ($response.Content -is [byte[]]) { $utf8.GetString($response.Content) } else { [string] $response.Content }
}

# Runs a program to its end; its output (stdout and stderr) goes to $logFile.
function Invoke-Native([string] $exe, [string[]] $arguments, [string] $logFile)
{
	# Windows PowerShell turns a native program's stderr lines into errors, which 'Stop' would throw on.
	$old = $ErrorActionPreference
	$ErrorActionPreference = 'Continue'
	try { $output = @(& $exe @arguments 2>&1 | ForEach-Object { "$_" }) }
	finally { $ErrorActionPreference = $old }
	$code = $LASTEXITCODE
	[IO.File]::WriteAllLines($logFile, [string[]] $output, $utf8)
	[pscustomobject]@{ Code = $code; Output = $output; Last = $(if ($output.Count -gt 0) { $output[-1] } else { '' }) }
}

function Invoke-Script([string] $script, [string[]] $arguments, [string] $logFile)
{
	Invoke-Native 'powershell.exe' (@('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $script) + $arguments) $logFile
}

# Reads a file another process still writes, from byte $from on.
function Read-Shared([string] $file, [long] $from = 0)
{
	if (-not (Test-Path -LiteralPath $file)) { return '' }
	$stream = [IO.File]::Open($file, 'Open', 'Read', 'ReadWrite')
	try
	{
		if ($from -gt $stream.Length) { $from = 0 }
		[void] $stream.Seek($from, 'Begin')
		(New-Object IO.StreamReader($stream, $utf8)).ReadToEnd()
	}
	finally { $stream.Dispose() }
}

function Get-LogEnd { if (Test-Path -LiteralPath $ClientLog) { (Get-Item -LiteralPath $ClientLog).Length } else { 0 } }

# Quoted so that a program's command line splits back into the same arguments (CreateProcess's rules).
function Format-Arg([string] $value)
{
	if ($value -ne '' -and $value -notmatch '[\s"]') { return $value }
	'"' + ($value -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
}

# The artifacts RuneLite's launcher takes on Windows x64: those for every platform, and those for Windows without an
# architecture (the bootstrap gives x86 and aarch64 builds an arch; x64 has none).
function Select-WindowsX64($fromBootstrap)
{
	@($fromBootstrap.artifacts | Where-Object {
		(-not $_.platform) -or @($_.platform | Where-Object { $_.name -eq 'win' -and -not $_.arch }).Count -gt 0
	})
}

# The test client's JVMs, told apart by their command lines as start-client.ps1 does; Spike is this script's client.
function Get-ClientJvms
{
	$all = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' or Name = 'javaw.exe'" |
		Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($ClientHome, [StringComparison]::OrdinalIgnoreCase) -ge 0 })
	[pscustomobject]@{
		RSProx   = @($all | Where-Object { $_.CommandLine -match 'net\.rsprox\.gui\.ProxyToolGuiKt|RsproxAutoLaunch|rsprox-launcher\.jar' })
		RuneLite = @($all | Where-Object { $_.CommandLine -match 'net\.runelite\.|--socket_id=' })
		Spike    = @($all | Where-Object { $_.CommandLine -match 'SpikeLoader run ' })
	}
}

# RuneLite by its window first, so it shuts down as on a click; whatever is left after 20 s is killed.
function Stop-TestClient
{
	$jvms = Get-ClientJvms
	$stop = @($jvms.RSProx) + @($jvms.RuneLite)
	if ($stop.Count -eq 0) { return }
	Step "Closing RSProx/RuneLite (pid $(@($stop | ForEach-Object { $_.ProcessId }) -join ', '))"
	$asked = @(foreach ($p in $jvms.RuneLite)
	{
		$process = Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue
		if ($process -and $process.CloseMainWindow()) { $p.ProcessId }
	})
	$closeBy = (Get-Date).AddSeconds(20)
	while ($asked.Count -gt 0 -and (Get-Date) -lt $closeBy -and
		@((Get-ClientJvms).RuneLite | Where-Object { $asked -contains $_.ProcessId }).Count -gt 0)
	{
		Start-Sleep -Seconds 1
	}
	$jvms = Get-ClientJvms
	foreach ($p in @($jvms.RSProx) + @($jvms.RuneLite))
	{
		Step "Killing pid $($p.ProcessId)"
		Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
	}
	$by = (Get-Date).AddSeconds(30)
	while (@((Get-ClientJvms).RSProx).Count + @((Get-ClientJvms).RuneLite).Count -gt 0)
	{
		if ((Get-Date) -gt $by) { throw 'RSProx/RuneLite did not close' }
		Start-Sleep -Seconds 1
	}
}

function Invoke-Setup
{
	foreach ($tool in @($Java, $javaw, $javac))
	{
		if (-not (Test-Path -LiteralPath $tool)) { throw "no $tool (give -Java a JDK 21's java.exe)" }
	}
	$pins = [IO.File]::ReadAllText((Join-Path $repoRoot 'src\main\resources\runelite-pins.json')) | ConvertFrom-Json
	$pin = @($pins.pins | Sort-Object revision)[-1]
	$jarDir = Join-Path $Work 'repo'
	New-Item -ItemType Directory -Force -Path $jarDir, (Join-Path $Work 'asm'), $classes | Out-Null

	$bootstrapFile = Join-Path $Work "bootstrap-$($pin.commit).json"
	$how = Get-Download "https://raw.githubusercontent.com/runelite/static.runelite.net/$($pin.commit)/bootstrap.json" $bootstrapFile $pin.bootstrapSha256 0
	$script:bootstrap = [IO.File]::ReadAllText($bootstrapFile) | ConvertFrom-Json
	Check 'bootstrap' 'pass' "revision $($pin.revision): RuneLite $($bootstrap.version), static.runelite.net $($pin.commit.Substring(0, 7)), SHA-256 as pinned ($how)"

	$artifacts = Select-WindowsX64 $bootstrap
	$jars = foreach ($a in $artifacts)
	{
		$file = Join-Path $jarDir $a.name
		[void] (Get-Download $a.path $file $a.hash $a.size)
		$file
	}
	[IO.File]::WriteAllLines($classpathFile, [string[]] $jars, $utf8)
	$total = ($artifacts | Measure-Object -Property size -Sum).Sum
	Check 'jars' 'pass' ("$($artifacts.Count) of the bootstrap's $(@($bootstrap.artifacts).Count) artifacts are Windows x64's; " +
		"each has the bootstrap's size and SHA-256 ($([math]::Round($total / 1MB, 1)) MB)")

	foreach ($jar in $asmJars)
	{
		$artifact = $jar.Name -replace '-9\.11\.jar$', ''
		$file = Join-Path $Work "asm\$($jar.Name)"
		try { [void] (Get-Download "https://repo1.maven.org/maven2/org/ow2/asm/$artifact/9.11/$($jar.Name)" $file $jar.Sha256 $jar.Size) }
		catch
		{
			# Maven Central turns some clients away for a while; Google's mirror of it has the same files.
			[void] (Get-Download "https://maven-central.storage-download.googleapis.com/maven2/org/ow2/asm/$artifact/9.11/$($jar.Name)" $file $jar.Sha256 $jar.Size)
		}
	}
	Check 'asm' 'pass' 'ASM 9.11 and its tree API, SHA-256 as pinned'

	$compile = Invoke-Native $javac @('-encoding', 'UTF-8', '-Xlint:all', '-cp', $asmPath, '-d', $classes, (Join-Path $PSScriptRoot 'SpikeLoader.java')) (Join-Path $runDir 'javac.txt')
	if ($compile.Code -ne 0) { throw "javac exited with $($compile.Code): $($compile.Output -join ' ')" }
	Check 'compile' 'pass' "SpikeLoader.java compiled$(if ($compile.Output.Count -gt 0) { ' with: ' + ($compile.Output -join ' ') })"

	$yaml = Get-Text "http://${Server}:$($GamePort + 1)/proxy-targets.yaml"
	$match = [regex]::Match($yaml, '(?m)^\s*modulus:\s*([0-9a-fA-F]+)\s*$')
	if (-not $match.Success) { throw "no modulus in http://${Server}:$($GamePort + 1)/proxy-targets.yaml" }
	$script:modulus = $match.Groups[1].Value
	Check 'modulus' 'pass' "our server's login key, $($modulus.Length) hex digits, from proxy-targets.yaml"

	# A runtime config of RuneLite's own (its "outdated" lists and the rest come from static.runelite.net otherwise).
	[IO.File]::WriteAllText($rtconf, '{}', $utf8)
}

function Invoke-Scan
{
	$scan = Invoke-Native $Java @('-cp', "$classes;$asmPath", 'SpikeLoader', 'scan', $classpathFile, $modulus, '--rlcustom') (Join-Path $runDir 'scan.txt')
	$report = @($scan.Output | Where-Object { $_ -match '^\[scan\] ' } | ForEach-Object { $_.Substring(7) })
	foreach ($line in $report | Where-Object { $_ -notmatch '^(MISSING |every required|\d+ required)' })
	{
		Check 'scan.found' 'info' $line
	}
	$script:scanPassed = $scan.Code -eq 0
	if ($scanPassed) { Check 'scan' 'pass' 'every required patch point is in the real jars' }
	else { Check 'scan' 'fail' ("exit $($scan.Code): " + (@($report | Where-Object { $_ -match '^MISSING ' }) + $scan.Last -join '; ')) }
}

# What RSProx starts its RuneLite with, so the spike's client gets the same home and profile. Only option names, -D
# keys, jar names and the profile go to the results.
function Save-RsproxClientArgs
{
	$script:rsproxChecked = $true
	$p = @((Get-ClientJvms).RuneLite | Where-Object { $_.CommandLine -notmatch 'SpikeLoader' })
	if ($p.Count -eq 0)
	{
		Check 'rsprox.client' 'info' "RSProx's RuneLite isn't running; using user.home $userHome and RuneLite's last profile"
		return
	}
	$tokens = @([regex]::Matches($p[0].CommandLine, '"[^"]*"|\S+') | ForEach-Object { $_.Value.Trim('"') })
	$kept = New-Object System.Collections.ArrayList
	for ($i = 0; $i -lt $tokens.Count; $i++)
	{
		$t = $tokens[$i]
		if ($t -match '^-Duser\.home=(.+)$') { $script:userHome = $Matches[1]; [void] $kept.Add($t) }
		elseif ($t -match '^-D([^=]+)') { [void] $kept.Add("-D$($Matches[1])=...") }
		elseif ($t -match '^--profile=(.+)$') { $script:profileName = $Matches[1]; [void] $kept.Add($t) }
		elseif ($t -eq '--profile' -and $i + 1 -lt $tokens.Count) { $i++; $script:profileName = $tokens[$i]; [void] $kept.Add("--profile $($tokens[$i])") }
		elseif ($t -match '^(-cp|-classpath|--class-path)$' -and $i + 1 -lt $tokens.Count) { $i++; [void] $kept.Add("$t <$(@($tokens[$i] -split ';').Count) entries>") }
		elseif ($t -match '^(--?[A-Za-z][\w.:+-]*)') { [void] $kept.Add($Matches[1]) }
		elseif ($t -match '^net\.runelite\.') { [void] $kept.Add($t) }
		elseif ($t -match '\.(jar|exe)$') { [void] $kept.Add((Split-Path -Leaf $t)) }
	}
	[IO.File]::WriteAllLines((Join-Path $runDir 'rsprox-client.txt'), [string[]] $kept, $utf8)
	Check 'rsprox.client' 'info' ("RSProx's RuneLite (pid $($p[0].ProcessId)): " + ($kept -join ' '))
}

function Get-TaskNote
{
	try
	{
		$info = Get-ScheduledTaskInfo -TaskName $TaskName -ErrorAction Stop
		"; task last result 0x{0:X} at {1}" -f $info.LastTaskResult, $info.LastRunTime
	}
	catch { '' }
}

# Starts the spike's client from a scheduled task and waits for SpikeLoader to start RuneLite; sets $clientPid.
function Start-SpikeClient([string] $name, [switch] $Developer)
{
	$script:clientPid = $null
	$out = Join-Path $runDir "$name-out.txt"
	$jvmArgs = @($bootstrap.clientJvm17Arguments)
	# RuneLite only takes --developer-mode with assertions on; on in its own class, as RSProx has it.
	if ($Developer) { $jvmArgs += '-ea:net.runelite.client.RuneLite' }
	$jvmArgs += @("-Duser.home=$userHome", "-Drunelite.rtconf=$rtconf", '-cp', "$classes;$asmPath", 'SpikeLoader', 'run',
		$classpathFile, $modulus, '--rlcustom', '--world', $Server, '--stub-log', (Join-Path $runDir "$name-api.txt"), '--out', $out,
		'--main', 'net.runelite.client.RuneLite', '--', '--jav_config', $javConfig, '--disable-telemetry', '--noupdate')
	if ($Developer) { $jvmArgs += '--developer-mode' }
	if ($profileName) { $jvmArgs += @('--profile', $profileName) }
	$line = @($jvmArgs | ForEach-Object { Format-Arg $_ }) -join ' '
	[IO.File]::WriteAllText((Join-Path $runDir "$name-command.txt"), "$(Format-Arg $javaw) $line", $utf8)

	try
	{
		$action = New-ScheduledTaskAction -Execute $javaw -Argument $line -WorkingDirectory $Work
		$principal = New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive
		$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Hours 3)
		Register-ScheduledTask -TaskName $TaskName -Action $action -Principal $principal -Settings $settings -Force | Out-Null
		Start-ScheduledTask -TaskName $TaskName | Out-Null
		$how = "scheduled task '$TaskName'"
	}
	catch
	{
		Step "No scheduled task ($($_.Exception.Message)); starting javaw from this session"
		Start-Process -FilePath $javaw -ArgumentList $line -WorkingDirectory $Work | Out-Null
		$how = 'Start-Process'
	}

	$by = (Get-Date).AddSeconds(90)
	$jvm = $null
	while (-not $jvm)
	{
		$jvm = @((Get-ClientJvms).Spike)[0]
		if ($jvm) { break }
		if ((Get-Date) -gt $by) { Check "$name.start" 'fail' "no SpikeLoader JVM within 90 s ($how$(Get-TaskNote))"; return }
		Start-Sleep -Seconds 1
	}
	$by = (Get-Date).AddSeconds(120)
	while ($true)
	{
		$text = Read-Shared $out
		if ($text -match '(?m)^\[spike\] starting ') { break }
		$why = $null
		if ($text -match '(?m)^\[spike\] (refusing to start RuneLite|RuneLite did not start|could not patch [^\r\n]*)') { $why = $Matches[1] }
		elseif (-not (Get-Process -Id $jvm.ProcessId -ErrorAction SilentlyContinue)) { $why = "the JVM ended$(Get-TaskNote)" }
		elseif ((Get-Date) -gt $by) { $why = 'SpikeLoader did not start RuneLite within 120 s' }
		if ($why) { Check "$name.start" 'fail' "$why (see $name-out.txt)"; return }
		Start-Sleep -Seconds 1
	}
	Check "$name.start" 'pass' "pid $($jvm.ProcessId), started by $how"
	$script:clientPid = $jvm.ProcessId
}

# Every remote end the spike client's TCP connections had, sampled twice a second from before it starts until the stop
# file appears. OkHttp keeps an idle connection open for 5 minutes, so an HTTP call to anywhere shows up here too; a
# connection shorter than a sample is in SpikeLoader's connect log instead (Test-Connects), unless it skipped Java's
# ProxySelector.
function Start-NetWatch([string] $file)
{
	$stopFile = "$file.stop"
	$readyFile = "$file.ready"
	$job = Start-Job -ArgumentList $file, $stopFile, $readyFile -ScriptBlock {
		param($file, $stopFile, $readyFile)
		$seen = @{}
		$processId = $null
		[IO.File]::WriteAllText($readyFile, 'ready')
		while (-not (Test-Path -LiteralPath $stopFile))
		{
			if (-not $processId)
			{
				$jvm = @(Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe'" | Where-Object { $_.CommandLine -match 'SpikeLoader run ' })[0]
				if ($jvm) { $processId = $jvm.ProcessId }
			}
			elseif (-not (Get-Process -Id $processId -ErrorAction SilentlyContinue)) { break }
			if ($processId)
			{
				foreach ($c in @(Get-NetTCPConnection -OwningProcess $processId -ErrorAction SilentlyContinue))
				{
					if ("$($c.State)" -eq 'Listen' -or "$($c.State)" -eq 'Bound') { continue }
					$key = "$($c.RemoteAddress) $($c.RemotePort)"
					if ($seen.ContainsKey($key)) { continue }
					$seen[$key] = $true
					[IO.File]::AppendAllText($file, ('{0:o} {1} {2} pid {3}' -f (Get-Date).ToUniversalTime(), $key, $c.State, $processId) + "`r`n")
				}
			}
			Start-Sleep -Milliseconds 500
		}
	}
	# Started before the client, so its first connections are seen too.
	$by = (Get-Date).AddSeconds(30)
	while (-not (Test-Path -LiteralPath $readyFile) -and (Get-Date) -lt $by) { Start-Sleep -Milliseconds 200 }
	[pscustomobject]@{ Job = $job; File = $file; Stop = $stopFile }
}

function Stop-NetWatch($watch, [string] $name)
{
	[IO.File]::WriteAllText($watch.Stop, 'stop')
	[void] (Wait-Job -Job $watch.Job -Timeout 15)
	[void] (Receive-Job -Job $watch.Job -ErrorAction SilentlyContinue -ErrorVariable jobErrors)
	Remove-Job -Job $watch.Job -Force
	$errorText = @($jobErrors | ForEach-Object { "$_" }) -join ' | '
	$rows = @(if (Test-Path -LiteralPath $watch.File) { [IO.File]::ReadAllLines($watch.File) })
	$ends = @($rows | ForEach-Object { $f = $_ -split ' '; "$($f[1]):$($f[2])" } | Sort-Object -Unique)
	$ours = @("${Server}:$GamePort", "${Server}:$($GamePort + 1)")
	$foreign = @($ends | Where-Object { $_ -notmatch '^(127\.0\.0\.1|::1):' -and $ours -notcontains $_ })
	if ($ends.Count -eq 0) { Check "$name.network" 'fail' "no connection seen at all$(if ($errorText) { ' (the watch said: ' + $errorText + ')' })" }
	elseif ($foreign.Count -eq 0) { Check "$name.network" 'pass' ('only our server and the stub: ' + ($ends -join ', ')) }
	else { Check "$name.network" 'fail' ('connections beyond our server and loopback: ' + ($foreign -join ', ')) }
}

# Names Windows looked up while the client ran (any program's), of Jagex's, RuneLite's or the Wiki's domains. The cache is
# emptied first when Windows lets this session do that; a name still cached from before can't be told apart.
function Get-DnsNames
{
	try { Clear-DnsClientCache -ErrorAction Stop; $script:dnsFlushed = $true } catch { $script:dnsFlushed = $false }
	@(Get-DnsClientCache -ErrorAction SilentlyContinue | ForEach-Object { "$($_.Entry)".ToLowerInvariant() } | Sort-Object -Unique)
}

function Test-Dns([string] $name, [string[]] $before)
{
	$watched = '(^|\.)(jagex\.com|runescape\.com|runelite\.net|runescape\.wiki)$'
	$now = @(Get-DnsClientCache -ErrorAction SilentlyContinue | ForEach-Object { "$($_.Entry)".ToLowerInvariant() } | Sort-Object -Unique)
	$new = @($now | Where-Object { $before -notcontains $_ })
	$flagged = @($new | Where-Object { $_ -match $watched })
	$older = @($before | Where-Object { $_ -match $watched })
	$note = if ($dnsFlushed) { 'cache emptied first' } elseif ($older.Count -gt 0) { "not emptied; cached from before: $($older -join ', ')" } else { 'not emptied' }
	if ($flagged.Count -eq 0) { Check "$name.dns" 'pass' "no Jagex, RuneLite or Wiki name looked up ($($new.Count) other new names; $note)" }
	else { Check "$name.dns" 'fail' ('looked up: ' + ($flagged -join ', ') + "; $note") }
}

function Test-Patched([string] $name)
{
	$text = Read-Shared (Join-Path $runDir "$name-out.txt")
	$planned = @([regex]::Matches($text, '(?m)^\[spike\] will patch ([^:\r\n]+):') | ForEach-Object { $_.Groups[1].Value })
	$patched = @([regex]::Matches($text, '(?m)^\[spike\] patched ([^:\r\n]+):(.*)$') | ForEach-Object { "$($_.Groups[1].Value):$($_.Groups[2].Value.TrimEnd())" })
	$failed = @([regex]::Matches($text, '(?m)^\[spike\] could not patch (.*)$') | ForEach-Object { $_.Groups[1].Value.TrimEnd() })
	$never = @($planned | Where-Object { $c = $_; -not ($patched | Where-Object { $_.StartsWith("${c}:") }) })
	if ($failed.Count -gt 0) { Check "$name.patches" 'fail' ('could not patch ' + ($failed -join '; ')) }
	elseif ($patched.Count -eq 0) { Check "$name.patches" 'fail' 'no class was patched' }
	else
	{
		Check "$name.patches" 'pass' ($patched -join '; ')
		if ($never.Count -gt 0) { Check "$name.patches" 'warn' ('planned but never loaded: ' + ($never -join ', ')) }
	}
}

function Save-ClientLog([string] $name, [long] $from)
{
	$text = Read-Shared $ClientLog $from
	[IO.File]::WriteAllText((Join-Path $runDir "$name-client.log"), $text, $utf8)
	$lines = @($text -split "`r?`n" | Where-Object { $_ })
	$errors = @($lines | Where-Object { $_ -match ' ERROR |Failure during startup|Uncaught exception|[Oo]utdated' })
	$detail = "$($lines.Count) lines in client.log, $($errors.Count) error lines"
	if ($errors.Count -gt 0) { $detail += ': ' + (@($errors | Select-Object -First 5) -join ' | ') }
	Check "$name.log" $(if ($errors.Count -eq 0) { 'pass' } else { 'warn' }) $detail
}

# What SpikeLoader logged as "[spike] connect <scheme>://<host>:<port><path>": each target Java was asked to connect to,
# once, however short the connection was.
function Get-Connects([string] $name)
{
	$text = Read-Shared (Join-Path $runDir "$name-out.txt")
	@([regex]::Matches($text, '(?m)^\[spike\] connect (\S+)') | ForEach-Object { $_.Groups[1].Value })
}

# Only our server's two ports and loopback may appear.
function Test-Connects([string] $name)
{
	$targets = Get-Connects $name
	$ours = @("${Server}:$GamePort", "${Server}:$($GamePort + 1)")
	$foreign = @($targets | Where-Object {
		$m = [regex]::Match($_, '^[a-z]+://(\[[^\]]+\]|[^/:]+)(?::(-?\d+))?')
		-not ($m.Groups[1].Value -match '^(127\.0\.0\.1|localhost|\[::1\])$' -or $ours -contains "$($m.Groups[1].Value):$($m.Groups[2].Value)")
	})
	$ends = @($targets | ForEach-Object { $_ -replace '^([a-z]+://[^/]+).*$', '$1' } | Group-Object |
		ForEach-Object { if ($_.Count -gt 1) { "$($_.Name) ($($_.Count) paths)" } else { $_.Name } })
	if ($targets.Count -eq 0) { Check "$name.connects" 'warn' "no connection logged through Java's ProxySelector, so only the connection watch checked this run" }
	elseif ($foreign.Count -eq 0) { Check "$name.connects" 'pass' ('every target Java was asked for is our server or loopback: ' + ($ends -join ', ')) }
	else { Check "$name.connects" 'fail' ('beyond our server and loopback: ' + (@($foreign | Select-Object -First 10) -join ', ')) }
}

# The plan's "no gamepack download". RuneLite's released client.jar can fetch Jagex's gamepack itself (the spike makes that
# method return at once). A download asks Java for the jar's URL first, so the connect log names it. This also fails on a
# gamepack or vanilla client line in RuneLite's log (the released jar's own messages aren't in RuneLite's public source,
# so the match is broad and the summary quotes the line), and on any jar or gamepack file written under the client's
# home while it ran.
function Test-Gamepack([string] $name, [long] $from, [datetime] $since)
{
	$fetched = @(Get-Connects $name | Where-Object { $_ -match '(?i)^https?://[^/]+/' -and ($_ -match '(?i)\.jar$' -or $_ -match '(?i)gamepack') })
	$text = (Read-Shared $ClientLog $from) + "`n" + (Read-Shared (Join-Path $runDir "$name-out.txt"))
	# SpikeLoader's own lines name updateVanilla when it patches it.
	$lines = @($text -split "`r?`n" | Where-Object { $_ -match '(?i)gamepack|vanilla' -and $_ -notmatch '^\[(spike|scan)\] ' } |
		Select-Object -First 5)
	# $since is taken just before the client starts, so the jars setup downloaded don't count.
	$written = @(Get-ChildItem -LiteralPath $userHome -Recurse -File -Force -ErrorAction SilentlyContinue |
		Where-Object { $_.LastWriteTimeUtc -ge $since -and $_.Name -match '(?i)\.jar$|gamepack|vanilla|patched' } |
		Select-Object -First 5 | ForEach-Object { $_.FullName })
	if ($fetched.Count -gt 0) { Check "$name.gamepack" 'fail' ('asked Java for: ' + ($fetched -join ', ')) }
	if ($written.Count -gt 0) { Check "$name.gamepack" 'fail' ('written during the run: ' + ($written -join ', ')) }
	if ($lines.Count -gt 0) { Check "$name.gamepack" 'fail' ('log lines: ' + ($lines -join ' | ')) }
	if ($fetched.Count -eq 0 -and $written.Count -eq 0 -and $lines.Count -eq 0)
	{
		Check "$name.gamepack" 'pass' "no jar asked for, no gamepack or vanilla client in client.log or $name-out.txt, no jar written under $userHome"
	}
}

function Test-ApiLog([string] $name)
{
	$file = Join-Path $runDir "$name-api.txt"
	$lines = @(if (Test-Path -LiteralPath $file) { [IO.File]::ReadAllLines($file) })
	# "<time> <method> <uri> -> <status>"
	$calls = @($lines | ForEach-Object { $f = $_ -split ' '; if ($f.Count -ge 5) { "$($f[1]) $(($f[2] -split '\?')[0]) $($f[4])" } } |
		Group-Object | ForEach-Object { "$($_.Name) x$($_.Count)" })
	$worlds = @($lines | Where-Object { $_ -match '/worlds\.js -> 200' }).Count
	Check "$name.api" $(if ($worlds -gt 0) { 'pass' } else { 'warn' }) ("worlds.js answered $worlds time(s); every call to the stub: " + ($calls -join ', '))
}

function Get-Js5Count
{
	try
	{
		$m = [regex]::Match((Get-Text "http://${Server}:$($GamePort + 1)/status"), 'js5 connections (\d+)')
		if ($m.Success) { [int] $m.Groups[1].Value } else { -1 }
	}
	catch { -1 }
}

function Test-Results([string] $id, [string] $name)
{
	$file = Join-Path $Results "$id.json"
	if (-not (Test-Path -LiteralPath $file) -or (Get-Item -LiteralPath $file).LastWriteTimeUtc -lt $started)
	{
		Check $name 'fail' 'no result from this run'
		return
	}
	$r = [IO.File]::ReadAllText($file) | ConvertFrom-Json
	$passed = @($r.steps | Where-Object { $_.result -eq 'pass' }).Count
	$detail = "$passed/$(@($r.steps).Count) steps passed in $([math]::Round($r.ms / 1000)) s ($($r.ticks) ticks)"
	if ($r.result -eq 'pass') { Check $name 'pass' $detail } else { Check $name 'fail' "$detail; $($r.failure)" }
}

function Invoke-LoginLoop
{
	$steps = New-Object System.Collections.ArrayList
	[void] $steps.Add([ordered]@{ step = 'waitForLogin' })
	for ($i = 1; $i -le $Logins; $i++)
	{
		[void] $steps.Add([ordered]@{ step = 'logout'; comment = "logout $i of $Logins" })
		[void] $steps.Add([ordered]@{ step = 'waitForLogin'; timeoutMs = 90000 })
		[void] $steps.Add([ordered]@{ step = 'waitTicks'; ticks = 17 })
	}
	$body = [ordered]@{ name = "Launcher spike: $Logins logouts and logins"; account = 'test'; timeoutMs = 30000; steps = $steps }
	$file = Join-Path $runDir 'spike-logins.json'
	[IO.File]::WriteAllText($file, ($body | ConvertTo-Json -Depth 5), $utf8)
	[void] (Invoke-Script (Join-Path $repoRoot 'test-runner\run-scripts.ps1') @($file, '-TimeoutSeconds', '2400') (Join-Path $runDir 'dev-logins.txt'))
	Test-Results 'spike-logins' 'dev.logins'
}

function Invoke-Dev
{
	Save-RsproxClientArgs
	Stop-TestClient
	$dnsBefore = Get-DnsNames
	$logFrom = Get-LogEnd
	$since = (Get-Date).ToUniversalTime()
	$watch = Start-NetWatch (Join-Path $runDir 'dev-net.txt')
	try
	{
		Start-SpikeClient 'dev' -Developer
		if (-not $clientPid) { return }
		# With a client running and no -Restart, start-client.ps1 only waits until the test runner has logged test in.
		$login = Invoke-Script (Join-Path $repoRoot 'test-runner\launch\start-client.ps1') @('-Server', $Server, '-TimeoutSeconds', '300') (Join-Path $runDir 'dev-login.txt')
		if ($login.Code -ne 0) { Check 'dev.login' 'fail' $login.Last; return }
		Check 'dev.login' 'pass' $login.Last
		$files = @($Examples | ForEach-Object { Join-Path $repoRoot "test-runner\examples\$_" })
		[void] (Invoke-Script (Join-Path $repoRoot 'test-runner\run-scripts.ps1') $files (Join-Path $runDir 'dev-examples.txt'))
		foreach ($example in $Examples) { Test-Results ([IO.Path]::GetFileNameWithoutExtension($example)) "dev.example $example" }
		Invoke-LoginLoop
		if (-not (Get-Process -Id $clientPid -ErrorAction SilentlyContinue)) { Check 'dev.running' 'fail' 'the client ended during the run' }
	}
	finally
	{
		Stop-TestClient
		Stop-NetWatch $watch 'dev'
		Test-Connects 'dev'
		Test-Dns 'dev' $dnsBefore
		Test-Patched 'dev'
		Test-ApiLog 'dev'
		Save-ClientLog 'dev' $logFrom
		Test-Gamepack 'dev' $logFrom $since
	}
}

function Save-Screenshot([int] $processId, [string] $file)
{
	try
	{
		Add-Type -AssemblyName System.Windows.Forms, System.Drawing
		if (-not ('SpikeWindow' -as [type]))
		{
			Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class SpikeWindow
{
	[StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
	[DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr window, out Rect rect);
	[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
}
'@
		}
		$bounds = [Windows.Forms.Screen]::PrimaryScreen.Bounds
		$window = (Get-Process -Id $processId).MainWindowHandle
		if ($window -ne [IntPtr]::Zero)
		{
			[void] [SpikeWindow]::SetForegroundWindow($window)
			Start-Sleep -Milliseconds 800
			$rect = New-Object SpikeWindow+Rect
			if ([SpikeWindow]::GetWindowRect($window, [ref] $rect) -and $rect.Right -gt $rect.Left)
			{
				$bounds = New-Object Drawing.Rectangle $rect.Left, $rect.Top, ($rect.Right - $rect.Left), ($rect.Bottom - $rect.Top)
			}
		}
		$bitmap = New-Object Drawing.Bitmap $bounds.Width, $bounds.Height
		$graphics = [Drawing.Graphics]::FromImage($bitmap)
		try
		{
			$graphics.CopyFromScreen($bounds.Location, [Drawing.Point]::Empty, $bounds.Size)
			$bitmap.Save($file, [Drawing.Imaging.ImageFormat]::Png)
		}
		finally { $graphics.Dispose(); $bitmap.Dispose() }
		Check 'normal.screenshot' 'info' "$file ($($bounds.Width)x$($bounds.Height), $(if ($window -ne [IntPtr]::Zero) { 'the client window' } else { 'the screen' }))"
	}
	catch { Check 'normal.screenshot' 'warn' "no screenshot: $($_.Exception.Message)" }
}

function Invoke-Normal
{
	if (-not $rsproxChecked) { Save-RsproxClientArgs }
	Stop-TestClient
	$js5Before = Get-Js5Count
	$dnsBefore = Get-DnsNames
	$logFrom = Get-LogEnd
	$since = (Get-Date).ToUniversalTime()
	$watch = Start-NetWatch (Join-Path $runDir 'normal-net.txt')
	try
	{
		Start-SpikeClient 'normal'
		if (-not $clientPid) { return }
		$until = (Get-Date).AddSeconds($NormalSeconds)
		$js5 = $js5Before
		while ($js5 -le $js5Before -and (Get-Date) -lt $until)
		{
			Start-Sleep -Seconds 3
			$js5 = Get-Js5Count
		}
		if ($js5Before -ge 0 -and $js5 -gt $js5Before) { Check 'normal.js5' 'pass' "the server let in $($js5 - $js5Before) new cache (JS5) connection(s), $js5Before -> $js5" }
		else { Check 'normal.js5' 'fail' "no new cache (JS5) connection within $NormalSeconds s ($js5Before -> $js5)" }
		# The login screen draws once the cache is loaded.
		Start-Sleep -Seconds 20
		Save-Screenshot $clientPid (Join-Path $runDir 'normal.png')
		while ((Get-Date) -lt $until) { Start-Sleep -Seconds 2 }
		if (Get-Process -Id $clientPid -ErrorAction SilentlyContinue) { Check 'normal.running' 'pass' "still running after $NormalSeconds s" }
		else { Check 'normal.running' 'fail' 'the client ended by itself' }
	}
	finally
	{
		Stop-TestClient
		Stop-NetWatch $watch 'normal'
		Test-Connects 'normal'
		Test-Dns 'normal' $dnsBefore
		Test-Patched 'normal'
		Test-ApiLog 'normal'
		Save-ClientLog 'normal' $logFrom
		Test-Gamepack 'normal' $logFrom $since
	}
}

function Invoke-Head
{
	foreach ($revision in $oldPins.Keys)
	{
		$commit = $oldPins[$revision]
		try
		{
			$old = Get-Text "https://raw.githubusercontent.com/runelite/static.runelite.net/$commit/bootstrap.json" | ConvertFrom-Json
			$artifacts = Select-WindowsX64 $old
			$gone = @(foreach ($a in $artifacts)
			{
				try
				{
					$r = Invoke-WebRequest -UseBasicParsing -Method Head -TimeoutSec 30 -Uri $a.path
					$length = if ($r.Headers.ContainsKey('Content-Length')) { $r.Headers['Content-Length'] } else { $null }
					if ($r.StatusCode -ne 200 -or ($length -and [long] $length -ne [long] $a.size)) { "$($a.name) ($($r.StatusCode), $length bytes)" }
				}
				catch { "$($a.name) ($($_.Exception.Message))" }
			})
			if ($gone.Count -eq 0) { Check "head.$revision" 'pass' "RuneLite $($old.version) (static.runelite.net $($commit.Substring(0, 7))): all $($artifacts.Count) Windows x64 jars still on RuneLite's repository" }
			else { Check "head.$revision" 'warn' "RuneLite $($old.version): $($gone.Count) of $($artifacts.Count) jars not there: $($gone -join ', ')" }
		}
		catch { Check "head.$revision" 'warn' "no bootstrap.json for $($commit.Substring(0, 7)): $($_.Exception.Message)" }
	}
}

function Invoke-Restore
{
	Stop-TestClient
	if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue)
	{
		Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
		Step "Removed scheduled task '$TaskName'"
	}
	$restart = Invoke-Script (Join-Path $repoRoot 'test-runner\launch\start-client.ps1') @('-Restart', '-Server', $Server) (Join-Path $runDir 'restore.txt')
	Check 'restore' $(if ($restart.Code -eq 0) { 'pass' } else { 'fail' }) "RSProx's client: $($restart.Last)"
}

function Write-Summary
{
	$failed = @($checks | Where-Object { $_.result -eq 'fail' })
	$verdict = if ($failed.Count -eq 0) { 'no check failed' } else { "$($failed.Count) check(s) failed: $(@($failed | ForEach-Object { $_.check }) -join ', ')" }
	$summary = [ordered]@{
		mode = $Mode
		startedUtc = $started.ToString('o')
		endedUtc = (Get-Date).ToUniversalTime().ToString('o')
		runelite = $(if ($bootstrap) { $bootstrap.version } else { $null })
		results = $runDir
		verdict = $verdict
		checks = @($checks)
	}
	[IO.File]::WriteAllText((Join-Path $runDir 'summary.json'), ($summary | ConvertTo-Json -Depth 4), $utf8)
	$text = @($checks | ForEach-Object { '{0,-4} {1}: {2}' -f $_.result.ToUpperInvariant(), $_.check, $_.detail }) + '' + "Mode $Mode, $verdict. Results in $runDir"
	[IO.File]::WriteAllLines((Join-Path $runDir 'summary.txt'), [string[]] $text, $utf8)
	Write-Host ''
	$text | ForEach-Object { Write-Host $_ }
	$script:exitCode = if ($failed.Count -eq 0) { 0 } else { 1 }
}

Step "Launcher spike, mode $Mode, results in $runDir"
try
{
	switch ($Mode)
	{
		'setup' { Invoke-Setup }
		'scan' { Invoke-Setup; Invoke-Scan }
		'dev' { Invoke-Setup; Invoke-Scan; if ($scanPassed) { Invoke-Dev } }
		'normal' { Invoke-Setup; Invoke-Scan; if ($scanPassed) { Invoke-Normal } }
		'head' { Invoke-Head }
		'restore' { Invoke-Restore }
		'all' { Invoke-Setup; Invoke-Scan; if ($scanPassed) { Invoke-Dev; Invoke-Normal }; Invoke-Head }
	}
}
catch
{
	Check 'spike' 'fail' "stopped at line $($_.InvocationInfo.ScriptLineNumber): $($_.Exception.Message)"
}
finally
{
	if ($Mode -eq 'all')
	{
		try { Invoke-Restore }
		catch { Check 'restore' 'fail' "stopped at line $($_.InvocationInfo.ScriptLineNumber): $($_.Exception.Message)" }
	}
	Write-Summary
}
exit $exitCode
