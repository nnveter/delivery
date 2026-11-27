# Запуск PostgreSQL + backend КР №3. Выполнять из корня репозитория:
#   powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$Env = Join-Path $Root ".env"
$Compose = Join-Path $PSScriptRoot "docker-compose.yml"
if (-not (Test-Path $Env)) { throw ".env не найден в корне проекта. Скопируйте .env.example в .env и заполните секреты." }

& docker compose --env-file $Env -f $Compose up -d --build --wait --wait-timeout 420
if ($LASTEXITCODE -ne 0) {
    & docker compose --env-file $Env -f $Compose logs --tail 150 backend
    exit $LASTEXITCODE
}
Write-Host ""
Write-Host " Web UI:      http://localhost:8080/" -ForegroundColor Green
Write-Host " Swagger UI:  http://localhost:8080/swagger-ui.html (только ADMIN)"
Write-Host " REST API:    http://localhost:8080/api"
Write-Host " Health:      http://localhost:8080/actuator/health"
Write-Host " PostgreSQL:  jdbc:postgresql://localhost:55433/delivery_kr3"
& docker compose --env-file $Env -f $Compose ps
