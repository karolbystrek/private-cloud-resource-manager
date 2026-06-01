# reset.ps1
# Requires PowerShell 5.1+

$auto_confirm = $false
if ($args -contains "-y") {
    $auto_confirm = $true
}

function Confirm-Action {
    if ($auto_confirm) {
        return
    }

    $reply = Read-Host "Are you sure you want to proceed? (y/N)"
    if ($reply -notmatch '^[Yy]$') {
        Write-Host "Script canceled."
        exit 1
    }
}

Write-Host ""
Write-Host "*** WARNING: This will remove all containers and container data, and optionally reset .env ***"
Write-Host ""

Confirm-Action

Write-Host "===> Stopping and removing all containers..."

if (Test-Path ".env") {
    docker compose -f docker-compose.yml down -v --remove-orphans
} elseif (Test-Path ".env.example") {
    Write-Host "No .env found, using .env.example for docker compose down..."
    docker compose --env-file .env.example -f docker-compose.yml down -v --remove-orphans
} else {
    Write-Host "Skipping 'docker compose down' because there's no env-file."
}

Write-Host "===> Cleaning up bind-mounted directories and generated secrets..."
$bind_mounts = @("./volumes/db/data", "./volumes/minio", "./secrets/nomad")

foreach ($dir in $bind_mounts) {
    if (Test-Path $dir) {
        Write-Host "Removing $dir..."
        Confirm-Action
        Remove-Item -Path $dir -Recurse -Force
    } else {
        Write-Host "$dir not found."
    }
}

# Clean up generated Nomad configuration files
if (Test-Path "./nomad/server.hcl") {
    Write-Host "Removing generated nomad/server.hcl..."
    Remove-Item -Path "./nomad/server.hcl" -Force
}
if (Test-Path "./nomad/client.hcl") {
    Write-Host "Removing generated nomad/client.hcl..."
    Remove-Item -Path "./nomad/client.hcl" -Force
}

Write-Host "===> Resetting .env file (will save backup to .env.old)..."
Confirm-Action

if (Test-Path ".env") {
    Write-Host "Renaming existing .env file to .env.old"
    if (Test-Path ".env.old") {
        Remove-Item -Path ".env.old" -Force
    }
    Move-Item -Path ".env" -Destination ".env.old" -Force
} else {
    Write-Host "No .env file found."
}

if (Test-Path ".env.example") {
    Write-Host "===> Copying .env.example to .env"
    Copy-Item -Path ".env.example" -Destination ".env" -Force
} else {
    Write-Host "No .env.example found, can't restore .env to default values."
}

Write-Host "Cleanup complete!"
Write-Host "Re-run 'docker compose pull' to update images."
Write-Host ""
