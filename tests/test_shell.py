from pathlib import Path

import pytest

from fullstack_agent_openai.tools.shell import SafeShell


def test_safe_command(tmp_path: Path):
    shell = SafeShell(tmp_path, confirm=lambda _: True)
    result = shell.run("python -c print(123)")
    assert result.returncode == 0
    assert "123" in result.stdout


def test_shell_operators_blocked(tmp_path: Path):
    shell = SafeShell(tmp_path, confirm=lambda _: True)
    with pytest.raises(PermissionError):
        shell.run("echo hello | cat")


def test_blocked_executable(tmp_path: Path):
    shell = SafeShell(tmp_path, confirm=lambda _: True)
    with pytest.raises(PermissionError):
        shell.run("sudo whoami")
