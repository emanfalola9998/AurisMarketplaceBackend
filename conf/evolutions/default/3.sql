-- conf/evolutions/default/3.sql
-- Auris — extended seed data
--
-- Populates enquiries, messages, notifications, surgeon_portfolio,
-- surgeon_blocked_slots and audit_log plus additional bookings and reviews.
--
-- All IDs use valid UUID hex characters only (0-9, a-f).
-- No semicolons appear inside any -- comment line.

-- !Ups

-- ─── Additional bookings ──────────────────────────────────────────────────────

INSERT INTO bookings (
  id, patient_id, surgeon_id,
  consultation_type, scheduled_at, duration_minutes,
  status, fee, paid_at, cancelled_at, cancellation_note, notes
) VALUES
  (
    'e2000000-0000-0000-0000-000000000001',
    'd1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000002',
    'virtual', NOW() - INTERVAL '7 weeks', 45,
    'completed', 150.00, NOW() - INTERVAL '7 weeks' - INTERVAL '1 day',
    NULL, NULL,
    'Patient is an excellent candidate for closed rhinoplasty. Discussed expectations thoroughly. Recommend 3-month review before proceeding.'
  ),
  (
    'e2000000-0000-0000-0000-000000000002',
    'd1000000-0000-0000-0000-000000000003',
    'b1000000-0000-0000-0000-000000000001',
    'in_clinic', NOW() - INTERVAL '10 weeks', 60,
    'completed', 350.00, NOW() - INTERVAL '10 weeks' - INTERVAL '2 days',
    NULL, NULL,
    'Full assessment completed. Patient seeking augmentation and lift. Recommended 350cc implants with mastopexy. Surgery booked for next quarter.'
  ),
  (
    'e2000000-0000-0000-0000-000000000003',
    'd1000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000001',
    'in_clinic', NOW() - INTERVAL '8 weeks', 60,
    'completed', 350.00, NOW() - INTERVAL '8 weeks' - INTERVAL '1 day',
    NULL, NULL,
    'Mummy makeover consultation. Patient in excellent health with realistic expectations. Discussed staged approach — abdominoplasty first then liposuction flanks at 6 months.'
  ),
  (
    'e2000000-0000-0000-0000-000000000004',
    'd1000000-0000-0000-0000-000000000005',
    'b1000000-0000-0000-0000-000000000002',
    'in_clinic', NOW() - INTERVAL '5 weeks', 60,
    'completed', 280.00, NOW() - INTERVAL '5 weeks' - INTERVAL '2 days',
    NULL, NULL,
    'Rhinoplasty consultation. Complex dorsal hump reduction with tip refinement required. Patient well-informed. Scheduled for pre-op tests.'
  ),
  (
    'e2000000-0000-0000-0000-000000000005',
    'd1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000003',
    'virtual', NOW() - INTERVAL '4 weeks', 45,
    'completed', 180.00, NOW() - INTERVAL '4 weeks' - INTERVAL '1 day',
    NULL, NULL,
    'Upper blepharoplasty assessment. Mild ptosis noted. Recommended surgical correction. Patient to return for in-person pre-op.'
  ),
  (
    'e2000000-0000-0000-0000-000000000006',
    'd1000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000002',
    'in_clinic', NOW() - INTERVAL '3 weeks', 60,
    'completed', 280.00, NOW() - INTERVAL '3 weeks' - INTERVAL '2 days',
    NULL, NULL,
    'Revision rhinoplasty assessment. Previous surgery 8 years ago. Cartilage grafting likely required. Referred for imaging.'
  ),
  (
    'e2000000-0000-0000-0000-000000000007',
    'd1000000-0000-0000-0000-000000000005',
    'b1000000-0000-0000-0000-000000000001',
    'in_clinic', NOW() + INTERVAL '4 weeks', 60,
    'confirmed', 350.00, NOW() - INTERVAL '3 days',
    NULL, NULL, NULL
  ),
  (
    'e2000000-0000-0000-0000-000000000008',
    'd1000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000004',
    'in_clinic', NOW() + INTERVAL '5 weeks', 60,
    'confirmed', 250.00, NOW() - INTERVAL '1 day',
    NULL, NULL, NULL
  ),
  (
    'e2000000-0000-0000-0000-000000000009',
    'd1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000005',
    'in_clinic', NOW() + INTERVAL '8 weeks', 60,
    'pending', 220.00, NULL,
    NULL, NULL, NULL
  ),
  (
    'e2000000-0000-0000-0000-000000000010',
    'd1000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000001',
    'in_clinic', NOW() - INTERVAL '2 weeks', 60,
    'cancelled_by_patient', 350.00, NOW() - INTERVAL '4 weeks',
    NOW() - INTERVAL '3 weeks',
    'Patient had a family emergency and needed to reschedule.', NULL
  ),
  (
    'e2000000-0000-0000-0000-000000000011',
    'd1000000-0000-0000-0000-000000000003',
    'b1000000-0000-0000-0000-000000000002',
    'virtual', NOW() - INTERVAL '1 week', 45,
    'cancelled_by_surgeon', 150.00, NOW() - INTERVAL '2 weeks',
    NOW() - INTERVAL '10 days',
    'Surgeon unavailable due to hospital commitment. Patient offered priority rescheduling.', NULL
  ),
  (
    'e2000000-0000-0000-0000-000000000012',
    'd1000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000003',
    'in_clinic', NOW() + INTERVAL '10 weeks', 60,
    'pending', 320.00, NULL,
    NULL, NULL, NULL
  );

-- ─── Additional reviews ───────────────────────────────────────────────────────

