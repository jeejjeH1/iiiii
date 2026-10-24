#!/usr/bin/env bash
# Builds redstone_avs.mp4 (1920x1080, 60fps, H.264 + AAC, Twitter/X-ready)
set -euo pipefail
cd "$(dirname "$0")"
export NODE_PATH="${NODE_PATH:-$(npm root -g)}"
FPS="${FPS:-60}"
WORKERS="${WORKERS:-4}"

node audio.js
node render.js video "$FPS" "$WORKERS"
ffmpeg -y -loglevel error -i build/video_noaudio.mp4 -i build/audio.wav \
  -map 0:v -map 1:a -c:v libx264 -profile:v high -preset slow -crf 20 -pix_fmt yuv420p \
  -r "$FPS" -c:a aac -b:a 192k -ar 48000 -shortest -movflags +faststart \
  redstone_avs.mp4
echo "done -> redstone_avs.mp4"
