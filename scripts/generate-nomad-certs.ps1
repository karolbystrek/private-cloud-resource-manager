$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path "$ScriptDir\.."
$RepoRootPath = $RepoRoot.Path

Write-Host "Generating certificates in $RepoRootPath\secrets\nomad..."

# Create secrets/nomad directory
if (!(Test-Path "$RepoRootPath\secrets\nomad")) {
    New-Item -ItemType Directory -Path "$RepoRootPath\secrets\nomad" -Force | Out-Null
}

# Clean existing PEM files and gossip key
Remove-Item -Path "$RepoRootPath\secrets\nomad\*.pem" -ErrorAction SilentlyContinue | Out-Null
Remove-Item -Path "$RepoRootPath\secrets\nomad\gossip.key" -ErrorAction SilentlyContinue | Out-Null

# Convert Windows path to a format friendly for Docker volume mounting
$DockerVolumePath = $RepoRootPath.Replace("\", "/")
if ($DockerVolumePath -match "^([A-Za-z]):(.*)") {
    $drive = $Matches[1].ToLower()
    $rest = $Matches[2]
    $DockerVolumePath = "/$drive$rest"
}

# Run docker to generate certificates
docker run --rm `
  -v "${DockerVolumePath}/secrets/nomad:/certs" `
  -w /certs `
  hashicorp/nomad:1.7 `
  tls ca create

docker run --rm `
  -v "${DockerVolumePath}/secrets/nomad:/certs" `
  -w /certs `
  hashicorp/nomad:1.7 `
  tls cert create -server -region global

docker run --rm `
  -v "${DockerVolumePath}/secrets/nomad:/certs" `
  -w /certs `
  hashicorp/nomad:1.7 `
  tls cert create -client

# Generate Gossip Key
$gossipKey = docker run --rm hashicorp/nomad:1.7 operator gossip keyring generate
$gossipKey = $gossipKey.Trim()

# Rename files for consistency
Rename-Item -Path "$RepoRootPath\secrets\nomad\nomad-agent-ca.pem" -NewName nomad-ca.pem
Rename-Item -Path "$RepoRootPath\secrets\nomad\nomad-agent-ca-key.pem" -NewName nomad-ca-key.pem
Rename-Item -Path "$RepoRootPath\secrets\nomad\global-server-nomad.pem" -NewName server.pem
Rename-Item -Path "$RepoRootPath\secrets\nomad\global-server-nomad-key.pem" -NewName server-key.pem
Rename-Item -Path "$RepoRootPath\secrets\nomad\global-client-nomad.pem" -NewName client.pem
Rename-Item -Path "$RepoRootPath\secrets\nomad\global-client-nomad-key.pem" -NewName client-key.pem

$gossipKey | Out-File -FilePath "$RepoRootPath\secrets\nomad\gossip.key" -NoNewline -Encoding ascii

Write-Host "Gossip key generated successfully: $gossipKey"

# Copy templates and replace the gossip key placeholder
if ((Test-Path "$RepoRootPath\nomad\server.hcl.template") -and (Test-Path "$RepoRootPath\nomad\client.hcl.template")) {
    $serverContent = (Get-Content "$RepoRootPath\nomad\server.hcl.template") -replace 'GOSSIP_KEY_PLACEHOLDER', $gossipKey
    [System.IO.File]::WriteAllLines("$RepoRootPath\nomad\server.hcl", $serverContent)
    
    $clientContent = (Get-Content "$RepoRootPath\nomad\client.hcl.template") -replace 'GOSSIP_KEY_PLACEHOLDER', $gossipKey
    [System.IO.File]::WriteAllLines("$RepoRootPath\nomad\client.hcl", $clientContent)
    
    Write-Host "Nomad configurations generated successfully from templates."
} else {
    Write-Warning "Templates not found. Nomad HCL configurations not generated."
}
