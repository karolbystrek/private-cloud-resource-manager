# Private Cloud Resource Manager - Agent

The Agent is a Python service designed to run on every worker node in the Private Cloud. It acts as the local enforcement authority, managing container lifecycles and ensuring no compute resources are used without a valid, paid-for lease.

## Architecture: The "Virtual Node"

In this development environment, the Agent runs as a **Virtual Node** using Docker.

- **Containerized**: The Agent itself runs inside a Docker container (`pcrm_agent`).
- **Host Access**: The host's Docker socket (`/var/run/docker.sock`) is mounted into the Agent container.
- **Role**: This allows the Agent to spawn "sibling" containers on your host machine, effectively simulating how a real worker node orchestrates user jobs.

## Running with Docker (Recommended)

The easiest way to run the agent is via the root `docker-compose.yml`.

1.  **Start the Cluster**:
    ```bash
    docker compose up --build -d
    ```
2.  **Verify**:
    Check the logs to see the agent starting:
    ```bash
    docker logs -f pcrm_agent
    ```
    Verify the agent can see your host's Docker daemon:
    ```bash
    docker exec pcrm_agent docker ps
    ```

## Local Development (Python)

If you need to modify the Python code and run it directly on your machine (outside the container):

1.  **Prerequisites**:
    - Python 3.11+
    - Docker installed and running locally.

2.  **Setup**:
    ```bash
    # Create virtual environment
    python3 -m venv venv
    source venv/bin/activate

    # Install dependencies
    pip install -r requirements.txt
    ```

3.  **Run**:
    ```bash
    # Ensure Docker is running!
    python main.py
    ```

## Configuration

| Environment Variable | Default              | Description                        |
| -------------------- | -------------------- | ---------------------------------- |
| `BROKER_URL`         | `http://broker:8080` | URL of the central Broker service. |
