$ErrorActionPreference = 'Stop'
$projectKey = 'inventory-management-phase1'
$baseUrl = 'http://localhost:9001'
$token = 'squ_8f3e79a17b13bb08ad43049a5f8d00e634fe8e36'
$pair = "$($token):"
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $auth" }

function Get-Sonar($path) { Invoke-RestMethod -Uri ("$baseUrl$path") -Headers $headers -Method Get }

$project = Get-Sonar "/api/components/show?component=$projectKey"
$analysis = Get-Sonar "/api/project_analyses/search?project=$projectKey&ps=1"
$qgStatus = Get-Sonar "/api/qualitygates/project_status?projectKey=$projectKey"
$qgDef = Get-Sonar "/api/qualitygates/get_by_project?project=$projectKey"
$branches = Get-Sonar "/api/project_branches/list?project=$projectKey"
$ce = Get-Sonar "/api/ce/component?component=$projectKey"

$metricKeys = @(
  'alert_status','ncloc','lines','coverage','new_coverage','lines_to_cover','uncovered_lines','new_lines_to_cover','new_uncovered_lines',
  'tests','test_success_density','test_failures','test_errors','skipped_tests',
  'code_smells','bugs','vulnerabilities','security_hotspots',
  'reliability_rating','security_rating','sqale_rating','duplicated_lines_density','new_duplicated_lines_density'
) -join ','
$measures = Get-Sonar "/api/measures/component?component=$projectKey&metricKeys=$metricKeys"

$issuesFacet = Get-Sonar "/api/issues/search?components=$projectKey&resolved=false&ps=1&facets=types,severities,impactSoftwareQualities,rules"
$issuesList = Get-Sonar "/api/issues/search?components=$projectKey&resolved=false&ps=100"

$filesCoverage = Get-Sonar "/api/measures/component_tree?component=$projectKey&metricKeys=coverage,lines_to_cover,uncovered_lines&strategy=leaves&qualifiers=FIL&ps=500"
$filesRows = foreach($c in $filesCoverage.components){
  $m = @{}
  foreach($x in $c.measures){ $m[$x.metric] = $x.value }
  $lt = [int]($m['lines_to_cover'])
  $uc = [int]($m['uncovered_lines'])
  if($lt -gt 0){
    [pscustomobject]@{
      File = $c.path
      CoveragePct = [math]::Round((($lt-$uc)*100.0)/$lt,2)
      LinesToCover = $lt
      UncoveredLines = $uc
    }
  }
}
$topUncovered = $filesRows | Sort-Object UncoveredLines -Descending | Select-Object -First 25

$measureTable = $measures.component.measures | ForEach-Object {
  [pscustomobject]@{ Metric = $_.metric; Value = if([string]::IsNullOrWhiteSpace($_.value)){ '(n/a)' } else { $_.value } }
} | Sort-Object Metric

$qgCondTable = $qgStatus.projectStatus.conditions | ForEach-Object {
  [pscustomobject]@{
    Metric = $_.metricKey
    Status = $_.status
    Actual = if([string]::IsNullOrWhiteSpace($_.actualValue)){ '(n/a)' } else { $_.actualValue }
    Threshold = if([string]::IsNullOrWhiteSpace($_.errorThreshold)){ '(n/a)' } else { $_.errorThreshold }
  }
}

$facetRows = foreach($f in $issuesFacet.facets){
  foreach($v in $f.values){
    [pscustomobject]@{ Facet = $f.property; Value = $v.val; Count = $v.count }
  }
}

$issueRows = $issuesList.issues | ForEach-Object {
  [pscustomobject]@{
    Key = $_.key
    Rule = $_.rule
    Severity = $_.severity
    Type = $_.type
    Component = $_.component
    Line = if($_.line){ $_.line } else { '' }
    Message = $_.message
    Status = $_.status
    Created = $_.creationDate
  }
}

$branchRows = $branches.branches | ForEach-Object {
  [pscustomobject]@{
    Name = $_.name
    IsMain = $_.isMain
    Type = $_.type
    Status = $_.status.qualityGateStatus
    AnalysisDate = $_.analysisDate
  }
}

$taskRows = $ce.queue + $ce.current + $ce.tasks | ForEach-Object {
  [pscustomobject]@{
    Id = $_.id
    Type = $_.type
    Status = $_.status
    SubmittedAt = $_.submittedAt
    StartedAt = $_.startedAt
    ExecutedAt = $_.executedAt
  }
}
if(-not $taskRows){ $taskRows = @([pscustomobject]@{ Id='(none)'; Type=''; Status=''; SubmittedAt=''; StartedAt=''; ExecutedAt='' }) }

