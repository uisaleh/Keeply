#!/usr/bin/env python3
"""Generates Keeply Play Store screenshots (1080x1920) as HTML, rendered later by headless Chrome."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = "file:///Users/saleh/Downloads/keeply/app/src/main/res/font"
LOGO = "file:///Users/saleh/Downloads/keeply/brand/keeply-logo-rounded.png"

CSS = f"""
@font-face {{ font-family: Jakarta; font-weight: 500; src: url({FONTS}/jakarta_medium.ttf); }}
@font-face {{ font-family: Jakarta; font-weight: 600; src: url({FONTS}/jakarta_semibold.ttf); }}
@font-face {{ font-family: Jakarta; font-weight: 700; src: url({FONTS}/jakarta_bold.ttf); }}
@font-face {{ font-family: Jakarta; font-weight: 800; src: url({FONTS}/jakarta_extrabold.ttf); }}
@font-face {{ font-family: Inter; font-weight: 400; src: url({FONTS}/inter_regular.ttf); }}
@font-face {{ font-family: Inter; font-weight: 500; src: url({FONTS}/inter_medium.ttf); }}
@font-face {{ font-family: Inter; font-weight: 600; src: url({FONTS}/inter_semibold.ttf); }}

* {{ box-sizing: border-box; margin: 0; padding: 0; }}
html, body {{ width: 1080px; height: 1920px; overflow: hidden; }}
body {{ font-family: Inter, sans-serif; -webkit-font-smoothing: antialiased; }}

