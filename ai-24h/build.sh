#!/usr/bin/env bash
# Builds the Instagram Reel: 1080x1920, H.264 + AAC, loudness-normalized to -14 LUFS.
#   ai24h_reel.mp4         music + SFX
#   ai24h_reel_sfx_only.mp4  SFX only (add a trending track inside Instagram)
set -euo pipefail
cd "$(dirname "$0")"
export NODE_PATH="${NODE_PATH:-$(npm root -g)}"
FPS="${FPS:-30}"
WORKERS="${WORKERS:-4}"

node audio.js
node render.js video "$FPS" "$WORKERS"
for v in "audio:ai24h_reel.mp4" "sfx:ai24h_reel_sfx_only.mp4"; do
  ffmpeg -y -loglevel error -i build/video_noaudio.mp4 -i "build/${v%%:*}.wav" \
    -map 0:v -map 1:a -c:v libx264 -profile:v high -preset slow -crf 18 -pix_fmt yuv420p \
    -r "$FPS" -af "loudnorm=I=-14:TP=-1:LRA=11" -c:a aac -b:a 192k -ar 48000 -shortest -movflags +faststart \
    "${v#*:}"
  echo "done -> ${v#*:}"
done
