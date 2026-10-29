#!/usr/bin/env python3
"""
Release checks. CI runs them after the release build; run them locally the same way:

    ./gradlew :app:assembleRelease -Punsigned && python3 scripts/checks.py

Fails when:
  - a merged release manifest asks for a permission that is not in docs/permissions.txt;
  - a tracked text file contains a long or medium dash, a personal path, or text about who or what
    wrote the code (authorship trailers, generator credits);
  - the latest review in docs/security/ is more than 6 months old;
  - CHANGELOG.md or the store changelog has no entry for the version in app/build.gradle.kts.
"""
import datetime
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
errors: list[str] = []


def permissions() -> None:
    allowed = {line.strip() for line in (ROOT / "docs/permissions.txt").read_text().splitlines() if line.strip()}
    manifests = sorted(ROOT.glob("app/build/intermediates/merged_manifest/release/*/AndroidManifest.xml"))
    if not manifests:
        errors.append("No merged release manifest: build the release first.")
    for m in manifests:
        for tag in ("uses-permission", "uses-permission-sdk-23"):
            for e in ET.parse(m).getroot().iter(tag):
                name = e.get(ANDROID_NAME)
                if name not in allowed:
                    errors.append(f"{m.relative_to(ROOT)}: {name} is not listed in docs/permissions.txt")


# Built from pieces so this file does not match itself.
DASHES = re.compile("[\u2013\u2014]")
PERSONAL = re.compile("/" + "Users/")
AUTHORSHIP = re.compile(r"\bcl[a]ude\b|generated (by|with) [a]i\b|co-[a]uthored-by", re.IGNORECASE)


def repository_text() -> None:
    files = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True).stdout.split(b"\0")
    for f in filter(None, files):
        path = ROOT / f.decode()
        if not path.is_file():
            continue
        data = path.read_bytes()
        if b"\0" in data:
            continue  # binary
        for n, line in enumerate(data.decode("utf-8", errors="replace").splitlines(), 1):
            for rx, what in ((DASHES, "long or medium dash"), (PERSONAL, "personal path"), (AUTHORSHIP, "authorship text")):
                if rx.search(line):
                    errors.append(f"{f.decode()}:{n}: {what}")


def add_months(d: datetime.date, months: int) -> datetime.date:
    y, m = divmod(d.month - 1 + months, 12)
    year, month = d.year + y, m + 1
    for day in (d.day, 30, 29, 28):
        try:
            return datetime.date(year, month, day)
        except ValueError:
            continue
    raise ValueError(d)


def security_review() -> None:
    dates = sorted(
        datetime.date.fromisoformat(p.stem)
        for p in (ROOT / "docs/security").glob("*.md")
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", p.stem)
    )
    if not dates:
        errors.append("No dated review in docs/security/.")
    elif add_months(dates[-1], 6) < datetime.date.today():
        errors.append(f"The latest security review ({dates[-1]}) is more than 6 months old.")


def changelog() -> None:
    gradle = (ROOT / "app/build.gradle.kts").read_text()
    name = re.search(r'versionName = "([^"]+)"', gradle).group(1)
    code = re.search(r"versionCode = (\d+)", gradle).group(1)
    if not re.search(rf"^## {re.escape(name)}\s*$", (ROOT / "CHANGELOG.md").read_text(), re.MULTILINE):
        errors.append(f"CHANGELOG.md has no '## {name}' entry.")
    if not (ROOT / f"fastlane/metadata/android/en-US/changelogs/{code}.txt").is_file():
        errors.append(f"No store changelog fastlane/metadata/android/en-US/changelogs/{code}.txt.")


for check in (permissions, repository_text, security_review, changelog):
    check()
for e in errors:
    print(f"::error::{e}")
if errors:
    sys.exit(1)
print("Release checks passed.")
