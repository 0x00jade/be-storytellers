# be-storytellers

Backend service for the StoryTellers application.

This project is a Java 21 Spring Boot API service. It uses PostgreSQL for persistent data, Redis for cache/session-style infrastructure, Flyway for database migrations, Spring Security with Google OAuth2 support, JWT configuration, AWS S3 configuration, and Springdoc OpenAPI/Swagger UI.

## Tech Stack

- Java 21
- Spring Boot 3.4.5
- Gradle
- PostgreSQL 16
- Redis 7
- Flyway
- Spring Data JPA
- Spring Security
- Google OAuth2 client
- JWT via `jjwt`
- AWS SDK S3
- Springdoc OpenAPI
- Docker Compose for local infrastructure

## Project Structure

```text
.
+-- build.gradle
+-- settings.gradle
+-- Dockerfile
+-- docker-compose.yml
+-- src
|   +-- main
|   |   +-- java/com/demo/bestorytellers
|   |   |   +-- auth
|   |   |   +-- chapter
|   |   |   +-- common
|   |   |   +-- config
|   |   |   +-- notification
|   |   |   +-- reading
|   |   |   +-- schedule
|   |   |   +-- search
|   |   |   +-- social
|   |   |   +-- story
|   |   |   +-- user
|   |   |   +-- BeStorytellersApplication.java
|   |   +-- resources
|   |       +-- application.yml
|   |       +-- db/migration
|   |           +-- V1__init.sql
|   |           +-- V2__seed_tags.sql
|   +-- test
|       +-- java/com/demo/bestorytellers
|           +-- auth
|           +-- chapter
|           +-- reading
|           +-- social
|           +-- story
|           +-- user
+-- docs
    +-- system_design.md
```

Main application packages:

- `auth`: OAuth2 login, JWT utilities, authentication controller, token refresh.
- `chapter`: chapter CRUD, content saving, autosave, publishing, version history.
- `common`: shared response DTOs, base entities, exceptions, utility classes.
- `config`: security, Redis, S3, async, and OpenAPI configuration.
- `notification`: notification entities, queries, and read/unread workflows.
- `reading`: reading lists, reading progress, and reading history.
- `schedule`: background jobs for publishing, view counts, and version cleanup.
- `search`: story search.
- `social`: comments, votes, and follows.
- `story`: stories, tags, cover images, and story metadata.
- `user`: user profiles, avatars, and follow-related user views.

## Requirements

For local development:

- JDK 21
- Docker and Docker Compose
- PostgreSQL client tools are optional but useful

The Gradle wrapper is included, so a separate Gradle installation is not required.

## Configuration

Runtime configuration is defined in:

```text
src/main/resources/application.yml
```

Spring uses placeholder syntax like this:

```yaml
url: ${DB_URL:jdbc:postgresql://localhost:5432/storytellers}
```

This means:

- Use the environment variable `DB_URL` if it exists.
- If `DB_URL` is not set, use the default value after `:`.

So this value:

```yaml
${DB_URL:jdbc:postgresql://localhost:5432/storytellers}
```

resolves to:

- `DB_URL` from the environment when present.
- `jdbc:postgresql://localhost:5432/storytellers` when `DB_URL` is missing.

## Environment Variables

| Variable | Default | Description |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/storytellers` | JDBC URL for PostgreSQL. |
| `DB_USER` | `dev` | PostgreSQL username. |
| `DB_PASS` | `dev` | PostgreSQL password. |
| `REDIS_HOST` | `localhost` | Redis hostname. |
| `REDIS_PORT` | `6379` | Redis port. |
| `GOOGLE_CLIENT_ID` | `dummy` | Google OAuth2 client ID. |
| `GOOGLE_CLIENT_SECRET` | `dummy` | Google OAuth2 client secret. |
| `JWT_SECRET` | `supersecretkey256bitminimumrequiredforsecurity` | Secret used for JWT signing. Use a strong secret outside local dev. |
| `AWS_REGION` | `us-east-1` | AWS region for S3. |
| `S3_BUCKET` | `storytellers-content` | S3 bucket name for content storage. |
| `CORS_ALLOWED_ORIGIN` | `http://localhost:3000` | Allowed frontend origin for CORS. |
| `FRONTEND_URL` | `http://localhost:3000` | Frontend URL used by the application. |

## Database URL Rules

The database URL depends on where the application is running.

### Running without Docker

When running the Spring Boot app directly on your machine, use `localhost`:

```text
jdbc:postgresql://localhost:5432/storytellers
```

That is already the default in `application.yml`, so this works if PostgreSQL is running locally on port `5432` with:

- database: `storytellers`
- username: `dev`
- password: `dev`

### Running inside Docker Compose

When the app runs inside Docker Compose, it must connect to the PostgreSQL service name:

