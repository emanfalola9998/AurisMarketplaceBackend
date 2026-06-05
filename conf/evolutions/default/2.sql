-- ─── conf/evolutions/default/2.sql ──────────────────────────────────────────
-- Auris — development seed data
--
-- 5 surgeons (Elite / Gold / Essential), 5 patients (fully onboarded)
-- Surgeon applications, weekly availability, bookings, reviews, saved surgeons
--
-- All seed account password: Seed1234!
-- Hashed via pgcrypto crypt() — compatible with jbcrypt $2a$ format.

-- !Ups

-- ─── Surgeon users ────────────────────────────────────────────────────────────

INSERT INTO users (id, email, password_hash, role, is_active, is_email_verified) VALUES
  ('a1000000-0000-0000-0000-000000000001', 'james.harrison@auris.co',
   crypt('Seed1234!', gen_salt('bf', 12)), 'surgeon', TRUE, TRUE),

  ('a1000000-0000-0000-0000-000000000002', 'sarah.mitchell@auris.co',
   crypt('Seed1234!', gen_salt('bf', 12)), 'surgeon', TRUE, TRUE),

  ('a1000000-0000-0000-0000-000000000003', 'robert.chen@auris.co',
   crypt('Seed1234!', gen_salt('bf', 12)), 'surgeon', TRUE, TRUE),

  ('a1000000-0000-0000-0000-000000000004', 'emily.thompson@auris.co',
   crypt('Seed1234!', gen_salt('bf', 12)), 'surgeon', TRUE, TRUE),

  ('a1000000-0000-0000-0000-000000000005', 'david.okafor@auris.co',
   crypt('Seed1234!', gen_salt('bf', 12)), 'surgeon', TRUE, TRUE);

-- ─── Patient users ────────────────────────────────────────────────────────────

INSERT INTO users (id, email, password_hash, role, is_active, is_email_verified) VALUES
  ('c1000000-0000-0000-0000-000000000001', 'sophie.williams@email.com',
   crypt('Seed1234!', gen_salt('bf', 12)), 'patient', TRUE, TRUE),

  ('c1000000-0000-0000-0000-000000000002', 'marcus.johnson@email.com',
   crypt('Seed1234!', gen_salt('bf', 12)), 'patient', TRUE, TRUE),

  ('c1000000-0000-0000-0000-000000000003', 'priya.patel@email.com',
   crypt('Seed1234!', gen_salt('bf', 12)), 'patient', TRUE, TRUE),

  ('c1000000-0000-0000-0000-000000000004', 'charlotte.davies@email.com',
   crypt('Seed1234!', gen_salt('bf', 12)), 'patient', TRUE, TRUE),

  ('c1000000-0000-0000-0000-000000000005', 'oliver.brown@email.com',
   crypt('Seed1234!', gen_salt('bf', 12)), 'patient', TRUE, TRUE);

-- ─── Surgeon profiles ─────────────────────────────────────────────────────────

