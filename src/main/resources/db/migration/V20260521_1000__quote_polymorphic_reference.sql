ALTER TABLE issue_quotes
    ADD COLUMN IF NOT EXISTS reference_id  UUID,
    ADD COLUMN IF NOT EXISTS reference_type VARCHAR(32);

UPDATE issue_quotes
   SET reference_id = ticket_id,
       reference_type = 'ISSUE'
 WHERE reference_id IS NULL
   AND ticket_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_quote_reference
    ON issue_quotes (reference_id, reference_type);
