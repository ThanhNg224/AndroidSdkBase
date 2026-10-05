# Change recipes

Use these as task checklists alongside [AGENTS.md](../AGENTS.md) and
[the architecture rules](ARCHITECTURE.md). CI runs all gates on every PR; local tiers only select the
checks needed before reporting work done. Replace `<name>` and `<module>` with the feature name and
Gradle project path. The base is Android-only; it does not target Kotlin Multiplatform.

## Add a feature

- **When:** adding a new independent capability.
- **Files:** `sdk/features/<name>/` and `gradle/module-topology.gradle.kts`; Settings includes registered
  modules whose build files exist, and the scaffold records the initial ABI file.
- **Commands:**

  ```bash
  ./scripts/new-feature.sh <name>
  ```

  Add the optional Compose UI with `./scripts/new-module.sh --zone ui <name>` after the feature exists.
  The UI module uses the feature's public API and `core-ui-compose`; implement UI and localized
  resources in that module.

  Replace the generated gateway stubs, config validation, error catalog, session interface/state,
  and runtime; test business behavior. After public edits, update the ABI baseline and run the root
  strict check:

  ```bash
  ./gradlew :sdk:features:<name>:apiDump
  ./gradlew check -Psdkbase.warningsAsErrors=true
  ```

  Follow “Building a feature” in `docs/ARCHITECTURE.md`.
- **Tier:** 2 (new module/topology and public API).
- **If skipped:** the module is unregistered or crosses a zone, its ABI may drift, and generated stubs
  can remain as the feature's behavior.

## Add an operation to a session

- **When:** adding a new action or state transition to an existing feature.
- **Files:** `session/*State.kt`, `session/*Session.kt`, `internal/*SdkRuntime.kt`, engine/gateway
  implementation as needed, and focused business-logic tests.
  Add the suspend operation and its Java callback twin; implement them with the shared
  `scope.ifOpen` and `scope.call` rather than creating another coroutine scope.
