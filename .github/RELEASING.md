# Releasing to Maven Central (maintainers)

Maintainer runbook. Part one is one-time setup. Part two is the steps for each release. Part three covers key upkeep.
It describes where secrets live. It never contains a secret, and a private key must never be committed.

## Part 1: One-time setup

### 1. Central Portal account and namespace

1. Sign in at https://central.sonatype.com.
2. Namespaces → add `com.braintrustappliedresearch`.
3. Publish the DNS TXT record the Portal gives you on `braintrustappliedresearch.com`, then click Verify.
4. Account → Generate User Token. Keep both halves. They become `MAVEN_CENTRAL_USERNAME` and
   `MAVEN_CENTRAL_PASSWORD` (step 4 below).

### 2. Create the signing key (PGP/GPG)

```
gpg --full-generate-key
```

Menu choices:

- Key type: **ECC (sign and encrypt)** (option 9 on older GnuPG), curve **Curve 25519**.
  RSA and RSA at 4096 bits is the compatible alternative. Do not use DSA, Elgamal-only or RSA at 2048 bits or below.
- Expiry: `2y` is recommended (see Part 3). `0` (never) also works.
- Name and email: a real address you can receive mail at. The keyserver verifies it.
- Passphrase: required. It becomes `MAVEN_GPG_PASSPHRASE`.

Find the key ID (the hex after the slash on the `sec` line, or the 40-character fingerprint below it):

```
gpg --list-secret-keys --keyid-format=long
```

### 3. Publish the PUBLIC key

Central checks signatures against a public keyserver, so the public half must be published before the first release.

```
gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
```

Then verify your email at https://keys.openpgp.org/manage (they send a link). If `--send-keys` fails, export the armored
public key and paste it at https://keys.openpgp.org/upload:

```
gpg --armor --export <KEY_ID>
```

The armored public key (`-----BEGIN PGP PUBLIC KEY BLOCK-----`) is safe to publish. It may be kept in this file, in
the section "Current public key" below.

### 4. Add the secrets in GitHub

Settings → Environments → create **`maven-central`**. Add yourself (and any other release approver) as a *required
reviewer*. Add these as *environment* secrets, not repository secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Portal token, username half |
| `MAVEN_CENTRAL_PASSWORD` | Portal token, password half |
| `MAVEN_GPG_PRIVATE_KEY` | Output of `gpg --armor --export-secret-keys <KEY_ID>`, the whole block including the BEGIN and END lines |
| `MAVEN_GPG_PASSPHRASE` | The key's passphrase |

Settings → Secrets and variables → Actions → *repository* secret **`SNYK_TOKEN`** (a service-account token is better
than a personal one). The `snyk` job fails without it, which blocks the release on purpose.

Optional: Settings → Rules → a tag ruleset for `v*`, so only maintainers can create release tags.

Delete any local copy of the exported private key once it is in GitHub. Keep the key itself backed up offline (for
example `gpg --export-secret-keys` to an encrypted drive, plus a revocation certificate:
`gpg --gen-revoke <KEY_ID>`).

## Part 2: Each release

1. Make sure `main` is green and up to date. Optional local dry run, which builds the sources, javadoc and SBOM jars
   without signing:

   ```
   ./mvnw -Prelease verify -Dgpg.skip
   ```

2. Run Snyk locally if you like (see CONTRIBUTORS.md), and fix anything medium or above that has a published fix.
3. Tag and push. The tag is the version of record, and the workflow sets the POM version from it:

   ```
   git checkout main && git pull
   git tag v1.0.0
   git push origin v1.0.0
   ```

4. In Actions, `test` and `snyk` run first. When `publish` waits, open it and click **Review deployments → Approve**.
5. `publish` signs the artifacts and uploads a validated bundle. It does **not** release it (`autoPublish` is `false`).
6. Open https://central.sonatype.com/publishing/deployments, check the contents, and click **Publish**. Use **Drop** to
   discard. A published version is permanent: it cannot be changed or deleted.
7. The artifact shows up on Maven Central shortly after, and in search later.

If a release fails after the bundle was uploaded: drop it in the Portal, fix the cause, delete the tag
(`git push --delete origin v1.0.0 && git tag -d v1.0.0`), and tag again.

## Part 3: Key upkeep

- **Expiry.** Extend before it lapses: `gpg --edit-key <KEY_ID>`, then `expire`, then `save`. Re-publish the key
  (`--send-keys`) and update `MAVEN_GPG_PRIVATE_KEY` in GitHub, since the exported copy carries the old expiry. Existing
  releases stay valid after the key expires.
- **Rotation or leak.** Revoke with the saved certificate, upload the revocation, create a new key, repeat Part 1,
  steps 2 to 4. Rotate `MAVEN_CENTRAL_*` too if the token may have leaked (Portal → Account).
- **Passphrase change.** Update `MAVEN_GPG_PASSPHRASE` and, if you re-export the key, `MAVEN_GPG_PRIVATE_KEY`.

## Current public key

Fingerprint: _(add)_

```
(paste the output of: gpg --armor --export <KEY_ID>)
```
