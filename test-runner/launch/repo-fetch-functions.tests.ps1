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
	Check 'a page of the listing by name' ((Get-RepoArtifactsUrl 'test-runner-jar' 2) -ceq
		'https://api.github.com/repos/SamSjosten/OSRSPS/actions/artifacts?name=test-runner-jar&per_page=100&page=2')

	# An artifact as the API lists it; $from is the repository its build's head came from (ours is 7).
	function Artifact([long] $id, [string] $branch, [string] $sha, [bool] $expired = $false, [long] $from = 7, [string] $name = 'test-runner-jar')
	{
		[pscustomobject]@{ id = $id; name = $name; expired = $expired; archive_download_url = "u$id"
			workflow_run = [pscustomobject]@{ head_branch = $branch; head_sha = $sha; repository_id = 7; head_repository_id = $from } }
	}
	function Listing([object[]] $artifacts, [long] $total) { [pscustomobject]@{ total_count = $total; artifacts = @($artifacts) } }

	Write-Host 'Select-RepoArtifact'
	$listing = Listing @(
		(Artifact 107 'main' 'fork1' -from 99),
		(Artifact 105 'claude/x' 'fff'),
		(Artifact 104 'main' 'eee' -expired $true),
		(Artifact 101 'main' 'abc111'),
		(Artifact 103 'main' 'def333'),
		(Artifact 106 'main' 'ggg' -name 'tick-recorder-jar')) 6
	Check "the newest unexpired build of our main: not a fork's main, a branch's or an expired one" ((Select-RepoArtifact $listing 'test-runner-jar' 'main' $null).id -eq 103)
	Check 'a commit picks its build, by a prefix of any case' ((Select-RepoArtifact $listing 'test-runner-jar' 'main' 'ABC1').id -eq 101)
	Check 'another branch when asked' ((Select-RepoArtifact $listing 'test-runner-jar' 'claude/x' $null).id -eq 105)
	Check "a fork's build is never chosen, even by its commit" ($null -eq (Select-RepoArtifact $listing 'test-runner-jar' 'main' 'fork1'))
	Check 'none on the page is nothing' ($null -eq (Select-RepoArtifact (Listing @() 0) 'x' 'main' $null))

	Write-Host 'Find-RepoArtifact'
	$script:asked = @()
	$fullPage = @(1..100 | ForEach-Object { Artifact (1000 - $_) 'claude/y' 'yyy' })
	$pages = @{ 1 = (Listing $fullPage 102); 2 = (Listing @((Artifact 800 'main' 'abc222'), (Artifact 799 'main' 'abc111')) 102) }
	$getPage = { param($page) $script:asked += $page; $pages[$page] }
	Check 'a build past the first page is found on the next' ((Find-RepoArtifact $getPage 'test-runner-jar' 'main' 'abc111').id -eq 799)
	Check 'pages are read in order' (($script:asked -join ',') -eq '1,2')
	$script:asked = @()
	Check 'the first page with a match holds the newest, so no more are read' ((Find-RepoArtifact $getPage 'test-runner-jar' 'claude/y' $null).id -eq 999 -and ($script:asked -join ',') -eq '1')
	$script:asked = @()
	Throws 'none on any page is an error naming what was asked' { Find-RepoArtifact $getPage 'test-runner-jar' 'main' '999' } 'no artifact test-runner-jar built on main at 999'
	Check 'it stops at the last page' (($script:asked -join ',') -eq '1,2')
	Throws 'an empty listing is an error' { Find-RepoArtifact { param($page) Listing @() 0 } 'x' 'main' $null } 'no artifact x built on main'

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
