-- Add optional name field to challenges
ALTER TABLE challenges ADD COLUMN IF NOT EXISTS name VARCHAR(50);