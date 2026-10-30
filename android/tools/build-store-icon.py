#!/usr/bin/env python3
"""Render the 512 x 512 store icon (Zapstore, Google Play) and the 180 x 180 iPhone Home Screen icon.

WHY THIS EXISTS. The APK ships no raster icon: the launcher icon is an adaptive-icon XML built from
vectors, because density PNGs are a source of build nondeterminism. A store front-end looks for a
bitmap and shows an empty tile without one, so the store icon is supplied as Fastlane metadata and
the APK is untouched (CheckLink's tool, 2026-10-12).

The composition mirrors the adaptive icon exactly: the icon background (res/values/colors.xml,
icon_background, the page's bg-base) and favicon.svg (icon draft B, "the end of the feed", ICON-1)
placed with the same numbers as res/drawable/ic_launcher_foreground.xml: translate(28.20 27.59)
scale(0.8064) in a 108-unit square. So the store tile and the launcher icon are the same picture.
apple-touch-icon.png is that same 512 icon scaled to 180 x 180, opaque (iOS rounds the corners
itself), as make_touch_icons.py does for the other Labs apps (2026-10-21).

Rendering uses headless Chromium, the engine that draws the web app (ImageMagick drops the
gradients this SVG uses and silently writes an empty image).

    python3 android/tools/build-store-icon.py

Needs chromium on PATH and Pillow. Writes fastlane/metadata/android/en-US/images/icon.png and
apple-touch-icon.png, and refuses to write an icon in which the mark did not render.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
SVG = os.path.join(REPO, "favicon.svg")
OUT = os.path.join(REPO, "fastlane", "metadata", "android", "en-US", "images", "icon.png")
TOUCH = os.path.join(REPO, "apple-touch-icon.png")

SIZE = 512
BACKGROUND = "#0C0A12"      # res/values/colors.xml icon_background
SCALE = 0.8064              # res/drawable/ic_launcher_foreground.xml
TRANSLATE = (28.20, 27.59)


def compose():
    src = open(SVG, encoding="utf-8").read()
    inner = re.search(r"<svg([^>]*)>(.*)</svg>", src, re.S)
    if not inner:
        raise SystemExit("could not read favicon.svg")
    # favicon.svg sets fill="none" on its root; the group carries it over, or every chevron would be
    # filled black.
    fill = re.search(r'\sfill="([^"]*)"', inner.group(1))
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="%d" height="%d">'
        '<rect width="108" height="108" fill="%s"/>'
        '<g transform="translate(%s %s) scale(%s)"%s>%s</g></svg>'
        % (SIZE, SIZE, BACKGROUND, TRANSLATE[0], TRANSLATE[1], SCALE,
           ' fill="%s"' % fill.group(1) if fill else "", inner.group(2))
    )


def check(img):
    bg = tuple(int(BACKGROUND[i:i + 2], 16) for i in (1, 3, 5))
    w, h = img.size
    samples = [img.getpixel((x, y)) for y in range(0, h, 2) for x in range(0, w, 2)]
    marked = sum(1 for p in samples if sum(abs(a - b) for a, b in zip(p, bg)) > 30)
    if marked < len(samples) // 20:
        raise SystemExit("the mark did not render; refusing to ship a blank icon")
    return marked, len(samples)


def main():
    browser = shutil.which("chromium") or shutil.which("google-chrome") or shutil.which("chromium-browser")
    if not browser:
        raise SystemExit("needs chromium or google-chrome on PATH")
    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "icon.html")
        shot = os.path.join(tmp, "shot.png")
        with open(page, "w", encoding="utf-8") as f:
            f.write(
                "<!doctype html><html><head><style>html,body{margin:0;padding:0;background:%s;overflow:hidden}"
                "svg{display:block}</style></head><body>%s</body></html>" % (BACKGROUND, compose())
            )
        subprocess.run(
            [browser, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
             "--force-device-scale-factor=1", "--window-size=%d,%d" % (SIZE, SIZE),
             "--user-data-dir=" + os.path.join(tmp, "profile"),
             "--screenshot=" + shot, "file://" + page],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        img = Image.open(shot).convert("RGB")
        if img.size != (SIZE, SIZE):
            img = img.crop((0, 0, SIZE, SIZE))
    marked, total = check(img)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT, "PNG", optimize=True)
    touch = img.resize((180, 180), Image.LANCZOS)
    check(touch)
    touch.save(TOUCH, "PNG", optimize=True)
    print("wrote %s (%dx%d; %d of %d sampled pixels carry the mark)" % (OUT, SIZE, SIZE, marked, total))
    print("wrote %s (180x180, opaque)" % TOUCH)


if __name__ == "__main__":
    sys.exit(main())
