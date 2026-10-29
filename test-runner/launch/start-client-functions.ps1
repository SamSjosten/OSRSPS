<#
The parts of start-client.ps1 that can be checked without a VM, dot-sourced by it and run by
start-client-functions.tests.ps1 in CI (pwsh on Linux): telling the client's JVMs apart by their command lines, and
reading and waiting on the Odin's Gate launcher's status file. Nothing here starts, stops or finds a process itself.
#>

# RSProx's GUI (started by its launcher or by RsproxAutoLaunch.java), the Odin's Gate launcher (its Main, which stays
# while the game runs) and the RuneLite either of them launches (under the launcher, the child JVM running our Loader
# with RuneLite's main class), told apart by their command lines. Only JVMs of the client in $ClientHome: another
# RuneLite on this PC is left alone. $Processes are objects with ProcessId and CommandLine, as Win32_Process gives them.
function Select-ClientJvms([object[]] $Processes, [string] $ClientHome)
{
	$all = @($Processes | Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($ClientHome, [StringComparison]::OrdinalIgnoreCase) -ge 0 })
	$launcher = @($all | Where-Object { $_.CommandLine -match 'net\.odinsgate\.launcher\.Main' })
	$launcherIds = @($launcher | ForEach-Object { $_.ProcessId })
	[pscustomobject]@{
		RSProx   = @($all | Where-Object { $_.CommandLine -match 'net\.rsprox\.gui\.ProxyToolGuiKt|RsproxAutoLaunch|rsprox-launcher\.jar' })
		Launcher = $launcher
		RuneLite = @($all | Where-Object { $launcherIds -notcontains $_.ProcessId -and $_.CommandLine -match 'net\.runelite\.|--socket_id=' })
	}
}

# The launcher's status file (launcher/README.md: version, state, detail, revision, gamePid, at), or $null while there
# is none or it can't be read as one. The launcher writes it whole by a rename, so a read sees an old file or a new
# one, never half of one; the file is opened sharing everything, so the rename never fails on a read in progress.
function Read-LauncherStatus([string] $Path)
{
	if (-not (Test-Path -LiteralPath $Path)) { return $null }
	try
	{
		$stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read,
			[IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete)
		try { $text = (New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8)).ReadToEnd() }
		finally { $stream.Dispose() }
		$status = $text | ConvertFrom-Json
		if (-not $status.version -or -not $status.state) { return $null }
		$status
	}
	catch { $null }
}

# One look at the status file for a launcher started after $Since: 'wait' while there is nothing new to go on (no
# file, an older one, a half-written one, or a state before RUNNING), the status once the game runs (its state RUNNING,
# and its version $Version when one is expected), and a thrown reason when nothing more will come: a launcher of
# another version, FAILED (with the launcher's detail) or EXITED already.
function Test-LauncherStatus([string] $Path, [DateTime] $Since, [string] $Version)
{
	if (-not (Test-Path -LiteralPath $Path) -or (Get-Item -LiteralPath $Path).LastWriteTime -le $Since) { return 'wait' }
	$status = Read-LauncherStatus $Path
	if (-not $status) { return 'wait' }
	if ($Version -and $status.version -ne $Version)
	{
		throw "the launcher that started is version $($status.version), not $Version (is the new one installed?)"
	}
	switch ($status.state)
	{
		'RUNNING' { return $status }
		'FAILED' { throw "the launcher stopped: $($status.detail)" }
		'EXITED' { throw "the game exited already: $($status.detail)" }
		default { return 'wait' }
	}
}

# Waits until Test-LauncherStatus has the running game's status, polling every $PollSeconds, and throws with the
# last state seen once $Deadline passes.
function Wait-LauncherStatus([string] $Path, [DateTime] $Since, [string] $Version, [DateTime] $Deadline, [int] $PollSeconds = 2)
{
	while ($true)
	{
		$seen = Test-LauncherStatus $Path $Since $Version
		if (-not ($seen -is [string])) { return $seen }
		if ((Get-Date) -gt $Deadline)
		{
			$last = Read-LauncherStatus $Path
			$state = if ($last -and (Get-Item -LiteralPath $Path).LastWriteTime -gt $Since) { "$($last.state): $($last.detail)" }
				else { "no status written since $($Since.ToString('HH:mm:ss'))" }
			throw "the launcher didn't reach RUNNING in time (last: $state)"
		}
		Start-Sleep -Seconds $PollSeconds
	}
}
