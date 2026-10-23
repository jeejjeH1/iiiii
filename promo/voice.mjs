// Narration lines for the promo video, in Edge's Persian newsreader voice (run in CI: needs the internet).
import { MsEdgeTTS, OUTPUT_FORMAT } from 'msedge-tts';
import fs from 'fs';
const lines = {
  v1: 'فوتبال، زنده‌تر از همیشه!',
  v2: 'نتیجه‌های لحظه‌ای، زمین زنده، رادار حریف و دستیار هوشمند فوتبال.',
  v3: 'هر گل را با گزارشگر فارسی، زنده بشنو!',
  v4: 'گل‌لایو!',
  v5: 'همین حالا از بازار دانلود کنید.',
  goal: 'گللللل!',
};
fs.mkdirSync('promo/out', { recursive: true });
for (const [name, text] of Object.entries(lines)) {
  const tts = new MsEdgeTTS();
  await tts.setMetadata('fa-IR-FaridNeural', OUTPUT_FORMAT.AUDIO_24KHZ_96KBITRATE_MONO_MP3);
  const { audioStream } = tts.toStream(text, { rate: name === 'goal' ? '+0%' : '+12%', pitch: name === 'v4' || name === 'goal' ? '+6Hz' : '+2Hz' });
  const parts = [];
  await new Promise((ok, no) => { audioStream.on('data', (c) => parts.push(c)); audioStream.on('close', ok); audioStream.on('error', no); });
  fs.writeFileSync(`promo/out/${name}.mp3`, Buffer.concat(parts));
  console.log(name, Buffer.concat(parts).length);
  tts.close?.();
}
