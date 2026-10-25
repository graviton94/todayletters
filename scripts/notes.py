#!/usr/bin/env python3
"""기능어 · 문형 · 관용구 노트 (편지 방 낱말 아래 뜻 + 누르면 짧은 문법 카드).

    python3 scripts/notes.py                    # 손으로 쓴 원본 → data/lexicon/notes_<말>.json · data/<작품>/idioms[_en|_ko].json
    python3 scripts/notes.py --check            # 만든 파일이 원본과 맞는지만 (다르면 1)
    python3 scripts/notes.py --candidates vincent [en|ko] [N]   # 관용구 후보 (기능어가 든 2~5 표제어 n-gram, 편지 수 많은 순)
    python3 scripts/notes.py --seed             # 일반 목록 (scripts/idioms/known/<말>.txt) 에서 찾은 관용구로 작품 · 말마다 처음 목록 (없는 것만)
    python3 scripts/notes.py --report [출력.md]  # 덮인 비율 (전 · 후) + 말마다 무작위 문장 20개의 칸

원본 (사람 · 앱이 씀, 이것만 고친다):
  scripts/notes/<말>.txt   (fr de es en ko) — 줄 머리 글자로 갈래:
    T|이름|ko|en                       꼬리표 (tag) 이름 → 보이는 글
    C|이름|조건 [|| 조건 …]            토큰 갈래 (아래 조건 중 하나라도 맞으면)
    W|사전 열쇠|꼬리표|짧은 뜻 ko|en|노트 ko|en         기능어 (lexicon 표제어 열쇠)
    E|꼴/TAG|꼬리표|짧은 뜻 ko|en|show|노트 ko|en        한국어 어미 · 조사 (gloss_ko 의 fn 조각), show=1 이면 낱말 뜻 뒤에 붙임
    P|id|부분 + 부분 …|설정|꼬리표|짧은 뜻 ko|en|노트 ko|en   문형 (파일 차례 = 먼저 잡는 차례)
  scripts/idioms/<작품>[_en|_ko].txt — `표제어 열쇠들|ko|en[|노트 ko|노트 en]` (열쇠 = gloss 토큰 사전 열쇠, 소문자, 띄어 씀)

조건 (쉼표 = 그리고, 앞에 ! = 아님): l=표제어/… (사전 열쇠, 소문자) · f=꼴/… (찍힌 꼴의 맞추는 꼴, core Anchors.norm) ·
  p=품사/… (lexicon pos) · c=갈래 · r=정규식 (찍힌 꼴 소문자 전체) · fn=정규식 (한국어 fn 에서 찾기) · i (활용된 꼴: 꼴 ≠ 표제어) · u (대문자로 시작)
부분 꼬리: @head (그 낱말 뜻 그대로) · @part (뜻 없음, 한 묶음) · 없으면 mark (문형의 짧은 뜻).
설정 (띄어 씀): gap=N (부분 사이 낱말 수 상한) · stop=조건 (사이 낱말이 이것이면 실패, 여러 번) · next!=조건 · prev!=조건 ·
  always (기능어 뜻을 끈 등급에도 뜻) · compound (독일어 분리동사: 둘째 부분 꼴 + 첫 부분 표제어가 사전에 있어야, 뜻은 그 사전 줄)

맞추기 규칙은 core `NoteBook` (android/core/.../core/Notes.kt) 와 같다. 둘이 같은지는 scripts/notes/expect.json
(작품 · 말마다 잡힌 수) 을 core `NotesDataTest` 가 다시 세어 본다 — 규칙을 고치면 이 스크립트를 다시 돌린다.
"""
import collections, glob, hashlib, json, os, random, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "scripts", "notes")
IDS = os.path.join(ROOT, "scripts", "idioms")
SERIES = {"vincent": "fr", "mozart": "de", "napoleon": "fr", "goya": "es"}
LANGS = ["fr", "de", "es", "en", "ko"]

