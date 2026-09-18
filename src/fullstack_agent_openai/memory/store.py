from __future__ import annotations

import re
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


@dataclass(slots=True)
class MemoryItem:
    key: str
    value: str
    tags: str
    updated_at: str


class MemoryStore:
    def __init__(self, db_path: Path, vault_path: Path):
        self.db_path = db_path
        self.vault_path = vault_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.vault_path.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS memories (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    tags TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """
            )
            conn.execute("CREATE INDEX IF NOT EXISTS idx_memories_updated ON memories(updated_at DESC)")

    @staticmethod
    def _now() -> str:
        return datetime.now(timezone.utc).isoformat()

    @staticmethod
    def _safe_key(key: str) -> str:
        clean = re.sub(r"[^a-zA-Z0-9_.:-]+", "-", key.strip()).strip("-")
        if not clean:
            raise ValueError("memory key cannot be empty")
        return clean[:120]

    def remember(self, key: str, value: str, tags: str = "") -> MemoryItem:
        key = self._safe_key(key)
        value = value.strip()
        if not value:
            raise ValueError("memory value cannot be empty")
        now = self._now()
        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO memories(key, value, tags, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET
                    value=excluded.value,
                    tags=excluded.tags,
                    updated_at=excluded.updated_at
                """,
                (key, value, tags.strip(), now, now),
            )
        self._append_vault(key, value, tags, now)
        return MemoryItem(key=key, value=value, tags=tags.strip(), updated_at=now)

    def forget(self, key: str) -> bool:
        key = self._safe_key(key)
        with self._connect() as conn:
            cur = conn.execute("DELETE FROM memories WHERE key = ?", (key,))
            deleted = cur.rowcount > 0
        return deleted

    def get(self, key: str) -> MemoryItem | None:
        key = self._safe_key(key)
        with self._connect() as conn:
            row = conn.execute(
                "SELECT key, value, tags, updated_at FROM memories WHERE key = ?", (key,)
            ).fetchone()
        return MemoryItem(**dict(row)) if row else None

    def search(self, query: str, limit: int = 6) -> list[MemoryItem]:
        terms = [t.lower() for t in re.findall(r"[\w-]{3,}", query, flags=re.UNICODE)][:8]
        with self._connect() as conn:
            rows = conn.execute(
                "SELECT key, value, tags, updated_at FROM memories ORDER BY updated_at DESC LIMIT 200"
            ).fetchall()
        items = [MemoryItem(**dict(r)) for r in rows]
        if not terms:
            return items[:limit]

        def score(item: MemoryItem) -> tuple[int, str]:
            haystack = f"{item.key} {item.value} {item.tags}".lower()
            return (sum(haystack.count(term) for term in terms), item.updated_at)

        ranked = sorted(items, key=score, reverse=True)
        return [item for item in ranked if score(item)[0] > 0][:limit]

    def recent(self, limit: int = 20) -> list[MemoryItem]:
        with self._connect() as conn:
            rows = conn.execute(
                "SELECT key, value, tags, updated_at FROM memories ORDER BY updated_at DESC LIMIT ?", (limit,)
            ).fetchall()
        return [MemoryItem(**dict(r)) for r in rows]

    def _append_vault(self, key: str, value: str, tags: str, timestamp: str) -> None:
        day = timestamp[:10]
        path = self.vault_path / f"{day}.md"
        existed = path.exists()
        with path.open("a", encoding="utf-8") as f:
            if not existed:
                f.write(f"# Agent memory — {day}\n\n")
            f.write(f"## {key}\n\n{value}\n\n")
            if tags.strip():
                f.write(f"Tags: `{tags.strip()}`\n\n")
            f.write(f"Updated: {timestamp}\n\n---\n\n")
