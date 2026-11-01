<#
Checks repo-fetch-functions.ps1 without the network: run by CI's launch-scripts job with pwsh on Linux, or by hand with
  pwsh -NoProfile -File .\test-runner\launch\repo-fetch-functions.tests.ps1
Exits with 0 when every check passed, 1 otherwise, each failure printed.
#>
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'repo-fetch-functions.ps1')

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

$dir = Join-Path ([IO.Path]::GetTempPath()) ("repo-fetch-tests-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $dir | Out-Null
try
{
	Write-Host 'Read-RepoToken'
	$tokenFile = Join-Path $dir 'github-token.txt'
	Check 'no file is no token' ($null -eq (Read-RepoToken $tokenFile))
	Check 'no path is no token' ($null -eq (Read-RepoToken ''))
	Set-Content -LiteralPath $tokenFile -Value ''
	Check 'an empty file is no token' ($null -eq (Read-RepoToken $tokenFile))
	Set-Content -LiteralPath $tokenFile -Value @('  github_pat_abc123  ', 'a second line')
	Check 'the token is the first line, trimmed' ((Read-RepoToken $tokenFile) -ceq 'github_pat_abc123')

	Write-Host 'Get-RepoHeaders'
	$with = Get-RepoHeaders 'tok' 'application/vnd.github.raw+json'
	Check 'a token goes as a bearer' ($with['Authorization'] -ceq 'Bearer tok')
	Check 'the media type asked for' ($with['Accept'] -eq 'application/vnd.github.raw+json')
	Check 'GitHub asks for a User-Agent' ([bool] $with['User-Agent'])
	$without = Get-RepoHeaders $null 'application/vnd.github+json'
	Check 'no token, no Authorization: the public repository needs none' (-not $without.ContainsKey('Authorization'))

	Write-Host 'Get-RepoFileUrl'
	Check 'a path and a commit' ((Get-RepoFileUrl 'test-runner/examples/a b.json' 'abc123') -ceq
		'https://api.github.com/repos/SamSjosten/OSRSPS/contents/test-runner/examples/a%20b.json?ref=abc123')
	Check 'backslashes and a leading slash are the same path' ((Get-RepoFileUrl '\test-runner\run-scripts.ps1' 'main') -ceq
		'https://api.github.com/repos/SamSjosten/OSRSPS/contents/test-runner/run-scripts.ps1?ref=main')
	Check 'a branch with a slash is escaped' ((Get-RepoFileUrl 'README.md' 'claude/x') -like '*?ref=claude%2Fx')
	Throws 'a path that climbs out is refused' { Get-RepoFileUrl 'test-runner/../../etc' 'main' } 'not a file in the repository'
	Throws 'a drive is refused' { Get-RepoFileUrl 'C:\RSPS\x.json' 'main' } 'not a file in the repository'
	Throws 'an empty path is refused' { Get-RepoFileUrl '/' 'main' } 'not a file in the repository'
	Throws 'a missing ref is refused' { Get-RepoFileUrl 'README.md' '' } 'no commit or branch'

	Write-Host 'Get-RepoArtifactsUrl'
	Check 'the listing by name' ((Get-RepoArtifactsUrl 'test-runner-jar') -ceq
		'https://api.github.com/repos/SamSjosten/OSRSPS/actions/artifacts?name=test-runner-jar&per_page=50')

	Write-Host 'Select-RepoArtifact'
	$listing = '{"artifacts":[
		{"id":105,"name":"test-runner-jar","expired":false,"archive_download_url":"u105","workflow_run":{"head_branch":"claude/x","head_sha":"fff"}},
		{"id":104,"name":"test-runner-jar","expired":true,"archive_download_url":"u104","workflow_run":{"head_branch":"main","head_sha":"eee"}},
		{"id":101,"name":"test-runner-jar","expired":false,"archive_download_url":"u101","workflow_run":{"head_branch":"main","head_sha":"abc111"}},
		{"id":103,"name":"test-runner-jar","expired":false,"archive_download_url":"u103","workflow_run":{"head_branch":"main","head_sha":"def333"}},
		{"id":106,"name":"tick-recorder-jar","expired":false,"archive_download_url":"u106","workflow_run":{"head_branch":"main","head_sha":"ggg"}}
	]}' | ConvertFrom-Json
	Check "the newest unexpired build on main, not a branch's or an expired one" ((Select-RepoArtifact $listing 'test-runner-jar' 'main' $null).id -eq 103)
	Check 'a commit picks its build, by a prefix of any case' ((Select-RepoArtifact $listing 'test-runner-jar' 'main' 'ABC1').id -eq 101)
	Check 'another branch when asked' ((Select-RepoArtifact $listing 'test-runner-jar' 'claude/x' $null).id -eq 105)
	Throws 'none left is an error naming what was asked' { Select-RepoArtifact $listing 'test-runner-jar' 'main' '999' } 'no artifact test-runner-jar built on main at 999'
	Throws 'an empty listing is an error' { Select-RepoArtifact ('{"artifacts":[]}' | ConvertFrom-Json) 'x' 'main' $null } 'no artifact x'

	Write-Host 'Get-RedirectHeaders'
	$headers = Get-RepoHeaders 'tok' 'application/vnd.github+json'
	$api = [Uri] 'https://api.github.com/repos/SamSjosten/OSRSPS/actions/artifacts/1/zip'
	$storage = Get-RedirectHeaders $headers $api ([Uri] 'https://productionresultssa1.blob.core.windows.net/x.zip?sig=secret')
	Check 'the token never leaves the API host' (-not $storage.ContainsKey('Authorization'))
	Check 'the other headers go on' ($storage['User-Agent'] -eq $headers['User-Agent'])
	$same = Get-RedirectHeaders $headers $api ([Uri] 'https://api.github.com/repositories/1/actions/artifacts/1/zip')
	Check 'on the API host the token goes on' ($same['Authorization'] -ceq 'Bearer tok')
	Check 'the given headers are left as they were' ($headers['Authorization'] -ceq 'Bearer tok')

	Write-Host 'Format-SafeUrl'
	Check "a signed address's signature is left out" ((Format-SafeUrl ([Uri] 'https://x.blob.core.windows.net/a.zip?sig=secret')) -ceq 'https://x.blob.core.windows.net/a.zip')
}
finally
{
	Remove-Item -Recurse -Force -LiteralPath $dir
}

if ($script:failures -gt 0) { Write-Host "$($script:failures) check(s) failed"; exit 1 }
Write-Host 'all checks passed'
exit 0
