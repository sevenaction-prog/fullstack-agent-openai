from __future__ import annotations

import json
from dataclasses import asdict
from typing import Any

from ..memory.store import MemoryStore
from .filesystem import WorkspaceFS
from .shell import SafeShell


class ToolRegistry:
    def __init__(
        self,
        fs: WorkspaceFS,
        memory: MemoryStore,
        shell: SafeShell | None,
        enable_write_tools: bool = True,
    ):
        self.fs = fs
        self.memory = memory
        self.shell = shell
        self.enable_write_tools = enable_write_tools

    def schemas(self) -> list[dict[str, Any]]:
        tools: list[dict[str, Any]] = [
            {
                "type": "function",
                "name": "list_files",
                "description": "List files and directories inside the configured workspace.",
                "parameters": {
                    "type": "object",
                    "properties": {"path": {"type": "string", "description": "Workspace-relative directory path"}},
                    "required": ["path"],
                    "additionalProperties": False,
                },
                "strict": True,
            },
            {
                "type": "function",
                "name": "read_file",
                "description": "Read a UTF-8 text file inside the configured workspace.",
                "parameters": {
                    "type": "object",
                    "properties": {"path": {"type": "string"}},
                    "required": ["path"],
                    "additionalProperties": False,
                },
                "strict": True,
            },
            {
                "type": "function",
                "name": "search_memory",
                "description": "Search durable local memory for relevant facts, preferences, and project decisions.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {"type": "string"},
                        "limit": {"type": "integer", "minimum": 1, "maximum": 20},
                    },
                    "required": ["query", "limit"],
                    "additionalProperties": False,
                },
                "strict": True,
            },
            {
                "type": "function",
                "name": "remember",
                "description": "Save or update a durable local memory. Never store secrets or transient chatter.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "key": {"type": "string"},
                        "value": {"type": "string"},
                        "tags": {"type": "string"},
                    },
                    "required": ["key", "value", "tags"],
                    "additionalProperties": False,
                },
                "strict": True,
            },
            {
                "type": "function",
                "name": "forget_memory",
                "description": "Delete a durable local memory by exact key.",
                "parameters": {
                    "type": "object",
                    "properties": {"key": {"type": "string"}},
                    "required": ["key"],
                    "additionalProperties": False,
                },
                "strict": True,
            },
        ]
        if self.enable_write_tools:
            tools.append({
                "type": "function",
                "name": "write_file",
                "description": "Create or replace a UTF-8 text file inside the configured workspace.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string"},
                        "content": {"type": "string"},
                        "overwrite": {"type": "boolean"},
                    },
                    "required": ["path", "content", "overwrite"],
                    "additionalProperties": False,
                },
                "strict": True,
            })
        if self.shell is not None:
            tools.append({
                "type": "function",
                "name": "run_command",
                "description": "Run one non-shell command inside the workspace. Pipes/redirection are disabled and risky commands require approval.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "command": {"type": "string"},
                        "cwd": {"type": "string"},
                    },
                    "required": ["command", "cwd"],
                    "additionalProperties": False,
                },
                "strict": True,
            })
        return tools

    def dispatch(self, name: str, args: dict[str, Any]) -> str:
        try:
            if name == "list_files":
                result = self.fs.list_files(args["path"])
            elif name == "read_file":
                result = self.fs.read_file(args["path"])
            elif name == "write_file":
                if not self.enable_write_tools:
                    raise PermissionError("write tools disabled")
                result = self.fs.write_file(args["path"], args["content"], args["overwrite"])
            elif name == "search_memory":
                result = [asdict(item) for item in self.memory.search(args["query"], args["limit"])]
            elif name == "remember":
                result = asdict(self.memory.remember(args["key"], args["value"], args["tags"]))
            elif name == "forget_memory":
                result = {"deleted": self.memory.forget(args["key"]), "key": args["key"]}
            elif name == "run_command":
                if self.shell is None:
                    raise PermissionError("shell tool disabled")
                result = asdict(self.shell.run(args["command"], args["cwd"]))
            else:
                raise KeyError(f"Unknown tool: {name}")
            return json.dumps({"ok": True, "result": result}, ensure_ascii=False)
        except Exception as exc:
            return json.dumps({"ok": False, "error": type(exc).__name__, "message": str(exc)}, ensure_ascii=False)
