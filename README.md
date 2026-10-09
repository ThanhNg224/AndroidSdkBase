# AndroidSdkBase

An Android SDK starter with enforced module boundaries and a manual demo host.

## Prerequisites

Use Python 3.12+, JDK 17, and Android SDK tools. The checked-in wrapper/catalog currently select Gradle 9.7.1, AGP 9.4.1, and KGP 2.4.20. Consumer support contracts remain in [Compatibility](docs/COMPATIBILITY.md).

## Initialize

Clone into your SDK directory and run on a clean working tree:

```bash
./scripts/rename-project.sh --group com.acme --namespace com.acme.paykit --name PayKit --developer-id acme --developer-name "Acme Inc." --developer-url https://acme.com --repo-url https://github.com/acme/paykit
```

The script updates identity, formats the project, and records renamed API baselines. `--skip-build-check` explicitly omits the Gradle formatting/API dump steps; it is used by the rename-only smoke and does not establish build readiness. Follow the printed verification steps and review the diff.

## Prepare

Open and sync the renamed project in Android Studio. Follow [Recipes](docs/RECIPES.md) to add features or remove the OTP/remote-config examples. The registry and module map live in [AGENTS.md](AGENTS.md) and [Architecture](docs/ARCHITECTURE.md).

## Verify

Choose the smallest risk level in [Verification](docs/VERIFICATION.md). The existing full CI jobs, public API/error/changelog requirements, compiler profiles, guard proofs, and consumer gates remain in [Recipes](docs/RECIPES.md) and [Compatibility](docs/COMPATIBILITY.md).

## Run the demo

Build `:apps:demo:assembleDebug` and launch AndroidSdkBase Demo on an Android device or emulator.
The host has Features, UI and Settings tabs. Features opens the OTP example in a separate screen;
use code `123456`, and go back to end the session. UI demonstrates the same components used by
the host. Settings saves the system/light/dark theme and system/Vietnamese/English language for
both the host and OTP screen. The host UI is internal to `apps/demo`; see [Theming](docs/THEMING.md#demo-host-ui)
to add another screen. Build/check evidence is separate from a successful device session.

## Further reading

- [Agent workflow](AGENTS.md) and [Git workflow](docs/GIT_FLOW.md)
- [Architecture](docs/ARCHITECTURE.md), [Recipes](docs/RECIPES.md), [Compatibility](docs/COMPATIBILITY.md)
- [Theming](docs/THEMING.md), [Logging](docs/LOGGING.md), [Error codes](docs/ERROR_CODE_REFERENCE.md)

Publication remains local-only (`build/local-repo`); Maven Central is not configured for this base. Versioning follows the existing public API/changelog policy. Licensed under [Apache 2.0](LICENSE).