INSERT INTO surgeon_profiles (
  id, user_id,
  title, first_name, last_name, gmc_number,
  qualifications, medical_school, graduation_year, fellowships,
  specialty, subspecialties,
  hospital, city, address,
  years_experience, languages, bio, procedures,
  consult_fee_clinic, consult_fee_virtual, offers_virtual,
  tier, profile_complete, profile_live,
  rating, review_count, consultation_count
) VALUES

  -- ── 1. Mr James Harrison — Elite, London ─────────────────────────────────
  (
    'b1000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000001',
    'Mr.', 'James', 'Harrison', '7654321',
    ARRAY['FRCS(Plast)', 'MBBS', 'MD'],
    'King''s College London', 2002,
    'Fellowship in Craniofacial Surgery, Great Ormond Street Hospital',
    'Plastic & Reconstructive Surgery',
    ARRAY['Rhinoplasty', 'Facelift', 'Breast Surgery'],
    'King''s College Hospital', 'London', 'Denmark Hill, London SE5 9RS',
    18, ARRAY['English', 'French'],
    'Mr James Harrison is a Consultant Plastic and Reconstructive Surgeon at King''s College '
    'Hospital with over 18 years of experience. He specialises in aesthetic facial and body '
    'procedures, with a particular interest in rhinoplasty and facial rejuvenation. Mr Harrison '
    'trained at King''s College London and completed a prestigious fellowship in craniofacial '
    'surgery at Great Ormond Street Hospital.',
    ARRAY['Rhinoplasty', 'Revision Rhinoplasty', 'Facelift', 'Neck Lift', 'Blepharoplasty',
          'Breast Augmentation', 'Breast Reduction', 'Liposuction', 'Tummy Tuck', 'Ear Reshaping'],
    350.00, 200.00, TRUE,
    'elite', TRUE, TRUE,
    4.90, 47, 65
  ),

  -- ── 2. Mr Sarah Mitchell — Gold, Manchester ──────────────────────────────
  (
    'b1000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000002',
    'Mr.', 'Sarah', 'Mitchell', '6543210',
    ARRAY['FRCS(ORL-HNS)', 'MBChB', 'DOHNS'],
    'University of Manchester', 2008,
    'Advanced Rhinoplasty Fellowship, Amsterdam',
    'Rhinoplasty & Facial Aesthetics',
    ARRAY['Rhinoplasty', 'Revision Rhinoplasty', 'Septoplasty'],
    'Spire Manchester Hospital', 'Manchester', 'The Drive, Didsbury, Manchester M20 2LN',
    12, ARRAY['English'],
    'Mr Sarah Mitchell is a specialist rhinoplasty and facial aesthetics surgeon based in '
    'Manchester. With 12 years of dedicated experience she has performed over 1,000 rhinoplasty '
    'procedures and is known for achieving natural, harmonious results. Mr Mitchell completed an '
    'advanced rhinoplasty fellowship in Amsterdam, one of the leading centres for nasal surgery '
    'in Europe.',
    ARRAY['Rhinoplasty', 'Revision Rhinoplasty', 'Septoplasty', 'Septorhinoplasty',
          'Ear Reshaping', 'Chin Augmentation', 'Lip Augmentation'],
    280.00, 150.00, TRUE,
    'gold', TRUE, TRUE,
    4.70, 31, 42
  ),

  -- ── 3. Mr Robert Chen — Gold, London ─────────────────────────────────────
  (
    'b1000000-0000-0000-0000-000000000003',
    'a1000000-0000-0000-0000-000000000003',
    'Mr.', 'Robert', 'Chen', '5432109',
    ARRAY['FRCOphth', 'MBBChir', 'MD(Cantab)'],
    'University of Cambridge', 2005,
    'Oculoplastic Fellowship, Moorfields Eye Hospital',
    'Oculoplastic & Facial Surgery',
    ARRAY['Blepharoplasty', 'Brow Lift', 'Facial Rejuvenation'],
    'The London Clinic', 'London', '20 Devonshire Place, London W1G 6BW',
    15, ARRAY['English', 'Mandarin'],
    'Mr Robert Chen is an Oculoplastic and Facial Surgeon at The London Clinic on Harley Street. '
    'He trained at Cambridge University and completed his oculoplastic fellowship at Moorfields '
    'Eye Hospital. Mr Chen specialises in eyelid surgery, brow lifting, and facial rejuvenation, '
    'combining surgical precision with a meticulous eye for natural, rested results.',
    ARRAY['Upper Blepharoplasty', 'Lower Blepharoplasty', 'Brow Lift', 'Ptosis Repair',
          'Facelift', 'Neck Lift', 'Fat Transfer'],
    320.00, 180.00, TRUE,
    'gold', TRUE, TRUE,
    4.80, 28, 38
  ),

  -- ── 4. Mr Emily Thompson — Essential, Birmingham ─────────────────────────
  (
    'b1000000-0000-0000-0000-000000000004',
    'a1000000-0000-0000-0000-000000000004',
    'Mr.', 'Emily', 'Thompson', '4321098',
    ARRAY['FRCS(Plast)', 'MBChB'],
    'University of Birmingham', 2012,
    NULL,
    'Breast Surgery',
    ARRAY['Breast Augmentation', 'Breast Reduction', 'Breast Lift'],
    'BMI The Priory Hospital', 'Birmingham', 'Priory Road, Birmingham B5 7UG',
    8, ARRAY['English'],
    'Mr Emily Thompson is a Consultant Plastic Surgeon specialising in breast surgery at BMI '
    'The Priory Hospital in Birmingham. With 8 years of experience she is passionate about '
    'delivering safe, high-quality breast surgery tailored to each patient''s goals. '
    'Mr Thompson takes a thorough, patient-centred approach to every consultation.',
    ARRAY['Breast Augmentation', 'Breast Reduction', 'Breast Lift', 'Breast Implant Removal',
          'Breast Implant Exchange', 'Nipple Correction', 'Male Chest Reduction'],
    250.00, 130.00, TRUE,
    'essential', TRUE, TRUE,
    4.50, 19, 27
  ),

  -- ── 5. Mr David Okafor — Essential, Glasgow ──────────────────────────────
  (
    'b1000000-0000-0000-0000-000000000005',
    'a1000000-0000-0000-0000-000000000005',
    'Mr.', 'David', 'Okafor', '3210987',
    ARRAY['FRCS(Plast)', 'MBBS', 'MSc'],
    'University of Edinburgh', 2010,
    'Body Contouring Fellowship, São Paulo',
    'Body Contouring & Reconstructive Surgery',
    ARRAY['Liposuction', 'Tummy Tuck', 'Body Lift'],
    'Ross Hall Hospital', 'Glasgow', 'Belfast Road, Glasgow G52 2UH',
    10, ARRAY['English', 'Yoruba'],
    'Mr David Okafor is a Consultant Plastic Surgeon specialising in body contouring procedures '
    'at Ross Hall Hospital in Glasgow. He trained at the University of Edinburgh and completed a '
    'body contouring fellowship in São Paulo, Brazil. Mr Okafor is known for his sculpting '
    'approach to liposuction and his expertise in post-weight-loss body procedures.',
    ARRAY['Liposuction', 'Tummy Tuck', 'Extended Tummy Tuck', 'Body Lift', 'Arm Lift',
          'Thigh Lift', 'Brazilian Butt Lift', 'Mummy Makeover'],
    220.00, 120.00, TRUE,
    'essential', TRUE, TRUE,
    4.60, 22, 30
  );

