from __future__ import annotations

from pathlib import Path


class WorkspaceFS:
    def __init__(self, workspace: Path):
        self.workspace = workspace.resolve()

    def resolve(self, user_path: str) -> Path:
        raw = Path(user_path)
        candidate = raw.resolve() if raw.is_absolute() else (self.workspace / raw).resolve()
        try:
            candidate.relative_to(self.workspace)
        except ValueError as exc:
            raise PermissionError(f"Path escapes workspace: {user_path}") from exc
        return candidate

    def list_files(self, path: str = ".", max_entries: int = 200) -> dict:
        target = self.resolve(path)
        if not target.exists():
            raise FileNotFoundError(path)
        if not target.is_dir():
            raise NotADirectoryError(path)
        entries = []
        for child in sorted(target.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower()))[:max_entries]:
            entries.append({
                "name": child.name,
                "path": str(child.relative_to(self.workspace)),
                "type": "directory" if child.is_dir() else "file",
                "size": child.stat().st_size if child.is_file() else None,
            })
        return {"path": str(target.relative_to(self.workspace)), "entries": entries, "truncated": len(entries) >= max_entries}

    def read_file(self, path: str, max_chars: int = 30000) -> dict:
        target = self.resolve(path)
        if not target.is_file():
            raise FileNotFoundError(path)
        text = target.read_text(encoding="utf-8", errors="replace")
        return {
            "path": str(target.relative_to(self.workspace)),
            "content": text[:max_chars],
            "truncated": len(text) > max_chars,
        }

    def write_file(self, path: str, content: str, overwrite: bool = False) -> dict:
        target = self.resolve(path)
        if target.exists() and not overwrite:
            raise FileExistsError(f"File exists; set overwrite=true to replace: {path}")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        return {"path": str(target.relative_to(self.workspace)), "bytes": len(content.encode("utf-8"))}
