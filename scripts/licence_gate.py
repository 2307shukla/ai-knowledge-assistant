#!/usr/bin/env python3
"""Licence gate for the runtime SBOM (target/sbom.json, written by cyclonedx-maven-plugin).

Policy (see README > Licences):
  * allowed: MIT, Apache-2.0, BSD family
  * approved exception: Jakarta APIs (EPL-2.0, often dual-licensed with GPL-2.0 + Classpath Exception)
  * anything copyleft (GPL, LGPL, AGPL, MPL, EPL outside Jakarta, CDDL) fails the build
  * anything else is listed as "needs review" in the job summary but does not fail
"""
import json
import os
import re
import sys

ALLOWED = re.compile(r"^(MIT|Apache-2\.0|BSD-[23]-Clause|0BSD|BSD)$", re.I)
ALLOWED_NAMES = re.compile(r"(apache license|apache software license|\bmit\b|\bbsd\b|eclipse distribution license)", re.I)
COPYLEFT = re.compile(r"(GPL|LGPL|AGPL|MPL|Mozilla|EPL|Eclipse Public|CDDL)", re.I)


def licences(component):
    out = []
    for entry in component.get("licenses", []):
        lic = entry.get("license", {})
        if "id" in lic:
            out.append(lic["id"])
        elif "name" in lic:
            out.append(lic["name"])
        elif "expression" in entry:
            out.append(entry["expression"])
    return out


def classify(group, names):
    if not names:
        return "review"
    # Dual-licensed: acceptable if any one choice is allowed.
    if any(ALLOWED.match(n) or ALLOWED_NAMES.search(n) for n in names):
        return "ok"
    if group.startswith("jakarta.") and any(re.search(r"EPL", n, re.I) for n in names):
        return "ok-approved"
    if any(COPYLEFT.search(n) for n in names):
        return "banned"
    return "review"


def main(path):
    with open(path, encoding="utf-8") as f:
        bom = json.load(f)
    rows = {"ok": [], "ok-approved": [], "review": [], "banned": []}
    for c in bom.get("components", []):
        group = c.get("group", "")
        names = licences(c)
        rows[classify(group, names)].append(f"{group}:{c.get('name')}:{c.get('version')} [{' | '.join(names) or 'none declared'}]")

    report = [f"Runtime components: {sum(len(v) for v in rows.values())}",
              f"Allowed (MIT/Apache/BSD): {len(rows['ok'])}",
              f"Approved exception (Jakarta EPL-2.0): {len(rows['ok-approved'])}"]
    for title, key in (("Approved exceptions", "ok-approved"), ("Needs review", "review"), ("BANNED", "banned")):
        if rows[key]:
            report.append(f"\n{title}:")
            report.extend("  " + r for r in sorted(rows[key]))
    text = "\n".join(report)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("## Licence gate\n```\n" + text + "\n```\n")
    return 1 if rows["banned"] else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "target/sbom.json"))
