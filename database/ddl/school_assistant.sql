-- CANONICAL DDL ONLY. Already applied to the dedicated Supabase project.
-- NEVER AUTO-EXECUTE FROM APP STARTUP, CI/CD OR DEPLOY.
-- PostgreSQL 17; schema school_assistant. No existing Bravum schema is modified.
CREATE SCHEMA IF NOT EXISTS school_assistant;

CREATE TABLE school_assistant.tenants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(180) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE school_assistant.guardians (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  external_user_id UUID,
  full_name VARCHAR(200) NOT NULL,
  email VARCHAR(320),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id,id)
);

CREATE TABLE school_assistant.students (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  display_name VARCHAR(200) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id,id)
);

CREATE TABLE school_assistant.student_guardians (
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  student_id UUID NOT NULL,
  guardian_id UUID NOT NULL,
  relationship VARCHAR(40) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id,student_id,guardian_id),
  FOREIGN KEY (tenant_id,student_id) REFERENCES school_assistant.students(tenant_id,id),
  FOREIGN KEY (tenant_id,guardian_id) REFERENCES school_assistant.guardians(tenant_id,id)
);

CREATE TABLE school_assistant.schools (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(200) NOT NULL,
  provider_school_id VARCHAR(180),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE school_assistant.enrollments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  student_id UUID NOT NULL,
  school_id UUID NOT NULL REFERENCES school_assistant.schools(id),
  academic_year INTEGER NOT NULL CHECK (academic_year BETWEEN 2000 AND 2200),
  classroom VARCHAR(120),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','TRANSFERRED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id,student_id,school_id,academic_year),
  FOREIGN KEY (tenant_id,student_id) REFERENCES school_assistant.students(tenant_id,id)
);

CREATE TABLE school_assistant.provider_connections (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  provider_code VARCHAR(60) NOT NULL,
  connection_name VARCHAR(180),
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACTIVE','INACTIVE','ERROR')),
  credential_ref VARCHAR(300),
  config JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id,id)
);

CREATE TABLE school_assistant.provider_student_contexts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  connection_id UUID NOT NULL,
  context_key VARCHAR(200) NOT NULL,
  external_student_id VARCHAR(180),
  student_id UUID,
  mapping_status VARCHAR(30) NOT NULL DEFAULT 'UNCONFIRMED'
    CHECK (mapping_status IN ('UNCONFIRMED','CONFIRMED','REJECTED')),
  last_seen_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id,id),
  UNIQUE (connection_id,context_key),
  FOREIGN KEY (tenant_id,connection_id) REFERENCES school_assistant.provider_connections(tenant_id,id),
  FOREIGN KEY (tenant_id,student_id) REFERENCES school_assistant.students(tenant_id,id),
  CHECK (mapping_status <> 'CONFIRMED' OR student_id IS NOT NULL)
);

CREATE TABLE school_assistant.inbound_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  connection_id UUID NOT NULL,
  event_key VARCHAR(240) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
    CHECK (status IN ('RECEIVED','PROCESSING','PROCESSED','FAILED')),
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ,
  error_message TEXT,
  UNIQUE (tenant_id,id),
  UNIQUE (connection_id,event_key),
  FOREIGN KEY (tenant_id,connection_id) REFERENCES school_assistant.provider_connections(tenant_id,id)
);

CREATE TABLE school_assistant.communications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  connection_id UUID,
  inbound_event_id UUID,
  source_fingerprint VARCHAR(128) NOT NULL,
  external_communication_id VARCHAR(200),
  title VARCHAR(350),
  body TEXT,
  category VARCHAR(100),
  published_at TIMESTAMPTZ,
  event_at TIMESTAMPTZ,
  captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  source_type VARCHAR(40) NOT NULL DEFAULT 'VISIBLE_DOM',
  content_complete BOOLEAN NOT NULL DEFAULT FALSE,
  review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
    CHECK (review_status IN ('PENDING','REVIEWED','REJECTED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id,id),
  UNIQUE (tenant_id,source_fingerprint),
  FOREIGN KEY (tenant_id,connection_id) REFERENCES school_assistant.provider_connections(tenant_id,id),
  FOREIGN KEY (tenant_id,inbound_event_id) REFERENCES school_assistant.inbound_events(tenant_id,id)
);

CREATE TABLE school_assistant.communication_recipients (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  communication_id UUID NOT NULL,
  student_id UUID,
  student_context_id UUID,
  mapping_status VARCHAR(30) NOT NULL DEFAULT 'UNCONFIRMED'
    CHECK (mapping_status IN ('UNCONFIRMED','CONFIRMED','REJECTED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant_id,communication_id) REFERENCES school_assistant.communications(tenant_id,id) ON DELETE CASCADE,
  FOREIGN KEY (tenant_id,student_id) REFERENCES school_assistant.students(tenant_id,id),
  FOREIGN KEY (tenant_id,student_context_id) REFERENCES school_assistant.provider_student_contexts(tenant_id,id),
  CHECK (student_id IS NOT NULL OR student_context_id IS NOT NULL),
  CHECK (mapping_status <> 'CONFIRMED' OR student_id IS NOT NULL),
  UNIQUE NULLS NOT DISTINCT (tenant_id,communication_id,student_id,student_context_id)
);

CREATE TABLE school_assistant.assignments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  communication_id UUID NOT NULL,
  source_item_key VARCHAR(100) NOT NULL DEFAULT 'primary',
  student_id UUID,
  student_context_id UUID,
  classification VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN'
    CHECK (classification IN ('HOMEWORK','SCHOOL_EVENT','MATERIAL_REQUEST','GENERAL_NOTICE','UNKNOWN')),
  title VARCHAR(350) NOT NULL,
  description TEXT,
  due_at TIMESTAMPTZ,
  due_date_source VARCHAR(120),
  confidence NUMERIC(4,3) CHECK (confidence BETWEEN 0 AND 1),
  requires_review BOOLEAN NOT NULL DEFAULT TRUE,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','CANCELLED')),
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id,id),
  UNIQUE (tenant_id,communication_id,source_item_key),
  FOREIGN KEY (tenant_id,communication_id) REFERENCES school_assistant.communications(tenant_id,id),
  FOREIGN KEY (tenant_id,student_id) REFERENCES school_assistant.students(tenant_id,id),
  FOREIGN KEY (tenant_id,student_context_id) REFERENCES school_assistant.provider_student_contexts(tenant_id,id)
);

CREATE TABLE school_assistant.assignment_materials (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  assignment_id UUID NOT NULL,
  description VARCHAR(500) NOT NULL,
  attachment_ref VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant_id,assignment_id) REFERENCES school_assistant.assignments(tenant_id,id) ON DELETE CASCADE
);