$now = Get-Date
$analysisDate = if($analysis.analyses -and $analysis.analyses.Count -gt 0){ $analysis.analyses[0].date } else { '(n/a)' }
$version = if($analysis.analyses -and $analysis.analyses.Count -gt 0){ $analysis.analyses[0].projectVersion } else { '(n/a)' }
$gateName = if($qgDef.qualityGate.name){ $qgDef.qualityGate.name } else { '(n/a)' }

$style = @"
<style>
body { font-family: 'Segoe UI', Arial, sans-serif; margin: 28px; color: #111; }
h1, h2 { margin-bottom: 6px; }
.meta { margin: 0 0 12px 0; color: #444; }
.card { border: 1px solid #ddd; border-radius: 8px; padding: 12px; margin-bottom: 14px; }
table { border-collapse: collapse; width: 100%; margin-top: 8px; margin-bottom: 16px; font-size: 12px; }
th, td { border: 1px solid #ccc; padding: 6px 8px; vertical-align: top; }
th { background: #f5f7fb; text-align: left; }
.ok { color: #0b7a34; font-weight: 600; }
.err { color: #a11; font-weight: 600; }
.small { font-size: 12px; color: #555; }
</style>
"@

$summaryStatusClass = if($qgStatus.projectStatus.status -eq 'OK'){ 'ok' } else { 'err' }

$html = @()
$html += '<!doctype html><html><head><meta charset="utf-8"><title>SonarQube Final Report</title>'
$html += $style
$html += '</head><body>'
$html += "<h1>SonarQube Final Report</h1>"
$html += "<p class='meta'>Project: <b>$($project.component.name)</b> ($projectKey)<br/>Generated: $($now.ToString('yyyy-MM-dd HH:mm:ss zzz'))<br/>Dashboard: $baseUrl/dashboard?id=$projectKey</p>"
$html += "<div class='card'><h2>Executive Summary</h2><p>Quality Gate: <span class='$summaryStatusClass'>$($qgStatus.projectStatus.status)</span><br/>Quality Gate Name: <b>$gateName</b><br/>Latest Analysis Date: <b>$analysisDate</b><br/>Project Version: <b>$version</b><br/>Open Issues: <b>$($issuesList.total)</b></p></div>"
$html += "<div class='card'><h2>Quality Gate Conditions</h2>"
$html += ($qgCondTable | ConvertTo-Html -Fragment)
$html += "</div>"
$html += "<div class='card'><h2>Key Measures</h2>"
$html += ($measureTable | ConvertTo-Html -Fragment)
$html += "</div>"
$html += "<div class='card'><h2>Branches</h2>"
$html += ($branchRows | ConvertTo-Html -Fragment)
$html += "</div>"
$html += "<div class='card'><h2>Issue Facets (Open Issues Breakdown)</h2>"
$html += ($facetRows | ConvertTo-Html -Fragment)
$html += "</div>"
$html += "<div class='card'><h2>Open Issues Detail</h2>"
if($issueRows.Count -eq 0){
  $html += "<p class='ok'>No open issues.</p>"
} else {
  $html += ($issueRows | ConvertTo-Html -Fragment)
}
$html += "</div>"
$html += "<div class='card'><h2>Top Files by Uncovered Lines</h2>"
$html += ($topUncovered | ConvertTo-Html -Fragment)
$html += "</div>"
$html += "<div class='card'><h2>Compute Engine Tasks</h2>"
$html += ($taskRows | ConvertTo-Html -Fragment)
$html += "</div>"
$html += "<p class='small'>Report source: SonarQube Web API extracted from project dashboard context.</p>"
$html += '</body></html>'

$htmlPath = Join-Path (Get-Location) ("SonarQube_Final_Report_{0}.html" -f $now.ToString('yyyy-MM-dd_HHmmss'))
$pdfPath = [System.IO.Path]::ChangeExtension($htmlPath, '.pdf')
$html -join "`r`n" | Set-Content -Path $htmlPath -Encoding UTF8

$fileUri = [System.Uri]::new($htmlPath).AbsoluteUri
& msedge --headless=new --disable-gpu --print-to-pdf="$pdfPath" "$fileUri" | Out-Null

"HTML=$htmlPath"
"PDF=$pdfPath"
"PDF_EXISTS=$([bool](Test-Path $pdfPath))"
if(Test-Path $pdfPath){
  $fi = Get-Item $pdfPath
  "PDF_SIZE_BYTES=$($fi.Length)"
}
