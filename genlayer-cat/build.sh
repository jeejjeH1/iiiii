#!/usr/bin/env bash
# Builds genlayer_dispute.mp4 (1920x1080, H.264 + AAC, X/Twitter-ready)
set -euo pipefail
cd "$(dirname "$0")"
export NODE_PATH="${NODE_PATH:-$(npm root -g)}"
FPS="${FPS:-30}"
WORKERS="${WORKERS:-2}"

node audio.js
node render.js video "$FPS" "$WORKERS"
ffmpeg -y -loglevel error -i build/video_noaudio.mp4 -i build/audio.wav \
  -map 0:v -map 1:a -c:v libx264 -profile:v high -preset slow -crf 24 -pix_fmt yuv420p \
  -r "$FPS" -c:a aac -b:a 192k -ar 48000 -shortest -movflags +faststart \
  genlayer_dispute.mp4
echo "done -> genlayer_dispute.mp4"
