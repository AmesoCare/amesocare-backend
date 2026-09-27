# AmesoCare backend

Five Spring Boot services plus PostgreSQL and Kafka. Start this stack before the
Docker-based UI stack in `amesocare-ui`.

| Service | Host port | Health URL (local) |
| --- | --- | --- |
| Authentication | 5001 | http://localhost:5001/health |
| Patient | 5002 | http://localhost:5002/health |
| Incident / SOS | 5003 | http://localhost:5003/health |
| Notifications | 5004 | http://localhost:5004/health |
| Audit | 5005 | http://localhost:5005/health |

## Start locally with Docker

Prerequisites: Docker Engine with Compose v2, or Docker Desktop running. Docker
builds Java inside its images; no local Java or Maven installation is needed.
Commands below start from the parent directory containing both repositories.

```sh
cd amesocare-backend
docker compose up -d --build
docker compose ps
docker compose logs -f auth-service patient-service incident-service notification-service audit-service
```

The first build takes longer while images and dependencies download. Wait for
PostgreSQL and Kafka to become healthy and the Java services to start. Ctrl+C
exits log viewing without stopping the servers. Check each service:

```sh
for port in 5001 5002 5003 5004 5005; do
  curl --fail --show-error "http://localhost:$port/health"
  echo
done
```

The local Compose file supplies development database credentials and the JWT
key; a `.env` file is not required. PostgreSQL is published on host port 5433
(database/user/password: `amesohermes` / `ameso` / `ameso`). Kafka is published
on 9092, but its advertised address `kafka:9092` is intended for the containers.

Once the APIs respond, start the two web servers:

```sh
cd ../amesocare-ui
docker compose up -d --build
```

Patient SOS: http://localhost:3000. Hospital UI: http://localhost:3001.
The backend creates `ameso-hermes-poc_default`; the UI joins that Docker network.
See the UI README for running Next.js directly with Node instead.

## Start on AWS / production

Run these commands **on the server** from `amesocare-backend`. For an existing
AmesoHermes deployment, read the migration section below first.

```sh
# First-time setup only; do not overwrite an existing .env.
cp .env.example .env
```

Edit `.env` before starting:

- `POSTGRES_PASSWORD`: the existing deployment's database password, or a new
  password for a fresh database.
- `JWT_KEY`: the existing signing key, or a new value of at least 32 characters
  for a fresh deployment.
- `WHATSAPP_ACCESS_TOKEN` and `WHATSAPP_PHONE_NUMBER_ID`: existing notification
  settings; leave empty to use the backend's mock notification behavior.

```sh
docker compose -f docker-compose.prod.yml config --quiet
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f incident-service
```

The production file uses database/user `postgres` / `postgres`, unlike local
Compose. Use the matching existing production volume and credentials; changing
`.env` does not reinitialize a database already stored in a volume. Do not switch
between local and production configurations against the same database volume.

For the existing plain-HTTP AWS deployment, APIs are at
`http://13.63.8.113:5001` through `:5005`. The server's firewall/security group
must allow the ports used by clients. Next, follow the UI README's production
steps **on the same Docker host**. Its `.env` holds browser-facing URLs, not
backend secrets. The default Compose files assume both repositories use the
same Docker daemon.

## Optional existing Caddy HTTPS setup

In backend `.env`, set `SSLIP_BASE=13-63-8-113.sslip.io` and
`BIND_ADDR=127.0.0.1`. In UI `.env`, set `BIND_ADDR=127.0.0.1` and all four API
URLs to `https://api.13-63-8-113.sslip.io`. Use the actual deployment hostname
if different. Ports 80/443 must be reachable for Caddy.

Recreate the backend and rebuild/start the UI with their production commands
above, then run from `amesocare-backend`:

```sh
docker compose -f docker-compose.prod.yml -f docker-compose.caddy.yml up -d caddy
docker compose -f docker-compose.prod.yml -f docker-compose.caddy.yml logs -f caddy
```

Open https://care.13-63-8-113.sslip.io or https://hermes.13-63-8-113.sslip.io.
Caddy proxies both UIs and all APIs over the shared Docker network.

## Stop and restart

From each repository, `docker compose stop` stops its local containers while
preserving data; `docker compose start` restarts them. For production use
`docker compose -f docker-compose.prod.yml stop` / `start`. Include
`-f docker-compose.caddy.yml` in backend commands when also managing Caddy.
Stop the UI before stopping the backend. Avoid `down -v`, which deletes data,
and `--remove-orphans` during migration. Do not start local and production
stacks at the same time: they share ports and the backend Compose project name.

## Migrate an existing AmesoHermes server

Keep the existing secrets and database data. The backend retains project name
`ameso-hermes-poc` and its named volumes; the new UI uses `amesocare-ui`.

1. Configure `.env` in both new repositories, preserving existing settings.
2. Run `docker compose -f docker-compose.prod.yml build` in each new repository.
3. From the original `AmesoHermes` directory, release only the old UI containers:

   ```sh
   docker compose -f docker-compose.prod.yml rm -s -f ameso-care hermes
   ```

4. Start the production backend from `amesocare-backend`, then the production UI
   from `amesocare-ui`, using the commands above. Start Caddy if applicable.

There is a brief UI outage. Do not start the original UI containers again while
the new ones are running. The original checkout remains a migration reference.
`infra/README.md` covers provisioning a new EC2 host; it is not required to start
services on the existing AWS server.

## Compile Java only

With JDK 21 and Maven 3.9, from `amesocare-backend`:

```sh
mvn -Dmaven.test.skip=true package
```

This compiles the shared Java project; it does **not** start any servers. The
project has five main classes, not a single executable Spring Boot JAR. Use the
Docker commands above to start all five services with their dependencies.
