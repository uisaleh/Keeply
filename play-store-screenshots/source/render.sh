#!/bin/zsh
# Render build_real.py's HTML pages to 1080x1920 PNGs in play-store-screenshots/
cd "$(dirname "$0")" && python3 build_real.py
for f in 0*.html; do
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --hide-scrollbars \
    --allow-file-access-from-files --virtual-time-budget=4000 --window-size=1080,1920 --screenshot=../${f%.html}.png "file://$PWD/$f" 2>/dev/null
done