DOC = ("Function-word notes, constructions and grammar hints for the reading room (scripts/notes.py from scripts/notes/<lang>.txt; "
       "do not edit by hand). words: lexicon key -> {tag, short, note} (ko/en each). endings (ko only): gloss_ko fn piece 'form/TAG' -> same + show "
       "(append short to the word gloss). classes: name -> list of token specs (any). patterns: tried in order; seq = token specs (lemma = lexicon keys "
       "lowercase, form = Anchors.norm of the surface, pos = lexicon pos, re = full regex on the lowercase surface, fn = regex found in the ko fn, cls = classes (all must match), "
       "infl = surface differs from lemma, upper = surface starts with a capital, not = specs that must not match) with role mark|head|part; gap = max tokens between parts; stop = specs a gap token "
       "must not match; next_not / prev_not = specs for the token after the last / before the first part; always = show even when function glosses are off; "
       "compound = German separable verb (part 2 form + part 1 lemma must be a lexicon key; short is that entry's meaning). Matching: core NoteBook.")
IDOC = ("Idioms of this series for this learning language (scripts/notes.py from scripts/idioms/<series>[_deck].txt). lem = lexicon keys (lowercase, space "
        "separated) matched in a row against gloss tokens; t = shown form; ko/en = short meaning; note_ko/note_en = optional one-line note; n = letters it "
        "appears in; first = [letter id, message index]. Patterns win over idioms, idioms over anchors.")


def norm(s):
    return "".join(c for c in s.lower() if c.isalnum())


def low(k):
    return k.lower()


# ── 원본 읽기 ─────────────────────────────────────────────
def parse_cond(text):
    """'l=avoir/être,!p=n,i' → spec dict."""
    spec, nots = {}, []
    for part in [p.strip() for p in text.split(",") if p.strip()]:
        neg = part.startswith("!")
        if neg:
            part = part[1:]
        if part == "i":
            one = {"infl": True}
        elif part == "u":
            one = {"upper": True}
        else:
            k, v = part.split("=", 1)
            if k == "l":
                one = {"lemma": [x.lower() for x in v.split("/")]}
            elif k == "f":
                one = {"form": [norm(x) for x in v.split("/")]}
            elif k == "p":
                one = {"pos": v.split("/")}
            elif k == "c":
                one = {"cls": [v]}
            elif k == "r":
                one = {"re": v}
            elif k == "fn":
                one = {"fn": v}
            else:
                raise SystemExit(f"조건을 모름: {part}")
        if neg:
            nots.append(one)
        else:
            for kk, vv in one.items():
                if kk == "cls" and kk in spec:
                    spec[kk] = spec[kk] + vv
                    continue
                if kk in spec:
                    raise SystemExit(f"조건이 겹침: {text}")
                spec[kk] = vv
    if nots:
        spec["not"] = nots
    return spec


def load_source(lang):
    tags, classes, words, endings, patterns = {}, {}, {}, {}, []
    path = os.path.join(SRC, f"{lang}.txt")
    for n, line in enumerate(open(path, encoding="utf-8"), 1):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        f = [x.strip() for x in line.split("|")]
        kind = f[0]
        try:
            if kind == "T":
                tags[f[1]] = {"ko": f[2], "en": f[3]}
            elif kind == "C":
                classes[f[1]] = [parse_cond(x) for x in "|".join(f[2:]).split("||")]
            elif kind == "W":
                _, key, tag, sko, sen, nko, nen = f
                if key in words:
                    raise SystemExit(f"{lang}:{n}: 낱말이 겹침 {key}")
                words[key] = {"tag": tags[tag], "short": {"ko": sko, "en": sen}, "note": {"ko": nko, "en": nen}}
            elif kind == "E":
                _, key, tag, sko, sen, show, nko, nen = f
                endings[key] = {"tag": tags[tag], "short": {"ko": sko, "en": sen}, "note": {"ko": nko, "en": nen}, "show": show == "1"}
            elif kind == "P":
                # 부분 칸에는 정규식의 | 가 들 수 있어 뒤에서부터 센다 (설정 · 꼬리표 · 뜻 · 노트 칸에는 | 없음)
                pid, seq = f[1], "|".join(f[2:-6])
                opts, tag, sko, sen, nko, nen = f[-6:]
                p = {"id": pid, "seq": []}
                for part in seq.split(" + "):
                    role = "mark"
                    m = re.search(r"@(head|part)$", part.strip())
                    if m:
                        role = m.group(1)
                        part = part.strip()[:m.start()]
                    s = parse_cond(part)
                    s["role"] = role
                    p["seq"].append(s)
                p["gap"] = 0
                for o in opts.split():
                    if o.startswith("gap="):
                        p["gap"] = int(o[4:])
                    elif o.startswith("stop="):
                        p.setdefault("stop", []).append(parse_cond(o[5:]))
                    elif o.startswith("next!="):
                        p.setdefault("next_not", []).append(parse_cond(o[6:]))
                    elif o.startswith("prev!="):
                        p.setdefault("prev_not", []).append(parse_cond(o[6:]))
                    elif o in ("always", "compound"):
                        p[o] = True
                    elif o != "-":
                        raise SystemExit(f"설정을 모름: {o}")
                p.update({"tag": tags[tag], "short": {"ko": sko, "en": sen}, "note": {"ko": nko, "en": nen}})
                if any(x["id"] == pid for x in patterns):
                    raise SystemExit(f"{lang}:{n}: 문형 id 가 겹침 {pid}")
                patterns.append(p)
            else:
                raise SystemExit(f"{lang}:{n}: 갈래를 모름 {kind}")
        except (ValueError, KeyError) as e:
            raise SystemExit(f"{lang}:{n}: {e!r}\n  {line}")
    for k, d in list(words.items()) + list(endings.items()) + [(p["id"], p) for p in patterns]:
        for l in ("ko", "en"):
            x = d["short"][l]
            if len(x) > NOTE_MAX:
                print(f"{lang}: 짧은 뜻이 김 {k} {l}: {x} → {clip(x)}")
    out = {"_doc": DOC, "v": 1, "lang": lang, "classes": classes, "words": words, "patterns": patterns}
    if endings:
        out["endings"] = endings
    return out


