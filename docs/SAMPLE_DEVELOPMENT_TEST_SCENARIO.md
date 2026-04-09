# Sample Development Test Scenario

0) If needed:
```bash
docker compose down -v
docker compose up --build
```

1) Register:
- Username: user
- Email: user@user.com
- Password: Password1!

2) Login

3) Through docker postgres terminal:
```bash
psql -U [login from .env]
```

```sql
pcrm_dev=# UPDATE wallets SET balance_credits=20;
```

4) New job:
- Docker image: alpine:3.20
- Execution command:
```bash
mkdir -p "$NOMAD_ALLOC_DIR/data" && echo "artifact test $(date -Iseconds)" > "$NOMAD_ALLOC_DIR/data/result.txt"
```
- Rest: empty

5) Check MinIO (login and password from .env) for the new file

6) Download button should appear, and file output should be in output.zip