INSERT INTO reviews (
  id, booking_id, patient_id, surgeon_id,
  rating, rating_results, rating_communication, rating_aftercare, rating_value,
  procedure, body,
  is_verified, is_published, published_at
) VALUES
  (
    '72000000-0000-0000-0000-000000000001',
    'e2000000-0000-0000-0000-000000000001',
    'd1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000002',
    5, 5, 5, 5, 5,
    'Rhinoplasty',
    'Mr Mitchell was fantastic from start to finish. Despite it being a virtual consultation I felt completely at ease. He asked exactly the right questions, showed me simulation images and was honest about what could realistically be achieved. I went away feeling fully informed and confident. Highly recommend.',
    TRUE, TRUE, NOW() - INTERVAL '6 weeks'
  ),
  (
    '72000000-0000-0000-0000-000000000002',
    'e2000000-0000-0000-0000-000000000002',
    'd1000000-0000-0000-0000-000000000003',
    'b1000000-0000-0000-0000-000000000001',
    5, 5, 5, 5, 4,
    'Breast Augmentation',
    'I came to Mr Harrison for a breast augmentation and lift consultation. He was thorough, professional and made me feel completely at ease. He gave me very clear advice about sizing, placement and recovery. The clinic at King''s is immaculate. I left with complete confidence in my decision to proceed.',
    TRUE, TRUE, NOW() - INTERVAL '9 weeks'
  ),
  (
    '72000000-0000-0000-0000-000000000003',
    'e2000000-0000-0000-0000-000000000003',
    'd1000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000001',
    4, 5, 4, 4, 4,
    'Tummy Tuck',
    'Mr Harrison was excellent. His knowledge of post-pregnancy body procedures is second to none. The consultation took over an hour and he never made me feel rushed. He recommended a staged approach which I appreciated — it showed he was putting my safety first rather than just doing everything at once.',
    TRUE, TRUE, NOW() - INTERVAL '7 weeks'
  ),
  (
    '72000000-0000-0000-0000-000000000004',
    'e2000000-0000-0000-0000-000000000004',
    'd1000000-0000-0000-0000-000000000005',
    'b1000000-0000-0000-0000-000000000002',
    5, 5, 5, 5, 4,
    'Rhinoplasty',
    'An exceptional consultation. Mr Mitchell has an incredible eye for facial aesthetics. He spent considerable time studying my face before making any recommendations and his explanation of the surgical plan was crystal clear. I came in unsure, I left completely confident. Cannot recommend highly enough.',
    TRUE, TRUE, NOW() - INTERVAL '4 weeks'
  ),
  (
    '72000000-0000-0000-0000-000000000005',
    'e2000000-0000-0000-0000-000000000005',
    'd1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000003',
    5, 5, 5, 5, 5,
    'Upper Blepharoplasty',
    'Mr Chen is extraordinary. His eye for detail is unmatched and he explained my options with such clarity. The virtual consultation was seamless and he had already reviewed my photos before we spoke. I felt heard and respected throughout. I am now booked for surgery and could not be more excited.',
    TRUE, TRUE, NOW() - INTERVAL '3 weeks'
  ),
  (
    '72000000-0000-0000-0000-000000000006',
    'e2000000-0000-0000-0000-000000000006',
    'd1000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000002',
    4, 4, 5, 4, 4,
    'Revision Rhinoplasty',
    'I went to Mr Mitchell for a revision rhinoplasty consultation — a complex situation following previous surgery elsewhere. He was honest that this would be a challenging case and didn''t oversell what could be achieved, which I respected enormously. His experience in revision cases is clearly extensive. Very reassuring.',
    TRUE, TRUE, NOW() - INTERVAL '2 weeks'
  );

-- ─── Enquiries ────────────────────────────────────────────────────────────────

