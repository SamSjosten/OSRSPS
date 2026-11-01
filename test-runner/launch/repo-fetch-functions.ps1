<#
The parts of repo-fetch.ps1 that can be checked without the network, dot-sourced by it and run by
repo-fetch-functions.tests.ps1 in CI (pwsh on Linux): the token file, the API's addresses and headers, which build's
artifact to take, and which headers follow a redirect. Nothing here makes a request itself.
#>

$script:RepoApi = 'https://api.github.com/repos/SamSjosten/OSRSPS'

# The VM's read token (a fine-grained token of Sam's: this repository only, Contents and Actions read), or $null when
# the file is missing or empty. It is the file's first line; it is never printed, and nothing here writes it anywhere.
function Read-RepoToken([string] $Path)
{
	if (-not $Path -or -not (Test-Path -LiteralPath $Path)) { return $null }
	$first = Get-Content -LiteralPath $Path -TotalCount 1
	if ($null -eq $first) { return $null }
	$token = ([string] $first).Trim()
	if ($token -eq '') { return $null }
	$token
}

# The headers of a request to the API: the token only when there is one, so the same call works on the public
# repository without it.
function Get-RepoHeaders([string] $Token, [string] $Accept)
{
	$headers = @{ 'Accept' = $Accept; 'X-GitHub-Api-Version' = '2022-11-28'; 'User-Agent' = 'osrsps-test-vm' }
	if ($Token) { $headers['Authorization'] = "Bearer $Token" }
	$headers
}

# A file of the repository at a commit or branch, by the contents API (with the raw media type it answers with the
# file's bytes). The path is the repository's, with / or \; a path that leaves it, or names a drive, is refused.
function Get-RepoFileUrl([string] $Path, [string] $Ref)
{
	$clean = ($Path -replace '\\', '/').Trim('/')
	if ($clean -eq '' -or $clean -match '^[A-Za-z]:' -or ($clean -split '/') -contains '..')
	{
		throw "not a file in the repository: $Path"
	}
	if (-not $Ref) { throw 'no commit or branch given' }
	$segments = @($clean -split '/' | ForEach-Object { [Uri]::EscapeDataString($_) })
	"$script:RepoApi/contents/$($segments -join '/')?ref=$([Uri]::EscapeDataString($Ref))"
}

$script:ArtifactsPerPage = 100

# One page of the listing of a named artifact's builds, newest first by the API's own order.
function Get-RepoArtifactsUrl([string] $Name, [int] $Page)
{
	"$script:RepoApi/actions/artifacts?name=$([Uri]::EscapeDataString($Name))&per_page=$script:ArtifactsPerPage&page=$Page"
}

# The artifact to download from one page of that listing (the parsed JSON), or $null when the page has none: the
# newest of that name built on that branch of this repository (a fork's pull request may come from a branch of the
# same name, and its build is no build of ours) that hasn't expired, and when $Commit is given, built from the commit
# it starts with. Newest is the highest id, which GitHub hands out in order, so no date has to be read in this
# PowerShell's culture.
function Select-RepoArtifact([object] $Listing, [string] $Name, [string] $Branch, [string] $Commit)
{
	$found = @($Listing.artifacts | Where-Object {
		$_.name -eq $Name -and -not $_.expired -and $_.workflow_run.head_branch -eq $Branch -and
			$null -ne $_.workflow_run.repository_id -and $_.workflow_run.head_repository_id -eq $_.workflow_run.repository_id -and
			(-not $Commit -or ([string] $_.workflow_run.head_sha).StartsWith($Commit, [StringComparison]::OrdinalIgnoreCase))
	})
	if ($found.Count -eq 0) { return $null }
	$found | Sort-Object { [long] $_.id } -Descending | Select-Object -First 1
}

# The artifact to download, page by page through the listing ($GetPage gets page n, from 1, as parsed JSON): the
# listing is newest first, so the first page with a match holds the newest. Pull requests' and other branches' builds
# share the listing, so the one asked for may be many pages in. Throws when no page has one.
function Find-RepoArtifact([scriptblock] $GetPage, [string] $Name, [string] $Branch, [string] $Commit)
{
	$seen = 0
	for ($page = 1; $page -le 100; $page++)
	{
		$listing = & $GetPage $page
		$items = @($listing.artifacts)
		$chosen = Select-RepoArtifact $listing $Name $Branch $Commit
		if ($null -ne $chosen) { return $chosen }
		$seen += $items.Count
		if ($items.Count -lt $script:ArtifactsPerPage -or $seen -ge [long] $listing.total_count) { break }
	}
	$from = if ($Commit) { "$Branch at $Commit" } else { $Branch }
	throw "no artifact $Name built on $from of this repository that hasn't expired"
}

# The headers that go on to where a redirect points: all of them on the API's own host, and none of the token
# anywhere else (an artifact's download redirects to signed storage that refuses, and must never see, the token).
function Get-RedirectHeaders([hashtable] $Headers, [Uri] $TokenHost, [Uri] $To)
{
	$next = @{}
	foreach ($key in $Headers.Keys)
	{
		if ($key -eq 'Authorization' -and $To.Host -ne $TokenHost.Host) { continue }
		$next[$key] = $Headers[$key]
	}
	$next
}

# An address fit for a message: its query left out, which on signed storage is the signature.
function Format-SafeUrl([Uri] $Url)
{
	$Url.GetLeftPart([UriPartial]::Path)
}
