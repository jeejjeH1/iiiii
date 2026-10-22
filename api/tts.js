// Persian speech for the app's live commentary, for phones without a Persian text-to-speech voice.
// GET /api/tts?t=<text>&v=farid|dilara  ->  audio/mpeg  (cached at the edge: the same sentence is spoken once)
const { MsEdgeTTS, OUTPUT_FORMAT } = require('msedge-tts');

const VOICES = { farid: 'fa-IR-FaridNeural', dilara: 'fa-IR-DilaraNeural' };

module.exports = async (req, res) => {
  const u = new URL(req.url, 'http://x');
  const text = (u.searchParams.get('t') || '').trim().slice(0, 400);
  const voice = VOICES[u.searchParams.get('v')] || VOICES.farid;
  if (!text) { res.statusCode = 400; return res.end('text'); }
  try {
    const tts = new MsEdgeTTS();
    await tts.setMetadata(voice, OUTPUT_FORMAT.AUDIO_24KHZ_48KBITRATE_MONO_MP3);
    // speak a little faster and livelier, like a match commentator
    const safe = text.replace(/[<>&]/g, ' ');
    const { audioStream } = tts.toStream(safe, { rate: '+8%', pitch: '+2Hz' });
    const audio = await new Promise((ok, no) => {
      const parts = [];
      audioStream.on('data', (c) => parts.push(c));
      audioStream.on('close', () => ok(Buffer.concat(parts)));
      audioStream.on('error', no);
      setTimeout(() => no(new Error('timeout')), 15000);
    });
    tts.close?.();
    if (!audio.length) throw new Error('empty');
    res.setHeader('Content-Type', 'audio/mpeg');
    res.setHeader('Cache-Control', 'public, max-age=86400, s-maxage=604800');
    res.end(audio);
  } catch (e) {
    res.statusCode = 502;
    res.end(String(e.message || e));
  }
};