INSERT INTO enquiries (
  id, patient_id, surgeon_id,
  procedure_interest, goals, previous_surgery, previous_details,
  preferred_date, preferred_time, consultation_type,
  status, fee, heard_about, surgeon_notes
) VALUES
  (
    '81000000-0000-0000-0000-000000000001',
    'd1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000001',
    'Rhinoplasty',
    'I would like to reduce the dorsal hump on my nose and refine the tip. I am looking for a natural result that suits my face.',
    FALSE, NULL,
    (CURRENT_DATE - INTERVAL '9 weeks')::DATE,
    '10:00:00', 'in_clinic',
    'completed', 350.00,
    'Auris platform search',
    'Patient is an excellent candidate. Clear goals. Booked for surgery.'
  ),
  (
    '81000000-0000-0000-0000-000000000002',
    'd1000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000003',
    'Upper Blepharoplasty',
    'My upper eyelids have become quite heavy and I often look tired. I want to look more alert and refreshed without looking operated on.',
    FALSE, NULL,
    (CURRENT_DATE - INTERVAL '8 weeks')::DATE,
    '14:00:00', 'virtual',
    'completed', 180.00,
    'Friend recommendation',
    'Clear excess skin. Good candidate. Surgery discussion to follow in-person.'
  ),
  (
    '81000000-0000-0000-0000-000000000003',
    'd1000000-0000-0000-0000-000000000003',
    'b1000000-0000-0000-0000-000000000004',
    'Breast Augmentation',
    'I have always wanted a fuller chest. I am petite and looking for a natural increase in size rather than anything dramatic.',
    FALSE, NULL,
    (CURRENT_DATE + INTERVAL '3 weeks')::DATE,
    '09:30:00', 'in_clinic',
    'confirmed', 250.00,
    'Instagram',
    NULL
  ),
  (
    '81000000-0000-0000-0000-000000000004',
    'd1000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000005',
    'Mummy Makeover',
    'After having two children I am looking to restore my pre-pregnancy figure. I am interested in a tummy tuck combined with a breast lift.',
    FALSE, NULL,
    (CURRENT_DATE + INTERVAL '6 weeks')::DATE,
    '11:00:00', 'in_clinic',
    'pending', 220.00,
    'Google search',
    NULL
  ),
  (
    '81000000-0000-0000-0000-000000000005',
    'd1000000-0000-0000-0000-000000000005',
    'b1000000-0000-0000-0000-000000000001',
    'Facelift',
    'I would like to address jowling and neck laxity. I want to look refreshed without it being obvious I have had work done.',
    FALSE, NULL,
    (CURRENT_DATE + INTERVAL '4 weeks')::DATE,
    '10:30:00', 'in_clinic',
    'pending', 350.00,
    'Auris platform search',
    NULL
  ),
  (
    '81000000-0000-0000-0000-000000000006',
    'd1000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000001',
    'Facelift',
    'Interested in facelift to address volume loss and skin laxity following significant weight loss.',
    FALSE, NULL,
    (CURRENT_DATE - INTERVAL '3 weeks')::DATE,
    '15:00:00', 'virtual',
    'declined', 350.00,
    'Auris platform search',
    'Not suitable for virtual consultation at this stage. Invited to rebook in-person.'
  ),
  (
    '81000000-0000-0000-0000-000000000007',
    'd1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000005',
    'Liposuction',
    'I have stubborn areas of fat on my abdomen and flanks that are not responding to diet and exercise. I am interested in liposuction.',
    FALSE, NULL,
    (CURRENT_DATE + INTERVAL '8 weeks')::DATE,
    '10:00:00', 'in_clinic',
    'pending', 220.00,
    'Auris platform search',
    NULL
  ),
  (
    '81000000-0000-0000-0000-000000000008',
    'd1000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000003',
    'Blepharoplasty',
    'I am interested in both upper and lower eyelid surgery. My eyes look tired and I would like to look more youthful.',
    FALSE, NULL,
    (CURRENT_DATE + INTERVAL '10 weeks')::DATE,
    '09:00:00', 'in_clinic',
    'pending', 320.00,
    'Colleague recommendation',
    NULL
  ),
  (
    '81000000-0000-0000-0000-000000000009',
    'd1000000-0000-0000-0000-000000000005',
    'b1000000-0000-0000-0000-000000000002',
    'Rhinoplasty',
    'I have a prominent dorsal hump and feel self-conscious about my profile. I am looking for a subtle, natural correction.',
    FALSE, NULL,
    (CURRENT_DATE - INTERVAL '7 weeks')::DATE,
    '11:00:00', 'in_clinic',
    'confirmed', 280.00,
    'Online research',
    'Good candidate for open rhinoplasty. Discussed realistic outcomes.'
  ),
  (
    '81000000-0000-0000-0000-000000000010',
    'd1000000-0000-0000-0000-000000000003',
    'b1000000-0000-0000-0000-000000000001',
    'Breast Lift',
    'After breastfeeding I have significant ptosis and would like to restore the shape of my breasts.',
    FALSE, NULL,
    (CURRENT_DATE - INTERVAL '5 weeks')::DATE,
    '14:00:00', 'in_clinic',
    'cancelled', 350.00,
    'Auris platform search',
    NULL
  );

-- ─── Messages ────────────────────────────────────────────────────────────────

