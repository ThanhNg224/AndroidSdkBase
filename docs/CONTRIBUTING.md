# Contributing

## Before you open a PR

Run the gates in this order — each one is cheaper than the next, so stop at the first failure:

```bash
./gradlew check                    # unit tests, lint, and the additive-only ABI check for every module
./scripts/verify-publication.sh    # publishes to a local file repo, builds the external consumer, checks the metadata floor
```

`./gradlew check` also runs the zone guard as a side effect of project configuration (any build
does), so a module-boundary violation surfaces before tests even run. See `docs/ARCHITECTURE.md`
for what the guard enforces and `docs/COMPATIBILITY.md` for the ABI and metadata rules.

## API baselines travel with the change that causes them

If a change adds public API to a published module, run `./gradlew :<module>:apiDump` and commit the
regenerated `api/<module>.api` file **in the same commit** as the code change — never as a follow-up
commit, and never regenerated just to make a failing `apiCheck` pass without looking at why it
failed. `apiCheck` fails on a removed or changed signature by design; the fix is almost always to
stop removing/changing it, not to re-dump the baseline. Re-dumping is legitimate only when the
change is a genuine, reviewed, intentional API change (see `docs/COMPATIBILITY.md`).

## Tracked vs. local documentation

- **Tracked** (reference docs, committed): `README.md`, `docs/ARCHITECTURE.md`,
  `docs/COMPATIBILITY.md`, `docs/ERROR_CODES.md`, `docs/CONTRIBUTING.md`,
  `docs/EXAMPLE_VS_INFRASTRUCTURE.md`.
- **Local-only** (working docs, gitignored): everything under `docs/superpowers/` — plans, specs,
  and other in-flight working notes. These never reach the remote.

The test: a reference doc explains a rule the code enforces and is expected to stay accurate
indefinitely; a working doc is a snapshot of one task's planning and is disposable once that task
ships.

## What never gets committed

No secrets, no `local.properties` (it is machine-specific and gitignored — the Android SDK location
is picked up from it or from `ANDROID_HOME`, never hardcoded), no keystores or signing material, and
no generated output (`build/`, `.gradle/`, `api-compat/` scratch files). Signing a publication is
opt-in and keyless by default: `sdkbase.android.publishing.gradle.kts` only calls
`signAllPublications()` when an in-memory signing key is actually present in the environment, so a
plain `publishToMavenLocal` works on a machine with no keys configured at all.

## No Maven Central publishing is wired up here

This base publishes to a **local file repository only** — `scripts/verify-publication.sh` publishes
every artifact to `build/local-repo` and then builds `verification/consumer` against it. There is no
`nmcp` plugin, no Sonatype/Central Portal credentials, and no `close`/`release` task anywhere in this
repository. That is deliberate: a template should not carry one maintainer's publishing credentials
or assume everyone forking it wants the same release process.

A project derived from this base that wants to publish to Maven Central needs to add, on top of what
is already here:
1. A Central Portal (or OSSRH) account and namespace verification for the new `group`.
2. A publishing plugin/task that talks to Central — e.g. the `com.gradleup.nmcp` plugin, configured
   with the portal's username/token — added alongside the existing
   `com.vanniktech.maven-publish`/Dokka setup already in `sdkbase.android.publishing.gradle.kts`.
   `vanniktech/gradle-maven-publish-plugin` can also drive Central directly in newer versions; either
   approach is additive to the existing convention plugin, not a replacement for it.
3. A real signing key (GPG or in-memory) supplied via CI secrets, so `signAllPublications()` actually
   signs instead of silently skipping, as it does today.
4. CI credentials for whichever of the above is chosen, added as repository secrets — never
   committed.

Until that work is done, "publish" in this repository means "publish to `build/local-repo` and prove
an external consumer can resolve it," which is exactly what `verify-publication.sh` checks.

## Known, accepted build notice

`./gradlew check` (or any configuration) prints:

```
The Dependency Analysis plugin is only known to work with versions of AGP between 8.10.0 and 9.3.1. You are using 9.4.1. Proceed at your own risk.
```

This is expected on this toolchain (AGP `9.4.1`, see `docs/COMPATIBILITY.md`) and is not a build
failure. It has been observed not to affect the plugin's behavior on this project. Do not chase it,
downgrade AGP to silence it, or add suppression flags for it — if the dependency-analysis plugin
starts genuinely misbehaving, look at *that*, not at this notice.

## Testing policy: no UI unit tests

Do not write unit tests for Jetpack Compose layout/styling (`:sdk:capabilities:*-ui-compose`
modules) or other purely visual code. Tests **are** required, and expected in the same PR as the
change, for:
- state machines and other core logic (e.g. `OtpStateMachine`, `OtpEngine`, `OtpTimer`);
- validators and config classes (e.g. `OtpSdkConfig.Builder`);
- error mapping (e.g. `SdkErrorsTest`, and any new mapping from a host/gateway failure to an
  `SdkError`);
- anything backing a published facade's public contract (e.g. `OtpSdkRuntime`, `OtpSdk`).

If you are unsure which side of that line a change falls on, look at the existing test files next to
the equivalent OTP code for the pattern to follow.

### What this policy does not catch, and what to do about it

This rule has a cost, and it is worth stating instead of discovering it in production. A UI that
renders the wrong thing for a *correct* state is invisible to every test in this repository.

A real example from this repo's own history: `OtpScreen`'s status `when` block had branches for
`Requesting`/`Verifying`, for an error, and for the expiry countdown — but none for `Verified`. On
success the screen fell through to the countdown branch and displayed a frozen "Expires in 63s". The
engine was correct, all 43 unit tests passed, and the user was simply never told the flow had
succeeded. It was found in ten seconds by running the demo on a device, and could not have been found
any other way under this policy.

So: **run `apps/demo` on a real device or emulator before shipping a UI change**, and walk every
terminal state, not just the happy path you were working on. Success, failure, lockout and expiry all
need to look different from each other. If you add a phase to a state enum, grep the UI for the
`when` blocks that switch on it — an exhaustive `when` on the enum (rather than a `when {}` with
boolean branches) would have made the compiler catch this one, and is worth preferring in new code.
