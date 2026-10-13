ALTER TABLE hint_logs ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'HINT';
ALTER TABLE hint_logs ALTER COLUMN level DROP NOT NULL;
ALTER TABLE hint_logs ADD CONSTRAINT chk_hint_logs_kind CHECK (kind IN ('HINT', 'EXPLANATION'));
ALTER TABLE hint_logs ADD CONSTRAINT chk_hint_logs_level CHECK (
    (kind = 'HINT' AND level IS NOT NULL AND level BETWEEN 1 AND 3)
    OR (kind = 'EXPLANATION' AND level IS NULL));
