-- ─── conf/evolutions/default/4.sql ─────────────────────────────────────────
-- Surgeon billing: annual membership fee (Stripe subscription) + a 1%
-- platform fee ledger on every paid booking. Auris collects all patient
-- payments directly (no Stripe Connect) and pays surgeons their share
-- manually outside Stripe — this ledger is what that manual payout is
-- reconciled against.

-- !Ups

CREATE TYPE subscription_status AS ENUM (
  'none',
  'active',
  'past_due',
  'canceled'
);

ALTER TABLE surgeon_profiles
  ADD COLUMN stripe_customer_id     TEXT,
  ADD COLUMN subscription_status    subscription_status NOT NULL DEFAULT 'none',
  ADD COLUMN subscription_renews_at TIMESTAMPTZ;

CREATE TYPE platform_fee_type AS ENUM (
  'transaction',
  'annual_membership'
);

CREATE TABLE platform_fees (
  id          UUID              PRIMARY KEY DEFAULT uuid_generate_v4(),
  surgeon_id  UUID              NOT NULL REFERENCES surgeon_profiles(id) ON DELETE CASCADE,
  booking_id  UUID              REFERENCES bookings(id) ON DELETE SET NULL,
  fee_type    platform_fee_type NOT NULL,
  amount      NUMERIC(10,2)     NOT NULL,
  paid_out    BOOLEAN           NOT NULL DEFAULT FALSE,
  paid_out_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_platform_fees_surgeon_id ON platform_fees (surgeon_id);
CREATE INDEX idx_platform_fees_paid_out   ON platform_fees (paid_out);

-- !Downs

DROP TABLE IF EXISTS platform_fees;
DROP TYPE IF EXISTS platform_fee_type;

ALTER TABLE surgeon_profiles
  DROP COLUMN IF EXISTS stripe_customer_id,
  DROP COLUMN IF EXISTS subscription_status,
  DROP COLUMN IF EXISTS subscription_renews_at;

DROP TYPE IF EXISTS subscription_status;