# ── 어휘 자료 ─────────────────────────────────────────────
def deck_suffix(deck):
    return f"_{deck}" if deck else ""


def lexicon(series, deck):
    L = json.load(open(os.path.join(ROOT, "data", series, f"lexicon{deck_suffix(deck)}.json"), encoding="utf-8"))
    F = L["fields"]
    out = {}
    for e in L["entries"]:
        d = dict(zip(F, e))
        out[d["id"] or d["lemma"]] = d
    return out


def tokens_of(series, deck):
    G = json.load(open(os.path.join(ROOT, "data", series, f"gloss{deck_suffix(deck)}.json"), encoding="utf-8"))
    out = {}
    for lid, msgs in G["letters"].items():
        out[lid] = [[(t, t, "") if isinstance(t, str) else (t[0], t[1], t[2] if len(t) > 2 else "") for t in m] for m in msgs]
    return out


# ── 맞추기 (core NoteBook 과 같게) ─────────────────────────
class Book:
    def __init__(self, notes, idioms, lex):
        self.n = notes
        self.idioms = idioms or []
        self.lex = lex
        self.res = {}

    def rx(self, p):
        if p not in self.res:
            self.res[p] = re.compile(p)
        return self.res[p]

    def ok(self, spec, t):
        surface, key, fn = t
        k = low(key)
        if "lemma" in spec and k not in spec["lemma"]:
            return False
        if "form" in spec and norm(surface) not in spec["form"]:
            return False
        if "pos" in spec:
            e = self.lex.get(key)
            if not e or e.get("pos") not in spec["pos"]:
                return False
        if "re" in spec and not self.rx(spec["re"]).fullmatch(surface.lower()):
            return False
        if "fn" in spec and not self.rx(spec["fn"]).search(fn or ""):
            return False
        if spec.get("infl") and surface.lower() == k.split("#")[0]:
            return False
        if spec.get("upper") and not surface[:1].isupper():
            return False
        for c in spec.get("cls", []):
            if not any(self.ok(s, t) for s in self.n["classes"][c]):
                return False
        if any(self.ok(s, t) for s in spec.get("not", [])):
            return False
        return True

    def try_at(self, p, toks, i, taken):
        seq = p["seq"]
        if taken[i] or not self.ok(seq[0], toks[i]):
            return None
        if "prev_not" in p and i > 0 and any(self.ok(s, toks[i - 1]) for s in p["prev_not"]):
            return None
        parts = [i]
        for s in seq[1:]:
            prev = parts[-1]
            found = None
            for j in range(prev + 1, min(len(toks), prev + 2 + p["gap"])):
                if not taken[j] and self.ok(s, toks[j]):
                    found = j
                    break
                if any(self.ok(st, toks[j]) for st in p.get("stop", [])):
                    break
            if found is None:
                return None
            parts.append(found)
        last = parts[-1]
        if "next_not" in p and last + 1 < len(toks) and any(self.ok(s, toks[last + 1]) for s in p["next_not"]):
            return None
        sh = p["short"]
        if p.get("compound"):
            e = self.lex.get(norm(toks[parts[1]][0]) + low(toks[parts[0]][1]))
            if not e:
                return None
            sh = {"ko": short(e.get("ko") or ""), "en": short(e.get("en") or "")}
        return parts, sh

    def match(self, toks, blocked=None):
        n = len(toks)
        taken = [bool(blocked and blocked[i]) for i in range(n)]
        hits = []
        for p in self.n["patterns"]:
            for i in range(n):
                r = self.try_at(p, toks, i, taken)
                if r:
                    parts, short = r
                    for j in parts:
                        taken[j] = True
                    hits.append({"kind": "PATTERN", "id": p["id"], "start": parts[0], "end": parts[-1], "parts": parts,
                                 "roles": [s["role"] for s in p["seq"]], "short": short, "always": bool(p.get("always"))})
        keys = [low(t[1]) for t in toks]
        for idm in sorted(self.idioms, key=lambda x: -len(x["lem"].split())):
            want = idm["lem"].split()
            m = len(want)
            for i in range(n - m + 1):
                if keys[i:i + m] == want and not any(taken[i:i + m]):
                    for j in range(i, i + m):
                        taken[j] = True
                    hits.append({"kind": "IDIOM", "id": idm["lem"], "start": i, "end": i + m - 1, "parts": list(range(i, i + m)),
                                 "roles": ["mark"] * m, "short": {"ko": idm["ko"], "en": idm["en"]}, "always": True})
        for i, t in enumerate(toks):
            if taken[i]:
                continue
            w = self.n["words"].get(t[1]) or self.n["words"].get(low(t[1]))
            end = None
            if t[2] and self.n.get("endings"):
                pieces = t[2].split("+")
                end = next((k for k in self.n["endings"] if k in pieces), None)
            if not w and not end:
                continue
            e = self.n["endings"][end] if end else None
            hits.append({"kind": "WORD", "id": t[1] if w else end, "start": i, "end": i, "parts": [i], "roles": ["mark"],
                         "short": (w or e)["short"], "always": False, "word": bool(w),
                         "suffix": e["short"] if e and e["show"] else None})
        return sorted(hits, key=lambda h: h["start"])


