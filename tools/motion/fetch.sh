#!/bin/sh
# Gets what the videos are made from: the app's fonts and logo (from the Android project) and the app screenshots
# (from the "android-screens" release, made by the screens job of .github/workflows/android.yml).
set -e
cd "$(dirname "$0")"
mkdir -p assets shots out
cp ../../android/app/src/main/res/font/vazir_*.ttf assets/
cp ../../android/app/src/main/res/drawable-nodpi/logo.png assets/
for n in $(grep -oh "'[0-9][0-9a-z-]*'" videos/*.js | tr -d "'" | sort -u); do
  [ -f "shots/$n.png" ] || curl -sfL -o "shots/$n.png" "https://github.com/jeejjeH1/iiiii/releases/download/android-screens/$n.png" || echo "missing screenshot: $n"
done
