param(
    [string]$SonarHostUrl = $(if ([string]::IsNullOrWhiteSpace($env:SONAR_HOST_URL)) { "http://localhost:9001" } else { $env:SONAR_HOST_URL }),
    [string]$SonarToken = $env:SONAR_TOKEN,
    [string]$ProjectKey = "inventory-management-phase2",
    [string]$ProjectName = "Inventory management phase 2",
    [string]$PythonExe = "python"
)

$ErrorActionPreference = "Stop"
$repoRoot = "C:\sts-4.31.0.RELEASE\Inventory_Management"

if ([string]::IsNullOrWhiteSpace($SonarToken)) {
    throw "Missing Sonar token. Set SONAR_TOKEN or pass -SonarToken."
}

Push-Location $repoRoot
try {
    Write-Host "[1/4] Installing Phase 2 Python dependencies..." -ForegroundColor Cyan
    & $PythonExe -m pip install -r requirements-phase2.txt
    if ($LASTEXITCODE -ne 0) { throw "pip install failed with exit code $LASTEXITCODE" }

    Write-Host "[2/4] Running Phase 2 tests with coverage..." -ForegroundColor Cyan
    & $PythonExe -m pytest rag/tests/test_phase2_rag_25cases.py --cov=rag --cov-report=xml:coverage-phase2.xml --cov-report=term-missing
    if ($LASTEXITCODE -ne 0) { throw "pytest failed with exit code $LASTEXITCODE" }

    Write-Host "[3/4] Running SonarQube scan for Phase 2..." -ForegroundColor Cyan
    sonar-scanner `
      -Dproject.settings=sonar-project-phase2.properties `
      -Dsonar.host.url=$SonarHostUrl `
      -Dsonar.token=$SonarToken `
      -Dsonar.projectKey=$ProjectKey `
      -Dsonar.projectName=$ProjectName
    if ($LASTEXITCODE -ne 0) { throw "sonar-scanner failed with exit code $LASTEXITCODE" }

    Write-Host "[4/4] Phase 2 scan complete." -ForegroundColor Green
    Write-Host "Dashboard: $SonarHostUrl/dashboard?id=$ProjectKey" -ForegroundColor Green
}
finally {
    Pop-Location
}