/* ---------- canvas ---------- */
.canvas {{ position: relative; width: 1080px; height: 1920px; overflow: hidden; }}
.bg-orange {{ background: linear-gradient(160deg, #FF7A3D 0%, #FF5A1F 38%, #E5400A 72%, #B8340A 100%); color: #fff; }}
.bg-light  {{ background: linear-gradient(180deg, #FFF6F0 0%, #F5F5F2 55%, #F1EFEA 100%); color: #16161A; }}
.bg-dark   {{ background: radial-gradient(120% 70% at 50% 0%, #2A1A12 0%, #121214 55%, #0B0B0D 100%); color: #fff; }}
.blob {{ position: absolute; border-radius: 50%; filter: blur(90px); pointer-events: none; }}
.grid-lines {{ position: absolute; inset: 0; opacity: .06;
  background-image: linear-gradient(currentColor 1px, transparent 1px), linear-gradient(90deg, currentColor 1px, transparent 1px);
  background-size: 72px 72px; mask-image: linear-gradient(180deg, #000 0%, transparent 45%); }}

.copy {{ position: absolute; top: 108px; left: 0; right: 0; text-align: center; padding: 0 80px; z-index: 5; }}
.eyebrow {{ display: inline-flex; align-items: center; gap: 12px; font: 600 26px/1 Inter; letter-spacing: .14em; text-transform: uppercase;
  padding: 14px 26px; border-radius: 999px; margin-bottom: 30px; }}
.eyebrow svg {{ width: 28px; height: 28px; }}
.bg-orange .eyebrow, .bg-dark .eyebrow {{ background: rgba(255,255,255,.14); color: #fff; border: 1.5px solid rgba(255,255,255,.22); }}
.bg-light .eyebrow {{ background: #FFE6D9; color: #C2370A; }}
h1 {{ font: 800 84px/1.04 Jakarta; letter-spacing: -0.035em; }}
h1 em {{ font-style: normal; }}
.bg-light h1 em {{ background: linear-gradient(90deg, #FF5A1F, #C2370A); -webkit-background-clip: text; color: transparent; }}
.bg-dark h1 em {{ background: linear-gradient(90deg, #FFB48A, #FF6A33); -webkit-background-clip: text; color: transparent; }}
.bg-orange h1 em {{ color: #FFE1A8; }}
.sub {{ font: 400 34px/1.42 Inter; margin: 26px auto 0; max-width: 860px; }}
.bg-light .sub {{ color: #5B5B63; }}
.bg-orange .sub {{ color: rgba(255,255,255,.88); }}
.bg-dark .sub {{ color: #B4B4BC; }}

/* ---------- phone ---------- */
.phone {{ position: absolute; left: 50%; top: 572px; width: 656px; height: 1312px; margin-left: -328px; border-radius: 86px;
  background: #0D0D0F; padding: 16px; z-index: 3;
  box-shadow: 0 0 0 3px #2B2B30, 0 60px 120px -20px rgba(40,10,0,.45), 0 30px 60px -30px rgba(0,0,0,.5); }}
.bg-dark .phone {{ box-shadow: 0 0 0 3px #3A3A40, 0 60px 140px -20px rgba(255,90,31,.25), 0 30px 60px -30px rgba(0,0,0,.8); }}
.screen {{ position: relative; width: 624px; height: 1280px; border-radius: 72px; overflow: hidden; }}
.ui {{ position: absolute; top: 0; left: 0; width: 390px; height: 800px; transform: scale(1.6); transform-origin: 0 0;
  font-family: Inter; color: #16161A; background: #F5F5F2; overflow: hidden; }}
.ui.dark {{ background: #0E0E10; color: #F4F4F5; }}
.punch {{ position: absolute; top: 18px; left: 50%; width: 22px; height: 22px; margin-left: -11px; border-radius: 50%; background: #050505; z-index: 50; }}

.status {{ height: 44px; display: flex; align-items: center; justify-content: space-between; padding: 0 26px 0 30px; font: 600 14px Inter; }}
.status .ic {{ display: flex; gap: 5px; align-items: center; }}
.status svg {{ width: 15px; height: 15px; stroke-width: 2.4; }}

.pad {{ padding: 0 18px; }}
.row {{ display: flex; align-items: center; }}
.between {{ justify-content: space-between; }}
.gap6 {{ gap: 6px; }} .gap8 {{ gap: 8px; }} .gap10 {{ gap: 10px; }} .gap12 {{ gap: 12px; }}
.grow {{ flex: 1; min-width: 0; }}
.h-title {{ font: 800 28px/1.1 Jakarta; letter-spacing: -.02em; }}
.sec {{ font: 700 15px Jakarta; margin: 18px 0 9px; display: flex; justify-content: space-between; align-items: center; }}
.sec a {{ font: 500 13px Inter; color: #E04A12; }}
.dark .sec a {{ color: #FF8A5C; }}
.muted {{ color: #6B6B73; }}
.dark .muted {{ color: #A1A1AA; }}

.card {{ background: #fff; border-radius: 20px; }}
.dark .card {{ background: #1B1B1F; }}
.list .item {{ display: flex; align-items: center; gap: 12px; padding: 12px 14px; }}
.list .item + .item {{ border-top: 1px solid #EFEEEA; }}
.dark .list .item + .item {{ border-top-color: #26262B; }}
.t1 {{ font: 600 14px/1.25 Inter; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }}
.t2 {{ font: 400 12px/1.35 Inter; color: #6B6B73; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }}
.dark .t2 {{ color: #A1A1AA; }}
.amt {{ font: 700 14px Jakarta; white-space: nowrap; }}

.tile {{ width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; flex: none; }}
.tile svg {{ width: 20px; height: 20px; }}
.mono {{ width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; flex: none; font: 800 17px Jakarta; color: #fff; }}
.t-orange {{ background: #FFEADF; color: #E5400A; }}
.t-green  {{ background: #E2F5E7; color: #15803D; }}
.t-violet {{ background: #EFE9FF; color: #6D3FE0; }}
.t-blue   {{ background: #E3EEFF; color: #1D5FD6; }}
.t-teal   {{ background: #DDF4F1; color: #0F766E; }}
.t-pink   {{ background: #FCE7F3; color: #BE185D; }}
.dark .t-orange {{ background: #3A2216; color: #FF8A5C; }}
.dark .t-green  {{ background: #12301C; color: #4ADE80; }}
.dark .t-violet {{ background: #261D45; color: #B39BFF; }}
.dark .t-blue   {{ background: #14264A; color: #7FB0FF; }}
.dark .t-teal   {{ background: #103230; color: #5EEAD4; }}
.dark .t-pink   {{ background: #3A1A2C; color: #F9A8D4; }}

.tag {{ font: 600 11px Inter; padding: 4px 8px; border-radius: 7px; white-space: nowrap; }}
.tag.cream {{ background: #FFF1E9; color: #C2370A; }}
.tag.gold  {{ background: #FFF4DB; color: #7A4B00; }}
.tag.green {{ background: #E3F5E8; color: #15803D; }}
.tag.red   {{ background: #FDE8E8; color: #B91C1C; }}
.tag.slate {{ background: #EDEDF0; color: #4B4B55; }}
.dark .tag.cream {{ background: #3A2216; color: #FF8A5C; }}
.dark .tag.gold  {{ background: #33270A; color: #FBBF24; }}
.dark .tag.green {{ background: #12301C; color: #4ADE80; }}

.chip {{ font: 500 13px Inter; padding: 8px 14px; border-radius: 999px; background: #fff; white-space: nowrap; display: inline-flex; align-items: center; gap: 6px; }}
.chip.on {{ background: #16161A; color: #fff; }}
.chip svg {{ width: 15px; height: 15px; }}
.dark .chip {{ background: #1B1B1F; }}
.dark .chip.on {{ background: #F4F4F5; color: #16161A; }}

.nav {{ position: absolute; left: 0; right: 0; bottom: 0; height: 84px; background: rgba(255,255,255,.96); border-top: 1px solid #EAE9E5;
  display: flex; justify-content: space-around; align-items: flex-start; padding-top: 10px; }}
.dark .nav {{ background: rgba(20,20,23,.96); border-top-color: #2A2A2F; }}
.nav .n {{ display: flex; flex-direction: column; align-items: center; gap: 4px; font: 500 11px Inter; color: #6B6B73; width: 64px; }}
.nav .n svg {{ width: 22px; height: 22px; }}
.nav .n.on {{ color: #E5400A; }}
.nav .n.on .pill {{ background: #FFE6D9; }}
.nav .pill {{ padding: 4px 16px; border-radius: 999px; display: grid; place-items: center; }}
.nav .add {{ width: 52px; height: 52px; border-radius: 18px; margin-top: -2px; display: grid; place-items: center; color: #fff;
  background: linear-gradient(145deg, #FF7A3D, #E5400A); box-shadow: 0 8px 18px -6px rgba(229,64,10,.7); }}
.nav .add svg {{ width: 26px; height: 26px; stroke-width: 2.6; }}
.nav .home-ind {{ position: absolute; bottom: 8px; left: 50%; width: 120px; margin-left: -60px; height: 4px; border-radius: 4px; background: #16161A; opacity: .85; }}
.dark .nav .home-ind {{ background: #fff; }}

/* ---------- floating callouts (outside phone, in canvas px) ---------- */
.float {{ position: absolute; z-index: 8; background: #fff; color: #16161A; border-radius: 34px; padding: 26px 30px;
  box-shadow: 0 30px 60px -18px rgba(30,10,0,.35), 0 0 0 1px rgba(0,0,0,.04); display: flex; align-items: center; gap: 22px; }}
.float .ft {{ width: 76px; height: 76px; border-radius: 22px; display: grid; place-items: center; flex: none; }}
.float .ft svg {{ width: 38px; height: 38px; }}
.float .f1 {{ font: 700 30px/1.2 Jakarta; letter-spacing: -.01em; }}
.float .f2 {{ font: 400 25px/1.35 Inter; color: #6B6B73; margin-top: 4px; }}
.badge {{ position: absolute; z-index: 8; display: inline-flex; align-items: center; gap: 14px; padding: 20px 28px; border-radius: 999px;
  font: 600 27px Inter; background: #fff; color: #16161A; box-shadow: 0 24px 50px -16px rgba(30,10,0,.35); }}
.badge svg {{ width: 32px; height: 32px; }}
"""

ICON_JS = """
<script src="https://cdn.jsdelivr.net/npm/lucide@0.460.0/dist/umd/lucide.min.js"></script>
<script>lucide.createIcons({ attrs: { 'stroke-width': 2 } });</script>
"""


def i(name, extra=""):
    return f'<i data-lucide="{name}" {extra}></i>'


def status(dark=False):
    return f'''<div class="status"><span>9:41</span><span class="ic">{i("signal")}{i("wifi")}{i("battery-full")}</span></div>'''


def nav(active="home"):
    def n(key, icon, label):
        on = " on" if key == active else ""
        return f'<div class="n{on}"><span class="pill">{i(icon)}</span>{label}</div>'
    return f'''<div class="nav">{n("home","house","Home")}{n("vault","archive","Vault")}
      <div class="n"><div class="add">{i("plus")}</div></div>
      {n("activity","calendar-clock","Activity")}{n("settings","settings","Settings")}<div class="home-ind"></div></div>'''


def page(name, bg, eyebrow_icon, eyebrow, title, sub, screen, extras="", ui_class=""):
    blobs = {
        "bg-orange": '<div class="blob" style="width:700px;height:700px;right:-260px;top:-200px;background:rgba(255,220,160,.45)"></div>'
                     '<div class="blob" style="width:600px;height:600px;left:-260px;top:900px;background:rgba(120,20,0,.45)"></div>',
        "bg-light": '<div class="blob" style="width:760px;height:760px;right:-300px;top:-260px;background:rgba(255,122,61,.28)"></div>'
                    '<div class="blob" style="width:620px;height:620px;left:-320px;top:980px;background:rgba(255,184,62,.22)"></div>',
        "bg-dark": '<div class="blob" style="width:800px;height:800px;left:140px;top:820px;background:rgba(255,90,31,.28)"></div>',
    }[bg]
    html = f"""<!doctype html><html><head><meta charset="utf-8"><title>{name}</title><style>{CSS}</style></head>
<body><div class="canvas {bg}">{blobs}<div class="grid-lines"></div>
  <div class="copy"><div class="eyebrow">{i(eyebrow_icon)}{eyebrow}</div><h1>{title}</h1><p class="sub">{sub}</p></div>
  <div class="phone"><div class="screen"><div class="punch"></div><div class="ui {ui_class}">{screen}</div></div></div>
  {extras}
</div>{ICON_JS}</body></html>"""
    with open(os.path.join(HERE, f"{name}.html"), "w") as f:
        f.write(html)


# =====================================================================
# 1 — Home
# =====================================================================
home = f"""
{status()}
<div style="position:absolute;inset:0;height:330px;background:radial-gradient(90% 100% at 80% 0%, rgba(255,122,61,.28), transparent 70%);"></div>
<div class="pad" style="position:relative">
  <div class="row gap10" style="margin-top:6px">
    <div class="grow"><div style="font:500 14px Inter;color:#8A2E0E">Good morning</div>
      <div class="h-title" style="font-size:32px">Alex</div></div>
    <div style="width:42px;height:42px;border-radius:50%;background:#fff;display:grid;place-items:center;position:relative">{i("bell", 'style="width:20px;height:20px"')}
      <span style="position:absolute;top:-3px;right:-3px;background:#FF5A1F;color:#fff;font:600 10px Inter;border:2px solid #F7EDE6;border-radius:99px;padding:1px 5px">3</span></div>
    <div style="width:42px;height:42px;border-radius:50%;background:linear-gradient(135deg,#FFB48A,#FF5A1F);color:#fff;display:grid;place-items:center;font:700 16px Jakarta">A</div>
  </div>
  <div class="row gap8" style="margin-top:14px;height:48px;border-radius:999px;background:#fff;padding:0 16px;color:#9C9CA3;font:400 14px Inter">
    {i("search", 'style="width:18px;height:18px;color:#6B6B73"')} Search receipts, warranties, links…</div>

  <div style="margin-top:14px;border-radius:24px;padding:18px;color:#fff;position:relative;overflow:hidden;
     background:linear-gradient(135deg,#FF5A1F,#E5400A 55%,#B8340A)">
    <div style="position:absolute;right:-90px;top:-90px;width:240px;height:240px;border-radius:50%;background:radial-gradient(rgba(255,255,255,.28),transparent 70%)"></div>
    <div class="row gap6" style="font:500 13px Inter;color:rgba(255,255,255,.85)">
      <span style="width:26px;height:26px;border-radius:50%;background:rgba(255,255,255,.16);display:grid;place-items:center">{i("refresh-cw", 'style="width:14px;height:14px"')}</span>
      <span class="grow">5 active subscriptions</span>{i("chevron-right", 'style="width:18px;height:18px"')}</div>
    <div class="row" style="align-items:flex-end;margin-top:10px"><span style="font:800 40px/1 Jakarta;letter-spacing:-.03em">$64.97</span>
      <span style="font:500 14px Inter;opacity:.85;margin:0 0 5px 4px">/ month</span></div>
    <div style="font:400 14px Inter;opacity:.9;margin-top:4px">About $779.64 a year</div>
    <div class="row" style="margin-top:14px;background:rgba(255,255,255,.16);border-radius:16px;padding:10px 14px;gap:14px">
      <div class="grow"><div style="font:500 11px Inter;opacity:.75">Next renewal</div><div style="font:600 14px Inter">StreamBox</div>
        <div style="font:400 12px Inter;opacity:.85">Tomorrow · $15.49</div></div>
      <div style="width:1px;height:42px;background:rgba(255,255,255,.25)"></div>
      <div class="grow"><div style="font:500 11px Inter;opacity:.75">Due this week</div><div style="font:600 14px Inter">$27.48</div>
        <div style="font:400 12px Inter;opacity:.85">2 subscriptions</div></div>
    </div>
  </div>

  <div class="sec">Quick add</div>
  <div class="row gap8">
    {''.join(f'<div class="card" style="flex:1;padding:10px 0;display:flex;flex-direction:column;align-items:center;gap:6px;font:500 11px Inter"><div class="tile {t}" style="width:36px;height:36px">{i(ic)}</div>{l}</div>'
             for ic, t, l in [("receipt","t-orange","Receipt"),("shield-check","t-green","Warranty"),("refresh-cw","t-violet","Subscription"),("id-card","t-teal","Document"),("link","t-blue","Link")])}
  </div>

  <div class="sec">Needs attention <a>See all</a></div>
  <div class="card list">
    <div class="item"><div class="mono" style="background:#E50914">S</div><div class="grow"><div class="t1">StreamBox Premium</div><div class="t2">Renews tomorrow · $15.49</div></div><span class="tag cream">Tomorrow</span></div>
    <div class="item"><div class="tile t-green">{i("headphones")}</div><div class="grow"><div class="t1">Sony WH-1000XM5</div><div class="t2">Warranty ends 5 Nov</div></div><span class="tag gold">In 30 days</span></div>
    <div class="item"><div class="tile t-teal">{i("car")}</div><div class="grow"><div class="t1">Car insurance</div><div class="t2">Policy expires 27 Oct</div></div><span class="tag gold">In 21 days</span></div>
  </div>
</div>
{nav("home")}
"""
page("01-home", "bg-orange", "sparkles", "Your personal vault",
     "Everything important, <em>in one place</em>",
     "Receipts, warranties, subscriptions, documents and links — organised in one private vault.",
     home,
     extras=f'''<div class="badge" style="left:30px;top:515px;transform:rotate(-4deg)"><span class="tile t-green" style="width:52px;height:52px;border-radius:16px">{i("circle-check")}</span>Receipt saved</div>
     <div class="badge" style="right:30px;top:540px;transform:rotate(3deg)"><span class="tile t-violet" style="width:52px;height:52px;border-radius:16px">{i("bell-ring")}</span>Renews tomorrow</div>''')

# =====================================================================
# 2 — Spending
# =====================================================================
bars = [("May", 52), ("Jun", 58), ("Jul", 55), ("Aug", 61), ("Sep", 61), ("Oct", 65)]
bar_html = ''.join(
    f'<div style="flex:1;display:flex;flex-direction:column;align-items:center;gap:6px">'
    f'<div style="font:600 10px Inter;color:{"#E5400A" if m=="Oct" else "#9C9CA3"}">${v}</div>'
    f'<div style="width:28px;height:{int(v*1.55)}px;border-radius:9px;background:{"linear-gradient(180deg,#FF7A3D,#E5400A)" if m=="Oct" else "#F1E6DF"}"></div>'
    f'<div style="font:500 11px Inter;color:{"#16161A" if m=="Oct" else "#9C9CA3"}">{m}</div></div>' for m, v in bars)

subs = [
    ("S", "#E50914", "StreamBox Premium", "Monthly · Tomorrow", "$15.49"),
    ("T", "#1DB954", "Tunely Music Family", "Monthly · 9 Oct", "$11.99"),
    ("C", "#2563EB", "CloudDrive 2 TB", "Monthly · 18 Oct", "$9.99"),
    ("D", "#7C3AED", "Design Suite Pro", "Yearly · 2 Feb", "$239.88"),
    ("F", "#F59E0B", "FitPulse Gym", "Monthly · 1 Nov", "$7.50"),
]
spend = f"""
{status()}
<div class="pad">
  <div class="row between" style="margin-top:6px"><div class="h-title">Spending</div>
    <div class="row" style="background:#EEEDE9;border-radius:999px;padding:3px;font:600 12px Inter">
      <span style="padding:6px 12px;border-radius:999px;background:#fff">Month</span><span style="padding:6px 12px;color:#6B6B73">Year</span></div></div>

  <div class="card" style="margin-top:14px;padding:16px">
    <div class="row between"><div><div style="font:500 12px Inter" class="muted">Subscriptions this month</div>
      <div style="font:800 34px/1.15 Jakarta;letter-spacing:-.03em">$64.97</div>
      <div class="row gap6" style="font:500 12px Inter;color:#15803D;margin-top:2px">{i("trending-up", 'style="width:14px;height:14px"')}$4.00 more than September</div></div>
      <div class="tile t-violet" style="width:46px;height:46px;border-radius:14px">{i("chart-column")}</div></div>
    <div class="row" style="align-items:flex-end;margin-top:16px;height:140px">{bar_html}</div>
  </div>

  <div class="row gap8" style="margin-top:10px">
    <div class="card" style="flex:1;padding:12px 14px"><div style="font:500 11px Inter" class="muted">Per year</div><div style="font:800 19px Jakarta">$779.64</div></div>
    <div class="card" style="flex:1;padding:12px 14px"><div style="font:500 11px Inter" class="muted">Due this week</div><div style="font:800 19px Jakarta;color:#E5400A">$27.48</div></div>
    <div class="card" style="flex:1;padding:12px 14px"><div style="font:500 11px Inter" class="muted">Active</div><div style="font:800 19px Jakarta">5</div></div>
  </div>

  <div class="sec">Your subscriptions <a>Add</a></div>
  <div class="card list">
    {''.join(f'<div class="item"><div class="mono" style="background:{c}">{l}</div><div class="grow"><div class="t1">{n}</div><div class="t2">{d}</div></div><div class="amt">{p}</div></div>' for l, c, n, d, p in subs)}
  </div>
</div>
{nav("vault")}
"""
page("02-spending", "bg-light", "wallet", "Subscriptions",
     "Know exactly <em>what you spend</em>",
     "Monthly and yearly totals, what's due this week, and every renewal at a glance.",
     spend,
     )

# =====================================================================
# 3 — Reminders
# =====================================================================
def tl(day, rows):
    out = f'<div style="font:600 12px Inter;letter-spacing:.06em;text-transform:uppercase;margin:16px 0 8px" class="muted">{day}</div><div class="card list">'
    for tile, ic, t, d, tag in rows:
        out += f'<div class="item"><div class="tile {tile}">{i(ic)}</div><div class="grow"><div class="t1">{t}</div><div class="t2">{d}</div></div>{tag}</div>'
    return out + "</div>"

remind = f"""
{status()}
<div class="pad">
  <div class="h-title" style="margin-top:6px">Activity</div>
  <div class="row gap8" style="margin-top:12px"><span class="chip on">Upcoming</span><span class="chip">Recently added</span><span class="chip">History</span></div>
  {tl("Tomorrow", [("t-violet","refresh-cw","StreamBox Premium","Renews · $15.49",'<span class="tag cream">1 day</span>')])}
  {tl("This week", [("t-violet","refresh-cw","Tunely Music Family","Renews Fri 9 Oct · $11.99",'<span class="tag cream">3 days</span>'),
                    ("t-pink","alarm-clock","Return jacket to store","Sat 10 Oct, 10:00",'<span class="tag slate">4 days</span>')])}
  {tl("This month", [("t-teal","car","Car insurance","Policy expires 27 Oct",'<span class="tag gold">21 days</span>'),
                     ("t-green","headphones","Sony WH-1000XM5","Warranty ends 5 Nov",'<span class="tag gold">30 days</span>'),
                     ("t-teal","plane","Passport","Expires 2 Jun 2027",'<span class="tag green">Renew early</span>')])}
</div>
{nav("activity")}
"""
notif = lambda top, left, ic, tile, title, body, rot: f'''<div class="float" style="left:{left}px;top:{top}px;width:860px;transform:rotate({rot}deg);align-items:flex-start;padding:28px 32px">
  <img src="{LOGO}" style="width:64px;height:64px;border-radius:18px;flex:none">
  <div class="grow"><div class="row between" style="font:500 22px Inter;color:#6B6B73"><span>Keeply · now</span>{i("chevron-down", 'style="width:26px;height:26px"')}</div>
  <div class="f1" style="margin-top:6px">{title}</div><div class="f2">{body}</div></div></div>'''
page("03-reminders", "bg-dark", "bell-ring", "Smart reminders",
     "Never miss a <em>renewal or deadline</em>",
     "Get alerts before subscriptions renew, warranties end and documents expire.",
     remind, ui_class="",
     extras=notif(1400, 110, "", "", "StreamBox renews tomorrow", "$15.49 will be charged to Visa ··4821", -2)
          + notif(1610, 110, "", "", "Headphones warranty ends in 30 days", "Sony WH-1000XM5 · claim repairs before 5 Nov", 1.5))

# =====================================================================
# 4 — Warranty detail
# =====================================================================
def kv(k, v):
    return f'<div class="row between" style="padding:11px 0;border-top:1px solid #EFEEEA;font:400 13px Inter"><span class="muted">{k}</span><span style="font-weight:600">{v}</span></div>'

warranty = f"""
{status()}
<div class="pad">
  <div class="row between" style="margin-top:4px">
    <div style="width:40px;height:40px;border-radius:50%;background:#fff;display:grid;place-items:center">{i("arrow-left", 'style="width:20px;height:20px"')}</div>
    <div class="row gap8">
      <div style="width:40px;height:40px;border-radius:50%;background:#fff;display:grid;place-items:center">{i("share-2", 'style="width:18px;height:18px"')}</div>
      <div style="width:40px;height:40px;border-radius:50%;background:#fff;display:grid;place-items:center">{i("pencil", 'style="width:18px;height:18px"')}</div></div></div>
  <div class="row gap12" style="margin-top:16px"><div class="tile t-green" style="width:56px;height:56px;border-radius:16px">{i("laptop", 'style="width:28px;height:28px"')}</div>
    <div><div style="font:500 12px Inter;color:#15803D">Warranty</div><div class="h-title" style="font-size:24px">MacBook Pro 14″</div></div></div>

  <div style="margin-top:16px;border-radius:20px;padding:16px;background:linear-gradient(135deg,#E3F5E8,#D2F0DB)">
    <div class="row between"><div class="row gap6" style="font:700 15px Jakarta;color:#15803D">{i("shield-check", 'style="width:18px;height:18px"')}Covered</div>
      <span style="font:600 12px Inter;color:#15803D">13 months left</span></div>
    <div style="height:8px;border-radius:8px;background:rgba(21,128,61,.15);margin-top:12px;overflow:hidden"><div style="width:46%;height:100%;border-radius:8px;background:#15803D"></div></div>
    <div class="row between" style="font:400 11px Inter;color:#3F6B4E;margin-top:8px"><span>Bought 14 Nov 2025</span><span>Ends 14 Nov 2027</span></div>
  </div>

  <div class="card" style="margin-top:12px;padding:4px 16px">
    <div style="height:0"></div>
    {kv("Brand","Apple").replace("border-top:1px solid #EFEEEA;","")}{kv("Model","MX2E3LL/A")}{kv("Serial number","C02F•••X7QK")}{kv("Retailer","Tech Store Downtown")}{kv("Reminders","30 & 7 days before")}
  </div>

  <div class="sec">Proof of purchase</div>
  <div class="card row gap12" style="padding:12px">
    <div style="width:64px;height:78px;border-radius:10px;background:#FAFAF8;border:1px solid #EAE9E5;padding:8px 7px;flex:none">
      <div style="height:4px;background:#D5D4CF;border-radius:2px;width:70%"></div>
      {''.join('<div style="height:3px;background:#E5E4E0;border-radius:2px;margin-top:5px;width:%d%%"></div>' % w for w in (100,85,92,60,100,75))}
      <div style="height:4px;background:#FF5A1F;border-radius:2px;margin-top:6px;width:55%;margin-left:auto"></div></div>
    <div class="grow"><div class="row gap6"><span class="tile t-orange" style="width:22px;height:22px;border-radius:7px">{i("receipt", 'style="width:13px;height:13px"')}</span><span class="t1">Receipt · Tech Store</span></div>
      <div class="t2" style="margin-top:6px">14 Nov 2025 · Visa ··4821</div><div class="amt" style="margin-top:4px;font-size:17px">$1,999.00</div></div>
    {i("chevron-right", 'style="width:18px;height:18px;color:#9C9CA3"')}
  </div>
</div>
"""
page("04-warranties", "bg-light", "shield-check", "Receipts & warranties",
     "Proof of purchase, <em>always at hand</em>",
     "Save receipts with photos, turn them into warranties in one tap, and know what's still covered.",
     warranty,
     extras=f'''<div class="float" style="left:36px;top:1640px;width:560px;transform:rotate(-2.5deg);flex-direction:column;align-items:stretch;gap:18px">
       <div class="row gap12"><div class="ft t-orange" style="width:60px;height:60px;border-radius:18px">{i("receipt", 'style="width:30px;height:30px"')}</div>
       <div class="f1" style="font-size:27px">Does this product have a warranty?</div></div>
       <div class="row gap12"><span style="flex:1;text-align:center;padding:18px;border-radius:20px;background:#F2F1EE;font:600 25px Inter">Not now</span>
       <span style="flex:1;text-align:center;padding:18px;border-radius:20px;background:#FF5A1F;color:#fff;font:600 25px Inter">Yes, add it</span></div></div>''')

# =====================================================================
# 5 — Documents / Vault
# =====================================================================
docs = [
    ("plane", "Passport", "No. •••• 4471 · Expires Jun 2027", '<span class="tag green">Valid</span>'),
    ("car", "Driving licence", "Class B · Expires Mar 2029", '<span class="tag green">Valid</span>'),
    ("shield", "Car insurance", "Policy SR-20931 · Expires 27 Oct", '<span class="tag gold">21 days</span>'),
    ("heart-pulse", "Health insurance card", "Member 88213 · Expires Dec 2026", '<span class="tag cream">2 months</span>'),
    ("graduation-cap", "University degree", "Doesn't expire", '<span class="tag slate">Saved</span>'),
    ("house", "Apartment lease", "Ends 31 Aug 2027", '<span class="tag green">Valid</span>'),
]
vault = f"""
{status()}
<div class="pad">
  <div class="row between" style="margin-top:6px"><div class="h-title">Vault</div>
    <div style="width:40px;height:40px;border-radius:50%;background:#fff;display:grid;place-items:center">{i("search", 'style="width:19px;height:19px"')}</div></div>
  <div class="row gap8" style="margin-top:12px;overflow:hidden;white-space:nowrap">
    <span class="chip">All</span><span class="chip on">{i("id-card")}Documents</span><span class="chip">Receipts</span><span class="chip">Warranties</span><span class="chip">Links</span></div>

  <div style="margin-top:14px;border-radius:22px;padding:16px;color:#fff;background:linear-gradient(135deg,#0F766E,#115E59);position:relative;overflow:hidden">
    <div style="position:absolute;right:-60px;top:-60px;width:180px;height:180px;border-radius:50%;background:radial-gradient(rgba(255,255,255,.22),transparent 70%)"></div>
    <div class="row gap12"><div style="width:46px;height:46px;border-radius:14px;background:rgba(255,255,255,.16);display:grid;place-items:center">{i("plane", 'style="width:24px;height:24px"')}</div>
      <div class="grow"><div style="font:500 12px Inter;opacity:.8">Next to renew</div><div style="font:700 18px Jakarta">Passport</div></div>
      <div style="text-align:right"><div style="font:800 22px Jakarta">8 mo</div><div style="font:400 11px Inter;opacity:.8">left</div></div></div>
    <div class="row gap6" style="margin-top:12px;font:500 12px Inter;background:rgba(255,255,255,.14);border-radius:12px;padding:8px 10px">{i("bell", 'style="width:14px;height:14px"')}Reminder set for 6 months before expiry</div>
  </div>

  <div class="sec">6 documents</div>
  <div class="card list">
    {''.join(f'<div class="item"><div class="tile t-teal">{i(ic)}</div><div class="grow"><div class="t1">{t}</div><div class="t2">{d}</div></div>{tag}</div>' for ic, t, d, tag in docs)}
  </div>
</div>
{nav("vault")}
"""
page("05-documents", "bg-orange", "id-card", "Documents",
     "Your IDs and papers, <em>safely organised</em>",
     "Passports, licences, insurance and more — with expiry reminders so nothing lapses.",
     vault,
     )

# =====================================================================
# 6 — Search (dark theme)
# =====================================================================
def hl(s, q="app"):
    idx = s.lower().find(q)
    if idx < 0:
        return s
    return s[:idx] + f'<mark style="background:rgba(255,106,51,.28);color:#FFB48A;border-radius:3px;padding:0 1px">{s[idx:idx+len(q)]}</mark>' + s[idx+len(q):]

results = [
    ("t-orange", "receipt", "Apple Store", "Receipt · 14 Nov 2025 · $1,999.00"),
    ("t-green", "laptop", "MacBook Pro 14″", "Warranty · Apple · 13 months left"),
    ("t-violet", "refresh-cw", "App Store — iCloud+", "Subscription · Monthly · $2.99"),
    ("t-blue", "link", "Best apps for budgeting", "Link · financeweekly.com"),
    ("t-pink", "alarm-clock", "Apply for visa renewal", "Reminder · Tue 20 Oct, 09:00"),
]
search = f"""
{status()}
<div class="pad">
  <div class="row gap10" style="margin-top:6px">
    {i("arrow-left", 'style="width:22px;height:22px"')}
    <div class="row gap8 grow" style="height:48px;border-radius:999px;background:#1B1B1F;padding:0 16px;font:500 15px Inter;box-shadow:0 0 0 2px #FF6A33">
      {i("search", 'style="width:18px;height:18px;color:#A1A1AA"')}<span>app</span><span style="width:2px;height:20px;background:#FF6A33;margin-left:-6px"></span>
      <span class="grow"></span>{i("x", 'style="width:18px;height:18px;color:#A1A1AA"')}</div></div>
  <div class="row gap8" style="margin-top:14px"><span class="chip on">All · 5</span><span class="chip">Receipts</span><span class="chip">Warranties</span><span class="chip">Links</span></div>
  <div class="sec" style="margin-top:20px">Top results</div>
  <div class="card list">
    {''.join(f'<div class="item"><div class="tile {t}">{i(ic)}</div><div class="grow"><div class="t1">{hl(a)}</div><div class="t2">{hl(b)}</div></div>{i("chevron-right", chr(39).join(["style=", "width:18px;height:18px;color:#71717A", ""]))}</div>' for t, ic, a, b in results)}
  </div>
  <div class="sec">Recent searches</div>
  <div class="row gap8" style="flex-wrap:wrap">
    {''.join(f'<span class="chip">{i("history")}{q}</span>' for q in ("headphones","insurance","IKEA","passport"))}
  </div>
  <div class="row gap8" style="margin-top:22px;justify-content:center;font:500 12px Inter" ><span class="muted row gap6">{i("wifi-off", 'style="width:14px;height:14px"')}Works offline — searched on your phone</span></div>
</div>
"""
page("06-search", "bg-dark", "search", "Instant search",
     "Find anything <em>in seconds</em>",
     "Search your whole vault by shop, brand, product or tag. Works offline. Light & dark themes.",
     search, ui_class="dark")

# =====================================================================
# 7 — Save from anywhere (home screen + widget)
# =====================================================================
apps = [("#34A853", "phone"), ("#4285F4", "message-circle"), ("#EA4335", "mail"), ("#FBBC05", "camera")]
capture = f"""
<div style="position:absolute;inset:0;background:linear-gradient(170deg,#3B1C6E 0%,#7C2D6B 45%,#E5582A 100%)"></div>
<div style="position:absolute;inset:0;background:radial-gradient(60% 40% at 20% 30%, rgba(255,180,138,.35), transparent 70%)"></div>
<div style="position:relative;color:#fff">{status()}</div>
<div class="pad" style="position:relative">
  <div style="color:#fff;margin-top:24px;text-align:center"><div style="font:300 64px/1 Inter;letter-spacing:-.03em;font-weight:400">9:41</div>
    <div style="font:500 14px Inter;opacity:.85;margin-top:6px">Tuesday, 6 October</div></div>

  <div style="margin-top:22px;border-radius:24px;padding:16px;background:rgba(255,255,255,.16);backdrop-filter:blur(20px);border:1px solid rgba(255,255,255,.22);color:#fff">
    <div class="row between"><div class="row gap8"><img src="{LOGO}" style="width:22px;height:22px;border-radius:6px"><span style="font:600 13px Inter">Subscription spend</span></div>
      <span style="font:500 11px Inter;opacity:.75">October</span></div>
    <div class="row" style="align-items:flex-end;margin-top:10px"><span style="font:800 32px/1 Jakarta">$64.97</span><span style="font:500 13px Inter;opacity:.8;margin:0 0 3px 4px">/ month</span></div>
    <div class="row gap10" style="margin-top:12px;background:rgba(255,255,255,.14);border-radius:14px;padding:10px 12px">
      <div class="mono" style="width:32px;height:32px;border-radius:10px;font-size:14px;background:#E50914">S</div>
      <div class="grow"><div style="font:600 13px Inter">StreamBox Premium</div><div style="font:400 11px Inter;opacity:.8">Renews tomorrow</div></div>
      <div style="font:700 14px Jakarta">$15.49</div></div>
    <div class="row gap6" style="justify-content:center;margin-top:10px"><span style="width:12px;height:4px;border-radius:4px;background:#fff"></span><span style="width:4px;height:4px;border-radius:4px;background:rgba(255,255,255,.5)"></span><span style="width:4px;height:4px;border-radius:4px;background:rgba(255,255,255,.5)"></span></div>
  </div>

  <div style="margin-top:16px;border-radius:22px;padding:14px;background:#fff;color:#16161A;box-shadow:0 14px 30px -10px rgba(0,0,0,.35)">
    <div class="row gap8" style="font:500 11px Inter;color:#6B6B73"><img src="{LOGO}" style="width:18px;height:18px;border-radius:5px">Keeply · Smart suggestion<span class="grow"></span>now</div>
    <div style="font:700 14px Jakarta;margin-top:8px">Payment spotted: Tunely Music $11.99</div>
    <div style="font:400 12px Inter;color:#6B6B73;margin-top:2px">Save it as a monthly subscription?</div>
    <div class="row gap8" style="margin-top:10px"><span style="flex:1;text-align:center;padding:9px;border-radius:12px;background:#F2F1EE;font:600 12px Inter">Dismiss</span>
      <span style="flex:1;text-align:center;padding:9px;border-radius:12px;background:#FF5A1F;color:#fff;font:600 12px Inter">Save to Keeply</span></div>
  </div>
</div>
<div style="position:absolute;right:18px;top:470px;width:56px;height:56px;border-radius:50%;display:grid;place-items:center;color:#fff;background:linear-gradient(145deg,#FF7A3D,#E5400A);box-shadow:0 10px 24px -6px rgba(0,0,0,.5)">{i("plus", 'style="width:28px;height:28px;stroke-width:2.6"')}</div>
<div class="row" style="position:absolute;left:0;right:0;bottom:44px;justify-content:space-around;padding:0 30px">
  {''.join(f'<div style="width:54px;height:54px;border-radius:50%;background:{c};display:grid;place-items:center;color:#fff">{i(ic, chr(39).join(["style=", "width:24px;height:24px", ""]))}</div>' for c, ic in apps)}
</div>
<div style="position:absolute;left:30px;right:30px;bottom:116px;height:46px;border-radius:999px;background:rgba(255,255,255,.88);display:flex;align-items:center;padding:0 16px;gap:8px;color:#6B6B73;font:400 14px Inter">{i("search", 'style="width:18px;height:18px"')}Search</div>
<div style="position:absolute;bottom:10px;left:50%;width:120px;margin-left:-60px;height:4px;border-radius:4px;background:#fff"></div>
"""
page("07-capture", "bg-light", "zap", "Quick capture",
     "Save from <em>anywhere</em>",
     "Share links straight into Keeply, get smart suggestions from payment alerts, and see spend on your home screen.",
     capture,
     extras=f'''<div class="float" style="left:30px;top:1440px;width:520px;transform:rotate(-2deg)"><div class="ft t-blue">{i("share-2")}</div>
       <div><div class="f1">Save to Keeply</div><div class="f2">From any app's Share menu</div></div></div>
       <div class="badge" style="right:28px;top:760px;transform:rotate(3deg)"><span class="tile t-orange" style="width:52px;height:52px;border-radius:16px">{i("layout-grid")}</span>Home screen widget</div>''')

# =====================================================================
# 8 — Security
# =====================================================================
lock = f"""
<div style="position:absolute;inset:0;background:radial-gradient(90% 55% at 50% 30%, #FFE9DD, #F5F5F2 70%)"></div>
<div style="position:relative">{status()}</div>
<div style="position:relative;text-align:center;padding:0 30px">
  <img src="{LOGO}" style="width:92px;height:92px;border-radius:26px;margin-top:70px;box-shadow:0 18px 36px -12px rgba(229,64,10,.55)">
  <div class="h-title" style="margin-top:22px;font-size:26px">Keeply is locked</div>
  <div style="font:400 14px/1.45 Inter;color:#6B6B73;margin-top:8px">Use your fingerprint, face or screen lock to open your vault</div>
  <div style="margin:46px auto 0;width:118px;height:118px;border-radius:50%;background:#fff;display:grid;place-items:center;position:relative;box-shadow:0 0 0 12px rgba(255,90,31,.10),0 0 0 26px rgba(255,90,31,.06)">
    {i("fingerprint", 'style="width:60px;height:60px;color:#E5400A;stroke-width:1.6"')}</div>
  <div style="font:600 14px Inter;color:#E04A12;margin-top:34px">Unlock</div>
</div>
<div class="pad" style="position:absolute;left:0;right:0;bottom:40px">
  <div class="card list">
    <div class="item"><div class="tile t-green">{i("cloud")}</div><div class="grow"><div class="t1">Synced to your account</div><div class="t2">Moves with you to a new phone</div></div>{i("check", 'style="width:18px;height:18px;color:#15803D"')}</div>
    <div class="item"><div class="tile t-slate" style="background:#EDEDF0;color:#4B4B55">{i("eye-off")}</div><div class="grow"><div class="t1">Privacy screen</div><div class="t2">Hidden in recent apps</div></div>
      <span style="width:40px;height:24px;border-radius:99px;background:#FF5A1F;position:relative"><span style="position:absolute;right:3px;top:3px;width:18px;height:18px;border-radius:50%;background:#fff"></span></span></div>
  </div>
</div>
"""
page("08-security", "bg-orange", "lock", "Private by design",
     "Your vault. <em>Only yours.</em>",
     "Biometric app lock, privacy screen and secure sync to your own account. No ads, no tracking.",
     lock,
     extras=f'''<div class="badge" style="left:30px;top:1180px;transform:rotate(-4deg)"><span class="tile t-orange" style="width:52px;height:52px;border-radius:16px">{i("fingerprint")}</span>Biometric lock</div>
       <div class="badge" style="right:30px;top:1420px;transform:rotate(3deg)"><span class="tile t-green" style="width:52px;height:52px;border-radius:16px">{i("shield-check")}</span>Private to you</div>''')

print("ok")
