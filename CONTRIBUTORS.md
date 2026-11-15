# Contributing

Contributions are welcome: bug fixes, new features, documentation, and example improvements.

This project was created quickly using AI, so careful review, tests and cleanups are especially appreciated.

Found a security problem? Please report it privately; see [SECURITY.md](SECURITY.md). Do not open a public issue.

## Compatibility requirements

Changes must support all of the following:

| Platform | Supported versions |
| --- | --- |
| Spring Boot | the **4.0.x** line or greater (the parent version in the root `pom.xml` is the minimum) |
| Java | the current LTS (**Java 25**) and the previous LTS (**Java 21**) |

A change that only works on a newer Spring Boot or Java than these, or that drops either listed Java version, will
not be merged. When the Spring Boot or Java LTS line moves on, these versions are updated here and in
`.github/workflows/ci.yml` together.

## Before opening a pull request

1. Run the full build: `mvn verify`. It runs all tests, including the example app's integration tests, plus the
   code quality checks (compiler lint with `-Werror`, Checkstyle, SpotBugs). See "Code quality checks" in the README.
   Any finding fails the build.
2. Add or update tests for behavior you change.
3. Write Javadoc for anything public you add to the library, with `@param` and `@return`. Missing or malformed
   comments fail the build (`-Xdoclint`), and the API docs are published from them.
4. If you change the REST contract, update `docs/openapi.yaml` and the README. `./docs/build-site.sh` lints the spec
   and builds the site (README, Swagger UI and Javadoc).
5. Keep changes focused: one concern per pull request.
6. Write commit titles in Conventional Commits style (see "Commit messages" below).

To run the checks automatically before every commit, enable the bundled hook once per clone:
`git config core.hooksPath .githooks`.

Formatting is set by `.editorconfig` (4 spaces for Java, 2 for XML/YAML/Markdown, LF line endings, no tabs) and
`.gitattributes`.

## Commit messages

Commit titles follow the [Conventional Commits](https://www.conventionalcommits.org/) style:

```
<type>(<optional scope>): <short description>
```

- **Type** is one of `feat` (a new capability), `fix` (a bug fix), `docs`, `test`, `refactor`, `perf`, `build` (Maven,
  dependencies), `ci` (workflows), `chore`, or `revert`.
- **Scope** is optional and names the area, for example `library`, `example`, `docs` or `deps`.
- **Description** is in the imperative mood ("add", not "added"), starts in lower case, has no trailing period, and
  keeps the whole title to about 72 characters.
- **Body** (optional, after a blank line) explains *why* the change was made, not just what changed.
- **Breaking changes** put `!` after the type or scope (`feat(library)!: ...`) and add a `BREAKING CHANGE:` footer
  describing what callers must change.

Examples:

```
feat(library): add "evaluate":"all" to run every decision in a model
fix(library): reject request keys that are not declared inputs
docs: add decision table screenshots to the README
build(deps): bump Spring Boot to 4.0.9
```

If a pull request is squash-merged, its title becomes the commit title, so use this style for pull request titles too.

## Maintainer notes

- **LICENSE and NOTICE exist twice.** The repository root has the canonical copies, and
  `spring-boot-business-rules/src/main/resources/META-INF/` has copies that ship inside the jar (IDEs cannot map a
  resource folder outside the module). If you change one, change the other.
- **Docs tools are locked.** The npm tools that build the site (Redocly, marked, Swagger UI) are pinned in
  `docs/tools/package.json` and installed with `npm ci --ignore-scripts`, which checks every package against the
  integrity hashes in `docs/tools/package-lock.json` and runs no install scripts. Dependabot updates both files
  together; to bump a tool by hand, edit `package.json` and run `npm install --package-lock-only --ignore-scripts`
  in that folder.
- **The Snyk CLI is locked too.** The release gate runs with `SNYK_TOKEN`, so the CLI is installed from
  `ci/snyk/package.json` and `ci/snyk/package-lock.json` with `npm ci --ignore-scripts`, not fetched with `npx`. To bump
  it, edit the version in `package.json` and run `npm install --package-lock-only --ignore-scripts` in `ci/snyk`
  (Dependabot does this too). Run the gate by hand with `ci/snyk/node_modules/.bin/snyk`.
- **Dependency security.** Dependencies are scanned with Snyk (`snyk test --all-projects`, plus `snyk code test` for the
  source), and the CI `snyk` job is a release gate: a version tag is not published to Maven Central if a dependency has a medium-or-higher finding (medium, high or critical) for which a fixed version exists, or if Snyk Code reports a medium-or-higher finding. Dependency findings with no published fix are listed but do not block
  (see the README's Pipeline section for the `SNYK_TOKEN` secret). Run the same two commands locally, with `--severity-threshold=medium`, before tagging. The gate reads `fixedIn` from `snyk test --json` and does not use `--fail-on`, which only counts issues fixable by upgrading a direct dependency and so passed critical transitive issues that did have fixes.
  To accept a finding deliberately, prefer fixing it; otherwise record a documented, dated ignore with a reason in a
  `.snyk` policy file so the exception is reviewable. The parent `pom.xml` overrides a few versions that Spring Boot
  manages, to pick up security fixes sooner (`jackson-2-bom.version`, `jackson-bom.version`, `logback.version`; each has
  a comment saying why). Remove an override once the Spring Boot parent manages a version at least that new. The Logback
  override is a minor version past the line Spring Boot supports, so re-run the tests and a startup check when changing it.

The CI pipeline runs the tests on pull requests. Publishing to Maven Central is done by maintainers only, from version
tags.
