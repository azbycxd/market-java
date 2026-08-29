"""Non-runtime governance validation for the versioned group-buy rules catalog."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


CATALOG_PATH = Path(__file__).with_name("group_buy_rules_v1.json")
REQUIRED_ENTRY_FIELDS = {
    "knowledge_id",
    "title",
    "category",
    "content",
    "source_type",
    "source_level",
    "source_files",
    "source_symbols",
    "version",
    "tags",
    "requires_realtime_facts",
}
VALID_LEVELS = {"LEVEL_1", "LEVEL_2", "LEVEL_3"}
SENSITIVE_PATTERNS = (
    "api_key",
    "api key",
    "password",
    "token",
    "authorization",
    "x-dev-authenticated-user-id",
    "jdbc:",
    "redis://",
    "notify_task.parameter_json",
    "outtradeno",
)
TEST_ORDER_PATTERN = re.compile(r"\b\d{12,}\b")
KNOWLEDGE_ID_PATTERN = re.compile(r"^[a-z][a-z0-9_]*$")


def fail(message: str) -> None:
    raise ValueError(message)


def validate() -> int:
    try:
        catalog = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"CATALOG_STATIC_VALIDATION=FAIL: {error}")
        return 1

    try:
        for field in ("catalog", "version", "generated_from", "review_date", "entry_count", "entries"):
            if field not in catalog or catalog[field] in (None, ""):
                fail(f"missing catalog field: {field}")
        entries = catalog["entries"]
        if not isinstance(entries, list) or not entries:
            fail("entries must be a non-empty list")
        if catalog["entry_count"] != len(entries):
            fail("entry_count does not match entries")

        seen_ids: set[str] = set()
        for index, entry in enumerate(entries):
            if not isinstance(entry, dict):
                fail(f"entry {index} is not an object")
            missing = REQUIRED_ENTRY_FIELDS.difference(entry)
            if missing:
                fail(f"entry {index} missing fields: {sorted(missing)}")
            knowledge_id = entry["knowledge_id"]
            if not isinstance(knowledge_id, str) or not KNOWLEDGE_ID_PATTERN.fullmatch(knowledge_id):
                fail(f"invalid knowledge_id: {knowledge_id!r}")
            if knowledge_id in seen_ids:
                fail(f"duplicate knowledge_id: {knowledge_id}")
            seen_ids.add(knowledge_id)
            for field in ("title", "category", "content", "source_type", "version"):
                if not isinstance(entry[field], str) or not entry[field].strip():
                    fail(f"entry {knowledge_id} has blank {field}")
            if entry["source_level"] not in VALID_LEVELS:
                fail(f"entry {knowledge_id} has invalid source_level")
            for field in ("source_files", "source_symbols", "tags"):
                values = entry[field]
                if not isinstance(values, list) or not values or not all(isinstance(value, str) and value.strip() for value in values):
                    fail(f"entry {knowledge_id} has invalid {field}")
            if not isinstance(entry["requires_realtime_facts"], bool):
                fail(f"entry {knowledge_id} requires_realtime_facts must be boolean")
            if any(Path(path).is_absolute() or ":" in path for path in entry["source_files"]):
                fail(f"entry {knowledge_id} has a non-portable source file path")
            if any(not (CATALOG_PATH.parents[3] / path).is_file() for path in entry["source_files"]):
                fail(f"entry {knowledge_id} references a source file that does not exist")

        serialized = json.dumps(catalog, ensure_ascii=False).lower()
        if any(pattern in serialized for pattern in SENSITIVE_PATTERNS):
            fail("catalog contains a prohibited sensitive or transport pattern")
        if TEST_ORDER_PATTERN.search(serialized):
            fail("catalog contains a possible test order number")
    except ValueError as error:
        print(f"CATALOG_STATIC_VALIDATION=FAIL: {error}")
        return 1

    print(f"CATALOG_STATIC_VALIDATION=PASS entries={len(entries)}")
    return 0


if __name__ == "__main__":
    sys.exit(validate())
