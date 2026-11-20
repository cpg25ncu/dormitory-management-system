"""Prints the CHANGELOG.md section for a release, followed by how to get the image and JAR.

Usage: python3 scripts/release-notes.py v1.1.0
"""
import pathlib
import re
import sys

tag = sys.argv[1]
version = tag.removeprefix("v")
changelog = pathlib.Path(__file__).resolve().parent.parent.joinpath("CHANGELOG.md").read_text(encoding="utf-8")

match = re.search(rf"^## \[{re.escape(version)}\][^\n]*\n(.*?)(?=^## \[|^\[[^\]]+\]: |\Z)", changelog, re.S | re.M)
if not match:
    sys.exit(f"CHANGELOG.md has no section for {version}.")

print(match.group(1).strip())
print(f"""
## Get it

- Container image: `docker pull ghcr.io/cpg25ncu/dormitory-management-system:{version}`
- Executable JAR: `dormitory-management-system-{version}.jar` below, with `SHA256SUMS` to verify it
- Deployment: see [Deployment and operations](https://github.com/cpg25ncu/dormitory-management-system/blob/{tag}/docs/deployment.md)""")
