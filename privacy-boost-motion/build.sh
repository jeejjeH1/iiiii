#!/usr/bin/env bash
# Builds privacy_boost_motion.mp4 (1920x1080, 60fps, H.264 + AAC, Twitter/X-ready)
set -euo pipefail
cd "$(dirname "$0")"
export NODE_PATH="${NODE_PATH:-$(npm root -g)}"
FPS="${FPS:-60}"
WORKERS="${WORKERS:-4}"

[ -f build/audio.wav ] && [ build/audio.wav -nt timeline.js ] && [ build/audio.wav -nt audio.js ] || node audio.js
node render.js video "$FPS" "$WORKERS"
ffmpeg -y -loglevel error -i build/video_noaudio.mp4 -i build/audio.wav \
  -map 0:v -map 1:a -c:v libx264 -profile:v high -preset slow -crf 20 -pix_fmt yuv420p \
  -r "$FPS" -c:a aac -b:a 192k -ar 48000 -shortest -movflags +faststart \
  privacy_boost_motion.mp4
echo "done -> privacy_boost_motion.mp4"
