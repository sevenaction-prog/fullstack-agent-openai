from pathlib import Path

from fullstack_agent_openai.memory.store import MemoryStore


def test_remember_search_forget(tmp_path: Path):
    store = MemoryStore(tmp_path / "memory.db", tmp_path / "vault")
    item = store.remember("project.language", "The project uses Python", "project,stack")
    assert item.key == "project.language"
    found = store.search("What language does the project use?")
    assert found and found[0].key == "project.language"
    assert store.forget("project.language") is True
    assert store.get("project.language") is None
