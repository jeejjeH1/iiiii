#!/usr/bin/env bash
# Builds the Instagram Reel: 1080x1920, H.264 + AAC, loudness-normalized to -14 LUFS.
#   claude_connectors_reel.mp4          music + SFX + Persian voiceover
#   claude_connectors_reel_sfx_only.mp4 SFX + voiceover (add a trending track inside Instagram)
#   claude_connectors_reel_novoice.mp4  music + SFX
# The voiceover phrases in voice/vo are pre-rendered; regenerate them with voice/tts.py
# (needs Piper: pip install piper-tts, plus a Persian voice such as fa_IR-amir-medium).
set -euo pipefail
cd "$(dirname "$0")"
export NODE_PATH="${NODE_PATH:-$(npm root -g)}"
FPS="${FPS:-30}"
WORKERS="${WORKERS:-4}"

node audio.js
node render.js video "$FPS" "$WORKERS"
for v in "audio:claude_connectors_reel.mp4" "sfx:claude_connectors_reel_sfx_only.mp4" "novoice:claude_connectors_reel_novoice.mp4"; do
  ffmpeg -y -loglevel error -i build/video_noaudio.mp4 -i "build/${v%%:*}.wav" \
    -map 0:v -map 1:a -c:v libx264 -profile:v high -preset slow -crf 18 -pix_fmt yuv420p \
    -r "$FPS" -af "loudnorm=I=-14:TP=-1:LRA=11" -c:a aac -b:a 192k -ar 48000 -shortest -movflags +faststart \
    "${v#*:}"
  echo "done -> ${v#*:}"
done
