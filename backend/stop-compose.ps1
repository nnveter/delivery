# Остановка контейнеров КР №3. Данные PostgreSQL остаются в volume.
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
& docker compose --env-file (Join-Path $Root ".env") -f (Join-Path $PSScriptRoot "docker-compose.yml") down
exit $LASTEXITCODE