INSERT INTO messages (id, sender_id, recipient_id, body, is_read, read_at, created_at) VALUES
  (
    '91000000-0000-0000-0000-000000000001',
    'c1000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000001',
    'Hello Mr Harrison, I just wanted to say thank you for such a thorough consultation last week. I have decided I would like to go ahead with the rhinoplasty.',
    TRUE, NOW() - INTERVAL '60 days' + INTERVAL '1 hour',
    NOW() - INTERVAL '60 days'
  ),
  (
    '91000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000001',
    'c1000000-0000-0000-0000-000000000001',
    'Dear Sophie, that is wonderful news. My coordinator Claire will be in touch within 48 hours to arrange your pre-operative assessment and to confirm a surgery date. Please do not hesitate to reach out if you have any further questions in the meantime.',
    TRUE, NOW() - INTERVAL '59 days' + INTERVAL '2 hours',
    NOW() - INTERVAL '59 days'
  ),
  (
    '91000000-0000-0000-0000-000000000003',
    'c1000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000001',
    'Thank you so much. I did have one more question — should I stop taking my evening primrose oil supplements before surgery?',
    TRUE, NOW() - INTERVAL '58 days' + INTERVAL '1 hour',
    NOW() - INTERVAL '58 days'
  ),
  (
    '91000000-0000-0000-0000-000000000004',
    'c1000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000003',
    'Mr Chen, I found our virtual consultation extremely helpful. You mentioned something about a ptosis repair — could you send me some more information on that procedure?',
    TRUE, NOW() - INTERVAL '40 days' + INTERVAL '1 hour',
    NOW() - INTERVAL '40 days'
  ),
  (
    '91000000-0000-0000-0000-000000000005',
    'a1000000-0000-0000-0000-000000000003',
    'c1000000-0000-0000-0000-000000000002',
    'Of course Marcus. Ptosis repair corrects drooping of the upper eyelid by tightening the levator muscle. It is often combined with blepharoplasty for the most natural result. I will have my team send over a detailed patient information leaflet by email.',
    TRUE, NOW() - INTERVAL '39 days' + INTERVAL '3 hours',
    NOW() - INTERVAL '39 days'
  ),
  (
    '91000000-0000-0000-0000-000000000006',
    'c1000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000003',
    'That is very helpful, thank you. I received the leaflet — I would like to book an in-person appointment to discuss further.',
    FALSE, NULL,
    NOW() - INTERVAL '35 days'
  ),
  (
    '91000000-0000-0000-0000-000000000007',
    'c1000000-0000-0000-0000-000000000004',
    'a1000000-0000-0000-0000-000000000005',
    'Hi Mr Okafor, I have a few questions before my upcoming consultation. Is it normal to feel nervous? And should I bring any medical records with me?',
    TRUE, NOW() - INTERVAL '30 days' + INTERVAL '2 hours',
    NOW() - INTERVAL '30 days'
  ),
  (
    '91000000-0000-0000-0000-000000000008',
    'a1000000-0000-0000-0000-000000000005',
    'c1000000-0000-0000-0000-000000000004',
    'Charlotte, feeling nervous is completely normal — most of my patients feel that way. Please do bring any relevant medical history including medications and previous surgeries. Also bring photographs of results you are hoping to achieve, as these are always helpful in guiding our discussion.',
    TRUE, NOW() - INTERVAL '29 days' + INTERVAL '1 hour',
    NOW() - INTERVAL '29 days'
  ),
  (
    '91000000-0000-0000-0000-000000000009',
    'c1000000-0000-0000-0000-000000000004',
    'a1000000-0000-0000-0000-000000000005',
    'That is so reassuring, thank you. I will bring everything along. See you at the consultation.',
    TRUE, NOW() - INTERVAL '28 days' + INTERVAL '30 minutes',
    NOW() - INTERVAL '28 days'
  ),
  (
    '91000000-0000-0000-0000-000000000010',
    'c1000000-0000-0000-0000-000000000005',
    'a1000000-0000-0000-0000-000000000002',
    'Mr Mitchell, I had my consultation with you last week regarding rhinoplasty. I wanted to ask about recovery — how long would I need to take off work?',
    TRUE, NOW() - INTERVAL '32 days' + INTERVAL '2 hours',
    NOW() - INTERVAL '32 days'
  ),
  (
    '91000000-0000-0000-0000-000000000011',
    'a1000000-0000-0000-0000-000000000002',
    'c1000000-0000-0000-0000-000000000005',
    'Oliver, for rhinoplasty most patients take two weeks off work, though if your work involves heavy lifting or physical activity you should allow four weeks. The cast comes off at seven days and most social swelling resolves within three to four weeks.',
    TRUE, NOW() - INTERVAL '31 days' + INTERVAL '4 hours',
    NOW() - INTERVAL '31 days'
  ),
  (
    '91000000-0000-0000-0000-000000000012',
    'c1000000-0000-0000-0000-000000000005',
    'a1000000-0000-0000-0000-000000000002',
    'Perfect — I have a desk job so two weeks should be fine. I would like to confirm my surgery date. Please let me know what is available.',
    FALSE, NULL,
    NOW() - INTERVAL '25 days'
  ),
  (
    '91000000-0000-0000-0000-000000000013',
    'c1000000-0000-0000-0000-000000000003',
    'a1000000-0000-0000-0000-000000000004',
    'Mr Thompson, I am looking forward to my upcoming consultation. I have been doing some research — what is the difference between an anatomical implant and a round implant?',
    TRUE, NOW() - INTERVAL '20 days' + INTERVAL '1 hour',
    NOW() - INTERVAL '20 days'
  ),
  (
    '91000000-0000-0000-0000-000000000014',
    'a1000000-0000-0000-0000-000000000004',
    'c1000000-0000-0000-0000-000000000003',
    'Great question Priya. Round implants give fullness throughout the breast particularly in the upper pole, while anatomical implants mimic the natural teardrop shape with more projection in the lower pole. The best choice depends on your starting anatomy and desired result — we will discuss this in detail at your consultation.',
    TRUE, NOW() - INTERVAL '19 days' + INTERVAL '2 hours',
    NOW() - INTERVAL '19 days'
  ),
  (
    '91000000-0000-0000-0000-000000000015',
    'c1000000-0000-0000-0000-000000000003',
    'a1000000-0000-0000-0000-000000000004',
    'Thank you — that is really helpful. I am quite petite so I am inclined towards the anatomical option for a more natural look. I will bring photos to the consultation.',
    FALSE, NULL,
    NOW() - INTERVAL '15 days'
  ),
  (
    '91000000-0000-0000-0000-000000000016',
    'c1000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000002',
    'Hello Mr Mitchell, I had a virtual consultation with you a few weeks ago regarding rhinoplasty. I have since had an in-person consultation with Mr Harrison and I am weighing up my options.',
    TRUE, NOW() - INTERVAL '42 days' + INTERVAL '3 hours',
    NOW() - INTERVAL '42 days'
  ),
  (
    '91000000-0000-0000-0000-000000000017',
    'a1000000-0000-0000-0000-000000000002',
    'c1000000-0000-0000-0000-000000000001',
    'Sophie, I completely understand. It is important you feel confident and at ease with your surgeon. If you have any further questions about the approach I outlined, please do not hesitate to get in touch.',
    TRUE, NOW() - INTERVAL '41 days' + INTERVAL '1 hour',
    NOW() - INTERVAL '41 days'
  ),
  (
    '91000000-0000-0000-0000-000000000018',
    'c1000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000001',
    'Mr Harrison, I had to cancel my consultation last minute due to a family emergency. I would very much like to rebook — do you have availability in the next few weeks?',
    TRUE, NOW() - INTERVAL '18 days' + INTERVAL '2 hours',
    NOW() - INTERVAL '18 days'
  ),
  (
    '91000000-0000-0000-0000-000000000019',
    'a1000000-0000-0000-0000-000000000001',
    'c1000000-0000-0000-0000-000000000002',
    'Marcus, I am sorry to hear about your family emergency. Please contact my coordinator Claire and she will be happy to rebook you at your convenience. We have availability in the next two to four weeks.',
    FALSE, NULL,
    NOW() - INTERVAL '17 days'
  ),
  (
    '91000000-0000-0000-0000-000000000020',
    'c1000000-0000-0000-0000-000000000003',
    'a1000000-0000-0000-0000-000000000001',
    'Mr Harrison, following our wonderful consultation I have decided to proceed with the augmentation and mastopexy. Please could you advise on the next steps?',
    FALSE, NULL,
    NOW() - INTERVAL '5 days'
  );

-- ─── Notifications ───────────────────────────────────────────────────────────

