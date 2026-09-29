[CmdletBinding()]
param([string]$BaseUrl = 'http://127.0.0.1:8080')
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:POLICY_STAFF_TOKEN)) { throw 'Set POLICY_STAFF_TOKEN to a staff session token in this terminal. Do not commit or share it.' }
$headers = @{ Authorization = "Bearer $env:POLICY_STAFF_TOKEN" }
$data = Get-Content -LiteralPath (Join-Path $PSScriptRoot '../docs/knowledge/demo-policies.json') -Raw -Encoding utf8 | ConvertFrom-Json
$existing = Invoke-RestMethod -Uri "$BaseUrl/api/staff/policies" -Headers $headers
foreach ($item in $data) {
    if ($existing | Where-Object { $_.policyKey -eq $item.policyKey -and $_.scope -eq $item.scope -and $_.version -eq $item.version }) {
        Write-Output "Already exists: $($item.policyKey)"; continue
    }
    $body = @{ policyKey=$item.policyKey;version=$item.version;title=$item.title;scope=$item.scope;content=$item.content;effectiveFrom=(Get-Date).ToString('yyyy-MM-dd');effectiveUntil=(Get-Date).AddYears(1).ToString('yyyy-MM-dd') } | ConvertTo-Json
    $null = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/staff/policies" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
    Write-Output "Draft created: $($item.policyKey)"
}
Write-Output 'Review and publish the drafts on /knowledge. Import does not call embedding or publish automatically.'
