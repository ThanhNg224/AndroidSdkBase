#!/usr/bin/env python3
"""Render the starter files for scripts/new-feature.sh."""

from pathlib import Path
import re
import sys


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("usage: render-feature.py <name> <core-namespace> <project-name>")

    name, namespace, project_name = sys.argv[1:]
    segments = name.split("-")
    pascal = "".join(segment.capitalize() for segment in segments)
    package = f"{namespace}.{''.join(segments)}"
    module_dir = Path("sdk/features") / name
    package_path = Path(*package.split("."))
    main_dir = module_dir / "src/main/kotlin" / package_path
    test_dir = module_dir / "src/test/kotlin" / package_path
    values = {
        "name": name,
        "ns": namespace,
        "project_name": project_name,
        "pascal": pascal,
        "pkg": package,
    }
    template_dir = Path("scripts/templates/feature")
    # Template file name (with {pascal} expanded) -> directory it is written to.
    routes = {
        "build.gradle.kts": module_dir,
        f"{pascal}Sdk.kt": main_dir,
        f"{pascal}Errors.kt": main_dir,
        f"{pascal}Session.kt": main_dir / "session",
        f"{pascal}State.kt": main_dir / "session",
        f"{pascal}Gateway.kt": main_dir / "gateway",
        f"{pascal}SdkConfig.kt": main_dir / "config",
        f"{pascal}SdkRuntime.kt": main_dir / "internal",
        f"{pascal}SdkTest.kt": test_dir,
        f"{pascal}SdkConfigTest.kt": test_dir / "config",
    }

    for template in sorted(template_dir.iterdir()):
        if not template.is_file():
            continue
        output_name = template.name.replace("{pascal}", pascal)
        if output_name not in routes:
            raise SystemExit(f"render-feature: no destination for template {template.name}")
        output = routes[output_name] / output_name

        content = template.read_text()
        content = re.sub(r"\{\{([a-z_]+)\}\}", lambda match: values[match.group(1)], content)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(content)


if __name__ == "__main__":
    main()
