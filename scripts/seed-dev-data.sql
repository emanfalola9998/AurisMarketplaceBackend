-- ─── scripts/seed-dev-data.sql ────────────────────────────────────────────────
--
-- Demo data for local development: 5 approved, live surgeons and 5 patients.
-- Not part of conf/evolutions — this is dev-only fixture data, not schema.
-- Safe to re-run (ON CONFLICT DO NOTHING throughout).
--
-- Usage:
--   psql -U auris -h localhost -p 5432 -d auris_dev -f scripts/seed-dev-data.sql
--
-- Passwords (bcrypt-hashed via pgcrypto, same mechanism as the admin seed in
-- conf/evolutions/default/1.sql):
--   all 5 surgeons  -> Surgeon123!
--   all 5 patients  -> Patient123!

-- ─── Surgeons ───────────────────────────────────────────────────────────────

INSERT INTO users (id, email, password_hash, role, is_active, is_email_verified)
VALUES
  ('a1000000-0000-0000-0000-000000000001', 'james.harrison@auris.co',  crypt('Surgeon123!', gen_salt('bf', 12)), 'surgeon', TRUE, TRUE),
  ('a1000000-0000-0000-0000-000000000002', 'sarah.mitchell@auris.co',  crypt('Surgeon123!', gen_salt('bf', 12)), 'surgeon', TRUE, TRUE),
  ('a1000000-0000-0000-0000-000000000003', 'robert.chen@auris.co',     crypt('Surgeon123!', gen_salt('bf', 12)), 'surgeon', TRUE, TRUE),
  ('a1000000-0000-0000-0000-000000000004', 'emily.thompson@auris.co',  crypt('Surgeon123!', gen_salt('bf', 12)), 'surgeon', TRUE, TRUE),
  ('a1000000-0000-0000-0000-000000000005', 'david.okafor@auris.co',    crypt('Surgeon123!', gen_salt('bf', 12)), 'surgeon', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO surgeon_profiles (
  id, user_id, title, first_name, last_name, gmc_number,
  qualifications, medical_school, graduation_year, fellowships,
  specialty, subspecialties, hospital, city, address,
  years_experience, languages, bio, procedures,
  consult_fee_clinic, consult_fee_virtual, offers_virtual,
  tier, profile_complete, profile_live, rating, review_count, consultation_count
) VALUES
  (
    'b1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001',
    'Mr.', 'James', 'Harrison', '7654321',
    ARRAY['FRCS(Plast)','MBBS','MD'], 'King''s College London', 2002,
    'Fellowship in Craniofacial Surgery, Great Ormond Street Hospital',
    'Plastic & Reconstructive Surgery', ARRAY['Rhinoplasty','Facelift','Breast Surgery'],
    'King''s College Hospital', 'London', 'Denmark Hill, London SE5 9RS',
    18, ARRAY['English','French'],
    'Mr James Harrison is a Consultant Plastic and Reconstructive Surgeon at King''s College Hospital with over 18 years of experience. He specialises in aesthetic facial and body procedures, with a particular interest in rhinoplasty and facial rejuvenation. Mr Harrison trained at King''s College London and completed a prestigious fellowship in craniofacial surgery at Great Ormond Street Hospital.',
    ARRAY['Rhinoplasty','Revision Rhinoplasty','Facelift','Neck Lift','Blepharoplasty','Breast Augmentation','Breast Reduction','Liposuction','Tummy Tuck','Ear Reshaping'],
    350, 200, TRUE, 'elite', TRUE, TRUE, 4.9, 47, 65
  ),
  (
    'b1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000002',
    'Mr.', 'Sarah', 'Mitchell', '6543210',
    ARRAY['FRCS(ORL-HNS)','MBChB','DOHNS'], 'University of Manchester', 2008,
    'Advanced Rhinoplasty Fellowship, Amsterdam',
    'Rhinoplasty & Facial Aesthetics', ARRAY['Rhinoplasty','Revision Rhinoplasty','Septoplasty'],
    'Spire Manchester Hospital', 'Manchester', 'The Drive, Didsbury, Manchester M20 2LN',
    12, ARRAY['English'],
    'Mr Sarah Mitchell is a specialist rhinoplasty and facial aesthetics surgeon based in Manchester. With 12 years of dedicated experience she has performed over 1,000 rhinoplasty procedures and is known for achieving natural, harmonious results. Mr Mitchell completed an advanced rhinoplasty fellowship in Amsterdam, one of the leading centres for nasal surgery in Europe.',
    ARRAY['Rhinoplasty','Revision Rhinoplasty','Septoplasty','Septorhinoplasty','Ear Reshaping','Chin Augmentation','Lip Augmentation'],
    280, 150, TRUE, 'gold', TRUE, TRUE, 4.7, 31, 42
  ),
  (
    'b1000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000003',
    'Mr.', 'Robert', 'Chen', '5432109',
    ARRAY['FRCOphth','MBBChir','MD(Cantab)'], 'University of Cambridge', 2005,
    'Oculoplastic Fellowship, Moorfields Eye Hospital',
    'Oculoplastic & Facial Surgery', ARRAY['Blepharoplasty','Brow Lift','Facial Rejuvenation'],
    'The London Clinic', 'London', '20 Devonshire Place, London W1G 6BW',
    15, ARRAY['English','Mandarin'],
    'Mr Robert Chen is an Oculoplastic and Facial Surgeon at The London Clinic on Harley Street. He trained at Cambridge University and completed his oculoplastic fellowship at Moorfields Eye Hospital. Mr Chen specialises in eyelid surgery, brow lifting, and facial rejuvenation, combining surgical precision with a meticulous eye for natural, rested results.',
    ARRAY['Upper Blepharoplasty','Lower Blepharoplasty','Brow Lift','Ptosis Repair','Facelift','Neck Lift','Fat Transfer'],
    320, 180, TRUE, 'gold', TRUE, TRUE, 4.8, 28, 38
  ),
  (
    'b1000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000004',
    'Mr.', 'Emily', 'Thompson', '4321098',
    ARRAY['FRCS(Plast)','MBChB'], 'University of Birmingham', 2012,
    NULL,
    'Breast Surgery', ARRAY['Breast Augmentation','Breast Reduction','Breast Lift'],
    'BMI The Priory Hospital', 'Birmingham', 'Priory Road, Birmingham B5 7UG',
    8, ARRAY['English'],
    'Mr Emily Thompson is a Consultant Plastic Surgeon specialising in breast surgery at BMI The Priory Hospital in Birmingham. With 8 years of experience she is passionate about delivering safe, high-quality breast surgery tailored to each patient''s goals. Mr Thompson takes a thorough, patient-centred approach to every consultation.',
    ARRAY['Breast Augmentation','Breast Reduction','Breast Lift','Breast Implant Removal','Breast Implant Exchange','Nipple Correction','Male Chest Reduction'],
    250, 130, TRUE, 'essential', TRUE, TRUE, 4.5, 19, 27
  ),
  (
    'b1000000-0000-0000-0000-000000000005', 'a1000000-0000-0000-0000-000000000005',
    'Mr.', 'David', 'Okafor', '3210987',
    ARRAY['FRCS(Plast)','MBBS','MSc'], 'University of Edinburgh', 2010,
    'Body Contouring Fellowship, São Paulo',
    'Body Contouring & Reconstructive Surgery', ARRAY['Liposuction','Tummy Tuck','Body Lift'],
    'Ross Hall Hospital', 'Glasgow', 'Belfast Road, Glasgow G52 2UH',
    10, ARRAY['English','Yoruba'],
    'Mr David Okafor is a Consultant Plastic Surgeon specialising in body contouring procedures at Ross Hall Hospital in Glasgow. He trained at the University of Edinburgh and completed a body contouring fellowship in São Paulo, Brazil. Mr Okafor is known for his sculpting approach to liposuction and his expertise in post-weight-loss body procedures.',
    ARRAY['Liposuction','Tummy Tuck','Extended Tummy Tuck','Body Lift','Arm Lift','Thigh Lift','Brazilian Butt Lift','Mummy Makeover'],
    220, 120, TRUE, 'essential', TRUE, TRUE, 4.6, 22, 30
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO surgeon_applications (id, surgeon_id, status, reviewer_notes, score, submitted_at, reviewed_at, approved_at)
VALUES
  ('f1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'approved', 'Excellent credentials. Elite tier approved.', 98, NOW() - INTERVAL '8 months', NOW() - INTERVAL '8 months' + INTERVAL '2 days', NOW() - INTERVAL '8 months' + INTERVAL '2 days'),
  ('f1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000002', 'approved', 'Strong rhinoplasty specialism. Gold tier.',   87, NOW() - INTERVAL '7 months', NOW() - INTERVAL '7 months' + INTERVAL '3 days', NOW() - INTERVAL '7 months' + INTERVAL '3 days'),
  ('f1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000003', 'approved', 'Impressive oculoplastic background. Gold tier.', 91, NOW() - INTERVAL '6 months', NOW() - INTERVAL '6 months' + INTERVAL '2 days', NOW() - INTERVAL '6 months' + INTERVAL '2 days'),
  ('f1000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000004', 'approved', 'Solid breast surgery focus.',                 78, NOW() - INTERVAL '5 months', NOW() - INTERVAL '5 months' + INTERVAL '4 days', NOW() - INTERVAL '5 months' + INTERVAL '4 days'),
  ('f1000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000005', 'approved', 'Good body contouring fellowship. Approved.',  82, NOW() - INTERVAL '4 months', NOW() - INTERVAL '4 months' + INTERVAL '3 days', NOW() - INTERVAL '4 months' + INTERVAL '3 days')
ON CONFLICT (id) DO NOTHING;

-- ─── Patients ─────────────────────────────────────────────────────────────────

INSERT INTO users (id, email, password_hash, role, is_active, is_email_verified)
VALUES
  ('c1000000-0000-0000-0000-000000000001', 'sophie.williams@email.com',  crypt('Patient123!', gen_salt('bf', 12)), 'patient', TRUE, TRUE),
  ('c1000000-0000-0000-0000-000000000002', 'marcus.johnson@email.com',   crypt('Patient123!', gen_salt('bf', 12)), 'patient', TRUE, TRUE),
  ('c1000000-0000-0000-0000-000000000003', 'priya.patel@email.com',      crypt('Patient123!', gen_salt('bf', 12)), 'patient', TRUE, TRUE),
  ('c1000000-0000-0000-0000-000000000004', 'charlotte.davies@email.com', crypt('Patient123!', gen_salt('bf', 12)), 'patient', TRUE, TRUE),
  ('c1000000-0000-0000-0000-000000000005', 'oliver.brown@email.com',     crypt('Patient123!', gen_salt('bf', 12)), 'patient', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO patient_profiles (id, user_id, first_name, last_name, onboarding_complete, procedure_interests)
VALUES
  ('d1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001', 'Sophie',     'Williams', TRUE, ARRAY['Rhinoplasty']),
  ('d1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000002', 'Marcus',     'Johnson',  TRUE, ARRAY['Liposuction','Tummy Tuck']),
  ('d1000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000003', 'Priya',      'Patel',    TRUE, ARRAY['Breast Augmentation']),
  ('d1000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000004', 'Charlotte',  'Davies',   TRUE, ARRAY['Blepharoplasty','Brow Lift']),
  ('d1000000-0000-0000-0000-000000000005', 'c1000000-0000-0000-0000-000000000005', 'Oliver',     'Brown',    FALSE, ARRAY[]::TEXT[])
ON CONFLICT (id) DO NOTHING;
