#!/usr/bin/env python3
"""Check one real initializer scenario from a committed Git archive.

Default: rename/structure proof. --build adds real checks/builds in the same
throwaway clone. The source checkout, branch, index, and remote are never changed.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import subprocess
import sys
import tarfile
import tempfile
from io import BytesIO
from pathlib import Path


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def run(command: list[str], root: Path, env: dict[str, str] | None = None) -> None:
    print("RUN " + " ".join(command), flush=True)
    result = subprocess.run(command, cwd=root, env=env, capture_output=True, text=True)
    if result.returncode:
        detail = result.stdout[-4000:] + result.stderr[-1000:]
        raise RuntimeError(f"Command exited {result.returncode}: {' '.join(command)}\n{detail}")


def text(root: Path, path: str) -> str:
    return (root / path).read_text(encoding="utf-8")


def tree_digest(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if path.is_file() and ".git" not in relative.parts:
            digest.update(relative.as_posix().encode())
            digest.update(path.read_bytes())
    return digest.hexdigest()


def archive_revision(repo: Path, revision: str, clone: Path) -> None:
    result = subprocess.run(["git", "archive", "--format=tar", revision], cwd=repo, check=True, capture_output=True)
    with tarfile.open(fileobj=BytesIO(result.stdout), mode="r:") as bundle:
        bundle.extractall(clone, filter="data")


def initialize_fixture_git(clone: Path) -> None:
    # This fixture owns its Git config and has no remote or user hooks.
    env = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
    git = ["git", "-c", f"core.hooksPath={os.devnull}", "-c", "commit.gpgsign=false",
           "-c", "user.name=Initializer smoke", "-c", "user.email=initializer-smoke@example.invalid"]
    run([*git, "init", "-b", "main"], clone, env)
    run([*git, "add", "--force", "."], clone, env)
    run([*git, "commit", "-m", "Archive fixture"], clone, env)


def check_clone(clone: Path, build: bool) -> None:
    initialize_fixture_git(clone)
    run(["bash", "scripts/rename-project.sh", "--group", "org.example", "--namespace", "org.example.smokesdk",
         "--name", "SmokeSdk", "--developer-id", "smoke", "--developer-name", "Smoke SDK",
         "--developer-url", "https://example.invalid", "--repo-url", "https://example.invalid/smokesdk",
         "--skip-build-check"], clone)
    properties = text(clone, "gradle.properties")
    for value in ("sdkbase.group=org.example", "sdkbase.pom.developerId=smoke",
                  "sdkbase.pom.developerName=Smoke SDK", "sdkbase.pom.url=https://example.invalid/smokesdk"):
        require(value in properties, f"POM/coordinate identity missing: {value}")
    require("SmokeSdk" in text(clone, "settings.gradle.kts"), "Root project name is stale")
    require(text(clone, "NOTICE").startswith("SmokeSdk\n"), "NOTICE name is stale")
    root = clone / "sdk/core/src/main/kotlin/org/example/smokesdk/core"
    require((root / "call/SafeCall.kt").is_file(), "Renamed SDK source path is missing")
    require("package org.example.smokesdk.core.call" in (root / "call/SafeCall.kt").read_text(), "SDK package declaration is stale")
    require(not (clone / "sdk/core/src/main/kotlin/io/github/thanhng224/sdkbase").exists(), "Old SDK source directory remains")
    if build:
        run(["./gradlew", "spotlessApply", "apiDump"], clone)
        run(["./gradlew", "check", "-Psdkbase.warningsAsErrors=true"], clone)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--revision", default="HEAD")
    parser.add_argument("--build", action="store_true", help="Also verify/build the initialized temporary clone")
    args = parser.parse_args()
    try:
        with tempfile.TemporaryDirectory(prefix="androidsdkbase-init-smoke-") as temp:
            clone = Path(temp)
            archive_revision(args.repo.resolve(), args.revision, clone)
            check_clone(clone, args.build)
    except (OSError, RuntimeError, subprocess.CalledProcessError, tarfile.TarError) as error:
        print(f"FAIL initializer smoke: {error}", file=sys.stderr)
        return 1
    print("PASS archive initializer: rename" + (" + build/check" if args.build else " only (build skipped)"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