-- ─── Surgeon applications ─────────────────────────────────────────────────────
-- All approved — these surgeons are live on the platform.

INSERT INTO surgeon_applications (
  id, surgeon_id, status, reviewer_notes, score,
  submitted_at, reviewed_at, approved_at
) VALUES
  ('f1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001',
   'approved', 'Excellent credentials. Elite tier approved.', 98,
   NOW() - INTERVAL '6 months', NOW() - INTERVAL '6 months' + INTERVAL '2 days',
   NOW() - INTERVAL '6 months' + INTERVAL '2 days'),

  ('f1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000002',
   'approved', 'Strong rhinoplasty specialism. Gold tier.', 87,
   NOW() - INTERVAL '5 months', NOW() - INTERVAL '5 months' + INTERVAL '3 days',
   NOW() - INTERVAL '5 months' + INTERVAL '3 days'),

  ('f1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000003',
   'approved', 'Impressive oculoplastic background. Gold tier.', 91,
   NOW() - INTERVAL '4 months', NOW() - INTERVAL '4 months' + INTERVAL '2 days',
   NOW() - INTERVAL '4 months' + INTERVAL '2 days'),

  ('f1000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000004',
   'approved', 'Solid breast surgery focus.', 78,
   NOW() - INTERVAL '3 months', NOW() - INTERVAL '3 months' + INTERVAL '4 days',
   NOW() - INTERVAL '3 months' + INTERVAL '4 days'),

  ('f1000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000005',
   'approved', 'Good body contouring fellowship. Approved.', 82,
   NOW() - INTERVAL '2 months', NOW() - INTERVAL '2 months' + INTERVAL '3 days',
   NOW() - INTERVAL '2 months' + INTERVAL '3 days');

