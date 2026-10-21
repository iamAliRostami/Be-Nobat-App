@echo off
echo ==============================================
echo Building and Publishing BeNobat Web App...
echo ==============================================
dotnet publish src\BeNobat.Web\BeNobat.Web.csproj -c Release -o .\publish
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Dotnet publish failed!
    exit /b %ERRORLEVEL%
)

echo.
echo ==============================================
echo Building Docker image (0 bytes internet needed)...
echo ==============================================
docker-compose build web
if %ERRORLEVEL% NEQ 0 (
    docker compose build web
)

echo.
echo ==============================================
echo Starting containers...
echo ==============================================
docker-compose up -d
if %ERRORLEVEL% NEQ 0 (
    docker compose up -d
)

echo.
echo ==============================================
echo SUCCESS! Application is running on http://localhost:8080
echo ==============================================
