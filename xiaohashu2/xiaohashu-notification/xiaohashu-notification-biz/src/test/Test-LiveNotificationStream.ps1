param([string]$OutputPath = (Join-Path $PSScriptRoot '..\..\target\live-sse-checks.json'))
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Import-LiveNotificationFixtureTokens.ps1')
$client = [Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(45)
$token = [Environment]::GetEnvironmentVariable('NOTIFICATION_LIVE_TOKEN_A')
$checks = [Collections.Generic.List[object]]::new()
$passed = $false
try {
    foreach ($endpoint in @('http://127.0.0.1:8000/notification/stream', 'http://127.0.0.1:5173/api/notification/stream')) {
        $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, $endpoint)
        $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
        $response = $client.SendAsync($request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
        try {
            if ([int]$response.StatusCode -ne 200 -or $response.Content.Headers.ContentType.MediaType -ne 'text/event-stream') { throw 'SSE response did not match the protocol.' }
            $reader = [IO.StreamReader]::new($response.Content.ReadAsStreamAsync().GetAwaiter().GetResult())
            $cancel = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(40))
            $ready = $false
            $heartbeat = $false
            $events = [Collections.Generic.List[string]]::new()
            try {
                # The first scheduled tick can be changed when the connection has a new revision.
                while (-not ($ready -and $heartbeat)) {
                    $line = $reader.ReadLineAsync($cancel.Token).AsTask().GetAwaiter().GetResult()
                    if ($null -eq $line) { throw 'SSE closed before a heartbeat.' }
                    if ($line.StartsWith('event:')) {
                        $event = $line.Substring(6).Trim()
                        if ($event -notin @('ready','changed','heartbeat')) { throw 'SSE emitted an unexpected control event.' }
                        $events.Add($event)
                        if ($event -eq 'ready') { $ready = $true }
                        if ($event -eq 'heartbeat') { $heartbeat = $true }
                    }
                }
            } finally { $cancel.Dispose(); $reader.Dispose() }
            $checks.Add(@{ endpoint=$endpoint; status=200; ready=$ready; heartbeat=$heartbeat; events=$events.ToArray() })
        } finally { $response.Dispose(); $request.Dispose() }
    }
    if ($checks.Count -ne 2) { throw 'Both the gateway and actual Vite proxy must pass.' }
    $passed = $true
} finally {
    $client.Dispose()
    $token = $null
    @{ passed=$passed; checks=$checks.ToArray(); tokensIncluded=$false } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputPath -Encoding utf8
}
'LIVE_SSE_CHECKS_PASS'