-- ─── Surgeon availability — Mon–Fri for all five ──────────────────────────────

INSERT INTO surgeon_availability (id, surgeon_id, day_of_week, start_time, end_time, buffer_minutes, is_active)
SELECT
  uuid_generate_v4(),
  surgeon_id,
  day_of_week,
  start_time::TIME,
  end_time::TIME,
  30,
  TRUE
FROM (VALUES
  -- Harrison: Mon/Wed/Fri 08:00–16:00, Tue/Thu 09:00–17:00
  ('b1000000-0000-0000-0000-000000000001'::uuid, 1, '08:00', '16:00'),
  ('b1000000-0000-0000-0000-000000000001'::uuid, 2, '09:00', '17:00'),
  ('b1000000-0000-0000-0000-000000000001'::uuid, 3, '08:00', '16:00'),
  ('b1000000-0000-0000-0000-000000000001'::uuid, 4, '09:00', '17:00'),
  ('b1000000-0000-0000-0000-000000000001'::uuid, 5, '08:00', '14:00'),

  -- Mitchell: Mon–Thu 09:00–17:00
  ('b1000000-0000-0000-0000-000000000002'::uuid, 1, '09:00', '17:00'),
  ('b1000000-0000-0000-0000-000000000002'::uuid, 2, '09:00', '17:00'),
  ('b1000000-0000-0000-0000-000000000002'::uuid, 3, '09:00', '17:00'),
  ('b1000000-0000-0000-0000-000000000002'::uuid, 4, '09:00', '17:00'),

  -- Chen: Tue/Wed/Fri 08:30–16:30
  ('b1000000-0000-0000-0000-000000000003'::uuid, 2, '08:30', '16:30'),
  ('b1000000-0000-0000-0000-000000000003'::uuid, 3, '08:30', '16:30'),
  ('b1000000-0000-0000-0000-000000000003'::uuid, 5, '08:30', '16:30'),

  -- Thompson: Mon/Tue/Thu 09:00–17:00
  ('b1000000-0000-0000-0000-000000000004'::uuid, 1, '09:00', '17:00'),
  ('b1000000-0000-0000-0000-000000000004'::uuid, 2, '09:00', '17:00'),
  ('b1000000-0000-0000-0000-000000000004'::uuid, 4, '09:00', '17:00'),

  -- Okafor: Mon–Fri 10:00–18:00
  ('b1000000-0000-0000-0000-000000000005'::uuid, 1, '10:00', '18:00'),
  ('b1000000-0000-0000-0000-000000000005'::uuid, 2, '10:00', '18:00'),
  ('b1000000-0000-0000-0000-000000000005'::uuid, 3, '10:00', '18:00'),
  ('b1000000-0000-0000-0000-000000000005'::uuid, 4, '10:00', '18:00'),
  ('b1000000-0000-0000-0000-000000000005'::uuid, 5, '10:00', '18:00')
) AS t(surgeon_id, day_of_week, start_time, end_time);

-- ─── Patient profiles ─────────────────────────────────────────────────────────