GLOSS_MAX = 14
NOTE_MAX = 16


def clip(x):
    """노트의 짧은 뜻은 손으로 쓴 그대로 (쉼표에서 끊지 않음), 길면 줄임 (core Notes.clip)."""
    x = (x or "").strip()
    return x if len(x) <= NOTE_MAX else x[:NOTE_MAX - 1].rstrip() + "…"


def short(x):
    x = x or ""
    first = re.split(r"[,;/(（]", x)[0].strip() or x.strip()
    return first if len(first) <= GLOSS_MAX else first[:GLOSS_MAX - 1].rstrip() + "…"


def silent(e):
    p = e.get("pos") or ""
    return p in ("name", "num", "abbr") or p.startswith("foreign")


def units(book, toks, lang, function=True, anchors=()):
    """core Interlinear.units 와 같게 (표현 카드 · 아는 낱말 없이): [(from, to, gloss, kind)]."""
    n = len(toks)
    lex = book.lex if book else {}
    taken = [False] * n
    out = []
    hits = book.match(toks) if book else []
    for h in hits:
        if h["kind"] == "PATTERN":
            show = h["always"] or function
            marks = [j for j, r in zip(h["parts"], h["roles"]) if r == "mark"]
            groups = []
            for j in marks:
                if groups and groups[-1][-1] == j - 1:
                    groups[-1].append(j)
                else:
                    groups.append([j])
            for gi, g in enumerate(groups):
                out.append((g[0], g[-1], clip(h["short"][lang]) if show and gi == len(groups) - 1 else None, "PATTERN"))
            for j, r in zip(h["parts"], h["roles"]):
                if r == "part":
                    out.append((j, j, None, "PATTERN"))
                elif r == "head":
                    out.append((j, j, word_gloss(lex, toks[j], lang, function), "PATTERN"))
            for j in h["parts"]:
                taken[j] = True
        elif h["kind"] == "IDIOM":
            out.append((h["start"], h["end"], clip(h["short"][lang]), "IDIOM"))
            for j in h["parts"]:
                taken[j] = True
    norms = [norm(t[0]) for t in toks]
    for a in sorted(anchors, key=lambda a: -len(a["k"].split())):
        want = "".join(a["k"].split())
        for i in range(n):
            if not norms[i] or not want.startswith(norms[i]):
                continue
            acc, j = "", i
            while j < n and len(acc) < len(want):
                acc += norms[j]
                if not want.startswith(acc):
                    break
                if acc == want:
                    if (j > i or len(a["k"].split()) == 1) and not any(taken[i:j + 1]):
                        for x in range(i, j + 1):
                            taken[x] = True
                        out.append((i, j, short(a["ko"] if lang == "ko" else a["en"]), "ANCHOR"))
                    break
                j += 1
    words = {h["start"]: h for h in hits if h["kind"] == "WORD"}
    for j, t in enumerate(toks):
        if taken[j]:
            continue
        g = word_gloss(lex, t, lang, function)
        h = words.get(j)
        if h is not None:
            e = lex.get(t[1])
            if h["word"] and (e is None or e.get("function")):
                g = clip(h["short"][lang]) if function else None
            if h["suffix"] and g:
                g = with_ending(g, h["suffix"][lang])
        out.append((j, j, g, "WORD"))
    return sorted(out)


