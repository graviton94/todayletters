#!/usr/bin/env python3
"""디자인 토큰 · 문자열을 앱 코드로 만든다.

    python3 scripts/generate.py           # 쓰기
    python3 scripts/generate.py --check   # 커밋된 결과와 다르면 실패 (CI)

- design/tokens.json  → android/app/.../design/Tokens.kt
                        android/app/src/main/res/values{,-night}/window.xml (창 · 시작 화면 바탕색)
- design/strings.json → android/app/src/main/res/values{,-ko}/strings.xml
- data/<작품>/series.json + 챕터 → android/app/src/main/assets/letters/<작품>/
"""
import glob, json, os, sys
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


def strings():
    s = json.load(open(os.path.join(ROOT, "design", "strings.json"), encoding="utf-8"))
    out = {}
    for lang, folder in (("en", "values"), ("ko", "values-ko")):
        L = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- 자동 생성: scripts/generate.py (design/strings.json) -->", "<resources>"]
        for k, v in s.items():
            if k.startswith("_"):
                continue
            text = v[lang] if isinstance(v, dict) else v
            text = escape(text).replace("'", "\\'").replace("\n", "\\n")
            L.append(f'    <string name="{k}">{text}</string>')
        L.append("</resources>")
        out[os.path.join(APP, "res", folder, "strings.xml")] = "\n".join(L) + "\n"
    return out


def letters():
    """data/<작품>/series.json + 챕터 파일 → assets/letters/<작품>/. 검토 전 챕터는 .draft.json 을 대신 쓴다."""
    out = {}
    for meta_path in glob.glob(os.path.join(ROOT, "data", "*", "series.json")):
        folder = os.path.dirname(meta_path)
        sid = os.path.basename(folder)
        meta = json.load(open(meta_path, encoding="utf-8"))
        dest = os.path.join(APP, "assets", "letters", sid)
        out[os.path.join(dest, "series.json")] = open(meta_path, encoding="utf-8").read()
        for ch in meta["chapters"]:
            src = os.path.join(folder, ch["file"])
            if not os.path.exists(src):
                src = src.replace(".json", ".draft.json")
            if os.path.exists(src):
                out[os.path.join(dest, ch["file"])] = open(src, encoding="utf-8").read()
    return out


def main():
    check = "--check" in sys.argv
    files = {**tokens(), **strings(), **letters()}
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
