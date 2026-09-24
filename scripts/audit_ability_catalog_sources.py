"""Compare the runtime Android Ability catalog with the legacy per-file copy."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CANONICAL_SOURCE = ROOT / "android-app/app/src/main/assets/pokerole/abilities-v3.json"
LEGACY_SOURCE = ROOT / "android-app/app/src/main/assets/legacy/json/Abilities"
RULES_VERSION = "3.0"
FIELDS = ("_id", "Name", "Description", "Effect")
CLASSIFICATIONS = {
    "berserk": ["formatting/spelling only"],
    "cloud-nine": ["formatting/spelling only"],
    "drought": ["formatting/spelling only"],
    "effect-spore": ["formatting/spelling only"],
    "inner-focus": ["formatting/spelling only"],
    "mimicry": ["formatting/spelling only"],
    "neutralizing-gas": ["formatting/spelling only"],
    "overcoat": ["descriptive/flavour", "mechanical"],
    "protean": ["formatting/spelling only"],
    "purifying-salt": ["formatting/spelling only"],
    "run-away-ability": ["identifier/reference"],
    "shed-skin": ["formatting/spelling only"],
    "slush-rush": ["mechanical"],
    "stalwart": ["formatting/spelling only"],
    "water-compaction": ["formatting/spelling only"],
}


def load_canonical(path: Path) -> list[dict[str, str]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError("Canonical Ability source must be a JSON array")
    return validate(payload, path.as_posix())


def load_legacy(path: Path) -> list[dict[str, str]]:
    payload = [
        json.loads(item.read_text(encoding="utf-8"))
        for item in sorted(path.glob("*.json"), key=lambda item: item.name.casefold())
    ]
    return validate(payload, path.as_posix())


def validate(payload: list[object], label: str) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    ids: set[str] = set()
    names: set[str] = set()
    for index, value in enumerate(payload):
        if not isinstance(value, dict) or set(value) != set(FIELDS):
            raise ValueError(f"{label}[{index}] has an invalid schema")
        row = {field: value[field] for field in FIELDS}
        if not all(isinstance(item, str) and item.strip() for item in row.values()):
            raise ValueError(f"{label}[{index}] has a blank required field")
        identity = row["_id"].casefold()
        name = row["Name"].strip().casefold()
        if identity in ids or name in names:
            raise ValueError(f"{label}[{index}] duplicates an ID or normalized name")
        ids.add(identity)
        names.add(name)
        rows.append(row)
    return rows


def canonical_md5(rows: list[dict[str, str]]) -> str:
    lines = [
        "\t".join(
            (RULES_VERSION, row["_id"], row["Name"], row["Description"], row["Effect"])
        )
        for row in sorted(rows, key=lambda row: row["_id"])
    ]
    return hashlib.md5("\n".join(lines).encode("utf-8")).hexdigest()


def build_diff() -> dict[str, object]:
    canonical = load_canonical(CANONICAL_SOURCE)
    legacy = load_legacy(LEGACY_SOURCE)
    canonical_by_name = {row["Name"].strip().casefold(): row for row in canonical}
    legacy_by_name = {row["Name"].strip().casefold(): row for row in legacy}
    if set(canonical_by_name) != set(legacy_by_name):
        raise ValueError("Canonical and legacy Ability name sets differ")

    mismatches = []
    for name_key in sorted(canonical_by_name):
        current = canonical_by_name[name_key]
        old = legacy_by_name[name_key]
        fields = {}
        for field in FIELDS:
            if current[field] != old[field]:
                fields[field] = {"legacy": old[field], "canonical": current[field]}
        if fields:
            classifications = CLASSIFICATIONS.get(current["_id"])
            if not classifications:
                raise ValueError(f"Missing classification for {current['_id']}")
            mismatches.append(
                {
                    "name": current["Name"],
                    "legacy_id": old["_id"],
                    "canonical_id": current["_id"],
                    "differing_fields": list(fields),
                    "classifications": classifications,
                    "differences": fields,
                }
            )

    classified_ids = {item["canonical_id"] for item in mismatches}
    if classified_ids != set(CLASSIFICATIONS):
        raise ValueError("Classification map and observed mismatches differ")

    return {
        "schema_version": "1.0",
        "rules_version": RULES_VERSION,
        "canonical_source": CANONICAL_SOURCE.relative_to(ROOT).as_posix(),
        "comparison_source": LEGACY_SOURCE.relative_to(ROOT).as_posix(),
        "comparison_normalization": "JSON formatting and object key order only; text is compared exactly.",
        "canonical_count": len(canonical),
        "comparison_count": len(legacy),
        "canonical_md5": canonical_md5(canonical),
        "comparison_md5": canonical_md5(legacy),
        "matched_definitions": len(canonical) - len(mismatches),
        "mismatched_definitions": len(mismatches),
        "mismatches": mismatches,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    rendered = json.dumps(build_diff(), indent=2, ensure_ascii=False) + "\n"
    output = args.output.resolve()
    if args.check:
        if not output.exists() or output.read_text(encoding="utf-8") != rendered:
            raise SystemExit(f"Ability diff artifact is out of date: {output}")
        print(f"OK: {output} matches both Ability sources")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(rendered, encoding="utf-8", newline="\n")
    print(f"Wrote {output}")


if __name__ == "__main__":
    main()
