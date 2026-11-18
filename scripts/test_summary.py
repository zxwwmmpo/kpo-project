import os
import sys
from collections import Counter
from html import escape
from pathlib import Path
import xml.etree.ElementTree as ET


def cell(text):
    return escape(" ".join(text.split())[:400]).replace("|", "&#124;")


def main():
    title, *patterns = sys.argv[1:]
    rows = []
    counts = Counter()
    for pattern in patterns:
        for file in sorted(Path.cwd().glob(pattern)):
            for case in ET.parse(file).iter("testcase"):
                issue = case.find("failure")
                if issue is None:
                    issue = case.find("error")
                if issue is not None:
                    status = "Ошибка"
                    detail = issue.get("message") or issue.text or ""
                elif case.find("skipped") is not None:
                    status, detail = "Пропущен", ""
                else:
                    status, detail = "Пройден", ""
                counts[status] += 1
                name = case.get("classname", "") + " — " + case.get("name", "")
                rows.append(f"| {cell(name)} | {status} | {cell(detail)} |")

    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
        print(f"## {title}\n", file=out)
        if not rows:
            print("Результаты тестов отсутствуют. Проверьте журнал запуска.", file=out)
            return 1
        print(f"Пройдено: {counts['Пройден']} · Ошибок: {counts['Ошибка']} · "
              f"Пропущено: {counts['Пропущен']}\n", file=out)
        print("| Тест | Статус | Ошибка |\n|---|---|---|", file=out)
        print("\n".join(rows), file=out)
    return int(bool(counts["Ошибка"]))


if __name__ == "__main__":
    sys.exit(main())
