-- DDL versionado de referência; aplicar SOMENTE ao projeto Supabase separado do School Assistant.
-- Não executar por aplicação, CI, Docker ou startup. Chaves privadas nunca entram no banco.
CREATE TABLE IF NOT EXISTS school_assistant.alexa_account_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  guardian_id UUID NOT NULL,
  user_hash CHAR(64) NOT NULL UNIQUE,
  encrypted_user_id TEXT NOT NULL,
  subscribed BOOLEAN NOT NULL DEFAULT FALSE,
  event_time TIMESTAMPTZ,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(tenant_id,id),
  FOREIGN KEY (tenant_id,guardian_id) REFERENCES school_assistant.guardians(tenant_id,id)
);
CREATE TABLE IF NOT EXISTS school_assistant.alexa_link_codes (
  code_hash CHAR(64) PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  guardian_id UUID NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant_id,guardian_id) REFERENCES school_assistant.guardians(tenant_id,id)
);
CREATE TABLE IF NOT EXISTS school_assistant.alexa_link_attempts (
  user_hash CHAR(64) PRIMARY KEY,
  attempts INTEGER NOT NULL DEFAULT 0,
  window_start TIMESTAMPTZ NOT NULL DEFAULT now(),
  locked_until TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS school_assistant.alexa_notice_outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES school_assistant.tenants(id),
  link_id UUID NOT NULL,
  communication_id UUID NOT NULL,
  reference_id VARCHAR(128) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
     CHECK(status IN ('PENDING','SENT','FAILED','CANCELLED')),
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_error VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at TIMESTAMPTZ,
  FOREIGN KEY (tenant_id,link_id) REFERENCES school_assistant.alexa_account_links(tenant_id,id),
  FOREIGN KEY (tenant_id,communication_id) REFERENCES school_assistant.communications(tenant_id,id),
  UNIQUE(link_id,communication_id)
);
CREATE INDEX IF NOT EXISTS idx_alexa_outbox_due ON school_assistant.alexa_notice_outbox(status,next_attempt_at);
ALTER TABLE school_assistant.alexa_account_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE school_assistant.alexa_link_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE school_assistant.alexa_link_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE school_assistant.alexa_notice_outbox ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON ALL TABLES IN SCHEMA school_assistant FROM PUBLIC, anon, authenticated;
REVOKE ALL ON SCHEMA school_assistant FROM PUBLIC, anon, authenticated;
