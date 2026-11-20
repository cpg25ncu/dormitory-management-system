"""Prints a Markdown table of line, branch and instruction coverage from a JaCoCo CSV report.

Usage: python3 scripts/coverage-summary.py [target/site/jacoco/jacoco.csv]
"""
import csv
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "target/site/jacoco/jacoco.csv"
with open(path, newline="", encoding="utf-8") as report:
    rows = list(csv.DictReader(report))

print("### Test coverage")
print()
print("| Counter | Covered | Total | Coverage |")
print("| --- | ---: | ---: | ---: |")
for counter in ("LINE", "BRANCH", "INSTRUCTION"):
    covered = sum(int(row[counter + "_COVERED"]) for row in rows)
    total = covered + sum(int(row[counter + "_MISSED"]) for row in rows)
    print(f"| {counter.title()} | {covered} | {total} | {100 * covered / total:.1f}% |")
