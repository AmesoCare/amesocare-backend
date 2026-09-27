-- Ameso Care & Hermes POC — PostgreSQL schema

CREATE TABLE app_user (
    id            SERIAL PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,           -- SHA-256 hex (POC only; use bcrypt/argon2 in prod)
    display_name  TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('Patient', 'CareExecutive', 'Admin')),
    patient_id    TEXT NULL,               -- set when role = Patient
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE hospital (
    id         TEXT PRIMARY KEY,           -- HOS1001
    name       TEXT NOT NULL,
    address    TEXT NOT NULL,
    phone      TEXT NOT NULL,
    email      TEXT NOT NULL,
    latitude   DOUBLE PRECISION NOT NULL,
    longitude  DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE patient (
    id                 TEXT PRIMARY KEY,   -- PAT1001
    name               TEXT NOT NULL,
    age                INT NOT NULL,
    blood_group        TEXT NOT NULL,
    phone              TEXT NOT NULL,
    address            TEXT NOT NULL,
    photo_url          TEXT NULL,
    medical_conditions TEXT[] NOT NULL DEFAULT '{}',
    medications        TEXT[] NOT NULL DEFAULT '{}',
    preferred_hospital_id TEXT NOT NULL REFERENCES hospital(id),
    home_latitude      DOUBLE PRECISION NOT NULL,
    home_longitude     DOUBLE PRECISION NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE emergency_contact (
    id         SERIAL PRIMARY KEY,
    patient_id TEXT NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    relation   TEXT NOT NULL,
    phone      TEXT NOT NULL,
    email      TEXT NULL,
    preferred_channel TEXT NOT NULL DEFAULT 'SMS' CHECK (preferred_channel IN ('SMS', 'WhatsApp', 'Email')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE incident (
    id            TEXT PRIMARY KEY,        -- INC-202607270001
    patient_id    TEXT NOT NULL REFERENCES patient(id),
    status        TEXT NOT NULL DEFAULT 'Open'
                  CHECK (status IN ('Open', 'Acknowledged', 'Closed', 'Cancelled')),
    severity      TEXT NOT NULL DEFAULT 'Critical'
                  CHECK (severity IN ('Critical', 'High', 'Medium', 'Low')),
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    acked_at      TIMESTAMPTZ NULL,
    acked_by      TEXT NULL,
    closed_at     TIMESTAMPTZ NULL,
    closed_by     TEXT NULL,
    close_reason  TEXT NULL
);
CREATE INDEX idx_incident_status ON incident(status);
CREATE INDEX idx_incident_created ON incident(created_at DESC);

CREATE TABLE incident_history (
    id          SERIAL PRIMARY KEY,
    incident_id TEXT NOT NULL REFERENCES incident(id) ON DELETE CASCADE,
    event_type  TEXT NOT NULL,             -- Created | Acknowledged | HospitalNotified | ContactsNotified | Closed
    actor       TEXT NOT NULL,             -- patient id, user name, or 'system'
    details     TEXT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_history_incident ON incident_history(incident_id);

CREATE TABLE notification (
    id           SERIAL PRIMARY KEY,
    incident_id  TEXT NOT NULL,
    recipient_type TEXT NOT NULL CHECK (recipient_type IN ('Hospital', 'EmergencyContact')),
    recipient_name TEXT NOT NULL,
    channel      TEXT NOT NULL CHECK (channel IN ('SMS', 'WhatsApp', 'Email')),
    destination  TEXT NOT NULL,            -- phone or email
    subject      TEXT NULL,
    body         TEXT NOT NULL,
    status       TEXT NOT NULL DEFAULT 'Pending'
                 CHECK (status IN ('Pending', 'Sent', 'Delivered', 'Failed')),
    sent_at      TIMESTAMPTZ NULL,
    delivered_at TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_incident ON notification(incident_id);

CREATE TABLE audit_log (
    id          SERIAL PRIMARY KEY,
    event_type  TEXT NOT NULL,             -- SosActivated, SosCancelled, IncidentCreated, Ack, NotificationSent, ...
    service     TEXT NOT NULL,
    actor       TEXT NULL,
    incident_id TEXT NULL,
    payload     JSONB NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_incident ON audit_log(incident_id);
CREATE INDEX idx_audit_occurred ON audit_log(occurred_at DESC);

-- Incident id sequence per day handled in app via this helper table
CREATE TABLE incident_counter (
    day     TEXT PRIMARY KEY,              -- 20260727
    counter INT NOT NULL DEFAULT 0
);
