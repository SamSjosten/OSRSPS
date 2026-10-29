<#
Writes the launcher manifest the test VM launches from, until releases ship the signed one.

  .\test-runner\launch\write-dev-manifest.ps1                          # from the server at 192.168.0.210
  .\test-runner\launch\write-dev-manifest.ps1 -Out C:\RSPS\launcher\manifest.json -Server 192.168.0.210

The launcher started with --manifest <file> trusts the file as it is (launcher/README.md), so this only has to agree
with the live server: the host and ports it is told, the key the server serves in /proxy-targets.yaml (the same place
RSProx friends get it, and what the launcher's own check compares against), and the pin table below, which says which
RuneLite release serves each revision (the manifest's "pins"; launcher/src/test/resources holds the proof for 241). The
"files" list stays empty: there is no release to update to. Warns when the server's /status.json names a revision the
table doesn't pin, because the launcher would then stop with "no pin for revision". Nothing here is secret: the modulus
is the public half of the server's key.
#>
param(
	[string] $Server = '192.168.0.210',
	[int] $GamePort = 43594,
	[int] $HttpPort = 43595,
	[string] $Out = 'C:\RSPS\launcher\manifest.json',
	# The manifest's own version; the launcher doesn't compare it with its jar's until updates exist.
	[string] $Version = '0.1.0'
)
$ErrorActionPreference = 'Stop'

# Revision -> the static.runelite.net commit of the RuneLite release for it and the SHA-256 of that commit's
# bootstrap.json. The launcher's tests prove the 241 entry against RuneLite's real signed bootstrap (Fixtures.java).
$pins = @(
	@{ revision = 241; runelite = '1.13.4'; commit = '1e634e64717418713898b06061a189ec1cdf948d'
		bootstrapSha256 = '25f22ff874ddf8c4016c8d03150c24920cdaa536ae7ad44aed36173c826c0b2a' }
)

$base = "http://${Server}:$HttpPort"
$yaml = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 "$base/proxy-targets.yaml").Content
if ($yaml -notmatch '(?m)^\s*modulus:\s*([0-9A-Fa-f]+)\s*$') { throw "$base/proxy-targets.yaml has no modulus line" }
$modulus = $Matches[1].ToLowerInvariant()
if ($modulus.Length -ne 256) { throw "the served modulus has $($modulus.Length) hex digits, not the 256 of a 1024-bit key" }

try
{
	$status = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 "$base/status.json").Content | ConvertFrom-Json
	if (-not ($pins | Where-Object { $_.revision -eq [int] $status.revision }))
	{
		Write-Warning "the server runs revision $($status.revision), which the pin table here doesn't have: add it"
	}
}
catch { Write-Warning "$base/status.json could not be read ($($_.Exception.Message)); the revision wasn't checked" }

$manifest = [ordered]@{
	format = 1
	version = $Version
	minBootstrapVersion = $Version
	server = [ordered]@{ host = $Server; gamePort = $GamePort; httpPort = $HttpPort; modulus = $modulus }
	pins = @($pins | ForEach-Object { [ordered]@{ revision = [int] $_.revision; runelite = $_.runelite; commit = $_.commit
		bootstrapSha256 = $_.bootstrapSha256 } })
	files = @()
}
$json = ($manifest | ConvertTo-Json -Depth 5) + "`n"
$dir = Split-Path -Parent $Out
if ($dir) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
[IO.File]::WriteAllText($Out, $json, (New-Object System.Text.UTF8Encoding $false))
Write-Host "Wrote $Out for $Server`:$GamePort (HTTP $HttpPort), key ...$($modulus.Substring(248)), pins: $(($pins | ForEach-Object { $_.revision }) -join ', ')"
