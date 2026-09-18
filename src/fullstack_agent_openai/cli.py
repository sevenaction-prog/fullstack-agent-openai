from __future__ import annotations

import argparse
import sys
from pathlib import Path

from rich.console import Console
from rich.markdown import Markdown
from rich.panel import Panel
from rich.prompt import Confirm

from .agent.runtime import AgentRuntime
from .config import Settings
from .memory.store import MemoryStore
from .tools.filesystem import WorkspaceFS
from .tools.registry import ToolRegistry
from .tools.shell import SafeShell


console = Console()


def _confirm(message: str) -> bool:
    return Confirm.ask(f"[yellow]{message}[/yellow]", default=False)


def build_runtime(root: Path) -> AgentRuntime:
    settings = Settings.load(root)
    errors = settings.validate()
    if errors:
        raise RuntimeError("; ".join(errors))
    memory = MemoryStore(settings.memory_db, settings.memory_vault)
    fs = WorkspaceFS(settings.workspace)
    shell = SafeShell(settings.workspace, settings.shell_timeout_seconds, _confirm) if settings.enable_shell_tool else None
    registry = ToolRegistry(fs, memory, shell, enable_write_tools=settings.enable_write_tools)
    return AgentRuntime(settings, memory, registry)


def doctor(root: Path) -> int:
    settings = Settings.load(root)
    rows = {
        "agent": settings.name,
        "model": settings.model,
        "workspace": str(settings.workspace),
        "prompt": str(settings.prompt_file),
        "memory db": str(settings.memory_db),
        "web search": settings.enable_web_search,
        "write tools": settings.enable_write_tools,
        "shell": settings.enable_shell_tool,
        "API key": "configured" if settings.api_key else "MISSING",
    }
    text = "\n".join(f"[bold]{k}[/bold]: {v}" for k, v in rows.items())
    console.print(Panel(text, title="Fullstack Agent doctor"))
    errors = settings.validate()
    if errors:
        for error in errors:
            console.print(f"[red]✗[/red] {error}")
        return 1
    console.print("[green]✓ Configuration looks usable[/green]")
    return 0


def chat(root: Path) -> int:
    try:
        runtime = build_runtime(root)
    except Exception as exc:
        console.print(f"[red]Startup error:[/red] {exc}")
        return 1

    console.print(Panel.fit(
        f"[bold]{runtime.settings.name}[/bold] online\n"
        f"model: {runtime.settings.model}\nworkspace: {runtime.settings.workspace}\n"
        "Commands: /reset, /memory, /quit",
        title="Fullstack Agent OpenAI",
    ))

    while True:
        try:
            user_text = console.input("\n[bold cyan]You[/bold cyan] › ").strip()
        except (EOFError, KeyboardInterrupt):
            console.print()
            break
        if not user_text:
            continue
        if user_text in {"/quit", "/exit"}:
            break
        if user_text == "/reset":
            runtime.reset()
            console.print("[dim]Conversation state reset; durable memory kept.[/dim]")
            continue
        if user_text == "/memory":
            items = runtime.memory.recent(20)
            if not items:
                console.print("[dim]No durable memories yet.[/dim]")
            else:
                for item in items:
                    console.print(f"[bold]{item.key}[/bold] — {item.value} [dim]({item.tags})[/dim]")
            continue

        try:
            answer = runtime.chat(user_text)
            console.print(f"\n[bold magenta]{runtime.settings.name}[/bold magenta] ›")
            console.print(Markdown(answer))
        except KeyboardInterrupt:
            console.print("\n[yellow]Interrupted.[/yellow]")
        except Exception as exc:
            console.print(f"[red]Agent error:[/red] {type(exc).__name__}: {exc}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Local-first OpenAI personal agent")
    parser.add_argument("--root", default=".", help="Project root containing .env and AGENT.md")
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("chat", help="Start interactive agent chat")
    sub.add_parser("doctor", help="Validate configuration")
    voice_parser = sub.add_parser("voice", help="Start the local Realtime voice web UI")
    voice_parser.add_argument("--host", default="127.0.0.1")
    voice_parser.add_argument("--port", type=int, default=8787)
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    if args.command in {None, "chat"}:
        return chat(root)
    if args.command == "doctor":
        return doctor(root)
    if args.command == "voice":
        from .voice.server import run_voice_server
        run_voice_server(root, host=args.host, port=args.port)
        return 0
    return 2


if __name__ == "__main__":
    sys.exit(main())
