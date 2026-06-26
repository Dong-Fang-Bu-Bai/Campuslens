param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Start", "Stop", "Verify")]
    [string]$Action
)

$ErrorActionPreference = "Stop"
if (Get-Variable PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runDir = Join-Path $root ".run"
$algorithmDir = Join-Path $root "algorithm"
$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"
$dockerDesktop = "D:\Tools\Docker\Docker\Docker Desktop.exe"
$dockerBin = "D:\Tools\Docker\Docker\resources\bin"
$mavenBin = "D:\Tools\apache-maven-3.9.11\bin"
$algorithmPython = if ($env:CAMPUSLENS_ALGORITHM_PYTHON) {
    $env:CAMPUSLENS_ALGORITHM_PYTHON
} elseif (Test-Path "D:\AnaConda\envs\campuslens-gpu\python.exe") {
    "D:\AnaConda\envs\campuslens-gpu\python.exe"
} else {
    Join-Path $algorithmDir ".venv\Scripts\python.exe"
}

$env:Path = "$mavenBin;$dockerBin;$env:Path"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

function Import-UserMailEnvironment {
    foreach ($name in @(
        "CAMPUSLENS_MAIL_HOST",
        "CAMPUSLENS_MAIL_PORT",
        "CAMPUSLENS_MAIL_USERNAME",
        "CAMPUSLENS_MAIL_PASSWORD",
        "CAMPUSLENS_MAIL_FROM"
    )) {
        if (-not (Get-Item "Env:$name" -ErrorAction SilentlyContinue)) {
            $value = [Environment]::GetEnvironmentVariable($name, "User")
            if ($value) { Set-Item "Env:$name" $value }
        }
    }
}

function Write-Step([string]$message) {
    Write-Host "[CampusLens] $message"
}

function Require-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Required command is unavailable: $name"
    }
}

function Test-Endpoint([string]$url, [switch]$Insecure) {
    $arguments = @("-f", "-sS", "--max-time", "5")
    if ($Insecure) { $arguments += "-k" }
    $arguments += $url
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & curl.exe @arguments 1>$null 2>$null
    $success = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $previousPreference
    return $success
}

function Wait-Endpoint(
    [string]$name,
    [string]$url,
    [int]$timeoutSeconds = 180,
    [switch]$Insecure
) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    do {
        if (Test-Endpoint $url -Insecure:$Insecure) {
            Write-Step "$name is ready: $url"
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "$name did not become ready: $url"
}

function Assert-Environment {
    foreach ($command in @("java", "mvn", "node", "npm", "docker", "curl.exe", "keytool.exe")) {
        Require-Command $command
    }
    foreach ($path in @(
        $algorithmPython,
        (Join-Path $algorithmDir ".env"),
        (Join-Path $algorithmDir "models\dinov2_model.pth"),
        (Join-Path $backendDir "pom.xml"),
        (Join-Path $frontendDir "package.json")
    )) {
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Required file is unavailable: $path"
        }
    }
    Write-Step "Environment check passed. Algorithm Python: $algorithmPython"
}

function Ensure-Docker {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    docker info 1>$null 2>$null
    $dockerReady = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $previousPreference
    if (-not $dockerReady) {
        if (-not (Test-Path -LiteralPath $dockerDesktop)) {
            throw "Docker Desktop is not running and was not found at $dockerDesktop"
        }
        Write-Step "Starting Docker Desktop..."
        Start-Process -FilePath $dockerDesktop -WindowStyle Hidden | Out-Null
        $deadline = (Get-Date).AddMinutes(5)
        do {
            Start-Sleep -Seconds 5
            $ErrorActionPreference = "Continue"
            docker info 1>$null 2>$null
            $dockerReady = $LASTEXITCODE -eq 0
            $ErrorActionPreference = $previousPreference
            if ($dockerReady) { break }
        } while ((Get-Date) -lt $deadline)
        if (-not $dockerReady) { throw "Docker Desktop did not become ready" }
    }

    Push-Location $root
    try {
        docker compose up -d mysql redis
        if ($LASTEXITCODE -ne 0) { throw "docker compose up failed" }
    } finally {
        Pop-Location
    }

    foreach ($container in @("campuslens-mysql", "campuslens-redis")) {
        $deadline = (Get-Date).AddMinutes(3)
        do {
            $health = docker inspect -f "{{.State.Health.Status}}" $container 2>$null
            if ($health -eq "healthy") { break }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $deadline)
        if ($health -ne "healthy") { throw "$container did not become healthy" }
    }
    Write-Step "MySQL and Redis are healthy; existing images and volumes were reused."
}

