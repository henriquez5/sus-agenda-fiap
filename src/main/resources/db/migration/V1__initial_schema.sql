CREATE TABLE patient (
 id VARCHAR(36) PRIMARY KEY,
 name VARCHAR(120) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE agenda (
 id VARCHAR(36) PRIMARY KEY,
 unit_name VARCHAR(120) NOT NULL,
 specialty VARCHAR(80) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(unit_name, specialty)
);
CREATE TABLE slot (
 id VARCHAR(36) PRIMARY KEY,
 agenda_id VARCHAR(36) NOT NULL REFERENCES agenda(id),
 starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
 status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN','RESERVED','BOOKED')),
 UNIQUE(agenda_id, starts_at)
);
CREATE TABLE appointment (
 id VARCHAR(36) PRIMARY KEY,
 slot_id VARCHAR(36) NOT NULL REFERENCES slot(id),
 patient_id VARCHAR(36) NOT NULL REFERENCES patient(id),
 status VARCHAR(16) NOT NULL CHECK (status IN ('CONFIRMED','CANCELLED')),
 source VARCHAR(16) NOT NULL CHECK (source IN ('DIRECT','WAITLIST')),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE wait_entry (
 id VARCHAR(36) PRIMARY KEY,
 sequence_no BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
 agenda_id VARCHAR(36) NOT NULL REFERENCES agenda(id),
 patient_id VARCHAR(36) NOT NULL REFERENCES patient(id),
 status VARCHAR(16) NOT NULL CHECK (status IN ('WAITING','OFFERED','BOOKED','EXPIRED','DECLINED','WITHDRAWN')),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE offer (
 id VARCHAR(36) PRIMARY KEY,
 slot_id VARCHAR(36) NOT NULL REFERENCES slot(id),
 wait_entry_id VARCHAR(36) NOT NULL REFERENCES wait_entry(id),
 status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','EXPIRED','DECLINED')),
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 appointment_id VARCHAR(36) REFERENCES appointment(id)
);
CREATE TABLE domain_event (
 id VARCHAR(36) PRIMARY KEY,
 sequence_no BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
 event_type VARCHAR(60) NOT NULL,
 aggregate_id VARCHAR(36) NOT NULL,
 actor VARCHAR(80) NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_slot_agenda_status ON slot(agenda_id, status, starts_at);
CREATE INDEX idx_appointment_patient ON appointment(patient_id, status);
CREATE INDEX idx_wait_fifo ON wait_entry(agenda_id, status, sequence_no);
CREATE INDEX idx_offer_expiration ON offer(status, expires_at);
CREATE INDEX idx_event_aggregate ON domain_event(aggregate_id, sequence_no);
