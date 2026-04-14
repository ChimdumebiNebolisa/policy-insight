param(
    [Parameter(Mandatory = $true)]
    [string]$Host,

    [Parameter(Mandatory = $true)]
    [string]$SshKeyPath,

    [string]$User = "opc",
    [string]$ImageRef = "ghcr.io/chimdumebinebolisa/policy-insight:latest",
    [string]$ContainerName = "policyinsight-web",
    [string]$EnvFilePath = "/opt/policyinsight/web.env",
    [int]$AppPort = 8080,
    [switch]$VerifyRoutes,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $SshKeyPath)) {
    throw "SSH key file not found: $SshKeyPath"
}

if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    throw "OpenSSH client (ssh) is required but was not found on PATH."
}

$remote = "${User}@${Host}"
$dockerCmd = @"
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is not installed on target host" >&2
  exit 1
fi

sudo mkdir -p /opt/policyinsight

if [ ! -f '$EnvFilePath' ]; then
  echo "missing env file: $EnvFilePath" >&2
  exit 1
fi

sudo docker pull '$ImageRef'
sudo docker rm -f '$ContainerName' >/dev/null 2>&1 || true
sudo docker run -d \
  --name '$ContainerName' \
  --restart unless-stopped \
  --env-file '$EnvFilePath' \
  -p '$AppPort:8080' \
  '$ImageRef'

sudo docker ps --filter name='$ContainerName' --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
"@

Write-Host "Deploy target: $remote"
Write-Host "Image: $ImageRef"
Write-Host "Container: $ContainerName"
Write-Host "Env file: $EnvFilePath"
Write-Host "Port mapping: $AppPort -> 8080"

if ($DryRun) {
    Write-Host "Dry run enabled; remote command not executed."
    Write-Host $dockerCmd
    exit 0
}

ssh -i $SshKeyPath -o StrictHostKeyChecking=accept-new $remote $dockerCmd

if ($VerifyRoutes) {
  $baseUrl = "http://$Host`:$AppPort"
  $paths = @("/health", "/readiness", "/sample-report", "/sample-pdf")
  foreach ($path in $paths) {
    $url = "$baseUrl$path"
    try {
      $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30
      if ($response.StatusCode -ne 200) {
        throw "Route check failed for $url with HTTP $($response.StatusCode)"
      }
      Write-Host "Verified $url -> 200"
    }
    catch {
      throw "Route verification failed for $url: $($_.Exception.Message)"
    }
  }
  Write-Host "Post-deploy route verification passed."
}
