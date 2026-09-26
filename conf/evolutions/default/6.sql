-- ─── conf/evolutions/default/6.sql ──────────────────────────────────────────
-- Fix: audit_log.ip_address was declared INET, but AuditLogRepository (and
-- the AuditLogTable Slick mapping) treats it as a plain String with no
-- cast — every INSERT (every admin approve/reject/suspend action) fails
-- with "column ip_address is of type inet but expression is of type
-- character varying". refresh_tokens.ip_address stores the same kind of
-- value as TEXT and works fine; align audit_log with that instead of
-- teaching Slick to cast to inet for no real benefit (nothing here ever
-- uses inet-specific operators like subnet containment).

-- !Ups

ALTER TABLE audit_log ALTER COLUMN ip_address TYPE TEXT;

-- !Downs

ALTER TABLE audit_log ALTER COLUMN ip_address TYPE INET USING ip_address::INET;
