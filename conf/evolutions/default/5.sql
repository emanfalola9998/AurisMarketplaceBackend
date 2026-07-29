-- ─── conf/evolutions/default/5.sql ──────────────────────────────────────────
-- Fix: the 10 seed accounts from evolution 2 (5 surgeons + 5 patients) no
-- longer sign in with the documented password "Seed1234!" on databases seeded
-- before that file's password literal was finalised — their stored hashes
-- predate it. Evolutions only ever apply once, so already-seeded databases
-- never picked up the corrected value. Reset them explicitly by id.

-- !Ups

UPDATE users
SET password_hash = crypt('Seed1234!', gen_salt('bf', 12))
WHERE id IN (
  'a1000000-0000-0000-0000-000000000001',
  'a1000000-0000-0000-0000-000000000002',
  'a1000000-0000-0000-0000-000000000003',
  'a1000000-0000-0000-0000-000000000004',
  'a1000000-0000-0000-0000-000000000005',
  'c1000000-0000-0000-0000-000000000001',
  'c1000000-0000-0000-0000-000000000002',
  'c1000000-0000-0000-0000-000000000003',
  'c1000000-0000-0000-0000-000000000004',
  'c1000000-0000-0000-0000-000000000005'
);

-- !Downs

-- No-op: the prior password hashes weren't for a known/documented password,
-- so there's nothing meaningful to restore.
