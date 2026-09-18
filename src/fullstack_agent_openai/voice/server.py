
import json
import os
from pathlib import Path

from ..config import Settings


HTML = r'''<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Fullstack Agent Voice</title>
  <style>
    :root { color-scheme: dark; font-family: Inter, ui-sans-serif, system-ui, sans-serif; }
    body { margin: 0; background: #0b0d10; color: #e8edf2; min-height: 100vh; display: grid; place-items: center; }
    main { width: min(900px, 92vw); }
    .card { background: #12161b; border: 1px solid #27303a; border-radius: 20px; padding: 24px; box-shadow: 0 20px 70px rgba(0,0,0,.35); }
    h1 { margin: 0 0 8px; font-size: 1.8rem; }
    p { color: #9eabb8; }
    .row { display:flex; gap:12px; flex-wrap:wrap; align-items:center; }
    button, input { border-radius:12px; border:1px solid #33404d; background:#19202a; color:#fff; padding:12px 14px; font:inherit; }
    button { cursor:pointer; }
    button.primary { background:#e9eef4; color:#11161b; }
    button:disabled { opacity:.45; cursor:not-allowed; }
    input { flex:1; min-width:240px; }
    #status { display:inline-flex; align-items:center; gap:8px; margin:12px 0 18px; }
    .dot { width:10px; height:10px; border-radius:50%; background:#777; }
    .online .dot { background:#54d98c; box-shadow:0 0 16px #54d98c; }
    pre { white-space:pre-wrap; background:#0d1116; border-radius:14px; padding:14px; min-height:160px; max-height:360px; overflow:auto; color:#c9d4df; }
    small { color:#778492; }
  </style>
</head>
<body>
<main class="card">
  <h1>Fullstack Agent Voice</h1>
  <p>Realtime speech-to-speech preview. The API key remains on the local server.</p>
  <div id="status"><span class="dot"></span><span id="statusText">offline</span></div>
  <div class="row">
    <button id="start" class="primary">Start microphone</button>
    <button id="stop" disabled>Stop</button>
  </div>
  <div class="row" style="margin-top:12px">
    <input id="text" placeholder="You can also type a message…" disabled />
    <button id="send" disabled>Send</button>
  </div>
  <pre id="log"></pre>
  <small>Voice mode is intentionally conversation-only in this MVP. Local filesystem/shell tools remain in the terminal chat until the approval bridge is added.</small>
</main>
<script>
let pc, dc, localStream;
const startBtn = document.querySelector('#start');
const stopBtn = document.querySelector('#stop');
const sendBtn = document.querySelector('#send');
const textInput = document.querySelector('#text');
const log = document.querySelector('#log');
const status = document.querySelector('#status');
const statusText = document.querySelector('#statusText');

function line(s) { log.textContent += s + '\n'; log.scrollTop = log.scrollHeight; }
function setOnline(v) { status.classList.toggle('online', v); statusText.textContent = v ? 'online' : 'offline'; }

async function start() {
  startBtn.disabled = true;
  line('Requesting microphone…');
  try {
    pc = new RTCPeerConnection();
    const audio = document.createElement('audio');
    audio.autoplay = true;
    pc.ontrack = e => { audio.srcObject = e.streams[0]; };

    localStream = await navigator.mediaDevices.getUserMedia({audio:true});
    localStream.getTracks().forEach(track => pc.addTrack(track, localStream));

    dc = pc.createDataChannel('oai-events');
    dc.onopen = () => {
      setOnline(true); stopBtn.disabled = false; sendBtn.disabled = false; textInput.disabled = false;
      line('Realtime session connected. Speak naturally.');
    };
    dc.onclose = () => setOnline(false);
    dc.onmessage = e => {
      try {
        const ev = JSON.parse(e.data);
        if (ev.type === 'conversation.item.input_audio_transcription.completed' && ev.transcript) line('You: ' + ev.transcript);
        else if (typeof ev.delta === 'string' && ev.type && ev.type.includes('transcript')) {
          if (!ev.type.includes('delta')) line(ev.delta);
        } else if (ev.type === 'response.done' || ev.type === 'error') {
          line(ev.type + (ev.error?.message ? ': ' + ev.error.message : ''));
        }
      } catch (_) {}
    };

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    const response = await fetch('/realtime-call', {method:'POST', headers:{'Content-Type':'application/sdp'}, body:offer.sdp});
    if (!response.ok) throw new Error(await response.text());
    const answer = {type:'answer', sdp: await response.text()};
    await pc.setRemoteDescription(answer);
  } catch (err) {
    line('Error: ' + err.message); stop();
  }
}

function stop() {
  if (dc) dc.close();
  if (pc) pc.close();
  if (localStream) localStream.getTracks().forEach(t => t.stop());
  pc = dc = localStream = null;
  setOnline(false); startBtn.disabled = false; stopBtn.disabled = true; sendBtn.disabled = true; textInput.disabled = true;
}

function sendText() {
  const text = textInput.value.trim();
  if (!text || !dc || dc.readyState !== 'open') return;
  dc.send(JSON.stringify({type:'conversation.item.create', item:{type:'message', role:'user', content:[{type:'input_text', text}]}}));
  dc.send(JSON.stringify({type:'response.create'}));
  line('You: ' + text); textInput.value='';
}

startBtn.onclick = start; stopBtn.onclick = stop; sendBtn.onclick = sendText;
textInput.addEventListener('keydown', e => { if (e.key === 'Enter') sendText(); });
</script>
</body>
</html>'''