function Ensure-Certificate {
    $certDir = Join-Path $frontendDir "certs"
    $certPath = Join-Path $certDir "campuslens-dev.p12"
    $metadataPath = Join-Path $certDir "campuslens-dev.txt"
    $addresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object {
            $_.IPAddress -notlike "127.*" -and
            $_.IPAddress -notlike "169.254.*" -and
            $_.IPAddress -notlike "198.18.*"
        } |
        Select-Object -ExpandProperty IPAddress -Unique |
        Sort-Object
    $identity = (@("localhost", "127.0.0.1") + $addresses) -join ","
    if ((Test-Path $certPath) -and (Test-Path $metadataPath) -and
        ((Get-Content $metadataPath -Raw).Trim() -eq $identity)) {
        Write-Step "HTTPS certificate is current: $identity"
        return
    }

    New-Item -ItemType Directory -Force -Path $certDir | Out-Null
    Remove-Item $certPath -Force -ErrorAction SilentlyContinue
    $san = (@("dns:localhost", "ip:127.0.0.1") + ($addresses | ForEach-Object { "ip:$_" })) -join ","
    & keytool.exe -genkeypair -alias campuslens-dev -keyalg RSA -keysize 2048 -validity 825 `
        -storetype PKCS12 -keystore $certPath -storepass campuslens-dev -keypass campuslens-dev `
        -dname "CN=CampusLens Development, OU=Local Development, O=CampusLens, C=CN" `
        -ext "SAN=$san" -noprompt
    if ($LASTEXITCODE -ne 0) { throw "keytool failed to generate the HTTPS certificate" }
    Set-Content $metadataPath $identity -Encoding ascii
    Write-Step "Generated HTTPS certificate for: $identity"
}

function Start-ManagedProcess(
    [string]$name,
    [string]$title,
    [string]$workingDirectory,
    [string[]]$commands,
    [string]$healthUrl,
    [switch]$Insecure
) {
    if (Test-Endpoint $healthUrl -Insecure:$Insecure) {
        Write-Step "$name already responds."
        return
    }
    $runner = Join-Path $runDir "run-$name.cmd"
    $pidFile = Join-Path $runDir "$name.pid"
    $lines = @("@echo off", "chcp 65001 >nul", "title $title", "cd /d `"$workingDirectory`"") + $commands
    Set-Content -LiteralPath $runner -Value $lines -Encoding utf8
    $process = Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "`"$runner`"" -PassThru
    Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ascii
    Write-Step "Started $name launcher (PID $($process.Id))."
}

function Stop-ManagedPorts([hashtable[]]$services) {
    foreach ($service in $services) {
        $processIds = @(
            Get-NetTCPConnection -State Listen -LocalPort $service.Port -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty OwningProcess -Unique
        )
        $parentIds = @(
            foreach ($processId in $processIds) {
                (Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue).ParentProcessId
            }
        )
        foreach ($processId in $processIds) { Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue }
        foreach ($parentId in $parentIds) {
            if ($parentId) { Stop-Process -Id $parentId -Force -ErrorAction SilentlyContinue }
        }
        $pidFile = Join-Path $runDir "$($service.Name).pid"
        if (Test-Path $pidFile) {
            $launcherId = Get-Content $pidFile | Select-Object -First 1
            if ($launcherId) { Stop-Process -Id ([int]$launcherId) -Force -ErrorAction SilentlyContinue }
            Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        }
        Remove-Item (Join-Path $runDir "run-$($service.Name).cmd") -Force -ErrorAction SilentlyContinue
        Write-Step "Stopped $($service.Name)."
    }
}

function Start-Stack {
    Import-UserMailEnvironment
    Assert-Environment
    Ensure-Docker
    Ensure-Certificate

    if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
        Write-Step "Installing frontend dependencies from npmmirror..."
        Push-Location $frontendDir
        try {
            & npm.cmd install --registry=https://registry.npmmirror.com
            if ($LASTEXITCODE -ne 0) { throw "npm install failed" }
        } finally { Pop-Location }
    }

    Start-ManagedProcess "algorithm-primary" "CampusLens Algorithm Primary" $algorithmDir @(
        "set PYTHONUTF8=1", "set PORT=8000", "set INSTANCE_ID=algorithm-primary",
        "set INSTANCE_ROLE=primary", "`"$algorithmPython`" app\main.py"
    ) "http://localhost:8000/api/v1/health"
    Start-ManagedProcess "algorithm-secondary" "CampusLens Algorithm Secondary" $algorithmDir @(
        "set PYTHONUTF8=1", "set PORT=8001", "set INSTANCE_ID=algorithm-secondary",
        "set INSTANCE_ROLE=secondary", "`"$algorithmPython`" app\main.py"
    ) "http://localhost:8001/api/v1/health"

    $profile = if ($env:CAMPUSLENS_BACKEND_PROFILE) { $env:CAMPUSLENS_BACKEND_PROFILE } else { "mysql" }
    Start-ManagedProcess "backend" "CampusLens Backend" $backendDir @(
        "set PATH=$mavenBin;%PATH%", "mvn spring-boot:run `"-Dspring-boot.run.profiles=$profile`""
    ) "http://localhost:8080/api/health"
    Start-ManagedProcess "frontend" "CampusLens Frontend HTTPS" $frontendDir @(
        "npm run dev"
    ) "https://localhost:5173" -Insecure

    Wait-Endpoint "Algorithm primary" "http://localhost:8000/api/v1/health" 180
    Wait-Endpoint "Algorithm secondary" "http://localhost:8001/api/v1/health" 180
    Wait-Endpoint "Backend" "http://localhost:8080/api/health" 180
    Wait-Endpoint "Frontend HTTPS" "https://localhost:5173" 120 -Insecure
    Write-Step "Stack started successfully."
}

