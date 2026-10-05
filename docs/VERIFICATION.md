# Verification

Choose the smallest level covering the change. Escalate when scope or uncertainty requires it.

| Level | Change | Evidence |
|---|---|---|
| 0 | Documentation, agent rules, ignore patterns, attributes | Diff, affected links/claims, ignore and attribute probes; no build |
| 1 | Local implementation without public-contract/build changes | Focused formatting/static checks and relevant existing tests |
| 2 | State, public contract, build, or dependency changes | Project gates plus applicable API/changelog steps |
| 3 | Initializer, scaffolds, validation scripts, or CI | Tooling tests, archive smoke, and existing gates affected by the change |

A level is a scope decision, not a requirement to run every listed command. Do not add tests for styling alone or repeat broad gates without a new failure or source change.

## Commands

| Purpose | Command |
|---|---|
| Focused module gate | `./gradlew :spotlessCheck :<module>:check` (replace `<module>` with the affected Gradle project path) |
| Project gate | `./gradlew check -Psdkbase.warningsAsErrors=true` |
| Format explicitly | `./gradlew spotlessApply` (rewrites Kotlin/build files) |
| Python tooling tests | `python3 -B -m unittest discover -s scripts -p 'test_*.py' --verbose` |

Public API, error-code, UI-string, build/dependency/topology, consumer rules, and API-baseline changes require the project gate and relevant API/error/changelog steps. Publishing/POM, Kotlin floor, consumer compatibility, guard changes, or uncertainty additionally require the existing publication (`--current` as applicable), integration, and/or guard scripts relevant to the affected contract. API/error catalog/changelog steps remain in [Recipes](RECIPES.md); compiler floor and publication/consumer commands remain in [Compatibility](COMPATIBILITY.md). Do not weaken those gates. `spotlessCheck` is part of `check`; formatting covers `sdk/`, `apps/`, `build-logic/src/`, and recursive Gradle Kotlin files, excluding the separate `verification/` build. This base remains Android-only, without KMP.

## Archive initializer smoke

Use Python 3.12+ for archive tooling.

`python3 scripts/smoke_init_project.py` reads committed `HEAD` via `git archive` and checks initialization in temporary copies. Use `--repo <path> --revision <ref>` to check another committed input. Uncommitted edits are not archive proof; validate intended edits in an isolated snapshot repository before committing to the source repository.

One rename updates SDK group, namespace, name, POM identity, and source paths. Dry-run content checks run where the initializer supports dry-run. No fake Gradle launcher is used.

Every PR runs this rename smoke without a native build. `python3 scripts/smoke_init_project.py --build` adds one real build/check in the temporary clone. It is also available through the existing CI workflow's manual `initializer_build` input, disabled by default. Existing CI jobs remain unchanged in name and coverage.

## Hygiene checks and proof boundaries

For level 0, run `git diff --check`, resolve changed document links, and probe relevant patterns with `git -c core.excludesFile=/dev/null check-ignore --no-index -v <path>` and `git check-attr text eol -- <path>`. Ignore probes may return no match for intentionally trackable templates.

A successful rename smoke proves the asserted identities and structure only. Build/check mode additionally proves the listed compiler/build tasks, not installation or runtime/device behavior. Local success does not prove remote CI or branch-protection enforcement. Report exact commands, results, and skipped gates with their reason.
