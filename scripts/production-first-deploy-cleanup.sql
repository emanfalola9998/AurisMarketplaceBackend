-- ─── scripts/production-first-deploy-cleanup.sql ──────────────────────────────
--
-- Run this ONCE against the production database, immediately after the
-- Play evolutions have applied for the first time and BEFORE opening the
-- site to real signups.
--
-- Why this is needed: conf/evolutions/default/2.sql, 3.sql, and 5.sql are
-- not schema migrations — they're demo seed data (5 fake surgeons, fake
-- patients, bookings, enquiries, reviews, messages) for local dev. Play
-- evolutions apply strictly in numeric order with no way to skip some, so
-- a brand-new production database ends up with that same fake data unless
-- it's removed afterward. Evolution 1.sql also seeds the real admin user
-- with a password that's printed in that file's own comment
-- ("auris-admin-2025" — CHANGE before going to production), which this
-- script also rotates.
--
-- This deletes every row created by those seed evolutions while keeping
-- the one real row: the admin user itself (by email, so it survives
-- regardless of which evolution created it).
--
-- Usage:
--   1. Edit the password literal in the final UPDATE below to a real,
--      strong, unique password — do not run this with the placeholder
--      still in place.
--   2. psql "$DATABASE_URL" -f scripts/production-first-deploy-cleanup.sql
--
-- Safe to run only once on a fresh database with no real users yet — it is
-- NOT a generic "wipe demo data" tool to run later once real patients,
-- surgeons, or bookings exist, since by then this blunt an approach would
-- delete real data too.

BEGIN;

DELETE FROM platform_fees;
DELETE FROM reviews;
DELETE FROM bookings;
DELETE FROM enquiries;
DELETE FROM messages;
DELETE FROM audit_log
  WHERE actor_id <> (SELECT id FROM users WHERE email = 'admin@auris.co');

-- Cascades to patient_profiles, surgeon_profiles, refresh_tokens,
-- email_verifications, password_resets, notifications, and (via those)
-- saved_surgeons, surgeon_applications, surgeon_availability,
-- surgeon_blocked_slots, surgeon_portfolio.
DELETE FROM users WHERE email <> 'admin@auris.co';

UPDATE users
SET password_hash = crypt('REPLACE_WITH_YOUR_OWN_STRONG_PASSWORD', gen_salt('bf', 12))
WHERE email = 'admin@auris.co';

COMMIT;
