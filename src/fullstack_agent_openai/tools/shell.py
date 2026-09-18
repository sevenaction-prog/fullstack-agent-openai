from __future__ import annotations

import re
import shlex
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


ConfirmFn = Callable[[str], bool]


@dataclass(slots=True)
class ShellResult:
    command: str
    returncode: int
    stdout: str
    stderr: str


class SafeShell:
    BLOCKED_EXECUTABLES = {
        "sudo", "su", "doas", "ssh", "scp", "sftp", "nc", "netcat",
        "shutdown", "reboot", "halt", "poweroff", "mkfs", "fdisk", "parted",
    }
    RISKY_EXECUTABLES = {"rm", "mv", "cp", "chmod", "chown", "git", "pip", "npm", "pnpm", "yarn", "docker", "kubectl"}
    SHELL_META = re.compile(r"[|;&><`$\n\r]")

    def __init__(self, workspace: Path, timeout_seconds: int = 30, confirm: ConfirmFn | None = None):
        self.workspace = workspace.resolve()
        self.timeout_seconds = timeout_seconds
        self.confirm = confirm or (lambda _message: False)

    def run(self, command: str, cwd: str = ".") -> ShellResult:
        if self.SHELL_META.search(command):
            raise PermissionError("Shell operators, pipes, redirects, substitutions, and multiline commands are disabled")
        argv = shlex.split(command)
        if not argv:
            raise ValueError("command cannot be empty")
        exe = Path(argv[0]).name.lower()
        if exe in self.BLOCKED_EXECUTABLES:
            raise PermissionError(f"Executable is blocked by policy: {exe}")

        workdir = (self.workspace / cwd).resolve() if not Path(cwd).is_absolute() else Path(cwd).resolve()
        try:
            workdir.relative_to(self.workspace)
        except ValueError as exc:
            raise PermissionError("cwd escapes workspace") from exc
        if not workdir.is_dir():
            raise NotADirectoryError(cwd)

        risky = exe in self.RISKY_EXECUTABLES or self._looks_destructive(argv)
        if risky and not self.confirm(f"Allow shell command? {command}"):
            raise PermissionError("User denied risky shell command")

        completed = subprocess.run(
            argv,
            cwd=workdir,
            capture_output=True,
            text=True,
            timeout=self.timeout_seconds,
            shell=False,
            check=False,
        )
        limit = 30000
        return ShellResult(
            command=command,
            returncode=completed.returncode,
            stdout=completed.stdout[:limit],
            stderr=completed.stderr[:limit],
        )

    @staticmethod
    def _looks_destructive(argv: list[str]) -> bool:
        joined = " ".join(argv).lower()
        patterns = ["--force", " -f ", "reset --hard", "clean -fd", "checkout --", "restore --source"]
        padded = f" {joined} "
        return any(p in padded for p in patterns)
