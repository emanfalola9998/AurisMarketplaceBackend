-- ─── conf/evolutions/default/1.sql ─────────────────────────────────────────
-- Auris — complete database schema
-- Play Evolutions format: !Ups section runs on apply, !Downs on revert
-- Auto-applied in dev, run manually in production.

-- !Ups

-- ─── Extensions ──────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";   -- for fuzzy surgeon search

-- ─── Enums ───────────────────────────────────────────────────────────────────

CREATE TYPE user_role AS ENUM (
  'patient',
  'surgeon',
  'admin'
);

CREATE TYPE surgeon_tier AS ENUM (
  'essential',
  'gold',
  'elite'
);

CREATE TYPE application_status AS ENUM (
  'pending',
  'in_review',
  'approved',
  'rejected',
  'more_info_required'
);

CREATE TYPE consultation_type AS ENUM (
  'in_clinic',
  'virtual'
);

CREATE TYPE booking_status AS ENUM (
  'pending',
  'confirmed',
  'completed',
  'cancelled_by_patient',
  'cancelled_by_surgeon'
);

CREATE TYPE enquiry_status AS ENUM (
  'pending',
  'confirmed',
  'declined',
  'completed',
  'cancelled'
);

CREATE TYPE notification_type AS ENUM (
  'booking_confirmed',
  'booking_cancelled',
  'enquiry_received',
  'enquiry_accepted',
  'enquiry_declined',
  'application_approved',
  'application_rejected',
  'application_more_info',
  'message_received',
  'review_received'
);

-- ─── users ────────────────────────────────────────────────────────────────────
-- Core identity table. One row per account regardless of role.

CREATE TABLE users (
  id                UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
  email             TEXT         NOT NULL UNIQUE,
  password_hash     TEXT         NOT NULL,
  role              user_role    NOT NULL,
  is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
  is_email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);

-- ─── refresh_tokens ───────────────────────────────────────────────────────────
-- Stores hashed refresh tokens for JWT rotation.

CREATE TABLE refresh_tokens (
  id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  TEXT        NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  revoked_at  TIMESTAMPTZ,
  user_agent  TEXT,
  ip_address  TEXT
);

CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

-- ─── email_verifications ─────────────────────────────────────────────────────

