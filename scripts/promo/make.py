#!/usr/bin/env python3
"""
Play 스토어 홍보 영상 (30초 · 1920×1080 · H.264 + AAC) 을 만든다. 한국어판 · 영어판.

재료는 모두 앱에 이미 있는 것:
  - 그림: android/app/src/main/assets/plates/ (공공 영역 · CC0, 출처는 data/*/chapter*.json 의 plate)
  - 낭독: android/app/src/main/assets/audio/vincent/I/c1-l1/ (편지 첫 문장 m1 · 회화 tb1 · tb3)
  - 곡: remote/audio/mozart/music/m-c7-l5.m4a (Eine kleine Nachtmusik, K. 525 · Musopen · 공공 영역)
  - 앱 화면: PromoShotsTest 가 찍은 build/screens/promo_<장면>_<ko|en>.png
  - 글꼴: android/app/src/main/res/font (Noto Serif KR · Cormorant Garamond · Crimson Pro)

쓰는 법:  python3 scripts/promo/make.py --lang ko --shots android/app/build/screens --out out/promo_ko.mp4
필요한 것: python3 + Pillow, ffmpeg.

장면 (초):  0–4 그림 · 4–10 편지 도착 · 10–15 낱말 아래 뜻 · 15–21 회화 · 21–26 세 작품 · 26–30 앱 아이콘
자막은 한 줄, 화면 아래쪽 1/4 (소리 없이도 이해되게).
"""
import argparse
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS, DUR = 1920, 1080, 30, 30.0
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "android/app/src/main/assets")
FONTS = os.path.join(ROOT, "android/app/src/main/res/font")
BG = (18, 14, 11)
INK = (241, 232, 222)
SOFT = (181, 166, 151)
GOLD = (217, 174, 85)
XF = 0.5  # 장면 사이 겹침 (초)

TEXT = {
    "ko": {
        "s1": "1888년 2월, 빈센트가 편지를 보냈어요",
        "s2": "진짜 편지로, 원어 그대로",
        "s3": "낱말마다 뜻이 함께",
        "s4": "그 사람과 직접 말해요",
        "names": ["빈센트", "볼프강", "나폴레옹"],
        "s5": "프랑스어 · 독일어 · 영어 · 한국어",
        "title": "언어 미술관",
        "s6": "편지로 배우는 언어 · 무료로 시작",
        "credit1": "빈센트 반 고흐, 〈밀짚모자를 쓴 자화상〉, 1887 · The Metropolitan Museum of Art · CC0",
        "credit5": "곡: 모차르트 〈아이네 클라이네 나흐트무지크〉 K. 525 · Musopen · 공공 영역",
    },
    "en": {
        "s1": "February 1888. Vincent writes to you.",
        "s2": "Real letters, in the original.",
        "s3": "Every word, explained.",
        "s4": "Talk with them.",
        "names": ["Vincent", "Wolfgang", "Napoleon"],
        "s5": "Learn French, German, English, Korean",
        "title": "Lingua Museum",
        "s6": "Learn languages through real letters · Start free",
        "credit1": "Vincent van Gogh, Self-Portrait with a Straw Hat, 1887 · The Metropolitan Museum of Art · CC0",
        "credit5": "Music: Mozart, Eine kleine Nachtmusik, K. 525 · Musopen · public domain",
    },
}


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)


def fonts(lang):
    if lang == "ko":
        return {"cap": font("kr_semibold.ttf", 54), "title": font("kr_semibold.ttf", 96), "name": font("kr_semibold.ttf", 44),
                "small": font("kr_regular.ttf", 22)}
    return {"cap": font("display_semibold.ttf", 66), "title": font("display_semibold.ttf", 112), "name": font("display_semibold.ttf", 52),
            "small": font("text_regular.ttf", 24)}


