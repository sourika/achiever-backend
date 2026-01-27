-- V2__multi_sport_challenges.sql
-- Migration for multi-sport challenges support

-- Add WALK to sport types and change challenge structure
-- Instead of single sport_type, we'll track enabled sports per challenge

-- Remove the constraint on sport_type that limits it to specific values
ALTER TABLE challenges DROP CONSTRAINT IF EXISTS chk_sport_type;

-- Rename sport_type to sport_types (will store comma-separated values like "RUN,SWIM,RIDE")
ALTER TABLE challenges RENAME COLUMN sport_type TO sport_types;
ALTER TABLE challenges ALTER COLUMN sport_types TYPE VARCHAR(50);

-- Add individual goal columns to challenge_participants
-- goal_value stays as legacy/default, new columns for each sport
ALTER TABLE challenge_participants ADD COLUMN IF NOT EXISTS goal_run_km DECIMAL(10,2);
ALTER TABLE challenge_participants ADD COLUMN IF NOT EXISTS goal_ride_km DECIMAL(10,2);
ALTER TABLE challenge_participants ADD COLUMN IF NOT EXISTS goal_swim_km DECIMAL(10,2);
ALTER TABLE challenge_participants ADD COLUMN IF NOT EXISTS goal_walk_km DECIMAL(10,2);

-- Migrate existing data: copy goal_value to the appropriate sport column based on challenge sport_type
UPDATE challenge_participants cp
SET goal_run_km = cp.goal_value
    FROM challenges c
WHERE cp.challenge_id = c.id AND c.sport_types = 'RUN';

UPDATE challenge_participants cp
SET goal_ride_km = cp.goal_value
    FROM challenges c
WHERE cp.challenge_id = c.id AND c.sport_types = 'RIDE';

UPDATE challenge_participants cp
SET goal_swim_km = cp.goal_value
    FROM challenges c
WHERE cp.challenge_id = c.id AND c.sport_types = 'SWIM';

UPDATE challenge_participants cp
SET goal_walk_km = cp.goal_value
    FROM challenges c
WHERE cp.challenge_id = c.id AND c.sport_types = 'WALK';

-- Now goal_value becomes optional (nullable) for new multi-sport challenges
ALTER TABLE challenge_participants ALTER COLUMN goal_value DROP NOT NULL;

-- Add sport type tracking to daily_progress for per-sport progress
ALTER TABLE daily_progress ADD COLUMN IF NOT EXISTS run_meters INTEGER DEFAULT 0;
ALTER TABLE daily_progress ADD COLUMN IF NOT EXISTS ride_meters INTEGER DEFAULT 0;
ALTER TABLE daily_progress ADD COLUMN IF NOT EXISTS swim_meters INTEGER DEFAULT 0;
ALTER TABLE daily_progress ADD COLUMN IF NOT EXISTS walk_meters INTEGER DEFAULT 0;

-- Migrate existing distance_meters to appropriate sport column
UPDATE daily_progress dp
SET run_meters = dp.distance_meters
    FROM challenges c, challenge_participants cp
WHERE dp.challenge_id = c.id
  AND dp.user_id = cp.user_id
  AND cp.challenge_id = c.id
  AND c.sport_types = 'RUN';

UPDATE daily_progress dp
SET ride_meters = dp.distance_meters
    FROM challenges c, challenge_participants cp
WHERE dp.challenge_id = c.id
  AND dp.user_id = cp.user_id
  AND cp.challenge_id = c.id
  AND c.sport_types = 'RIDE';

UPDATE daily_progress dp
SET swim_meters = dp.distance_meters
    FROM challenges c, challenge_participants cp
WHERE dp.challenge_id = c.id
  AND dp.user_id = cp.user_id
  AND cp.challenge_id = c.id
  AND c.sport_types = 'SWIM';

UPDATE daily_progress dp
SET walk_meters = dp.distance_meters
    FROM challenges c, challenge_participants cp
WHERE dp.challenge_id = c.id
  AND dp.user_id = cp.user_id
  AND cp.challenge_id = c.id
  AND c.sport_types = 'WALK';

-- Update strava_activities to support WALK
ALTER TABLE strava_activities DROP CONSTRAINT IF EXISTS chk_strava_sport_type;