INSERT INTO patient_profiles (
  id, user_id,
  first_name, last_name, date_of_birth, phone,
  onboarding_complete, procedure_interests,
  location_preference, consult_preference, budget_range, timeline
) VALUES
  (
    'd1000000-0000-0000-0000-000000000001',
    'c1000000-0000-0000-0000-000000000001',
    'Sophie', 'Williams', '1995-03-14', '+44 7700 900001',
    TRUE, ARRAY['Rhinoplasty', 'Ear Reshaping'],
    'London', 'in_clinic', '£2,000–£5,000', '3–6 months'
  ),
  (
    'd1000000-0000-0000-0000-000000000002',
    'c1000000-0000-0000-0000-000000000002',
    'Marcus', 'Johnson', '1988-11-22', '+44 7700 900002',
    TRUE, ARRAY['Blepharoplasty', 'Facelift'],
    'Manchester', 'virtual', '£5,000–£10,000', '6–12 months'
  ),
  (
    'd1000000-0000-0000-0000-000000000003',
    'c1000000-0000-0000-0000-000000000003',
    'Priya', 'Patel', '1991-07-08', '+44 7700 900003',
    TRUE, ARRAY['Breast Augmentation', 'Breast Lift'],
    'Birmingham', 'in_clinic', '£3,000–£7,000', '1–3 months'
  ),
  (
    'd1000000-0000-0000-0000-000000000004',
    'c1000000-0000-0000-0000-000000000004',
    'Charlotte', 'Davies', '1984-05-30', '+44 7700 900004',
    TRUE, ARRAY['Liposuction', 'Tummy Tuck', 'Mummy Makeover'],
    'London', 'in_clinic', '£5,000–£10,000', '3–6 months'
  ),
  (
    'd1000000-0000-0000-0000-000000000005',
    'c1000000-0000-0000-0000-000000000005',
    'Oliver', 'Brown', '1979-09-17', '+44 7700 900005',
    TRUE, ARRAY['Rhinoplasty', 'Facelift'],
    'Glasgow', 'in_clinic', 'Over £10,000', '6–12 months'
  );

-- ─── Saved surgeons ───────────────────────────────────────────────────────────

INSERT INTO saved_surgeons (patient_id, surgeon_id, saved_at) VALUES
  -- Sophie saved Harrison and Mitchell
  ('d1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', NOW() - INTERVAL '10 days'),
  ('d1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000002', NOW() - INTERVAL '8 days'),
  -- Marcus saved Chen
  ('d1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000003', NOW() - INTERVAL '14 days'),
  -- Charlotte saved Harrison and Okafor
  ('d1000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000001', NOW() - INTERVAL '5 days'),
  ('d1000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000005', NOW() - INTERVAL '3 days'),
  -- Oliver saved Harrison
  ('d1000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000001', NOW() - INTERVAL '7 days');

-- ─── Bookings ─────────────────────────────────────────────────────────────────

INSERT INTO bookings (
  id, patient_id, surgeon_id,
  consultation_type, scheduled_at, duration_minutes,
  status, fee, paid_at
) VALUES
  -- Sophie × Harrison — completed in_clinic consultation (2 months ago)
  (
    'e1000000-0000-0000-0000-000000000001',
    'd1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000001',
    'in_clinic', NOW() - INTERVAL '2 months', 60,
    'completed', 350.00, NOW() - INTERVAL '2 months' - INTERVAL '1 day'
  ),
  -- Marcus × Chen — completed virtual consultation (6 weeks ago)
  (
    'e1000000-0000-0000-0000-000000000002',
    'd1000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000003',
    'virtual', NOW() - INTERVAL '6 weeks', 45,
    'completed', 180.00, NOW() - INTERVAL '6 weeks' - INTERVAL '1 day'
  ),
  -- Priya × Thompson — confirmed, upcoming in_clinic (3 weeks from now)
  (
    'e1000000-0000-0000-0000-000000000003',
    'd1000000-0000-0000-0000-000000000003',
    'b1000000-0000-0000-0000-000000000004',
    'in_clinic', NOW() + INTERVAL '3 weeks', 60,
    'confirmed', 250.00, NOW() - INTERVAL '2 days'
  ),
  -- Charlotte × Okafor — pending, 6 weeks from now
  (
    'e1000000-0000-0000-0000-000000000004',
    'd1000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000005',
    'in_clinic', NOW() + INTERVAL '6 weeks', 60,
    'pending', 220.00, NULL
  );

