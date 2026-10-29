"""Draw the square dock icons (YouTube, Netflix, Prime Video, AirPlay) and the icon shadow.

The logos are trademarks, so the PNGs are kept out of git: run this locally before building.

Usage:
	python3 -m venv tools/iconvenv && tools/iconvenv/bin/pip install pillow cairosvg
	tools/iconvenv/bin/python -I tools/make_dock_icons.py \
		launcher/app/src/main/res/drawable-nodpi launcher/app/src/main/res/font/open_sans.ttf

cairosvg needs the cairo library (macOS: brew install cairo).
"""
import sys, pathlib
from PIL import Image, ImageDraw, ImageFont
OUT = pathlib.Path(sys.argv[1]); FONT = sys.argv[2]
S, K = 256, 4                 # output size, supersample factor
B = S * K
R = int(B * 0.225)            # squircle corner radius (Apple-like)

def canvas(): return Image.new("RGBA", (B, B), (0, 0, 0, 0))
def shape_mask():
    # Soft preview mask only (legacy). Runtime tiles are clipped by the card outline —
    # artwork must be a full opaque square or transparent corners show as a grey frame.
    m = Image.new("L", (B, B), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, B - 1, B - 1], R, fill=255); return m
def fill(color):
    return Image.new("RGBA", (B, B), color)
def vgradient(top, bottom):
    g = Image.new("RGBA", (1, B))
    for y in range(B):
        t = y / (B - 1); g.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    return g.resize((B, B))
def save(img, name): img.resize((S, S), Image.LANCZOS).save(OUT / name)
def P(*xy): return [(x * B, y * B) for x, y in xy]

# YouTube: white tile, red rounded play button (large — fills most of the tile)
img = fill((255, 255, 255, 255)); d = ImageDraw.Draw(img)
d.rounded_rectangle([0.08 * B, 0.22 * B, 0.92 * B, 0.78 * B], int(0.12 * B), fill=(255, 0, 0, 255))
d.polygon(P((0.40, 0.34), (0.40, 0.66), (0.68, 0.50)), fill=(255, 255, 255, 255))
save(img, "dock_youtube.png")

# Netflix: black tile, red N (two dark bars, bright diagonal ribbon)
img = fill((0, 0, 0, 255)); d = ImageDraw.Draw(img)
dark, red = (177, 6, 15, 255), (229, 9, 20, 255)
d.polygon(P((0.33, 0.18), (0.44, 0.18), (0.44, 0.82), (0.33, 0.85)), fill=dark)
d.polygon(P((0.56, 0.18), (0.67, 0.18), (0.67, 0.85), (0.56, 0.82)), fill=dark)
d.polygon(P((0.33, 0.18), (0.44, 0.18), (0.67, 0.85), (0.56, 0.82)), fill=red)
save(img, "dock_netflix.png")

# Prime Video: blue tile with the official "prime video" wordmark + smile (Simple Icons, CC0), centred
LOGO = pathlib.Path(__file__).parent / "assets" / "prime_video_logo_white.png"
if not LOGO.exists():
	try:
		import urllib.request, cairosvg
		svg = urllib.request.urlopen("https://unpkg.com/simple-icons@13.0.0/icons/primevideo.svg", timeout=30).read()
		LOGO.parent.mkdir(exist_ok=True)
		cairosvg.svg2png(bytestring=svg.replace(b"<path ", b'<path fill="#FFFFFF" '), write_to=str(LOGO),
			output_width=1024, output_height=1024)
	except Exception as e:
		print("skip dock_prime:", e)
		LOGO = None
