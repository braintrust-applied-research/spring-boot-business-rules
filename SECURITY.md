# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for a security problem. Report it privately through GitHub:

**https://github.com/braintrust-applied-research/spring-boot-business-rules/security/advisories/new**
(or the repository's **Security** tab, then **Report a vulnerability**).

Include what you found, how to reproduce it (a minimal request or test is ideal), the version or commit, and what
an attacker could do with it. If private reporting is unavailable, open an issue that says only that you have a
security report and ask for a private way to send it, without any details.

We aim to acknowledge a report within 7 days, to keep you informed while we investigate, and to agree a disclosure
date with you once a fix is ready. We credit reporters who want credit.

## Supported versions

This project is pre-1.0. Security fixes are made on `main` and released as the next version; only the latest release is
supported.

## What is in scope

- The `spring-boot-business-rules` library: the REST endpoint, how request data reaches the rules engine, and what
  responses reveal.
- The example application and the release pipeline (`.github/workflows/ci.yml`).

## Known by design (not vulnerabilities on their own)

- **The endpoint has no authentication of its own.** Protecting it is the host application's job; see "Securing the
  endpoint" in the README. The library logs a warning at startup when Spring Security is absent. A way to bypass
  authentication that a host *has* configured would be in scope.
- **The DMN models are trusted code.** They are loaded from the application's classpath, not from callers, and FEEL can
  call external Java functions. Do not load models from untrusted sources.

## Reports we are especially interested in

- Any way for a caller to influence a result other than through the model's declared inputs.
- Any way for a caller to learn about the model's structure while `spring-boot-business-rules.instructions-enabled` is off.
- Weaknesses in the release pipeline that could let someone publish an artifact they should not.
