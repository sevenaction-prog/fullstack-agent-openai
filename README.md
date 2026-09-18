# Fullstack Agent OpenAI

A local-first personal agent runtime inspired by `jaredrhod/fullstack-agent`, rebuilt around the OpenAI API.

The project gives an OpenAI model a persistent local identity (`AGENT.md`), durable memory, workspace-scoped filesystem tools, a guarded command runner, optional hosted web search, and an optional Realtime voice UI.

> **MVP status:** terminal agent, memory, filesystem tools, guarded shell, web search, tests, and Realtime voice preview are implemented. Voice-to-local-tool execution is intentionally not enabled yet; privileged browser/voice tool calls need an approval bridge first.

## Architecture

```text
                           OpenAI Responses API
                              GPT-5.6 (default)
                                    |
                         +----------+----------+
                         |                     |
                    AGENT.md              hosted web search
                         |
                  AgentRuntime/tool loop
                         |
       +-----------------+-------------------+
       |                 |                   |
  Local memory      Workspace files     Guarded commands
 SQLite + Markdown   read/write tools    shell=False + approval
       |
 memory/vault/

Optional voice:
 Browser microphone <-> WebRTC <-> local proxy <-> OpenAI Realtime API
```

## What is included

- **Responses API agent loop** with OpenAI function calling.
- **Persistent local memory** in SQLite, with a human-readable Markdown vault log.
- **`AGENT.md` identity/instructions** loaded on every response.
- **Workspace confinement** for file reads/writes and command working directories.
- **Guarded shell execution** without `shell=True`; pipelines, redirects and shell substitution are blocked by default.
- **Interactive approval** for higher-risk local commands.
- **Optional OpenAI web search** as a hosted tool.
- **Realtime voice preview** using browser WebRTC while keeping the API key server-side.
- **Cross-platform entry points** (`start.sh`, `start.bat`, and the `fullstack-agent` CLI).
- **Unit tests + GitHub Actions**.

## Requirements

- Python 3.11+
- An OpenAI API key with API billing enabled
- A supported OpenAI model (default: `gpt-5.6`)

ChatGPT subscriptions and OpenAI API billing are separate.

## Install

```bash
git clone <your-repo-url>
cd fullstack-agent-openai
python -m venv .venv
```

macOS/Linux:

```bash
source .venv/bin/activate
pip install -e .
cp .env.example .env
```

Windows PowerShell:

```powershell
.venv\Scripts\Activate.ps1
pip install -e .
Copy-Item .env.example .env
```

Edit `.env` and set:

```dotenv
OPENAI_API_KEY=sk-...
```

Never commit `.env`.

## Run

Validate the local setup:

```bash
fullstack-agent doctor
```

Start terminal mode:

```bash
fullstack-agent chat
```

or:

```bash
./start.sh
```

Useful terminal commands:

```text
/reset   reset OpenAI conversation state but keep durable memory
/memory  show recent durable memory entries
/quit    exit
```

## Voice mode

Install optional dependencies:

```bash
pip install -e '.[voice]'
```

Start the local voice UI:

```bash
fullstack-agent voice
```

Open `http://127.0.0.1:8787`, allow microphone access, and speak.

Optional voice settings:

```dotenv
OPENAI_REALTIME_MODEL=gpt-realtime-2.1
OPENAI_REALTIME_VOICE=marin
```

The MVP voice mode is conversational only. Local filesystem/shell tools stay in terminal mode until a secure approval bridge is implemented.

## Memory

Durable memory is stored in:

```text
memory/agent.db
```

A readable append-only journal is also written to:

```text
memory/vault/YYYY-MM-DD.md
```

The model has three relevant tools:

- `search_memory`
- `remember`
- `forget_memory`

The runtime also retrieves a small set of relevant memories before each user turn and provides them as context.

## Local tools

### Filesystem

`list_files`, `read_file`, and `write_file` resolve paths against `AGENT_WORKSPACE`. Resolved paths outside the workspace are rejected, including `../` traversal and symlink escapes.

### Commands

`run_command` uses `subprocess.run(..., shell=False)`. Shell operators such as `|`, `>`, `<`, `;`, command substitution, and multiline commands are rejected. A set of privileged/system executables is blocked, and commands likely to modify state require terminal approval.

This is defense-in-depth, **not a hardened sandbox**. Keep `AGENT_WORKSPACE` narrow and run the agent as a normal unprivileged OS user.

## Configuration

Key `.env` variables:

| Variable | Default | Purpose |
|---|---|---|
| `OPENAI_MODEL` | `gpt-5.6` | Responses API model |
| `OPENAI_REASONING_EFFORT` | `medium` | Reasoning effort |
| `AGENT_NAME` | `Nova` | Display/identity name |
| `AGENT_WORKSPACE` | `.` | Boundary for local tools |
| `AGENT_PROMPT_FILE` | `AGENT.md` | Persistent instructions |
| `ENABLE_WEB_SEARCH` | `true` | Hosted OpenAI web search |
| `ENABLE_WRITE_TOOLS` | `true` | Local file writes |
| `ENABLE_SHELL_TOOL` | `true` | Guarded command runner |
| `SHELL_TIMEOUT_SECONDS` | `30` | Command timeout |

## Project layout

```text
fullstack-agent-openai/
├── AGENT.md
├── README.md
├── SECURITY.md
├── .env.example
├── pyproject.toml
├── start.sh
├── start.bat
├── memory/
│   └── vault/
├── src/fullstack_agent_openai/
│   ├── agent/runtime.py
│   ├── memory/store.py
│   ├── tools/filesystem.py
│   ├── tools/registry.py
│   ├── tools/shell.py
│   ├── voice/server.py
│   ├── config.py
│   └── cli.py
├── tests/
└── .github/workflows/test.yml
```

## Tests

```bash
pip install -e '.[dev]'
pytest -q
```

## Security defaults

Read `SECURITY.md` before granting the agent access to sensitive project directories.

The API key is expected in the process environment / `.env`; it is not exposed as a model tool and the browser voice UI does not receive it.

## Roadmap

- Approval bridge for Realtime voice tool calls
- Desktop companion UI / avatar visualizer
- Optional semantic/vector memory index
- MCP server configuration
- Per-tool permission policies (`allow`, `ask`, `deny`)
- Structured audit log of every privileged action
- Packaging/install wizard for macOS, Windows and Linux

## License

MIT
