-- V1__initial_schema.sql

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255), -- nullable for OAuth-only users
    timezone VARCHAR(50) NOT NULL DEFAULT 'America/Los_Angeles',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Strava connection (1:1 with users)
CREATE TABLE strava_connection (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    athlete_id BIGINT UNIQUE NOT NULL,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Challenges
CREATE TABLE challenges (
    id UUID PRIMARY KEY,
    created_by UUID NOT NULL REFERENCES users(id),
    invite_code VARCHAR(12) UNIQUE NOT NULL,
    sport_type VARCHAR(20) NOT NULL, -- RUN, RIDE, SWIM
    start_at DATE NOT NULL,
    end_at DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, ACTIVE, COMPLETED
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_dates CHECK (end_at > start_at),
    CONSTRAINT chk_sport_type CHECK (sport_type IN ('RUN', 'RIDE', 'SWIM')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED'))
);

CREATE INDEX idx_challenges_status ON challenges(status);
CREATE INDEX idx_challenges_created_by ON challenges(created_by);

-- Challenge participants
CREATE TABLE challenge_participants (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    goal_value DECIMAL(10,2) NOT NULL, -- in kilometers
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    UNIQUE(challenge_id, user_id)
);

CREATE INDEX idx_participants_challenge ON challenge_participants(challenge_id);
CREATE INDEX idx_participants_user ON challenge_participants(user_id);

-- Strava activities (raw data from API)
CREATE TABLE strava_activities (
    id BIGINT PRIMARY KEY, -- Strava's activity ID
    user_id UUID NOT NULL REFERENCES users(id),
    sport_type VARCHAR(20) NOT NULL,
    name VARCHAR(255),
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    distance_meters INTEGER NOT NULL,
    moving_time_seconds INTEGER,
    synced_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activities_user_date ON strava_activities(user_id, start_date);
CREATE INDEX idx_activities_sport ON strava_activities(sport_type);

-- Daily progress snapshots
CREATE TABLE daily_progress (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    date DATE NOT NULL,
    distance_meters INTEGER NOT NULL DEFAULT 0, -- cumulative for the challenge period
    progress_percent INTEGER NOT NULL DEFAULT 0, -- 0-100, capped
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    UNIQUE(challenge_id, user_id, date)
);

CREATE INDEX idx_progress_challenge ON daily_progress(challenge_id);
CREATE INDEX idx_progress_user ON daily_progress(user_id);

-- Weekly results
CREATE TABLE challenge_week_results (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    user_a_id UUID NOT NULL REFERENCES users(id),
    user_b_id UUID NOT NULL REFERENCES users(id),
    user_a_percent INTEGER NOT NULL,
    user_b_percent INTEGER NOT NULL,
    winner_user_id UUID REFERENCES users(id), -- NULL = tie
    computed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    UNIQUE(challenge_id, week_start)
);

CREATE INDEX idx_results_challenge ON challenge_week_results(challenge_id);
