#!/usr/bin/env python3
"""닻 표현 (시안 beginner.html ⑤): 작품마다 여러 편지에 되풀이되는 2~3낱말 덩어리.

    python3 scripts/anchors.py                     # 손으로 고른 목록 → data/<작품>/anchors[_en|_ko].json
    python3 scripts/anchors.py --check             # 만든 파일이 목록 · gloss 와 맞는지만 (다르면 1)
    python3 scripts/anchors.py --candidates vincent [en|ko] [N]   # gloss 에서 센 후보 (편지 수 많은 순)

손으로 고른 목록: scripts/anchors/<작품>[_en|_ko].txt — 한 줄에 `낱말들|ko|en` (# 은 주석).
낱말들은 gloss 토큰 꼴을 소문자로 띄어 쓴 것 (j' ai · daß ich · i 'm · 사랑하는 테오에게).
흔하지만 뜻 없는 조합 (dans le · und die · of the) 은 넣지 않고, 인사 · 맺음 · 버릇 같은 말을 고른다.
뜻 (ko · en) 은 앱이 쓴 짧은 풀이 — 파일의 "checked": false (사람이 확인하면 목록 머리에 `# checked` 한 줄).

맞추기는 core `Anchors` 와 같다: 토큰마다 소문자 · 글자와 숫자만 남긴 꼴 (j' → j) 이 차례로 같으면 그 덩어리.
나온 편지가 2통 미만인 줄은 빠진다 (알림).

파일 꼴 (한 줄 JSON):
  {"_doc", "lang", "checked", "anchors": [{"t": 보이는 꼴, "k": 맞추는 꼴 ("j ai"), "ko", "en",
                                          "n": 나온 편지 수, "first": [편지 id, 메시지 번호], "letters": "편지 id …"}]}
"""
import collections, glob, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "scripts", "anchors")
MIN_LETTERS = 2

DOC = ("Anchor chunks: recurring 2-3 word phrases of this series (scripts/anchors.py from the hand-picked list "
       "scripts/anchors/<series>[_deck].txt). k = normalized tokens (lowercase, letters/digits only, space separated) "
       "matched against gloss tokens in order; t = shown form; ko/en = short app-written meaning; n = number of letters "
       "it appears in; first = [letter id, message index] of the first appearance; letters = space-separated letter ids.")


def norm(surface):
    return "".join(c for c in surface.lower() if c.isalnum())


def surf(t):
    return t if isinstance(t, str) else t[0]


def join(surfaces):
    out = ""
    for s in surfaces:
        if out and not out.endswith(("'", "’")) and not s.startswith(("'", "’")):
            out += " "
        out += s
    return out


def gloss(series, deck):
    path = os.path.join(ROOT, "data", series, f"gloss{'_' + deck if deck else ''}.json")
    return json.load(open(path, encoding="utf-8"))


def find(letters, key):
    """key (정규화 토큰 목록) 가 나온 곳: [(편지, 메시지, 토큰 번호, 찍힌 꼴들)]."""
    n = len(key)
    hits = []
    for lid, msgs in letters.items():
        for m, toks in enumerate(msgs):
            ns = [norm(surf(t)) for t in toks]
            for i in range(len(ns) - n + 1):
                if ns[i:i + n] == key:
                    hits.append((lid, m, i, [surf(t) for t in toks[i:i + n]]))
    return hits


def shown(hits):
    """보이는 꼴: 소문자로 시작하는 꼴이 있으면 그것 (문장 첫머리 대문자를 피함), 영어 i 는 I."""
    forms = [h[3] for h in hits]
    pick = next((f for f in forms if f[0][:1].islower()), forms[0])
    pick = ["I" if s == "i" else s for s in pick]
    return join(pick)


def build(series, deck):
    name = series + (f"_{deck}" if deck else "")
    src = os.path.join(SRC, name + ".txt")
    if not os.path.exists(src):
        return None, []
    g = gloss(series, deck)
    letters = g["letters"]
    order = list(letters)
    checked = False
    out, notes = [], []
    for line in open(src, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.strip() == "# checked":
            checked = True
        if not line.strip() or line.startswith("#"):
            continue
        chunk, ko, en = (x.strip() for x in line.split("|"))
        key = [norm(x) for x in chunk.split()]
        hits = find(letters, key)
        ls = sorted({h[0] for h in hits}, key=order.index)
        if len(ls) < MIN_LETTERS:
            notes.append(f"{name}: '{chunk}' {len(ls)}통 — 뺌")
            continue
        first = min(hits, key=lambda h: (order.index(h[0]), h[1], h[2]))
        out.append({"t": shown(hits), "k": " ".join(key), "ko": ko, "en": en, "n": len(ls),
                    "first": [first[0], first[1]], "letters": " ".join(ls)})
    data = {"_doc": DOC, "lang": g.get("lang", deck), "checked": checked, "anchors": out}
    return (name, data), notes


def candidates(series, deck, top):
    letters = gloss(series, deck)["letters"]
    c = collections.defaultdict(set)
    for lid, msgs in letters.items():
        for toks in msgs:
            ns = [norm(surf(t)) for t in toks]
            for n in (2, 3):
                for i in range(len(ns) - n + 1):
                    c[" ".join(ns[i:i + n])].add(lid)
    for k, v in sorted(c.items(), key=lambda x: -len(x[1]))[:top]:
        print(f"{len(v):3d}  {k}")


def main():
    args = sys.argv[1:]
    if args and args[0] == "--candidates":
        series = args[1]
        deck = args[2] if len(args) > 2 and not args[2].isdigit() else ""
        top = int(args[-1]) if args[-1].isdigit() else 60
        candidates(series, deck, top)
        return 0
    check = "--check" in args
    bad = 0
    for src in sorted(glob.glob(os.path.join(SRC, "*.txt"))):
        base = os.path.basename(src)[:-4]
        m = re.fullmatch(r"([a-z]+)(?:_([a-z]{2}))?", base)
        if not m:
            continue
        built, notes = build(m.group(1), m.group(2) or "")
        for n in notes:
            print("  " + n)
        name, data = built
        path = os.path.join(ROOT, "data", m.group(1), f"anchors{'_' + m.group(2) if m.group(2) else ''}.json")
        text = json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n"
        old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if old == text:
            continue
        if check:
            print(f"다름: {os.path.relpath(path, ROOT)}")
            bad = 1
        else:
            open(path, "w", encoding="utf-8").write(text)
            print(f"썼음: {os.path.relpath(path, ROOT)} ({len(data['anchors'])}개)")
    return bad


if __name__ == "__main__":
    sys.exit(main())