-- ─── Reviews ─────────────────────────────────────────────────────────────────
-- Only for completed bookings (published so they appear on surgeon profiles).

INSERT INTO reviews (
  id, booking_id, patient_id, surgeon_id,
  rating, rating_results, rating_communication, rating_aftercare, rating_value,
  procedure, body,
  is_verified, is_published, published_at
) VALUES
  -- Sophie's review of Harrison
  (
    '71000000-0000-0000-0000-000000000001',
    'e1000000-0000-0000-0000-000000000001',
    'd1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000001',
    5, 5, 5, 5, 5,
    'Rhinoplasty',
    'I cannot recommend Mr Harrison highly enough. From the initial consultation he took the time '
    'to understand exactly what I was looking for and explained the procedure in detail. The result '
    'is absolutely natural — friends keep telling me I look well-rested without being able to '
    'pinpoint why. His team at King''s were brilliant too. Worth every penny.',
    TRUE, TRUE, NOW() - INTERVAL '7 weeks'
  ),
  -- Marcus's review of Chen
  (
    '71000000-0000-0000-0000-000000000002',
    'e1000000-0000-0000-0000-000000000002',
    'd1000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000003',
    5, 5, 5, 4, 5,
    'Upper Blepharoplasty',
    'Mr Chen is a true specialist. I had an upper blepharoplasty consultation and he gave me an '
    'honest, thorough assessment. He explained all my options clearly and never pushed me towards '
    'anything. The virtual format worked really well — crisp video, no technical issues, and he '
    'was fully prepared with my photos in advance. Booked my surgery straight after.',
    TRUE, TRUE, NOW() - INTERVAL '5 weeks'
  );

-- !Downs

DELETE FROM reviews       WHERE id IN (
  '71000000-0000-0000-0000-000000000001',
  '71000000-0000-0000-0000-000000000002'
);
DELETE FROM bookings      WHERE id IN (
  'e1000000-0000-0000-0000-000000000001',
  'e1000000-0000-0000-0000-000000000002',
  'e1000000-0000-0000-0000-000000000003',
  'e1000000-0000-0000-0000-000000000004'
);
DELETE FROM saved_surgeons
  WHERE patient_id IN (
    'd1000000-0000-0000-0000-000000000001',
    'd1000000-0000-0000-0000-000000000002',
    'd1000000-0000-0000-0000-000000000004',
    'd1000000-0000-0000-0000-000000000005'
  );
DELETE FROM patient_profiles WHERE id IN (
  'd1000000-0000-0000-0000-000000000001',
  'd1000000-0000-0000-0000-000000000002',
  'd1000000-0000-0000-0000-000000000003',
  'd1000000-0000-0000-0000-000000000004',
  'd1000000-0000-0000-0000-000000000005'
);
DELETE FROM surgeon_applications WHERE id IN (
  'f1000000-0000-0000-0000-000000000001',
  'f1000000-0000-0000-0000-000000000002',
  'f1000000-0000-0000-0000-000000000003',
  'f1000000-0000-0000-0000-000000000004',
  'f1000000-0000-0000-0000-000000000005'
);
DELETE FROM surgeon_availability
  WHERE surgeon_id IN (
    'b1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000003',
    'b1000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000005'
  );
DELETE FROM surgeon_profiles WHERE id IN (
  'b1000000-0000-0000-0000-000000000001',
  'b1000000-0000-0000-0000-000000000002',
  'b1000000-0000-0000-0000-000000000003',
  'b1000000-0000-0000-0000-000000000004',
  'b1000000-0000-0000-0000-000000000005'
);
DELETE FROM users WHERE id IN (
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
