---
id: task-1
title: Programmatic rule evaluation independent of the REST gate
status: Done
assignee: []
labels:
  - feature
  - library
dependencies: []
priority: medium
---

## Description

Let application code load a named DMN rule file (`.dmn`) and evaluate it directly with a map of input data, without
going through the REST controller. Today the only supported entry point is `RulesController`; `RulesEngine` exists as a
bean but is not a documented API, and it is created only inside `BusinessRulesAutoConfiguration`, which is switched off
entirely by `spring-boot-business-rules.enabled=false` and requires a servlet web application
(`@ConditionalOnWebApplication`).

The REST endpoint must stay gated (opt-out via `enabled`, protection by Spring Security, input limits, etc.). Turning
the gate off or closing it must **not** remove the ability to evaluate rules in-process. The gate controls who can call
rules over HTTP, not whether the application itself can use them.

## Requirements

- Provide a small public, documented API to evaluate a model by name (e.g. `rules.evaluate("customer-rules", inputs)`
  and a decision-specific variant), returning the existing `RulesEngine.Evaluation` result or an equivalent type.
- Resolve the model by its DMN model name (same lookup as `findModel`/`findModels`); fail with a clear exception when
  the model or decision is unknown, is ambiguous, or the inputs include undeclared keys (keep the existing
  `unknownInputs` protection so a caller cannot override a decision's value).
- Keep the REST controller, response-headers filter, instructions route and unprotected-endpoint warning gated as they
  are today.
- Separate the engine beans from the web beans so that:
  - `RulesEngine` (and the new API) is registered whenever the library is on the classpath, including non-web
    applications, and when the REST endpoint is disabled;
  - the controller beans are registered only when the web conditions hold and the endpoint is enabled.
- Decide how to express the two gates in configuration (decided: see "Rename the web switch" below). Document the
  behaviour change, since `enabled=false` previously meant "nothing is loaded".
- Rules are still loaded from `rules-location` and still fail fast at startup if a model does not compile.
- **One-off rules are disallowed** (decided). Only rules loaded from `rules-location` at startup may be evaluated. No
  public way to load or run an ad hoc `.dmn` file, `Resource` or string: the `RulesEngine` constructor is not public,
  evaluation is by model name, and a `DMNModel` this engine did not load is refused. Adding or changing a rule means
  editing a `.dmn` under `rules-location` and restarting.

## Acceptance criteria

- [x] Application code can inject the evaluation API and evaluate a named model with a `Map<String, Object>` of inputs
- [x] Works in a non-web Spring Boot application (no servlet)
- [x] With the REST endpoint disabled, no controller, filter or instructions beans exist, but programmatic evaluation
      still works
- [x] With the REST endpoint enabled, behaviour and responses are unchanged (existing tests pass)
- [x] Unknown model, unknown decision and undeclared input keys give clear, tested errors
- [x] Auto-configuration tests cover: web + enabled, web + REST disabled, and non-web
- [x] README documents programmatic use with a code example, the gating semantics and any new property; the
      configuration metadata test still passes for new properties
- [x] `example/` shows a short programmatic call (e.g. a test or runner) using `customer-rules.dmn`
- [x] Checkstyle, SpotBugs and `-Werror` lint pass; Javadoc is complete on the new public API

## Diagram of how rule data is called and used

Add a flow diagram (or similar graph) that shows how the data in a business rule is created, loaded, called and
consumed, so a new reader can see both ways in at a glance.

- Show the path from a `.dmn` file under `rules/`, through startup loading (and fail-fast validation), to the
  `RulesEngine` bean.
- Show the two entry points side by side: application code calling `RulesEngine` directly, and an HTTP client calling
  the REST endpoint. Mark the gate (`enabled`, servlet web app, Spring Security, request limits) as applying to the HTTP
  path only.
- Show what comes back (`Evaluation`: result, decisions, messages, failed) and how each path reports it (return value
  versus `200`/`400`/`422` JSON).
- Store it as `docs/images/rule-evaluation-flow.svg` so it renders on GitHub and on the built docs site, with a title
  and description for accessibility, and embed it in the README.

Acceptance criteria for this piece:

- [x] Diagram file exists in `docs/images/` and is valid SVG with readable text in light and dark viewers
- [x] README embeds it with descriptive alt text next to the "Evaluating from Java" section
- [x] The diagram matches the real behaviour (names, property and exception names agree with the code)

## Rename the web switch

`spring-boot-business-rules.enabled` now turns only the web side on and off, so its name is misleading. Give it a name
that says so, and update everything that mentions it.

- Rename it to `spring-boot-business-rules.web.enabled` (a nested `Web` record in `BusinessRulesProperties`, like
  `limits`). Default stays `true`.
- The old name must not be silently ignored: someone who set `enabled=false` to close the endpoint would find it open.
  Fail startup with a message that names the new property instead.
- Update every place the name appears: `BusinessRulesProperties` and auto-configuration Javadoc, the startup warning
  text, the `GET` help route's `configuration` output, `docs/openapi.yaml`, the README (property table, "Securing the
  endpoint", "Evaluating from Java"), `docs/images/rule-evaluation-flow.svg`, tests and the example tests.

Acceptance criteria for this piece:

- [x] The property is `spring-boot-business-rules.web.enabled` and appears in the generated configuration metadata
- [x] Setting the old `spring-boot-business-rules.enabled` stops startup with a message that says to rename it
- [x] No doc, diagram, Javadoc, log message, OpenAPI file or test refers to the old name, except where explaining the rename

## Disallow one-off rules

Programmatic evaluation must not become a way to run rules that were never reviewed and deployed with the application.

- Remove the public `RulesEngine(Collection<Resource>)` constructor (package-private; the auto-configuration is the only
  builder).
- Make the public evaluate methods reject a `DMNModel` that the engine did not load (`IllegalArgumentException`).
- Document it in the README ("Evaluating from Java" and the FAQ), the `RulesEngine` Javadoc and the flow diagram.

Acceptance criteria for this piece:

- [x] No public constructor or other public API loads a `.dmn` file, `Resource` or string
- [x] A model from another engine is refused by both `evaluate(DMNModel, ...)` overloads, with tests
- [x] README, Javadoc and `docs/images/rule-evaluation-flow.svg` say that only rules loaded at startup can run

## Make the web side opt-in

`spring-boot-business-rules.web.enabled` defaults to `false`, so the REST endpoint is opt-IN. Adding the library gives
you the rules engine for Java code only; nothing is exposed over HTTP until the application sets `web.enabled=true`.
This is the safer default for an endpoint that has no authentication of its own.

- Change the default from `true` to `false` (property record, configuration metadata, auto-configuration condition no
  longer matches when the property is missing).
- The example app exposes the property in `application.yml` with a comment that it is optional and defaults to `false`,
  and sets it to `true` so the example serves HTTP.
- Update the README (property table, "Using the artifact", "Securing the endpoint", "Running the example API"),
  `docs/openapi.yaml`, the startup warning wording and the flow diagram: the gate now starts with "opt in".
- Tests: default has no web beans; `true` has them; the engine works either way; existing web tests opt in explicitly.

Acceptance criteria for this piece:

- [x] With no configuration, no controller, filter or help route exists and the engine still evaluates
- [x] With `spring-boot-business-rules.web.enabled=true`, the web beans exist and behave as before
- [x] The example `application.yml` shows the property, a comment saying it is optional and defaults to `false`
- [x] README, OpenAPI file, startup warning, Javadoc and diagram describe the web side as opt-in

## Notes

- Relevant code: `RulesEngine`, `BusinessRulesAutoConfiguration`, `BusinessRulesProperties`, `RulesController`.
- Security: programmatic callers bypass HTTP limits (body size, depth, etc.), so decide whether the same limits apply
  in-process or are documented as the caller's responsibility.
  - These are to be documented as the caller's responsibility.
