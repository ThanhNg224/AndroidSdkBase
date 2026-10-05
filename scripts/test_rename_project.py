from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

def git_fixture(root: Path) -> None:
    env = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
    git = ["git", "-c", f"core.hooksPath={os.devnull}", "-c", "commit.gpgsign=false",
           "-c", "user.name=Tooling test", "-c", "user.email=tooling@example.invalid"]
    for args in (["init", "-b", "main"], ["add", "--force", "."], ["commit", "-m", "Fixture"]):
        subprocess.run([*git, *args], cwd=root, env=env, check=True, capture_output=True)


def digest(root: Path) -> str:
    value = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        if path.is_file() and ".git" not in path.relative_to(root).parts:
            value.update(path.relative_to(root).as_posix().encode())
            value.update(path.read_bytes())
    return value.hexdigest()


class TestSdkRename(unittest.TestCase):
    def fixture(self, root: Path) -> list[str]:
        scripts = root / "scripts"
        scripts.mkdir()
        shutil.copy2(Path(__file__).with_name("rename-project.sh"), scripts / "rename-project.sh")
        (root / "gradle.properties").write_text("sdkbase.group=io.github.thanhng224\nsdkbase.pom.url=old\nsdkbase.pom.developerId=old\nsdkbase.pom.developerName=old\nsdkbase.pom.developerUrl=old\nsdkbase.pom.inceptionYear=2020\n")
        (root / "NOTICE").write_text("AndroidSdkBase\nCopyright old\nLicense paragraph\n")
        (root / "settings.gradle.kts").write_text('rootProject.name = "AndroidSdkBase"\n')
        source = root / "sdk/core/src/main/kotlin/io/github/thanhng224/sdkbase/core"
        source.mkdir(parents=True)
        (source / "Value.kt").write_text("package io.github.thanhng224.sdkbase.core\n")
        git_fixture(root)
        return ["bash", "scripts/rename-project.sh", "--group", "org.example", "--namespace", "org.example.sdk",
                "--name", "ExampleSdk", "--developer-id", "example", "--developer-name", "Example SDK",
                "--developer-url", "https://example.invalid", "--repo-url", "https://example.invalid/sdk"]

    def test_explicit_skip_renames_without_a_gradle_wrapper(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            command = self.fixture(root)
            result = subprocess.run([*command, "--skip-build-check"], cwd=root, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("Gradle formatting/API dump skipped", result.stdout)
            self.assertTrue((root / "sdk/core/src/main/kotlin/org/example/sdk/core/Value.kt").is_file())
            self.assertIn("sdkbase.group=org.example", (root / "gradle.properties").read_text())

    def test_dirty_tree_is_rejected_before_rewriting(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            command = self.fixture(root)
            with (root / "NOTICE").open("a") as handle:
                handle.write("Unrelated work\n")
            before = digest(root)
            result = subprocess.run([*command, "--skip-build-check"], cwd=root, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("dirty tree", result.stderr)
            self.assertEqual(digest(root), before)

    def test_default_still_requires_real_gradle_steps(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            command = self.fixture(root)
            result = subprocess.run(command, cwd=root, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Formatting the renamed project", result.stdout)
            self.assertIn("gradlew", result.stderr)


if __name__ == "__main__":
    unittest.main()
