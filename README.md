# Auris Backend

Scala + Play Framework + Slick + PostgreSQL API for Auris, an aesthetic-surgery
marketplace.

## Configuration

Sensitive values in `conf/application.conf` are placeholders by default and
meant to be overridden by environment variables — see the `${?ENV_VAR}` lines
throughout that file (`PLAY_SECRET_KEY`, `JWT_SECRET`, `DATABASE_URL`,
`STRIPE_SECRET_KEY`, etc.). `conf/application.dev.conf` (gitignored) is where
local-only secrets belong instead.

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
