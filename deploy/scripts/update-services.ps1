param(
    [string]$Server = "121.43.103.15",
    [string]$User = "root",
    [string]$RemoteRoot = "/opt/yinbo-agent",
    [string[]]$Services = @("ai-infra", "mcp-server", "backend", "gateway"),
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$serviceMap = @{
    "ai-infra" = @{
        LocalJar = "ai-infra\target\yinbo-ai-infra-0.0.1-SNAPSHOT.jar"
        RemoteJar = "yinbo-ai-infra.jar"
        Unit = "yinbo-ai-infra"
        Health = "http://127.0.0.1:8082/actuator/health"
    }
    "mcp-server" = @{
        LocalJar = "mcp-server\target\yinbo-mcp-server-0.0.1-SNAPSHOT.jar"
        RemoteJar = "yinbo-mcp-server.jar"
        Unit = "yinbo-mcp-server"
        Health = "http://127.0.0.1:8083/actuator/health"
    }
    "backend" = @{
        LocalJar = "backend\target\yinbo-agent-service-0.0.1-SNAPSHOT.jar"
        RemoteJar = "yinbo-agent-service.jar"
        Unit = "yinbo-backend"
        Health = "http://127.0.0.1:8080/actuator/health"
    }
    "gateway" = @{
        LocalJar = "gateway\target\yinbo-agent-gateway-0.0.1-SNAPSHOT.jar"
        RemoteJar = "yinbo-agent-gateway.jar"
        Unit = "yinbo-gateway"
        Health = "http://127.0.0.1:8081/actuator/health"
    }
}

foreach ($service in $Services) {
    if (-not $serviceMap.ContainsKey($service)) {
        throw "Unknown service '$service'. Allowed values: $($serviceMap.Keys -join ', ')"
    }
}

if (-not $SkipBuild) {
    Write-Host "Building Maven project..."
    mvn -DskipTests package
}

$remote = "$User@$Server"
$timestamp = Get-Date -Format "yyyyMMddHHmmss"

Write-Host "Ensuring remote app directory..."
ssh $remote "mkdir -p $RemoteRoot/apps"

foreach ($service in $Services) {
    $item = $serviceMap[$service]
    $localJar = $item.LocalJar
    $remoteJar = $item.RemoteJar
    $unit = $item.Unit
    $health = $item.Health

    if (-not (Test-Path $localJar)) {
        throw "Jar not found: $localJar. Run without -SkipBuild first."
    }

    Write-Host "Uploading $service..."
    scp $localJar "${remote}:$RemoteRoot/apps/$remoteJar.new"

    $remoteCommand = @"
set -e
cd $RemoteRoot/apps
if [ -f "$remoteJar" ]; then
  cp "$remoteJar" "$remoteJar.bak.$timestamp"
fi
mv "$remoteJar.new" "$remoteJar"
chown yinbo:yinbo "$remoteJar"
systemctl restart "$unit"
sleep 3
curl -fsS "$health"
"@

    Write-Host "Restarting $unit..."
    $remoteCommand | ssh $remote "bash -s"
    Write-Host "$service updated."
}

Write-Host "Done."
