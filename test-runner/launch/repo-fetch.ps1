<#
Gets a file of this repository at a commit, or the newest build of a CI artifact, onto the test VM, with the VM's
read token when there is one: what raw.githubusercontent.com and nightly.link did while the repository was public,
and what they can't once it is private (test-runner/launch/README.md, "Fetching from the repository").

  powershell -ExecutionPolicy Bypass -File C:\RSPS\bin\repo-fetch.ps1 -File test-runner/examples/x.json -Ref <sha> -OutFile C:\RSPS\x.json
  powershell -ExecutionPolicy Bypass -File C:\RSPS\bin\repo-fetch.ps1 -Artifact test-runner-jar -OutFile C:\RSPS\downloads\test-runner-jar.zip

The token is the first line of -TokenFile (C:\RSPS\github-token.txt by default) and is never printed. A file needs
no token while the repository is public; an artifact always does, as GitHub serves artifacts to signed-in users only.
Exits with 0 when the file is written whole, 1 otherwise, the reason printed.
#>
[CmdletBinding(DefaultParameterSetName = 'File')]
param(
	[Parameter(Mandatory = $true, ParameterSetName = 'File')] [string] $File,
	[Parameter(ParameterSetName = 'File')] [string] $Ref = 'main',
	[Parameter(Mandatory = $true, ParameterSetName = 'Artifact')] [string] $Artifact,
	[Parameter(ParameterSetName = 'Artifact')] [string] $Branch = 'main',
	[Parameter(ParameterSetName = 'Artifact')] [string] $Commit,
	[Parameter(Mandatory = $true)] [string] $OutFile,
	[string] $TokenFile = 'C:\RSPS\github-token.txt'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'repo-fetch-functions.ps1')

# Windows PowerShell 5.1 may offer TLS 1.0 only, which GitHub refuses.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.Net.Http

# GET $Url, following redirects by hand so the token stays on the API's host (Get-RedirectHeaders), into $OutFile
# when given (written as .part and renamed, so a cut download leaves no half file), else as text.
function Invoke-RepoGet([string] $Url, [hashtable] $Headers, [string] $OutFile)
{
	$handler = New-Object System.Net.Http.HttpClientHandler
	$handler.AllowAutoRedirect = $false
	$client = New-Object System.Net.Http.HttpClient($handler)
	$client.Timeout = [TimeSpan]::FromMinutes(10)
	try
	{
		$first = [Uri] $Url
		$at = $first
		$send = $Headers
		for ($hops = 0; ; $hops++)
		{
			$request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, $at)
			foreach ($key in $send.Keys) { [void] $request.Headers.TryAddWithoutValidation($key, [string] $send[$key]) }
			$response = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
			$code = [int] $response.StatusCode
			if ($code -lt 300 -or $code -ge 400 -or -not $response.Headers.Location) { break }
			if ($hops -ge 5) { throw "more than 5 redirects from $(Format-SafeUrl $first)" }
			$next = $response.Headers.Location
			if (-not $next.IsAbsoluteUri) { $next = New-Object Uri($at, $next) }
			$response.Dispose()
			$send = Get-RedirectHeaders $Headers $first $next
			$at = $next
		}
		if (-not $response.IsSuccessStatusCode)
		{
			$hint = if ($code -eq 404 -or $code -eq 401) { ' (a private repository answers 404 without a token that may read it)' } else { '' }
			throw "$code $($response.ReasonPhrase) from $(Format-SafeUrl $at)$hint"
		}
		if (-not $OutFile) { return $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() }
		$part = "$OutFile.part"
		$stream = [IO.File]::Create($part)
		try { $response.Content.CopyToAsync($stream).GetAwaiter().GetResult() }
		finally { $stream.Dispose() }
		Move-Item -LiteralPath $part -Destination $OutFile -Force
	}
	finally { $client.Dispose() }
}

try
{
	$token = Read-RepoToken $TokenFile
	$folder = Split-Path -Parent ([IO.Path]::GetFullPath($OutFile))
	if ($folder) { New-Item -ItemType Directory -Force -Path $folder | Out-Null }
	if ($PSCmdlet.ParameterSetName -eq 'File')
	{
		Invoke-RepoGet (Get-RepoFileUrl $File $Ref) (Get-RepoHeaders $token 'application/vnd.github.raw+json') $OutFile
		Write-Host "repo-fetch: $File at $Ref -> $OutFile ($((Get-Item -LiteralPath $OutFile).Length) bytes)"
	}
	else
	{
		if (-not $token) { throw "an artifact needs the read token in $TokenFile" }
		$headers = Get-RepoHeaders $token 'application/vnd.github+json'
		$getPage = { param($page) (Invoke-RepoGet (Get-RepoArtifactsUrl $Artifact $page) $headers $null) | ConvertFrom-Json }
		$chosen = Find-RepoArtifact $getPage $Artifact $Branch $Commit
		Invoke-RepoGet $chosen.archive_download_url $headers $OutFile
		Write-Host "repo-fetch: $Artifact from $Branch at $($chosen.workflow_run.head_sha) -> $OutFile ($((Get-Item -LiteralPath $OutFile).Length) bytes)"
	}
	exit 0
}
catch
{
	Write-Host "repo-fetch: FAILED: $($_.Exception.Message)"
	exit 1
}
