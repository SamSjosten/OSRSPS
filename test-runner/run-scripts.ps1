<#
Runs test-runner scripts in the running test client, one at a time, and prints a pass/fail table.

  .\test-runner\run-scripts.ps1 .\test-runner\examples
  .\test-runner\run-scripts.ps1 .\test-runner\examples\castle-talk-cook.json .\test-runner\examples\hans-demo.json

A folder runs every script in it, by file name. Each script is written into the tests folder, which makes the plugin
run it, and the next is written only once its result file has been rewritten, so scripts never overlap. The client
must be running, logged in or logging itself in (Auto login). Exits with 0 when every script passed, 1 otherwise.
#>
param(
	[Parameter(Mandatory = $true, Position = 0, ValueFromRemainingArguments = $true)]
	[string[]] $Scripts,
	[string] $Tests = 'C:\RSPS\.rlcustom\tests',
	[string] $Results = 'C:\RSPS\.rlcustom\test-results',
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
$rows = foreach ($f in $files)
{
	# The plugin names a result after the script's file name; a name with other characters than these gets %XX in it.
	$id = [IO.Path]::GetFileNameWithoutExtension($f.Name)
	$result = Join-Path $Results "$id.json"
	$before = if (Test-Path -LiteralPath $result) { (Get-Item -LiteralPath $result).LastWriteTimeUtc } else { [DateTime]::MinValue }
	# Written fresh, with no BOM, so the plugin sees a change even when the script is the same as last time.
	[IO.File]::WriteAllText((Join-Path $Tests $f.Name), [IO.File]::ReadAllText($f.FullName), $utf8)
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