SUFFIX_MAX = 22


def with_ending(g, s):
    """한국어 어미 · 조사의 짧은 뜻을 낱말 뜻 뒤에 괄호로 (to think (past)), 너무 길면 붙이지 않음 (core Notes.withEnding)."""
    s = s.strip()
    if not s:
        return g
    s = s if s.startswith("(") else f"({s})"
    out = f"{g} {s}"
    return out if len(out) <= SUFFIX_MAX else g


def word_gloss(lex, t, lang, function):
    e = lex.get(t[1])
    if e is None:
        return ""
    if silent(e):
        return None
    m = e.get(lang) or e.get("en" if lang == "ko" else "ko") or ""
    if e.get("function"):
        return short(m) if function else None
    return short(m)


# ── 관용구 ───────────────────────────────────────────────
def load_idioms(series, deck):
    path = os.path.join(ROOT, "data", series, f"idioms{deck_suffix(deck)}.json")
    if not os.path.exists(path):
        return []
    return json.load(open(path, encoding="utf-8"))["idioms"]


def build_idioms(series, deck, toks_by_letter, anchors_keys):
    name = series + deck_suffix(deck)
    src = os.path.join(IDS, name + ".txt")
    if not os.path.exists(src):
        return None, []
    order = list(toks_by_letter)
    out, notes = [], []
    for line in open(src, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        f = [x.strip() for x in line.split("|")]
        lem, ko, en = f[0].lower(), f[1], f[2]
        nko = f[3] if len(f) > 3 else ""
        nen = f[4] if len(f) > 4 else ""
        want = lem.split()
        hits = []
        for lid, msgs in toks_by_letter.items():
            for mi, m in enumerate(msgs):
                ks = [low(t[1]) for t in m]
                for i in range(len(ks) - len(want) + 1):
                    if ks[i:i + len(want)] == want:
                        hits.append((lid, mi, i, [t[0] for t in m[i:i + len(want)]]))
        if not hits:
            notes.append(f"{name}: '{lem}' 편지에 없음 — 뺌")
            continue
        surf_key = " ".join(norm(s) for s in hits[0][3])
        inside = next((a for a in anchors_keys if f" {surf_key} " in f" {a} "), None)
        if inside:
            notes.append(f"{name}: '{lem}' 은 닻 표현 '{inside}' 안에 듦 — 뺌")
            continue
        if any(x["lem"] == lem for x in out):
            raise SystemExit(f"{name}: '{lem}' 겹침")
        ls = sorted({h[0] for h in hits}, key=order.index)
        first = min(hits, key=lambda h: (order.index(h[0]), h[1], h[2]))
        forms = [h[3] for h in hits]
        pick = next((fm for fm in forms if fm[0][:1].islower()), forms[0])
        t = ""
        for s in pick:
            if t and not t.endswith(("'", "’")) and not s.startswith(("'", "’")):
                t += " "
            t += s
        d = {"lem": lem, "t": t, "ko": ko, "en": en}
        if nko:
            d["note_ko"] = nko
        if nen:
            d["note_en"] = nen
        d.update({"n": len(ls), "first": [first[0], first[1]]})
        out.append(d)
    return (name, {"_doc": IDOC, "lang": SERIES[series] if not deck else deck, "checked": False, "idioms": out}), notes


def known(lang):
    """scripts/idioms/known/<말>.txt: 일반 관용구 목록 (낱말들|ko|en[|노트 ko|노트 en]), 두 낱말 이상만."""
    path = os.path.join(IDS, "known", f"{lang}.txt")
    out = []
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            f = [x.strip() for x in line.split("|")]
            words = f[0].lower().split()
            if len(words) >= 2:
                out.append((words, f[1:]))
    return out


def known_hits(series, deck):
    """일반 목록의 관용구가 이 작품 · 말의 편지에 나오는 곳: 낱말마다 표제어 열쇠나 찍힌 꼴 (맞추는 꼴) 이 같으면.
    문형에 먼저 잡혀 관용구로 쓰일 일이 없는 것은 뺀다. → [(표제어 열쇠들, [ko, en, …], 편지 수)]."""
    lang = SERIES[series] if not deck else deck
    toks = tokens_of(series, deck)
    lex = lexicon(series, deck)
    notes = load_source(lang) if os.path.exists(os.path.join(SRC, f"{lang}.txt")) else {"classes": {}, "words": {}, "patterns": []}
    book = Book(notes, [], lex)
    found = collections.OrderedDict()
    msgs_taken = []
    for lid, msgs in toks.items():
        for msg in msgs:
            taken = [False] * len(msg)
            for h in book.match(msg):
                if h["kind"] == "PATTERN":
                    for j in h["parts"]:
                        taken[j] = True
            msgs_taken.append((lid, msg, taken))
    for words, rest in known(lang):
        m = len(words)
        for lid, msg, taken in msgs_taken:
            for i in range(len(msg) - m + 1):
                win = msg[i:i + m]
                if all(w in (low(t[1]), norm(t[0])) for w, t in zip(words, win)) and not any(taken[i:i + m]):
                    lem = " ".join(low(t[1]) for t in win)
                    found.setdefault((lem, tuple(rest)), set()).add(lid)
    return [(lem, list(rest), len(ls)) for (lem, rest), ls in found.items()]


def candidates(series, deck, top):
    toks = tokens_of(series, deck)
    lex = lexicon(series, deck)
    anchors = {a["k"] for a in json.load(open(os.path.join(ROOT, "data", series, f"anchors{deck_suffix(deck)}.json"), encoding="utf-8"))["anchors"]}
    print("# 일반 목록에서 (표제어 열쇠|뜻 …|편지 수)")
    for lem, rest, n in known_hits(series, deck):
        print(f"{lem}|{'|'.join(rest)}|{n}")
    print("# 자주 나오는 n-gram (편지 수, 표제어 열쇠, ‹보기›) — 관사 · 한정사로 시작하거나 끝나는 것, 이름 · 숫자 든 것 뺌")
    c = collections.defaultdict(set)
    show = {}
    edge = {"art", "det"}
    for lid, msgs in toks.items():
        for m in msgs:
            ks = [low(t[1]) for t in m]
            ps = [(lex.get(t[1]) or {}).get("pos") for t in m]
            fn = [bool((lex.get(t[1]) or {}).get("function")) for t in m]
            for n in (2, 3, 4, 5):
                for i in range(len(ks) - n + 1):
                    if not any(fn[i:i + n]) or all(fn[i:i + n]):
                        continue
                    if ps[i] in edge or ps[i + n - 1] in edge or any(p in ("name", "num", None) for p in ps[i:i + n]):
                        continue
                    if " ".join(norm(t[0]) for t in m[i:i + n]) in anchors:
                        continue
                    k = " ".join(ks[i:i + n])
                    c[k].add(lid)
                    show.setdefault(k, " ".join(t[0] for t in m[i:i + n]))
    rows = sorted(((len(v), k) for k, v in c.items() if len(v) >= 3), key=lambda x: (-x[0], -len(x[1].split())))
    for cnt, k in rows[:top]:
        print(f"{cnt:3d}  {k}  ‹{show[k]}›")


def seed(series, deck):
    """일반 목록에서 찾은 관용구로 scripts/idioms/<작품>[_말].txt 의 처음 꼴을 쓴다 (이미 있으면 그대로 두고 알림)."""
    name = series + deck_suffix(deck)
    path = os.path.join(IDS, name + ".txt")
    if os.path.exists(path):
        print(f"{name}: 이미 있음 — 손으로 고칠 것")
        return
    lines = [f"# {name} 관용구 (scripts/notes.py --seed 로 일반 목록에서 찾은 것 + 손으로 고름). 줄 = 표제어 열쇠|ko|en[|노트 ko|노트 en]"]
    for lem, rest, n in known_hits(series, deck):
        lines.append("|".join([lem] + rest))
    open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(f"{name}: {len(lines) - 1}")


# ── 덮인 비율 · 보기 ─────────────────────────────────────
def report(path):
    rnd = random.Random(7)
    lines = ["# 낱말 아래 뜻 노트 점검 (scripts/notes.py --report)", "",
             "토큰 = 뜻이 필요한 토큰 (이름 · 숫자 · 외국어 · 약어 빼고). 덮임 = 뜻이 붙었거나 문형 · 관용구 칸에 든 토큰.",
             "A1·A2 = 기능어 뜻 켬, B1+ = 기능어 뜻 끔 (아는 낱말은 없다고 침). 전 = 사전 첫 뜻 + 닻 (지금 앱), 후 = 문형 · 관용구 · 기능어 노트 + 닻.",
             "맥락 뜻 = 뜻이 사전 첫 뜻이 아니라 문형 · 관용구 · 기능어 노트에서 온 토큰, 카드 = 누르면 문법 카드가 있는 토큰.", "",
             "| 작품 | 말 | 토큰 | A1·A2 전 → 후 % | B1+ 전 → 후 % | 맥락 뜻 % | 카드 % | 문형 | 관용구 | 기능어 · 어미 |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    samples = collections.defaultdict(list)
    for series, orig in SERIES.items():
        for deck in ["", "en", "ko"]:
            lang = orig if not deck else deck
            gl = "ko" if lang != "ko" else "en"
            notes = json.load(open(os.path.join(ROOT, "data", "lexicon", f"notes_{lang}.json"), encoding="utf-8"))
            lex = lexicon(series, deck)
            anchors = json.load(open(os.path.join(ROOT, "data", series, f"anchors{deck_suffix(deck)}.json"), encoding="utf-8"))["anchors"]
            book = Book(notes, load_idioms(series, deck), lex)
            bare = Book({"classes": {}, "words": {}, "patterns": []}, [], lex)
            tot = 0
            cov = collections.Counter()
            cnt = collections.Counter()
            for lid, msgs in tokens_of(series, deck).items():
                for mi, m in enumerate(msgs):
                    need = [j for j, t in enumerate(m) if not (lex.get(t[1]) and silent(lex[t[1]]))]
                    tot += len(need)
                    def covered(us):
                        g = set()
                        for a_, b_, gg, k in us:
                            if gg or k in ("PATTERN", "IDIOM"):
                                g.update(range(a_, b_ + 1))
                        return len([j for j in need if j in g])
                    for fn_on, tag in ((True, "A"), (False, "B")):
                        cov[tag + "0"] += covered(units(bare, m, gl, fn_on, anchors))
                        ua = units(book, m, gl, fn_on, anchors)
                        cov[tag + "1"] += covered(ua)
                        if fn_on:
                            ub = {(x, y): g for x, y, g, k in units(bare, m, gl, True, anchors)}
                            ctx = set()
                            for x, y, g, k in ua:
                                if g and (k in ("PATTERN", "IDIOM") and ub.get((x, y)) != g or k == "WORD" and ub.get((x, y)) != g):
                                    ctx.update(range(x, y + 1))
                            cov["ctx"] += len([j for j in need if j in ctx])
                            first_ua = ua
                    hs = book.match(m)
                    cov["card"] += len([j for j in need if j in {p for h in hs for p in h["parts"]}])
                    for h in hs:
                        cnt[h["kind"]] += 1
                    samples[lang].append((series, lid, mi, m, first_ua, hs))
            pct = lambda x: f"{100 * x / max(tot, 1):.1f}"
            lines.append(f"| {series} | {lang} | {tot} | {pct(cov['A0'])} → {pct(cov['A1'])} | {pct(cov['B0'])} → {pct(cov['B1'])} | "
                         f"{pct(cov['ctx'])} | {pct(cov['card'])} | {cnt['PATTERN']} | {cnt['IDIOM']} | {cnt['WORD']} |")
    lines.append("")
    for lang in LANGS:
        gl = "ko" if lang != "ko" else "en"
        lines += [f"## {lang} 보기 20 (뜻 = {gl}, A1·A2)", "", "ᴾ 문형 · ᴵ 관용구 · ᴬ 닻, 〔·〕 뜻 없음 (이름 · 문형의 묶음 조각), 〔?〕 사전에 없음.", ""]
        pool = [s_ for s_ in samples[lang] if 5 <= len(s_[3]) <= 30 and any(h["kind"] != "WORD" for h in s_[5])]
        for series, lid, mi, m, ua, hs in rnd.sample(pool, min(20, len(pool))):
            cells = []
            for a_, b_, g, k in ua:
                txt = " ".join(t[0] for t in m[a_:b_ + 1])
                tag = {"PATTERN": "ᴾ", "IDIOM": "ᴵ", "ANCHOR": "ᴬ"}.get(k, "")
                cells.append(f"{txt}{tag}〔{g if g else '·' if g is None else '?'}〕")
            ids = sorted({h["id"] for h in hs if h["kind"] != "WORD"})
            lines.append(f"- `{series} {lid}:{mi}` " + " ".join(cells) + (f"  — {', '.join(ids)}" if ids else ""))
        lines.append("")
    open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(path)


# ── 만들기 ───────────────────────────────────────────────
def dump(d):
    return json.dumps(d, ensure_ascii=False, separators=(",", ":")) + "\n"


def build_all():
    files, notes = {}, []
    for lang in LANGS:
        if os.path.exists(os.path.join(SRC, f"{lang}.txt")):
            files[os.path.join(ROOT, "data", "lexicon", f"notes_{lang}.json")] = load_source(lang)
    expect = {}
    for series, orig in SERIES.items():
        for deck in ["", "en", "ko"]:
            lang = orig if not deck else deck
            toks = tokens_of(series, deck)
            akeys = {a["k"] for a in json.load(open(os.path.join(ROOT, "data", series, f"anchors{deck_suffix(deck)}.json"), encoding="utf-8"))["anchors"]}
            r, ns = build_idioms(series, deck, toks, akeys)
            notes += ns
            idioms = []
            if r:
                files[os.path.join(ROOT, "data", series, f"idioms{deck_suffix(deck)}.json")] = r[1]
                idioms = r[1]["idioms"]
            nb = files.get(os.path.join(ROOT, "data", "lexicon", f"notes_{lang}.json"))
            if nb:
                book = Book(nb, idioms, lexicon(series, deck))
                c = collections.Counter()
                for lid, msgs in toks.items():
                    for m in msgs:
                        for h in book.match(m):
                            c[h["kind"]] += 1
                expect[series + deck_suffix(deck)] = {k: c[k] for k in ("PATTERN", "IDIOM", "WORD")}
    files[os.path.join(SRC, "expect.json")] = {"_doc": "Hit counts per series[_deck] from scripts/notes.py; core NotesDataTest recounts them with NoteBook.", **expect}
    return files, notes


def main():
    args = sys.argv[1:]
    if args and args[0] == "--candidates":
        series = args[1]
        deck = args[2] if len(args) > 2 and not args[2].isdigit() else ""
        top = int(args[-1]) if args[-1].isdigit() else 150
        candidates(series, deck, top)
        return 0
    if args and args[0] == "--seed":
        for series, orig in SERIES.items():
            for deck in ["", "en", "ko"]:
                seed(series, deck)
        return 0
    if args and args[0] == "--report":
        report(args[1] if len(args) > 1 else os.path.join(ROOT, "notes_report.md"))
        return 0
    files, notes = build_all()
    for x in notes:
        print(x)
    check = "--check" in args
    bad = 0
    for path, d in files.items():
        text = dump(d)
        old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if old != text:
            if check:
                print("다름:", os.path.relpath(path, ROOT))
                bad += 1
            else:
                open(path, "w", encoding="utf-8").write(text)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
