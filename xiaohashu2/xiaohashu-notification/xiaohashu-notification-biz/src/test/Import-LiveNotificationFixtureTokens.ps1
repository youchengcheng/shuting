param(
    [string]$FixturePath = (Join-Path $PSScriptRoot '..\..\target\notification-live-fixture\fixture.json')
)
# Sets only dedicated process variables; never returns tokens or decrypted JSON.
$ErrorActionPreference = 'Stop'
$fixture = Get-Content -LiteralPath $FixturePath -Raw | ConvertFrom-Json
if (-not $fixture.dedicated -or -not ([string]$fixture.runPrefix).StartsWith('notification_live_')) { throw 'Dedicated fixture manifest required.' }
Add-Type -AssemblyName System.Security
$encryptedBytes = [IO.File]::ReadAllBytes([IO.Path]::GetFullPath([string]$fixture.tokenDpapiPath))
$privateBytes = $null
$privateTokens = $null
try {
    $privateBytes = [Security.Cryptography.ProtectedData]::Unprotect($encryptedBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $privateTokens = [Text.Encoding]::UTF8.GetString($privateBytes) | ConvertFrom-Json
    foreach ($actor in @('A', 'B', 'C')) {
        $tokenName = [string]$fixture.actors.$actor.tokenEnv
        if ($tokenName -cne "NOTIFICATION_LIVE_TOKEN_$actor") { throw 'Dedicated token variable mismatch.' }
        $privateValue = [string]$privateTokens.$tokenName
        $previousValue = [Environment]::GetEnvironmentVariable($tokenName, 'Process')
        if ($privateValue -cnotmatch '^[A-Za-z0-9_-]{128}$' -or ($previousValue -and $previousValue -cne $privateValue)) {
            throw 'Encrypted fixture tokens are invalid or would overwrite another token.'
        }
    }
    foreach ($actor in @('A', 'B', 'C')) {
        $tokenName = [string]$fixture.actors.$actor.tokenEnv
        [Environment]::SetEnvironmentVariable($tokenName, [string]$privateTokens.$tokenName, 'Process')
    }
} finally {
    [Array]::Clear($encryptedBytes, 0, $encryptedBytes.Length)
    if ($privateBytes) { [Array]::Clear($privateBytes, 0, $privateBytes.Length) }
    $privateTokens = $null
    $privateValue = $null
    $previousValue = $null
}