if LOGO is not None and LOGO.exists():
	img = vgradient((31, 168, 236), (0, 120, 200))
	logo = Image.open(LOGO).convert("RGBA")
	logo = logo.crop(logo.getchannel("A").getbbox())
	w = int(0.74 * B); logo = logo.resize((w, int(logo.height * w / logo.width)), Image.LANCZOS)
	img.alpha_composite(logo, ((B - logo.width) // 2, (B - logo.height) // 2))
	save(img, "dock_prime.png")

# AirPlay: graphite tile, white screen outline open at the bottom, solid triangle
img = vgradient((88, 88, 94), (40, 40, 44))
sym = Image.new("L", (B, B), 0); s = ImageDraw.Draw(sym)
s.rounded_rectangle([0.20 * B, 0.24 * B, 0.80 * B, 0.66 * B], int(0.06 * B), outline=255, width=int(0.055 * B))
s.polygon(P((0.32, 0.80), (0.50, 0.56), (0.68, 0.80)), fill=0)        # gap in the frame around the triangle
s.polygon(P((0.37, 0.78), (0.50, 0.61), (0.63, 0.78)), fill=255)
img.paste(Image.new("RGBA", (B, B), (255, 255, 255, 255)), (0, 0), sym)
save(img, "dock_airplay.png")

# Flujo TV: orange full-bleed tile + larger wordmark
img = vgradient((255, 122, 24), (230, 70, 0)); d = ImageDraw.Draw(img)
try:
    font = ImageFont.truetype(FONT, int(0.30 * B))
except Exception:
    font = ImageFont.load_default()
tw = d.textbbox((0, 0), "FLUJO", font=font)
d.text(((B - (tw[2] - tw[0])) / 2, (B - (tw[3] - tw[1])) / 2 - 0.04 * B), "FLUJO", font=font, fill=(255, 255, 255, 255))
d.arc([0.10 * B, 0.55 * B, 0.90 * B, 1.08 * B], 200, 340, fill=(255, 255, 255, 110), width=int(0.04 * B))
save(img, "dock_flujo.png")

# Amazon Appstore: dark tile, large "apps" + smile
img = fill((35, 47, 62, 255)); d = ImageDraw.Draw(img)
try:
    font = ImageFont.truetype(FONT, int(0.36 * B))
except Exception:
    font = ImageFont.load_default()
tw = d.textbbox((0, 0), "apps", font=font)
d.text(((B - (tw[2] - tw[0])) / 2, 0.22 * B), "apps", font=font, fill=(255, 255, 255, 255))
d.arc([0.14 * B, 0.42 * B, 0.86 * B, 0.98 * B], 20, 160, fill=(255, 153, 0, 255), width=int(0.055 * B))
d.polygon(P((0.78, 0.74), (0.90, 0.62), (0.86, 0.82)), fill=(255, 153, 0, 255))
save(img, "dock_appstore.png")

# Zeus browser: dark tile + white globe (reads as a browser, not a bolt)
img = fill((18, 22, 32, 255)); d = ImageDraw.Draw(img)
cx, cy, r = B / 2, B / 2, 0.36 * B
d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(235, 240, 255, 255), width=int(0.045 * B))
d.ellipse([cx - r * 0.45, cy - r, cx + r * 0.45, cy + r], outline=(235, 240, 255, 255), width=int(0.028 * B))
d.arc([cx - r, cy - r * 0.55, cx + r, cy + r * 0.55], 0, 360, fill=(235, 240, 255, 255), width=int(0.028 * B))
d.line([(cx - r, cy), (cx + r, cy)], fill=(235, 240, 255, 255), width=int(0.028 * B))
# Accent cursor / pointer like a browse affordance
d.polygon(P((0.68, 0.62), (0.86, 0.72), (0.72, 0.78), (0.70, 0.90)), fill=(80, 180, 255, 255))
save(img, "dock_zeus.png")

# Twitch / Kick: official marks from tools/assets, centered with ~45% margin (not committed).
ASSETS = pathlib.Path(__file__).parent / "assets"
tw_src = ASSETS / "twitch_launcher.png"
if tw_src.exists():
	src = Image.open(tw_src).convert("RGBA")
	mask = Image.new("L", src.size, 0)
	sp, mp = src.load(), mask.load()
	for y in range(src.size[1]):
		for x in range(src.size[0]):
			r, g, b, a = sp[x, y]
			if a > 200 and r > 200 and g > 200 and b > 200:
				mp[x, y] = 255
	bbox = mask.getbbox()
	logo = Image.new("RGBA", (bbox[2] - bbox[0], bbox[3] - bbox[1]), (0, 0, 0, 0))
	logo.paste((255, 255, 255, 255), (0, 0), mask.crop(bbox))
	target = int(S * 0.55)
	logo = logo.resize((target, int(logo.height * target / logo.width)), Image.LANCZOS)
	tile = Image.new("RGBA", (S, S), (145, 70, 255, 255))
	tile.alpha_composite(logo, ((S - logo.width) // 2, (S - logo.height) // 2))
	tile.save(OUT / "dock_twitch.png")
else:
	print("skip dock_twitch: missing", tw_src)

kick_src = ASSETS / "kick_foreground.webp"
if kick_src.exists():
	kick = Image.open(kick_src).convert("RGBA")
	pix, w, h = kick.load(), kick.size[0], kick.size[1]
	xs, ys = [], []
	for y in range(h):
		for x in range(w):
			r, g, b, a = pix[x, y]
			if a > 200 and g > 180 and r < 120:
				xs.append(x); ys.append(y)
	if xs:
		kick = kick.crop((min(xs), min(ys), max(xs) + 1, max(ys) + 1))
	kick.thumbnail((int(S * 0.48), int(S * 0.48)), Image.LANCZOS)
	logo = Image.new("RGBA", kick.size, (0, 0, 0, 0))
	lp, kp = logo.load(), kick.load()
	for y in range(kick.size[1]):
		for x in range(kick.size[0]):
			r, g, b, a = kp[x, y]
			if a > 100 and g > 80:
				lp[x, y] = (83, 252, 24, 255)
	tile = Image.new("RGBA", (S, S), (0, 0, 0, 255))
	tile.alpha_composite(logo, ((S - logo.width) // 2, (S - logo.height) // 2))
	tile.save(OUT / "dock_kick.png")
else:
	print("skip dock_kick: missing", kick_src)

# Settings: tonal gray tile + smaller gear (was oversized on the dock)
import math
img = fill((72, 72, 80, 255))
cx, cy = B / 2, B / 2
ring = Image.new("L", (B, B), 0); rd = ImageDraw.Draw(ring)
outer, inner, hub = 0.24 * B, 0.15 * B, 0.07 * B
rd.ellipse([cx - outer, cy - outer, cx + outer, cy + outer], fill=255)
rd.ellipse([cx - inner, cy - inner, cx + inner, cy + inner], fill=0)
for i in range(8):
    a = i * math.pi / 4
    rd.line([(cx + math.cos(a) * inner * 0.5, cy + math.sin(a) * inner * 0.5),
             (cx + math.cos(a) * 0.32 * B, cy + math.sin(a) * 0.32 * B)], fill=255, width=int(0.10 * B))
rd.ellipse([cx - hub, cy - hub, cx + hub, cy + hub], fill=0)
img.paste(Image.new("RGBA", (B, B), (240, 240, 245, 255)), (0, 0), ring)
save(img, "dock_settings.png")

# VLC: full orange bleed + white cone
ORANGE = (255, 128, 0, 255)
img = fill(ORANGE); d = ImageDraw.Draw(img)
d.polygon([(0.34 * B, 0.12 * B), (0.66 * B, 0.12 * B), (0.84 * B, 0.78 * B), (0.16 * B, 0.78 * B)], fill=(255, 255, 255, 255))
d.rounded_rectangle([0.10 * B, 0.74 * B, 0.90 * B, 0.90 * B], int(0.05 * B), fill=(255, 255, 255, 255))
d.polygon([(0.40 * B, 0.28 * B), (0.60 * B, 0.28 * B), (0.64 * B, 0.40 * B), (0.36 * B, 0.40 * B)], fill=ORANGE)
d.polygon([(0.32 * B, 0.50 * B), (0.68 * B, 0.50 * B), (0.74 * B, 0.64 * B), (0.26 * B, 0.64 * B)], fill=ORANGE)
save(img, "dock_vlc.png")

# DAZN: crop padding from official icon, nearly full-bleed on navy
dazn_src = ASSETS / "dazn_launcher.png"
if dazn_src.exists():
	dazn = Image.open(dazn_src).convert("RGBA")
	px, w, h = dazn.load(), dazn.size[0], dazn.size[1]
	br, bgc, bb, _ = px[2, 2]
	xs, ys = [], []
	for y in range(h):
		for x in range(w):
			r, g, b, a = px[x, y]
			if abs(r - br) + abs(g - bgc) + abs(b - bb) > 40:
				xs.append(x); ys.append(y)
	pad = 4
	crop = dazn.crop((max(0, min(xs) - pad), max(0, min(ys) - pad),
		min(w, max(xs) + 1 + pad), min(h, max(ys) + 1 + pad)))
	side = int(S * 0.92)
	crop = crop.resize((side, side), Image.LANCZOS)
	base = Image.new("RGBA", (S, S), (12, 22, 29, 255))
	base.alpha_composite(crop, ((S - side) // 2, (S - side) // 2))
	base.save(OUT / "dock_dazn.png")
else:
	print("skip dock_dazn: missing", dazn_src)

# Soft drop shadow drawn behind each dock icon: a wide, diffuse blur of a rounded square slightly larger than
# the 56dp icon, on a 120dp canvas so the blur fades out before the edges.
from PIL import ImageFilter
CANVAS_DP, BODY_DP, BLUR_DP, ALPHA = 120, 60, 10, 140
SH = CANVAS_DP * K * 2                 # 2x density, supersampled
sh = Image.new("L", (SH, SH), 0)
inset = (CANVAS_DP - BODY_DP) / 2 / CANVAS_DP * SH
ImageDraw.Draw(sh).rounded_rectangle([inset, inset, SH - inset, SH - inset], int(SH * BODY_DP / CANVAS_DP * 0.225), fill=ALPHA)
sh = sh.filter(ImageFilter.GaussianBlur(SH * BLUR_DP / CANVAS_DP))
shadow = Image.new("RGBA", (SH, SH), (0, 0, 0, 0)); shadow.putalpha(sh)
shadow.resize((CANVAS_DP * 2, CANVAS_DP * 2), Image.LANCZOS).save(OUT / "dock_shadow.png")
