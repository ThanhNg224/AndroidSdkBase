#!/usr/bin/env python3
"""Add a module to the shared topology registry."""

from pathlib import Path
import re
import sys


MAX_LINE = 120


def append_to_list(text: str, header: str, module: str) -> str:
    match = re.search(re.escape(header) + r"listOf(?:<String>)?\(", text)
    if match is None:
        raise SystemExit(f"register-module: no list after {header.strip()!r}")
    depth = 1
    index = match.end()
    while depth:
        depth += {"(": 1, ")": -1}.get(text[index], 0)
        index += 1
    close = index - 1
    inner = text[match.end() : close]
    quoted = f'"{module}"'
    if quoted in inner:
        raise SystemExit(f"register-module: {module} is already registered")
    if not inner.strip():
        return text[: match.start()] + f"{header}listOf({quoted})" + text[close + 1 :]

    if "\n" in inner:
        indentation = re.search(r"\n( *)\S", inner)
        indent = indentation.group(1) if indentation else "        "
        head = text[:close].rstrip()
        return head + f"\n{indent}{quoted}," + text[len(head) :]

    # A single-line list: keep it on one line while it fits the formatter's column limit, otherwise
    # expand to one element per line so the formatter gate stays green right after scaffolding.
    line_start = text.rfind("\n", 0, match.start()) + 1
    line_end = text.find("\n", close)
    line_end = len(text) if line_end == -1 else line_end
    appended = text[:close] + f", {quoted}" + text[close:]
    line = appended[line_start : line_end + len(appended) - len(text)]
    if len(line) <= MAX_LINE:
        return appended
    base = re.match(r" *", text[line_start:]).group(0)
    items = re.findall(r'"[^"]*"', inner) + [quoted]
    body = "".join(f"\n{base}    {item}," for item in items)
    return text[: match.end()] + body + f"\n{base}" + text[close:]


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("usage: register-module.py <zone> <module-path> <published|unpublished>")
    zone, module, publication = sys.argv[1:]
    if publication not in {"published", "unpublished"}:
        raise SystemExit("publication must be published or unpublished")
    path = Path("gradle/module-topology.gradle.kts")
    text = path.read_text()
    text = append_to_list(text, f'"{zone}" to ', module)
    if publication == "published":
        text = append_to_list(text, 'extra["publishedArtifacts"] = ', module)
    path.write_text(text)


if __name__ == "__main__":
    main()
