#!/usr/bin/env python3
"""디자인 토큰 · 문자열을 앱 코드로 만든다.

    python3 scripts/generate.py           # 쓰기
    python3 scripts/generate.py --check   # 커밋된 결과와 다르면 실패 (CI)

- design/tokens.json  → android/app/.../design/Tokens.kt
                        android/app/src/main/res/values{,-night}/window.xml (창 · 시작 화면 바탕색)
- design/strings.json → android/app/src/main/res/values{,-ko}/strings.xml
- data/<작품>/series.json + 챕터 + 어휘 자료(lexicon · gloss · daily[_en|_ko].json) → android/app/src/main/assets/letters/<작품>/
  ("hidden": true 인 출시 전 작품은 넣지 않는다 — 판매 규칙 검사만)
- remote/ (앱 밖에 두는 낭독 · 그림 · 곡) → android/app/src/main/assets/letters/<작품>/remote.json
- data/medals.json (메달 진열장 · 레벨) → android/app/src/main/assets/medals/medals.json
- data/shop.json (상점 줄의 작은 그림과 출처) → android/app/src/main/assets/shop/shop.json
"""
import re, glob, json, os, sys
from xml.sax.saxutils import escape

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "android", "app", "src", "main")
PKG = os.path.join(APP, "java", "io", "github", "graviton94", "todayletters", "design")
HEAD = "// 자동 생성: scripts/generate.py (design/tokens.json). 손으로 고치지 말 것."


def kt_color(hexs):
    h = hexs.lstrip("#")
    h = "FF" + h if len(h) == 6 else h[6:8] + h[0:6]
    return f"Color(0x{h.upper()})"


def plain(group):
    return {k: v for k, v in group.items() if not k.startswith("_")}


def tokens():
    t = json.load(open(os.path.join(ROOT, "design", "tokens.json"), encoding="utf-8"))
    light, dark = t["color"]["light"], t["color"]["dark"]
    if light.keys() != dark.keys():
        sys.exit("tokens.json: light 와 dark 의 색 이름이 같아야 해요 (컴포넌트는 하나, 색만 바뀜)")
    L = [HEAD, "package io.github.graviton94.todayletters.design", "",
         "import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.unit.dp", "import androidx.compose.ui.unit.sp", "",
         "/** 한 테마의 색. 라이트와 다크는 이름이 같고 값만 다르다. */",
         "data class Palette(" + ", ".join(f"val {k}: Color" for k in light) + ")", "",
         "/** 보내는 사람의 봉랍: 바탕 · 안쪽 테 · 글자. 두 테마 공통. */",
         "data class Seal(val wax: Color, val ring: Color, val ink: Color)", "",
         "object Tokens {"]
    for mode in ("light", "dark"):
        c = t["color"][mode]
        L.append(f"    val {mode} = Palette(" + ", ".join(f"{k} = {kt_color(v)}" for k, v in c.items()) + ")")
    L.append("    object Seals {")
    for k, v in plain(t["seals"]).items():
        L.append(f"        val {k} = Seal({kt_color(v[0])}, {kt_color(v[1])}, {kt_color(v[2])})")
    L.append("    }")
    for group, unit in (("space", "dp"), ("radius", "dp"), ("stroke", "dp"), ("size", "dp"), ("text", "sp")):
        L.append(f"    object {group.capitalize()} {{")
        for k, v in plain(t[group]).items():
            L.append(f"        val {k} = {v}.{unit}")
        L.append("    }")
    L.append("    object Font {")
    for k, v in plain(t["font"]).items():
        L.append(f'        const val {k} = "{v}"')
    L.append("    }")
    L.append("    object Motion {")
    for k, v in plain(t["motion"]).items():
        L.append(f"        const val {k} = {v}" + ("f" if isinstance(v, float) else ""))
    L.append("    }")
    for group in ("leading", "tracking", "alpha", "ratio"):
        L.append(f"    object {group.capitalize()} {{")
        for k, v in plain(t[group]).items():
            L.append(f"        const val {k} = {float(v)}f")
        L.append("    }")
    L.append("}")
    out = {os.path.join(PKG, "Tokens.kt"): "\n".join(L) + "\n"}
    for folder, mode in (("values", "light"), ("values-night", "dark")):
        out[os.path.join(APP, "res", folder, "window.xml")] = (
            '<?xml version="1.0" encoding="utf-8"?>\n<!-- 자동 생성: scripts/generate.py (design/tokens.json) -->\n'
            f'<resources>\n    <color name="window">{t["color"][mode]["paper"]}</color>\n'
            f'    <color name="seal">{t["seals"]["vincent"][0]}</color>\n</resources>\n')
    return out


