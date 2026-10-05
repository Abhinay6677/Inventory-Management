param(
    [string]$SonarHostUrl = $(if ([string]::IsNullOrWhiteSpace($env:SONAR_HOST_URL)) { "http://localhost:9001" } else { $env:SONAR_HOST_URL }),
    [string]$SonarToken = $env:SONAR_TOKEN,
    [string]$ProjectKey = "inventory-management-phase1",
    [string]$ProjectName = "Inventory Management Phase 1",
    [switch]$AutoStartSonar,
    [string]$DockerContainerName = "sonarqube",
    [int]$StartupTimeoutSeconds = 420
)

$ErrorActionPreference = "Stop"
$repoRoot = "C:\sts-4.31.0.RELEASE\Inventory_Management"

function Get-SonarUri {
    param([string]$Url)
    try {
        return [Uri]$Url
    }
    catch {
        throw "Invalid Sonar URL: $Url"
    }
}

function Start-SonarContainer {
    param(
        [string]$ContainerName,
        [int]$HostPort
    )

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker CLI not found. Install Docker Desktop or run SonarQube manually."
    }

    $existing = docker ps -a --filter "name=^/$ContainerName$" --format "{{.Names}}|{{.Status}}"

    if (-not [string]::IsNullOrWhiteSpace($existing)) {
        if ($existing -match "Up") {
            Write-Host "Sonar container '$ContainerName' is already running." -ForegroundColor DarkCyan
            return
        }

        Write-Host "Starting existing Sonar container '$ContainerName'..." -ForegroundColor Cyan
        docker start $ContainerName | Out-Null
        return
    }

    Write-Host "Creating Sonar container '$ContainerName' on port $HostPort..." -ForegroundColor Cyan
    docker run -d --name $ContainerName -p "${HostPort}:9000" sonarqube:lts-community | Out-Null
}

function Wait-SonarReady {
    param(
        [string]$BaseUrl,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $statusUrl = "$BaseUrl/api/system/status"

    Write-Host "Waiting for SonarQube at $BaseUrl ..." -ForegroundColor Cyan

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $statusUrl -Method Get -TimeoutSec 8
            if ($response.status -eq "UP") {
                Write-Host "SonarQube is UP." -ForegroundColor Green
                return
            }
            Write-Host "SonarQube status: $($response.status)" -ForegroundColor DarkYellow
        }
        catch {
            Write-Host "SonarQube not reachable yet..." -ForegroundColor DarkYellow
        }

        Start-Sleep -Seconds 5
    }

    throw "SonarQube did not become ready within $TimeoutSeconds seconds."
}

if ([string]::IsNullOrWhiteSpace($SonarHostUrl)) {
    throw "Missing Sonar host URL. Set SONAR_HOST_URL or pass -SonarHostUrl."
}

if ([string]::IsNullOrWhiteSpace($SonarToken)) {
    throw "Missing Sonar token. Set SONAR_TOKEN or pass -SonarToken."
}

$sonarUri = Get-SonarUri -Url $SonarHostUrl

if ($AutoStartSonar) {
    if ($sonarUri.Host -notin @("localhost", "127.0.0.1")) {
        throw "-AutoStartSonar is only supported for localhost URLs. Current URL: $SonarHostUrl"
    }

    Start-SonarContainer -ContainerName $DockerContainerName -HostPort $sonarUri.Port
}

Wait-SonarReady -BaseUrl $SonarHostUrl -TimeoutSeconds $StartupTimeoutSeconds

Push-Location $repoRoot
try {
    Write-Host "[1/3] Building frontend artifacts..." -ForegroundColor Cyan
    Push-Location (Join-Path $repoRoot "frontend")
    try {
        npm run build
        if ($LASTEXITCODE -ne 0) {
            throw "Frontend build failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }

    Write-Host "[2/3] Running backend tests and generating JaCoCo XML..." -ForegroundColor Cyan
    mvn clean verify
    if ($LASTEXITCODE -ne 0) {
        throw "Backend verify failed with exit code $LASTEXITCODE"
    }

    Write-Host "[3/3] Running SonarQube scan and waiting for quality gate..." -ForegroundColor Cyan
    $sonarArgs = @(
        "org.sonarsource.scanner.maven:sonar-maven-plugin:sonar",
        "-Dsonar.host.url=$SonarHostUrl",
        "-Dsonar.token=$SonarToken",
        "-Dsonar.projectKey=$ProjectKey",
        "-Dsonar.projectName=$ProjectName",
        "-Dsonar.qualitygate.wait=true"
    )
    & mvn @sonarArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Sonar scan failed with exit code $LASTEXITCODE"
    }

    Write-Host "Scan complete." -ForegroundColor Green
    Write-Host "Dashboard: $SonarHostUrl/dashboard?id=$ProjectKey" -ForegroundColor Green
}
finally {
    Pop-Location
}