```text
jdbc:postgresql://postgres:5432/storytellers
```

This is configured in `docker-compose.yml`:

```yaml
environment:
  DB_URL: jdbc:postgresql://postgres:5432/storytellers
  DB_USER: dev
  DB_PASS: dev
```

Inside the Docker network, `postgres` is the hostname of the PostgreSQL container. `localhost` would mean the app container itself, not the database container.

## Running With Docker Compose

Start PostgreSQL, Redis, and the application:

```bash
docker compose up --build
```

The app will be available at:

```text
http://localhost:8080
```

PostgreSQL will be exposed on:

```text
localhost:5432
```

Redis will be exposed on:

```text
localhost:6379
```

Stop the services:

```bash
docker compose down
```

Stop the services and remove the PostgreSQL volume:

```bash
docker compose down -v
```

Use `-v` only when you want to delete local database data.

## Running Locally Without Docker

Start PostgreSQL and Redis first. You can use Docker Compose for only the infrastructure:

```bash
docker compose up postgres redis
```

Then run the Spring Boot app directly:

```bash
./gradlew bootRun
```

Because `application.yml` has local defaults, this command uses:

```text
DB_URL=jdbc:postgresql://localhost:5432/storytellers
DB_USER=dev
DB_PASS=dev
REDIS_HOST=localhost
REDIS_PORT=6379
```

You can override values inline:

```bash
DB_URL=jdbc:postgresql://localhost:5432/storytellers DB_USER=dev DB_PASS=dev ./gradlew bootRun
```

Or export them first:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/storytellers
export DB_USER=dev
export DB_PASS=dev
export REDIS_HOST=localhost
export REDIS_PORT=6379

./gradlew bootRun
```

## Build

Build the project:

```bash
./gradlew build
```

Create the application jar:

```bash
./gradlew bootJar
```

The jar is written to:

```text
build/libs/
```

## Test

Run tests:

```bash
./gradlew test
```

The project includes Spring Boot test dependencies, Spring Security test support, and Testcontainers PostgreSQL dependencies.

## API Documentation

Springdoc OpenAPI is configured in `application.yml`.

When the app is running:

```text
Swagger UI: http://localhost:8080/swagger-ui
OpenAPI JSON: http://localhost:8080/api-docs
```

## Database Migrations

Flyway is enabled:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

Migration files live in:

```text
src/main/resources/db/migration
```

Current migrations:

- `V1__init.sql`: creates the initial schema.
- `V2__seed_tags.sql`: inserts default story tags.

The initial schema includes tables for:

- users
- stories
- chapters
- chapter versions
- tags
- story tags
- comments
- comment votes
- follows
- reading lists
- reading list items
- reading progress
- notifications

## Dockerfile

The Docker image expects a built jar:

```dockerfile
COPY build/libs/be-storytellers-*.jar app.jar
```

If you build the image manually, create the jar first:

```bash
./gradlew bootJar
docker build -t be-storytellers .
```

Run the image manually:

```bash
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/storytellers \
  -e DB_USER=dev \
  -e DB_PASS=dev \
  -e REDIS_HOST=host.docker.internal \
  be-storytellers
```

When using `docker compose up --build`, Compose provides the correct container-to-container values automatically.

## Common Issues

### `DB_URL` is not found

You do not need to define `DB_URL` for normal local development if your database is available at:

```text
jdbc:postgresql://localhost:5432/storytellers
```

Spring will use that fallback from `application.yml`.

Define `DB_URL` only when you need a different database host, port, or database name.

### App cannot connect to PostgreSQL in Docker

Inside Docker Compose, the database host should be:

```text
postgres
```

Use:

```text
jdbc:postgresql://postgres:5432/storytellers
```

Do not use `localhost` from inside the app container unless PostgreSQL is running in the same container, which it is not.

### App cannot connect to PostgreSQL locally

When running the app directly on your machine, the database host should usually be:

```text
localhost
```

Use:

```text
jdbc:postgresql://localhost:5432/storytellers
```

Make sure the PostgreSQL container or local PostgreSQL server is running and exposing port `5432`.

### Google OAuth values are `dummy`

The app has local fallback values for:

```text
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
```

Real Google login requires valid credentials from Google Cloud Console.

### JWT secret is only a local default

The default JWT secret is for local development only. In production or shared environments, set a strong secret with:

```bash
export JWT_SECRET=your-long-random-secret
```

## Useful Commands

```bash
# Run all services
docker compose up --build

# Run only local infrastructure
docker compose up postgres redis

# Run app locally
./gradlew bootRun

# Run tests
./gradlew test

# Build jar
./gradlew bootJar

# Stop Docker services
docker compose down

# Stop Docker services and remove database data
docker compose down -v
```
