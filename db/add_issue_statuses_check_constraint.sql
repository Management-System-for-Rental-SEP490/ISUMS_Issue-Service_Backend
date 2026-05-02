-- ISUMS Issue Service
-- Fix issue_tickets status check constraint to include all enum statuses currently in code

DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT conname
    INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'issue_tickets'::regclass
      AND contype = 'c'
      AND conname = 'issue_tickets_status_check';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE issue_tickets DROP CONSTRAINT %I', constraint_name);
    END IF;

    ALTER TABLE issue_tickets
        ADD CONSTRAINT issue_tickets_status_check
        CHECK (status IN (
            'CREATED',
            'NEED_RESCHEDULE',
            'SCHEDULED',
            'IN_PROGRESS',
            'WAITING_MANAGER_CONFIRM',
            'WAITING_MANAGER_APPROVAL_QUOTE',
            'WAITING_TENANT_APPROVAL_QUOTE',
            'WAITING_STAFF_COMPLETION',
            'WAITING_CASH_PAYMENT',
            'WAITING_PAYMENT',
            'DONE',
            'CLOSED',
            'CANCELLED'
        ));
END $$;
