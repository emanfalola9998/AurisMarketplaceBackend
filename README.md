# Auris Backend

Scala + Play Framework + Slick + PostgreSQL API for Auris, an aesthetic-surgery
marketplace.

## Configuration

Sensitive values in `conf/application.conf` are placeholders by default and
meant to be overridden by environment variables — see the `${?ENV_VAR}` lines
throughout that file (`PLAY_SECRET_KEY`, `JWT_SECRET`, `DATABASE_URL`,
`STRIPE_SECRET_KEY`, etc.). `conf/application.dev.conf` (gitignored) is where
local-only secrets belong instead.

## Deployment

Deploys to [Render](https://render.com) as a Docker web service — `Dockerfile`
multi-stage builds the app with sbt and stages it (`sbt stage`) into a slim
JRE runtime image, and `render.yaml` is a Render Blueprint that creates the
service with the right build/health-check config pre-filled.

1. Push this repo to GitHub, then at
   [dashboard.render.com/blueprints](https://dashboard.render.com/blueprints)
   connect it — Render reads `render.yaml` and creates the `auris-backend`
   web service automatically.
2. Every env var listed in `render.yaml` with `sync: false` has no value
   checked into git on purpose — Render prompts for each one once, in its
   dashboard, after the first deploy:
   - `PLAY_SECRET_KEY`, `JWT_SECRET` — any random string meeting the length
     noted in `application.conf`'s placeholders; generate with e.g.
     `openssl rand -base64 48`.
   - `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` — a **production**
     Postgres instance, kept separate from the dev one. At
     [console.neon.tech](https://console.neon.tech), create a new **project**
     (not just a branch of the dev one — full isolation is worth it for
     production) named e.g. `auris-production`, region **AWS eu-west-2
     (London)** to match both the dev DB and Render's `frankfurt` region in
     `render.yaml` above (minimizes latency; a US-region DB behind an
     EU-region app adds real round-trip time to every request). Neon shows a
     connection string immediately — split it into the three env vars:
     `DATABASE_URL` is `jdbc:postgresql://<host>/<dbname>?sslmode=require`
     (note the `jdbc:` prefix application.conf expects, which Neon's own
     connection string doesn't include), `DATABASE_USER` and
     `DATABASE_PASSWORD` are the corresponding parts of what Neon gave you.
   - `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` — live-mode Stripe keys,
     once the Stripe account is verified for live charges.
   - `SMTP_HOST`, `SMTP_USER`, `SMTP_PASSWORD` — a real transactional-email
     provider (the default `play.mailer.host` assumes SendGrid).
   - `AWS_S3_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` — see
     *File storage* below for the bucket setup this also requires.
   - `FRONTEND_URL` — the deployed frontend's URL, used to build links in
     transactional emails.
   - There is **no** `ALLOWED_HOSTS`/`ALLOWED_ORIGINS` env var — both are
     array-typed config keys, and Typesafe Config substitutes environment
     variables as plain strings, never as a list, so setting either one
     crashes the app at boot (`ConfigException$WrongType: ... has type
     STRING rather than LIST`), confirmed directly against a local build.
     `play.filters.hosts.allowed` already hardcodes `.onrender.com`
     (`conf/application.conf`) so Render's own health checks pass; once
     the frontend has a real deployed origin, add it to
     `play.filters.cors.allowedOrigins` directly and redeploy — CORS has
     no wildcard-domain support, so this one needs the exact origin
     either way, env var or not.
3. `ConfigValidator` (`app/co/auris/startup/ConfigValidator.scala`) refuses
   to boot in production if `PLAY_SECRET_KEY`/`JWT_SECRET` are still the
   committed placeholders, and logs warnings for the Stripe/mailer/S3 ones —
   check the Render logs after first deploy for anything still misconfigured.
4. **First deploy only:** `render.yaml` sets `EVOLUTIONS_AUTO_APPLY=false`
   (the safe default for every deploy *after* the first, so a schema change
   never applies itself unreviewed) — but that also means a brand-new,
   empty production database has no tables yet, and every request will fail
   until the schema exists. Either flip `EVOLUTIONS_AUTO_APPLY` to `true` in
   the Render dashboard for the first deploy only (safe here specifically
   because the database is empty, so there's nothing an auto-applied
   evolution could get wrong), then back to `false` once it's up — or apply
   `conf/evolutions/default/*.sql` to the production database manually
   first, by hand or via `psql`, before the first deploy.
5. **Also first deploy only:** those same evolutions seed demo data meant
   for local dev — 5 fake surgeons, fake patients, bookings, reviews — plus
   a real admin account (`admin@auris.co`) with a password printed in
   evolution `1.sql`'s own comment. Immediately after the schema is up, run
   `scripts/production-first-deploy-cleanup.sql` (edit the placeholder
   password in it first) against the production database to remove every
   seeded row except that one admin account, with its password rotated to
   something real. Verified against a throwaway local database running the
   actual evolutions end to end before being added here — see that script's
   header comment for exactly what it does and why.

## Stripe (live mode)

Only test-mode keys (`sk_test_...`/`pk_test_...`) have ever been used,
confirmed against `conf/application.dev.conf` and the frontend's
`.env.local`. Going live needs:

1. **Activate the Stripe account for live charges** — in the Stripe
   dashboard, this means completing business verification (legal entity,
   bank account for payouts, etc). This step is entirely on Stripe's side
   and can take a few days; worth starting before everything else is ready.
2. Once activated, switch the dashboard to **Live mode** (top-left toggle)
   and copy the live secret key into Render's `STRIPE_SECRET_KEY`.
3. **Register the live webhook endpoint** — Developers → Webhooks → Add
   endpoint, URL `https://<your-render-url>/api/stripe/webhook` (only
   exists once the backend is deployed, so this step comes after that).
   `StripeWebhookController.handle` (`app/co/auris/controllers/StripeWebhookController.scala`)
   only acts on 5 event types — select exactly these, nothing more is read:
   - `payment_intent.succeeded`
   - `invoice.paid`
   - `invoice.payment_failed`
   - `customer.subscription.deleted`
   - `customer.subscription.updated`

   Stripe shows the endpoint's signing secret once it's created — that's
   `STRIPE_WEBHOOK_SECRET` (a **live**-mode endpoint has its own secret,
   separate from the test-mode one already in use locally).
4. In the frontend, set `VITE_STRIPE_PUBLISHABLE_KEY` to the matching
   **live** publishable key (`pk_live_...`) in Vercel's env vars — test and
   live mode must match on both the frontend publishable key and this
   backend's secret key, or `stripe.confirmPayment()` fails with a
   key-mismatch error (noted in the frontend repo's README too).
5. Test-mode's known-safe card number (`4242 4242 4242 4242`) does **not**
   work in live mode for obvious reasons — the first live transaction has
   to be a real card making a real charge. Consider a small real booking
   as the first end-to-end live test rather than assuming live mode works
   just because test mode did.

## Transactional email (SMTP)

`play.mailer.mock = true` everywhere today — every email `NotificationService`
(`app/co/auris/services/NotificationService.scala`) sends (verification,
password reset, booking confirmations, enquiry notifications) is logged, not
actually delivered. Going live needs:

1. **A real SMTP provider account.** `play.mailer.host` defaults to
   `smtp.sendgrid.net`, so SendGrid is the path of least config change, but
   Play Mailer works with any SMTP provider — swap the host if you'd rather
   use something else.
2. **Verify the sending domain** (`auris.email.fromAddress` is
   `noreply@auris.co`) with whichever provider you pick — this means adding
   SPF/DKIM DNS records for `auris.co` in the provider's dashboard. Sending
   from an unverified domain gets emails marked as spam or rejected outright
   by most receiving mail servers, regardless of how correct the SMTP
   credentials are. This assumes `auris.co` is an owned, DNS-controllable
   domain — if it isn't yet, that's a prerequisite to this step, not
   something `NotificationService` can work around.
3. Set `SMTP_USER` (SendGrid's convention: the literal string `apikey`, not
   an actual username) and `SMTP_PASSWORD` (the real API key) in Render.
4. `render.yaml` already sets `MAILER_MOCK=false` — once real credentials
   are in place, that's the only switch needed; no code change.

## File storage (`auris.storage`)

Surgeon avatars and portfolio photos are handled by `StorageService`
(`app/co/auris/services/StorageService.scala`), which supports two providers
behind the same interface:

| `auris.storage.provider` | Behaviour |
|---|---|
| `local` (default) | Writes to `auris.storage.localPath` on disk, served back by `UploadsController` at `/uploads/*`. No AWS setup needed — this is what every environment uses today. |
| `s3` | Uploads to the bucket named by `auris.storage.s3Bucket` (env var `AWS_S3_BUCKET`) in `auris.storage.s3Region`, and returns the object's public `https://<bucket>.s3.<region>.amazonaws.com/<key>` URL. |

`provider` and `s3Region` have no env-var hook in `application.conf` — switch
them either by editing that file directly for a deployment, or by passing a
JVM system property at startup (Play/Typesafe Config honours `-D` for any
config key, no code change needed):

```
-Dauris.storage.provider=s3
```

AWS credentials are resolved via the AWS SDK's normal default chain (env
vars, `~/.aws/credentials`, or an instance/task IAM role) — nothing
Auris-specific to configure beyond having *some* valid credentials available
to the process.

### Production setup

The provider was verified earlier against a real but disposable test
bucket — production needs its own **permanent** bucket (a fresh
`aws s3 mb`, not a renamed/reused version of the test one) plus its own
IAM credentials:

1. Create the bucket and set `AWS_S3_BUCKET`/`auris.storage.s3Region` (and
   `auris.storage.provider = "s3"`, which has no env-var hook — see above)
   to match.
2. Create an IAM user (or role, if Render supported instance roles — it
   doesn't, so this needs a real access key pair) scoped to **only**
   `s3:PutObject` on that one bucket — `StorageService.storeToS3` never
   calls `GetObject`, `ListBucket`, or `DeleteObject`; reads happen over
   the plain public HTTPS URL, not through the SDK, so the credentials the
   app itself holds don't need any read/list/delete permission at all:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Action": "s3:PutObject",
       "Resource": "arn:aws:s3:::<your-bucket>/*"
     }]
   }
   ```
3. Set `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` in Render to that IAM
   user's key pair — picked up automatically by the AWS SDK's default
   credential chain, no code change needed.

### Required bucket setup for the `s3` provider

**A freshly created S3 bucket is private by default and will not work
out of the box** — uploads will succeed, but every `avatarUrl` returned to
the frontend will 403 until the bucket is made publicly readable. This was
verified against a real bucket while building the provider: `PutObject`
worked immediately, but the returned URL 403'd until the two steps below
were applied.

1. **Disable Block Public Access** on the bucket (all four settings — ACLs
   and policy):

   ```bash
   aws s3api put-public-access-block --bucket <your-bucket> \
     --public-access-block-configuration \
     BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false
   ```

2. **Add a bucket policy** granting public read on the objects Auris writes:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "PublicReadForAurisUploads",
         "Effect": "Allow",
         "Principal": "*",
         "Action": "s3:GetObject",
         "Resource": "arn:aws:s3:::<your-bucket>/*"
       }
     ]
   }
   ```

   ```bash
   aws s3api put-bucket-policy --bucket <your-bucket> --policy file://bucket-policy.json
   ```

This makes the whole bucket world-readable (matching how `local` storage
already behaves — `UploadsController` serves any file under its root with no
auth), which is appropriate for public-facing images like avatars, but do
**not** point this provider at a bucket that also holds anything sensitive.

**If you'd rather not have a publicly readable bucket at all**, put
CloudFront in front of it instead, using Origin Access Control (OAC) so the
bucket itself stays fully private and only CloudFront can read it — this
needs its own distribution and isn't something `StorageService` sets up for
you.

`StorageService` itself never touches bucket policy or public-access-block
settings — that's account/infrastructure configuration, done once per
environment, not application logic.
