from pathlib import Path

import pytest

from fullstack_agent_openai.tools.filesystem import WorkspaceFS


def test_read_write_and_boundary(tmp_path: Path):
    fs = WorkspaceFS(tmp_path)
    fs.write_file("notes/hello.txt", "hello")
    assert fs.read_file("notes/hello.txt")["content"] == "hello"
    with pytest.raises(PermissionError):
        fs.read_file("../outside.txt")