def create_app(root: Path):
    try:
        import httpx
        from fastapi import FastAPI, HTTPException, Request
        from fastapi.responses import HTMLResponse, Response
    except ImportError as exc:
        raise RuntimeError("Voice dependencies missing. Install with: pip install -e '.[voice]'") from exc

    settings = Settings.load(root)
    if not settings.api_key:
        raise RuntimeError("OPENAI_API_KEY is missing")

    prompt = settings.prompt_file.read_text(encoding="utf-8").replace("{{AGENT_NAME}}", settings.name)
    model = os.getenv("OPENAI_REALTIME_MODEL", "gpt-realtime-2.1")
    voice = os.getenv("OPENAI_REALTIME_VOICE", "marin")
    app = FastAPI(title="Fullstack Agent Voice")

    @app.get("/", response_class=HTMLResponse)
    async def index():
        return HTML

    @app.post("/realtime-call")
    async def realtime_call(request: Request):
        offer_sdp = (await request.body()).decode("utf-8")
        if not offer_sdp.strip():
            raise HTTPException(status_code=400, detail="Missing SDP offer")

        session = {
            "type": "realtime",
            "model": model,
            "instructions": prompt,
            "audio": {
                "input": {
                    "transcription": {"model": "gpt-4o-transcribe"},
                    "turn_detection": {
                        "type": "semantic_vad",
                        "eagerness": "auto",
                        "create_response": True,
                        "interrupt_response": True,
                    },
                },
                "output": {"voice": voice},
            },
        }
        files = {
            "sdp": (None, offer_sdp, "application/sdp"),
            "session": (None, json.dumps(session), "application/json"),
        }
        async with httpx.AsyncClient(timeout=30.0) as client:
            upstream = await client.post(
                "https://api.openai.com/v1/realtime/calls",
                headers={"Authorization": f"Bearer {settings.api_key}"},
                files=files,
            )
        if upstream.status_code >= 400:
            raise HTTPException(status_code=upstream.status_code, detail=upstream.text)
        return Response(content=upstream.text, media_type="application/sdp")

    return app


def run_voice_server(root: Path, host: str = "127.0.0.1", port: int = 8787) -> None:
    try:
        import uvicorn
    except ImportError as exc:
        raise RuntimeError("Voice dependencies missing. Install with: pip install -e '.[voice]'") from exc
    app = create_app(root)
    uvicorn.run(app, host=host, port=port)