function Stop-Stack {
    Stop-ManagedPorts @(
        @{ Name = "backend"; Port = 8080 },
        @{ Name = "frontend"; Port = 5173 },
        @{ Name = "algorithm-primary"; Port = 8000 },
        @{ Name = "algorithm-secondary"; Port = 8001 }
    )
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        Push-Location $root
        try { docker compose stop mysql redis | Out-Host } finally { Pop-Location }
    }
    Write-Step "Stack stopped. Docker images and named volumes were preserved."
}

function Verify-Stack {
    Start-Stack
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $landmarkCount = docker exec campuslens-mysql mysql -ucampuslens -pcampuslens123 campuslens -N -B `
        -e "SELECT COUNT(*) FROM landmark;" 2>$null
    $mysqlExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($mysqlExitCode -ne 0) { throw "MySQL verification query failed" }
    if ($landmarkCount -ne "10") { throw "Expected 10 landmarks, found $landmarkCount" }
    $ErrorActionPreference = "Continue"
    $redis = docker exec campuslens-redis redis-cli PING 2>$null
    $redisExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($redisExitCode -ne 0) { throw "Redis verification command failed" }
    if ($redis -ne "PONG") { throw "Redis did not return PONG" }
    $landmarks = Invoke-RestMethod "http://localhost:8080/api/landmarks" -TimeoutSec 10
    if ($landmarks.Count -lt 10 -or $landmarks[0].code -ne "L01") {
        throw "Backend landmark contract verification failed"
    }
    $primary = Invoke-RestMethod "http://localhost:8000/api/v1/health" -TimeoutSec 10
    $secondary = Invoke-RestMethod "http://localhost:8001/api/v1/health" -TimeoutSec 10
    if ($primary.instanceRole -ne "primary" -or $secondary.instanceRole -ne "secondary") {
        throw "Algorithm instance roles are incorrect"
    }
    Write-Step "Verification passed: MySQL, Redis, backend, HTTPS frontend and dual algorithms are healthy."
}

switch ($Action) {
    "Start" { Start-Stack }
    "Stop" { Stop-Stack }
    "Verify" { Verify-Stack }
}
