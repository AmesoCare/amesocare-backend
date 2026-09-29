-- Apply once to existing databases; also used by fresh Docker initialization.
BEGIN;
ALTER TABLE patient
    ADD COLUMN device_id UUID UNIQUE,
    ADD COLUMN gender VARCHAR(200),
    ADD COLUMN medication VARCHAR(200),
    ADD COLUMN allergies VARCHAR(200),
    ADD COLUMN surgery VARCHAR(200),
    ADD COLUMN remarks VARCHAR(200),
    ALTER COLUMN phone DROP NOT NULL,
    ALTER COLUMN preferred_hospital_id DROP NOT NULL,
    ALTER COLUMN home_latitude DROP NOT NULL,
    ALTER COLUMN home_longitude DROP NOT NULL;
ALTER TABLE patient ADD CONSTRAINT patient_registration_valid CHECK (
    device_id IS NULL OR (
        length(trim(name)) BETWEEN 1 AND 200 AND age BETWEEN 1 AND 120
        AND gender IS NOT NULL AND gender IN ('Male', 'Female')
        AND length(trim(address)) BETWEEN 1 AND 200
        AND blood_group IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')
    )
);
ALTER TABLE incident ALTER COLUMN latitude DROP NOT NULL, ALTER COLUMN longitude DROP NOT NULL;
COMMIT;