INSERT INTO notifications (id, user_id, type, title, body, link, is_read, read_at, created_at) VALUES
  (
    '92000000-0000-0000-0000-000000000001',
    'c1000000-0000-0000-0000-000000000003',
    'booking_confirmed',
    'Consultation Confirmed',
    'Your consultation with Mr Emily Thompson has been confirmed. We look forward to seeing you.',
    '/bookings/e1000000-0000-0000-0000-000000000003',
    TRUE, NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '2 days'
  ),
  (
    '92000000-0000-0000-0000-000000000002',
    'c1000000-0000-0000-0000-000000000004',
    'booking_confirmed',
    'Consultation Confirmed',
    'Your consultation with Mr James Harrison has been confirmed. We look forward to seeing you.',
    '/bookings/e1000000-0000-0000-0000-000000000004',
    FALSE, NULL,
    NOW() - INTERVAL '3 days'
  ),
  (
    '92000000-0000-0000-0000-000000000003',
    'c1000000-0000-0000-0000-000000000005',
    'booking_confirmed',
    'Consultation Confirmed',
    'Your upcoming consultation with Mr James Harrison has been confirmed. We look forward to seeing you.',
    '/bookings/e2000000-0000-0000-0000-000000000007',
    FALSE, NULL,
    NOW() - INTERVAL '3 days'
  ),
  (
    '92000000-0000-0000-0000-000000000004',
    'c1000000-0000-0000-0000-000000000004',
    'booking_confirmed',
    'Consultation Confirmed',
    'Your consultation with Mr Emily Thompson has been confirmed.',
    '/bookings/e2000000-0000-0000-0000-000000000008',
    FALSE, NULL,
    NOW() - INTERVAL '1 day'
  ),
  (
    '92000000-0000-0000-0000-000000000005',
    'c1000000-0000-0000-0000-000000000002',
    'booking_cancelled',
    'Consultation Cancelled',
    'Your consultation with Mr James Harrison has been cancelled as requested. You can rebook at any time.',
    '/surgeons/b1000000-0000-0000-0000-000000000001',
    TRUE, NOW() - INTERVAL '20 days',
    NOW() - INTERVAL '21 days'
  ),
  (
    '92000000-0000-0000-0000-000000000006',
    'c1000000-0000-0000-0000-000000000003',
    'booking_cancelled',
    'Consultation Rescheduled',
    'Your virtual consultation with Mr Sarah Mitchell has been cancelled due to a scheduling conflict. You have been offered priority rebooking.',
    '/surgeons/b1000000-0000-0000-0000-000000000002',
    TRUE, NOW() - INTERVAL '9 days',
    NOW() - INTERVAL '10 days'
  ),
  (
    '92000000-0000-0000-0000-000000000007',
    'a1000000-0000-0000-0000-000000000005',
    'enquiry_received',
    'New Enquiry Received',
    'You have a new consultation enquiry from Charlotte Davies regarding a Mummy Makeover.',
    '/enquiries/81000000-0000-0000-0000-000000000004',
    FALSE, NULL,
    NOW() - INTERVAL '7 days'
  ),
  (
    '92000000-0000-0000-0000-000000000008',
    'a1000000-0000-0000-0000-000000000001',
    'enquiry_received',
    'New Enquiry Received',
    'You have a new consultation enquiry from Oliver Brown regarding a Facelift.',
    '/enquiries/81000000-0000-0000-0000-000000000005',
    FALSE, NULL,
    NOW() - INTERVAL '5 days'
  ),
  (
    '92000000-0000-0000-0000-000000000009',
    'a1000000-0000-0000-0000-000000000003',
    'enquiry_received',
    'New Enquiry Received',
    'You have a new consultation enquiry from Charlotte Davies regarding Blepharoplasty.',
    '/enquiries/81000000-0000-0000-0000-000000000008',
    FALSE, NULL,
    NOW() - INTERVAL '4 days'
  ),
  (
    '92000000-0000-0000-0000-000000000010',
    'c1000000-0000-0000-0000-000000000003',
    'enquiry_accepted',
    'Enquiry Accepted',
    'Mr Emily Thompson has accepted your consultation request. Please complete your booking to confirm your appointment.',
    '/bookings/e1000000-0000-0000-0000-000000000003',
    TRUE, NOW() - INTERVAL '5 days',
    NOW() - INTERVAL '6 days'
  ),
  (
    '92000000-0000-0000-0000-000000000011',
    'c1000000-0000-0000-0000-000000000005',
    'enquiry_accepted',
    'Enquiry Accepted',
    'Mr Sarah Mitchell has accepted your consultation request. Please complete your booking to confirm your appointment.',
    '/bookings/e2000000-0000-0000-0000-000000000007',
    TRUE, NOW() - INTERVAL '3 days',
    NOW() - INTERVAL '4 days'
  ),
  (
    '92000000-0000-0000-0000-000000000012',
    'a1000000-0000-0000-0000-000000000001',
    'message_received',
    'New Message from Sophie Williams',
    'Sophie Williams has sent you a message regarding her upcoming surgery.',
    '/messages',
    TRUE, NOW() - INTERVAL '58 days',
    NOW() - INTERVAL '58 days'
  ),
  (
    '92000000-0000-0000-0000-000000000013',
    'a1000000-0000-0000-0000-000000000001',
    'message_received',
    'New Message from Priya Patel',
    'Priya Patel has sent you a message regarding your consultation.',
    '/messages',
    FALSE, NULL,
    NOW() - INTERVAL '5 days'
  ),
  (
    '92000000-0000-0000-0000-000000000014',
    'a1000000-0000-0000-0000-000000000001',
    'message_received',
    'New Message from Marcus Johnson',
    'Marcus Johnson has sent you a message about rebooking his cancelled consultation.',
    '/messages',
    FALSE, NULL,
    NOW() - INTERVAL '18 days'
  ),
  (
    '92000000-0000-0000-0000-000000000015',
    'a1000000-0000-0000-0000-000000000001',
    'review_received',
    'New Review Posted',
    'Priya Patel has left you a 5-star review following her breast augmentation consultation.',
    '/reviews',
    TRUE, NOW() - INTERVAL '8 weeks',
    NOW() - INTERVAL '9 weeks'
  ),
  (
    '92000000-0000-0000-0000-000000000016',
    'a1000000-0000-0000-0000-000000000001',
    'review_received',
    'New Review Posted',
    'Charlotte Davies has left you a 4-star review following her tummy tuck consultation.',
    '/reviews',
    TRUE, NOW() - INTERVAL '6 weeks',
    NOW() - INTERVAL '7 weeks'
  ),
  (
    '92000000-0000-0000-0000-000000000017',
    'a1000000-0000-0000-0000-000000000002',
    'review_received',
    'New Review Posted',
    'Sophie Williams has left you a 5-star review following her rhinoplasty consultation.',
    '/reviews',
    TRUE, NOW() - INTERVAL '5 weeks',
    NOW() - INTERVAL '6 weeks'
  ),
  (
    '92000000-0000-0000-0000-000000000018',
    'a1000000-0000-0000-0000-000000000002',
    'review_received',
    'New Review Posted',
    'Oliver Brown has left you a 5-star review following his rhinoplasty consultation.',
    '/reviews',
    FALSE, NULL,
    NOW() - INTERVAL '4 weeks'
  ),
  (
    '92000000-0000-0000-0000-000000000019',
    'a1000000-0000-0000-0000-000000000003',
    'review_received',
    'New Review Posted',
    'Sophie Williams has left you a 5-star review following her blepharoplasty consultation.',
    '/reviews',
    FALSE, NULL,
    NOW() - INTERVAL '3 weeks'
  ),
  (
    '92000000-0000-0000-0000-000000000020',
    'a1000000-0000-0000-0000-000000000002',
    'review_received',
    'New Review Posted',
    'Marcus Johnson has left you a 4-star review following his revision rhinoplasty consultation.',
    '/reviews',
    FALSE, NULL,
    NOW() - INTERVAL '2 weeks'
  );

