# Voice mode

The optional voice UI uses the OpenAI Realtime API over WebRTC. The browser sends its SDP offer to the local Python server, and the server forwards it to OpenAI with the API key. The key is never sent to browser JavaScript.

Install the extra dependencies:

```bash
pip install -e '.[voice]'
fullstack-agent voice
```

Then open `http://127.0.0.1:8787` and allow microphone access.

The first MVP keeps voice conversational only. Filesystem/shell/memory tool execution remains in terminal chat because privileged local tool calls need an explicit approval bridge before exposing them to a browser voice session.
