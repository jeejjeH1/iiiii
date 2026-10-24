#!/bin/sh
# Makes one video with its sound: ./make.sh 1-rating  →  out/1-rating-final.mp4   (no name: all of them)
set -e
cd "$(dirname "$0")"
[ -d assets ] || ./fetch.sh
for v in ${@:-$(ls videos | sed 's/\.js$//')}; do
  node render.js "$v" 30
  python3 audio.py "$v"
  ffmpeg -loglevel error -y -i "out/$v.mp4" -i "out/$v.wav" -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -shortest -movflags +faststart "out/$v-final.mp4"
  echo "ready: out/$v-final.mp4"
done
