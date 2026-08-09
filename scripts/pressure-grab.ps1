param(
    [string]$BaseUrl = "http://localhost:18086",
    [int]$Users = 20,
    [long]$StartUserId = 9000,
    [long]$SessionId = 101,
    [long]$TicketCategoryId = 1001
)

$startedAt = Get-Date

$jobs = 1..$Users | ForEach-Object {
    $userId = $StartUserId + $_
    Start-Job -ScriptBlock {
        param($BaseUrl, $UserId, $SessionId, $TicketCategoryId)

        $body = @{
            userId = $UserId
            sessionId = $SessionId
            ticketCategoryId = $TicketCategoryId
        } | ConvertTo-Json

        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/orders/grab" -ContentType "application/json" -Body $body | Out-Null
            $watch.Stop()
            [pscustomobject]@{
                userId = $UserId
                ok = $true
                status = 200
                ms = $watch.ElapsedMilliseconds
                message = "OK"
            }
        } catch {
            $watch.Stop()
            $status = 0
            if ($_.Exception.Response) {
                $status = [int]$_.Exception.Response.StatusCode
            }
            [pscustomobject]@{
                userId = $UserId
                ok = $false
                status = $status
                ms = $watch.ElapsedMilliseconds
                message = $_.Exception.Message
            }
        }
    } -ArgumentList $BaseUrl, $userId, $SessionId, $TicketCategoryId
}

$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
$elapsedMs = [int]((Get-Date) - $startedAt).TotalMilliseconds
$okResults = @($results | Where-Object { $_.ok })
$failedResults = @($results | Where-Object { -not $_.ok })
$sortedLatency = @($results | Sort-Object ms | Select-Object -ExpandProperty ms)
$averageLatency = if ($results.Count -gt 0) { [math]::Round(($results | Measure-Object ms -Average).Average, 2) } else { 0 }
$p95Index = if ($sortedLatency.Count -gt 0) { [math]::Max([math]::Ceiling($sortedLatency.Count * 0.95) - 1, 0) } else { 0 }
$p95Latency = if ($sortedLatency.Count -gt 0) { $sortedLatency[$p95Index] } else { 0 }

Write-Host "EventRush local pressure baseline"
Write-Host "baseUrl=$BaseUrl users=$Users sessionId=$SessionId ticketCategoryId=$TicketCategoryId"
Write-Host "success=$($okResults.Count) failed=$($failedResults.Count) elapsedMs=$elapsedMs"
Write-Host "avgMs=$averageLatency p95Ms=$p95Latency"

if ($failedResults.Count -gt 0) {
    Write-Host "failed status distribution:"
    $failedResults | Group-Object status | Sort-Object Name | ForEach-Object {
        Write-Host "  status=$($_.Name) count=$($_.Count)"
    }
}
