#!/usr/bin/env python3
"""Keeply Play Store screenshots (1080x1920) built from real device captures.

screens/  full-screen captures from the emulator (signed in to a real account, demo status bar)
crops/    real UI cards cut from those captures, used as floating callouts
Renders HTML here; render.sh turns it into PNGs with headless Chrome.
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = "file:///Users/saleh/Downloads/keeply/app/src/main/res/font"
SCR = f"file://{HERE}/screens"
CROP = f"file://{HERE}/crops"

CSS = f"""
@font-face {{ font-family: Jakarta; font-weight: 700; src: url({FONTS}/jakarta_bold.ttf); }}
@font-face {{ font-family: Jakarta; font-weight: 800; src: url({FONTS}/jakarta_extrabold.ttf); }}
@font-face {{ font-family: Inter; font-weight: 400; src: url({FONTS}/inter_regular.ttf); }}
@font-face {{ font-family: Inter; font-weight: 600; src: url({FONTS}/inter_semibold.ttf); }}
* {{ box-sizing: border-box; margin: 0; padding: 0; }}
html, body {{ width: 1080px; height: 1920px; overflow: hidden; }}
body {{ font-family: Inter, sans-serif; -webkit-font-smoothing: antialiased; }}

.canvas {{ position: relative; width: 1080px; height: 1920px; overflow: hidden; }}
.bg-orange {{ background: linear-gradient(160deg, #FF7A3D 0%, #FF5A1F 38%, #E5400A 72%, #B8340A 100%); color: #fff; }}
.bg-light  {{ background: linear-gradient(180deg, #FFF6F0 0%, #F5F5F2 55%, #F1EFEA 100%); color: #16161A; }}
.bg-dark   {{ background: radial-gradient(120% 70% at 50% 0%, #2A1A12 0%, #121214 55%, #0B0B0D 100%); color: #fff; }}
.blob {{ position: absolute; border-radius: 50%; filter: blur(90px); }}
.grid-lines {{ position: absolute; inset: 0; opacity: .06;
  background-image: linear-gradient(currentColor 1px, transparent 1px), linear-gradient(90deg, currentColor 1px, transparent 1px);
  background-size: 72px 72px; mask-image: linear-gradient(180deg, #000 0%, transparent 45%); }}

.copy {{ position: absolute; top: 96px; left: 0; right: 0; text-align: center; padding: 0 70px; z-index: 5; }}
.eyebrow {{ display: inline-flex; align-items: center; gap: 12px; font: 600 26px/1 Inter; letter-spacing: .14em; text-transform: uppercase;
  padding: 14px 26px; border-radius: 999px; margin-bottom: 28px; }}
.eyebrow svg {{ width: 28px; height: 28px; }}
.bg-orange .eyebrow, .bg-dark .eyebrow {{ background: rgba(255,255,255,.14); color: #fff; border: 1.5px solid rgba(255,255,255,.22); }}
.bg-light .eyebrow {{ background: #FFE6D9; color: #C2370A; }}
h1 {{ font: 800 82px/1.04 Jakarta; letter-spacing: -0.035em; }}
h1 em {{ font-style: normal; }}
.bg-light h1 em {{ background: linear-gradient(90deg, #FF5A1F, #C2370A); -webkit-background-clip: text; color: transparent; }}
.bg-dark h1 em {{ background: linear-gradient(90deg, #FFB48A, #FF6A33); -webkit-background-clip: text; color: transparent; }}
.bg-orange h1 em {{ color: #FFE1A8; }}
.sub {{ font: 400 32px/1.4 Inter; margin: 24px auto 0; max-width: 900px; }}
.bg-light .sub {{ color: #5B5B63; }}
.bg-orange .sub {{ color: rgba(255,255,255,.9); }}
.bg-dark .sub {{ color: #B4B4BC; }}

/* phone: screen keeps the device's 1080x2400 aspect */
.phone {{ position: absolute; left: 50%; top: 590px; width: 632px; height: 1365px; margin-left: -316px; border-radius: 82px;
  background: #0D0D0F; padding: 16px; z-index: 3;
  box-shadow: 0 0 0 3px #2B2B30, 0 60px 120px -20px rgba(40,10,0,.45), 0 30px 60px -30px rgba(0,0,0,.5); }}
.bg-dark .phone {{ box-shadow: 0 0 0 3px #3A3A40, 0 60px 140px -20px rgba(255,90,31,.25), 0 30px 60px -30px rgba(0,0,0,.8); }}
.screen {{ position: relative; width: 600px; height: 1333px; border-radius: 68px; overflow: hidden; background: #F5F5F2; }}
.screen img {{ width: 100%; height: 100%; display: block; }}
.punch {{ position: absolute; top: 14px; left: 50%; width: 22px; height: 22px; margin-left: -11px; border-radius: 50%; background: #050505; z-index: 2; }}

/* real UI cards cut from the captures */
.crop {{ position: absolute; z-index: 8; border-radius: 30px;
  filter: drop-shadow(0 28px 40px rgba(30,10,0,.32)) drop-shadow(0 0 1px rgba(0,0,0,.12)); }}
.bg-dark .crop {{ filter: drop-shadow(0 28px 50px rgba(0,0,0,.6)) drop-shadow(0 0 1px rgba(255,255,255,.2)); }}
.badge {{ position: absolute; z-index: 8; display: inline-flex; align-items: center; gap: 14px; padding: 18px 28px 18px 18px; border-radius: 999px;
  font: 600 27px Inter; background: #fff; color: #16161A; box-shadow: 0 24px 50px -16px rgba(30,10,0,.35); }}
.badge .t {{ width: 52px; height: 52px; border-radius: 16px; display: grid; place-items: center; }}
.badge svg {{ width: 30px; height: 30px; }}
.t-orange {{ background: #FFEADF; color: #E5400A; }}
.t-green  {{ background: #E2F5E7; color: #15803D; }}
.t-violet {{ background: #EFE9FF; color: #6D3FE0; }}
.t-pink   {{ background: #FCE7F3; color: #BE185D; }}
.t-teal   {{ background: #DDF4F1; color: #0F766E; }}
"""

ICON_JS = """<script src="https://cdn.jsdelivr.net/npm/lucide@0.460.0/dist/umd/lucide.min.js"></script>
<script>lucide.createIcons({ attrs: { 'stroke-width': 2 } });</script>"""

BLOBS = {
    "bg-orange": '<div class="blob" style="width:700px;height:700px;right:-260px;top:-200px;background:rgba(255,220,160,.45)"></div>'
                 '<div class="blob" style="width:600px;height:600px;left:-260px;top:900px;background:rgba(120,20,0,.45)"></div>',
    "bg-light": '<div class="blob" style="width:760px;height:760px;right:-300px;top:-260px;background:rgba(255,122,61,.28)"></div>'
                '<div class="blob" style="width:620px;height:620px;left:-320px;top:980px;background:rgba(255,184,62,.22)"></div>',
    "bg-dark": '<div class="blob" style="width:800px;height:800px;left:140px;top:820px;background:rgba(255,90,31,.28)"></div>',
}


def i(name):
    return f'<i data-lucide="{name}"></i>'


def crop(name, left, top, width, rot=0):
    return f'<img class="crop" src="{CROP}/{name}.png" style="left:{left}px;top:{top}px;width:{width}px;transform:rotate({rot}deg)">'


def badge(text, icon, tone, pos, rot=0):
    return f'<div class="badge" style="{pos};transform:rotate({rot}deg)"><span class="t {tone}">{i(icon)}</span>{text}</div>'


def page(name, bg, icon, eyebrow, title, sub, screen, extras=""):
    html = f"""<!doctype html><html><head><meta charset="utf-8"><title>{name}</title><style>{CSS}</style></head>
<body><div class="canvas {bg}">{BLOBS[bg]}<div class="grid-lines"></div>
  <div class="copy"><div class="eyebrow">{i(icon)}{eyebrow}</div><h1>{title}</h1><p class="sub">{sub}</p></div>
  <div class="phone"><div class="screen"><img src="{SCR}/{screen}.png"><div class="punch"></div></div></div>
  {extras}
</div>{ICON_JS}</body></html>"""
    with open(os.path.join(HERE, f"{name}.html"), "w") as f:
        f.write(html)


page("01-home", "bg-orange", "sparkles", "Your personal vault",
     "Everything important, <em>in one place</em>",
     "Receipts, warranties, subscriptions, documents, links and reminders in one private vault.",
     "home1",
     badge("Nestly renews in 2 days", "bell-ring", "t-orange", "right:28px;top:1440px", 3))

page("02-vault", "bg-light", "archive", "Your vault",
     "Every item, <em>one tap away</em>",
     "Filter by upcoming, favourites or type, and see what renews next.",
     "vault_all",
     crop("upcoming", 40, 1540, 640, -2.5))

page("03-add", "bg-dark", "circle-plus", "Add anything",
     "Save it in <em>seconds</em>",
     "Six item types, each with the right fields, plus expiry dates and reminders you choose.",
     "addsheet",
     crop("doc_remind", 470, 690, 570, 2.5))

page("04-preview", "bg-light", "file-search", "Item preview",
     "All the details, <em>at a glance</em>",
     "Amount, next payment and reminders on one page. Private numbers stay hidden until you tap Show.",
     "detail_nestly1",
     crop("doc_number", 30, 1580, 560, -3))

page("05-activity", "bg-orange", "history", "Activity timeline",
     "See what changed, <em>and when</em>",
     "Everything you've added, edited, cancelled or deleted, and what you were reminded about.",
     "activity1",
     badge("Added", "circle-plus", "t-green", "left:40px;top:1120px", -4)
     + badge("Edited", "pencil", "t-violet", "right:40px;top:1380px", 3)
     + badge("Reminded", "bell-ring", "t-pink", "left:40px;top:1640px", -2))

page("06-notifications", "bg-dark", "bell-ring", "Smart reminders",
     "Never miss a <em>renewal or expiry</em>",
     "Get notified before payments, warranties and documents are due. Snooze or mark done from the alert.",
     "settings3",
     crop("notif_nestly", 40, 1150, 800, -2) + crop("notif_passport", 240, 1470, 800, 1.5))

page("07-widget", "bg-light", "layout-grid", "Home screen widget",
     "Your spend, <em>on your home screen</em>",
     "See monthly spend and your next renewal without opening the app. Add from any app with the floating + button.",
     "home_widget",
     crop("quickadd", 30, 1480, 640, -2.5) + crop("smart", 420, 1290, 630, 2.5))

page("08-analytics", "bg-orange", "chart-line", "Profile & analytics",
     "Know where your <em>money goes</em>",
     "See spending by month or year, what's still due, and how your vault grows.",
     "profile1",
     crop("vault_chart", 640, 1200, 410, 3))

page("09-settings", "bg-dark", "lock", "Private by design",
     "Your vault. <em>Only yours.</em>",
     "Fingerprint or face lock, a privacy screen, and sync to your own account. No ads, no analytics.",
     "settings1",
     crop("privacy", 30, 1480, 620, -2.5))

print("ok")