- **Commands:**

  ```bash
  ./gradlew :spotlessCheck :sdk:features:<name>:check
  ```

  If the session interface changes, run `./gradlew :sdk:features:<name>:apiDump` first, then
  `./gradlew check -Psdkbase.warningsAsErrors=true` and complete the
  [public API checklist](#public-api-change-checklist).
  A new public state or value class with hand-written `equals`/`hashCode` adds a test using
  `assertValueSemantics` with one differing instance per property.
- **Verification:** level 1 for implementation only; level 2 for a public signature change.
- **If skipped:** state transitions may race or survive session close, Java callers may lack a
  callback twin, and public ABI changes remain unreviewed.

## Add an error code

- **When:** introducing a new failure the host or UI must distinguish.
- **Files:** the owning `*Errors.kt`, `docs/ERROR_CODE_REFERENCE.md`, generated
  `sdk/error-codes.ledger`, and `CHANGELOG.md`.
- **Commands:**

  ```bash
  ./gradlew errorCatalogDump
  ./gradlew :<module>:apiDump
  ./gradlew check -Psdkbase.warningsAsErrors=true
  ```

  The error constant is public: after changing it, dump that module's ABI before running the root
  check. Before API freeze, codes may be renumbered or removed by updating the constant, ledger, and
  reference together. After freeze, codes are append-only; mark a removed code `retired` instead.
- **Tier:** 2.
- **If skipped:** `checkErrorCatalog` fails for an unrecorded/changed code, or consumers see an
  undocumented code/disposition.

## Add or change a gateway method

- **When:** the host must provide another operation the SDK consumes.
- **Files:** the gateway interface in `gateway/`, the Java-friendly callback gateway/adapter where
  applicable, its suspend bridge, and gateway contract tests.
- **Commands:**

  ```bash
  ./gradlew :sdk:features:<name>:apiDump
  ./gradlew check -Psdkbase.warningsAsErrors=true
  ```

  The gateway lives in the feature's config; a Java callback form is a `Builder` constructor overload
  that calls `asGateway()`, not another entry-point overload.

  Test `GatewayContract` for suspend gateways and `assertCallsBackOnce` or
  `assertCompletesOnce` for callback gateways. A new abstract method on a host-implemented interface
  is a breaking API change; the pre-release policy allows changing it directly. Record the API diff
  and changelog entry. Route host calls through `safeCall`.
- **Verification:** level 2; apply the additional contract gates in [Verification](VERIFICATION.md) for dependency, publishing, or consumer compatibility changes.
- **If skipped:** host implementations may no longer compile, callback and suspend contracts may
  diverge, or exceptions can escape the SDK boundary.

## Add a UI module or string

- **When:** adding optional UI for a feature or user-visible copy.
- **Files:** `<feature>-ui-compose` sources/resources and module registration; add localized resource
  entries where the module already provides translations. Keep strings resource-backed and namespaced
  (for example `sdk_otp_ui_compose_verify`). UI modules may use `core-ui-compose`; they must not use
  colour literals.
- **Commands:**

  Generate a Compose UI module with:

  ```bash
  ./scripts/new-module.sh --zone ui <feature-name>
  ./gradlew check -Psdkbase.warningsAsErrors=true
  ```

  The script registers the module in the feature zone and writes its ABI baseline. A public string
  resource change also requires a `CHANGELOG.md` entry.
- **Tier:** 2.
- **If skipped:** the UI zone guard or source rule fails, a string may be missing for a locale, or a
  published resource name changes without being recorded.

## Add a one-shot feature (no session)

- **When:** a request returns a result once, with no ongoing state, commands or subscriptions. Use
  the OTP/session shape when callers need a state stream or session-owned background work.
- **Example:** `sdk/features/remote-config` fetches an immutable revision/value snapshot. Its config
  validates a positive gateway timeout; a blank response revision returns business error 3301.
- **Steps:** scaffold with `./scripts/new-feature.sh <name>`, then remove the session/state/runtime
  stubs and their tests. Keep the host gateway and environment in one config with `validateConfig`.
  Put suspend host calls through `safeCall`; implement the Java gateway with `awaitCallback` and
  a builder constructor overload. Implement the outbound Java twin with `launchCallback` returning
  `Cancellable`; cancellation suppresses delivery. Do not create a feature-owned coroutine scope.
- **Verify:** dump the new API at the end, register any business error and reference row, add the
  changelog entry, and run the applicable publication/integration gates in [Verification](VERIFICATION.md). Add Java/Kotlin coordinate usage to the headless consumer so both
  floor/current publication modes compile and execute the new API.

## Add a composition, adapter, or vendor binary

- **When:** multiple features need orchestration, a host opts into a concrete library bridge, or a
  vendor supplies a local `.jar`/`.aar` without a Maven coordinate.
- **Files:** new module/build file and `gradle/module-topology.gradle.kts`. Settings derives project
  inclusion from the registry; explicit includes remain available to isolated guard fixtures. A vendor
  module is never published and only an adapter may depend on it. The adapter is the only SDK zone
  allowed HTTP/DI; no other SDK module depends on an adapter. Keep cross-feature calls in composition.
- **Commands:**

  ```bash
  ./scripts/new-module.sh --zone composition <name>
  ./scripts/new-module.sh --zone adapter <name>
  ./gradlew check -Psdkbase.warningsAsErrors=true
  ```

  Both module scaffolds add the module to its zone and `publishedArtifacts`, then dump the ABI baseline.
  Keep an adapter's dependency graph publishable before including it as a Maven artifact.

  Scaffold a local binary wrapper and its host-owned adapter without adding either to the published list:

  ```bash
  ./scripts/new-module.sh --zone vendor <vendor-name>
  ./scripts/new-module.sh --zone adapter --unpublished <adapter-name>
  ```

  Drop `.jar`/`.aar` files into the vendor's `libs/` directory (see its generated README), then add
  `implementation(project(":sdk:vendor:<vendor-name>"))` to the adapter. Both modules apply only
  `sdkbase.android.library`, have no ABI/publication plugin, and skip `apiDump`.
  `--unpublished` is accepted only for adapters; vendor modules are always unpublished.

  For composition/adapter integration, run the existing `./scripts/verify-integration.sh`. If a published
  POM or Kotlin floor is affected, also run both publication modes:

  ```bash
  ./scripts/verify-publication.sh
  ./scripts/verify-publication.sh --current
  ```
- **Verification:** level 2 for topology/build changes; apply additional contract gates when integration, publication, POM, dependency
  resolution, or consumer behavior is affected.
- **If skipped:** the zone guard can reject the module, an optional adapter/vendor can leak into
  consumers, or a coordinate-only consumer can fail to resolve it.

## Public API change checklist

1. Run `./gradlew :<module>:apiDump` after edits; review the `.api` diff.
2. Add the required `CHANGELOG.md` entry and check `docs/COMPATIBILITY.md` versioning.
3. For error codes, update the constant, ledger, and reference together.
4. Apply the pre-release breaking-change rule in `AGENTS.md`; do not add compatibility shims.
5. Run the level 2 project gate in [Verification](VERIFICATION.md) and report the commands that passed.

## Remove the examples

Delete `sdk/features/otp*`, `sdk/features/remote-config` and the OTP-dependent
`sdk/adapters/otp-fake-sms`, their entries in `gradle/module-topology.gradle.kts`, and the
demo/consumer code and coordinate dependencies that use them. Remove their error ledger/reference rows and API baselines
as permitted by the pre-release policy. Keep in `sdk/core` only the gateway-agnostic parts you still
need — `SdkResult`, `SdkError`/`SdkErrors`, `SdkLogger`.