-- ─── Surgeon portfolio ────────────────────────────────────────────────────────

INSERT INTO surgeon_portfolio (
  id, surgeon_id, before_url, after_url, procedure, caption, consent_given, is_published, created_at
) VALUES
  (
    '85000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000001',
    'https://cdn.auris.co/portfolio/b1001/rhinoplasty-01-before.jpg',
    'https://cdn.auris.co/portfolio/b1001/rhinoplasty-01-after.jpg',
    'Rhinoplasty',
    'Closed rhinoplasty with dorsal hump reduction and tip refinement. Natural, harmonious result at 12 months.',
    TRUE, TRUE, NOW() - INTERVAL '3 months'
  ),
  (
    '85000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000001',
    'https://cdn.auris.co/portfolio/b1001/facelift-01-before.jpg',
    'https://cdn.auris.co/portfolio/b1001/facelift-01-after.jpg',
    'Facelift',
    'Full facelift with neck lift. Patient sought a refreshed, natural appearance. Result shown at 6 months post-op.',
    TRUE, TRUE, NOW() - INTERVAL '4 months'
  ),
  (
    '85000000-0000-0000-0000-000000000003',
    'b1000000-0000-0000-0000-000000000002',
    'https://cdn.auris.co/portfolio/b1002/rhinoplasty-01-before.jpg',
    'https://cdn.auris.co/portfolio/b1002/rhinoplasty-01-after.jpg',
    'Rhinoplasty',
    'Open rhinoplasty for dorsal hump reduction with preserved natural nasal character. 9-month result.',
    TRUE, TRUE, NOW() - INTERVAL '2 months'
  ),
  (
    '85000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000002',
    'https://cdn.auris.co/portfolio/b1002/revision-01-before.jpg',
    'https://cdn.auris.co/portfolio/b1002/revision-01-after.jpg',
    'Revision Rhinoplasty',
    'Complex revision following previous surgery elsewhere. Cartilage grafting with improved symmetry and airway function.',
    TRUE, TRUE, NOW() - INTERVAL '5 months'
  ),
  (
    '85000000-0000-0000-0000-000000000005',
    'b1000000-0000-0000-0000-000000000003',
    'https://cdn.auris.co/portfolio/b1003/bleph-01-before.jpg',
    'https://cdn.auris.co/portfolio/b1003/bleph-01-after.jpg',
    'Upper Blepharoplasty',
    'Bilateral upper blepharoplasty with ptosis repair. Patient reported feeling more alert and confident at 3-month review.',
    TRUE, TRUE, NOW() - INTERVAL '3 months'
  ),
  (
    '85000000-0000-0000-0000-000000000006',
    'b1000000-0000-0000-0000-000000000003',
    'https://cdn.auris.co/portfolio/b1003/browlift-01-before.jpg',
    'https://cdn.auris.co/portfolio/b1003/browlift-01-after.jpg',
    'Brow Lift',
    'Endoscopic brow lift to address brow ptosis and forehead lines. Combined with upper blepharoplasty.',
    TRUE, TRUE, NOW() - INTERVAL '6 months'
  ),
  (
    '85000000-0000-0000-0000-000000000007',
    'b1000000-0000-0000-0000-000000000004',
    'https://cdn.auris.co/portfolio/b1004/ba-01-before.jpg',
    'https://cdn.auris.co/portfolio/b1004/ba-01-after.jpg',
    'Breast Augmentation',
    'Bilateral breast augmentation with anatomical implants via inframammary approach. Natural, proportionate enhancement.',
    TRUE, TRUE, NOW() - INTERVAL '2 months'
  ),
  (
    '85000000-0000-0000-0000-000000000008',
    'b1000000-0000-0000-0000-000000000004',
    'https://cdn.auris.co/portfolio/b1004/reduction-01-before.jpg',
    'https://cdn.auris.co/portfolio/b1004/reduction-01-after.jpg',
    'Breast Reduction',
    'Bilateral breast reduction with superior pedicle technique. Significant improvement in posture and patient comfort.',
    TRUE, TRUE, NOW() - INTERVAL '4 months'
  ),
  (
    '85000000-0000-0000-0000-000000000009',
    'b1000000-0000-0000-0000-000000000005',
    'https://cdn.auris.co/portfolio/b1005/lipo-01-before.jpg',
    'https://cdn.auris.co/portfolio/b1005/lipo-01-after.jpg',
    'Liposuction',
    'High-definition liposuction of abdomen and flanks. Sculpted result achieved with minimal downtime.',
    TRUE, TRUE, NOW() - INTERVAL '3 months'
  ),
  (
    '85000000-0000-0000-0000-000000000010',
    'b1000000-0000-0000-0000-000000000005',
    'https://cdn.auris.co/portfolio/b1005/tummytuck-01-before.jpg',
    'https://cdn.auris.co/portfolio/b1005/tummytuck-01-after.jpg',
    'Tummy Tuck',
    'Extended abdominoplasty with muscle repair following significant weight loss. Dramatic improvement in abdominal contour.',
    TRUE, TRUE, NOW() - INTERVAL '5 months'
  );

