---
name: coverage-audit
description: Inspects JaCoCo XML reports against mandatory 90% branch and line coverage gates, pinpointing classes and missed source lines.
---

# JaCoCo Coverage Audit Skill

Use this skill when verifying test coverage compliance against the repository's mandatory `>= 90%` line and branch coverage gates.

## Capabilities

1. **Repository-Wide Coverage Scan**: Inspects `target/site/jacoco/jacoco.xml` to check instruction and branch coverage percentages.
2. **Class & Source File Inspection**: Pinpoints exact classes and missed branch counts.

## Usage

Check repository-wide branch & instruction coverage against the 90% threshold:
```bash
python3 scripts/check_coverage.py
```

Inspect missed branches for a specific Java file:
```bash
python3 scripts/check_coverage.py --file FreetradeCsvParser.java
```
