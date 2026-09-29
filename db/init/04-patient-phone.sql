-- Existing registrations may lack a phone. Preserve them while enforcing the rule
-- for new or updated registered patient rows. Legacy demo patients are exempt.
ALTER TABLE patient ADD CONSTRAINT patient_registration_phone_valid
    CHECK (device_id IS NULL OR (phone IS NOT NULL AND phone ~ '^[0-9]{10}$')) NOT VALID;
