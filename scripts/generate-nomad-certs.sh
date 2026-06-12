#!/usr/bin/env bash
set -euo pipefail

# Determine repository root directory (assumed to be parent of scripts/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "Generating certificates in $REPO_ROOT/secrets/nomad..."

# Create secrets/nomad directory
mkdir -p "$REPO_ROOT/secrets/nomad"

# Clean up any existing certs in secrets/nomad to avoid prompt/conflicts
rm -f "$REPO_ROOT/secrets/nomad"/*.pem "$REPO_ROOT/secrets/nomad"/gossip.key

# Run a temporary Nomad container to generate CA and certificates.
# We mount the secrets/nomad directory into the container.
docker run --rm \
  -v "$REPO_ROOT/secrets/nomad:/certs" \
  -w /certs \
  hashicorp/nomad:1.7 \
  tls ca create

docker run --rm \
  -v "$REPO_ROOT/secrets/nomad:/certs" \
  -w /certs \
  hashicorp/nomad:1.7 \
  tls cert create -server -region global

docker run --rm \
  -v "$REPO_ROOT/secrets/nomad:/certs" \
  -w /certs \
  hashicorp/nomad:1.7 \
  tls cert create -client

# Generate Gossip Key
GOSSIP_KEY=$(docker run --rm hashicorp/nomad:1.7 operator gossip keyring generate | tr -d '\r\n ')

# Rename files for consistency with our nomad configurations
# Default CA: nomad-agent-ca.pem -> nomad-ca.pem
mv "$REPO_ROOT/secrets/nomad/nomad-agent-ca.pem" "$REPO_ROOT/secrets/nomad/nomad-ca.pem"
mv "$REPO_ROOT/secrets/nomad/nomad-agent-ca-key.pem" "$REPO_ROOT/secrets/nomad/nomad-ca-key.pem"

# Default Server: global-server-nomad.pem -> server.pem
mv "$REPO_ROOT/secrets/nomad/global-server-nomad.pem" "$REPO_ROOT/secrets/nomad/server.pem"
mv "$REPO_ROOT/secrets/nomad/global-server-nomad-key.pem" "$REPO_ROOT/secrets/nomad/server-key.pem"

# Default Client: global-client-nomad.pem -> client.pem
mv "$REPO_ROOT/secrets/nomad/global-client-nomad.pem" "$REPO_ROOT/secrets/nomad/client.pem"
mv "$REPO_ROOT/secrets/nomad/global-client-nomad-key.pem" "$REPO_ROOT/secrets/nomad/client-key.pem"

# Save gossip key to file
echo "$GOSSIP_KEY" > "$REPO_ROOT/secrets/nomad/gossip.key"

echo "Gossip key generated successfully: $GOSSIP_KEY"

# Copy templates and replace the gossip key placeholder
if [ -f "$REPO_ROOT/nomad/server.hcl.template" ] && [ -f "$REPO_ROOT/nomad/client.hcl.template" ]; then
  sed "s/GOSSIP_KEY_PLACEHOLDER/$GOSSIP_KEY/g" "$REPO_ROOT/nomad/server.hcl.template" > "$REPO_ROOT/nomad/server.hcl"
  sed "s/GOSSIP_KEY_PLACEHOLDER/$GOSSIP_KEY/g" "$REPO_ROOT/nomad/client.hcl.template" > "$REPO_ROOT/nomad/client.hcl"
  echo "Nomad configurations generated successfully from templates."
else
  echo "Warning: Templates not found. Nomad HCL configurations not generated."
fi