-- ─── Surgeon blocked slots ────────────────────────────────────────────────────

INSERT INTO surgeon_blocked_slots (id, surgeon_id, blocked_at, duration_mins, reason) VALUES
  (
    '84000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000001',
    NOW() + INTERVAL '2 weeks',
    480,
    'Annual leave'
  ),
  (
    '84000000-0000-0000-0000-000000000002',
    'b1000000-0000-0000-0000-000000000002',
    NOW() + INTERVAL '3 weeks',
    240,
    'Surgical conference in Amsterdam'
  ),
  (
    '84000000-0000-0000-0000-000000000003',
    'b1000000-0000-0000-0000-000000000003',
    NOW() + INTERVAL '1 week',
    120,
    'Teaching commitment at Moorfields'
  ),
  (
    '84000000-0000-0000-0000-000000000004',
    'b1000000-0000-0000-0000-000000000004',
    NOW() + INTERVAL '4 weeks',
    480,
    'Annual leave'
  ),
  (
    '84000000-0000-0000-0000-000000000005',
    'b1000000-0000-0000-0000-000000000005',
    NOW() + INTERVAL '2 weeks' + INTERVAL '3 days',
    360,
    'NHS surgical list commitment'
  );

-- ─── Audit log ───────────────────────────────────────────────────────────────

INSERT INTO audit_log (id, actor_id, action, target_type, target_id, metadata, ip_address, created_at) VALUES
  (
    '86000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000001',
    'profile.update',
    'surgeon_profile',
    'b1000000-0000-0000-0000-000000000001',
    '{"fields_updated": ["bio", "procedures", "consult_fee_clinic"]}',
    '192.168.1.10',
    NOW() - INTERVAL '5 months'
  ),
  (
    '86000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000002',
    'profile.update',
    'surgeon_profile',
    'b1000000-0000-0000-0000-000000000002',
    '{"fields_updated": ["bio", "consult_fee_virtual", "languages"]}',
    '192.168.1.11',
    NOW() - INTERVAL '4 months'
  ),
  (
    '86000000-0000-0000-0000-000000000003',
    'a1000000-0000-0000-0000-000000000003',
    'profile.update',
    'surgeon_profile',
    'b1000000-0000-0000-0000-000000000003',
    '{"fields_updated": ["hospital", "address", "consult_fee_clinic"]}',
    '192.168.1.12',
    NOW() - INTERVAL '3 months'
  ),
  (
    '86000000-0000-0000-0000-000000000004',
    'a1000000-0000-0000-0000-000000000004',
    'availability.update',
    'surgeon_profile',
    'b1000000-0000-0000-0000-000000000004',
    '{"days_updated": [1, 2, 4], "action": "added_thursday_slot"}',
    '192.168.1.13',
    NOW() - INTERVAL '2 months'
  ),
  (
    '86000000-0000-0000-0000-000000000005',
    'a1000000-0000-0000-0000-000000000005',
    'profile.update',
    'surgeon_profile',
    'b1000000-0000-0000-0000-000000000005',
    '{"fields_updated": ["procedures", "bio", "years_experience"]}',
    '192.168.1.14',
    NOW() - INTERVAL '1 month'
  ),
  (
    '86000000-0000-0000-0000-000000000006',
    'c1000000-0000-0000-0000-000000000001',
    'profile.update',
    'patient_profile',
    'd1000000-0000-0000-0000-000000000001',
    '{"fields_updated": ["procedure_interests", "budget_range", "timeline"]}',
    '192.168.1.20',
    NOW() - INTERVAL '45 days'
  ),
  (
    '86000000-0000-0000-0000-000000000007',
    'c1000000-0000-0000-0000-000000000002',
    'profile.update',
    'patient_profile',
    'd1000000-0000-0000-0000-000000000002',
    '{"fields_updated": ["consult_preference", "location_preference"]}',
    '192.168.1.21',
    NOW() - INTERVAL '35 days'
  ),
  (
    '86000000-0000-0000-0000-000000000008',
    'a1000000-0000-0000-0000-000000000001',
    'availability.update',
    'surgeon_profile',
    'b1000000-0000-0000-0000-000000000001',
    '{"action": "blocked_slot_added", "blocked_slot_id": "84000000-0000-0000-0000-000000000001"}',
    '192.168.1.10',
    NOW() - INTERVAL '5 days'
  ),
  (
    '86000000-0000-0000-0000-000000000009',
    'a1000000-0000-0000-0000-000000000002',
    'availability.update',
    'surgeon_profile',
    'b1000000-0000-0000-0000-000000000002',
    '{"action": "blocked_slot_added", "blocked_slot_id": "84000000-0000-0000-0000-000000000002"}',
    '192.168.1.11',
    NOW() - INTERVAL '4 days'
  ),
  (
    '86000000-0000-0000-0000-000000000010',
    'c1000000-0000-0000-0000-000000000003',
    'booking.cancelled',
    'booking',
    'e2000000-0000-0000-0000-000000000011',
    '{"reason": "Surgeon unavailable", "refund_status": "full_refund_issued"}',
    '192.168.1.22',
    NOW() - INTERVAL '10 days'
  );

