-- Existing students keep a NULL gender until it is recorded. A student needs a recorded gender
-- before a check-in or transfer, but current stays are not affected.
ALTER TABLE student ADD COLUMN gender VARCHAR(6);
ALTER TABLE student ADD CONSTRAINT student_gender_values CHECK (gender IN ('FEMALE', 'MALE'));
ALTER TABLE student ADD COLUMN phone VARCHAR(30);
ALTER TABLE student ADD COLUMN email VARCHAR(120);

-- Existing buildings stay mixed, so the migration rejects no current assignment.
ALTER TABLE building ADD COLUMN gender_policy VARCHAR(12) NOT NULL DEFAULT 'MIXED';
ALTER TABLE building ADD CONSTRAINT building_gender_policy_values
    CHECK (gender_policy IN ('MIXED', 'FEMALE_ONLY', 'MALE_ONLY'));
