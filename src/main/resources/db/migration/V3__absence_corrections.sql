-- Absence records can be corrected and withdrawn. A withdrawn record stays in the table with who
-- withdrew it, when and why, and is restored if the same absence is recorded again.
ALTER TABLE absence_record ADD COLUMN updated_at TIMESTAMP(6);
ALTER TABLE absence_record ADD COLUMN updated_by BIGINT;
ALTER TABLE absence_record ADD CONSTRAINT absence_record_updated_by_fk FOREIGN KEY (updated_by) REFERENCES app_user(id);
ALTER TABLE absence_record ADD CONSTRAINT absence_record_update_complete
    CHECK ((updated_at IS NULL AND updated_by IS NULL) OR (updated_at IS NOT NULL AND updated_by IS NOT NULL));

ALTER TABLE absence_record ADD COLUMN withdrawn_at TIMESTAMP(6);
ALTER TABLE absence_record ADD COLUMN withdrawn_by BIGINT;
ALTER TABLE absence_record ADD COLUMN withdrawal_reason VARCHAR(240);
ALTER TABLE absence_record ADD CONSTRAINT absence_record_withdrawn_by_fk FOREIGN KEY (withdrawn_by) REFERENCES app_user(id);
ALTER TABLE absence_record ADD CONSTRAINT absence_record_withdrawal_complete CHECK (
    (withdrawn_at IS NULL AND withdrawn_by IS NULL AND withdrawal_reason IS NULL)
    OR (withdrawn_at IS NOT NULL AND withdrawn_by IS NOT NULL AND withdrawal_reason IS NOT NULL)
);
