# Architecture notes

## Trust boundaries

The language model chooses *what it wants to do*. Python code decides *what it is actually allowed to do*.

The model never receives a raw unrestricted shell. Every local capability is exposed as a narrow function with validation in Python.

## Responses API loop

1. Load `AGENT.md` as response instructions.
2. Search local memory for context relevant to the user turn.
3. Call `client.responses.create(...)` with local function schemas and optional hosted web search.
4. Execute returned `function_call` items through `ToolRegistry`.
5. Send each result back as a `function_call_output` using `previous_response_id`.
6. Continue until the model produces a normal response or the maximum tool-round limit is reached.

## Memory design

SQLite is the source of truth for current key/value memories. Markdown files in `memory/vault/` are an append-only human-readable journal. This intentionally keeps memory understandable and inspectable without requiring a vector database.

## Voice design

The browser establishes WebRTC audio. It sends the SDP offer to the local server. The server authenticates the call to OpenAI and returns the SDP answer. The standard API key therefore never enters browser JavaScript.

Local privileged tools are not exposed to the Realtime session in the MVP. A future bridge should surface every privileged call through a local approval UI before execution.