JOIN = "\u2060"


def keep_all(text):
    """한글이 낀 낱말 안의 글자 사이에 보이지 않는 이음표를 넣어, 띄어쓰기에서만 줄이 바뀌게 한다 (core/Breaks.kt 와 같음)."""
    def hangul(c):
        return "\uac00" <= c <= "\ud7a3" or "\u1100" <= c <= "\u11ff" or "\u3130" <= c <= "\u318f"
    out = []
    for i, c in enumerate(text):
        out.append(c)
        n = text[i + 1] if i + 1 < len(text) else None
        if n is None or JOIN in (c, n) or c.isspace() or n.isspace():
            continue
        if hangul(c) or hangul(n):
            out.append(JOIN)
    return "".join(out)


def strings():
    s = json.load(open(os.path.join(ROOT, "design", "strings.json"), encoding="utf-8"))
    out = {}
    for lang, folder in (("en", "values"), ("ko", "values-ko")):
        L = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- 자동 생성: scripts/generate.py (design/strings.json) -->", "<resources>"]
        for k, v in s.items():
            if k.startswith("_"):
                continue
            text = keep_all(v[lang] if isinstance(v, dict) else v)
            text = escape(text).replace("'", "\\'").replace("\n", "\\n")
            L.append(f'    <string name="{k}">{text}</string>')
        L.append("</resources>")
        out[os.path.join(APP, "res", folder, "strings.xml")] = "\n".join(L) + "\n"
    return out


def shown_metas():
    """앱에 넣는 작품의 series.json 들 ("hidden": true 인 출시 전 작품은 뺀다)."""
    return [m for m in glob.glob(os.path.join(ROOT, "data", "*", "series.json")) if not json.load(open(m, encoding="utf-8")).get("hidden")]


def letters():
    """data/<작품>/series.json + 챕터 파일 → assets/letters/<작품>/. 검토 전 챕터는 .draft.json 을 대신 쓴다.
    작품 목록은 assets/letters/index.json 에 따로 적는다 (폴더 목록 읽기에 기대지 않게)."""
    out = {}
    # 서재 순서: series.json 의 "order" (없으면 뒤로), 같으면 이름순
    # 출시 전 작품 ("hidden": true) 은 앱에 넣지 않는다: 판매 규칙만 보고, 서재에는 ‘곧 도착’ 카드가 그대로 남는다
    metas = shown_metas()
    ids = [os.path.basename(os.path.dirname(m)) for m in sorted(metas, key=lambda m: (json.load(open(m, encoding="utf-8")).get("order", 99), m))]
    out[os.path.join(APP, "assets", "letters", "index.json")] = json.dumps(ids) + "\n"
    for meta_path in glob.glob(os.path.join(ROOT, "data", "*", "series.json")):
        if meta_path not in metas:
            check_sale(os.path.dirname(meta_path), os.path.basename(os.path.dirname(meta_path)), json.load(open(meta_path, encoding="utf-8")))
    for meta_path in metas:
        folder = os.path.dirname(meta_path)
        sid = os.path.basename(folder)
        meta = json.load(open(meta_path, encoding="utf-8"))
        dest = os.path.join(APP, "assets", "letters", sid)
        out[os.path.join(dest, "series.json")] = open(meta_path, encoding="utf-8").read()
        check_sale(folder, sid, meta)
        extra = [meta[k] for k in ("prologue", "outro") if k in meta and "file" in meta[k]]
        for ch in meta["chapters"] + extra:
            src = os.path.join(folder, ch["file"])
            if not os.path.exists(src):
                src = src.replace(".json", ".draft.json")
            if os.path.exists(src):
                text = open(src, encoding="utf-8").read()
                check_spots(src, json.loads(text))
                out[os.path.join(dest, ch["file"])] = text
        # 어휘 자료 (data/lexicon/README.md): 사전 · 눌러서 뜻 보기 토큰 · 오늘의 낱말. 작아서 그대로 넣는다
        # 배우는 말마다 한 벌: 원어 lexicon.json …, 영어 lexicon_en.json …, 한국어 lexicon_ko.json … (있는 것만)
        for base in ("lexicon", "gloss", "daily"):
            for src in sorted(glob.glob(os.path.join(folder, base + "*.json"))):
                name = os.path.basename(src)
                if re.fullmatch(base + r"(_[a-z]{2})?\.json", name):
                    out[os.path.join(dest, name)] = open(src, encoding="utf-8").read()
    return out


