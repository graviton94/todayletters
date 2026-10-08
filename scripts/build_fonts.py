#!/usr/bin/env python3
"""앱 글꼴: 공개(OFL) 글꼴을 받아 실제로 쓰는 글자만 남겨 res/font 로.

    pip install fonttools && python3 scripts/build_fonts.py
    python3 scripts/build_fonts.py --check     # CI: 앱에 나오는 한글을 글꼴이 다 갖고 있는지

- Cormorant Garamond 500 · 600 · 이탤릭 500: 제목 · 이름 · 숫자 · 서명 (라틴)
- Crimson Pro 400 · 500 · 이탤릭 400: 편지 · 본문 (라틴, 프랑스어 · 독일어 · 네덜란드어 악센트 포함)
- Cinzel 500: 작은 대문자 라벨 (라틴만)
- Noto Serif KR 400 · 600: 한글 (앱 글자 + 편지 데이터에 나오는 글자만)
화면은 글자의 언어에 따라 라틴 글꼴과 한글 글꼴 중 하나를 고른다 (design/Theme.kt Faces).
"""
import glob, json, os, sys, urllib.request
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, ".cache")
OUT = os.path.join(ROOT, "android", "app", "src", "main", "res", "font")
SRC = {
    "CormorantGaramond[wght].ttf": "cormorantgaramond/CormorantGaramond%5Bwght%5D.ttf",
    "CormorantGaramond-Italic[wght].ttf": "cormorantgaramond/CormorantGaramond-Italic%5Bwght%5D.ttf",
    "CrimsonPro[wght].ttf": "crimsonpro/CrimsonPro%5Bwght%5D.ttf",
    "CrimsonPro-Italic[wght].ttf": "crimsonpro/CrimsonPro-Italic%5Bwght%5D.ttf",
    "Cinzel[wght].ttf": "cinzel/Cinzel%5Bwght%5D.ttf",
    "NotoSerifKR[wght].ttf": "notoserifkr/NotoSerifKR%5Bwght%5D.ttf",
}
PUNCT = "‘’“”«»‹›–—…·•¶❦№"
LATIN = "".join(chr(c) for c in range(0x20, 0x250)) + PUNCT


def fetch():
    os.makedirs(CACHE, exist_ok=True)
    for name, path in SRC.items():
        p = os.path.join(CACHE, name)
        if not os.path.exists(p):
            urllib.request.urlretrieve("https://raw.githubusercontent.com/google/fonts/main/ofl/" + path, p)


def korean_text():
    chars = set()
    s = json.load(open(os.path.join(ROOT, "design", "strings.json"), encoding="utf-8"))
    for k, v in s.items():
        if not k.startswith("_"):
            chars |= set(v["ko"] if isinstance(v, dict) else v)
    for f in glob.glob(os.path.join(ROOT, "data", "**", "*.json"), recursive=True):
        chars |= set(open(f, encoding="utf-8").read())
    for f in glob.glob(os.path.join(ROOT, "android", "app", "src", "main", "java", "**", "*.kt"), recursive=True):
        chars |= set(open(f, encoding="utf-8").read())
    chars |= set("0123456789년월일주시분오전후통장 ")
    return "".join(sorted(c for c in chars if c >= " " and c not in "\t\n"))


def make(src, out, text, wght=None):
    f = TTFont(os.path.join(CACHE, src))
    if wght is not None and "fvar" in f:
        f = instancer.instantiateVariableFont(f, {"wght": wght})
    opt = subset.Options()
    opt.layout_features = ["*"]
    opt.name_IDs = ["*"]
    opt.notdef_outline = True
    sub = subset.Subsetter(opt)
    sub.populate(text=text)
    sub.subset(f)
    f.save(os.path.join(OUT, out))
    return os.path.getsize(os.path.join(OUT, out))


def check():
    cm = TTFont(os.path.join(OUT, "kr_regular.ttf")).getBestCmap()
    missing = sorted(c for c in korean_text() if "가" <= c <= "힣" and ord(c) not in cm)
    if missing:
        print("글꼴에 없는 글자:", "".join(missing), "→ python3 scripts/build_fonts.py")
        sys.exit(1)
    print("fonts ok")


def main():
    fetch()
    os.makedirs(OUT, exist_ok=True)
    ko = korean_text() + LATIN
    sizes = {
        "display_medium.ttf": make("CormorantGaramond[wght].ttf", "display_medium.ttf", LATIN, 500),
        "display_semibold.ttf": make("CormorantGaramond[wght].ttf", "display_semibold.ttf", LATIN, 600),
        "display_italic.ttf": make("CormorantGaramond-Italic[wght].ttf", "display_italic.ttf", LATIN, 500),
        "text_regular.ttf": make("CrimsonPro[wght].ttf", "text_regular.ttf", LATIN, 400),
        "text_medium.ttf": make("CrimsonPro[wght].ttf", "text_medium.ttf", LATIN, 500),
        "text_italic.ttf": make("CrimsonPro-Italic[wght].ttf", "text_italic.ttf", LATIN, 400),
        "caps.ttf": make("Cinzel[wght].ttf", "caps.ttf", LATIN, 500),
        "kr_regular.ttf": make("NotoSerifKR[wght].ttf", "kr_regular.ttf", ko, 400),
        "kr_semibold.ttf": make("NotoSerifKR[wght].ttf", "kr_semibold.ttf", ko, 600),
    }
    print(len(ko), "chars;", {k: f"{v / 1024:.0f}KB" for k, v in sizes.items()})


if __name__ == "__main__":
    check() if "--check" in sys.argv else main()
