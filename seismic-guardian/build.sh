#!/usr/bin/env bash
# Builds seismic_guardian.mp4 (original colors) and seismic_guardian_brand.mp4
# 1920x1080, 60fps, H.264 + AAC. Also writes soundtrack.mp3 for the web page.
set -euo pipefail
cd "$(dirname "$0")"
export NODE_PATH="${NODE_PATH:-$(npm root -g)}"
FPS="${FPS:-60}"
WORKERS="${WORKERS:-4}"
[ -d node_modules/three ] || npm install --no-audit --no-fund

node audio.js
ffmpeg -y -loglevel error -i build/audio.wav -c:a libmp3lame -b:a 160k soundtrack.mp3
for PAL in original brand; do
  node render.js video "$FPS" "$WORKERS" "$PAL"
  OUT=seismic_guardian.mp4; [ "$PAL" = brand ] && OUT=seismic_guardian_brand.mp4
  ffmpeg -y -loglevel error -i "build/video_$PAL.mp4" -i build/audio.wav \
    -map 0:v -map 1:a -c:v libx264 -profile:v high -preset slow -crf 20 -pix_fmt yuv420p \
    -r "$FPS" -c:a aac -b:a 192k -ar 48000 -shortest -movflags +faststart "$OUT"
  echo "done -> $OUT"
done
