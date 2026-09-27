-- Seed data — Ameso Care & Hermes POC

INSERT INTO hospital (id, name, address, phone, email, latitude, longitude) VALUES
('HOS1001', 'Gleneagles BGS Kengeri', 'No. 67, Uttarahalli Main Road, Sunkalpalya, near SJB IT College, Bengaluru 560060', '+918095609422', 'srivatsahg@gmail.com', 12.8996, 77.4827),
('HOS1002', 'JSS Hospital', 'MG Road, Ramachandra Agrahara, Mysuru, Karnataka 570004', '+919886300072', 'aartisudheendra20@gmail.com', 12.2959, 76.6552);

INSERT INTO patient (id, name, age, blood_group, phone, address, photo_url, medical_conditions, medications, preferred_hospital_id, home_latitude, home_longitude) VALUES
('PAT1001', 'John Doe', 76, 'O+', '+919886300072', '21, KR Road, Bangalore 560001 ',
 NULL, ARRAY['Diabetes', 'Hypertension'], ARRAY['Metformin 500mg', 'Amlodipine 5mg'],
 'HOS1001', 12.971599, 77.594566),
('PAT1002', 'Ingrid Larsen', 82, 'A-', '+918095609422', '201, Sai Nandana Apartments, Kenchenahalli Road, Bangalore 560098',
 NULL, ARRAY['Atrial fibrillation', 'Osteoporosis'], ARRAY['Warfarin 3mg', 'Calcium + D3'],
 'HOS1001', 12.9095,77.5119),
('PAT1003', 'Erik Nielsen', 71, 'B+', '+918095609422', 'Vesterbrogade 101, 1620 København V',
 NULL, ARRAY['COPD'], ARRAY['Salbutamol inhaler'],
 'HOS1002', 12.3003,76.6238);

INSERT INTO emergency_contact (patient_id, name, relation, phone, email, preferred_channel) VALUES
('PAT1001', 'Priya Patel', 'Daughter', '+918095609422', 'priya.patel@example.com', 'WhatsApp'),
('PAT1001', 'Daksh Sharma', 'Son', '+918095609422', 'michael.doe@example.com', 'SMS'),
('PAT1002', 'Søren Larsen', 'Son', '+919482996670', 'soren.larsen@example.com', 'Email'),
('PAT1003', 'Anna Nielsen', 'Wife', '+918151050502', 'anna.nielsen@example.com', 'SMS');

-- Demo users. Password for all: demo123 (SHA-256 hex)
-- sha256('demo123') = d3ad9315b7be5dd53b31a273b3b3aba5defe700808305aa16a3062b76658a791
INSERT INTO app_user (username, password_hash, display_name, role, patient_id) VALUES
('john.doe',   'd3ad9315b7be5dd53b31a273b3b3aba5defe700808305aa16a3062b76658a791', 'John Doe', 'Patient', 'PAT1001'),
('care.anna',  'd3ad9315b7be5dd53b31a273b3b3aba5defe700808305aa16a3062b76658a791', 'Anna Berg', 'CareExecutive', NULL),
('care.lars',  'd3ad9315b7be5dd53b31a273b3b3aba5defe700808305aa16a3062b76658a791', 'Lars Holm', 'CareExecutive', NULL);

-- One closed historical incident for "previous incidents" panel
INSERT INTO incident (id, patient_id, status, severity, latitude, longitude, created_at, acked_at, acked_by, closed_at, closed_by, close_reason) VALUES
('INC-202606150001', 'PAT1001', 'Closed', 'Critical', 12.971599, 77.594566,
 '2026-06-15T09:12:00Z', '2026-06-15T09:12:40Z', 'Anna Berg', '2026-06-15T09:45:00Z', 'Anna Berg', 'Ambulance dispatched, patient stabilized');

INSERT INTO incident_history (incident_id, event_type, actor, details, occurred_at) VALUES
('INC-202606150001', 'Created', 'PAT1001', 'SOS activated', '2026-06-15T09:12:00Z'),
('INC-202606150001', 'Acknowledged', 'Anna Berg', NULL, '2026-06-15T09:12:40Z'),
('INC-202606150001', 'Closed', 'Anna Berg', 'Ambulance dispatched, patient stabilized', '2026-06-15T09:45:00Z');
