from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        os.environ.setdefault(key, value)


def _bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(slots=True)
class Settings:
    api_key: str
    model: str
    reasoning_effort: str
    workspace: Path
    name: str
    prompt_file: Path
    memory_db: Path
    memory_vault: Path
    enable_web_search: bool
    enable_write_tools: bool
    enable_shell_tool: bool
    shell_timeout_seconds: int

    @classmethod
    def load(cls, root: Path | None = None) -> "Settings":
        root = (root or Path.cwd()).resolve()
        _load_dotenv(root / ".env")

        workspace_raw = Path(os.getenv("AGENT_WORKSPACE", "."))
        workspace = (root / workspace_raw).resolve() if not workspace_raw.is_absolute() else workspace_raw.resolve()

        def root_path(env_name: str, default: str) -> Path:
            p = Path(os.getenv(env_name, default))
            return (root / p).resolve() if not p.is_absolute() else p.resolve()

        return cls(
            api_key=os.getenv("OPENAI_API_KEY", ""),
            model=os.getenv("OPENAI_MODEL", "gpt-5.6"),
            reasoning_effort=os.getenv("OPENAI_REASONING_EFFORT", "medium"),
            workspace=workspace,
            name=os.getenv("AGENT_NAME", "Nova"),
            prompt_file=root_path("AGENT_PROMPT_FILE", "AGENT.md"),
            memory_db=root_path("AGENT_MEMORY_DB", "memory/agent.db"),
            memory_vault=root_path("AGENT_MEMORY_VAULT", "memory/vault"),
            enable_web_search=_bool("ENABLE_WEB_SEARCH", True),
            enable_write_tools=_bool("ENABLE_WRITE_TOOLS", True),
            enable_shell_tool=_bool("ENABLE_SHELL_TOOL", True),
            shell_timeout_seconds=int(os.getenv("SHELL_TIMEOUT_SECONDS", "30")),
        )

    def validate(self) -> list[str]:
        errors: list[str] = []
        if not self.api_key:
            errors.append("OPENAI_API_KEY is missing")
        if not self.prompt_file.exists():
            errors.append(f"Prompt file not found: {self.prompt_file}")
        if not self.workspace.exists():
            errors.append(f"Workspace not found: {self.workspace}")
        if self.reasoning_effort not in {"none", "minimal", "low", "medium", "high", "xhigh", "max"}:
            errors.append(f"Unsupported reasoning effort: {self.reasoning_effort}")
        return errors
