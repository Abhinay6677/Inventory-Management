param(
    [string]$ProjectKey = "inventory-management-phase4",
    [string]$BaseUrl = $(if ([string]::IsNullOrWhiteSpace($env:SONAR_HOST_URL)) { "http://localhost:9001" } else { $env:SONAR_HOST_URL }),
    [string]$Token = $env:SONAR_TOKEN
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Token)) {
    throw "Missing Sonar token. Set SONAR_TOKEN in terminal or pass -Token."
}

$pair = "$($Token):"
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $auth" }

function Get-Sonar($path) { Invoke-RestMethod -Uri ("$BaseUrl$path") -Headers $headers -Method Get }

$project = Get-Sonar "/api/components/show?component=$ProjectKey"
$analysis = Get-Sonar "/api/project_analyses/search?project=$ProjectKey&ps=1"
$qgStatus = Get-Sonar "/api/qualitygates/project_status?projectKey=$ProjectKey"
$issues = Get-Sonar "/api/issues/search?components=$ProjectKey&resolved=false&ps=100"
$measures = Get-Sonar "/api/measures/component?component=$ProjectKey&metricKeys=alert_status,coverage,ncloc,code_smells,bugs,vulnerabilities,security_hotspots,duplicated_lines_density"

$statusClass = if ($qgStatus.projectStatus.status -eq "OK") { "ok" } else { "err" }
$generatedAt = Get-Date
$projectDisplayName = if ($project.component.name) { $project.component.name } else { $ProjectKey }
$latestAnalysisDate = if ($analysis.analyses -and $analysis.analyses.Count -gt 0) { $analysis.analyses[0].date } else { "(n/a)" }
$projectVersion = if ($analysis.analyses -and $analysis.analyses.Count -gt 0) { $analysis.analyses[0].projectVersion } else { "(n/a)" }

$rows = $measures.component.measures | ForEach-Object {
    "<tr><td>$($_.metric)</td><td>$($_.value)</td></tr>"
}

$issueRows = if ($issues.total -eq 0) {
    "<tr><td colspan='4'>No open issues</td></tr>"
} else {
    $issues.issues | ForEach-Object {
        "<tr><td>$($_.severity)</td><td>$($_.type)</td><td>$($_.component)</td><td>$($_.message)</td></tr>"
    }
}

$html = @"
<!doctype html>
<html>
<head>
<meta charset='utf-8'>
<title>Inventory management phase 4 - SonarQube Report</title>
<style>
body { font-family: Segoe UI, Arial, sans-serif; margin: 24px; }
.card { border: 1px solid #ddd; border-radius: 8px; padding: 12px; margin-bottom: 16px; }
.ok { color: #0b7a34; font-weight: 700; }
.err { color: #a11; font-weight: 700; }
table { border-collapse: collapse; width: 100%; }
th, td { border: 1px solid #ccc; padding: 6px; text-align: left; }
th { background: #f6f8fb; }
</style>
</head>
<body>
<h1>Inventory management phase 4 - SonarQube Report</h1>
<p>Project: <b>$projectDisplayName</b><br/>Project key: <b>$ProjectKey</b><br/>Latest analysis: $latestAnalysisDate<br/>Project version: $projectVersion<br/>Generated at: $generatedAt<br/>Dashboard: $BaseUrl/dashboard?id=$ProjectKey</p>
<div class='card'>
<h2>Quality Gate</h2>
<p>Status: <span class='$statusClass'>$($qgStatus.projectStatus.status)</span></p>
</div>
<div class='card'>
<h2>Key Measures</h2>
<table>
<tr><th>Metric</th><th>Value</th></tr>
$($rows -join "`n")
</table>
</div>
<div class='card'>
<h2>Open Issues</h2>
<table>
<tr><th>Severity</th><th>Type</th><th>Component</th><th>Message</th></tr>
$($issueRows -join "`n")
</table>
</div>
</body>
</html>
"@

$reportPath = Join-Path (Get-Location) ("SonarQube_Phase4_Report_{0}.html" -f (Get-Date -Format "yyyy-MM-dd_HHmmss"))
Set-Content -Path $reportPath -Value $html -Encoding UTF8
Write-Host "Report generated: $reportPath" -ForegroundColor Green
