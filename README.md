# Achievr Backend

Fitness challenge platform with Strava integration. Built with Spring Boot 3, PostgreSQL, and Java 21.

## Prerequisites

- Java 21+
- PostgreSQL 16+
- Strava API Application (create at https://www.strava.com/settings/api)

## Quick Start

### 1. Database Setup

```bash
# Create database
createdb achiever

# Or with psql
psql -c "CREATE DATABASE achiever;"
```

### 2. Configure Environment

Create `.env` and fill in your values:

Required variables:
- `STRAVA_CLIENT_ID` - from Strava API settings
- `STRAVA_CLIENT_SECRET` - from Strava API settings
- `JWT_SECRET` - generate with: `openssl rand -base64 32`

### 3. Run the Application

```bash
# With Maven
./mvnw spring-boot:run

# Or build and run JAR
./mvnw clean package
java -jar target/achiever-backend-0.0.1-SNAPSHOT.jar
```

Server starts at `http://localhost:8080`

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/strava` | Redirect to Strava OAuth |
| GET | `/api/auth/strava/callback` | OAuth callback (handled automatically) |
| GET | `/api/auth/me` | Get current user |

### Challenges

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/challenges` | Create new challenge |
| GET | `/api/challenges/{id}` | Get challenge by ID |
| GET | `/api/challenges/invite/{code}` | Get challenge by invite code (public) |
| POST | `/api/challenges/invite/{code}/join` | Join challenge |
| GET | `/api/challenges/{id}/progress` | Get challenge progress |
| POST | `/api/challenges/{id}/sync` | Sync Strava & get progress |
| GET | `/api/challenges/my` | Get user's challenges |
| GET | `/api/challenges/my/active` | Get user's active challenges |

## Request/Response Examples

### Create Challenge

```bash
POST /api/challenges
Authorization: Bearer <token>
Content-Type: application/json

{
  "sportType": "RUN",
  "goalValue": 50.0,
  "startAt": "2024-01-15",
  "endAt": "2024-01-21"
}
```

### Join Challenge

```bash
POST /api/challenges/invite/ABC123XY/join
Authorization: Bearer <token>
Content-Type: application/json

{
  "goalValue": 75.0
}
```

## Strava API Setup

1. Go to https://www.strava.com/settings/api
2. Create an application
3. Set Authorization Callback Domain to `localhost` (for dev)
4. Copy Client ID and Client Secret to `.env`

**Important:** In production, update the callback domain to your actual domain.

## Scheduled Tasks

| Task | Schedule | Description |
|------|----------|-------------|
| Strava Sync | Every 10 min | Syncs activities for users in active challenges |
| Activate Challenges | Every hour | Activates pending challenges that should start |
| Complete Challenges | Every hour | Completes challenges that have ended |
| Weekly Results | Mon 00:05 | Calculates weekly winners |

## Project Structure

```
src/main/java/com/achiever/
├── AchieverApplication.java     # Main class
├── config/                     # Security, JWT config
├── controller/                 # REST endpoints
├── dto/                        # Request/Response DTOs
├── entity/                     # JPA entities
├── repository/                 # Data access
├── service/                    # Business logic
└── strava/                     # Strava API integration
```

## Development

### Run Tests

```bash
./mvnw test
```

### Code Formatting

Uses standard Java conventions. Recommend IntelliJ IDEA or VS Code with Java extension.

## Deployment

### Fly.io (Current)

1. Install Fly CLI: `powershell -Command "iwr https://fly.io/install.ps1 -useb | iex"`
2. Login: `fly auth login`
3. Create app: `fly launch --no-deploy`
4. Create Postgres: `fly postgres create --name achiever-db --region sjc --vm-size shared-cpu-1x --volume-size 1`
5. Attach database: `fly postgres attach achiever-db --app achiever-backend`
6. Set secrets:
```bash
fly secrets set DB_HOST=achiever-db.internal DB_PORT=5432 DB_NAME=achiever_backend DB_USERNAME=achiever_backend DB_PASSWORD= STRAVA_CLIENT_ID= STRAVA_CLIENT_SECRET= JWT_SECRET= APP_BASE_URL=https://achiever-backend.fly.dev SPRING_PROFILES_ACTIVE=prod --app achiever-backend
```
7. Deploy: `fly deploy`

**Production URL:** https://achiever-backend.fly.dev

### Docker

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx384m", "-Xms128m", "-XX:+UseSerialGC", "-jar", "app.jar"]
```

## License

MIT