CREATE TABLE email_verifications (
  id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token      TEXT        NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── password_resets ─────────────────────────────────────────────────────────

CREATE TABLE password_resets (
  id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT        NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── patient_profiles ────────────────────────────────────────────────────────

CREATE TABLE patient_profiles (
  id                   UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id              UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  first_name           TEXT        NOT NULL,
  last_name            TEXT        NOT NULL,
  date_of_birth        DATE,
  phone                TEXT,
  onboarding_complete  BOOLEAN     NOT NULL DEFAULT FALSE,
  procedure_interests  TEXT[]      NOT NULL DEFAULT '{}',
  location_preference  TEXT,
  consult_preference   consultation_type,
  budget_range         TEXT,
  timeline             TEXT,
  avatar_url           TEXT,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_patient_profiles_user_id ON patient_profiles (user_id);

-- ─── surgeon_profiles ────────────────────────────────────────────────────────

CREATE TABLE surgeon_profiles (
  id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id             UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  title               TEXT         NOT NULL DEFAULT 'Dr.',
  first_name          TEXT         NOT NULL,
  last_name           TEXT         NOT NULL,
  gmc_number          TEXT         NOT NULL UNIQUE,
  qualifications      TEXT[]       NOT NULL DEFAULT '{}',
  medical_school      TEXT,
  graduation_year     SMALLINT,
  fellowships         TEXT,
  specialty           TEXT         NOT NULL,
  subspecialties      TEXT[]       NOT NULL DEFAULT '{}',
  hospital            TEXT         NOT NULL,
  city                TEXT         NOT NULL,
  address             TEXT,
  years_experience    SMALLINT     NOT NULL DEFAULT 0,
  languages           TEXT[]       NOT NULL DEFAULT '{English}',
  bio                 TEXT,
  procedures          TEXT[]       NOT NULL DEFAULT '{}',
  consult_fee_clinic  NUMERIC(10,2),
  consult_fee_virtual NUMERIC(10,2),
  offers_virtual      BOOLEAN      NOT NULL DEFAULT TRUE,
  tier                surgeon_tier NOT NULL DEFAULT 'essential',
  profile_complete    BOOLEAN      NOT NULL DEFAULT FALSE,
  profile_live        BOOLEAN      NOT NULL DEFAULT FALSE,
  avatar_url          TEXT,
  rating              NUMERIC(3,2) NOT NULL DEFAULT 0.00,
  review_count        INTEGER      NOT NULL DEFAULT 0,
  consultation_count  INTEGER      NOT NULL DEFAULT 0,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_rating CHECK (rating >= 0 AND rating <= 5),
  CONSTRAINT chk_consult_fee_clinic  CHECK (consult_fee_clinic  IS NULL OR consult_fee_clinic  > 0),
  CONSTRAINT chk_consult_fee_virtual CHECK (consult_fee_virtual IS NULL OR consult_fee_virtual > 0)
);

CREATE INDEX idx_surgeon_profiles_user_id   ON surgeon_profiles (user_id);
CREATE INDEX idx_surgeon_profiles_specialty  ON surgeon_profiles (specialty);
CREATE INDEX idx_surgeon_profiles_city       ON surgeon_profiles (city);
CREATE INDEX idx_surgeon_profiles_live       ON surgeon_profiles (profile_live);
CREATE INDEX idx_surgeon_profiles_rating     ON surgeon_profiles (rating DESC);
-- trigram index for fuzzy name/specialty search
CREATE INDEX idx_surgeon_profiles_name_trgm ON surgeon_profiles
  USING GIN ((first_name || ' ' || last_name) gin_trgm_ops);

-- ─── surgeon_applications ────────────────────────────────────────────────────

CREATE TABLE surgeon_applications (
  id              UUID               PRIMARY KEY DEFAULT uuid_generate_v4(),
  surgeon_id      UUID               NOT NULL REFERENCES surgeon_profiles(id) ON DELETE CASCADE,
  status          application_status NOT NULL DEFAULT 'pending',
  reviewer_id     UUID               REFERENCES users(id),
  reviewer_notes  TEXT,
  flags           TEXT[]             NOT NULL DEFAULT '{}',
  score           SMALLINT,
  cv_url          TEXT,
  indemnity_url   TEXT,
  photo_url       TEXT,
  submitted_at    TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
  reviewed_at     TIMESTAMPTZ,
  approved_at     TIMESTAMPTZ,

  CONSTRAINT chk_score CHECK (score IS NULL OR (score >= 0 AND score <= 100))
);

CREATE INDEX idx_surgeon_applications_surgeon_id ON surgeon_applications (surgeon_id);
CREATE INDEX idx_surgeon_applications_status     ON surgeon_applications (status);
CREATE INDEX idx_surgeon_applications_reviewer   ON surgeon_applications (reviewer_id);

-- ─── surgeon_availability ────────────────────────────────────────────────────

CREATE TABLE surgeon_availability (
  id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
  surgeon_id      UUID        NOT NULL REFERENCES surgeon_profiles(id) ON DELETE CASCADE,
  day_of_week     SMALLINT    NOT NULL,   -- 1=Mon ... 7=Sun (ISO)
  start_time      TIME        NOT NULL,
  end_time        TIME        NOT NULL,
  buffer_minutes  SMALLINT    NOT NULL DEFAULT 30,
  is_active       BOOLEAN     NOT NULL DEFAULT TRUE,

  CONSTRAINT chk_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
  CONSTRAINT chk_times CHECK (start_time < end_time)
);

CREATE INDEX idx_surgeon_availability_surgeon_id ON surgeon_availability (surgeon_id);

-- ─── surgeon_blocked_slots ───────────────────────────────────────────────────

CREATE TABLE surgeon_blocked_slots (
  id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
  surgeon_id  UUID        NOT NULL REFERENCES surgeon_profiles(id) ON DELETE CASCADE,
  blocked_at  TIMESTAMPTZ NOT NULL,
  duration_mins SMALLINT  NOT NULL DEFAULT 60,
  reason      TEXT
);

CREATE INDEX idx_surgeon_blocked_slots_surgeon ON surgeon_blocked_slots (surgeon_id, blocked_at);

-- ─── saved_surgeons ───────────────────────────────────────────────────────────

CREATE TABLE saved_surgeons (
  patient_id  UUID        NOT NULL REFERENCES patient_profiles(id) ON DELETE CASCADE,
  surgeon_id  UUID        NOT NULL REFERENCES surgeon_profiles(id) ON DELETE CASCADE,
  saved_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  PRIMARY KEY (patient_id, surgeon_id)
);

CREATE INDEX idx_saved_surgeons_patient ON saved_surgeons (patient_id);
CREATE INDEX idx_saved_surgeons_surgeon ON saved_surgeons (surgeon_id);

-- ─── enquiries ───────────────────────────────────────────────────────────────
-- Patient requests a consultation before a formal booking is created.

CREATE TABLE enquiries (
  id                  UUID               PRIMARY KEY DEFAULT uuid_generate_v4(),
  patient_id          UUID               NOT NULL REFERENCES patient_profiles(id),
  surgeon_id          UUID               NOT NULL REFERENCES surgeon_profiles(id),
  procedure_interest  TEXT,
  goals               TEXT,
  previous_surgery    BOOLEAN            NOT NULL DEFAULT FALSE,
  previous_details    TEXT,
  preferred_date      DATE,
  preferred_time      TIME,
  consultation_type   consultation_type  NOT NULL DEFAULT 'in_clinic',
  status              enquiry_status     NOT NULL DEFAULT 'pending',
  fee                 NUMERIC(10,2)      NOT NULL,
  heard_about         TEXT,
  surgeon_notes       TEXT,
  created_at          TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_enquiries_patient_id ON enquiries (patient_id);
CREATE INDEX idx_enquiries_surgeon_id ON enquiries (surgeon_id);
CREATE INDEX idx_enquiries_status     ON enquiries (status);
CREATE INDEX idx_enquiries_created_at ON enquiries (created_at DESC);

-- ─── bookings ────────────────────────────────────────────────────────────────

CREATE TABLE bookings (
  id                 UUID               PRIMARY KEY DEFAULT uuid_generate_v4(),
  enquiry_id         UUID               REFERENCES enquiries(id),
  patient_id         UUID               NOT NULL REFERENCES patient_profiles(id),
  surgeon_id         UUID               NOT NULL REFERENCES surgeon_profiles(id),
  consultation_type  consultation_type  NOT NULL,
  scheduled_at       TIMESTAMPTZ        NOT NULL,
  duration_minutes   SMALLINT           NOT NULL DEFAULT 60,
  status             booking_status     NOT NULL DEFAULT 'pending',
  fee                NUMERIC(10,2)      NOT NULL,
  stripe_payment_id  TEXT,
  stripe_refund_id   TEXT,
  paid_at            TIMESTAMPTZ,
  cancelled_at       TIMESTAMPTZ,
  cancellation_note  TEXT,
  video_link         TEXT,             -- populated for virtual consultations
  notes              TEXT,             -- surgeon post-consultation notes
  created_at         TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ        NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_fee CHECK (fee > 0)
);

CREATE INDEX idx_bookings_patient_id   ON bookings (patient_id);
CREATE INDEX idx_bookings_surgeon_id   ON bookings (surgeon_id);
CREATE INDEX idx_bookings_status       ON bookings (status);
CREATE INDEX idx_bookings_scheduled_at ON bookings (scheduled_at);

-- ─── reviews ─────────────────────────────────────────────────────────────────

CREATE TABLE reviews (
  id                UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
  booking_id        UUID        NOT NULL UNIQUE REFERENCES bookings(id),
  patient_id        UUID        NOT NULL REFERENCES patient_profiles(id),
  surgeon_id        UUID        NOT NULL REFERENCES surgeon_profiles(id),
  rating            SMALLINT    NOT NULL,
  rating_results    SMALLINT,
  rating_communication SMALLINT,
  rating_aftercare  SMALLINT,
  rating_value      SMALLINT,
  procedure         TEXT,
  body              TEXT        NOT NULL,
  is_verified       BOOLEAN     NOT NULL DEFAULT TRUE,
  is_published      BOOLEAN     NOT NULL DEFAULT FALSE,
  published_at      TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_rating              CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT chk_rating_results      CHECK (rating_results      IS NULL OR rating_results      BETWEEN 1 AND 5),
  CONSTRAINT chk_rating_comms        CHECK (rating_communication IS NULL OR rating_communication BETWEEN 1 AND 5),
  CONSTRAINT chk_rating_aftercare    CHECK (rating_aftercare    IS NULL OR rating_aftercare    BETWEEN 1 AND 5),
  CONSTRAINT chk_rating_value        CHECK (rating_value        IS NULL OR rating_value        BETWEEN 1 AND 5)
);

CREATE INDEX idx_reviews_surgeon_id  ON reviews (surgeon_id);
CREATE INDEX idx_reviews_patient_id  ON reviews (patient_id);
CREATE INDEX idx_reviews_published   ON reviews (surgeon_id, is_published, published_at DESC);

-- ─── messages ────────────────────────────────────────────────────────────────

CREATE TABLE messages (
  id           UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
  sender_id    UUID        NOT NULL REFERENCES users(id),
  recipient_id UUID        NOT NULL REFERENCES users(id),
  body         TEXT        NOT NULL,
  is_read      BOOLEAN     NOT NULL DEFAULT FALSE,
  read_at      TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_no_self_message CHECK (sender_id <> recipient_id)
);

CREATE INDEX idx_messages_recipient ON messages (recipient_id, is_read, created_at DESC);
CREATE INDEX idx_messages_sender    ON messages (sender_id, created_at DESC);
CREATE INDEX idx_messages_thread    ON messages (
  LEAST(sender_id, recipient_id),
  GREATEST(sender_id, recipient_id),
  created_at DESC
);

-- ─── notifications ───────────────────────────────────────────────────────────

CREATE TABLE notifications (
  id         UUID               PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id    UUID               NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type       notification_type  NOT NULL,
  title      TEXT               NOT NULL,
  body       TEXT               NOT NULL,
  link       TEXT,
  is_read    BOOLEAN            NOT NULL DEFAULT FALSE,
  read_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, is_read, created_at DESC);

-- ─── surgeon_portfolio ───────────────────────────────────────────────────────

CREATE TABLE surgeon_portfolio (
  id           UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
  surgeon_id   UUID        NOT NULL REFERENCES surgeon_profiles(id) ON DELETE CASCADE,
  before_url   TEXT        NOT NULL,
  after_url    TEXT        NOT NULL,
  procedure    TEXT,
  caption      TEXT,
  consent_given BOOLEAN    NOT NULL DEFAULT FALSE,
  is_published  BOOLEAN    NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_portfolio_surgeon ON surgeon_portfolio (surgeon_id, is_published);

-- ─── audit_log ───────────────────────────────────────────────────────────────
-- Immutable append-only log for admin actions.

CREATE TABLE audit_log (
  id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
  actor_id    UUID        NOT NULL REFERENCES users(id),
  action      TEXT        NOT NULL,
  target_type TEXT        NOT NULL,
  target_id   UUID        NOT NULL,
  metadata    JSONB,
  ip_address  INET,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_actor     ON audit_log (actor_id, created_at DESC);
CREATE INDEX idx_audit_log_target    ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_created   ON audit_log (created_at DESC);

-- ─── Seed: admin user ─────────────────────────────────────────────────────────
-- Password: auris-admin-2025  — CHANGE before going to production.
-- Uses pgcrypto crypt() which produces a $2a$ Blowfish hash compatible with jbcrypt.

INSERT INTO users (email, password_hash, role, is_active, is_email_verified)
VALUES (
  'admin@auris.co',
  crypt('auris-admin-2025', gen_salt('bf', 12)),
  'admin',
  TRUE,
  TRUE
)
ON CONFLICT (email) DO NOTHING;

-- !Downs

DROP TABLE IF EXISTS audit_log             CASCADE;
DROP TABLE IF EXISTS surgeon_portfolio     CASCADE;
DROP TABLE IF EXISTS notifications         CASCADE;
DROP TABLE IF EXISTS messages              CASCADE;
DROP TABLE IF EXISTS reviews               CASCADE;
DROP TABLE IF EXISTS bookings              CASCADE;
DROP TABLE IF EXISTS enquiries             CASCADE;
DROP TABLE IF EXISTS saved_surgeons        CASCADE;
DROP TABLE IF EXISTS surgeon_blocked_slots CASCADE;
DROP TABLE IF EXISTS surgeon_availability  CASCADE;
DROP TABLE IF EXISTS surgeon_applications  CASCADE;
DROP TABLE IF EXISTS surgeon_profiles      CASCADE;
DROP TABLE IF EXISTS patient_profiles      CASCADE;
DROP TABLE IF EXISTS password_resets       CASCADE;
DROP TABLE IF EXISTS email_verifications   CASCADE;
DROP TABLE IF EXISTS refresh_tokens        CASCADE;
DROP TABLE IF EXISTS users                 CASCADE;

DROP TYPE IF EXISTS notification_type;
DROP TYPE IF EXISTS enquiry_status;
DROP TYPE IF EXISTS booking_status;
DROP TYPE IF EXISTS consultation_type;
DROP TYPE IF EXISTS application_status;
DROP TYPE IF EXISTS surgeon_tier;
DROP TYPE IF EXISTS user_role;

DROP EXTENSION IF EXISTS "pg_trgm";
DROP EXTENSION IF EXISTS "pgcrypto";
DROP EXTENSION IF EXISTS "uuid-ossp";