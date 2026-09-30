#!/usr/bin/env python3
"""Render a build file for scripts/new-module.sh."""

from pathlib import Path
import re
import sys


def main() -> None:
    if len(sys.argv) not in {6, 7}:
        raise SystemExit("usage: render-module.py <zone> <module-dir> <name> <namespace> <project-name> [owner]")
    zone, module_dir, name, namespace, project_name = sys.argv[1:6]
    owner = sys.argv[6] if len(sys.argv) == 7 else ""
    template = Path("scripts/templates/module") / zone / "build.gradle.kts"
    source_name = owner if zone == "ui" else name
    pascal = "".join(segment.capitalize() for segment in source_name.split("-"))
    values = {
        "name": name,
        "namespace": namespace,
        "owner": owner,
        "pascal": pascal,
        "project_name": project_name,
    }
    content = re.sub(r"\{\{([a-z_]+)\}\}", lambda match: values[match.group(1)], template.read_text())
    module_path = Path(module_dir)
    build_file = module_path / "build.gradle.kts"
    build_file.parent.mkdir(parents=True, exist_ok=True)
    build_file.write_text(content)

    for source_template in template.parent.glob("*.kt"):
        source_name = source_template.name.replace("{pascal}", pascal)
        package_path = Path(*namespace.split("."))
        source = module_path / "src/main/kotlin" / package_path / source_name
        source.parent.mkdir(parents=True, exist_ok=True)
        source_content = re.sub(
            r"\{\{([a-z_]+)\}\}", lambda match: values[match.group(1)], source_template.read_text()
        )
        source.write_text(source_content)


if __name__ == "__main__":
    main()
