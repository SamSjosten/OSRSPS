<#
Runs test-runner scripts in the running test client, one at a time, and prints a pass/fail table.

  .\test-runner\run-scripts.ps1 .\test-runner\examples
  .\test-runner\run-scripts.ps1 .\test-runner\examples\castle-talk-cook.json .\test-runner\examples\hans-demo.json
  .\test-runner\run-scripts.ps1 .\test-runner\examples -Account test2

-Account runs the scripts written for "account": "test" as that account instead (VM 217 logs in as test2), by
rewriting the field in the copy it drops into the tests folder; the files in git stay as they are. Scripts for any
other account (the two-player pairs' test2 halves) are left alone.

A folder runs every script in it, by file name. Each script is written into the tests folder, which makes the plugin
run it, and the next is written only once its result file has been rewritten, so scripts never overlap. The client
must be running, logged in or logging itself in (Auto login). Exits with 0 when every script passed, 1 otherwise.
#>
param(
	[Parameter(Mandatory = $true, Position = 0, ValueFromRemainingArguments = $true)]
	[string[]] $Scripts,
	[string] $Tests = 'C:\RSPS\.rlcustom\tests',
	[string] $Results = 'C:\RSPS\.rlcustom\test-results',
	# The account to run "account": "test" scripts as; empty runs every script as written.
	[string] $Account = '',
	# How long to wait for one script's result. A script's own step timeouts end a stuck run well before this.
	[int] $TimeoutSeconds = 900
)
$ErrorActionPreference = 'Stop'

$files = foreach ($s in $Scripts)
{
	if (Test-Path -LiteralPath $s -PathType Container) { Get-ChildItem -LiteralPath $s -Filter *.json | Sort-Object Name }
	else { Get-Item -LiteralPath $s }
}
$utf8 = New-Object System.Text.UTF8Encoding $false

# The id the plugin gives a script, and names its result file after (Script.idFor): the file name without .json, with
# each UTF-8 byte other than A-Z a-z 0-9 . _ - written as %XX.
function Get-ScriptId([string] $fileName)
{
	$base = if ($fileName.ToLowerInvariant().EndsWith('.json')) { $fileName.Substring(0, $fileName.Length - 5) } else { $fileName }
	$id = New-Object System.Text.StringBuilder
	foreach ($b in $utf8.GetBytes($base))
	{
		$c = [char] $b
		if (($c -cmatch '[A-Za-z0-9._-]') -and $b -lt 128) { [void] $id.Append($c) }
		else { [void] $id.Append(('%{0:X2}' -f $b)) }
	}
	$id.ToString()
}
# The script with its "account": "test" (each script has one, at the top) set to $name. Only the field's value
# changes, so the rest of the file is byte for byte what git has.
function Set-ScriptAccount([string] $text, [string] $name)
{
	$quoted = $name.Replace('\', '\\').Replace('"', '\"')
	[regex]::new('("account"\s*:\s*)"test"').Replace($text, { param($m) $m.Groups[1].Value + '"' + $quoted + '"' }, 1)
}
$rows = foreach ($f in $files)
{
	$id = Get-ScriptId $f.Name
	$result = Join-Path $Results "$id.json"
	$before = if (Test-Path -LiteralPath $result) { (Get-Item -LiteralPath $result).LastWriteTimeUtc } else { [DateTime]::MinValue }
	# Written fresh, with no BOM, so the plugin sees a change even when the script is the same as last time.
	$text = [IO.File]::ReadAllText($f.FullName)
	if ($Account) { $text = Set-ScriptAccount $text $Account }
	[IO.File]::WriteAllText((Join-Path $Tests $f.Name), $text, $utf8)
	$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
	$done = $false
	while (-not $done -and (Get-Date) -lt $deadline)
	{
		Start-Sleep -Seconds 1
		# The plugin writes a result whole and then moves it into place, so a newer file is a finished one.
		$done = (Test-Path -LiteralPath $result) -and (Get-Item -LiteralPath $result).LastWriteTimeUtc -gt $before
	}
	if (-not $done)
	{
		[pscustomobject]@{ Script = $id; Result = 'none'; Steps = ''; Seconds = ''; Failure = "no result within $TimeoutSeconds s" }
		continue
	}
	$r = Get-Content -LiteralPath $result -Raw -Encoding UTF8 | ConvertFrom-Json
	$steps = if ($r.steps) { @($r.steps) } else { @() }
	$passed = @($steps | Where-Object { $_.result -eq 'pass' }).Count
	[pscustomobject]@{
		Script = $id
		Result = $r.result
		Steps = "$passed/$($steps.Count)"
		Seconds = [math]::Round($r.ms / 1000, 1)
		Failure = $r.failure
	}
}
$rows | Format-Table -AutoSize -Wrap
$failed = @($rows | Where-Object { $_.Result -ne 'pass' }).Count
Write-Output ("{0} of {1} passed" -f (@($rows).Count - $failed), @($rows).Count)
exit $(if ($failed -eq 0) { 0 } else { 1 })
