$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "========================================"
Write-Host " E-Invoice Platform - CI Build"
Write-Host "========================================"

Write-Host ""
Write-Host ">>> [1/5] Maven Build (all modules, checkstyle included)"
Set-Location $ScriptDir
mvn clean install -DskipTests
if ($LASTEXITCODE -ne 0) { Write-Host "Maven build FAILED"; exit 1 }

Write-Host ""
Write-Host ">>> [2/5] Maven Tests"
mvn test
if ($LASTEXITCODE -ne 0) { Write-Host "Maven tests FAILED"; exit 1 }

Write-Host ""
Write-Host ">>> [3/5] Frontend Build"
Set-Location "$ScriptDir\frontend"
npm ci
if ($LASTEXITCODE -ne 0) { Write-Host "npm ci FAILED"; exit 1 }
npm run build
if ($LASTEXITCODE -ne 0) { Write-Host "Frontend build FAILED"; exit 1 }

Write-Host ""
Write-Host ">>> [4/5] Frontend Lint (ESLint)"
npm run lint
if ($LASTEXITCODE -ne 0) { Write-Host "Frontend lint FAILED"; exit 1 }

Write-Host ""
Write-Host ">>> [5/5] Frontend Tests"
npm run test -- --no-watch --browsers=ChromeHeadless
if ($LASTEXITCODE -ne 0) { Write-Host "Frontend tests FAILED"; exit 1 }

Set-Location $ScriptDir
Write-Host ""
Write-Host "========================================"
Write-Host " CI Build PASSED"
Write-Host "========================================"
