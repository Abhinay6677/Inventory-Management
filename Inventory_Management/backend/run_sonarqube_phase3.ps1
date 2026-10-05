param(
    [string]$SonarHostUrl = $(if ([string]::IsNullOrWhiteSpace($env:SONAR_HOST_URL)) { "http://localhost:9001" } else { $env:SONAR_HOST_URL }),
    [string]$SonarToken = $env:SONAR_TOKEN,
    [string]$ProjectKey = "inventory-management-phase3",
    [string]$ProjectName = "Inventory management phase 3",
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

    Write-Host "[2/4] Running Phase 3 tests with coverage..." -ForegroundColor Cyan
    & $PythonExe -m pytest -p no:cacheprovider rag/tests/test_phase3_spec_cases.py rag/tests/test_phase3_additional_25cases.py --cov=agent --cov-report=xml:coverage-phase3.xml --cov-report=term-missing
    if ($LASTEXITCODE -ne 0) { throw "pytest failed with exit code $LASTEXITCODE" }

    # Prevent scanner traversal issues on locked pytest cache files in Windows.
    if (Test-Path ".pytest_cache") {
        Remove-Item ".pytest_cache" -Recurse -Force -ErrorAction SilentlyContinue
    }

    Write-Host "[3/4] Running SonarQube scan for Phase 3..." -ForegroundColor Cyan
        if ($useMavenScanner) {
                Write-Host "sonar-scanner CLI not found, using Maven Sonar plugin fallback..." -ForegroundColor DarkYellow
            $agentDir = Join-Path $repoRoot "agent"
            $agentPom = Join-Path $agentDir "pom-sonar-phase3.xml"
            if (-not (Test-Path $agentPom)) {
                throw "Missing Phase 3 sonar runner pom at $agentPom"
            }
                $sonarArgs = @(
                "-f",
                $agentPom,
                "org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar",
                        "-Dsonar.host.url=$SonarHostUrl",
                        "-Dsonar.token=$SonarToken",
                        "-Dsonar.projectKey=$ProjectKey",
                        "-Dsonar.projectName=$ProjectName",
                        "-Dsonar.projectVersion=3.0",
                "-Dsonar.sources=.",
                "-Dsonar.python.coverage.reportPaths=$repoRoot/coverage-phase3.xml",
                    "-Dsonar.python.version=3.14",
                        "-Dsonar.sourceEncoding=UTF-8"
                )
            Push-Location $agentDir
            try {
                & mvn @sonarArgs
            }
            finally {
                Pop-Location
            }
        }
        else {
                & $SonarScannerExe `
                    -Dproject.settings=sonar-project-phase3.properties `
                    -Dsonar.host.url=$SonarHostUrl `
                    -Dsonar.token=$SonarToken `
                    -Dsonar.projectKey=$ProjectKey `
                    -Dsonar.projectName=$ProjectName
        }
    if ($LASTEXITCODE -ne 0) { throw "sonar-scanner failed with exit code $LASTEXITCODE" }

    Write-Host "[4/4] Phase 3 scan complete." -ForegroundColor Green
    Write-Host "Dashboard: $SonarHostUrl/dashboard?id=$ProjectKey" -ForegroundColor Green
}
finally {
    Pop-Location
}
