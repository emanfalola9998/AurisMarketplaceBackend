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
     Postgres instance (a separate Neon project/branch from the dev one is
     fine — the database itself doesn't live on Render).
   - `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` — live-mode Stripe keys,
     once the Stripe account is verified for live charges.
   - `SMTP_HOST`, `SMTP_USER`, `SMTP_PASSWORD` — a real transactional-email
     provider (the default `play.mailer.host` assumes SendGrid).
   - `AWS_S3_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` — see
     *File storage* below for the bucket setup this also requires.
   - `FRONTEND_URL` — the deployed frontend's URL, used to build links in
     transactional emails.
   - `ALLOWED_HOSTS`, `ALLOWED_ORIGINS` — the real production domain(s); the
     committed defaults (`auris.co`) only work if that's the actual domain.
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
