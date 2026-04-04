# Private Cloud Resource Manager

This system is a distributed on-premise cloud for batch jobs, enforcing strict billing via a "Prepaid Lease" mechanism.

## How to run the project locally

1. **Environment Setup**: Copy the example environment file to `.env`:

   ```bash
   cp .env.example .env
   ```

2. **Install Dependencies**: Use npm workspaces to install the required dependencies from the repository root:

   ```bash
   npm install
   ```

3. **Generate Security Keys**: Create the cryptographic keys required for JWT authentication.

   ```bash
   mkdir -p secrets
   openssl genpkey -algorithm ed25519 -out secrets/private.pem
   openssl pkey -in secrets/private.pem -pubout -out secrets/public.pem
   ```

4. **Start Services**: Run the following command to start the development environment using Docker Compose:

   ```bash
   docker compose up
   ```

## UI URLs (Local Development)

- Frontend: http://localhost:3000
- Backend: http://localhost:8080/api/swagger-ui/index.html
- Nomad UI: http://localhost:4646
- MinIO Console: http://localhost:9001

For authentication API testing, use [`apps/broker/client.http`](apps/broker/client.http), which includes requests for
all auth endpoints.