-- !Downs

DELETE FROM audit_log WHERE id IN (
  '86000000-0000-0000-0000-000000000001',
  '86000000-0000-0000-0000-000000000002',
  '86000000-0000-0000-0000-000000000003',
  '86000000-0000-0000-0000-000000000004',
  '86000000-0000-0000-0000-000000000005',
  '86000000-0000-0000-0000-000000000006',
  '86000000-0000-0000-0000-000000000007',
  '86000000-0000-0000-0000-000000000008',
  '86000000-0000-0000-0000-000000000009',
  '86000000-0000-0000-0000-000000000010'
);
DELETE FROM surgeon_portfolio WHERE id IN (
  '85000000-0000-0000-0000-000000000001',
  '85000000-0000-0000-0000-000000000002',
  '85000000-0000-0000-0000-000000000003',
  '85000000-0000-0000-0000-000000000004',
  '85000000-0000-0000-0000-000000000005',
  '85000000-0000-0000-0000-000000000006',
  '85000000-0000-0000-0000-000000000007',
  '85000000-0000-0000-0000-000000000008',
  '85000000-0000-0000-0000-000000000009',
  '85000000-0000-0000-0000-000000000010'
);
DELETE FROM surgeon_blocked_slots WHERE id IN (
  '84000000-0000-0000-0000-000000000001',
  '84000000-0000-0000-0000-000000000002',
  '84000000-0000-0000-0000-000000000003',
  '84000000-0000-0000-0000-000000000004',
  '84000000-0000-0000-0000-000000000005'
);
DELETE FROM notifications WHERE id IN (
  '92000000-0000-0000-0000-000000000001',
  '92000000-0000-0000-0000-000000000002',
  '92000000-0000-0000-0000-000000000003',
  '92000000-0000-0000-0000-000000000004',
  '92000000-0000-0000-0000-000000000005',
  '92000000-0000-0000-0000-000000000006',
  '92000000-0000-0000-0000-000000000007',
  '92000000-0000-0000-0000-000000000008',
  '92000000-0000-0000-0000-000000000009',
  '92000000-0000-0000-0000-000000000010',
  '92000000-0000-0000-0000-000000000011',
  '92000000-0000-0000-0000-000000000012',
  '92000000-0000-0000-0000-000000000013',
  '92000000-0000-0000-0000-000000000014',
  '92000000-0000-0000-0000-000000000015',
  '92000000-0000-0000-0000-000000000016',
  '92000000-0000-0000-0000-000000000017',
  '92000000-0000-0000-0000-000000000018',
  '92000000-0000-0000-0000-000000000019',
  '92000000-0000-0000-0000-000000000020'
);
DELETE FROM messages WHERE id IN (
  '91000000-0000-0000-0000-000000000001',
  '91000000-0000-0000-0000-000000000002',
  '91000000-0000-0000-0000-000000000003',
  '91000000-0000-0000-0000-000000000004',
  '91000000-0000-0000-0000-000000000005',
  '91000000-0000-0000-0000-000000000006',
  '91000000-0000-0000-0000-000000000007',
  '91000000-0000-0000-0000-000000000008',
  '91000000-0000-0000-0000-000000000009',
  '91000000-0000-0000-0000-000000000010',
  '91000000-0000-0000-0000-000000000011',
  '91000000-0000-0000-0000-000000000012',
  '91000000-0000-0000-0000-000000000013',
  '91000000-0000-0000-0000-000000000014',
  '91000000-0000-0000-0000-000000000015',
  '91000000-0000-0000-0000-000000000016',
  '91000000-0000-0000-0000-000000000017',
  '91000000-0000-0000-0000-000000000018',
  '91000000-0000-0000-0000-000000000019',
  '91000000-0000-0000-0000-000000000020'
);
DELETE FROM enquiries WHERE id IN (
  '81000000-0000-0000-0000-000000000001',
  '81000000-0000-0000-0000-000000000002',
  '81000000-0000-0000-0000-000000000003',
  '81000000-0000-0000-0000-000000000004',
  '81000000-0000-0000-0000-000000000005',
  '81000000-0000-0000-0000-000000000006',
  '81000000-0000-0000-0000-000000000007',
  '81000000-0000-0000-0000-000000000008',
  '81000000-0000-0000-0000-000000000009',
  '81000000-0000-0000-0000-000000000010'
);
DELETE FROM reviews WHERE id IN (
  '72000000-0000-0000-0000-000000000001',
  '72000000-0000-0000-0000-000000000002',
  '72000000-0000-0000-0000-000000000003',
  '72000000-0000-0000-0000-000000000004',
  '72000000-0000-0000-0000-000000000005',
  '72000000-0000-0000-0000-000000000006'
);
DELETE FROM bookings WHERE id IN (
  'e2000000-0000-0000-0000-000000000001',
  'e2000000-0000-0000-0000-000000000002',
  'e2000000-0000-0000-0000-000000000003',
  'e2000000-0000-0000-0000-000000000004',
  'e2000000-0000-0000-0000-000000000005',
  'e2000000-0000-0000-0000-000000000006',
  'e2000000-0000-0000-0000-000000000007',
  'e2000000-0000-0000-0000-000000000008',
  'e2000000-0000-0000-0000-000000000009',
  'e2000000-0000-0000-0000-000000000010',
  'e2000000-0000-0000-0000-000000000011',
  'e2000000-0000-0000-0000-000000000012'
);