CREATE TABLE school_assistant.reminder_rules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  channel VARCHAR(30) NOT NULL CHECK (channel IN ('ALEXA','MOBILE_PUSH','CHATGPT','EMAIL')),
  minutes_before INTEGER NOT NULL CHECK (minutes_before >= 0),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id,id),
  UNIQUE (tenant_id,channel,minutes_before)
);

CREATE TABLE school_assistant.reminder_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  assignment_id UUID NOT NULL,
  rule_id UUID NOT NULL,
  scheduled_at TIMESTAMPTZ NOT NULL,
  channel VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','CANCELLED')),
  attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  next_attempt_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id,id),
  UNIQUE (tenant_id,assignment_id,rule_id),
  FOREIGN KEY (tenant_id,assignment_id) REFERENCES school_assistant.assignments(tenant_id,id),
  FOREIGN KEY (tenant_id,rule_id) REFERENCES school_assistant.reminder_rules(tenant_id,id)
);

CREATE TABLE school_assistant.notification_endpoints (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  guardian_id UUID NOT NULL,
  channel VARCHAR(30) NOT NULL CHECK (channel IN ('ALEXA','MOBILE_PUSH','CHATGPT','EMAIL')),
  provider_ref VARCHAR(300),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant_id,guardian_id) REFERENCES school_assistant.guardians(tenant_id,id)
);

CREATE TABLE school_assistant.notification_deliveries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  reminder_job_id UUID NOT NULL,
  endpoint_id UUID,
  provider VARCHAR(100) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','SENT','DELIVERED','FAILED','CANCELLED')),
  provider_message_id VARCHAR(250),
  provider_status_code VARCHAR(40),
  sent_at TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant_id,reminder_job_id) REFERENCES school_assistant.reminder_jobs(tenant_id,id),
  FOREIGN KEY (endpoint_id) REFERENCES school_assistant.notification_endpoints(id)
);

CREATE TABLE school_assistant.outbox_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_id UUID NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);

CREATE INDEX idx_guardians_tenant ON school_assistant.guardians(tenant_id);
CREATE INDEX idx_students_tenant ON school_assistant.students(tenant_id);
CREATE INDEX idx_enrollments_student ON school_assistant.enrollments(tenant_id,student_id);
CREATE INDEX idx_context_mapping ON school_assistant.provider_student_contexts(tenant_id,mapping_status);
CREATE INDEX idx_inbound_events_status ON school_assistant.inbound_events(status,received_at);
CREATE INDEX idx_communications_published ON school_assistant.communications(tenant_id,published_at DESC);
CREATE INDEX idx_recipients_student ON school_assistant.communication_recipients(tenant_id,student_id);
CREATE INDEX idx_assignments_student_due ON school_assistant.assignments(tenant_id,student_id,due_at);
CREATE INDEX idx_assignments_pending ON school_assistant.assignments(tenant_id,status,due_at);
CREATE INDEX idx_reminders_due ON school_assistant.reminder_jobs(status,scheduled_at);
CREATE INDEX idx_deliveries_job ON school_assistant.notification_deliveries(tenant_id,reminder_job_id);
CREATE INDEX idx_outbox_pending ON school_assistant.outbox_events(status,created_at);

REVOKE ALL ON SCHEMA school_assistant FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL TABLES IN SCHEMA school_assistant FROM PUBLIC, anon, authenticated;
