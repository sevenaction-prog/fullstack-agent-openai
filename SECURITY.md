# Security model

This project intentionally treats the model as an untrusted planner and local tools as privileged executors.

- File operations are confined to `AGENT_WORKSPACE`.
- Symlink/path traversal escapes are rejected after path resolution.
- Shell commands are executed without `shell=True`.
- Shell metacharacters/pipelines/redirection are rejected by the default runner.
- High-risk executables and destructive argument patterns require interactive confirmation or are blocked.
- The API key is read from the environment and should never be committed.

This is an MVP, not a hardened sandbox. Run it as a normal unprivileged user, keep the workspace narrow, review prompts before approving risky commands, and do not run it against directories containing sensitive credentials.
