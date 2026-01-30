-- Add EXPIRED and CANCELLED statuses for challenges
ALTER TABLE challenges DROP CONSTRAINT IF EXISTS chk_status;
ALTER TABLE challenges ADD CONSTRAINT chk_status CHECK (status IN ('PENDING', 'SCHEDULED', 'ACTIVE', 'COMPLETED', 'EXPIRED', 'CANCELLED'));
