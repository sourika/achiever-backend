# Achiever Backend

Fitness challenge platform with Strava integration.

Turn fitness into a fair game: integrate your Strava, set your own targets, and challenge friends to see who can get closest to their personal 100% within the deadline.

Built with Spring Boot 3, PostgreSQL, and Java 21.

## Features

- **Email/Password Authentication** + Strava OAuth
- **Multi-sport Challenges** — Run, Ride, Swim, Walk
- **Challenge Lifecycle** — PENDING → SCHEDULED → ACTIVE → COMPLETED/EXPIRED
- **Real-time Progress Tracking** — syncs with Strava
- **In-app Notifications** — challenge updates, invites, results
- **Automated Daily Processing** — midnight cron job for status updates and winner determination

## Prerequisites

- Java 21+
- PostgreSQL 15+
- Strava API Application (create at https://www.strava.com/settings/api)

## Quick Start

### 1. Database Setup

```bash
createdb achiever
```

### 2. Configure Environment

Create `.env` file with:

| Variable | Description |
|----------|-------------|
| `DB_HOST` | Database host (default: localhost) |
| `DB_PORT` | Database port (default: 5432) |
| `DB_NAME` | Database name (default: achiever) |
| `DB_USERNAME` | Database user (default: postgres) |
| `DB_PASSWORD` | Database password |
| `STRAVA_CLIENT_ID` | From Strava API settings |
| `STRAVA_CLIENT_SECRET` | From Strava API settings |
| `JWT_SECRET` | Generate with: `openssl rand -base64 32` |
| `APP_BASE_URL` | Backend URL (default: http://localhost:8080) |
| `FRONTEND_URL` | Frontend URL (default: http://localhost:5173) |

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

Server starts at `http://localhost:8080`

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/check-email?email=` | Check if email exists, has password |
| POST | `/api/auth/login` | Login with email/password |
| POST | `/api/auth/set-password` | Set password (authenticated) |
| GET | `/api/auth/strava` | Redirect to Strava OAuth |
| GET | `/api/auth/strava/callback` | OAuth callback |
| GET | `/api/auth/me` | Get current user |

### Challenges

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/challenges` | Create challenge |
| GET | `/api/challenges/{id}` | Get challenge by ID |
| PATCH | `/api/challenges/{id}` | Update challenge |
| DELETE | `/api/challenges/{id}` | Delete challenge |
| GET | `/api/challenges/invite/{code}` | Get challenge by invite code (public) |
| POST | `/api/challenges/invite/{code}/join` | Join challenge |
| POST | `/api/challenges/{id}/leave` | Leave (forfeit) challenge |
| GET | `/api/challenges/{id}/progress` | Get challenge progress |
| POST | `/api/challenges/{id}/sync` | Sync Strava data |
| GET | `/api/challenges/my` | Get user's challenges |
| GET | `/api/challenges/my/active` | Get user's active challenges |

### Notifications

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/notifications` | Get all notifications |
| GET | `/api/notifications/unread-count` | Get unread count |
| POST | `/api/notifications/read` | Mark all as read |

## Request Examples

### Login
```bash
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

### Create Challenge
```bash
POST /api/challenges
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "February Running Challenge",
  "goals": {
    "RUN": 50,
    "RIDE": 100
  },
  "startAt": "2025-02-01",
  "endAt": "2025-02-28",
  "timezone": "America/Los_Angeles"
}
```

### Join Challenge
```bash
POST /api/challenges/invite/ABC123XY/join
Authorization: Bearer <token>
Content-Type: application/json

{
  "goals": {
    "RUN": 75,
    "RIDE": 80
  }
}
```

## Challenge Statuses

| Status | Description |
|--------|-------------|
| `PENDING` | Created, waiting for opponent to join |
| `SCHEDULED` | Opponent joined, waiting for start date |
| `ACTIVE` | In progress |
| `COMPLETED` | Ended, winner determined |
| `EXPIRED` | No one joined before end date |

## Scheduled Tasks

| Task | Schedule | Description |
|------|----------|-------------|
| Midnight Sync | Daily 00:00 UTC | Updates statuses, syncs Strava, determines winners |

### Midnight Job Actions:
1. **PENDING → EXPIRED** — if end date passed without opponent
2. **SCHEDULED → ACTIVE** — if start date reached
3. **Sync Strava** — for all active challenge participants
4. **ACTIVE → COMPLETED** — if end date passed, determine winner

## Project Structure

```
src/main/java/com/achiever/
├── AchieverApplication.java
├── config/          # Security, JWT configuration
├── controller/      # REST endpoints
├── dto/             # Request/Response objects
├── entity/          # JPA entities
├── repository/      # Data access layer
├── service/         # Business logic & scheduler
└── strava/          # Strava API integration
```

## Testing

```bash
# Run all tests
./mvnw test

# Run with coverage
./mvnw test jacoco:report
```

Tests include:
- **Unit tests** — services with mocked dependencies
- **Integration tests** — full API tests with test database

## Deployment (Fly.io)

### Initial Setup

```bash
# Install Fly CLI
brew install flyctl

# Login
fly auth login

# Launch app
fly launch --no-deploy

# Create Postgres
fly postgres create --name achiever-db

# Attach database
fly postgres attach achiever-db
```

### Set Secrets

```bash
fly secrets set \
  STRAVA_CLIENT_ID=your_id \
  STRAVA_CLIENT_SECRET=your_secret \
  JWT_SECRET=your_secret \
  APP_BASE_URL=https://api.achiever.fit \
  FRONTEND_URL=https://www.achiever.fit \
  SPRING_PROFILES_ACTIVE=prod
```

### Deploy

```bash
fly deploy
```

## Tech Stack

- **Framework:** Spring Boot 3.4
- **Language:** Java 21
- **Database:** PostgreSQL 15+
- **Migrations:** Flyway
- **Auth:** JWT + Strava OAuth2
- **Deployment:** Fly.io

## License

MIT
