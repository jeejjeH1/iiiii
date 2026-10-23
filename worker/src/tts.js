// /api/tts?t=<text>&v=farid|dilara|ryan|sonia -> audio/mpeg, from Microsoft Edge's read-aloud voices over a WebSocket
// (the same protocol as the app's EdgeVoice.kt; msedge-tts needs Node sockets, which Workers don't have).
const TOKEN = '6A5AA1D4EAFF4E9FB37E23D68491D6F4';
const GEC_VERSION = '1-143.0.3650.96';
const VOICES = { farid: 'fa-IR-FaridNeural', dilara: 'fa-IR-DilaraNeural', ryan: 'en-GB-RyanNeural', sonia: 'en-GB-SoniaNeural' };

async function gec() {
  const ticks = Math.floor(Date.now() / 1000) + 11644473600;
  const rounded = BigInt(ticks - (ticks % 300)) * 10000000n;
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(rounded.toString() + TOKEN));
  return [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, '0')).join('').toUpperCase();
}

const stamp = () => new Date().toUTCString().replace('GMT', 'GMT+0000 (Coordinated Universal Time)');
const uid = () => crypto.randomUUID().replace(/-/g, '');

export async function tts(url) {
  const text = (url.searchParams.get('t') || '').trim().slice(0, 400).replace(/[<>&]/g, ' ');
  const voice = VOICES[url.searchParams.get('v')] || VOICES.farid;
  if (!text) return new Response('text', { status: 400 });
  const wss = `https://api.msedgeservices.com/tts/cognitiveservices/websocket/v1?Ocp-Apim-Subscription-Key=${TOKEN}` +
    `&Sec-MS-GEC=${await gec()}&Sec-MS-GEC-Version=${GEC_VERSION}&ConnectionId=${uid()}`;
  const up = await fetch(wss, { headers: { Upgrade: 'websocket', Origin: 'chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0' } });
  const ws = up.webSocket;
  if (!ws) return new Response('voice service refused: ' + up.status, { status: 502 });
  ws.accept();
  const parts = [];
  const delim = new TextEncoder().encode('Path:audio\r\n');
  const done = new Promise((resolve) => {
    const timer = setTimeout(resolve, 15000);
    ws.addEventListener('message', (ev) => {
      if (typeof ev.data === 'string') { if (ev.data.includes('Path:turn.end')) { clearTimeout(timer); resolve(); } return; }
      const b = new Uint8Array(ev.data);
      for (let i = 0; i <= b.length - delim.length; i++) {
        let ok = true;
        for (let j = 0; j < delim.length; j++) if (b[i + j] !== delim[j]) { ok = false; break; }
        if (ok) { parts.push(b.slice(i + delim.length)); break; }
      }
    });
    ws.addEventListener('close', () => { clearTimeout(timer); resolve(); });
  });
  ws.send(`X-Timestamp:${stamp()}\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n` +
    '{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}');
  ws.send(`X-RequestId:${uid()}\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${stamp()}Z\r\nPath:ssml\r\n\r\n` +
    `<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='${voice.slice(0, 5)}'><voice name='${voice}'>` +
    `<prosody pitch='+2Hz' rate='+8%' volume='+0%'>${text}</prosody></voice></speak>`);
  await done;
  try { ws.close(1000); } catch {}
  const size = parts.reduce((n, p) => n + p.length, 0);
  if (size < 500) return new Response('empty', { status: 502 });
  const audio = new Uint8Array(size);
  let at = 0;
  for (const p of parts) { audio.set(p, at); at += p.length; }
  return new Response(audio, { headers: { 'Content-Type': 'audio/mpeg', 'Cache-Control': 'public, s-maxage=604800' } });
}