# 시리즈 소장 값 (docs/billing.md): 편지 40통 미만 1 · 40–70통 2 · 70통 넘거나 기능이 더 붙으면 ("extra": true) 3
TIER_WON = {1: 1900, 2: 2900, 3: 3900}


def check_sale(folder, sid, meta):
    """판매 정책과 맞는가: 무료는 I · II장뿐, "product" 는 series_<작품>, "tier" 는 편지 수 (프롤로그 · 맺음 편지 포함) 에 맞게."""
    def count(name):
        path = os.path.join(folder, name)
        if not os.path.exists(path):
            path = path.replace(".json", ".draft.json")
        return len(json.load(open(path, encoding="utf-8")).get("letters", [])) if os.path.exists(path) else 0
    bad = []
    free = [bool(c.get("free")) for c in meta["chapters"]]
    if free != [i < 2 for i in range(len(free))]:
        bad.append(f"무료 장은 I · II장뿐이어야 해요: {free}")
    if meta.get("product") != f"series_{sid}":
        bad.append(f'"product" 는 "series_{sid}" 여야 해요')
    n = sum(count(meta[k]["file"]) for k in ("prologue",) if k in meta and "file" in meta[k]) + sum(count(c["file"]) for c in meta["chapters"])
    n += 1 if "outro" in meta and "file" in meta["outro"] and os.path.exists(os.path.join(folder, meta["outro"]["file"])) else 0
    want = 3 if meta.get("extra") or n > 70 else 2 if n >= 40 else 1
    if meta.get("tier") != want:
        bad.append(f'"tier" 는 {want} (₩{TIER_WON[want]:,}) 여야 해요: 편지 {n}통')
    if bad:
        sys.exit(f"data/{sid}/series.json: 판매 정책과 어긋남\n  " + "\n  ".join(bad))


def r2_key(rel, revs):
    """R2 에 놓이는 이름: 고친 파일은 rev/<판>/<경로>, 나머지는 경로 그대로 (scripts/r2_guard.py 와 같은 규칙)."""
    n = int(revs.get(rel, 1))
    return rel if n <= 1 else f"rev/{n}/{rel}"


