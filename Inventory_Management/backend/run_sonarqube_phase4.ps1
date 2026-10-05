param(
    [string]$SonarHostUrl = $(if ([string]::IsNullOrWhiteSpace($env:SONAR_HOST_URL)) { "http://localhost:9001" } else { $env:SONAR_HOST_URL }),
    [string]$SonarToken = $env:SONAR_TOKEN,
    [string]$ProjectKey = "inventory-management-phase4",
    [string]$ProjectName = "Inventory management phase 4",
    [string]$PythonExe = "python",
    [string]$SonarScannerExe = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = "C:\sts-4.31.0.RELEASE\Inventory_Management\backend"

if ([string]::IsNullOrWhiteSpace($SonarToken)) {
    throw "Missing Sonar token. Set SONAR_TOKEN in terminal or pass -SonarToken."
}

$useMavenScanner = $false
if ([string]::IsNullOrWhiteSpace($SonarScannerExe)) {
    $scannerOnPath = Get-Command sonar-scanner -ErrorAction SilentlyContinue
    if ($scannerOnPath) {
        $SonarScannerExe = $scannerOnPath.Source
    }
    else {
        $bundledScanner = Join-Path $repoRoot "tools\sonar-scanner\bin\sonar-scanner.bat"
        if (Test-Path $bundledScanner) {
            $SonarScannerExe = $bundledScanner
        }
        else {
            $useMavenScanner = $true
        }
    }
}

Push-Location $repoRoot
try {
    Write-Host "[1/4] Installing Python dependencies..." -ForegroundColor Cyan
    & $PythonExe -m pip install -r requirements-phase2.txt
    if ($LASTEXITCODE -ne 0) { throw "pip install failed with exit code $LASTEXITCODE" }

    Write-Host "[2/4] Running Phase 4 tests with coverage..." -ForegroundColor Cyan
    & $PythonExe -m pytest -p no:cacheprovider rag/tests/test_phase4_spec_cases.py rag/tests/test_phase4_additional_25cases.py --cov=mcp_server --cov-report=xml:coverage-phase4.xml --cov-report=term-missing
    if ($LASTEXITCODE -ne 0) { throw "pytest failed with exit code $LASTEXITCODE" }

    if (Test-Path ".pytest_cache") {
        Remove-Item ".pytest_cache" -Recurse -Force -ErrorAction SilentlyContinue
    }

    Write-Host "[3/4] Running SonarQube scan for Phase 4..." -ForegroundColor Cyan
    if ($useMavenScanner) {
        Write-Host "sonar-scanner CLI not found, using Maven Sonar plugin fallback..." -ForegroundColor DarkYellow
        $scanBaseDir = Join-Path $repoRoot "mcp_server"
        $runnerPom = Join-Path $scanBaseDir "pom-sonar-phase4.xml"
        if (-not (Test-Path $runnerPom)) {
            throw "Missing Phase 4 sonar runner pom at $runnerPom"
        }
        $sonarArgs = @(
            "-f",
            $runnerPom,
            "org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar",
            "-Dsonar.host.url=$SonarHostUrl",
            "-Dsonar.token=$SonarToken",
            "-Dsonar.projectKey=$ProjectKey",
            "-Dsonar.projectName=$ProjectName",
            "-Dsonar.projectVersion=4.0",
            "-Dsonar.sources=.",
            "-Dsonar.exclusions=**/__pycache__/**,**/*.pyc",
            "-Dsonar.python.coverage.reportPaths=$repoRoot/coverage-phase4.xml",
            "-Dsonar.python.version=3.14",
            "-Dsonar.sourceEncoding=UTF-8"
        )
        & mvn @sonarArgs
    }
    else {
        & $SonarScannerExe `
            -Dproject.settings=sonar-project-phase4.properties `
            -Dsonar.host.url=$SonarHostUrl `
            -Dsonar.token=$SonarToken `
            -Dsonar.projectKey=$ProjectKey `
            -Dsonar.projectName=$ProjectName
    }
    if ($LASTEXITCODE -ne 0) { throw "sonar-scanner failed with exit code $LASTEXITCODE" }

    Write-Host "[4/4] Phase 4 scan complete." -ForegroundColor Green
    Write-Host "Dashboard: $SonarHostUrl/dashboard?id=$ProjectKey" -ForegroundColor Green
}
finally {
    Pop-Location
}