def ease(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def cover(im, w, h, cy=0.5):
    """[im] 을 w×h 에 꽉 차게 (가운데 맞춤, 세로 위치 cy)."""
    s = max(w / im.width, h / im.height)
    r = im.resize((max(w, round(im.width * s)), max(h, round(im.height * s))), Image.LANCZOS)
    x = (r.width - w) // 2
    y = int((r.height - h) * cy)
    return r.crop((x, y, x + w, y + h))


def blurred_bg(im, dim=0.32):
    b = cover(im, W // 4, H // 4).filter(ImageFilter.GaussianBlur(6)).resize((W, H), Image.BILINEAR)
    return Image.blend(Image.new("RGB", (W, H), BG), b, dim)


def rounded(im, r):
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    out = im.convert("RGBA")
    out.putalpha(m)
    return out


def phone(shot, height):
    """화면 사진을 휴대폰 모양 테두리에 넣는다 (RGBA)."""
    bez = max(10, height // 60)
    sh = height - 2 * bez
    sw = round(shot.width * sh / shot.height)
    screen = rounded(shot.convert("RGB").resize((sw, sh), Image.LANCZOS), bez * 2)
    pw, ph = sw + 2 * bez, height
    pad = 40
    out = Image.new("RGBA", (pw + 2 * pad, ph + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("L", out.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle((pad, pad + 12, pad + pw, pad + ph + 12), bez * 3, fill=150)
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    out.paste((0, 0, 0, 255), (0, 0), shadow)
    d = ImageDraw.Draw(out)
    d.rounded_rectangle((pad, pad, pad + pw, pad + ph), bez * 3, fill=(12, 10, 9, 255), outline=(70, 60, 52, 255), width=2)
    out.alpha_composite(screen, (pad + bez, pad + bez))
    return out


def text_layer(txt, f, color, cy, alpha=1.0, band=False):
    """한 줄 글 (가운데). [band] 이면 아래쪽에 옅은 어둠 띠."""
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if band:
        g = Image.new("L", (1, 340))
        for y in range(340):
            g.putpixel((0, y), int(200 * ease(y / 339)))
        lay.paste((0, 0, 0, 255), (0, H - 340), g.resize((W, 340)))
    d = ImageDraw.Draw(lay)
    bb = d.textbbox((0, 0), txt, font=f)
    tw = bb[2] - bb[0]
    if tw > W - 160:  # 너무 길면 줄여서 한 줄로
        f = f.font_variant(size=int(f.size * (W - 160) / tw))
        bb = d.textbbox((0, 0), txt, font=f)
        tw = bb[2] - bb[0]
    th = bb[3] - bb[1]
    x = (W - tw) // 2 - bb[0]
    y = int(cy - th / 2) - bb[1]
    d.text((x + 2, y + 3), txt, font=f, fill=(0, 0, 0, 160))
    d.text((x, y), txt, font=f, fill=color + (255,))
    if alpha < 1.0:
        a = lay.getchannel("A").point(lambda v: int(v * alpha))
        lay.putalpha(a)
    return lay


def small_right(txt, f, alpha=1.0):
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    bb = d.textbbox((0, 0), txt, font=f)
    d.text((W - 48 - (bb[2] - bb[0]), H - 44 - (bb[3] - bb[1])), txt, font=f, fill=SOFT + (int(230 * alpha),))
    return lay


CAP_Y = 945  # 아래쪽 1/4 (810–1080) 의 가운데


class Promo:
    def __init__(self, lang, shots):
        self.lang, self.t, self.f = lang, TEXT[lang], fonts(lang)
        plate = lambda n: Image.open(os.path.join(ASSETS, "plates", n)).convert("RGB")
        self.p1 = plate("c1-l1.webp")
        self.bg = blurred_bg(self.p1)
        self.bg1 = blurred_bg(self.p1, 0.42)
        self.heroes = [plate("c1-l3.webp"), plate("m-c6-l6.webp"), plate("n-c1-l1.webp")]
        self.phones = {}
        for k in ("arrival", "gloss", "talk"):
            p = os.path.join(shots, f"promo_{k}_{lang}.png")
            if not os.path.isfile(p):
                sys.exit(f"화면 사진이 없어요: {p}")
            self.phones[k] = phone(Image.open(p), 770)
        icon = os.path.join(ROOT, "design/store/icon-512.png")
        self.icon = rounded(Image.open(icon).convert("RGB").resize((300, 300), Image.LANCZOS), 66)
        self.caps = {}
        b = [0, 4, 10, 15, 21, 26, 30]
        self.scenes = [(b[i], b[i + 1], fn) for i, fn in enumerate([self.s1, self.s2, self.s3, self.s4, self.s5, self.s6])]

    def cap(self, key, lt, band=True):
        a = ease((lt - 0.35) / 0.4)
        if a <= 0:
            return None
        k = (key, round(a, 2))
        if k not in self.caps:
            self.caps[k] = text_layer(self.t[key], self.f["cap"], INK, CAP_Y, a, band)
        return self.caps[k]

    # 0–4 · 그림이 천천히 다가옴
    def s1(self, lt, dur):
        z = 1.0 + 0.10 * (lt / dur)
        h = int(1060 * z)
        pw = round(self.p1.width * h / self.p1.height)
        pic = self.p1.resize((pw, h), Image.BILINEAR)
        frame = self.bg1.copy()
        frame.paste(pic, ((W - pw) // 2, (H - h) // 2 - int(40 * (z - 1) * 10)))
        frame = frame.convert("RGBA")
        c = self.cap("s1", lt)
        if c:
            frame.alpha_composite(c)
        if lt > 0.8:
            frame.alpha_composite(small_right(self.t["credit1"], self.f["small"], ease((lt - 0.8) / 0.5)))
        return frame.convert("RGB")

    def phone_scene(self, key, cap, lt, dur):
        frame = self.bg.copy().convert("RGBA")
        ph = self.phones[key]
        rise = int(30 * (1 - ease(lt / 0.8)))
        frame.alpha_composite(ph, ((W - ph.width) // 2, 30 - 40 + rise))
        c = self.cap(cap, lt, band=False)
        if c:
            frame.alpha_composite(c)
        return frame.convert("RGB")

    def s2(self, lt, dur): return self.phone_scene("arrival", "s2", lt, dur)
    def s3(self, lt, dur): return self.phone_scene("gloss", "s3", lt, dur)
    def s4(self, lt, dur): return self.phone_scene("talk", "s4", lt, dur)

    # 21–26 · 세 작품이 차례로
    def s5(self, lt, dur):
        frame = Image.new("RGBA", (W, H), BG + (255,))
        tw, th, gap = 500, 600, 70
        x0 = (W - (3 * tw + 2 * gap)) // 2
        for i, im in enumerate(self.heroes):
            a = ease((lt - 0.15 - i * 0.45) / 0.5)
            if a <= 0:
                continue
            z = 1.0 + 0.06 * (lt / dur)
            tile = cover(im, int(tw * z), int(th * z), 0.3)
            tile = tile.crop(((tile.width - tw) // 2, (tile.height - th) // 2, (tile.width - tw) // 2 + tw, (tile.height - th) // 2 + th))
            tile = rounded(tile, 10)
            if a < 1:
                tile.putalpha(tile.getchannel("A").point(lambda v: int(v * a)))
            x = x0 + i * (tw + gap)
            frame.alpha_composite(tile, (x, 80 + int(24 * (1 - a))))
            d = ImageDraw.Draw(frame)
            nm = self.t["names"][i]
            bb = d.textbbox((0, 0), nm, font=self.f["name"])
            d.text((x + (tw - (bb[2] - bb[0])) // 2 - bb[0], 712 - bb[1]), nm, font=self.f["name"], fill=GOLD + (int(255 * a),))
        c = self.cap("s5", lt + 0.6, band=False)
        if c:
            frame.alpha_composite(c)
        frame.alpha_composite(small_right(self.t["credit5"], self.f["small"], ease((lt - 1.0) / 0.5)))
        return frame.convert("RGB")

    # 26–30 · 앱 아이콘
    def s6(self, lt, dur):
        frame = Image.new("RGBA", (W, H), BG + (255,))
        a = ease(lt / 0.6)
        ic = self.icon.copy()
        ic.putalpha(ic.getchannel("A").point(lambda v: int(v * a)))
        frame.alpha_composite(ic, ((W - 300) // 2, 170 + int(20 * (1 - a))))
        frame.alpha_composite(text_layer(self.t["title"], self.f["title"], INK, 620, a))
        c = self.cap("s6", lt, band=False)
        if c:
            frame.alpha_composite(c)
        g = ease((lt - 1.0) / 0.5)
        if g > 0:
            frame.alpha_composite(text_layer("Google Play", self.f["small"].font_variant(size=30), GOLD, 1030, g))
        return frame.convert("RGB")

    def frame(self, t):
        parts = []
        for i, (a, b, fn) in enumerate(self.scenes):
            s = a - (XF / 2 if i > 0 else 0)
            e = b + (XF / 2 if i < len(self.scenes) - 1 else 0)
            if s <= t < e:
                w = 1.0
                if i > 0 and t < a + XF / 2:
                    w = (t - s) / XF
                parts.append((fn(t - s, e - s), w))
        if not parts:
            return Image.new("RGB", (W, H), BG)
        img = parts[0][0]
        if len(parts) > 1:
            img = Image.blend(parts[0][0], parts[1][0], ease(parts[1][1]))
        # 처음 · 끝 어둠
        k = min(ease(t / 0.4), ease((DUR - t) / 0.5))
        if k < 1:
            img = Image.blend(Image.new("RGB", (W, H), (0, 0, 0)), img, k)
        return img


def audio_args(lang):
    a = os.path.join(ASSETS, "audio/vincent/I/c1-l1")
    music = os.path.join(ROOT, "remote/audio/mozart/music/m-c7-l5.m4a")
    clips = [  # (파일, 시작 초, 길이 상한, 음량)
        (os.path.join(a, "m1_fr.m4a"), 0.7, 12.0, 1.0),
        (os.path.join(a, "tb1_fr.m4a"), 15.7, 3.0, 1.0),
        (os.path.join(a, "tb3_fr.m4a"), 18.4, 2.6, 1.0),
        (music, 20.8, 9.2, 0.55),
    ]
    for p, *_ in clips:
        if not os.path.isfile(p):
            sys.exit(f"소리 파일이 없어요: {p}")
    ins, flt, labels = [], [], []
    for i, (p, at, cap, vol) in enumerate(clips):
        ins += ["-i", p]
        n = i + 1
        fo = 1.4 if p == music else 0.25
        flt.append(f"[{n}:a]aformat=sample_rates=48000:channel_layouts=stereo,atrim=0:{cap},asetpts=PTS-STARTPTS,"
                   f"afade=t=in:d={0.6 if p == music else 0.02},afade=t=out:st={cap - fo}:d={fo},volume={vol},adelay={int(at * 1000)}|{int(at * 1000)}[a{n}]")
        labels.append(f"[a{n}]")
    flt.append("".join(labels) + f"amix=inputs={len(labels)}:normalize=0:duration=longest,atrim=0:{DUR},afade=t=out:st={DUR - 0.6}:d=0.6[aout]")
    return ins, ";".join(flt)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", choices=["ko", "en"], required=True)
    ap.add_argument("--shots", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--frames", help="이 초들의 한 장면만 PNG 로 (점검용, 쉼표로)")
    a = ap.parse_args()
    p = Promo(a.lang, a.shots)
    if a.frames:
        for s in a.frames.split(","):
            p.frame(float(s)).save(f"{a.out}_{s}.png")
        return
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    ins, flt = audio_args(a.lang)
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-"] + ins + [
        "-filter_complex", flt, "-map", "0:v", "-map", "[aout]",
        "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-profile:v", "high", "-r", str(FPS),
        "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-movflags", "+faststart", "-t", str(DUR), a.out]
    ff = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    n = int(DUR * FPS)
    for i in range(n):
        ff.stdin.write(p.frame(i / FPS).tobytes())
        if i % 150 == 0:
            print(f"{a.lang}: {i}/{n}", flush=True)
    ff.stdin.close()
    if ff.wait() != 0:
        sys.exit("ffmpeg 실패")
    print("만듦:", a.out)


if __name__ == "__main__":
    main()