def remote():
    """앱 밖(R2)에 두는 파일의 목록 → assets/letters/<작품>/remote.json (장마다 파일 · 크기 · 합).
    remote/ 는 assets/ 와 같은 모양이다 (remote/audio/<작품>/<장>/... · remote/plates/ · remote/sketches/ · remote/gallery/ · remote/audio/<작품>/music/).
    한 장의 묶음: 그 장의 낭독 + 그 장의 편지가 쓰는 그림 · 스케치 · 곡 가운데 remote/ 에 있는 것.
    소장품(kit.collection) 가운데 remote/ 에 둔 것은 앱 밖 장들에 차례로 나눠 담는다 (그 장을 받으면 뽑기에 들어온다).
    어느 장에도 들지 않는 파일이 remote/ 에 있으면 멈춘다 (받을 길이 없는 파일).
    고친 파일 (data/remote.json 의 "revisions": {경로: 판}) 은 R2 의 rev/<판>/<경로> 에 두고, 목록에 그 주소를 셋째 칸으로 적는다
    (폰 안 경로는 그대로라 화면 코드는 바뀌지 않고, 옛 버전 앱은 옛 주소의 옛 파일을 계속 받는다. docs/release.md 5)."""
    out = {}
    cfg = json.load(open(os.path.join(ROOT, "data", "remote.json"), encoding="utf-8"))
    R = os.path.join(ROOT, "remote")
    there = lambda rel: os.path.isfile(os.path.join(R, rel))
    every = {os.path.relpath(f, R).replace(os.sep, "/") for f in glob.glob(os.path.join(R, "**", "*"), recursive=True) if os.path.isfile(f)}
    used = set()
    revs = cfg.get("revisions", {})
    unknown = sorted(set(revs) - every)
    if unknown:
        sys.exit("data/remote.json revisions: remote/ 에 없는 파일\n  " + "\n  ".join(unknown[:20]))
    shown = set(shown_metas())
    # 출시 전 작품 ("hidden") 의 앱 밖 파일도 장 묶음을 세어 ‘받을 길 없음’ 검사에서 빼되, remote.json 은 쓰지 않는다
    for meta_path in sorted(glob.glob(os.path.join(ROOT, "data", "*", "series.json"))):
        folder = os.path.dirname(meta_path)
        sid = os.path.basename(folder)
        meta = json.load(open(meta_path, encoding="utf-8"))
        packs = {}
        for ch in meta["chapters"]:
            rels = sorted(r for r in every if r.startswith(f"audio/{sid}/{ch['id']}/"))
            src = os.path.join(folder, ch["file"])
            if not os.path.exists(src):
                src = src.replace(".json", ".draft.json")
            letters = json.load(open(src, encoding="utf-8")).get("letters", []) if os.path.exists(src) else []
            for letter in letters:
                want = []
                if (letter.get("plate") or {}).get("image"):
                    want.append("plates/" + letter["plate"]["image"])
                for m in letter.get("moments", []):
                    if m.get("image"):
                        want += ["plates/" + m["image"], "sketches/" + m["image"]]
                    if m.get("audio"):
                        want.append(f"audio/{sid}/music/" + m["audio"])
                rels += [r for r in want if there(r) and r not in rels]
            packs[ch["id"]] = rels
        late = [c for c, rels in packs.items() if rels]
        pieces = [p["image"] for p in meta.get("kit", {}).get("collection", []) if there(p["image"])]
        if pieces and not late:
            sys.exit(f"remote/: {sid} 의 소장품을 담을 앱 밖 장이 없음")
        per = -(-len(pieces) // max(1, len(late)))
        for i, c in enumerate(late):
            packs[c] += pieces[i * per:(i + 1) * per]
        chapters = {}
        for c, rels in packs.items():
            if rels:
                files = [[r, os.path.getsize(os.path.join(R, r))] + ([r2_key(r, revs)] if r in revs else []) for r in rels]
                chapters[c] = {"bytes": sum(x[1] for x in files), "files": files}
                used.update(rels)
        if chapters and meta_path in shown:
            doc = {"base": cfg["base"], "chapters": chapters}
            out[os.path.join(APP, "assets", "letters", sid, "remote.json")] = json.dumps(doc, ensure_ascii=False, separators=(",", ":")) + "\n"
    if every - used:
        sys.exit("remote/: 어느 장에도 들지 않는 파일 (받을 길이 없음)\n  " + "\n  ".join(sorted(every - used)[:20]))
    return out


def medals():
    """data/medals.json (메달 진열장 · 끝없는 레벨) → assets/medals/medals.json. 그림은 이미 assets/medals/<id>.webp 에 있다."""
    src = os.path.join(ROOT, "data", "medals.json")
    if not os.path.exists(src):
        return {}
    return {os.path.join(APP, "assets", "medals", "medals.json"): open(src, encoding="utf-8").read()}


def shop():
    """data/shop.json (상점 줄의 작은 그림 · 출처) → assets/shop/shop.json. 그림은 이미 assets/shop/<image> 에 있다."""
    src = os.path.join(ROOT, "data", "shop.json")
    if not os.path.exists(src):
        return {}
    doc = json.load(open(src, encoding="utf-8"))
    for pc in doc.get("pictures", []):
        if not os.path.exists(os.path.join(APP, "assets", "shop", pc.get("image", ""))):
            sys.exit(f"data/shop.json: assets/shop/{pc.get('image')} 없음")
    return {os.path.join(APP, "assets", "shop", "shop.json"): open(src, encoding="utf-8").read()}


def check_spots(src, chapter):
    """그림 속 산책: 자리의 낱말이 그 자리의 '편지 속 문장'에 정말 나오는지 (세 언어 모두). 어긋나면 멈춘다."""
    bad = []
    for letter in chapter.get("letters", []):
        for k, spot in enumerate((letter.get("plate") or {}).get("spots", []), 1):
            q = spot.get("quote")
            if q is None:
                continue
            msg = letter["messages"][q]
            for lang, word in (spot.get("word") or {}).items():
                if lang == "ipa" or lang not in msg:
                    continue
                parts = [w for w in re.split(r"[ ,]+", word) if w]
                if not any(w.lower()[:max(3, len(w) - 2)] in msg[lang].lower() for w in parts):
                    bad.append(f"{letter['id']} 자리 {k} ({lang}) '{word}' 가 문장 {q} 에 없음")
    if bad:
        sys.exit(f"{os.path.relpath(src, ROOT)}: 그림 산책 낱말과 문장이 어긋남\n  " + "\n  ".join(bad))


def main():
    check = "--check" in sys.argv
    files = {**tokens(), **strings(), **letters(), **remote(), **medals(), **shop()}
    stale = []
    for path, text in files.items():
        old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if old == text:
            continue
        if check:
            stale.append(os.path.relpath(path, ROOT))
        else:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "w", encoding="utf-8").write(text)
            print("wrote", os.path.relpath(path, ROOT))
    if stale:
        sys.exit("오래된 생성 파일 (python3 scripts/generate.py 를 돌리세요):\n  " + "\n  ".join(stale))


if __name__ == "__main__":
    main()
