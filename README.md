# Private Cloud Resource Manager

This system is a distributed on-premise cloud for batch jobs, enforcing strict billing via a "Pre-Paid Lease" mechanism.

## How to run the project locally

1. **Environment Setup**: Copy the example environment file to `.env`:

   ```bash
   cp .env.example .env
   ```

2. **Generate Security Keys**: Create the cryptographic keys required for JWT authentication.

   ```bash
   mkdir -p secrets
   openssl genpkey -algorithm ed25519 -out secrets/private.pem
   openssl pkey -in secrets/private.pem -pubout -out secrets/public.pem
   ```

3. **Start Services**: Run the following command to start the development environment using Docker Compose:

   ```bash
   docker compose up -d
   ```
