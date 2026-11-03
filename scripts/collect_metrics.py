import json
import os
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path("metrics-input")


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def percent(covered, total):
    return round(100 * covered / total, 2) if total else None


def java_coverage():
    report = ET.parse(ROOT / "backend-coverage" / "jacoco.xml").getroot()
    counters = {item.get("type"): item for item in report.findall("counter")}

    def coverage(kind):
        item = counters.get(kind)
        if item is None:
            return None
        covered = int(item.attrib["covered"])
        missed = int(item.attrib["missed"])
        return percent(covered, covered + missed)

    return {"lines_percent": coverage("LINE"), "branches_percent": coverage("BRANCH")}


def frontend_coverage():
    path = ROOT / "frontend-coverage" / "coverage-summary.json"
    total = json.loads(path.read_text(encoding="utf-8"))["total"]
    return {
        f"{kind}_percent": percent(total[kind]["covered"], total[kind]["total"])
        for kind in ("lines", "branches")
    }


def test_metrics(folder, pattern):
    files = sorted((ROOT / folder).rglob(pattern))
    if not files:
        raise FileNotFoundError(f"Не найдены отчёты тестов: {folder}/{pattern}")

    passed = failed = skipped = 0
    seconds = 0.0
    for file in files:
        for case in ET.parse(file).getroot().iter("testcase"):
            seconds += float(case.get("time") or 0)
            if case.find("failure") is not None or case.find("error") is not None:
                failed += 1
            elif case.find("skipped") is not None:
                skipped += 1
            else:
                passed += 1

    return {
        "passed": passed,
        "failed": failed,
        "skipped": skipped,
        "duration_seconds": round(seconds, 3),
    }


def commit_size():
    stats = git("show", "--first-parent", "--format=", "--numstat", "HEAD")
    added = deleted = 0
    for row in stats.splitlines():
        insertions, removals, _ = row.split("\t", 2)
        if insertions != "-":  # У бинарных файлов нет количества строк.
            added += int(insertions)
            deleted += int(removals)
    return {"lines_added": added, "lines_deleted": deleted}


metrics = {
    "stage": os.environ["STAGE_NAME"],
    "measured_at_utc": datetime.now(timezone.utc).isoformat(),
    "commit": git("rev-parse", "HEAD"),
    "backend": {
        "coverage": java_coverage(),
        "tests": test_metrics("backend-test-reports", "TEST-*.xml"),
    },
    "frontend": {
        "coverage": frontend_coverage(),
        "tests": test_metrics("frontend-test-reports", "junit.xml"),
    },
    "commit_size": commit_size(),
}

output = Path("metrics-output")
output.mkdir(exist_ok=True)
result = json.dumps(metrics, ensure_ascii=False, indent=2)
(output / "metrics.json").write_text(result + "\n", encoding="utf-8")
print(result)