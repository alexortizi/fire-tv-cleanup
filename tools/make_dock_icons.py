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

# Zeus: near-black full bleed with a large white bolt
img = fill((18, 18, 22, 255)); d = ImageDraw.Draw(img)
d.polygon(P((0.62, 0.08), (0.28, 0.52), (0.46, 0.52), (0.36, 0.92), (0.74, 0.42), (0.54, 0.42)), fill=(245, 245, 250, 255))
save(img, "dock_zeus.png")

# Settings: tonal gray tile + large bold gear filling most of the tile
import math
img = fill((72, 72, 80, 255))
cx, cy = B / 2, B / 2
ring = Image.new("L", (B, B), 0); rd = ImageDraw.Draw(ring)
outer, inner, hub = 0.42 * B, 0.28 * B, 0.12 * B
rd.ellipse([cx - outer, cy - outer, cx + outer, cy + outer], fill=255)
rd.ellipse([cx - inner, cy - inner, cx + inner, cy + inner], fill=0)
for i in range(8):
    a = i * math.pi / 4
    rd.line([(cx + math.cos(a) * inner * 0.55, cy + math.sin(a) * inner * 0.55),
             (cx + math.cos(a) * 0.52 * B, cy + math.sin(a) * 0.52 * B)], fill=255, width=int(0.16 * B))
rd.ellipse([cx - hub, cy - hub, cx + hub, cy + hub], fill=0)
img.paste(Image.new("RGBA", (B, B), (240, 240, 245, 255)), (0, 0), ring)
save(img, "dock_settings.png")

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
